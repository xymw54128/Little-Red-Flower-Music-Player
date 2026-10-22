@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.xiaohonghua.music

import androidx.compose.foundation.*
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable internal fun SheetTitle(title: String,subtitle: String="") {
    Column(Modifier.fillMaxWidth().padding(horizontal=24.dp,vertical=12.dp)) {Text(title,fontSize=22.sp,fontWeight=FontWeight.SemiBold);if(subtitle.isNotBlank()) Text(subtitle,fontSize=12.sp,color=muted,modifier=Modifier.padding(top=8.dp))}
}
@Composable internal fun SettingRow(icon: ImageVector,title: String,subtitle: String="",onClick:()->Unit) {
    val interaction=remember {MutableInteractionSource()}
    Row(Modifier.fillMaxWidth().pressScale(interaction).clickable(interactionSource=interaction,indication=LocalIndication.current,onClick=onClick).padding(horizontal=24.dp,vertical=16.dp),verticalAlignment=Alignment.CenterVertically) {
        Icon(icon,null,tint=MaterialTheme.colorScheme.primary,modifier=Modifier.size(23.dp));Column(Modifier.padding(horizontal=16.dp).weight(1f)) {Text(title,fontSize=16.sp);if(subtitle.isNotBlank()) Text(subtitle,fontSize=12.sp,color=muted,modifier=Modifier.padding(top=5.dp))};Icon(Icons.Outlined.ChevronRight,null,tint=muted,modifier=Modifier.size(18.dp))
    }
}
@Composable private fun GroupLabel(text: String) {Text(text,Modifier.padding(start=24.dp,top=24.dp,bottom=8.dp),fontSize=12.sp,color=muted)}
@Composable internal fun SettingsPage(library: LibraryState,onPage:(String)->Unit,onSystemLock:()->Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom=24.dp)) {
        GroupLabel("播放与歌词")
        SettingRow(Icons.Outlined.Lock,"系统锁屏音乐控件","前往手机通知设置管理锁屏显示",onSystemLock)
        SettingRow(Icons.Outlined.TextFields,"歌词样式","歌词导入、字体与动态滚动") {onPage("lyric-style")}
        GroupLabel("外观")
        SettingRow(Icons.Outlined.Palette,"主题颜色","夜间主题 · 红花、森林、海蓝、陶土、暮紫") {onPage("theme")}
        GroupLabel("本地音乐")
        SettingRow(Icons.Outlined.FolderOpen,"歌曲文件夹","${library.folders.size} 个来源 · 刷新与增删改同步") {onPage("folders")}
        SettingRow(Icons.Outlined.Backup,"备份与恢复","保存歌单、排序、歌词与设置") {onPage("backup")}
        GroupLabel("关于")
        Text("小红花 ${BuildConfig.VERSION_NAME} · 本地播放器\n春风桃李花开日，秋雨梧桐叶落时。",Modifier.padding(horizontal=24.dp,vertical=12.dp),fontSize=12.sp,lineHeight=20.sp,color=muted)
        Text("作者：浪雨林兴云魔王",Modifier.testTag("app-author").padding(horizontal=24.dp),fontSize=14.sp,color=MaterialTheme.colorScheme.primary,fontFamily=lyricFont("kai"))
    }
}
@Composable internal fun FolderPage(library: LibraryState,onAdd:()->Unit,onRefresh:()->Unit,onRemove:(Folder)->Unit,onFiles:(()->Unit)?=null) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {Button(onClick=onAdd,modifier=Modifier.weight(1f)) {Icon(Icons.Outlined.CreateNewFolder,null,Modifier.size(18.dp));Text("添加文件夹")};OutlinedButton(onClick=onRefresh,enabled=!library.busy,modifier=Modifier.weight(1f)) {Icon(Icons.Outlined.Refresh,null,Modifier.size(18.dp));Text("刷新")}}
        if(onFiles != null) OutlinedButton(onClick=onFiles,modifier=Modifier.testTag("import-music-files").fillMaxWidth().padding(top=8.dp)) {Icon(Icons.Outlined.AudioFile,null,Modifier.size(18.dp));Text("导入音乐文件")}
        if(library.showScanProgress) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top=16.dp))
        Text(library.status.ifBlank {"共 ${library.songs.size} 首音乐"},fontSize=12.sp,color=muted,modifier=Modifier.padding(vertical=16.dp))
        Text("仅手动刷新。每次刷新重新读取全部标签、封面和歌词，并检查文件增删改；包含文件夹和单独导入的歌曲。扫描期间请稍候。",fontSize=12.sp,lineHeight=20.sp,color=muted,modifier=Modifier.padding(vertical=16.dp))
        library.folders.forEach {folder -> Surface(Modifier.fillMaxWidth().padding(vertical=6.dp),shape=RoundedCornerShape(16.dp),color=MaterialTheme.colorScheme.surface) {Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically) {
            Icon(Icons.Outlined.Folder,null,tint=MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f).padding(start=12.dp)) {Text(folder.name,fontWeight=FontWeight.Medium);Text("${library.songs.count {folder.uri in it.sources}} 首歌曲",fontSize=12.sp,color=muted,modifier=Modifier.padding(top=6.dp));Text(UriLabel(folder.uri),fontSize=10.sp,color=muted,maxLines=2,overflow=TextOverflow.Ellipsis)}
            ExpressiveIconButton(onClick={onRemove(folder)}) {Icon(Icons.Outlined.Close,"移除文件夹")}
        }} }
        if(library.folders.isEmpty()) Text("尚未添加文件夹\n单独导入的文件会保留在音乐库中。",Modifier.padding(vertical=24.dp),color=muted,fontSize=13.sp,lineHeight=22.sp)
        Text("最近同步",fontSize=15.sp,fontWeight=FontWeight.Medium,modifier=Modifier.padding(top=20.dp,bottom=12.dp))
        library.events.forEach {Text(it,fontSize=12.sp,color=muted,lineHeight=20.sp,modifier=Modifier.padding(vertical=5.dp))}
    }
}
private fun UriLabel(uri: String)=android.net.Uri.decode(uri.substringAfter("/tree/"))

@Composable internal fun LyricSettings(settings: Settings,song: Song?=null,onImport:(()->Unit)?=null,onComplete:(String)->Unit={},onChange:(Settings)->Unit) {
    var licenses by remember {mutableStateOf(false)}
    var draft by remember(settings) {mutableStateOf(settings)}
    val haptic=rememberActionHaptic()
    fun apply(value: Settings) {val changed=value != draft;draft=value;if(value != settings) onChange(value);if(changed) haptic()}
    fun finish() {if(draft != settings) {onChange(draft);haptic();onComplete("已保存歌词样式")}}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
        Text("当前歌曲歌词",fontWeight=FontWeight.Medium)
        Text(song?.let {"${it.title} - ${it.artist}"} ?: "先播放一首歌曲，再导入对应歌词。",fontSize=12.sp,color=muted,modifier=Modifier.padding(top=8.dp))
        OutlinedButton(onClick={onImport?.invoke()},enabled=song != null && onImport != null,modifier=Modifier.testTag("import-current-lyrics").fillMaxWidth().padding(top=12.dp,bottom=24.dp)) {
            Icon(Icons.Outlined.Description,null,Modifier.size(18.dp));Spacer(Modifier.width(8.dp));Text("导入当前歌曲歌词")
        }
        Text("字体",fontWeight=FontWeight.Medium)
        Column(Modifier.selectableGroup().padding(top=8.dp)) {lyricFontOptions.forEach { (key,label) ->
            val selected=draft.lyricFont == key
            val tint by animateColorAsState(if(selected) MaterialTheme.colorScheme.primary.copy(alpha=.08f) else Color.Transparent,tween(160),label="font-selection")
            Row(Modifier.testTag("lyric-font-$key").fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(tint).selectable(selected,role=Role.RadioButton,onClick={apply(draft.copy(lyricFont=key))}).padding(vertical=2.dp),verticalAlignment=Alignment.CenterVertically) {
                RadioButton(selected,onClick=null)
                Column(Modifier.padding(vertical=8.dp)) {
                    Text(label,fontFamily=lyricFont(key))
                    Text(lyricFontDescriptions.getValue(key),fontSize=12.sp,color=muted,modifier=Modifier.padding(top=3.dp))
                }
            }
        } }
        SettingSlider("字号","${draft.lyricSize.toInt()} sp",draft.lyricSize,18f..34f,onFinished=::finish) {draft=draft.copy(lyricSize=it)}
        SettingSlider("行间距","${draft.lyricGap.toInt()} dp",draft.lyricGap,0f..48f,onFinished=::finish) {draft=draft.copy(lyricGap=it)}
        Row(Modifier.fillMaxWidth().padding(vertical=12.dp),verticalAlignment=Alignment.CenterVertically) {Column(Modifier.weight(1f)) {Text("动态歌词");Text("按 LRC 时间戳高亮并平滑定位",fontSize=12.sp,color=muted,modifier=Modifier.padding(top=6.dp))};Switch(draft.dynamicLyrics,{apply(draft.copy(dynamicLyrics=it))})}
        Text("效果预览",fontSize=12.sp,color=muted,modifier=Modifier.padding(top=20.dp,bottom=12.dp))
        Surface(Modifier.fillMaxWidth(),shape=RoundedCornerShape(20.dp),color=pageColor) {Column(Modifier.padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally) {
            listOf("让音乐在这里开花 0123456789","Music blooms · 音楽が咲く","음악이 피어나다 · 123").forEachIndexed {index,text -> Text(text,Modifier.fillMaxWidth().padding(vertical=draft.lyricGap.dp/2+6.dp).graphicsLayer {scaleX=if(index == 1) 1.05f else 1f;scaleY=scaleX},fontSize=draft.lyricSize.sp,fontFamily=lyricFont(draft.lyricFont),textAlign=TextAlign.Center,
                color=if(index == 1) MaterialTheme.colorScheme.primary else muted.copy(alpha=.6f),fontWeight=FontWeight.Normal) }
        }}
        Text("手动浏览歌词后，1.5 秒无操作平滑回到当前歌词行。普通 LRC 提供逐行效果；没有逐字时间戳时不生成逐字动画。",Modifier.padding(top=16.dp),fontSize=12.sp,color=muted,lineHeight=20.sp)
        TextButton(onClick={licenses=true},modifier=Modifier.padding(top=8.dp)) {Text("字体来源与许可",fontSize=12.sp)}
    }
    if(licenses) FontLicenseDialog {licenses=false}
}
@Composable private fun FontLicenseDialog(onDismiss:()->Unit) {
    val context=LocalContext.current
    val text by produceState("正在读取许可…",context) {value=withContext(Dispatchers.IO) {
        lyricFontLicenses.joinToString("\n\n") {(name,file) ->
            name+"\n\n"+context.assets.open("font-licenses/$file").bufferedReader().use {it.readText()}
        }
    }}
    AppAlertDialog(onDismissRequest=onDismiss,title={Text("开源字体许可")},text={Text(text,Modifier.heightIn(max=380.dp).verticalScroll(rememberScrollState()),fontSize=12.sp,lineHeight=18.sp)},
        confirmButton={TextButton(onClick=onDismiss) {Text("关闭")}})
}
@Composable internal fun ThemeSettings(settings: Settings,onChange:(Settings)->Unit) {
    val haptic=rememberActionHaptic()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).selectableGroup().padding(24.dp)) {
        Row(Modifier.fillMaxWidth().padding(bottom=24.dp),verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {Text("夜间主题",fontWeight=FontWeight.Medium);Text("柔和深色背景，保留花朵主题色",fontSize=12.sp,color=muted,modifier=Modifier.padding(top=6.dp))}
            Switch(settings.nightMode,{onChange(settings.copy(nightMode=it));haptic()},modifier=Modifier.testTag("night-theme"))
        }
        Text("用一种颜色，陪伴音乐",fontSize=15.sp,color=muted,modifier=Modifier.padding(bottom=24.dp))
        listOf("红花","森林","海蓝","陶土","暮紫").forEachIndexed {index,label ->
            val selected=settings.theme == index
            val tint by animateColorAsState(if(selected) accents[index].copy(alpha=.09f) else Color.Transparent,tween(180),label="theme-selection")
            Row(Modifier.testTag("theme-$index").fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(tint).selectable(selected,role=Role.RadioButton,onClick={if(!selected) {onChange(settings.copy(theme=index));haptic()}}).padding(16.dp),verticalAlignment=Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).clip(CircleShape).background(accents[index]));Text(label,Modifier.weight(1f).padding(start=16.dp))
            Box(Modifier.size(24.dp),contentAlignment=Alignment.Center) {ThemeSelectionMark(selected,accents[index])}
        } }
    }
}
@Composable private fun ThemeSelectionMark(selected: Boolean,color: Color) {
    AnimatedVisibility(selected,enter=fadeIn(tween(150))+scaleIn(tween(180),initialScale=.7f),exit=fadeOut(tween(100))) {Icon(Icons.Outlined.Check,"已选择",tint=color)}
}
@Composable internal fun SettingSlider(label: String,valueLabel: String,value: Float,range: ClosedFloatingPointRange<Float>,onFinished:()->Unit={},onChange:(Float)->Unit) {
    val interaction=remember {MutableInteractionSource()}
    val dragging by interaction.collectIsDraggedAsState()
    val color by animateColorAsState(if(dragging) MaterialTheme.colorScheme.primary else muted,tween(120),label="slider-value")
    Row(Modifier.fillMaxWidth().padding(top=20.dp)) {Text(label,Modifier.weight(1f),fontWeight=FontWeight.Medium);Text(valueLabel,fontSize=12.sp,color=color)}
    Slider(value,onChange,modifier=Modifier.testTag("setting-slider-$label"),onValueChangeFinished=onFinished,valueRange=range,interactionSource=interaction)
}
@Composable internal fun BackupPage(onExport:()->Unit,onRestore:()->Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
        Icon(Icons.Outlined.Backup,null,Modifier.size(48.dp),tint=MaterialTheme.colorScheme.primary)
        Text("把喜欢的顺序，一起带走",fontSize=21.sp,fontWeight=FontWeight.Medium)
        Text("备份包含音乐索引、歌单、自定义排序、手动歌词、封面图案和设置（含夜间主题）。音乐文件与歌单照片请另行复制；换手机后重新选择照片。",fontSize=14.sp,lineHeight=24.sp,color=muted)
        Button(onClick=onExport,modifier=Modifier.fillMaxWidth()) {Text("导出备份")}
        OutlinedButton(onClick=onRestore,modifier=Modifier.fillMaxWidth()) {Text("从备份恢复")}
        Text("更换手机：先复制音乐文件，恢复此备份，再选择对应文件夹授权。应用会按文件名和大小匹配；重名且相同大小的多个文件需要避免混用。\n\n版本升级：使用相同包名和签名直接覆盖安装，保留内部数据。卸载会清除应用数据，请先导出备份。",fontSize=12.sp,lineHeight=21.sp,color=muted)
    }
}
@Composable internal fun NameDialog(title: String,initial: String="",onDismiss:()->Unit,onConfirm:(String)->Unit) {
    var name by rememberSaveable(initial) {mutableStateOf(initial)}
    var submitted by rememberSaveable(initial) {mutableStateOf(false)}
    val focus=remember {FocusRequester()}
    val haptic=rememberActionHaptic()
    fun submit() {if(name.isNotBlank() && !submitted) {submitted=true;haptic();onConfirm(name.trim())}}
    LaunchedEffect(Unit) {focus.requestFocus()}
    AppAlertDialog(onDismissRequest=onDismiss,title={Text(title)},text={OutlinedTextField(name,{if(it.length<=60) {name=it;submitted=false}},Modifier.testTag("playlist-name-field").focusRequester(focus),singleLine=true,label={Text("歌单名称")},keyboardOptions=KeyboardOptions(imeAction=ImeAction.Done),keyboardActions=KeyboardActions(onDone={submit()}))},
        confirmButton={TextButton(onClick=::submit,enabled=name.isNotBlank() && !submitted) {Text("确定")}},dismissButton={TextButton(onClick=onDismiss) {Text("取消")}})
}
@Composable internal fun PlaylistPicker(song: Song,playlists: List<Playlist>,onDismiss:()->Unit,onAdd:(Playlist)->Unit,onCreate:(String)->Unit) {
    var selected by rememberSaveable(song.id) {mutableStateOf<String?>(null)}
    var create by rememberSaveable(song.id) {mutableStateOf(false)}
    var name by rememberSaveable(song.id) {mutableStateOf("")}
    var submitted by rememberSaveable(song.id) {mutableStateOf(false)}
    val latestPlaylists by rememberUpdatedState(playlists)
    val haptic=rememberActionHaptic()
    val focus=remember {FocusRequester()}
    val focusManager=LocalFocusManager.current
    val keyboard=LocalSoftwareKeyboardController.current
    val sheetState=rememberModalBottomSheetState()
    val dismissSheet=rememberSheetDismiss(sheetState)
    var closing by remember(song.id) {mutableStateOf(false)}
    fun close() {if(!closing) {closing=true;keyboard?.hide();focusManager.clearFocus();dismissSheet(onDismiss)}}
    fun showLists() {keyboard?.hide();focusManager.clearFocus();create=false;submitted=false}
    fun submitName() {if(name.isNotBlank() && !submitted && !closing) {submitted=true;haptic();onCreate(name.trim());close()}}
    AppModalBottomSheet(onDismissRequest=onDismiss,sheetState=sheetState,sheetGesturesEnabled=!closing) {
        BackHandler(create) {showLists()}
        AnimatedContent(create,modifier=Modifier.fillMaxWidth().imePadding(),transitionSpec={
            (fadeIn(tween(160,delayMillis=40))+slideInHorizontally(tween(220)) {if(targetState) it/12 else -it/12}) togetherWith
                (fadeOut(tween(100))+slideOutHorizontally(tween(180)) {if(targetState) -it/12 else it/12})
        },label="playlist-picker-step") {creating ->
        if(creating) Column(Modifier.fillMaxWidth().padding(start=24.dp,end=24.dp,bottom=24.dp)) {
            LaunchedEffect(Unit) {focus.requestFocus()}
            Row(verticalAlignment=Alignment.CenterVertically) {
                ExpressiveIconButton(onClick=::showLists) {Icon(Icons.Outlined.ArrowBack,"返回歌单选择")}
                Text("新建歌单",fontSize=22.sp,fontWeight=FontWeight.SemiBold)
            }
            Text("创建后加入「${song.title}」",fontSize=12.sp,color=muted,modifier=Modifier.padding(top=8.dp,bottom=20.dp))
            OutlinedTextField(name,{if(it.length<=60) {name=it;submitted=false}},Modifier.testTag("playlist-picker-name").fillMaxWidth().focusRequester(focus),singleLine=true,label={Text("歌单名称")},keyboardOptions=KeyboardOptions(imeAction=ImeAction.Done),keyboardActions=KeyboardActions(onDone={submitName()}))
            Button(onClick=::submitName,enabled=name.isNotBlank() && !submitted && !closing,modifier=Modifier.testTag("playlist-create-confirm").fillMaxWidth().padding(top=20.dp)) {Text("确定")}
        } else Column(Modifier.fillMaxWidth()) {
            SheetTitle("添加到歌单",song.title)
            TextButton(onClick={create=true;submitted=false},modifier=Modifier.padding(horizontal=16.dp)) {Icon(Icons.Outlined.Add,null);Text("新建歌单并添加")}
            LazyColumn(Modifier.selectableGroup().heightIn(max=340.dp)) {items(playlists,key={it.id}) {list ->
                val exists=song.id in list.songIds
                val chosen=selected == list.id && !exists
                val tint by animateColorAsState(if(chosen) MaterialTheme.colorScheme.primary.copy(alpha=.08f) else Color.Transparent,tween(160),label="playlist-selection")
                Row(Modifier.testTag("playlist-choice-${list.id}").fillMaxWidth().background(tint).selectable(chosen,enabled=!exists,role=Role.RadioButton,onClick={if(selected != list.id) {selected=list.id;haptic()}}).padding(horizontal=24.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
                    RadioButton(chosen,onClick=null,enabled=!exists);PlaylistCover(list,Modifier.size(44.dp));Column(Modifier.weight(1f).padding(start=12.dp)) {Text(list.name,maxLines=1,overflow=TextOverflow.Ellipsis);Text(if(exists) "已在此歌单中" else "${list.songIds.size} 首歌曲",fontSize=12.sp,color=muted)}
                    if(exists) Icon(Icons.Outlined.Check,"已添加",Modifier.size(18.dp),tint=muted)
                }
            }}
            if(playlists.isEmpty()) Text("还没有歌单，创建一个即可添加歌曲。",Modifier.padding(24.dp),fontSize=13.sp,color=muted)
            val target=playlists.find {it.id == selected && song.id !in it.songIds}
            Button(onClick={if(!submitted && !closing) latestPlaylists.find {it.id == selected && song.id !in it.songIds}?.let {submitted=true;haptic();onAdd(it);close()}},enabled=target != null && !submitted && !closing,modifier=Modifier.testTag("playlist-add-confirm").fillMaxWidth().padding(24.dp)) {Text("确认添加")}
        }
        }
    }
}
@Composable internal fun SongInfo(song: Song,onDismiss:()->Unit) {
    AppModalBottomSheet(onDismissRequest=onDismiss) {
        Row(Modifier.fillMaxWidth().padding(start=24.dp,end=20.dp,bottom=8.dp),verticalAlignment=Alignment.CenterVertically) {
            Text("歌曲信息",Modifier.weight(1f),fontSize=22.sp,fontWeight=FontWeight.SemiBold)
            BotanicalTitleSprig(false,Modifier.size(36.dp))
        }
        BoxWithConstraints(Modifier.fillMaxWidth().heightIn(max=560.dp).verticalScroll(rememberScrollState())
            .testTag("song-info-content").padding(horizontal=24.dp)) {
            val stacked=maxWidth < 280.dp || LocalDensity.current.fontScale > 1.3f
            Column(Modifier.fillMaxWidth().padding(bottom=28.dp)) {
                SongArtworkPreview(song,Modifier.align(Alignment.CenterHorizontally))
                Text("标题",fontSize=12.sp,color=muted,modifier=Modifier.padding(top=8.dp))
                Text(song.title.ifBlank {"未设置"},Modifier.fillMaxWidth().padding(top=6.dp),
                    fontSize=22.sp,lineHeight=30.sp,fontWeight=FontWeight.SemiBold)
                Spacer(Modifier.height(24.dp))
                SongInfoRows(listOf("艺术家" to song.artist,"作曲家" to song.composer,"专辑" to song.album),stacked)
                HorizontalDivider(Modifier.padding(top=24.dp,bottom=20.dp),color=ink.copy(alpha=.1f))
                Text("音频与文件",fontSize=14.sp,fontWeight=FontWeight.SemiBold,color=MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(16.dp))
                SongInfoRows(listOf("格式" to song.format,"时长" to timeLabel(song.duration),
                    "采样率" to if(song.sampleRate > 0) "${song.sampleRate} Hz" else "未知",
                    "位深" to if(song.bitDepth > 0) "${song.bitDepth} bit" else "未知",
                    "文件大小" to "%.2f MB".format(song.size/1048576.0),
                    "文件名" to song.name,"文件地址" to android.net.Uri.decode(song.uri)),stacked)
            }
        }
    }
}

@Composable private fun SongInfoRows(fields: List<Pair<String,String>>,stacked: Boolean) {
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        fields.forEach {(label,value) ->
            if(stacked) Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                Text(label,fontSize=12.sp,color=muted)
                Text(value.ifBlank {"未设置"},fontSize=15.sp,lineHeight=22.sp)
            } else Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.Top) {
                Text(label,Modifier.width(76.dp).padding(top=1.dp,end=12.dp),fontSize=13.sp,lineHeight=21.sp,color=muted)
                Text(value.ifBlank {"未设置"},Modifier.weight(1f),fontSize=15.sp,lineHeight=22.sp)
            }
        }
    }
}
