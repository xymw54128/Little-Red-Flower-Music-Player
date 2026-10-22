import SwiftUI
import UIKit

struct LyricView: UIViewRepresentable {
    var text: String
    var settings: MusicSettings
    var playback: Playback
    var active: Bool
    var reduceMotion: Bool

    func makeUIView(context: Context) -> LyricScrollView { LyricScrollView() }
    func updateUIView(_ view: LyricScrollView, context: Context) {
        view.configure(text: text, settings: settings, playback: playback, active: active, reduceMotion: reduceMotion)
    }
    static func dismantleUIView(_ view: LyricScrollView, coordinator: ()) { view.stop() }
}

@MainActor
private final class LyricFrameTarget: NSObject {
    weak var view: LyricScrollView?
    @objc func frame(_ link: CADisplayLink) { view?.frame(link) }
}

/// Labels keep fixed layout positions. A single display-link clock updates their
/// transforms and the scroll offset, so interrupted motion never queues tweens.
final class LyricScrollView: UIScrollView, UIScrollViewDelegate {
    private var text = ""
    private var songID: String?
    private var settings = MusicSettings()
    private var lines: [LyricLine] = []
    private var timed = true
    private var labels: [UILabel] = []
    private var centers: [CGFloat] = []
    private var link: CADisplayLink?
    private let frameTarget = LyricFrameTarget()
    private weak var playback: Playback?
    private var reduceMotion = false
    private var lastSize = CGSize.zero
    private var manualUntil: CFTimeInterval = 0
    private var transition: (time: CFTimeInterval, offset: CGFloat, emphasis: [Int: CGFloat])?
    private var displayedEmphasis: [Int: CGFloat] = [:]
    private var lastPosition = -1.0
    private var lastSeekGeneration: UInt64 = 0
    private var lastFrameTime = 0.0
    private var frameInterval = 1.0 / 60
    private var wasManual = false
    private var wasActive = false
    private var needsPlacement = true
    private let emptyLabel = UILabel()

    override init(frame: CGRect) {
        super.init(frame: frame)
        delegate = self
        showsVerticalScrollIndicator = false
        contentInsetAdjustmentBehavior = .never
        alwaysBounceVertical = true
        isDirectionalLockEnabled = true
        frameTarget.view = self
        emptyLabel.text = "暂无歌词\n可在设置中导入 LRC"
        emptyLabel.numberOfLines = 0; emptyLabel.textAlignment = .center
        emptyLabel.textColor = UIColor(Brand.muted); emptyLabel.font = .systemFont(ofSize: 17)
        addSubview(emptyLabel)
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) is not supported") }

    func configure(text: String, settings: MusicSettings, playback: Playback, active: Bool, reduceMotion: Bool) {
        self.playback = playback; self.reduceMotion = reduceMotion
        let colorChanged = self.settings.theme != settings.theme || self.settings.nightMode != settings.nightMode
        let layoutChanged = songID != playback.current?.id || self.text != text || self.settings.lyricFont != settings.lyricFont || self.settings.lyricSize != settings.lyricSize || self.settings.lyricGap != settings.lyricGap
        self.settings = settings
        if layoutChanged {
            songID = playback.current?.id
            self.text = text; lines = Lyrics.parse(text)
            timed = !lines.isEmpty
            if !timed {
                lines = text.components(separatedBy: .newlines).map { $0.trimmingCharacters(in: .whitespaces) }.filter { !$0.isEmpty }
                    .enumerated().map { LyricLine(time: Double($0.offset), text: $0.element) }
            }
            for label in labels { label.removeFromSuperview() }
            labels = lines.map { line in
                let label = UILabel()
                label.text = line.text; label.numberOfLines = 0; label.textAlignment = .center
                label.font = Brand.font(settings.lyricFont, size: settings.lyricSize)
                label.textColor = inactiveColor(); label.alpha = 1
                label.isAccessibilityElement = true
                addSubview(label)
                return label
            }
            emptyLabel.isHidden = !lines.isEmpty
            lastSize = .zero; displayedEmphasis = [:]; manualUntil = 0; transition = nil
            needsPlacement = true; lastPosition = -1
            setNeedsLayout()
        }
        if colorChanged || layoutChanged { apply(displayedEmphasis, allRows: true) }
        if active && !wasActive {
            // Before the page becomes visible, place the actual current line once.
            // Do not animate from a stale page offset or from the first lyric.
            manualUntil = 0; transition = nil; wasManual = false
            needsPlacement = true; lastPosition = -1; lastFrameTime = 0
            lastSeekGeneration = playback.seekGeneration
            render(playback.audio?.currentTime ?? playback.progress.position, now: CACurrentMediaTime(), manual: false)
        }
        wasActive = active
        if active, timed, link == nil {
            let link = CADisplayLink(target: frameTarget, selector: #selector(LyricFrameTarget.frame(_:)))
            link.preferredFrameRateRange = CAFrameRateRange(minimum: 30, maximum: 120, preferred: ProcessInfo.processInfo.isLowPowerModeEnabled ? 60 : 120)
            link.add(to: .main, forMode: .common)
            self.link = link
        } else if !active || !timed { stop() }
    }
    func stop() { link?.invalidate(); link = nil; lastFrameTime = 0 }

    override func traitCollectionDidChange(_ previousTraitCollection: UITraitCollection?) {
        super.traitCollectionDidChange(previousTraitCollection)
        if traitCollection.hasDifferentColorAppearance(comparedTo: previousTraitCollection) {
            apply(displayedEmphasis, allRows: true)
        }
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        guard bounds.size != lastSize, bounds.width > 0, bounds.height > 0 else { return }
        lastSize = bounds.size
        emptyLabel.frame = bounds.insetBy(dx: 24, dy: 0)
        let width = max(1, bounds.width - 48)
        centers = []
        var y: CGFloat = bounds.height / 2
        for (index, label) in labels.enumerated() {
            label.transform = .identity
            let height = ceil(label.sizeThatFits(CGSize(width: width, height: .greatestFiniteMagnitude)).height)
            if index == 0 { y -= height / 2 }
            label.frame = CGRect(x: 24, y: y, width: width, height: height)
            centers.append(label.frame.midY)
            y += height + settings.lyricGap
        }
        contentSize = CGSize(width: bounds.width, height: max(bounds.height, (centers.last ?? 0) + bounds.height / 2))
        needsPlacement = true
        if timed, let playback { render(playback.audio?.currentTime ?? playback.progress.position, now: CACurrentMediaTime(), manual: false) }
        else { contentOffset = .zero }
    }
    func frame(_ link: CADisplayLink) {
        guard let playback, !lines.isEmpty, centers.count == lines.count else { return }
        let position = playback.audio?.currentTime ?? playback.progress.position
        let now = CACurrentMediaTime()
        frameInterval = max(1.0 / 120, min(0.1, link.targetTimestamp - link.timestamp))
        let expected = playback.playing && lastFrameTime != 0 ? link.timestamp - lastFrameTime : 0
        let jumped = lastPosition >= 0 && (playback.seekGeneration != lastSeekGeneration || abs(position - lastPosition - expected) > 0.2)
        if jumped && !reduceMotion {
            manualUntil = 0
            transition = (now, contentOffset.y, displayedEmphasis)
        }
        let manual = isDragging || isDecelerating || now < manualUntil
        if !manual && wasManual && !reduceMotion { transition = (now, contentOffset.y, displayedEmphasis) }
        render(position, now: now, manual: manual)
        wasManual = manual
        let preferred: Float = playback.playing || manual || transition != nil ? (ProcessInfo.processInfo.isLowPowerModeEnabled ? 60 : 120) : 30
        if link.preferredFrameRateRange.preferred != preferred { link.preferredFrameRateRange = CAFrameRateRange(minimum: 30, maximum: 120, preferred: preferred) }
        lastPosition = position; lastFrameTime = link.timestamp; lastSeekGeneration = playback.seekGeneration
    }
    private func render(_ position: Double, now: CFTimeInterval, manual: Bool) {
        guard timed, centers.count == lines.count, !centers.isEmpty else { return }
        let motion = reduceMotion ? (index: Lyrics.current(lines, at: position), fraction: 0.0) : Lyrics.motion(lines, at: position, frameInterval: frameInterval)
        let index = max(0, min(motion.index, centers.count - 1))
        let next = min(index + 1, centers.count - 1)
        var target = centers[index] + (centers[next] - centers[index]) * CGFloat(motion.fraction) - bounds.height / 2
        var emphasis: [Int: CGFloat] = [:]
        if motion.index >= 0 {
            for row in Set([index, next]) {
                let value = CGFloat(Lyrics.emphasis(row, motion: motion))
                if value > 0 { emphasis[row] = value }
            }
        }
        if needsPlacement || reduceMotion { transition = nil }
        if let transition, !manual {
            let t = CGFloat(Lyrics.easeOut((now - transition.time) / 0.3))
            target = transition.offset + (target - transition.offset) * t
            for row in Set(transition.emphasis.keys).union(emphasis.keys) {
                emphasis[row] = (transition.emphasis[row] ?? 0) * (1 - t) + (emphasis[row] ?? 0) * t
            }
            if t >= 1 { self.transition = nil }
        }
        apply(emphasis)
        if !manual && (settings.dynamicLyrics || needsPlacement), abs(contentOffset.y - target) > 0.05 { contentOffset = CGPoint(x: 0, y: target) }
        needsPlacement = false
    }
    private func apply(_ emphasis: [Int: CGFloat], allRows: Bool = false) {
        let rows = allRows ? Set(labels.indices) : Set(displayedEmphasis.keys).union(emphasis.keys)
        let idle = inactiveColor()
        for row in rows where labels.indices.contains(row) {
            let weight = emphasis[row] ?? 0
            let label = labels[row]
            // Composite the idle color once, rather than fading a dynamic
            // secondaryLabel color whose own alpha differs from blended rows.
            label.alpha = 1
            label.transform = CGAffineTransform(scaleX: 1 + 0.05 * weight, y: 1 + 0.05 * weight)
            label.textColor = blend(idle, UIColor(Brand.accent(settings)), fraction: weight)
            label.accessibilityTraits = weight >= 0.5 ? [.staticText, .selected] : .staticText
        }
        displayedEmphasis = emphasis.filter { $0.value > 0 }
    }
    private func inactiveColor() -> UIColor {
        var r0: CGFloat = 0, g0: CGFloat = 0, b0: CGFloat = 0, a0: CGFloat = 0
        var r1: CGFloat = 0, g1: CGFloat = 0, b1: CGFloat = 0, a1: CGFloat = 0
        UIColor(Brand.muted).resolvedColor(with: traitCollection).getRed(&r0, green: &g0, blue: &b0, alpha: &a0)
        UIColor(Brand.paper).resolvedColor(with: traitCollection).getRed(&r1, green: &g1, blue: &b1, alpha: &a1)
        let alpha = a0 * 0.6
        return UIColor(red: r0 * alpha + r1 * (1 - alpha), green: g0 * alpha + g1 * (1 - alpha), blue: b0 * alpha + b1 * (1 - alpha), alpha: 1)
    }
    private func blend(_ from: UIColor, _ to: UIColor, fraction: CGFloat) -> UIColor {
        var r0: CGFloat = 0, g0: CGFloat = 0, b0: CGFloat = 0, a0: CGFloat = 0
        var r1: CGFloat = 0, g1: CGFloat = 0, b1: CGFloat = 0, a1: CGFloat = 0
        from.resolvedColor(with: traitCollection).getRed(&r0, green: &g0, blue: &b0, alpha: &a0)
        to.resolvedColor(with: traitCollection).getRed(&r1, green: &g1, blue: &b1, alpha: &a1)
        return UIColor(red: r0 + (r1 - r0) * fraction, green: g0 + (g1 - g0) * fraction, blue: b0 + (b1 - b0) * fraction, alpha: 1)
    }
    func scrollViewWillBeginDragging(_ scrollView: UIScrollView) { manualUntil = CACurrentMediaTime() + 1.5; transition = nil }
    func scrollViewDidEndDragging(_ scrollView: UIScrollView, willDecelerate decelerate: Bool) { manualUntil = CACurrentMediaTime() + 1.5 }
    func scrollViewDidEndDecelerating(_ scrollView: UIScrollView) { manualUntil = CACurrentMediaTime() + 1.5 }
}
