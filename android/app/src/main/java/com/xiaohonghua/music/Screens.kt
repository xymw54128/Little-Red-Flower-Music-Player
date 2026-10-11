@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.xiaohonghua.music

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import java.io.File

internal val accents = listOf(Color(0xFFCE3C4C), Color(0xFF527568), Color(0xFF436B92), Color(0xFF98644E), Color(0xFF76668D))
internal val pageColor = Color(0xFFF8F8F6)
internal val ink = Color(0xFF272B29)
internal val muted = Color(0xFF696F6B)

@Composable
fun MusicApp(model: MusicModel,showStartup: Boolean = false) {
    val library by model.library.collectAsStateWithLifecycle()
    // Only the seek bar observes position ticks; library/navigation stay stable.
    val details=remember(model) {model.playback.map {it.copy(position=0)}.distinctUntilChanged()}
    val playback by details.collectAsStateWithLifecycle(initialValue=PlaybackState())
    val context = LocalContext.current
    val focusManager=LocalFocusManager.current
    val keyboard=LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val haptic=rememberActionHaptic()
    var feedbackJob by remember {mutableStateOf<Job?>(null)}
    fun clearFeedback() {feedbackJob?.cancel();snackbar.currentSnackbarData?.dismiss()}
    fun feedback(text: String,completed: Boolean=false,undo: (()->Boolean)?=null) {
        clearFeedback()
        feedbackJob=scope.launch {
            val result=snackbar.showSnackbar(ActionFeedback(text,if(undo != null) "撤销" else null,completed,
                if(undo != null) SnackbarDuration.Long else SnackbarDuration.Short))
            if(result == SnackbarResult.ActionPerformed && undo != null) {
                val restored=undo()
                haptic()
                snackbar.showSnackbar(ActionFeedback(if(restored) "已撤销" else "内容已变化，无需恢复",completed=restored))
            }
        }
    }
    fun message(text: String) {feedback(text)}
    fun completed(text: String,undo: (()->Boolean)?=null) {feedback(text,true,undo)}
    var screen by rememberSaveable { mutableStateOf("library") }
    var returning by remember {mutableStateOf(false)}
    val mainPages=remember {listOf("library","playlists","settings")}
    val homePager=rememberPagerState(initialPage=mainPages.indexOf(screen).coerceAtLeast(0),pageCount={mainPages.size})
    val pageStates=rememberSaveableStateHolder()
    var history by rememberSaveable {mutableStateOf(emptyList<String>())}
    var startupActive by remember {mutableStateOf(showStartup)}
    var flowerDestination by remember {mutableStateOf<Offset?>(null)}
    var playlistId by rememberSaveable { mutableStateOf<String?>(null) }
    var coverId by rememberSaveable { mutableStateOf<String?>(null) }
    var coverTarget by rememberSaveable { mutableStateOf<String?>(null) }
    var group by rememberSaveable { mutableStateOf<String?>(null) }
    var libraryTab by rememberSaveable { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var searchExpanded by rememberSaveable {mutableStateOf(false)}
    var sheet by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<Song?>(null) }
    var addSong by remember { mutableStateOf<Song?>(null) }
    var lyricTarget by rememberSaveable { mutableStateOf<String?>(null) }
    var rename by remember { mutableStateOf<Playlist?>(null) }
    var create by remember { mutableStateOf(false) }
    var restoreUri by remember { mutableStateOf<Uri?>(null) }
    var removeFolder by remember { mutableStateOf<Folder?>(null) }
    val files = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris.forEach { uri -> runCatching { context.contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION) }.onFailure { message("文件授权未能长期保存，重启后可能需要重新选择") } }
        if(uris.isNotEmpty()) model.repository.importFiles(uris)
    }
    val folder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if(uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION)
                val name = android.provider.DocumentsContract.getTreeDocumentId(uri).substringAfterLast('/').substringAfterLast(':').ifBlank { "音乐文件夹" }
                model.repository.addFolder(uri,name)
            }.onFailure { message("文件夹授权失败：${it.message}") }
        }
    }
    val lyrics = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if(uri != null) lyricTarget?.let { model.importLyrics(it,uri,::message) } }
    val image = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if(uri != null) model.importBackground(uri,::message) }
    val coverPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) {uri ->
        if(uri != null) coverTarget?.let {model.importPlaylistCover(it,uri,::message)}
        coverTarget=null
    }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> if(uri != null) model.exportBackup(uri,::message) }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> restoreUri=uri }
    val playlist = library.playlists.find { it.id == playlistId }
    val byId=remember(library.songs) {library.songs.associateBy {it.id}}
    val current = byId[playback.currentId]
    val favoriteIds=remember(library.playlists) {library.playlists.firstOrNull {it.isFavorites}?.songIds.orEmpty().toSet()}
    val inPlaylist=screen == "playlist"
    val baseSongs = remember(inPlaylist,playlist?.songIds,library.songs) { if(inPlaylist) playlist?.songIds?.mapNotNull(byId::get).orEmpty() else library.songs }
    val activeSort=if(inPlaylist) playlist?.sort ?: SortMode.CUSTOM else library.sort
    val custom=if(inPlaylist) playlist?.songIds.orEmpty() else library.custom
    val random=if(inPlaylist) playlist?.randomIds.orEmpty() else library.random
    val sorted = remember(sheet,baseSongs,activeSort,custom,random) {if(sheet == "custom-sort") orderedSongs(baseSongs,activeSort,custom,random) else emptyList()}
    fun go(page: String) {
        if(screen == page) return
        returning=false
        keyboard?.hide();focusManager.clearFocus()
        if(page == "player" && history.lastOrNull() == "player") history=history.dropLast(1)
        else history=history+(if(screen in mainPages) mainPages[homePager.targetPage] else screen)
        screen=page; sheet=null
    }
    fun back() {returning=true;keyboard?.hide();focusManager.clearFocus(); when {
        screen == "library" && searchExpanded -> {searchExpanded=false;query=""}
        screen == "library" && group != null -> group=null
        history.isNotEmpty() -> {screen=history.last();history=history.dropLast(1)}
        screen == "playlist" -> screen="playlists"
        else -> screen="settings"
    } }
    fun selectHome(index: Int) {
        returning=false
        keyboard?.hide();focusManager.clearFocus()
        group=null;query="";searchExpanded=false;sheet=null;info=null;addSong=null;coverId=null;rename=null;create=false;history=emptyList()
        if(screen in mainPages) scope.launch {homePager.animateScrollToPage(index,animationSpec=spring(dampingRatio=.9f,stiffness=450f))} else screen=mainPages[index]
    }
    LaunchedEffect(homePager) {snapshotFlow {homePager.settledPage}.collect {index ->
        if(screen in mainPages && screen != mainPages[index]) {haptic();screen=mainPages[index];group=null;query="";searchExpanded=false}
    }}
    var scanWasVisible by remember {mutableStateOf(false)}
    LaunchedEffect(library.showScanProgress) {
        if(library.showScanProgress) scanWasVisible=true
        else if(scanWasVisible) {scanWasVisible=false;if(library.status.isNotBlank()) message(library.status)}
    }
    LaunchedEffect(screen) {if(screen in mainPages && homePager.settledPage != mainPages.indexOf(screen) && !homePager.isScrollInProgress) homePager.scrollToPage(mainPages.indexOf(screen))}
    LaunchedEffect(playlistId,library.playlists,library.ready,screen) {if(library.ready && screen == "playlist" && playlist == null) {history=emptyList();screen="playlists"}}
    BackHandler(screen !in mainPages || screen == "library" && (group != null || searchExpanded)) { back() }
    BackHandler(startupActive) {startupActive=false}
    MaterialTheme(colorScheme=lightColorScheme(primary=accents[library.settings.theme],background=pageColor,surface=Color.White,onSurface=ink,onBackground=ink,outlineVariant=Color(0xFFE6E8E3))) {
        Box(Modifier.fillMaxSize()) {
        Scaffold(modifier=Modifier.fillMaxSize(),containerColor=pageColor,snackbarHost={ SnackbarHost(snackbar) {ActionSnackbar(it)} },
            bottomBar={ if(screen != "player") Column {
                if(current != null) MiniPlayer(current,playback,model) { go("player") }
                NavigationBar(containerColor=pageColor,tonalElevation=0.dp) {
                    listOf(Triple("library","音乐库",Icons.Outlined.LibraryMusic),Triple("playlists","歌单",Icons.Outlined.QueueMusic),Triple("settings","设置",Icons.Outlined.Settings)).forEachIndexed {index,(page,label,icon) ->
                        val selected=if(screen in mainPages) homePager.targetPage == index else if(screen == "playlist") index == 1 else homePager.settledPage == index
                        val iconScale=animateFloatAsState(if(selected) 1.08f else 1f,spring(dampingRatio=.8f,stiffness=650f),label="navigation-icon")
                        NavigationBarItem(modifier=Modifier.testTag("nav-$page"),selected=selected,onClick={selectHome(index)},icon={ Icon(icon,label,Modifier.graphicsLayer {scaleX=iconScale.value;scaleY=iconScale.value}) },label={ Text(label) })
                    }
                }
            } }) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                val renderPage: @Composable (String)->Unit = {page ->
                pageStates.SaveableStateProvider(if(page == "playlist") "$page:$playlistId" else page) {
                val pageBase=remember(page,playlist?.songIds,library.songs) {if(page == "playlist") playlist?.songIds?.mapNotNull(byId::get).orEmpty() else library.songs}
                val pageSort=if(page == "playlist") playlist?.sort ?: SortMode.CUSTOM else library.sort
                val pageCustom=if(page == "playlist") playlist?.songIds.orEmpty() else library.custom
                val pageRandom=if(page == "playlist") playlist?.randomIds.orEmpty() else library.random
                val pageSorted=remember(page,pageBase,pageSort,pageCustom,pageRandom) {if(page in listOf("library","playlist")) orderedSongs(pageBase,pageSort,pageCustom,pageRandom) else emptyList()}
                val displayed=remember(pageSorted,group,libraryTab,query) {pageSorted.filter { song -> (group == null || if(libraryTab == 1) "${song.artist}\u0000${song.album}" == group else song.artist == group) &&
                    (query.isBlank() || listOf(song.title,song.artist,song.album,song.name).any {it.contains(query,true)})}}
                val groupSongs=remember(pageSorted,query) {if(query.isBlank()) pageSorted else pageSorted.filter {"${it.title} ${it.artist} ${it.album}".contains(query,true)}}
                if(page == "player") {
                    PlayerScreen(current,library,playback,model,onBack=::back,onQueue={ sheet="queue" },onAdd={ addSong=current },onLyrics={
                        if(current != null) { lyricTarget=current.id; lyrics.launch(arrayOf("*/*")) }
                    },onStyle={ go("lyric-style") })
                } else Column(Modifier.testTag("page-$page").fillMaxSize()) {
                    val title = when(page) {
                        "library" -> if(group != null) (if(libraryTab == 1) group!!.substringAfter('\u0000') else group!!) else "音乐库"
                        "playlist" -> playlist?.name ?: "歌单"
                        "playlists" -> "我的歌单"
                        "folders" -> "歌曲文件夹"
                        "lyric-style" -> "歌词样式"
                        "theme" -> "主题颜色"
                        "background" -> "播放背景"
                        "backup" -> "备份与恢复"
                        else -> "设置"
                    }
                    Row(Modifier.fillMaxWidth().padding(start=16.dp,end=12.dp,top=12.dp,bottom=8.dp),verticalAlignment=Alignment.CenterVertically) {
                        if(page !in listOf("library","playlists","settings") || group != null) ExpressiveIconButton(onClick=::back) { Icon(Icons.Outlined.ArrowBack,"返回") }
                        else Image(painterResource(when(page) {
                            "playlists" -> R.drawable.ic_header_playlists
                            "settings" -> R.drawable.ic_header_settings
                            else -> R.drawable.ic_flower
                        }),null,modifier=Modifier.padding(8.dp).size(24.dp).onGloballyPositioned {
                            if(page == "library") flowerDestination=it.boundsInRoot().center
                        }.testTag(if(page == "library") "library-header-flower" else "page-header-flower").graphicsLayer {
                            scaleX=1.5f;scaleY=1.5f;alpha=if(page == "library" && startupActive) 0f else 1f
                        })
                        if(page == "library") LibrarySearchHeader(title,query,searchExpanded,{query=it},{searchExpanded=true},{searchExpanded=false;query=""},Modifier.weight(1f))
                        else Text(title,Modifier.weight(1f),fontSize=27.sp,fontWeight=FontWeight.SemiBold,maxLines=1,overflow=TextOverflow.Ellipsis)
                        if(page == "playlists") ExpressiveIconButton(onClick={ create=true }) { Icon(Icons.Outlined.Add,"新建歌单") }
                        if(page == "playlist") {
                            ExpressiveIconButton(onClick={ coverId=playlist?.id }) { Icon(Icons.Outlined.PhotoLibrary,"更换歌单封面") }
                            if(playlist?.isFavorites != true) {
                                ExpressiveIconButton(onClick={ rename=playlist }) { Icon(Icons.Outlined.Edit,"重命名歌单") }
                                ExpressiveIconButton(onClick={ sheet="delete-playlist" }) { Icon(Icons.Outlined.DeleteOutline,"删除歌单") }
                            }
                        }
                    }
                    when(page) {
                        "library","playlist" -> {
                            if(page == "library" && group == null) Row(Modifier.padding(horizontal=24.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                listOf("歌曲","专辑","歌手").forEachIndexed { index,label ->
                                    val source=remember {MutableInteractionSource()}
                                    FilterChip(selected=libraryTab == index,onClick={if(libraryTab != index) haptic();libraryTab=index;query=""},
                                        modifier=Modifier.pressScale(source),interactionSource=source,label={ Text(label) })
                                }
                            }
                            if(page == "playlist") OutlinedTextField(query,{query=it},Modifier.fillMaxWidth().padding(horizontal=24.dp,vertical=8.dp),singleLine=true,placeholder={ Text("搜索歌曲、歌手或专辑") },leadingIcon={ Icon(Icons.Outlined.Search,null) },shape=RoundedCornerShape(16.dp))
                            if(page == "library" && libraryTab != 0 && group == null) {
                                GroupList(groupSongs,libraryTab) { group=it; query="" }
                            } else {
                                Row(Modifier.padding(horizontal=24.dp).fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                                    Text("共 ${pageBase.size} 首${if(query.isNotBlank()) " · 找到 ${displayed.size} 首" else ""}",Modifier.weight(1f),fontSize=12.sp,color=muted)
                                    TextButton(onClick={ sheet="sort" }) { Text(if(page == "playlist") playlist?.sort?.label ?: "排序" else library.sort.label); Icon(Icons.Outlined.SwapVert, null,Modifier.size(18.dp)) }
                                }
                                if(page == "playlist") Row(Modifier.fillMaxWidth().padding(horizontal=24.dp),verticalAlignment=Alignment.CenterVertically) {
                                    FilledTonalButton(onClick={ model.play(displayed) },enabled=displayed.any { it.available }) { Icon(Icons.Outlined.PlayArrow,null); Text("播放全部") }
                                    if(page == "playlist") Text("  添加歌曲：在音乐库选择目标歌单",fontSize=11.sp,color=muted,modifier=Modifier.weight(1f))
                                }
                                if(displayed.isEmpty()) EmptyLibrary(query.isNotBlank(),page == "playlist") { sheet="import" }
                                else SongList(displayed,pageBase.size,current?.id,Modifier.weight(1f),onPlay={model.play(displayed,it.id)},onAdd={addSong=it},onInfo={info=it},onRemove=if(page == "playlist" && playlist != null) {
                                    {song -> model.repository.removePlaylistSong(playlist.id,song.id)?.let {removed ->
                                        haptic();completed("已从「${playlist.name}」移除",undo={model.repository.restorePlaylistSong(removed)})
                                    }}
                                } else null,favoriteIds=favoriteIds,onFavorite={song ->
                                    if(model.repository.state.value.songs.any {it.id == song.id}) {
                                        val liked=song.id in model.repository.state.value.playlists.firstOrNull {it.isFavorites}?.songIds.orEmpty()
                                        model.repository.toggleFavorite(song.id)
                                        completed(if(liked) "已取消喜欢" else "已加入「我喜欢」")
                                    }
                                })
                            }
                        }
                        "playlists" -> {
                            Text("${library.playlists.size} 个歌单",Modifier.padding(horizontal=24.dp,vertical=8.dp),fontSize=12.sp,color=muted)
                            if(library.playlists.isEmpty()) Column(Modifier.fillMaxWidth().padding(40.dp),horizontalAlignment=Alignment.CenterHorizontally) { Icon(Icons.Outlined.QueueMusic,null,Modifier.size(52.dp),tint=MaterialTheme.colorScheme.primary); Text("把喜欢的歌放在一起",Modifier.padding(20.dp)); Button(onClick={create=true}) { Text("创建第一个歌单") } }
                            LazyColumn(contentPadding=PaddingValues(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                                items(library.playlists,key={it.id}) { list ->
                                    val source=remember {MutableInteractionSource()}
                                    Row(Modifier.animateItem().testTag("playlist-${list.id}").fillMaxWidth().pressScale(source,.985f).clip(RoundedCornerShape(20.dp)).background(Color.White).clickable(interactionSource=source,indication=LocalIndication.current) { playlistId=list.id; go("playlist"); group=null; query="" }.padding(16.dp),verticalAlignment=Alignment.CenterVertically) {
                                    PlaylistCover(list,Modifier.size(64.dp).clickable {coverId=list.id})
                                    Column(Modifier.padding(start=16.dp).weight(1f)) { Text(list.name,fontWeight=FontWeight.Medium,fontSize=18.sp); Text("${list.songIds.size} 首歌曲",fontSize=12.sp,color=muted,modifier=Modifier.padding(top=6.dp)) }
                                    ExpressiveIconButton(onClick={coverId=list.id}) {Icon(Icons.Outlined.PhotoLibrary,"更换歌单封面",tint=muted,modifier=Modifier.size(20.dp))}
                                } }
                            }
                        }
                        "settings" -> SettingsPage(library,onPage=::go,onSystemLock={
                            runCatching { context.startActivity(Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(AndroidSettings.EXTRA_APP_PACKAGE,context.packageName)) }.onFailure { message("请在手机设置中打开小红花的通知与锁屏显示设置") }
                        })
                        "folders" -> FolderPage(library,onAdd={folder.launch(null)},onRefresh={model.repository.requestRefresh()},onRemove={removeFolder=it},onMonitor={model.repository.settings(library.settings.copy(monitor=it))},onFiles={files.launch(arrayOf("audio/*","application/ogg","application/octet-stream"))})
                        "lyric-style" -> LyricSettings(library.settings,onComplete={completed(it)}) { model.repository.settings(it) }
                        "theme" -> ThemeSettings(library.settings) {model.repository.settings(it);completed("已应用主题颜色")}
                        "background" -> BackgroundSettings(library.settings,current,{image.launch(arrayOf("image/jpeg","image/png","image/webp"))},onComplete={completed(it)}) { model.repository.settings(it) }
                        "backup" -> BackupPage(onExport={export.launch("小红花备份-${java.time.LocalDate.now()}.json")},onRestore={restore.launch(arrayOf("application/json","text/plain","application/octet-stream"))})
                    }
                }
                }
                }
                AnimatedContent(targetState=if(screen in mainPages) "home" else screen,modifier=Modifier.fillMaxSize(),transitionSpec={
                    val enteringPlayer=targetState == "player"
                    val leavingPlayer=initialState == "player"
                    ((fadeIn(tween(220)) + if(enteringPlayer) slideInVertically(tween(300, easing=FastOutSlowInEasing)) {it/10} else slideInHorizontally(tween(250,easing=FastOutSlowInEasing)) {if(leavingPlayer) 0 else if(returning) -it/14 else it/14}) togetherWith
                        (fadeOut(tween(140)) + if(leavingPlayer) slideOutVertically(tween(260,easing=FastOutSlowInEasing)) {it/10} else slideOutHorizontally(tween(220,easing=FastOutSlowInEasing)) {if(enteringPlayer) 0 else if(returning) it/14 else -it/14})).using(null)
                },label="page-transition") {page ->
                    if(page == "home") HorizontalPager(homePager,Modifier.testTag("home-pager").fillMaxSize(),userScrollEnabled=group == null && !searchExpanded && sheet == null && info == null && addSong == null && coverId == null && rename == null && !create) {index ->renderPage(mainPages[index])}
                    else renderPage(page)
                }
                if(library.showScanProgress && screen in listOf("library","playlist")) LinearProgressIndicator(Modifier.testTag("manual-scan-progress").align(Alignment.TopCenter).fillMaxWidth())
            }
        }
        if(startupActive) LaunchScreen(flowerDestination) {startupActive=false}
        }
        library.playlists.find {it.id == coverId}?.let {list ->
            PlaylistCoverSheet(list,onDismiss={coverId=null},onPreset={model.repository.playlistCover(list.id,it)},onPhoto={
                coverTarget=list.id;coverId=null;coverPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },onComplete={completed(it)})
        }
        if(sheet == "import") ModalBottomSheet(onDismissRequest={sheet=null}) { SheetTitle("导入本地音乐","按原位置读取，不复制音乐文件")
            SettingRow(Icons.Outlined.AudioFile,"选择音乐文件","支持一次选择多首") { sheet=null; files.launch(arrayOf("audio/*","application/ogg","application/octet-stream")) }
            SettingRow(Icons.Outlined.FolderOpen,"选择音乐文件夹","扫描子文件夹和同名 LRC") { sheet=null; folder.launch(null) }
            Text("支持 FLAC、MP3、WAV、AAC、M4A、OGG、Opus 等常见音频。实际解码能力取决于文件编码。",Modifier.padding(24.dp),fontSize=12.sp,color=muted)
        }
        if(sheet == "sort") ModalBottomSheet(onDismissRequest={sheet=null}) { SheetTitle("歌曲排序","音乐库和每个歌单独立保存顺序")
            SortMode.entries.forEach { mode -> SettingRow(Icons.Outlined.Sort,mode.label,if(mode == SortMode.CUSTOM) "拖动调整，保存后生效" else "") {
                if(mode == SortMode.CUSTOM) sheet="custom-sort" else { model.repository.sort(mode,if(screen == "playlist") playlistId else null); sheet=null;completed("已按${mode.label}排列") }
            } }; Spacer(Modifier.height(16.dp))
        }
        if(sheet == "custom-sort") OrderSheet(sorted,onCancel={sheet=null},onSave={ ids -> model.repository.customOrder(ids,if(screen == "playlist") playlistId else null); sheet=null;completed("已保存歌曲顺序") })
        if(sheet == "queue") QueueSheet(library,playback,model,onDismiss={sheet=null},onFeedback={completed(it)})
        if(addSong != null) PlaylistPicker(addSong!!,library.playlists,onDismiss={addSong=null},onAdd={ list ->
            val song=addSong
            val target=model.repository.state.value.playlists.find {it.id == list.id}
            if(song != null && target != null && model.repository.state.value.songs.any {it.id == song.id}) {
                if(song.id !in target.songIds) {model.repository.playlist(target.copy(songIds=target.songIds+song.id));completed("已添加到「${target.name}」")}
                else message("这首歌已在「${target.name}」中")
            } else message("歌曲或歌单已变化，请重新选择")
            addSong=null
        },onCreate={ name ->
            val song=addSong
            if(song != null && model.repository.state.value.songs.any {it.id == song.id}) {
                val priorIds=model.repository.state.value.playlists.map {it.id}.toSet()
                val list=model.repository.createPlaylist(name)
                if(song.id !in list.songIds) {
                    model.repository.playlist(list.copy(songIds=list.songIds+song.id))
                    completed(if(list.id in priorIds) "已添加到「${list.name}」" else "已创建「${list.name}」并添加歌曲")
                } else message("这首歌已在「${list.name}」中")
            } else message("歌曲已变化，请重新选择")
            addSong=null
        })
        if(info != null) SongInfo(info!!) {info=null}
        if(create || rename != null) NameDialog(if(rename != null) "重命名歌单" else "新建歌单",rename?.name.orEmpty(),onDismiss={create=false;rename=null}) { name ->
            if(rename != null && name == "我喜欢") message("“我喜欢”是默认歌单，请使用其他名称")
            else {
                if(rename != null) {
                    model.repository.state.value.playlists.find {it.id == rename!!.id}?.let {model.repository.playlist(it.copy(name=name));completed("已重命名为「$name」")}
                } else {model.repository.createPlaylist(name);completed(if(name == "我喜欢") "「我喜欢」已在歌单中" else "已创建「$name」")}
                create=false;rename=null
            }
        }
        if(sheet == "delete-playlist" && playlist != null && !playlist.isFavorites) AlertDialog(onDismissRequest={sheet=null},title={Text("删除「${playlist.name}」？")},text={Text("只删除歌单，保留原音乐文件。")},confirmButton={TextButton(onClick={
            val deleted=model.repository.deletePlaylist(playlist.id);selectHome(1)
            if(deleted != null) {haptic();completed("已删除「${deleted.playlist.name}」",undo={model.repository.restorePlaylist(deleted)})}
        }){Text("删除")}},dismissButton={TextButton(onClick={sheet=null}){Text("取消")}})
        if(restoreUri != null) AlertDialog(onDismissRequest={restoreUri=null},title={Text("恢复备份？")},text={Text("这会替换当前音乐索引、歌单和设置。原音乐文件不会改变；恢复后需要重新授权文件夹。")},confirmButton={TextButton(onClick={clearFeedback();model.restoreBackup(restoreUri!!,::message);restoreUri=null}){Text("恢复")}},dismissButton={TextButton(onClick={restoreUri=null}){Text("取消")}})
        if(removeFolder != null) AlertDialog(onDismissRequest={removeFolder=null},title={Text("移除文件夹？")},text={Text("从音乐库和歌单移除此来源的歌曲，原文件保留。其他来源仍在的歌曲会保留。")},confirmButton={TextButton(onClick={
            val target=removeFolder!!;val task=model.repository.removeFolder(target);removeFolder=null
            scope.launch {task.join();if(!task.isCancelled && model.repository.state.value.folders.none {it.uri == target.uri}) {haptic();completed("已移除文件夹")}
                else message("文件夹未能移除，请重试")}
        }){Text("移除")}},dismissButton={TextButton(onClick={removeFolder=null}){Text("取消")}})
    }
}

@Composable internal fun Cover(song: Song?, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha=.08f)),contentAlignment=Alignment.Center) {
        val pixels=with(androidx.compose.ui.platform.LocalDensity.current) {maxWidth.coerceAtMost(400.dp).roundToPx()}.coerceIn(64,1400)
        val image = rememberImage(song?.artwork.orEmpty(),pixels)
        if(image != null) Image(image,null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
        else Icon(Icons.Outlined.LocalFlorist,null,Modifier.fillMaxSize(.45f),tint=MaterialTheme.colorScheme.primary.copy(alpha=.8f))
    }
}
@Composable internal fun rememberImage(path: String,targetPixels: Int=1600): androidx.compose.ui.graphics.ImageBitmap? {
    val stamp=if(path.isNotBlank()) File(path).lastModified() else 0L
    return produceState<androidx.compose.ui.graphics.ImageBitmap?>(null,path,stamp,targetPixels) {
        value=withContext(Dispatchers.IO) { runCatching {
            if(path.isBlank()) return@runCatching null
            val info=BitmapFactory.Options().apply { inJustDecodeBounds=true }
            BitmapFactory.decodeFile(path,info)
            val options=BitmapFactory.Options().apply {inSampleSize=1;while(info.outWidth/inSampleSize>targetPixels || info.outHeight/inSampleSize>targetPixels*1.5f) inSampleSize*=2}
            BitmapFactory.decodeFile(path,options)?.asImageBitmap()
        }.getOrNull() }
    }.value
}
@Composable private fun LibrarySearchHeader(title: String,query: String,expanded: Boolean,onQuery:(String)->Unit,onExpand:()->Unit,onCollapse:()->Unit,modifier: Modifier=Modifier) {
    val focus=remember {FocusRequester()}
    val focusManager=LocalFocusManager.current
    val keyboard=LocalSoftwareKeyboardController.current
    val titleAlpha by animateFloatAsState(if(expanded) 0f else 1f,tween(180),label="search-title")
    fun close() {keyboard?.hide();focusManager.clearFocus();onCollapse()}
    LaunchedEffect(expanded) {if(expanded) focus.requestFocus()}
    BoxWithConstraints(modifier.height(56.dp)) {
        val searchWidth by animateDpAsState(if(expanded) maxWidth else 48.dp,tween(240),label="search-width")
        Text(title,Modifier.align(Alignment.CenterStart).padding(end=52.dp).graphicsLayer {alpha=titleAlpha},fontSize=27.sp,fontWeight=FontWeight.SemiBold,maxLines=1,overflow=TextOverflow.Ellipsis)
        Box(Modifier.align(Alignment.CenterEnd).width(searchWidth).fillMaxHeight().clip(RoundedCornerShape(16.dp))) {
            AnimatedVisibility(expanded,enter=fadeIn(tween(120,delayMillis=60)),exit=fadeOut(tween(100))) {
                OutlinedTextField(query,onQuery,Modifier.testTag("library-search-field").fillMaxSize().focusRequester(focus),singleLine=true,
                    textStyle=TextStyle(fontSize=15.sp),placeholder={Text("歌曲、歌手或专辑",fontSize=13.sp,maxLines=1,overflow=TextOverflow.Ellipsis)},
                    trailingIcon={ExpressiveIconButton(onClick=::close,modifier=Modifier.testTag("close-library-search")) {Icon(Icons.Outlined.Close,"关闭搜索")}},
                    shape=RoundedCornerShape(16.dp),keyboardOptions=KeyboardOptions(imeAction=ImeAction.Search),keyboardActions=KeyboardActions(onSearch={keyboard?.hide();focusManager.clearFocus()}))
            }
            if(!expanded) ExpressiveIconButton(onClick=onExpand,modifier=Modifier.testTag("open-library-search").align(Alignment.CenterEnd)) {Icon(Icons.Outlined.Search,"搜索音乐")}
        }
    }
}
@Composable private fun SongRow(song: Song,index: Int,selected: Boolean,onClick:()->Unit,onAdd:()->Unit,onInfo:()->Unit,modifier: Modifier=Modifier,onRemove:(()->Unit)?=null,favorite: Boolean=false,onFavorite:(()->Unit)?=null) {
    var menu by remember { mutableStateOf(false) }
    val source=remember {MutableInteractionSource()}
    val background by animateColorAsState(if(selected) MaterialTheme.colorScheme.primary.copy(alpha=.055f) else Color.Transparent,tween(160),label="current-song-surface")
    Row(modifier.testTag("song-${song.id}").semantics {this.selected=selected}.fillMaxWidth().pressScale(source,.99f).background(background)
        .clickable(enabled=song.available,interactionSource=source,indication=LocalIndication.current,onClick=onClick).padding(start=16.dp,end=4.dp,top=8.dp,bottom=8.dp),verticalAlignment=Alignment.CenterVertically) {
        Text(index.toString(),Modifier.width(28.dp),fontSize=12.sp,textAlign=TextAlign.Center,color=if(selected) MaterialTheme.colorScheme.primary else muted)
        Cover(song,Modifier.size(44.dp))
        Column(Modifier.weight(1f).padding(horizontal=12.dp)) {
            Text(song.title,maxLines=1,overflow=TextOverflow.Ellipsis,fontSize=15.sp,color=if(selected) MaterialTheme.colorScheme.primary else ink,fontWeight=if(selected) FontWeight.SemiBold else FontWeight.Normal)
            Text(if(song.available) "${song.artist} · ${song.format}" else "文件待重新授权",maxLines=1,overflow=TextOverflow.Ellipsis,fontSize=11.sp,color=muted,modifier=Modifier.padding(top=4.dp))
        }
        Text(timeLabel(song.duration),fontSize=11.sp,color=muted)
        if(onFavorite != null) FavoriteButton(song,favorite,onFavorite)
        Box { ExpressiveIconButton(onClick={menu=true}) { Icon(Icons.Outlined.MoreVert,"歌曲操作") }
            DropdownMenu(menu,{menu=false}) {
                DropdownMenuItem(text={Text("添加到歌单")},onClick={menu=false;onAdd()})
                DropdownMenuItem(text={Text("歌曲信息")},onClick={menu=false;onInfo()})
                if(onRemove != null) DropdownMenuItem(text={Text("从歌单移除")},onClick={menu=false;onRemove()})
            }
        }
    }
}
@Composable private fun FavoriteButton(song: Song,favorite: Boolean,onFavorite: ()->Unit) {
    val pulse=remember {Animatable(1f)}
    var initial by remember {mutableStateOf(true)}
    LaunchedEffect(favorite) {
        if(initial) initial=false else {
            pulse.animateTo(.84f,tween(65))
            pulse.animateTo(1f,spring(dampingRatio=.55f,stiffness=700f))
        }
    }
    val color by animateColorAsState(if(favorite) accents[0] else Color.White,tween(130),label="favorite-color")
    ExpressiveIconButton(onClick=onFavorite,tactile=true,modifier=Modifier.testTag("favorite-${song.id}").semantics {selected=favorite}) {
        Box(Modifier.graphicsLayer {scaleX=pulse.value;scaleY=pulse.value}) {
            Icon(Icons.Filled.Favorite,if(favorite) "取消喜欢：${song.title}" else "喜欢：${song.title}",Modifier.size(20.dp),tint=color)
            if(!favorite) Icon(Icons.Outlined.FavoriteBorder,null,Modifier.size(20.dp),tint=muted.copy(alpha=.6f))
        }
    }
}
@Composable internal fun SongList(songs: List<Song>,total: Int,currentId: String?,modifier: Modifier = Modifier,
    onPlay:(Song)->Unit,onAdd:(Song)->Unit,onInfo:(Song)->Unit,onRemove:((Song)->Unit)?=null,favoriteIds: Set<String> = emptySet(),onFavorite:((Song)->Unit)?=null) {
    val list=rememberLazyListState()
    val scope=rememberCoroutineScope()
    val density=LocalDensity.current
    val haptic=rememberActionHaptic()
    val currentIndex=remember(songs,currentId) {songs.indexOfFirst {it.id == currentId}}
    var locatorVisible by remember {mutableStateOf(false)}
    var locating by remember {mutableStateOf(false)}
    LaunchedEffect(list) {
        snapshotFlow {list.isScrollInProgress to locating}.collectLatest {(scrolling,jumping) ->
            if(scrolling && !jumping) locatorVisible=true
            else if(!scrolling) {delay(2000);locatorVisible=false}
        }
    }
    Box(modifier.fillMaxWidth()) {
        LazyColumn(Modifier.testTag("song-list").fillMaxSize(),state=list,contentPadding=PaddingValues(top=12.dp,bottom=16.dp)) {
            itemsIndexed(songs,key={_,song->song.id}) {index,song ->
                SongRow(song,index+1,song.id == currentId,{onPlay(song)},{onAdd(song)},{onInfo(song)},
                    modifier=Modifier.animateItem(placementSpec=spring(dampingRatio=.9f,stiffness=450f)),onRemove=onRemove?.let {remove -> {remove(song)}},
                    favorite=song.id in favoriteIds,onFavorite=onFavorite?.let {like -> {like(song)}})
            }
            item {Text("$total 首歌曲 · 音乐保存在本机",Modifier.fillMaxWidth().padding(20.dp),textAlign=TextAlign.Center,fontSize=11.sp,color=muted)}
        }
        AnimatedVisibility(locatorVisible && currentIndex >= 0,modifier=Modifier.align(Alignment.BottomEnd).padding(end=80.dp,bottom=48.dp),
            enter=fadeIn(tween(160))+scaleIn(tween(180),initialScale=.85f),exit=fadeOut(tween(160))+scaleOut(tween(160),targetScale=.9f)) {
            SmallFloatingActionButton(onClick={
                haptic()
                locatorVisible=false
                if(currentIndex >= 0) scope.launch {
                    locating=true
                    try {
                        val rowHeight=list.layoutInfo.visibleItemsInfo.firstOrNull()?.size ?: with(density) {64.dp.roundToPx()}
                        val viewport=list.layoutInfo.viewportEndOffset-list.layoutInfo.viewportStartOffset
                        list.animateScrollToItem(currentIndex,-((viewport-rowHeight)/2).coerceAtLeast(0))
                    } finally {locating=false}
                }
            },modifier=Modifier.testTag("locate-current-song"),shape=CircleShape,containerColor=Color.White,contentColor=MaterialTheme.colorScheme.primary) {
                Icon(Icons.Outlined.MyLocation,"定位当前播放歌曲",Modifier.size(22.dp))
            }
        }
    }
}
@Composable private fun GroupList(songs: List<Song>,tab: Int,onSelect:(String)->Unit) {
    val groups=songs.groupBy { if(tab == 1) "${it.artist}\u0000${it.album}" else it.artist }.toSortedMap()
    LazyColumn(contentPadding=PaddingValues(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        items(groups.keys.toList(),key={it}) { key -> val tracks=groups.getValue(key)
            val source=remember {MutableInteractionSource()}
            Row(Modifier.animateItem().fillMaxWidth().pressScale(source,.985f).clickable(interactionSource=source,indication=LocalIndication.current) {onSelect(key)},verticalAlignment=Alignment.CenterVertically) {
                Cover(tracks.first(),Modifier.size(60.dp))
                Column(Modifier.padding(horizontal=16.dp).weight(1f)) { Text(if(tab == 1) tracks.first().album else key,maxLines=1,overflow=TextOverflow.Ellipsis,fontSize=17.sp); Text("${tracks.size} 首${if(tab == 1) " · ${tracks.first().artist}" else ""}",fontSize=12.sp,color=muted,modifier=Modifier.padding(top=6.dp)) }
                Icon(Icons.Outlined.ChevronRight,null)
            }
        }
    }
}
@Composable private fun EmptyLibrary(search: Boolean,playlist: Boolean,onImport:()->Unit) {
    Column(Modifier.fillMaxWidth().padding(40.dp),horizontalAlignment=Alignment.CenterHorizontally) {
        Icon(if(search) Icons.Outlined.SearchOff else Icons.Outlined.LocalFlorist,null,Modifier.size(60.dp),tint=MaterialTheme.colorScheme.primary.copy(alpha=.7f))
        Text(if(search) "没有找到匹配的歌曲" else if(playlist) "歌单还没有歌曲" else "让音乐，在这里开花",Modifier.padding(top=24.dp),fontSize=19.sp)
        Text(if(playlist) "在音乐库的歌曲菜单中添加到此歌单" else "从本机文件或文件夹导入",Modifier.padding(vertical=12.dp),fontSize=12.sp,color=muted,textAlign=TextAlign.Center)
        if(!playlist && !search) Button(onClick=onImport) {Text("导入音乐")}
    }
}
@Composable private fun MiniPlayer(song: Song,p: PlaybackState,model: MusicModel,onOpen:()->Unit) {
    val source=remember {MutableInteractionSource()}
    Surface(color=Color.White,shadowElevation=1.dp) { Row(Modifier.testTag("mini-player").fillMaxWidth().pressScale(source,.99f).clickable(interactionSource=source,indication=LocalIndication.current,onClick=onOpen).padding(start=16.dp,top=8.dp,bottom=8.dp),verticalAlignment=Alignment.CenterVertically) {
        Crossfade(song,animationSpec=tween(180),label="mini-artwork") {Cover(it,Modifier.size(40.dp))}
        Column(Modifier.weight(1f).padding(horizontal=12.dp)) { Text(song.title,maxLines=1,overflow=TextOverflow.Ellipsis,fontSize=14.sp); Text(song.artist,maxLines=1,overflow=TextOverflow.Ellipsis,fontSize=11.sp,color=muted) }
        ExpressiveIconButton(onClick={model.previous()},enabled=p.connected,tactile=true) {Icon(Icons.Outlined.SkipPrevious,"上一首")}
        ExpressiveIconButton(onClick={model.toggle()},enabled=p.connected,tactile=true) {
            Crossfade(p.playing,animationSpec=tween(120),label="mini-play-state") {playing -> Icon(if(playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,if(playing) "暂停" else "播放")}
        }
        ExpressiveIconButton(onClick={model.next()},enabled=p.connected,tactile=true) { Icon(Icons.Outlined.SkipNext,"下一首") }
    } }
}
