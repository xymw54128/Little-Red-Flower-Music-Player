package com.xiaohonghua.music

import kotlin.math.floor

private val restingHeaderPetals=listOf(0f,0f,0f,0f,0f)

/** A drawable pose, also used as the starting pose when another tap interrupts an animation. */
internal data class HeaderFlowerFrame(
    val rotation: Float=0f,
    val outline: Float=1f,
    val outlineAlpha: Float=1f,
    val centerAlpha: Float=1f,
    val centerScale: Float=1f,
    val petals: List<Float> = restingHeaderPetals
)

/** One clock for all the parts of a flower; no independent tweens or completion callbacks. */
internal object HeaderFlowerMotion {
    const val DURATION=2000L

    private fun ease(time: Long,start: Long,duration: Long): Float {
        val t=((time-start).toDouble()/duration).coerceIn(0.0,1.0)
        return (t*t*t*(t*(t*6.0-15.0)+10.0)).toFloat()
    }

    private fun mix(from: Float,to: Float,fraction: Float)=from+(to-from)*fraction

    // The integral of a short acceleration followed by a long, gentle braking phase.
    // Both end velocities are zero; the last frame needs no corrective snap.
    private fun spin(time: Long): Float {
        val t=(time.toDouble()/DURATION).coerceIn(0.0,1.0)
        val acceleration=.08
        val area=acceleration/2.0+(1.0-acceleration)/4.0
        if(t <= acceleration) return (t*t/(2.0*acceleration*area)).toFloat()
        val remaining=1.0-(t-acceleration)/(1.0-acceleration)
        return ((acceleration/2.0+(1.0-acceleration)/4.0*(1.0-remaining*remaining*remaining*remaining))/area).toFloat()
    }

    fun sample(page: String,time: Long,start: HeaderFlowerFrame=HeaderFlowerFrame()): HeaderFlowerFrame {
        val elapsed=time.coerceIn(0L,DURATION)
        return when(page) {
            "playlists" -> {
                // Fade the actual interrupted contour first, rather than briefly showing a full flower.
                val clearing=1f-ease(elapsed,0L,200L)
                val drawing=elapsed >= 320L
                HeaderFlowerFrame(
                    outline=if(drawing) ease(elapsed,320L,1680L) else start.outline,
                    outlineAlpha=if(drawing) 1f else start.outlineAlpha*clearing,
                    centerAlpha=if(elapsed < 200L) start.centerAlpha*clearing else ease(elapsed,200L,220L),
                    centerScale=if(elapsed < 200L) mix(start.centerScale,.7f,1f-clearing) else mix(.7f,1f,ease(elapsed,200L,300L))
                )
            }
            "settings" -> HeaderFlowerFrame(
                centerScale=mix(start.centerScale,1f,ease(elapsed,0L,260L)),
                petals=List(5) {index ->
                    val separated=mix(start.petals[index],32f,ease(elapsed,0L,450L))
                    separated*(1f-ease(elapsed,660L+index*210L,500L))
                }
            )
            else -> {
                // Land in the same orientation as the app icon, including after a mid-spin restart.
                val destination=(floor(start.rotation/360f)+4f)*360f
                HeaderFlowerFrame(rotation=mix(start.rotation,destination,spin(elapsed)))
            }
        }
    }
}
