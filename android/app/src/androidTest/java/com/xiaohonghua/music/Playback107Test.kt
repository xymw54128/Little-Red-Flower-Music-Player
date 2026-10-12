package com.xiaohonghua.music

import android.os.Build
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ShuffleOrder
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Native queue mutation only: no stored library changes and no audio playback. */
@RunWith(AndroidJUnit4::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class Playback107Test {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private fun song(id: String,available: Boolean=true)=Song(id,"file:///queue-test/$id.flac","$id.flac",id,available=available)
    private fun withPlayer(checks: (ExoPlayer)->Unit) {
        check(Build.HARDWARE in listOf("ranchu","goldfish")) {"播放测试只能在模拟器中执行"}
        instrumentation.runOnMainSync {
            val player=ExoPlayer.Builder(instrumentation.targetContext).build()
            try {checks(player)} finally {player.release()}
        }
    }
    @Test fun nextInsertionKeepsCurrentPositionPauseAndRepeatAndMovesDuplicates() = withPlayer {player ->
        player.setMediaItems(listOf("a","b","c","d").map {song(it).mediaItem("test-playlist")},1,4_200)
        player.repeatMode=Player.REPEAT_MODE_ALL
        assertTrue(placeQueueSong(player,song("d"),QueuePlacement.NEXT))
        assertEquals(listOf("a","b","d","c"),(0 until player.mediaItemCount).map {player.getMediaItemAt(it).mediaId})
        assertEquals("b",player.currentMediaItem?.mediaId)
        assertEquals(4_200L,player.currentPosition)
        assertFalse(player.playWhenReady)
        assertEquals(Player.REPEAT_MODE_ALL,player.repeatMode)
        assertEquals(MANUAL_QUEUE_SOURCE,player.queueSource)
        assertTrue(placeQueueSong(player,song("d"),QueuePlacement.LAST))
        assertEquals(4,player.mediaItemCount)
        assertEquals("d",player.getMediaItemAt(3).mediaId)
        player.removeMediaItem(0)
        assertEquals(MANUAL_QUEUE_SOURCE,player.queueSource) // No old source leaks after removal.
    }
    @Test fun shuffledInsertionIsReallyNextWithoutDisablingShuffleOrRepeatOne() = withPlayer {player ->
        player.setMediaItems(listOf("a","b","c","d").map {song(it).mediaItem(LIBRARY_QUEUE_SOURCE)},1,5_100)
        player.repeatMode=Player.REPEAT_MODE_ONE
        player.shuffleModeEnabled=true
        player.setShuffleOrder(ShuffleOrder.DefaultShuffleOrder(intArrayOf(3,1,2,0),123L))
        assertTrue(placeQueueSong(player,song("a"),QueuePlacement.NEXT))
        assertTrue(player.shuffleModeEnabled)
        assertEquals(Player.REPEAT_MODE_ONE,player.repeatMode)
        assertEquals("b",player.currentMediaItem?.mediaId)
        assertEquals(5_100L,player.currentPosition)
        val next=player.shuffleOrder.getNextIndex(player.currentMediaItemIndex)
        assertEquals("a",player.getMediaItemAt(next).mediaId)
        assertEquals(listOf("d","b","a","c"),player.queuePlayOrder())
        assertTrue(placeQueueSong(player,song("c"),QueuePlacement.LAST))
        assertEquals("c",player.getMediaItemAt(player.shuffleOrder.lastIndex).mediaId)
        assertEquals(listOf("d","b","a","c"),player.queuePlayOrder())
    }
    @Test fun currentTrackAdditionIsNoOpAndUnavailableSongIsRejected() = withPlayer {player ->
        player.setMediaItems(listOf("a","b").map {song(it).mediaItem("playlist")},0,3_000)
        player.playWhenReady=true // No prepare: no source reads or audio.
        assertTrue(placeQueueSong(player,song("a"),QueuePlacement.NEXT))
        assertEquals(2,player.mediaItemCount)
        assertEquals(3_000L,player.currentPosition)
        assertTrue(player.playWhenReady)
        assertEquals("playlist",player.queueSource)
        assertFalse(placeQueueSong(player,song("missing",false),QueuePlacement.LAST))
        assertEquals(2,player.mediaItemCount)
    }
    @Test fun emptyQueuePreparesPausedAndDoesNotStartAudio() = withPlayer {player ->
        assertTrue(placeQueueSong(player,song("first"),QueuePlacement.LAST))
        assertEquals(1,player.mediaItemCount)
        assertEquals("first",player.currentMediaItem?.mediaId)
        assertFalse(player.playWhenReady)
        assertEquals(0L,player.currentPosition)
        assertEquals(MANUAL_QUEUE_SOURCE,player.queueSource)
    }
    @Test fun savedShuffleTraversalSurvivesAPlayerRestartAndFiltersDeletedFiles() = withPlayer {original ->
        original.setMediaItems(listOf("a","b","c","d").map {song(it).mediaItem(LIBRARY_QUEUE_SOURCE)},1,4_200)
        original.shuffleModeEnabled=true
        original.setShuffleOrder(ShuffleOrder.DefaultShuffleOrder(intArrayOf(3,1,2,0),123L))
        assertTrue(placeQueueSong(original,song("a"),QueuePlacement.NEXT))
        val savedOrder=original.queuePlayOrder()
        assertEquals(listOf("d","b","a","c"),savedOrder)
        val surviving=(0 until original.mediaItemCount).map(original::getMediaItemAt).filterNot {it.mediaId == "d"}
        val restored=ExoPlayer.Builder(instrumentation.targetContext).build()
        try {
            // A new known item is appended; a deleted saved item is never restored.
            val items=surviving+song("e").mediaItem(MANUAL_QUEUE_SOURCE)
            restored.setMediaItems(items,items.indexOfFirst {it.mediaId == "b"},4_200)
            restored.shuffleModeEnabled=true
            restoreQueueShuffleOrder(restored,savedOrder)
            assertEquals(listOf("b","a","c","e"),restored.queuePlayOrder())
            assertEquals("a",restored.getMediaItemAt(restored.shuffleOrder.getNextIndex(restored.currentMediaItemIndex)).mediaId)
            assertEquals("b",restored.currentMediaItem?.mediaId)
            assertEquals(4_200L,restored.currentPosition)
            assertFalse(restored.playWhenReady)
        } finally {restored.release()}
    }

}
