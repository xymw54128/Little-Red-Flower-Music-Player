package com.xiaohonghua.music

import org.junit.Assert.*
import org.junit.Test

class LyricMotionTest {
    private val lines=listOf(0L,80L,580L,5580L,5650L).map {LyricLine(it,"歌词 $it")}

    @Test fun sharedTimestampStartsColorScaleAndScrollTogether() {
        val motion=LyricTransition(mapOf(0 to 1f),0f,1,120f,1000)
        val before=motion.sample(999)
        val start=motion.sample(1000)
        assertEquals(mapOf(0 to 1f),before.weights)
        assertEquals(before,start)
        assertEquals(0f,start.offset,0f)
        val middle=motion.sample(1125)
        assertEquals(.125f,middle.weights[0]!!,.00001f)
        assertEquals(.875f,middle.weights[1]!!,.00001f)
        val remaining=1f-125f/300f
        assertEquals(120f*(1f-remaining*remaining*remaining),middle.offset,.0001f)
        assertTrue(middle.running)
        val styleFinished=motion.sample(1250)
        assertEquals(mapOf(1 to 1f),styleFinished.weights)
        assertTrue(styleFinished.offset < 120f)
        assertTrue(styleFinished.running)
        val finished=motion.sample(1300)
        assertEquals(mapOf(1 to 1f),finished.weights)
        assertEquals(120f,finished.offset,0f)
        assertFalse(finished.running)
    }

    @Test fun denseTimestampsInterruptFromActualVisualWeightsAndOffset() {
        val first=LyricTransition(mapOf(0 to 1f),0f,1,100f,0)
        val atNextTimestamp=first.sample(80)
        assertTrue(atNextTimestamp.weights[1]!! < 1f)
        val second=LyricTransition(atNextTimestamp.weights,atNextTimestamp.offset,2,200f,80)
        assertEquals(atNextTimestamp,second.sample(80))
        val next=second.sample(160)
        assertTrue(next.weights[0]!! < atNextTimestamp.weights[0]!!)
        assertTrue(next.weights[1]!! < atNextTimestamp.weights[1]!!)
        assertTrue(next.weights[2]!! > 0f)
        assertTrue(next.offset > atNextTimestamp.offset)
        assertEquals(mapOf(2 to 1f),second.sample(380).weights)
        assertEquals(200f,second.sample(380).offset,0f)
    }

    @Test fun forwardAndBackwardSeekDoNotQueueOrResetIntermediateLines() {
        val forward=LyricTransition(mapOf(0 to 1f),0f,40,4000f,500)
        val interrupted=forward.sample(600)
        val backward=LyricTransition(interrupted.weights,interrupted.offset,0,0f,600)
        assertEquals(interrupted,backward.sample(600))
        var previous=interrupted.offset
        for(now in 601L..900L) {
            val state=backward.sample(now)
            assertTrue(state.offset <= previous)
            assertTrue(state.offset >= 0f)
            assertTrue(state.weights.keys.all {it == 0 || it == 40})
            previous=state.offset
        }
        assertEquals(mapOf(0 to 1f),backward.sample(900).weights)
        assertFalse(backward.sample(900).running)
    }

    @Test fun displayCadencesUseOneClockAndNeverAnimateBeforeTimestamp() {
        for(hz in listOf(60,90,120)) for(phase in 0..16) {
            val frameMs=1000.0/hz
            for(i in 0 until lines.lastIndex) {
                val timestamp=lines[i+1].time
                val lastFrame=(kotlin.math.ceil((timestamp-phase)/frameMs)-1)*frameMs+phase
                val motion=LyricTransition(mapOf(i to 1f),i*100f,i+1,(i+1)*100f,timestamp)
                assertEquals(i,Lrc.current(lines,lastFrame.toLong()))
                assertEquals(mapOf(i to 1f),motion.sample(lastFrame.toLong()).weights)
                assertEquals(i*100f,motion.sample(lastFrame.toLong()).offset,0f)
                assertEquals(i+1,Lrc.current(lines,timestamp))
                val nextFrame=(lastFrame+frameMs).toLong().coerceAtLeast(timestamp+1)
                val state=motion.sample(nextFrame)
                assertTrue(state.weights[i+1]!! > 0f)
                assertTrue(state.offset > i*100f)
            }
        }
    }

    @Test fun repeatedSamplesDoNotRestartAndPausedSeekCanComplete() {
        val seek=LyricTransition(mapOf(1 to 1f),80f,4,320f,2000)
        assertEquals(seek.sample(2100),seek.sample(2100))
        assertEquals(320f,seek.sample(2300).offset,0f)
        assertFalse(seek.sample(2300).running)
        assertEquals(seek.sample(2300),seek.sample(Long.MAX_VALUE))
    }

    @Test fun returningToSameLineOnlyAnimatesPosition() {
        val returning=LyricTransition(mapOf(2 to 1f),500f,2,200f,100)
        assertEquals(mapOf(2 to 1f),returning.sample(250).weights)
        assertTrue(returning.sample(250).running)
        assertEquals(200f,returning.sample(400).offset,0f)
        assertFalse(returning.sample(400).running)
        val settled=LyricTransition(mapOf(2 to 1f),200f,2,200f,100)
        assertFalse(settled.sample(100).running)
    }

    @Test fun emptyTargetAndReducedMotionFinishWithoutInvalidValues() {
        val beforeFirst=LyricTransition(emptyMap(),0f,-1,0f,0)
        assertEquals(emptyMap<Int,Float>(),beforeFirst.sample(0).weights)
        assertFalse(beforeFirst.sample(0).running)
        val reduced=LyricTransition(mapOf(0 to 1f),0f,1,100f,100,0,0)
        assertEquals(mapOf(1 to 1f),reduced.sample(100).weights)
        assertEquals(100f,reduced.sample(100).offset,0f)
        assertFalse(reduced.sample(100).running)
    }

    @Test fun inputWeightsAreSnapshottedForReliableInterruption() {
        val mutable=mutableMapOf(0 to .4f,1 to .6f)
        val motion=LyricTransition(mutable,20f,2,40f,0)
        mutable.clear()
        assertEquals(mapOf(0 to .4f,1 to .6f),motion.sample(0).weights)
    }
}
