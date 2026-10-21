package com.xiaohonghua.music

import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Synthetic UI only; no playback service, library import or stored data. */
@RunWith(AndroidJUnit4::class)
class MiniPlayerUiTest {
    @get:Rule val compose=createComposeRule()
    private val song=Song("mini-test","content://example/mini","mini.flac","一首歌名很长的歌曲 Long Song Title",
        artist="测试歌手与名字较长的合作歌手",duration=180_000)
    private val playback=MutableStateFlow(PlaybackState(connected=true,currentId=song.id,duration=song.duration))

    @Before fun emulatorOnly() {
        check(Build.HARDWARE in listOf("ranchu","goldfish")) {"迷你播放栏测试只能在模拟器中执行"}
        compose.mainClock.autoAdvance=false
    }
    private fun show(width: Int=320,fontScale: Float=1f,onPrevious:()->Unit={},onToggle:()->Unit={},
        onNext:()->Unit={},onOpen:()->Unit={}) {
        compose.setContent {MaterialTheme(colorScheme=appColorScheme(0,false)) {
            val density=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,fontScale)) {
                Box(Modifier.width(width.dp).testTag("mini-host")) {
                    MiniPlayer(song,playback,onPrevious,onToggle,onNext,onOpen)
                }
            }
        }}
    }
    @Test fun transportButtonsKeepTheirActionsWithoutOpeningThePlayer() {
        var previous=0;var toggles=0;var next=0;var opens=0
        show(onPrevious={previous++},onToggle={toggles++;playback.value=playback.value.copy(playing=!playback.value.playing)},
            onNext={next++},onOpen={opens++})
        compose.onNodeWithTag("mini-previous").performTouchInput {click()}
        compose.onNodeWithTag("mini-toggle").performTouchInput {click()}
        compose.mainClock.advanceTimeBy(180)
        compose.onNodeWithContentDescription("暂停").assertIsDisplayed()
        compose.onNodeWithTag("mini-next").performTouchInput {click()}
        compose.runOnIdle {assertEquals(1,previous);assertEquals(1,toggles);assertEquals(1,next);assertEquals(0,opens)}
        compose.onNodeWithTag("mini-player").performClick()
        compose.runOnIdle {assertEquals(1,opens)}
    }
    @Test fun narrowScreenFillsTheHostAndKeepsThreeSeparateFullTouchTargets() {
        show()
        compose.onNodeWithTag("mini-player-surface").assertWidthIsEqualTo(320.dp)
        compose.onNodeWithTag("mini-player").assertWidthIsEqualTo(320.dp).assertHeightIsEqualTo(72.dp)
        val host=compose.onNodeWithTag("mini-host").fetchSemanticsNode().boundsInRoot
        val bar=compose.onNodeWithTag("mini-player").fetchSemanticsNode().boundsInRoot
        assertEquals(host.left,bar.left,.1f);assertEquals(host.right,bar.right,.1f)
        val buttons=listOf("mini-previous","mini-toggle","mini-next").map {tag ->
            compose.onNodeWithTag(tag).assertWidthIsEqualTo(48.dp).assertHeightIsEqualTo(48.dp)
                .fetchSemanticsNode().boundsInRoot
        }
        assertTrue(buttons.zipWithNext().all {(a,b)->a.right <= b.left+.1f})
        assertTrue(buttons.all {it.left >= bar.left && it.right <= bar.right && it.top >= bar.top && it.bottom <= bar.bottom})
        compose.onNodeWithTag("mini-player-title",useUnmergedTree=true).assertTextEquals(song.title)
    }
    @Test fun largeSystemTextStillLeavesTransportControlsInsideTheBar() {
        show(fontScale=2f)
        val bar=compose.onNodeWithTag("mini-player").fetchSemanticsNode().boundsInRoot
        listOf("mini-previous","mini-toggle","mini-next").forEach {tag ->
            val b=compose.onNodeWithTag(tag).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            assertTrue(b.left >= bar.left && b.right <= bar.right && b.top >= bar.top && b.bottom <= bar.bottom)
        }
    }
    @Test fun disconnectedControllerDisablesTransportButThePlayerCanStillOpen() {
        playback.value=playback.value.copy(connected=false)
        var opens=0
        show(onOpen={opens++})
        listOf("mini-previous","mini-toggle","mini-next").forEach {compose.onNodeWithTag(it).assertIsNotEnabled()}
        compose.onNodeWithTag("mini-player").performClick()
        compose.runOnIdle {assertEquals(1,opens)}
    }
    @Test fun ringObservesRealPositionTicksAndClearsWhenTheTrackDoesNotMatchYet() {
        show()
        fun progress()=compose.onNodeWithTag("mini-flower-artwork",useUnmergedTree=true)
            .fetchSemanticsNode().config[MiniPlayerProgressKey]
        assertEquals(0f,progress(),0f)
        compose.runOnIdle {playback.value=playback.value.copy(position=90_000)}
        compose.mainClock.advanceTimeByFrame()
        assertEquals(.5f,progress(),0f)
        compose.runOnIdle {playback.value=playback.value.copy(position=30_000,duration=0)}
        compose.mainClock.advanceTimeByFrame()
        assertEquals(1f/6f,progress(),.000001f)
        compose.runOnIdle {playback.value=playback.value.copy(currentId="next-track",position=90_000)}
        compose.mainClock.advanceTimeByFrame()
        assertEquals(0f,progress(),0f)
    }
}
