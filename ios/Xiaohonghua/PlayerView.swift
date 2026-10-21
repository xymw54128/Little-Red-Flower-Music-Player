import SwiftUI
import UIKit

struct PlayerView: View {
    @EnvironmentObject private var library: MusicLibrary
    @EnvironmentObject private var playback: Playback
    @Environment(\.dismiss) private var dismiss
    @Environment(\.scenePhase) private var phase
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var page = 0
    @State private var queueSheet = false
    @State private var addSheet = false
    @State private var infoSheet = false
    private var lyrics: String {
        guard let song = playback.current else { return "" }
        return library.state.manualLyrics[song.id] ?? song.lyrics
    }
    private var lines: [LyricLine] { Lyrics.parse(lyrics) }

    var body: some View {
        VStack(spacing: 0) {
            HStack(spacing: 2) {
                Button { dismiss() } label: { Image(systemName: "chevron.down").frame(width: 44, height: 44) }
                    .accessibilityLabel("收起播放页")
                Spacer(minLength: 4)
                HStack(spacing: 2) {
                    TitleFlowerSprig().frame(width: 42, height: 38).opacity(page == 1 ? 1 : 0)
                        .animation(reduceMotion ? nil : .easeOut(duration: 0.22), value: page)
                    LyricMarquee(text: page == 0 ? "正在播放" : (playback.current.map { $0.title + " - " + $0.artist } ?? "歌词"),
                                 font: .systemFont(ofSize: 14, weight: .medium), color: UIColor(Brand.muted),
                                 active: page == 1 && phase == .active && !reduceMotion)
                        .frame(height: 38)
                    TitleFlowerSprig(right: true).frame(width: 42, height: 38).opacity(page == 1 ? 1 : 0)
                        .animation(reduceMotion ? nil : .easeOut(duration: 0.22), value: page)
                }.frame(maxWidth: .infinity)
                Spacer(minLength: 4)
                HeaderFlower()
            }.padding(.horizontal, 12)
            TabView(selection: $page) {
                coverPage.tag(0)
                LyricView(text: lyrics, settings: library.state.settings, playback: playback,
                          active: page == 1 && phase == .active && !queueSheet && !addSheet && !infoSheet,
                          reduceMotion: reduceMotion).padding(.vertical, 8)
                    .mask { LinearGradient(stops: [.init(color: .clear, location: 0), .init(color: .black, location: 0.08), .init(color: .black, location: 0.91), .init(color: .clear, location: 1)], startPoint: .top, endPoint: .bottom) }.tag(1)
            }.tabViewStyle(.page(indexDisplayMode: .never))
            HStack(spacing: 0) {
                ForEach(0..<2) { index in
                    Button { withAnimation(reduceMotion ? nil : .easeOut(duration: 0.3)) { page = index } } label: {
                        Capsule().fill(page == index ? Brand.accent(library.state.settings) : Brand.ink.opacity(0.15))
                            .frame(width: page == index ? 17 : 5, height: 5).frame(width: 44, height: 32)
                    }.accessibilityLabel(index == 0 ? "封面页" : "歌词页")
                        .accessibilityAddTraits(page == index ? .isSelected : [])
                }
            }
            PlayerSeekBar(progress: playback.progress, seek: playback.seek).padding(.horizontal, 30)
            controls.padding(.horizontal, 22).padding(.top, 8)
            HStack {
                Text(playback.mode.label).font(.system(size: 10)).foregroundStyle(Brand.muted)
                Spacer()
                if page == 0 {
                    Menu {
                        Button("添加到歌单", systemImage: "text.badge.plus") { addSheet = true }
                        Button("歌曲信息", systemImage: "info.circle") { infoSheet = true }
                    } label: { Image(systemName: "ellipsis").frame(width: 44, height: 36).foregroundStyle(Brand.muted) }
                        .accessibilityLabel("歌曲更多选项")
                }
            }.frame(height: 36).padding(.horizontal, 30).padding(.bottom, 4)
        }.background(Brand.paper.ignoresSafeArea()).foregroundStyle(Brand.ink)
            .tint(Brand.accent(library.state.settings)).buttonStyle(.plain)
            .sheet(isPresented: $queueSheet) { QueueView().flowerPanel() }
            .sheet(isPresented: $addSheet) { AddToPlaylist(songIDs: playback.current.map { [$0.id] } ?? []).flowerPanel() }
            .sheet(isPresented: $infoSheet) { if let song = playback.current { SongInfoView(song: song).flowerPanel() } }
    }

    private var controls: some View {
        HStack(spacing: 0) {
            Button { playback.cycleMode() } label: { Image(systemName: playback.mode.symbol).font(.system(size: 19)).frame(width: 44, height: 52) }.accessibilityLabel(playback.mode.label)
            Spacer(minLength: 4)
            Button { playback.previous() } label: { Image(systemName: "backward.end.fill").font(.system(size: 25)).frame(width: 44, height: 64) }.accessibilityLabel("上一首")
            Spacer(minLength: 4)
            Button { playback.toggle() } label: {
                Group {
                    if playback.loading { ProgressView().tint(.white) }
                    else { Image(systemName: playback.playing ? "pause.fill" : "play.fill").font(.system(size: 27)).offset(x: playback.playing ? 0 : 2) }
                }.frame(width: 68, height: 68).foregroundStyle(.white).background(Brand.accent(library.state.settings), in: Circle())
            }.accessibilityLabel(playback.playing ? "暂停" : "播放")
            Spacer(minLength: 4)
            Button { playback.next() } label: { Image(systemName: "forward.end.fill").font(.system(size: 25)).frame(width: 44, height: 64) }.accessibilityLabel("下一首")
            Spacer(minLength: 4)
            Button { queueSheet = true } label: { Image(systemName: "text.line.first.and.arrowtriangle.forward").font(.system(size: 19)).frame(width: 44, height: 52) }.accessibilityLabel("播放队列")
        }.disabled(playback.current == nil)
    }

    private var coverPage: some View {
        VStack(spacing: 0) {
            GeometryReader { space in
                let side = max(0, min(400, space.size.width - 24, space.size.height - 24))
                ZStack {
                    Artwork(name: playback.current?.artwork ?? "", radius: 22, pixels: 1200)
                    BotanicalCoverFrame()
                }.frame(width: side, height: side)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .contentShape(Rectangle()).onTapGesture { openLyrics() }
                    .accessibilityElement(children: .ignore).accessibilityHidden(false).accessibilityLabel("歌曲封面，查看歌词").accessibilityAddTraits(.isButton)
                    .accessibilityAction { openLyrics() }
                    .accessibilityIdentifier("player.artwork")
            }.clipped()
            VStack(alignment: .leading, spacing: 7) {
                HStack(spacing: 8) {
                    Text(playback.current?.title ?? "未选择歌曲").font(.system(size: 24, weight: .semibold))
                        .lineLimit(2).frame(maxWidth: .infinity, alignment: .leading)
                    if let song = playback.current {
                        let favorite = library.isFavorite(song.id)
                        VStack(spacing: 7) {
                            FavoriteBloomButton(isOn: favorite) {
                                if library.isFavorite(song.id) == favorite { library.toggleFavorite(song.id) }
                            }.id(song.id)
                            RainbowFlower(playing: playback.playing, active: page == 0 && !queueSheet && !addSheet && !infoSheet).frame(width: 34, height: 34)
                        }
                    }
                }
                Text(playback.current?.subtitle ?? "").font(.system(size: 13)).foregroundStyle(Brand.muted).lineLimit(2)
                if let song = playback.current {
                    Text(quality(song)).font(.system(size: 11)).foregroundStyle(Brand.muted).lineLimit(1)
                }
            }.fixedSize(horizontal: false, vertical: true).padding(.top, 10)
            CoverLyricPreview(lines: lines, settings: library.state.settings, progress: playback.progress,
                              active: page == 0 && phase == .active, reduceMotion: reduceMotion)
                .frame(height: 48).contentShape(Rectangle()).onTapGesture { openLyrics() }
                .accessibilityAddTraits(.isButton).accessibilityAction { openLyrics() }
                .accessibilityIdentifier("player.lyric-preview")
        }.padding(.horizontal, 20).padding(.vertical, 6)
    }
    private func openLyrics() { withAnimation(reduceMotion ? nil : .easeOut(duration: 0.3)) { page = 1 } }
    private func quality(_ song: Song) -> String {
        var parts = [song.format]
        if song.bitDepth > 0 { parts.append("\(song.bitDepth) bit") }
        if song.sampleRate > 0 { parts.append(String(format: "%g kHz", Double(song.sampleRate) / 1000)) }
        return parts.filter { !$0.isEmpty }.joined(separator: " · ")
    }
}

/// Only this small preview observes progress ticks; the cover and navigation do not.
private struct CoverLyricPreview: View {
    var lines: [LyricLine]
    var settings: MusicSettings
    @ObservedObject var progress: PlaybackProgress
    var active: Bool
    var reduceMotion: Bool
    private var text: String {
        let index = Lyrics.current(lines, at: progress.position)
        return lines.indices.contains(index) ? lines[index].text.replacingOccurrences(of: "\n", with: " · ") : "点击封面或左滑查看歌词"
    }
    var body: some View {
        GeometryReader { space in
            if reduceMotion {
                ScrollView(.horizontal, showsIndicators: false) {
                    Text(text).font(Font(Brand.font(settings.lyricFont, size: CGFloat(min(settings.lyricSize, 20)))))
                        .fixedSize(horizontal: true, vertical: false).frame(minWidth: space.size.width)
                }.foregroundStyle(Brand.accent(settings)).frame(maxHeight: .infinity)
            } else {
                LyricMarquee(text: text, font: Brand.font(settings.lyricFont, size: min(settings.lyricSize, 20)),
                             color: UIColor(Brand.accent(settings)), active: active)
                    .accessibilityLabel(text)
            }
        }
    }
}

private struct LyricMarquee: UIViewRepresentable {
    var text: String
    var font: UIFont
    var color: UIColor
    var active: Bool
    func makeUIView(context: Context) -> MarqueeLabelView { MarqueeLabelView() }
    func updateUIView(_ view: MarqueeLabelView, context: Context) { view.configure(text: text, font: font, color: color, active: active) }
}

/// Core Animation moves one laid-out label; it does not cause per-frame SwiftUI layout.
private final class MarqueeLabelView: UIView {
    private let label = UILabel()
    private var active = false
    private var animationKey = ""
    override init(frame: CGRect) { super.init(frame: frame); clipsToBounds = true; label.numberOfLines = 1; addSubview(label) }
    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }
    func configure(text: String, font: UIFont, color: UIColor, active: Bool) {
        label.text = text; label.font = font; label.textColor = color; self.active = active
        accessibilityLabel = text; accessibilityTraits = .staticText; isAccessibilityElement = true
        setNeedsLayout()
    }
    override func layoutSubviews() {
        super.layoutSubviews()
        let width = ceil(label.sizeThatFits(CGSize(width: CGFloat.greatestFiniteMagnitude, height: bounds.height)).width)
        let key = "\(label.text ?? "")|\(label.font.fontName)|\(label.font.pointSize)|\(bounds.size)|\(active)"
        guard animationKey != key else { return }
        animationKey = key; label.layer.removeAnimation(forKey: "lyric-marquee")
        label.transform = .identity
        label.frame = CGRect(x: width <= bounds.width ? (bounds.width - width) / 2 : 0, y: 0, width: width, height: bounds.height)
        let distance = width - bounds.width
        guard active, distance > 1, bounds.width > 0, window != nil else { return }
        let travel = Double(distance / 28), back = Double(distance / 90)
        let duration = 2.4 + travel + back
        let animation = CAKeyframeAnimation(keyPath: "transform.translation.x")
        animation.values = [0, 0, -distance, -distance, 0]
        animation.keyTimes = [0, NSNumber(value: 1.2 / duration), NSNumber(value: (1.2 + travel) / duration), NSNumber(value: (2.4 + travel) / duration), 1]
        animation.duration = duration; animation.repeatCount = .infinity
        animation.calculationMode = .linear
        label.layer.add(animation, forKey: "lyric-marquee")
    }
    override func didMoveToWindow() { super.didMoveToWindow(); animationKey = ""; setNeedsLayout() }
}

struct PlayerSeekBar: View {
    @ObservedObject var progress: PlaybackProgress
    var seek: (Double) -> Void
    @EnvironmentObject private var library: MusicLibrary
    @State private var drag: Double?
    var body: some View {
        VStack(spacing: 4) {
            GeometryReader { geometry in
                let fraction = progress.duration > 0 ? min(max((drag ?? progress.position) / progress.duration, 0), 1) : 0
                ZStack(alignment: .leading) {
                    Capsule().fill(Brand.ink.opacity(0.12)).frame(height: 3)
                    Capsule().fill(Brand.accent(library.state.settings)).frame(width: max(0, geometry.size.width * fraction), height: 3)
                    Circle().fill(Brand.accent(library.state.settings)).frame(width: drag == nil ? 9 : 15, height: drag == nil ? 9 : 15).offset(x: (geometry.size.width - (drag == nil ? 9 : 15)) * fraction)
                }.frame(height: 32).contentShape(Rectangle())
                    .gesture(DragGesture(minimumDistance: 0).onChanged { drag = min(max($0.location.x / max(geometry.size.width, 1), 0), 1) * progress.duration }.onEnded { event in
                        let time = min(max(event.location.x / max(geometry.size.width, 1), 0), 1) * progress.duration
                        drag = nil; seek(time)
                    })
            }.frame(height: 32)
                .accessibilityElement().accessibilityLabel("播放进度").accessibilityValue(timeLabel(drag ?? progress.position) + "，共" + timeLabel(progress.duration))
                .accessibilityAdjustableAction { direction in seek(progress.position + (direction == .increment ? 10 : -10)) }
            HStack { Text(timeLabel(drag ?? progress.position)).accessibilityIdentifier("player.elapsed"); Spacer(); Text(timeLabel(progress.duration)).accessibilityIdentifier("player.duration") }.font(.system(size: 11).monospacedDigit()).foregroundStyle(Brand.muted)
        }
    }
}

struct QueueView: View {
    @EnvironmentObject private var library: MusicLibrary
    @EnvironmentObject private var playback: Playback
    @Environment(\.dismiss) private var dismiss
    var songs: [Song] { let byID = Dictionary(library.state.songs.map { ($0.id, $0) }, uniquingKeysWith: { first, _ in first }); return playback.queue.compactMap { byID[$0] } }
    var body: some View {
        NavigationStack {
            List {
                ForEach(songs) { song in
                    Button { playback.play(song); dismiss() } label: {
                        HStack { if playback.current?.id == song.id { Image(systemName: "waveform").foregroundStyle(Brand.accent(library.state.settings)) }; VStack(alignment: .leading, spacing: 5) { Text(song.title).foregroundStyle(Brand.ink); Text(song.artist).font(.caption).foregroundStyle(Brand.muted) } }
                    }
                }.onMove { indices, destination in var ids = songs.map(\.id); ids.move(fromOffsets: indices, toOffset: destination); playback.setQueue(ids) }
                    .onDelete { indices in var ids = songs.map(\.id); ids.remove(atOffsets: indices); playback.setQueue(ids) }
            }.scrollContentBackground(.hidden).background(Brand.paper)
                .navigationTitle("播放队列 · \(songs.count)").navigationBarTitleDisplayMode(.inline)
                .toolbar { ToolbarItem(placement: .cancellationAction) { Button("完成") { dismiss() } }; ToolbarItem(placement: .topBarTrailing) { EditButton() } }
        }.presentationDetents([.medium, .large])
    }
}
