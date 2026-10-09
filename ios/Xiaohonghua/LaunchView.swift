import SwiftUI

struct LaunchView: View {
    var target: CGRect
    var active: Bool
    var finish: () -> Void
    @State private var started = Date()
    @State private var elapsed = 0.0
    @State private var pauseDate: Date?

    var body: some View {
        GeometryReader { geometry in
            TimelineView(.animation(minimumInterval: 1.0 / 120, paused: !active)) { timeline in
                let time = elapsed + (pauseDate == nil ? timeline.date.timeIntervalSince(started) : 0)
                let frame = geometry.frame(in: .global)
                let destination = target == .zero ? CGPoint(x: 39, y: geometry.safeAreaInsets.top + 42) : CGPoint(x: target.midX - frame.minX, y: target.midY - frame.minY)
                let start = CGPoint(x: geometry.size.width / 2, y: geometry.size.height / 2)
                let flight = LaunchMotion.flight(time)
                let position = curved(start: start, end: destination, size: geometry.size, t: flight)
                let size = 200 + ((target == .zero ? 30 : target.width) - 200) * flight
                ZStack {
                    Brand.paper.opacity(LaunchMotion.background(time)).ignoresSafeArea()
                    Text("小红花 · 音乐播放器")
                        .font(.custom(Brand.fontNames["kai"]!, size: 24)).italic().tracking(1.2)
                        .foregroundStyle(Brand.red).opacity(LaunchMotion.caption(time))
                        .position(x: start.x, y: start.y + 124)
                    Canvas { context, canvasSize in
                        context.translateBy(x: canvasSize.width / 2, y: canvasSize.height / 2)
                        context.rotate(by: .degrees(LaunchMotion.rotation(time)))
                        let rect = CGRect(x: -100, y: -100, width: 200, height: 200)
                        if time >= 1.080 { context.draw(Image("Flower"), in: rect) }
                        else {
                            for index in 0..<5 {
                                var petal = context
                                let growth = LaunchMotion.petal(index, at: time)
                                petal.scaleBy(x: growth, y: growth)
                                var sector = Path()
                                sector.move(to: .zero)
                                sector.addArc(center: .zero, radius: 200, startAngle: .degrees(-126 + Double(index) * 72), endAngle: .degrees(-54 + Double(index) * 72), clockwise: false)
                                sector.closeSubpath()
                                petal.clip(to: sector)
                                petal.draw(Image("Flower"), in: rect)
                            }
                            // Asset viewBox is 72 units with a 7.5-unit radius core.
                            let radius = 200 * 7.5 / 72
                            context.fill(Path(ellipseIn: CGRect(x: -radius, y: -radius, width: radius * 2, height: radius * 2)), with: .color(Brand.gold))
                        }
                    }.frame(width: 200, height: 200).scaleEffect(size / 200).position(start)
                        .offset(x: position.x - start.x, y: position.y - start.y)
                }.onChange(of: time) { _, value in if value >= LaunchMotion.duration { finish() } }
            }
        }.ignoresSafeArea().allowsHitTesting(true).accessibilityHidden(true)
            .onChange(of: active) { _, value in
                if value { started = Date(); pauseDate = nil }
                else { elapsed += Date().timeIntervalSince(started); pauseDate = Date() }
            }
    }
    private func curved(start: CGPoint, end: CGPoint, size: CGSize, t: Double) -> CGPoint {
        let u = 1 - t
        let p1 = CGPoint(x: start.x + size.width * 0.23, y: start.y - size.height * 0.18)
        let p2 = CGPoint(x: end.x + size.width * 0.20, y: end.y + size.height * 0.10)
        return CGPoint(x: u*u*u*start.x + 3*u*u*t*p1.x + 3*u*t*t*p2.x + t*t*t*end.x,
                       y: u*u*u*start.y + 3*u*u*t*p1.y + 3*u*t*t*p2.y + t*t*t*end.y)
    }
}
