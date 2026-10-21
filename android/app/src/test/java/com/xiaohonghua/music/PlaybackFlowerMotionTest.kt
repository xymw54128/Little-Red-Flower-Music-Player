package com.xiaohonghua.music

import org.junit.Assert.*
import org.junit.Test

class PlaybackFlowerMotionTest {
    private fun run(motion: PlaybackFlowerMotion,millis: Int,playing: Boolean,frame: Int=10) {
        repeat(millis/frame) {motion.advance(frame.toDouble(),playing)}
    }

    @Test fun playingSettlesAtConstantSpeed() {
        val motion=PlaybackFlowerMotion()
        run(motion,300,true)
        val start=motion.rotation
        run(motion,1000,true)
        assertEquals(48.0,motion.velocity,0.000001)
        assertEquals(48.0,motion.rotation-start,0.000001)
    }

    @Test fun detailPlayerUsesFasterCruiseWhileMiniDefaultIsUnchanged() {
        val detail=PlaybackFlowerMotion(60.0)
        val mini=PlaybackFlowerMotion()
        run(detail,300,true)
        run(mini,300,true)
        val detailStart=detail.rotation
        val miniStart=mini.rotation
        run(detail,1000,true)
        run(mini,1000,true)
        assertEquals(60.0,detail.velocity,0.000001)
        assertEquals(60.0,detail.rotation-detailStart,0.000001)
        assertEquals(48.0,mini.rotation-miniStart,0.000001)
        detail.advance(0.0,false)
        assertEquals(60.0,detail.velocity,0.0)
        run(detail,450,false)
        assertEquals(30.0,detail.velocity,0.000001)
        val brakingAngle=detail.rotation
        detail.advance(0.0,true)
        assertEquals(brakingAngle,detail.rotation,0.0)
        assertEquals(30.0,detail.velocity,0.000001)
        run(detail,240,true)
        assertEquals(60.0,detail.velocity,0.000001)
        run(detail,900,false)
        assertTrue(detail.atRest)
    }

    @Test fun pauseBrakesToRestWithoutSnapping() {
        val motion=PlaybackFlowerMotion()
        run(motion,300,true)
        val start=motion.rotation
        motion.advance(0.0,false)
        assertEquals(start,motion.rotation,0.0)
        assertEquals(48.0,motion.velocity,0.0)
        run(motion,450,false)
        assertEquals(24.0,motion.velocity,0.000001)
        assertTrue(motion.rotation > start)
        run(motion,450,false)
        assertEquals(start+21.6,motion.rotation,0.000001)
        assertTrue(motion.atRest)
        val stopped=motion.rotation
        run(motion,1000,false)
        assertEquals(stopped,motion.rotation,0.0)
    }

    @Test fun rapidPlayPauseKeepsBothAngleAndVelocityContinuous() {
        val motion=PlaybackFlowerMotion()
        run(motion,300,true)
        repeat(25) {index ->
            val playing=index%2 == 0
            val angle=motion.rotation
            val velocity=motion.velocity
            motion.advance(0.0,playing)
            assertEquals(angle,motion.rotation,0.0)
            assertEquals(velocity,motion.velocity,0.0)
            run(motion,40,playing)
            assertTrue(motion.velocity in 0.0..48.0)
        }
        run(motion,900,false)
        assertTrue(motion.atRest)
    }

    @Test fun integrationDoesNotDependOnDisplayRefreshRate() {
        val slow=PlaybackFlowerMotion()
        val fast=PlaybackFlowerMotion()
        for(playing in listOf(true,false,true,false)) {
            run(slow,960,playing,40)
            run(fast,960,playing,5)
            assertEquals(slow.rotation,fast.rotation,0.000001)
            assertEquals(slow.velocity,fast.velocity,0.000001)
        }
    }

    @Test fun stalledFrameDoesNotJumpAndInvalidTimeIsIgnored() {
        val motion=PlaybackFlowerMotion()
        run(motion,300,true)
        val start=motion.rotation
        motion.advance(120000.0,true)
        assertEquals(48.0*.064,motion.rotation-start,0.000001)
        val before=motion.rotation
        for(delta in listOf(Double.NaN,Double.POSITIVE_INFINITY,-10.0)) motion.advance(delta,true)
        assertEquals(before,motion.rotation,0.0)
    }

    @Test fun disabledAnimationsFreezeAndCanRestartFromSameAngle() {
        val motion=PlaybackFlowerMotion()
        run(motion,300,true)
        val angle=motion.rotation
        motion.freeze()
        assertEquals(angle,motion.rotation,0.0)
        assertTrue(motion.atRest)
        motion.advance(0.0,true)
        assertEquals(angle,motion.rotation,0.0)
        assertEquals(0.0,motion.velocity,0.0)
        run(motion,240,true)
        assertEquals(48.0,motion.velocity,0.000001)
    }
}
