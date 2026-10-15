import Foundation

struct LyricLine: Equatable, Identifiable {
    var time: Double
    var text: String
    var id: Double { time }
}

enum Lyrics {
    static func parse(_ text: String) -> [LyricLine] {
        let stamp = try! NSRegularExpression(pattern: #"\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?\]"#)
        let offsetPattern = try! NSRegularExpression(pattern: #"\[offset:\s*([+-]?\d+)\]"#, options: .caseInsensitive)
        let whole = text as NSString
        let offset = offsetPattern.firstMatch(in: text, range: NSRange(location: 0, length: whole.length)).flatMap {
            Double(whole.substring(with: $0.range(at: 1)))
        } ?? 0
        var grouped: [Double: [String]] = [:]
        for raw in text.components(separatedBy: .newlines) {
            let ns = raw as NSString
            let matches = stamp.matches(in: raw, range: NSRange(location: 0, length: ns.length))
            guard let last = matches.last else { continue }
            let content = ns.substring(from: NSMaxRange(last.range)).trimmingCharacters(in: .whitespaces)
            guard !content.isEmpty else { continue }
            for match in matches {
                guard let minute = Double(ns.substring(with: match.range(at: 1))),
                      let second = Double(ns.substring(with: match.range(at: 2))), second < 60 else { continue }
                let fractionRange = match.range(at: 3)
                let digits = fractionRange.location == NSNotFound ? "" : ns.substring(with: fractionRange)
                let fraction = Double(digits + String(repeating: "0", count: 3 - digits.count)) ?? 0
                let time = max(0, minute * 60 + second + (fraction + offset) / 1000)
                if !(grouped[time] ?? []).contains(content) { grouped[time, default: []].append(content) }
            }
        }
        return grouped.keys.sorted().map { LyricLine(time: $0, text: grouped[$0]!.joined(separator: "\n")) }
    }

    static func current(_ lines: [LyricLine], at position: Double) -> Int {
        var low = 0, high = lines.count
        while low < high {
            let middle = (low + high) / 2
            if lines[middle].time <= position { low = middle + 1 } else { high = middle }
        }
        return low - 1
    }

    /// One audio-clock phase drives position, colour, opacity and scale together.
    /// Settle before the next stamp, with a shorter window for closely spaced lines.
    static func motion(_ lines: [LyricLine], at position: Double, frameInterval: Double = 1.0 / 60) -> (index: Int, fraction: Double) {
        guard !lines.isEmpty else { return (-1, 0) }
        let index = current(lines, at: position)
        guard index >= 0 else { return (-1, 0) }
        guard index + 1 < lines.count else { return (index, 0) }
        let gap = lines[index + 1].time - lines[index].time
        let interval = frameInterval.isFinite && frameInterval > 0 ? frameInterval : 1.0 / 60
        let margin = min(max(0.024, interval * 1.5), gap / 2)
        let end = lines[index + 1].time - margin
        let duration = max(0, min(0.300, gap * 0.55, end - lines[index].time))
        let start = end - duration
        let t = duration == 0 ? 1 : min(max((position - start) / duration, 0), 1)
        return (index, easeOut(t))
    }

    static func easeOut(_ fraction: Double) -> Double {
        let t = min(max(fraction, 0), 1)
        return 1 - (1 - t) * (1 - t) * (1 - t)
    }

    static func emphasis(_ row: Int, motion: (index: Int, fraction: Double)) -> Double {
        guard motion.index >= 0 else { return 0 }
        if row == motion.index { return 1 - motion.fraction }
        if row == motion.index + 1 { return motion.fraction }
        return 0
    }

    static func decode(_ data: Data) -> String? {
        if data.starts(with: [0xFF, 0xFE]) { return String(data: data.dropFirst(2), encoding: .utf16LittleEndian) }
        if data.starts(with: [0xFE, 0xFF]) { return String(data: data.dropFirst(2), encoding: .utf16BigEndian) }
        if let text = String(data: data, encoding: .utf8) { return text.replacingOccurrences(of: "\u{FEFF}", with: "") }
        // GB18030, including commonly encountered GBK LRC files.
        let encoding = String.Encoding(rawValue: 0x80000632)
        return String(data: data, encoding: encoding)
    }
}

enum LaunchMotion {
    static let duration = 2.640
    static func ease(_ time: Double, _ start: Double, _ duration: Double) -> Double {
        let t = min(max((time - start) / duration, 0), 1)
        return t * t * t * (t * (t * 6 - 15) + 10)
    }
    static func petal(_ index: Int, at time: Double) -> Double { ease(time, 0.220 + Double(index) * 0.150, 0.260) }
    static func caption(_ time: Double) -> Double { ease(time, 0.220, 0.240) * (1 - ease(time, 1.660, 0.320)) }
    static func background(_ time: Double) -> Double { 1 - ease(time, 1.480, 0.300) }
    static func flight(_ time: Double) -> Double { ease(time, 1.500, 0.840) }
    static func rotation(_ time: Double) -> Double { 72 * ease(time, 1.120, 0.340) + 1368 * flight(time) + 72 * ease(time, 2.340, 0.300) }
}
