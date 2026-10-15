package com.xiaohonghua.music

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test

class ThousandSongSearchTest {
    private val songs=(0 until 1000).map {index ->
        val number=index.toString().padStart(4,'0')
        Song("song-$number","content://music/$number","小红花 $number.flac","小红花 $number",
            artist="花开歌手 "+index%20,album="春日专辑 "+index%40)
    }
    private fun transliterate(text: String) = text.replace("小红花","xiao hong hua")
        .replace("花开歌手","hua kai ge shou").replace("春日专辑","chun ri zhuan ji")

    @Test fun thousandSongIndexCachesSharedFieldsAndFiltersWithoutMoreTransliteration() = runBlocking {
        val calls=mutableListOf<String>()
        val index=buildSongSearchIndex(songs) {calls+=it;transliterate(it)}
        val fields=songs.flatMap {listOf(it.title,it.artist,it.album,it.albumArtist,it.name)}.toSet()
        assertEquals(fields.size,calls.size)
        assertEquals(fields,calls.toSet())
        assertEquals(1000,index.entries.size)
        assertTrue(index.readyFor(songs))
        val reversed=songs.reversed()
        assertEquals(reversed.map {it.id},index.filter(reversed,"xhh").map {it.id})
        assertEquals(listOf("song-0999"),index.filter(songs,"xhh0999").map {it.id})
        assertEquals(50,index.filter(songs,"hkgs1").count {it.artist == "花开歌手 1"})
        assertEquals(fields.size,calls.size)
    }

    @Test fun thousandSongMetadataRefreshRebuildsOnlyChangedFieldsAndPrunesRemovedFiles() = runBlocking {
        val index=buildSongSearchIndex(songs,transliterate=::transliterate)
        val changed=songs.toMutableList().apply {this[500]=this[500].copy(artist="夜雨歌手")}.drop(1)
        val calls=mutableListOf<String>()
        val refreshed=buildSongSearchIndex(changed,index) {calls+=it;transliterate(it)}
        assertEquals(listOf("夜雨歌手"),calls)
        assertEquals(999,refreshed.entries.size)
        assertFalse(refreshed.entries.containsKey("song-0000"))
        assertFalse(refreshed.fieldTerms.containsKey("小红花 0000"))
        assertEquals(listOf("song-0500"),refreshed.filter(changed,"夜雨歌手").map {it.id})
        assertFalse(index.matches(changed[499],"hkgs0"))
        assertTrue(refreshed.readyFor(changed))
        val unchanged=buildSongSearchIndex(changed,refreshed) {error("Unchanged fields must use the cache")}
        assertSame(refreshed,unchanged)
    }

    @Test fun cancellingAThousandSongRefreshCannotPublishAPartialIndex() = runBlocking {
        val index=buildSongSearchIndex(songs,transliterate=::transliterate)
        val changed=songs.map {it.copy(title="新标题 "+it.title)}
        val job=Job()
        var calls=0
        var cancelled=false
        try {
            withContext(job) {
                buildSongSearchIndex(changed,index) {text ->
                    if(++calls == 100) job.cancel()
                    transliterate(text)
                }
            }
        } catch(_: CancellationException) {cancelled=true}
        assertTrue(cancelled)
        assertEquals(1000,index.entries.size)
        assertTrue(index.readyFor(songs))
        assertFalse(index.readyFor(changed))
    }
}
