@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.xiaohonghua.music

import androidx.compose.foundation.*
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

private val coverColors=listOf(Color(0xFFD2778B),Color(0xFF7C9D7B),Color(0xFFD85C66),Color(0xFF78A094),Color(0xFF8D79B9),
    Color(0xFFC4953C),Color(0xFFCF6D6B),Color(0xFF759FCA),Color(0xFFC07F91),Color(0xFF6198C1),Color(0xFFCE3C4C))
// Keep flower IDs 8..18 stable; old 0..7 presets migrate when the library is read.
private val coverIds=(8 until PLAYLIST_COVER_COUNT).toList()
private val floralCovers=listOf(R.drawable.ic_cover_peach,R.drawable.ic_cover_daisy,R.drawable.ic_cover_tulip,
    R.drawable.ic_cover_lily,R.drawable.ic_cover_iris,R.drawable.ic_cover_sunflower,R.drawable.ic_cover_camellia,
    R.drawable.ic_cover_hydrangea,R.drawable.ic_cover_lotus,R.drawable.ic_cover_cornflower,R.drawable.ic_cover_favorite)
private val coverNames=listOf("桃枝","雏菊","郁金香","铃兰","鸢尾","向日葵","山茶","绣球","荷风","矢车菊","心之花")

@Composable internal fun PlaylistCover(playlist: Playlist,modifier: Modifier=Modifier) {
    val index=normalizePlaylistCover(playlist.id,playlist.coverPreset)-8
    val photo=playlist.coverPhoto.takeIf {it.matches(Regex("[a-f0-9-]{36}\\.jpg"))}
    val path=photo?.let {File(LocalContext.current.filesDir,"playlist-covers/$it").absolutePath}.orEmpty()
    val color by animateColorAsState(coverColors[index],tween(180),label="playlist-cover-color")
    BoxWithConstraints(modifier.testTag("playlist-cover-${playlist.id}").clip(RoundedCornerShape(14.dp)).background(color.copy(alpha=.12f)),contentAlignment=Alignment.Center) {
        val pixels=with(LocalDensity.current) {maxWidth.coerceAtMost(400.dp).roundToPx()}
        val image=rememberCachedArtwork(path,pixels,photo.orEmpty())
        if(image != null) Image(image,null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
        else {
            Crossfade(index,modifier=Modifier.fillMaxSize(),animationSpec=tween(160),label="playlist-cover-icon") {preset ->
                Image(painterResource(floralCovers[preset]),null,Modifier.fillMaxSize(),contentScale=ContentScale.Fit)
            }
        }
    }
}

@Composable internal fun PlaylistCoverSheet(playlist: Playlist,onDismiss:()->Unit,onPreset:(Int)->Unit,onPhoto:()->Unit,onComplete:(String)->Unit={}) {
    val haptic=rememberActionHaptic()
    var changed by remember(playlist.id) {mutableStateOf(false)}
    fun select(index: Int) {if(playlist.coverPhoto.isNotEmpty() || playlist.coverPreset != index) {changed=true;onPreset(index);haptic()}}
    ModalBottomSheet(onDismissRequest=onDismiss) {
        SheetTitle("歌单封面",playlist.name)
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).selectableGroup().padding(horizontal=24.dp),horizontalAlignment=Alignment.CenterHorizontally) {
            PlaylistCover(playlist,Modifier.size(112.dp).padding(bottom=8.dp))
            Text("选择图案，或使用自己的照片",color=muted,fontSize=12.sp,modifier=Modifier.padding(vertical=12.dp))
            repeat((coverNames.size+3)/4) {row -> Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                repeat(4) columns@ {column ->
                    val position=row*4+column
                    if(position >= coverNames.size) {Spacer(Modifier.weight(1f));return@columns}
                    val index=coverIds[position]
                    val selected=playlist.coverPhoto.isEmpty() && normalizePlaylistCover(playlist.id,playlist.coverPreset) == index
                    val border by animateColorAsState(if(selected) MaterialTheme.colorScheme.primary else Color.Transparent,tween(160),label="cover-selection-border")
                    val scale by animateFloatAsState(if(selected) 1f else .96f,tween(180),label="cover-selection-scale")
                    Column(Modifier.testTag("playlist-cover-preset-$index").weight(1f).clip(RoundedCornerShape(16.dp)).selectable(selected,role=Role.RadioButton,onClick={select(index)}).padding(vertical=8.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                        Box(Modifier.fillMaxWidth().aspectRatio(1f).graphicsLayer {scaleX=scale;scaleY=scale}.border(2.dp,border,RoundedCornerShape(16.dp)).padding(4.dp)) {
                            PlaylistCover(playlist.copy(coverPreset=index,coverPhoto=""),Modifier.fillMaxSize())
                        }
                        Text(coverNames[position],fontSize=11.sp,color=if(selected) MaterialTheme.colorScheme.primary else muted,modifier=Modifier.padding(top=6.dp))
                    }
                }
            } }
            OutlinedButton(onClick=onPhoto,modifier=Modifier.fillMaxWidth().padding(top=16.dp)) {Icon(Icons.Outlined.PhotoLibrary,null,Modifier.size(20.dp));Spacer(Modifier.width(8.dp));Text("从本地相册选择")}
            TextButton(onClick={select(defaultPlaylistCover(playlist.id))},modifier=Modifier.padding(top=4.dp)) {Text("恢复默认封面")}
            Button(onClick={if(changed) onComplete("已更换歌单封面");onDismiss()},modifier=Modifier.testTag("playlist-cover-done").fillMaxWidth().padding(top=8.dp,bottom=24.dp)) {Text("完成")}
        }
    }
}
