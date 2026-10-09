import SwiftUI
import UniformTypeIdentifiers

struct PlayerView: View {
    @EnvironmentObject private var library: MusicLibrary
    @EnvironmentObject private var playback: Playback
    @Environment(\.dismiss) private var dismiss
    @Environment(\.scenePhase) private var phase
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var page = 0
    @State private var queueSheet = false
    @State private var addSheet = false
    @State private var lyricImporter = false
    private var lyrics: String { guard let song = playback.current else { return "" }; return library.state.manualLyrics[song.id] ?? song.lyrics }

    var body: some View {
        GeometryReader { geometry in
            ZStack {
                PlayerBackground(artwork: playback.current?.artwork ?? "", settings: library.state.settings).ignoresSafeArea()
                VStack(spacing: 0) {
                    HStack {
                        Button { dismiss() } label: { Image(systemName: "chevron.down").frame(width: 44, height: 44) }.accessibilityLabel("收起播放页")
                        Spacer()
                        Text(page == 0 ? "正在播放" : "歌词").font(.system(size: 14, weight: .medium)).foregroundStyle(Brand.muted)
                        Spacer()
                        Menu {
                            Button("加入歌单", systemImage: "text.badge.plus") { addSheet = true }
                            Button("导入歌词文件", systemImage: "text.quote") { lyricImporter = true }
                            Button("歌词设置", systemImage: "textformat") { settingsSheet = .lyrics }
                            Button("更换播放背景", systemImage: "photo") { settingsSheet = .background }
                        } label: { Image(systemName: "ellipsis").frame(width: 44, height: 44) }.accessibilityLabel("播放设置")
                    }.padding(.horizontal, 14)
                    TabView(selection: $page) {
                        coverPage(height: geometry.size.height).tag(0)
                        LyricView(text: lyrics, settings: library.state.settings, playback: playback, active: page == 1 && phase == .active && settingsSheet == nil && !queueSheet && !addSheet && !lyricImporter, reduceMotion: reduceMotion)
                            .padding(.vertical, 8).tag(1)
                    }.tabViewStyle(.page(indexDisplayMode: .never))
                    HStack(spacing: 7) {
                        ForEach(0..<2) { index in Circle().fill(page == index ? Brand.accent(library.state.settings) : Brand.ink.opacity(0.15)).frame(width: 5, height: 5) }
                    }.padding(.bottom, 18).accessibilityHidden(true)
                    VStack(alignment: .leading, spacing: 8) {
                        Text(playback.current?.title ?? "未选择歌曲").font(.system(size: 24, weight: .semibold)).lineLimit(2)
                        Text(playback.current?.subtitle ?? "").font(.system(size: 14)).foregroundStyle(Brand.muted).lineLimit(1)
                    }.frame(maxWidth: .infinity, alignment: .leading).padding(.horizontal, 30).padding(.bottom, 16)
                    PlayerSeekBar(progress: playback.progress, seek: playback.seek).padding(.horizontal, 30)
                    HStack(spacing: 0) {
                        Button { playback.cycleMode() } label: { Image(systemName: playback.mode.symbol).font(.system(size: 19)).frame(width: 48, height: 52) }.accessibilityLabel(playback.mode.label)
                        Spacer(minLength: 6)
                        Button { playback.previous() } label: { Image(systemName: "backward.end.fill").font(.system(size: 26)).frame(width: 48, height: 64) }.accessibilityLabel("上一首")
                        Spacer(minLength: 6)
                        Button { playback.toggle() } label: {
                            Group { if playback.loading { ProgressView().tint(.white) } else { Image(systemName: playback.playing ? "pause.fill" : "play.fill").font(.system(size: 27)).offset(x: playback.playing ? 0 : 2) } }
                                .frame(width: 68, height: 68).foregroundStyle(.white).background(Brand.accent(library.state.settings), in: Circle())
                        }.accessibilityLabel(playback.playing ? "暂停" : "播放")
                        Spacer(minLength: 6)
                        Button { playback.next() } label: { Image(systemName: "forward.end.fill").font(.system(size: 26)).frame(width: 48, height: 64) }.accessibilityLabel("下一首")
                        Spacer(minLength: 6)
                        Button { queueSheet = true } label: { Image(systemName: "text.line.first.and.arrowtriangle.forward").font(.system(size: 19)).frame(width: 48, height: 52) }.accessibilityLabel("播放队列")
                    }.padding(.horizontal, 22).padding(.top, 10).padding(.bottom, 22)
                }
            }
        }.foregroundStyle(Brand.ink).tint(Brand.accent(library.state.settings)).buttonStyle(.plain)
            .sheet(isPresented: $queueSheet) { QueueView() }
            .sheet(isPresented: $addSheet) { AddToPlaylist(songIDs: playback.current.map { [$0.id] } ?? []) }
            .sheet(item: $settingsSheet) { setting in NavigationStack { Group { if setting == .lyrics { LyricSettingsView() } else { BackgroundSettingsView() } }.toolbar { ToolbarItem(placement: .cancellationAction) { Button("完成") { settingsSheet = nil } } } }.tint(Brand.accent(library.state.settings)) }
            .fileImporter(isPresented: $lyricImporter, allowedContentTypes: [UTType(filenameExtension: "lrc") ?? .plainText, .plainText]) { result in
                if case .success(let url) = result, let song = playback.current { Task { await library.importLyrics(url, songID: song.id) } }
                else if case .failure(let error) = result { library.message = error.localizedDescription }
            }
            .alert("小红花", isPresented: Binding(get: { library.message != nil }, set: { if !$0 { library.message = nil } })) { Button("知道了", role: .cancel) { library.message = nil } } message: { Text(library.message ?? "") }
    }
    @State private var settingsSheet: PlayerSetting?
    private enum PlayerSetting: String, Identifiable { case lyrics, background; var id: String { rawValue } }

    private func coverPage(height: CGFloat) -> some View {
        VStack(spacing: 20) {
            Spacer(minLength: 12)
            Artwork(name: playback.current?.artwork ?? "", radius: 24).aspectRatio(1, contentMode: .fit)
                .frame(maxWidth: min(360, max(160, height * 0.43)))
                .shadow(color: .black.opacity(0.07), radius: 20, y: 10)
                .padding(.horizontal, 34)
            Text("左滑查看歌词").font(.system(size: 12)).foregroundStyle(Brand.muted).padding(.top, 6)
            Spacer(minLength: 12)
        }.frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

struct PlayerBackground: View {
    var artwork: String
    var settings: MusicSettings
    var body: some View {
        GeometryReader { geometry in
            let name = settings.background == "photo" ? settings.backgroundPhoto : settings.background == "artwork" ? artwork : ""
            ZStack {
                Brand.paper
                if let image = ImageCache.shared.image(name, pixels: 1600) {
                    let ratio = max(geometry.size.width / image.size.width, geometry.size.height / image.size.height) * settings.backgroundScale
                    let width = image.size.width * ratio, height = image.size.height * ratio
                    Image(uiImage: image).resizable().frame(width: width, height: height)
                        .position(x: geometry.size.width / 2 + (width - geometry.size.width) * (0.5 - settings.backgroundX),
                                  y: geometry.size.height / 2 + (height - geometry.size.height) * (0.5 - settings.backgroundY))
                        .opacity(settings.backgroundOpacity)
                }
            }.clipped()
        }.accessibilityHidden(true)
    }
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
            HStack { Text(timeLabel(drag ?? progress.position)); Spacer(); Text(timeLabel(progress.duration)) }.font(.system(size: 11).monospacedDigit()).foregroundStyle(Brand.muted)
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
            }.navigationTitle("播放队列 · \(songs.count)").navigationBarTitleDisplayMode(.inline)
                .toolbar { ToolbarItem(placement: .cancellationAction) { Button("完成") { dismiss() } }; ToolbarItem(placement: .topBarTrailing) { EditButton() } }
        }.presentationDetents([.medium, .large])
    }
}
