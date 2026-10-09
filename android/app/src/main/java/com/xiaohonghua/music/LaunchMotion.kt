package com.xiaohonghua.music

/** One elapsed-time timeline, independent of the display's refresh rate. */
internal object LaunchMotion {
    const val DURATION=2640L
    private fun ease(time: Long,start: Long,duration: Long): Float {
        val t=((time-start).toDouble()/duration).coerceIn(0.0,1.0)
        return (t*t*t*(t*(t*6.0-15.0)+10.0)).toFloat()
    }
    fun petal(time: Long,index: Int)=ease(time,220L+index*150L,260L)
    fun caption(time: Long)=ease(time,220L,240L)*(1f-ease(time,1660L,320L))
    fun background(time: Long)=1f-ease(time,1480L,300L)
    fun flight(time: Long)=ease(time,1500L,840L)
    fun rotation(time: Long)=72f*ease(time,1120L,340L)+1368f*flight(time)+72f*ease(time,2340L,300L)
}
