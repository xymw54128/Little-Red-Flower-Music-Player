package com.xiaohonghua.music

import java.net.URLDecoder

/** Resolve a clicked subset against the latest source, never an outgoing cached UI order. */
internal fun playbackSourceSongs(state: LibraryState,source: String,selection: Set<String>?=null): List<Song> {
    val album=if(source.startsWith("album:")) runCatching {URLDecoder.decode(source.substringAfter("album:"),"UTF-8")}.getOrNull() ?: return emptyList() else null
    val playlist=if(source == LIBRARY_QUEUE_SOURCE || album != null) null else state.playlists.find {it.id == source} ?: return emptyList()
    val members=playlist?.songIds?.toHashSet()
    val songs=state.songs.filter {it.available && (selection == null || it.id in selection) &&
        (members == null || it.id in members) && (album == null || it.albumKey == album)}.distinctBy {it.id}
    return if(album != null) albumSongs(songs) else orderedSongs(songs,playlist?.sort ?: state.sort,
        playlist?.songIds ?: state.custom,playlist?.randomIds ?: state.random)
}

/** A queue edit compares the audible traversal, including a zero-physical-move shuffle exit. */
internal fun queueOrderChanged(requested: List<String>,physical: List<String>,traversal: List<String>,shuffled: Boolean): Boolean =
    requested != if(shuffled && traversal.isNotEmpty()) traversal else physical
