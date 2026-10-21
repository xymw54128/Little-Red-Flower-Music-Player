package com.xiaohonghua.music

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Synthetic feedback only: never changes the device music library. */
@RunWith(AndroidJUnit4::class)
class ActionSnackbarUiTest {
    @get:Rule val compose=createComposeRule()
    private val host=SnackbarHostState()

    @Before fun emulatorOnly() {
        check(Build.HARDWARE in listOf("ranchu","goldfish")) {"完成提示测试只能在模拟器中执行"}
        compose.mainClock.autoAdvance=false
    }
    @Test fun completionDrawsCircleBeforeCheckThenExitsSmoothly() {
        compose.setContent {MaterialTheme {
            LaunchedEffect(Unit) {host.showSnackbar(ActionFeedback("已保存歌曲顺序",completed=true,duration=SnackbarDuration.Indefinite))}
            ActionSnackbarHost(host)
        }}
        compose.mainClock.advanceTimeBy(180)
        val mark=compose.onNodeWithTag("action-complete-mark",useUnmergedTree=true)
        val early=mark.fetchSemanticsNode().config
        assertTrue(early[CompletionCircleProgressKey] > 0f)
        assertEquals(0f,early[CompletionTickProgressKey],.001f)
        compose.mainClock.advanceTimeBy(300)
        val late=mark.fetchSemanticsNode().config
        assertEquals(1f,late[CompletionCircleProgressKey],.001f)
        assertTrue(late[CompletionTickProgressKey] > 0f)
        compose.mainClock.advanceTimeBy(200)
        assertEquals(1f,mark.fetchSemanticsNode().config[CompletionTickProgressKey],.001f)
        compose.runOnIdle {host.currentSnackbarData!!.dismiss()}
        compose.mainClock.advanceTimeBy(64)
        compose.onNodeWithTag("action-snackbar").assertExists()
        compose.mainClock.advanceTimeBy(320)
        compose.onNodeWithTag("action-snackbar").assertDoesNotExist()
    }
    @Test fun undoActionStillCompletesHostResult() {
        var result: SnackbarResult?=null
        compose.setContent {MaterialTheme {
            LaunchedEffect(Unit) {result=host.showSnackbar(ActionFeedback("已移除歌曲","撤销",true,SnackbarDuration.Indefinite))}
            ActionSnackbarHost(host)
        }}
        compose.mainClock.advanceTimeBy(640)
        compose.onNodeWithText("撤销").performClick()
        compose.waitForIdle()
        compose.runOnIdle {assertEquals(SnackbarResult.ActionPerformed,result)}
    }
}
