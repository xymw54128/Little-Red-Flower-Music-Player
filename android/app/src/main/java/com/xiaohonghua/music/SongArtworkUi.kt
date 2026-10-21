package com.xiaohonghua.music

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.LocalFlorist
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.min
import kotlin.math.roundToInt

internal fun clampArtworkPan(pan: Offset,scale: Float,viewport: IntSize,image: IntSize): Offset {
    if(viewport.width <= 0 || viewport.height <= 0 || image.width <= 0 || image.height <= 0) return Offset.Zero
    val fit=min(viewport.width.toFloat()/image.width,viewport.height.toFloat()/image.height)
    val maxX=((image.width*fit*scale-viewport.width)/2).coerceAtLeast(0f)
    val maxY=((image.height*fit*scale-viewport.height)/2).coerceAtLeast(0f)
    // Offset compares packed bits, so a zero-width range must return +0f on both axes.
    return Offset(if(maxX == 0f) 0f else pan.x.coerceIn(-maxX,maxX),
        if(maxY == 0f) 0f else pan.y.coerceIn(-maxY,maxY))
}

@Composable internal fun SongArtworkPreview(song: Song,modifier: Modifier=Modifier) {
    var viewing by remember(song.id) {mutableStateOf(false)}
    Box(modifier.fillMaxWidth().padding(vertical=12.dp),contentAlignment=Alignment.Center) {
        Cover(song,Modifier.size(120.dp).testTag("song-info-cover")
            .semantics {contentDescription="查看${song.title}封面"}
            .clickable(role=Role.Button,onClickLabel="查看专辑封面") {viewing=true})
    }
    if(viewing) key(song.id,song.artwork,song.artworkRevision) {
        ArtworkViewer(song) {viewing=false}
    }
}

@Composable private fun ArtworkViewer(song: Song,onClose: ()->Unit) {
    val image=rememberImage(song.artwork,1600)
    val imageSize=image?.let {IntSize(it.width,it.height)} ?: IntSize.Zero
    var viewport by remember {mutableStateOf(IntSize.Zero)}
    var scale by remember {mutableFloatStateOf(1f)}
    var pan by remember {mutableStateOf(Offset.Zero)}
    var animation by remember {mutableStateOf<Job?>(null)}
    val scope=rememberCoroutineScope()
    fun zoomTo(target: Float,focus: Offset=Offset(viewport.width/2f,viewport.height/2f)) {
        animation?.cancel()
        val startScale=scale
        val startPan=pan
        val center=Offset(viewport.width/2f,viewport.height/2f)
        val targetPan=clampArtworkPan((startPan+center-focus)*(target/startScale)-(center-focus),
            target,viewport,imageSize)
        animation=scope.launch {
            animate(0f,1f,animationSpec=tween(180)) {fraction,_ ->
                scale=startScale+(target-startScale)*fraction
                pan=clampArtworkPan(startPan+(targetPan-startPan)*fraction,scale,viewport,imageSize)
            }
        }
    }
    Dialog(onDismissRequest=onClose,properties=DialogProperties(usePlatformDefaultWidth=false,
        decorFitsSystemWindows=false,dismissOnClickOutside=false)) {
        AppDialogWindowAnimation()
        val view=LocalView.current
        val lightBars=MaterialTheme.colorScheme.background.luminance() > .5f
        SideEffect {
            (view.parent as? DialogWindowProvider)?.window?.let {window ->
                WindowCompat.getInsetsController(window,view).apply {
                    isAppearanceLightStatusBars=lightBars
                    isAppearanceLightNavigationBars=lightBars
                }
            }
        }
        Surface(Modifier.fillMaxSize().testTag("artwork-viewer"),color=MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().systemBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(start=24.dp,end=12.dp,top=8.dp),
                    verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(song.title,maxLines=1,overflow=TextOverflow.Ellipsis,fontSize=18.sp)
                        Text(song.album,maxLines=1,overflow=TextOverflow.Ellipsis,fontSize=12.sp,color=muted)
                    }
                    IconButton(onClick=onClose,modifier=Modifier.size(48.dp).testTag("artwork-close")) {
                        Icon(Icons.Outlined.Close,"关闭封面预览")
                    }
                }
                Box(Modifier.fillMaxWidth().weight(1f).padding(24.dp).clipToBounds()
                    .testTag("artwork-viewport").onSizeChanged {
                        viewport=it;pan=clampArtworkPan(pan,scale,it,imageSize)
                    }.semantics {
                        stateDescription="${(scale*100).roundToInt()}%"
                        if(image != null) customActions=listOf(
                            CustomAccessibilityAction("放大封面") {zoomTo(2f);true},
                            CustomAccessibilityAction("还原封面") {zoomTo(1f);true})
                    }.pointerInput(song.id,image) {
                        detectTransformGestures {centroid,movement,zoom,_ ->
                            if(image != null) {
                                animation?.cancel()
                                val next=(scale*zoom).coerceIn(1f,4f)
                                val center=Offset(viewport.width/2f,viewport.height/2f)
                                pan=clampArtworkPan((pan+center-centroid)*(next/scale)-(center-centroid)+movement,
                                    next,viewport,imageSize)
                                scale=next
                            }
                        }
                    }.pointerInput(song.id,image) {
                        detectTapGestures(onDoubleTap={focus -> if(image != null) zoomTo(if(scale > 1f) 1f else 2f,focus)})
                    },contentAlignment=Alignment.Center) {
                    if(image != null) Image(image,"${song.title}的专辑封面",Modifier.fillMaxSize().testTag("artwork-image")
                        .graphicsLayer {scaleX=scale;scaleY=scale;translationX=pan.x;translationY=pan.y},
                        contentScale=ContentScale.Fit)
                    else Icon(Icons.Outlined.LocalFlorist,"暂无歌曲封面",Modifier.size(120.dp),
                        tint=MaterialTheme.colorScheme.primary)
                }
                Text(if(image != null) "双指缩放 · 双击放大 · 拖动查看" else "暂无歌曲封面",
                    Modifier.align(Alignment.CenterHorizontally).padding(bottom=24.dp),fontSize=12.sp,color=muted)
            }
        }
    }
}
