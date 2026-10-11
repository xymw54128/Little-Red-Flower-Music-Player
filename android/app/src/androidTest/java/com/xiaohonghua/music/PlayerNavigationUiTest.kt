package com.xiaohonghua.music

import android.app.Application
import android.os.Build
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
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
    private fun player(settings: Settings=Settings(),onBack:()->Unit={},onQueue:()->Unit={}) {
        compose.setContent {MaterialTheme {
            PlayerScreen(song,LibraryState(songs=listOf(song),settings=settings),PlaybackState(currentId=song.id),model,
                onBack=onBack,onQueue=onQueue,onAdd={})
        }}
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
    @Test fun lyricPageRemovesPhotoBackgroundAndRestoresItOnlyOnCoverPage() {
        player(Settings(background="photo"))
        compose.onNodeWithTag("player-page-background").assertExists()
        compose.onNodeWithTag("player-artwork").performClick()
        compose.onNodeWithTag("player-lyrics-page").assertIsDisplayed()
        compose.onNodeWithTag("player-page-background").assertDoesNotExist()
        compose.onNodeWithTag("player-pages").performTouchInput {swipeRight(durationMillis=450)}
        compose.onNodeWithTag("player-page-background").assertExists()
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
    @Test fun longPressReorderRespectsBoundaryThenMovesByOneMeasuredRow() {
        val tracks=(0..2).map {song.copy(id="reorder-$it",title="排序歌曲 $it")}
        val draft=mutableStateListOf<Song>().apply {addAll(tracks)}
        var moves=0
        compose.setContent {MaterialTheme {
            LazyColumn {
                itemsIndexed(draft,key={_,track -> track.id}) {index,track ->
                    ReorderRow(track,index,draft.size,onMove={direction ->
                        val from=draft.indexOfFirst {it.id == track.id}
                        val to=(from+direction).coerceIn(0,draft.lastIndex)
                        if(from != to) {draft.add(to,draft.removeAt(from));moves++}
                    })
                }
            }
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
