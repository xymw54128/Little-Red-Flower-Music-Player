package com.xiaohonghua.music

import org.junit.Assert.*
import org.junit.Test

class PlaylistUndoTest {
    private val favorites = Playlist(FAVORITES_PLAYLIST_ID,"我喜欢")
    private val known = setOf("a","b","c","d","e")

    @Test fun deletedPlaylistReturnsToItsPositionWithIdentityCoverAndOrders() {
        val deleted = Playlist("drive","开车",listOf("c","a","b"),SortMode.RANDOM,
            listOf("b","c","a"),coverPreset=5,coverPhoto="photo.jpg")
        val before = listOf(favorites,Playlist("first","第一"),deleted,Playlist("last","最后"))
        val restored = restoreDeletedPlaylist(before-deleted,DeletedPlaylist(deleted,2),known)
        assertEquals(before,restored)
    }

    @Test fun deletedPlaylistDoesNotResurrectMissingSongs() {
        val deleted = Playlist("drive","开车",listOf("a","deleted","b"),SortMode.RANDOM,
            listOf("deleted","b","a","non-member"),coverPreset=6,coverPhoto="photo.jpg")
        val restored = restoreDeletedPlaylist(listOf(favorites),DeletedPlaylist(deleted,1),known).last()
        assertEquals(deleted.copy(songIds=listOf("a","b"),randomIds=listOf("b","a")),restored)
    }

    @Test fun restoringAnExistingIdDoesNotOverwriteItsLaterEdits() {
        val old = Playlist("drive","旧名称",listOf("a"),coverPhoto="old.jpg")
        val current = old.copy(name="新名称",songIds=listOf("b"),coverPhoto="new.jpg")
        val playlists = listOf(favorites,current)
        assertEquals(playlists,restoreDeletedPlaylist(playlists,DeletedPlaylist(old,1),known))
    }

    @Test fun defaultFavoritesCannotBeRestoredAsADeletedPlaylist() {
        val playlists = listOf(favorites,Playlist("ordinary","歌单"))
        assertEquals(playlists,restoreDeletedPlaylist(playlists,DeletedPlaylist(favorites,0),known))
    }

    @Test fun deletedPlaylistPositionIsClampedAfterOtherPlaylistsDisappear() {
        val deleted = Playlist("drive","开车")
        assertEquals(listOf(favorites,deleted),restoreDeletedPlaylist(listOf(favorites),DeletedPlaylist(deleted,8),known))
    }

    @Test fun removedSongRestoresItsCustomAndRandomPositions() {
        val before = Playlist("drive","开车",listOf("a","b","c"),SortMode.RANDOM,listOf("c","b","a"))
        val current = before.copy(songIds=listOf("a","c"),randomIds=listOf("c","a"))
        assertEquals(before,restoreRemovedPlaylistSong(current,RemovedPlaylistSong(before,"b"),known))
    }

    @Test fun removedSongUndoPreservesLaterNameCoverSortAndOtherMembershipChanges() {
        val before = Playlist("drive","旧名称",listOf("a","b","c","d"),SortMode.RANDOM,
            listOf("d","b","a","c"),coverPhoto="old.jpg")
        val current = before.copy(name="新名称",songIds=listOf("d","c","a","e"),sort=SortMode.CUSTOM,
            randomIds=listOf("c","a","d","e"),coverPreset=7,coverPhoto="new.jpg")
        val restored = restoreRemovedPlaylistSong(current,RemovedPlaylistSong(before,"b"),known)
        assertEquals(current.copy(songIds=listOf("d","b","c","a","e"),randomIds=listOf("c","b","a","d","e")),restored)
    }

    @Test fun removedSongUsesSurvivingNeighborsWithoutRestoringOtherRemovedSongs() {
        val before = Playlist("drive","开车",listOf("a","b","c","d"),randomIds=listOf("d","c","b","a"))
        val current = before.copy(songIds=listOf("a","e"),randomIds=listOf("e","a"))
        val restored = restoreRemovedPlaylistSong(current,RemovedPlaylistSong(before,"b"),known)
        assertEquals(listOf("a","b","e"),restored.songIds)
        assertEquals(listOf("e","b","a"),restored.randomIds)
    }

    @Test fun removedSongFallsBackToClampedPositionWhenNoOriginalNeighborsRemain() {
        val before = Playlist("drive","开车",listOf("a","b","c"))
        val current = before.copy(songIds=listOf("e"))
        val restored = restoreRemovedPlaylistSong(current,RemovedPlaylistSong(before,"b"),known)
        assertEquals(listOf("e","b"),restored.songIds)
        assertTrue(restored.randomIds.isEmpty())
    }

    @Test fun manuallyReaddedSongIsNotDuplicatedOrRepositionedByUndo() {
        val before = Playlist("drive","开车",listOf("a","b","c"))
        val current = before.copy(songIds=listOf("c","a","b"))
        assertEquals(current,restoreRemovedPlaylistSong(current,RemovedPlaylistSong(before,"b"),known))
    }

    @Test fun deletedSongOrDifferentParentPlaylistCannotBeRestored() {
        val before = Playlist("drive","开车",listOf("a","b","c"))
        val current = before.copy(songIds=listOf("a","c"))
        val removed = RemovedPlaylistSong(before,"b")
        assertEquals(current,restoreRemovedPlaylistSong(current,removed,known-"b"))
        val different = current.copy(id="other")
        assertEquals(different,restoreRemovedPlaylistSong(different,removed,known))
    }

    @Test fun unavailableSongMetadataCanStillReturnToFavorites() {
        val unavailable = Song("b","content://two","two.flac","二",available=false)
        val before = favorites.copy(songIds=listOf("b"))
        assertEquals(before,restoreRemovedPlaylistSong(favorites,RemovedPlaylistSong(before,"b"),setOf(unavailable.id)))
    }
}
