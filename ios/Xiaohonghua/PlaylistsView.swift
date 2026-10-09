import SwiftUI
import PhotosUI

struct PlaylistsView: View {
    @EnvironmentObject private var library: MusicLibrary
    @State private var creating = false
    @State private var name = ""
    @State private var renaming: Playlist?
    @State private var renamePresented = false
    @State private var deleting: Playlist?
    var body: some View {
        VStack(spacing: 0) {
            FlowerHeader(title: "歌单", icon: "FlowerPlaylists") { name = ""; creating = true }
            HStack { Text("\(library.state.playlists.count) 个歌单").font(.caption).foregroundStyle(Brand.muted); Spacer() }.padding(.horizontal, 24).padding(.bottom, 16)
            if library.state.playlists.isEmpty { EmptyMusic(title: "为喜欢的音乐留一束花", detail: "创建歌单，把不同心情放在一起") }
            else {
                ScrollView {
                    LazyVGrid(columns: [GridItem(.flexible(), spacing: 18), GridItem(.flexible(), spacing: 18)], spacing: 24) {
                        ForEach(library.state.playlists) { playlist in
                            NavigationLink { PlaylistDetail(playlistID: playlist.id) } label: {
                                VStack(alignment: .leading, spacing: 9) {
                                    PlaylistArtwork(playlist: playlist).aspectRatio(1, contentMode: .fit)
                                    Text(playlist.name).font(.system(size: 17, weight: .medium)).foregroundStyle(Brand.ink).lineLimit(1)
                                    Text("\(playlist.songIDs.count) 首歌曲").font(.system(size: 12)).foregroundStyle(Brand.muted)
                                }
                            }.buttonStyle(.plain).contextMenu {
                                Button("重命名", systemImage: "pencil") { renaming = playlist; name = playlist.name; renamePresented = true }
                                Button("删除歌单", systemImage: "trash", role: .destructive) { deleting = playlist }
                            }
                        }
                    }.padding(.horizontal, 24).padding(.bottom, 24)
                }
            }
        }.background(Brand.paper).toolbar(.hidden, for: .navigationBar)
            .alert("创建歌单", isPresented: $creating) { TextField("歌单名称", text: $name); Button("取消", role: .cancel) {}; Button("创建") { library.createPlaylist(name) } }
            .alert("歌单名称", isPresented: $renamePresented) {
                TextField("歌单名称", text: $name); Button("取消", role: .cancel) {}
                Button("保存") {
                    let trimmed = name.trimmingCharacters(in: .whitespacesAndNewlines)
                    if !trimmed.isEmpty { library.update { state in if let index = state.playlists.firstIndex(where: { $0.id == renaming?.id }) { state.playlists[index].name = trimmed } } }
                }
            }
            .confirmationDialog("删除这个歌单？歌曲文件会保留。", isPresented: Binding(get: { deleting != nil }, set: { if !$0 { deleting = nil } }), titleVisibility: .visible) {
                Button("删除歌单", role: .destructive) { let id = deleting?.id; library.update { $0.playlists.removeAll { $0.id == id } }; deleting = nil }
            }
    }
}

struct PlaylistDetail: View {
    @EnvironmentObject private var library: MusicLibrary
    @EnvironmentObject private var playback: Playback
    var playlistID: String
    @State private var coverSheet = false
    @State private var addSheet = false
    private var playlist: Playlist? { library.state.playlists.first { $0.id == playlistID } }
    private var songs: [Song] { library.playlistSongs(playlistID) }
    var body: some View {
        VStack(spacing: 0) {
            if let playlist {
                HStack(spacing: 18) {
                    Button { coverSheet = true } label: { PlaylistArtwork(playlist: playlist).frame(width: 92, height: 92).overlay(alignment: .bottomTrailing) { Image(systemName: "camera.fill").font(.caption).padding(7).background(.regularMaterial, in: Circle()).padding(6) } }.buttonStyle(.plain).accessibilityLabel("更换歌单封面")
                    VStack(alignment: .leading, spacing: 12) {
                        Text(playlist.name).font(.system(size: 24, weight: .semibold)).lineLimit(2)
                        Text("共 \(library.playlistSongs(playlistID).count) 首歌曲").font(.caption).foregroundStyle(Brand.muted)
                    }
                    Spacer()
                }.padding(.horizontal, 24).padding(.vertical, 16)
                HStack {
                    PillButton(title: "播放全部", symbol: "play") { if let first = songs.first { playback.play(first, within: songs.map(\.id)) } }
                    Spacer(); SortMenu(sort: playlist.sort, playlistID: playlistID)
                }.padding(.horizontal, 24).padding(.bottom, 12)
                if songs.isEmpty { EmptyMusic(title: "歌单还在等一首歌", detail: "点右上角 ＋ 从音乐库选择歌曲") }
                else { SongList(songs: songs, playlistID: playlistID) }
            }
        }.background(Brand.paper).navigationTitle("歌单").navigationBarTitleDisplayMode(.inline).toolbar(.visible, for: .navigationBar)
            .toolbar { ToolbarItem(placement: .topBarTrailing) { Button { addSheet = true } label: { Image(systemName: "plus") }.accessibilityLabel("添加歌曲") } }
            .sheet(isPresented: $coverSheet) { CoverPicker(playlistID: playlistID) }
            .sheet(isPresented: $addSheet) { SelectSongs(playlistID: playlistID) }
    }
}

struct SelectSongs: View {
    @EnvironmentObject private var library: MusicLibrary
    @Environment(\.dismiss) private var dismiss
    var playlistID: String
    @State private var selected = Set<String>()
    @State private var query = ""
    var body: some View {
        NavigationStack {
            List(library.orderedSongs.filter { query.isEmpty || ($0.title + $0.artist).localizedCaseInsensitiveContains(query) }) { song in
                Button { if !selected.insert(song.id).inserted { selected.remove(song.id) } } label: {
                    HStack { VStack(alignment: .leading, spacing: 5) { Text(song.title).foregroundStyle(Brand.ink); Text(song.artist).font(.caption).foregroundStyle(Brand.muted) }; Spacer(); Image(systemName: selected.contains(song.id) ? "checkmark.circle.fill" : "circle") }.padding(.vertical, 4)
                }
            }.searchable(text: $query, prompt: "搜索歌曲或歌手").navigationTitle("添加歌曲").navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) { Button("取消") { dismiss() } }
                    ToolbarItem(placement: .confirmationAction) { Button("加入 \(selected.count) 首") { library.add(library.orderedSongs.map(\.id).filter { selected.contains($0) }, to: playlistID); dismiss() }.disabled(selected.isEmpty) }
                }
        }
    }
}

struct CoverPicker: View {
    @EnvironmentObject private var library: MusicLibrary
    @Environment(\.dismiss) private var dismiss
    var playlistID: String
    @State private var photo: PhotosPickerItem?
    @State private var saving = false
    @State private var photoError: String?
    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 24) {
                    LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 20) {
                        ForEach(0..<8) { index in
                            Button {
                                library.update { state in if let p = state.playlists.firstIndex(where: { $0.id == playlistID }) { state.playlists[p].coverPreset = index; state.playlists[p].coverPhoto = "" } }; dismiss()
                            } label: { VStack(spacing: 8) { PlaylistArtwork(playlist: Playlist(name: "", coverPreset: index)).aspectRatio(1, contentMode: .fit); Text(CoverStyle.names[index]).font(.subheadline).foregroundStyle(Brand.ink) } }.buttonStyle(.plain)
                        }
                    }
                    PhotosPicker(selection: $photo, matching: .images) { Label(saving ? "正在保存照片…" : "从相册选择照片", systemImage: "photo").frame(maxWidth: .infinity).padding() }.disabled(saving)
                }.padding(24)
            }.background(Brand.paper).navigationTitle("歌单封面").navigationBarTitleDisplayMode(.inline)
                .toolbar { ToolbarItem(placement: .cancellationAction) { Button("关闭") { dismiss() } } }
                .onChange(of: photo) { _, item in
                    guard let item else { return }
                    saving = true
                    Task {
                        do {
                            guard let data = try await item.loadTransferable(type: Data.self) else { throw LibraryError.unsupported }
                            let name = try await library.files.savePhoto(data)
                            library.update { state in if let p = state.playlists.firstIndex(where: { $0.id == playlistID }) { state.playlists[p].coverPhoto = name } }
                            dismiss()
                        } catch { photoError = error.localizedDescription }
                        saving = false
                    }
                }
                .alert("照片导入失败", isPresented: Binding(get: { photoError != nil }, set: { if !$0 { photoError = nil } })) { Button("知道了", role: .cancel) { photoError = nil } } message: { Text(photoError ?? "") }
        }
    }
}
