package com.xiaohonghua.music

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class LibraryState(
    val songs: List<Song> = emptyList(), val playlists: List<Playlist> = normalizeFavorites(emptyList()), val folders: List<Folder> = emptyList(),
    val settings: Settings = Settings(), val sort: SortMode = SortMode.TITLE, val custom: List<String> = emptyList(),
    val random: List<String> = emptyList(), val manualLyrics: Map<String,String> = emptyMap(),
    val busy: Boolean = false, val status: String = "", val events: List<String> = emptyList(), val showScanProgress: Boolean = false, val ready: Boolean = false,
    val scanSucceeded: Boolean = false,
)

class MusicRepository private constructor(private val context: Context) {
    val store = LibraryStore(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val scanMutex = Mutex()
    private val persistenceMutex = Mutex()
    private val initialized = CompletableDeferred<Unit>()
    private val persistenceRequests = Channel<Unit>(Channel.CONFLATED)
    private var persistedMetadata: LibraryState? = null
    private val mutable = MutableStateFlow(LibraryState())
    val state = mutable.asStateFlow()
    init {
        scope.launch { try { scanMutex.withLock { persistenceMutex.withLock {
            val songs=store.songs()
            val known=songs.map {it.id}.toSet()
            val playlists=normalizeFavorites(store.playlists().map {playlist ->
                val members=playlist.songIds.filter(known::contains).distinct()
                playlist.copy(songIds=members,randomIds=mergeSongOrder(playlist.randomIds,members))
            },known)
            mutable.value = LibraryState(songs,playlists,store.folders(),store.settings(),
                runCatching { SortMode.valueOf(store.prefs.getString("sort","TITLE")!!) }.getOrDefault(SortMode.TITLE),
                mergeSongOrder(store.array("custom").strings(),songs.map {it.id}),
                mergeSongOrder(store.array("random").strings(),songs.map {it.id}),store.manualLyrics().filterKeys(known::contains),ready=true)
            writeMetadata(state.value)
        } }; initialized.complete(Unit)
        } catch(e: Exception) {
            initialized.completeExceptionally(e)
            if(e is CancellationException) throw e
            mutable.update {it.copy(status="音乐库读取失败：${e.message ?: "请重新打开应用"}")}
        } }
        scope.launch {
            runCatching {initialized.await()}.getOrElse {return@launch}
            for(request in persistenceRequests) {
                try {persistenceMutex.withLock {writeMetadata(state.value)}}
                catch(e: CancellationException) {throw e}
                catch(e: Exception) {mutable.update {it.copy(status="保存失败：${e.message ?: "请检查存储空间"}")}}
            }
        }
    }
    // UI changes publish immediately; serialization and disk writes share one ordered IO writer.
    private fun persistMetadata() {persistenceRequests.trySend(Unit)}
    private fun writeMetadata(value: LibraryState) {
        val previous=persistedMetadata
        val edit=store.prefs.edit()
        if(previous?.settings != value.settings) edit.putString("settings",value.settings.toJson().toString())
        if(previous?.playlists !== value.playlists) edit.putString("playlists",JSONArray(normalizeFavorites(value.playlists).map {it.toJson()}).toString())
        if(previous?.folders !== value.folders) edit.putString("folders",JSONArray(value.folders.map {JSONObject().put("uri",it.uri).put("name",it.name)}).toString())
        if(previous?.manualLyrics !== value.manualLyrics) edit.putString("manualLyrics",JSONObject(value.manualLyrics).toString())
        if(previous?.sort != value.sort) edit.putString("sort",value.sort.name)
        if(previous?.custom !== value.custom) edit.putString("custom",JSONArray(value.custom).toString())
        if(previous?.random !== value.random) edit.putString("random",JSONArray(value.random).toString())
        edit.apply()
        persistedMetadata=value
    }
    fun settings(value: Settings) { mutable.update { it.copy(settings=value) }; persistMetadata() }
    fun addFolder(uri: Uri, name: String) = scope.launch {
        initialized.await()
        scanMutex.withLock {
            mutable.update {it.copy(folders=it.folders.filter {folder -> folder.uri != uri.toString()}+Folder(uri.toString(),name))}
            persistMetadata()
        }
        refresh(true,uri.toString())
    }
    fun removeFolder(folder: Folder) = scope.launch {
        initialized.await()
        try {scanMutex.withLock {
            val folders = state.value.folders.filter { it.uri != folder.uri }
            val songs = reconcileSongs(state.value.songs,emptyList(),folder.uri)
            val task=currentCoroutineContext()
            store.replaceSongs(songs) {task.ensureActive()}
            mutable.update { it.copy(folders=folders) }; publishSongs(songs,"已移除文件夹索引，原文件保留")
        }} catch(e: CancellationException) {throw e}
        catch(e: Exception) {
            val message="移除文件夹失败：${e.message ?: "请重试"}"
            mutable.update {it.copy(status=message,events=(listOf(message)+it.events).take(20))}
        }
    }
    fun importFiles(uris: List<Uri>) = scope.launch { initialized.await(); scanMutex.withLock {
        mutable.update { it.copy(busy=true,showScanProgress=true,scanSucceeded=false,status="正在读取音乐标签…") }
        try {
            val scanner = LibraryScanner(context)
            val before = state.value.songs
            val scanned = LinkedHashMap<String,Song>()
            var added = 0
            val errors = mutableListOf<String>()
            uris.forEach { uri ->
                ensureActive()
                try {
                    val fresh = scanner.single(uri)
                    scanned[fresh.id] = fresh
                } catch(e: CancellationException) {throw e
                } catch (e: Exception) { errors += e.message ?: "文件无法读取" }
            }
            val merged = before.associateByTo(LinkedHashMap()) {it.id}
            relink(scanned.values.toList(),before).forEach {fresh ->
                val old=merged[fresh.id]
                merged[fresh.id]=fresh.copy(sources=(old?.sources ?: emptySet())+"files")
                if(old == null) added++
            }
            val songs=merged.values.toList()
            val task=currentCoroutineContext()
            store.replaceSongs(songs) {task.ensureActive()}
            publishSongs(songs,"新增 $added 首，读取失败 ${errors.size} 首",errors)
            mutable.update {it.copy(scanSucceeded=true)}
        } catch(e: CancellationException) {throw e
        } catch(e: Exception) {mutable.update {it.copy(status="导入失败：${e.message ?: "请重试"}")}
        } finally { mutable.update { it.copy(busy=false,showScanProgress=false) } }
    } }
    // Every user refresh rereads all tags, even when a provider reports unchanged timestamps.
    fun requestRefresh(showProgress: Boolean = true) { scope.launch { refresh(showProgress) } }
    private suspend fun refresh(showProgress: Boolean, onlyFolderUri: String? = null) {
        initialized.await()
        scanMutex.withLock {
            if(!state.value.ready) return
            mutable.update { it.copy(busy=true,showScanProgress=showProgress,scanSucceeded=false,status="正在刷新文件与全部标签…") }
            val errors = mutableListOf<String>()
            val failedSources = mutableSetOf<String>()
            val before = state.value.songs
            val rescanned=mutableSetOf<String>()
            var songs = before
            try {
                val scanner = LibraryScanner(context)
                val task=currentCoroutineContext()
                state.value.folders.filter {onlyFolderUri == null || it.uri == onlyFolderUri}.forEach { folder ->
                    task.ensureActive()
                    try {
                        val result = scanner.folder(Uri.parse(folder.uri),songs,true) {task.ensureActive()}
                        val fresh=relink(result.songs,songs)
                        rescanned += fresh.map {it.id}
                        songs = reconcileSongs(songs,fresh,folder.uri)
                        errors += result.errors
                    } catch(e: CancellationException) {throw e
                    } catch (e: Exception) { failedSources += folder.uri; errors += "${folder.name}：${e.message ?: "请重新授权"}" }
                }
                if(onlyFolderUri == null) {
                    // Folder scans and standalone imports both receive a complete metadata read.
                    songs=songs.map {song ->
                        task.ensureActive()
                        if("files" !in song.sources || song.id in rescanned) song else try {
                            val fresh=scanner.single(Uri.parse(song.uri)).copy(id=song.id,sources=song.sources)
                            // Failed folder listing cannot prove its external LRC was deleted.
                            if(song.lyricFile.isNotEmpty() && song.sources.any(failedSources::contains))
                                fresh.copy(lyrics=song.lyrics,lyricFile=song.lyricFile,lyricRevision="") else fresh
                        } catch(e: CancellationException) {throw e
                        } catch(e: Exception) {
                            errors += "${song.name}：${e.message ?: "文件无法读取"}"
                            song.copy(available=false)
                        }
                    }
                }
                songs = songs.map {song -> if(song.sources.isNotEmpty() && song.sources.all {it in failedSources}) song.copy(available=false) else song}
                val oldIds = before.map { it.id }.toSet()
                val newIds = songs.map { it.id }.toSet()
                val oldById = before.associateBy { it.id }
                val changed = songs.count { song -> oldById[song.id]?.let { it != song } == true }
                store.replaceSongs(songs) {task.ensureActive()}
                val summary = "新增 ${(newIds-oldIds).size} · 移除 ${(oldIds-newIds).size} · 更新 $changed"
                publishSongs(songs,summary,errors)
                mutable.update {it.copy(scanSucceeded=true)}
            } catch(e: CancellationException) {throw e
            } catch(e: Exception) {mutable.update {it.copy(status="刷新失败：${e.message ?: "请重试"}")}
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
        mutable.update { old ->
            val custom=old.custom.toSet()
            val random=old.random.toSet()
            old.copy(songs=songs,status=status,events=(errors.take(8)+listOf(status)+old.events).take(20),
            playlists=normalizeFavorites(old.playlists.map { it.copy(songIds=it.songIds.filter(known::contains),randomIds=it.randomIds.filter(known::contains)) },known),
            custom=old.custom.filter(known::contains)+songs.filter { it.id !in custom }.map { it.id },
            random=old.random.filter(known::contains)+songs.filter { it.id !in random }.shuffled().map { it.id },
            manualLyrics=old.manualLyrics.filterKeys(known::contains)) }
        persistMetadata()
    }
    fun playlist(value: Playlist) {
        mutable.update { old ->
            val known=old.songs.map {it.id}.toSet()
            val updated=value.copy(name=if(value.isFavorites) "我喜欢" else value.name,
                songIds=value.songIds.filter(known::contains).distinct(),
                randomIds=mergeSongOrder(value.randomIds,value.songIds.filter(known::contains)),description=value.description.trim().take(PLAYLIST_DESCRIPTION_LIMIT),coverPreset=normalizePlaylistCover(value.id,value.coverPreset))
            val playlists=if(old.playlists.any {it.id == updated.id}) old.playlists.map {if(it.id == updated.id) updated else it} else old.playlists+updated
            old.copy(playlists=normalizeFavorites(playlists,known))
        }
        persistMetadata()
    }
    fun toggleFavorite(songId: String) {
        mutable.update { old ->
            if(old.songs.none {it.id == songId}) old else old.copy(playlists=toggleFavorite(old.playlists,songId))
        }
        persistMetadata()
    }
    fun createPlaylist(name: String): Playlist {
        if(name.trim() == "我喜欢") return state.value.playlists.first {it.isFavorites}
        val preset=(8..17).minBy {index -> state.value.playlists.count {it.coverPreset == index}}
        return Playlist(UUID.randomUUID().toString(),name.trim(),coverPreset=preset).also {playlist(it)}
    }
    fun editPlaylist(id: String, name: String, description: String): Boolean {
        val trimmed=name.trim()
        if(id != FAVORITES_PLAYLIST_ID && (trimmed.length !in 1..60 || trimmed == "我喜欢")) return false
        val before=mutable.getAndUpdate {old -> old.copy(playlists=old.playlists.map {playlist ->
            if(playlist.id == id) playlist.copy(name=if(playlist.isFavorites) "我喜欢" else trimmed,
                description=description.trim().take(PLAYLIST_DESCRIPTION_LIMIT)) else playlist
        })}
        if(before.playlists.none {it.id == id}) return false
        persistMetadata()
        return true
    }
    fun addPlaylistSongs(id: String, ids: List<String>): Int {
        val before=mutable.getAndUpdate {old -> old.copy(playlists=old.playlists.map {playlist ->
            if(playlist.id == id) addPlaylistMembers(playlist,ids,old.songs.map {it.id}.toSet()) else playlist
        })}
        val previous=before.playlists.find {it.id == id} ?: return 0
        val updated=addPlaylistMembers(previous,ids,before.songs.map {it.id}.toSet())
        if(updated != previous) persistMetadata()
        return (updated.songIds.toSet()-previous.songIds.toSet()).size
    }
    fun removePlaylistSongs(id: String, ids: List<String>): RemovedPlaylistSongs? {
        val selected=ids.toSet()
        val before=mutable.getAndUpdate {old -> old.copy(playlists=old.playlists.map {playlist ->
            if(playlist.id == id) removePlaylistMembers(playlist,selected) else playlist
        })}
        val previous=before.playlists.find {it.id == id} ?: return null
        val removed=previous.songIds.filter(selected::contains).distinct()
        if(removed.isEmpty()) return null
        persistMetadata()
        return RemovedPlaylistSongs(previous,removed)
    }
    fun restorePlaylistSongs(removed: RemovedPlaylistSongs): Boolean {
        val before=mutable.getAndUpdate {old -> old.copy(playlists=old.playlists.map {playlist ->
            if(playlist.id == removed.playlist.id) restoreRemovedPlaylistSongs(playlist,removed,old.songs.map {it.id}.toSet()) else playlist
        })}
        val playlist=before.playlists.find {it.id == removed.playlist.id} ?: return false
        val changed=restoreRemovedPlaylistSongs(playlist,removed,before.songs.map {it.id}.toSet()) != playlist
        if(changed) persistMetadata()
        return changed
    }
    fun playlistCover(id: String, preset: Int, photo: String = ""): Boolean {
        if(state.value.playlists.none {it.id == id}) return false
        mutable.update {s -> s.copy(playlists=s.playlists.map {if(it.id == id) it.copy(coverPreset=normalizePlaylistCover(id,preset),coverPhoto=photo) else it})}
        persistMetadata()
        return true
    }
    fun deletePlaylist(id: String): DeletedPlaylist? {
        if(id == FAVORITES_PLAYLIST_ID) return null
        val before=mutable.getAndUpdate {it.copy(playlists=it.playlists.filter {p -> p.id != id})}
        val index=before.playlists.indexOfFirst {it.id == id}
        if(index < 0) return null
        persistMetadata()
        return DeletedPlaylist(before.playlists[index],index)
    }
    fun restorePlaylist(deleted: DeletedPlaylist): Boolean {
        val before=mutable.getAndUpdate {old -> old.copy(playlists=restoreDeletedPlaylist(old.playlists,deleted,old.songs.map {it.id}.toSet()))}
        val changed=restoreDeletedPlaylist(before.playlists,deleted,before.songs.map {it.id}.toSet()) != before.playlists
        if(changed) persistMetadata()
        return changed
    }
    fun removePlaylistSong(playlistId: String, songId: String): RemovedPlaylistSong? {
        val before=mutable.getAndUpdate {old -> old.copy(playlists=old.playlists.map {playlist ->
            if(playlist.id == playlistId && songId in playlist.songIds) playlist.copy(songIds=playlist.songIds-songId,randomIds=playlist.randomIds-songId) else playlist
        })}
        val playlist=before.playlists.find {it.id == playlistId && songId in it.songIds} ?: return null
        persistMetadata()
        return RemovedPlaylistSong(playlist,songId)
    }
    fun restorePlaylistSong(removed: RemovedPlaylistSong): Boolean {
        val before=mutable.getAndUpdate {old -> old.copy(playlists=old.playlists.map {playlist ->
            if(playlist.id == removed.playlist.id) restoreRemovedPlaylistSong(playlist,removed,old.songs.map {it.id}.toSet()) else playlist
        })}
        val playlist=before.playlists.find {it.id == removed.playlist.id} ?: return false
        val changed=restoreRemovedPlaylistSong(playlist,removed,before.songs.map {it.id}.toSet()) != playlist
        if(changed) persistMetadata()
        return changed
    }
    fun sort(mode: SortMode, playlistId: String? = null): Boolean {
        val before=mutable.getAndUpdate {old ->
            if(playlistId != null) old.copy(playlists=old.playlists.map {playlist ->
                if(playlist.id == playlistId) playlist.copy(sort=mode,
                    randomIds=if(mode == SortMode.RANDOM) playlist.songIds.distinct().shuffled() else playlist.randomIds) else playlist
            }) else old.copy(sort=mode,random=if(mode == SortMode.RANDOM) old.songs.shuffled().map(Song::id) else old.random)
        }
        if(playlistId != null) {
            if(before.playlists.none {it.id == playlistId}) return false
            persistMetadata()
        } else saveOrder()
        return true
    }
    fun customOrder(ids: List<String>, playlistId: String? = null): Boolean {
        val before=mutable.getAndUpdate {old ->
            if(playlistId != null) old.copy(playlists=old.playlists.map {playlist ->
                if(playlist.id == playlistId) playlist.copy(songIds=mergeSongOrder(ids,playlist.songIds),sort=SortMode.CUSTOM) else playlist
            }) else old.copy(custom=mergeSongOrder(ids,orderedSongs(old.songs,old.sort,old.custom,old.random).map {it.id}),sort=SortMode.CUSTOM)
        }
        if(playlistId != null) {
            if(before.playlists.none {it.id == playlistId}) return false
            persistMetadata()
        } else saveOrder()
        return true
    }
    private fun saveOrder() {persistMetadata()}
    fun lyrics(id: String, text: String?) {
        mutable.update { it.copy(manualLyrics=if(text == null) it.manualLyrics-id else it.manualLyrics+(id to text)) }
        persistMetadata()
    }
    fun backup(): String = state.value.let { s -> JSONObject().apply {
        put("kind","xiaohonghua-android"); put("version",1); put("songs",JSONArray(s.songs.map { it.toJson(true) }))
        put("playlists",JSONArray(normalizeFavorites(s.playlists,s.songs.map {it.id}.toSet()).map { it.toJson(true) })); put("folders",JSONArray(s.folders.map { JSONObject().put("uri",it.uri).put("name",it.name) }))
        put("settings",s.settings.toJson())
        put("manualLyrics",JSONObject(s.manualLyrics)); put("sort",s.sort.name); put("custom",JSONArray(s.custom)); put("random",JSONArray(s.random))
    }.toString(2) }
    suspend fun restore(text: String) = withContext(Dispatchers.IO) { initialized.await(); scanMutex.withLock {
        require(text.toByteArray().size <= 64*1024*1024) { "备份文件过大" }
        val data = JSONObject(text)
        require(data.optString("kind") == "xiaohonghua-android" && data.optInt("version") == 1) { "不是支持的小红花安卓备份" }
        val array = data.getJSONArray("songs")
        require(array.length() <= 30_000)
        val songs = (0 until array.length()).map { songFromJson(array.getJSONObject(it)).copy(available=false,artwork="") }
        require(songs.all { Uri.parse(it.uri).scheme == "content" && it.id.length in 1..128 }) { "备份中的音乐地址无效" }
        require(songs.map { it.id }.distinct().size == songs.size) { "备份中的歌曲编号重复" }
        val known=songs.map {it.id}.toSet()
        val parsedPlaylists = data.getJSONArray("playlists").let { a ->
            require(a.length() <= 5000)
            (0 until a.length()).map { playlistFromJson(a.getJSONObject(it)).let { p ->
                require(p.id.length in 1..128 && p.name.length in 1..60) { "歌单数据无效" }
                val members=p.songIds.filter(known::contains).distinct()
                p.copy(songIds=members,randomIds=mergeSongOrder(p.randomIds,members),coverPhoto="")
            } }
        }
        require(parsedPlaylists.map {it.id}.distinct().size == parsedPlaylists.size) { "歌单编号重复" }
        val playlists=normalizeFavorites(parsedPlaylists,known)
        val folders = data.getJSONArray("folders").let { a -> (0 until a.length()).map { a.getJSONObject(it).let { Folder(it.getString("uri"),it.getString("name")) } } }
        require(folders.all { Uri.parse(it.uri).scheme == "content" })
        val settings = settingsFromJson(data.getJSONObject("settings"))
        val lyrics = data.getJSONObject("manualLyrics").let { obj -> obj.keys().asSequence().filter(known::contains).associateWith { obj.getString(it) } }
        val sort = SortMode.valueOf(data.getString("sort"))
        val custom = data.getJSONArray("custom").strings().filter(known::contains).distinct()
        val random = data.getJSONArray("random").strings().filter(known::contains).distinct()
        // Parse and validate everything before replacing any current data.
        val task=currentCoroutineContext()
        persistenceMutex.withLock {
            store.replaceSongs(songs) {task.ensureActive()}
            mutable.value = LibraryState(songs,playlists,folders,settings,sort,custom,random,lyrics,status="备份已恢复，请重新授权音乐文件夹",ready=true)
            writeMetadata(state.value)
        }
    } }
    companion object {
        @Volatile private var instance: MusicRepository? = null
        fun get(context: Context): MusicRepository = instance ?: synchronized(this) { instance ?: MusicRepository(context.applicationContext).also { instance=it } }
    }
}
