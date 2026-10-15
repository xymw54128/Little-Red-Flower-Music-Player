import Foundation

/// Pure queue edits keep the playing file and its clock outside array changes.
enum PlaybackOrder {
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

    static func appending(_ additions: [String], to queue: [String]) -> [String] {
        var seen = Set(queue)
        return queue + additions.filter { seen.insert($0).inserted }
    }
}
