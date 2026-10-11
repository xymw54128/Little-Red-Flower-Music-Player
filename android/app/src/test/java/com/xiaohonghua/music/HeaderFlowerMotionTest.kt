package com.xiaohonghua.music

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class HeaderFlowerMotionTest {
    @Test fun libraryAcceleratesThenBrakesWithoutChangingItsLandingOrientation() {
        assertEquals(0f,HeaderFlowerMotion.sample("library",0L).rotation,0f)
        val quick=HeaderFlowerMotion.sample("library",500L).rotation-HeaderFlowerMotion.sample("library",400L).rotation
        val braking=HeaderFlowerMotion.sample("library",1600L).rotation-HeaderFlowerMotion.sample("library",1500L).rotation
        assertTrue(quick > braking*5f)
        assertEquals(0f,HeaderFlowerMotion.sample("library",2000L).rotation%360f,0f)
        assertTrue(abs(HeaderFlowerMotion.sample("library",2000L).rotation-HeaderFlowerMotion.sample("library",1999L).rotation) < .001f)
    }

    @Test fun libraryRestartPreservesTheInterruptedAngleAndFinishesAnotherFullSequence() {
        val interrupted=HeaderFlowerMotion.sample("library",650L)
        assertEquals(interrupted.rotation,HeaderFlowerMotion.sample("library",0L,interrupted).rotation,0f)
        assertTrue(HeaderFlowerMotion.sample("library",500L,interrupted).rotation > interrupted.rotation)
        assertEquals(0f,HeaderFlowerMotion.sample("library",2000L,interrupted).rotation%360f,0f)
    }

    @Test fun playlistsClearsTheOldFlowerThenRevealsTheCenterAndRedrawsItsContour() {
        assertEquals(1f,HeaderFlowerMotion.sample("playlists",0L).outlineAlpha,0f)
        val cleared=HeaderFlowerMotion.sample("playlists",200L)
        assertEquals(0f,cleared.outlineAlpha,0f)
        assertEquals(0f,cleared.centerAlpha,0f)
        val drawing=HeaderFlowerMotion.sample("playlists",550L)
        assertEquals(1f,drawing.centerAlpha,0f)
        assertTrue(drawing.outline in .001f.. .999f)
        assertEquals(1f,HeaderFlowerMotion.sample("playlists",2000L).outline,0f)
    }

    @Test fun playlistsRestartFadesTheActualPartialLineWithoutFlashingACompleteFlower() {
        val interrupted=HeaderFlowerMotion.sample("playlists",1000L)
        val restart=HeaderFlowerMotion.sample("playlists",0L,interrupted)
        assertEquals(interrupted.outline,restart.outline,0f)
        assertEquals(interrupted.outlineAlpha,restart.outlineAlpha,0f)
        assertEquals(interrupted.centerAlpha,restart.centerAlpha,0f)
        assertEquals(interrupted.centerScale,restart.centerScale,0f)
        assertEquals(interrupted.outline,HeaderFlowerMotion.sample("playlists",100L,interrupted).outline,0f)
    }

    @Test fun settingsPetalsSeparateTogetherAndReturnInOrder() {
        val detached=HeaderFlowerMotion.sample("settings",450L)
        detached.petals.forEach {assertEquals(32f,it,0f)}
        val landing=HeaderFlowerMotion.sample("settings",1100L)
        assertTrue(landing.petals[0] < landing.petals[1])
        assertTrue(landing.petals[1] < landing.petals[2])
        assertEquals(32f,landing.petals[3],0f)
        assertEquals(32f,landing.petals[4],0f)
        HeaderFlowerMotion.sample("settings",2000L).petals.forEach {assertEquals(0f,it,0f)}
    }

    @Test fun settingsRestartContinuesFromEachPetalsCurrentPosition() {
        val interrupted=HeaderFlowerMotion.sample("settings",1234L)
        assertEquals(interrupted.petals,HeaderFlowerMotion.sample("settings",0L,interrupted).petals)
        assertEquals(interrupted.centerScale,HeaderFlowerMotion.sample("settings",0L,interrupted).centerScale,0f)
        HeaderFlowerMotion.sample("settings",2000L,interrupted).petals.forEach {assertEquals(0f,it,0f)}
    }

    @Test fun rapidRestartAndIrregularFramesKeepAllValuesFiniteAndWithinDrawingBounds() {
        for(page in listOf("library","playlists","settings")) {
            var start=HeaderFlowerFrame()
            repeat(100) {tap ->
                val interrupted=HeaderFlowerMotion.sample(page,(tap*37L)%1500L,start)
                val restart=HeaderFlowerMotion.sample(page,0L,interrupted)
                assertEquals(interrupted.rotation,restart.rotation,0f)
                assertEquals(interrupted.outline,restart.outline,0f)
                assertEquals(interrupted.centerAlpha,restart.centerAlpha,0f)
                assertEquals(interrupted.petals,restart.petals)
                start=interrupted
            }
            for(hz in listOf(60,90,120)) {
                var time=3.0
                while(time < HeaderFlowerMotion.DURATION) {
                    val pose=HeaderFlowerMotion.sample(page,time.toLong(),start)
                    assertTrue(pose.rotation.isFinite())
                    assertTrue(pose.outline in 0f..1f && pose.outlineAlpha in 0f..1f && pose.centerAlpha in 0f..1f)
                    pose.petals.forEach {assertTrue(it in 0f..32f)}
                    time+=1000.0/hz
                }
            }
        }
    }
}
