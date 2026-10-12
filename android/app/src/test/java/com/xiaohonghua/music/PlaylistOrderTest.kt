package com.xiaohonghua.music

import org.junit.Assert.*
import org.junit.Test

class PlaylistOrderTest {
    @Test fun staleCustomDraftKeepsNewMembersAndCannotResurrectRemovedSongs() {
        assertEquals(listOf("c","a","new"),mergeSongOrder(listOf("c","removed","a","c"),listOf("a","c","new")))
    }

    @Test fun sourceSortReordersOnlyItsExistingSearchOrGroupQueue() {
        assertEquals(listOf("c","a"),orderedQueueIds(listOf("a","c"),LIBRARY_QUEUE_SOURCE,
            listOf("c","b","a","new"),LIBRARY_QUEUE_SOURCE))
    }

    @Test fun sameSongsFromAnotherPlaylistAndLegacyUnmarkedQueueStayUnchanged() {
        val queue=listOf("a","b")
        assertEquals(queue,orderedQueueIds(queue,"other",listOf("b","a"),"edited"))
        assertEquals(queue,orderedQueueIds(queue,null,listOf("b","a"),LIBRARY_QUEUE_SOURCE))
    }

    @Test fun queueSortPreservesDuplicateOccurrencesAndItemsNoLongerInTheSource() {
        assertEquals(listOf("b","b","a","removed"),orderedQueueIds(listOf("a","b","removed","b"),"drive",
            listOf("b","new","a"),"drive"))
    }

    @Test fun applyingMovesKeepsTheExactCurrentOccurrence() {
        val timeline=mutableListOf("a:0","b:0","gone:0","b:1")
        val current="b:1"
        val queue=timeline.map {it.substringBefore(':')}
        val ordered=orderedQueueIds(queue,"drive",listOf("b","a"),"drive")
        queueMovePlan(queue,ordered).forEach {(from,to) -> timeline.add(to,timeline.removeAt(from))}
        assertEquals(listOf("b:0","b:1","a:0","gone:0"),timeline)
        assertEquals(1,timeline.indexOf(current))
        assertTrue(queueMovePlan(ordered,ordered).isEmpty())
    }

    @Test fun batchAddUsesKnownIdsOnceWithoutChangingExistingMemberOrRandomOrder() {
        val original=Playlist("drive","开车",listOf("b","a"),SortMode.RANDOM,listOf("a","b"))
        assertEquals(original.copy(songIds=listOf("b","a","c"),randomIds=listOf("a","b","c")),
            addPlaylistMembers(original,listOf("a","missing","c","c"),setOf("a","b","c")))
    }

    @Test fun batchRemoveAndUndoPreserveLaterDetailsAndOnlyRestoreStillKnownSongs() {
        val original=Playlist("drive","旧名称",listOf("a","b","c","d"),SortMode.RANDOM,
            listOf("d","c","b","a"),description="旧简介")
        val removed=RemovedPlaylistSongs(original,listOf("b","c"))
        val after=removePlaylistMembers(original,setOf("b","c","missing"))
        assertEquals(listOf("a","d"),after.songIds)
        assertEquals(listOf("d","a"),after.randomIds)
        val current=after.copy(name="新名称",description="新简介",songIds=listOf("d","a","new"),sort=SortMode.CUSTOM,
            randomIds=listOf("a","d","new"),coverPreset=7)
        val restored=restoreRemovedPlaylistSongs(current,removed,setOf("a","b","d","new"))
        assertEquals(current.copy(songIds=listOf("b","d","a","new"),randomIds=listOf("b","a","d","new")),restored)
    }

    @Test fun undoDoesNotRecreateADeletedPlaylistOrRepositionManuallyReaddedSongs() {
        val original=Playlist("drive","开车",listOf("a","b","c"))
        val removed=RemovedPlaylistSongs(original,listOf("b","c"))
        val current=original.copy(songIds=listOf("c","a","b"),description="后续修改")
        assertEquals(current,restoreRemovedPlaylistSongs(current,removed,setOf("a","b","c")))
        val other=current.copy(id="other")
        assertEquals(other,restoreRemovedPlaylistSongs(other,removed,setOf("a","b","c")))
    }
}
