package com.xiaohonghua.music

import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.sin

/** Decorative rhythm from the same continuous rotation clock; not audio beat detection. */
internal fun miniPlayerPetalHop(rotation: Double,velocity: Double,index: Int): Double {
    if(!rotation.isFinite() || !velocity.isFinite() || index !in 0..6) return 0.0
    // Fourteen handovers per rotation: 0..6 twice. A single petal owns each interval;
    // sin squared gives both ends zero speed, including the 360-degree wrap.
    val phase=((rotation%360.0+360.0)%360.0)*14.0/360.0
    val step=floor(phase).toInt()
    if(step%7 != index) return 0.0
    val amplitude=4.2*(velocity/48.0).coerceIn(0.0,1.0)
    val pulse=sin(PI*(phase-step))
    return amplitude*pulse*pulse
}

internal fun miniPlayerProgress(position: Long,duration: Long): Float =
    if(duration <= 0L) 0f else (position.toDouble()/duration.toDouble()).coerceIn(0.0,1.0).toFloat()
