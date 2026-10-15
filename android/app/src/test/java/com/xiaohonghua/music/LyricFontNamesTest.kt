package com.xiaohonghua.music

import org.junit.Assert.assertEquals
import org.junit.Test

class LyricFontNamesTest {
    @Test fun oldFontSelectionsHaveMultilingualCounterparts() {
        assertEquals("song", normalizeLyricFontName("serif"))
        assertEquals("kai", normalizeLyricFontName("fangsong"))
        assertEquals("kiwi", normalizeLyricFontName("mono"))
        assertEquals("kiwi", normalizeLyricFontName("cute"))
        assertEquals("kurenaido", normalizeLyricFontName("handdrawn"))
    }

    @Test fun currentSelectionsRemainAndUnknownSelectionFallsBack() {
        for (name in listOf("system", "kai", "song", "xiaowei", "zenmaru", "kiwi", "kurenaido", "longcang")) {
            assertEquals(name, normalizeLyricFontName(name))
        }
        assertEquals("system", normalizeLyricFontName("missing-font"))
    }

    @Test fun allFiveChosenStylesHaveIndependentOptionsAndOriginalNames() {
        assertEquals(listOf("system", "kai", "song", "xiaowei", "zenmaru", "kiwi", "kurenaido", "longcang"),
            lyricFontOptions.map { it.first })
        assertEquals(lyricFontOptions.map { it.first }.toSet(), lyricFontDescriptions.keys)
        assertEquals(7, lyricFontLicenses.size)
    }
}
