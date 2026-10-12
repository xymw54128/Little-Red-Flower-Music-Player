package com.xiaohonghua.music

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Synthetic covers and JSON only; never changes the user's stored library or photos. */
@RunWith(AndroidJUnit4::class)
class PlaylistCoverUiTest {
    @get:Rule val compose=createComposeRule()

    @Test fun pickerOffersOnlyElevenNaturalFlowersAndCornflowerKeepsIdSeventeen() {
        val playlist=mutableStateOf(Playlist("cover-test","封面测试",coverPreset=0))
        val chosen=mutableListOf<Int>()
        compose.setContent {MaterialTheme {
            PlaylistCoverSheet(playlist.value,onDismiss={},onPreset={id ->
                chosen+=id;playlist.value=playlist.value.copy(coverPreset=id,coverPhoto="")
            },onPhoto={})
        }}
        (0..7).forEach {compose.onNodeWithTag("playlist-cover-preset-$it").assertDoesNotExist()}
        (8..18).forEach {compose.onNodeWithTag("playlist-cover-preset-$it").assertExists()}
        compose.onNodeWithText("蓝罂粟").assertDoesNotExist()
        compose.onNodeWithTag("playlist-cover-preset-17").performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithText("矢车菊").assertIsDisplayed()
        compose.runOnIdle {assertEquals(listOf(17),chosen)}
    }

    @Test fun oldJsonPresetsMigrateWhilePhotoDescriptionMembershipAndOrderSurvive() {
        val photo="12345678-1234-1234-1234-123456789abc.jpg"
        val old=JSONObject().put("id","saved-playlist").put("name","旧歌单").put("coverPreset",3)
            .put("coverPhoto",photo).put("description","春日午后").put("sort",SortMode.CUSTOM.name)
            .put("songIds",JSONArray(listOf("b","a"))).put("randomIds",JSONArray(listOf("a","b")))
        val migrated=playlistFromJson(old)
        assertEquals(defaultPlaylistCover("saved-playlist"),migrated.coverPreset)
        assertEquals(photo,migrated.coverPhoto)
        assertEquals("春日午后",migrated.description)
        assertEquals(listOf("b","a"),migrated.songIds)
        assertEquals(listOf("a","b"),migrated.randomIds)
        assertEquals(migrated,playlistFromJson(migrated.toJson()))
    }

    @Test fun allNaturalFlowerJsonPresetsStayStableAcrossAnUpgrade() {
        (8..18).forEach {preset ->
            val old=JSONObject().put("id","old-$preset").put("name","已有花朵").put("coverPreset",preset)
            assertEquals(preset,playlistFromJson(old).coverPreset)
        }
        val favorite=JSONObject().put("id",FAVORITES_PLAYLIST_ID).put("name","我喜欢").put("coverPreset",6)
        assertEquals(FAVORITES_FLOWER_COVER,playlistFromJson(favorite).coverPreset)
    }
}
