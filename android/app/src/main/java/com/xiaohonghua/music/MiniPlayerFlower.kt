package com.xiaohonghua.music

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocalFlorist
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive

internal val MiniPlayerFlowerRotationKey=SemanticsPropertyKey<Float>("MiniPlayerFlowerRotation")
internal var SemanticsPropertyReceiver.miniPlayerFlowerRotation by MiniPlayerFlowerRotationKey
internal val MiniPlayerFlowerVelocityKey=SemanticsPropertyKey<Float>("MiniPlayerFlowerVelocity")
internal var SemanticsPropertyReceiver.miniPlayerFlowerVelocity by MiniPlayerFlowerVelocityKey
internal val MiniPlayerProgressKey=SemanticsPropertyKey<Float>("MiniPlayerProgress")
internal var SemanticsPropertyReceiver.miniPlayerProgress by MiniPlayerProgressKey

/** Only the outer petals move. The cached artwork and real playback progress stay independent. */
@Composable
internal fun MiniPlayerFlowerArtwork(song: Song,playing: Boolean,position: Long,duration: Long,
    modifier: Modifier=Modifier) {
    val motion=remember {PlaybackFlowerMotion()}
    val rotation=remember {mutableFloatStateOf(0f)}
    val velocity=remember {mutableFloatStateOf(0f)}
    val latestPlaying by rememberUpdatedState(playing)
    val progress=miniPlayerProgress(position,duration)
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    val accent=MaterialTheme.colorScheme.primary
    val petal=remember {Path().apply {
        // Broad shoulders overlap their neighbours slightly, forming a close scalloped
        // flower. The roots remain beneath the cover while each tip stretches.
        moveTo(21.4f,14f)
        cubicTo(17.8f,12.3f,17.9f,8.9f,21.7f,5.5f)
        cubicTo(27.2f,.8f,36.8f,.8f,42.3f,5.5f)
        cubicTo(46.1f,8.9f,46.2f,12.3f,42.6f,14f)
        cubicTo(37f,16.3f,27f,16.3f,21.4f,14f)
        close()
    }}
    val petalColors=remember {listOf(
        Color(0xFFD85D67),Color(0xFFE79A5D),Color(0xFFD7BC59),Color(0xFF86AA7A),
        Color(0xFF69A8AF),Color(0xFF798FC2),Color(0xFFAD86B5)
    )}
    val pixels=with(LocalDensity.current) {40.dp.roundToPx()}
    val image=rememberImage(song.artwork,pixels,song.artworkRevision)

    // This effect never restarts for seek, position ticks or track changes. No background
    // time is accumulated, and the settled/disabled state suspends instead of polling.
    LaunchedEffect(lifecycle) {
        val scale=coroutineContext[MotionDurationScale]
        snapshotFlow {scale?.scaleFactor ?: 1f}.collectLatest {durationScale ->
            if(durationScale <= 0f) {
                motion.freeze()
                velocity.floatValue=0f
                return@collectLatest
            }
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                var previousFrame=0L
                while(isActive) {
                    if(!latestPlaying && motion.atRest) {
                        previousFrame=0L
                        snapshotFlow {latestPlaying}.first {it}
                    }
                    withFrameNanos {frame ->
                        val delta=if(previousFrame == 0L) 0.0 else (frame-previousFrame)/1_000_000.0
                        previousFrame=frame
                        motion.advance(delta/durationScale,latestPlaying)
                        rotation.floatValue=motion.rotation.toFloat()
                        velocity.floatValue=motion.velocity.toFloat()
                    }
                }
            }
        }
    }

    Box(modifier.size(64.dp).testTag("mini-flower-artwork").clearAndSetSemantics {
        // Decorative state is exposed to tests without a second TalkBack announcement.
        miniPlayerFlowerRotation=rotation.floatValue
        miniPlayerFlowerVelocity=velocity.floatValue
        miniPlayerProgress=progress
    },contentAlignment=Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            withTransform({scale(size.width/64f,size.height/64f,pivot=Offset.Zero)}) {
                for(index in 0..6) rotate(rotation.floatValue+index*360f/7f,Offset(32f,32f)) {
                    val hop=miniPlayerPetalHop(rotation.floatValue.toDouble(),velocity.floatValue.toDouble(),index).toFloat()
                    // Stretch from the fixed root instead of translating the whole petal:
                    // the contact with the progress border cannot open during a hop.
                    withTransform({scale(1f,1f+hop/10f,pivot=Offset(32f,12f))}) {
                        drawPath(petal,petalColors[index])
                        withTransform({scale(.54f,.78f,pivot=Offset(32f,12f))}) {
                            drawPath(petal,Color.White.copy(alpha=.15f))
                        }
                    }
                }
            }
        }
        Box(Modifier.size(40.dp).clip(CircleShape).background(accent.copy(alpha=.08f)),
            contentAlignment=Alignment.Center) {
            if(image != null) Image(image,null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
            else Icon(Icons.Outlined.LocalFlorist,null,Modifier.size(22.dp),tint=accent)
        }
        Canvas(Modifier.fillMaxSize()) {
            withTransform({scale(size.width/64f,size.height/64f,pivot=Offset.Zero)}) {
                // Radius 20.8 minus half the 1.6 stroke is exactly the 20dp artwork
                // radius. Drawing last seals the petal roots and makes the ring its border.
                drawCircle(accent.copy(alpha=.12f),20.8f,Offset(32f,32f),style=Stroke(1.6f))
                if(progress > 0f) drawArc(accent,-90f,360f*progress,false,
                    Offset(11.2f,11.2f),Size(41.6f,41.6f),style=Stroke(1.6f,cap=StrokeCap.Round))
            }
        }
    }
}
