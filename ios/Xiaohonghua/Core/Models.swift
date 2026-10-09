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
}

struct Playlist: Codable, Identifiable, Equatable {
    var id: String = UUID().uuidString
    var name: String
    var songIDs: [String] = []
    var sort: SongSort = .custom
    var randomIDs: [String] = []
    var coverPreset: Int = 0
    var coverPhoto: String = ""
}

struct MusicSettings: Codable, Equatable {
    var theme: Int = 0
    var lyricFont: String = "system"
    var lyricSize: Double = 23
    var lyricGap: Double = 24
    var dynamicLyrics: Bool = true
    var monitor: Bool = true
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
        if !["system", "serif", "mono", "kai", "song", "fangsong"].contains(lyricFont) { lyricFont = "system" }
        if !["plain", "artwork", "photo"].contains(background) { background = "plain" }
    }
}

struct MusicState: Codable {
    var version: Int = 1
    var songs: [Song] = []
    var sources: [MusicSource] = []
    var playlists: [Playlist] = []
    var settings = MusicSettings()
    var sort: SongSort = .title
    var customIDs: [String] = []
    var randomIDs: [String] = []
    var manualLyrics: [String: String] = [:]
    var queueIDs: [String] = []
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
        switch sort {
        case .title, .artist:
            return songs.sorted {
                let a = sort == .artist ? $0.artist : $0.title
                let b = sort == .artist ? $1.artist : $1.title
                let result = a.compare(b, options: [.caseInsensitive, .diacriticInsensitive], locale: Locale(identifier: "zh_CN"))
                if result != .orderedSame { return result == .orderedAscending }
                let title = $0.title.localizedStandardCompare($1.title)
                return title == .orderedSame ? $0.id < $1.id : title == .orderedAscending
            }
        case .random, .custom:
            let positions = Dictionary(ids.enumerated().map { ($0.element, $0.offset) }, uniquingKeysWith: { first, _ in first })
            return songs.enumerated().sorted {
                let a = positions[$0.element.id] ?? (ids.count + $0.offset)
                let b = positions[$1.element.id] ?? (ids.count + $1.offset)
                return a < b
            }.map(\.element)
        }
    }

    static func restore(_ backup: MusicBackup, into state: inout MusicState) -> Int {
        // Only unambiguous filename + size matches are portable across devices.
        let groups = Dictionary(grouping: state.songs, by: \.migrationKey)
        var mapping: [String: String] = [:]
        for song in backup.songs {
            if let same = state.songs.first(where: { $0.id == song.id }) { mapping[song.id] = same.id }
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
        return mapping.count
    }
}
