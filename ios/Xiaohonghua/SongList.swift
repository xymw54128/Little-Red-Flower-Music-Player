import SwiftUI

struct SongList: View {
    @EnvironmentObject private var library: MusicLibrary
    @EnvironmentObject private var playback: Playback
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    var songs: [Song]
    var playlistID: String?
    var queueSource: String? = "library"
    var header: AnyView?
    @State private var infoSong: Song?
    @State private var addIDs: [String] = []
    @State private var addPresented = false
    @State private var scrollingState = SongScrollingState()
    @State private var locateVisible = false

    var body: some View {
        ScrollViewReader { reader in
            ScrollView {
                LazyVStack(spacing: 0) {
                    if let header { header }
                    ForEach(Array(songs.enumerated()), id: \.element.id) { index, song in
                        HStack(spacing: 12) {
                            Text(String(index + 1)).font(.system(size: 12).monospacedDigit())
                                .foregroundStyle(playback.current?.id == song.id ? Brand.accent(library.state.settings) : Brand.muted)
                                .frame(width: 20, alignment: .center)
                            Button { playback.play(song, within: songs.map(\.id), sourceID: playlistID ?? queueSource) } label: {
                                HStack(spacing: 12) {
                                    Artwork(name: song.artwork, pixels: 160).frame(width: 48, height: 48)
                                    VStack(alignment: .leading, spacing: 7) {
                                        Text(song.title).font(.system(size: 16, weight: playback.current?.id == song.id ? .semibold : .regular))
                                            .foregroundStyle(playback.current?.id == song.id ? Brand.accent(library.state.settings) : Brand.ink).lineLimit(1)
                                        Text(song.artist + " · " + song.album).font(.system(size: 12)).foregroundStyle(Brand.muted).lineLimit(1)
                                    }.frame(maxWidth: .infinity, alignment: .leading)
                                    Text(timeLabel(song.duration)).font(.system(size: 12).monospacedDigit()).foregroundStyle(Brand.muted)
                                }.contentShape(Rectangle()).opacity(song.available ? 1 : 0.5)
                            }.buttonStyle(.plain).accessibilityLabel(song.title + "，" + song.artist)
                            Button { library.toggleFavorite(song.id) } label: {
                                Image(systemName: library.isFavorite(song.id) ? "heart.fill" : "heart")
                                    .font(.system(size: 19)).foregroundStyle(library.isFavorite(song.id) ? Brand.accent(library.state.settings) : Brand.muted.opacity(0.6))
                                    .frame(width: 36, height: 44)
                            }.buttonStyle(.plain).accessibilityLabel(library.isFavorite(song.id) ? "取消喜欢" : "喜欢这首歌")
                                .sensoryFeedback(.selection, trigger: library.isFavorite(song.id))
                            Menu {
                                Button("下一首播放", systemImage: "text.insert") { playback.insertNext([song.id]) }
                                Button("追加队尾", systemImage: "text.append") { playback.appendToQueue([song.id]) }
                                Button("加入歌单", systemImage: "text.badge.plus") { addIDs = [song.id]; addPresented = true }
                                Button("歌曲信息", systemImage: "info.circle") { infoSong = song }
                                if let playlistID {
                                    Button("从歌单移除", systemImage: "minus.circle", role: .destructive) { library.remove([song.id], from: playlistID) }
                                }
                            } label: { Image(systemName: "ellipsis").rotationEffect(.degrees(90)).foregroundStyle(Brand.muted.opacity(0.55)).frame(width: 28, height: 44) }
                                .accessibilityLabel(song.title + "的更多操作")
                        }.padding(.horizontal, 18).padding(.vertical, 12).id(song.id)
                    }
                    Text("共 \(songs.count) 首歌曲").font(.system(size: 12)).foregroundStyle(Brand.muted).padding(.vertical, 22)
                }.background(GeometryReader { geometry in
                    Color.clear.preference(key: SongOffsetKey.self, value: geometry.frame(in: .named("song-list")).minY)
                })
            }.coordinateSpace(name: "song-list")
                .simultaneousGesture(DragGesture(minimumDistance: 4).onChanged { _ in scrollingState.dragged = true; scrolling() })
                .onPreferenceChange(SongOffsetKey.self) { offset in
                    if scrollingState.dragged, !scrollingState.locating, abs(offset - scrollingState.lastOffset) > 0.5 { scrolling() }
                    scrollingState.lastOffset = offset
                }
                .overlay(alignment: .bottomTrailing) {
                    if locateVisible, let id = playback.current?.id, songs.contains(where: { $0.id == id }) {
                        Button {
                            scrollingState.locating = true; scrollingState.dragged = false; scrollingState.hideTask?.cancel()
                            if reduceMotion { reader.scrollTo(id, anchor: .center) }
                            else { withAnimation(.easeInOut(duration: 0.35)) { reader.scrollTo(id, anchor: .center) } }
                            withAnimation(.easeOut(duration: 0.18)) { locateVisible = false }
                            Task { try? await Task.sleep(for: .milliseconds(450)); scrollingState.locating = false }
                        } label: {
                            Image(systemName: "location.circle.fill").font(.system(size: 25)).foregroundStyle(Brand.accent(library.state.settings))
                                .frame(width: 46, height: 46).background(.regularMaterial, in: Circle())
                                .shadow(color: .black.opacity(0.08), radius: 8, y: 3)
                        }.accessibilityLabel("定位当前歌曲").padding(.trailing, 70).padding(.bottom, 40)
                            .transition(.opacity.combined(with: .scale(scale: 0.94)))
                    }
                }
        }.sheet(isPresented: $addPresented) { AddToPlaylist(songIDs: addIDs) }
            .sheet(item: $infoSong) { SongInfoView(song: $0) }
            .onDisappear { scrollingState.hideTask?.cancel() }
    }
    private func scrolling() {
        guard !scrollingState.locating, let id = playback.current?.id, songs.contains(where: { $0.id == id }) else { return }
        scrollingState.deadline = Date().addingTimeInterval(2)
        if !locateVisible { withAnimation(.easeOut(duration: 0.18)) { locateVisible = true } }
        guard scrollingState.hideTask == nil else { return }
        scrollingState.hideTask = Task {
            do {
                while true {
                    let remaining = scrollingState.deadline.timeIntervalSinceNow
                    if remaining <= 0 { break }
                    try await Task.sleep(for: .seconds(remaining))
                }
                withAnimation(.easeOut(duration: 0.22)) { locateVisible = false }
            } catch { }
            scrollingState.hideTask = nil
        }
    }
}

/// Offset samples do not publish SwiftUI state or rebuild the song list per frame.
private final class SongScrollingState {
    var dragged = false
    var locating = false
    var lastOffset: CGFloat = 0
    var deadline = Date.distantPast
    var hideTask: Task<Void, Never>?
}

private struct SongOffsetKey: PreferenceKey {
    static var defaultValue: CGFloat = 0
    static func reduce(value: inout CGFloat, nextValue: () -> CGFloat) { value = nextValue() }
}

struct SortMenu: View {
    @EnvironmentObject private var library: MusicLibrary
    var sort: SongSort
    var playlistID: String?
    @State private var reorder = false
    var body: some View {
        Menu {
            ForEach(SongSort.allCases) { value in
                Button(value.label) {
                    if value == .custom { reorder = true }
                    else { library.sort(value, playlistID: playlistID) }
                }
            }
        } label: { Text(sort.label).font(.system(size: 13, weight: .medium)); Image(systemName: "arrow.up.arrow.down").font(.system(size: 12)) }
            .sheet(isPresented: $reorder) {
                ReorderSongs(songs: playlistID.map { library.playlistSongs($0) } ?? library.orderedSongs) { ids in library.saveOrder(ids, playlistID: playlistID) }
            }
    }
}

struct ReorderSongs: View {
    @Environment(\.dismiss) private var dismiss
    @State var songs: [Song]
    var save: ([String]) -> Void
    var body: some View {
        NavigationStack {
            List {
                ForEach(songs) { song in
                    HStack(spacing: 12) {
                        Artwork(name: song.artwork, radius: 10, pixels: 128).frame(width: 40, height: 40)
                        VStack(alignment: .leading, spacing: 5) { Text(song.title).lineLimit(1); Text(song.artist + " · " + song.album).font(.caption).foregroundStyle(Brand.muted).lineLimit(1) }
                    }.padding(.vertical, 3)
                }
                    .onMove { songs.move(fromOffsets: $0, toOffset: $1) }
            }.environment(\.editMode, .constant(.active)).navigationTitle("拖动调整顺序").navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) { Button("取消") { dismiss() } }
                    ToolbarItem(placement: .confirmationAction) { Button("保存") { save(songs.map(\.id)); dismiss() } }
                }
        }
    }
}

struct AddToPlaylist: View {
    @EnvironmentObject private var library: MusicLibrary
    @Environment(\.dismiss) private var dismiss
    var songIDs: [String]
    @State private var name = ""
    @State private var creating = false
    var body: some View {
        NavigationStack {
            List {
                ForEach(library.state.playlists) { playlist in
                    Button { library.add(songIDs, to: playlist.id); dismiss() } label: {
                        HStack(spacing: 14) {
                            PlaylistArtwork(playlist: playlist, pixels: 160).frame(width: 46, height: 46)
                            VStack(alignment: .leading, spacing: 5) { Text(playlist.name).foregroundStyle(Brand.ink); Text("\(playlist.songIDs.count) 首").font(.caption).foregroundStyle(Brand.muted) }
                        }.padding(.vertical, 4)
                    }
                }
                Button("新建歌单", systemImage: "plus") { creating = true }
            }.navigationTitle("选择歌单").navigationBarTitleDisplayMode(.inline)
                .toolbar { ToolbarItem(placement: .cancellationAction) { Button("取消") { dismiss() } } }
                .alert("创建歌单", isPresented: $creating) {
                    TextField("歌单名称", text: $name)
                    Button("取消", role: .cancel) {}
                    Button("创建并加入") {
                        library.createPlaylist(name)
                        if let id = library.state.playlists.last?.id, !name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty { library.add(songIDs, to: id); dismiss() }
                    }
                }
        }.presentationDetents([.medium, .large])
    }
}

/// Reused by the list menu and the full player.
struct SongInfoView: View {
    @Environment(\.dismiss) private var dismiss
    var song: Song
    @State private var showingArtwork = false
    var body: some View {
        NavigationStack {
            Form {
                Section {
                    Button { showingArtwork = true } label: {
                        Artwork(name: song.artwork, radius: 22, pixels: 1000).aspectRatio(1, contentMode: .fit)
                    }.buttonStyle(.plain).accessibilityLabel("放大歌曲封面")
                        .listRowInsets(EdgeInsets(top: 14, leading: 14, bottom: 14, trailing: 14))
                }
                Section("歌曲信息") {
                    LabeledContent("歌名", value: song.title)
                    LabeledContent("歌手", value: song.artist)
                    LabeledContent("专辑", value: song.album)
                    LabeledContent("时长", value: timeLabel(song.duration))
                    LabeledContent("格式", value: song.format.uppercased())
                    if song.sampleRate > 0 { LabeledContent("采样率", value: String(format: "%.1f kHz", Double(song.sampleRate) / 1000)) }
                    if song.bitDepth > 0 { LabeledContent("位深", value: "\(song.bitDepth) bit") }
                    LabeledContent("文件", value: song.name)
                }.textSelection(.enabled)
            }.navigationTitle("歌曲信息").navigationBarTitleDisplayMode(.inline)
                .toolbar { ToolbarItem(placement: .confirmationAction) { Button("完成") { dismiss() } } }
        }.fullScreenCover(isPresented: $showingArtwork) {
            ZStack(alignment: .topTrailing) {
                Brand.paper.ignoresSafeArea()
                Artwork(name: song.artwork, radius: 0, pixels: 2048).aspectRatio(1, contentMode: .fit).frame(maxHeight: .infinity)
                Button { showingArtwork = false } label: { Image(systemName: "xmark").font(.system(size: 20, weight: .medium)).frame(width: 44, height: 44).background(.regularMaterial, in: Circle()) }.padding(20).accessibilityLabel("关闭封面")
            }
        }
    }
}
