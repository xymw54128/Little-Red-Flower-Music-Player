package com.xiaohonghua.music

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Native nested scroll and synthetic songs only; never changes the device music library. */
@RunWith(AndroidJUnit4::class)
class PlaylistDetailUiTest {
    @get:Rule val compose=createComposeRule()
    private val songs=(1..80).map {index ->
        Song("detail-$index","content://example/$index","$index.flac","歌曲 $index",artist="花",album="春日")
    }
    private val playlist=Playlist("detail","春日歌单",songs.map {it.id},description="春风拂过的旋律")

    private fun summaryHeight(): Float {
        val bounds=compose.onNodeWithTag("playlist-summary").getUnclippedBoundsInRoot()
        return bounds.bottom.value-bounds.top.value
    }

    @Test fun nativeListScrollFullyCollapsesSummaryAndDownwardGestureRestoresIt() {
        compose.setContent {MaterialTheme {Box(Modifier.fillMaxWidth().height(500.dp)) {
            PlaylistDetailContent(playlist,songs,songs.size,null,false,onPlay={},onPlayCover={},onSort={},
                onBulkAdd={},onAdd={},onInfo={},onRemove={},favoriteIds=emptySet(),onFavorite={})
        }}}
        val expandedHeight=summaryHeight()
        assertTrue("The metadata header should initially be visible",expandedHeight > 100f)
        compose.onNodeWithTag("playlist-description").assertTextEquals("春风拂过的旋律")
        compose.onNodeWithTag("playlist-song-count").assertTextEquals("共 80 首")
        repeat(3) {compose.onNodeWithTag("song-list").performTouchInput {swipeUp(durationMillis=450)}}
        compose.waitUntil(3_000) {summaryHeight() <= 1f}
        compose.onNodeWithTag("playlist-song-count").assertIsDisplayed()
        compose.onNodeWithTag("playlist-sort").assertIsDisplayed()
        compose.onNodeWithTag("song-detail-1").assertDoesNotExist()
        // Expand from the middle of the list; reaching its first item must not be required.
        compose.onNodeWithTag("song-list").performTouchInput {swipeDown(durationMillis=450)}
        compose.waitUntil(3_000) {summaryHeight() >= expandedHeight-1f}
        compose.onNodeWithTag("playlist-description").assertIsDisplayed()
        compose.onNodeWithTag("song-detail-1").assertDoesNotExist()
    }

    @Test fun headerControlsUseIndependentCallbacksAndReportUpdatedMetadata() {
        val current=mutableStateOf(playlist)
        var coverPlays=0
        var sorts=0
        var rowPlays: String?=null
        compose.setContent {MaterialTheme {Box(Modifier.fillMaxWidth().height(500.dp)) {
            PlaylistDetailContent(current.value,songs,songs.size,null,false,onPlay={rowPlays=it.id},
                onPlayCover={coverPlays++},onSort={sorts++},onBulkAdd={},onAdd={},onInfo={},onRemove={},
                favoriteIds=emptySet(),onFavorite={})
        }}}
        compose.onNodeWithTag("playlist-summary-cover").performClick()
        compose.onNodeWithTag("playlist-sort").performClick()
        compose.onNodeWithTag("song-detail-1").performClick()
        compose.runOnIdle {
            assertEquals(1,coverPlays);assertEquals(1,sorts);assertEquals("detail-1",rowPlays)
            current.value=current.value.copy(name="夜晚歌单",description="星光下的旋律")
        }
        compose.onNodeWithText("夜晚歌单").assertIsDisplayed()
        compose.onNodeWithTag("playlist-description").assertTextEquals("星光下的旋律")
        compose.onNodeWithText("播放全部").assertDoesNotExist()
    }

    @Test fun emptyPlaylistCanAddSongsWhileEmptySearchCannotChangeMembership() {
        val searching=mutableStateOf(false)
        var adds=0
        compose.setContent {MaterialTheme {Box(Modifier.fillMaxWidth().height(500.dp)) {
            PlaylistDetailContent(playlist,emptyList(),0,null,searching.value,onPlay={},onPlayCover={},onSort={},
                onBulkAdd={adds++},onAdd={},onInfo={},onRemove={},favoriteIds=emptySet(),onFavorite={})
        }}}
        compose.onNodeWithText("歌单还没有歌曲").assertIsDisplayed()
        compose.onNodeWithTag("playlist-summary-cover").assertIsNotEnabled()
        compose.onNodeWithTag("playlist-empty-add").performClick()
        compose.runOnIdle {assertEquals(1,adds);searching.value=true}
        compose.onNodeWithText("没有找到匹配的歌曲").assertIsDisplayed()
        compose.onNodeWithTag("playlist-empty-add").assertDoesNotExist()
        compose.onNodeWithTag("playlist-song-count").assertTextEquals("共 0 首 · 找到 0 首")
    }
}
