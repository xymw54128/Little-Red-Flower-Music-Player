import XCTest

final class PlayerUITests: XCTestCase {
    private func app() -> XCUIApplication {
        let app = XCUIApplication()
        app.launchEnvironment["XIAOHONGHUA_UI_TESTING"] = "1"
        app.launch()
        XCTAssertTrue(app.buttons["mini.open"].waitForExistence(timeout: 20))
        return app
    }
    func testMiniPlayerPreviousAndNext() {
        let app = app()
        app.buttons["上一首"].tap()
        XCTAssertEqual(app.buttons["mini.open"].value as? String, "01 花开")
        app.buttons["下一首"].tap()
        XCTAssertEqual(app.buttons["mini.open"].value as? String, "02 风来")
    }
    func testFullPlayerHasThreeControlsAndLyricsPage() {
        let app = app()
        app.buttons["mini.open"].tap()
        XCTAssertTrue(app.buttons["收起播放页"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.buttons["上一首"].exists)
        XCTAssertTrue(app.buttons["下一首"].exists)
        let start = app.coordinate(withNormalizedOffset: CGVector(dx: 0.8, dy: 0.35))
        let end = app.coordinate(withNormalizedOffset: CGVector(dx: 0.15, dy: 0.35))
        start.press(forDuration: 0.05, thenDragTo: end)
        XCTAssertTrue(app.staticTexts["风经过窗边"].waitForExistence(timeout: 5))
    }
    func testPlaylistsAndLyricSettingsAndAuthor() {
        let app = app()
        app.tabBars.buttons["歌单"].tap()
        XCTAssertTrue(app.staticTexts["我喜欢"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.staticTexts["喜欢"].exists)
        app.tabBars.buttons["设置"].tap()
        let about = app.buttons["关于小红花"]
        for _ in 0..<3 where !about.isHittable { app.swipeUp() }
        about.tap()
        XCTAssertTrue(app.staticTexts.matching(NSPredicate(format: "label CONTAINS %@", "浪雨林兴云魔王")).firstMatch.waitForExistence(timeout: 5))
        app.navigationBars.buttons.firstMatch.tap()
        let lyrics = app.buttons["歌词字体与动态效果"]
        for _ in 0..<3 where !lyrics.isHittable { app.swipeDown() }
        lyrics.tap()
        XCTAssertTrue(app.navigationBars["歌词设置"].waitForExistence(timeout: 5))
        XCTAssertEqual(app.sliders.count, 2)
    }
    func testDurationRemainsTheFullSongAfterSeeking() {
        let app = app()
        app.buttons["mini.open"].tap()
        let progress = app.descendants(matching: .any).matching(identifier: "播放进度").firstMatch
        XCTAssertTrue(progress.waitForExistence(timeout: 5))
        XCTAssertTrue(app.staticTexts["0:20"].exists)
        let start = progress.coordinate(withNormalizedOffset: CGVector(dx: 0.1, dy: 0.5))
        let end = progress.coordinate(withNormalizedOffset: CGVector(dx: 0.65, dy: 0.5))
        start.press(forDuration: 0.05, thenDragTo: end)
        expectation(for: NSPredicate { _, _ in
            guard let value = progress.value as? String else { return false }
            return !value.hasPrefix("0:00") && value.contains("共0:20")
        }, evaluatedWith: progress)
        waitForExpectations(timeout: 5)
        XCTAssertTrue((progress.value as? String)?.contains("共0:20") == true)
        XCTAssertTrue(app.staticTexts["0:20"].exists)
    }
    func testCoverAndPreviewOpenTheCleanLyricsPage() {
        let app = app()
        app.buttons["mini.open"].tap()
        let cover = app.descendants(matching: .any).matching(identifier: "player.artwork").firstMatch
        XCTAssertTrue(cover.waitForExistence(timeout: 5))
        cover.tap()
        XCTAssertTrue(app.staticTexts["02 风来 - 未知歌手"].waitForExistence(timeout: 5))
        XCTAssertFalse(app.buttons["导入歌词"].exists)
        XCTAssertFalse(app.buttons["回到当前"].exists)
        XCTAssertFalse(app.buttons["歌曲更多选项"].exists)
        app.buttons["封面页"].tap()
        let preview = app.descendants(matching: .any).matching(identifier: "player.lyric-preview").firstMatch
        XCTAssertTrue(preview.waitForExistence(timeout: 5))
        preview.tap()
        XCTAssertTrue(app.staticTexts["02 风来 - 未知歌手"].waitForExistence(timeout: 5))
    }
    func testVerticalDragKeepsTheCoverPageFixed() {
        let app = app()
        app.buttons["mini.open"].tap()
        let cover = app.descendants(matching: .any).matching(identifier: "player.artwork").firstMatch
        XCTAssertTrue(cover.waitForExistence(timeout: 5))
        let original = cover.frame
        let start = cover.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.8))
        let end = cover.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.2))
        start.press(forDuration: 0.05, thenDragTo: end)
        XCTAssertEqual(cover.frame.minY, original.minY, accuracy: 2)
        XCTAssertEqual(cover.frame.height, original.height, accuracy: 2)
        XCTAssertTrue(app.buttons["歌曲更多选项"].exists)
    }
    func testRootSwipeAndPlaylistEntryKeepNavigationAndPlaybackSeparate() {
        let app = app()
        let start = app.coordinate(withNormalizedOffset: CGVector(dx: 0.85, dy: 0.45))
        let end = app.coordinate(withNormalizedOffset: CGVector(dx: 0.15, dy: 0.45))
        start.press(forDuration: 0.05, thenDragTo: end)
        let playlistsTab = app.tabBars.buttons["歌单"]
        expectation(for: NSPredicate { _, _ in playlistsTab.isSelected }, evaluatedWith: playlistsTab)
        waitForExpectations(timeout: 5)
        let favorite = app.staticTexts["我喜欢"].firstMatch
        XCTAssertTrue(favorite.waitForExistence(timeout: 5))
        favorite.tap()
        XCTAssertTrue(app.navigationBars["我喜欢"].waitForExistence(timeout: 5))
        XCTAssertEqual(app.buttons["mini.open"].value as? String, "02 风来")
        start.press(forDuration: 0.05, thenDragTo: end)
        XCTAssertTrue(playlistsTab.isSelected)
        XCTAssertTrue(app.navigationBars["我喜欢"].exists)
    }


    func testRemovingFavoriteKeepsTheCurrentPlaybackAndRemovesItsRow() {
        let app = app()
        let like = app.buttons["喜欢这首歌"].firstMatch
        XCTAssertTrue(like.waitForExistence(timeout: 5))
        like.tap()
        app.tabBars.buttons["歌单"].tap()
        app.staticTexts["我喜欢"].firstMatch.tap()
        let song = app.staticTexts["01 花开"].firstMatch
        XCTAssertTrue(song.waitForExistence(timeout: 5))
        let unlike = app.buttons["取消喜欢"].firstMatch
        XCTAssertTrue(unlike.waitForExistence(timeout: 5))
        unlike.tap()
        expectation(for: NSPredicate { _, _ in !song.exists }, evaluatedWith: song)
        waitForExpectations(timeout: 5)
        XCTAssertEqual(app.buttons["mini.open"].value as? String, "02 风来")
    }

    func testRepeatedCoverAndLyricsTransitionsKeepTheSameSong() {
        let app = app()
        app.buttons["mini.open"].tap()
        for _ in 0..<4 {
            app.buttons["歌词页"].tap()
            XCTAssertTrue(app.staticTexts["02 风来 - 未知歌手"].waitForExistence(timeout: 5))
            app.buttons["封面页"].tap()
            let cover = app.descendants(matching: .any).matching(identifier: "player.artwork").firstMatch
            XCTAssertTrue(cover.waitForExistence(timeout: 5))
            XCTAssertTrue(app.buttons["上一首"].exists)
            XCTAssertTrue(app.buttons["下一首"].exists)
        }
        app.buttons["收起播放页"].tap()
        XCTAssertEqual(app.buttons["mini.open"].value as? String, "02 风来")
    }
}
