package com.xiaohonghua.music

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Synthetic playlists only; never writes the library or touches music files. */
@RunWith(AndroidJUnit4::class)
class PlaylistManagementUiTest {
    @get:Rule val compose=createComposeRule()
    private val songs=listOf(
        Song("a","content://example/a","a.flac","阳光",artist="花",album="春天"),
        Song("b","content://example/b","b.flac","夜风",artist="许愿",album="早春"),
        Song("c","content://example/c","c.flac","雨",artist="云",album="晚风"),
    )

    @Test fun searchSelectionSkipsExistingMembersAndConfirmsInLibraryOrderOnce() {
        val confirmed=mutableListOf<List<String>>()
        compose.setContent {MaterialTheme {
            PlaylistSongPicker("添加歌曲",songs,initialSelected=setOf("a","missing"),excludedIds=setOf("a"),
                onDismiss={},onConfirm={confirmed+=it})
        }}
        compose.onNodeWithTag("playlist-song-choice-a").assertIsNotEnabled()
        compose.onNodeWithText("已添加").assertIsDisplayed()
        compose.onNodeWithTag("playlist-songs-confirm").assertIsNotEnabled()
        val search=compose.onNodeWithTag("playlist-songs-search")
        search.performTextReplacement("晚风")
        compose.onNodeWithTag("playlist-songs-select-all").performClick()
        search.performTextReplacement("许愿")
        compose.onNodeWithTag("playlist-songs-select-all").performClick()
        compose.onNodeWithTag("playlist-songs-confirm").performSemanticsAction(SemanticsActions.OnClick) {click ->click();click()}
        compose.onNodeWithTag("playlist-songs-confirm").assertIsNotEnabled()
        compose.runOnIdle {assertEquals(listOf(listOf("b","c")),confirmed)}
    }

    @Test fun changedSongsAndMembershipPruneSelectionBeforeConfirmation() {
        val currentSongs=mutableStateOf(songs)
        val excluded=mutableStateOf(emptySet<String>())
        var confirms=0
        compose.setContent {MaterialTheme {
            PlaylistSongPicker("移除歌曲",currentSongs.value,initialSelected=setOf("a","b"),excludedIds=excluded.value,
                onDismiss={},onConfirm={confirms++})
        }}
        compose.onNodeWithTag("playlist-songs-confirm").assertIsEnabled()
        compose.runOnIdle {currentSongs.value=listOf(songs[0]);excluded.value=setOf("a")}
        compose.onNodeWithTag("playlist-songs-confirm").assertIsNotEnabled()
        compose.runOnIdle {assertEquals(0,confirms)}
    }

    @Test fun cancelDoesNotConfirmTheSelectedSongs() {
        var dismisses=0
        var confirms=0
        compose.setContent {MaterialTheme {
            PlaylistSongPicker("移除歌曲",songs,initialSelected=setOf("b"),onDismiss={dismisses++},onConfirm={confirms++})
        }}
        compose.onNodeWithText("取消").performClick()
        compose.runOnIdle {assertEquals(1,dismisses);assertEquals(0,confirms)}
    }

    @Test fun pickerListOwnsBoundaryDragsWithoutMovingTheSearchOrLosingSelection() {
        val longSongs=(1..80).map {index ->
            Song("pick-$index","content://example/$index","$index.flac","歌曲 $index",artist="花",album="春日")
        }
        var dismisses=0
        val confirmed=mutableListOf<List<String>>()
        compose.setContent {MaterialTheme {
            PlaylistSongPicker("添加音乐",longSongs,onDismiss={dismisses++},onConfirm={confirmed+=it})
        }}
        compose.onNodeWithTag("playlist-song-choice-pick-1").performClick()
        val search=compose.onNodeWithTag("playlist-songs-search")
        val list=compose.onNodeWithTag("playlist-songs-list")
        val originalSearch=search.getUnclippedBoundsInRoot()
        val originalList=list.getUnclippedBoundsInRoot()
        // Hold a downward drag at the first item: the old sheet took this gesture
        // and moved the entire picker, then bounced upward again on release.
        list.performTouchInput {
            down(Offset(center.x,16f))
            moveTo(Offset(center.x,height*.65f),delayMillis=350)
        }
        val heldSearch=search.getUnclippedBoundsInRoot()
        assertEquals(originalSearch.top.value,heldSearch.top.value,.5f)
        assertEquals(originalList.top.value,list.getUnclippedBoundsInRoot().top.value,.5f)
        list.performTouchInput {up()}
        // Swipe up repeatedly, including beyond the last item. The viewport and
        // footer must stay put even when the list can no longer consume motion.
        list.performScrollToIndex(79)
        repeat(3) {list.performTouchInput {swipeUp(durationMillis=350)}}
        assertEquals(originalSearch.top.value,search.getUnclippedBoundsInRoot().top.value,.5f)
        compose.onNodeWithText("已选 1 首").assertIsDisplayed()
        compose.onNodeWithTag("playlist-songs-confirm").performClick()
        compose.runOnIdle {assertEquals(1,dismisses);assertEquals(listOf(listOf("pick-1")),confirmed)}
    }

    @Test fun editorKeepsDraftAcrossCoverChangesAndSavesTrimmedValuesOnce() {
        val playlist=mutableStateOf(Playlist("edit","原名称",description="原描述"))
        val saved=mutableListOf<Pair<String,String>>()
        var covers=0
        compose.setContent {MaterialTheme {
            PlaylistEditSheet(playlist.value,onDismiss={},onSave={name,description ->saved+=name to description},onCover={covers++})
        }}
        val name=compose.onNodeWithTag("playlist-edit-name")
        val description=compose.onNodeWithTag("playlist-edit-description")
        name.performTextReplacement("   ")
        compose.onNodeWithTag("playlist-edit-save").assertIsNotEnabled()
        name.performTextReplacement("  新名称  ")
        description.performTextReplacement("  新描述  ")
        compose.onNodeWithTag("playlist-edit-cover").performScrollTo().performClick()
        compose.runOnIdle {playlist.value=playlist.value.copy(coverPreset=9)}
        name.assertTextContains("  新名称  ")
        description.assertTextContains("  新描述  ")
        compose.onNodeWithTag("playlist-edit-save").performSemanticsAction(SemanticsActions.OnClick) {click ->click();click()}
        compose.runOnIdle {assertEquals(1,covers);assertEquals(listOf("新名称" to "新描述"),saved)}
    }

    @Test fun favoriteNameStaysFixedWhileDescriptionRemainsEditable() {
        val saved=mutableListOf<Pair<String,String>>()
        compose.setContent {MaterialTheme {
            PlaylistEditSheet(Playlist(FAVORITES_PLAYLIST_ID,"我喜欢"),onDismiss={},onSave={name,description ->saved+=name to description},onCover={})
        }}
        compose.onNodeWithTag("playlist-edit-name").assertIsNotEnabled()
        compose.onNodeWithTag("playlist-edit-description").performTextReplacement("我的收藏")
        compose.onNodeWithTag("playlist-edit-save").performClick()
        compose.runOnIdle {assertEquals(listOf("我喜欢" to "我的收藏"),saved)}
    }
    @Test fun confirmationCommitsImmediatelyAndKeepsSheetMountedUntilNativeHideFinishes() {
        val shown=mutableStateOf(true)
        var confirms=0
        var dismisses=0
        compose.setContent {MaterialTheme {
            if(shown.value) PlaylistSongPicker("退出动画",songs,initialSelected=setOf("b"),
                onConfirm={confirms++},onDismiss={dismisses++;shown.value=false})
        }}
        val confirm=compose.onNodeWithTag("playlist-songs-confirm")
        confirm.assertIsDisplayed()
        compose.mainClock.autoAdvance=false
        confirm.performClick()
        compose.runOnIdle {assertEquals(1,confirms);assertEquals(0,dismisses)}
        confirm.assertExists()
        compose.mainClock.advanceTimeBy(80)
        confirm.assertExists()
        compose.mainClock.advanceTimeBy(1000)
        confirm.assertDoesNotExist()
        compose.runOnIdle {assertEquals(1,dismisses)}
    }
    @Test fun cancelWaitsForTheSheetToHideAndNeverConfirms() {
        val shown=mutableStateOf(true)
        var confirms=0
        var dismisses=0
        compose.setContent {MaterialTheme {
            if(shown.value) PlaylistSongPicker("取消退出动画",songs,initialSelected=setOf("b"),
                onConfirm={confirms++},onDismiss={dismisses++;shown.value=false})
        }}
        val cancel=compose.onNodeWithText("取消")
        cancel.assertIsDisplayed()
        compose.mainClock.autoAdvance=false
        cancel.performClick()
        compose.runOnIdle {assertEquals(0,confirms);assertEquals(0,dismisses)}
        cancel.assertExists()
        compose.mainClock.advanceTimeBy(80)
        cancel.assertExists()
        compose.mainClock.advanceTimeBy(1000)
        cancel.assertDoesNotExist()
        compose.runOnIdle {assertEquals(0,confirms);assertEquals(1,dismisses)}
    }
    @Test fun replacingAnExitingSheetCancelsItsOldCompletionCallback() {
        val request=mutableStateOf<String?>("旧面板")
        val dismissed=mutableListOf<String>()
        var confirms=0
        compose.setContent {MaterialTheme {
            request.value?.let {current -> androidx.compose.runtime.key(current) {
                PlaylistSongPicker(current,songs,initialSelected=setOf("b"),onConfirm={confirms++},
                    onDismiss={dismissed+=current;request.value=null})
            }}
        }}
        compose.onNodeWithTag("playlist-songs-confirm").assertIsDisplayed()
        compose.mainClock.autoAdvance=false
        compose.onNodeWithTag("playlist-songs-confirm").performClick()
        compose.runOnIdle {assertEquals(1,confirms);request.value="新面板"}
        compose.mainClock.advanceTimeBy(1000)
        compose.onNodeWithText("新面板").assertIsDisplayed()
        compose.runOnIdle {assertEquals(emptyList<String>(),dismissed)}
    }

}
