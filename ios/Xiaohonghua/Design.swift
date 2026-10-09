import SwiftUI
import UIKit
import ImageIO

enum Brand {
    static let red = Color(hex: 0xCE3C4C)
    static let gold = Color(hex: 0xF6D88C)
    static let paper = Color(hex: 0xF8F8F6)
    static let ink = Color(hex: 0x272B29)
    static let muted = Color(hex: 0x696F6B)
    static let themes: [(String, UInt)] = [("花红", 0xCE3C4C), ("森林", 0x527568), ("海蓝", 0x436B92), ("陶土", 0x98644E), ("暮紫", 0x76668D)]
    static func accent(_ settings: MusicSettings) -> Color { Color(hex: themes[min(max(settings.theme, 0), themes.count - 1)].1) }
    static let fonts: [(String, String)] = [("system", "系统字体"), ("serif", "衬线体"), ("mono", "等宽体"), ("kai", "楷体 · 文楷"), ("song", "宋体 · 思源"), ("fangsong", "仿宋 · 朱雀")]
    // Filled from the original font name table, not display/family names.
    static let fontNames = ["kai": "LXGWWenKai-Regular", "song": "SourceHanSerifCN-Regular", "fangsong": "ZhuqueFangsong-Regular"]
    static func font(_ name: String, size: CGFloat) -> UIFont {
        if let face = fontNames[name], let custom = UIFont(name: face, size: size) { return custom }
        if name == "mono" { return UIFont.monospacedSystemFont(ofSize: size, weight: .regular) }
        let normal = UIFont.systemFont(ofSize: size)
        if name == "serif", let descriptor = normal.fontDescriptor.withDesign(.serif) { return UIFont(descriptor: descriptor, size: size) }
        return normal
    }
}

extension Color {
    init(hex: UInt) { self.init(red: Double((hex >> 16) & 255) / 255, green: Double((hex >> 8) & 255) / 255, blue: Double(hex & 255) / 255) }
}

final class ImageCache {
    static let shared = ImageCache()
    private let cache = NSCache<NSString, UIImage>()
    init() { cache.totalCostLimit = 48 * 1024 * 1024; cache.countLimit = 80 }
    func image(_ name: String, pixels: Int = 512) -> UIImage? {
        let key = (name + "@" + String(pixels)) as NSString
        if let image = cache.object(forKey: key) { return image }
        guard let url = AppFiles.image(name), let source = CGImageSourceCreateWithURL(url as CFURL, nil),
              let cg = CGImageSourceCreateThumbnailAtIndex(source, 0, [kCGImageSourceCreateThumbnailFromImageAlways: true,
                    kCGImageSourceThumbnailMaxPixelSize: pixels, kCGImageSourceCreateThumbnailWithTransform: true] as CFDictionary) else { return nil }
        let image = UIImage(cgImage: cg)
        cache.setObject(image, forKey: key, cost: cg.bytesPerRow * cg.height)
        return image
    }
}

struct Artwork: View {
    var name: String
    var radius: CGFloat = 16
    var body: some View {
        Group {
            if let image = ImageCache.shared.image(name) { Image(uiImage: image).resizable().scaledToFill() }
            else {
                ZStack {
                    Brand.red.opacity(0.07)
                    Image("Flower").resizable().scaledToFit().padding(12)
                }
            }
        }.clipped().clipShape(RoundedRectangle(cornerRadius: radius, style: .continuous))
            .accessibilityHidden(true)
    }
}

struct FlowerHeader: View {
    var title: String
    var icon = "Flower"
    var hiddenFlower = false
    var action: (() -> Void)?
    var body: some View {
        HStack(spacing: 12) {
            Image(icon).resizable().scaledToFit().frame(width: 30, height: 30)
                .opacity(hiddenFlower ? 0 : 1)
                .background(GeometryReader { geometry in
                    Color.clear.preference(key: FlowerFrameKey.self, value: icon == "Flower" ? geometry.frame(in: .global) : .zero)
                }).accessibilityHidden(true)
            Text(title).font(.system(size: 29, weight: .semibold)).foregroundStyle(Brand.ink)
            Spacer()
            if let action {
                Button(action: action) { Image(systemName: "plus").font(.system(size: 24, weight: .regular)).frame(width: 44, height: 44) }
                    .accessibilityLabel(title == "歌单" ? "创建歌单" : "导入音乐")
            }
        }.padding(.horizontal, 24).padding(.top, 12).padding(.bottom, 16)
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
    static let names = ["红花", "森林", "海浪", "月夜", "暖阳", "唱片", "耳机", "钢琴"]
    static let symbols = ["", "leaf.fill", "water.waves", "moon.stars.fill", "sun.max.fill", "opticaldisc.fill", "headphones", "pianokeys"]
    static let colors: [UInt] = [0xF2DADC, 0xD8E4DA, 0xDAE7F0, 0xE2DEF0, 0xF2E4C6, 0xDBDEDA, 0xE8DCD4, 0xE3DDE6]
}

struct PlaylistArtwork: View {
    var playlist: Playlist
    var body: some View {
        let preset = min(max(playlist.coverPreset, 0), 7)
        Group {
            if !playlist.coverPhoto.isEmpty, ImageCache.shared.image(playlist.coverPhoto) != nil { Artwork(name: playlist.coverPhoto) }
            else {
                ZStack {
                    Color(hex: CoverStyle.colors[preset])
                    if preset == 0 { Image("Flower").resizable().scaledToFit().padding(18) }
                    else { Image(systemName: CoverStyle.symbols[preset]).resizable().scaledToFit().padding(25).foregroundStyle(Brand.ink.opacity(0.75)) }
                }.clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
            }
        }.accessibilityHidden(true)
    }
}
