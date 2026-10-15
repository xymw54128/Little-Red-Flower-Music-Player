import XCTest
import Foundation
#if canImport(XiaohonghuaCore)
@testable import XiaohonghuaCore
#else
@testable import Xiaohonghua
#endif

final class Collection111Tests: XCTestCase {
    private func songs(_ count: Int) -> [Song] {
        (0..<count).map { Song(id: "song-\($0)", sourceID: "folder", relativePath: "\($0).flac", name: "\($0).flac", title: String(format: "%04d", $0), size: Int64($0 + 1)) }
    }
    func testLegacyStateDecodesWithFavoritesAndRetiredSettingsDisabled() throws {
        let data = Data(#"{"songs":[],"playlists":[{"id":"old","name":"旧歌单","songIDs":[]}],"settings":{"monitor":true,"background":"photo","backgroundPhoto":"old.jpg","lyricFont":"mono"}}"#.utf8)
        let state = try JSONDecoder().decode(MusicState.self, from: data)
        XCTAssertEqual(state.playlists.first(where: { $0.id == "old" })?.description, "")
        XCTAssertEqual(state.playlists.filter(\.isFavorites).count, 1)
        XCTAssertFalse(state.settings.monitor)
        XCTAssertEqual(state.settings.background, "plain")
        XCTAssertEqual(state.settings.backgroundPhoto, "")
        XCTAssertEqual(state.settings.lyricFont, "kiwi")
        XCTAssertFalse(state.settings.nightMode)
        XCTAssertNil(state.queueSource)
    }
    func testEveryCurrentFontAndLegacySelectionHasStableMapping() {
        for name in ["system", "kai", "song", "xiaowei", "zenmaru", "kiwi", "kurenaido", "longcang"] {
            XCTAssertEqual(MusicSettings.normalizedFont(name), name)
        }
        XCTAssertEqual(MusicSettings.normalizedFont("serif"), "song")
        XCTAssertEqual(MusicSettings.normalizedFont("fangsong"), "kai")
        XCTAssertEqual(MusicSettings.normalizedFont("handdrawn"), "kurenaido")
        XCTAssertEqual(MusicSettings.normalizedFont("unknown"), "system")
    }
    func testThousandTrackNormalizationDeduplicatesAndPreservesSavedOrder() {
        var state = MusicState()
        state.songs = songs(1000)
        var updated = state.songs[500]; updated.album = "新专辑"
        state.songs.append(updated)
        state.customIDs = ["song-900", "song-900", "missing", "song-1"]
        state.queueIDs = ["song-1", "song-1", "missing"]
        state.playlists.append(Playlist(id: "mine", name: "测试", songIDs: ["song-900", "song-900", "missing"], coverPreset: 99, description: "描述"))
        state.normalize()
        XCTAssertEqual(state.songs.count, 1000)
        XCTAssertEqual(state.songs[500].album, "新专辑")
        XCTAssertEqual(Array(state.customIDs.prefix(2)), ["song-900", "song-1"])
        XCTAssertEqual(Set(state.customIDs).count, 1000)
        XCTAssertEqual(state.queueIDs, ["song-1"])
        XCTAssertEqual(state.playlists.last?.songIDs, ["song-900"])
        XCTAssertEqual(state.playlists.last?.coverPreset, 10)
        XCTAssertEqual(state.playlists.last?.description, "描述")
    }
    func testRefreshKeepsExistingOrderAndUsesLatestTagsWithoutRevivingDeletedFiles() {
        let previous = songs(1000)
        var scanned = Array(previous.dropFirst())
        scanned.reverse()
        scanned[0].title = "标签更新"
        let new = Song(id: "new", sourceID: "folder", relativePath: "new.flac", name: "new.flac", title: "新增")
        scanned += [new, new]
        let result = MusicOrder.reconcile(previous: previous, scanned: scanned, sourceIDs: ["folder"])
        XCTAssertEqual(result.count, 1000)
        XCTAssertEqual(result.first?.id, "song-1")
        XCTAssertEqual(result.last?.id, "new")
        XCTAssertEqual(result.first(where: { $0.id == "song-999" })?.title, "标签更新")
        XCTAssertFalse(result.contains(where: { $0.id == "song-0" }))
    }
    func testImportOnlyReplacesItsOwnSourceAndMergesNewCustomIDsOnce() {
        var previous = songs(1000)
        previous[0].sourceID = "other"
        let scanned = [Song(id: "new", sourceID: "added", relativePath: "n.flac", name: "n.flac", title: "新增")]
        let result = MusicOrder.reconcile(previous: previous, scanned: scanned, sourceIDs: ["added"])
        XCTAssertEqual(result.count, 1001)
        XCTAssertEqual(result.first?.sourceID, "other")
        let ids = MusicOrder.mergedIDs(["song-999", "song-999", "missing"], available: result.map(\.id))
        XCTAssertEqual(ids.first, "song-999")
        XCTAssertEqual(ids.last, "new")
        XCTAssertEqual(ids.count, 1001)
    }
    func testFavoritesMigrationKeepsExistingHeartsAndDescription() {
        var state = MusicState()
        state.songs = songs(2)
        state.playlists = [Playlist(id: "legacy-like", name: "我喜欢", songIDs: ["song-1"], description: "偏爱收藏")]
        state.normalize(); state.normalize()
        XCTAssertEqual(state.playlists.count, 1)
        XCTAssertEqual(state.playlists[0].id, Playlist.favoritesID)
        XCTAssertEqual(state.playlists[0].songIDs, ["song-1"])
        XCTAssertEqual(state.playlists[0].description, "偏爱收藏")
        XCTAssertEqual(state.playlists[0].coverPreset, 10)
    }
    func testThousandTrackPortableRestorePreservesDescriptionFavoritesAndNightMode() {
        var old = MusicState(); old.songs = songs(1000)
        old.playlists = [Playlist(id: Playlist.favoritesID, name: "我喜欢", songIDs: ["song-999"], coverPreset: 10, description: "收藏描述")]
        old.settings.nightMode = true; old.settings.lyricFont = "longcang"
        var current = MusicState()
        current.songs = old.songs.map { var song = $0; song.id = "new-" + song.id; return song }
        XCTAssertEqual(MusicOrder.restore(MusicBackup(state: old), into: &current), 1000)
        XCTAssertEqual(current.playlists.first(where: \.isFavorites)?.songIDs, ["new-song-999"])
        XCTAssertEqual(current.playlists.first(where: \.isFavorites)?.description, "收藏描述")
        XCTAssertTrue(current.settings.nightMode)
        XCTAssertEqual(current.settings.lyricFont, "longcang")
    }
    func testLightweightPlaybackDocumentContainsNoTagIndexAndCanRestoreSource() throws {
        var state = MusicState(); state.songs = songs(1000)
        let clock = PlaybackState(queueIDs: state.songs.map(\.id), currentID: "song-700", position: 81.2, mode: .shuffle, queueSource: "library")
        let data = try JSONEncoder().encode(clock)
        let json = try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any])
        XCTAssertNil(json["songs"]); XCTAssertNil(json["lyrics"]); XCTAssertNil(json["sources"])
        try JSONDecoder().decode(PlaybackState.self, from: data).apply(to: &state)
        state.normalize()
        XCTAssertEqual(state.currentID, "song-700")
        XCTAssertEqual(state.position, 81.2)
        XCTAssertEqual(state.queueSource, "library")
        XCTAssertEqual(state.mode, .shuffle)
    }
    func testLegacySongDecodesAlbumCreditsWithoutNewKeys() throws {
        let data = Data(#"{"id":"old","sourceID":"folder","relativePath":"old.flac","name":"old.flac","title":"歌","artist":"歌手","album":"专辑"}"#.utf8)
        let song = try JSONDecoder().decode(Song.self, from: data)
        XCTAssertEqual(song.albumArtist, "")
        XCTAssertEqual(song.track, 0)
        XCTAssertEqual(song.disc, 0)
        XCTAssertTrue(song.available)
    }
    func testAlbumGroupingUsesAlbumArtistAndKeepsSameTitleAlbumsDistinct() {
        var a = songs(1)[0]; a.album = "同名专辑"; a.artist = "演唱者甲"; a.albumArtist = "合集作者"
        var b = a; b.id = "b"; b.artist = "演唱者乙"
        XCTAssertEqual(a.albumKey, b.albumKey)
        b.albumArtist = "另一作者"
        XCTAssertNotEqual(a.albumKey, b.albumKey)
        b.albumArtist = ""; b.artist = "合集作者"
        XCTAssertEqual(a.albumKey, b.albumKey)
    }
    func testAlbumSongsUseDiscAndTrackBeforeTitlesAndUnknownTracksGoLast() {
        var items = songs(5)
        items[0].disc = 2; items[0].track = 1
        items[1].disc = 1; items[1].track = 3
        items[2].disc = 1; items[2].track = 1
        items[3].disc = 1; items[3].track = 0
        items[4].disc = 0; items[4].track = 2
        XCTAssertEqual(MusicOrder.albumSongs(items).map(\.id), ["song-2", "song-4", "song-1", "song-3", "song-0"])
    }
    func testPreparedSearchMatchesChinesePinyinInitialsAlbumAndFullwidthDigits() {
        var song = songs(1)[0]
        song.title = "稻香 １２３"; song.artist = "周杰伦"; song.album = "魔杰座"
        let entry = SongSearchEntry(song)
        for query in ["稻香", "daoxiang", "dx", "zhoujielun", "zjl", "魔杰座", "123"] {
            XCTAssertTrue(entry.matches([SongSearchEntry.normalized(query)]), query)
        }
        XCTAssertTrue(entry.matches(["dx", "zjl"]))
        XCTAssertFalse(entry.matches(["不存在"]))
        XCTAssertTrue(entry.represents(song))
        song.artist = "新歌手"
        XCTAssertFalse(entry.represents(song))
    }
    func testID3AlbumArtistTrackAndDiscFrames() {
        func frame(_ id: String, _ value: String) -> Data {
            let body = Data([3]) + Data(value.utf8)
            return Data(id.utf8) + Data([0, 0, UInt8(body.count >> 8), UInt8(body.count & 255), 0, 0]) + body
        }
        let body = frame("TPE2", "专辑作者") + frame("TRCK", "07/12") + frame("TPOS", "2/3")
        let tags = TagReader.id3(version: 3, flags: 0, body: body)
        XCTAssertEqual(tags.albumArtist, "专辑作者")
        XCTAssertEqual(tags.track, 7)
        XCTAssertEqual(tags.disc, 2)
    }

    func testUnknownFLACPaddingIsSkippedBeforeReadingTags() throws {
        let padding = Data(repeating: 0, count: 2 * 1024 * 1024)
        let comments = ["TITLE=padding之后的歌曲", "ALBUMARTIST=合集作者", "TRACKNUMBER=4/8", "DISCNUMBER=2"]
        func little(_ value: Int) -> Data { Data((0..<4).map { UInt8((value >> ($0 * 8)) & 255) }) }
        var vorbis = little(0) + little(comments.count)
        for comment in comments { let data = Data(comment.utf8); vorbis += little(data.count) + data }
        func block(_ type: UInt8, _ data: Data) -> Data { Data([type, UInt8((data.count >> 16) & 255), UInt8((data.count >> 8) & 255), UInt8(data.count & 255)]) + data }
        let url = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString + ".flac")
        try (Data("fLaC".utf8) + block(1, padding) + block(0x84, vorbis)).write(to: url)
        defer { try? FileManager.default.removeItem(at: url) }
        let tags = TagReader.read(url)
        XCTAssertEqual(tags.title, "padding之后的歌曲")
        XCTAssertEqual(tags.albumArtist, "合集作者")
        XCTAssertEqual(tags.track, 4)
        XCTAssertEqual(tags.disc, 2)
    }
}
