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

final class LyricScrollView: UIScrollView, UIScrollViewDelegate {
    private var text = ""
    private var settings = MusicSettings()
    private var lines: [LyricLine] = []
    private var timed = true
    private var labels: [UILabel] = []
    private var centers: [CGFloat] = []
    private var link: CADisplayLink?
    private let frameTarget = LyricFrameTarget()
    private weak var playback: Playback?
    private var reduceMotion = false
    private var highlighted = -2
    private var lastSize = CGSize.zero
    private var manualUntil: CFTimeInterval = 0
    private var returning: (time: CFTimeInterval, offset: CGFloat)?
    private var lastPosition = -1.0
    private var lastFrameTime = 0.0
    private var frameInterval = 1.0 / 60
    private var wasManual = false
    private let emptyLabel = UILabel()

    override init(frame: CGRect) {
        super.init(frame: frame)
        delegate = self
        showsVerticalScrollIndicator = false
        contentInsetAdjustmentBehavior = .never
        alwaysBounceVertical = true
        isDirectionalLockEnabled = true
        frameTarget.view = self
        emptyLabel.text = "暂无歌词\n可从播放页菜单导入 LRC"
        emptyLabel.numberOfLines = 0; emptyLabel.textAlignment = .center
        emptyLabel.textColor = UIColor(Brand.muted); emptyLabel.font = .systemFont(ofSize: 17)
        addSubview(emptyLabel)
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) is not supported") }

    func configure(text: String, settings: MusicSettings, playback: Playback, active: Bool, reduceMotion: Bool) {
        self.playback = playback; self.reduceMotion = reduceMotion
        let layoutChanged = self.text != text || self.settings.lyricFont != settings.lyricFont || self.settings.lyricSize != settings.lyricSize || self.settings.lyricGap != settings.lyricGap
        let colorChanged = self.settings.theme != settings.theme
        self.settings = settings
        if layoutChanged {
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
                label.textColor = UIColor(Brand.muted)
                label.isAccessibilityElement = true
                addSubview(label)
                return label
            }
            emptyLabel.isHidden = !lines.isEmpty
            lastSize = .zero; highlighted = -2; manualUntil = 0; returning = nil; lastPosition = -1
            setNeedsLayout()
        } else if colorChanged { highlighted = -2 }
        if active, timed, link == nil {
            let link = CADisplayLink(target: frameTarget, selector: #selector(LyricFrameTarget.frame(_:)))
            link.preferredFrameRateRange = CAFrameRateRange(minimum: 30, maximum: 120, preferred: ProcessInfo.processInfo.isLowPowerModeEnabled ? 60 : 120)
            link.add(to: .main, forMode: .common)
            self.link = link
        } else if !active || !timed { stop() }
    }
    func stop() { link?.invalidate(); link = nil; lastFrameTime = 0 }

    override func layoutSubviews() {
        super.layoutSubviews()
        guard bounds.size != lastSize, bounds.width > 0, bounds.height > 0 else { return }
        lastSize = bounds.size
        emptyLabel.frame = bounds.insetBy(dx: 24, dy: 0)
        let width = max(1, bounds.width - 48)
        centers = []
        var y: CGFloat = bounds.height / 2
        for (index, label) in labels.enumerated() {
            let height = ceil(label.sizeThatFits(CGSize(width: width, height: .greatestFiniteMagnitude)).height)
            if index == 0 { y -= height / 2 }
            label.frame = CGRect(x: 24, y: y, width: width, height: height)
            centers.append(label.frame.midY)
            y += height + settings.lyricGap
        }
        contentSize = CGSize(width: bounds.width, height: max(bounds.height, (centers.last ?? 0) + bounds.height / 2))
        if timed, let playback { follow(playback.audio?.currentTime ?? playback.progress.position, instant: true) }
        else { contentOffset = .zero }
    }
    func frame(_ link: CADisplayLink) {
        guard let playback, !lines.isEmpty, centers.count == lines.count else { return }
        let position = playback.audio?.currentTime ?? playback.progress.position
        frameInterval = max(1.0 / 120, min(0.1, link.targetTimestamp - link.timestamp))
        let current = Lyrics.current(lines, at: position)
        if current != highlighted {
            let previous = highlighted
            highlighted = current
            for index in Set([previous, current]).filter({ labels.indices.contains($0) }) {
                let label = labels[index]
                label.textColor = UIColor(index == current ? Brand.accent(settings) : Brand.muted)
                label.accessibilityTraits = index == current ? [.staticText, .selected] : .staticText
                if !reduceMotion {
                    UIView.animate(withDuration: 0.12, delay: 0, options: [.beginFromCurrentState, .allowUserInteraction]) { label.alpha = index == current ? 1 : 0.65 }
                } else { label.alpha = index == current ? 1 : 0.65 }
            }
            if previous == -2 {
                for index in labels.indices {
                    labels[index].textColor = UIColor(index == current ? Brand.accent(settings) : Brand.muted)
                    labels[index].alpha = index == current ? 1 : 0.65
                }
            }
        }
        let now = CACurrentMediaTime()
        let manual = isDragging || isDecelerating || now < manualUntil
        if !manual, settings.dynamicLyrics {
            if wasManual { returning = (now, contentOffset.y) }
            let frameGap = lastFrameTime == 0 ? 0 : link.timestamp - lastFrameTime
            let expected = playback.playing ? frameGap : 0
            let jumped = lastPosition < 0 || abs(position - lastPosition - expected) > 0.2
            if jumped { returning = nil }
            follow(position, instant: jumped || reduceMotion)
        }
        wasManual = manual
        let preferred: Float = playback.playing || isDragging || isDecelerating || returning != nil ? (ProcessInfo.processInfo.isLowPowerModeEnabled ? 60 : 120) : 30
        if link.preferredFrameRateRange.preferred != preferred { link.preferredFrameRateRange = CAFrameRateRange(minimum: 30, maximum: 120, preferred: preferred) }
        lastPosition = position; lastFrameTime = link.timestamp
    }
    private func follow(_ position: Double, instant: Bool) {
        guard !centers.isEmpty else { return }
        let motion = Lyrics.motion(lines, at: position, frameInterval: frameInterval)
        let index = max(0, min(motion.index, centers.count - 1))
        let next = min(index + 1, centers.count - 1)
        let fraction = reduceMotion ? 0 : motion.fraction
        var target = centers[index] + (centers[next] - centers[index]) * fraction - bounds.height / 2
        if !instant, let returning {
            let t = min(max((CACurrentMediaTime() - returning.time) / 0.35, 0), 1)
            target = returning.offset + (target - returning.offset) * t * t * (3 - 2 * t)
            if t >= 1 { self.returning = nil }
        }
        if abs(contentOffset.y - target) > 0.05 { contentOffset = CGPoint(x: 0, y: target) }
    }
    func scrollViewWillBeginDragging(_ scrollView: UIScrollView) { manualUntil = CACurrentMediaTime() + 4; returning = nil }
    func scrollViewDidEndDragging(_ scrollView: UIScrollView, willDecelerate decelerate: Bool) { manualUntil = CACurrentMediaTime() + 4 }
    func scrollViewDidEndDecelerating(_ scrollView: UIScrollView) { manualUntil = CACurrentMediaTime() + 4 }
}
