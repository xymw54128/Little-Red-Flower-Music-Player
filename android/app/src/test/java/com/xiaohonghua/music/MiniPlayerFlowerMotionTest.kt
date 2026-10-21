package com.xiaohonghua.music

import org.junit.Assert.*
import org.junit.Test

class MiniPlayerFlowerMotionTest {
    private fun advance(motion: PlaybackFlowerMotion,millis: Double,playing: Boolean,hz: Int) {
        val frame=1000.0/hz
        var elapsed=0.0
        while(elapsed < millis-.000001) {
            val step=minOf(frame,millis-elapsed)
            motion.advance(step,playing)
            elapsed+=step
        }
    }

    @Test fun petalsTakeTurnsInOrderTwicePerRotationAndNeverHopTogether() {
        for(step in 0..13) {
            val angle=(step+.5)*360.0/14.0
            val hops=(0..6).map {miniPlayerPetalHop(angle,48.0,it)}
            assertEquals(listOf(step%7),hops.indices.filter {hops[it] > .000001})
            assertEquals(4.2,hops[step%7],.000001)
        }
        for(sample in 0..1440) {
            val hops=(0..6).map {miniPlayerPetalHop(sample*.25,48.0,it)}
            assertTrue(hops.count {it > .000001} <= 1)
        }
    }

    @Test fun handoversAndFullRotationWrapHaveNoJumpOrVelocityKink() {
        for(step in 0..14) {
            val angle=step*360.0/14.0
            for(index in 0..6) {
                assertEquals(0.0,miniPlayerPetalHop(angle,48.0,index),.000001)
                assertTrue(miniPlayerPetalHop(angle-.0001,48.0,index) < .000001)
                assertTrue(miniPlayerPetalHop(angle+.0001,48.0,index) < .000001)
            }
        }
        for(index in 0..6) {
            assertEquals(miniPlayerPetalHop(352.0,48.0,index),miniPlayerPetalHop(-8.0,48.0,index),.000001)
            assertEquals(miniPlayerPetalHop(8.0,48.0,index),miniPlayerPetalHop(368.0,48.0,index),.000001)
        }
        assertEquals(0.0,miniPlayerPetalHop(10.0,48.0,-1),0.0)
        assertEquals(0.0,miniPlayerPetalHop(10.0,48.0,7),0.0)
    }

    @Test fun petalsHaveTheSameSteadyMotionAt48And60And120Hz() {
        val samples=listOf(48,60,120).map {hz ->
            PlaybackFlowerMotion().also {motion ->
                advance(motion,1000.0,true,hz)
                val start=motion.rotation
                advance(motion,1000.0,true,hz)
                assertEquals(48.0,motion.rotation-start,.000001)
            }
        }
        for(motion in samples.drop(1)) {
            assertEquals(samples.first().rotation,motion.rotation,.000001)
            for(index in 0..6) assertEquals(miniPlayerPetalHop(samples.first().rotation,48.0,index),
                miniPlayerPetalHop(motion.rotation,48.0,index),.000001)
        }
    }

    @Test fun pauseGraduallyRemovesTheHopAndKeepsTheFinalPose() {
        val motion=PlaybackFlowerMotion()
        advance(motion,400.0,true,60)
        val start=motion.rotation
        advance(motion,450.0,false,60)
        assertEquals(24.0,motion.velocity,.000001)
        assertTrue((0..6).any {miniPlayerPetalHop(motion.rotation,motion.velocity,it) > .1})
        assertTrue((0..6).all {miniPlayerPetalHop(motion.rotation,motion.velocity,it) in 0.0..2.1})
        advance(motion,450.0,false,60)
        assertEquals(start+21.6,motion.rotation,.000001)
        // A fractional frame can finish a floating point epsilon short of 900 ms.
        advance(motion,1.0,false,60)
        assertTrue((0..6).all {miniPlayerPetalHop(motion.rotation,motion.velocity,it) == 0.0})
        val stopped=motion.rotation
        advance(motion,1000.0,false,120)
        assertEquals(stopped,motion.rotation,0.0)
    }

    @Test fun rapidRetargetKeepsThePetalPoseContinuousAndClampsAmplitude() {
        val motion=PlaybackFlowerMotion()
        advance(motion,400.0,true,120)
        repeat(30) {index ->
            val before=(0..6).map {miniPlayerPetalHop(motion.rotation,motion.velocity,it)}
            motion.advance(0.0,index%2 == 0)
            assertEquals(before,(0..6).map {miniPlayerPetalHop(motion.rotation,motion.velocity,it)})
            advance(motion,35.0,index%2 == 0,120)
            assertTrue((0..6).all {miniPlayerPetalHop(motion.rotation,motion.velocity,it) in 0.0..4.2})
        }
        assertEquals(0.0,miniPlayerPetalHop(180.0,Double.NaN,2),0.0)
        assertEquals(0.0,miniPlayerPetalHop(Double.NaN,48.0,2),0.0)
    }

    @Test fun progressUsesTheRealPositionForSeekAndTrackChanges() {
        assertEquals(.25f,miniPlayerProgress(30000,120000),0f)
        assertEquals(.75f,miniPlayerProgress(90000,120000),0f)
        assertEquals(0f,miniPlayerProgress(0,180000),0f)
        assertEquals(1f,miniPlayerProgress(Long.MAX_VALUE,180000),0f)
        assertEquals(0f,miniPlayerProgress(-100,180000),0f)
        assertEquals(0f,miniPlayerProgress(1000,0),0f)
        assertEquals(0f,miniPlayerProgress(1000,-1),0f)
    }
}
