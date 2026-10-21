package com.xiaohonghua.music

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FavoriteMotionUiTest {
    @get:Rule val compose=createComposeRule()
    private val song=Song("favorite-motion","content://example/favorite","test.flac","花间")

    @Before fun emulatorOnly() {
        check(Build.HARDWARE in listOf("ranchu","goldfish")) {"收藏动画测试只能在模拟器中执行"}
        compose.mainClock.autoAdvance=false
    }
    private fun pixels(image: ImageBitmap): IntArray {
        val map=image.toPixelMap()
        return IntArray(image.width*image.height) {map[it%image.width,it/image.width].toArgb()}
    }
    @Test fun openingAnAlreadyLikedSongIsStillAndHasNoPressedBackdrop() {
        compose.setContent {MaterialTheme {
            Box(Modifier.background(Color.White)) {FavoriteButton(song,true) {}}
        }}
        val button=compose.onNodeWithTag("favorite-${song.id}")
        button.assertIsSelected().assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        val before=button.captureToImage()
        compose.mainClock.advanceTimeBy(640)
        assertArrayEquals(pixels(before),pixels(button.captureToImage()))
        button.performTouchInput {down(center)}
        compose.mainClock.advanceTimeBy(96)
        val pressed=button.captureToImage().toPixelMap()
        // This is outside the 20dp heart, inside the old 48dp circular ripple.
        assertEquals(Color.White.toArgb(),pressed[pressed.width/2,(pressed.height*.2f).toInt()].toArgb())
        button.performTouchInput {cancel()}
    }
    @Test fun rapidLikeAndUnlikeRestartsWithoutLosingClicksOrLeavingParticles() {
        val favorite=mutableStateOf(false)
        var clicks=0
        compose.setContent {MaterialTheme {
            Box(Modifier.background(Color.White)) {FavoriteButton(song,favorite.value) {
                favorite.value=!favorite.value
                clicks++
            }}
        }}
        val button=compose.onNodeWithTag("favorite-${song.id}")
        val resting=pixels(button.captureToImage())
        button.performClick()
        compose.mainClock.advanceTimeBy(160)
        button.assertIsSelected()
        val mid=button.captureToImage().toPixelMap()
        // A flourish extends beyond the resting heart; its backdrop remains transparent.
        assertTrue((0 until (mid.height*.28f).toInt()).any {y ->
            (0 until mid.width).any {x -> mid[x,y].toArgb() != Color.White.toArgb()}
        })
        button.performClick()
        compose.mainClock.advanceTimeBy(32)
        button.assertIsNotSelected().performClick()
        compose.mainClock.advanceTimeBy(32)
        button.assertIsSelected().performClick()
        compose.mainClock.advanceTimeBy(720)
        button.assertIsNotSelected()
        compose.runOnIdle {assertEquals(4,clicks)}
        assertArrayEquals(resting,pixels(button.captureToImage()))
    }
    @Test fun lastFavoritePersistsUnlikeImmediatelyButKeepsRowUntilHeartFinishes() {
        val favoriteIds=mutableStateOf(setOf(song.id))
        val songs=mutableStateOf(listOf(song))
        compose.setContent {MaterialTheme {
            PlaylistDetailContent(Playlist(FAVORITES_PLAYLIST_ID,"我喜欢",songIds=songs.value.map {it.id}),
                songs.value,songs.value.size,null,false,onPlay={},onSort={},onBulkAdd={},onAdd={},onInfo={},onRemove={},
                favoriteIds=favoriteIds.value,onFavorite={
                    favoriteIds.value=emptySet();songs.value=emptyList()
                })
        }}
        val button=compose.onNodeWithTag("favorite-${song.id}")
        button.assertIsSelected().performClick()
        compose.runOnIdle {assertTrue("The membership write is immediate",favoriteIds.value.isEmpty())}
        compose.mainClock.advanceTimeBy(400)
        button.assertExists().assertIsNotSelected()
        compose.onNodeWithText("歌单还没有歌曲").assertDoesNotExist()
        compose.mainClock.advanceTimeBy(240)
        button.assertExists() // The heart has finished; the row is now fading and collapsing.
        compose.mainClock.advanceTimeBy(320)
        button.assertDoesNotExist()
        compose.onNodeWithText("歌单还没有歌曲").assertIsDisplayed()
    }
    @Test fun likingAgainDuringRowCollapseReversesItsExit() {
        val favoriteIds=mutableStateOf(setOf(song.id))
        val songs=mutableStateOf(listOf(song))
        compose.setContent {MaterialTheme {
            PlaylistDetailContent(Playlist(FAVORITES_PLAYLIST_ID,"我喜欢",songIds=songs.value.map {it.id}),
                songs.value,songs.value.size,null,false,onPlay={},onSort={},onBulkAdd={},onAdd={},onInfo={},onRemove={},
                favoriteIds=favoriteIds.value,onFavorite={
                    val adding=it.id !in favoriteIds.value
                    favoriteIds.value=if(adding) setOf(it.id) else emptySet()
                    songs.value=if(adding) listOf(it) else emptyList()
                })
        }}
        val button=compose.onNodeWithTag("favorite-"+song.id)
        button.performClick()
        compose.mainClock.advanceTimeBy(672)
        button.assertExists().assertIsNotSelected().performClick()
        compose.mainClock.advanceTimeBy(960)
        button.assertIsSelected().assertIsDisplayed()
        compose.onNodeWithTag("playlist-song-count").assertTextEquals("共 1 首")
    }
    @Test fun likingAgainDuringOutgoingFavoriteFlourishCancelsRowRemoval() {
        val favoriteIds=mutableStateOf(setOf(song.id))
        val songs=mutableStateOf(listOf(song))
        compose.setContent {MaterialTheme {
            PlaylistDetailContent(Playlist(FAVORITES_PLAYLIST_ID,"我喜欢",songIds=songs.value.map {it.id}),
                songs.value,songs.value.size,null,false,onPlay={},onSort={},onBulkAdd={},onAdd={},onInfo={},onRemove={},
                favoriteIds=favoriteIds.value,onFavorite={
                    val adding=it.id !in favoriteIds.value
                    favoriteIds.value=if(adding) setOf(it.id) else emptySet()
                    songs.value=if(adding) listOf(it) else emptyList()
                })
        }}
        val button=compose.onNodeWithTag("favorite-${song.id}")
        button.performClick()
        compose.mainClock.advanceTimeBy(160)
        button.assertIsNotSelected().performClick()
        compose.mainClock.advanceTimeBy(720)
        button.assertIsSelected().assertIsDisplayed()
        compose.runOnIdle {assertEquals(setOf(song.id),favoriteIds.value)}
    }

}
