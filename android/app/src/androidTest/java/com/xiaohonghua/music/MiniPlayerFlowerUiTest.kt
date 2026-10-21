package com.xiaohonghua.music

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MiniPlayerFlowerUiTest {
    @get:Rule val compose=createComposeRule()
    @Before fun stopClock() {compose.mainClock.autoAdvance=false}
    private fun node()=compose.onNodeWithTag("mini-flower-artwork").fetchSemanticsNode().config

    @Test fun realProgressRespondsToSeekAndTrackChangeWithoutResettingTheFlower() {
        val song=mutableStateOf(Song("one","","one.flac","One"))
        val position=mutableStateOf(30000L)
        val duration=mutableStateOf(120000L)
        compose.setContent {MaterialTheme {
            MiniPlayerFlowerArtwork(song.value,true,position.value,duration.value)
        }}
        compose.mainClock.advanceTimeBy(480)
        assertEquals(.25f,node()[MiniPlayerProgressKey],0f)
        val start=node()[MiniPlayerFlowerRotationKey]
        compose.runOnIdle {position.value=90000L}
        compose.mainClock.advanceTimeByFrame()
        assertEquals(.75f,node()[MiniPlayerProgressKey],0f)
        assertTrue(node()[MiniPlayerFlowerRotationKey] >= start)
        val beforeSwitch=node()[MiniPlayerFlowerRotationKey]
        compose.runOnIdle {
            song.value=Song("two","","two.flac","Two")
            position.value=0
            duration.value=180000
        }
        compose.mainClock.advanceTimeByFrame()
        assertEquals(0f,node()[MiniPlayerProgressKey],0f)
        assertTrue(node()[MiniPlayerFlowerRotationKey] >= beforeSwitch)
        assertEquals(48f,node()[MiniPlayerFlowerVelocityKey],.001f)
    }

    @Test fun pauseBrakesAndTheProgressStaysAtItsActualPosition() {
        val playing=mutableStateOf(true)
        compose.setContent {MaterialTheme {MiniPlayerFlowerArtwork(
            Song("one","","one.flac","One"),playing.value,30000,120000)
        }}
        compose.mainClock.advanceTimeBy(480)
        compose.runOnIdle {playing.value=false}
        compose.mainClock.advanceTimeBy(400)
        assertTrue(node()[MiniPlayerFlowerVelocityKey] in .001f..47.999f)
        compose.mainClock.advanceTimeBy(600)
        assertEquals(0f,node()[MiniPlayerFlowerVelocityKey],.001f)
        val stopped=node()[MiniPlayerFlowerRotationKey]
        compose.mainClock.advanceTimeBy(1600)
        assertEquals(stopped,node()[MiniPlayerFlowerRotationKey],0f)
        assertEquals(.25f,node()[MiniPlayerProgressKey],0f)
    }
}
