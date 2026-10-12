package com.xiaohonghua.music

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay

/** Positive values delay lyrics; only releasing or tapping commits the song's offset. */
@Composable internal fun LyricTimingControl(song: Song?,text: String,offsetMillis: Long,
    playbackPosition: ()->Long,onChange: ((Long)->Unit)?,modifier: Modifier=Modifier) {
    key(song?.id) {
        var draft by remember(offsetMillis) {mutableLongStateOf(offsetMillis)}
        val mounted=remember {booleanArrayOf(true)}
        DisposableEffect(Unit) {onDispose {mounted[0]=false}}
        val latestPosition by rememberUpdatedState(playbackPosition)
        val lifecycle=LocalLifecycleOwner.current.lifecycle
        var position by remember {mutableLongStateOf(latestPosition())}
        val lines=remember(text,song?.duration) {Lrc.parse(text,song?.duration?.takeIf {it > 0})}
        val haptic=rememberActionHaptic()
        val enabled=song != null && onChange != null
        fun commit(value: Long) {if(!mounted[0] || !enabled) return;draft=value.coerceIn(-MAX_LYRIC_OFFSET,MAX_LYRIC_OFFSET);onChange?.invoke(draft);haptic()}
        LaunchedEffect(lifecycle) {lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while(true) {position=latestPosition();delay(100)}
        }}
        Column(modifier.fillMaxWidth().padding(bottom=24.dp)) {
            Text("歌词时间校准",fontWeight=FontWeight.Medium)
            Text("只调整当前歌曲；正值延后，负值提前，不改变音频进度。",fontSize=12.sp,color=muted,
                lineHeight=18.sp,modifier=Modifier.padding(top=6.dp))
            Row(Modifier.fillMaxWidth().padding(top=8.dp),verticalAlignment=Alignment.CenterVertically) {
                ExpressiveIconButton(onClick={commit(draft-100)},enabled=enabled && draft > -MAX_LYRIC_OFFSET,
                    modifier=Modifier.testTag("lyric-offset-minus")) {Icon(Icons.Outlined.Remove,"歌词提前 0.1 秒")}
                val label=when {draft > 0 -> "延后 %.1f 秒".format(draft/1000.0);draft < 0 -> "提前 %.1f 秒".format(-draft/1000.0);else -> "与 LRC 一致"}
                Text(label,Modifier.weight(1f).testTag("lyric-offset-value"),fontSize=14.sp,color=MaterialTheme.colorScheme.primary)
                ExpressiveIconButton(onClick={commit(draft+100)},enabled=enabled && draft < MAX_LYRIC_OFFSET,
                    modifier=Modifier.testTag("lyric-offset-plus")) {Icon(Icons.Outlined.Add,"歌词延后 0.1 秒")}
            }
            Slider(draft.toFloat(),onValueChange={draft=(kotlin.math.round(it/100)*100).toLong()},
                onValueChangeFinished={if(enabled) commit(draft)},valueRange=-MAX_LYRIC_OFFSET.toFloat()..MAX_LYRIC_OFFSET.toFloat(),
                steps=199,enabled=enabled,modifier=Modifier.testTag("lyric-offset-slider").semantics {contentDescription="当前歌曲歌词偏移"})
            val index=Lrc.current(lines,lyricPosition(position,draft))
            Surface(shape=MaterialTheme.shapes.medium,color=MaterialTheme.colorScheme.primary.copy(alpha=.07f)) {
                Text(lines.getOrNull(index)?.text?.ifBlank {"·"} ?: "播放时可在这里预览当前歌词",Modifier.fillMaxWidth().padding(16.dp).testTag("lyric-offset-preview"),
                    fontSize=14.sp,color=muted,lineHeight=22.sp,maxLines=3,overflow=TextOverflow.Ellipsis)
            }
            TextButton(onClick={commit(0)},enabled=enabled && draft != 0L,modifier=Modifier.testTag("lyric-offset-reset")) {Text("重置为 0")}
        }
    }
}
