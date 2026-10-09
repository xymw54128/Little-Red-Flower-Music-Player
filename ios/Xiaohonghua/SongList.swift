import SwiftUI
import UniformTypeIdentifiers

struct SongList: View {
    @EnvironmentObject private var library: MusicLibrary
    @EnvironmentObject private var playback: Playback
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    var songs: [Song]
    var playlistID: String?
    @State private var addIDs: [String] = []
    @State private var addPresented = false
    @State private var lyricSong: Song?
    @State private var lyricImporter = false
    @State private var scrollingState = SongScrollingState()
    @State private var locateVisible = false

    var body: some View {
        ScrollViewReader { reader in
            ScrollView {
                LazyVStack(spacing: 0) {
                    ForEach(Array(songs.enumerated()), id: \.element.id) { index, song in
                        HStack(spacing: 12) {
                            Text(String(index + 1)).font(.system(size: 12).monospacedDigit())
                                .foregroundStyle(playback.current?.id == song.id ? Brand.accent(library.state.settings) : Brand.muted)
                                .frame(width: 20, alignment: .center)
                            Button { playback.play(song, within: songs.map(\.id)) } label: {
                                HStack(spacing: 12) {
                                    Artwork(name: song.artwork).frame(width: 48, height: 48)
                                    VStack(alignment: .leading, spacing: 7) {
                                        Text(song.title).font(.system(size: 16, weight: playback.current?.id == song.id ? .semibold : .regular))
                                            .foregroundStyle(playback.current?.id == song.id ? Brand.accent(library.state.settings) : Brand.ink).lineLimit(1)
                                        Text(song.artist + " · " + song.format).font(.system(size: 12)).foregroundStyle(Brand.muted).lineLimit(1)
                                    }.frame(maxWidth: .infinity, alignment: .leading)
                                    Text(timeLabel(song.duration)).font(.system(size: 12).monospacedDigit()).foregroundStyle(Brand.muted)
                                }.contentShape(Rectangle()).opacity(song.available ? 1 : 0.5)
                            }.buttonStyle(.plain).accessibilityLabel(song.title + "，" + song.artist)
                            Menu {
                                Button("加入歌单", systemImage: "text.badge.plus") { addIDs = [song.id]; addPresented = true }
                                Button("下一首播放", systemImage: "text.insert") {
                                    var ids = playback.queue.filter { $0 != song.id }
                                    let index = playback.current.flatMap { ids.firstIndex(of: $0.id) }.map { $0 + 1 } ?? ids.count
                                    ids.insert(song.id, at: index); playback.setQueue(ids)
                                }
                                Button("导入歌词", systemImage: "text.quote") { lyricSong = song; lyricImporter = true }
                                if let playlistID {
                                    Button("从歌单移除", systemImage: "minus.circle", role: .destructive) {
                                        library.update { state in
                                            guard let p = state.playlists.firstIndex(where: { $0.id == playlistID }) else { return }
                                            state.playlists[p].songIDs.removeAll { $0 == song.id }
                                        }
                                    }
                                }
                            } label: { Image(systemName: "ellipsis").rotationEffect(.degrees(90)).frame(width: 32, height: 44) }
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
            .fileImporter(isPresented: $lyricImporter, allowedContentTypes: [UTType(filenameExtension: "lrc") ?? .plainText, .plainText]) { result in
                if case .success(let url) = result, let song = lyricSong { Task { await library.importLyrics(url, songID: song.id) } }
                else if case .failure(let error) = result { library.message = error.localizedDescription }
            }
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
                ForEach(songs) { song in Text(song.title).lineLimit(1) }
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
                            PlaylistArtwork(playlist: playlist).frame(width: 46, height: 46)
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
