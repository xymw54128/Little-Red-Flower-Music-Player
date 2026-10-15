import Foundation

enum SongSort: String, Codable, CaseIterable, Identifiable {
    case title, artist, random, custom
    var id: String { rawValue }
    var label: String {
        switch self {
        case .title: return "歌曲名 A–Z"
        case .artist: return "歌手 A–Z"
        case .random: return "随机排序"
        case .custom: return "自定义排序"
        }
    }
}

enum PlayMode: String, Codable, CaseIterable {
    case ordered, repeatAll, repeatOne, shuffle
    var label: String {
        switch self {
        case .ordered: return "顺序播放"
        case .repeatAll: return "列表循环"
        case .repeatOne: return "单曲循环"
        case .shuffle: return "随机播放"
        }
    }
    var symbol: String {
        switch self {
        case .ordered: return "arrow.right"
        case .repeatAll: return "repeat"
        case .repeatOne: return "repeat.1"
        case .shuffle: return "shuffle"
        }
    }
}

struct MusicSource: Codable, Identifiable, Equatable {
    var id: String = UUID().uuidString
    var name: String
    var bookmark: Data?
    var isFolder: Bool
    var isInternal: Bool = false
    var error: String?
}

struct Song: Codable, Identifiable, Equatable {
    var id: String
    var sourceID: String
    var relativePath: String
    var name: String
    var title: String
    var artist: String = "未知歌手"
    var albumArtist: String = ""
    var track: Int = 0
    var disc: Int = 0
    var album: String = "未知专辑"
    var duration: Double = 0
    var size: Int64 = 0
    var modified: Double = 0
    var format: String = ""
    var sampleRate: Int = 0
    var bitDepth: Int = 0
    var lyrics: String = ""
    var artwork: String = ""
    var lyricModified: Double = 0
    var available: Bool = true
    var migrationKey: String { name.lowercased() + "|" + String(size) }
    var subtitle: String { artist + " · " + album }
    var albumKey: String {
        let owner = albumArtist.trimmingCharacters(in: .whitespacesAndNewlines)
        return (owner.isEmpty ? artist : owner).trimmingCharacters(in: .whitespacesAndNewlines).lowercased() + "\u{0}" + album.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
    }
}

struct Playlist: Codable, Identifiable, Equatable {
    var id: String = UUID().uuidString
    var name: String
    var songIDs: [String] = []
    var sort: SongSort = .custom
    var randomIDs: [String] = []
    var coverPreset: Int = 0
    var coverPhoto: String = ""
    var description: String = ""
    static let favoritesID = "favorites"
    var isFavorites: Bool { id == Self.favoritesID }
}

struct MusicSettings: Codable, Equatable {
    var theme: Int = 0
    var lyricFont: String = "system"
    var lyricSize: Double = 23
    var lyricGap: Double = 24
    var dynamicLyrics: Bool = true
    var nightMode: Bool = false
    // Kept for decoding 1.0.1 backups. Monitoring and custom player backgrounds are retired.
    var monitor: Bool = false
    var lockScreen: Bool = true
    var background: String = "plain"
    var backgroundPhoto: String = ""
    var backgroundOpacity: Double = 0.35
    var backgroundScale: Double = 1
    var backgroundX: Double = 0.5
    var backgroundY: Double = 0.5

    mutating func normalize() {
        theme = min(max(theme, 0), 4)
        lyricSize = lyricSize.isFinite ? min(max(lyricSize, 18), 34) : 23
        lyricGap = lyricGap.isFinite ? min(max(lyricGap, 0), 48) : 24
        backgroundOpacity = backgroundOpacity.isFinite ? min(max(backgroundOpacity, 0), 1) : 0.35
        backgroundScale = backgroundScale.isFinite ? min(max(backgroundScale, 1), 2.5) : 1
        backgroundX = backgroundX.isFinite ? min(max(backgroundX, 0), 1) : 0.5
        backgroundY = backgroundY.isFinite ? min(max(backgroundY, 0), 1) : 0.5
        lyricFont = Self.normalizedFont(lyricFont)
        monitor = false
        background = "plain"
        backgroundPhoto = ""
    }
}

struct MusicState: Codable {
    var version: Int = 1
    var songs: [Song] = []
    var sources: [MusicSource] = []
    var playlists: [Playlist] = [Playlist(id: Playlist.favoritesID, name: "我喜欢", coverPreset: 10)]
    var settings = MusicSettings()
    var sort: SongSort = .title
    var customIDs: [String] = []
    var randomIDs: [String] = []
    var manualLyrics: [String: String] = [:]
    var queueIDs: [String] = []
    var queueSource: String?
    var currentID: String?
    var position: Double = 0
    var mode: PlayMode = .repeatAll
}

/// Bookmarks and device paths are deliberately omitted from a portable backup.
struct MusicBackup: Codable {
    var kind = "xiaohonghua-ios"
    var version = 1
    var songs: [Song]
    var playlists: [Playlist]
    var settings: MusicSettings
    var manualLyrics: [String: String]
    var sort: SongSort
    var customIDs: [String]
    var randomIDs: [String]

    init(state: MusicState) {
        songs = state.songs
        playlists = state.playlists.map { var p = $0; p.coverPhoto = ""; return p }
        settings = state.settings
        settings.backgroundPhoto = ""
        if settings.background == "photo" { settings.background = "plain" }
        manualLyrics = state.manualLyrics
        sort = state.sort
        customIDs = state.customIDs
        randomIDs = state.randomIDs
    }
}

enum MusicOrder {
    static func ordered(_ songs: [Song], sort: SongSort, ids: [String]) -> [Song] {
        let songs = uniqueSongs(songs)
        switch sort {
        case .title, .artist:
            let locale = Locale(identifier: "zh_CN")
            return songs.sorted {
                let a = sort == .artist ? $0.artist : $0.title
                let b = sort == .artist ? $1.artist : $1.title
                let result = a.compare(b, options: [.caseInsensitive, .diacriticInsensitive], locale: locale)
                if result != .orderedSame { return result == .orderedAscending }
                let title = $0.title.localizedStandardCompare($1.title)
                return title == .orderedSame ? $0.id < $1.id : title == .orderedAscending
            }
        case .random, .custom:
            let byID = Dictionary(songs.map { ($0.id, $0) }, uniquingKeysWith: { _, latest in latest })
            return mergedIDs(ids, available: songs.map(\.id)).compactMap { byID[$0] }
        }
    }

    static func restore(_ backup: MusicBackup, into state: inout MusicState) -> Int {
        // Only unambiguous filename + size matches are portable across devices.
        let groups = Dictionary(grouping: state.songs, by: \.migrationKey)
        let existingIDs = Set(state.songs.map(\.id))
        var mapping: [String: String] = [:]
        for song in backup.songs {
            if existingIDs.contains(song.id) { mapping[song.id] = song.id }
            else if let matches = groups[song.migrationKey], matches.count == 1 { mapping[song.id] = matches[0].id }
        }
        func remap(_ ids: [String]) -> [String] {
            var seen = Set<String>()
            return ids.compactMap { mapping[$0] }.filter { seen.insert($0).inserted }
        }
        state.playlists = backup.playlists.map {
            var p = $0; p.songIDs = remap(p.songIDs); p.randomIDs = remap(p.randomIDs); p.coverPhoto = ""; return p
        }
        state.customIDs = remap(backup.customIDs)
        state.randomIDs = remap(backup.randomIDs)
        state.manualLyrics = Dictionary(backup.manualLyrics.compactMap { id, text in mapping[id].map { ($0, text) } }, uniquingKeysWith: { _, new in new })
        state.settings = backup.settings
        state.settings.normalize()
        state.settings.backgroundPhoto = ""
        if state.settings.background == "photo" { state.settings.background = "plain" }
        state.sort = backup.sort
        state.normalize()
        return mapping.count
    }
}

// Explicit default decoding keeps existing 1.0.1 state files and portable backups readable.
extension Playlist {
    enum CodingKeys: String, CodingKey { case id, name, songIDs, sort, randomIDs, coverPreset, coverPhoto, description }
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decodeIfPresent(String.self, forKey: .id) ?? UUID().uuidString
        name = try c.decodeIfPresent(String.self, forKey: .name) ?? "未命名歌单"
        songIDs = try c.decodeIfPresent([String].self, forKey: .songIDs) ?? []
        sort = try c.decodeIfPresent(SongSort.self, forKey: .sort) ?? .custom
        randomIDs = try c.decodeIfPresent([String].self, forKey: .randomIDs) ?? []
        coverPreset = try c.decodeIfPresent(Int.self, forKey: .coverPreset) ?? (id == Self.favoritesID ? 10 : 0)
        coverPhoto = try c.decodeIfPresent(String.self, forKey: .coverPhoto) ?? ""
        description = try c.decodeIfPresent(String.self, forKey: .description) ?? ""
    }
}

extension MusicSettings {
    enum CodingKeys: String, CodingKey {
        case theme, lyricFont, lyricSize, lyricGap, dynamicLyrics, nightMode, monitor, lockScreen
        case background, backgroundPhoto, backgroundOpacity, backgroundScale, backgroundX, backgroundY
    }
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        self.init()
        theme = try c.decodeIfPresent(Int.self, forKey: .theme) ?? theme
        lyricFont = try c.decodeIfPresent(String.self, forKey: .lyricFont) ?? lyricFont
        lyricSize = try c.decodeIfPresent(Double.self, forKey: .lyricSize) ?? lyricSize
        lyricGap = try c.decodeIfPresent(Double.self, forKey: .lyricGap) ?? lyricGap
        dynamicLyrics = try c.decodeIfPresent(Bool.self, forKey: .dynamicLyrics) ?? dynamicLyrics
        nightMode = try c.decodeIfPresent(Bool.self, forKey: .nightMode) ?? nightMode
        lockScreen = try c.decodeIfPresent(Bool.self, forKey: .lockScreen) ?? lockScreen
        // Decode retired keys through defaults rather than restoring obsolete behavior.
        normalize()
    }
    static func normalizedFont(_ value: String) -> String {
        switch value {
        case "serif": return "song"
        case "fangsong": return "kai"
        case "mono", "cute": return "kiwi"
        case "handdrawn": return "kurenaido"
        case "system", "kai", "song", "xiaowei", "zenmaru", "kiwi", "kurenaido", "longcang": return value
        default: return "system"
        }
    }
}

extension MusicState {
    enum CodingKeys: String, CodingKey {
        case version, songs, sources, playlists, settings, sort, customIDs, randomIDs, manualLyrics
        case queueIDs, queueSource, currentID, position, mode
    }
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        self.init()
        version = try c.decodeIfPresent(Int.self, forKey: .version) ?? version
        songs = try c.decodeIfPresent([Song].self, forKey: .songs) ?? []
        sources = try c.decodeIfPresent([MusicSource].self, forKey: .sources) ?? []
        playlists = try c.decodeIfPresent([Playlist].self, forKey: .playlists) ?? []
        settings = try c.decodeIfPresent(MusicSettings.self, forKey: .settings) ?? settings
        sort = try c.decodeIfPresent(SongSort.self, forKey: .sort) ?? sort
        customIDs = try c.decodeIfPresent([String].self, forKey: .customIDs) ?? []
        randomIDs = try c.decodeIfPresent([String].self, forKey: .randomIDs) ?? []
        manualLyrics = try c.decodeIfPresent([String: String].self, forKey: .manualLyrics) ?? [:]
        queueIDs = try c.decodeIfPresent([String].self, forKey: .queueIDs) ?? []
        queueSource = try c.decodeIfPresent(String.self, forKey: .queueSource)
        currentID = try c.decodeIfPresent(String.self, forKey: .currentID)
        position = try c.decodeIfPresent(Double.self, forKey: .position) ?? 0
        mode = try c.decodeIfPresent(PlayMode.self, forKey: .mode) ?? mode
        normalize()
    }
    mutating func normalize() {
        settings.normalize()
        songs = MusicOrder.uniqueSongs(songs)
        var sourceIDs = Set<String>()
        sources = sources.filter { sourceIDs.insert($0.id).inserted }
        let ids = songs.map(\.id), available = Set(ids)
        customIDs = MusicOrder.mergedIDs(customIDs, available: ids)
        randomIDs = MusicOrder.mergedIDs(randomIDs, available: ids)
        queueIDs = MusicOrder.uniqueIDs(queueIDs).filter { available.contains($0) }
        if let id = currentID, !available.contains(id) { currentID = nil; position = 0 }
        position = position.isFinite ? max(0, position) : 0
        var playlistIDs = Set<String>()
        playlists = playlists.filter { playlistIDs.insert($0.id).inserted }
        if !playlists.contains(where: \.isFavorites) {
            if let index = playlists.firstIndex(where: { $0.name == "我喜欢" }) { playlists[index].id = Playlist.favoritesID; playlists[index].coverPreset = 10 }
            else { playlists.append(Playlist(id: Playlist.favoritesID, name: "我喜欢", coverPreset: 10)) }
        }
        for index in playlists.indices {
            playlists[index].songIDs = MusicOrder.uniqueIDs(playlists[index].songIDs).filter { available.contains($0) }
            playlists[index].randomIDs = MusicOrder.mergedIDs(playlists[index].randomIDs, available: playlists[index].songIDs)
            if playlists[index].isFavorites { playlists[index].name = "我喜欢" }
            playlists[index].coverPreset = min(max(playlists[index].coverPreset, 0), 10)
        }
        if let source = queueSource, source != "library", !playlists.contains(where: { $0.id == source }) { queueSource = nil }
    }
}

/// Separate from the tag index so a five-second clock save never re-encodes every song's lyrics.
struct PlaybackState: Codable {
    var queueIDs: [String]
    var currentID: String?
    var position: Double
    var mode: PlayMode
    var queueSource: String?

    func apply(to state: inout MusicState) {
        state.queueIDs = queueIDs; state.currentID = currentID; state.position = position
        state.mode = mode; state.queueSource = queueSource
    }
}

extension MusicOrder {
    static func uniqueIDs(_ ids: [String]) -> [String] {
        var seen = Set<String>()
        return ids.filter { seen.insert($0).inserted }
    }
    static func uniqueSongs(_ songs: [Song]) -> [Song] {
        var order: [String] = [], byID: [String: Song] = [:]
        for song in songs {
            if byID[song.id] == nil { order.append(song.id) }
            byID[song.id] = song
        }
        return order.compactMap { byID[$0] }
    }
    static func mergedIDs(_ preferred: [String], available: [String]) -> [String] {
        let valid = Set(available)
        var seen = Set<String>()
        return (preferred.filter { valid.contains($0) } + available).filter { seen.insert($0).inserted }
    }
    /// Replace only scanned sources, preserving existing rows and custom order on refresh.
    static func reconcile(previous: [Song], scanned: [Song], sourceIDs: Set<String>) -> [Song] {
        var byID = Dictionary(uniqueSongs(scanned).map { ($0.id, $0) }, uniquingKeysWith: { _, latest in latest })
        var result: [Song] = []
        var seen = Set<String>()
        for song in previous where seen.insert(song.id).inserted {
            if let updated = byID.removeValue(forKey: song.id) { result.append(updated) }
            else if !sourceIDs.contains(song.sourceID) { result.append(song) }
        }
        for song in scanned {
            if let new = byID.removeValue(forKey: song.id) { result.append(new) }
        }
        return result
    }
}


extension Song {
    enum CodingKeys: String, CodingKey {
        case id, sourceID, relativePath, name, title, artist, albumArtist, track, disc, album
        case duration, size, modified, format, sampleRate, bitDepth, lyrics, artwork, lyricModified, available
    }
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(String.self, forKey: .id)
        sourceID = try c.decode(String.self, forKey: .sourceID)
        relativePath = try c.decodeIfPresent(String.self, forKey: .relativePath) ?? ""
        name = try c.decode(String.self, forKey: .name)
        title = try c.decodeIfPresent(String.self, forKey: .title) ?? name
        artist = try c.decodeIfPresent(String.self, forKey: .artist) ?? "未知歌手"
        albumArtist = try c.decodeIfPresent(String.self, forKey: .albumArtist) ?? ""
        track = max(0, try c.decodeIfPresent(Int.self, forKey: .track) ?? 0)
        disc = max(0, try c.decodeIfPresent(Int.self, forKey: .disc) ?? 0)
        album = try c.decodeIfPresent(String.self, forKey: .album) ?? "未知专辑"
        duration = try c.decodeIfPresent(Double.self, forKey: .duration) ?? 0
        size = try c.decodeIfPresent(Int64.self, forKey: .size) ?? 0
        modified = try c.decodeIfPresent(Double.self, forKey: .modified) ?? 0
        format = try c.decodeIfPresent(String.self, forKey: .format) ?? ""
        sampleRate = try c.decodeIfPresent(Int.self, forKey: .sampleRate) ?? 0
        bitDepth = try c.decodeIfPresent(Int.self, forKey: .bitDepth) ?? 0
        lyrics = try c.decodeIfPresent(String.self, forKey: .lyrics) ?? ""
        artwork = try c.decodeIfPresent(String.self, forKey: .artwork) ?? ""
        lyricModified = try c.decodeIfPresent(Double.self, forKey: .lyricModified) ?? 0
        available = try c.decodeIfPresent(Bool.self, forKey: .available) ?? true
    }
}

struct SongSearchEntry {
    private let source: [String]
    private let fields: [String]
    init(_ song: Song) {
        let raw = [song.title, song.artist, song.album, song.albumArtist]
        source = raw
        fields = raw.flatMap { value -> [String] in
            let original = Self.normalized(value)
            let latin = Self.normalized(value.applyingTransform(.toLatin, reverse: false) ?? value)
            let initials = latin.split { !$0.isLetter && !$0.isNumber }.compactMap(\.first).map(String.init).joined()
            return [original, latin, latin.filter { $0.isLetter || $0.isNumber }, initials]
        }
    }
    func represents(_ song: Song) -> Bool { source == [song.title, song.artist, song.album, song.albumArtist] }
    static func normalized(_ text: String) -> String {
        text.folding(options: [.caseInsensitive, .diacriticInsensitive, .widthInsensitive], locale: Locale(identifier: "zh_CN")).lowercased()
    }
    func matches(_ tokens: [String]) -> Bool {
        tokens.allSatisfy { token in fields.contains { $0.contains(token) } }
    }
}

extension MusicOrder {
    static func albumSongs(_ songs: [Song]) -> [Song] {
        songs.sorted {
            let aDisc = max(1, $0.disc), bDisc = max(1, $1.disc)
            if aDisc != bDisc { return aDisc < bDisc }
            let aTrack = $0.track > 0 ? $0.track : Int.max, bTrack = $1.track > 0 ? $1.track : Int.max
            if aTrack != bTrack { return aTrack < bTrack }
            let title = $0.title.localizedStandardCompare($1.title)
            return title == .orderedSame ? $0.id < $1.id : title == .orderedAscending
        }
    }
}
