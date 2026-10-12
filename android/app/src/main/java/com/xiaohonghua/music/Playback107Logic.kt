package com.xiaohonghua.music

const val MANUAL_QUEUE_SOURCE = "manual"
internal enum class QueuePlacement { NEXT, LAST }

/** Keeps the audible song in place; moving an existing entry never duplicates it. */
internal fun queueIncomingOrder(queue: List<String>,currentId: String?,incomingId: String,placement: QueuePlacement): List<String> {
    if(incomingId == currentId && incomingId in queue) return queue
    val result=queue.filterNot {it == incomingId}.toMutableList()
    val current=result.indexOf(currentId)
    val index=if(placement == QueuePlacement.NEXT && current >= 0) current+1 else result.size
    result.add(index,incomingId)
    return result
}

data class SleepTimerState(val deadlineElapsedRealtime: Long = 0) {
    val active: Boolean get() = deadlineElapsedRealtime > 0
    fun remainingMillis(nowElapsedRealtime: Long): Long = (deadlineElapsedRealtime-nowElapsedRealtime).coerceAtLeast(0)
}

/** An elapsed clock cannot drift when the user changes the phone date/time. */
internal class PlaybackSleepDeadline {
    var state=SleepTimerState(); private set
    fun start(minutes: Int,nowElapsedRealtime: Long): Boolean {
        if(minutes !in listOf(15,30,45,60)) return false
        state=SleepTimerState(nowElapsedRealtime+minutes*60_000L)
        return true
    }
    fun cancel() {state=SleepTimerState()}
    fun expire(nowElapsedRealtime: Long): Boolean {
        if(!state.active || state.remainingMillis(nowElapsedRealtime) > 0) return false
        cancel()
        return true
    }
}

/** Paused restoration and seek/progress events do not count as a new listen. */
internal class PlaybackHistoryTracker {
    private var lastPlayedId: String? = null
    fun observe(currentId: String?,playing: Boolean): String? {
        if(currentId == null) {lastPlayedId=null;return null}
        if(!playing || currentId == lastPlayedId) return null
        lastPlayedId=currentId
        return currentId
    }
}
