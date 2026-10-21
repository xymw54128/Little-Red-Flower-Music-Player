package com.xiaohonghua.music

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
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

internal val PlaybackFlowerRotationKey=SemanticsPropertyKey<Float>("PlaybackFlowerRotation")
internal var SemanticsPropertyReceiver.playbackFlowerRotation by PlaybackFlowerRotationKey
internal val PlaybackFlowerVelocityKey=SemanticsPropertyKey<Float>("PlaybackFlowerVelocity")
internal var SemanticsPropertyReceiver.playbackFlowerVelocity by PlaybackFlowerVelocityKey

/** Seven gently curved rainbow petals. Decorative only: no touch target or background. */
@Composable
internal fun PlaybackFlower(playing: Boolean,visible: Boolean,modifier: Modifier=Modifier) {
    val motion=remember {PlaybackFlowerMotion(60.0)}
    val rotation=remember {mutableFloatStateOf(0f)}
    val velocity=remember {mutableFloatStateOf(0f)}
    val latestPlaying by rememberUpdatedState(playing)
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    val petal=remember {Path().apply {
        moveTo(50f,48f)
        cubicTo(38f,43f,30f,22f,39f,12f)
        cubicTo(49f,1f,65f,14f,64f,27f)
        cubicTo(63f,39f,57f,43f,50f,48f)
        close()
    }}
    val petalColors=remember {listOf(
        Color(0xFFE36376),Color(0xFFEDA46C),Color(0xFFE9CA69),Color(0xFF81B88A),
        Color(0xFF6FBDBB),Color(0xFF779ED2),Color(0xFFAB88C4)
    )}

    LaunchedEffect(visible,lifecycle) {
        if(!visible) return@LaunchedEffect
        val scale=coroutineContext[MotionDurationScale]
        // Compose exposes the live system preference. When disabled, suspend on its changes
        // instead of requesting frames or repeatedly polling while the user is listening.
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

    Canvas(modifier.size(32.dp).testTag("player-playback-flower").clearAndSetSemantics {
        // Test-only observable properties; do not announce a second playback state to TalkBack.
        playbackFlowerRotation=rotation.floatValue
        playbackFlowerVelocity=velocity.floatValue
    }) {
        withTransform({scale(size.width/100f,size.height/100f,pivot=Offset.Zero)}) {
            rotate(rotation.floatValue,Offset(50f,50f)) {
                for(index in 0..6) rotate(index*360f/7f,Offset(50f,50f)) {
                    drawPath(petal,petalColors[index])
                    // A narrow, soft inner fold gives each petal shape without thin outlines.
                    withTransform({scale(.57f,.8f,pivot=Offset(50f,48f))}) {
                        drawPath(petal,Color.White.copy(alpha=.14f))
                    }
                }
                drawCircle(Color(0xFFCA9B4D),9.5f,Offset(50f,50f))
                drawCircle(Color(0xFFF2D482),7.8f,Offset(50f,50f))
                drawCircle(Color(0xFFFFE6A7),2.6f,Offset(48f,48f))
            }
        }
    }
}
