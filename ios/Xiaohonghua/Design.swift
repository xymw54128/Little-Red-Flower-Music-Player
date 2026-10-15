import SwiftUI
import UIKit
import ImageIO

enum Brand {
    static let red = Color(hex: 0xCE3C4C)
    static let gold = Color(hex: 0xF6D88C)
    static let paper = Color(uiColor: UIColor { $0.userInterfaceStyle == .dark ? UIColor(red: 0.09, green: 0.10, blue: 0.095, alpha: 1) : UIColor(red: 0.973, green: 0.973, blue: 0.965, alpha: 1) })
    static let ink = Color(uiColor: .label)
    static let muted = Color(uiColor: .secondaryLabel)
    static let themes: [(String, UInt)] = [("花红", 0xCE3C4C), ("森林", 0x527568), ("海蓝", 0x436B92), ("陶土", 0x98644E), ("暮紫", 0x76668D)]
    static func accent(_ settings: MusicSettings) -> Color { Color(hex: themes[min(max(settings.theme, 0), themes.count - 1)].1) }
    static let fonts: [(String, String)] = [("system", "系统字体"), ("kai", "楷体 · 文楷"), ("song", "宋体"), ("xiaowei", "小薇宋意"), ("zenmaru", "禅丸圆体"), ("kiwi", "奇异果圆体"), ("kurenaido", "红道手写"), ("longcang", "龙藏手书")]
    // Read from the bundled fonts' PostScript name table.
    static let fontNames = ["kai": "LXGWWenKai-Regular", "song": "LittleFlowerSerif-Regular", "xiaowei": "LittleFlowerLightSerif-Regular", "zenmaru": "LittleFlowerGentleRound-Regular", "kiwi": "LittleFlowerRoundSerif-Regular", "kurenaido": "LittleFlowerDiaryHand-Regular", "longcang": "LittleFlowerFreeHand-Regular"]
    static func font(_ name: String, size: CGFloat) -> UIFont {
        if let face = fontNames[name], let custom = UIFont(name: face, size: size) {
            // The display faces intentionally stay byte-identical to Android.
            // Missing punctuation (such as U+00B7) falls back to bundled WenKai.
            if name == "kai" { return custom }
            let descriptor = custom.fontDescriptor.addingAttributes([
                .cascadeList: [UIFontDescriptor(name: "LXGWWenKai-Regular", size: size)]
            ])
            return UIFont(descriptor: descriptor, size: size)
        }
        if name == "mono" { return UIFont.monospacedSystemFont(ofSize: size, weight: .regular) }
        let normal = UIFont.systemFont(ofSize: size)
        if name == "serif", let descriptor = normal.fontDescriptor.withDesign(.serif) { return UIFont(descriptor: descriptor, size: size) }
        return normal
    }
}

extension Color {
    init(hex: UInt) { self.init(red: Double((hex >> 16) & 255) / 255, green: Double((hex >> 8) & 255) / 255, blue: Double(hex & 255) / 255) }
}

private final class ArtworkCancellation {
    private let lock = NSLock()
    private var cancelled = false
    func cancel() { lock.lock(); cancelled = true; lock.unlock() }
    var isCancelled: Bool { lock.lock(); defer { lock.unlock() }; return cancelled }
}

final class ImageCache {
    static let shared = ImageCache()
    private let cache = NSCache<NSString, UIImage>()
    private let decoder: OperationQueue = {
        let queue = OperationQueue()
        queue.maxConcurrentOperationCount = 2
        queue.qualityOfService = .userInitiated
        return queue
    }()
    init() { cache.totalCostLimit = 24 * 1024 * 1024; cache.countLimit = 160 }
    func loadedImage(_ name: String, pixels: Int = 256) async -> UIImage? {
        guard !name.isEmpty else { return nil }
        let size = min(max(pixels, 64), 2048)
        let key = (name + "@" + String(size)) as NSString
        if let image = cache.object(forKey: key) { return image }
        let request = ArtworkCancellation()
        return await withTaskCancellationHandler {
            await withCheckedContinuation { continuation in
                decoder.addOperation { [self] in
                    // Do not cancel the Operation itself: its block must run once
                    // to resume this continuation, even when the row is offscreen.
                    guard !request.isCancelled else { continuation.resume(returning: nil); return }
                    if let image = cache.object(forKey: key) { continuation.resume(returning: image); return }
                    guard let url = AppFiles.image(name), let source = CGImageSourceCreateWithURL(url as CFURL, nil),
                          let cg = CGImageSourceCreateThumbnailAtIndex(source, 0, [kCGImageSourceCreateThumbnailFromImageAlways: true,
                            kCGImageSourceThumbnailMaxPixelSize: size, kCGImageSourceCreateThumbnailWithTransform: true,
                            kCGImageSourceShouldCacheImmediately: true] as CFDictionary) else { continuation.resume(returning: nil); return }
                    let image = UIImage(cgImage: cg)
                    cache.setObject(image, forKey: key, cost: cg.bytesPerRow * cg.height)
                    continuation.resume(returning: image)
                }
            }
        } onCancel: { request.cancel() }
    }
}

struct Artwork: View {
    var name: String
    var radius: CGFloat = 16
    var pixels: Int = 256
    @State private var image: UIImage?
    @State private var loadedKey = ""
    private var requestKey: String { name + "@" + String(pixels) }
    var body: some View {
        Group {
            if loadedKey == requestKey, let image { Image(uiImage: image).resizable().scaledToFill() }
            else { ZStack { Brand.red.opacity(0.07); Image("Flower").resizable().scaledToFit().padding(12) } }
        }.clipped().clipShape(RoundedRectangle(cornerRadius: radius, style: .continuous))
            .accessibilityHidden(true)
            .task(id: requestKey) {
                let key = requestKey
                let result = await ImageCache.shared.loadedImage(name, pixels: pixels)
                guard !Task.isCancelled else { return }
                image = result; loadedKey = key
            }
    }
}

struct FlowerHeader: View {
    @EnvironmentObject private var library: MusicLibrary
    var title: String
    var icon = "Flower"
    var hiddenFlower = false
    var searchText: Binding<String>?
    var action: (() -> Void)?
    @State private var searching = false
    @FocusState private var focused: Bool
    var body: some View {
        HStack(spacing: 12) {
            HeaderFlower(icon: icon).frame(width: 30, height: 30).opacity(hiddenFlower ? 0 : 1)
                .background(GeometryReader { geometry in
                    Color.clear.preference(key: FlowerFrameKey.self, value: icon == "Flower" ? geometry.frame(in: .global) : .zero)
                })
            if searching, let searchText {
                HStack(spacing: 8) {
                    Image(systemName: "magnifyingglass").foregroundStyle(Brand.muted)
                    TextField("搜索歌曲、歌手或专辑", text: searchText).font(.system(size: 15))
                        .focused($focused).autocorrectionDisabled().submitLabel(.search)
                    Button { searchText.wrappedValue = ""; withAnimation(.easeOut(duration: 0.25)) { searching = false }; focused = false } label: { Image(systemName: "xmark.circle.fill").foregroundStyle(Brand.muted) }.accessibilityLabel("收起搜索")
                }.padding(12).background(Brand.ink.opacity(0.05), in: RoundedRectangle(cornerRadius: 14))
                    .transition(.opacity.combined(with: .move(edge: .trailing)))
            } else {
                Text(title).font(.system(size: 29, weight: .semibold)).foregroundStyle(Brand.ink)
                Spacer()
                if searchText != nil {
                    Button { withAnimation(.easeOut(duration: 0.25)) { searching = true }; focused = true } label: { Image(systemName: "magnifyingglass").font(.system(size: 22)).frame(width: 44, height: 44) }.accessibilityLabel("搜索音乐")
                } else if let action {
                    Button(action: action) { Image(systemName: "plus").font(.system(size: 24)).frame(width: 44, height: 44) }.accessibilityLabel("创建歌单")
                }
            }
        }.padding(.horizontal, 24).padding(.top, 12).padding(.bottom, 16)
            .tint(Brand.accent(library.state.settings))
    }
}

/// A fresh clock on every tap cancels the previous two-second flourish.
struct HeaderFlower: View {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    var icon: String = "Flower"
    var hidden = false
    @State private var started: Date?
    var body: some View {
        Button { started = Date() } label: {
            TimelineView(.animation(minimumInterval: 1.0 / 60, paused: started == nil || reduceMotion)) { timeline in
                let t = reduceMotion ? 2 : min(2, max(0, started.map { timeline.date.timeIntervalSince($0) } ?? 2))
                Canvas { context, size in
                    let center = CGPoint(x: size.width / 2, y: size.height / 2)
                    context.translateBy(x: center.x, y: center.y)
                    let rect = CGRect(x: -center.x, y: -center.y, width: size.width, height: size.height)
                    if icon == "Flower" {
                        let progress = t / 2
                        context.rotate(by: .degrees(1440 * (1 - pow(1 - progress, 3))))
                        context.draw(Image(icon), in: rect)
                    } else if icon == "FlowerPlaylists", t < 2 {
                        let radius = size.width * 0.105
                        context.fill(Path(ellipseIn: CGRect(x: -radius, y: -radius, width: radius * 2, height: radius * 2)), with: .color(Brand.gold))
                        var sector = Path(); sector.move(to: .zero)
                        sector.addArc(center: .zero, radius: size.width, startAngle: .degrees(-90), endAngle: .degrees(-90 + 360 * min(1, max(0, (t - 0.2) / 1.7))), clockwise: false)
                        sector.closeSubpath(); context.clip(to: sector); context.draw(Image(icon), in: rect)
                    } else if icon == "FlowerSettings", t < 2 {
                        for index in 0..<5 {
                            var petal = context
                            let angle = Double(index) * 72 - 90
                            let outward = sin(min(1, t / 0.35) * .pi / 2)
                            let home = min(1, max(0, (t - 0.5 - Double(index) * 0.19) / 0.65))
                            let distance = 7 * outward * pow(1 - home, 3)
                            petal.translateBy(x: cos(angle * .pi / 180) * distance, y: sin(angle * .pi / 180) * distance)
                            var sector = Path(); sector.move(to: .zero)
                            sector.addArc(center: .zero, radius: size.width, startAngle: .degrees(angle - 36), endAngle: .degrees(angle + 36), clockwise: false)
                            sector.closeSubpath(); petal.clip(to: sector); petal.draw(Image(icon), in: rect)
                        }
                        let radius = size.width * 0.105
                        context.fill(Path(ellipseIn: CGRect(x: -radius, y: -radius, width: radius * 2, height: radius * 2)), with: .color(Brand.gold))
                    } else { context.draw(Image(icon), in: rect) }
                }
            }
        }.frame(width: 30, height: 30).padding(7).contentShape(Rectangle()).buttonStyle(.plain).opacity(hidden ? 0 : 1)
            .accessibilityLabel(icon == "Flower" ? "旋转小红花" : icon == "FlowerPlaylists" ? "描绘小红花" : "绽放小红花")
            .task(id: started) {
                guard started != nil else { return }
                do { try await Task.sleep(for: .seconds(2)); started = nil } catch { }
            }
    }
}

struct FlowerFrameKey: PreferenceKey {
    static var defaultValue: CGRect = .zero
    static func reduce(value: inout CGRect, nextValue: () -> CGRect) { let next = nextValue(); if next != .zero { value = next } }
}

struct EmptyMusic: View {
    var title: String
    var detail: String
    var body: some View {
        VStack(spacing: 14) {
            Image("Flower").resizable().scaledToFit().frame(width: 62, height: 62)
            Text(title).font(.headline).foregroundStyle(Brand.ink)
            Text(detail).font(.subheadline).foregroundStyle(Brand.muted).multilineTextAlignment(.center).lineSpacing(5)
        }.padding(36).frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

struct PillButton: View {
    var title: String
    var symbol: String
    var action: () -> Void
    var body: some View {
        Button(action: action) { Label(title, systemImage: symbol).font(.system(size: 15, weight: .medium)).padding(.horizontal, 18).padding(.vertical, 12) }
            .buttonStyle(.plain).background(Brand.red.opacity(0.08), in: Capsule())
    }
}

func timeLabel(_ seconds: Double) -> String {
    guard seconds.isFinite, seconds >= 0 else { return "0:00" }
    let value = Int(seconds)
    return String(format: "%d:%02d", value / 60, value % 60)
}

enum CoverStyle {
    static let names = ["桃花", "雏菊", "郁金香", "百合", "鸢尾", "向日葵", "山茶", "绣球", "莲花", "矢车菊", "心中小红花"]
    static let images = ["FlowerCoverPeach", "FlowerCoverDaisy", "FlowerCoverTulip", "FlowerCoverLily", "FlowerCoverIris", "FlowerCoverSunflower", "FlowerCoverCamellia", "FlowerCoverHydrangea", "FlowerCoverLotus", "FlowerCoverCornflower", "FlowerCoverFavorite"]
}

struct PlaylistArtwork: View {
    var playlist: Playlist
    var pixels: Int = 384
    var body: some View {
        Group {
            if !playlist.coverPhoto.isEmpty { Artwork(name: playlist.coverPhoto, pixels: pixels) }
            else {
                Image(CoverStyle.images[min(max(playlist.coverPreset, 0), CoverStyle.images.count - 1)])
                    .resizable().scaledToFill().clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
            }
        }.accessibilityHidden(true)
    }
}

struct RootPagePreferenceKey: PreferenceKey {
    static var defaultValue: [Int: Bool] = [:]
    static func reduce(value: inout [Int: Bool], nextValue: () -> [Int: Bool]) { value.merge(nextValue(), uniquingKeysWith: { _, next in next }) }
}
