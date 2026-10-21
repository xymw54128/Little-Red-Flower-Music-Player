import Foundation
import Combine

@MainActor
final class MusicLibrary: ObservableObject {
    @Published private(set) var state = MusicState()
    @Published private(set) var ready = false
    @Published private(set) var scanning = false
    @Published private(set) var visibleScan = false
    @Published private(set) var orderGeneration: UInt64 = 0
    // Explicit user sorts are synchronous; background cache completion cannot
    // override a later mode choice or a manual queue edit.
    @Published private(set) var playbackOrderSource: String?
    @Published var message: String?
    @Published private var orderedCache: [Song] = []
    @Published private var playlistCache: [String: [Song]] = [:]
    let files = FileLibrary()
    private var starting = false
    private var revision: UInt64 = 0
    private var playbackRevision: UInt64 = 0
    private var orderRevision: UInt64 = 0
    private var pendingSave: Task<Void, Never>?
    private var orderTask: Task<Void, Never>?
    private var playbackState: PlaybackState?
    private var songIndex: [String: Song] = [:]
    private var searchIndex: [String: SongSearchEntry] = [:]
    private var favoriteIDs = Set<String>()

    func start() async {
        guard !ready, !starting else { return }
        starting = true
        defer { starting = false }
        do { state = try await files.load() }
        catch { message = "索引读取失败，原文件保留：" + error.localizedDescription }
        #if DEBUG
        if ProcessInfo.processInfo.environment["XIAOHONGHUA_UI_TESTING"] == "1" {
            do { state = try DebugFixtures.make() } catch { message = error.localizedDescription }
        }
        #endif
        if !state.sources.contains(where: \.isInternal) {
            state.sources.insert(MusicSource(id: "internal-music", name: "小红花 / Music", isFolder: true, isInternal: true), at: 0)
        }
        state.normalize()
        rebuildIndex()
        orderRevision += 1
        await rebuildOrder(state, token: orderRevision)
        ready = true
        // Changes to files and all tags are inspected only when the user refreshes.
    }
    func update(_ change: (inout MusicState) -> Void) {
        let songs = state.songs, playlists = state.playlists
        let sort = state.sort, custom = state.customIDs, random = state.randomIDs
        change(&state)
        let collectionChanged = songs != state.songs || playlists != state.playlists
        if collectionChanged { state.normalize(); rebuildIndex() }
        else { state.settings.normalize() }
        if collectionChanged || sort != state.sort || custom != state.customIDs || random != state.randomIDs {
            orderTask?.cancel()
            let snapshot = state
            orderRevision += 1
            let token = orderRevision
            orderTask = Task {
                // Coalesce rapid edits before starting another thousand-row sort.
                try? await Task.sleep(for: .milliseconds(16))
                guard !Task.isCancelled else { return }
                await rebuildOrder(snapshot, token: token)
            }
        }
        persist()
    }
    private func rebuildOrder(_ snapshot: MusicState, token: UInt64) async {
        let previousSearch = searchIndex
        let prepared = await Task.detached(priority: .userInitiated) {
            let songs = MusicOrder.ordered(snapshot.songs, sort: snapshot.sort, ids: snapshot.sort == .random ? snapshot.randomIDs : snapshot.customIDs)
            let byID = Dictionary(snapshot.songs.map { ($0.id, $0) }, uniquingKeysWith: { _, latest in latest })
            var playlists: [String: [Song]] = [:]
            for playlist in snapshot.playlists {
                playlists[playlist.id] = MusicOrder.ordered(playlist.songIDs.compactMap { byID[$0] }, sort: playlist.sort, ids: playlist.sort == .random ? playlist.randomIDs : playlist.songIDs)
            }
            let search = Dictionary(snapshot.songs.map { song in
                if let cached = previousSearch[song.id], cached.represents(song) { return (song.id, cached) }
                return (song.id, SongSearchEntry(song))
            }, uniquingKeysWith: { _, latest in latest })
            return (songs, playlists, search)
        }.value
        guard !Task.isCancelled, token == orderRevision else { return }
        orderedCache = prepared.0
        playlistCache = prepared.1
        searchIndex = prepared.2
        orderGeneration += 1
    }
    private func rebuildIndex() {
        songIndex = Dictionary(state.songs.map { ($0.id, $0) }, uniquingKeysWith: { _, latest in latest })
        favoriteIDs = Set(state.playlists.first(where: \.isFavorites)?.songIDs ?? [])
    }
    func song(_ id: String) -> Song? { songIndex[id] }
    func search(_ songs: [Song], query: String) -> [Song] {
        let tokens = SongSearchEntry.normalized(query).split(whereSeparator: { $0.isWhitespace }).map(String.init)
        guard !tokens.isEmpty else { return songs }
        return songs.filter { song in
            if let entry = searchIndex[song.id] { return entry.matches(tokens) }
            let original = SongSearchEntry.normalized(song.title + " " + song.artist + " " + song.album + " " + song.albumArtist)
            return tokens.allSatisfy { original.contains($0) }
        }
    }
    func recordPlayback(queue: [String], id: String?, position: Double, mode: PlayMode, sourceID: String? = nil) {
        guard ready else { return }
        let clock = PlaybackState(queueIDs: MusicOrder.uniqueIDs(queue), currentID: id, position: position.isFinite ? max(0, position) : 0, mode: mode, queueSource: sourceID)
        playbackState = clock
        playbackRevision += 1
        let token = playbackRevision
        Task {
            do { try await files.savePlayback(clock, revision: token) }
            catch { message = "播放进度保存失败：" + error.localizedDescription }
        }
    }
    func persist(immediate: Bool = false) {
        guard ready else { return }
        pendingSave?.cancel()
        revision += 1
        var snapshot = state
        playbackState?.apply(to: &snapshot)
        let token = revision
        pendingSave = Task {
            if !immediate { try? await Task.sleep(for: .milliseconds(350)) }
            guard !Task.isCancelled else { return }
            do { try await files.save(snapshot, revision: token) }
            catch { message = "保存失败：" + error.localizedDescription }
        }
    }
    func refresh(visible: Bool, force: Bool = true) async {
        guard ready, !scanning else { return }
        scanning = true; visibleScan = visible
        defer { scanning = false; visibleScan = false }
        do {
            // A manual refresh always checks tags, even when timestamps and file sizes match.
            let result = try await files.scan(sources: state.sources, previous: state.songs, force: true)
            update { $0.songs = result.songs; $0.sources = result.sources }
            await orderTask?.value
            if visible, !result.errors.isEmpty { message = result.errors.joined(separator: "\n") }
        } catch is CancellationError { }
        catch { message = error.localizedDescription }
    }
    func importURLs(_ urls: [URL], folder: Bool, replacingID: String? = nil) async {
        guard ready, !scanning else { message = "请等待当前扫描结束。"; return }
        scanning = true; visibleScan = true
        defer { scanning = false; visibleScan = false }
        do {
            var known = await files.locations(state.sources.filter { $0.id != replacingID })
            var imported: [MusicSource] = []
            for url in (replacingID == nil ? urls : Array(urls.prefix(1))) {
                try Task.checkCancellation()
                if !known.insert(url.standardizedFileURL.path).inserted { continue }
                imported.append(try await files.source(url, folder: folder, replacingID: replacingID))
            }
            guard !imported.isEmpty else { return }
            let result = try await files.scan(sources: imported, previous: state.songs, force: true)
            let importedIDs = Set(imported.map(\.id))
            // One publication and save for an entire import, not one per selected file.
            update { state in
                state.sources.removeAll { importedIDs.contains($0.id) }
                state.sources += result.sources
                state.songs = MusicOrder.reconcile(previous: state.songs, scanned: result.songs, sourceIDs: importedIDs)
            }
            await orderTask?.value
            if !result.errors.isEmpty { message = result.errors.joined(separator: "\n") }
        } catch is CancellationError { }
        catch { message = error.localizedDescription }
    }
    func removeSource(_ id: String) {
        guard !scanning else { message = "请等待当前扫描结束。"; return }
        guard let source = state.sources.first(where: { $0.id == id }), !source.isInternal else { return }
        update { state in
            state.sources.removeAll { $0.id == id }
            state.songs.removeAll { $0.sourceID == id }
        }
    }
    func playlistSongs(_ id: String) -> [Song] { playlistCache[id] ?? [] }
    var orderedSongs: [Song] { orderedCache }
    func sort(_ sort: SongSort, playlistID: String? = nil) {
        if let playlistID, !state.playlists.contains(where: { $0.id == playlistID }) { return }
        update {
            if let playlistID, let index = $0.playlists.firstIndex(where: { $0.id == playlistID }) {
                $0.playlists[index].sort = sort
                if sort == .random { $0.playlists[index].randomIDs = $0.playlists[index].songIDs.shuffled() }
            } else {
                $0.sort = sort
                if sort == .random { $0.randomIDs = $0.songs.map(\.id).shuffled() }
            }
        }
        playbackOrderSource = playlistID ?? "library"
    }
    func saveOrder(_ ids: [String], playlistID: String? = nil) {
        if let playlistID, !state.playlists.contains(where: { $0.id == playlistID }) { return }
        update {
            if let playlistID, let index = $0.playlists.firstIndex(where: { $0.id == playlistID }) {
                $0.playlists[index].songIDs = MusicOrder.mergedIDs(ids, available: $0.playlists[index].songIDs)
                $0.playlists[index].sort = .custom
            } else { $0.customIDs = MusicOrder.mergedIDs(ids, available: $0.songs.map(\.id)); $0.sort = .custom }
        }
        playbackOrderSource = playlistID ?? "library"
    }
    func createPlaylist(_ name: String, description: String = "") {
        let trimmed = name.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        update { $0.playlists.append(Playlist(name: trimmed, coverPreset: $0.playlists.filter { !$0.isFavorites }.count % 10, description: description)) }
    }
    func editPlaylist(id: String, name: String, description: String) {
        let name = name.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !name.isEmpty else { return }
        update {
            guard let index = $0.playlists.firstIndex(where: { $0.id == id }) else { return }
            if !$0.playlists[index].isFavorites { $0.playlists[index].name = name }
            $0.playlists[index].description = description.trimmingCharacters(in: .whitespacesAndNewlines)
        }
    }
    func add(_ songs: [String], to playlistID: String) {
        update {
            guard let index = $0.playlists.firstIndex(where: { $0.id == playlistID }) else { return }
            let valid = Set($0.songs.map(\.id))
            var existing = Set($0.playlists[index].songIDs)
            $0.playlists[index].songIDs += songs.filter { valid.contains($0) && existing.insert($0).inserted }
        }
    }
    func isFavorite(_ id: String) -> Bool { favoriteIDs.contains(id) }
    func toggleFavorite(_ id: String) {
        guard songIndex[id] != nil else { return }
        if isFavorite(id) { remove([id], from: Playlist.favoritesID) }
        else { add([id], to: Playlist.favoritesID) }
    }
    func remove(_ ids: [String], from playlistID: String) {
        let removing = Set(ids)
        update {
            guard let index = $0.playlists.firstIndex(where: { $0.id == playlistID }) else { return }
            $0.playlists[index].songIDs.removeAll { removing.contains($0) }
        }
    }
    func deletePlaylist(_ id: String) {
        guard id != Playlist.favoritesID else { return }
        update { $0.playlists.removeAll { $0.id == id } }
    }
    func importLyrics(_ url: URL, songID: String) async {
        do { let text = try await files.readLyrics(url); update { $0.manualLyrics[songID] = text } }
        catch { message = error.localizedDescription }
    }
    func restoreBackup(_ url: URL) async {
        guard !scanning else { message = "请等待当前扫描结束。"; return }
        do {
            let backup = try await files.readBackup(url)
            var count = 0
            update { count = MusicOrder.restore(backup, into: &$0) }
            message = "已恢复歌单与设置，匹配 \(count) 首歌曲。未匹配歌曲请先导入，再重新恢复。"
        } catch { message = error.localizedDescription }
    }
}
