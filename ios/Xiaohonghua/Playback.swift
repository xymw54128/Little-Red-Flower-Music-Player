import Foundation
import AVFoundation
import MediaPlayer
import Combine
import UIKit

/// High-frequency progress is isolated from library, navigation and queue state.
@MainActor
final class PlaybackProgress: ObservableObject {
    @Published var position: Double = 0
    @Published var duration: Double = 0
}

@MainActor
final class Playback: NSObject, ObservableObject, AVAudioPlayerDelegate {
    @Published private(set) var current: Song?
    @Published private(set) var playing = false
    @Published private(set) var loading = false
    @Published private(set) var queue: [String] = []
    @Published private(set) var queueSource: String?
    @Published private(set) var mode: PlayMode = .repeatAll
    let progress = PlaybackProgress()
    private(set) var audio: AVAudioPlayer?
    private(set) var seekGeneration: UInt64 = 0
    private var access: ScopedFile?
    private weak var library: MusicLibrary?
    private var ticker: AnyCancellable?
    private var libraryObservation: AnyCancellable?
    private var orderObservation: AnyCancellable?
    private var metadataSync: Task<Void, Never>?
    private var artworkTask: Task<Void, Never>?
    private var artworkGeneration: UInt64 = 0
    private var lockScreenArtwork: (name: String, image: UIImage)?
    private var observations: [NSObjectProtocol] = []
    private var commandTargets: [(MPRemoteCommand, Any)] = []
    private var loadGeneration = 0
    private var lastSaved = Date.distantPast
    private var resumeAfterInterruption = false
    private var history: [String] = []
    private var foreground = true

    func connect(_ library: MusicLibrary) {
        guard self.library == nil else { return }
        self.library = library
        queue = library.state.queueIDs
        queueSource = library.state.queueSource
        mode = library.state.mode
        if let id = library.state.currentID, let song = library.song(id) {
            current = song
            progress.position = max(0, library.state.position)
            progress.duration = song.duration
        }
        libraryObservation = library.$state.dropFirst().sink { [weak self] _ in
            self?.metadataSync?.cancel()
            self?.metadataSync = Task { @MainActor [weak self] in
                await Task.yield()
                guard !Task.isCancelled else { return }
                self?.updateCurrentMetadata()
                self?.refreshLockScreen()
            }
        }
        orderObservation = library.$orderGeneration.dropFirst().sink { [weak self] _ in
            self?.synchronizeSourceOrder()
        }
        startClock()
        observations.append(NotificationCenter.default.addObserver(forName: AVAudioSession.interruptionNotification, object: nil, queue: .main) { [weak self] note in
            Task { @MainActor in self?.interruption(note) }
        })
        observations.append(NotificationCenter.default.addObserver(forName: AVAudioSession.routeChangeNotification, object: nil, queue: .main) { [weak self] note in
            guard let raw = note.userInfo?[AVAudioSessionRouteChangeReasonKey] as? UInt,
                  AVAudioSession.RouteChangeReason(rawValue: raw) == .oldDeviceUnavailable else { return }
            Task { @MainActor in self?.pause() }
        })
        remoteCommands()
        refreshLockScreen()
    }
    func setForeground(_ value: Bool) {
        guard value != foreground else { return }
        foreground = value
        if let audio { progress.position = audio.currentTime }
        if library != nil { startClock() }
    }
    private func startClock() {
        ticker?.cancel()
        ticker = Timer.publish(every: foreground ? 0.1 : 5, on: .main, in: .common).autoconnect().sink { [weak self] _ in
            guard let self, let audio = self.audio else { return }
            self.progress.position = audio.currentTime
            if self.playing, Date().timeIntervalSince(self.lastSaved) >= 5 { self.savePosition() }
        }
    }

    func play(_ song: Song, within ids: [String]? = nil, sourceID: String? = nil, start: Double = 0, remember: Bool = true) {
        guard let library else { return }
        if let ids {
            let available = Set(library.state.songs.filter(\.available).map(\.id))
            queue = PlaybackOrder.unique(ids, available: available)
            queueSource = sourceID; history = []
        }
        if queue.isEmpty { queue = [song.id] }
        if remember, let previous = current?.id, previous != song.id {
            history.append(previous)
            if history.count > 1000 { history.removeFirst(history.count - 1000) }
        }
        audio?.stop(); audio = nil; access = nil
        playing = false; loading = true; current = song
        progress.position = max(0, start); progress.duration = song.duration
        refreshLockScreen()
        loadGeneration += 1
        let generation = loadGeneration
        guard let source = library.state.sources.first(where: { $0.id == song.sourceID }) else {
            loading = false; library.message = "请重新导入这首歌曲。"; return
        }
        Task {
            do {
                let prepared = try await Task.detached(priority: .userInitiated) {
                    let file = try ScopedFile(source: source, relativePath: song.relativePath)
                    let audio = try AVAudioPlayer(contentsOf: file.url)
                    guard audio.prepareToPlay() else { throw LibraryError.unsupported }
                    return (file, audio)
                }.value
                guard generation == loadGeneration else { return }
                let session = AVAudioSession.sharedInstance()
                try session.setCategory(.playback, mode: .default)
                try session.setActive(true)
                access = prepared.0; audio = prepared.1
                audio?.delegate = self
                progress.duration = prepared.1.duration
                audio?.currentTime = min(max(start, 0), prepared.1.duration)
                playing = prepared.1.play()
                loading = false
                if !playing { throw LibraryError.unsupported }
                savePosition(); refreshLockScreen()
            } catch {
                guard generation == loadGeneration else { return }
                loading = false; playing = false; audio = nil; access = nil
                refreshLockScreen()
                library.message = "\(song.title)：\(error.localizedDescription)\n如文件位于云端，请先在“文件”中下载到本机。"
            }
        }
    }
    func toggle() {
        if playing { pause() }
        else if let audio {
            do {
                try AVAudioSession.sharedInstance().setActive(true)
                playing = audio.play()
                if !playing { library?.message = LibraryError.unsupported.localizedDescription }
                refreshLockScreen()
            } catch { library?.message = error.localizedDescription }
        } else if let current { play(current, start: progress.position) }
    }
    func pause() {
        resumeAfterInterruption = false
        // Also invalidate an in-flight file preparation when the user pauses.
        if loading { loadGeneration += 1; loading = false }
        audio?.pause(); playing = false
        savePosition(); refreshLockScreen()
    }
    func seek(_ position: Double) {
        seekGeneration &+= 1
        let time = position.isFinite ? min(max(position, 0), progress.duration) : 0
        audio?.currentTime = time
        progress.position = time
        savePosition(); refreshLockScreen()
    }
    func previous() {
        guard let library, let current else { return }
        var id: String?
        if mode == .shuffle, let previous = history.popLast() { id = previous }
        else if let index = queue.firstIndex(of: current.id), index > 0 { id = queue[index - 1] }
        if let id, let song = library.song(id) { play(song, remember: false) }
        else { seek(0) }
    }
    func next(automatic: Bool = false) {
        guard let library, let current else { return }
        let availableIDs = Set(library.state.songs.filter(\.available).map(\.id))
        let available = queue.filter { availableIDs.contains($0) }
        guard !available.isEmpty else { pause(); return }
        if automatic, mode == .repeatOne { seek(0); if !playing { toggle() }; return }
        let id: String
        if mode == .shuffle { id = available.filter { $0 != current.id }.randomElement() ?? current.id }
        else {
            let index = available.firstIndex(of: current.id) ?? -1
            if automatic, mode == .ordered, index + 1 >= available.count { pause(); seek(progress.duration); return }
            id = available[(index + 1) % available.count]
        }
        if let song = library.song(id) { play(song) }
    }
    func cycleMode() {
        let modes = PlayMode.allCases
        mode = modes[((modes.firstIndex(of: mode) ?? 0) + 1) % modes.count]
        savePosition()
    }
    func setQueue(_ ids: [String]) {
        guard let library else { return }
        queueSource = nil
        queue = PlaybackOrder.unique(ids, available: Set(library.state.songs.filter(\.available).map(\.id)))
        savePosition()
    }
    func syncQueueSource(_ sourceID: String, ids: [String]) {
        guard queueSource == sourceID else { return }
        let updated = PlaybackOrder.synchronized(ids, currentID: current?.id)
        guard updated != queue else { return }
        queue = updated
        // Reordering changes the next track, never the opened AVAudioPlayer.
        savePosition()
    }
    private func synchronizeSourceOrder() {
        guard let library, let sourceID = queueSource else { return }
        if sourceID == "library" {
            syncQueueSource(sourceID, ids: library.orderedSongs.filter(\.available).map(\.id))
        } else if library.state.playlists.contains(where: { $0.id == sourceID }) {
            syncQueueSource(sourceID, ids: library.playlistSongs(sourceID).filter(\.available).map(\.id))
        } else { queueSource = nil; savePosition() }
    }
    func insertNext(_ ids: [String]) {
        guard let library else { return }
        let additions = PlaybackOrder.unique(ids, available: Set(library.state.songs.filter(\.available).map(\.id)))
        queueSource = nil
        queue = PlaybackOrder.insertingNext(additions, into: queue, currentID: current?.id)
        savePosition()
    }
    func appendToQueue(_ ids: [String]) {
        guard let library else { return }
        let additions = PlaybackOrder.unique(ids, available: Set(library.state.songs.filter(\.available).map(\.id)))
        queueSource = nil
        queue = PlaybackOrder.appending(additions, to: queue)
        savePosition()
    }
    func updateCurrentMetadata() {
        if let id = current?.id, let updated = library?.song(id), updated != current {
            current = updated
            refreshLockScreen()
        }
    }
    func savePosition() {
        guard let library else { return }
        lastSaved = Date()
        let position = audio?.currentTime ?? progress.position
        library.recordPlayback(queue: queue, id: current?.id, position: position, mode: mode, sourceID: queueSource)
    }
    func refreshLockScreen() {
        artworkTask?.cancel(); artworkGeneration &+= 1
        let enabled = library?.state.settings.lockScreen == true
        for (command, _) in commandTargets { command.isEnabled = enabled && current != nil }
        guard enabled, let song = current else { MPNowPlayingInfoCenter.default().nowPlayingInfo = nil; return }
        let generation = artworkGeneration
        let cached = lockScreenArtwork?.name == song.artwork ? lockScreenArtwork?.image : nil
        publishLockScreen(song, image: cached ?? UIImage(named: "Flower"))
        guard cached == nil, !song.artwork.isEmpty else { return }
        artworkTask = Task { [weak self] in
            let image = await ImageCache.shared.loadedImage(song.artwork, pixels: 512)
            guard let self, !Task.isCancelled, generation == self.artworkGeneration,
                  self.current?.id == song.id, self.library?.state.settings.lockScreen == true, let image else { return }
            self.lockScreenArtwork = (song.artwork, image)
            self.publishLockScreen(song, image: image)
        }
    }
    private func publishLockScreen(_ song: Song, image: UIImage?) {
        var info: [String: Any] = [MPMediaItemPropertyTitle: song.title, MPMediaItemPropertyArtist: song.artist,
            MPMediaItemPropertyAlbumTitle: song.album, MPMediaItemPropertyPlaybackDuration: progress.duration,
            MPNowPlayingInfoPropertyElapsedPlaybackTime: audio?.currentTime ?? progress.position,
            MPNowPlayingInfoPropertyPlaybackRate: playing ? 1.0 : 0.0]
        if let image { info[MPMediaItemPropertyArtwork] = MPMediaItemArtwork(boundsSize: image.size) { _ in image } }
        MPNowPlayingInfoCenter.default().nowPlayingInfo = info
    }
    private func remoteCommands() {
        let remote = MPRemoteCommandCenter.shared()
        func register(_ command: MPRemoteCommand, _ action: @escaping (Playback) -> Void) {
            let token = command.addTarget { [weak self] _ in
                Task { @MainActor in if let self { action(self) } }
                return .success
            }
            commandTargets.append((command, token))
        }
        register(remote.playCommand) { if !$0.playing { $0.toggle() } }
        register(remote.pauseCommand) { $0.pause() }
        register(remote.togglePlayPauseCommand) { $0.toggle() }
        register(remote.previousTrackCommand) { $0.previous() }
        register(remote.nextTrackCommand) { $0.next() }
        let token = remote.changePlaybackPositionCommand.addTarget { [weak self] event in
            guard let position = (event as? MPChangePlaybackPositionCommandEvent)?.positionTime else { return .commandFailed }
            Task { @MainActor in self?.seek(position) }
            return .success
        }
        commandTargets.append((remote.changePlaybackPositionCommand, token))
    }
    private func interruption(_ note: Notification) {
        guard let raw = note.userInfo?[AVAudioSessionInterruptionTypeKey] as? UInt,
              let type = AVAudioSession.InterruptionType(rawValue: raw) else { return }
        if type == .began {
            let wasPlaying = playing
            pause()
            resumeAfterInterruption = wasPlaying
        } else {
            let rawOptions = note.userInfo?[AVAudioSessionInterruptionOptionKey] as? UInt ?? 0
            if resumeAfterInterruption, AVAudioSession.InterruptionOptions(rawValue: rawOptions).contains(.shouldResume) { toggle() }
            resumeAfterInterruption = false
        }
    }
    nonisolated func audioPlayerDidFinishPlaying(_ player: AVAudioPlayer, successfully flag: Bool) {
        Task { @MainActor in
            guard self.audio === player else { return }
            self.playing = false
            if flag { self.next(automatic: true) }
            else { self.pause(); self.library?.message = "音频解码中断，请检查文件。" }
        }
    }
    nonisolated func audioPlayerDecodeErrorDidOccur(_ player: AVAudioPlayer, error: Error?) {
        Task { @MainActor in
            guard self.audio === player else { return }
            self.pause(); self.library?.message = error?.localizedDescription ?? "音频解码失败。"
        }
    }
}
