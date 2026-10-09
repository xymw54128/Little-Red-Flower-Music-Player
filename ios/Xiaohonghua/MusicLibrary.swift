import Foundation
import Combine

@MainActor
final class MusicLibrary: ObservableObject {
    @Published private(set) var state = MusicState()
    @Published private(set) var ready = false
    @Published private(set) var scanning = false
    @Published private(set) var visibleScan = false
    @Published var message: String?
    let files = FileLibrary()
    private var revision: UInt64 = 0
    private var pendingSave: Task<Void, Never>?
    private var lastForcedScan = Date.distantPast
    private var orderedCache: [Song] = []
    private var playbackState: (queue: [String], id: String?, position: Double, mode: PlayMode)?

    func start() async {
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
        rebuildOrder()
        ready = true
        await refresh(visible: false)
    }
    func update(_ change: (inout MusicState) -> Void) {
        let songs = state.songs, sort = state.sort, custom = state.customIDs, random = state.randomIDs
        change(&state)
        state.settings.normalize()
        if songs != state.songs || sort != state.sort || custom != state.customIDs || random != state.randomIDs { rebuildOrder() }
        persist()
    }
    func recordPlayback(queue: [String], id: String?, position: Double, mode: PlayMode) {
        // Persist the player clock without publishing library changes to every page.
        playbackState = (queue, id, position, mode)
        persist()
    }
    func persist(immediate: Bool = false) {
        pendingSave?.cancel()
        revision += 1
        var snapshot = state
        if let playbackState {
            snapshot.queueIDs = playbackState.queue; snapshot.currentID = playbackState.id
            snapshot.position = playbackState.position; snapshot.mode = playbackState.mode
        }
        let token = revision
        pendingSave = Task {
            if !immediate { try? await Task.sleep(for: .milliseconds(350)) }
            guard !Task.isCancelled else { return }
            do { try await files.save(snapshot, revision: token) }
            catch { message = "保存失败：" + error.localizedDescription }
        }
    }
    func refresh(visible: Bool, force: Bool = false) async {
        guard ready, !scanning else { return }
        scanning = true; visibleScan = visible
        defer { scanning = false; visibleScan = false }
        let forced = force || Date().timeIntervalSince(lastForcedScan) >= 60
        if forced { lastForcedScan = Date() }
        let result = await files.scan(sources: state.sources, previous: state.songs, force: forced)
        if state.songs != result.songs || state.sources != result.sources {
            update { $0.songs = result.songs; $0.sources = result.sources }
        }
        if visible, !result.errors.isEmpty { message = result.errors.joined(separator: "\n") }
    }
    func importURLs(_ urls: [URL], folder: Bool, replacingID: String? = nil) async {
        guard !scanning else { message = "请等待当前扫描结束。"; return }
        do {
            var known = await files.locations(state.sources.filter { $0.id != replacingID })
            for url in (replacingID == nil ? urls : Array(urls.prefix(1))) {
                if !known.insert(url.standardizedFileURL.path).inserted { continue }
                let source = try await files.source(url, folder: folder, replacingID: replacingID)
                update {
                    if let index = $0.sources.firstIndex(where: { $0.id == source.id }) { $0.sources[index] = source }
                    else { $0.sources.append(source) }
                }
            }
            await refresh(visible: true, force: true)
        } catch { message = error.localizedDescription }
    }
    func removeSource(_ id: String) {
        update { state in
            state.sources.removeAll { $0.id == id && !$0.isInternal }
            state.songs.removeAll { $0.sourceID == id }
        }
    }
    func playlistSongs(_ id: String) -> [Song] {
        guard let p = state.playlists.first(where: { $0.id == id }) else { return [] }
        let byID = Dictionary(state.songs.map { ($0.id, $0) }, uniquingKeysWith: { first, _ in first })
        return MusicOrder.ordered(p.songIDs.compactMap { byID[$0] }, sort: p.sort, ids: p.sort == .random ? p.randomIDs : p.songIDs)
    }
    var orderedSongs: [Song] { orderedCache }
    private func rebuildOrder() { orderedCache = MusicOrder.ordered(state.songs, sort: state.sort, ids: state.sort == .random ? state.randomIDs : state.customIDs) }
    func sort(_ sort: SongSort, playlistID: String? = nil) {
        update {
            if let playlistID, let index = $0.playlists.firstIndex(where: { $0.id == playlistID }) {
                $0.playlists[index].sort = sort
                if sort == .random { $0.playlists[index].randomIDs = $0.playlists[index].songIDs.shuffled() }
            } else {
                $0.sort = sort
                if sort == .random { $0.randomIDs = $0.songs.map(\.id).shuffled() }
            }
        }
    }
    func saveOrder(_ ids: [String], playlistID: String? = nil) {
        update {
            if let playlistID, let index = $0.playlists.firstIndex(where: { $0.id == playlistID }) {
                $0.playlists[index].songIDs = ids; $0.playlists[index].sort = .custom
            } else { $0.customIDs = ids; $0.sort = .custom }
        }
    }
    func createPlaylist(_ name: String) {
        let trimmed = name.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        update { $0.playlists.append(Playlist(name: trimmed, coverPreset: $0.playlists.count % 8)) }
    }
    func add(_ songs: [String], to playlistID: String) {
        update {
            guard let index = $0.playlists.firstIndex(where: { $0.id == playlistID }) else { return }
            let existing = Set($0.playlists[index].songIDs)
            $0.playlists[index].songIDs += songs.filter { !existing.contains($0) }
        }
    }
    func importLyrics(_ url: URL, songID: String) async {
        do { let text = try await files.readLyrics(url); update { $0.manualLyrics[songID] = text } }
        catch { message = error.localizedDescription }
    }
    func restoreBackup(_ url: URL) async {
        do {
            let backup = try await files.readBackup(url)
            var count = 0
            update { count = MusicOrder.restore(backup, into: &$0) }
            message = "已恢复歌单与设置，匹配 \(count) 首歌曲。未匹配歌曲请先导入，再重新恢复。"
        } catch { message = error.localizedDescription }
    }
}
