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
    val albumArtist: String = "",
    val disc: String = "",
    val lyricRevision: String = "",
) {
    val albumDisplayArtist: String get() = albumArtist.trim().ifBlank {artist}
    val albumKey: String get() = "${albumDisplayArtist}\u0000$album"
    // Matching on a new phone must not rely on provider-specific document IDs.
    val migrationKey: String get() = "${name.lowercase(Locale.ROOT)}|$size"
}

enum class SortMode(val label: String) { TITLE("歌曲名 A–Z"), ARTIST("歌手 A–Z"), RANDOM("随机排序"), CUSTOM("自定义排序") }
const val PLAYLIST_COVER_COUNT = 19
const val PLAYLIST_DESCRIPTION_LIMIT = 300
const val LIBRARY_QUEUE_SOURCE = "library"
const val FAVORITES_FLOWER_COVER = 18
fun defaultPlaylistCover(id: String): Int = if(id == FAVORITES_PLAYLIST_ID) FAVORITES_FLOWER_COVER else 8+(id.hashCode() and Int.MAX_VALUE)%10

// Upgrade only the old default. A chosen photo or another preset remains untouched.
fun upgradeFavoriteFlower(playlist: Playlist): Playlist = if(playlist.isFavorites && playlist.coverPhoto.isEmpty() &&
    playlist.coverPreset == (FAVORITES_PLAYLIST_ID.hashCode() and Int.MAX_VALUE)%8) playlist.copy(coverPreset=FAVORITES_FLOWER_COVER) else playlist
data class Playlist(val id: String, val name: String, val songIds: List<String> = emptyList(), val sort: SortMode = SortMode.CUSTOM,
    val randomIds: List<String> = emptyList(), val coverPreset: Int = defaultPlaylistCover(id), val coverPhoto: String = "", val description: String = "")
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
data class DeletedPlaylist(val playlist: Playlist, val index: Int)
data class RemovedPlaylistSong(val playlist: Playlist, val songId: String)
data class RemovedPlaylistSongs(val playlist: Playlist, val songIds: List<String>)

fun mergeSongOrder(requested: List<String>, current: List<String>): List<String> {
    val members=current.toSet()
    return (requested.filter(members::contains)+current).distinct()
}

fun orderedQueueIds(queue: List<String>, queueSource: String?, ordered: List<String>, source: String?): List<String> {
    if(queueSource != source) return queue
    val rank=ordered.distinct().withIndex().associate {it.value to it.index}
    return queue.sortedBy {rank[it] ?: Int.MAX_VALUE}
}

fun queueMovePlan(current: List<String>, ordered: List<String>): List<Pair<Int,Int>> {
    val working=current.toMutableList()
    return buildList {
        ordered.forEachIndexed {to,id ->
            val from=(to until working.size).firstOrNull {working[it] == id}
            if(from != null && from != to) {
                working.add(to,working.removeAt(from))
                add(from to to)
            }
        }
    }
}

fun addPlaylistMembers(playlist: Playlist, ids: List<String>, knownSongIds: Set<String>): Playlist {
    val songs=(playlist.songIds+ids).filter(knownSongIds::contains).distinct()
    return playlist.copy(songIds=songs,randomIds=mergeSongOrder(playlist.randomIds,songs))
}

fun removePlaylistMembers(playlist: Playlist, ids: Set<String>): Playlist =
    playlist.copy(songIds=playlist.songIds.filterNot(ids::contains),randomIds=playlist.randomIds.filterNot(ids::contains))

fun restoreRemovedPlaylistSongs(current: Playlist, removed: RemovedPlaylistSongs, knownSongIds: Set<String>): Playlist =
    removed.songIds.fold(current) {playlist,id -> restoreRemovedPlaylistSong(playlist,RemovedPlaylistSong(removed.playlist,id),knownSongIds)}

fun restoreDeletedPlaylist(playlists: List<Playlist>, deleted: DeletedPlaylist, knownSongIds: Set<String>): List<Playlist> {
    if(deleted.playlist.isFavorites || playlists.any {it.id == deleted.playlist.id}) return playlists
    val songs=deleted.playlist.songIds.filter(knownSongIds::contains).distinct()
    val members=songs.toSet()
    val restored=deleted.playlist.copy(songIds=songs,randomIds=deleted.playlist.randomIds.filter(members::contains).distinct())
    return playlists.toMutableList().apply {add(deleted.index.coerceIn(0,size),restored)}
}

fun restoreRemovedPlaylistSong(current: Playlist, removed: RemovedPlaylistSong, knownSongIds: Set<String>): Playlist {
    val id=removed.songId
    if(current.id != removed.playlist.id || id !in knownSongIds || id in current.songIds || id !in removed.playlist.songIds) return current
    return current.copy(songIds=restoreSongPosition(current.songIds,removed.playlist.songIds,id),
        randomIds=if(id in removed.playlist.randomIds) restoreSongPosition(current.randomIds,removed.playlist.randomIds,id) else current.randomIds)
}

private fun restoreSongPosition(current: List<String>, original: List<String>, id: String): List<String> {
    if(id in current) return current
    val originalIndex=original.indexOf(id)
    if(originalIndex < 0) return current
    val members=current.toSet()
    val next=original.drop(originalIndex+1).firstOrNull(members::contains)
    val previous=original.take(originalIndex).lastOrNull(members::contains)
    // Anchor only the restored item; later reorders and other removals stay intact.
    val index=when {
        next != null -> current.indexOf(next)
        previous != null -> current.indexOf(previous)+1
        else -> originalIndex.coerceAtMost(current.size)
    }
    return current.toMutableList().apply {add(index,id)}
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
    val nightMode: Boolean = false,
)
data class LyricLine(val time: Long, val text: String, val endTime: Long = Long.MAX_VALUE) {
    val startTime: Long get() = time
}

data class LyricFrame(val weights: Map<Int,Float>, val offset: Float, val running: Boolean)

// All positions and durations are in media milliseconds, not wall-clock time.
// The real LRC index remains unchanged until its timestamp; this is its visual lead.
data class LyricLead(
    val targetIndex: Int,
    val startedAtPosition: Long,
    val finishAtPosition: Long,
    val durationMillis: Long,
    val elapsedMillis: Long,
)

// One clock drives both styles and centering. The caller can use media time for
// natural playback (which freezes on pause), or wall time for a seek/return.
// Replacing a transition
// with its sampled frame preserves the visible state during a seek or dense LRC.
class LyricTransition(
    fromWeights: Map<Int,Float>,
    private val fromOffset: Float,
    private val targetIndex: Int,
    private val targetOffset: Float,
    private val startedAtMillis: Long,
    private val styleDurationMillis: Long = 250L,
    private val scrollDurationMillis: Long = 300L,
) {
    private val initialWeights = fromWeights.toMap()
    private val targetWeights = if(targetIndex < 0) emptyMap() else mapOf(targetIndex to 1f)
    private val animatedIndices = (initialWeights.keys+targetWeights.keys).toIntArray()

    fun sample(now: Long): LyricFrame {
        // Subtract as doubles so an extreme seek/clock sample cannot overflow Long.
        val elapsed = (now.toDouble()-startedAtMillis.toDouble()).coerceAtLeast(0.0)
        val styleFraction = easeOut(elapsed,styleDurationMillis)
        val scrollFraction = easeOut(elapsed,scrollDurationMillis)
        val weights = when {
            styleFraction == 0f -> initialWeights
            styleFraction == 1f -> targetWeights
            else -> buildMap {
                for(index in animatedIndices) {
                    val initial = initialWeights[index] ?: 0f
                    val weight = initial+((targetWeights[index] ?: 0f)-initial)*styleFraction
                    if(weight > 0f) put(index,weight)
                }
            }
        }
        val offset = when(scrollFraction) {
            0f -> fromOffset
            1f -> targetOffset
            else -> fromOffset+(targetOffset-fromOffset)*scrollFraction
        }
        val running = (initialWeights != targetWeights && elapsed < styleDurationMillis) ||
            (fromOffset != targetOffset && elapsed < scrollDurationMillis)
        return LyricFrame(weights,offset,running)
    }

    private fun easeOut(elapsed: Double, duration: Long): Float {
        if(duration <= 0) return 1f
        val remaining = 1.0-(elapsed/duration).coerceIn(0.0,1.0)
        return (1.0-remaining*remaining*remaining).toFloat()
    }
}

object Lrc {
    private val timestamp = Regex("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?]")
    private val offset = Regex("\\[offset:([+-]?\\d+)]", RegexOption.IGNORE_CASE)
    fun parse(text: String, duration: Long? = null): List<LyricLine> {
        val shift = offset.find(text)?.groupValues?.get(1)?.toLongOrNull() ?: 0
        val parsed = text.lineSequence().flatMap { line ->
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
        return parsed.mapIndexed { index, line ->
            line.copy(endTime=parsed.getOrNull(index+1)?.time ?: duration?.coerceAtLeast(line.time) ?: Long.MAX_VALUE)
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

    // Begin up to 300 wall milliseconds early and finish 20 wall milliseconds
    // before the timestamp. Short intervals compress the whole animation; its
    // start never overlaps the preceding row's timestamp. The one-frame margin
    // shrinks only when the interval itself is shorter than a few display frames.
    fun lead(lines: List<LyricLine>, position: Long, speed: Float = 1f): LyricLead? {
        val actual = current(lines,position)
        val targetIndex = actual+1
        val target = lines.getOrNull(targetIndex) ?: return null
        val previous = lines.getOrNull(actual)?.startTime?.coerceAtLeast(0) ?: 0L
        if(target.startTime <= previous) return null
        val gap = target.startTime-previous
        // A one-millisecond interval cannot contain a positive animation and a
        // pre-timestamp deadline. The normal index still handles this boundary.
        if(gap < 2) return null
        val rate = speed.takeIf {it.isFinite() && it > 0f}?.toDouble() ?: 1.0
        val margin = kotlin.math.ceil(20.0*rate).toLong().coerceIn(1L,(gap/4).coerceAtLeast(1L))
        val finish = target.startTime-margin
        val duration = kotlin.math.ceil(300.0*rate).toLong().coerceIn(1L,finish-previous)
        val start = finish-duration
        if(position < start) return null
        return LyricLead(targetIndex,start,finish,duration,(position-start).coerceIn(0L,duration))
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

// FLAC/Vorbis and ID3 may store numbers as "2/3"; compare their first number.
fun tagNumber(value: String): Int? = value.trim().substringBefore('/').trim().toIntOrNull()?.takeIf {it > 0}

fun albumSongs(songs: List<Song>): List<Song> {
    val collator=Collator.getInstance(Locale.CHINA).apply {strength=Collator.PRIMARY}
    return songs.sortedWith(compareBy<Song> {tagNumber(it.disc) ?: 1}
        .thenBy {tagNumber(it.track) ?: Int.MAX_VALUE}
        .thenComparator {a,b -> collator.compare(a.title,b.title).takeIf {it != 0} ?: a.id.compareTo(b.id)})
}

const val MAX_LYRIC_OFFSET = 10_000L
const val RECENT_PLAYED_LIMIT = 100

// Positive calibration delays the displayed lyric. Negative positions keep the
// first line unhighlighted until its calibrated timestamp, rather than clamping early.
fun lyricPosition(position: Long, offset: Long): Long {
    val bounded=offset.coerceIn(-MAX_LYRIC_OFFSET,MAX_LYRIC_OFFSET)
    return when {
        bounded > 0 && position < Long.MIN_VALUE+bounded -> Long.MIN_VALUE
        bounded < 0 && position > Long.MAX_VALUE+bounded -> Long.MAX_VALUE
        else -> position-bounded
    }
}

fun mediaPosition(lyricTime: Long, offset: Long, duration: Long): Long {
    val bounded=offset.coerceIn(-MAX_LYRIC_OFFSET,MAX_LYRIC_OFFSET)
    val position=when {
        bounded > 0 && lyricTime > Long.MAX_VALUE-bounded -> Long.MAX_VALUE
        bounded < 0 && lyricTime < Long.MIN_VALUE-bounded -> Long.MIN_VALUE
        else -> lyricTime+bounded
    }
    return position.coerceIn(0,duration.coerceAtLeast(0))
}

fun recentPlayedIds(current: List<String>, id: String, known: Set<String>): List<String> =
    (listOf(id)+current).filter(known::contains).distinct().take(RECENT_PLAYED_LIMIT)

// Unknown provider timestamps are never safe for reusing a sibling LRC cache.
fun lyricRevision(uri: String, size: Long, modified: Long): String =
    if(modified > 0) "$uri|$size|$modified" else ""

fun reuseAudioTags(previous: Song?, name: String, size: Long, modified: Long, force: Boolean): Boolean =
    !force && previous != null && previous.available && previous.name == name && previous.size == size && previous.modified == modified

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
