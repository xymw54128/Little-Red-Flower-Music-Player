package com.xiaohonghua.music

import java.net.URLEncoder
import org.junit.Assert.*
import org.junit.Test

class PlaybackSourceMatrixTest {
    private val songs=listOf(
        Song("a","content://music/a","a.flac","Cedar",artist="Beta"),
        Song("b","content://music/b","b.flac","Apple",artist="Delta"),
        Song("c","content://music/c","c.flac","Birch",artist="Gamma"),
        Song("d","content://music/d","d.flac","Dahlia",artist="Alpha"),
    )
    private fun library(mode: SortMode=SortMode.CUSTOM)=LibraryState(
        songs=songs,sort=mode,custom=listOf("c","d","b","a"),random=listOf("a","c","d","b"),
        playlists=listOf(
            Playlist(FAVORITES_PLAYLIST_ID,"我喜欢",listOf("a","c","b"),mode,listOf("b","a","c")),
            Playlist("A","出发",listOf("a","d","c"),mode,listOf("c","d","a")),
            Playlist("B","回家",listOf("c","b","d"),mode,listOf("d","c","b")),
        ),
    )
    private fun ids(state: LibraryState,source: String,selection: Set<String>?=null)=
        playbackSourceSongs(state,source,selection).map {it.id}

    @Test fun allFourSortModesHaveExplicitOrdersForLibraryFavoritesAndBothPlaylists() {
        val expected=mapOf(
            SortMode.TITLE to mapOf("library" to listOf("b","c","a","d"),"favorites" to listOf("b","c","a"),
                "A" to listOf("c","a","d"),"B" to listOf("b","c","d")),
            SortMode.ARTIST to mapOf("library" to listOf("d","a","b","c"),"favorites" to listOf("a","b","c"),
                "A" to listOf("d","a","c"),"B" to listOf("d","b","c")),
            SortMode.CUSTOM to mapOf("library" to listOf("c","d","b","a"),"favorites" to listOf("a","c","b"),
                "A" to listOf("a","d","c"),"B" to listOf("c","b","d")),
            SortMode.RANDOM to mapOf("library" to listOf("a","c","d","b"),"favorites" to listOf("b","a","c"),
                "A" to listOf("c","d","a"),"B" to listOf("d","c","b")),
        )
        for(mode in SortMode.entries) for((source,order) in expected.getValue(mode)) {
            assertEquals("$source / $mode",order,ids(library(mode),source))
        }
    }

    @Test fun overlappingSongsUseEachRequestedPlaylistOrderWithoutLeakingOtherMembers() {
        val state=library()
        assertEquals(listOf("a","d","c"),ids(state,"A"))
        assertEquals(listOf("c","b","d"),ids(state,"B"))
        // The same two visible songs must change order when their playback source changes.
        assertEquals(listOf("d","c"),ids(state,"A",setOf("c","d")))
        assertEquals(listOf("c","d"),ids(state,"B",setOf("c","d")))
        assertEquals(listOf("c","b","d"),ids(state,"B",setOf("a","b","c","d")))
        assertEquals(listOf("a","c","b"),ids(state,FAVORITES_PLAYLIST_ID))
    }

    @Test fun staleVisibleSelectionUsesLatestSortTagsAndAvailabilityWithoutGrowingTheSearchQueue() {
        val staleSelection=setOf("a","b","c","deleted")
        val updated=library().copy(sort=SortMode.TITLE,songs=songs.map {
            when(it.id) {
                "a" -> it.copy(title="Aardvark")
                "b" -> it.copy(available=false)
                else -> it
            }
        })
        assertEquals(listOf("a","c"),ids(updated,LIBRARY_QUEUE_SOURCE,staleSelection))
        assertEquals("Aardvark",playbackSourceSongs(updated,LIBRARY_QUEUE_SOURCE,staleSelection).first().title)
        // A cached row for d is not allowed into this playlist, even though it is in the library.
        assertEquals(listOf("a","c"),ids(updated,FAVORITES_PLAYLIST_ID,setOf("a","c","d")))
    }

    @Test fun deletedSourcesAndMembersOrUnavailableFilesCannotBePlayedAgain() {
        val original=library()
        val state=original.copy(
            songs=songs.map {if(it.id == "a") it.copy(available=false) else it},
            playlists=original.playlists.filterNot {it.id == "B"}.map {
                if(it.isFavorites) it.copy(songIds=listOf("c","a","deleted")) else it
            },
        )
        assertEquals(listOf("c"),ids(state,FAVORITES_PLAYLIST_ID))
        assertTrue(ids(state,"B").isEmpty())
        assertTrue(ids(state,"unknown-playlist",setOf("a","b","c")).isEmpty())
        assertTrue(ids(state,"A",setOf("b","deleted")).isEmpty())
    }

    @Test fun duplicateIdsAppearOnceAndAnExplicitlyEmptySelectionNeverBecomesTheWholeLibrary() {
        val state=library().copy(songs=listOf(songs[0],songs[1],songs[1],songs[3],songs[0]),
            custom=listOf("b","b","a","deleted","d"))
        assertEquals(listOf("b","a","d"),ids(state,LIBRARY_QUEUE_SOURCE))
        assertEquals(listOf("b"),ids(state,LIBRARY_QUEUE_SOURCE,setOf("b")))
        assertTrue(ids(state,LIBRARY_QUEUE_SOURCE,emptySet()).isEmpty())
        assertTrue(ids(state,FAVORITES_PLAYLIST_ID,emptySet()).isEmpty())
        assertTrue(ids(state.copy(songs=state.songs.map {it.copy(available=false)}),LIBRARY_QUEUE_SOURCE).isEmpty())
    }

    @Test fun encodedAlbumSourceKeepsNumericDiscTrackOrderAndItsExactAlbumIdentity() {
        val albumSongs=listOf(
            songs[0].copy(album="Live + 花",albumArtist="Artist / 日本",disc="2/2",track="1/12"),
            songs[1].copy(album="Live + 花",albumArtist="Artist / 日本",disc="1/2",track="10/12"),
            songs[2].copy(album="Live + 花",albumArtist="Artist / 日本",disc="1/2",track="2/12"),
            songs[3].copy(album="Live + 花",albumArtist="Another Artist",disc="1",track="1"),
        )
        val key=albumSongs.first().albumKey
        val source="album:"+URLEncoder.encode(key,"UTF-8")
        val state=library(SortMode.RANDOM).copy(songs=albumSongs)
        assertEquals(listOf("c","b","a"),ids(state,source))
        assertEquals(listOf("c","a"),ids(state,source,setOf("a","c","d")))
        assertTrue(ids(state,"album:"+URLEncoder.encode("Unknown\u0000Live + 花","UTF-8")).isEmpty())
    }

    @Test fun thousandSongSourcesKeepExactOrdersAndSearchSubsetsInEverySortMode() {
        val all=(0 until 1000).map {"id"+it.toString().padStart(4,'0')}
        val largeSongs=all.mapIndexed {index,id -> Song(id,"content://music/$id","$id.flac",
            "Title"+index.toString().padStart(4,'0'),artist="Artist"+(999-index).toString().padStart(4,'0'))}
        val favoriteMembers=all.filterIndexed {index,_ -> index%2 == 0}.reversed()
        val aMembers=all.take(700).reversed()
        val bMembers=all.drop(300).let {it.drop(211)+it.take(211)}
        val libraryCustom=all.drop(313)+all.take(313)
        val libraryRandom=all.reversed()
        val membersBySource=mapOf(LIBRARY_QUEUE_SOURCE to libraryCustom,FAVORITES_PLAYLIST_ID to favoriteMembers,
            "A" to aMembers,"B" to bMembers)
        val randomBySource=mapOf(LIBRARY_QUEUE_SOURCE to libraryRandom,FAVORITES_PLAYLIST_ID to favoriteMembers.reversed(),
            "A" to (aMembers.drop(123)+aMembers.take(123)),"B" to bMembers.reversed())
        val subset=all.filterIndexed {index,_ -> index%7 == 0}.toSet()
        for(mode in SortMode.entries) {
            val state=LibraryState(songs=largeSongs,sort=mode,custom=libraryCustom,random=libraryRandom,
                playlists=membersBySource.filterKeys {it != LIBRARY_QUEUE_SOURCE}.map {(id,members) ->
                    Playlist(id,id,members,mode,randomBySource.getValue(id))
                })
            for((source,members) in membersBySource) {
                val memberSet=members.toSet()
                val expected=when(mode) {
                    SortMode.TITLE -> all.filter(memberSet::contains)
                    SortMode.ARTIST -> all.reversed().filter(memberSet::contains)
                    SortMode.CUSTOM -> members
                    SortMode.RANDOM -> randomBySource.getValue(source)
                }
                val actual=ids(state,source)
                assertEquals("$source / $mode order",expected,actual)
                assertEquals("$source / $mode membership",memberSet,actual.toSet())
                assertEquals(actual.size,actual.distinct().size)
                assertEquals("$source / $mode subset",expected.filter(subset::contains),ids(state,source,subset))
            }
        }
    }
}
