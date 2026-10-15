import SwiftUI
import PhotosUI

struct PlaylistsView: View {
    @EnvironmentObject private var library: MusicLibrary
    @State private var rootVisible = false
    @State private var creating = false
    @State private var name = ""
    @State private var editing: Playlist?
    @State private var deleting: Playlist?
    var body: some View {
        VStack(spacing: 0) {
            FlowerHeader(title: "歌单", icon: "FlowerPlaylists") { name = ""; creating = true }
            HStack { Text("\(library.state.playlists.count) 个歌单").font(.caption).foregroundStyle(Brand.muted); Spacer() }.padding(.horizontal, 24).padding(.bottom, 16)
            ScrollView {
                LazyVGrid(columns: [GridItem(.flexible(), spacing: 18), GridItem(.flexible(), spacing: 18)], spacing: 24) {
                    ForEach(library.state.playlists) { playlist in
                        NavigationLink { PlaylistDetail(playlistID: playlist.id) } label: {
                            VStack(alignment: .leading, spacing: 8) {
                                PlaylistArtwork(playlist: playlist).aspectRatio(1, contentMode: .fit)
                                Text(playlist.name).font(.system(size: 17, weight: .medium)).foregroundStyle(Brand.ink).lineLimit(1)
                                Text(playlist.description.isEmpty ? (playlist.isFavorites ? "把心动的旋律，藏进花里" : "写下一段歌单心情") : playlist.description)
                                    .font(.system(size: 12)).foregroundStyle(Brand.muted).lineLimit(2).frame(height: 32, alignment: .topLeading)
                                Text("\(playlist.songIDs.count) 首歌曲").font(.system(size: 12)).foregroundStyle(Brand.muted)
                            }
                        }.buttonStyle(.plain).contextMenu {
                            Button("编辑歌单", systemImage: "pencil") { editing = playlist }
                            if !playlist.isFavorites { Button("删除歌单", systemImage: "trash", role: .destructive) { deleting = playlist } }
                        }
                    }
                }.padding(.horizontal, 24).padding(.bottom, 24)
            }
        }.background(Brand.paper).toolbar(.hidden, for: .navigationBar)
            .preference(key: RootPagePreferenceKey.self, value: [1: rootVisible])
            .onAppear { rootVisible = true }.onDisappear { rootVisible = false }
            .alert("创建歌单", isPresented: $creating) { TextField("歌单名称", text: $name); Button("取消", role: .cancel) {}; Button("创建") { library.createPlaylist(name) } }
            .sheet(item: $editing) { PlaylistEditor(playlistID: $0.id) }
            .confirmationDialog("删除这个歌单？歌曲文件会保留。", isPresented: Binding(get: { deleting != nil }, set: { if !$0 { deleting = nil } }), titleVisibility: .visible) {
                Button("删除歌单", role: .destructive) { if let deleting { library.deletePlaylist(deleting.id) }; deleting = nil }
            }
    }
}

struct PlaylistDetail: View {
    @EnvironmentObject private var library: MusicLibrary
    @Environment(\.dismiss) private var dismiss
    var playlistID: String
    @State private var editSheet = false
    @State private var addSheet = false
    @State private var removeSheet = false
    @State private var deleting = false
    @State private var query = ""
    @State private var searching = false
    @FocusState private var searchFocused: Bool
    private var playlist: Playlist? { library.state.playlists.first { $0.id == playlistID } }
    private var songs: [Song] { library.search(library.playlistSongs(playlistID), query: query) }
    var body: some View {
        VStack(spacing: 0) {
            if let playlist {
                if searching {
                    HStack(spacing: 10) {
                        Image(systemName: "magnifyingglass").foregroundStyle(Brand.muted)
                        TextField("搜索歌曲、歌手或专辑", text: $query).focused($searchFocused).autocorrectionDisabled().submitLabel(.search)
                        Button { query = ""; withAnimation(.easeOut(duration: 0.25)) { searching = false } } label: { Image(systemName: "xmark.circle.fill").foregroundStyle(Brand.muted) }.accessibilityLabel("收起搜索")
                    }.padding(12).background(Brand.ink.opacity(0.05), in: RoundedRectangle(cornerRadius: 14)).padding(.horizontal, 24).padding(.bottom, 14)
                        .transition(.opacity.combined(with: .move(edge: .trailing)))
                }
                HStack {
                    Text(query.isEmpty ? "共 \(library.playlistSongs(playlistID).count) 首" : "找到 \(songs.count) 首").font(.caption).foregroundStyle(Brand.muted)
                    Spacer(); SortMenu(sort: playlist.sort, playlistID: playlistID)
                }.padding(.horizontal, 24).padding(.vertical, 12)
                SongList(songs: songs, playlistID: playlistID, header: AnyView(playlistHeader(playlist)))
            }
        }.background(Brand.paper).navigationTitle(playlist?.name ?? "歌单").navigationBarTitleDisplayMode(.inline).toolbar(.visible, for: .navigationBar)
            .toolbar {
                ToolbarItemGroup(placement: .topBarTrailing) {
                    Button { withAnimation(.easeOut(duration: 0.25)) { searching = true }; searchFocused = true } label: { Image(systemName: "magnifyingglass") }.accessibilityLabel("搜索歌单")
                    Menu {
                        Button("编辑歌单", systemImage: "pencil") { editSheet = true }
                        Button("批量添加歌曲", systemImage: "text.badge.plus") { addSheet = true }
                        Button("批量删除歌曲", systemImage: "minus.circle") { removeSheet = true }.disabled(songs.isEmpty)
                        if playlist?.isFavorites == false { Button("删除歌单", systemImage: "trash", role: .destructive) { deleting = true } }
                    } label: { Image(systemName: "ellipsis").foregroundStyle(Brand.muted.opacity(0.6)) }.accessibilityLabel("歌单操作")
                }
            }
            .sheet(isPresented: $editSheet) { PlaylistEditor(playlistID: playlistID) }
            .sheet(isPresented: $addSheet) { SelectSongs(playlistID: playlistID) }
            .sheet(isPresented: $removeSheet) { SelectSongs(playlistID: playlistID, removing: true) }
            .confirmationDialog("删除这个歌单？歌曲文件会保留。", isPresented: $deleting, titleVisibility: .visible) {
                Button("删除歌单", role: .destructive) { library.deletePlaylist(playlistID); dismiss() }
            }
    }
    private func playlistHeader(_ playlist: Playlist) -> some View {
        HStack(alignment: .top, spacing: 18) {
            PlaylistArtwork(playlist: playlist, pixels: 320).frame(width: 98, height: 98)
            VStack(alignment: .leading, spacing: 10) {
                Text(playlist.name).font(.system(size: 24, weight: .semibold)).lineLimit(2)
                Text(playlist.description.isEmpty ? (playlist.isFavorites ? "把心动的旋律，藏进花里" : "写下一段歌单心情") : playlist.description).font(.system(size: 13)).foregroundStyle(Brand.muted)
                Text("共 \(playlist.songIDs.count) 首歌曲").font(.caption).foregroundStyle(Brand.muted)
            }.frame(maxWidth: .infinity, alignment: .leading)
        }.padding(.horizontal, 24).padding(.top, 8).padding(.bottom, 24)
    }
}

struct PlaylistEditor: View {
    @EnvironmentObject private var library: MusicLibrary
    @Environment(\.dismiss) private var dismiss
    var playlistID: String
    @State private var name = ""
    @State private var description = ""
    @State private var coverSheet = false
    private var playlist: Playlist? { library.state.playlists.first { $0.id == playlistID } }
    var body: some View {
        NavigationStack {
            Form {
                if let playlist {
                    Section {
                        Button { coverSheet = true } label: {
                            HStack(spacing: 18) { PlaylistArtwork(playlist: playlist, pixels: 320).frame(width: 96, height: 96); Label("更换歌单封面", systemImage: "photo") }
                        }.buttonStyle(.plain).padding(.vertical, 6)
                    }
                    Section("歌单名称") { TextField("歌单名称", text: $name).disabled(playlist.isFavorites) }
                    Section("歌单描述") { TextEditor(text: $description).frame(minHeight: 120) }
                }
            }.navigationTitle("编辑歌单").navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) { Button("取消") { dismiss() } }
                    ToolbarItem(placement: .confirmationAction) { Button("保存") { library.editPlaylist(id: playlistID, name: name, description: description); dismiss() }.disabled(name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty) }
                }
                .onAppear { if let playlist { name = playlist.name; description = playlist.description } }
                .sheet(isPresented: $coverSheet) { CoverPicker(playlistID: playlistID) }
        }
    }
}

/// Native List keeps stable song identities while scrolling or selecting a thousand tracks.
struct SelectSongs: View {
    @EnvironmentObject private var library: MusicLibrary
    @Environment(\.dismiss) private var dismiss
    var playlistID: String
    var removing = false
    @State private var selected = Set<String>()
    @State private var query = ""
    @State private var candidates: [Song] = []
    private var filtered: [Song] { library.search(candidates, query: query) }
    var body: some View {
        NavigationStack {
            List(filtered) { song in
                Button { if !selected.insert(song.id).inserted { selected.remove(song.id) } } label: {
                    HStack(spacing: 12) {
                        Artwork(name: song.artwork, radius: 10, pixels: 128).frame(width: 40, height: 40)
                        VStack(alignment: .leading, spacing: 5) { Text(song.title).foregroundStyle(Brand.ink).lineLimit(1); Text(song.subtitle).font(.caption).foregroundStyle(Brand.muted).lineLimit(1) }
                        Spacer(); Image(systemName: selected.contains(song.id) ? "checkmark.circle.fill" : "circle")
                    }.padding(.vertical, 4)
                }.buttonStyle(.plain)
            }.searchable(text: $query, prompt: "搜索歌曲、歌手或专辑").navigationTitle(removing ? "删除歌单歌曲" : "添加歌曲").navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) { Button("取消") { dismiss() } }
                    ToolbarItem(placement: .topBarLeading) { Button(selected.count == filtered.count && !filtered.isEmpty ? "取消全选" : "全选") { let ids = Set(filtered.map(\.id)); if ids.isSubset(of: selected) { selected.subtract(ids) } else { selected.formUnion(ids) } } }
                    ToolbarItem(placement: .confirmationAction) { Button("\(removing ? "移除" : "加入") \(selected.count) 首") {
                        let ids = candidates.map(\.id).filter { selected.contains($0) }
                        if removing { library.remove(ids, from: playlistID) } else { library.add(ids, to: playlistID) }; dismiss()
                    }.disabled(selected.isEmpty) }
                }
                .onAppear { candidates = removing ? library.playlistSongs(playlistID) : library.orderedSongs }
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
                        ForEach(CoverStyle.images.indices, id: \.self) { index in
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
                    guard let item else { return }; saving = true
                    Task {
                        do {
                            guard let data = try await item.loadTransferable(type: Data.self) else { throw LibraryError.unsupported }
                            let name = try await library.files.savePhoto(data)
                            library.update { state in if let p = state.playlists.firstIndex(where: { $0.id == playlistID }) { state.playlists[p].coverPhoto = name } }; dismiss()
                        } catch { photoError = error.localizedDescription }; saving = false
                    }
                }
                .alert("照片导入失败", isPresented: Binding(get: { photoError != nil }, set: { if !$0 { photoError = nil } })) { Button("知道了", role: .cancel) { photoError = nil } } message: { Text(photoError ?? "") }
        }
    }
}
