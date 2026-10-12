package com.xiaohonghua.music

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Test

class SongArtworkPanTest {
    @Test fun fitImageCannotBeMovedIntoBlankSpace() {
        assertEquals(Offset.Zero,clampArtworkPan(Offset(500f,-500f),1f,IntSize(300,400),IntSize(100,100)))
        assertEquals(Offset.Zero,clampArtworkPan(Offset(500f,-500f),1f,IntSize(300,400),IntSize(400,100)))
    }
    @Test fun zoomedImageKeepsBothEdgesInsideTheViewport() {
        assertEquals(Offset(150f,-100f),clampArtworkPan(Offset(500f,-500f),2f,IntSize(300,400),IntSize(100,100)))
        assertEquals(Offset(50f,-200f),clampArtworkPan(Offset(500f,-500f),2f,IntSize(300,400),IntSize(100,200)))
        assertEquals(Offset(20f,-30f),clampArtworkPan(Offset(20f,-30f),2f,IntSize(300,400),IntSize(100,200)))
    }
    @Test fun missingImageOrUnmeasuredViewportCannotProduceAnInvalidOffset() {
        assertEquals(Offset.Zero,clampArtworkPan(Offset(500f,-500f),2f,IntSize.Zero,IntSize(100,100)))
        assertEquals(Offset.Zero,clampArtworkPan(Offset(500f,-500f),2f,IntSize(300,400),IntSize.Zero))
    }
}
