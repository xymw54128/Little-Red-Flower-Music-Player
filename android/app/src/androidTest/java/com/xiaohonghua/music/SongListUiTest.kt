package com.xiaohonghua.music

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Synthetic rows only: does not import, replace or play the user's files. */
@RunWith(AndroidJUnit4::class)
class SongListUiTest {
    @get:Rule val compose=createComposeRule()
    private val songs=List(80) {Song("$it","content://example/$it","$it.flac","歌曲 $it",format="FLAC")}
    private fun list(currentId: String?,onPlay:(Song)->Unit={}) {
        compose.setContent {MaterialTheme {
            SongList(songs,songs.size,currentId,Modifier.fillMaxSize(),onPlay,onAdd={},onInfo={})
        }}
    }
    @Test fun draggingOffersLocationAndClickCentersThePlayingSongWithoutChangingPlayback() {
        var plays=0
        list("40") {plays++}
        compose.onNodeWithTag("locate-current-song").assertDoesNotExist()
        compose.onNodeWithTag("song-list").performTouchInput {swipeUp(durationMillis=350)}
        compose.onNodeWithContentDescription("定位当前播放歌曲").performClick()
        compose.onNodeWithTag("song-40").assertIsDisplayed().assertIsSelected()
        assertEquals(0,plays)
        val list=compose.onNodeWithTag("song-list").getUnclippedBoundsInRoot()
        val row=compose.onNodeWithTag("song-40").getUnclippedBoundsInRoot()
        assertEquals((list.top.value+list.bottom.value)/2,(row.top.value+row.bottom.value)/2,8f)
    }
    @Test fun locatorFadesOutAfterTwoSecondsWithoutScrolling() {
        list("40")
        compose.onNodeWithTag("song-list").performTouchInput {swipeUp(durationMillis=350)}
        compose.onNodeWithContentDescription("定位当前播放歌曲").assertIsDisplayed()
        compose.waitUntil(5000) {compose.onAllNodesWithTag("locate-current-song").fetchSemanticsNodes().isEmpty()}
    }
    @Test fun aDifferentPlaylistDoesNotOfferAnUnreachableSong() {
        list("outside-this-list")
        compose.onNodeWithTag("song-list").performTouchInput {swipeUp(durationMillis=350)}
        compose.onNodeWithTag("locate-current-song").assertDoesNotExist()
    }
}
