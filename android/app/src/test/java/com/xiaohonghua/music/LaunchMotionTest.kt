package com.xiaohonghua.music

import org.junit.Assert.*
import org.junit.Test

class LaunchMotionTest {
    @Test fun petalsGrowInOrderAndFinishBeforeTheFlowerTurns() {
        assertEquals(0f,LaunchMotion.petal(0,0),0f)
        assertEquals(0f,LaunchMotion.caption(0),0f)
        for(index in 0..4) {
            val start=220L+index*150L
            assertEquals(0f,LaunchMotion.petal(start,index),0f)
            assertTrue(LaunchMotion.petal(start+100,index) > 0f)
            if(index < 4) assertEquals(0f,LaunchMotion.petal(start+100,index+1),0f)
            assertEquals(1f,LaunchMotion.petal(start+260,index),0f)
        }
        assertTrue(LaunchMotion.caption(320) > 0f)
        assertEquals(1f,LaunchMotion.caption(600),0f)
        for(index in 0..4) assertEquals(1f,LaunchMotion.petal(1120,index),0f)
    }

    @Test fun flightAndFadeReachTheirEndpointsWithoutAnAbruptJump() {
        assertEquals(0f,LaunchMotion.flight(1500),0f)
        assertEquals(1f,LaunchMotion.flight(2340),0f)
        assertEquals(1f,LaunchMotion.background(1460),0f)
        assertEquals(0f,LaunchMotion.background(1800),0f)
        assertEquals(1f,LaunchMotion.caption(1500),0f)
        assertEquals(0f,LaunchMotion.caption(2000),0f)
        for(boundary in listOf(1120L,1460L,1500L,2340L,2640L)) {
            assertTrue(kotlin.math.abs(LaunchMotion.rotation(boundary+1)-LaunchMotion.rotation(boundary-1)) < .05f)
            assertTrue(kotlin.math.abs(LaunchMotion.flight(boundary+1)-LaunchMotion.flight(boundary-1)) < .01f)
        }
        // Five equal petals land with the same orientation as the static icon.
        assertEquals(0f,LaunchMotion.rotation(LaunchMotion.DURATION)%72f,0f)
    }

    @Test fun irregularDisplayFramesUseOneContinuousTimeLine() {
        for(hz in listOf(60,90,120)) for(phase in 0..16) {
            var previous=0f
            var time=phase.toDouble()
            while(time < LaunchMotion.DURATION) {
                val fraction=LaunchMotion.flight(time.toLong())
                assertTrue(fraction in 0f..1f && fraction >= previous)
                previous=fraction
                time+=1000.0/hz
            }
            assertEquals(1f,LaunchMotion.flight(time.toLong()),0f)
        }
    }
}
