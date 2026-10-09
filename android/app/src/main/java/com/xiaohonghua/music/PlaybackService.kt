package com.xiaohonghua.music

import android.app.PendingIntent
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import org.json.JSONArray

fun Song.mediaItem(): MediaItem = MediaItem.Builder().setMediaId(id).setUri(uri).setMediaMetadata(
    MediaMetadata.Builder().setTitle(title).setArtist(artist).setAlbumTitle(album)
        .setArtworkUri(artwork.takeIf { it.isNotBlank() }?.let { android.net.Uri.fromFile(java.io.File(it)).buildUpon().appendQueryParameter("version",artworkRevision).build() }).build()
).build()

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private lateinit var repository: MusicRepository
    private lateinit var player: ExoPlayer
    private val handler = Handler(Looper.getMainLooper())
    private val persist = object : Runnable {
        override fun run() { snapshot(); handler.postDelayed(this,3000) }
    }
    override fun onCreate() {
        super.onCreate()
        repository = MusicRepository.get(this)
        player = ExoPlayer.Builder(this).build().apply {
            setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),true)
            setHandleAudioBecomingNoisy(true)
            setWakeMode(C.WAKE_MODE_LOCAL)
        }
        val pending = PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        session = MediaSession.Builder(this,player).setSessionActivity(pending).setCallback(object : MediaSession.Callback {
            override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
                return if (controller.packageName == packageName || controller.isTrusted) super.onConnect(session,controller)
                else MediaSession.ConnectionResult.reject()
            }
        }).build()
        val prefs = repository.store.prefs
        val byId = repository.store.songs().filter { it.available }.associateBy { it.id }
        val queue = repository.store.array("queue").strings().mapNotNull(byId::get)
        if (queue.isNotEmpty()) {
            val index = queue.indexOfFirst { it.id == prefs.getString("current", "") }.coerceAtLeast(0)
            player.setMediaItems(queue.map { it.mediaItem() },index,prefs.getLong("position",0).coerceAtLeast(0))
            player.repeatMode = prefs.getInt("repeat",Player.REPEAT_MODE_OFF).coerceIn(0,2)
            player.shuffleModeEnabled = prefs.getBoolean("shuffle",false)
            player.prepare() // Restore paused; never start audio unexpectedly at launch.
        }
        player.addListener(object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) { repository.playing(player.isPlaying); snapshot(events.contains(Player.EVENT_TIMELINE_CHANGED)) }
        })
        snapshot(queueChanged=true)
        handler.postDelayed(persist,3000)
    }
    private fun snapshot(queueChanged: Boolean = false) {
        if (!::player.isInitialized) return
        val edit=repository.store.prefs.edit()
        if(queueChanged) edit.putString("queue",JSONArray((0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId }).toString())
        edit.putString("current",player.currentMediaItem?.mediaId).putLong("position",player.currentPosition.coerceAtLeast(0))
            .putInt("repeat",player.repeatMode).putBoolean("shuffle",player.shuffleModeEnabled).apply()
    }
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session
    override fun onDestroy() {
        handler.removeCallbacks(persist); snapshot(queueChanged=true); repository.playing(false)
        session?.run { player.release(); release() }; session=null
        super.onDestroy()
    }
}
