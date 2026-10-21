package com.xiaohonghua.music

import androidx.activity.BackEventCompat
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Synthetic overlays only; never loads audio or changes the saved library. */
@OptIn(ExperimentalMaterial3Api::class)
@RunWith(AndroidJUnit4::class)
class OverlayUiTest {
    @get:Rule val compose=createComposeRule()

    private fun progress(fraction: Float)=BackEventCompat(0f,200f,fraction,BackEventCompat.EDGE_LEFT)
    private fun sheetBounds()=compose.onNodeWithTag("app-sheet-surface").fetchSemanticsNode().boundsInRoot
    private fun sameBounds(expected: Rect,actual: Rect) {
        assertEquals(expected.left,actual.left,1f)
        assertEquals(expected.top,actual.top,1f)
        assertEquals(expected.width,actual.width,1f)
        assertEquals(expected.height,actual.height,1f)
    }
    private fun matches(expected: Color,tag: String) {
        val pixels=compose.onNodeWithTag(tag).captureToImage().toPixelMap()
        val actual=pixels[pixels.width/2,6]
        assertEquals(expected.red,actual.red,.025f)
        assertEquals(expected.green,actual.green,.025f)
        assertEquals(expected.blue,actual.blue,.025f)
    }

    @Test fun sheetBackProgressAndCancellationKeepItsSizeThenCommitWaitsForHide() {
        val shown=mutableStateOf(true)
        var dismisses=0
        lateinit var parentDispatcher: OnBackPressedDispatcher
        lateinit var sheetDispatcher: OnBackPressedDispatcher
        compose.setContent {MaterialTheme(colorScheme=appColorScheme(0,false)) {
            val parent=LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            SideEffect {parentDispatcher=parent}
            if(shown.value) AppModalBottomSheet(onDismissRequest={dismisses++;shown.value=false}) {
                val dialog=LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
                SideEffect {sheetDispatcher=dialog}
                Box(Modifier.fillMaxWidth().height(220.dp)) {Text("稳定的返回面板")}
            }
        }}
        compose.onNodeWithText("稳定的返回面板").assertIsDisplayed()
        val initial=sheetBounds()
        compose.runOnIdle {assertNotSame("The sheet must own its back dispatcher",parentDispatcher,sheetDispatcher)}
        compose.mainClock.autoAdvance=false
        compose.runOnIdle {sheetDispatcher.dispatchOnBackStarted(progress(0f));sheetDispatcher.dispatchOnBackProgressed(progress(.72f))}
        compose.mainClock.advanceTimeByFrame()
        sameBounds(initial,sheetBounds())
        compose.runOnIdle {sheetDispatcher.dispatchOnBackCancelled()}
        compose.mainClock.advanceTimeBy(100)
        sameBounds(initial,sheetBounds())
        compose.runOnIdle {assertTrue(shown.value);assertEquals(0,dismisses)}
        compose.runOnIdle {
            sheetDispatcher.dispatchOnBackStarted(progress(0f))
            sheetDispatcher.dispatchOnBackProgressed(progress(.9f))
            sheetDispatcher.onBackPressed()
        }
        compose.runOnIdle {assertEquals(0,dismisses)}
        compose.onNodeWithTag("app-sheet-surface").assertExists()
        compose.mainClock.advanceTimeBy(1000)
        compose.onNodeWithTag("app-sheet-surface").assertDoesNotExist()
        compose.runOnIdle {assertEquals(1,dismisses)}
    }

    @Test fun disabledSheetDraggingStillAllowsBackAfterItsChildStepHandlesFirst() {
        val shown=mutableStateOf(true)
        val child=mutableStateOf(true)
        var dismisses=0
        lateinit var dispatcher: OnBackPressedDispatcher
        compose.setContent {MaterialTheme {
            if(shown.value) AppModalBottomSheet(onDismissRequest={dismisses++;shown.value=false},sheetGesturesEnabled=false) {
                val owner=LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
                SideEffect {dispatcher=owner}
                BackHandler(child.value) {child.value=false}
                Box(Modifier.fillMaxWidth().height(220.dp)) {Text(if(child.value) "新建步骤" else "歌单选择")}
            }
        }}
        compose.onNodeWithText("新建步骤").assertIsDisplayed()
        compose.runOnIdle {dispatcher.onBackPressed()}
        compose.onNodeWithText("歌单选择").assertIsDisplayed()
        compose.runOnIdle {assertEquals(0,dismisses);assertTrue(shown.value)}
        compose.runOnIdle {dispatcher.onBackPressed()}
        compose.onNodeWithTag("app-sheet-surface").assertDoesNotExist()
        compose.runOnIdle {assertEquals(1,dismisses)}
    }

    @Test fun sheetBackgroundMatchesHomeInBothModesWhileOpen() {
        val night=mutableStateOf(false)
        compose.setContent {MaterialTheme(colorScheme=appColorScheme(3,night.value)) {
            AppModalBottomSheet(onDismissRequest={}) {
                Box(Modifier.fillMaxWidth().height(220.dp)) {Text("背景检查")}
            }
        }}
        matches(appColorScheme(3,false).background,"app-sheet-surface")
        compose.runOnIdle {night.value=true}
        matches(appColorScheme(3,true).background,"app-sheet-surface")
    }

    @Test fun alertBackgroundMatchesHomeInBothModesWhileOpen() {
        val night=mutableStateOf(false)
        compose.setContent {MaterialTheme(colorScheme=appColorScheme(4,night.value)) {
            AppAlertDialog(onDismissRequest={},modifier=Modifier.testTag("theme-alert"),title={Text("删除歌单？")},
                text={Text("原音乐文件会保留")},confirmButton={TextButton(onClick={}) {Text("确定")}})
        }}
        matches(appColorScheme(4,false).background,"theme-alert")
        compose.runOnIdle {night.value=true}
        matches(appColorScheme(4,true).background,"theme-alert")
    }
}
