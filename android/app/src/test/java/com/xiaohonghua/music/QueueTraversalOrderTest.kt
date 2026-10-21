package com.xiaohonghua.music

import org.junit.Assert.*
import org.junit.Test

class QueueTraversalOrderTest {
    private val physical=listOf("a","b","c")
    private val shuffled=listOf("b","a","c")

    @Test fun draggingShuffledRowsBackToPhysicalOrderStillNeedsACommit() {
        assertTrue(queueOrderChanged(physical,physical,shuffled,true))
        val reordered=physical.toMutableList()
        queueMovePlan(physical,physical).forEach {(from,to) -> reordered.add(to,reordered.removeAt(from))}
        assertEquals(physical,reordered)
        // A zero-move commit must still disable shuffle so the requested traversal becomes audible.
        assertFalse(queueOrderChanged(physical,reordered,shuffled,false))
    }

    @Test fun endingADragWithoutChangingTheShuffledTraversalMustKeepShuffle() {
        assertFalse(queueOrderChanged(shuffled,physical,shuffled,true))
        assertTrue(queueOrderChanged(shuffled,physical,shuffled,false))
    }

    @Test fun ordinaryModesAndMissingShuffleTraversalUseThePhysicalOrder() {
        assertFalse(queueOrderChanged(physical,physical,shuffled,false))
        assertFalse(queueOrderChanged(physical,physical,emptyList(),true))
        assertTrue(queueOrderChanged(physical.reversed(),physical,emptyList(),true))
    }
}
