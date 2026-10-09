import XCTest
import Foundation
#if canImport(AVFAudio)
import AVFAudio
#endif
#if canImport(XiaohonghuaCore)
@testable import XiaohonghuaCore
#else
@testable import Xiaohonghua
#endif

final class CoreTests: XCTestCase {
    func testMultiTimestampAndTranslatedLines() {
        let lines = Lyrics.parse("[00:01.20][00:04.200]风来了\n[00:01.200]The wind\n[00:01.200]The wind\n[ar:歌手]")
        XCTAssertEqual(lines, [LyricLine(time: 1.2, text: "风来了\nThe wind"), LyricLine(time: 4.2, text: "风来了")])
    }
    func testOffsetFractionsAndInvalidSeconds() {
        let lines = Lyrics.parse("[offset:-500]\n[00:00.1]一\n[00:01:23]二\n[00:02.456]三\n[00:60]无效")
        XCTAssertEqual(lines.map(\.time), [0, 0.73, 1.956])
    }
    func testTimestampHighlightBoundary() {
        let lines = Lyrics.parse("[00:01]a\n[00:02]b")
        XCTAssertEqual(Lyrics.current(lines, at: 0.9), -1)
        XCTAssertEqual(Lyrics.current(lines, at: 1.999), 0)
        XCTAssertEqual(Lyrics.current(lines, at: 2), 1)
        XCTAssertEqual(Lyrics.current(lines, at: 999), 1)
        XCTAssertEqual(Lyrics.current([], at: 1), -1)
    }
    func testMotionEndsBeforeUnequalTimestamps() {
        let lines = [0.0, 0.04, 0.3, 1.0, 8.0].map { LyricLine(time: $0, text: "line") }
        for index in 0..<(lines.count - 1) {
            let gap = lines[index + 1].time - lines[index].time
            let end = lines[index + 1].time - min(0.025, gap / 2)
            let motion = Lyrics.motion(lines, at: end)
            XCTAssertEqual(motion.index, index)
            XCTAssertEqual(motion.fraction, 1, accuracy: 0.000001)
            XCTAssertEqual(Lyrics.current(lines, at: end), index)
            XCTAssertEqual(Lyrics.motion(lines, at: lines[index + 1].time).index, index + 1)
        }
    }
    func testMotionIsMonotonicWithoutOvershoot() {
        let lines = Lyrics.parse("[00:00]a\n[00:01]b")
        var previous = 0.0
        for frame in 0..<120 {
            let motion = Lyrics.motion(lines, at: Double(frame) / 120)
            XCTAssertGreaterThanOrEqual(motion.fraction, previous)
            XCTAssertLessThanOrEqual(motion.fraction, 1)
            previous = motion.fraction
        }
    }
    func testLowRefreshRateUsesEarlierSettlingWindow() {
        let lines = Lyrics.parse("[00:00]a\n[00:01]b")
        XCTAssertEqual(Lyrics.motion(lines, at: 0.95, frameInterval: 1.0 / 30).fraction, 1, accuracy: 0.000001)
        XCTAssertLessThan(Lyrics.motion(lines, at: 0.95, frameInterval: 1.0 / 120).fraction, 1)
        XCTAssertEqual(Lyrics.motion(lines, at: 0.85, frameInterval: 0.1).fraction, 1, accuracy: 0.000001)
    }
    func testPlainTextDoesNotInventTimestamps() { XCTAssertTrue(Lyrics.parse("无时间戳歌词").isEmpty) }
    func testUTF16LyricFile() {
        let data = Data([0xFF, 0xFE]) + "[00:01]你好".data(using: .utf16LittleEndian)!
        XCTAssertEqual(Lyrics.decode(data), "[00:01]你好")
    }
    func testLaunchOrderAndFinalPose() {
        XCTAssertEqual(LaunchMotion.caption(0), 0)
        XCTAssertGreaterThan(LaunchMotion.petal(0, at: 0.35), 0)
        XCTAssertEqual(LaunchMotion.petal(1, at: 0.35), 0)
        for petal in 0..<5 { XCTAssertEqual(LaunchMotion.petal(petal, at: 1.08), 1) }
        XCTAssertEqual(LaunchMotion.flight(2.34), 1)
        XCTAssertEqual(LaunchMotion.rotation(2.64), 1512, accuracy: 0.000001)
        XCTAssertEqual(LaunchMotion.caption(2.64), 0)
        XCTAssertEqual(LaunchMotion.background(2.64), 0)
    }
    func testLaunchBoundaryContinuity() {
        for time in [0.22, 1.12, 1.46, 1.50, 2.34, 2.64] {
            XCTAssertLessThan(abs(LaunchMotion.rotation(time + 0.000001) - LaunchMotion.rotation(time - 0.000001)), 0.1)
        }
    }
    private func song(_ id: String, title: String, name: String = "a.flac", size: Int64 = 20) -> Song {
        Song(id: id, sourceID: "source", relativePath: name, name: name, title: title, size: size)
    }
    func testSavedCustomOrderIncludesNewSongs() {
        let songs = [song("a", title: "A"), song("b", title: "B"), song("c", title: "C")]
        XCTAssertEqual(MusicOrder.ordered(songs, sort: .custom, ids: ["b", "b", "missing", "a"]).map(\.id), ["b", "a", "c"])
    }
    func testCaseInsensitiveTitleSort() {
        XCTAssertEqual(MusicOrder.ordered([song("b", title: "Zebra"), song("a", title: "apple")], sort: .title, ids: []).map(\.id), ["a", "b"])
    }
    func testBackupRemapsUniqueFilesAcrossDevices() {
        var old = MusicState()
        old.songs = [song("old", title: "一首歌")]
        old.playlists = [Playlist(name: "喜欢", songIDs: ["old"], coverPhoto: "secret.jpg")]
        old.manualLyrics = ["old": "[00:01]歌词"]
        old.settings.background = "photo"; old.settings.backgroundPhoto = "private.jpg"
        let backup = MusicBackup(state: old)
        var new = MusicState(); new.songs = [song("new", title: "一首歌")]
        XCTAssertEqual(MusicOrder.restore(backup, into: &new), 1)
        XCTAssertEqual(new.playlists[0].songIDs, ["new"])
        XCTAssertEqual(new.manualLyrics["new"], "[00:01]歌词")
        XCTAssertEqual(new.playlists[0].coverPhoto, "")
        XCTAssertEqual(new.settings.background, "plain")
    }
    func testBackupDoesNotGuessAmbiguousFilenames() {
        var old = MusicState(); old.songs = [song("old", title: "歌")]; old.playlists = [Playlist(name: "歌单", songIDs: ["old"])]
        var new = MusicState(); new.songs = [song("one", title: "歌"), song("two", title: "另一个同名文件")]
        XCTAssertEqual(MusicOrder.restore(MusicBackup(state: old), into: &new), 0)
        XCTAssertTrue(new.playlists[0].songIDs.isEmpty)
    }
    func testSettingsClampInvalidValues() {
        var settings = MusicSettings()
        settings.lyricSize = .nan; settings.lyricGap = -1; settings.theme = 100; settings.backgroundScale = .infinity
        settings.normalize()
        XCTAssertEqual(settings.lyricSize, 23); XCTAssertEqual(settings.lyricGap, 0); XCTAssertEqual(settings.theme, 4); XCTAssertEqual(settings.backgroundScale, 1)
    }
    func testFLACTagsAndDurationWithoutReadingAudio() throws {
        func little(_ value: Int) -> Data { Data((0..<4).map { UInt8((value >> ($0 * 8)) & 255) }) }
        var stream = Data(repeating: 0, count: 34)
        let packed = (UInt64(48000) << 44) | (UInt64(23) << 36) | UInt64(48000 * 5)
        for index in 0..<8 { stream[10 + index] = UInt8((packed >> (56 - index * 8)) & 255) }
        let comments = ["TITLE=小红花", "ARTIST=浪雨林兴云魔王", "ALBUM=专辑", "LYRICS=[00:01]歌词"]
        var vorbis = little(0) + little(comments.count)
        for comment in comments { let data = Data(comment.utf8); vorbis += little(data.count) + data }
        func block(_ type: UInt8, _ data: Data) -> Data { Data([type, UInt8((data.count >> 16) & 255), UInt8((data.count >> 8) & 255), UInt8(data.count & 255)]) + data }
        let url = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString + ".flac")
        try (Data("fLaC".utf8) + block(0, stream) + block(0x84, vorbis)).write(to: url)
        defer { try? FileManager.default.removeItem(at: url) }
        let result = TagReader.read(url)
        XCTAssertEqual(result.title, "小红花"); XCTAssertEqual(result.artist, "浪雨林兴云魔王")
        XCTAssertEqual(result.album, "专辑"); XCTAssertEqual(result.lyrics, "[00:01]歌词")
        XCTAssertEqual(result.duration, 5); XCTAssertEqual(result.sampleRate, 48000); XCTAssertEqual(result.bitDepth, 24)
    }
    func testTruncatedID3DoesNotTrap() {
        XCTAssertEqual(TagReader.id3(version: 3, flags: 0, body: Data([0x54, 0x49])).title, "")
        XCTAssertEqual(TagReader.id3(version: 4, flags: 0x40, body: Data(repeating: 0xFF, count: 8)).title, "")
    }
    func testID3UTF8Text() {
        let text = Data([3]) + Data("风经过窗边".utf8)
        let count = text.count
        let header = Data("TIT2".utf8) + Data([0, 0, UInt8(count >> 8), UInt8(count & 255), 0, 0])
        XCTAssertEqual(TagReader.id3(version: 3, flags: 0, body: header + text).title, "风经过窗边")
    }
    private var fixtureBundle: Bundle {
        #if SWIFT_PACKAGE
        return .module
        #else
        return Bundle(for: CoreTests.self)
        #endif
    }
    func testReal24BitFLACMetadata() throws {
        let url = try XCTUnwrap(fixtureBundle.url(forResource: "tone-24bit-96k", withExtension: "flac"))
        let result = TagReader.read(url)
        XCTAssertEqual(result.sampleRate, 96000)
        XCTAssertEqual(result.bitDepth, 24)
        XCTAssertGreaterThan(result.duration, 0)
    }
    #if canImport(AVFAudio)
    func testNativeFLACAndWAVPreparation() throws {
        for file in ["tone.flac", "tone-24bit-96k.flac", "tone.wav"] {
            let url = try XCTUnwrap(fixtureBundle.url(forResource: file, withExtension: nil))
            let player = try AVAudioPlayer(contentsOf: url)
            XCTAssertTrue(player.prepareToPlay(), file)
            XCTAssertGreaterThan(player.duration, 0, file)
            if file == "tone-24bit-96k.flac" { XCTAssertEqual(player.format.sampleRate, 96000, file) }
        }
    }
    #endif
}
