package com.xiaohonghua.music

import android.app.Application
import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Synthetic UI only; does not import audio, modify the stored library or start playback. */
@RunWith(AndroidJUnit4::class)
class PlayerNavigationUiTest {
    @get:Rule val compose=createComposeRule()
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val store=ViewModelStore()
    private lateinit var model: MusicModel
    private val song=Song("navigation-test","content://example/navigation-test","test.flac","导航测试",
        artist="测试歌手",album="测试专辑",lyrics="[00:00.00]第一行歌词\n[00:03.00]第二行歌词",duration=10_000)

    @Before fun prepare() {
        check(Build.HARDWARE in listOf("ranchu","goldfish")) {"播放器导航测试只能在模拟器中执行"}
        instrumentation.runOnMainSync {
            val application=instrumentation.targetContext.applicationContext as Application
            model=ViewModelProvider(store,ViewModelProvider.AndroidViewModelFactory(application))[MusicModel::class.java]
        }
    }
    @After fun release() {instrumentation.runOnMainSync {store.clear()}}
    private fun player(settings: Settings=Settings(),onBack:()->Unit={},onQueue:()->Unit={},onAdd:()->Unit={},onInfo:()->Unit={},
        library: LibraryState=LibraryState(songs=listOf(song),settings=settings),songOverride: Song=song,
        modifier: Modifier=Modifier.fillMaxSize(),fontScale: Float?=null) {
        compose.setContent {MaterialTheme {
            val density=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,fontScale ?: density.fontScale)) {
                Box(modifier) {
                    PlayerScreen(songOverride,library,PlaybackState(currentId=songOverride.id),model,
                        onBack=onBack,onQueue=onQueue,onAdd=onAdd,onInfo=onInfo)
                }
            }
        }}
    }
    @Test fun coverPageStaysFixedDuringVerticalSwipes() {
        player()
        val tags=listOf("player-artwork","player-title","player-lyric-preview")
        val original=tags.associateWith {compose.onNodeWithTag(it).fetchSemanticsNode().boundsInRoot}
        val cover=hasTestTag("player-cover-content") or hasAnyAncestor(hasTestTag("player-cover-content"))
        // The offscreen full lyrics page remains scrollable; inspect only the cover subtree.
        compose.onAllNodes(cover and hasScrollAction(),useUnmergedTree=true).assertCountEquals(0)
        compose.onAllNodes(cover and SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange),useUnmergedTree=true).assertCountEquals(0)
        compose.onNodeWithTag("player-cover-content").performTouchInput {swipeUp(durationMillis=450)}
        tags.forEach {assertEquals("Upward swipe moved $it",original.getValue(it),compose.onNodeWithTag(it).fetchSemanticsNode().boundsInRoot)}
        compose.onNodeWithTag("player-cover-content").performTouchInput {swipeDown(durationMillis=450)}
        tags.forEach {assertEquals("Downward swipe moved $it",original.getValue(it),compose.onNodeWithTag(it).fetchSemanticsNode().boundsInRoot)}
        compose.onNodeWithText("正在播放").assertIsDisplayed()
    }
    @Test fun compactCoverReservesTwoLyricLinesBelowLongMetadataAtLargeFontScale() {
        val longSong=song.copy(title="一首很长很长需要展示两行的歌曲标题与更多文字",
            artist="这是一位名字很长的测试歌手",album="一张名字很长需要换行展示的测试专辑与特别版本",
            lyrics="[00:00.00]这是需要换成两行展示的预览歌词以检查有限空间内完整保留两行文字")
        player(songOverride=longSong,modifier=Modifier.size(320.dp,640.dp),fontScale=1.3f)
        val pages=compose.onNodeWithTag("player-pages").fetchSemanticsNode().boundsInRoot
        val quality=compose.onNodeWithTag("player-quality").fetchSemanticsNode().boundsInRoot
        val preview=compose.onNodeWithTag("player-lyric-preview").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val textNode=compose.onNodeWithTag("player-lyric-preview-text",useUnmergedTree=true)
        val layouts=mutableListOf<TextLayoutResult>()
        textNode.performSemanticsAction(SemanticsActions.GetTextLayoutResult) {assertTrue(it(layouts))}
        val layout=layouts.single()
        val text=textNode.assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertEquals("The long lyric must wrap into two preview lines",2,layout.lineCount)
        assertTrue("Preview must follow the quality metadata",preview.top >= quality.bottom-.5f)
        assertTrue("Preview must stay inside the pager",preview.top >= pages.top && preview.bottom <= pages.bottom+.5f)
        assertTrue("Both text lines must fit in their visible bounds",text.height+.5f >= layout.size.height)
        assertTrue("Text must stay inside its preview space",text.top >= preview.top-.5f && text.bottom <= preview.bottom+.5f)
    }
    @Test fun tappingArtworkOpensLyricsAndRightSwipeReturnsToCover() {
        player()
        compose.onNodeWithTag("player-artwork").performClick()
        compose.onNodeWithText("${song.title} - ${song.artist}").assertIsDisplayed()
        compose.onNodeWithTag("lyrics-list").assertIsDisplayed()
        compose.onNodeWithTag("player-pages").performTouchInput {swipeRight(durationMillis=450)}
        compose.onNodeWithText("正在播放").assertIsDisplayed()
        compose.onNodeWithTag("player-artwork").assertIsDisplayed()
    }
    @Test fun leftSwipeAndCurrentLyricPreviewBothOpenLyrics() {
        player()
        compose.onNodeWithTag("player-pages").performTouchInput {swipeLeft(durationMillis=450)}
        compose.onNodeWithText("${song.title} - ${song.artist}").assertIsDisplayed()
        compose.onNodeWithTag("player-pages").performTouchInput {swipeRight(durationMillis=450)}
        compose.onNodeWithTag("player-lyric-preview").performClick()
        compose.onNodeWithText("${song.title} - ${song.artist}").assertIsDisplayed()
    }
    @Test fun coverAndLyricsBothUsePlainThemeWithoutCustomBackground() {
        player()
        compose.onNodeWithTag("player-page-background").assertDoesNotExist()
        compose.onNodeWithTag("player-artwork").performClick()
        compose.onNodeWithTag("player-lyrics-page").assertIsDisplayed()
        compose.onNodeWithTag("player-page-background").assertDoesNotExist()
        compose.onNodeWithTag("player-pages").performTouchInput {swipeRight(durationMillis=450)}
        compose.onNodeWithTag("player-page-background").assertDoesNotExist()
    }
    @Test fun queueAndCollapseControlsKeepTheirActionsOnEitherPlayerPage() {
        var queues=0
        var backs=0
        player(onBack={backs++},onQueue={queues++})
        compose.onNodeWithContentDescription("播放队列").performClick()
        compose.onNodeWithText("正在播放").assertIsDisplayed()
        compose.onNodeWithTag("player-artwork").performClick()
        compose.onNodeWithText("${song.title} - ${song.artist}").assertIsDisplayed()
        compose.onNodeWithText("动态歌词").assertDoesNotExist()
        compose.onNodeWithText("导入歌词").assertDoesNotExist()
        compose.onNodeWithText("回到当前").assertDoesNotExist()
        compose.onNodeWithContentDescription("歌词字体、字号与行间距").assertDoesNotExist()
        compose.onNodeWithContentDescription("添加到歌单").assertDoesNotExist()
        compose.onNodeWithContentDescription("播放队列").performClick()
        compose.onNodeWithContentDescription("收起播放器").performClick()
        compose.runOnIdle {
            assertEquals(2,queues)
            assertEquals(1,backs)
        }
    }
    @Test fun pageIndicatorsHaveFullTouchTargetsAndExposeSelectedPage() {
        player()
        compose.onNodeWithTag("player-page-cover").assertWidthIsEqualTo(48.dp).assertHeightIsEqualTo(48.dp).assertIsSelected()
        compose.onNodeWithTag("player-page-lyrics").assertIsNotSelected().performClick()
        compose.onNodeWithTag("player-page-lyrics").assertIsSelected()
        compose.onNodeWithText("${song.title} - ${song.artist}").assertIsDisplayed()
        compose.onNodeWithTag("player-page-cover").performClick().assertIsSelected()
        compose.onNodeWithText("正在播放").assertIsDisplayed()
    }
    @Test fun coverMoreMenuRoutesAddAndInfoAndClosesAfterSelection() {
        var additions=0
        var details=0
        player(onAdd={additions++},onInfo={details++})
        compose.onNodeWithContentDescription("歌曲更多选项").performClick()
        compose.onNodeWithText("添加到歌单").assertIsDisplayed().performClick()
        compose.onNodeWithText("添加到歌单").assertDoesNotExist()
        compose.runOnIdle {assertEquals(1,additions);assertEquals(0,details)}
        compose.onNodeWithTag("player-more").performClick()
        compose.onNodeWithText("歌曲信息").assertIsDisplayed().performClick()
        compose.onNodeWithText("歌曲信息").assertDoesNotExist()
        compose.runOnIdle {assertEquals(1,additions);assertEquals(1,details)}
    }
    @Test fun moreMenuOnlyAppearsOnCoverAndReturnsAfterLyrics() {
        player()
        compose.onNodeWithTag("favorite-${song.id}").assertIsNotSelected()
        compose.onNodeWithTag("player-more").assertIsDisplayed()
        compose.onNodeWithTag("player-page-lyrics").performClick()
        compose.onNodeWithTag("player-more").assertDoesNotExist()
        compose.onNodeWithContentDescription("歌曲更多选项").assertDoesNotExist()
        compose.onNodeWithTag("player-page-cover").performClick()
        compose.onNodeWithTag("player-more").assertIsDisplayed().performClick()
        compose.onNodeWithText("添加到歌单").assertIsDisplayed()
        compose.onNodeWithText("歌曲信息").assertIsDisplayed()
    }
    @Test fun galleryFavoriteReflectsMembershipInFavoritesPlaylist() {
        val favorites=Playlist(FAVORITES_PLAYLIST_ID,"我喜欢",songIds=listOf(song.id))
        player(library=LibraryState(songs=listOf(song),playlists=listOf(favorites)))
        compose.onNodeWithTag("favorite-${song.id}").assertIsDisplayed().assertIsSelected()
        compose.onNodeWithTag("player-artwork").performClick()
        compose.onNodeWithTag("player-page-cover").performClick()
        compose.onNodeWithTag("favorite-${song.id}").assertIsDisplayed().assertIsSelected()
    }
    @Test fun longPressReorderRespectsBoundaryThenMovesByOneMeasuredRow() {
        val tracks=(0..2).map {song.copy(id="reorder-$it",title="排序歌曲 $it")}
        val draft=mutableStateListOf<Song>().apply {addAll(tracks)}
        var moves=0
        compose.setContent {MaterialTheme {
            ReorderList(draft,onReorder={ids ->
                val byId=draft.associateBy {it.id}
                val next=ids.mapNotNull(byId::get)
                if(next != draft.toList()) {draft.clear();draft.addAll(next);moves++}
            })
        }}
        compose.onNodeWithTag("reorder-handle-reorder-0").performTouchInput {
            down(center);advanceEventTime(650);moveBy(Offset(0f,-height*2f));up()
        }
        compose.runOnIdle {
            assertEquals(tracks.map {it.id},draft.map {it.id})
            assertEquals(0,moves)
        }
        compose.onNodeWithTag("reorder-handle-reorder-1").performTouchInput {
            down(center);advanceEventTime(650);moveBy(Offset(0f,height*2.25f));up()
        }
        compose.runOnIdle {
            assertEquals(listOf(tracks[0].id,tracks[2].id,tracks[1].id),draft.map {it.id})
            assertEquals(1,moves)
        }
    }
}
