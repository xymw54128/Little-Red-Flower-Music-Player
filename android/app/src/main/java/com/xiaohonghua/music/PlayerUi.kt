@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class,androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.xiaohonghua.music

import androidx.compose.animation.core.*
import androidx.compose.animation.Crossfade
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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive

internal val LyricMotionRunningKey=SemanticsPropertyKey<Boolean>("LyricMotionRunning")
internal var SemanticsPropertyReceiver.lyricMotionRunning by LyricMotionRunningKey

@Composable internal fun PlayerScreen(song: Song?,library: LibraryState,p: PlaybackState,model: MusicModel,
    onBack:()->Unit,onQueue:()->Unit,onAdd:()->Unit,onInfo:()->Unit={}) {
    val pager=rememberPagerState {2}
    val scope=rememberCoroutineScope()
    val lyricText=library.manualLyrics[song?.id] ?: song?.lyrics.orEmpty()
    val lines=remember(lyricText,song?.duration) {Lrc.parse(lyricText,song?.duration?.takeIf {it > 0})}
    val pageMotion=tween<Float>(360,easing=FastOutSlowInEasing)
    val lyricsVisible by remember {derivedStateOf {pager.currentPage == 1 || pager.currentPageOffsetFraction > 0f}}
    val lyricHeaderSettled by remember {derivedStateOf {pager.settledPage == 1}}
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    val surface=pageColor
    val foreground=ink
    val secondary=muted
    fun openLyrics() {scope.launch {pager.animateScrollToPage(1,animationSpec=pageMotion)}}
    CompositionLocalProvider(LocalContentColor provides foreground) {
    Box(Modifier.fillMaxSize().background(surface)) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal=8.dp,vertical=4.dp),verticalAlignment=Alignment.CenterVertically) {
                ExpressiveIconButton(onClick=onBack) {Icon(Icons.Outlined.KeyboardArrowDown,"收起播放器")}
                // Keep both header layouts mounted. Switching the text at currentPage's midpoint
                // used to remeasure this row and move its flowers during an in-flight fade.
                Box(Modifier.weight(1f).padding(horizontal=4.dp),contentAlignment=Alignment.Center) {
                    Text("正在播放",Modifier.testTag("player-cover-header-title").graphicsLayer {
                        alpha=1f-(pager.currentPage+pager.currentPageOffsetFraction).coerceIn(0f,1f)
                        compositingStrategy=CompositingStrategy.ModulateAlpha
                    },textAlign=TextAlign.Center,fontSize=13.sp,color=secondary,maxLines=1)
                    Row(Modifier.testTag("lyrics-title-flowers").fillMaxWidth().graphicsLayer {
                        alpha=(pager.currentPage+pager.currentPageOffsetFraction).coerceIn(0f,1f)
                        compositingStrategy=CompositingStrategy.ModulateAlpha
                    },verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(1.dp,Alignment.CenterHorizontally)) {
                        BotanicalTitleSprig(false,Modifier.testTag("lyrics-title-sprig-left").size(36.dp,36.dp))
                        Text(song?.let {"${it.title} - ${it.artist}"}.orEmpty(),Modifier.testTag("player-header-title").weight(1f,fill=false).basicMarquee(
                            // A reversal during a swipe leaves the existing marquee running;
                            // it starts/stops only after the pager has actually settled.
                            iterations=if(lyricHeaderSettled && lifecycleState.isAtLeast(Lifecycle.State.STARTED)) Int.MAX_VALUE else 0,
                            initialDelayMillis=1200,repeatDelayMillis=1200,velocity=24.dp),textAlign=TextAlign.Center,fontSize=13.sp,color=secondary,maxLines=1,softWrap=false,overflow=TextOverflow.Clip)
                        BotanicalTitleSprig(true,Modifier.testTag("lyrics-title-sprig-right").size(36.dp,36.dp))
                    }
                }
                InteractiveHeaderFlower("player")
            }
            if(song == null) Column(Modifier.weight(1f).fillMaxWidth(),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
                Icon(Icons.Outlined.LocalFlorist,null,Modifier.size(72.dp),tint=MaterialTheme.colorScheme.primary); Text("从音乐库选择一首歌曲",Modifier.padding(24.dp),color=muted)
            } else HorizontalPager(pager,Modifier.testTag("player-pages").weight(1f).fillMaxWidth(),beyondViewportPageCount=1,
                flingBehavior=PagerDefaults.flingBehavior(pager,snapAnimationSpec=pageMotion)) { page ->
                if(page == 0) BoxWithConstraints(Modifier.fillMaxSize()) {
                    val favorite=library.playlists.firstOrNull {it.isFavorites}?.songIds?.contains(song.id) == true
                    val compact=maxHeight < 400.dp
                    Column(Modifier.testTag("player-cover-content").fillMaxSize().padding(horizontal=23.dp,vertical=6.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                        // Reserve the actual lyric height first; artwork receives only the remainder.
                        Column(Modifier.weight(1f).fillMaxWidth(),horizontalAlignment=Alignment.CenterHorizontally) {
                            BoxWithConstraints(Modifier.testTag("player-artwork-area").weight(1f).fillMaxWidth(),contentAlignment=Alignment.Center) {
                                // The C frame extends 6.25% past each cover edge. Reserve its
                                // vertical footprint without taking space from lyrics or controls.
                                val coverSize=minOf((maxWidth-4.dp).coerceAtLeast(0.dp),maxHeight/1.125f,400.dp)
                                if(coverSize > 0.dp) GalleryArtwork(song,p.playing,!lyricsVisible,Modifier.size(coverSize),::openLyrics)
                            }
                            Row(Modifier.fillMaxWidth().padding(top=if(compact) 10.dp else 18.dp),verticalAlignment=Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(song.title,Modifier.testTag("player-title"),fontSize=27.sp,fontWeight=FontWeight.SemiBold,maxLines=2,overflow=TextOverflow.Ellipsis)
                                    Text("${song.artist} · ${song.album}",Modifier.testTag("player-artist-album").padding(top=8.dp),fontSize=12.sp,color=secondary,maxLines=2,overflow=TextOverflow.Ellipsis)
                                }
                                FavoriteButton(song,favorite) {model.repository.toggleFavorite(song.id)}
                            }
                            Row(Modifier.testTag("player-quality").fillMaxWidth().padding(top=10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(9.dp)) {
                                if(song.format.isNotBlank()) Text(song.format,Modifier.border(1.dp,secondary.copy(alpha=.20f),RoundedCornerShape(3.dp)).padding(horizontal=4.dp,vertical=1.dp),fontSize=9.sp,color=secondary)
                                Text(listOfNotNull(song.bitDepth.takeIf {it > 0}?.let {"$it bit"},song.sampleRate.takeIf {it > 0}?.let {"${it/1000f} kHz"}).joinToString(" / "),Modifier.weight(1f),fontSize=10.sp,color=secondary,maxLines=1,overflow=TextOverflow.Ellipsis)
                                // Match the 48dp favorite column above; the enlarged flower shares its center.
                                Box(Modifier.width(48.dp).height(36.dp),contentAlignment=Alignment.Center) {
                                    PlaybackFlower(p.playing,!lyricsVisible)
                                }
                            }
                        }
                        Box(Modifier.testTag("player-lyric-preview").fillMaxWidth().heightIn(min=48.dp).clickable(onClickLabel="查看歌词",onClick=::openLyrics).padding(vertical=6.dp),contentAlignment=Alignment.Center) {
                            PlayerLyricPreview(lines,library.settings,p,!lyricsVisible,model::playbackPosition)
                        }
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
            PlayerControls(song?.duration ?: 0,model,onQueue,surface,secondary,song != null && pager.currentPage == 0,onAdd,onInfo)
        }
    }
    }
}
@Composable private fun GalleryArtwork(song: Song,playing: Boolean,visible: Boolean,modifier: Modifier,onLyrics:()->Unit) {
    val press=remember {MutableInteractionSource()}
    val shape=RoundedCornerShape(22.dp)
    val image=rememberImage(song.artwork,with(LocalDensity.current) {400.dp.roundToPx().coerceIn(64,1600)},song.artworkRevision)
    Box(modifier.testTag("player-artwork").pressScale(press,.985f)
        .clickable(interactionSource=press,indication=null,onClickLabel="查看歌词",onClick=onLyrics),contentAlignment=Alignment.Center) {
        Box(Modifier.fillMaxSize().shadow(12.dp,shape,ambientColor=Color(0x14477859),spotColor=Color(0x14477859))
            .clip(shape).background(MaterialTheme.colorScheme.primary.copy(alpha=.08f)),contentAlignment=Alignment.Center) {
            if(image != null) Image(image,null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
            else Icon(Icons.Outlined.LocalFlorist,null,Modifier.fillMaxSize(.40f),tint=MaterialTheme.colorScheme.primary.copy(alpha=.8f))
        }
        BotanicalCoverFrame(playing,visible,Modifier.matchParentSize())
    }
}
@Composable private fun PlayerControls(fallbackDuration: Long,model: MusicModel,onQueue:()->Unit,surface: Color,secondary: Color,
    showOptions: Boolean,onAdd:()->Unit,onInfo:()->Unit) {
    val p by model.playback.collectAsStateWithLifecycle()
    val haptic=rememberActionHaptic()
    val playPress=remember {MutableInteractionSource()}
    var drag by remember { mutableStateOf<Float?>(null) }
    var menu by remember {mutableStateOf(false)}
    LaunchedEffect(p.currentId) {drag=null}
    LaunchedEffect(showOptions) {if(!showOptions) menu=false}
    val duration=(if(p.duration > 0) p.duration else fallbackDuration).coerceAtLeast(1)
    Column(Modifier.fillMaxWidth().background(surface).padding(horizontal=22.dp,vertical=8.dp)) {
        p.error?.let { Text(it,fontSize=12.sp,color=MaterialTheme.colorScheme.error,maxLines=2,modifier=Modifier.padding(bottom=8.dp)) }
        PlaybackSeekBar(p,duration,drag,onChange={drag=it},onFinished={drag?.let {model.seek(it.toLong());haptic()};drag=null})
        val shownPosition=(drag?.toLong() ?: p.position).coerceIn(0,duration)
        Row(Modifier.fillMaxWidth()) { Text(timeLabel(shownPosition),Modifier.testTag("player-elapsed").weight(1f),fontSize=10.sp,color=secondary); Text(timeLabel(duration),Modifier.testTag("player-duration"),fontSize=10.sp,color=secondary) }
        Row(Modifier.fillMaxWidth().padding(top=12.dp,bottom=4.dp),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
            ExpressiveIconButton(onClick={model.mode()},enabled=p.currentId != null,tactile=true) {Icon(when(p.mode) {2 -> Icons.Outlined.RepeatOne; 3 -> Icons.Outlined.Shuffle; else -> Icons.Outlined.Repeat},listOf("顺序播放","列表循环","单曲循环","随机播放")[p.mode],tint=if(p.mode == 0) secondary else MaterialTheme.colorScheme.primary)}
            ExpressiveIconButton(onClick={model.previous()},enabled=p.currentId != null,modifier=Modifier.size(48.dp),tactile=true) {Icon(Icons.Outlined.SkipPrevious,"上一首",Modifier.size(26.dp))}
            FilledIconButton(onClick={model.toggle();haptic()},enabled=p.currentId != null && p.connected,modifier=Modifier.size(66.dp).pressScale(playPress,.94f),shape=CircleShape,interactionSource=playPress) {
                Crossfade(targetState=p.playing,animationSpec=tween(120),label="play-pause") {playing ->
                    Icon(if(playing) Icons.Outlined.Pause else Icons.Filled.PlayArrow,if(playing) "暂停" else "播放",Modifier.size(30.dp))
                }
            }
            ExpressiveIconButton(onClick={model.next()},enabled=p.currentId != null,modifier=Modifier.size(48.dp),tactile=true) {Icon(Icons.Outlined.SkipNext,"下一首",Modifier.size(26.dp))}
            ExpressiveIconButton(onClick=onQueue) {Icon(Icons.Outlined.QueueMusic,"播放队列")}
        }
        Box(Modifier.fillMaxWidth().heightIn(min=48.dp),contentAlignment=Alignment.Center) {
            Text(listOf("顺序播放","列表循环","单曲循环","随机播放")[p.mode],fontSize=10.sp,color=secondary,textAlign=TextAlign.Center)
            if(showOptions) Box(Modifier.align(Alignment.CenterEnd)) {
                ExpressiveIconButton(onClick={menu=true},modifier=Modifier.testTag("player-more")) {Icon(Icons.Outlined.MoreHoriz,"歌曲更多选项",Modifier.size(20.dp),tint=menuTint)}
                AppDropdownMenu(menu,{menu=false}) {
                    AppDropdownMenuItem(text={Text("添加到歌单")},leadingIcon={Icon(Icons.Outlined.PlaylistAdd,null,Modifier.size(20.dp))},onClick={menu=false;onAdd()})
                    AppDropdownMenuItem(text={Text("歌曲信息")},leadingIcon={Icon(Icons.Outlined.Info,null,Modifier.size(20.dp))},onClick={menu=false;onInfo()})
                }
            }
        }
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
    val inactiveThumb=muted.copy(alpha=.35f)
    Slider(value=drag ?: progress.value,onValueChange=onChange,onValueChangeFinished=onFinished,valueRange=0f..duration.toFloat(),enabled=p.currentId != null,
        modifier=Modifier.testTag("playback-seek").fillMaxWidth().height(44.dp),interactionSource=interaction,
        thumb={ Canvas(Modifier.size(20.dp)) {drawCircle(if(p.currentId != null) accent else inactiveThumb,radius.dp.toPx())} },
        track={ state -> Canvas(Modifier.fillMaxWidth().height(3.dp)) {
            val start=Offset(0f,size.height/2);val end=Offset(size.width,size.height/2)
            drawLine(trackColor,start,end,strokeWidth=size.height,cap=StrokeCap.Round)
            val fraction=(state.value/duration.toFloat()).coerceIn(0f,1f)
            if(fraction > 0f) drawLine(accent,start,Offset(size.width*fraction,end.y),strokeWidth=size.height,cap=StrokeCap.Round)
        } })
}
/** The small preview follows the media clock directly; only a changed line recomposes. */
@Composable internal fun PlayerLyricPreview(lines: List<LyricLine>,settings: Settings,p: PlaybackState,visible: Boolean,position: ()->Long) {
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    val lifecycleState by lifecycle.currentStateFlow.collectAsState()
    val latestPosition by rememberUpdatedState(position)
    var current by remember(lines,p.currentId) {mutableIntStateOf(Lrc.current(lines,position()))}
    LaunchedEffect(lines,p.currentId,p.playing,p.seekRevision,visible,lifecycle) {
        if(!visible) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            current=Lrc.current(lines,latestPosition())
            while(isActive) {
                if(p.playing) withFrameNanos {} else delay(50)
                // No lead window here: the preview switches at the exact LRC timestamp.
                current=Lrc.current(lines,latestPosition())
            }
        }
    }
    val size=settings.lyricSize.coerceAtMost(20f)
    Text(lines.getOrNull(current)?.text ?: "点击封面或左滑查看歌词",Modifier.testTag("player-lyric-preview-text").basicMarquee(
        iterations=if(visible && lifecycleState.isAtLeast(Lifecycle.State.STARTED)) Int.MAX_VALUE else 0,
        initialDelayMillis=1200,repeatDelayMillis=1200,velocity=28.dp),textAlign=TextAlign.Center,fontSize=size.sp,lineHeight=(size*1.5f).sp,
        fontFamily=lyricFont(settings.lyricFont),color=MaterialTheme.colorScheme.primary,maxLines=1,softWrap=false,overflow=TextOverflow.Clip)
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
    val surface=pageColor
    // Bake the idle opacity into this flat background; every glyph is painted once.
    val inactiveColor=muted.copy(alpha=.6f).compositeOver(surface)
    BoxWithConstraints(Modifier.testTag("player-lyrics-page").fillMaxSize().background(surface).drawWithCache {
        // Feather the fixed viewport, not individual rows. Cached gradients are equivalent
        // to an alpha fade over this flat page color and avoid a full-screen blur layer.
        val edge=minOf(56.dp.toPx(),size.height*.16f)
        val top=Brush.verticalGradient(listOf(surface,surface.copy(alpha=0f)),0f,edge.coerceAtLeast(1f))
        val bottom=Brush.verticalGradient(listOf(surface.copy(alpha=0f),surface),(size.height-edge).coerceAtLeast(0f),size.height.coerceAtLeast(1f))
        onDrawWithContent {
            drawContent()
            drawRect(top,size=Size(size.width,edge))
            drawRect(bottom,topLeft=Offset(0f,size.height-edge),size=Size(size.width,edge))
        }
    }) {
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
                val motionRunning by remember {derivedStateOf {visual.value.running}}
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
                    val motionDurationScale=coroutineContext[MotionDurationScale]
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
                        var previousDurationScale=(motionDurationScale?.scaleFactor ?: 1f).takeIf {it.isFinite()}?.coerceAtLeast(0f) ?: 1f
                        val recoveryDuration=(300.0*previousDurationScale).toLong()
                        // Recover an interrupted foreground presentation from its visible pose.
                        transition=LyricTransition(visual.value.weights,offset(),current,
                            if(follow) centers.getOrElse(current) {0f} else offset(),initialClock,recoveryDuration,recoveryDuration)
                        while(isActive) {
                            val now=android.os.SystemClock.elapsedRealtime()
                            val position=model.playbackPosition()
                            val actual=Lrc.current(lines,position)
                            val state=latestPlayback
                            val durationScale=(motionDurationScale?.scaleFactor ?: 1f).takeIf {it.isFinite()}?.coerceAtLeast(0f) ?: 1f
                            val motionEnabled=durationScale > 0f
                            val scaleChanged=durationScale != previousDurationScale
                            // Media3 reports real discontinuities. A delayed frame or a speed
                            // change must not be mistaken for a seek and restart the animation.
                            val seeking=seekRequest != lastRequest || state.seekRevision != lastSeek
                            val entering=latestVisible && !wasVisible
                            if(seeking || entering || !latestVisible) follow=true
                            val returning=follow && !wasFollowing
                            val followChanged=follow != wasFollowing
                            val lead=if(motionEnabled) Lrc.lead(lines,position,state.speed) else null
                            if(actual >= suppressedLead) suppressedLead=-1
                            if(seeking) suppressedLead=lead?.targetIndex ?: -1
                            val candidate=lead?.takeIf {it.targetIndex != suppressedLead}?.targetIndex ?: actual
                            // Slowing playback can move the lead window into the future. Keep
                            // the already selected row instead of briefly scrolling backward.
                            val anticipated=if(mediaClock && current > actual && !seeking) current else candidate
                            val retime=motionEnabled && mediaClock && state.playing && transition != null && current > actual && state.speed != motionSpeed
                            val targetIndex=when {
                                !motionEnabled -> actual
                                seeking -> actual
                                state.playing -> anticipated
                                entering || returning -> actual
                                else -> current // Pausing an anticipated row freezes its presentation.
                            }
                            val changed=targetIndex != current
                            if(changed || seeking || entering || returning || followChanged || retime || scaleChanged) {
                                val weights=visual.value.weights
                                val target=if(follow && (latestDynamic || seeking || entering || returning)) centers.getOrElse(targetIndex) {0f} else offset()
                                val targetLead=lead?.takeIf {it.targetIndex == targetIndex} ?: if(retime)
                                    Lrc.lead(lines,lines[targetIndex].startTime-1L,state.speed) else null
                                val natural=motionEnabled && state.playing && !seeking && (changed || entering || returning || retime || scaleChanged) && targetLead?.targetIndex == targetIndex
                                if(natural) {
                                    // Start from the actual rendered frame, never backdate new weights.
                                    // Both layers and scrolling reach the next row before its timestamp.
                                    finishPosition=targetLead!!.finishAtPosition
                                    motionSpeed=state.speed
                                    val remaining=(finishPosition-position).coerceAtLeast(0L)
                                    transition=LyricTransition(weights,offset(),targetIndex,target,position,remaining,remaining)
                                    mediaClock=true
                                } else {
                                    // A disabled animation snaps both text layers and centering together.
                                    // Scale wall-clock seeks/returns, while natural media deadlines stay tied to the LRC.
                                    val keepMedia=motionEnabled && mediaClock && !seeking && !entering && !returning && (followChanged || scaleChanged)
                                    val remaining=if(!motionEnabled) 0L else if(keepMedia) (finishPosition-position).coerceAtLeast(0L) else (300.0*durationScale).toLong()
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
                            lastSeek=state.seekRevision;lastRequest=seekRequest;wasVisible=latestVisible;wasFollowing=follow;previousDurationScale=durationScale
                            if(latestVisible && (state.playing || transition != null && !mediaClock)) withFrameNanos {} else delay(50)
                        }
                    }
                }
                LazyColumn(Modifier.testTag("lyrics-list").semantics {lyricMotionRunning=motionRunning}.fillMaxSize().pointerInput(text) {
                    awaitEachGesture {awaitFirstDown(requireUnconsumed=false);browse(true);waitForUpOrCancellation();browse(false)}
                },state=scroll,contentPadding=PaddingValues(horizontal=24.dp,vertical=maxHeight/2),horizontalAlignment=Alignment.CenterHorizontally) {
                    itemsIndexed(lines,key={index,_ -> index}) {index,line ->
                        val weight=remember(index) {derivedStateOf(structuralEqualityPolicy()) {visual.value.weights[index] ?: 0f}}
                        Box(Modifier.fillMaxWidth().height(with(density) {heights[index].toDp()}).testTag(if(index == current) "current-lyric" else "lyric-$index")
                            .clickable {model.seek(line.startTime.coerceIn(0,latestPlayback.duration.takeIf {it > 0} ?: Long.MAX_VALUE));seekRequest++;follow=true},contentAlignment=Alignment.Center) {
                            // One opaque glyph pass avoids transparent text-layer reuse artifacts.
                            // Color and scale still read the same frame weight, without remeasuring text.
                            BasicText(line.text.ifBlank {"·"},Modifier.fillMaxWidth().graphicsLayer {
                                scaleX=1f+.05f*weight.value;scaleY=scaleX
                            },style=style,color={lerp(inactiveColor,activeColor,weight.value)})
                        }
                    }
                }
            }
        }
    }
}

@Composable internal fun OrderSheet(songs: List<Song>,onCancel:()->Unit,onSave:(List<String>)->Unit) {
    val source=remember(songs) {songs.map {it.id}.distinct()}
    val byId=remember(songs) {songs.associateBy {it.id}}
    var draft by remember {mutableStateOf(source)}
    var dragging by remember {mutableStateOf(false)}
    LaunchedEffect(source) {draft=reconcileReorderIds(draft,source)}
    val order=remember(draft,source) {reconcileReorderIds(draft,source)}
    val ordered=remember(order,byId) {order.mapNotNull(byId::get)}
    val sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)
    val dismiss=rememberSheetDismiss(sheetState)
    var closing by remember {mutableStateOf(false)}
    AppModalBottomSheet(onDismissRequest=onCancel,modifier=Modifier.fillMaxHeight(),sheetState=sheetState,sheetGesturesEnabled=!closing) {
        Column(Modifier.fillMaxWidth().fillMaxHeight()) {
            SheetTitle("自定义顺序 · "+order.size+" 首","按住手柄拖动，点按手柄可逐首移动")
            ReorderList(ordered,onReorder={draft=it},modifier=Modifier.weight(1f).fillMaxWidth(),onDraggingChanged={dragging=it})
            HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant.copy(alpha=.55f))
            Row(Modifier.fillMaxWidth().padding(horizontal=24.dp,vertical=16.dp),horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                OutlinedButton(onClick={if(!closing) {closing=true;dismiss(onCancel)}},enabled=!closing,modifier=Modifier.weight(1f).heightIn(min=48.dp)) {Text("取消")}
                Button(onClick={if(!closing) {closing=true;onSave(reconcileReorderIds(draft,source));dismiss(onCancel)}},enabled=!dragging && !closing,modifier=Modifier.testTag("reorder-save").weight(1f).heightIn(min=48.dp)) {Text("保存顺序")}
            }
        }
    }
}
private data class ActiveReorderDrag(val id: String,val fingerY: Float,val touchOffset: Float,val height: Float,val finishing: Boolean=false) {
    val top: Float get()=fingerY-touchOffset
    val center: Float get()=top+height/2
}
@Composable internal fun ReorderList(songs: List<Song>,onReorder:(List<String>)->Unit,modifier: Modifier=Modifier,
    onDraggingChanged:(Boolean)->Unit={},onClick:((Song)->Unit)?=null,onRemove:((Song)->Unit)?=null,currentId:String?=null) {
    val scroll=rememberLazyListState()
    val scope=rememberCoroutineScope()
    val density=LocalDensity.current
    val haptic=rememberActionHaptic()
    val latestSongs by rememberUpdatedState(songs)
    val latestReorder by rememberUpdatedState(onReorder)
    val latestDragging by rememberUpdatedState(onDraggingChanged)
    var activeDrag by remember {mutableStateOf<ActiveReorderDrag?>(null)}
    var pendingLayout by remember {mutableStateOf<List<String>?>(null)}
    val ids=remember(songs) {songs.map {it.id}}
    val latestIds by rememberUpdatedState(ids)
    val handleWidth=with(density) {48.dp.toPx()}
    val horizontalPadding=with(density) {16.dp.toPx()}
    val edge=with(density) {72.dp.toPx()}
    val speed=with(density) {900.dp.toPx()}
    fun clearDrag() {activeDrag=null;pendingLayout=null;latestDragging(false)}
    fun advanceDrag() {
        val active=activeDrag ?: return
        val order=latestIds
        val pending=pendingLayout
        if(pending != null && pending != order) {
            // A live folder/queue membership update cannot acknowledge the preceding draft.
            if(pending.toSet() == order.toSet()) return
            pendingLayout=null
        }
        val slots=scroll.layoutInfo.visibleItemsInfo.map {ReorderSlot(it.key as String,it.index,it.offset.toFloat(),it.size.toFloat())}
        val target=reorderDragTarget(order,active.id,active.center,slots) ?: return
        pendingLayout=null
        val next=moveReorderId(order,active.id,target)
        if(next != order) {pendingLayout=next;latestReorder(next);haptic()}
    }
    fun finishDrag() {
        if(activeDrag == null || activeDrag?.finishing == true) return
        val finishing=activeDrag!!.copy(finishing=true)
        activeDrag=finishing
        // Let the last layout catch up before resolving a fast final move and enabling Save.
        scope.launch {
            repeat(3) {
                withFrameNanos {}
                if(activeDrag !== finishing) return@launch
                advanceDrag()
            }
            clearDrag()
        }
    }
    fun move(id: String,direction: Int) {
        if(activeDrag != null) return
        val order=latestIds
        val from=order.indexOf(id)
        if(from < 0) return
        val next=moveReorderId(order,id,(from+direction).coerceIn(0,order.lastIndex))
        if(next != order) {latestReorder(next);haptic()}
    }
    LaunchedEffect(ids) {if(activeDrag?.id?.let {it !in ids} == true) clearDrag()}
    LaunchedEffect(density.density,density.fontScale) {if(activeDrag != null) clearDrag()}
    LaunchedEffect(activeDrag?.id,density.density,density.fontScale) {
        if(activeDrag == null) return@LaunchedEffect
        var lastFrame=withFrameNanos {it}
        while(isActive && activeDrag != null) {
            val frame=withFrameNanos {it}
            val active=activeDrag ?: break
            if(!active.finishing) {
                val layout=scroll.layoutInfo
                val velocity=reorderEdgeVelocity(active.fingerY,layout.viewportStartOffset.toFloat(),layout.viewportEndOffset.toFloat(),edge,speed)
                if(velocity != 0f) scroll.scrollBy(velocity*((frame-lastFrame)/1_000_000_000f).coerceAtMost(.05f))
                advanceDrag()
            }
            lastFrame=frame
        }
    }
    // This pointer owner never moves or leaves composition when a keyed row changes index.
    Box(modifier.clipToBounds().pointerInput(scroll,density.density,density.fontScale) {
        try {
            awaitEachGesture {
                val down=awaitFirstDown(requireUnconsumed=false)
                if(activeDrag != null || down.position.x !in (size.width-horizontalPadding-handleWidth)..(size.width-horizontalPadding)) return@awaitEachGesture
                val item=scroll.layoutInfo.visibleItemsInfo.firstOrNull {down.position.y >= it.offset && down.position.y < it.offset+it.size}
                    ?: return@awaitEachGesture
                val id=item.key as String
                val press=awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture
                if(id !in latestIds) return@awaitEachGesture
                val currentItem=scroll.layoutInfo.visibleItemsInfo.firstOrNull {it.key == id} ?: return@awaitEachGesture
                activeDrag=ActiveReorderDrag(id,press.position.y,press.position.y-currentItem.offset,currentItem.size.toFloat())
                latestDragging(true);haptic()
                press.consume()
                while(true) {
                    // Take the active gesture before LazyColumn or the sheet can claim scrolling.
                    val event=awaitPointerEvent(PointerEventPass.Initial)
                    val change=event.changes.firstOrNull {it.id == press.id}
                    if(change == null || change.isConsumed) {clearDrag();break}
                    change.consume()
                    if(!change.pressed) {finishDrag();break}
                    activeDrag=activeDrag?.copy(fingerY=change.position.y)
                    advanceDrag()
                }
            }
        } finally {if(activeDrag != null) clearDrag()}
    }) {
        LazyColumn(Modifier.testTag("reorder-list").fillMaxSize(),state=scroll,userScrollEnabled=activeDrag == null,
            contentPadding=PaddingValues(vertical=8.dp)) {
            itemsIndexed(songs,key={_,song -> song.id}) {index,song ->
                ReorderRow(song,index,songs.size,onMove={move(song.id,it)},
                    modifier=Modifier.padding(horizontal=16.dp).animateItem(fadeInSpec=null,fadeOutSpec=null,
                        placementSpec=if(activeDrag?.id == song.id) null else spring(dampingRatio=1f,stiffness=500f)).graphicsLayer {alpha=if(activeDrag?.id == song.id) 0f else 1f},
                    enabled=activeDrag == null,selected=song.id == currentId,onClick=onClick?.let {{it(song)}},onRemove=onRemove?.let {{it(song)}})
            }
        }
        activeDrag?.let {active -> latestSongs.firstOrNull {it.id == active.id}?.let {song ->
            val index=latestSongs.indexOfFirst {it.id == active.id}
            Box(Modifier.fillMaxWidth().padding(horizontal=16.dp).offset {androidx.compose.ui.unit.IntOffset(0,active.top.toInt())}
                .height(with(density) {active.height.toDp()}).zIndex(1f).clearAndSetSemantics {}.graphicsLayer {
                    shadowElevation=3.dp.toPx();shape=RoundedCornerShape(12.dp)
                }.background(MaterialTheme.colorScheme.surfaceContainer,RoundedCornerShape(12.dp))) {
                ReorderRow(song,index,songs.size,onMove={},enabled=false,selected=song.id == currentId)
            }
        } }
    }
}
@Composable internal fun ReorderRow(song: Song,index: Int,count: Int,onMove:(Int)->Unit,modifier: Modifier=Modifier,
    onClick:(()->Unit)?=null,onRemove:(()->Unit)?=null,enabled: Boolean=true,selected:Boolean=false) {
    var menu by remember(song.id) {mutableStateOf(false)}
    val shape=RoundedCornerShape(12.dp)
    Row(modifier.testTag("reorder-"+song.id).fillMaxWidth().heightIn(min=68.dp).semantics {
        customActions=buildList {
            if(enabled && index > 0) add(androidx.compose.ui.semantics.CustomAccessibilityAction("上移一位") {onMove(-1);true})
            if(enabled && index < count-1) add(androidx.compose.ui.semantics.CustomAccessibilityAction("下移一位") {onMove(1);true})
        }
    }.then(if(onClick != null) Modifier.clip(shape).clickable(enabled=enabled,onClick=onClick) else Modifier),verticalAlignment=Alignment.CenterVertically) {
        Text(""+(index+1),Modifier.width(30.dp),fontSize=12.sp,color=if(selected) MaterialTheme.colorScheme.primary else muted,textAlign=TextAlign.Center)
        Cover(song,Modifier.size(44.dp))
        Column(Modifier.weight(1f).padding(start=12.dp,end=4.dp)) {
            Text(song.title,maxLines=1,overflow=TextOverflow.Ellipsis,fontSize=14.sp,fontWeight=FontWeight.Medium,color=if(selected) MaterialTheme.colorScheme.primary else ink)
            Text("${song.artist} · ${song.album}",maxLines=1,overflow=TextOverflow.Ellipsis,fontSize=11.sp,color=muted)
        }
        if(onRemove != null) ExpressiveIconButton(onClick=onRemove,enabled=enabled,tactile=true) {Icon(Icons.Outlined.Close,"移出队列",Modifier.size(18.dp))}
        Box {
            ExpressiveIconButton(onClick={if(enabled) menu=true},enabled=enabled,modifier=Modifier.testTag("reorder-handle-"+song.id).size(48.dp)) {
                Icon(Icons.Outlined.DragHandle,"排序 "+song.title,tint=muted)
            }
            AppDropdownMenu(expanded=menu && enabled,onDismissRequest={menu=false}) {
                AppDropdownMenuItem(text={Text("上移一位")},onClick={menu=false;onMove(-1)},enabled=index > 0,
                    leadingIcon={Icon(Icons.Outlined.KeyboardArrowUp,null)},modifier=Modifier.testTag("move-up-"+song.id))
                AppDropdownMenuItem(text={Text("下移一位")},onClick={menu=false;onMove(1)},enabled=index < count-1,
                    leadingIcon={Icon(Icons.Outlined.KeyboardArrowDown,null)},modifier=Modifier.testTag("move-down-"+song.id))
            }
        }
    }
}
@Composable internal fun QueueSheet(library: LibraryState,p: PlaybackState,model: MusicModel,onFeedback:(String)->Unit={},onDismiss:()->Unit) {
    val byId=remember(library.songs) {library.songs.associateBy {it.id}}
    var pendingRemovals by remember {mutableStateOf<Set<String>>(emptySet())}
    val visibleQueue=if(p.mode == 3 && p.playOrder.isNotEmpty()) p.playOrder else p.queue
    val source=remember(visibleQueue,pendingRemovals) {visibleQueue.distinct().filterNot(pendingRemovals::contains)}
    var draft by remember {mutableStateOf(source)}
    var pending by remember {mutableStateOf<List<String>?>(null)}
    var dragging by remember {mutableStateOf(false)}
    var changed by remember {mutableStateOf(false)}
    fun liveOrder():List<String> {val live=model.playback.value;return if(live.mode == 3 && live.playOrder.isNotEmpty()) live.playOrder else live.queue}
    fun submit() {
        val order=reconcileReorderIds(draft,liveOrder().filterNot(pendingRemovals::contains))
        draft=order
        val live=model.playback.value
        if(queueOrderChanged(order,live.queue,live.playOrder,live.mode == 3)) {pending=order;model.queueOrder(order);changed=true}
    }
    LaunchedEffect(p.queue) {pendingRemovals=pendingRemovals.intersect(p.queue.toSet())}
    LaunchedEffect(pendingRemovals) {if(pendingRemovals.isNotEmpty()) {delay(2000);pendingRemovals=emptySet()}}
    LaunchedEffect(source) {
        draft=if(dragging || pending != null) reconcileReorderIds(draft,source) else source
        if(pending?.let {source == reconcileReorderIds(it,source)} == true) pending=null
    }
    // Intermediate Media3 acknowledgments must not overwrite a local drag draft.
    // If the controller rejects a request, return to its actual queue after it settles.
    LaunchedEffect(pending,dragging) {
        if(pending != null && !dragging) {delay(2000);draft=liveOrder().distinct();pending=null}
    }
    val order=remember(draft,source) {reconcileReorderIds(draft,source)}
    val ordered=remember(order,byId) {order.map {id -> byId[id] ?: Song(id,"",id,"无法读取的歌曲")}}
    val sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)
    val dismiss=rememberSheetDismiss(sheetState)
    var closing by remember {mutableStateOf(false)}
    AppModalBottomSheet(onDismissRequest=onDismiss,modifier=Modifier.fillMaxHeight(),sheetState=sheetState,sheetGesturesEnabled=!closing) {
        Column(Modifier.fillMaxWidth().fillMaxHeight()) {
            val sourceLabel=when(val origin=p.queueSource) {
                LIBRARY_QUEUE_SOURCE -> "音乐库"
                MANUAL_QUEUE_SOURCE -> "手动队列"
                else -> library.playlists.find {it.id == origin}?.name ?: if(origin?.startsWith("album:") == true) "专辑 · "+android.net.Uri.decode(origin.substringAfter("album:")).substringAfter('\u0000') else "播放队列"
            }
            val currentIndex=order.indexOf(p.currentId)
            val modeLabel=listOf("顺序播放","列表循环","单曲循环","随机播放")[p.mode]
            SheetTitle("播放队列 · "+order.size+" 首",sourceLabel+" · "+modeLabel+(if(currentIndex >= 0) " · 当前第 ${currentIndex+1} 首" else "")+"\n按住手柄拖动，点按手柄可逐首移动")
            ReorderList(ordered,currentId=p.currentId,modifier=Modifier.weight(1f).fillMaxWidth(),
                onReorder={draft=it;if(!dragging) submit()},onDraggingChanged={wasDragging -> dragging=wasDragging;if(!wasDragging) submit()},
                onClick={model.queuePlay(it.id)},onRemove={song ->
                    pendingRemovals=pendingRemovals+song.id;draft=draft-song.id;pending=pending?.minus(song.id);model.queueRemove(song.id);changed=true;onFeedback("已移出播放队列")
                })
            HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant.copy(alpha=.55f))
            TextButton(onClick={if(!closing) {closing=true;if(changed) onFeedback("队列已更新");dismiss(onDismiss)}},enabled=!dragging && !closing,
                modifier=Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=12.dp).heightIn(min=48.dp)) {Text("完成")}
        }
    }
}
