import XCTest
import Foundation
#if canImport(XiaohonghuaCore)
@testable import XiaohonghuaCore
#else
@testable import Xiaohonghua
#endif

final class Lyrics118Tests: XCTestCase {
    func testPreviewIndexUsesExactOffsetAdjustedTimestampRatherThanMotionLead() {
        let lines = Lyrics.parse("[offset:+50]\n[00:00.00]第一行\n[00:00.05]第二行\n[00:00.15]第三行")
        for (time, index) in [(0.049, -1), (0.05, 0), (0.099, 0), (0.1, 1), (0.199, 1), (0.2, 2)] {
            XCTAssertEqual(Lyrics.current(lines, at: time), index)
        }
        // Detail motion may prepare the next line, while the preview keeps the
        // actual timestamp-bound text until that timestamp is reached.
        XCTAssertGreaterThan(Lyrics.motion(lines, at: 0.199).fraction, 0)
        XCTAssertEqual(Lyrics.current(lines, at: 0.199), 1)
    }

    func testBackwardSeekAndBeforeFirstTimestampHaveNoStalePreviewIndex() {
        let lines = Lyrics.parse("[00:02]第一行\n[00:04]第二行\n[00:06]第三行")
        for (time, index) in [(7.0, 2), (4, 1), (1, -1), (2, 0)] {
            XCTAssertEqual(Lyrics.current(lines, at: time), index)
        }
        XCTAssertEqual(Lyrics.current([], at: 9), -1)
    }
}

#if canImport(Xiaohonghua)
import UIKit

final class Lyrics118RenderingTests: XCTestCase {
    @MainActor
    func testAllIdleLabelsAreOpaqueAndKeepTheSameColorAfterEmphasis() {
        let playback = Playback()
        let view = LyricScrollView(frame: CGRect(x: 0, y: 0, width: 390, height: 500))
        let text = "[00:00]第一行\n[00:04]第二行\n[00:08]第三行"
        view.configure(text: text, settings: MusicSettings(), playback: playback, active: true, reduceMotion: true)
        view.layoutIfNeeded()
        defer { view.stop() }
        let labels = view.subviews.compactMap { $0 as? UILabel }.filter { $0.text == "第二行" || $0.text == "第三行" }
        XCTAssertEqual(labels.count, 2)
        XCTAssertTrue(labels.allSatisfy { $0.alpha == 1 })
        XCTAssertEqual(labels[0].textColor, labels[1].textColor)
        let idle = labels[0].textColor
        playback.progress.position = 4
        view.configure(text: text, settings: MusicSettings(), playback: playback, active: false, reduceMotion: true)
        view.configure(text: text, settings: MusicSettings(), playback: playback, active: true, reduceMotion: true)
        playback.progress.position = 0
        view.configure(text: text, settings: MusicSettings(), playback: playback, active: false, reduceMotion: true)
        view.configure(text: text, settings: MusicSettings(), playback: playback, active: true, reduceMotion: true)
        XCTAssertEqual(labels[0].textColor, idle)
        XCTAssertEqual(labels[0].textColor, labels[1].textColor)
        XCTAssertTrue(labels.allSatisfy { $0.alpha == 1 })
    }

    @MainActor
    func testPausedPreviewSeeksAndHiddenReturnUseCurrentPosition() {
        let playback = Playback()
        let view = CoverLyricPreviewView(frame: CGRect(x: 0, y: 0, width: 340, height: 48))
        let lines = Lyrics.parse("[offset:+50]\n[00:00]第一行\n[00:00.05]第二行\n[00:00.15]第三行")
        func configure(_ active: Bool = true) {
            view.configure(lines: lines, settings: MusicSettings(), playback: playback, active: active, reduceMotion: false)
            view.layoutIfNeeded()
        }
        configure()
        XCTAssertEqual(view.accessibilityLabel, "点击封面或左滑查看歌词")
        playback.progress.position = 0.099; configure()
        XCTAssertEqual(view.accessibilityLabel, "第一行")
        playback.progress.position = 0.1; configure()
        XCTAssertEqual(view.accessibilityLabel, "第二行")
        configure(false)
        playback.progress.position = 0.25; configure(false)
        configure()
        XCTAssertEqual(view.accessibilityLabel, "第三行")
        playback.progress.position = 0.05; configure()
        XCTAssertEqual(view.accessibilityLabel, "第一行")
        view.stop()
    }
}
#endif
