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

    @Test fun longAndDenseIntervalsFinishBeforeTimestampWithoutOverlappingWindows() {
        for(i in 0 until lines.lastIndex) {
            val timestamp=lines[i+1].startTime
            val lead=Lrc.lead(lines,timestamp-1)!!
            assertEquals(i+1,lead.targetIndex)
            assertTrue(lead.startedAtPosition >= lines[i].startTime)
            assertTrue(lead.finishAtPosition < timestamp)
            assertTrue(lead.durationMillis in 1L..300L)
            assertEquals(lead.finishAtPosition-lead.startedAtPosition,lead.durationMillis)
            assertTrue(Lrc.lead(lines,lead.startedAtPosition-1)?.targetIndex != i+1)
            val motion=LyricTransition(mapOf(i to 1f),i*100f,i+1,(i+1)*100f,
                lead.startedAtPosition,lead.durationMillis,lead.durationMillis)
            val finished=motion.sample(lead.finishAtPosition)
            assertEquals(mapOf(i+1 to 1f),finished.weights)
            assertEquals((i+1)*100f,finished.offset,0f)
            assertFalse(finished.running)
            assertEquals(i,Lrc.current(lines,lead.finishAtPosition))
            assertEquals(i+1,Lrc.current(lines,timestamp))
        }
    }

    @Test fun standardWindowPreservesWallDurationAndSafetyMarginAtDifferentSpeeds() {
        val spaced=listOf(LyricLine(0,"第一行"),LyricLine(5000,"第二行"))
        for(speed in listOf(.5f,1f,1.5f,2f)) {
            val lead=Lrc.lead(spaced,4999,speed)!!
            assertEquals(300.0,lead.durationMillis/speed.toDouble(),.00001)
            assertEquals(20.0,(5000-lead.finishAtPosition)/speed.toDouble(),.00001)
            assertNull(Lrc.lead(spaced,lead.startedAtPosition-1,speed))
            assertEquals(0L,Lrc.lead(spaced,lead.startedAtPosition,speed)!!.elapsedMillis)
            assertEquals(lead.durationMillis,Lrc.lead(spaced,lead.finishAtPosition,speed)!!.elapsedMillis)
        }
    }

    @Test fun displayCadencesCompleteOnAFrameBeforeTheTimestamp() {
        val spaced=listOf(0L,500L,5000L,5600L).map {LyricLine(it,"歌词")}
        for(hz in listOf(60,90,120)) for(phase in 0..16) for(speed in listOf(.5f,1f,2f)) {
            val frameMs=1000.0/hz
            for(i in 0 until spaced.lastIndex) {
                val timestamp=spaced[i+1].time
                val lead=Lrc.lead(spaced,timestamp-1,speed)!!
                val timestampWall=timestamp/speed.toDouble()
                val lastFrameWall=(kotlin.math.ceil((timestampWall-phase)/frameMs)-1)*frameMs+phase
                val lastFramePosition=(lastFrameWall*speed).toLong()
                val motion=LyricTransition(mapOf(i to 1f),i*100f,i+1,(i+1)*100f,
                    lead.startedAtPosition,lead.durationMillis,lead.durationMillis)
                val finished=motion.sample(lastFramePosition)
                assertTrue(lastFramePosition < timestamp)
                assertEquals(mapOf(i+1 to 1f),finished.weights)
                assertEquals((i+1)*100f,finished.offset,0f)
                assertFalse(finished.running)
            }
        }
    }

    @Test fun naturalStylesAndPositionHaveExactlyTheSameProgress() {
        val spaced=listOf(LyricLine(0,"第一行"),LyricLine(1000,"第二行"))
        val lead=Lrc.lead(spaced,700)!!
        val motion=LyricTransition(mapOf(0 to 1f),20f,1,220f,lead.startedAtPosition,
            lead.durationMillis,lead.durationMillis)
        for(position in lead.startedAtPosition..lead.finishAtPosition) {
            val frame=motion.sample(position)
            val progress=frame.weights[1] ?: 0f
            assertEquals(progress,(frame.offset-20f)/200f,.000001f)
            assertEquals(1f,frame.weights.values.sum(),.000001f)
        }
    }

    @Test fun firstObservedFrameRebasesWithoutJumpAndStillMeetsItsDeadline() {
        val lead=Lrc.lead(listOf(LyricLine(0,"一"),LyricLine(1000,"二")),696)!!
        val picture=LyricFrame(mapOf(0 to .7f,1 to .3f),68f,true)
        val remaining=lead.finishAtPosition-696
        val motion=LyricTransition(picture.weights,picture.offset,lead.targetIndex,220f,696,
            remaining,remaining)
        assertEquals(picture,motion.sample(696))
        assertEquals(220f,motion.sample(lead.finishAtPosition).offset,0f)
        assertEquals(mapOf(1 to 1f),motion.sample(lead.finishAtPosition).weights)
        assertFalse(motion.sample(lead.finishAtPosition).running)
    }

    @Test fun pauseFreezesMediaMotionWhileASeekUsesAnIndependentWallClock() {
        val lead=Lrc.lead(listOf(LyricLine(0,"一"),LyricLine(1000,"二")),750)!!
        val natural=LyricTransition(mapOf(0 to 1f),0f,1,100f,lead.startedAtPosition,
            lead.durationMillis,lead.durationMillis)
        val paused=natural.sample(750)
        repeat(100) {assertEquals(paused,natural.sample(750))}
        val seek=LyricTransition(paused.weights,paused.offset,8,800f,5000,300,300)
        assertEquals(paused,seek.sample(5000))
        assertEquals(mapOf(8 to 1f),seek.sample(5300).weights)
        assertEquals(800f,seek.sample(5300).offset,0f)
        assertFalse(seek.sample(5300).running)
    }

    @Test fun speedChangesRebaseTheSelectedFutureRowWithoutReversingItsPicture() {
        val spaced=listOf(LyricLine(0,"一"),LyricLine(5000,"二"))
        val position=4775L
        val originalLead=Lrc.lead(spaced,position,1f)!!
        val original=LyricTransition(mapOf(0 to 1f),0f,originalLead.targetIndex,100f,
            originalLead.startedAtPosition,originalLead.durationMillis,originalLead.durationMillis)
        val picture=original.sample(position)
        assertEquals(0,Lrc.current(spaced,position))
        assertTrue(picture.weights[1]!! in 0f..1f)
        for(speed in listOf(4f,.25f)) {
            // A slower clock may place the current media position before its new
            // window. Keep the already selected row and query that row's deadline.
            val direct=Lrc.lead(spaced,position,speed)
            if(speed < 1f) assertNull(direct)
            val nextLead=direct?.takeIf {it.targetIndex == originalLead.targetIndex}
                ?: Lrc.lead(spaced,spaced[originalLead.targetIndex].startTime-1,speed)!!
            assertEquals(originalLead.targetIndex,nextLead.targetIndex)
            assertTrue(nextLead.finishAtPosition < spaced[nextLead.targetIndex].startTime)
            assertEquals(20.0,(5000-nextLead.finishAtPosition)/speed.toDouble(),.00001)
            val remaining=nextLead.finishAtPosition-position
            assertTrue(remaining > 0)
            val rebased=LyricTransition(picture.weights,picture.offset,nextLead.targetIndex,100f,
                position,remaining,remaining)
            assertEquals(picture,rebased.sample(position))
            var lastWeight=picture.weights[1]!!
            var lastOffset=picture.offset
            for(at in position..nextLead.finishAtPosition) {
                val frame=rebased.sample(at)
                assertTrue(frame.weights[1]!! >= lastWeight)
                assertTrue(frame.offset >= lastOffset)
                lastWeight=frame.weights[1]!!
                lastOffset=frame.offset
            }
            val finished=rebased.sample(nextLead.finishAtPosition)
            assertEquals(mapOf(1 to 1f),finished.weights)
            assertEquals(100f,finished.offset,0f)
            assertFalse(finished.running)
        }
    }

    @Test fun visualLeadDoesNotChangeTheRealLrcIndexOrRestartAtItsTimestamp() {
        val spaced=listOf(LyricLine(0,"一"),LyricLine(1000,"二"),LyricLine(5000,"三"))
        val before=Lrc.lead(spaced,999)!!
        assertEquals(1,before.targetIndex)
        assertEquals(0,Lrc.current(spaced,999))
        assertNull(Lrc.lead(spaced,1000))
        assertEquals(before.targetIndex,Lrc.current(spaced,1000))
        assertEquals(1,Lrc.current(spaced,4500))
        assertNull(Lrc.lead(spaced,5000))
    }

    @Test fun emptyBeforeFirstTinyIntervalsAndInvalidSpeedsRemainFinite() {
        assertNull(Lrc.lead(emptyList(),0))
        assertNull(Lrc.lead(listOf(LyricLine(0,"一")),0))
        assertNull(Lrc.lead(listOf(LyricLine(0,"一"),LyricLine(1,"二")),0))
        val first=listOf(LyricLine(1000,"第一行"))
        assertNull(Lrc.lead(first,679))
        assertEquals(0,Lrc.lead(first,680)!!.targetIndex)
        for(speed in listOf(Float.NaN,Float.POSITIVE_INFINITY,0f,-1f)) {
            assertEquals(Lrc.lead(first,700),Lrc.lead(first,700,speed))
        }
        val huge=Lrc.lead(listOf(LyricLine(0,"一"),LyricLine(Long.MAX_VALUE,"二")),
            Long.MAX_VALUE-1,Float.MAX_VALUE)!!
        assertTrue(huge.startedAtPosition >= 0)
        assertTrue(huge.finishAtPosition < Long.MAX_VALUE)
        assertTrue(huge.durationMillis > 0)
        assertEquals(huge.finishAtPosition-huge.startedAtPosition,huge.durationMillis)
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
