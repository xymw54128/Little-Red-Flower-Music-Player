package com.xiaohonghua.music

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File

/** Bounded, shared thumbnails; evicted bitmaps can still be displayed, so never recycle them. */
internal object ArtworkCache {
    private data class Key(val path: String,val revision: String,val pixels: Int)
    private data class Entry(val bitmap: Bitmap,val image: ImageBitmap=bitmap.asImageBitmap())
    private val budget=(Runtime.getRuntime().maxMemory()/16).coerceIn(4L*1024*1024,24L*1024*1024).toInt()
    private val cache=object : LruCache<Key,Entry>(budget) {
        override fun sizeOf(key: Key,value: Entry): Int = value.bitmap.allocationByteCount
    }
    private val decoders=Semaphore(2)

    internal fun pixels(targetPixels: Int): Int {
        val target=targetPixels.coerceIn(64,2048)
        var size=64
        while(size < target) size*=2
        return size
    }

    fun cached(path: String,targetPixels: Int,revision: String): ImageBitmap? =
        if(path.isBlank() || revision.isBlank()) null else cache.get(Key(path,revision,pixels(targetPixels)))?.image

    suspend fun load(path: String,targetPixels: Int,revision: String=""): ImageBitmap? = withContext(Dispatchers.IO) {
        if(path.isBlank()) return@withContext null
        // Managed song covers supply a content hash; imported playlist photos have UUID paths.
        // Only legacy callers need a filesystem revision, always read on the IO dispatcher.
        val version=revision.ifEmpty {
            runCatching {File(path).let {it.lastModified().toString()+"|"+it.length()}}.getOrElse {return@withContext null}
        }
        val key=Key(path,version,pixels(targetPixels))
        cache.get(key)?.let {return@withContext it.image}
        decoders.withPermit {
            cache.get(key)?.let {return@withPermit it.image}
            currentCoroutineContext().ensureActive()
            try {
                val bounds=BitmapFactory.Options().apply {inJustDecodeBounds=true}
                BitmapFactory.decodeFile(path,bounds)
                if(bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withPermit null
                val options=BitmapFactory.Options().apply {
                    inPreferredConfig=Bitmap.Config.ARGB_8888
                    inSampleSize=1
                    while(bounds.outWidth/inSampleSize > key.pixels || bounds.outHeight/inSampleSize > key.pixels) inSampleSize*=2
                }
                val bitmap=BitmapFactory.decodeFile(path,options) ?: return@withPermit null
                currentCoroutineContext().ensureActive()
                val entry=Entry(bitmap)
                cache.put(key,entry)
                entry.image
            } catch(e: CancellationException) {throw e}
            catch(e: Exception) {null}
            catch(e: OutOfMemoryError) {null}
        }
    }
}

@Composable internal fun rememberCachedArtwork(path: String,targetPixels: Int,revision: String=""): ImageBitmap? {
    if(path.isBlank()) return null
    val pixels=ArtworkCache.pixels(targetPixels)
    // produceState alone retains its previous value when keys change. Restart its state as
    // well as the producer, so a recycled row never temporarily shows another song's cover.
    return key(path,revision,pixels) {
        produceState(ArtworkCache.cached(path,pixels,revision)) {value=ArtworkCache.load(path,pixels,revision)}.value
    }
}
