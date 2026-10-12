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
}
