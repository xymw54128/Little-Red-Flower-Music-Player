import SwiftUI
import UniformTypeIdentifiers

struct LibraryView: View {
    @EnvironmentObject private var library: MusicLibrary
    @EnvironmentObject private var playback: Playback
    var hiddenFlower: Bool
    @State private var category = 0
    @State private var query = ""
    @State private var importing = false
    @State private var folder = false
    @State private var importMenu = false
    var songs: [Song] {
        library.orderedSongs.filter { query.isEmpty || ($0.title + " " + $0.artist + " " + $0.album).localizedCaseInsensitiveContains(query) }
    }
    var body: some View {
        VStack(spacing: 0) {
            FlowerHeader(title: "音乐库", hiddenFlower: hiddenFlower) { importMenu = true }
            Picker("分类", selection: $category) { Text("歌曲").tag(0); Text("专辑").tag(1); Text("歌手").tag(2) }
                .pickerStyle(.segmented).padding(.horizontal, 24).padding(.bottom, 16)
            HStack(spacing: 12) {
                Image(systemName: "magnifyingglass").foregroundStyle(Brand.muted)
                TextField("搜索歌曲、歌手或专辑", text: $query).font(.system(size: 15)).submitLabel(.search).autocorrectionDisabled()
                if !query.isEmpty { Button { query = "" } label: { Image(systemName: "xmark.circle.fill").foregroundStyle(Brand.muted) }.accessibilityLabel("清除搜索") }
            }.padding(15).background(Color.white.opacity(0.6), in: RoundedRectangle(cornerRadius: 16))
                .overlay(RoundedRectangle(cornerRadius: 16).strokeBorder(Brand.ink.opacity(0.16), lineWidth: 0.8)).padding(.horizontal, 24)
            HStack {
                Text(query.isEmpty ? "共 \(library.state.songs.count) 首" : "找到 \(songs.count) / \(library.state.songs.count) 首").font(.system(size: 12)).foregroundStyle(Brand.muted)
                Spacer()
                SortMenu(sort: library.state.sort)
            }.padding(.horizontal, 24).padding(.top, 21).padding(.bottom, 15)
            if library.visibleScan { ProgressView().progressViewStyle(.linear).padding(.horizontal, 24).padding(.bottom, 8).accessibilityLabel("正在导入音乐") }
            if songs.isEmpty {
                EmptyMusic(title: query.isEmpty ? "让音乐在这里开花" : "没有找到歌曲", detail: query.isEmpty ? "点右上角 ＋ 导入歌曲或文件夹\n也可在“文件”里的小红花 / Music 放入音乐" : "换个关键词试试")
            } else if category == 0 {
                HStack { PillButton(title: "播放全部", symbol: "play") { if let first = songs.first { playback.play(first, within: songs.map(\.id)) } }; Spacer() }
                    .padding(.horizontal, 24).padding(.bottom, 12)
                SongList(songs: songs)
            } else {
                let grouped = Dictionary(grouping: songs, by: { category == 1 ? $0.album : $0.artist })
                ScrollView {
                    LazyVStack(spacing: 0) {
                        ForEach(grouped.keys.sorted { $0.localizedStandardCompare($1) == .orderedAscending }, id: \.self) { key in
                            NavigationLink { GroupSongsView(title: key, ids: (grouped[key] ?? []).map(\.id)) } label: {
                                HStack(spacing: 14) {
                                    Artwork(name: grouped[key]?.first?.artwork ?? "").frame(width: 58, height: 58)
                                    VStack(alignment: .leading, spacing: 7) { Text(key).font(.system(size: 17)); Text("\(grouped[key]?.count ?? 0) 首歌曲").font(.caption).foregroundStyle(Brand.muted) }
                                    Spacer(); Image(systemName: "chevron.right").font(.caption).foregroundStyle(Brand.muted)
                                }.padding(.horizontal, 24).padding(.vertical, 12).foregroundStyle(Brand.ink)
                            }.buttonStyle(.plain)
                        }
                    }
                }
            }
        }.background(Brand.paper).toolbar(.hidden, for: .navigationBar)
            .confirmationDialog("导入本地音乐", isPresented: $importMenu, titleVisibility: .visible) {
                Button("选择音乐文件") { folder = false; importing = true }
                Button("选择音乐文件夹") { folder = true; importing = true }
            } message: { Text("文件夹直接读取，不复制整库。请先把云端文件下载到 iPhone。") }
            .fileImporter(isPresented: $importing, allowedContentTypes: folder ? [.folder] : [.audio], allowsMultipleSelection: !folder) { result in
                switch result {
                case .success(let urls): Task { await library.importURLs(urls, folder: folder) }
                case .failure(let error): library.message = error.localizedDescription
                }
            }
    }
}

struct GroupSongsView: View {
    @EnvironmentObject private var library: MusicLibrary
    @EnvironmentObject private var playback: Playback
    var title: String
    var ids: [String]
    var songs: [Song] { library.orderedSongs.filter { ids.contains($0.id) } }
    var body: some View {
        VStack(spacing: 12) {
            HStack { Text("共 \(songs.count) 首").font(.caption).foregroundStyle(Brand.muted); Spacer(); PillButton(title: "播放全部", symbol: "play") { if let first = songs.first { playback.play(first, within: songs.map(\.id)) } } }.padding(.horizontal, 24)
            SongList(songs: songs)
        }.background(Brand.paper).navigationTitle(title).navigationBarTitleDisplayMode(.inline).toolbar(.visible, for: .navigationBar)
    }
}
