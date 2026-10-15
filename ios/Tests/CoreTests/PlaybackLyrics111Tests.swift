import XCTest
import Foundation
#if canImport(XiaohonghuaCore)
@testable import XiaohonghuaCore
#else
@testable import Xiaohonghua
#endif

final class PlaybackLyrics111Tests: XCTestCase {
    func testLyricStylesAndPositionShareContinuousPhaseAtEveryStamp() {
        let lines = Lyrics.parse("[00:00]一\n[00:00.04]二\n[00:00.35]三\n[00:04.20]四")
        for row in 0..<(lines.count - 1) {
            let gap = lines[row + 1].time - lines[row].time
            let settled = lines[row + 1].time - min(0.024, gap / 2)
            let before = Lyrics.motion(lines, at: settled, frameInterval: 1.0 / 120)
            let boundary = Lyrics.motion(lines, at: lines[row + 1].time, frameInterval: 1.0 / 120)
            XCTAssertEqual(before.fraction, 1, accuracy: 0.000001)
            XCTAssertEqual(Lyrics.emphasis(row, motion: before), 0, accuracy: 0.000001)
            XCTAssertEqual(Lyrics.emphasis(row + 1, motion: before), 1, accuracy: 0.000001)
            XCTAssertEqual(Lyrics.emphasis(row + 1, motion: boundary), 1, accuracy: 0.000001)
        }
    }
    func testBeforeFirstStampAndBackwardSeekDoNotRetainOldHighlight() {
        let lines = Lyrics.parse("[00:02]一\n[00:04]二\n[00:06]三")
        XCTAssertEqual(Lyrics.motion(lines, at: 1).index, -1)
        XCTAssertEqual(Lyrics.emphasis(0, motion: Lyrics.motion(lines, at: 1)), 0)
        let after = Lyrics.motion(lines, at: 8)
        let seek = Lyrics.motion(lines, at: 2)
        XCTAssertEqual(Lyrics.emphasis(2, motion: after), 1)
        XCTAssertEqual(Lyrics.emphasis(2, motion: seek), 0)
        XCTAssertEqual(Lyrics.emphasis(0, motion: seek), 1)
    }
    func testMotionWeightsAlwaysSumToOneAndStayBounded() {
        let lines = Lyrics.parse("[00:00]一\n[00:00.04]二\n[00:01]三\n[00:10]四")
        for frame in 0...1200 {
            let motion = Lyrics.motion(lines, at: Double(frame) / 120)
            let weights = lines.indices.map { Lyrics.emphasis($0, motion: motion) }
            XCTAssertEqual(weights.reduce(0, +), 1, accuracy: 0.000001)
            XCTAssertTrue(weights.allSatisfy { $0 >= 0 && $0 <= 1 })
        }
        XCTAssertEqual(Lyrics.easeOut(-1), 0)
        XCTAssertEqual(Lyrics.easeOut(2), 1)
    }
    func testThousandSongSourceReorderRetainsCurrentWithoutDuplicates() {
        let ids = (0..<1000).map(String.init)
        XCTAssertEqual(PlaybackOrder.synchronized(Array(ids.reversed()), currentID: "500"), Array(ids.reversed()))
        XCTAssertEqual(PlaybackOrder.synchronized(["a", "a", "b"], currentID: "playing"), ["playing", "a", "b"])
        XCTAssertEqual(PlaybackOrder.unique(ids + ids + ["missing"], available: Set(ids)), ids)
    }
    func testInsertNextMovesExistingItemsWithoutMovingCurrent() {
        XCTAssertEqual(PlaybackOrder.insertingNext(["d", "b", "b"], into: ["a", "b", "c", "d"], currentID: "b"), ["a", "b", "d", "c"])
        XCTAssertEqual(PlaybackOrder.insertingNext(["x", "y"], into: [], currentID: nil), ["x", "y"])
        XCTAssertEqual(PlaybackOrder.appending(["b", "c", "c"], to: ["a", "b"]), ["a", "b", "c"])
    }
}
