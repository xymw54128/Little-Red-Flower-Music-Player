package com.xiaohonghua.music

import org.junit.Assert.*
import org.junit.Test

class LyricMotionTest {
    private val lines=listOf(0L,80L,580L,5580L,5650L).map {LyricLine(it,"歌词 $it")}

    @Test fun endsBeforeEveryTimestampAndHighlightsAtTimestamp() {
        for(i in 0 until lines.lastIndex) {
            val next=lines[i+1].time
            val motion=Lrc.motion(lines,next-1)
            assertEquals(i,motion.current)
            assertEquals(i+1,motion.to)
            assertEquals(1f,motion.fraction,0f)
            assertTrue(motion.end < next)
            assertTrue(motion.start >= lines[i].time)
            val at=Lrc.motion(lines,next)
            assertEquals(i+1,at.current)
            assertEquals(i+1,at.from)
            assertEquals(0f,at.fraction,0f)
        }
    }

    @Test fun durationsAdaptToShortLongIntervalsAndPlaybackSpeed() {
        val short=Lrc.motion(lines,1)
        val long=Lrc.motion(lines,581)
        assertTrue(short.end-short.start < long.end-long.start)
        assertEquals(480L,long.end-long.start)
        assertEquals(960L,Lrc.motion(lines,581,2f).let {it.end-it.start})
        assertEquals(240L,Lrc.motion(lines,581,.5f).let {it.end-it.start})
        assertTrue(short.end < 80L)
    }

    @Test fun positionDeterminesSmoothProgressWithoutAnimationQueue() {
        val m=Lrc.motion(lines,581)
        assertEquals(0f,Lrc.motion(lines,m.start).fraction,0f)
        assertEquals(.5f,Lrc.motion(lines,(m.start+m.end)/2).fraction,.01f)
        assertEquals(1f,Lrc.motion(lines,m.end).fraction,0f)
        var previous=0f
        for(position in m.start..m.end) {
            val fraction=Lrc.motion(lines,position).fraction
            assertTrue(fraction >= previous)
            previous=fraction
        }
        // A forward/backward seek calculates the new segment directly.
        assertEquals(3,Lrc.motion(lines,5600).current)
        assertEquals(0,Lrc.motion(lines,10).current)
    }

    @Test fun normalDisplayCadencesReachTargetBeforeIrregularTimestamp() {
        for(hz in listOf(60,90,120)) for(phase in 0..16) {
            val frameMs=1000.0/hz
            for(i in 0 until lines.lastIndex) {
                val deadline=lines[i+1].time
                val lastFrame=(kotlin.math.ceil((deadline-phase)/frameMs)-1)*frameMs+phase
                assertTrue(lastFrame < deadline)
                assertEquals("${hz} Hz phase $phase, line $i",1f,Lrc.motion(lines,lastFrame.toLong()).fraction,0f)
            }
        }
    }

    @Test fun handlesEmptyFirstLastOffsetsAndOneMillisecondGap() {
        assertEquals(-1,Lrc.motion(emptyList(),100).current)
        val shifted=Lrc.parse("[offset:500]\n[00:01.00]开始\n[00:01.001]下一句")
        val before=Lrc.motion(shifted,1499)
        assertEquals(-1,before.current)
        assertEquals(0,before.from)
        assertEquals(0,before.to)
        assertEquals(1f,Lrc.motion(shifted,1500).fraction,0f)
        val last=Lrc.motion(shifted,10000)
        assertEquals(1,last.current)
        assertEquals(last.from,last.to)
    }
}
