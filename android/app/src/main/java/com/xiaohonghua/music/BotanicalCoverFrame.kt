package com.xiaohonghua.music

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.cos

/** The selected C artwork. SVG contours are parsed once, never during animation frames. */
private class BotanicalPaths {
    private fun path(data: String): Path=PathParser().parsePathString(data).toPath()
    val leaf=path("M0 0C-4-7-1-16 5-21C13-12 10-3 0 0Z")
    val vein=path("M1-2Q5-10 5-18")
    val roseOuter=path("M0-16C4-23 14-19 15-12C24-11 24 0 17 4C20 12 12 20 5 15C-1 23-12 18-12 10C-22 10-24 0-16-5C-18-14-8-22 0-16Z")
    val roseInner=path("M0-3C-6-12-13-7-8-1C-18 3-10 11-4 5C-3 16 9 12 6 5C18 5 15-8 6-4C10-15-1-17 0-3Z")
    val roseDetail=path("M-7-3Q-4-7 0-5M6-4Q10-1 8 3M-5 6Q0 10 4 6")
    // Complete closed petals prevent the former center-to-edge closure from cutting a lobe.
    val daisy=path("M0.00 -18.00C3.20 -18.00 5.80 -13.52 5.80 -8.00C5.80 -2.48 3.20 2.00 0.00 2.00C-3.20 2.00 -5.80 -2.48 -5.80 -8.00C-5.80 -13.52 -3.20 -18.00 0.00 -18.00ZM15.59 -9.00C17.19 -6.23 14.61 -1.74 9.83 1.02C5.05 3.78 -0.13 3.77 -1.73 1.00C-3.33 -1.77 -0.75 -6.26 4.03 -9.02C8.81 -11.78 13.99 -11.77 15.59 -9.00ZM15.59 9.00C13.99 11.77 8.81 11.78 4.03 9.02C-0.75 6.26 -3.33 1.77 -1.73 -1.00C-0.13 -3.77 5.05 -3.78 9.83 -1.02C14.61 1.74 17.19 6.23 15.59 9.00ZM0.00 18.00C-3.20 18.00 -5.80 13.52 -5.80 8.00C-5.80 2.48 -3.20 -2.00 -0.00 -2.00C3.20 -2.00 5.80 2.48 5.80 8.00C5.80 13.52 3.20 18.00 0.00 18.00ZM-15.59 9.00C-17.19 6.23 -14.61 1.74 -9.83 -1.02C-5.05 -3.78 0.13 -3.77 1.73 -1.00C3.33 1.77 0.75 6.26 -4.03 9.02C-8.81 11.78 -13.99 11.77 -15.59 9.00ZM-15.59 -9.00C-13.99 -11.77 -8.81 -11.78 -4.03 -9.02C0.75 -6.26 3.33 -1.77 1.73 1.00C0.13 3.77 -5.05 3.78 -9.83 1.02C-14.61 -1.74 -17.19 -6.23 -15.59 -9.00Z")
    val wild=path("M0.00 -17.50C3.59 -17.50 6.50 -13.25 6.50 -8.00C6.50 -2.75 3.59 1.50 0.00 1.50C-3.59 1.50 -6.50 -2.75 -6.50 -8.00C-6.50 -13.25 -3.59 -17.50 0.00 -17.50ZM15.16 -8.75C16.95 -5.64 14.72 -0.99 10.18 1.63C5.63 4.25 0.50 3.86 -1.30 0.75C-3.09 -2.36 -0.87 -7.01 3.68 -9.63C8.22 -12.25 13.36 -11.86 15.16 -8.75ZM15.16 8.75C13.36 11.86 8.22 12.25 3.68 9.63C-0.87 7.01 -3.09 2.36 -1.30 -0.75C0.50 -3.86 5.63 -4.25 10.18 -1.63C14.72 0.99 16.95 5.64 15.16 8.75ZM0.00 17.50C-3.59 17.50 -6.50 13.25 -6.50 8.00C-6.50 2.75 -3.59 -1.50 -0.00 -1.50C3.59 -1.50 6.50 2.75 6.50 8.00C6.50 13.25 3.59 17.50 0.00 17.50ZM-15.16 8.75C-16.95 5.64 -14.72 0.99 -10.18 -1.63C-5.63 -4.25 -0.50 -3.86 1.30 -0.75C3.09 2.36 0.87 7.01 -3.68 9.63C-8.22 12.25 -13.36 11.86 -15.16 8.75ZM-15.16 -8.75C-13.36 -11.86 -8.22 -12.25 -3.68 -9.63C0.87 -7.01 3.09 -2.36 1.30 0.75C-0.50 3.86 -5.63 4.25 -10.18 1.63C-14.72 -0.99 -16.95 -5.64 -15.16 -8.75Z")
    val wildDetail=path("M0.00 -7.00L0.00 -12.00M6.06 -3.50L10.39 -6.00M6.06 3.50L10.39 6.00M0.00 7.00L0.00 12.00M-6.06 3.50L-10.39 6.00M-6.06 -3.50L-10.39 -6.00")
    val lowerBranch=path("M13 316C22 322 31 335 51 344C67 353 91 349 113 344M25 332Q12 304 6 290M35 338Q33 315 24 303M55 345Q57 326 68 316")
    val lowerTwigs=path("M41 340Q36 353 25 357M77 348Q88 332 85 323")
    val upperBranch=path("M270 13C297 8 330 12 341 30C350 44 348 67 345 85")
    val upperTwigs=path("M317 14Q323 1 333 4M344 50Q358 45 356 31")
    val titleBranch=path("M34 29C25 29 17 22 12 10M20 25Q23 22 24 16")
    val titleAccentBranch=path("M15 20Q9 21 8 23")
    val titleLeaf=path("M0 0C-4-1-7-5-7-9C-2-9 2-5 0 0Z")
    val titleBloom=path("M41.152,35.018 A13,13 0,0 1 66.848,35.018 Q67.013,36.089 68.082,35.915 A13,13 0,0 1 76.023,60.354 Q75.056,60.841 75.551,61.805 A13,13 0,0 1 54.763,76.908 Q54,76.139 53.237,76.908 A13,13 0,0 1 32.449,61.805 Q32.944,60.841 31.977,60.354 A13,13 0,0 1 39.918,35.915 Q40.987,36.089 41.152,35.018 Z")
}

private val botanicalLeaf=Color(0xFF819B79)
private val botanicalLightLeaf=Color(0xFFAFC1A2)
private val botanicalGold=Color(0xFFD9B65A)
private val botanicalDetail=Color(0xFFFFF9EC).copy(alpha=.5f)
private val botanicalBranch=Color(0xFF82957B)
private val botanicalTwig=Color(0xFFA1AF93)
private val botanicalStroke=Stroke(1f,cap=StrokeCap.Round,join=StrokeJoin.Round)
private val botanicalFineStroke=Stroke(.65f,cap=StrokeCap.Round,join=StrokeJoin.Round)
private val botanicalDetailStroke=Stroke(.5f,cap=StrokeCap.Round,join=StrokeJoin.Round)

private inline fun DrawScope.botanicalAt(x: Float,y: Float,rotation: Float,scale: Float,draw: DrawScope.()->Unit) {
    withTransform({translate(x,y);rotate(rotation,pivot=Offset.Zero);scale(scale,scale,pivot=Offset.Zero)}) {draw()}
}

private fun DrawScope.botanicalLeaf(paths: BotanicalPaths,x: Float,y: Float,rotation: Float,scale: Float,light: Boolean=false) {
    botanicalAt(x,y,rotation,scale) {
        drawPath(paths.leaf,if(light) botanicalLightLeaf else botanicalLeaf)
        drawPath(paths.vein,Color(0xFFF3F5EB).copy(alpha=.65f),style=botanicalDetailStroke)
    }
}

private fun DrawScope.botanicalWild(paths: BotanicalPaths,x: Float,y: Float,rotation: Float,scale: Float,color: Color) {
    botanicalAt(x,y,rotation,scale) {
        drawPath(paths.wild,color)
        drawPath(paths.wildDetail,botanicalDetail,style=botanicalDetailStroke)
        drawCircle(botanicalGold,3.2f,Offset.Zero)
    }
}

private fun DrawScope.botanicalDaisy(paths: BotanicalPaths,x: Float,y: Float,rotation: Float,scale: Float) {
    botanicalAt(x,y,rotation,scale) {
        drawPath(paths.daisy,Color(0xFFFFF9E9))
        drawPath(paths.daisy,Color(0xFFC8BDA1),style=Stroke(.35f,cap=StrokeCap.Round,join=StrokeJoin.Round))
        drawCircle(botanicalGold,3.7f,Offset.Zero)
        drawCircle(Color(0xFFF8E3A0),.75f,Offset(-1f,-1f))
        drawCircle(Color(0xFFF8E3A0),.75f,Offset(1.4f,.8f))
    }
}

private fun DrawScope.botanicalRose(paths: BotanicalPaths,x: Float,y: Float,rotation: Float,scale: Float,accent: Color) {
    botanicalAt(x,y,rotation,scale) {
        drawPath(paths.roseOuter,lerp(Color.White,accent,.6f))
        drawPath(paths.roseInner,accent)
        drawPath(paths.roseDetail,botanicalDetail,style=botanicalDetailStroke)
        drawCircle(botanicalGold,3.1f,Offset.Zero)
        drawCircle(Color(0xFFFFF4CE),.8f,Offset(-.9f,-.8f))
    }
}

/**
 * Attach over an unclipped square cover. The photo occupies 20..340 of the 360-unit design,
 * so decoration extends by 6.25% outside its bounds without changing image size or hit targets.
 */
@Composable
internal fun BotanicalCoverFrame(playing: Boolean,visible: Boolean,modifier: Modifier=Modifier) {
    val paths=remember {BotanicalPaths()}
    val accent=MaterialTheme.colorScheme.primary
    val phase=remember {mutableFloatStateOf(0f)}
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(playing,visible,lifecycle) {
        if(!playing || !visible) return@LaunchedEffect
        val motionScale=coroutineContext[MotionDurationScale]
        snapshotFlow {motionScale?.scaleFactor ?: 1f}.collectLatest {durationScale ->
            if(durationScale <= 0f) return@collectLatest
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                var previousFrame=0L
                while(isActive) withFrameNanos {frame ->
                    if(previousFrame != 0L) {
                        val seconds=((frame-previousFrame)/1_000_000_000f).coerceAtMost(.064f)
                        phase.floatValue=(phase.floatValue+seconds/durationScale)%36f
                    }
                    previousFrame=frame
                }
            }
        }
    }
    Canvas(modifier.testTag("player-botanical-frame").clearAndSetSemantics {}) {
        withTransform({scale(size.width/320f,size.height/320f,pivot=Offset.Zero);translate(-20f,-20f)}) {
            val elapsed=phase.floatValue
            rotate((-cos(elapsed*2*PI/6)*.65).toFloat(),Offset(50f,348f)) {
                drawPath(paths.lowerBranch,botanicalBranch,style=botanicalStroke)
                drawPath(paths.lowerTwigs,botanicalTwig,style=botanicalFineStroke)
                botanicalLeaf(paths,18f,317f,-67f,.92f,true)
                botanicalLeaf(paths,30f,334f,-127f,.9f)
                botanicalLeaf(paths,54f,345f,78f,.85f,true)
                botanicalLeaf(paths,87f,348f,64f,.6f)
                botanicalWild(paths,19f,299f,-18f,.82f,Color(0xFF789DBB))
                botanicalWild(paths,33f,325f,14f,.7f,Color(0xFFA68AB7))
                botanicalDaisy(paths,59f,343f,13f,.74f)
                botanicalWild(paths,77f,326f,-15f,.5f,Color(0xFFD6B569))
                botanicalRose(paths,23f,342f,-12f,.44f,accent)
            }
            rotate((-cos((elapsed+2.5)*2*PI/7.2)*.65).toFloat(),Offset(333f,20f)) {
                drawPath(paths.upperBranch,botanicalBranch,style=botanicalStroke)
                drawPath(paths.upperTwigs,botanicalTwig,style=botanicalFineStroke)
                botanicalLeaf(paths,297f,12f,-72f,.76f,true)
                botanicalLeaf(paths,334f,24f,42f,.85f)
                botanicalLeaf(paths,347f,65f,28f,.72f,true)
                botanicalDaisy(paths,330f,23f,-13f,.88f)
                botanicalWild(paths,347f,49f,17f,.52f,Color(0xFF9C8BB3))
            }
        }
    }
}

/** Restrained diagonal sprigs: the left flowers face upward and the right trio downward.
 * Point-rotated branches and the logo's rounded silhouette keep the title decoration cohesive. */
@Composable
internal fun BotanicalTitleSprig(mirrored: Boolean,modifier: Modifier=Modifier) {
    val paths=remember {BotanicalPaths()}
    Canvas(modifier.clearAndSetSemantics {}) {
        withTransform({
            scale(size.width/36f,size.height/36f,pivot=Offset.Zero)
            if(mirrored) {translate(36f,36f);scale(-1f,-1f,pivot=Offset.Zero)}
        }) {
            val titleGreen=Color(0xFF4C9A63)
            val titleStroke=Stroke(1.15f,cap=StrokeCap.Round,join=StrokeJoin.Round)
            drawPath(paths.titleBranch,titleGreen,style=titleStroke)
            if(mirrored) drawPath(paths.titleAccentBranch,titleGreen,style=titleStroke)
            botanicalAt(20f,25f,-20f,.75f) {
                drawPath(paths.titleLeaf,Color(0xFF55AD70))
            }
            botanicalAt(28f,28f,90f,.6f) {
                drawPath(paths.titleLeaf,Color(0xFF7DBD68))
            }
            botanicalAt(12f,10f,if(mirrored) -10f else -12f,if(mirrored) .215f else .225f) {
                withTransform({translate(-54f,-54f)}) {
                    drawPath(paths.titleBloom,if(mirrored) Color(0xFF4F9DE8) else Color(0xFFCE3C4C))
                    drawCircle(Color(0xFFF6D88C),7.5f,Offset(54f,54f))
                }
            }
            botanicalAt(24f,16f,if(mirrored) 12f else 14f,.18f) {
                withTransform({translate(-54f,-54f)}) {
                    drawPath(paths.titleBloom,if(mirrored) Color(0xFFF0B83D) else Color(0xFFED798C))
                    drawCircle(Color(0xFFF6D88C),7.5f,Offset(54f,54f))
                }
            }
            if(mirrored) botanicalAt(8f,23f,5f,.155f) {
                withTransform({translate(-54f,-54f)}) {
                    drawPath(paths.titleBloom,Color(0xFF9D79D7))
                    drawCircle(Color(0xFFF6D88C),7.5f,Offset(54f,54f))
                }
            }
        }
    }
}
