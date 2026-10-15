package com.xiaohonghua.music

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Only synthetic images in the app cache directory are used. */
@RunWith(AndroidJUnit4::class)
class ArtworkCacheTest {
    private fun image(file: File,color: Int) {
        val bitmap=Bitmap.createBitmap(1024,768,Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(color)
            file.outputStream().use {assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,it))}
        } finally {bitmap.recycle()}
    }

    @Test fun thumbnailsAreSampledAndAnEquivalentSizeReusesTheBitmap() = runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val file=File.createTempFile("art-cache-size-",".png",context.cacheDir)
        try {
            image(file,Color.RED)
            val small=ArtworkCache.load(file.absolutePath,64,"size")!!
            val same=ArtworkCache.load(file.absolutePath,60,"size")
            val large=ArtworkCache.load(file.absolutePath,256,"size")!!
            assertTrue(small.width <= 64 && small.height <= 64)
            assertTrue(large.width <= 256 && large.height <= 256)
            assertTrue(large.width > small.width)
            assertSame(small,same)
        } finally {file.delete()}
    }

    @Test fun explicitRevisionRefreshesAReplacedImageWithoutFileTimestampChanges() = runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val file=File.createTempFile("art-cache-revision-",".png",context.cacheDir)
        try {
            image(file,Color.RED)
            val stamp=file.lastModified()
            val old=ArtworkCache.load(file.absolutePath,64,"first")!!
            image(file,Color.BLUE)
            assertTrue(file.setLastModified(stamp))
            val updated=ArtworkCache.load(file.absolutePath,64,"second")!!
            assertNotSame(old,updated)
            assertEquals(Color.RED,old.asAndroidBitmap().getPixel(0,0))
            assertEquals(Color.BLUE,updated.asAndroidBitmap().getPixel(0,0))
            assertSame(updated,ArtworkCache.load(file.absolutePath,64,"second"))
        } finally {file.delete()}
    }

    @Test fun failedImagesRemainNullAndAreNotCachedAcrossRetries() = runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val file=File.createTempFile("art-cache-invalid-",".png",context.cacheDir)
        try {
            assertNull(ArtworkCache.load("",64))
            assertNull(ArtworkCache.load(file.absolutePath+".missing",64))
            file.writeText("not an image")
            assertNull(ArtworkCache.load(file.absolutePath,64,"retry"))
            image(file,Color.GREEN)
            assertEquals(Color.GREEN,ArtworkCache.load(file.absolutePath,64,"retry")!!.asAndroidBitmap().getPixel(0,0))
        } finally {file.delete()}
    }
}
