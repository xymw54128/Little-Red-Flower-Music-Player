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
