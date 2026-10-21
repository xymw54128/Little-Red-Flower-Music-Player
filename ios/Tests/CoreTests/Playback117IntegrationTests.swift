#if canImport(Xiaohonghua)
import XCTest
import Combine
@testable import Xiaohonghua

/// Runs in the iOS XCTest target, not the portable Swift Package. Missing file
/// sources stop preparation before AVAudioPlayer opens; queue state is real.
final class Playback117IntegrationTests: XCTestCase {
    @MainActor
    private func fixture() -> (MusicLibrary, Playback) {
        let library = MusicLibrary()
        library.update {
            $0.songs = ["a", "b", "c", "d"].map {
                Song(id: $0, sourceID: "missing-test-source", relativePath: $0 + ".flac",
                     name: $0 + ".flac", title: $0, duration: 30)
            }
            $0.sort = .custom; $0.customIDs = ["d", "c", "b", "a"]
            $0.playlists += [
                Playlist(id: "A", name: "A", songIDs: ["c", "a", "b"]),
                Playlist(id: "B", name: "B", songIDs: ["d", "b", "a"])
            ]
            $0.mode = .ordered
        }
        let playback = Playback()
        playback.connect(library)
        return (library, playback)
    }

    @MainActor
    func testOverlappingSongUsesNewPlaylistSourceAndLatestOrder() throws {
        let (library, playback) = fixture()
        let a = try XCTUnwrap(library.song("a"))
        playback.play(a, within: ["a", "b", "c"], sourceID: "A")
        XCTAssertEqual(playback.queue, ["c", "a", "b"])
        playback.play(a, within: ["a", "b", "d"], sourceID: "B")
        XCTAssertEqual(playback.queueSource, "B")
        XCTAssertEqual(playback.queue, ["d", "b", "a"])
        playback.next()
        XCTAssertEqual(playback.current?.id, "d")
        XCTAssertEqual(playback.queueSource, "B")
    }

    @MainActor
    func testSavedOrderIsImmediateAndInactiveSourceCannotReplaceIt() throws {
        let (library, playback) = fixture()
        playback.play(try XCTUnwrap(library.song("a")), within: ["a", "b", "c"], sourceID: "A")
        playback.seek(12)
        library.saveOrder(["b", "a", "c"], playlistID: "A")
        library.saveOrder(["a", "b", "d"], playlistID: "B")
        XCTAssertEqual(playback.queue, ["b", "a", "c"])
        XCTAssertEqual(playback.current?.id, "a")
        XCTAssertEqual(playback.progress.position, 12)
        XCTAssertFalse(playback.playing)
        XCTAssertNil(playback.audio)
        playback.next()
        XCTAssertEqual(playback.current?.id, "c")
    }

    @MainActor
    func testSearchSnapshotDoesNotGrowAndStaleClickedOrderIsResolvedAgain() throws {
        let (library, playback) = fixture()
        let a = try XCTUnwrap(library.song("a"))
        playback.play(a, within: ["a", "c"], sourceID: "library")
        XCTAssertEqual(playback.queue, ["c", "a"])
        library.saveOrder(["a", "b", "c", "d"])
        XCTAssertEqual(playback.queue, ["a", "c"])
        library.saveOrder(["c", "b", "a", "d"])
        playback.play(a, within: ["a", "c"], sourceID: "library")
        XCTAssertEqual(playback.queue, ["c", "a"])
        library.update { $0.songs.removeAll { $0.id == "a" } }
        playback.play(a, within: ["a", "c"], sourceID: "library")
        XCTAssertEqual(playback.queue, ["c", "a"])
        XCTAssertEqual(playback.current?.id, "a")
    }

    @MainActor
    func testShuffleTraversesVisibleQueueAndManualOrderExitsShuffle() throws {
        let (library, playback) = fixture()
        playback.play(try XCTUnwrap(library.song("a")), within: ["a", "b", "c"], sourceID: "A")
        for _ in 0..<3 { playback.cycleMode() }
        XCTAssertEqual(playback.mode, .shuffle)
        let traversal = playback.queue
        XCTAssertEqual(traversal.first, "a")
        playback.next()
        XCTAssertEqual(playback.current?.id, traversal[1])
        playback.previous()
        XCTAssertEqual(playback.current?.id, "a")
        playback.setQueue(traversal)
        XCTAssertEqual(playback.mode, .shuffle)
        playback.setQueue(Array(traversal.reversed()))
        XCTAssertEqual(playback.queue, Array(traversal.reversed()))
        XCTAssertEqual(playback.mode, .repeatAll)
        XCTAssertNil(playback.queueSource)
    }

    @MainActor
    func testLaterModeAndCurrentSongNoOpSurviveBackgroundOrderCompletion() async throws {
        let (library, playback) = fixture()
        playback.play(try XCTUnwrap(library.song("a")), within: ["a", "b", "c"], sourceID: "A")
        let rebuilt = expectation(description: "Source order cache completed")
        let observation = library.$orderGeneration.dropFirst().sink { _ in rebuilt.fulfill() }
        defer { observation.cancel() }
        library.saveOrder(["b", "a", "c"], playlistID: "A")
        for _ in 0..<3 { playback.cycleMode() }
        let traversal = playback.queue
        playback.appendToQueue(["a"])
        playback.insertNext(["a"])
        XCTAssertEqual(playback.queueSource, "A")
        await fulfillment(of: [rebuilt], timeout: 5)
        XCTAssertEqual(playback.mode, .shuffle)
        XCTAssertEqual(playback.queue, traversal)
        playback.cycleMode()
        XCTAssertEqual(playback.mode, .ordered)
        XCTAssertEqual(playback.queue, ["b", "a", "c"])
    }
}
#endif
