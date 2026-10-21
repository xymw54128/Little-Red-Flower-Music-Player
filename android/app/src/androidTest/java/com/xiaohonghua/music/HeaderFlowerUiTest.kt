package com.xiaohonghua.music

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** A mutable animation scale exercises the live Compose setting without changing device settings. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class HeaderFlowerUiTest {
    private val durationScale=object: MotionDurationScale {
        var factor by mutableFloatStateOf(1f)
        override val scaleFactor: Float get()=factor
    }
    @get:Rule val compose=createComposeRule(effectContext=durationScale)

    @Before fun emulatorOnly() {
        check(Build.HARDWARE in listOf("ranchu","goldfish")) {"顶部小红花动画测试只能在模拟器中执行"}
        compose.mainClock.autoAdvance=false
    }
    private fun pixels(): IntArray {
        val image=compose.onNodeWithTag("playlists-header-flower").captureToImage()
        val map=image.toPixelMap()
        return IntArray(image.width*image.height) {map[it%image.width,it/image.width].toArgb()}
    }

    @Test fun liveRemovalOfMotionSettlesCurrentAndSubsequentHeaderTaps() {
        compose.setContent {MaterialTheme {
            Box(Modifier.background(Color.White)) {InteractiveHeaderFlower("playlists")}
        }}
        val button=compose.onNodeWithTag("playlists-header-flower")
        val resting=pixels()
        button.performClick()
        compose.mainClock.advanceTimeBy(400)
        assertFalse(resting.contentEquals(pixels()))
        compose.runOnIdle {durationScale.factor=2f}
        compose.mainClock.advanceTimeBy(240)
        assertFalse(resting.contentEquals(pixels()))

        compose.runOnIdle {durationScale.factor=0f}
        compose.mainClock.advanceTimeByFrame()
        assertArrayEquals(resting,pixels())
        button.performClick()
        compose.mainClock.advanceTimeBy(640)
        assertArrayEquals(resting,pixels())

        compose.runOnIdle {durationScale.factor=1f}
        compose.mainClock.advanceTimeBy(640)
        assertArrayEquals(resting,pixels())
        button.performClick()
        compose.mainClock.advanceTimeBy(400)
        assertFalse(resting.contentEquals(pixels()))
    }
}
