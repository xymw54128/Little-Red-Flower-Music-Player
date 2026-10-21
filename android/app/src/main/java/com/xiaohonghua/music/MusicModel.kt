package com.xiaohonghua.music

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.Bundle
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.session.SessionCommand
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.UUID
import java.util.concurrent.Executor

data class PlaybackState(val connected: Boolean = false, val currentId: String? = null, val queue: List<String> = emptyList(),
    val playing: Boolean = false, val position: Long = 0, val duration: Long = 0, val mode: Int = 0, val error: String? = null,
    val speed: Float = 1f, val seekRevision: Long = 0, val queueSource: String? = null, val playOrder: List<String> = emptyList())

/** A controller timeline carries the service shuffle traversal, unlike physical queue indices. */
internal fun Player.queuePlayOrder(): List<String> {
    val timeline=currentTimeline
    if(timeline.isEmpty) return (0 until mediaItemCount).map {getMediaItemAt(it).mediaId}
    return buildList {
        var index=timeline.getFirstWindowIndex(shuffleModeEnabled)
        while(index in 0 until mediaItemCount && size < timeline.windowCount) {
            add(getMediaItemAt(index).mediaId)
            index=timeline.getNextWindowIndex(index,Player.REPEAT_MODE_OFF,shuffleModeEnabled)
        }
    }
}

class MusicModel(application: Application) : AndroidViewModel(application) {
    val repository = MusicRepository.get(application)
    val library = repository.state
    private val mutablePlayback = MutableStateFlow(PlaybackState())
    val playback = mutablePlayback.asStateFlow()
    private var controller: MediaController? = null
    private var seekRevision = 0L
    private val playbackHandler=Handler(Looper.getMainLooper())
    private val refreshQueue=Runnable {updatePlayback(queueChanged=true)}
    private var sortJob: Job?=null
    private var queueOrderJob: Job?=null
    private var modeRevision=0L
    private val future: ListenableFuture<MediaController>
    init {
        future = MediaController.Builder(application,SessionToken(application,ComponentName(application,PlaybackService::class.java))).buildAsync()
        future.addListener({
            try {
                controller = future.get().also { it.addListener(object : Player.Listener {
                    override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) { seekRevision++ }
                    override fun onEvents(player: Player, events: Player.Events) {
                        if(events.contains(Player.EVENT_TIMELINE_CHANGED)) {
                            playbackHandler.removeCallbacks(refreshQueue)
                            playbackHandler.postDelayed(refreshQueue,16)
                        } else updatePlayback()
                    }
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
                    val source=player.queueSource
                    fun removed(index: Int): Boolean {
                        val song=byId[player.getMediaItemAt(index).mediaId]
                        return song == null || (!song.available && song.id != player.currentMediaItem?.mediaId)
                    }
                    fun changed(index: Int): Boolean = byId[player.getMediaItemAt(index).mediaId]?.let {
                        it.available && oldById[it.id] != it
                    } == true
                    var i=player.mediaItemCount-1
                    while(i >= 0) {
                        if(removed(i)) {
                            val end=i+1
                            while(i >= 0 && removed(i)) i--
                            player.removeMediaItems(i+1,end)
                        } else if(changed(i)) {
                            val end=i+1
                            while(i >= 0 && !removed(i) && changed(i)) i--
                            // Same URI/configuration: Media3 updates metadata without replacing audio sources.
                            val items=(i+1 until end).map {byId.getValue(player.getMediaItemAt(it).mediaId).mediaItem(source)}
                            player.replaceMediaItems(i+1,end,items)
                        } else i--
                    }
                }
                previous=state.songs
            }
        }
    }
    private fun updatePlayback(queueChanged: Boolean = false) {
        val player = controller ?: return
        // Position ticks must not walk a potentially thousands-song timeline.
        val previous=mutablePlayback.value
        val queue=if(queueChanged) (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId } else previous.queue
        val shuffleChanged=player.shuffleModeEnabled != (previous.mode == 3)
        val playOrder=if(queueChanged || shuffleChanged) {if(player.shuffleModeEnabled) player.queuePlayOrder() else queue} else previous.playOrder
        mutablePlayback.value=PlaybackState(true,player.currentMediaItem?.mediaId,queue,
            player.isPlaying,player.currentPosition.coerceAtLeast(0),player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0,
            if(player.shuffleModeEnabled) 3 else when(player.repeatMode) { Player.REPEAT_MODE_ALL -> 1; Player.REPEAT_MODE_ONE -> 2; else -> 0 },player.playerError?.let { "此文件暂时无法播放：${it.errorCodeName}" },
            player.playbackParameters.speed,seekRevision,player.queueSource,playOrder)
    }
    // Called on the UI thread's frame clock; no per-frame StateFlow emissions or timeline scans.
    fun playbackPosition(): Long = controller?.currentPosition?.coerceAtLeast(0) ?: playback.value.position
    private fun cancelQueueOrderWork() {sortJob?.cancel();queueOrderJob?.cancel()}
    fun play(songs: List<Song>, id: String? = null, playlistId: String? = null) {
        val source=playlistId ?: LIBRARY_QUEUE_SOURCE
        // Favorites can retain outgoing rows while their UI sort worker prepares a new order.
        // Use current tags/membership/order, while keeping the clicked search/group subset.
        val playable=playbackSourceSongs(library.value,source,songs.mapTo(mutableSetOf()) {it.id})
        if(playable.isEmpty() || id != null && playable.none {it.id == id}) return
        cancelQueueOrderWork()
        controller?.apply {setMediaItems(playable.map {it.mediaItem(source)},playable.indexOfFirst {it.id == id}.coerceAtLeast(0),0);prepare();play()}
    }
    fun playNext(song: Song): Boolean = enqueue(song,QUEUE_NEXT_COMMAND)
    fun appendQueue(song: Song): Boolean = enqueue(song,QUEUE_LAST_COMMAND)
    private fun enqueue(song: Song,action: String): Boolean {
        val player=controller ?: return false
        if(!song.available || library.value.songs.none {it.id == song.id && it.available}) return false
        if(player.currentMediaItem?.mediaId == song.id) return true // The current song is an explicit queue no-op.
        cancelQueueOrderWork()
        player.sendCustomCommand(SessionCommand(action,Bundle.EMPTY),Bundle().apply {putString("songId",song.id)})
        return true
    }
    fun applySort(mode: SortMode, playlistId: String? = null) {
        if(repository.sort(mode,playlistId)) syncSourceOrder(playlistId)
    }
    fun customOrder(ids: List<String>, playlistId: String? = null) {
        if(repository.customOrder(ids,playlistId)) syncSourceOrder(playlistId)
    }
    private fun syncSourceOrder(playlistId: String?) {
        val source=playlistId ?: LIBRARY_QUEUE_SOURCE
        // Editing an inactive playlist must not cancel the active source's pending sync.
        if(controller?.queueSource != source) return
        val state=library.value
        val requestedModeRevision=modeRevision
        sortJob?.cancel()
        sortJob=viewModelScope.launch {
            val ids=withContext(Dispatchers.Default) {playbackSourceSongs(state,source).map {it.id}}
            reorderQueue(ids,source,exitShuffle=requestedModeRevision == modeRevision)
        }
    }
    private fun reorderQueue(ids: List<String>, source: String?,exitShuffle: Boolean=true) {
        val player=controller ?: return
        if(player.queueSource != source) return
        queueOrderJob?.cancel()
        val queue=(0 until player.mediaItemCount).map {player.getMediaItemAt(it).mediaId}
        val ordered=orderedQueueIds(queue,player.queueSource,ids,source)
        val moves=queueMovePlan(queue,ordered)
        queueOrderJob=viewModelScope.launch(start=CoroutineStart.UNDISPATCHED) {
            if(exitShuffle) player.shuffleModeEnabled=false
            val expected=queue.toMutableList()
            var frameStarted=System.nanoTime()
            moves.forEachIndexed {index,(from,to) ->
                ensureActive()
                player.moveMediaItem(from,to)
                expected.add(to,expected.removeAt(from))
                if(index < moves.lastIndex && System.nanoTime()-frameStarted >= 4_000_000) {
                    updatePlayback(queueChanged=true)
                    delay(1) // Let input/drawing run between costly native timeline updates.
                    if(player.queueSource != source || (0 until player.mediaItemCount).map {player.getMediaItemAt(it).mediaId} != expected) return@launch
                    frameStarted=System.nanoTime()
                }
            }
            updatePlayback(queueChanged=true)
        }
    }
    fun toggle() { controller?.let { if(it.isPlaying) it.pause() else { if(it.playbackState == Player.STATE_ENDED) it.seekToDefaultPosition(); it.prepare(); it.play() } } }
    fun previous() { controller?.apply {if(hasPreviousMediaItem()) seekToPreviousMediaItem() else seekTo(0)} }
    fun next() { controller?.seekToNext() }
    fun seek(position: Long) { controller?.seekTo(position.coerceAtLeast(0)); updatePlayback() }
    fun mode() { controller?.apply {
        modeRevision++
        // Read optimistic controller state so rapid mode taps do not reuse an old UI snapshot.
        val currentMode=if(shuffleModeEnabled) 3 else when(repeatMode) {Player.REPEAT_MODE_ALL -> 1;Player.REPEAT_MODE_ONE -> 2;else -> 0}
        when(currentMode) {
            0 -> repeatMode=Player.REPEAT_MODE_ALL
            1 -> repeatMode=Player.REPEAT_MODE_ONE
            2 -> { repeatMode=Player.REPEAT_MODE_ALL;shuffleModeEnabled=true }
            else -> { shuffleModeEnabled=false;repeatMode=Player.REPEAT_MODE_OFF }
        }
    } }
    fun queueMove(from: Int, to: Int) {cancelQueueOrderWork();controller?.apply {shuffleModeEnabled=false;moveMediaItem(from,to)}}
    fun queueOrder(ids: List<String>) {cancelQueueOrderWork();reorderQueue(ids,controller?.queueSource)}
    fun queueRemove(index: Int) {cancelQueueOrderWork();controller?.removeMediaItem(index)}
    fun queuePlay(index: Int) { controller?.apply { seekToDefaultPosition(index); play() } }
    fun queueRemove(id: String) { queuedIndex(id)?.let(::queueRemove) }
    fun queuePlay(id: String) { queuedIndex(id)?.let(::queuePlay) }
    private fun queuedIndex(id: String): Int? = controller?.let {player -> (0 until player.mediaItemCount).firstOrNull {player.getMediaItemAt(it).mediaId == id}}
    fun stopForRestore() {cancelQueueOrderWork();controller?.apply { stop(); clearMediaItems() }}
    fun importLyrics(id: String, uri: Uri, onResult: (String) -> Unit) = viewModelScope.launch {
        val result=runCatching { withContext(Dispatchers.IO) { TagReader(getApplication()).lyricText(uri) } }
        result.fold({ repository.lyrics(id,it); onResult("歌词已导入") },{ onResult(it.message ?: "歌词读取失败") })
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
        val result=runCatching { withContext(Dispatchers.IO) {
            val text=repository.backup()
            getApplication<Application>().contentResolver.openOutputStream(uri,"wt")!!.bufferedWriter().use { it.write(text) }
        } }
        onResult(if(result.isSuccess) "备份已导出，音乐与歌单照片请另行保存" else "导出失败：${result.exceptionOrNull()?.message}")
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
    override fun onCleared() { playbackHandler.removeCallbacks(refreshQueue);MediaController.releaseFuture(future);super.onCleared() }
}
