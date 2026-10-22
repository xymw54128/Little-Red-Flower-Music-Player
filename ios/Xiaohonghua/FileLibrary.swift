import Foundation
import AVFoundation
import CryptoKit
import ImageIO
import UIKit

enum AppFiles {
    static let documents = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
    static let music = documents.appendingPathComponent("Music", isDirectory: true)
    static let support = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0].appendingPathComponent("Xiaohonghua", isDirectory: true)
    static let images = support.appendingPathComponent("Images", isDirectory: true)
    static let state = support.appendingPathComponent("library.json")
    static let playback = support.appendingPathComponent("playback.json")
    static func prepare() throws {
        for directory in [music, support, images] {
            try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
            try? FileManager.default.setAttributes([.protectionKey: FileProtectionType.completeUntilFirstUserAuthentication], ofItemAtPath: directory.path)
        }
    }
    static func image(_ name: String) -> URL? {
        guard !name.isEmpty, URL(fileURLWithPath: name).lastPathComponent == name else { return nil }
        return images.appendingPathComponent(name)
    }
    static func hash(_ text: String) -> String { SHA256.hash(data: Data(text.utf8)).map { String(format: "%02x", $0) }.joined() }
}

final class ScopedFile {
    let root: URL
    let url: URL
    let stale: Bool
    private let scoped: Bool
    init(source: MusicSource, relativePath: String = "") throws {
        var bookmarkStale = false
        if source.isInternal { root = AppFiles.music }
        else {
            guard let bookmark = source.bookmark else { throw LibraryError.authorization }
            root = try URL(resolvingBookmarkData: bookmark, options: [.withoutUI], relativeTo: nil, bookmarkDataIsStale: &bookmarkStale)
        }
        stale = bookmarkStale
        scoped = root.startAccessingSecurityScopedResource()
        let candidate = relativePath.isEmpty ? root : root.appendingPathComponent(relativePath).standardizedFileURL
        guard relativePath.isEmpty || candidate.path.hasPrefix(root.standardizedFileURL.path + "/") else {
            if scoped { root.stopAccessingSecurityScopedResource() }
            throw LibraryError.authorization
        }
        url = candidate
    }
    deinit { if scoped { root.stopAccessingSecurityScopedResource() } }
}

enum LibraryError: LocalizedError {
    case authorization, unsupported, invalidBackup
    var errorDescription: String? {
        switch self {
        case .authorization: return "文件访问已失效，请重新选择原文件或文件夹。"
        case .unsupported: return "该文件无法解码，请检查音乐格式或文件是否完整。"
        case .invalidBackup: return "这不是受支持的小红花 iOS 备份。"
        }
    }
}

struct ScanResult { var songs: [Song]; var sources: [MusicSource]; var errors: [String] }

actor FileLibrary {
    private var saveRevision: UInt64 = 0
    private var playbackRevision: UInt64 = 0
    private let formats: Set<String> = ["flac", "mp3", "wav", "m4a", "aac", "aif", "aiff", "caf", "mp4"]

    func load() throws -> MusicState {
        try AppFiles.prepare()
        var state = MusicState()
        if FileManager.default.fileExists(atPath: AppFiles.state.path) {
            state = try JSONDecoder().decode(MusicState.self, from: Data(contentsOf: AppFiles.state))
        }
        if let data = try? Data(contentsOf: AppFiles.playback), let clock = try? JSONDecoder().decode(PlaybackState.self, from: data) { clock.apply(to: &state) }
        state.normalize()
        return state
    }

    func save(_ state: MusicState, revision: UInt64) throws {
        guard revision >= saveRevision else { return }
        let data = try JSONEncoder().encode(state)
        try data.write(to: AppFiles.state, options: .atomic)
        saveRevision = revision
    }

    func savePlayback(_ playback: PlaybackState, revision: UInt64) throws {
        guard revision >= playbackRevision else { return }
        try AppFiles.prepare()
        try JSONEncoder().encode(playback).write(to: AppFiles.playback, options: .atomic)
        playbackRevision = revision
    }

    func source(_ url: URL, folder: Bool, replacingID: String? = nil) throws -> MusicSource {
        let scoped = url.startAccessingSecurityScopedResource()
        defer { if scoped { url.stopAccessingSecurityScopedResource() } }
        let bookmark = try url.bookmarkData(options: [.minimalBookmark], includingResourceValuesForKeys: nil, relativeTo: nil)
        return MusicSource(id: replacingID ?? UUID().uuidString, name: url.lastPathComponent, bookmark: bookmark, isFolder: folder)
    }

    func locations(_ sources: [MusicSource]) -> Set<String> {
        Set(sources.compactMap { source in
            guard let scoped = try? ScopedFile(source: source) else { return nil }
            return scoped.root.standardizedFileURL.path
        })
    }

    func scan(sources: [MusicSource], previous: [Song], force: Bool) async throws -> ScanResult {
        var songs: [Song] = [], updated = sources, errors: [String] = []
        let old = Dictionary(previous.map { ($0.id, $0) }, uniquingKeysWith: { _, last in last })
        let bySource = Dictionary(grouping: previous, by: \.sourceID)
        for index in updated.indices {
            try Task.checkCancellation()
            let source = updated[index]
            do {
                if source.isInternal { try AppFiles.prepare() }
                let access = try ScopedFile(source: source)
                if access.stale, let renewed = try? access.root.bookmarkData(options: [.minimalBookmark], includingResourceValuesForKeys: nil, relativeTo: nil) { updated[index].bookmark = renewed }
                let urls = try enumerate(access.root, folder: source.isFolder)
                var sourceSongs: [Song] = []
                sourceSongs.reserveCapacity(urls.count)
                for (fileIndex, url) in urls.enumerated() where formats.contains(url.pathExtension.lowercased()) {
                    try Task.checkCancellation()
                    if fileIndex % 16 == 0 { await Task.yield() }
                    let values = try url.resourceValues(forKeys: [.fileSizeKey, .contentModificationDateKey, .isRegularFileKey])
                    guard values.isRegularFile == true else { continue }
                    let relative = source.isFolder ? String(url.path.dropFirst(access.root.path.count + 1)) : ""
                    let id = AppFiles.hash(source.id + "/" + relative)
                    let lrc = sidecar(url)
                    let lyricTime = lrc.flatMap { try? $0.resourceValues(forKeys: [.contentModificationDateKey]).contentModificationDate?.timeIntervalSince1970 } ?? 0
                    let modified = values.contentModificationDate?.timeIntervalSince1970 ?? 0
                    let size = Int64(values.fileSize ?? 0)
                    if !force, var cached = old[id], cached.modified == modified, cached.size == size, cached.lyricModified == lyricTime {
                        cached.available = true
                        sourceSongs.append(cached)
                        continue
                    }
                    var tags = TagReader.read(url)
                    let asset = AVURLAsset(url: url)
                    if tags.duration == 0, let duration = try? await asset.load(.duration), duration.seconds.isFinite { tags.duration = duration.seconds }
                    // Complete FLAC tags come directly from the bounded reader; do not ask AVFoundation to parse them a second time.
                    if (tags.title.isEmpty || tags.artist.isEmpty || tags.album.isEmpty || tags.artwork == nil), let metadata = try? await asset.load(.commonMetadata) {
                        for item in metadata {
                            switch item.commonKey {
                            case .commonKeyTitle: if tags.title.isEmpty { tags.title = (try? await item.load(.stringValue)) ?? "" }
                            case .commonKeyArtist: if tags.artist.isEmpty { tags.artist = (try? await item.load(.stringValue)) ?? "" }
                            case .commonKeyAlbumName: if tags.album.isEmpty { tags.album = (try? await item.load(.stringValue)) ?? "" }
                            case .commonKeyArtwork: if tags.artwork == nil { tags.artwork = try? await item.load(.dataValue) }
                            default: break
                            }
                        }
                    }
                    // The bounded FLAC reader already handles COMPOSER. Other containers
                    // may expose composer only through their format-specific metadata.
                    if tags.composer.isEmpty, url.pathExtension.lowercased() != "flac",
                       let formats = try? await asset.load(.availableMetadataFormats) {
                        for format in formats {
                            guard let metadata = try? await asset.loadMetadata(for: format) else { continue }
                            for item in metadata where item.identifier == .iTunesMetadataComposer || item.identifier == .id3MetadataComposer {
                                let value = ((try? await item.load(.stringValue)) ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
                                if !value.isEmpty { tags.composer = value; break }
                            }
                            if !tags.composer.isEmpty { break }
                        }
                    }
                    if let lrc, let data = try? boundedData(lrc, maximum: 2 * 1024 * 1024), let text = Lyrics.decode(data) { tags.lyrics = text }
                    var artwork = ""
                    if let image = tags.artwork {
                        let contentHash = SHA256.hash(data: image).map { String(format: "%02x", $0) }.joined()
                        let name = id + "-" + String(contentHash.prefix(12)) + ".jpg"
                        if let dest = AppFiles.image(name) {
                            if FileManager.default.fileExists(atPath: dest.path) { artwork = name }
                            else if let preview = Self.thumbnail(image) { try preview.write(to: dest, options: .atomic); artwork = name }
                        }
                    }
                    sourceSongs.append(Song(id: id, sourceID: source.id, relativePath: relative, name: url.lastPathComponent,
                        title: tags.title.isEmpty ? url.deletingPathExtension().lastPathComponent : tags.title,
                        artist: tags.artist.isEmpty ? "未知歌手" : tags.artist, composer: tags.composer.trimmingCharacters(in: .whitespacesAndNewlines), albumArtist: tags.albumArtist, track: tags.track, disc: tags.disc, album: tags.album.isEmpty ? "未知专辑" : tags.album,
                        duration: tags.duration, size: size, modified: modified, format: url.pathExtension.uppercased(),
                        sampleRate: tags.sampleRate, bitDepth: tags.bitDepth, lyrics: tags.lyrics, artwork: artwork, lyricModified: lyricTime))
                }
                songs += sourceSongs
                updated[index].error = nil
            } catch is CancellationError { throw CancellationError() }
            catch {
                // A provider failure is not a confirmed deletion. Preserve its index.
                songs += (bySource[source.id] ?? []).map { var s = $0; s.available = false; return s }
                let message = "\(source.name)：\(error.localizedDescription)"
                errors.append(message)
                updated[index].error = message
            }
        }
        try Task.checkCancellation()
        return ScanResult(songs: MusicOrder.reconcile(previous: previous, scanned: songs, sourceIDs: Set(sources.map(\.id))), sources: updated, errors: errors)
    }

    private func enumerate(_ root: URL, folder: Bool) throws -> [URL] {
        guard folder else {
            guard FileManager.default.fileExists(atPath: root.path) else { throw LibraryError.authorization }
            return [root]
        }
        var failure: Error?
        guard let files = FileManager.default.enumerator(at: root, includingPropertiesForKeys: [.isRegularFileKey, .fileSizeKey, .contentModificationDateKey], options: [.skipsHiddenFiles, .skipsPackageDescendants], errorHandler: { _, error in failure = error; return false }) else { throw LibraryError.authorization }
        var urls: [URL] = []
        for case let url as URL in files { if formats.contains(url.pathExtension.lowercased()) { urls.append(url) } }
        if let failure { throw failure }
        return urls.sorted { $0.path < $1.path }
    }

    private func sidecar(_ url: URL) -> URL? {
        let base = url.deletingPathExtension()
        for ext in ["lrc", "LRC"] {
            let file = base.appendingPathExtension(ext)
            if FileManager.default.fileExists(atPath: file.path) { return file }
        }
        return nil
    }
    func readLyrics(_ url: URL) throws -> String {
        let scoped = url.startAccessingSecurityScopedResource()
        defer { if scoped { url.stopAccessingSecurityScopedResource() } }
        guard let text = Lyrics.decode(try boundedData(url, maximum: 2 * 1024 * 1024)) else { throw LibraryError.unsupported }
        return text
    }
    func readBackup(_ url: URL) throws -> MusicBackup {
        let scoped = url.startAccessingSecurityScopedResource()
        defer { if scoped { url.stopAccessingSecurityScopedResource() } }
        let backup = try JSONDecoder().decode(MusicBackup.self, from: boundedData(url, maximum: 64 * 1024 * 1024))
        guard backup.kind == "xiaohonghua-ios", backup.version == 1 else { throw LibraryError.invalidBackup }
        return backup
    }
    private func boundedData(_ url: URL, maximum: Int) throws -> Data {
        let file = try FileHandle(forReadingFrom: url)
        defer { try? file.close() }
        let data = try file.read(upToCount: maximum + 1) ?? Data()
        guard data.count <= maximum else { throw LibraryError.unsupported }
        return data
    }
    func savePhoto(_ data: Data) throws -> String {
        guard let preview = Self.thumbnail(data) else { throw LibraryError.unsupported }
        let name = UUID().uuidString + ".jpg"
        try preview.write(to: AppFiles.images.appendingPathComponent(name), options: .atomic)
        return name
    }
    static func thumbnail(_ data: Data) -> Data? {
        guard let image = CGImageSourceCreateWithData(data as CFData, nil),
              let thumb = CGImageSourceCreateThumbnailAtIndex(image, 0, [kCGImageSourceCreateThumbnailFromImageAlways: true,
                    kCGImageSourceThumbnailMaxPixelSize: 1600, kCGImageSourceCreateThumbnailWithTransform: true] as CFDictionary) else { return nil }
        return UIImage(cgImage: thumb).jpegData(compressionQuality: 0.86)
    }
}
