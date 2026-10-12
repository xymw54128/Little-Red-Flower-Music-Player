package com.xiaohonghua.music

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** NativePaint uses one Typeface: exercise the same face rather than Compose-only fallback. */
@RunWith(AndroidJUnit4::class)
class LyricFontRenderingTest {
    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val resources = listOf(R.font.lxgw_wenkai, R.font.flower_serif,
        R.font.flower_cute, R.font.flower_handdrawn)

    @Test fun everyBundledFaceRendersChineseLatinJapaneseKoreanAndDigits() {
        for (resource in resources) {
            val face = requireNotNull(ResourcesCompat.getFont(context, resource))
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = face; textSize = 48f }
            for (character in "小红花春夏秋冬FlowerABC0123456789あいうえおアイウエオゕゖ꽃피는노래") {
                assertTrue("Resource $resource missing $character", paint.hasGlyph(character.toString()))
            }
            assertTrue(paint.measureText("音乐 Flower あいう 꽃피는노래 123") > 100f)
        }
    }

    @Test fun changingFontChangesNativeRenderingInEachLanguageAndDigits() {
        val faces = listOf(Typeface.create("sans-serif", Typeface.NORMAL)) +
            resources.map { requireNotNull(ResourcesCompat.getFont(context, it)) }
        for (sample in listOf("春风桃李", "Flower Love", "ひらがなアイウ", "꽃피는노래", "0123456789")) {
            val images = faces.map { face ->
                val bitmap = Bitmap.createBitmap(1000, 120, Bitmap.Config.ARGB_8888)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    typeface = face; textSize = 64f; color = Color.WHITE
                }
                Canvas(bitmap).drawText(sample, 16f, 90f, paint)
                val pixels = IntArray(bitmap.width * bitmap.height)
                bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                bitmap.recycle()
                pixels.contentHashCode()
            }
            assertEquals("Every selectable face should differ for $sample", faces.size, images.distinct().size)
        }
    }
}
