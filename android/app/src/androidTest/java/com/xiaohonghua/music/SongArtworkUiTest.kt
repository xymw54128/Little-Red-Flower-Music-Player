package com.xiaohonghua.music

import android.graphics.Bitmap
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Creates a private synthetic PNG; never reads or modifies the user music files. */
@RunWith(AndroidJUnit4::class)
class SongArtworkUiTest {
    @get:Rule val compose=createComposeRule()
    private val song=Song("cover","content://music/cover","cover.flac","春日小花",artist="花",album="四季")

    @Test fun absentArtworkStillOpensAccessiblePreviewAndClosesWithoutChangingSong() {
        compose.setContent {MaterialTheme {SongArtworkPreview(song)}}
        compose.onNodeWithTag("song-info-cover").assertHasClickAction().performClick()
        compose.onNodeWithTag("artwork-viewer").assertIsDisplayed()
        compose.onNodeWithText("春日小花").assertIsDisplayed()
        compose.onNodeWithText("暂无歌曲封面").assertIsDisplayed()
        compose.onNodeWithContentDescription("关闭封面预览").assertHasClickAction().performClick()
        compose.onNodeWithTag("artwork-viewer").assertDoesNotExist()
        compose.onNodeWithTag("song-info-cover").assertIsDisplayed()
        assertEquals("content://music/cover",song.uri)
    }

    @Test fun actualImageCanDoubleTapZoomResetAndReopenAtFitSize() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val file=File.createTempFile("flower-artwork-",".png",context.cacheDir)
        try {
            val bitmap=Bitmap.createBitmap(400,200,Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(android.graphics.Color.rgb(206,60,76))
            file.outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
            bitmap.recycle()
            compose.setContent {MaterialTheme {SongArtworkPreview(song.copy(artwork=file.absolutePath))}}
            compose.onNodeWithTag("song-info-cover").performClick()
            compose.waitUntil(5_000) {compose.onAllNodesWithTag("artwork-image").fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithTag("artwork-viewport").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,"100%"))
            compose.onNodeWithTag("artwork-viewport").performTouchInput {doubleClick()}
            compose.onNodeWithTag("artwork-viewport").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,"200%"))
            compose.onNodeWithTag("artwork-viewport").performTouchInput {swipeLeft(durationMillis=350)}
            compose.onNodeWithTag("artwork-viewer").assertIsDisplayed()
            compose.onNodeWithTag("artwork-viewport").performTouchInput {doubleClick()}
            compose.onNodeWithTag("artwork-viewport").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,"100%"))
            compose.onNodeWithTag("artwork-close").performClick()
            compose.onNodeWithTag("song-info-cover").performClick()
            compose.onNodeWithTag("artwork-viewport").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,"100%"))
            compose.onNodeWithTag("artwork-close").performClick()
            compose.onNodeWithTag("artwork-viewer").assertDoesNotExist()
        } finally {file.delete()}
    }
}
