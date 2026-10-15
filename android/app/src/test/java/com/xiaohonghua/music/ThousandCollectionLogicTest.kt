package com.xiaohonghua.music

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class ThousandCollectionLogicTest {
    private val songs=(0 until 1000).map {index ->
        val number=index.toString().padStart(4,'0')
        Song("song-$number","content://music/$number","$number.flac","歌曲 $number",artist="歌手 "+index%31,album="专辑 "+index%50)
    }
    private val ids=songs.map {it.id}
    private val known=ids.toSet()

    @Test fun thousandSongCustomDraftSurvivesRefreshAndDragsWithoutLosingMembers() {
        val source=ids.drop(2)+"new-song"
        val draft=ids.reversed()+listOf(ids.last(),"deleted")
        val merged=reconcileReorderIds(draft,source)
        assertEquals(ids.drop(2).reversed()+"new-song",merged)
        val moved=moveReorderId(merged,ids[500],0)
        val saved=mergeSongOrder(moved,source)
        assertEquals(moved,saved)
        assertEquals(source.toSet(),saved.toSet())
        assertEquals(source.size,saved.size)
        assertEquals(saved,orderedSongs(songs.filter {it.id in source}+song("new-song"),SortMode.CUSTOM,saved,emptyList()).map {it.id})
    }

    @Test fun duplicateCustomIdsUseTheFirstSavedPositionLikeMergedOrders() {
        val draft=listOf(ids[999],ids[0],ids[999])+ids.drop(1).dropLast(1)
        val expected=mergeSongOrder(draft,ids)
        assertEquals(expected,orderedSongs(songs,SortMode.CUSTOM,draft,emptyList()).map {it.id})
        assertEquals(expected,orderedSongs(songs,SortMode.RANDOM,emptyList(),draft).map {it.id})
    }

    @Test fun thousandSongBatchFavoritesAndPlaylistMembershipStayUniqueAndKeepOrder() {
        val favorite=Playlist(FAVORITES_PLAYLIST_ID,"我喜欢",ids.take(100),randomIds=ids.take(100).reversed())
        val added=addPlaylistMembers(favorite,ids.reversed()+ids+listOf("missing"),known)
        assertEquals(ids.take(100)+ids.drop(100).reversed(),added.songIds)
        assertEquals(1000,added.songIds.size)
        assertEquals(ids.take(100).reversed()+ids.drop(100).reversed(),added.randomIds)
        assertEquals(1000,added.randomIds.toSet().size)
        val removed=ids.filterIndexed {index,_ -> index%2 == 0}.toSet()
        val after=removePlaylistMembers(added,removed)
        assertEquals(500,after.songIds.size)
        assertEquals(after.songIds.toSet(),after.randomIds.toSet())
        val normalized=normalizeFavorites(listOf(added,Playlist("old-favorite","我喜欢",ids.reversed()+"missing")),known)
        assertEquals(1,normalized.count {it.isFavorites})
        assertEquals(added.songIds,normalized.first().songIds)
        val toggled=toggleFavorite(normalized,ids[999])
        assertEquals(999,toggled.first().songIds.size)
        assertEquals(1000,toggleFavorite(toggled,ids[999]).first().songIds.size)
    }

    @Test fun batchUndoOfAThousandSongsRestoresBothOrdersAndPreservesLaterMetadata() {
        val original=Playlist("collection","原名称",ids,SortMode.RANDOM,ids.reversed(),description="原简介")
        val deleted=removePlaylistMembers(original,known).copy(name="新名称",description="新简介",coverPhoto="new.jpg")
        val restored=restoreRemovedPlaylistSongs(deleted,RemovedPlaylistSongs(original,ids.shuffled(Random(9))+ids.take(3)),known)
        assertEquals(deleted.copy(songIds=ids,randomIds=ids.reversed()),restored)
        val missing=known-ids[400]
        val selected=ids.filterIndexed {index,_ -> index%2 == 0}
        val partial=removePlaylistMembers(original,selected.toSet())
        assertEquals(original.copy(songIds=ids.filter {it in missing},randomIds=ids.reversed().filter {it in missing}),
            restoreRemovedPlaylistSongs(partial,RemovedPlaylistSongs(original,selected),missing))
    }

    @Test fun batchUndoMatchesIndividualAnchorsAfterLaterReordersAndInsertions() {
        val random=Random(86)
        repeat(12) {round ->
            val original=Playlist("collection","原名称",ids.shuffled(random),SortMode.CUSTOM,ids.shuffled(random))
            val selected=ids.shuffled(random).take(600)
            val current=removePlaylistMembers(original,selected.toSet()).copy(name="已编辑",description="后续简介")
                .let {if(round%2 == 0) it.copy(songIds=(it.songIds+"new").shuffled(random),randomIds=(it.randomIds+"new").shuffled(random)) else it}
            val allowed=known-ids[50]+"new"
            val expected=selected.fold(current) {value,id -> restoreRemovedPlaylistSong(value,RemovedPlaylistSong(original,id),allowed)}
            assertEquals(expected,restoreRemovedPlaylistSongs(current,RemovedPlaylistSongs(original,selected),allowed))
        }
    }

    @Test fun thousandSongQueueReordersPreserveEveryOccurrenceAndIgnoreAnotherSource() {
        val current=ids+listOf(ids[100],ids[900],"outside")
        val target=orderedQueueIds(current,"collection",ids.reversed(),"collection")
        val occurrences=current.mapIndexed {index,id -> "$id:$index"}.toMutableList()
        val audible=occurrences[1000]
        queueMovePlan(current,target).forEach {(from,to) -> occurrences.add(to,occurrences.removeAt(from))}
        assertEquals(target,occurrences.map {it.substringBefore(':')})
        assertEquals(current.size,occurrences.size)
        assertEquals(current.indices.toSet(),occurrences.map {it.substringAfter(':').toInt()}.toSet())
        assertTrue(audible in occurrences)
        assertEquals(current,orderedQueueIds(current,"other",ids.reversed(),"collection"))
        assertTrue(queueMovePlan(target,target).isEmpty())
        val incoming=queueIncomingOrder(current,ids[500],ids[100],QueuePlacement.NEXT)
        assertEquals(ids[100],incoming[incoming.indexOf(ids[500])+1])
        assertEquals(1,incoming.count {it == ids[100]})
        assertEquals(current.filterNot {it == ids[100]}.toSet(),incoming.filterNot {it == ids[100]}.toSet())
        assertEquals(current,queueIncomingOrder(current,ids[500],ids[500],QueuePlacement.LAST))
    }

    @Test fun duplicateScansOfAThousandFilesProduceOneLatestEntryPerIdAndUnionSources() {
        val existing=songs.map {it.copy(sources=setOf("folder-a"))}+songs.take(20).map {it.copy(sources=setOf("folder-b"))}
        val fresh=songs.map {it.copy(title="新标签 "+it.title)}
        val scanned=fresh+fresh.take(40).map {it.copy(title="最新标签 "+it.title)}
        val reconciled=reconcileSongs(existing,scanned,"folder-c")
        assertEquals(ids,reconciled.map {it.id})
        assertEquals(1000,reconciled.size)
        assertEquals(setOf("folder-a","folder-b","folder-c"),reconciled.first().sources)
        assertEquals("最新标签 新标签 歌曲 0000",reconciled.first().title)
        assertEquals(setOf("folder-a","folder-c"),reconciled[50].sources)
        val initial=reconcileSongs(emptyList(),scanned,"folder-c")
        assertEquals(ids,initial.map {it.id})
        assertEquals(1000,initial.size)
        val removed=reconcileSongs(reconciled,emptyList(),"folder-c")
        assertEquals(1000,removed.size)
        assertTrue(removed.all {"folder-c" !in it.sources})
    }

    @Test fun allSortModesKeepAThousandDistinctSongsAndAlbumOrderUsesNumericTags() {
        val shuffled=songs.shuffled(Random(4))
        for(mode in SortMode.entries) {
            val ordered=orderedSongs(shuffled,mode,ids.reversed(),ids.shuffled(Random(3)))
            assertEquals(1000,ordered.size)
            assertEquals(known,ordered.map {it.id}.toSet())
        }
        val album=songs.mapIndexed {index,song -> song.copy(disc=(index/100+1).toString()+"/10",track=(index%100+1).toString()+"/100")}.shuffled(Random(6))
        assertEquals(ids,albumSongs(album).map {it.id})
    }

    private fun song(id: String) = Song(id,"content://music/$id","$id.flac",id)
}
