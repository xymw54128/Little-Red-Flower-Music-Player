import XCTest
import Foundation
#if canImport(XiaohonghuaCore)
@testable import XiaohonghuaCore
#else
@testable import Xiaohonghua
#endif

final class PlaybackOrder117Tests: XCTestCase {
    private func song(_ id: String, title: String, artist: String) -> Song {
        Song(id: id, sourceID: "folder", relativePath: id + ".flac", name: id + ".flac", title: title, artist: artist)
    }
    private func fixture() -> MusicState {
        var state = MusicState()
        state.songs = [song("a", title: "Delta", artist: "Amy"), song("b", title: "Alpha", artist: "Zoe"),
                       song("c", title: "Charlie", artist: "Ben"), song("d", title: "Bravo", artist: "Ian")]
        state.customIDs = ["d", "c", "b", "a"]; state.randomIDs = ["c", "a", "d", "b"]
        state.playlists = [Playlist(id: Playlist.favoritesID, name: "我喜欢", songIDs: ["c", "b", "a"], randomIDs: ["a", "c", "b"]),
                           Playlist(id: "A", name: "A", songIDs: ["b", "a", "c"], randomIDs: ["c", "b", "a"]),
                           Playlist(id: "B", name: "B", songIDs: ["d", "b", "a"], randomIDs: ["a", "d", "b"])]
        return state
    }
    func testEverySourceUsesItsOwnFourSortOrders() {
        let expected: [SongSort: [[String]]] = [
            .title: [["b", "d", "c", "a"], ["b", "c", "a"], ["b", "c", "a"], ["b", "d", "a"]],
            .artist: [["a", "c", "d", "b"], ["a", "c", "b"], ["a", "c", "b"], ["a", "d", "b"]],
            .random: [["c", "a", "d", "b"], ["a", "c", "b"], ["c", "b", "a"], ["a", "d", "b"]],
            .custom: [["d", "c", "b", "a"], ["c", "b", "a"], ["b", "a", "c"], ["d", "b", "a"]]
        ]
        for sort in SongSort.allCases {
            var state = fixture(); state.sort = sort
            for index in state.playlists.indices { state.playlists[index].sort = sort }
            for (index, source) in ["library", Playlist.favoritesID, "A", "B"].enumerated() {
                XCTAssertEqual(PlaybackOrder.sourceSongs(state, sourceID: source).map(\.id), expected[sort]![index])
            }
        }
    }
    func testSharedSongSelectionChangesSourceWithoutLeakingMembers() {
        let state = fixture()
        XCTAssertEqual(PlaybackOrder.sourceSongs(state, sourceID: "A").map(\.id), ["b", "a", "c"])
        XCTAssertEqual(PlaybackOrder.sourceSongs(state, sourceID: "B").map(\.id), ["d", "b", "a"])
        XCTAssertEqual(PlaybackOrder.sourceSongs(state, sourceID: "A", selection: ["a", "d"]).map(\.id), ["a"])
    }
    func testStaleSelectionUsesLatestTagsAndMembersWithoutGrowingQueue() {
        var state = fixture(); state.sort = .title
        state.songs[0].title = "Aardvark"; state.songs[2].available = false
        XCTAssertEqual(PlaybackOrder.sourceSongs(state, sourceID: "library", selection: ["a", "b", "c"]).map(\.id), ["a", "b"])
        state.playlists[1].songIDs = ["c", "a"]
        XCTAssertEqual(PlaybackOrder.sourceSongs(state, sourceID: "A", selection: ["a", "b"]).map(\.id), ["a"])
        XCTAssertEqual(PlaybackOrder.sourceSongs(state, sourceID: "missing").map(\.id), [])
        XCTAssertEqual(PlaybackOrder.sourceSongs(state, sourceID: "library", selection: []).map(\.id), [])
    }
    func testAlbumKeepsExactIdentityAndNumericDiscTrackOrder() {
        var state = fixture()
        for index in state.songs.indices { state.songs[index].album = "专辑 + 名"; state.songs[index].albumArtist = "合集" }
        state.songs[0].disc = 2; state.songs[0].track = 1
        state.songs[1].disc = 1; state.songs[1].track = 10
        state.songs[2].disc = 1; state.songs[2].track = 2
        state.songs[3].albumArtist = "其他"
        let source = "album:" + state.songs[0].albumKey
        XCTAssertEqual(PlaybackOrder.sourceSongs(state, sourceID: source).map(\.id), ["c", "b", "a"])
    }
    func testAlbumQueueSourceSurvivesNormalizationAndRestart() throws {
        var state = fixture()
        state.queueSource = "album:" + state.songs[0].albumKey
        state.queueIDs = ["a"]; state.currentID = "a"; state.position = 12.5; state.mode = .shuffle
        state.normalize()
        let restored = try JSONDecoder().decode(MusicState.self, from: JSONEncoder().encode(state))
        XCTAssertEqual(restored.queueSource, state.queueSource)
        XCTAssertEqual(restored.queueIDs, ["a"])
        XCTAssertEqual(restored.position, 12.5)
        XCTAssertEqual(restored.mode, .shuffle)
        var missing = restored
        missing.queueSource = "album:unknown"
        missing.normalize()
        XCTAssertNil(missing.queueSource)
    }
    func testShuffledQueueIsOneStableTraversalAnchoredAtCurrent() {
        let ids = (0..<1000).map(String.init)
        let queue = PlaybackOrder.shuffled(ids, currentID: "500")
        XCTAssertEqual(queue.first, "500")
        XCTAssertEqual(Set(queue), Set(ids))
        XCTAssertEqual(queue.count, 1000)
        XCTAssertEqual(PlaybackOrder.shuffled(["only"], currentID: "only"), ["only"])
        XCTAssertEqual(PlaybackOrder.shuffled([], currentID: nil), [])
    }
    func testAppendMovesExistingSongToTailAndNeverMovesCurrent() {
        XCTAssertEqual(PlaybackOrder.appending(["b", "d", "b"], to: ["a", "b", "c", "d"], currentID: "a"), ["a", "c", "b", "d"])
        XCTAssertEqual(PlaybackOrder.appending(["a"], to: ["a", "b"], currentID: "a"), ["a", "b"])
        XCTAssertEqual(PlaybackOrder.insertingNext(["b"], into: ["a", "b", "c"], currentID: "b"), ["a", "b", "c"])
    }
    func testThousandSongSourceSubsetKeepsExactLatestOrder() {
        var state = MusicState()
        state.songs = (0..<1000).map { song(String($0), title: String(format: "%04d", $0), artist: "Artist") }
        state.sort = .custom; state.customIDs = Array(state.songs.map(\.id).reversed())
        let subset = Set(stride(from: 0, to: 1000, by: 3).map(String.init))
        XCTAssertEqual(PlaybackOrder.sourceSongs(state, sourceID: "library", selection: subset).map(\.id), state.customIDs.filter { subset.contains($0) })
    }
}
