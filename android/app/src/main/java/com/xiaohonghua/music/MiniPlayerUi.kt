package com.xiaohonghua.music

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.StateFlow

/** Keep the 100ms playback ticks inside this small surface, away from the library and pager. */
@Composable internal fun MiniPlayer(song: Song,playback: StateFlow<PlaybackState>,onPrevious:()->Unit,
    onToggle:()->Unit,onNext:()->Unit,onOpen:()->Unit) {
    val p by playback.collectAsStateWithLifecycle()
    val source=remember {MutableInteractionSource()}
    val toggleSource=remember {MutableInteractionSource()}
    val haptic=rememberActionHaptic()
    val shape=RoundedCornerShape(26.dp)
    val active=p.currentId == song.id
    val duration=if(active && p.duration > 0L) p.duration else song.duration
    val enabled=p.connected && p.currentId != null
    Surface(Modifier.testTag("mini-player-surface").fillMaxWidth(),shape=shape,
        color=MaterialTheme.colorScheme.surface,tonalElevation=0.dp,shadowElevation=0.dp) {
        Box(Modifier.fillMaxWidth().drawWithCache {
            val unit=1.dp.toPx()
            val w=size.width/unit;val h=size.height/unit
            // Five alternating leaves share one stem along the right lower frame.
            // Leaf roots sit on its knots; the rising tip stays outside the next icon.
            val stem=Path().apply {
                moveTo(w-132f,h-2f);quadraticTo(w-120f,h-2f,w-110f,h-3f)
                quadraticTo(w-100f,h-4f,w-88f,h-5f)
                quadraticTo(w-76f,h-9f,w-66f,h-10f)
                quadraticTo(w-49f,h-5f,w-36f,h-9f)
                quadraticTo(w-19f,h-13f,w-13f,h-21f)
                quadraticTo(w-9f,h-25f,w-9f,h-29f)
            }
            val firstLeaf=Path().apply {
                moveTo(w-110f,h-3f);cubicTo(w-115f,h-10f,w-124f,h-12f,w-131f,h-10f)
                cubicTo(w-129f,h-3f,w-118f,h-1f,w-110f,h-3f);close()
            }
            val secondLeaf=Path().apply {
                moveTo(w-88f,h-5f);cubicTo(w-87f,h-13f,w-78f,h-18f,w-71f,h-17f)
                cubicTo(w-72f,h-9f,w-80f,h-4f,w-88f,h-5f);close()
            }
            val thirdLeaf=Path().apply {
                moveTo(w-66f,h-10f);cubicTo(w-72f,h-13f,w-81f,h-8f,w-87f,h-3f)
                cubicTo(w-78f,h-.8f,w-70f,h-2f,w-66f,h-10f);close()
            }
            val fourthLeaf=Path().apply {
                moveTo(w-36f,h-9f);cubicTo(w-39f,h-18f,w-48f,h-21f,w-56f,h-19f)
                cubicTo(w-54f,h-11f,w-44f,h-7f,w-36f,h-9f);close()
            }
            val fifthLeaf=Path().apply {
                moveTo(w-13f,h-21f);cubicTo(w-18f,h-26f,w-16f,h-32f,w-10f,h-36f)
                cubicTo(w-5f,h-30f,w-4f,h-24f,w-13f,h-21f);close()
            }
            val veins=Path().apply {
                moveTo(w-110f,h-3f);quadraticTo(w-120f,h-5f,w-127f,h-9f)
                moveTo(w-88f,h-5f);quadraticTo(w-82f,h-10f,w-74f,h-15f)
                moveTo(w-66f,h-10f);quadraticTo(w-74f,h-6f,w-83f,h-3f)
                moveTo(w-36f,h-9f);quadraticTo(w-45f,h-12f,w-52f,h-18f)
                moveTo(w-13f,h-21f);quadraticTo(w-11f,h-27f,w-10f,h-32f)
            }
            onDrawBehind {withTransform({scale(unit,unit,pivot=Offset.Zero)}) {
                drawPath(stem,Color(0xBB7F9974),style=Stroke(.7f,cap=StrokeCap.Round))
                drawPath(firstLeaf,Color(0xFF99B88B));drawPath(secondLeaf,Color(0xFF7D9E70))
                drawPath(thirdLeaf,Color(0xFFA8C59A));drawPath(fourthLeaf,Color(0xFF8FAF81))
                drawPath(fifthLeaf,Color(0xFF9CBA8E))
                drawPath(veins,Color(0x99728A69),style=Stroke(.45f,cap=StrokeCap.Round))
            }}
        }) {
        Row(Modifier.testTag("mini-player").fillMaxWidth().heightIn(min=72.dp).clip(shape)
            .clickable(interactionSource=source,indication=null,role=Role.Button,
                onClickLabel="打开播放器",onClick=onOpen).padding(horizontal=8.dp,vertical=4.dp),
            verticalAlignment=Alignment.CenterVertically) {
            MiniPlayerFlowerArtwork(song,active && p.playing,if(active) p.position else 0L,duration,
                Modifier.size(64.dp))
            Column(Modifier.testTag("mini-player-info").weight(1f).padding(start=10.dp,end=4.dp),
                verticalArrangement=Arrangement.spacedBy(5.dp)) {
                Text(song.title,Modifier.testTag("mini-player-title"),fontSize=15.sp,fontWeight=FontWeight.Medium,
                    color=MaterialTheme.colorScheme.onSurface,maxLines=1,overflow=TextOverflow.Ellipsis)
                Text(song.artist,Modifier.testTag("mini-player-artist"),fontSize=11.sp,
                    color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1,overflow=TextOverflow.Ellipsis)
            }
            ExpressiveIconButton(onClick=onPrevious,enabled=enabled,tactile=true,
                modifier=Modifier.testTag("mini-previous").size(48.dp)) {
                Icon(Icons.Outlined.SkipPrevious,"上一首",Modifier.size(23.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=if(enabled) 1f else .38f))
            }
            Box(Modifier.testTag("mini-toggle").size(48.dp).pressScale(toggleSource,.94f)
                .clickable(enabled=enabled,interactionSource=toggleSource,indication=null,role=Role.Button,
                    onClick={haptic();onToggle()}),contentAlignment=Alignment.Center) {
                Crossfade(p.playing,animationSpec=tween(120),label="mini-play-state") {playing ->
                    Icon(if(playing) Icons.Outlined.Pause else Icons.Filled.PlayArrow,
                        if(playing) "暂停" else "播放",Modifier.size(25.dp),tint=MaterialTheme.colorScheme.primary.copy(alpha=if(enabled) 1f else .38f))
                }
            }
            ExpressiveIconButton(onClick=onNext,enabled=enabled,tactile=true,
                modifier=Modifier.testTag("mini-next").size(48.dp)) {
                Icon(Icons.Outlined.SkipNext,"下一首",Modifier.size(23.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=if(enabled) 1f else .38f))
            }
        }
        }
    }
}
