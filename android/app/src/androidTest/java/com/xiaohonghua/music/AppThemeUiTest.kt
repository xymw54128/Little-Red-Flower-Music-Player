package com.xiaohonghua.music

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.*
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppThemeUiTest {
    @get:Rule val compose=createComposeRule()
    private fun menuBackground(): Color {
        val pixels=compose.onNodeWithTag("theme-popup").captureToImage().toPixelMap()
        return pixels[pixels.width/2,4]
    }
    private fun matches(expected: Color,actual: Color) {
        assertEquals(expected.red,actual.red,.025f)
        assertEquals(expected.green,actual.green,.025f)
        assertEquals(expected.blue,actual.blue,.025f)
    }

    @Test fun openSongMenuKeepsTheHomeBackgroundAcrossThemeChanges() {
        val theme=mutableStateOf(0)
        compose.setContent {MaterialTheme(colorScheme=appColorScheme(theme.value,false)) {
            Box {AppDropdownMenu(true,{},modifier=Modifier.testTag("theme-popup")) {
                DropdownMenuItem(text={Text("下一首播放")},onClick={})
            }}
        }}
        matches(appColorScheme(0,false).background,menuBackground())
        compose.runOnIdle {theme.value=1}
        matches(appColorScheme(1,false).background,menuBackground())
    }

    @Test fun popupUsesTheNightHomeBackgroundWithoutATintedContainer() {
        compose.setContent {MaterialTheme(colorScheme=appColorScheme(2,true)) {
            Box {AppDropdownMenu(true,{},modifier=Modifier.testTag("theme-popup")) {
                DropdownMenuItem(text={Text("歌曲信息")},onClick={})
            }}
        }}
        matches(appColorScheme(2,true).background,menuBackground())
    }
}
