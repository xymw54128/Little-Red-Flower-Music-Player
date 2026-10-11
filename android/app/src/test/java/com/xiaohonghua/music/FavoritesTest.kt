package com.xiaohonghua.music

import org.junit.Assert.*
import org.junit.Test

class FavoritesTest {
    @Test fun emptyLibraryAlwaysHasOneDefaultPlaylist() {
        val playlists = normalizeFavorites(emptyList())
        assertEquals(1, playlists.size)
        assertEquals(FAVORITES_PLAYLIST_ID, playlists.single().id)
        assertEquals("我喜欢", playlists.single().name)
        assertTrue(playlists.single().isFavorites)
        assertTrue(playlists.single().songIds.isEmpty())
        assertEquals(FAVORITES_FLOWER_COVER,playlists.single().coverPreset)
    }

    @Test fun existingNamedPlaylistKeepsCoverAndCustomOrderWhenAdopted() {
        val old = Playlist("old-id", "我喜欢", listOf("c", "a", "b"), SortMode.CUSTOM,
            listOf("b", "a", "c"), coverPreset=6, coverPhoto="existing.jpg")
        val favorite = normalizeFavorites(listOf(old)).single()
        assertEquals(old.copy(id=FAVORITES_PLAYLIST_ID), favorite)
    }

    @Test fun duplicatesMergeAfterCanonicalOrderAndKeepExistingPhoto() {
        val legacy = Playlist("legacy", " 我喜欢 ", listOf("a", "c", "a"), SortMode.CUSTOM,
            listOf("c", "a"), coverPreset=6, coverPhoto="photo.jpg")
        val canonical = Playlist(FAVORITES_PLAYLIST_ID, "曾经被改名", listOf("b", "a"), SortMode.RANDOM,
            listOf("a", "b", "missing"), coverPreset=3)
        val favorite = normalizeFavorites(listOf(legacy, canonical)).single()
        assertEquals("我喜欢", favorite.name)
        assertEquals(listOf("b", "a", "c"), favorite.songIds)
        assertEquals(listOf("a", "b", "c"), favorite.randomIds)
        assertEquals(SortMode.RANDOM, favorite.sort)
        assertEquals(6, favorite.coverPreset)
        assertEquals("photo.jpg", favorite.coverPhoto)
    }

    @Test fun ordinaryPlaylistsStayUnchangedAndFavoritesComeFirst() {
        val ordinary = Playlist("study", "学习", listOf("b", "a"), coverPreset=2, coverPhoto="study.jpg")
        val favorite = Playlist("legacy", "我喜欢", listOf("c"))
        val playlists = normalizeFavorites(listOf(ordinary, favorite))
        assertTrue(playlists.first().isFavorites)
        assertEquals(ordinary, playlists[1])
        assertFalse(ordinary.isFavorites)
    }

    @Test fun normalizationIsIdempotentWithAndWithoutKnownSongIds() {
        val initial = listOf(Playlist("legacy", "我喜欢", listOf("b", "a", "b", "missing")))
        val normalized = normalizeFavorites(initial)
        assertEquals(normalized, normalizeFavorites(normalized))
        val pruned = normalizeFavorites(initial, setOf("a", "b"))
        assertEquals(pruned, normalizeFavorites(pruned, setOf("a", "b")))
    }

    @Test fun toggleAddsThenRemovesOneMembershipWithoutASeparateFlag() {
        val ordinary = Playlist("other", "其他", listOf("song"))
        val added = toggleFavorite(listOf(ordinary), "song")
        assertEquals(listOf("song"), added.first().songIds)
        assertEquals(listOf("song"), added.first().randomIds)
        assertEquals(ordinary, added[1])
        val removed = toggleFavorite(added, "song")
        assertTrue(removed.first().songIds.isEmpty())
        assertTrue(removed.first().randomIds.isEmpty())
        assertEquals(ordinary, removed[1])
    }

    @Test fun togglingPreservesRemainingCustomAndRandomOrders() {
        val favorite = Playlist(FAVORITES_PLAYLIST_ID, "我喜欢", listOf("c", "a", "b"), SortMode.RANDOM,
            listOf("b", "c", "a"), coverPreset=5, coverPhoto="photo.jpg")
        val removed = toggleFavorite(listOf(favorite), "a").single()
        assertEquals(listOf("c", "b"), removed.songIds)
        assertEquals(listOf("b", "c"), removed.randomIds)
        val added = toggleFavorite(listOf(removed), "d").single()
        assertEquals(listOf("c", "b", "d"), added.songIds)
        assertEquals(listOf("b", "c", "d"), added.randomIds)
        assertEquals(favorite.sort, added.sort)
        assertEquals(favorite.coverPhoto, added.coverPhoto)
    }

    @Test fun deletedSongsArePrunedButUnavailableBackupSongsRemainFavorites() {
        val songs = listOf(Song("available", "content://one", "one.flac", "一"),
            Song("unavailable", "content://two", "two.flac", "二", available=false))
        val playlist = Playlist(FAVORITES_PLAYLIST_ID, "我喜欢", listOf("deleted", "unavailable", "available"),
            randomIds=listOf("available", "deleted", "unavailable", "non-member"))
        val normalized = normalizeFavorites(listOf(playlist), songs.map { it.id }.toSet()).single()
        assertEquals(listOf("unavailable", "available"), normalized.songIds)
        assertEquals(listOf("available", "unavailable"), normalized.randomIds)
    }

    @Test fun schemaOneBackupPlaylistsAdoptDefaultAndDeduplicateMembership() {
        val backupPlaylists = listOf(Playlist("old-like", "我喜欢", listOf("b", "b", "missing")),
            Playlist("second-like", "我喜欢", listOf("a", "b")), Playlist("drive", "开车", listOf("a")))
        val restored = normalizeFavorites(backupPlaylists, setOf("a", "b"))
        assertEquals(listOf(FAVORITES_PLAYLIST_ID, "drive"), restored.map { it.id })
        assertEquals(listOf("b", "a"), restored.first().songIds)
        assertEquals(restored, normalizeFavorites(restored, setOf("a", "b")))
        assertTrue(normalizeFavorites(listOf(backupPlaylists.last()), setOf("a", "b")).first().isFavorites)
    }

    @Test fun oldDefaultFavoriteFlowerUpgradePreservesOrderAndMembership() {
        val old=Playlist(FAVORITES_PLAYLIST_ID,"我喜欢",listOf("b","a"),SortMode.RANDOM,
            listOf("a","b"),coverPreset=(FAVORITES_PLAYLIST_ID.hashCode() and Int.MAX_VALUE)%8)
        val upgraded=upgradeFavoriteFlower(old)
        assertEquals(old.copy(coverPreset=FAVORITES_FLOWER_COVER),upgraded)
        assertEquals(upgraded,upgradeFavoriteFlower(upgraded))
    }

    @Test fun flowerUpgradeDoesNotReplacePhotosOrChosenLegacyPresets() {
        val oldDefault=(FAVORITES_PLAYLIST_ID.hashCode() and Int.MAX_VALUE)%8
        val photo=Playlist(FAVORITES_PLAYLIST_ID,"我喜欢",coverPreset=oldDefault,coverPhoto="photo.jpg")
        val chosen=photo.copy(coverPreset=(oldDefault+1)%8,coverPhoto="")
        val ordinary=photo.copy(id="ordinary",coverPhoto="")
        assertEquals(photo,upgradeFavoriteFlower(photo))
        assertEquals(chosen,upgradeFavoriteFlower(chosen))
        assertEquals(ordinary,upgradeFavoriteFlower(ordinary))
    }

    @Test fun newPlaylistDefaultsUseAllTenFlowerPresetsAndStayInRange() {
        val presets=(0..1000).map {defaultPlaylistCover("playlist-"+it)}.toSet()
        assertEquals((8..17).toSet(),presets)
        assertEquals(18,defaultPlaylistCover(FAVORITES_PLAYLIST_ID))
        assertEquals(19,PLAYLIST_COVER_COUNT)
    }
}
