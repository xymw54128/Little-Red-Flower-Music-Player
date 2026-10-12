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
                    Text("共 $total 首歌曲",fontSize=12.sp,color=muted)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal=24.dp),verticalAlignment=Alignment.CenterVertically) {
            Text("共 $total 首${if(searching) " · 找到 ${songs.size} 首" else ""}",
                Modifier.weight(1f).testTag("playlist-song-count"),fontSize=12.sp,color=muted)
            TextButton(onClick=onSort,modifier=Modifier.testTag("playlist-sort")) {
                Text(playlist.sort.label);Icon(Icons.Outlined.SwapVert,null,Modifier.size(18.dp))
            }
        }
        if(songs.isEmpty()) Column(Modifier.fillMaxWidth().weight(1f).padding(32.dp),
            horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
            Icon(Icons.Outlined.LocalFlorist,null,Modifier.size(48.dp),tint=MaterialTheme.colorScheme.primary)
            Text(if(searching && !searchReady) "正在准备拼音搜索…" else if(searching) "没有找到匹配的歌曲" else "歌单还没有歌曲",Modifier.padding(vertical=16.dp),color=muted)
            if(!searching) TextButton(onClick=onBulkAdd,modifier=Modifier.testTag("playlist-empty-add")) {Text("添加音乐库歌曲")}
        } else SongList(songs,total,currentId,Modifier.weight(1f),onPlay,onAdd,onInfo,onRemove,favoriteIds,onFavorite,onNext,onAppend)
    }
}
