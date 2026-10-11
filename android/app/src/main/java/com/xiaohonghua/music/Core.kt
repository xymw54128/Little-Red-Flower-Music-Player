package com.xiaohonghua.music

import java.text.Collator
import java.util.Locale

data class Song(
    val id: String,
    val uri: String,
    val name: String,
    val title: String,
    val artist: String = "未知歌手",
    val album: String = "未知专辑",
    val duration: Long = 0,
    val size: Long = 0,
    val modified: Long = 0,
    val format: String = "",
    val sampleRate: Int = 0,
    val bitDepth: Int = 0,
    val track: String = "",
    val lyrics: String = "",
    val artwork: String = "",
    val sources: Set<String> = emptySet(),
    val available: Boolean = true,
    val lyricFile: String = "",
    val artworkRevision: String = "",
) {
    // Matching on a new phone must not rely on provider-specific document IDs.
    val migrationKey: String get() = "${name.lowercase(Locale.ROOT)}|$size"
}

enum class SortMode(val label: String) { TITLE("歌曲名 A–Z"), ARTIST("歌手 A–Z"), RANDOM("随机排序"), CUSTOM("自定义排序") }
const val PLAYLIST_COVER_COUNT = 8
fun defaultPlaylistCover(id: String): Int = (id.hashCode() and Int.MAX_VALUE) % PLAYLIST_COVER_COUNT
data class Playlist(val id: String, val name: String, val songIds: List<String> = emptyList(), val sort: SortMode = SortMode.CUSTOM,
    val randomIds: List<String> = emptyList(), val coverPreset: Int = defaultPlaylistCover(id), val coverPhoto: String = "")
const val FAVORITES_PLAYLIST_ID = "favorites"
val Playlist.isFavorites: Boolean get() = id == FAVORITES_PLAYLIST_ID

fun normalizeFavorites(playlists: List<Playlist>, knownSongIds: Set<String>? = null): List<Playlist> {
    val candidates = playlists.filter { it.isFavorites || it.name.trim() == "我喜欢" }
    val adopted = candidates.firstOrNull { it.isFavorites } ?: candidates.firstOrNull() ?: Playlist(FAVORITES_PLAYLIST_ID,"我喜欢")
    val ordered = listOf(adopted) + candidates.filter { it !== adopted }
    val songIds = ordered.flatMap { it.songIds }.distinct().filter { knownSongIds == null || it in knownSongIds }
    val members = songIds.toSet()
    val photo = adopted.takeIf { it.coverPhoto.isNotEmpty() } ?: candidates.firstOrNull { it.coverPhoto.isNotEmpty() }
    val favorite = adopted.copy(id=FAVORITES_PLAYLIST_ID,name="我喜欢",songIds=songIds,
        randomIds=ordered.flatMap { it.randomIds }.distinct().filter(members::contains),
        coverPreset=photo?.coverPreset ?: adopted.coverPreset,coverPhoto=photo?.coverPhoto.orEmpty())
    return listOf(favorite) + playlists.filter { !it.isFavorites && it.name.trim() != "我喜欢" }
}

fun toggleFavorite(playlists: List<Playlist>, songId: String): List<Playlist> = normalizeFavorites(playlists).map { playlist ->
    if(!playlist.isFavorites) playlist else if(songId in playlist.songIds) {
        playlist.copy(songIds=playlist.songIds-songId,randomIds=playlist.randomIds-songId)
    } else playlist.copy(songIds=playlist.songIds+songId,randomIds=(playlist.randomIds+songId).distinct())
}
data class Folder(val uri: String, val name: String)
data class Settings(
    val theme: Int = 0,
    val lyricFont: String = "system",
    val lyricSize: Float = 23f,
    val lyricGap: Float = 24f,
    val dynamicLyrics: Boolean = true,
    val monitor: Boolean = true,
    val background: String = "plain",
    val opacity: Float = .35f,
    val scale: Float = 1f,
    val x: Float = .5f,
    val y: Float = .5f,
)
data class LyricLine(val time: Long, val text: String)
data class LyricMotion(val current: Int, val from: Int, val to: Int, val fraction: Float, val start: Long, val end: Long)

object Lrc {
    private val timestamp = Regex("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?]")
    private val offset = Regex("\\[offset:([+-]?\\d+)]", RegexOption.IGNORE_CASE)
    fun parse(text: String): List<LyricLine> {
        val shift = offset.find(text)?.groupValues?.get(1)?.toLongOrNull() ?: 0
        return text.lineSequence().flatMap { line ->
            val matches = timestamp.findAll(line).toList()
            val words = timestamp.replace(line, "").trim()
            matches.asSequence().mapNotNull { m ->
                val seconds = m.groupValues[2].toLong()
                if (seconds > 59) null else {
                    val fraction = m.groupValues[3].padEnd(3, '0').take(3).toLongOrNull() ?: 0
                    LyricLine((m.groupValues[1].toLong() * 60_000 + seconds * 1000 + fraction + shift).coerceAtLeast(0), words)
                }
            }
        }.sortedBy { it.time }.groupBy { it.time }.map { (time, lines) ->
            LyricLine(time, lines.map { it.text }.distinct().joinToString("\n"))
        }
    }
    fun current(lines: List<LyricLine>, position: Long): Int {
        var low = 0
        var high = lines.lastIndex
        var found = -1
        while (low <= high) {
            val middle = (low + high) ushr 1
            if (lines[middle].time <= position) { found = middle; low = middle + 1 } else high = middle - 1
        }
        return found
    }
    fun motion(lines: List<LyricLine>, position: Long, speed: Float = 1f): LyricMotion {
        val index=current(lines,position)
        if(index < 0 || index == lines.lastIndex) {
            val anchor=if(lines.isEmpty()) -1 else index.coerceAtLeast(0)
            return LyricMotion(index,anchor,anchor,0f,position,position)
        }
        val gap=lines[index+1].time-lines[index].time
        val rate=speed.takeIf {it.isFinite() && it > 0f} ?: 1f
        // Reserve more than a 60 Hz frame for presentation; very dense timestamps
        // shorten both the animation and margin instead of queuing late transitions.
        val margin=minOf(kotlin.math.ceil(24.0*rate).toLong(),(gap/2).coerceAtLeast(1))
        val end=lines[index+1].time-margin
        val duration=minOf((480*rate).toLong(),(gap*.55).toLong(),end-lines[index].time).coerceAtLeast(0)
        val start=end-duration
        val t=if(duration == 0L) 1f else ((position-start).toDouble()/duration).coerceIn(0.0,1.0).toFloat()
        return LyricMotion(index,index,index+1,t*t*(3-2*t),start,end)
    }
}

fun orderedSongs(songs: List<Song>, mode: SortMode, custom: List<String>, random: List<String>): List<Song> {
    val collator = Collator.getInstance(Locale.CHINA).apply { strength = Collator.PRIMARY }
    return when (mode) {
        SortMode.TITLE, SortMode.ARTIST -> songs.sortedWith { a, b ->
            val main = collator.compare(if (mode == SortMode.TITLE) a.title else a.artist, if (mode == SortMode.TITLE) b.title else b.artist)
            if (main != 0) main else collator.compare(a.title, b.title).takeIf { it != 0 } ?: a.id.compareTo(b.id)
        }
        else -> {
            val rank = (if (mode == SortMode.RANDOM) random else custom).withIndex().associate { it.value to it.index }
            songs.sortedWith(compareBy<Song> { rank[it.id] ?: Int.MAX_VALUE }.thenBy { it.title })
        }
    }
}

fun reconcileSongs(existing: List<Song>, scanned: List<Song>, source: String): List<Song> {
    val scannedById = scanned.associateBy { it.id }
    val result = existing.mapNotNull { old ->
        val fresh = scannedById[old.id]
        when {
            fresh != null -> fresh.copy(sources = old.sources + source)
            source in old.sources -> (old.sources - source).takeIf { it.isNotEmpty() }?.let { old.copy(sources = it) }
            else -> old
        }
    }.toMutableList()
    val known = existing.map { it.id }.toSet()
    result += scanned.filter { it.id !in known }.map { it.copy(sources = it.sources + source) }
    return result
}

fun timeLabel(ms: Long): String = "%d:%02d".format(Locale.ROOT, ms.coerceAtLeast(0) / 60_000, ms.coerceAtLeast(0) / 1000 % 60)
