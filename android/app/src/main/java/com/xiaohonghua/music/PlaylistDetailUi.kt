@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.xiaohonghua.music

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocalFlorist
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable internal fun PlaylistDetailContent(
    playlist: Playlist,songs: List<Song>,total: Int,currentId: String?,searching: Boolean,
    onPlay: (Song)->Unit,onSort: ()->Unit,onBulkAdd: ()->Unit,
    onAdd: (Song)->Unit,onInfo: (Song)->Unit,onRemove: (Song)->Unit,
    favoriteIds: Set<String>,onFavorite: (Song)->Unit,modifier: Modifier=Modifier,onNext:((Song)->Unit)?=null,onAppend:((Song)->Unit)?=null,searchReady:Boolean=true
) {
    // Persist the unlike immediately, but keep its row until the heart's actual tween ends.
    // This state also keeps the last row mounted before the empty-playlist view appears.
    val retained=remember(playlist.id) {mutableStateMapOf<String,Pair<Int,Song>>()}
    var exiting by remember(playlist.id) {mutableStateOf(emptySet<String>())}
    LaunchedEffect(favoriteIds,songs) {
        val preparedIds=songs.mapTo(mutableSetOf()) {it.id}
        // A quick re-like reverses the exit; keep the row until its worker result arrives.
        retained.keys.filter {it in favoriteIds && it in preparedIds}.forEach(retained::remove)
        exiting=exiting.filterNot(favoriteIds::contains).toSet()
    }
    val retainedRows=retained.toMap()
    val renderedSongs=remember(songs,retainedRows) {
        if(retainedRows.isEmpty()) songs else songs.toMutableList().apply {
            val visible=mapTo(mutableSetOf()) {it.id}
            retainedRows.values.sortedBy {it.first}.forEach {(index,song) ->
                if(visible.add(song.id)) add(index.coerceIn(0,size),song)
            }
        }
    }
    fun finishFavoriteAnimation(songId: String) {
        if(songId in retained && songId !in favoriteIds) exiting=exiting+songId
    }
    fun finishRowExit(songId: String) {
        exiting=exiting-songId
        val removed=retained.remove(songId) ?: return
        if(songId !in favoriteIds) retained.entries.toList().forEach {(id,row) ->
            if(row.first > removed.first) retained[id]=(row.first-1) to row.second
        }
    }
    val displayedTotal=total+retained.keys.count {it !in favoriteIds}
    val density=LocalDensity.current
    var summaryHeight by remember(playlist.id,density) {mutableFloatStateOf(with(density) {140.dp.toPx()})}
    val headerState=rememberTopAppBarState()
    val scrollBehavior=TopAppBarDefaults.enterAlwaysScrollBehavior(headerState)
    LaunchedEffect(summaryHeight) {headerState.heightOffsetLimit=-summaryHeight}
    val visibleHeight=with(density) {(summaryHeight+headerState.heightOffset).coerceAtLeast(0f).toDp()}
    Column(modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection)) {
        // Keep the full summary measured while its viewport contracts. Song indices stay unchanged.
        Box(Modifier.testTag("playlist-summary").fillMaxWidth().height(visibleHeight).clipToBounds()) {
            Row(Modifier.fillMaxWidth().wrapContentHeight(Alignment.Top,unbounded=true)
                .onSizeChanged {summaryHeight=it.height.toFloat()}.padding(horizontal=24.dp,vertical=16.dp),
                verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(18.dp)) {
                PlaylistCover(playlist,Modifier.size(88.dp).testTag("playlist-summary-cover"))
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    Text(playlist.name,fontSize=21.sp,fontWeight=FontWeight.SemiBold,maxLines=2,overflow=TextOverflow.Ellipsis)
                    Text(playlist.description.ifBlank {"暂无简介"},Modifier.testTag("playlist-description"),
                        fontSize=12.sp,lineHeight=18.sp,color=muted,maxLines=3,overflow=TextOverflow.Ellipsis)
                    Text("共 $displayedTotal 首歌曲",fontSize=12.sp,color=muted)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal=24.dp),verticalAlignment=Alignment.CenterVertically) {
            Text("共 $displayedTotal 首${if(searching) " · 找到 ${renderedSongs.size} 首" else ""}",
                Modifier.weight(1f).testTag("playlist-song-count"),fontSize=12.sp,color=muted)
            TextButton(onClick=onSort,modifier=Modifier.testTag("playlist-sort")) {
                Text(playlist.sort.label);Icon(Icons.Outlined.SwapVert,null,Modifier.size(18.dp))
            }
        }
        if(renderedSongs.isEmpty()) Column(Modifier.fillMaxWidth().weight(1f).padding(32.dp),
            horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
            Icon(Icons.Outlined.LocalFlorist,null,Modifier.size(48.dp),tint=MaterialTheme.colorScheme.primary)
            Text(if(searching && !searchReady) "正在准备拼音搜索…" else if(searching) "没有找到匹配的歌曲" else "歌单还没有歌曲",Modifier.padding(vertical=16.dp),color=muted)
            if(!searching) TextButton(onClick=onBulkAdd,modifier=Modifier.testTag("playlist-empty-add")) {Text("添加音乐库歌曲")}
        } else SongList(renderedSongs,displayedTotal,currentId,Modifier.weight(1f),onPlay={song ->
            // A retained visual row is already outside the actual playback source.
            if(!playlist.isFavorites || song.id in favoriteIds) onPlay(song)
        },onAdd=onAdd,onInfo=onInfo,onRemove=onRemove,favoriteIds=favoriteIds,onFavorite={song ->
            if(playlist.isFavorites && song.id in favoriteIds) retained[song.id]=renderedSongs.indexOfFirst {it.id == song.id} to song
            onFavorite(song)
        },onNext=onNext,onAppend=onAppend,onFavoriteAnimationFinished={finishFavoriteAnimation(it.id)},exitingSongIds=exiting,
            onRowExitFinished=if(playlist.isFavorites) {{finishRowExit(it.id)}} else null)
    }
}
