package com.xiaohonghua.music

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Synthetic media only; no imports, playback, or edits to the user's library. */
@RunWith(AndroidJUnit4::class)
class ReorderUiTest {
    @get:Rule val compose=createComposeRule()
    private val tracks=List(80) {Song("$it","content://example/$it","$it.flac","歌曲 $it",artist="歌手 $it")}

    @Test fun compactRowsHaveAFullHandleAndAccessibleMoves() {
        var draft by mutableStateOf(tracks.take(3))
        compose.setContent {MaterialTheme {ReorderList(draft,onReorder={ids -> draft=ids.map {id -> tracks.first {it.id == id}}},modifier=Modifier.fillMaxSize())}}
        compose.onNodeWithTag("reorder-handle-1").assertWidthIsEqualTo(48.dp).assertHeightIsEqualTo(48.dp)
        compose.onNodeWithTag("reorder-1").assertHeightIsEqualTo(68.dp)
        compose.onNodeWithTag("move-up-1").assertDoesNotExist()
        val actions=compose.onNodeWithTag("reorder-1").fetchSemanticsNode().config[SemanticsActions.CustomActions]
        compose.runOnIdle {assertTrue(actions.first {it.label == "上移一位"}.action())}
        compose.runOnIdle {assertEquals(listOf("1","0","2"),draft.map {it.id})}
        compose.onNodeWithTag("reorder-handle-1").performClick()
        compose.onNodeWithTag("move-down-1").performClick()
        compose.runOnIdle {assertEquals(listOf("0","1","2"),draft.map {it.id})}
    }

    @Test fun draftSurvivesMetadataRefreshAndSavesExactlyTheCurrentMembership() {
        var songs by mutableStateOf(tracks.take(3))
        var saved: List<String>?=null
        compose.setContent {MaterialTheme {OrderSheet(songs,onCancel={},onSave={saved=it})}}
        repeat(2) {
            compose.onNodeWithTag("reorder-handle-2").performClick()
            compose.onNodeWithTag("move-up-2").performClick()
        }
        compose.runOnIdle {songs=listOf(tracks[0].copy(title="新标题"),tracks[2].copy(artist="新歌手"),tracks[3],tracks[3])}
        compose.onNodeWithText("新标题").assertIsDisplayed()
        compose.onNodeWithText("新歌手").assertIsDisplayed()
        compose.onNodeWithTag("reorder-save").assertIsDisplayed().performClick()
        compose.runOnIdle {assertEquals(listOf("2","0","3"),saved)}
    }

    @Test fun oneContinuousDragSurvivesRowMovesAndScrollsBeyondTheInitialViewport() {
        var draft by mutableStateOf(tracks)
        var dragging=false
        compose.setContent {MaterialTheme {ReorderList(draft,onReorder={ids -> draft=ids.map {id -> tracks.first {it.id == id}}},
            onDraggingChanged={dragging=it},modifier=Modifier.fillMaxWidth().height(360.dp))}}
        val list=compose.onNodeWithTag("reorder-list").getUnclippedBoundsInRoot()
        val handle=compose.onNodeWithTag("reorder-handle-0").getUnclippedBoundsInRoot()
        val start=Offset((handle.left.value+handle.right.value)/2-list.left.value,(handle.top.value+handle.bottom.value)/2-list.top.value)
        val density=compose.density.density
        compose.mainClock.autoAdvance=false
        compose.onNodeWithTag("reorder-list").performTouchInput {down(start*density);advanceEventTime(650);moveBy(Offset(0f,100f*density))}
        compose.mainClock.advanceTimeBy(100)
        compose.runOnIdle {assertTrue(dragging);assertTrue(draft.indexOfFirst {it.id == "0"} > 0)}
        compose.onNodeWithTag("reorder-list").performTouchInput {moveTo(Offset(start.x*density,(list.bottom.value-list.top.value-16f)*density))}
        compose.mainClock.advanceTimeBy(1800)
        compose.onNodeWithTag("reorder-list").performTouchInput {up()}
        compose.mainClock.advanceTimeBy(200)
        compose.mainClock.autoAdvance=true
        compose.runOnIdle {
            assertFalse(dragging)
            assertTrue("The dragged song must pass rows initially outside the viewport",draft.indexOfFirst {it.id == "0"} > 8)
            assertEquals(tracks.size,draft.map {it.id}.toSet().size)
        }
    }

    @Test fun liveMembershipAdditionDoesNotStallAnActiveDrag() {
        var draft by mutableStateOf(tracks.take(3))
        var added=false
        var dragging=false
        compose.setContent {MaterialTheme {ReorderList(draft,onReorder={ids ->
            draft=ids.map {id -> tracks.first {it.id == id}}
            // A folder refresh can publish a new member before the pending move is laid out.
            if(!added) {added=true;draft=draft+tracks[3]}
        },onDraggingChanged={dragging=it},modifier=Modifier.fillMaxWidth().height(360.dp))}}
        val list=compose.onNodeWithTag("reorder-list").getUnclippedBoundsInRoot()
        val handle=compose.onNodeWithTag("reorder-handle-0").getUnclippedBoundsInRoot()
        val density=compose.density.density
        val start=Offset((handle.left.value+handle.right.value)/2-list.left.value,(handle.top.value+handle.bottom.value)/2-list.top.value)*density
        compose.mainClock.autoAdvance=false
        compose.onNodeWithTag("reorder-list").performTouchInput {down(start);advanceEventTime(650);moveBy(Offset(0f,100f*density))}
        compose.mainClock.advanceTimeBy(100)
        compose.runOnIdle {
            assertTrue(dragging);assertTrue(added)
            assertEquals(listOf("1","0","2","3"),draft.map {it.id})
        }
        compose.onNodeWithTag("reorder-list").performTouchInput {moveBy(Offset(0f,140f*density))}
        compose.mainClock.advanceTimeBy(100)
        compose.onNodeWithTag("reorder-list").performTouchInput {up()}
        compose.mainClock.advanceTimeBy(200)
        compose.mainClock.autoAdvance=true
        compose.runOnIdle {
            assertFalse(dragging)
            assertEquals(listOf("1","2","3","0"),draft.map {it.id})
        }
    }

    @Test fun cancellationReleasesTheGestureAndTheNextDragStartsFresh() {
        var draft by mutableStateOf(tracks.take(4))
        var dragging=false
        compose.setContent {MaterialTheme {ReorderList(draft,onReorder={ids -> draft=ids.map {id -> tracks.first {it.id == id}}},
            onDraggingChanged={dragging=it},modifier=Modifier.fillMaxWidth().height(360.dp))}}
        val list=compose.onNodeWithTag("reorder-list").getUnclippedBoundsInRoot()
        val handle=compose.onNodeWithTag("reorder-handle-0").getUnclippedBoundsInRoot()
        val density=compose.density.density
        val start=Offset((handle.left.value+handle.right.value)/2-list.left.value,(handle.top.value+handle.bottom.value)/2-list.top.value)*density
        compose.onNodeWithTag("reorder-list").performTouchInput {down(start);advanceEventTime(650);moveBy(Offset(0f,-80f*density));cancel()}
        compose.runOnIdle {assertFalse(dragging);assertEquals(listOf("0","1","2","3"),draft.map {it.id})}
        compose.onNodeWithTag("reorder-list").performTouchInput {down(start);advanceEventTime(650);moveBy(Offset(0f,170f*density));up()}
        compose.runOnIdle {assertFalse(dragging);assertEquals(listOf("1","2","0","3"),draft.map {it.id})}
    }

    @Test fun saveAndCancelRemainVisibleAfterScrollingTheLongList() {
        var saved=false
        var cancelled=false
        compose.setContent {MaterialTheme {OrderSheet(tracks,onCancel={cancelled=true},onSave={saved=true})}}
        compose.onNodeWithTag("reorder-list").performScrollToIndex(79)
        compose.onNodeWithTag("reorder-save").assertIsDisplayed()
        compose.onNodeWithText("取消").assertIsDisplayed().performClick()
        compose.runOnIdle {assertTrue(cancelled);assertFalse(saved)}
    }
}
