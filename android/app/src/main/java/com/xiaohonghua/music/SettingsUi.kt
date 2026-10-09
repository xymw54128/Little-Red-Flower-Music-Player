@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.xiaohonghua.music

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
    Row(Modifier.fillMaxWidth().clickable(onClick=onClick).padding(horizontal=24.dp,vertical=16.dp),verticalAlignment=Alignment.CenterVertically) {
        Icon(icon,null,tint=MaterialTheme.colorScheme.primary,modifier=Modifier.size(23.dp));Column(Modifier.padding(horizontal=16.dp).weight(1f)) {Text(title,fontSize=16.sp);if(subtitle.isNotBlank()) Text(subtitle,fontSize=12.sp,color=muted,modifier=Modifier.padding(top=5.dp))};Icon(Icons.Outlined.ChevronRight,null,tint=muted,modifier=Modifier.size(18.dp))
    }
}
@Composable private fun GroupLabel(text: String) {Text(text,Modifier.padding(start=24.dp,top=24.dp,bottom=8.dp),fontSize=12.sp,color=muted)}
@Composable internal fun SettingsPage(library: LibraryState,onPage:(String)->Unit,onSystemLock:()->Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom=24.dp)) {
        GroupLabel("播放与歌词")
        SettingRow(Icons.Outlined.Lock,"系统锁屏音乐控件","前往手机通知设置管理锁屏显示",onSystemLock)
        SettingRow(Icons.Outlined.TextFields,"歌词样式","字体、字号、行间距与动态滚动") {onPage("lyric-style")}
        GroupLabel("外观")
        SettingRow(Icons.Outlined.Palette,"主题颜色","红花、森林、海蓝、陶土、暮紫") {onPage("theme")}
        SettingRow(Icons.Outlined.Wallpaper,"播放背景","封面或本地照片，调整透明度与构图") {onPage("background")}
        GroupLabel("本地音乐")
        SettingRow(Icons.Outlined.FolderOpen,"歌曲文件夹","${library.folders.size} 个来源 · 刷新与增删改同步") {onPage("folders")}
        SettingRow(Icons.Outlined.Backup,"备份与恢复","保存歌单、排序、歌词与设置") {onPage("backup")}
        GroupLabel("关于")
        Text("小红花 ${BuildConfig.VERSION_NAME} · 本地播放器\n音乐文件按原位置读取，全部播放与管理功能无需网络。",Modifier.padding(horizontal=24.dp,vertical=12.dp),fontSize=12.sp,lineHeight=20.sp,color=muted)
        Text("作者：浪雨林兴云魔王",Modifier.testTag("app-author").padding(horizontal=24.dp),fontSize=14.sp,color=MaterialTheme.colorScheme.primary,fontFamily=lyricFont("kai"))
    }
}
@Composable internal fun FolderPage(library: LibraryState,onAdd:()->Unit,onRefresh:()->Unit,onRemove:(Folder)->Unit,onMonitor:(Boolean)->Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {Button(onClick=onAdd,modifier=Modifier.weight(1f)) {Icon(Icons.Outlined.CreateNewFolder,null,Modifier.size(18.dp));Text("添加文件夹")};OutlinedButton(onClick=onRefresh,enabled=!library.busy,modifier=Modifier.weight(1f)) {Icon(Icons.Outlined.Refresh,null,Modifier.size(18.dp));Text("刷新")}}
        if(library.showScanProgress) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top=16.dp))
        Text(library.status.ifBlank {"共 ${library.songs.size} 首音乐"},fontSize=12.sp,color=muted,modifier=Modifier.padding(vertical=16.dp))
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {Column(Modifier.weight(1f)) {Text("监测文件夹变化",fontWeight=FontWeight.Medium);Text("应用前台或正在播放时自动检查",fontSize=12.sp,color=muted,modifier=Modifier.padding(top=6.dp))};Switch(library.settings.monitor,onMonitor)}
        Text("检测新增、删除和标签变化；刷新按钮会强制重新读取全部标签。应用关闭或系统休眠时不会持续扫描，再次进入会检查。",fontSize=12.sp,lineHeight=20.sp,color=muted,modifier=Modifier.padding(vertical=16.dp))
        library.folders.forEach {folder -> Surface(Modifier.fillMaxWidth().padding(vertical=6.dp),shape=RoundedCornerShape(16.dp),color=Color.White) {Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically) {
            Icon(Icons.Outlined.Folder,null,tint=MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f).padding(start=12.dp)) {Text(folder.name,fontWeight=FontWeight.Medium);Text("${library.songs.count {folder.uri in it.sources}} 首歌曲",fontSize=12.sp,color=muted,modifier=Modifier.padding(top=6.dp));Text(UriLabel(folder.uri),fontSize=10.sp,color=muted,maxLines=2,overflow=TextOverflow.Ellipsis)}
            IconButton(onClick={onRemove(folder)}) {Icon(Icons.Outlined.Close,"移除文件夹")}
        }} }
        if(library.folders.isEmpty()) Text("尚未添加文件夹\n单独导入的文件会保留在音乐库中。",Modifier.padding(vertical=24.dp),color=muted,fontSize=13.sp,lineHeight=22.sp)
        Text("最近同步",fontSize=15.sp,fontWeight=FontWeight.Medium,modifier=Modifier.padding(top=20.dp,bottom=12.dp))
        library.events.forEach {Text(it,fontSize=12.sp,color=muted,lineHeight=20.sp,modifier=Modifier.padding(vertical=5.dp))}
    }
}
private fun UriLabel(uri: String)=android.net.Uri.decode(uri.substringAfter("/tree/"))

@Composable internal fun LyricSettings(settings: Settings,onChange:(Settings)->Unit) {
    var licenses by remember {mutableStateOf(false)}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
        Text("字体",fontWeight=FontWeight.Medium)
        lyricFontOptions.forEach { (key,label) -> Row(Modifier.fillMaxWidth().clickable {onChange(settings.copy(lyricFont=key))},verticalAlignment=Alignment.CenterVertically) {
            RadioButton(settings.lyricFont == key,{onChange(settings.copy(lyricFont=key))});Text(label,fontFamily=lyricFont(key))
        } }
        SettingSlider("字号","${settings.lyricSize.toInt()} sp",settings.lyricSize,18f..34f) {onChange(settings.copy(lyricSize=it))}
        SettingSlider("行间距","${settings.lyricGap.toInt()} dp",settings.lyricGap,0f..48f) {onChange(settings.copy(lyricGap=it))}
        Row(Modifier.fillMaxWidth().padding(vertical=12.dp),verticalAlignment=Alignment.CenterVertically) {Column(Modifier.weight(1f)) {Text("动态歌词");Text("按 LRC 时间戳高亮并平滑定位",fontSize=12.sp,color=muted,modifier=Modifier.padding(top=6.dp))};Switch(settings.dynamicLyrics,{onChange(settings.copy(dynamicLyrics=it))})}
        Text("效果预览",fontSize=12.sp,color=muted,modifier=Modifier.padding(top=20.dp,bottom=12.dp))
        Surface(Modifier.fillMaxWidth(),shape=RoundedCornerShape(20.dp),color=Color.White) {Column(Modifier.padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally) {
            listOf("风经过窗边","让音乐在这里开花","把这一刻慢慢留下").forEachIndexed {index,text -> Text(text,Modifier.fillMaxWidth().padding(vertical=settings.lyricGap.dp/2+6.dp),fontSize=settings.lyricSize.sp,fontFamily=lyricFont(settings.lyricFont),textAlign=TextAlign.Center,
                color=if(index == 1) MaterialTheme.colorScheme.primary else muted.copy(alpha=.5f),fontWeight=FontWeight.Normal) }
        }}
        Text("手动浏览歌词后，4 秒无操作恢复定位。普通 LRC 提供逐行效果；没有逐字时间戳时不生成逐字动画。",Modifier.padding(top=16.dp),fontSize=12.sp,color=muted,lineHeight=20.sp)
        TextButton(onClick={licenses=true},modifier=Modifier.padding(top=8.dp)) {Text("字体来源与许可",fontSize=12.sp)}
    }
    if(licenses) FontLicenseDialog {licenses=false}
}
@Composable private fun FontLicenseDialog(onDismiss:()->Unit) {
    val context=LocalContext.current
    val text by produceState("正在读取许可…",context) {value=withContext(Dispatchers.IO) {
        listOf("霞鹜文楷 1.522" to "lxgw_wenkai.ttf","思源宋体 2.003" to "source_han_serif_cn.otf","朱雀仿宋 0.212" to "zhuque_fangsong.ttf").joinToString("\n\n") {(name,file) ->
            name+"\n\n"+context.assets.open("font-licenses/$file.OFL.txt").bufferedReader().use {it.readText()}
        }
    }}
    AlertDialog(onDismissRequest=onDismiss,title={Text("开源字体许可")},text={Text(text,Modifier.heightIn(max=380.dp).verticalScroll(rememberScrollState()),fontSize=12.sp,lineHeight=18.sp)},
        confirmButton={TextButton(onClick=onDismiss) {Text("关闭")}})
}
@Composable internal fun ThemeSettings(settings: Settings,onChange:(Settings)->Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
        Text("用一种颜色，陪伴音乐",fontSize=15.sp,color=muted,modifier=Modifier.padding(bottom=24.dp))
        listOf("红花","森林","海蓝","陶土","暮紫").forEachIndexed {index,label -> Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable {onChange(settings.copy(theme=index))}.padding(16.dp),verticalAlignment=Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).clip(CircleShape).background(accents[index]));Text(label,Modifier.weight(1f).padding(start=16.dp));if(settings.theme == index) Icon(Icons.Outlined.Check,"已选择",tint=accents[index])
        } }
    }
}
@Composable internal fun SettingSlider(label: String,valueLabel: String,value: Float,range: ClosedFloatingPointRange<Float>,onChange:(Float)->Unit) {
    Row(Modifier.fillMaxWidth().padding(top=20.dp)) {Text(label,Modifier.weight(1f),fontWeight=FontWeight.Medium);Text(valueLabel,fontSize=12.sp,color=muted)}
    Slider(value,onChange,valueRange=range)
}
@Composable internal fun BackgroundSettings(settings: Settings,song: Song?,onImport:()->Unit,onChange:(Settings)->Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
        Box(Modifier.fillMaxWidth().height(240.dp).clip(RoundedCornerShape(20.dp))) {PlayerBackground(settings,song,Modifier.matchParentSize());Column(Modifier.align(Alignment.Center),horizontalAlignment=Alignment.CenterHorizontally) {Icon(Icons.Outlined.LocalFlorist,null,Modifier.size(48.dp),tint=MaterialTheme.colorScheme.primary);Text(song?.title ?: "音乐在这里开花",Modifier.padding(top=16.dp),fontSize=20.sp,fontWeight=FontWeight.SemiBold);Text("实时背景预览",Modifier.padding(top=8.dp),fontSize=12.sp,color=muted)}}
        Row(Modifier.fillMaxWidth().padding(top=12.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            FilterChip(settings.background == "plain",{onChange(settings.copy(background="plain"))},label={Text("纯色")})
            FilterChip(settings.background == "artwork",{onChange(settings.copy(background="artwork"))},label={Text("歌曲封面")})
            FilterChip(settings.background == "photo",{onImport()},label={Text("本地照片")})
        }
        Text("等比铺满，不拉伸原图；调整缩放后可移动构图。",fontSize=12.sp,color=muted,modifier=Modifier.padding(top=12.dp))
        SettingSlider("图片不透明度","${(settings.opacity*100).toInt()}%",settings.opacity,0f..1f) {onChange(settings.copy(opacity=it))}
        SettingSlider("缩放","${(settings.scale*100).toInt()}%",settings.scale,1f..2.5f) {onChange(settings.copy(scale=it))}
        SettingSlider("水平位置","${(settings.x*100).toInt()}%",settings.x,0f..1f) {onChange(settings.copy(x=it))}
        SettingSlider("垂直位置","${(settings.y*100).toInt()}%",settings.y,0f..1f) {onChange(settings.copy(y=it))}
        OutlinedButton(onClick={onChange(settings.copy(opacity=.35f,scale=1f,x=.5f,y=.5f))},modifier=Modifier.fillMaxWidth().padding(top=12.dp)) {Text("重置构图")}
    }
}
@Composable internal fun BackupPage(onExport:()->Unit,onRestore:()->Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
        Icon(Icons.Outlined.Backup,null,Modifier.size(48.dp),tint=MaterialTheme.colorScheme.primary)
        Text("把喜欢的顺序，一起带走",fontSize=21.sp,fontWeight=FontWeight.Medium)
        Text("备份包含音乐索引、歌单、自定义排序、手动歌词、封面图案和设置。音乐文件、背景与歌单照片请另行复制；换手机后重新选择照片。",fontSize=14.sp,lineHeight=24.sp,color=muted)
        Button(onClick=onExport,modifier=Modifier.fillMaxWidth()) {Text("导出备份")}
        OutlinedButton(onClick=onRestore,modifier=Modifier.fillMaxWidth()) {Text("从备份恢复")}
        Text("更换手机：先复制音乐文件，恢复此备份，再选择对应文件夹授权。应用会按文件名和大小匹配；重名且相同大小的多个文件需要避免混用。\n\n版本升级：使用相同包名和签名直接覆盖安装，保留内部数据。卸载会清除应用数据，请先导出备份。",fontSize=12.sp,lineHeight=21.sp,color=muted)
    }
}
@Composable internal fun NameDialog(title: String,initial: String="",onDismiss:()->Unit,onConfirm:(String)->Unit) {
    var name by remember {mutableStateOf(initial)}
    AlertDialog(onDismissRequest=onDismiss,title={Text(title)},text={OutlinedTextField(name,{if(it.length<=60) name=it},singleLine=true,label={Text("歌单名称")})},
        confirmButton={TextButton(onClick={onConfirm(name.trim())},enabled=name.isNotBlank()) {Text("确定")}},dismissButton={TextButton(onClick=onDismiss) {Text("取消")}})
}
@Composable internal fun PlaylistPicker(song: Song,playlists: List<Playlist>,onDismiss:()->Unit,onAdd:(Playlist)->Unit,onCreate:(String)->Unit) {
    var selected by remember {mutableStateOf<String?>(null)}
    var create by remember {mutableStateOf(false)}
    ModalBottomSheet(onDismissRequest=onDismiss) {SheetTitle("添加到歌单",song.title)
        TextButton(onClick={create=true},modifier=Modifier.padding(horizontal=16.dp)) {Icon(Icons.Outlined.Add,null);Text("新建歌单并添加")}
        LazyColumn(Modifier.heightIn(max=340.dp)) {items(playlists,key={it.id}) {list -> val exists=song.id in list.songIds
            Row(Modifier.fillMaxWidth().clickable(enabled=!exists) {selected=list.id}.padding(horizontal=24.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
                RadioButton(selected == list.id,{selected=list.id},enabled=!exists);PlaylistCover(list,Modifier.size(44.dp));Column(Modifier.weight(1f).padding(start=12.dp)) {Text(list.name,maxLines=1,overflow=TextOverflow.Ellipsis);Text(if(exists) "已在此歌单中" else "${list.songIds.size} 首歌曲",fontSize=12.sp,color=muted)}
            }
        }}
        if(playlists.isEmpty()) Text("还没有歌单，创建一个即可添加歌曲。",Modifier.padding(24.dp),fontSize=13.sp,color=muted)
        Button(onClick={playlists.find {it.id == selected}?.let(onAdd)},enabled=selected != null,modifier=Modifier.fillMaxWidth().padding(24.dp)) {Text("确认添加")}
    }
    if(create) NameDialog("新建歌单",onDismiss={create=false}) {create=false;onCreate(it)}
}
@Composable internal fun SongInfo(song: Song,onDismiss:()->Unit) {
    ModalBottomSheet(onDismissRequest=onDismiss) {SheetTitle("歌曲信息",song.title)
        Column(Modifier.fillMaxWidth().heightIn(max=480.dp).verticalScroll(rememberScrollState()).padding(24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            listOf("歌手" to song.artist,"专辑" to song.album,"曲目" to song.track.ifBlank {"未设置"},"格式" to song.format,
                "时长" to timeLabel(song.duration),"采样率" to if(song.sampleRate > 0) "${song.sampleRate} Hz" else "未知",
                "位深" to if(song.bitDepth > 0) "${song.bitDepth} bit" else "未知","文件大小" to "%.2f MB".format(song.size/1048576.0),
                "文件名" to song.name,"文件地址" to android.net.Uri.decode(song.uri)).forEach {(key,value) -> Column {Text(key,fontSize=11.sp,color=muted);Text(value,fontSize=14.sp,modifier=Modifier.padding(top=4.dp))}}
        }
    }
}
