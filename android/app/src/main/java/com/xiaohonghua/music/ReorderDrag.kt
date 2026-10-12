package com.xiaohonghua.music

// Draft order survives metadata refreshes; only membership follows the live target.
internal fun reconcileReorderIds(draft: List<String>,source: List<String>): List<String> {
    val members=source.toSet()
    return (draft.filter(members::contains)+source).distinct()
}

internal fun moveReorderId(ids: List<String>,id: String,to: Int): List<String> {
    val from=ids.indexOf(id)
    if(from < 0 || to !in ids.indices || from == to) return ids
    return ids.toMutableList().apply {add(to,removeAt(from))}
}

internal data class ReorderSlot(val id: String,val index: Int,val top: Float,val height: Float)

internal fun reorderDragTarget(ids: List<String>,id: String,center: Float,slots: List<ReorderSlot>): Int? {
    val from=ids.indexOf(id)
    // Lazy layout can still describe the preceding frame after a list mutation.
    if(from < 0 || slots.any {ids.getOrNull(it.index) != it.id}) return null
    return slots.filter {it.index > from && center > it.top+it.height/2}.maxOfOrNull {it.index}
        ?: slots.filter {it.index < from && center < it.top+it.height/2}.minOfOrNull {it.index}
        ?: from
}

internal fun reorderEdgeVelocity(y: Float,start: Float,end: Float,edge: Float,speed: Float): Float {
    val zone=minOf(edge,(end-start)/3)
    if(zone <= 0f) return 0f
    return when {
        y < start+zone -> -speed*((start+zone-y)/zone).coerceIn(0f,1f)
        y > end-zone -> speed*((y-end+zone)/zone).coerceIn(0f,1f)
        else -> 0f
    }
}
