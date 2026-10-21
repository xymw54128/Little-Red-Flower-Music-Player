package com.xiaohonghua.music

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.cos
import kotlin.math.sin

// These are the actual silhouettes from ic_flower / ic_header_playlists / ic_header_settings.
// Parse once; frame updates only change drawing, never the header's layout or its paths.
private const val headerFlowerOutline="M41.152,35.018 A13,13 0,0 1 66.848,35.018 Q67.013,36.089 68.082,35.915 A13,13 0,0 1 76.023,60.354 Q75.056,60.841 75.551,61.805 A13,13 0,0 1 54.763,76.908 Q54,76.139 53.237,76.908 A13,13 0,0 1 32.449,61.805 Q32.944,60.841 31.977,60.354 A13,13 0,0 1 39.918,35.915 Q40.987,36.089 41.152,35.018 Z"
private val headerFlowerPetals=listOf(
    "M54,54 L41.152,35.018 A13,13 0,0 1 66.848,35.018 Q67.013,36.089 68.082,35.915 Z",
    "M54,54 L68.082,35.915 A13,13 0,0 1 76.023,60.354 Q75.056,60.841 75.551,61.805 Z",
    "M54,54 L75.551,61.805 A13,13 0,0 1 54.763,76.908 Q54,76.139 53.237,76.908 Z",
    "M54,54 L53.237,76.908 A13,13 0,0 1 32.449,61.805 Q32.944,60.841 31.977,60.354 Z",
    "M54,54 L31.977,60.354 A13,13 0,0 1 39.918,35.915 Q40.987,36.089 41.152,35.018 Z"
)

@Composable
internal fun InteractiveHeaderFlower(page: String,modifier: Modifier=Modifier) {
    val start=remember(page) {mutableStateOf(HeaderFlowerFrame())}
    val elapsed=remember(page) {mutableLongStateOf(HeaderFlowerMotion.DURATION)}
    val request=remember(page) {mutableLongStateOf(0L)}
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    val haptic=rememberActionHaptic()
    val outline=remember {PathParser().parsePathString(headerFlowerOutline).toPath()}
    val petals=remember {headerFlowerPetals.map {PathParser().parsePathString(it).toPath()}}
    val measure=remember(outline) {PathMeasure().apply {setPath(outline,true)}}
    val segment=remember {Path()}
    val directions=remember {List(5) {index ->
        val angle=Math.toRadians(-90.0+index*72.0)
        Offset(cos(angle).toFloat(),sin(angle).toFloat())
    }}
    val restingPainter=painterResource(when(page) {
        "playlists" -> R.drawable.ic_header_playlists
        "settings" -> R.drawable.ic_header_settings
        else -> R.drawable.ic_flower
    })
    val red=Color(0xFFCE3C4C)
    val pink=Color(0xFFE87985)
    val gold=Color(0xFFF6D88C)

    LaunchedEffect(page,request.longValue,lifecycle) {
        if(request.longValue == 0L) return@LaunchedEffect
        val generation=request.longValue
        val motionScale=coroutineContext[MotionDurationScale]
        var playedMillis=0.0
        snapshotFlow {motionScale?.scaleFactor ?: 1f}.collectLatest {durationScale ->
            if(durationScale <= 0f) {elapsed.longValue=HeaderFlowerMotion.DURATION;return@collectLatest}
            // Preserve fractional progress across scale changes and exclude background time.
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                var previousFrame=0L
                while(elapsed.longValue < HeaderFlowerMotion.DURATION && generation == request.longValue) {
                    withFrameNanos {frame ->
                        if(generation == request.longValue) {
                            if(previousFrame != 0L) playedMillis+=(frame-previousFrame)/(1_000_000.0*durationScale)
                            previousFrame=frame
                            elapsed.longValue=playedMillis.toLong().coerceAtMost(HeaderFlowerMotion.DURATION)
                        }
                    }
                }
            }
        }
    }

    Box(modifier.size(48.dp).testTag("$page-header-flower")
        .semantics {contentDescription=when(page) {
            "playlists" -> "重绘歌单小红花"
            "settings" -> "绽放设置小红花"
            "player" -> "旋转小红花"
            else -> "旋转音乐库小红花"
        }}.clickable(interactionSource=remember {MutableInteractionSource()},indication=null,
            role=Role.Button,onClickLabel="播放小红花动画") {
            // Every tap restarts the two-second sequence from the current visible pose.
            start.value=HeaderFlowerMotion.sample(page,elapsed.longValue,start.value)
            elapsed.longValue=0L
            request.longValue++
            haptic()
        },contentAlignment=Alignment.Center) {
        Canvas(Modifier.size(36.dp)) {
            if(elapsed.longValue >= HeaderFlowerMotion.DURATION) with(restingPainter) {draw(size)}
            else {
            val pose=HeaderFlowerMotion.sample(page,elapsed.longValue,start.value)
            withTransform({scale(size.width/108f,size.height/108f,pivot=Offset.Zero)}) {
                when(page) {
                    "playlists" -> {
                        if(pose.outlineAlpha > 0f && pose.outline > 0f) {
                            val contour=if(pose.outline == 1f) outline else {
                                segment.reset()
                                measure.getSegment(0f,measure.length*pose.outline,segment,true)
                                segment
                            }
                            drawPath(contour,red.copy(alpha=pose.outlineAlpha),style=Stroke(4.5f,cap=StrokeCap.Round,join=StrokeJoin.Round))
                        }
                        drawCircle(gold.copy(alpha=pose.centerAlpha),7.5f*pose.centerScale,Offset(54f,54f))
                    }
                    "settings" -> {
                        if(pose.petals.all {it == 0f}) {
                            // Match the shipped vector exactly, without seams between resting sectors.
                            drawPath(outline,pink)
                            for(index in listOf(0,2,4)) drawPath(petals[index],red)
                        } else for(index in 0..4) {
                            val shift=directions[index]*pose.petals[index]
                            translate(shift.x,shift.y) {drawPath(petals[index],if(index%2 == 0) red else pink)}
                        }
                        drawCircle(gold,7.5f*pose.centerScale,Offset(54f,54f))
                    }
                    else -> rotate(pose.rotation,Offset(54f,54f)) {
                        drawPath(outline,red)
                        drawCircle(gold,7.5f,Offset(54f,54f))
                    }
                }
            }
            }
        }
    }
}
