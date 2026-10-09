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
        XCTAssertTrue(app.staticTexts["喜欢"].exists)
        app.tabBars.buttons["设置"].tap()
        XCTAssertTrue(app.staticTexts.matching(NSPredicate(format: "label CONTAINS %@", "浪雨林兴云魔王")).firstMatch.exists)
        app.buttons["歌词字体与动态效果"].tap()
        XCTAssertTrue(app.navigationBars["歌词设置"].waitForExistence(timeout: 5))
        XCTAssertEqual(app.sliders.count, 2)
    }
}
