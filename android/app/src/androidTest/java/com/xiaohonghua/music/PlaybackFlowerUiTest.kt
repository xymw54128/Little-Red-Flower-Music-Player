package com.xiaohonghua.music

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlaybackFlowerUiTest {
    @get:Rule val compose=createComposeRule()

    @Before fun emulatorOnly() {
        check(Build.HARDWARE in listOf("ranchu","goldfish")) {"七色花动画测试只能在模拟器中执行"}
        compose.mainClock.autoAdvance=false
    }
    private fun rotation()=compose.onNodeWithTag("player-playback-flower").fetchSemanticsNode().config[PlaybackFlowerRotationKey]
    private fun velocity()=compose.onNodeWithTag("player-playback-flower").fetchSemanticsNode().config[PlaybackFlowerVelocityKey]
    private fun forward(from: Float,to: Float)=(to-from+360f)%360f

    @Test fun steadyPlaybackRotatesAndPauseBrakesBeforeBecomingCompletelyStill() {
        val playing=mutableStateOf(true)
        compose.setContent {MaterialTheme {PlaybackFlower(playing.value,true)}}
        compose.onNodeWithTag("player-playback-flower").assertWidthIsEqualTo(32.dp).assertHeightIsEqualTo(32.dp)
        compose.mainClock.advanceTimeBy(480)
        assertEquals(60f,velocity(),.01f)
        val cruising=rotation()
        compose.mainClock.advanceTimeBy(320)
        assertEquals(60f*.32f,forward(cruising,rotation()),1f)

        compose.runOnIdle {playing.value=false}
        compose.mainClock.advanceTimeBy(160)
        val braking=rotation()
        assertTrue(velocity() > 0f && velocity() < 60f)
        compose.mainClock.advanceTimeBy(900)
        assertEquals(0f,velocity(),.001f)
        assertTrue(forward(braking,rotation()) > 0f)
        val stopped=rotation()
        compose.mainClock.advanceTimeBy(1600)
        assertEquals(stopped,rotation(),0f)
    }

    @Test fun quickRetargetAndHideShowPreserveAngleWithoutAccumulatingHiddenTime() {
        val playing=mutableStateOf(true)
        val visible=mutableStateOf(true)
        compose.setContent {MaterialTheme {PlaybackFlower(playing.value,visible.value)}}
        compose.mainClock.advanceTimeBy(480)
        compose.runOnIdle {playing.value=false}
        compose.mainClock.advanceTimeBy(96)
        val beforeRestart=rotation()
        val slowing=velocity()
        assertTrue(slowing > 0f && slowing < 60f)
        compose.runOnIdle {playing.value=true}
        compose.mainClock.advanceTimeByFrame()
        assertTrue(forward(beforeRestart,rotation()) < 2f)
        assertTrue(velocity() > 0f)
        compose.mainClock.advanceTimeBy(320)
        assertEquals(60f,velocity(),.01f)

        compose.runOnIdle {visible.value=false}
        compose.mainClock.advanceTimeByFrame()
        val hidden=rotation()
        compose.mainClock.advanceTimeBy(5000)
        assertEquals(hidden,rotation(),0f)
        compose.runOnIdle {visible.value=true}
        compose.mainClock.advanceTimeByFrame()
        assertTrue(forward(hidden,rotation()) < 2f)
        val resumed=rotation()
        compose.mainClock.advanceTimeBy(320)
        assertEquals(60f*.32f,forward(resumed,rotation()),1f)
    }
}
