import Foundation

/// Pure queue edits keep the playing file and its clock outside array changes.
enum PlaybackOrder {
    /// A clicked row can outlive a background cache rebuild. Resolve its subset
    /// against current tags and source membership, not the outgoing visible array.
    static func sourceSongs(_ state: MusicState, sourceID: String, selection: Set<String>? = nil) -> [Song] {
        let album = sourceID.hasPrefix("album:") ? String(sourceID.dropFirst(6)) : nil
        let playlist = sourceID == "library" || album != nil ? nil : state.playlists.first { $0.id == sourceID }
        guard sourceID == "library" || album != nil || playlist != nil else { return [] }
        let members = playlist.map { Set($0.songIDs) }
        let songs = MusicOrder.uniqueSongs(state.songs).filter {
            $0.available && (selection == nil || selection!.contains($0.id)) &&
                (members == nil || members!.contains($0.id)) && (album == nil || $0.albumKey == album)
        }
        if album != nil { return MusicOrder.albumSongs(songs) }
        let sort = playlist?.sort ?? state.sort
        let preferred = sort == .random ? (playlist?.randomIDs ?? state.randomIDs) : (playlist?.songIDs ?? state.customIDs)
        return MusicOrder.ordered(songs, sort: sort, ids: preferred)
    }

    /// Shuffle once; the displayed queue is also the next/previous traversal.
    static func shuffled(_ ids: [String], currentID: String?) -> [String] {
        let ids = MusicOrder.uniqueIDs(ids)
        let rest = ids.filter { $0 != currentID }.shuffled()
        if let currentID, ids.contains(currentID) { return [currentID] + rest }
        return rest
    }

    static func unique(_ ids: [String], available: Set<String>) -> [String] {
        var seen = Set<String>()
        return ids.filter { available.contains($0) && seen.insert($0).inserted }
    }

    static func synchronized(_ ids: [String], currentID: String?) -> [String] {
        var seen = Set<String>()
        var result = ids.filter { seen.insert($0).inserted }
        // A source can lose its current song during refresh. Finish the already
        // opened audio without interrupting it; the following songs use new order.
        if let currentID, !seen.contains(currentID) { result.insert(currentID, at: 0) }
        return result
    }

    static func insertingNext(_ additions: [String], into queue: [String], currentID: String?) -> [String] {
        var seen = Set<String>()
        let additions = additions.filter { $0 != currentID && seen.insert($0).inserted }
        let moved = Set(additions)
        var result = queue.filter { !moved.contains($0) }
        let insertion = currentID.flatMap { result.firstIndex(of: $0) }.map { $0 + 1 } ?? 0
        result.insert(contentsOf: additions, at: insertion)
        return result
    }

    static func appending(_ additions: [String], to queue: [String], currentID: String? = nil) -> [String] {
        var seen = Set<String>()
        let additions = additions.filter { $0 != currentID && seen.insert($0).inserted }
        let moved = Set(additions)
        return queue.filter { !moved.contains($0) } + additions
    }
}
