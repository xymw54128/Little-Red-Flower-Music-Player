package com.xiaohonghua.music

import org.junit.Assert.*
import org.junit.Test

class LibraryCalibrationTest {
    @Test fun positiveOffsetDelaysHighlightAndNegativeOffsetAdvancesIt() {
        val lines=Lrc.parse("[00:01]一\n[00:02]二")
        assertEquals(-1,Lrc.current(lines,lyricPosition(1400,500)))
        assertEquals(0,Lrc.current(lines,lyricPosition(1500,500)))
        assertEquals(0,Lrc.current(lines,lyricPosition(500,-500)))
        assertEquals(-500L,lyricPosition(0,500))
        assertEquals(1500L,mediaPosition(1000,500,3000))
        assertEquals(500L,mediaPosition(1000,-500,3000))
        assertEquals(0L,mediaPosition(1000,-10_000,3000))
        assertEquals(3000L,mediaPosition(1000,10_000,3000))
    }
    @Test fun calibrationBoundsAndExtremePositionsCannotOverflow() {
        assertEquals(Long.MAX_VALUE,lyricPosition(Long.MAX_VALUE,-500))
        assertEquals(Long.MIN_VALUE,lyricPosition(Long.MIN_VALUE,500))
        assertEquals(Long.MAX_VALUE,mediaPosition(Long.MAX_VALUE,500,Long.MAX_VALUE))
        assertEquals(0L,mediaPosition(Long.MIN_VALUE,-500,Long.MAX_VALUE))
        assertEquals(10_000L,mediaPosition(0,Long.MAX_VALUE,20_000))
    }
    @Test fun recentHistoryIsUniqueBoundedAndPrunedToKnownSongs() {
        val known=(1..120).map {"$it"}.toSet()
        val history=(1..120).map {"$it"}
        val updated=recentPlayedIds(history,"20",known)
        assertEquals("20",updated.first());assertEquals(100,updated.size)
        assertEquals(updated.distinct(),updated)
        assertFalse("deleted" in recentPlayedIds(updated,"deleted",known))
        assertEquals(listOf("2","1"),recentPlayedIds(listOf("1","2","1","gone"),"2",setOf("1","2")))
    }
    @Test fun audioAndLyricCachesInvalidateIndependently() {
        val song=Song("1","content://test/1","song.flac","Song",size=1000,modified=2000)
        assertTrue(reuseAudioTags(song,"song.flac",1000,2000,false))
        assertFalse(reuseAudioTags(song,"renamed.flac",1000,2000,false))
        assertFalse(reuseAudioTags(song,"song.flac",1001,2000,false))
        assertFalse(reuseAudioTags(song,"song.flac",1000,2001,false))
        assertFalse(reuseAudioTags(song,"song.flac",1000,2000,true))
        assertFalse(reuseAudioTags(song.copy(available=false),"song.flac",1000,2000,false))
        assertEquals("",lyricRevision("content://test/lrc",10,0))
        assertNotEquals(lyricRevision("content://test/lrc",10,1),lyricRevision("content://test/lrc",10,2))
        assertNotEquals(lyricRevision("content://test/lrc",10,1),lyricRevision("content://test/new-lrc",10,1))
    }
}
