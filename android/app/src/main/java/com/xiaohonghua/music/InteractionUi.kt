package com.xiaohonghua.music

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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

@Composable internal fun ActionSnackbar(data: SnackbarData) {
    val complete=(data.visuals as? ActionFeedback)?.completed == true
    Snackbar(modifier=Modifier.padding(horizontal=16.dp,vertical=8.dp),shape=RoundedCornerShape(20.dp),
        containerColor=ink,contentColor=Color.White,actionContentColor=Color(0xFFF6D88C),
        action=data.visuals.actionLabel?.let {label -> {TextButton(onClick=data::performAction) {Text(label,color=Color(0xFFF6D88C))}}}) {
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            Icon(if(complete) Icons.Outlined.CheckCircle else Icons.Outlined.Info,null,
                Modifier.size(20.dp),tint=if(complete) Color(0xFFF6D88C) else Color.White.copy(alpha=.8f))
            Text(data.visuals.message,fontSize=14.sp)
        }
    }
}
