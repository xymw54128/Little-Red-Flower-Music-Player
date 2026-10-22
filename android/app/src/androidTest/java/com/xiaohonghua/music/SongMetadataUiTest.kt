package com.xiaohonghua.music

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SongMetadataUiTest {
    @get:Rule val compose=createComposeRule()
    private val song=Song("info","content://test/music/song.flac","原始文件.flac","清晰的歌曲标题",
        artist="艺术家甲",album="春日专辑",albumArtist="专辑演唱者",disc="2/3",track="10/12",composer="作曲家乙")

    @Test fun informationUsesRequestedLabelsAndKeepsCoverPreview() {
        compose.setContent {MaterialTheme(colorScheme=appColorScheme(0,false)) {SongInfo(song) {}}}
        compose.onNodeWithText("标题").assertIsDisplayed()
        compose.onNodeWithText(song.title).assertIsDisplayed()
        compose.onNodeWithText("艺术家").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(song.artist).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("作曲家").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(song.composer).performScrollTo().assertIsDisplayed()
        listOf("歌手","专辑歌手","碟号","曲目").forEach {compose.onNodeWithText(it).assertDoesNotExist()}
        compose.onNodeWithTag("song-info-cover").performScrollTo().performClick()
        compose.onNodeWithTag("artwork-viewer").assertIsDisplayed()
    }

    @Test fun absentComposerAndLongValuesRemainReadableAtLargeFontScale() {
        val longName="用于检查较大字号下文件名完整换行的音乐文件.flac"
        val longUri="content://test/music/"+"很长的文件路径/".repeat(12)+"music.flac"
        compose.setContent {
            val density=LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density,1.6f)) {
                MaterialTheme(colorScheme=appColorScheme(3,true)) {SongInfo(song.copy(name=longName,uri=longUri,composer="")) {}}
            }
        }
        compose.onNodeWithText("未设置").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(longName).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(longUri).performScrollTo().assertIsDisplayed()
    }
}
