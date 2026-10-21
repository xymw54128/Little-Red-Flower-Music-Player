import SwiftUI
import UIKit

/// Native presentation surfaces follow the same paper and accent as the library.
struct FlowerPanel: ViewModifier {
    @EnvironmentObject private var library: MusicLibrary
    func body(content: Content) -> some View {
        content.presentationBackground(Brand.paper).presentationCornerRadius(28)
            .tint(Brand.accent(library.state.settings)).scrollContentBackground(.hidden)
            .toolbarBackground(Brand.paper, for: .navigationBar)
    }
}

extension View {
    func flowerPanel() -> some View { modifier(FlowerPanel()) }
}

struct FavoriteBloomButton: View {
    var isOn: Bool
    var deferRemoval = false
    var onDeparture: () -> Void = {}
    var action: () -> Void
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var localValue: Bool?
    @State private var burst: Date?
    @State private var pending = false
    @State private var pendingTask: Task<Void, Never>?
    @State private var pendingAction: (() -> Void)?
    @State private var pulse = 0
    private var selected: Bool { localValue ?? isOn }
    var body: some View {
        Button {
            guard !pending else { return }
            let removing = selected
            localValue = !removing; pulse += 1
            burst = reduceMotion ? nil : Date()
            if removing && deferRemoval && !reduceMotion {
                pending = true
                pendingAction = action
                // Keep the row alive for the heart flourish, then collapse it,
                // and only then remove its membership from the source model.
                pendingTask = Task { @MainActor in
                    do {
                        try await Task.sleep(for: .milliseconds(240))
                        guard !Task.isCancelled else { return }
                        onDeparture()
                        try await Task.sleep(for: .milliseconds(220))
                        guard !Task.isCancelled else { return }
                        finishPending()
                    } catch { }
                }
            } else { action(); localValue = nil }
        } label: {
            Image(systemName: selected ? "heart.fill" : "heart")
                .font(.system(size: 21, weight: .regular))
                .foregroundStyle(selected ? Brand.red : Brand.muted.opacity(0.55))
                .symbolEffect(.bounce, value: pulse)
                .frame(width: 44, height: 44)
                .overlay {
                    TimelineView(.animation(minimumInterval: 1.0 / 60, paused: burst == nil || reduceMotion)) { tick in
                        let t = min(1, max(0, burst.map { tick.date.timeIntervalSince($0) / 0.42 } ?? 1))
                        Canvas { context, size in
                            guard t < 1 else { return }
                            let radius = 10 + 13 * (1 - pow(1 - t, 3))
                            for index in 0..<7 {
                                let angle = Double(index) * .pi * 2 / 7 - .pi / 2
                                let side = 3.8 * (1 - t)
                                let center = CGPoint(x: size.width / 2 + cos(angle) * radius,
                                                     y: size.height / 2 + sin(angle) * radius)
                                context.fill(Path(ellipseIn: CGRect(x: center.x - side / 2, y: center.y - side / 2, width: side, height: side)),
                                             with: .color((index % 2 == 0 ? Brand.red : Brand.gold).opacity(1 - t)))
                            }
                        }
                    }.allowsHitTesting(false).accessibilityHidden(true)
                }
        }.buttonStyle(.plain).accessibilityLabel(selected ? "取消喜欢" : "喜欢这首歌")
            .sensoryFeedback(.selection, trigger: pulse)
            .task(id: burst) {
                guard burst != nil else { return }
                do { try await Task.sleep(for: .milliseconds(450)); burst = nil } catch { }
            }
            .onChange(of: isOn) { _, _ in if !pending { localValue = nil } }
            .onDisappear {
                // Commit the captured intent once when its row goes offscreen;
                // never leave a delayed toggle running after the view is gone.
                pendingTask?.cancel()
                finishPending()
            }
    }
    private func finishPending() {
        let completion = pendingAction
        pendingAction = nil; pendingTask = nil; pending = false; localValue = nil
        completion?()
    }
}

/// Only these seven tiny shape layers receive frame updates. Album artwork,
/// labels and navigation never rebuild on the animation clock.
struct RainbowFlower: UIViewRepresentable {
    var playing: Bool
    var orbit = false
    var active = true
    @Environment(\.scenePhase) private var phase
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    func makeUIView(context: Context) -> FlowerMotionView { FlowerMotionView() }
    func updateUIView(_ view: FlowerMotionView, context: Context) {
        view.configure(playing: playing, orbit: orbit, active: active && phase == .active, reduceMotion: reduceMotion)
    }
    static func dismantleUIView(_ view: FlowerMotionView, coordinator: ()) { view.stop() }
}

@MainActor private final class FlowerMotionTarget: NSObject {
    weak var owner: FlowerMotionView?
    @objc func frame(_ link: CADisplayLink) { owner?.frame(link) }
}

final class FlowerMotionView: UIView {
    private let target = FlowerMotionTarget()
    private let rotor = CALayer()
    private let colors: [UIColor] = [UIColor(red: 0.90, green: 0.31, blue: 0.39, alpha: 1), UIColor(red: 0.98, green: 0.59, blue: 0.29, alpha: 1), UIColor(red: 0.96, green: 0.78, blue: 0.28, alpha: 1), UIColor(red: 0.46, green: 0.70, blue: 0.39, alpha: 1), UIColor(red: 0.27, green: 0.66, blue: 0.70, alpha: 1), UIColor(red: 0.36, green: 0.53, blue: 0.82, alpha: 1), UIColor(red: 0.68, green: 0.42, blue: 0.73, alpha: 1)]
    private var petals: [CAShapeLayer] = []
    private let centerLayer = CAShapeLayer()
    private var link: CADisplayLink?
    private var playing = false
    private var active = true
    private var reduceMotion = false
    private var orbit = false
    private var angle = 0.0
    private var beat = 0.0
    private var velocity = 0.0
    private var previousTime = 0.0
    override init(frame: CGRect) {
        super.init(frame: frame)
        isUserInteractionEnabled = false; isAccessibilityElement = false
        layer.addSublayer(rotor); target.owner = self
        for color in colors {
            let petal = CAShapeLayer(); petal.fillColor = color.cgColor
            rotor.addSublayer(petal); petals.append(petal)
        }
        centerLayer.fillColor = UIColor(red: 0.98, green: 0.82, blue: 0.33, alpha: 1).cgColor
        rotor.addSublayer(centerLayer)
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) is not supported") }
    func configure(playing: Bool, orbit: Bool, active: Bool, reduceMotion: Bool) {
        let changedShape = self.orbit != orbit
        self.playing = playing; self.orbit = orbit; self.active = active; self.reduceMotion = reduceMotion
        if changedShape { setNeedsLayout() }
        if !active || reduceMotion { stop(); velocity = 0; updateLayers() }
        else if playing || velocity > 0.002 { start() }
    }
    override func layoutSubviews() {
        super.layoutSubviews()
        CATransaction.begin(); CATransaction.setDisableActions(true)
        rotor.bounds = bounds; rotor.position = CGPoint(x: bounds.midX, y: bounds.midY)
        let side = min(bounds.width, bounds.height)
        for petal in petals {
            let width = side * (orbit ? 0.285 : 0.31)
            let height = side * (orbit ? 0.32 : 0.47)
            petal.bounds = CGRect(x: 0, y: 0, width: width, height: height)
            petal.path = UIBezierPath(ovalIn: petal.bounds).cgPath
        }
        let diameter = orbit ? 0 : side * 0.22
        centerLayer.path = UIBezierPath(ovalIn: CGRect(x: bounds.midX - diameter / 2, y: bounds.midY - diameter / 2, width: diameter, height: diameter)).cgPath
        CATransaction.commit(); updateLayers()
    }
    private func start() {
        guard window != nil, link == nil else { return }
        previousTime = 0
        let next = CADisplayLink(target: target, selector: #selector(FlowerMotionTarget.frame(_:)))
        next.preferredFrameRateRange = CAFrameRateRange(minimum: 30, maximum: 60, preferred: 60)
        next.add(to: .main, forMode: .common); link = next
    }
    func stop() { link?.invalidate(); link = nil; previousTime = 0 }
    func frame(_ link: CADisplayLink) {
        let dt = previousTime == 0 ? 1.0 / 60 : min(0.05, max(0, link.timestamp - previousTime))
        previousTime = link.timestamp
        let targetSpeed = playing ? (orbit ? 0.34 : 0.52) : 0.0
        velocity += (targetSpeed - velocity) * (1 - exp(-dt / (playing ? 0.22 : 0.4)))
        angle = (angle + velocity * dt).truncatingRemainder(dividingBy: .pi * 2)
        beat = (beat + velocity * dt * 9).truncatingRemainder(dividingBy: .pi * 2)
        updateLayers()
        if !playing && velocity < 0.002 { velocity = 0; stop(); updateLayers() }
    }
    private func updateLayers() {
        CATransaction.begin(); CATransaction.setDisableActions(true)
        let side = min(bounds.width, bounds.height)
        rotor.setAffineTransform(CGAffineTransform(rotationAngle: angle))
        for (index, petal) in petals.enumerated() {
            let direction = Double(index) * .pi * 2 / 7 - .pi / 2
            let pulse = orbit && !reduceMotion ? pow(max(0, sin(beat - Double(index) * .pi * 2 / 7)), 5) * min(1, velocity / 0.34) : 0
            let radius = side * (orbit ? 0.32 : 0.22) + pulse * side * 0.055
            petal.position = CGPoint(x: bounds.midX + cos(direction) * radius, y: bounds.midY + sin(direction) * radius)
            petal.setAffineTransform(CGAffineTransform(rotationAngle: direction + .pi / 2).scaledBy(x: 1 + pulse * 0.08, y: 1 + pulse * 0.16))
        }
        CATransaction.commit()
    }
    override func didMoveToWindow() {
        super.didMoveToWindow()
        if window == nil { stop() }
        else if active && !reduceMotion && playing { start() }
    }
}

struct MiniProgressRing: View {
    @ObservedObject var progress: PlaybackProgress
    var color: Color
    var body: some View {
        let value = progress.duration > 0 ? min(1, max(0, progress.position / progress.duration)) : 0
        ZStack {
            Circle().strokeBorder(color.opacity(0.18), lineWidth: 2)
            Circle().trim(from: 0, to: value).stroke(color, style: StrokeStyle(lineWidth: 2.2, lineCap: .round)).rotationEffect(.degrees(-90))
        }.accessibilityHidden(true)
    }
}

struct FiveLeafBranch: View {
    var body: some View {
        Canvas { context, size in
            func point(_ x: CGFloat, _ y: CGFloat) -> CGPoint { CGPoint(x: x * size.width, y: y * size.height) }
            let start = point(0.02, 0.93), first = point(0.36, 1.06)
            let second = point(0.83, 0.98), end = point(0.99, 0.64)
            var stem = Path(); stem.move(to: start)
            stem.addCurve(to: end, control1: first, control2: second)
            context.stroke(stem, with: .color(Color(hex: 0x6E8E5E).opacity(0.65)), lineWidth: 0.9)
            let leaves: [(CGFloat, Double, CGFloat)] = [(0.14, -30, 0.12), (0.34, -145, 0.14), (0.53, -33, 0.14), (0.72, -145, 0.13), (0.90, -55, 0.10)]
            for (index, leaf) in leaves.enumerated() {
                // Anchor each leaf to the same cubic stem, rather than placing
                // disconnected leaves near it. The shallow growth stays below controls.
                let t = leaf.0, u = 1 - t
                let x = u * u * u * start.x + 3 * u * u * t * first.x + 3 * u * t * t * second.x + t * t * t * end.x
                let y = u * u * u * start.y + 3 * u * u * t * first.y + 3 * u * t * t * second.y + t * t * t * end.y
                var drawing = context; drawing.translateBy(x: x, y: y); drawing.rotate(by: .degrees(leaf.1))
                let length = leaf.2 * size.width, width = length * 0.37
                var path = Path(); path.move(to: .zero)
                path.addQuadCurve(to: CGPoint(x: length, y: 0), control: CGPoint(x: length * 0.43, y: -width))
                path.addQuadCurve(to: .zero, control: CGPoint(x: length * 0.55, y: width * 0.75))
                drawing.fill(path, with: .color(Color(hex: index % 2 == 0 ? 0x8EAC74 : 0x739863).opacity(0.8)))
            }
        }.allowsHitTesting(false).accessibilityHidden(true)
    }
}

/// Small complete flower shapes echo the app's rounded five-petal mark.
private func drawBlossom(_ context: GraphicsContext, center: CGPoint, radius: CGFloat, color: Color, petals: Int = 5) {
    for index in 0..<petals {
        var petal = context
        let a = Double(index) * .pi * 2 / Double(petals) - .pi / 2
        petal.translateBy(x: center.x + cos(a) * radius * 0.54, y: center.y + sin(a) * radius * 0.54)
        petal.rotate(by: .radians(a + .pi / 2))
        petal.fill(Path(ellipseIn: CGRect(x: -radius * 0.43, y: -radius * 0.62, width: radius * 0.86, height: radius * 1.24)), with: .color(color))
    }
    context.fill(Path(ellipseIn: CGRect(x: center.x - radius * 0.25, y: center.y - radius * 0.25, width: radius * 0.5, height: radius * 0.5)), with: .color(Brand.gold))
}

struct TitleFlowerSprig: View {
    var right = false
    var body: some View {
        Canvas { context, size in
            if right { context.translateBy(x: size.width, y: size.height); context.rotate(by: .degrees(180)) }
            func p(_ x: CGFloat, _ y: CGFloat) -> CGPoint { CGPoint(x: size.width * x, y: size.height * y) }
            var stem = Path(); stem.move(to: p(0.98, 0.65))
            stem.addQuadCurve(to: p(0.16, 0.30), control: p(0.42, 0.80))
            context.stroke(stem, with: .color(Color(hex: 0x6B9064)), style: StrokeStyle(lineWidth: 1.1, lineCap: .round))
            for (x, y, turn) in [(0.50, 0.57, -0.6), (0.70, 0.66, 2.4)] {
                var leaf = context; leaf.translateBy(x: size.width * x, y: size.height * y); leaf.rotate(by: .radians(turn))
                var shape = Path(); shape.move(to: .zero); shape.addQuadCurve(to: CGPoint(x: 11, y: 0), control: CGPoint(x: 6, y: -7)); shape.addQuadCurve(to: .zero, control: CGPoint(x: 7, y: 4))
                leaf.fill(shape, with: .color(Color(hex: 0x82A56D)))
            }
            drawBlossom(context, center: p(0.19, 0.27), radius: 6, color: right ? Color(hex: 0x668DD5) : Brand.red)
            drawBlossom(context, center: p(0.48, 0.38), radius: 4.4, color: right ? Color(hex: 0xA15DCC) : Color(hex: 0xEEAF38), petals: right ? 6 : 5)
            if right { drawBlossom(context, center: p(0.71, 0.52), radius: 3.4, color: Color(hex: 0xE16B91)) }
        }.allowsHitTesting(false).accessibilityHidden(true)
    }
}

struct BotanicalCoverFrame: View {
    var body: some View {
        GeometryReader { geometry in
            let side = min(geometry.size.width, geometry.size.height)
            ZStack {
                coverCorner.frame(width: side * 0.32, height: side * 0.27)
                    .position(x: side * 0.12, y: side * 0.92)
                coverCorner.rotationEffect(.degrees(180)).frame(width: side * 0.24, height: side * 0.22)
                    .position(x: side * 0.91, y: side * 0.08)
            }
        }.allowsHitTesting(false).accessibilityHidden(true)
    }
    private var coverCorner: some View {
        Canvas { context, size in
            func p(_ x: CGFloat, _ y: CGFloat) -> CGPoint { CGPoint(x: size.width * x, y: size.height * y) }
            var stem = Path(); stem.move(to: p(0.92, 0.76)); stem.addCurve(to: p(0.17, 0.16), control1: p(0.50, 0.93), control2: p(0.14, 0.80))
            context.stroke(stem, with: .color(Color(hex: 0x7C9465)), style: StrokeStyle(lineWidth: 1.1, lineCap: .round))
            for (x, y, a) in [(0.70, 0.78, -0.5), (0.31, 0.59, 2.3), (0.23, 0.31, -1.6)] {
                var leaf = context; leaf.translateBy(x: size.width * x, y: size.height * y); leaf.rotate(by: .radians(a))
                var shape = Path(); shape.move(to: .zero); shape.addQuadCurve(to: CGPoint(x: 13, y: 0), control: CGPoint(x: 7, y: -8)); shape.addQuadCurve(to: .zero, control: CGPoint(x: 7, y: 4))
                leaf.fill(shape, with: .color(Color(hex: 0x87A571)))
            }
            let radius = max(3.5, size.width * 0.07)
            drawBlossom(context, center: p(0.18, 0.22), radius: radius, color: Color(hex: 0x659AD0))
            drawBlossom(context, center: p(0.35, 0.58), radius: radius * 0.8, color: Color(hex: 0xAA72BB), petals: 6)
            drawBlossom(context, center: p(0.56, 0.74), radius: radius * 1.1, color: Color(hex: 0xFFF0CE))
            drawBlossom(context, center: p(0.78, 0.66), radius: radius * 0.65, color: Color(hex: 0xEFBC42), petals: 6)
            drawBlossom(context, center: p(0.21, 0.73), radius: radius * 0.58, color: Brand.red)
        }
    }
}
