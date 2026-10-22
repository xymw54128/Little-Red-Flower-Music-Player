package com.xiaohonghua.music

import android.app.Application
import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

/** Preview tests use an ordinary clock, never audio files or a stored library. */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class Lyric118UiTest {
    @get:Rule val compose=createComposeRule(effectContext=object: MotionDurationScale {
        override val scaleFactor: Float get()=0f
    })
    private var clock=0L
    private var reads=0
    private val playback=mutableStateOf(PlaybackState(currentId="preview-a",playing=true))
    private val visible=mutableStateOf(true)
    private val textNode get()=compose.onNodeWithTag("player-lyric-preview-text",useUnmergedTree=true)

    private fun preview(lines: List<LyricLine>) {
        compose.mainClock.autoAdvance=false
        compose.setContent {MaterialTheme(colorScheme=appColorScheme(0,false)) {
            Box(Modifier.fillMaxSize()) {
                PlayerLyricPreview(lines,Settings(),playback.value,visible.value,position={reads++;clock})
            }
        }}
        frames()
    }
    private fun frames() {repeat(3) {compose.mainClock.advanceTimeByFrame()};compose.waitForIdle()}
    private fun sample(position: Long, expected: String) {
        // No snapshot changes: only the component's frame read can see this value.
        compose.runOnIdle {clock=position}
        frames()
        textNode.assertTextEquals(expected)
    }

    @Test fun previewUsesLiveFramesAndNeverAnticipatesOffsetAdjustedTimestamps() {
        preview(Lrc.parse("[offset:+50]\n[00:00.00]第一行\n[00:00.05]第二行\n[00:00.15]第三行"))
        sample(49,"点击封面或左滑查看歌词")
        sample(50,"第一行")
        sample(99,"第一行")
        sample(100,"第二行")
        sample(199,"第二行")
        sample(200,"第三行")
        compose.runOnIdle {
            assertEquals("The sampled PlaybackState clock was never advanced",0L,playback.value.position)
        }
    }

    @Test fun pausedSeeksAndAnotherSongWithTheSameLyricsImmediatelyResample() {
        playback.value=playback.value.copy(playing=false)
        val lines=Lrc.parse("[00:00.00]第一行\n[00:00.10]第二行\n[00:00.20]第三行")
        preview(lines)
        textNode.assertTextEquals("第一行")
        compose.runOnIdle {
            clock=199
            playback.value=playback.value.copy(seekRevision=1)
        }
        frames()
        textNode.assertTextEquals("第二行")
        compose.runOnIdle {
            clock=250
            playback.value=playback.value.copy(seekRevision=2)
        }
        frames()
        textNode.assertTextEquals("第三行")
        compose.runOnIdle {
            clock=0
            playback.value=playback.value.copy(currentId="preview-b",seekRevision=0)
        }
        frames()
        textNode.assertTextEquals("第一行")
    }

    @Test fun hiddenPreviewStopsContinuousFrameReadsAndSamplesWhenShown() {
        preview(Lrc.parse("[00:00.00]第一行\n[00:00.10]第二行\n[00:00.20]第三行"))
        compose.runOnIdle {visible.value=false}
        frames()
        compose.runOnIdle {reads=0;clock=250}
        compose.mainClock.advanceTimeBy(160)
        compose.waitForIdle()
        compose.runOnIdle {
            // Allow a low-rate fallback read, but never one read per hidden frame.
            assertTrue("Hidden preview kept polling on every display frame: $reads",reads <= 4)
            visible.value=true
        }
        frames()
        textNode.assertTextEquals("第三行")
    }

    @Test fun recycledInactiveRowsKeepTheirColorOutsideTheViewportFeather() {
        // Only this test creates a MusicModel. It must never inspect a real
        // phone's library or connect to an existing real-phone media session.
        check(Build.HARDWARE in listOf("ranchu","goldfish")) {"歌词像素回归只能在模拟器中执行"}
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val store=ViewModelStore()
        lateinit var model: MusicModel
        instrumentation.runOnMainSync {
            val application=instrumentation.targetContext.applicationContext as Application
            model=ViewModelProvider(store,ViewModelProvider.AndroidViewModelFactory(application))[MusicModel::class.java]
        }
        try {
            val song=Song("lyric-color-118","content://example/lyric-color-118","color.flac","歌词颜色回归",
                duration=800_000,lyrics=(0 until 80).joinToString("\n") {index ->
                    "[%02d:%02d.00]同一行歌词".format(Locale.ROOT,index*10/60,index*10%60)
                })
            val scheme=appColorScheme(0,false)
            compose.setContent {MaterialTheme(colorScheme=scheme) {
                PlayerScreen(song,LibraryState(songs=listOf(song)),PlaybackState(currentId=song.id),model,
                    onBack={},onQueue={},onAdd={})
            }}
            compose.onNodeWithTag("player-page-lyrics").performClick()
            val viewport=compose.onNodeWithTag("lyrics-list").assertIsDisplayed()
            val expected=scheme.onSurfaceVariant.copy(alpha=.6f).compositeOver(scheme.background)
            for(index in listOf(10,50,72,20,60,12)) {
                viewport.performScrollToIndex(index)
                val node=compose.onNodeWithTag("lyric-$index",useUnmergedTree=true).assertIsDisplayed()
                val bounds=node.getUnclippedBoundsInRoot()
                val viewBounds=viewport.getUnclippedBoundsInRoot()
                assertTrue("Test row entered the top feather",bounds.top.value >= viewBounds.top.value+56f)
                assertTrue("Test row entered the bottom feather",bounds.bottom.value <= viewBounds.bottom.value-56f)
                val pixels=node.captureToImage().toPixelMap()
                var darkest=Color.White
                for(y in 0 until pixels.height) for(x in 0 until pixels.width) {
                    val color=pixels[x,y]
                    if(color.luminance() < darkest.luminance()) darkest=color
                }
                assertEquals("Recycled row $index red channel became pale",expected.red,darkest.red,.04f)
                assertEquals("Recycled row $index green channel became pale",expected.green,darkest.green,.04f)
                assertEquals("Recycled row $index blue channel became pale",expected.blue,darkest.blue,.04f)
            }
        } finally {instrumentation.runOnMainSync {store.clear()}}
    }
}
