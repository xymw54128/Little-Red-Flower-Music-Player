import XCTest
import Foundation
#if canImport(XiaohonghuaCore)
@testable import XiaohonghuaCore
#else
@testable import Xiaohonghua
#endif

final class Composer118Tests: XCTestCase {
    private func frame(_ id: String, _ value: String, version: Int) -> Data {
        let payload = Data([3]) + Data(value.utf8)
        let shifts = version == 4 ? [21, 14, 7, 0] : [24, 16, 8, 0]
        let mask = version == 4 ? 127 : 255
        return Data(id.utf8) + Data(shifts.map { UInt8((payload.count >> $0) & mask) }) + Data([0, 0]) + payload
    }
    private func flac(composer: String, key: String = "COMPOSER") -> Data {
        func little(_ value: Int) -> Data { Data((0..<4).map { UInt8((value >> ($0 * 8)) & 255) }) }
        let comments = ["TITLE=标签测试", "ARTIST=演唱者", "\(key)=\(composer)"]
        var body = little(0) + little(comments.count)
        for comment in comments { let bytes = Data(comment.utf8); body += little(bytes.count) + bytes }
        return Data("fLaC".utf8) + Data([0x84, UInt8((body.count >> 16) & 255), UInt8((body.count >> 8) & 255), UInt8(body.count & 255)]) + body
    }

    func testID3ComposerStaysDistinctFromArtistInBothSupportedVersions() {
        for version in 3...4 {
            let body = frame("TPE1", "演唱者", version: version) + frame("TCOM", "作曲者 · Composer", version: version)
            let tags = TagReader.id3(version: version, flags: 0, body: body)
            XCTAssertEqual(tags.artist, "演唱者")
            XCTAssertEqual(tags.composer, "作曲者 · Composer")
            XCTAssertEqual(TagReader.id3(version: version, flags: 0, body: frame("TPE1", "只有艺术家", version: version)).composer, "")
        }
    }
    func testFLACComposerPreservesUnicodeAndCaseInsensitiveKey() throws {
        let url = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString + ".flac")
        defer { try? FileManager.default.removeItem(at: url) }
        try flac(composer: "作曲者 / Composer", key: "composer").write(to: url)
        let tags = TagReader.read(url)
        XCTAssertEqual(tags.composer, "作曲者 / Composer")
        XCTAssertEqual(tags.artist, "演唱者")
    }
    func testRereadingSameSizeAndDateDetectsEditedAndRemovedComposer() throws {
        let url = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString + ".flac")
        defer { try? FileManager.default.removeItem(at: url) }
        let date = Date(timeIntervalSince1970: 1_700_000_000)
        let first = flac(composer: "甲"), second = flac(composer: "乙"), removed = flac(composer: "乙", key: "COMMENT_")
        XCTAssertEqual(first.count, second.count)
        XCTAssertEqual(second.count, removed.count)
        for (data, expected) in [(first, "甲"), (second, "乙"), (removed, "")] {
            try data.write(to: url)
            try FileManager.default.setAttributes([.modificationDate: date], ofItemAtPath: url.path)
            XCTAssertEqual(TagReader.read(url).composer, expected)
        }
    }
    func testLegacySongDefaultsComposerWithoutLosingAlbumOrderFields() throws {
        let data = Data(#"{"id":"old","sourceID":"folder","name":"old.flac","title":"旧歌","artist":"歌手","albumArtist":"专辑艺术家","disc":2,"track":7}"#.utf8)
        let song = try JSONDecoder().decode(Song.self, from: data)
        XCTAssertEqual(song.composer, "")
        XCTAssertEqual(song.albumArtist, "专辑艺术家")
        XCTAssertEqual(song.disc, 2)
        XCTAssertEqual(song.track, 7)
    }
    func testSongAndPortableBackupRoundTripComposer() throws {
        var song = Song(id: "test", sourceID: "folder", relativePath: "test.flac", name: "test.flac", title: "标题")
        song.artist = "艺术家"; song.composer = "作曲家"
        let decoded = try JSONDecoder().decode(Song.self, from: JSONEncoder().encode(song))
        XCTAssertEqual(decoded, song)
        var state = MusicState(); state.songs = [song]
        let backup = try JSONDecoder().decode(MusicBackup.self, from: JSONEncoder().encode(MusicBackup(state: state)))
        XCTAssertEqual(backup.songs.first?.composer, "作曲家")
        XCTAssertEqual(backup.songs.first?.artist, "艺术家")
    }
}
