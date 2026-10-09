@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.xiaohonghua.music

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

private val coverColors=listOf(Color(0xFFA94857),Color(0xFF557968),Color(0xFF4C7396),Color(0xFF817097),
    Color(0xFFB18B47),Color(0xFFAC765B),Color(0xFF497F83),Color(0xFF687586))
private val coverIcons=listOf(Icons.Outlined.LocalFlorist,Icons.Outlined.Forest,Icons.Outlined.Waves,Icons.Outlined.NightsStay,
    Icons.Outlined.WbSunny,Icons.Outlined.Album,Icons.Outlined.Headphones,Icons.Outlined.Piano)
private val coverNames=listOf("红花","森林","海浪","月夜","暖阳","唱片","耳机","钢琴")

@Composable internal fun PlaylistCover(playlist: Playlist,modifier: Modifier=Modifier) {
    val index=playlist.coverPreset.coerceIn(0,PLAYLIST_COVER_COUNT-1)
    val photo=playlist.coverPhoto.takeIf {it.matches(Regex("[a-f0-9-]{36}\\.jpg"))}
    val image=rememberImage(photo?.let {File(LocalContext.current.filesDir,"playlist-covers/$it").absolutePath}.orEmpty())
    val color=coverColors[index]
    BoxWithConstraints(modifier.testTag("playlist-cover-${playlist.id}").clip(RoundedCornerShape(14.dp)).background(color.copy(alpha=.12f)),contentAlignment=Alignment.Center) {
        if(image != null) Image(image,null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
        else {
            Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(color.copy(alpha=.07f),color.copy(alpha=.21f)))))
            Box(Modifier.size(maxWidth*.76f).offset(x=maxWidth*.25f,y=maxWidth*.32f).border(1.dp,color.copy(alpha=.13f),CircleShape))
            Icon(coverIcons[index],null,Modifier.size(maxWidth*.44f),tint=color)
        }
    }
}

@Composable internal fun PlaylistCoverSheet(playlist: Playlist,onDismiss:()->Unit,onPreset:(Int)->Unit,onPhoto:()->Unit) {
    ModalBottomSheet(onDismissRequest=onDismiss) {
        SheetTitle("歌单封面",playlist.name)
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal=24.dp),horizontalAlignment=Alignment.CenterHorizontally) {
            PlaylistCover(playlist,Modifier.size(112.dp).padding(bottom=8.dp))
            Text("选择图案，或使用自己的照片",color=muted,fontSize=12.sp,modifier=Modifier.padding(vertical=12.dp))
            repeat(2) {row -> Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                repeat(4) {column ->
                    val index=row*4+column
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).clickable {onPreset(index)}.padding(vertical=8.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                        val selected=playlist.coverPhoto.isEmpty() && playlist.coverPreset == index
                        Box(Modifier.fillMaxWidth().aspectRatio(1f).then(if(selected) Modifier.border(2.dp,MaterialTheme.colorScheme.primary,RoundedCornerShape(16.dp)) else Modifier).padding(4.dp)) {
                            PlaylistCover(playlist.copy(coverPreset=index,coverPhoto=""),Modifier.fillMaxSize())
                        }
                        Text(coverNames[index],fontSize=11.sp,color=if(selected) MaterialTheme.colorScheme.primary else muted,modifier=Modifier.padding(top=6.dp))
                    }
                }
            } }
            OutlinedButton(onClick=onPhoto,modifier=Modifier.fillMaxWidth().padding(top=16.dp)) {Icon(Icons.Outlined.PhotoLibrary,null,Modifier.size(20.dp));Spacer(Modifier.width(8.dp));Text("从本地相册选择")}
            TextButton(onClick={onPreset(defaultPlaylistCover(playlist.id))},modifier=Modifier.padding(top=4.dp,bottom=16.dp)) {Text("恢复默认封面")}
        }
    }
}
