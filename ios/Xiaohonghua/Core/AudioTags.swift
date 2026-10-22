import Foundation

struct AudioTags {
    var title = "", artist = "", composer = "", album = "", albumArtist = "", lyrics = ""
    var track = 0, disc = 0
    var duration: Double = 0
    var sampleRate = 0, bitDepth = 0
    var artwork: Data?
}

enum TagReader {
    static let maximumBlock = 16 * 1024 * 1024

    static func read(_ url: URL) -> AudioTags {
        guard let file = try? FileHandle(forReadingFrom: url) else { return AudioTags() }
        defer { try? file.close() }
        guard let head = try? file.read(upToCount: 10), head.count >= 4 else { return AudioTags() }
        if head.prefix(4) == Data("fLaC".utf8) {
            try? file.seek(toOffset: 4)
            return flac(file)
        }
        if head.prefix(3) == Data("ID3".utf8), head.count == 10 {
            let size = syncSafe(Array(head[6..<10]))
            guard size <= maximumBlock, let body = try? file.read(upToCount: size), body.count == size else { return AudioTags() }
            return id3(version: Int(head[3]), flags: head[5], body: body)
        }
        return AudioTags()
    }

    private static func flac(_ file: FileHandle) -> AudioTags {
        var result = AudioTags(), total = 0
        guard let end = try? file.seekToEnd() else { return result }
        try? file.seek(toOffset: 4)
        for _ in 0..<256 {
            guard let h = try? file.read(upToCount: 4), h.count == 4 else { break }
            let count = Int(h[1]) << 16 | Int(h[2]) << 8 | Int(h[3])
            total += count
            guard count <= maximumBlock, total <= 64 * 1024 * 1024 else { break }
            let type = h[0] & 0x7F
            if ![0, 4, 6].contains(type) {
                // FLAC padding/application/seek blocks need no allocation. Check their bounds before seeking.
                guard let position = try? file.offset(), UInt64(count) <= end - min(position, end) else { break }
                do { try file.seek(toOffset: position + UInt64(count)) } catch { break }
                if h[0] & 0x80 != 0 { break }
                continue
            }
            guard let block = try? file.read(upToCount: count), block.count == count else { break }
            switch type {
            case 0:
                if block.count >= 18 {
                    let bits = block[10..<18].reduce(UInt64(0)) { ($0 << 8) | UInt64($1) }
                    result.sampleRate = Int(bits >> 44)
                    result.bitDepth = Int((bits >> 36) & 31) + 1
                    if result.sampleRate > 0 { result.duration = Double(bits & 0xFFFFFFFFF) / Double(result.sampleRate) }
                }
            case 4:
                var cursor = ByteCursor(block)
                guard let vendor = cursor.uint32(little: true), cursor.skip(Int(vendor)),
                      let entries = cursor.uint32(little: true), entries <= 100_000 else { continue }
                for _ in 0..<entries {
                    guard let length = cursor.uint32(little: true), Int(length) <= maximumBlock,
                          let data = cursor.take(Int(length)), let value = String(data: data, encoding: .utf8),
                          let equal = value.firstIndex(of: "=") else { break }
                    let key = value[..<equal].uppercased(), text = String(value[value.index(after: equal)...])
                    switch key {
                    case "TITLE": result.title = text
                    case "ARTIST": result.artist = result.artist.isEmpty ? text : result.artist + " / " + text
                    case "COMPOSER": result.composer = result.composer.isEmpty ? text : result.composer + " / " + text
                    case "ALBUM": result.album = text
                    case "ALBUMARTIST", "ALBUM ARTIST": result.albumArtist = text
                    case "TRACKNUMBER", "TRACK": result.track = number(text)
                    case "DISCNUMBER", "DISC": result.disc = number(text)
                    case "LYRICS", "UNSYNCEDLYRICS", "LYRIC": result.lyrics = text
                    default: break
                    }
                }
            case 6:
                var cursor = ByteCursor(block)
                guard let pictureType = cursor.uint32(), let mimeLength = cursor.uint32(), mimeLength < 1000,
                      cursor.skip(Int(mimeLength)), let descriptionLength = cursor.uint32(),
                      Int(descriptionLength) <= maximumBlock, cursor.skip(Int(descriptionLength)), cursor.skip(16),
                      let length = cursor.uint32(), Int(length) <= maximumBlock, let image = cursor.take(Int(length)) else { continue }
                if result.artwork == nil || pictureType == 3 { result.artwork = image }
            default: break
            }
            if h[0] & 0x80 != 0 { break }
        }
        return result
    }

    static func id3(version: Int, flags: UInt8, body: Data) -> AudioTags {
        guard [3, 4].contains(version) else { return AudioTags() }
        var bytes = Array(body)
        if flags & 0x80 != 0 {
            var cleaned: [UInt8] = [], i = 0
            while i < bytes.count {
                cleaned.append(bytes[i])
                if bytes[i] == 0xFF, i + 1 < bytes.count, bytes[i + 1] == 0 { i += 1 }
                i += 1
            }
            bytes = cleaned
        }
        var cursor = ByteCursor(Data(bytes)), result = AudioTags()
        if flags & 0x40 != 0 {
            guard let raw = cursor.take(4) else { return result }
            let size = version == 4 ? syncSafe(Array(raw)) - 4 : bigEndian(Array(raw))
            guard size >= 0, cursor.skip(size) else { return result }
        }
        while let header = cursor.take(10) {
            let id = String(data: header.prefix(4), encoding: .ascii) ?? ""
            if header[0] == 0 { break }
            let count = version == 4 ? syncSafe(Array(header[4..<8])) : bigEndian(Array(header[4..<8]))
            guard count > 0, count <= maximumBlock, let frame = cursor.take(count) else { break }
            guard header[8] == 0, header[9] == 0, !frame.isEmpty else { continue }
            switch id {
            case "TIT2": result.title = text(frame.dropFirst(), encoding: frame[0])
            case "TPE1": result.artist = text(frame.dropFirst(), encoding: frame[0])
            case "TCOM": result.composer = text(frame.dropFirst(), encoding: frame[0])
            case "TALB": result.album = text(frame.dropFirst(), encoding: frame[0])
            case "TPE2": result.albumArtist = text(frame.dropFirst(), encoding: frame[0])
            case "TRCK": result.track = number(text(frame.dropFirst(), encoding: frame[0]))
            case "TPOS": result.disc = number(text(frame.dropFirst(), encoding: frame[0]))
            case "USLT":
                if frame.count > 4, let start = terminator(frame, from: 4, wide: frame[0] == 1 || frame[0] == 2) {
                    result.lyrics = text(frame[start...], encoding: frame[0])
                }
            case "APIC":
                if let mimeEnd = frame.dropFirst().firstIndex(of: 0), mimeEnd + 2 < frame.count,
                   let start = terminator(frame, from: mimeEnd + 2, wide: frame[0] == 1 || frame[0] == 2), start < frame.count {
                    result.artwork = Data(frame[start...])
                }
            default: break
            }
        }
        return result
    }

    private static func number(_ value: String) -> Int {
        let first = String(value.split(separator: "/").first ?? "")
        return max(0, Int(first.trimmingCharacters(in: .whitespacesAndNewlines)) ?? 0)
    }

    private static func text(_ bytes: Data.SubSequence, encoding: UInt8) -> String {
        let enc: String.Encoding
        switch encoding { case 0: enc = .isoLatin1; case 1: enc = .utf16; case 2: enc = .utf16BigEndian; default: enc = .utf8 }
        return (String(data: Data(bytes), encoding: enc) ?? "").trimmingCharacters(in: CharacterSet(charactersIn: "\0\r\n "))
    }
    private static func terminator(_ data: Data, from start: Int, wide: Bool) -> Int? {
        let step = wide ? 2 : 1
        var index = start
        while index + step <= data.count {
            if data[index] == 0 && (!wide || data[index + 1] == 0) { return index + step }
            index += step
        }
        return nil
    }
    private static func bigEndian(_ bytes: [UInt8]) -> Int { bytes.reduce(0) { ($0 << 8) | Int($1) } }
    private static func syncSafe(_ bytes: [UInt8]) -> Int { bytes.reduce(0) { ($0 << 7) | Int($1 & 0x7F) } }
}

private struct ByteCursor {
    private let bytes: Data
    private var index = 0
    init(_ data: Data) { bytes = data }
    mutating func take(_ count: Int) -> Data? {
        guard count >= 0, count <= bytes.count - index else { return nil }
        defer { index += count }
        return Data(bytes[index..<(index + count)])
    }
    mutating func skip(_ count: Int) -> Bool {
        guard count >= 0, count <= bytes.count - index else { return false }
        index += count
        return true
    }
    mutating func uint32(little: Bool = false) -> UInt32? {
        guard let data = take(4) else { return nil }
        return (little ? Array(data.reversed()) : Array(data)).reduce(0) { ($0 << 8) | UInt32($1) }
    }
}
