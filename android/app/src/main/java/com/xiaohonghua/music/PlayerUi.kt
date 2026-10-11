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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.BasicText
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextMotion
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
    onBack:()->Unit,onQueue:()->Unit,onAdd:()->Unit) {
    val pager=rememberPagerState {2}
    val scope=rememberCoroutineScope()
    val artworkPress=remember {MutableInteractionSource()}
    val lyricText=library.manualLyrics[song?.id] ?: song?.lyrics.orEmpty()
    val lines=remember(lyricText,song?.duration) {Lrc.parse(lyricText,song?.duration?.takeIf {it > 0})}
    val lyricIndex=remember(lines,model) {model.playback.map {Lrc.current(lines,it.position)}.distinctUntilChanged()}
    val current by lyricIndex.collectAsStateWithLifecycle(initialValue=-1)
    val pageMotion=tween<Float>(360,easing=FastOutSlowInEasing)
    val showBackground by remember {derivedStateOf {pager.currentPage == 0 || pager.currentPageOffsetFraction != 0f}}
    val lyricsVisible by remember {derivedStateOf {pager.currentPage == 1 || pager.currentPageOffsetFraction > 0f}}
    val surface=pageColor
    val foreground=ink
    val secondary=muted
    fun openLyrics() {scope.launch {pager.animateScrollToPage(1,animationSpec=pageMotion)}}
    CompositionLocalProvider(LocalContentColor provides foreground) {
    Box(Modifier.fillMaxSize().background(surface)) {
        if(showBackground) PlayerBackground(library.settings,song,Modifier.testTag("player-page-background").matchParentSize().graphicsLayer {
            alpha=(1f-pager.currentPage-pager.currentPageOffsetFraction).coerceIn(0f,1f)
        })
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal=8.dp,vertical=4.dp),verticalAlignment=Alignment.CenterVertically) {
                ExpressiveIconButton(onClick=onBack) {Icon(Icons.Outlined.KeyboardArrowDown,"收起播放器")}
                Text(if(pager.currentPage == 1) song?.let {"${it.title} - ${it.artist}"}.orEmpty() else "正在播放",Modifier.weight(1f).padding(horizontal=8.dp),textAlign=TextAlign.Center,fontSize=13.sp,color=secondary,maxLines=1,overflow=TextOverflow.Ellipsis)
                if(pager.currentPage == 0) ExpressiveIconButton(onClick=onAdd,enabled=song != null) {Icon(Icons.Outlined.PlaylistAdd,"添加到歌单")}
                else Spacer(Modifier.size(48.dp))
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
                } else LyricsPage(lines,lyricText,library.settings,lyricsVisible,p,model)
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.Center) {
                repeat(2) { index ->
                    val selected=pager.currentPage == index
                    val dotWidth by animateDpAsState(if(selected) 18.dp else 6.dp,spring(dampingRatio=1f,stiffness=650f),label="player-page-dot")
                    Box(Modifier.testTag(if(index == 0) "player-page-cover" else "player-page-lyrics").size(48.dp).clip(CircleShape)
                        .semantics {this.selected=selected;contentDescription=if(index == 0) "播放封面" else "歌词"}
                        .clickable(role=Role.Tab) {scope.launch {pager.animateScrollToPage(index,animationSpec=pageMotion)}},contentAlignment=Alignment.Center) {
                        Box(Modifier.size(dotWidth,6.dp).clip(CircleShape).background(if(selected) MaterialTheme.colorScheme.primary else secondary.copy(alpha=.25f)))
                    }
                }
            }
            PlayerControls(song?.duration ?: 0,model,onQueue,surface,secondary)
        }
    }
    }
}
@Composable private fun PlayerControls(fallbackDuration: Long,model: MusicModel,onQueue:()->Unit,surface: Color,secondary: Color) {
    val p by model.playback.collectAsStateWithLifecycle()
    val haptic=rememberActionHaptic()
    val playPress=remember {MutableInteractionSource()}
    var drag by remember { mutableStateOf<Float?>(null) }
    LaunchedEffect(p.currentId) {drag=null}
    val duration=(if(p.duration > 0) p.duration else fallbackDuration).coerceAtLeast(1)
    Column(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(surface.copy(alpha=0f),surface.copy(alpha=.90f)))).padding(horizontal=28.dp,vertical=8.dp)) {
        p.error?.let { Text(it,fontSize=12.sp,color=MaterialTheme.colorScheme.error,maxLines=2,modifier=Modifier.padding(bottom=8.dp)) }
        PlaybackSeekBar(p,duration,drag,onChange={drag=it},onFinished={drag?.let {model.seek(it.toLong());haptic()};drag=null})
        Row(Modifier.fillMaxWidth()) { Text(timeLabel((drag?.toLong() ?: p.position)),Modifier.weight(1f),fontSize=11.sp,color=secondary); Text(timeLabel(duration),fontSize=11.sp,color=secondary) }
        Row(Modifier.fillMaxWidth().padding(top=12.dp,bottom=4.dp),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
            ExpressiveIconButton(onClick={model.mode()},enabled=p.currentId != null,tactile=true) {Icon(when(p.mode) {2 -> Icons.Outlined.RepeatOne; 3 -> Icons.Outlined.Shuffle; else -> Icons.Outlined.Repeat},listOf("顺序播放","列表循环","单曲循环","随机播放")[p.mode],tint=if(p.mode == 0) secondary else MaterialTheme.colorScheme.primary)}
            ExpressiveIconButton(onClick={model.previous()},enabled=p.currentId != null,modifier=Modifier.size(52.dp),tactile=true) {Icon(Icons.Outlined.SkipPrevious,"上一首",Modifier.size(32.dp))}
            FilledIconButton(onClick={model.toggle();haptic()},enabled=p.currentId != null && p.connected,modifier=Modifier.size(64.dp).pressScale(playPress,.94f),shape=CircleShape,interactionSource=playPress) {
                Crossfade(targetState=p.playing,animationSpec=tween(120),label="play-pause") {playing ->
                    Icon(if(playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,if(playing) "暂停" else "播放",Modifier.size(36.dp))
                }
            }
            ExpressiveIconButton(onClick={model.next()},enabled=p.currentId != null,modifier=Modifier.size(52.dp),tactile=true) {Icon(Icons.Outlined.SkipNext,"下一首",Modifier.size(32.dp))}
            ExpressiveIconButton(onClick=onQueue) {Icon(Icons.Outlined.QueueMusic,"播放队列")}
        }
        Text(listOf("顺序播放","列表循环","单曲循环","随机播放")[p.mode],Modifier.fillMaxWidth().padding(top=4.dp),fontSize=10.sp,color=secondary,textAlign=TextAlign.Center)
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
    val trackColor=LocalContentColor.current.copy(alpha=.18f)
    Slider(value=drag ?: progress.value,onValueChange=onChange,onValueChangeFinished=onFinished,valueRange=0f..duration.toFloat(),enabled=p.currentId != null,
        modifier=Modifier.testTag("playback-seek").fillMaxWidth().height(44.dp),interactionSource=interaction,
        thumb={ Canvas(Modifier.size(20.dp)) {drawCircle(if(p.currentId != null) accent else muted.copy(alpha=.35f),radius.dp.toPx())} },
        track={ state -> Canvas(Modifier.fillMaxWidth().height(3.dp)) {
            val start=Offset(0f,size.height/2);val end=Offset(size.width,size.height/2)
            drawLine(trackColor,start,end,strokeWidth=size.height,cap=StrokeCap.Round)
            val fraction=(state.value/duration.toFloat()).coerceIn(0f,1f)
            if(fraction > 0f) drawLine(accent,start,Offset(size.width*fraction,end.y),strokeWidth=size.height,cap=StrokeCap.Round)
        } })
}
@Composable private fun LyricsPage(lines: List<LyricLine>,text: String,settings: Settings,visible: Boolean,p: PlaybackState,model: MusicModel) {
    val density=LocalDensity.current
    val measurer=rememberTextMeasurer()
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    val style=MaterialTheme.typography.bodyLarge.copy(fontSize=settings.lyricSize.sp,fontFamily=lyricFont(settings.lyricFont),
        lineHeight=(settings.lyricSize*1.35f).sp,fontWeight=FontWeight.Normal,textAlign=TextAlign.Center,textMotion=TextMotion.Animated)
    val typeface by LocalFontFamilyResolver.current.resolve(style.fontFamily,FontWeight.Normal)
    val latestVisible by rememberUpdatedState(visible)
    val latestPlayback by rememberUpdatedState(p)
    val latestDynamic by rememberUpdatedState(settings.dynamicLyrics)
    BoxWithConstraints(Modifier.testTag("player-lyrics-page").fillMaxSize().background(pageColor)) {
        if(lines.isEmpty()) Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
            Text(text.ifBlank {"还没有歌词\n在设置 → 歌词样式中导入当前歌曲歌词"},style=style,color=muted.copy(alpha=.6f))
        } else {
            val width=with(density) {(maxWidth-48.dp).roundToPx().coerceAtLeast(1)}
            // Measure the same fixed text layout used below. Scaling affects drawing only;
            // wrapped/translated rows keep their height throughout the transition.
            val heights=remember(lines,width,style,settings.lyricGap,typeface,density) {
                val padding=with(density) {(settings.lyricGap.dp/2+6.dp).roundToPx()*2}
                lines.map {measurer.measure(AnnotatedString(it.text.ifBlank {"·"}),style,constraints=Constraints(maxWidth=width)).size.height+padding}
            }
            val centers=remember(heights) {
                var top=0f
                heights.map {height -> (top+height/2f).also {top+=height}}
            }
            // A lazy viewport keeps the frame cost independent of the complete LRC length.
            // Prefix heights include wrapped translations; half-screen padding centers any row.
            val tops=remember(heights) {var top=0f;heights.map {height -> top.also {top+=height}}}
            val activeColor=MaterialTheme.colorScheme.primary
            key(lines,p.currentId,width,style,settings.lyricGap,typeface) {
                val initial=Lrc.current(lines,model.playbackPosition())
                val scroll=rememberLazyListState(initialFirstVisibleItemIndex=initial.coerceAtLeast(0),
                    initialFirstVisibleItemScrollOffset=if(initial >= 0) heights[initial]/2 else 0)
                fun offset(): Float=tops.getOrElse(scroll.firstVisibleItemIndex) {0f}+scroll.firstVisibleItemScrollOffset
                var current by remember {mutableIntStateOf(initial)}
                val visual=remember {mutableStateOf(LyricFrame(if(initial >= 0) mapOf(initial to 1f) else emptyMap(),centers.getOrElse(initial) {0f},false))}
                var follow by remember {mutableStateOf(true)}
                var fingerDown by remember {mutableStateOf(false)}
                var touched by remember {mutableLongStateOf(0)}
                var seekRequest by remember {mutableLongStateOf(0)}
                fun browse(down: Boolean) {follow=false;fingerDown=down;touched=android.os.SystemClock.elapsedRealtime()}
                LaunchedEffect(follow,touched) {
                    if(!follow) snapshotFlow {scroll.isScrollInProgress || fingerDown}.collectLatest {busy ->
                        if(!busy) {delay(1500);follow=true}
                    }
                }
                LaunchedEffect(scroll,lines,lifecycle) {
                    lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                        snapshotFlow {scroll.layoutInfo.totalItemsCount}.first {it == lines.size}
                        var transition: LyricTransition?=null
                        var mediaClock=false
                        var finishPosition=0L
                        var motionSpeed=latestPlayback.speed
                        var suppressedLead=-1
                        var lastSeek=latestPlayback.seekRevision
                        var lastRequest=seekRequest
                        val initialClock=android.os.SystemClock.elapsedRealtime()
                        var wasVisible=latestVisible
                        var wasFollowing=follow
                        // Recover an interrupted foreground presentation from its visible pose.
                        transition=LyricTransition(visual.value.weights,offset(),current,
                            if(follow) centers.getOrElse(current) {0f} else offset(),initialClock,300L,300L)
                        while(isActive) {
                            val now=android.os.SystemClock.elapsedRealtime()
                            val position=model.playbackPosition()
                            val actual=Lrc.current(lines,position)
                            val state=latestPlayback
                            // Media3 reports real discontinuities. A delayed frame or a speed
                            // change must not be mistaken for a seek and restart the animation.
                            val seeking=seekRequest != lastRequest || state.seekRevision != lastSeek
                            val entering=latestVisible && !wasVisible
                            if(seeking || entering || !latestVisible) follow=true
                            val returning=follow && !wasFollowing
                            val followChanged=follow != wasFollowing
                            val lead=Lrc.lead(lines,position,state.speed)
                            if(actual >= suppressedLead) suppressedLead=-1
                            if(seeking) suppressedLead=lead?.targetIndex ?: -1
                            val candidate=lead?.takeIf {it.targetIndex != suppressedLead}?.targetIndex ?: actual
                            // Slowing playback can move the lead window into the future. Keep
                            // the already selected row instead of briefly scrolling backward.
                            val anticipated=if(mediaClock && current > actual && !seeking) current else candidate
                            val retime=mediaClock && state.playing && transition != null && current > actual && state.speed != motionSpeed
                            val targetIndex=when {
                                seeking -> actual
                                state.playing -> anticipated
                                entering || returning -> actual
                                else -> current // Pausing an anticipated row freezes its presentation.
                            }
                            val changed=targetIndex != current
                            if(changed || seeking || entering || returning || followChanged || retime) {
                                val weights=visual.value.weights
                                val target=if(follow && (latestDynamic || seeking || entering || returning)) centers.getOrElse(targetIndex) {0f} else offset()
                                val targetLead=lead?.takeIf {it.targetIndex == targetIndex} ?: if(retime)
                                    Lrc.lead(lines,lines[targetIndex].startTime-1L,state.speed) else null
                                val natural=state.playing && !seeking && (changed || entering || returning || retime) && targetLead?.targetIndex == targetIndex
                                if(natural) {
                                    // Start from the actual rendered frame, never backdate new weights.
                                    // Both layers and scrolling reach the next row before its timestamp.
                                    finishPosition=targetLead!!.finishAtPosition
                                    motionSpeed=state.speed
                                    val remaining=(finishPosition-position).coerceAtLeast(0L)
                                    transition=LyricTransition(weights,offset(),targetIndex,target,position,remaining,remaining)
                                    mediaClock=true
                                } else {
                                    val remaining=if(mediaClock && !seeking && !entering && !returning && followChanged) (finishPosition-position).coerceAtLeast(0L) else 300L
                                    val keepMedia=mediaClock && !seeking && !entering && !returning && followChanged
                                    transition=LyricTransition(weights,offset(),targetIndex,target,if(keepMedia) position else now,remaining,remaining)
                                    mediaClock=keepMedia
                                }
                                current=targetIndex
                            }
                            transition?.let {motion ->
                                val frame=motion.sample(if(mediaClock) position else now)
                                visual.value=frame
                                if(follow) {
                                    val delta=frame.offset-offset()
                                    if(kotlin.math.abs(delta) > .1f) try {scroll.scrollBy(delta)} catch(e: kotlinx.coroutines.CancellationException) {
                                        // Touch owns the scroll mutex; lifecycle cancellation still exits.
                                        if(!isActive) throw e
                                    }
                                }
                                if(!frame.running) transition=null
                            }
                            lastSeek=state.seekRevision;lastRequest=seekRequest;wasVisible=latestVisible;wasFollowing=follow
                            if(latestVisible && (state.playing || transition != null && !mediaClock)) withFrameNanos {} else delay(50)
                        }
                    }
                }
                LazyColumn(Modifier.testTag("lyrics-list").fillMaxSize().pointerInput(text) {
                    awaitEachGesture {awaitFirstDown(requireUnconsumed=false);browse(true);waitForUpOrCancellation();browse(false)}
                },state=scroll,contentPadding=PaddingValues(horizontal=24.dp,vertical=maxHeight/2),horizontalAlignment=Alignment.CenterHorizontally) {
                    itemsIndexed(lines,key={index,_ -> index}) {index,line ->
                        val weight=remember(index) {derivedStateOf(structuralEqualityPolicy()) {visual.value.weights[index] ?: 0f}}
                        Box(Modifier.fillMaxWidth().height(with(density) {heights[index].toDp()}).testTag(if(index == current) "current-lyric" else "lyric-$index")
                            .clickable {model.seek(line.startTime);seekRequest++;follow=true},contentAlignment=Alignment.Center) {
                            // Static glyph layers: only alpha and the shared transform change per frame.
                            // Keep one accessible text label, despite the two visual color layers.
                            Box(Modifier.fillMaxWidth().graphicsLayer {scaleX=1f+.05f*weight.value;scaleY=scaleX}) {
                                BasicText(line.text.ifBlank {"·"},Modifier.fillMaxWidth().graphicsLayer {alpha=.6f},style=style,color={muted})
                                BasicText(line.text.ifBlank {"·"},Modifier.fillMaxWidth().clearAndSetSemantics {}.graphicsLayer {alpha=weight.value},style=style,color={activeColor})
                            }
                        }
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
