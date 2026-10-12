package com.xiaohonghua.music

import android.os.SystemClock
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SleepTimerUiTest {
    @get:Rule val compose=createComposeRule()
    @Test fun presetsAndCancelUseSingleAccessibleActionsAndKeepPanelOpen() {
        val state=mutableStateOf(SleepTimerState())
        val selected=mutableListOf<Int>()
        var cancelled=0
        compose.setContent {MaterialTheme {SleepTimerPanel(state.value,true,
            onSet={minutes -> selected+=minutes;state.value=SleepTimerState(SystemClock.elapsedRealtime()+minutes*60_000L);true},
            onCancel={cancelled++;state.value=SleepTimerState();true})}}
        compose.onAllNodes(hasClickAction()).assertCountEquals(4)
        listOf(15,30,45,60).forEach {minutes ->
            compose.onNodeWithTag("sleep-timer-preset-$minutes").assertHeightIsAtLeast(48.dp).performClick()
            compose.onNodeWithTag("sleep-timer-panel").assertExists()
            compose.onNodeWithTag("sleep-timer-cancel").assertExists()
        }
        compose.onAllNodes(hasClickAction()).assertCountEquals(5)
        compose.onNodeWithTag("sleep-timer-cancel").assertHeightIsAtLeast(48.dp).performClick()
        compose.onNodeWithText("未开启定时暂停").assertExists()
        compose.onNodeWithTag("sleep-timer-cancel").assertDoesNotExist()
        compose.runOnIdle {assertEquals(listOf(15,30,45,60),selected);assertEquals(1,cancelled)}
    }
    @Test fun unavailableControllerDisablesPresetsAndCancel() {
        var calls=0
        compose.setContent {MaterialTheme {SleepTimerPanel(SleepTimerState(SystemClock.elapsedRealtime()+90_000),false,
            onSet={calls++;true},onCancel={calls++;true})}}
        listOf(15,30,45,60).forEach {compose.onNodeWithTag("sleep-timer-preset-$it").assertIsNotEnabled()}
        compose.onNodeWithTag("sleep-timer-cancel").assertIsNotEnabled()
        compose.onNodeWithTag("sleep-timer-connecting").assertExists()
        compose.runOnIdle {assertEquals(0,calls)}
    }
    @Test fun failedSetShowsInlineErrorAndASuccessClearsIt() {
        var succeed=false
        val errors=mutableListOf<String>()
        compose.setContent {MaterialTheme {SleepTimerPanel(SleepTimerState(),true,
            onSet={succeed},onCancel={false},onError={errors.add(it)})}}
        compose.onNodeWithTag("sleep-timer-preset-15").performClick()
        compose.onNodeWithText("定时暂停未开启，请稍后再试").assertExists()
        compose.runOnIdle {assertEquals(1,errors.size);succeed=true}
        compose.onNodeWithTag("sleep-timer-preset-30").performClick()
        compose.onNodeWithTag("sleep-timer-error").assertDoesNotExist()
    }
    @Test fun activeTimerExposesCountdownAndFailedCancelKeepsItVisible() {
        val deadline=SystemClock.elapsedRealtime()+60_000
        compose.setContent {MaterialTheme {SleepTimerPanel(SleepTimerState(deadline),true,onSet={true},onCancel={false})}}
        compose.onNodeWithTag("sleep-timer-countdown").assertTextContains("剩余",substring=true)
        compose.onNodeWithText("到时暂停播放，保留当前进度").assertExists()
        compose.onNodeWithTag("sleep-timer-cancel").performClick()
        compose.onNodeWithText("取消失败，请稍后再试").assertExists()
        compose.onNodeWithTag("sleep-timer-cancel").assertExists()
        compose.onAllNodes(hasClickAction()).assertCountEquals(5)
    }
}
