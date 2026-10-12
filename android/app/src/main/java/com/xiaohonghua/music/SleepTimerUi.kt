package com.xiaohonghua.music

import android.os.SystemClock
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay

/** Shared by the player sheet and settings page; playback stays owned by the service. */
@Composable internal fun SleepTimerPanel(
    state: SleepTimerState,connected: Boolean,onSet: (Int)->Boolean,onCancel: ()->Boolean,
    modifier: Modifier=Modifier,onError: (String)->Unit={},
) {
    val lifecycleOwner=LocalLifecycleOwner.current
    var now by remember(state.deadlineElapsedRealtime) {mutableLongStateOf(SystemClock.elapsedRealtime())}
    var error by remember {mutableStateOf<String?>(null)}
    LaunchedEffect(state.deadlineElapsedRealtime,lifecycleOwner) {
        if(state.active) lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while(true) {now=SystemClock.elapsedRealtime();delay(1_000)}
        }
    }
    val colors=MaterialTheme.colorScheme
    val remaining=state.remainingMillis(now)
    val countdown=timeLabel(((remaining+999)/1_000)*1_000)
    Column(modifier.testTag("sleep-timer-panel").fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement=Arrangement.spacedBy(20.dp)) {
        Surface(Modifier.fillMaxWidth(),shape=RoundedCornerShape(20.dp),color=colors.primaryContainer) {
            Row(Modifier.padding(20.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                Icon(Icons.Outlined.Timer,null,Modifier.size(28.dp),tint=colors.onPrimaryContainer)
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    Text(if(state.active) "剩余 $countdown" else "未开启定时暂停",
                        Modifier.testTag("sleep-timer-countdown"),fontSize=22.sp,fontWeight=FontWeight.SemiBold,color=colors.onPrimaryContainer)
                    Text("到时暂停播放，保留当前进度",fontSize=12.sp,lineHeight=18.sp,color=colors.onPrimaryContainer)
                }
            }
        }
        Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
            listOf(listOf(15,30),listOf(45,60)).forEach {row ->
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    row.forEach {minutes ->
                        FilledTonalButton(onClick={
                            if(onSet(minutes)) error=null else {
                                val text="定时暂停未开启，请稍后再试"
                                error=text;onError(text)
                            }
                        },enabled=connected,modifier=Modifier.testTag("sleep-timer-preset-$minutes").weight(1f).heightIn(min=52.dp)) {
                            Text("$minutes 分钟",fontSize=16.sp)
                        }
                    }
                }
            }
        }
        if(state.active) OutlinedButton(onClick={
            if(onCancel()) error=null else {
                val text="取消失败，请稍后再试"
                error=text;onError(text)
            }
        },enabled=connected,modifier=Modifier.testTag("sleep-timer-cancel").fillMaxWidth().heightIn(min=48.dp)) {Text("取消定时暂停")}
        if(!connected) Text("播放器正在连接，请稍候",Modifier.testTag("sleep-timer-connecting"),fontSize=13.sp,color=colors.onSurfaceVariant)
        error?.let {Text(it,Modifier.testTag("sleep-timer-error"),fontSize=13.sp,lineHeight=20.sp,color=colors.error)}
    }
}
