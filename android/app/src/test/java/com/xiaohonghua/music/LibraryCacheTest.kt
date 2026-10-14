package com.xiaohonghua.music

import org.junit.Assert.*
import org.junit.Test

class LibraryCacheTest {
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
