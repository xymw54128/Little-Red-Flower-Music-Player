package com.xiaohonghua.music

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test

class SongSearchTest {
    private val song=Song("flower","content://music/flower","房东的猫-云烟成雨.flac","云烟成雨",
        artist="房东的猫",album="春风 EP",albumArtist="邓紫棋")
    private val pinyin=mapOf("云烟成雨" to "yún yān chéng yǔ","房东的猫" to "fáng dōng de māo",
        "春风 EP" to "chūn fēng EP","邓紫棋" to "dèng zǐ qí",
        "房东的猫-云烟成雨.flac" to "fáng dōng de māo-yún yān chéng yǔ.flac")

    @Test fun normalizationHandlesCaseAccentsWidthSpacingAndPunctuation() {
        assertEquals("dengziqi",normalizeSongSearch("  DÈNG-Zǐ QÍ "))
        assertEquals("gem11",normalizeSongSearch("Ｇ.Ｅ.Ｍ. １１"))
        assertEquals("beyonce",normalizeSongSearch("Beyoncé"))
        assertEquals("dzq",songSearchInitials("dèng zǐ qí"))
        assertEquals("fddm",songSearchInitials("fáng dōng de māo"))
        assertEquals("gemdzq",songSearchInitials("G.E.M. dèng zǐ qí"))
    }

    @Test fun emptyIndexKeepsImmediateOriginalTextMatching() {
        val index=SongSearchIndex()
        assertFalse(index.readyFor(listOf(song)))
        assertTrue(index.matches(song,"云烟"))
        assertTrue(index.matches(song,"房东"))
        assertTrue(index.matches(song,"春风"))
        assertTrue(index.matches(song,"邓紫棋"))
        assertTrue(index.matches(song,".FLAC"))
        assertTrue(index.matches(song,"  "))
        assertFalse(index.matches(song,"dengziqi"))
        assertFalse(index.matches(song,"?"))
    }

    @Test fun completedIndexMatchesPinyinInitialsAlbumsAndFiles() = runBlocking {
        val index=buildSongSearchIndex(listOf(song)) {pinyin[it] ?: it}
        assertTrue(index.readyFor(listOf(song)))
        listOf("YUN YAN CHENG YU","yycy","fangdongdemao","fddm","chunfeng",
            "cfep","dengziqi","dzq","cheng-yu.FLAC").forEach {query ->
            assertTrue("Expected a match for $query",index.matches(song,query))
        }
        assertFalse(index.matches(song,"dgzq"))
        assertFalse(index.matches(song,"not-a-song"))
        assertFalse(index.matches(song,"!!!"))
    }

    @Test fun mixedEnglishWordsCanStayWholeBesideChineseInitials() = runBlocking {
        val mixed=song.copy(artist="New Boy邓紫棋")
        val index=buildSongSearchIndex(listOf(mixed)) {
            if(it == "New Boy邓紫棋") "New Boy dèng zǐ qí" else pinyin[it] ?: it
        }
        assertTrue(index.matches(mixed,"newboydzq"))
        assertTrue(index.matches(mixed,"nbdzq"))
        assertTrue(index.matches(mixed,"cfep"))
    }

    @Test fun cacheReusesSharedFieldsAndOnlyRebuildsChangedMetadata() = runBlocking {
        val translated=mutableListOf<String>()
        val second=song.copy(id="second",title="第二首",name="second.flac")
        val index=buildSongSearchIndex(listOf(song,second)) {translated+=it;pinyin[it] ?: it}
        assertEquals(1,translated.count {it == "房东的猫"})
        assertEquals(1,translated.count {it == "邓紫棋"})
        translated.clear()
        val artworkChanged=song.copy(artwork="new-cover",lyrics="new-lyrics",duration=300_000)
        val same=buildSongSearchIndex(listOf(artworkChanged,second),index) {translated+=it;it}
        assertSame(index,same)
        assertTrue(translated.isEmpty())
        val changed=second.copy(artist="新歌手")
        val refreshed=buildSongSearchIndex(listOf(artworkChanged,changed),same) {translated+=it;it}
        assertEquals(listOf("新歌手"),translated)
        assertEquals(setOf("flower","second"),refreshed.entries.keys)
    }

    @Test fun oldMetadataCannotMatchDuringIndexRefreshAndRemovedFieldsArePruned() = runBlocking {
        val index=buildSongSearchIndex(listOf(song)) {pinyin[it] ?: it}
        val changed=song.copy(title="夜空",artist="花开",album="冬日",albumArtist="新歌手",name="夜空.flac")
        assertFalse(index.readyFor(listOf(changed)))
        assertFalse(index.matches(changed,"fddm"))
        assertFalse(index.matches(changed,"dzq"))
        assertTrue(index.matches(changed,"夜空"))
        val refreshed=buildSongSearchIndex(listOf(changed),index) {it}
        assertTrue(refreshed.readyFor(listOf(changed)))
        assertFalse(refreshed.fieldTerms.containsKey("房东的猫"))
        val empty=buildSongSearchIndex(emptyList(),refreshed) {it}
        assertTrue(empty.entries.isEmpty())
        assertTrue(empty.fieldTerms.isEmpty())
    }

    @Test fun filteringPreservesSourceOrderAndUpdatesCannotStackAfterCancellation() = runBlocking {
        val first=song.copy(id="first")
        val second=song.copy(id="second")
        val index=buildSongSearchIndex(listOf(first,second)) {pinyin[it] ?: it}
        assertEquals(listOf("second","first"),index.filter(listOf(second,first),"fddm").map {it.id})
        var caught=false
        try {
            withContext(Job().apply {cancel()}) {buildSongSearchIndex(listOf(first),index) {it}}
        } catch(_: CancellationException) {caught=true}
        assertTrue(caught)
        assertEquals(setOf("first","second"),index.entries.keys)
    }
}
