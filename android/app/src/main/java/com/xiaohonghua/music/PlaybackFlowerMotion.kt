package com.xiaohonghua.music

/** A single continuous angle and speed. Retargeting never restarts the flower's pose. */
internal class PlaybackFlowerMotion(private val playingVelocity: Double=48.0) {
    var rotation=0.0
        private set
    var velocity=0.0
        private set
    private var target=0.0
    private var initialVelocity=0.0
    private var elapsed=0.0
    private var duration=0.0
    val atRest get()=velocity == 0.0 && target == 0.0

    fun advance(deltaMillis: Double,playing: Boolean) {
        val desired=if(playing) playingVelocity else 0.0
        if(desired != target) {
            initialVelocity=velocity
            target=desired
            elapsed=0.0
            duration=if(playing) 240.0 else 900.0
        }
        if(!deltaMillis.isFinite() || deltaMillis <= 0.0) return
        // Exclude suspended/stalled wall-clock time instead of jumping several rotations.
        val delta=deltaMillis.coerceAtMost(64.0)
        val step=minOf(delta,(duration-elapsed).coerceAtLeast(0.0))
        var distance=target*(delta-step)/1000.0
        if(step > 0.0) {
            val old=elapsed/duration
            elapsed+=step
            val next=elapsed/duration
            // Integrate smoothstep velocity exactly, preserving equal motion at 60/90/120 Hz.
            fun integral(t: Double)=t*t*t-.5*t*t*t*t
            distance+=(initialVelocity*step+(target-initialVelocity)*duration*(integral(next)-integral(old)))/1000.0
            velocity=if(elapsed >= duration) target else initialVelocity+(target-initialVelocity)*next*next*(3.0-2.0*next)
        } else velocity=target
        rotation=(rotation+distance)%360.0
    }

    /** Animation disabling freezes the current angle; re-enabling accelerates from rest. */
    fun freeze() {
        velocity=0.0
        target=0.0
        initialVelocity=0.0
        elapsed=0.0
        duration=0.0
    }
}
