package com.xiaohonghua.music

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class LibraryState(
    val songs: List<Song> = emptyList(), val playlists: List<Playlist> = emptyList(), val folders: List<Folder> = emptyList(),
    val settings: Settings = Settings(), val sort: SortMode = SortMode.TITLE, val custom: List<String> = emptyList(),
    val random: List<String> = emptyList(), val manualLyrics: Map<String,String> = emptyMap(),
    val busy: Boolean = false, val status: String = "", val events: List<String> = emptyList(), val showScanProgress: Boolean = false,
)

class MusicRepository private constructor(private val context: Context) {
    val store = LibraryStore(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val scanMutex = Mutex()
    private val mutable = MutableStateFlow(LibraryState())
    val state = mutable.asStateFlow()
    private var foreground = false
    private var playing = false
    private var monitorJob: Job? = null
    private var observationJob: Job? = null
    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            if (state.value.settings.monitor && (foreground || playing)) {
                observationJob?.cancel()
                observationJob = scope.launch { delay(600); refresh(true) }
            }
        }
    }
    init {
        scope.launch {
            mutable.value = LibraryState(store.songs(),store.playlists(),store.folders(),store.settings(),
                runCatching { SortMode.valueOf(store.prefs.getString("sort","TITLE")!!) }.getOrDefault(SortMode.TITLE),
                store.array("custom").strings(),store.array("random").strings(),store.manualLyrics())
            observeFolders()
            updateMonitoring()
            if(foreground) requestRefresh(false,showProgress=false)
        }
    }
    fun settings(value: Settings) { store.saveSettings(value); mutable.update { it.copy(settings=value) }; updateMonitoring() }
    fun foreground(value: Boolean) { foreground=value; updateMonitoring(); if (value) requestRefresh(false,showProgress=false) }
    fun playing(value: Boolean) { playing=value; updateMonitoring() }
    private fun updateMonitoring() {
        if (state.value.settings.monitor && (foreground || playing)) {
            if (monitorJob?.isActive != true) monitorJob = scope.launch {
                var lastFull = 0L
                while (isActive) {
                    delay(5000)
                    val now = android.os.SystemClock.elapsedRealtime()
                    val force = now-lastFull >= 60_000
                    refresh(force)
                    if(force) lastFull=now
                }
            }
        } else { monitorJob?.cancel(); monitorJob=null }
    }
    private fun observeFolders() {
        context.contentResolver.unregisterContentObserver(observer)
        state.value.folders.forEach { folder -> runCatching { context.contentResolver.registerContentObserver(Uri.parse(folder.uri),true,observer) } }
    }
    fun addFolder(uri: Uri, name: String) {
        val folders = (state.value.folders.filter { it.uri != uri.toString() } + Folder(uri.toString(),name))
        store.saveFolders(folders); mutable.update { it.copy(folders=folders) }; observeFolders(); requestRefresh(true)
    }
    fun removeFolder(folder: Folder) = scope.launch { scanMutex.withLock {
        val folders = state.value.folders.filter { it.uri != folder.uri }
        val songs = reconcileSongs(state.value.songs,emptyList(),folder.uri)
        store.saveFolders(folders); store.replaceSongs(songs)
        mutable.update { it.copy(folders=folders) }; publishSongs(songs,"已移除文件夹索引，原文件保留")
        observeFolders()
    } }
    fun importFiles(uris: List<Uri>) = scope.launch { scanMutex.withLock {
        mutable.update { it.copy(busy=true,showScanProgress=true,status="正在读取音乐标签…") }
        try {
            val scanner = LibraryScanner(context)
            var songs = state.value.songs
            var added = 0
            val errors = mutableListOf<String>()
            uris.forEach { uri ->
                try {
                    val fresh = relink(listOf(scanner.single(uri)),songs).single()
                    val old = songs.find { it.id == fresh.id }
                    songs = songs.filter { it.id != fresh.id } + fresh.copy(sources=(old?.sources ?: emptySet())+"files")
                    if(old == null) added++
                } catch (e: Exception) { errors += e.message ?: "文件无法读取" }
            }
            store.replaceSongs(songs)
            publishSongs(songs,"新增 $added 首，读取失败 ${errors.size} 首",errors)
        } finally { mutable.update { it.copy(busy=false,showScanProgress=false) } }
    } }
    fun requestRefresh(force: Boolean = true,showProgress: Boolean = true) { scope.launch { refresh(force,showProgress) } }
    private suspend fun refresh(force: Boolean,showProgress: Boolean = false) {
        if (scanMutex.isLocked && !force) return
        scanMutex.withLock {
            if (state.value.folders.isEmpty()) return
            mutable.update { it.copy(busy=true,showScanProgress=showProgress,status=if(force) "正在刷新文件与全部标签…" else "正在检查文件夹…") }
            val errors = mutableListOf<String>()
            val failedSources = mutableSetOf<String>()
            val before = state.value.songs
            var songs = before
            try {
                val scanner = LibraryScanner(context)
                state.value.folders.forEach { folder ->
                    try {
                        val result = scanner.folder(Uri.parse(folder.uri),songs,force)
                        songs = reconcileSongs(songs,relink(result.songs,songs),folder.uri)
                        errors += result.errors
                    } catch (e: Exception) { failedSources += folder.uri; errors += "${folder.name}：${e.message ?: "请重新授权"}" }
                }
                songs = songs.map {song -> if(song.sources.isNotEmpty() && song.sources.all {it in failedSources}) song.copy(available=false) else song}
                val oldIds = before.map { it.id }.toSet()
                val newIds = songs.map { it.id }.toSet()
                val oldById = before.associateBy { it.id }
                val changed = songs.count { song -> oldById[song.id]?.let { it != song } == true }
                store.replaceSongs(songs)
                val summary = "新增 ${(newIds-oldIds).size} · 移除 ${(oldIds-newIds).size} · 更新 $changed"
                publishSongs(songs,summary,errors)
            } finally { mutable.update { it.copy(busy=false,showScanProgress=false) } }
        }
    }
    private fun relink(fresh: List<Song>, previous: List<Song>): List<Song> {
        val unavailable = previous.filter { !it.available }.groupBy { it.migrationKey }
        val freshKeys = fresh.groupBy { it.migrationKey }
        val known = previous.filter { it.available }.map { it.id }.toSet()
        return fresh.map { song ->
            val candidate = unavailable[song.migrationKey]?.singleOrNull()
            if (song.id !in known && candidate != null && freshKeys[song.migrationKey]?.size == 1) song.copy(id=candidate.id) else song
        }
    }
    private fun publishSongs(songs: List<Song>, status: String, errors: List<String> = emptyList()) {
        val known = songs.map { it.id }.toSet()
        mutable.update { old -> old.copy(songs=songs,status=status,events=(errors.take(8)+listOf(status)+old.events).take(20),
            playlists=old.playlists.map { it.copy(songIds=it.songIds.filter(known::contains),randomIds=it.randomIds.filter(known::contains)) },
            custom=old.custom.filter(known::contains)+songs.filter { it.id !in old.custom }.map { it.id },
            random=old.random.filter(known::contains)+songs.filter { it.id !in old.random }.shuffled().map { it.id }) }
        store.savePlaylists(state.value.playlists)
        saveOrder()
    }
    fun playlist(value: Playlist) {
        mutable.update { it.copy(playlists=it.playlists.filter { p -> p.id != value.id }+value) }
        store.savePlaylists(state.value.playlists)
    }
    fun createPlaylist(name: String): Playlist {
        val preset=(0 until PLAYLIST_COVER_COUNT).minBy {index -> state.value.playlists.count {it.coverPreset == index}}
        return Playlist(UUID.randomUUID().toString(),name.trim(),coverPreset=preset).also {playlist(it)}
    }
    fun playlistCover(id: String, preset: Int, photo: String = ""): Boolean {
        if(state.value.playlists.none {it.id == id}) return false
        mutable.update {s -> s.copy(playlists=s.playlists.map {if(it.id == id) it.copy(coverPreset=preset.coerceIn(0,PLAYLIST_COVER_COUNT-1),coverPhoto=photo) else it})}
        store.savePlaylists(state.value.playlists)
        return true
    }
    fun deletePlaylist(id: String) { mutable.update { it.copy(playlists=it.playlists.filter { p -> p.id != id }) }; store.savePlaylists(state.value.playlists) }
    fun sort(mode: SortMode, playlistId: String? = null) {
        if(playlistId != null) state.value.playlists.find { it.id == playlistId }?.let { playlist(it.copy(sort=mode,randomIds=if(mode == SortMode.RANDOM) it.songIds.shuffled() else it.randomIds)) }
        else { mutable.update { it.copy(sort=mode,random=if(mode == SortMode.RANDOM) it.songs.shuffled().map(Song::id) else it.random) }; saveOrder() }
    }
    fun customOrder(ids: List<String>, playlistId: String? = null) {
        if(playlistId != null) state.value.playlists.find { it.id == playlistId }?.let { playlist(it.copy(songIds=ids,sort=SortMode.CUSTOM)) }
        else { mutable.update { it.copy(custom=ids,sort=SortMode.CUSTOM) }; saveOrder() }
    }
    private fun saveOrder() { state.value.let { store.prefs.edit().putString("sort",it.sort.name).putString("custom",JSONArray(it.custom).toString()).putString("random",JSONArray(it.random).toString()).apply() } }
    fun lyrics(id: String, text: String?) {
        mutable.update { it.copy(manualLyrics=if(text == null) it.manualLyrics-id else it.manualLyrics+(id to text)) }
        store.saveManualLyrics(state.value.manualLyrics)
    }
    fun backup(): String = state.value.let { s -> JSONObject().apply {
        put("kind","xiaohonghua-android"); put("version",1); put("songs",JSONArray(s.songs.map { it.toJson(true) }))
        put("playlists",JSONArray(s.playlists.map { it.toJson(true) })); put("folders",JSONArray(s.folders.map { JSONObject().put("uri",it.uri).put("name",it.name) }))
        put("settings",s.settings.toJson().put("background",if(s.settings.background == "artwork") "artwork" else "plain"))
        put("manualLyrics",JSONObject(s.manualLyrics)); put("sort",s.sort.name); put("custom",JSONArray(s.custom)); put("random",JSONArray(s.random))
    }.toString(2) }
    suspend fun restore(text: String) = withContext(Dispatchers.IO) { scanMutex.withLock {
        require(text.toByteArray().size <= 64*1024*1024) { "备份文件过大" }
        val data = JSONObject(text)
        require(data.optString("kind") == "xiaohonghua-android" && data.optInt("version") == 1) { "不是支持的小红花安卓备份" }
        val array = data.getJSONArray("songs")
        require(array.length() <= 30_000)
        val songs = (0 until array.length()).map { songFromJson(array.getJSONObject(it)).copy(available=false,artwork="") }
        require(songs.all { Uri.parse(it.uri).scheme == "content" && it.id.length in 1..128 }) { "备份中的音乐地址无效" }
        require(songs.map { it.id }.distinct().size == songs.size) { "备份中的歌曲编号重复" }
        val known=songs.map {it.id}.toSet()
        val playlists = data.getJSONArray("playlists").let { a ->
            require(a.length() <= 5000)
            (0 until a.length()).map { playlistFromJson(a.getJSONObject(it)).let { p ->
                require(p.id.length in 1..128 && p.name.length in 1..60) { "歌单数据无效" }
                p.copy(songIds=p.songIds.filter(known::contains).distinct(),randomIds=p.randomIds.filter(known::contains).distinct(),coverPhoto="")
            } }
        }
        require(playlists.map {it.id}.distinct().size == playlists.size) { "歌单编号重复" }
        val folders = data.getJSONArray("folders").let { a -> (0 until a.length()).map { a.getJSONObject(it).let { Folder(it.getString("uri"),it.getString("name")) } } }
        require(folders.all { Uri.parse(it.uri).scheme == "content" })
        val settings = settingsFromJson(data.getJSONObject("settings")).let { it.copy(background=if(it.background == "artwork") "artwork" else "plain") }
        val lyrics = data.getJSONObject("manualLyrics").let { obj -> obj.keys().asSequence().associateWith { obj.getString(it) } }
        val sort = SortMode.valueOf(data.getString("sort"))
        val custom = data.getJSONArray("custom").strings().filter(known::contains).distinct()
        val random = data.getJSONArray("random").strings().filter(known::contains).distinct()
        // Parse and validate everything before replacing any current data.
        store.replaceSongs(songs); store.savePlaylists(playlists); store.saveFolders(folders); store.saveSettings(settings); store.saveManualLyrics(lyrics)
        mutable.value = LibraryState(songs,playlists,folders,settings,sort,custom,random,lyrics,status="备份已恢复，请重新授权音乐文件夹")
        saveOrder(); observeFolders(); updateMonitoring()
    } }
    companion object {
        @Volatile private var instance: MusicRepository? = null
        fun get(context: Context): MusicRepository = instance ?: synchronized(this) { instance ?: MusicRepository(context.applicationContext).also { instance=it } }
    }
}
