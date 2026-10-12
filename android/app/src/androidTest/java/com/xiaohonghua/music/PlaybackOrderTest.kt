package com.xiaohonghua.music

import android.net.Uri
import android.os.SystemClock
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@RunWith(AndroidJUnit4::class)
class PlaybackOrderTest {
    @Test fun nativeTimelineMovesKeepCurrentPositionRepeatAndPlayingOrPausedState() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        val audio=File(context.cacheDir,"playback-order-test.wav")
        instrumentation.context.assets.open("tone.wav").use {input -> audio.outputStream().use {input.copyTo(it)}}
        lateinit var player: ExoPlayer
        instrumentation.runOnMainSync {player=ExoPlayer.Builder(context).build()}
        try {
            val songs=listOf("a","b","c").map {id -> Song(id,Uri.fromFile(audio).toString(),"$id.wav",id)}
            for(playing in listOf(false,true)) {
                instrumentation.runOnMainSync {
                    player.playWhenReady=false
                    player.setMediaItems(songs.map {it.mediaItem("drive")},1,1200)
                    player.repeatMode=Player.REPEAT_MODE_ALL
                    player.prepare()
                }
                val deadline=SystemClock.elapsedRealtime()+10_000
                var ready=false
                while(!ready && SystemClock.elapsedRealtime() < deadline) {
                    instrumentation.runOnMainSync {ready=player.playbackState == Player.STATE_READY}
                    if(!ready) Thread.sleep(20)
                }
                assertTrue("测试音频应准备完成",ready)
                instrumentation.runOnMainSync {
                    player.playWhenReady=playing
                    player.shuffleModeEnabled=true
                    val position=player.currentPosition
                    val queue=(0 until player.mediaItemCount).map {player.getMediaItemAt(it).mediaId}
                    val ordered=orderedQueueIds(queue,player.queueSource,listOf("c","b","a"),"drive")
                    queueMovePlan(queue,ordered).forEach {(from,to) -> player.moveMediaItem(from,to)}
                    player.shuffleModeEnabled=false
                    assertEquals(listOf("c","b","a"),(0 until player.mediaItemCount).map {player.getMediaItemAt(it).mediaId})
                    assertEquals("b",player.currentMediaItem?.mediaId)
                    assertTrue(kotlin.math.abs(player.currentPosition-position) < 200)
                    assertEquals(playing,player.playWhenReady)
                    assertEquals(playing,player.isPlaying)
                    assertEquals(Player.REPEAT_MODE_ALL,player.repeatMode)
                    assertEquals("drive",player.queueSource)
                    assertFalse(player.shuffleModeEnabled)
                    player.replaceMediaItem(0,songs[2].copy(title="新标签").mediaItem(player.queueSource))
                    assertEquals("drive",player.queueSource)
                }
            }
        } finally {instrumentation.runOnMainSync {player.release()}}
    }

    @Test fun playlistDescriptionSupportsLegacyJsonNormalSaveAndPortableBackup() {
        val legacy=JSONObject().put("id","drive").put("name","开车")
        assertEquals("",playlistFromJson(legacy).description)
        val playlist=Playlist("drive","开车",listOf("b","a"),description="每天出发时听的歌")
        assertEquals(playlist,playlistFromJson(playlist.toJson()))
        assertEquals(playlist,playlistFromJson(playlist.toJson(true)))
        assertEquals(PLAYLIST_DESCRIPTION_LIMIT,playlistFromJson(legacy.put("description","长".repeat(400))).description.length)
        assertEquals(playlist.description,playlist.toJson(true).getString("description"))
    }
}
