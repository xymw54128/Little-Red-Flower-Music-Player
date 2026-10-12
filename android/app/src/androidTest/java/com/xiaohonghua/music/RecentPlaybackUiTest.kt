package com.xiaohonghua.music

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecentPlaybackUiTest {
    @get:Rule val compose=createComposeRule()
    private val newest=Song("newest","content://music/newest","newest.flac","新近听过",artist="花",album="春天")
    private val older=Song("older","content://music/older","older.flac","上次听过",artist="雨",album="秋天")

    @Test fun emptyHistoryExplainsLocalLimitAndOffersNoDestructiveAction() {
        compose.setContent {MaterialTheme {
            RecentPlaybackContent(emptyList(),null,onPlay={},onAdd={},onInfo={},favoriteIds=emptySet(),
                onFavorite={},onClear={})
        }}
        compose.onNodeWithText("还没有播放记录").assertIsDisplayed()
        compose.onNodeWithText("播放过的歌曲会出现在这里，最多保留 100 首。").assertIsDisplayed()
        compose.onNodeWithTag("clear-recent-playback").assertDoesNotExist()
        compose.onNodeWithTag("song-list").assertDoesNotExist()
    }

    @Test fun latestRowsKeepSourceOrderCurrentHighlightFavoritesAndIndependentCallbacks() {
        val current=mutableStateOf(listOf(newest,older))
        val favorites=mutableStateOf(setOf(older.id))
        var played: String?=null
        var cleared=0
        compose.setContent {MaterialTheme {
            RecentPlaybackContent(current.value,older.id,onPlay={played=it.id},onAdd={},onInfo={},
                favoriteIds=favorites.value,onFavorite={song -> favorites.value=favorites.value+song.id},
                onClear={cleared++})
        }}
        compose.onNodeWithTag("recent-count").assertTextEquals("共 2 首 · 最近播放优先")
        val recentRow=compose.onNodeWithTag("song-newest").getUnclippedBoundsInRoot()
        val olderRow=compose.onNodeWithTag("song-older").getUnclippedBoundsInRoot()
        org.junit.Assert.assertTrue(recentRow.top < olderRow.top)
        compose.onNodeWithTag("song-older").assertIsSelected()
        compose.onNodeWithTag("favorite-older").assertIsSelected()
        compose.onNodeWithTag("song-newest").performClick()
        compose.runOnIdle {assertEquals("newest",played)}
        compose.onNodeWithTag("favorite-newest").performClick().assertIsSelected()
        compose.onNodeWithTag("clear-recent-playback").performClick()
        compose.runOnIdle {assertEquals(1,cleared);assertEquals(2,current.value.size)}
        // The parent owns confirmation and data mutation, so clicking never silently clears.
        compose.runOnIdle {current.value=emptyList()}
        compose.onNodeWithText("还没有播放记录").assertIsDisplayed()
        compose.onNodeWithTag("clear-recent-playback").assertDoesNotExist()
    }
}
