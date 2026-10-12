package com.xiaohonghua.music

import org.junit.Assert.*
import org.junit.Test

class Playback107LogicTest {
    @Test fun incomingNextAndTailMoveExistingWithoutDuplicates() {
        assertEquals(listOf("a","b","d","c"),queueIncomingOrder(listOf("a","b","c","d"),"b","d",QueuePlacement.NEXT))
        assertEquals(listOf("b","a","c","d"),queueIncomingOrder(listOf("a","b","c","d"),"b","a",QueuePlacement.NEXT))
        assertEquals(listOf("a","c","d","b"),queueIncomingOrder(listOf("a","b","c","d"),"a","b",QueuePlacement.LAST))
        assertEquals(listOf("a","b","c"),queueIncomingOrder(listOf("a","c","b","b"),"a","b",QueuePlacement.NEXT))
    }
    @Test fun incomingCurrentTrackIsANoOpAndEmptyQueueHasOnlyTheIncomingSong() {
        assertEquals(listOf("a","b","c"),queueIncomingOrder(listOf("a","b","c"),"b","b",QueuePlacement.LAST))
        assertEquals(listOf("a","b","c"),queueIncomingOrder(listOf("a","b","c"),"b","b",QueuePlacement.NEXT))
        assertEquals(listOf("new"),queueIncomingOrder(emptyList(),null,"new",QueuePlacement.NEXT))
        assertEquals(listOf("new"),queueIncomingOrder(emptyList(),null,"new",QueuePlacement.LAST))
    }
    @Test fun shuffledNextPlacementPreservesTheRemainingTraversal() {
        assertEquals(listOf("d","b","a","c"),queueIncomingOrder(listOf("d","b","c","a"),"b","a",QueuePlacement.NEXT))
        assertEquals(listOf("d","b","c","a"),queueIncomingOrder(listOf("a","d","b","c"),"b","a",QueuePlacement.LAST))
    }
    @Test fun timerUsesElapsedDeadlineAndExpiresExactlyOnce() {
        val timer=PlaybackSleepDeadline()
        assertFalse(timer.state.active)
        timer.start(15,9_000)
        assertEquals(909_000L,timer.state.deadlineElapsedRealtime)
        assertEquals(1L,timer.state.remainingMillis(908_999))
        assertFalse(timer.expire(908_999))
        assertTrue(timer.expire(909_000))
        assertFalse(timer.state.active)
        assertFalse(timer.expire(909_001))
        timer.start(30,1_000)
        timer.cancel()
        assertFalse(timer.expire(9_999_999))
    }
    @Test fun timerReplacesOldDeadlineAndRejectsUnsupportedDurations() {
        val timer=PlaybackSleepDeadline()
        assertTrue(timer.start(15,1_000))
        assertTrue(timer.start(60,2_000))
        assertEquals(3_602_000L,timer.state.deadlineElapsedRealtime)
        assertFalse(timer.start(0,3_000))
        assertEquals(3_602_000L,timer.state.deadlineElapsedRealtime)
        assertFalse(timer.start(90,3_000))
    }
    @Test fun historyOnlyRecordsActuallyPlayingNewTracks() {
        val history=PlaybackHistoryTracker()
        assertNull(history.observe("a",false))
        assertEquals("a",history.observe("a",true))
        assertNull(history.observe("a",true)) // Position, seek and metadata events.
        assertNull(history.observe("a",false))
        assertNull(history.observe("a",true)) // Pause/resume is the same listening session.
        assertNull(history.observe("b",false))
        assertEquals("b",history.observe("b",true))
        assertEquals("a",history.observe("a",true))
        assertNull(history.observe(null,false))
        assertEquals("a",history.observe("a",true))
    }
}
