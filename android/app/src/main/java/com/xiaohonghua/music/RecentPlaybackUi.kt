package com.xiaohonghua.music

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable internal fun RecentPlaybackContent(
    songs: List<Song>,currentId: String?,onPlay: (Song)->Unit,onAdd: (Song)->Unit,onInfo: (Song)->Unit,
    favoriteIds: Set<String>,onFavorite: (Song)->Unit,onClear: ()->Unit,modifier: Modifier=Modifier,
    onNext: ((Song)->Unit)?=null,onAppend: ((Song)->Unit)?=null
) {
    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal=24.dp),verticalAlignment=Alignment.CenterVertically) {
            Text("共 ${songs.size} 首 · 最近播放优先",Modifier.weight(1f).testTag("recent-count"),fontSize=12.sp,color=muted)
            if(songs.isNotEmpty()) TextButton(onClick=onClear,modifier=Modifier.testTag("clear-recent-playback")) {
                Text("清空记录")
            }
        }
        if(songs.isEmpty()) Column(Modifier.fillMaxWidth().weight(1f).padding(32.dp),
            horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
            Icon(Icons.Outlined.History,null,Modifier.size(48.dp),tint=MaterialTheme.colorScheme.primary)
            Text("还没有播放记录",Modifier.padding(top=18.dp,bottom=8.dp),color=ink)
            Text("播放过的歌曲会出现在这里，最多保留 100 首。",fontSize=12.sp,color=muted)
        } else SongList(songs,songs.size,currentId,Modifier.weight(1f),onPlay,onAdd,onInfo,
            favoriteIds=favoriteIds,onFavorite=onFavorite,onNext=onNext,onAppend=onAppend)
    }
}
