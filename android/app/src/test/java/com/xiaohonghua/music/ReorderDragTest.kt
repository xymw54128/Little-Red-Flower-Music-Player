package com.xiaohonghua.music

import org.junit.Assert.*
import org.junit.Test

class ReorderDragTest {
    @Test fun draftKeepsUserOrderAcrossMetadataMembershipChangesAndRejectsDuplicateIds() {
        assertEquals(listOf("c","a","d"),reconcileReorderIds(listOf("c","b","a","c"),listOf("a","c","d","d")))
        assertEquals(emptyList<String>(),reconcileReorderIds(listOf("a"),emptyList()))
    }
    @Test fun dragCrossesMeasuredCentersAndCanMoveSeveralRowsAtOnce() {
        val ids=listOf("a","b","c","d")
        val slots=listOf(ReorderSlot("a",0,0f,68f),ReorderSlot("b",1,68f,96f),ReorderSlot("c",2,164f,68f),ReorderSlot("d",3,232f,68f))
        assertEquals(0,reorderDragTarget(ids,"a",115f,slots))
        assertEquals(2,reorderDragTarget(ids,"a",210f,slots))
        assertEquals(0,reorderDragTarget(ids,"d",20f,slots))
        assertEquals(listOf("b","c","a","d"),moveReorderId(ids,"a",2))
    }
    @Test fun staleLayoutCannotMoveTheWrongSongOrOscillateAfterAReorder() {
        val ids=listOf("b","c","a")
        val stale=listOf(ReorderSlot("a",0,0f,68f),ReorderSlot("b",1,68f,68f),ReorderSlot("c",2,136f,68f))
        assertNull(reorderDragTarget(ids,"a",180f,stale))
        assertNull(reorderDragTarget(ids,"removed",180f,emptyList()))
        assertEquals(ids,moveReorderId(ids,"removed",0))
    }
    @Test fun edgeScrollingUsesViewportPixelsAndIsProportionalAtAnyDensity() {
        assertEquals(0f,reorderEdgeVelocity(200f,0f,400f,72f,900f),.01f)
        assertEquals(-450f,reorderEdgeVelocity(36f,0f,400f,72f,900f),.01f)
        assertEquals(900f,reorderEdgeVelocity(430f,0f,400f,72f,900f),.01f)
        assertEquals(900f,reorderEdgeVelocity(728f,0f,800f,144f,1800f),.01f)
        assertEquals(0f,reorderEdgeVelocity(0f,0f,0f,72f,900f),.01f)
    }
}
