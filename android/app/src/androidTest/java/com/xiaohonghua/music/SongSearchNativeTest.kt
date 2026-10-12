package com.xiaohonghua.music

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Platform ICU needs an Android runtime; JVM tests exercise the independent cache logic. */
@RunWith(AndroidJUnit4::class)
class SongSearchNativeTest {
    @get:Rule val compose=createComposeRule()
    private val songs=listOf(
        Song("gem","content://music/gem","G.E.M.-邓紫棋-晴天.flac","晴天",artist="G.E.M. 邓紫棋",
            album="春风 EP",albumArtist="邓紫棋"),
        Song("cat","content://music/cat","房东的猫-云烟成雨.flac","云烟成雨",artist="房东的猫",album="秋雨")
    )

    @Test @SdkSuppress(minSdkVersion=29)
    fun nativeIcuMatchesFullPinyinInitialsMixedEnglishAlbumsAndFilename() = runBlocking {
        val index=withContext(Dispatchers.Default) {platformSongSearchIndex(songs)}
        listOf("dengziqi","DENG ZI QI","dzq","gemdzq","qing-tian","qt.flac","chunfeng","cfep").forEach {query ->
            assertTrue("Expected an ICU match for $query",index.matches(songs[0],query))
        }
        listOf("fangdongdemao","fddm","yycy","qiuyu").forEach {query ->
            assertTrue("Expected an ICU match for $query",index.matches(songs[1],query))
        }
        assertEquals(listOf("cat"),index.filter(songs,"fddm").map {it.id})
        assertEquals(listOf("gem"),index.filter(songs,"dzq").map {it.id})
    }

    @Test @SdkSuppress(minSdkVersion=29)
    fun metadataUpdatesRefreshPinyinWithoutResettingSelectionOrPublishingStaleMatches() {
        val currentSongs=mutableStateOf(songs)
        compose.setContent {MaterialTheme {
            val index=rememberSongSearchIndex(currentSongs.value)
            val selected=remember {mutableStateOf(false)}
            Column {
                Checkbox(selected.value,{selected.value=it},Modifier.testTag("selection"))
                Text(index.filter(currentSongs.value,"dzq").size.toString(),Modifier.testTag("old-result"))
                Text(index.filter(currentSongs.value,"fddm").size.toString(),Modifier.testTag("new-result"))
                Text(index.filter(currentSongs.value,"文件更新").size.toString(),Modifier.testTag("raw-result"))
            }
        }}
        compose.waitUntil(5_000) {compose.onNodeWithTag("old-result").fetchSemanticsNode().config
            .get(androidx.compose.ui.semantics.SemanticsProperties.Text).firstOrNull()?.text == "1"}
        compose.onNodeWithTag("selection").performClick().assertIsOn()
        compose.runOnIdle {
            currentSongs.value=listOf(songs[0].copy(title="文件更新",artist="房东的猫",albumArtist="房东的猫",name="文件更新.flac"))
        }
        compose.onNodeWithTag("raw-result").assertTextEquals("1")
        compose.onNodeWithTag("old-result").assertTextEquals("0")
        compose.waitUntil(5_000) {compose.onNodeWithTag("new-result").fetchSemanticsNode().config
            .get(androidx.compose.ui.semantics.SemanticsProperties.Text).firstOrNull()?.text == "1"}
        compose.onNodeWithTag("selection").assertIsOn()
    }

    @Test @SdkSuppress(maxSdkVersion=28)
    fun androidEightAndNineKeepRawAndNormalizedSearchWithoutIcu() = runBlocking {
        val index=withContext(Dispatchers.Default) {platformSongSearchIndex(songs)}
        assertTrue(index.readyFor(songs))
        assertTrue(index.matches(songs[0],"邓紫棋"))
        assertTrue(index.matches(songs[1],"房东的猫"))
        assertTrue(index.matches(songs[0],"GEM"))
        assertTrue(index.matches(songs[0],"FLAC"))
        assertTrue(index.matches(songs[0],"春风 EP"))
        assertFalse(index.matches(songs[0],"dengziqi"))
        assertFalse(index.matches(songs[1],"fddm"))
    }
}
