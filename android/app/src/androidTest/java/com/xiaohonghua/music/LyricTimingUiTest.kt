package com.xiaohonghua.music

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class LyricTimingUiTest {
    @get:Rule val compose=createComposeRule()
    private val first=Song("timing-first","content://timing/first","first.flac","第一首",duration=5_000)
    private val second=Song("timing-second","content://timing/second","second.flac","第二首",duration=5_000)
    private val text="""[00:00.00]第一行
[00:01.00]第二行
[00:02.00]第三行"""
    @Test fun buttonsCommitOneHundredMillisecondsAndResetWithoutChangingAudioTime() {
        val offset=mutableStateOf(0L)
        val committed=mutableListOf<Long>()
        val audioPosition=1_050L
        compose.setContent {MaterialTheme {Column(Modifier.fillMaxWidth().padding(16.dp)) {
            LyricTimingControl(first,text,offset.value,{audioPosition},{committed+=it;offset.value=it})
        }}}
        compose.onNodeWithTag("lyric-offset-preview").assertTextEquals("第二行")
        compose.onNodeWithTag("lyric-offset-plus").performClick()
        compose.onNodeWithTag("lyric-offset-value").assertTextContains("延后 0.1 秒")
        compose.onNodeWithTag("lyric-offset-preview").assertTextEquals("第一行")
        compose.onNodeWithTag("lyric-offset-minus").performClick()
        compose.onNodeWithTag("lyric-offset-preview").assertTextEquals("第二行")
        compose.onNodeWithTag("lyric-offset-minus").performClick()
        compose.onNodeWithTag("lyric-offset-value").assertTextContains("提前 0.1 秒")
        compose.onNodeWithTag("lyric-offset-reset").performClick()
        compose.onNodeWithTag("lyric-offset-value").assertTextEquals("与 LRC 一致")
        compose.onNodeWithTag("lyric-offset-reset").assertIsNotEnabled()
        compose.runOnIdle {assertEquals(listOf(100L,0L,-100L,0L),committed);assertEquals(1_050L,audioPosition)}
    }
    @Test fun negativeOffsetAdvancesPreviewToTheUpcomingLine() {
        compose.setContent {MaterialTheme {LyricTimingControl(first,text,-100L,{950L},{})}}
        compose.onNodeWithTag("lyric-offset-preview").assertTextEquals("第二行")
        compose.onNodeWithTag("lyric-offset-value").assertTextContains("提前 0.1 秒")
    }
    @Test fun sliderPreviewsDuringDragButWritesOnlyAfterRelease() {
        val offset=mutableStateOf(0L)
        val committed=mutableListOf<Long>()
        compose.setContent {MaterialTheme {LyricTimingControl(first,text,offset.value,{1_050L},{committed+=it;offset.value=it})}}
        compose.onNodeWithTag("lyric-offset-slider").performTouchInput {
            down(center)
            moveTo(Offset(width*.7f,centerY),delayMillis=100)
        }
        compose.runOnIdle {assertTrue("Dragging must not persist every pointer move",committed.isEmpty())}
        compose.onNodeWithTag("lyric-offset-slider").performTouchInput {up()}
        compose.runOnIdle {
            assertEquals(1,committed.size)
            assertTrue(committed.single() > 0)
            assertEquals(0L,committed.single()%100)
            assertTrue(committed.single() <= MAX_LYRIC_OFFSET)
        }
    }
    @Test fun changingSongsDuringDragDiscardsTheOldDraftInsteadOfWritingToTheNewSong() {
        val target=mutableStateOf(first)
        val offset=mutableStateOf(0L)
        val writes=mutableListOf<Pair<String,Long>>()
        compose.setContent {MaterialTheme {
            val song=target.value
            LyricTimingControl(song,text,offset.value,{1_050L},{value -> writes+=song.id to value;offset.value=value})
        }}
        compose.onNodeWithTag("lyric-offset-slider").performTouchInput {
            down(center);moveTo(Offset(width*.8f,centerY),delayMillis=100)
        }
        compose.runOnIdle {target.value=second;offset.value=500L}
        compose.onNodeWithTag("lyric-offset-slider").performTouchInput {up()}
        compose.runOnIdle {assertTrue("A cancelled old-song drag must not commit to either song",writes.isEmpty())}
        compose.onNodeWithTag("lyric-offset-value").assertTextContains("延后 0.5 秒")
        compose.onNodeWithTag("lyric-offset-minus").performClick()
        compose.runOnIdle {assertEquals(listOf(second.id to 400L),writes)}
    }
}
