package com.xiaohonghua.music

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

/** A single, interruptible floral gesture shared by the player and all song lists. */
@Composable internal fun FavoriteButton(song: Song,favorite: Boolean,onAnimationFinished: (()->Unit)?=null,onFavorite: ()->Unit) {
    val progress=remember(song.id) {Animatable(1f)}
    var request by remember(song.id) {mutableIntStateOf(0)}
    var requestedAdding by remember(song.id) {mutableStateOf(false)}
    var adding by remember(song.id) {mutableStateOf(false)}
    var startScale by remember(song.id) {mutableFloatStateOf(1f)}
    var startRotation by remember(song.id) {mutableFloatStateOf(0f)}
    val latestFinished by rememberUpdatedState(onAnimationFinished)
    // If a lazy row leaves the viewport, no visible flourish remains to retain.
    DisposableEffect(song.id) {onDispose {latestFinished?.invoke()}}
    val source=remember {MutableInteractionSource()}
    val haptic=rememberActionHaptic()
    val outline=muted.copy(alpha=.6f)
    val color by animateColorAsState(if(favorite) accents[0] else Color.White,tween(160),label="favorite-color")
    // Only an actual click starts a flourish: opening a page or recycling a row stays still.
    // A new request cancels the previous tween; Animatable honors system duration scaling.
    LaunchedEffect(song.id,request) {
        if(request == 0) return@LaunchedEffect
        // Preserve the actual pose before replacing the clock and direction on a rapid tap.
        val scale=favoriteHeartScale(progress.value,startScale)
        val rotation=favoriteHeartRotation(progress.value,startRotation,adding)
        startScale=scale
        startRotation=rotation
        adding=requestedAdding
        progress.snapTo(0f)
        progress.animateTo(1f,tween(560,easing=LinearEasing))
        latestFinished?.invoke()
    }
    Box(Modifier.size(48.dp).testTag("favorite-${song.id}").semantics {selected=favorite}
        .clickable(interactionSource=source,indication=null,role=Role.Button) {
            requestedAdding=!favorite
            request++
            haptic()
            onFavorite()
        },contentAlignment=Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val p=((progress.value-.08f)/.92f).coerceIn(0f,1f)
            if(p <= 0f || p >= 1f) return@Canvas
            // Five small petals, never a circular backdrop. Adding scatters them gently;
            // removing folds them back into the outline before they dissolve.
            val visibility=sin(PI*p).toFloat()
            val radius=(if(adding) 12f+9f*p else 21f-9f*p).dp.toPx()
            val width=(1.4f+1.3f*visibility).dp.toPx()
            val height=(3f+2.3f*visibility).dp.toPx()
            repeat(5) {index ->
                rotate(index*72f+(if(adding) 12f*p else -12f*p),center) {
                    drawOval((if(index%2 == 0) accents[0] else Color(0xFFE2B96C)).copy(alpha=visibility*.8f),
                        topLeft=Offset(center.x-width/2f,center.y-radius-height/2f),size=Size(width,height))
                }
            }
        }
        Box(Modifier.graphicsLayer {
            val p=progress.value
            val scale=favoriteHeartScale(p,startScale)
            scaleX=scale;scaleY=scale
            rotationZ=favoriteHeartRotation(p,startRotation,adding)
        }) {
            Icon(Icons.Filled.Favorite,if(favorite) "取消喜欢：${song.title}" else "喜欢：${song.title}",Modifier.size(20.dp),tint=color)
            if(!favorite) Icon(Icons.Outlined.FavoriteBorder,null,Modifier.size(20.dp),tint=outline)
        }
    }
}

private fun favoriteEase(t: Float): Float {
    val v=t.coerceIn(0f,1f)
    return 1f-(1f-v)*(1f-v)*(1f-v)
}

private fun favoriteHeartScale(p: Float,start: Float): Float=when {
    p < .16f -> start+(.83f-start)*favoriteEase(p/.16f)
    p < .46f -> .83f+.35f*favoriteEase((p-.16f)/.3f)
    p < .76f -> 1.18f-.21f*favoriteEase((p-.46f)/.3f)
    else -> .97f+.03f*favoriteEase((p-.76f)/.24f)
}

private fun favoriteHeartRotation(p: Float,start: Float,adding: Boolean): Float =
    start*(1f-favoriteEase(p/.4f))+(if(adding) 10f else -10f)*sin(PI*p).toFloat()*(1f-p)
