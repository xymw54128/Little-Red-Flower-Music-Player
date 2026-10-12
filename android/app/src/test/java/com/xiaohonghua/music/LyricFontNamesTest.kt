package com.xiaohonghua.music

import org.junit.Assert.assertEquals
import org.junit.Test

class LyricFontNamesTest {
    @Test fun oldFontSelectionsHaveMultilingualCounterparts() {
        assertEquals("song", normalizeLyricFontName("serif"))
        assertEquals("kai", normalizeLyricFontName("fangsong"))
        assertEquals("cute", normalizeLyricFontName("mono"))
    }

    @Test fun currentSelectionsRemainAndUnknownSelectionFallsBack() {
        for (name in listOf("system", "kai", "song", "cute", "handdrawn")) {
            assertEquals(name, normalizeLyricFontName(name))
        }
        assertEquals("system", normalizeLyricFontName("missing-font"))
    }
}
