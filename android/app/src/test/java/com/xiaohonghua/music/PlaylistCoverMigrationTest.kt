package com.xiaohonghua.music

import org.junit.Assert.*
import org.junit.Test

class PlaylistCoverMigrationTest {
    @Test fun everyRetiredPresetBecomesANaturalFlowerAndMigrationIsStable() {
        listOf("one","two",FAVORITES_PLAYLIST_ID).forEach {id ->
            (0..7).forEach {retired ->
                val migrated=normalizePlaylistCover(id,retired)
                assertEquals(defaultPlaylistCover(id),migrated)
                assertTrue(migrated in 8..18)
                assertEquals(migrated,normalizePlaylistCover(id,migrated))
            }
        }
    }

    @Test fun allExistingNaturalFlowersKeepTheirSavedIds() {
        (8..18).forEach {preset ->
            assertEquals(preset,normalizePlaylistCover("ordinary",preset))
            assertEquals(preset,normalizePlaylistCover(FAVORITES_PLAYLIST_ID,preset))
        }
    }

    @Test fun corruptIdsFallBackWithoutChangingFavoritesDefault() {
        listOf(-1,19,Int.MIN_VALUE,Int.MAX_VALUE).forEach {preset ->
            assertEquals(defaultPlaylistCover("ordinary"),normalizePlaylistCover("ordinary",preset))
            assertEquals(FAVORITES_FLOWER_COVER,normalizePlaylistCover(FAVORITES_PLAYLIST_ID,preset))
        }
    }
}
