#if DEBUG
import Foundation

/// Simulator UI tests only. No fixture code is compiled into Release.
enum DebugFixtures {
    static func make() throws -> MusicState {
        try AppFiles.prepare()
        var state = MusicState()
        state.sources = [MusicSource(id: "internal-music", name: "小红花 / Music", isFolder: true, isInternal: true)]
        let names = ["01 花开", "02 风来", "03 月光"]
        for name in names {
            let file = name + ".wav"
            let url = AppFiles.music.appendingPathComponent(file)
            let wave = tone()
            try wave.write(to: url, options: .atomic)
            let id = AppFiles.hash("internal-music/" + file)
            state.songs.append(Song(id: id, sourceID: "internal-music", relativePath: file, name: file, title: name, duration: 20, size: Int64(wave.count), format: "WAV"))
            state.manualLyrics[id] = "[00:00]风经过窗边\n[00:03.25]让音乐在这里开花\n[00:04.08]不一样的间隔\n[00:09.20]仍然跟随时间\n[00:14.00]小红花"
        }
        state.queueIDs = state.songs.map(\.id)
        state.currentID = state.songs[1].id
        state.playlists = [Playlist(name: "喜欢", songIDs: state.queueIDs, coverPreset: 3)]
        state.settings.monitor = false
        return state
    }
    private static func tone() -> Data {
        let rate = 8000, frames = rate * 20
        func little(_ number: Int, bytes: Int) -> Data { Data((0..<bytes).map { UInt8((number >> ($0 * 8)) & 255) }) }
        var data = Data("RIFF".utf8) + little(36 + frames * 2, bytes: 4) + Data("WAVEfmt ".utf8)
        data += little(16, bytes: 4) + little(1, bytes: 2) + little(1, bytes: 2) + little(rate, bytes: 4) + little(rate * 2, bytes: 4) + little(2, bytes: 2) + little(16, bytes: 2)
        data += Data("data".utf8) + little(frames * 2, bytes: 4)
        for sample in 0..<frames {
            let value = Int16(sin(Double(sample) * 2 * .pi * 220 / Double(rate)) * 1000)
            data += little(Int(UInt16(bitPattern: value)), bytes: 2)
        }
        return data
    }
}
#endif
