package com.xiaohonghua.music

import android.app.PendingIntent
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.Bundle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ShuffleOrder
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionError
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import org.json.JSONArray
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first

private const val QUEUE_SOURCE_KEY = "queueSource"
private const val QUEUE_SHUFFLE_ORDER_KEY = "queueShuffleOrder"
internal const val QUEUE_NEXT_COMMAND = "com.xiaohonghua.music.QUEUE_NEXT"
internal const val QUEUE_LAST_COMMAND = "com.xiaohonghua.music.QUEUE_LAST"
val Player.queueSource: String? get() = if(mediaItemCount == 0) null else getMediaItemAt(0).mediaMetadata.extras?.getString(QUEUE_SOURCE_KEY)

fun Song.mediaItem(source: String? = null): MediaItem = MediaItem.Builder().setMediaId(id).setUri(uri).setMediaMetadata(
    MediaMetadata.Builder().setTitle(title).setArtist(artist).setAlbumTitle(album)
        .setExtras(source?.let {Bundle().apply {putString(QUEUE_SOURCE_KEY,it)}})
        .setArtworkUri(artwork.takeIf { it.isNotBlank() }?.let { android.net.Uri.fromFile(java.io.File(it)).buildUpon().appendQueryParameter("version",artworkRevision).build() }).build()
).build()

/** Update the native queue without setMediaItems: preserve position, pause and modes. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal fun placeQueueSong(player: ExoPlayer,song: Song,placement: QueuePlacement): Boolean {
    if(!song.available) return false
    val oldIds=(0 until player.mediaItemCount).map {player.getMediaItemAt(it).mediaId}
    val currentId=player.currentMediaItem?.mediaId
    if(song.id == currentId) return true // Never restart or displace the audible entry.
    val shuffleIds=buildList {
        val order=player.shuffleOrder
        var index=order.firstIndex
        repeat(oldIds.size) {
            if(index in oldIds.indices) {add(oldIds[index]);index=order.getNextIndex(index)}
        }
    }
    val wasEmpty=oldIds.isEmpty()
    val existing=oldIds.indexOf(song.id)
    // Defensive deduplication also handles queues restored from earlier versions.
    oldIds.indices.reversed().filter {oldIds[it] == song.id && it != existing}.forEach(player::removeMediaItem)
    val queue=(0 until player.mediaItemCount).map {player.getMediaItemAt(it).mediaId}
    val ordered=queueIncomingOrder(queue,currentId,song.id,placement)
    val target=ordered.indexOf(song.id)
    val from=queue.indexOf(song.id)
    if(from < 0) player.addMediaItem(target,song.mediaItem(MANUAL_QUEUE_SOURCE))
    else if(from != target) player.moveMediaItem(from,target)
    // All source markers change together so deleting the first item cannot restore an old source.
    if(player.queueSource != MANUAL_QUEUE_SOURCE) {
        val marked=(0 until player.mediaItemCount).map {index ->
            val item=player.getMediaItemAt(index)
            item.buildUpon().setMediaMetadata(item.mediaMetadata.buildUpon()
                .setExtras(Bundle(item.mediaMetadata.extras ?: Bundle.EMPTY).apply {putString(QUEUE_SOURCE_KEY,MANUAL_QUEUE_SOURCE)}).build()).build()
        }
        player.replaceMediaItems(0,player.mediaItemCount,marked)
    }
    if(player.shuffleModeEnabled) {
        val order=queueIncomingOrder(shuffleIds,currentId,song.id,placement)
        val indicesById=ordered.withIndex().groupBy({it.value},{it.index}).mapValues {it.value.toMutableList()}
        val indices=order.map {indicesById.getValue(it).removeAt(0)}.toIntArray()
        player.setShuffleOrder(ShuffleOrder.DefaultShuffleOrder(indices,System.nanoTime()))
    }
    if(wasEmpty) {player.playWhenReady=false;player.prepare()}
    return true
}

/** Merge saved traversal with the unique restored queue; deleted files stay excluded. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal fun restoreQueueShuffleOrder(player: ExoPlayer,savedIds: List<String>) {
    if(!player.shuffleModeEnabled || player.mediaItemCount == 0) return
    val queue=(0 until player.mediaItemCount).map {player.getMediaItemAt(it).mediaId}
    val indicesById=queue.withIndex().associate {it.value to it.index}
    val order=mergeSongOrder(savedIds,queue).map {indicesById.getValue(it)}.toIntArray()
    player.setShuffleOrder(ShuffleOrder.DefaultShuffleOrder(order,System.nanoTime()))
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private lateinit var repository: MusicRepository
    private lateinit var player: ExoPlayer
    private val handler = Handler(Looper.getMainLooper())
    private val restoreScope = CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private val writes = Channel<PlaybackSnapshot>(Channel.CONFLATED)
    private var restorePending = true
    private var queueTouched = false
    private var canPersist = false
    private var pendingQueueChange = false
    private var cachedQueue = QueueSnapshot(emptyList(),null,emptyList())
    private data class QueueSnapshot(val ids: List<String>,val source: String?,val order: List<String>)
    private data class PlaybackSnapshot(val queue: QueueSnapshot,val current: String?,val position: Long,val repeat: Int,val shuffle: Boolean)
    private val flush = Runnable {
        val changed=pendingQueueChange
        pendingQueueChange=false
        snapshot(changed)
    }
    private val persist = object : Runnable {
        override fun run() { snapshot(); handler.postDelayed(this,3000) }
    }
    override fun onCreate() {
        super.onCreate()
        setMediaNotificationProvider(DefaultMediaNotificationProvider.Builder(this).build().apply {
            setSmallIcon(R.drawable.ic_notification_flower)
        })
        repository = MusicRepository.get(this)
        player = ExoPlayer.Builder(this).build().apply {
            setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),true)
            setHandleAudioBecomingNoisy(true)
            setWakeMode(C.WAKE_MODE_LOCAL)
        }
        val pending = PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        session = MediaSession.Builder(this,player).setSessionActivity(pending).setCallback(object : MediaSession.Callback {
            override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
                if(controller.packageName != packageName) return if(controller.isTrusted) super.onConnect(session,controller) else MediaSession.ConnectionResult.reject()
                val defaults=super.onConnect(session,controller)
                val commands=defaults.availableSessionCommands.buildUpon()
                    .add(SessionCommand(QUEUE_NEXT_COMMAND,Bundle.EMPTY))
                    .add(SessionCommand(QUEUE_LAST_COMMAND,Bundle.EMPTY)).build()
                return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                    .setAvailableSessionCommands(commands)
                    .setAvailablePlayerCommands(defaults.availablePlayerCommands).build()
            }
            override fun onCustomCommand(session: MediaSession,controller: MediaSession.ControllerInfo,command: SessionCommand,args: Bundle): ListenableFuture<SessionResult> {
                if(controller.packageName != packageName) return Futures.immediateFuture(SessionResult(SessionError.ERROR_PERMISSION_DENIED))
                val result=when(command.customAction) {
                    QUEUE_NEXT_COMMAND,QUEUE_LAST_COMMAND -> {
                        val song=repository.state.value.songs.find {it.id == args.getString("songId")}
                        if(song != null && placeQueueSong(player,song,if(command.customAction == QUEUE_NEXT_COMMAND) QueuePlacement.NEXT else QueuePlacement.LAST)) SessionResult.RESULT_SUCCESS
                        else SessionError.ERROR_BAD_VALUE
                    }
                    else -> return super.onCustomCommand(session,controller,command,args)
                }
                return Futures.immediateFuture(SessionResult(result))
            }
        }).build()
        // One serial writer also drains the final snapshot after the service is destroyed.
        CoroutineScope(Dispatchers.IO).launch {
            var previous: QueueSnapshot?=null
            for(value in writes) {
                try {
                val edit=repository.store.prefs.edit()
                if(previous != value.queue) {
                    edit.putString("queue",JSONArray(value.queue.ids).toString())
                        .putString(QUEUE_SOURCE_KEY,value.queue.source)
                        .putString(QUEUE_SHUFFLE_ORDER_KEY,JSONArray(value.queue.order).toString())
                }
                edit.putString("current",value.current).putLong("position",value.position)
                    .putInt("repeat",value.repeat).putBoolean("shuffle",value.shuffle).apply()
                previous=value.queue
                } catch(e: CancellationException) {throw e
                } catch(e: Exception) {android.util.Log.w("PlaybackService","Playback snapshot could not be saved",e)}
            }
        }
        player.addListener(object : Player.Listener {
            override fun onEvents(player: Player,events: Player.Events) {
                val changed=events.contains(Player.EVENT_TIMELINE_CHANGED) || events.contains(Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED)
                if(restorePending) {if(changed) queueTouched=true;return}
                if(changed) canPersist=true
                pendingQueueChange=pendingQueueChange || changed
                handler.removeCallbacks(flush)
                handler.postDelayed(flush,16)
            }
        })
        restoreScope.launch {
            try {
                val saved=withContext(Dispatchers.IO) {
                    val byId=repository.state.first {it.ready}.songs.filter {it.available}.associateBy {it.id}
                    val prefs=repository.store.prefs
                    val source=prefs.getString(QUEUE_SOURCE_KEY,null)
                    val items=repository.store.array("queue").strings().distinct().mapNotNull(byId::get).map {it.mediaItem(source)}
                    Triple(items,PlaybackSnapshot(QueueSnapshot(emptyList(),source,repository.store.array(QUEUE_SHUFFLE_ORDER_KEY).strings()),
                        prefs.getString("current",null),prefs.getLong("position",0).coerceAtLeast(0),
                        prefs.getInt("repeat",Player.REPEAT_MODE_OFF).coerceIn(0,2),prefs.getBoolean("shuffle",false)),prefs.contains(QUEUE_SHUFFLE_ORDER_KEY))
                }
                // A user action during disk loading takes precedence over the saved session.
                if(!queueTouched && player.mediaItemCount == 0 && saved.first.isNotEmpty()) {
                    val value=saved.second
                    player.setMediaItems(saved.first,saved.first.indexOfFirst {it.mediaId == value.current}.coerceAtLeast(0),value.position)
                    player.repeatMode=value.repeat
                    player.shuffleModeEnabled=value.shuffle
                    if(saved.third) restoreQueueShuffleOrder(player,value.queue.order)
                    player.prepare() // Always restore paused.
                }
                canPersist=true
            } catch(e: CancellationException) {throw e
            } catch(e: Exception) {
                // Keep the old saved queue if loading failed before any user action.
                canPersist=queueTouched
                android.util.Log.w("PlaybackService","Saved queue could not be restored",e)
            } finally {restorePending=false}
            snapshot(queueChanged=true)
        }
        handler.postDelayed(persist,3000)
    }
    private fun snapshot(queueChanged: Boolean = false) {
        if (!::player.isInitialized || restorePending || !canPersist) return
        if(queueChanged) cachedQueue=QueueSnapshot((0 until player.mediaItemCount).map {player.getMediaItemAt(it).mediaId},player.queueSource,player.queuePlayOrder())
        writes.trySend(PlaybackSnapshot(cachedQueue,player.currentMediaItem?.mediaId,player.currentPosition.coerceAtLeast(0),player.repeatMode,player.shuffleModeEnabled))
    }
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session
    override fun onDestroy() {
        handler.removeCallbacks(persist);handler.removeCallbacks(flush)
        if(restorePending && queueTouched) {restorePending=false;canPersist=true}
        snapshot(queueChanged=true)
        restoreScope.cancel();writes.close()
        session?.run { player.release(); release() }; session=null
        super.onDestroy()
    }
}
