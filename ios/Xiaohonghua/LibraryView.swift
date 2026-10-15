import SwiftUI

struct LibraryView: View {
    @EnvironmentObject private var library: MusicLibrary
    @State private var rootVisible = false
    var hiddenFlower: Bool
    @State private var category = 0
    @State private var query = ""
    private var songs: [Song] {
        library.search(library.orderedSongs, query: query)
    }
    var body: some View {
        VStack(spacing: 0) {
            FlowerHeader(title: "音乐库", hiddenFlower: hiddenFlower, searchText: $query)
            HStack(spacing: 10) {
                ForEach(Array(["歌曲", "专辑", "歌手"].enumerated()), id: \.offset) { index, title in
                    Button { withAnimation(.easeOut(duration: 0.18)) { category = index } } label: {
                        Text(title).font(.system(size: 16, weight: .medium)).frame(width: 70, height: 44)
                            .foregroundStyle(category == index ? Color.white : Brand.ink)
                            .background(category == index ? Brand.accent(library.state.settings) : .clear, in: RoundedRectangle(cornerRadius: 13))
                            .overlay { if category != index { RoundedRectangle(cornerRadius: 13).strokeBorder(Brand.ink.opacity(0.12), lineWidth: 1) } }
                    }.buttonStyle(.plain).accessibilityAddTraits(category == index ? .isSelected : [])
                }
                Spacer(minLength: 0)
            }.padding(.horizontal, 24).padding(.bottom, 16)
            HStack {
                Text(query.isEmpty ? "共 \(library.state.songs.count) 首" : "找到 \(songs.count) / \(library.state.songs.count) 首").font(.system(size: 12)).foregroundStyle(Brand.muted)
                Spacer(); SortMenu(sort: library.state.sort)
            }.padding(.horizontal, 24).padding(.bottom, 15)
            if library.visibleScan { ProgressView().progressViewStyle(.linear).padding(.horizontal, 24).padding(.bottom, 8).accessibilityLabel("正在导入音乐") }
            if songs.isEmpty {
                EmptyMusic(title: query.isEmpty ? "让音乐在这里开花" : "没有找到歌曲", detail: query.isEmpty ? "前往设置导入歌曲或文件夹\n也可在“文件”里的小红花 / Music 放入音乐" : "换个关键词试试")
            } else if category == 0 { SongList(songs: songs, queueSource: query.isEmpty ? "library" : nil) }
            else { SongGroups(songs: songs, category: category) }
        }.background(Brand.paper).toolbar(.hidden, for: .navigationBar)
            .preference(key: RootPagePreferenceKey.self, value: [0: rootVisible])
            .onAppear { rootVisible = true }.onDisappear { rootVisible = false }
    }
}

private struct SongGroups: View {
    var songs: [Song]
    var category: Int
    @State private var grouped: [String: [Song]] = [:]
    @State private var keys: [String] = []
    var body: some View {
        ScrollView {
            LazyVStack(spacing: 0) {
                ForEach(keys, id: \.self) { key in
                    let first = grouped[key]?.first
                    let title = category == 1 ? first?.album ?? key : key
                    let artist = first.map { $0.albumArtist.isEmpty ? $0.artist : $0.albumArtist } ?? ""
                    let count = "\(grouped[key]?.count ?? 0) 首歌曲"
                    NavigationLink { GroupSongsView(title: title, ids: (grouped[key] ?? []).map(\.id), album: category == 1) } label: {
                        HStack(spacing: 14) {
                            Artwork(name: grouped[key]?.first?.artwork ?? "", pixels: 192).frame(width: 58, height: 58)
                            VStack(alignment: .leading, spacing: 7) { Text(title).font(.system(size: 17)); Text(category == 1 ? artist + " · " + count : count).font(.caption).foregroundStyle(Brand.muted) }
                            Spacer(); Image(systemName: "chevron.right").font(.caption).foregroundStyle(Brand.muted)
                        }.padding(.horizontal, 24).padding(.vertical, 12).foregroundStyle(Brand.ink)
                    }.buttonStyle(.plain)
                }
            }
        }.onAppear(perform: rebuild).onChange(of: songs) { _, _ in rebuild() }.onChange(of: category) { _, _ in rebuild() }
    }
    private func rebuild() {
        grouped = Dictionary(grouping: songs, by: { category == 1 ? $0.albumKey : $0.artist })
        keys = grouped.keys.sorted { left, right in
            let a = category == 1 ? grouped[left]?.first?.album ?? left : left
            let b = category == 1 ? grouped[right]?.first?.album ?? right : right
            return a == b ? left < right : a.localizedStandardCompare(b) == .orderedAscending
        }
    }
}

struct GroupSongsView: View {
    @EnvironmentObject private var library: MusicLibrary
    var title: String
    var ids: [String]
    var album = false
    @State private var songs: [Song] = []
    var body: some View {
        VStack(spacing: 12) {
            HStack { Text("共 \(songs.count) 首").font(.caption).foregroundStyle(Brand.muted); Spacer() }.padding(.horizontal, 24)
            SongList(songs: songs, queueSource: nil)
        }.background(Brand.paper).navigationTitle(title).navigationBarTitleDisplayMode(.inline).toolbar(.visible, for: .navigationBar)
            .task(id: library.orderGeneration) {
                let all = library.orderedSongs, allowed = Set(ids), isAlbum = album
                let prepared = await Task.detached(priority: .userInitiated) {
                    let group = all.filter { allowed.contains($0.id) }
                    return isAlbum ? MusicOrder.albumSongs(group) : group
                }.value
                guard !Task.isCancelled else { return }; songs = prepared
            }
    }
}
