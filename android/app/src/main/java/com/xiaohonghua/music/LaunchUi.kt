package com.xiaohonghua.music

import android.provider.Settings as SystemSettings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle

@Composable
internal fun LaunchScreen(destination: Offset?,onComplete:()->Unit) {
    val launchBackground=pageColor
    val elapsed=remember {mutableLongStateOf(0L)}
    var viewport by remember {mutableStateOf(IntSize.Zero)}
    val density=LocalDensity.current
    val flowerPx=with(density) {288.dp.toPx()}
    val targetScale=36f/288f
    val painter=painterResource(R.drawable.ic_flower)
    val wedges=remember(flowerPx) {List(5) {index -> Path().apply {
        val center=flowerPx/2f
        val radius=flowerPx*.72f
        moveTo(center,center)
        arcTo(Rect(center-radius,center-radius,center+radius,center+radius),-126f+index*72f,72f,false)
        close()
    }}}
    val resolver=LocalFontFamilyResolver.current
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    val complete by rememberUpdatedState(onComplete)
    val context=LocalContext.current
    val durationScale=remember(context) {runCatching {
        SystemSettings.Global.getFloat(context.contentResolver,SystemSettings.Global.ANIMATOR_DURATION_SCALE,1f).coerceAtLeast(0f)
    }.getOrDefault(1f)}
    LaunchedEffect(destination != null,viewport != IntSize.Zero) {
        if(destination == null || viewport == IntSize.Zero) return@LaunchedEffect
        if(durationScale == 0f) {complete();return@LaunchedEffect}
        // Preload off the animation path so the first caption never swaps typefaces mid-flight.
        runCatching {resolver.preload(lyricFont("kai"))}
        var playedNanos=0L
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            var previousFrame=0L
            while(elapsed.longValue < LaunchMotion.DURATION) withFrameNanos {frame ->
                if(previousFrame != 0L) playedNanos+=frame-previousFrame
                previousFrame=frame
                elapsed.longValue=(playedNanos/(1_000_000.0*durationScale)).toLong().coerceAtMost(LaunchMotion.DURATION)
            }
            complete()
        }
    }
    Box(Modifier.testTag("launch-animation").fillMaxSize().onSizeChanged {viewport=it}.pointerInput(Unit) {
        awaitPointerEventScope {while(true) awaitPointerEvent().changes.forEach {it.consume()}}
    }) {
        Canvas(Modifier.fillMaxSize().graphicsLayer {alpha=LaunchMotion.background(elapsed.longValue)}) {drawRect(launchBackground)}
        Canvas(Modifier.size(288.dp).align(Alignment.Center).graphicsLayer {
            val progress=LaunchMotion.flight(elapsed.longValue)
            val start=Offset(viewport.width/2f,viewport.height/2f)
            val end=destination ?: start
            val control1=start+Offset(viewport.width*.23f,-viewport.height*.18f)
            val control2=end+Offset(viewport.width*.20f,viewport.height*.10f)
            val remaining=1f-progress
            val position=start*(remaining*remaining*remaining)+control1*(3f*remaining*remaining*progress)+
                control2*(3f*remaining*progress*progress)+end*(progress*progress*progress)
            translationX=position.x-start.x;translationY=position.y-start.y
            scaleX=1f+(targetScale-1f)*progress;scaleY=scaleX
            rotationZ=LaunchMotion.rotation(elapsed.longValue)
        }) {
            val time=elapsed.longValue
            if(time >= 1080L) with(painter) {draw(Size(flowerPx,flowerPx))}
            else {
                for(index in 0..4) {
                    val growth=LaunchMotion.petal(time,index)
                    if(growth > 0f) scale(growth,growth,center) {clipPath(wedges[index]) {
                        with(painter) {draw(Size(flowerPx,flowerPx))}
                    }}
                }
                drawCircle(Color(0xFFF6D88C),flowerPx*7.5f/108f)
            }
        }
        Text("小红花 · 音乐播放器",modifier=Modifier.align(Alignment.Center).offset(y=124.dp).fillMaxWidth().padding(horizontal=20.dp)
            .graphicsLayer {alpha=LaunchMotion.caption(elapsed.longValue)},
            color=Color(0xFFCE3C4C),fontFamily=lyricFont("kai"),fontStyle=FontStyle.Italic,fontWeight=FontWeight.Medium,
            fontSize=24.sp,letterSpacing=1.2.sp,textAlign=TextAlign.Center)
    }
}
