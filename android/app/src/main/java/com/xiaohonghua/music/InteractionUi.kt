package com.xiaohonghua.music

import android.view.HapticFeedbackConstants
import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

// Share the click interaction with the ripple so scrolling never triggers a tap effect.
internal fun Modifier.pressScale(source: MutableInteractionSource,pressedScale: Float=.97f): Modifier = composed {
    val pressed by source.collectIsPressedAsState()
    val scale=animateFloatAsState(if(pressed) pressedScale else 1f,
        spring(dampingRatio=.78f,stiffness=650f),label="press-scale")
    graphicsLayer {scaleX=scale.value;scaleY=scale.value}
}

@Composable internal fun rememberActionHaptic(): ()->Unit {
    val view=LocalView.current
    // Native feedback honors the user's touch-feedback setting; no vibrator permission.
    return remember(view) {{view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);Unit}}
}

@Composable internal fun ExpressiveIconButton(onClick: ()->Unit,modifier: Modifier=Modifier,
    enabled: Boolean=true,tactile: Boolean=false,content: @Composable ()->Unit) {
    val source=remember {MutableInteractionSource()}
    val haptic=rememberActionHaptic()
    IconButton(onClick={if(tactile) haptic();onClick()},modifier=modifier.pressScale(source,.94f),
        enabled=enabled,interactionSource=source,content=content)
}

internal data class ActionFeedback(override val message: String,override val actionLabel: String?=null,
    val completed: Boolean=false,override val duration: SnackbarDuration=SnackbarDuration.Short): SnackbarVisuals {
    override val withDismissAction: Boolean=false
}

/** Keep the native sheet mounted until its hide animation finishes. A disposed sheet cancels the job. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun rememberSheetDismiss(state: SheetState): ((()->Unit)->Unit) {
    val scope=rememberCoroutineScope()
    var closing by remember(state) {mutableStateOf<Job?>(null)}
    return remember(state,scope) {{onHidden ->
        if(closing?.isActive != true) closing=scope.launch {
            state.hide()
            if(!state.isVisible) onHidden()
        }
    }}
}

/** SnackbarHostState still owns queuing/undo; this host gives appearance and dismissal one clock. */
@Composable internal fun ActionSnackbarHost(host: SnackbarHostState,modifier: Modifier=Modifier) {
    // Modal sheets own another window; show the completion only after that window releases focus.
    val focused=LocalWindowInfo.current.isWindowFocused
    val data=if(focused) host.currentSnackbarData else null
    val accessibility=LocalAccessibilityManager.current
    LaunchedEffect(data,accessibility) {
        if(data == null) return@LaunchedEffect
        val duration=when(data.visuals.duration) {
            SnackbarDuration.Short -> 4000L
            SnackbarDuration.Long -> 10000L
            SnackbarDuration.Indefinite -> Long.MAX_VALUE
        }
        val timeout=accessibility?.calculateRecommendedTimeoutMillis(duration,
            containsIcons=true,containsText=true,containsControls=data.visuals.actionLabel != null) ?: duration
        delay(timeout)
        data.dismiss()
    }
    AnimatedContent(data,modifier.fillMaxWidth(),contentAlignment=Alignment.BottomCenter,
        transitionSpec={
            (fadeIn(tween(260))+slideInVertically(tween(320,easing=FastOutSlowInEasing)) {it/4})
                .togetherWith(fadeOut(tween(200))+slideOutVertically(tween(200,easing=FastOutSlowInEasing)) {it/6})
                .using(SizeTransform(clip=false,sizeAnimationSpec={_,_ -> tween(240,easing=FastOutSlowInEasing)}))
        },label="action-feedback-presence") {shown ->
        if(shown != null) ActionSnackbar(shown)
    }
}

internal val CompletionCircleProgressKey=SemanticsPropertyKey<Float>("CompletionCircleProgress")
internal val CompletionTickProgressKey=SemanticsPropertyKey<Float>("CompletionTickProgress")

/** Draw the ring first, then the check, with no path parsing or allocations per frame. */
@Composable private fun CompletionMark(data: SnackbarData,color: Color) {
    val progress=remember(data) {Animatable(0f)}
    val tick=remember {Path().apply {moveTo(6.8f,12f);lineTo(10.3f,15.5f);lineTo(17.5f,8.5f)}}
    val measure=remember(tick) {PathMeasure().apply {setPath(tick,false)}}
    val visibleTick=remember {Path()}
    LaunchedEffect(data) {progress.animateTo(1f,tween(560,easing=LinearEasing))}
    Canvas(Modifier.size(24.dp).testTag("action-complete-mark").clearAndSetSemantics {
        this[CompletionCircleProgressKey]=(progress.value/.6f).coerceIn(0f,1f)
        this[CompletionTickProgressKey]=((progress.value-.6f)/.4f).coerceIn(0f,1f)
    }) {
        withTransform({scale(size.width/24f,size.height/24f,pivot=Offset.Zero)}) {
            val circle=(progress.value/.6f).coerceIn(0f,1f)
            val check=((progress.value-.6f)/.4f).coerceIn(0f,1f)
            if(circle > 0f) drawArc(color,-90f,360f*circle,false,Offset(2f,2f),Size(20f,20f),
                style=Stroke(1.8f,cap=StrokeCap.Round))
            if(check > 0f) {
                visibleTick.reset()
                measure.getSegment(0f,measure.length*check,visibleTick,true)
                drawPath(visibleTick,color,style=Stroke(1.8f,cap=StrokeCap.Round,join=StrokeJoin.Round))
            }
        }
    }
}

@Composable internal fun ActionSnackbar(data: SnackbarData) {
    val complete=(data.visuals as? ActionFeedback)?.completed == true
    val flowerRed=Color(0xFFCE3C4C)
    val shape=RoundedCornerShape(20.dp)
    Snackbar(modifier=Modifier.padding(horizontal=16.dp,vertical=8.dp).testTag("action-snackbar")
        .border(.6.dp,Color(0xFFE1E7DF),shape).semantics {
            liveRegion=LiveRegionMode.Polite
            dismiss {data.dismiss();true}
        },shape=shape,containerColor=Color.White,contentColor=Color(0xFF272B29),actionContentColor=flowerRed,
        action=data.visuals.actionLabel?.let {label -> {TextButton(onClick=data::performAction) {Text(label,color=flowerRed)}}}) {
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            if(complete) CompletionMark(data,flowerRed)
            else Icon(Icons.Outlined.Info,null,Modifier.size(24.dp),tint=flowerRed)
            Text(data.visuals.message,Modifier.weight(1f),fontSize=14.sp)
            Box(Modifier.size(30.dp)) {
                Canvas(Modifier.matchParentSize()) {
                    val unit=size.width/30f
                    withTransform({scale(unit,unit,pivot=Offset.Zero)}) {
                        drawLine(Color(0xFF6E9D73),Offset(4f,27f),Offset(17f,12f),1.1f,StrokeCap.Round)
                        drawOval(Color(0xFF86B985),Offset(3f,17f),Size(9f,5f))
                        drawOval(Color(0xFFA3C58D),Offset(8f,18f),Size(9f,4f))
                    }
                }
                Icon(painterResource(R.drawable.ic_flower),null,Modifier.size(30.dp).align(Alignment.TopEnd),tint=Color.Unspecified)
            }
        }
    }
}
