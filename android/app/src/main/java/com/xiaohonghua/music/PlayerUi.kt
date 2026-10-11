@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.xiaohonghua.music

import androidx.compose.animation.core.*
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import java.io.File

@Composable internal fun PlayerBackground(settings: Settings,song: Song?,modifier: Modifier=Modifier) {
    val context=LocalContext.current
    val path=when(settings.background) { "photo" -> File(context.filesDir,"player-background.img").absolutePath; "artwork" -> song?.artwork.orEmpty(); else -> "" }
    val image=rememberImage(path)
    Box(modifier.clip(RoundedCornerShape(0.dp)).background(pageColor)) {
        if(image != null) Image(image,null,Modifier.fillMaxSize().graphicsLayer { alpha=settings.opacity; scaleX=settings.scale; scaleY=settings.scale;
            translationX=(settings.x-.5f)*size.width*(settings.scale-1f); translationY=(settings.y-.5f)*size.height*(settings.scale-1f) },
            contentScale=ContentScale.Crop,alignment=androidx.compose.ui.BiasAlignment(2*settings.x-1,2*settings.y-1))
        Box(Modifier.fillMaxSize().background(pageColor.copy(alpha=.20f)))
    }
}

@Composable internal fun PlayerScreen(song: Song?,library: LibraryState,p: PlaybackState,model: MusicModel,
    onBack:()->Unit,onQueue:()->Unit,onAdd:()->Unit,onLyrics:()->Unit,onStyle:()->Unit) {
    val pager=rememberPagerState {2}
    val scope=rememberCoroutineScope()
    val artworkPress=remember {MutableInteractionSource()}
    val lyricText=library.manualLyrics[song?.id] ?: song?.lyrics.orEmpty()
    val lines=remember(lyricText) {Lrc.parse(lyricText)}
    val lyricIndex=remember(lines,model) {model.playback.map {Lrc.current(lines,it.position)}.distinctUntilChanged()}
    val current by lyricIndex.collectAsStateWithLifecycle(initialValue=-1)
    val pageMotion=tween<Float>(360,easing=FastOutSlowInEasing)
    val showBackground by remember {derivedStateOf {pager.currentPage == 0 || pager.currentPageOffsetFraction != 0f}}
    fun openLyrics() {scope.launch {pager.animateScrollToPage(1,animationSpec=pageMotion)}}
    Box(Modifier.fillMaxSize().background(pageColor)) {
        if(showBackground) PlayerBackground(library.settings,song,Modifier.testTag("player-page-background").matchParentSize().graphicsLayer {
            alpha=(1f-pager.currentPage-pager.currentPageOffsetFraction).coerceIn(0f,1f)
        })
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal=8.dp,vertical=4.dp),verticalAlignment=Alignment.CenterVertically) {
                ExpressiveIconButton(onClick=onBack) {Icon(Icons.Outlined.KeyboardArrowDown,"收起播放器")}
                Text(if(pager.currentPage == 1) "动态歌词" else "正在播放",Modifier.weight(1f),textAlign=TextAlign.Center,fontSize=13.sp,color=muted)
                ExpressiveIconButton(onClick=onAdd,enabled=song != null) {Icon(Icons.Outlined.PlaylistAdd,"添加到歌单")}
            }
            if(song == null) Column(Modifier.weight(1f).fillMaxWidth(),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
                Icon(Icons.Outlined.LocalFlorist,null,Modifier.size(72.dp),tint=MaterialTheme.colorScheme.primary); Text("从音乐库选择一首歌曲",Modifier.padding(24.dp),color=muted)
            } else HorizontalPager(pager,Modifier.testTag("player-pages").weight(1f).fillMaxWidth(),beyondViewportPageCount=1,
                flingBehavior=PagerDefaults.flingBehavior(pager,snapAnimationSpec=pageMotion)) { page ->
                if(page == 0) BoxWithConstraints(Modifier.fillMaxSize()) {
                    val coverSize=minOf(maxWidth*.72f,maxHeight*.60f,290.dp)
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=28.dp,vertical=16.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                        Cover(song,Modifier.testTag("player-artwork").size(coverSize).pressScale(artworkPress,.985f).clickable(interactionSource=artworkPress,indication=ripple(),onClickLabel="查看歌词",onClick=::openLyrics))
                        Text(song.title,Modifier.fillMaxWidth().padding(top=24.dp),fontSize=23.sp,fontWeight=FontWeight.SemiBold,textAlign=TextAlign.Center,maxLines=2,overflow=TextOverflow.Ellipsis)
                        Text("${song.artist} · ${song.album}",Modifier.testTag("player-artist-album").fillMaxWidth().padding(top=8.dp),fontSize=14.sp,color=muted,textAlign=TextAlign.Center,maxLines=2,overflow=TextOverflow.Ellipsis)
                        Text(listOfNotNull(song.format.takeIf {it.isNotBlank()},song.bitDepth.takeIf {it > 0}?.let {"$it bit"},song.sampleRate.takeIf {it > 0}?.let {"${it/1000f} kHz"}).joinToString(" · "),Modifier.padding(top=12.dp),fontSize=11.sp,color=muted,textAlign=TextAlign.Center)
                        Text(lines.getOrNull(current)?.text ?: "点击封面或左滑查看歌词",Modifier.testTag("player-lyric-preview").fillMaxWidth().padding(top=24.dp).clickable(onClickLabel="查看歌词",onClick=::openLyrics),textAlign=TextAlign.Center,fontSize=library.settings.lyricSize.coerceAtMost(24f).sp,fontFamily=lyricFont(library.settings.lyricFont),color=MaterialTheme.colorScheme.primary,maxLines=2,overflow=TextOverflow.Ellipsis)
                    }
                } else LyricsPage(lines,lyricText,library.settings,pager.currentPage == 1,p,model,onImport=onLyrics,onStyle=onStyle)
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.Center) {
                repeat(2) { index ->
                    val selected=pager.currentPage == index
                    val dotWidth by animateDpAsState(if(selected) 18.dp else 6.dp,spring(dampingRatio=1f,stiffness=650f),label="player-page-dot")
                    Box(Modifier.testTag(if(index == 0) "player-page-cover" else "player-page-lyrics").size(48.dp).clip(CircleShape)
                        .semantics {this.selected=selected;contentDescription=if(index == 0) "播放封面" else "歌词"}
                        .clickable(role=Role.Tab) {scope.launch {pager.animateScrollToPage(index,animationSpec=pageMotion)}},contentAlignment=Alignment.Center) {
                        Box(Modifier.size(dotWidth,6.dp).clip(CircleShape).background(if(selected) MaterialTheme.colorScheme.primary else muted.copy(alpha=.25f)))
                    }
                }
            }
            PlayerControls(song?.duration ?: 0,model,onQueue)
        }
    }
}
@Composable private fun PlayerControls(fallbackDuration: Long,model: MusicModel,onQueue:()->Unit) {
    val p by model.playback.collectAsStateWithLifecycle()
    val haptic=rememberActionHaptic()
    val playPress=remember {MutableInteractionSource()}
    var drag by remember { mutableStateOf<Float?>(null) }
    LaunchedEffect(p.currentId) {drag=null}
    val duration=(if(p.duration > 0) p.duration else fallbackDuration).coerceAtLeast(1)
    Column(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(pageColor.copy(alpha=0f),pageColor.copy(alpha=.90f)))).padding(horizontal=28.dp,vertical=8.dp)) {
        p.error?.let { Text(it,fontSize=12.sp,color=MaterialTheme.colorScheme.error,maxLines=2,modifier=Modifier.padding(bottom=8.dp)) }
        PlaybackSeekBar(p,duration,drag,onChange={drag=it},onFinished={drag?.let {model.seek(it.toLong());haptic()};drag=null})
        Row(Modifier.fillMaxWidth()) { Text(timeLabel((drag?.toLong() ?: p.position)),Modifier.weight(1f),fontSize=11.sp,color=muted); Text(timeLabel(duration),fontSize=11.sp,color=muted) }
        Row(Modifier.fillMaxWidth().padding(top=12.dp,bottom=4.dp),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
            ExpressiveIconButton(onClick={model.mode()},enabled=p.currentId != null,tactile=true) {Icon(when(p.mode) {2 -> Icons.Outlined.RepeatOne; 3 -> Icons.Outlined.Shuffle; else -> Icons.Outlined.Repeat},listOf("顺序播放","列表循环","单曲循环","随机播放")[p.mode],tint=if(p.mode == 0) muted else MaterialTheme.colorScheme.primary)}
            ExpressiveIconButton(onClick={model.previous()},enabled=p.currentId != null,modifier=Modifier.size(52.dp),tactile=true) {Icon(Icons.Outlined.SkipPrevious,"上一首",Modifier.size(32.dp))}
            FilledIconButton(onClick={model.toggle();haptic()},enabled=p.currentId != null && p.connected,modifier=Modifier.size(64.dp).pressScale(playPress,.94f),shape=CircleShape,interactionSource=playPress) {
                Crossfade(targetState=p.playing,animationSpec=tween(120),label="play-pause") {playing ->
                    Icon(if(playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,if(playing) "暂停" else "播放",Modifier.size(36.dp))
                }
            }
            ExpressiveIconButton(onClick={model.next()},enabled=p.currentId != null,modifier=Modifier.size(52.dp),tactile=true) {Icon(Icons.Outlined.SkipNext,"下一首",Modifier.size(32.dp))}
            ExpressiveIconButton(onClick=onQueue) {Icon(Icons.Outlined.QueueMusic,"播放队列")}
        }
        Text(listOf("顺序播放","列表循环","单曲循环","随机播放")[p.mode],Modifier.fillMaxWidth().padding(top=4.dp),fontSize=10.sp,color=muted,textAlign=TextAlign.Center)
    }
}
@Composable private fun PlaybackSeekBar(p: PlaybackState,duration: Long,drag: Float?,onChange:(Float)->Unit,onFinished:()->Unit) {
    val interaction=remember {MutableInteractionSource()}
    val dragging by interaction.collectIsDraggedAsState()
    val progress=remember(p.currentId) {Animatable(p.position.toFloat())}
    LaunchedEffect(p.position,p.playing,p.currentId,drag) {
        val target=(drag ?: p.position.toFloat()).coerceIn(0f,duration.toFloat())
        if(drag != null || !p.playing || kotlin.math.abs(progress.value-target) > 1000f) progress.snapTo(target)
        else progress.animateTo(target,tween(100,easing=LinearEasing))
    }
    val radius by animateFloatAsState(if(dragging) 7f else 5f,tween(160),label="seek-thumb")
    val accent=MaterialTheme.colorScheme.primary
    Slider(value=drag ?: progress.value,onValueChange=onChange,onValueChangeFinished=onFinished,valueRange=0f..duration.toFloat(),enabled=p.currentId != null,
        modifier=Modifier.testTag("playback-seek").fillMaxWidth().height(44.dp),interactionSource=interaction,
        thumb={ Canvas(Modifier.size(20.dp)) {drawCircle(if(p.currentId != null) accent else muted.copy(alpha=.35f),radius.dp.toPx())} },
        track={ state -> Canvas(Modifier.fillMaxWidth().height(3.dp)) {
            val start=Offset(0f,size.height/2);val end=Offset(size.width,size.height/2)
            drawLine(muted.copy(alpha=.18f),start,end,strokeWidth=size.height,cap=StrokeCap.Round)
            val fraction=(state.value/duration.toFloat()).coerceIn(0f,1f)
            if(fraction > 0f) drawLine(accent,start,Offset(size.width*fraction,end.y),strokeWidth=size.height,cap=StrokeCap.Round)
        } })
}
@Composable private fun LyricsPage(lines: List<LyricLine>,text: String,settings: Settings,active: Boolean,p: PlaybackState,model: MusicModel,onImport:()->Unit,onStyle:()->Unit) {
    val list=rememberLazyListState()
    var follow by remember(text) { mutableStateOf(true) }
    var current by remember(text) { mutableIntStateOf(Lrc.current(lines,model.playbackPosition())) }
    var touched by remember { mutableLongStateOf(0) }
    val scope=rememberCoroutineScope()
    fun browse() {follow=false;touched=android.os.SystemClock.elapsedRealtime()}
    LaunchedEffect(touched) { if(touched > 0) snapshotFlow {list.isScrollInProgress}.collectLatest { scrolling ->
        if(!scrolling) {delay(4000);follow=true}
    } }
    val measurer=rememberTextMeasurer()
    val density=LocalDensity.current
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    val lyricStyle=MaterialTheme.typography.bodyLarge.copy(fontSize=settings.lyricSize.sp,fontFamily=lyricFont(settings.lyricFont),lineHeight=(settings.lyricSize*1.35f).sp,
        fontWeight=FontWeight.Normal,textAlign=TextAlign.Center)
    val typeface by LocalFontFamilyResolver.current.resolve(lyricStyle.fontFamily,lyricStyle.fontWeight ?: FontWeight.Normal)
    BoxWithConstraints(Modifier.testTag("player-lyrics-page").fillMaxSize().background(pageColor)) {
    val lyricWidth=with(density) {(maxWidth-48.dp).roundToPx().coerceAtLeast(1)}
    val heights=remember(lines,lyricWidth,lyricStyle,settings.lyricGap,typeface,density) { mutableMapOf<Int,Int>() }
    fun height(index: Int): Int = heights.getOrPut(index) {
        measurer.measure(AnnotatedString(lines[index].text.ifBlank {"·"}),lyricStyle,constraints=Constraints(maxWidth=lyricWidth)).size.height+
            with(density) {(settings.lyricGap.dp/2+6.dp).roundToPx()*2}
    }
    suspend fun align(motion: LyricMotion) {
        if(motion.from !in lines.indices) return
        var info=list.layoutInfo
        var from=info.visibleItemsInfo.find {it.index == motion.from}
        var to=info.visibleItemsInfo.find {it.index == motion.to}
        if(from == null && to == null) {
            // A seek or return from manual browsing goes straight to the new anchor.
            list.scrollToItem(motion.from,height(motion.from)/2)
            info=list.layoutInfo
            from=info.visibleItemsInfo.find {it.index == motion.from} ?: return
            to=info.visibleItemsInfo.find {it.index == motion.to}
        }
        from?.let {heights[it.index]=it.size}
        to?.let {heights[it.index]=it.size}
        // Wrapped/translated rows can be taller than the viewport. Either visible anchor
        // gives the same absolute trajectory without repeatedly jumping back to the old row.
        val origin=from?.let {it.offset+it.size/2f} ?: (to!!.offset+to.size/2f-(height(motion.from)+to.size)/2f)
        val target=if(motion.to == motion.from) origin else to?.let {it.offset+it.size/2f}
            ?: (origin+(from!!.size+height(motion.to))/2f)
        val delta=origin+(target-origin)*motion.fraction-(info.viewportEndOffset+info.viewportStartOffset)/2f
        if(kotlin.math.abs(delta) > .1f) list.scrollBy(delta)
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal=16.dp),verticalAlignment=Alignment.CenterVertically) {
            TextButton(onClick=onImport) {Icon(Icons.Outlined.Description,null,Modifier.size(18.dp)); Text("导入歌词",fontSize=12.sp)}
            Spacer(Modifier.weight(1f))
            if(!follow && current >= 0) TextButton(onClick={follow=true;if(!settings.dynamicLyrics) scope.launch {
                val index=Lrc.current(lines,model.playbackPosition()).coerceAtLeast(0)
                align(LyricMotion(index,index,index,0f,0,0))
            } }) {Text("回到当前",fontSize=12.sp)}
            ExpressiveIconButton(onClick=onStyle) {Icon(Icons.Outlined.TextFields,"歌词字体、字号与行间距")}
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val viewportHeight=with(density) {maxHeight.roundToPx()}
            LaunchedEffect(lines,active,p.currentId,p.playing,p.speed,p.seekRevision,follow,settings.dynamicLyrics,heights,viewportHeight,lifecycle) {
                if(!active || lines.isEmpty()) return@LaunchedEffect
                lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    snapshotFlow {list.layoutInfo.totalItemsCount}.first {it == lines.size}
                    suspend fun frame() {
                        val motion=Lrc.motion(lines,model.playbackPosition(),p.speed)
                        current=motion.current
                        if(follow && settings.dynamicLyrics) align(motion)
                    }
                    frame()
                    if(p.playing) while(isActive) {
                        withFrameNanos { }
                        frame()
                    }
                }
            }
            if(lines.isEmpty()) Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
                Text(text.ifBlank {"还没有歌词\n可以导入同名 LRC，或选择本地歌词文件"},fontSize=settings.lyricSize.sp,fontFamily=lyricFont(settings.lyricFont),lineHeight=(settings.lyricSize*1.35f+settings.lyricGap).sp,textAlign=TextAlign.Center,color=muted)
            } else LazyColumn(state=list,modifier=Modifier.testTag("lyrics-list").fillMaxSize().pointerInput(text) {
                awaitEachGesture {awaitFirstDown(requireUnconsumed=false);browse();waitForUpOrCancellation();browse()}
            },contentPadding=PaddingValues(horizontal=24.dp,vertical=maxHeight/2),horizontalAlignment=Alignment.CenterHorizontally) {
                itemsIndexed(lines,key={index,_->index}) {index,line ->
                    Text(line.text.ifBlank {"·"},Modifier.testTag(if(index == current) "current-lyric" else "lyric-$index").fillMaxWidth().clickable {browse();model.seek(line.time)}.padding(vertical=settings.lyricGap.dp/2+6.dp),
                        style=lyricStyle,color=if(index == current) MaterialTheme.colorScheme.primary else ink.copy(alpha=.43f))
                }
            }
        }
    }
    }
}

@Composable internal fun OrderSheet(songs: List<Song>,onCancel:()->Unit,onSave:(List<String>)->Unit) {
    val draft=remember {mutableStateListOf<Song>().apply {addAll(songs)}}
    var draggingId by remember {mutableStateOf<String?>(null)}
    ModalBottomSheet(onDismissRequest=onCancel) {
        SheetTitle("自定义顺序","按住右侧手柄拖动；保存后生效")
        LazyColumn(Modifier.fillMaxWidth().heightIn(max=420.dp),contentPadding=PaddingValues(horizontal=16.dp)) {
            itemsIndexed(draft,key={_,song->song.id}) {index,song ->
                ReorderRow(song,index,draft.size,modifier=Modifier.animateItem(fadeInSpec=null,fadeOutSpec=null,placementSpec=if(draggingId == song.id) null else spring(dampingRatio=1f,stiffness=500f)),
                    onDraggingChanged={dragging -> draggingId=if(dragging) song.id else null},
                    onMove={direction -> val from=draft.indexOfFirst {it.id == song.id};if(from >= 0) {val to=(from+direction).coerceIn(0,draft.lastIndex);if(from != to) draft.add(to,draft.removeAt(from))} })
            }
        }
        Row(Modifier.fillMaxWidth().padding(24.dp),horizontalArrangement=Arrangement.spacedBy(16.dp)) {OutlinedButton(onClick=onCancel,modifier=Modifier.weight(1f)) {Text("取消")};Button(onClick={onSave(draft.map {it.id})},modifier=Modifier.weight(1f)) {Text("保存顺序")}}
    }
}
@Composable internal fun ReorderRow(song: Song,index: Int,count: Int,onMove:(Int)->Unit,modifier: Modifier=Modifier,onClick:(()->Unit)?=null,onRemove:(()->Unit)?=null,onDraggingChanged:(Boolean)->Unit={}) {
    val latestMove by rememberUpdatedState(onMove)
    val latestIndex by rememberUpdatedState(index)
    val latestCount by rememberUpdatedState(count)
    val latestDragging by rememberUpdatedState(onDraggingChanged)
    val haptic=rememberActionHaptic()
    var offset by remember {mutableFloatStateOf(0f)}
    var dragging by remember {mutableStateOf(false)}
    var raised by remember {mutableStateOf(false)}
    var dragIndex by remember {mutableIntStateOf(index)}
    val initialHeight=with(LocalDensity.current) {68.dp.toPx()}
    var rowHeight by remember {mutableFloatStateOf(initialHeight)}
    val latestHeight by rememberUpdatedState(rowHeight)
    val restingOffset=animateFloatAsState(offset,if(dragging) snap() else spring(dampingRatio=1f,stiffness=550f),label="reorder-return")
    val scale=animateFloatAsState(if(dragging) 1.015f else 1f,spring(dampingRatio=1f,stiffness=700f),label="reorder-scale")
    val lift=animateFloatAsState(if(dragging) 3f else 0f,tween(140),label="reorder-lift")
    val background=animateColorAsState(if(dragging) MaterialTheme.colorScheme.surfaceContainer else Color.Transparent,tween(140),label="reorder-background")
    LaunchedEffect(dragging) {if(!dragging) {delay(260);raised=false}}
    val rowShape=RoundedCornerShape(12.dp)
    Row(modifier.testTag("reorder-${song.id}").fillMaxWidth().heightIn(min=68.dp).onSizeChanged {rowHeight=it.height.toFloat().coerceAtLeast(1f)}
        .zIndex(if(raised) 1f else 0f).graphicsLayer {
            translationY=if(dragging) offset else restingOffset.value;scaleX=scale.value;scaleY=scale.value;shadowElevation=lift.value.dp.toPx();shape=rowShape
        }.drawBehind {drawRoundRect(background.value,cornerRadius=CornerRadius(12.dp.toPx()))}
        .then(if(onClick != null) Modifier.clip(rowShape).clickable(onClick=onClick) else Modifier),verticalAlignment=Alignment.CenterVertically) {
        Text("${index+1}",Modifier.width(30.dp),fontSize=12.sp,color=muted,textAlign=TextAlign.Center)
        Column(Modifier.weight(1f).padding(horizontal=8.dp)) {Text(song.title,maxLines=1,overflow=TextOverflow.Ellipsis,fontSize=14.sp);Text(song.artist,maxLines=1,overflow=TextOverflow.Ellipsis,fontSize=11.sp,color=muted)}
        Column {ExpressiveIconButton(onClick={latestMove(-1)},enabled=index > 0,modifier=Modifier.testTag("move-up-${song.id}").size(48.dp),tactile=true) {Icon(Icons.Outlined.KeyboardArrowUp,"上移",Modifier.size(20.dp))};ExpressiveIconButton(onClick={latestMove(1)},enabled=index < count-1,modifier=Modifier.testTag("move-down-${song.id}").size(48.dp),tactile=true) {Icon(Icons.Outlined.KeyboardArrowDown,"下移",Modifier.size(20.dp))}}
        if(onRemove != null) ExpressiveIconButton(onClick=onRemove,tactile=true) {Icon(Icons.Outlined.Close,"移出队列",Modifier.size(18.dp))}
        Box(Modifier.testTag("reorder-handle-${song.id}").size(48.dp).pointerInput(song.id) {detectDragGesturesAfterLongPress(
            onDragStart={dragging=true;raised=true;dragIndex=latestIndex;offset=0f;latestDragging(true);haptic()},
            onDragEnd={dragging=false;offset=0f;latestDragging(false)},onDragCancel={dragging=false;offset=0f;latestDragging(false)}) {change,amount ->
            change.consume();offset+=amount.y
            val threshold=latestHeight
            while(kotlin.math.abs(offset) >= threshold) {
                val direction=if(offset > 0f) 1 else -1
                if(dragIndex+direction !in 0 until latestCount) {offset=offset.coerceIn(-threshold*.2f,threshold*.2f);break}
                latestMove(direction);dragIndex+=direction;offset-=direction*threshold;haptic()
            }
            if(dragIndex == 0 && offset < 0f) offset=offset.coerceAtLeast(-threshold*.2f)
            if(dragIndex == latestCount-1 && offset > 0f) offset=offset.coerceAtMost(threshold*.2f)
        }},contentAlignment=Alignment.Center) {Icon(Icons.Outlined.DragHandle,"长按拖动排序",tint=MaterialTheme.colorScheme.primary)}
    }
}
@Composable internal fun QueueSheet(library: LibraryState,p: PlaybackState,model: MusicModel,onFeedback:(String)->Unit={},onDismiss:()->Unit) {
    val byId=library.songs.associateBy {it.id}
    var draggingId by remember {mutableStateOf<String?>(null)}
    var changed by remember {mutableStateOf(false)}
    ModalBottomSheet(onDismissRequest=onDismiss) {SheetTitle("播放队列 · ${p.queue.size} 首","队列顺序独立于音乐库和歌单")
        LazyColumn(Modifier.heightIn(max=460.dp),contentPadding=PaddingValues(horizontal=16.dp,vertical=8.dp)) {
            itemsIndexed(p.queue,key={_,id->id}) {index,id -> byId[id]?.let {song -> ReorderRow(song,index,p.queue.size,
                modifier=Modifier.animateItem(fadeInSpec=tween(140),fadeOutSpec=tween(120),placementSpec=if(draggingId == id) null else spring(dampingRatio=1f,stiffness=500f)),
                onDraggingChanged={dragging -> draggingId=if(dragging) id else null},
                onMove={direction -> val queue=model.playback.value.queue;val from=queue.indexOf(id);if(from >= 0) {
                    val to=(from+direction).coerceIn(0,queue.lastIndex);if(from != to) {model.queueMove(from,to);changed=true}
                }},
                onClick={val current=model.playback.value.queue.indexOf(id);if(current >= 0) model.queuePlay(current)},
                onRemove={val current=model.playback.value.queue.indexOf(id);if(current >= 0) {model.queueRemove(current);changed=true;onFeedback("已移出播放队列")}})} }
        }
        TextButton(onClick={onDismiss();if(changed) onFeedback("队列已更新")},modifier=Modifier.fillMaxWidth().padding(16.dp)) {Text("完成")}
    }
}
