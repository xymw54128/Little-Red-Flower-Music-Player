package com.xiaohonghua.music

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.UUID
import java.util.concurrent.Executor

data class PlaybackState(val connected: Boolean = false, val currentId: String? = null, val queue: List<String> = emptyList(),
    val playing: Boolean = false, val position: Long = 0, val duration: Long = 0, val mode: Int = 0, val error: String? = null,
    val speed: Float = 1f, val seekRevision: Long = 0)

class MusicModel(application: Application) : AndroidViewModel(application) {
    val repository = MusicRepository.get(application)
    val library = repository.state
    private val mutablePlayback = MutableStateFlow(PlaybackState())
    val playback = mutablePlayback.asStateFlow()
    private var controller: MediaController? = null
    private var seekRevision = 0L
    private val future: ListenableFuture<MediaController>
    init {
        future = MediaController.Builder(application,SessionToken(application,ComponentName(application,PlaybackService::class.java))).buildAsync()
        future.addListener({
            try {
                controller = future.get().also { it.addListener(object : Player.Listener {
                    override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) { seekRevision++ }
                    override fun onEvents(player: Player, events: Player.Events) { updatePlayback(events.contains(Player.EVENT_TIMELINE_CHANGED)) }
                }) }
                updatePlayback(queueChanged=true)
            } catch (e: Exception) { mutablePlayback.value=PlaybackState(error="播放器连接失败：${e.cause?.message ?: e.message}") }
        },Executor { Handler(Looper.getMainLooper()).post(it) })
        viewModelScope.launch { while(isActive) { updatePlayback(); delay(100) } }
        viewModelScope.launch {
            var previous: List<Song>? = null
            library.collect { state ->
                if (previous != null && previous != state.songs) controller?.let { player ->
                    val byId = state.songs.associateBy { it.id }
                    val oldById = previous!!.associateBy {it.id}
                    for (i in player.mediaItemCount-1 downTo 0) {
                        val song = byId[player.getMediaItemAt(i).mediaId]
                        if(song == null) player.removeMediaItem(i)
                        else if(oldById[song.id] != song) player.replaceMediaItem(i,song.mediaItem())
                    }
                }
                previous=state.songs
            }
        }
    }
    private fun updatePlayback(queueChanged: Boolean = false) {
        val player = controller ?: return
        // Position ticks must not walk a potentially thousands-song timeline.
        val queue=if(queueChanged) (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId } else mutablePlayback.value.queue
        mutablePlayback.value=PlaybackState(true,player.currentMediaItem?.mediaId,queue,
            player.isPlaying,player.currentPosition.coerceAtLeast(0),player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0,
            if(player.shuffleModeEnabled) 3 else when(player.repeatMode) { Player.REPEAT_MODE_ALL -> 1; Player.REPEAT_MODE_ONE -> 2; else -> 0 },player.playerError?.let { "此文件暂时无法播放：${it.errorCodeName}" },
            player.playbackParameters.speed,seekRevision)
    }
    // Called on the UI thread's frame clock; no per-frame StateFlow emissions or timeline scans.
    fun playbackPosition(): Long = controller?.currentPosition?.coerceAtLeast(0) ?: playback.value.position
    fun play(songs: List<Song>, id: String? = null) {
        val playable=songs.filter { it.available }
        if(playable.isEmpty()) return
        controller?.apply { setMediaItems(playable.map { it.mediaItem() },playable.indexOfFirst { it.id == id }.coerceAtLeast(0),0); prepare(); play() }
    }
    fun toggle() { controller?.let { if(it.isPlaying) it.pause() else { if(it.playbackState == Player.STATE_ENDED) it.seekToDefaultPosition(); it.prepare(); it.play() } } }
    fun previous() { controller?.apply {if(hasPreviousMediaItem()) seekToPreviousMediaItem() else seekTo(0)} }
    fun next() { controller?.seekToNext() }
    fun seek(position: Long) { controller?.seekTo(position.coerceAtLeast(0)); updatePlayback() }
    fun mode() { controller?.apply {
        when(playback.value.mode) {
            0 -> repeatMode=Player.REPEAT_MODE_ALL
            1 -> repeatMode=Player.REPEAT_MODE_ONE
            2 -> { repeatMode=Player.REPEAT_MODE_ALL; shuffleModeEnabled=true }
            else -> { shuffleModeEnabled=false; repeatMode=Player.REPEAT_MODE_OFF }
        }
    } }
    fun queueMove(from: Int, to: Int) { controller?.moveMediaItem(from,to) }
    fun queueRemove(index: Int) { controller?.removeMediaItem(index) }
    fun queuePlay(index: Int) { controller?.apply { seekToDefaultPosition(index); play() } }
    fun stopForRestore() { controller?.apply { stop(); clearMediaItems() } }
    fun importLyrics(id: String, uri: Uri, onResult: (String) -> Unit) = viewModelScope.launch {
        val result=runCatching { withContext(Dispatchers.IO) { TagReader(getApplication()).lyricText(uri) } }
        result.fold({ repository.lyrics(id,it); onResult("歌词已导入") },{ onResult(it.message ?: "歌词读取失败") })
    }
    fun importBackground(uri: Uri, onResult: (String) -> Unit) = viewModelScope.launch {
        val result=runCatching { withContext(Dispatchers.IO) {
            val context=getApplication<Application>()
            val type=context.contentResolver.getType(uri)
            require(type in listOf("image/jpeg","image/png","image/webp")) { "请选择 JPG、PNG 或 WebP 图片" }
            val bytes=context.contentResolver.openInputStream(uri)!!.use { input -> input.readLimited(24*1024*1024+1) }
            require(bytes.size <= 24*1024*1024) { "图片不能超过 24 MB" }
            val options=android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds=true }
            android.graphics.BitmapFactory.decodeByteArray(bytes,0,bytes.size,options)
            require(options.outWidth > 0 && options.outHeight > 0) { "图片格式无效" }
            val file=File(context.filesDir,"player-background.img")
            file.writeBytes(bytes)
        } }
        result.fold({ repository.settings(library.value.settings.copy(background="photo")); onResult("播放背景已更新") },{ onResult(it.message ?: "图片读取失败") })
    }
    fun importPlaylistCover(id: String, uri: Uri, onResult: (String) -> Unit) = viewModelScope.launch {
        val result=runCatching { withContext(Dispatchers.IO) {
            val context=getApplication<Application>()
            val bytes=context.contentResolver.openInputStream(uri)?.use {it.readLimited(24*1024*1024+1)} ?: error("照片无法读取")
            require(bytes.size <= 24*1024*1024) {"照片不能超过 24 MB"}
            val bitmap=if(android.os.Build.VERSION.SDK_INT >= 28) {
                // ImageDecoder applies EXIF orientation before producing the thumbnail.
                android.graphics.ImageDecoder.decodeBitmap(android.graphics.ImageDecoder.createSource(java.nio.ByteBuffer.wrap(bytes))) {decoder,info,_ ->
                    val ratio=minOf(1f,1024f/maxOf(info.size.width,info.size.height))
                    decoder.setTargetSize((info.size.width*ratio).toInt().coerceAtLeast(1),(info.size.height*ratio).toInt().coerceAtLeast(1))
                    decoder.allocator=android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
                }
            } else {
                val options=android.graphics.BitmapFactory.Options().apply {inJustDecodeBounds=true}
                android.graphics.BitmapFactory.decodeByteArray(bytes,0,bytes.size,options)
                require(options.outWidth > 0 && options.outHeight > 0) {"照片格式无效"}
                options.inJustDecodeBounds=false
                options.inSampleSize=1
                while(maxOf(options.outWidth,options.outHeight)/options.inSampleSize > 1024) options.inSampleSize*=2
                val decoded=android.graphics.BitmapFactory.decodeByteArray(bytes,0,bytes.size,options) ?: error("照片格式无效")
                val orientation=runCatching {androidx.exifinterface.media.ExifInterface(bytes.inputStream()).getAttributeInt(androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION,1)}.getOrDefault(1)
                val matrix=android.graphics.Matrix().apply {when(orientation) {
                    2 -> setScale(-1f,1f); 3 -> setRotate(180f); 4 -> setScale(1f,-1f)
                    5 -> {setRotate(90f);postScale(-1f,1f)}; 6 -> setRotate(90f)
                    7 -> {setRotate(270f);postScale(-1f,1f)}; 8 -> setRotate(270f)
                }}
                if(matrix.isIdentity) decoded else android.graphics.Bitmap.createBitmap(decoded,0,0,decoded.width,decoded.height,matrix,true).also {decoded.recycle()}
            }
            val folder=File(context.filesDir,"playlist-covers").apply {mkdirs()}
            val name="${UUID.randomUUID()}.jpg"
            val file=File(folder,name)
            try {file.outputStream().use {check(bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG,88,it)) {"照片保存失败"}}}
            finally {bitmap.recycle()}
            name
        } }
        result.fold({name ->
            val current=library.value.playlists.find {it.id == id}
            if(current != null && repository.playlistCover(id,current.coverPreset,name)) onResult("歌单封面已更新")
            else onResult("此歌单已删除，照片未应用")
        },{onResult("照片导入失败：${it.message ?: "格式不支持"}")})
    }
    fun exportBackup(uri: Uri, onResult: (String)->Unit) = viewModelScope.launch {
        val text=repository.backup()
        val result=runCatching { withContext(Dispatchers.IO) { getApplication<Application>().contentResolver.openOutputStream(uri,"wt")!!.bufferedWriter().use { it.write(text) } } }
        onResult(if(result.isSuccess) "备份已导出，音乐、背景与歌单照片请另行保存" else "导出失败：${result.exceptionOrNull()?.message}")
    }
    fun restoreBackup(uri: Uri, onResult: (String)->Unit) = viewModelScope.launch {
        val result=runCatching {
            val text=withContext(Dispatchers.IO) { getApplication<Application>().contentResolver.openInputStream(uri)!!.use { stream ->
                val bytes=stream.readLimited(64*1024*1024+1); require(bytes.size <= 64*1024*1024) { "备份文件过大" }; String(bytes,Charsets.UTF_8)
            } }
            repository.restore(text)
            stopForRestore()
        }
        onResult(if(result.isSuccess) "备份已恢复，请重新授权原音乐文件夹" else "恢复失败，原数据保留：${result.exceptionOrNull()?.message}")
    }
    override fun onCleared() { MediaController.releaseFuture(future); super.onCleared() }
}
