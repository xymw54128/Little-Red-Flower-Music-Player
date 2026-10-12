package com.xiaohonghua.music

import android.icu.text.Transliterator
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.text.Normalizer
import java.util.Locale

internal fun normalizeSongSearch(text: String): String =
    Normalizer.normalize(text,Normalizer.Form.NFKD).lowercase(Locale.ROOT).filter {it.isLetterOrDigit()}

private val searchWords=Regex("[\\p{L}\\p{N}]+")
private val searchMarks=Regex("\\p{M}+")
private val latinWords=Regex("[a-z0-9]+")

private fun unaccentedSearchText(text: String): String =
    Normalizer.normalize(text,Normalizer.Form.NFKD).replace(searchMarks,"").lowercase(Locale.ROOT)

internal fun songSearchInitials(text: String,originalLatinWords: Set<String> = emptySet()): String =
    searchWords.findAll(unaccentedSearchText(text)).joinToString("") {
        if(it.value in originalLatinWords) it.value else it.value.take(1)
    }

private fun Song.searchFields(): List<String> = listOf(title,artist,album,albumArtist,name)

internal data class SongSearchEntry(val fields: List<String>,val terms: List<String>)

// Immutable snapshots prevent a cancelled refresh from publishing a partial index.
internal class SongSearchIndex(
    internal val entries: Map<String,SongSearchEntry> = emptyMap(),
    internal val fieldTerms: Map<String,List<String>> = emptyMap()
) {
    fun readyFor(songs: List<Song>): Boolean = songs.all {entries[it.id]?.fields == it.searchFields()}

    fun matches(song: Song,query: String): Boolean = matches(song,query.trim(),normalizeSongSearch(query))

    fun filter(songs: List<Song>,query: String): List<Song> {
        val raw=query.trim()
        if(raw.isEmpty()) return songs
        val normalized=normalizeSongSearch(raw)
        return songs.filter {matches(it,raw,normalized)}
    }

    private fun matches(song: Song,raw: String,normalized: String): Boolean {
        if(raw.isEmpty()) return true
        val fields=song.searchFields()
        // Original title/artist/album/filename search stays available during warm-up.
        if(fields.any {it.contains(raw,ignoreCase=true)}) return true
        if(normalized.isEmpty()) return false
        val entry=entries[song.id]?.takeIf {it.fields == fields} ?: return false
        return entry.terms.any {it.contains(normalized)}
    }
}

internal suspend fun buildSongSearchIndex(
    songs: List<Song>,previous: SongSearchIndex=SongSearchIndex(),transliterate: (String)->String
): SongSearchIndex {
    val entries=LinkedHashMap<String,SongSearchEntry>(songs.size)
    val fields=HashMap<String,List<String>>()
    for(song in songs) {
        currentCoroutineContext().ensureActive()
        val metadata=song.searchFields()
        val terms=metadata.flatMap {text ->
            fields.getOrPut(text) {
                previous.fieldTerms[text] ?: run {
                    val latin=transliterate(text)
                    val originals=latinWords.findAll(unaccentedSearchText(text)).map {it.value}.toSet()
                    listOf(normalizeSongSearch(text),normalizeSongSearch(latin),songSearchInitials(latin),
                        songSearchInitials(latin,originals))
                        .filter(String::isNotEmpty).distinct()
                }
            }
        }.distinct()
        entries[song.id]=previous.entries[song.id]?.takeIf {it.fields == metadata}
            ?: SongSearchEntry(metadata,terms)
    }
    currentCoroutineContext().ensureActive()
    return if(entries == previous.entries && fields == previous.fieldTerms) previous else SongSearchIndex(entries,fields)
}

internal suspend fun platformSongSearchIndex(songs: List<Song>,previous: SongSearchIndex=SongSearchIndex()): SongSearchIndex {
    // Public Android ICU transliteration starts at API 29. Android 8/9 retains
    // original Chinese/English/filename search and normalized Latin matching.
    return if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        // One ICU instance per worker; its mutable internals never cross coroutine jobs.
        val pinyin by lazy {Transliterator.getInstance("Han-Latin; Latin-ASCII")}
        buildSongSearchIndex(songs,previous) {text ->
            if(text.any {it.code >= 0x3400}) pinyin.transliterate(text) else text
        }
    } else buildSongSearchIndex(songs,previous) {it}
}

@Composable internal fun rememberSongSearchIndex(songs: List<Song>): SongSearchIndex {
    val index by produceState(initialValue=SongSearchIndex(),songs) {
        val previous=value
        value=withContext(Dispatchers.Default) {platformSongSearchIndex(songs,previous)}
    }
    return index
}
