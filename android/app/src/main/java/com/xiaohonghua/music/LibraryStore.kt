package com.xiaohonghua.music

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

fun Song.toJson(backup: Boolean = false): JSONObject = JSONObject().apply {
    put("id", id); put("uri", uri); put("name", name); put("title", title); put("artist", artist); put("album", album)
    put("duration", duration); put("size", size); put("modified", modified); put("format", format)
    put("sampleRate", sampleRate); put("bitDepth", bitDepth); put("track", track); put("lyrics", lyrics)
    put("artwork", if (backup) "" else artwork); put("sources", JSONArray(sources.toList())); put("available", available)
    put("lyricFile", lyricFile)
    put("artworkRevision", artworkRevision)
    put("albumArtist",albumArtist); put("disc",disc); put("lyricRevision",if(backup) "" else lyricRevision)
}
fun songFromJson(j: JSONObject): Song = Song(j.getString("id"), j.getString("uri"), j.getString("name"), j.optString("title"),
    j.optString("artist", "未知歌手"), j.optString("album", "未知专辑"), j.optLong("duration"), j.optLong("size"), j.optLong("modified"),
    j.optString("format"), j.optInt("sampleRate"), j.optInt("bitDepth"), j.optString("track"), j.optString("lyrics"), j.optString("artwork"),
    j.optJSONArray("sources").strings().toSet(), j.optBoolean("available", true), j.optString("lyricFile"), j.optString("artworkRevision"),
    j.optString("albumArtist"),j.optString("disc"),j.optString("lyricRevision"))
fun JSONArray?.strings(): List<String> = if (this == null) emptyList() else (0 until length()).map { getString(it) }
fun Playlist.toJson(backup: Boolean = false): JSONObject = JSONObject().apply {
    put("id", id); put("name", name); put("songIds", JSONArray(songIds)); put("sort", sort.name); put("randomIds", JSONArray(randomIds))
    put("coverPreset",normalizePlaylistCover(id,coverPreset)); put("coverPhoto",if(backup) "" else coverPhoto)
    put("description",description.take(PLAYLIST_DESCRIPTION_LIMIT))
}
fun playlistFromJson(j: JSONObject): Playlist = Playlist(j.getString("id"), j.getString("name"), j.optJSONArray("songIds").strings(),
    runCatching { SortMode.valueOf(j.optString("sort")) }.getOrDefault(SortMode.CUSTOM), j.optJSONArray("randomIds").strings(),
    normalizePlaylistCover(j.getString("id"),j.optInt("coverPreset",defaultPlaylistCover(j.getString("id")))),
    j.optString("coverPhoto").takeIf {it.matches(Regex("[a-f0-9-]{36}\\.jpg"))}.orEmpty(),j.optString("description").take(PLAYLIST_DESCRIPTION_LIMIT))
fun Settings.toJson(): JSONObject = JSONObject().apply {
    put("theme", theme); put("font", lyricFont); put("size", lyricSize); put("gap", lyricGap); put("dynamic", dynamicLyrics); put("nightMode",nightMode)
}
private fun JSONObject.bounded(key: String,default: Float,min: Float,max: Float): Float = optDouble(key,default.toDouble()).toFloat().let { if(it.isFinite()) it.coerceIn(min,max) else default }
fun settingsFromJson(j: JSONObject): Settings = Settings(j.optInt("theme").coerceIn(0,4), normalizeLyricFontName(j.optString("font", "system")),
    j.bounded("size",23f,18f,34f), j.bounded("gap",24f,0f,48f), j.optBoolean("dynamic",true),j.optBoolean("nightMode",false))

class LibraryStore(val context: Context) : SQLiteOpenHelper(context, "music.db", null, 1) {
    val prefs = context.getSharedPreferences("music", Context.MODE_PRIVATE)
    override fun onCreate(db: SQLiteDatabase) { db.execSQL("CREATE TABLE songs (id TEXT PRIMARY KEY, data TEXT NOT NULL)") }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) { error("未定义从 $oldVersion 到 $newVersion 的迁移") }
    fun songs(): List<Song> = readableDatabase.rawQuery("SELECT data FROM songs ORDER BY rowid", null).use { cursor ->
        buildList { while (cursor.moveToNext()) add(songFromJson(JSONObject(cursor.getString(0)))) }
    }
    fun replaceSongs(songs: List<Song>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val ids = songs.map { it.id }.toSet()
            val previous = db.rawQuery("SELECT id,data FROM songs", null).use { cursor ->
                buildMap<String,String> {while(cursor.moveToNext()) put(cursor.getString(0),cursor.getString(1))}
            }
            previous.keys.filter {it !in ids}.forEach {db.delete("songs","id=?",arrayOf(it))}
            songs.forEach { song ->
                val data=song.toJson().toString()
                if(previous[song.id] != data) db.insertWithOnConflict("songs", null, ContentValues().apply {put("id",song.id);put("data",data)},SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    fun settings(): Settings = runCatching { settingsFromJson(JSONObject(prefs.getString("settings","{}")!!)) }.getOrDefault(Settings())
    fun saveSettings(settings: Settings) { prefs.edit().putString("settings", settings.toJson().toString()).apply() }
    fun playlists(): List<Playlist> = array("playlists").let { array ->
        val list=mutableListOf<Playlist>()
        var migrated=false
        for(index in 0 until array.length()) {
            val json=array.getJSONObject(index)
            val playlist=playlistFromJson(json)
            if(json.has("coverPreset") && json.optInt("coverPreset") != playlist.coverPreset) migrated=true
            list += if(json.has("coverPreset")) playlist else {
                migrated=true
                playlist.copy(coverPreset=if(playlist.isFavorites) FAVORITES_FLOWER_COVER else (8..17).minBy {preset -> list.count {it.coverPreset == preset}})
            }
        }
        val updateFlower= !prefs.getBoolean("favoriteFlowerCoverV1",false)
        val normalized=normalizeFavorites(list).let {if(updateFlower) it.map(::upgradeFavoriteFlower) else it}
        if(migrated || normalized != list) savePlaylists(normalized)
        if(updateFlower) prefs.edit {putBoolean("favoriteFlowerCoverV1",true)}
        normalized
    }
    fun savePlaylists(list: List<Playlist>) { prefs.edit().putString("playlists", JSONArray(normalizeFavorites(list).map { it.toJson() }).toString()).apply() }
    fun folders(): List<Folder> = array("folders").let { list -> (0 until list.length()).map { list.getJSONObject(it).let { Folder(it.getString("uri"), it.getString("name")) } } }
    fun saveFolders(list: List<Folder>) { prefs.edit().putString("folders", JSONArray(list.map { JSONObject().put("uri", it.uri).put("name", it.name) }).toString()).apply() }
    fun array(key: String): JSONArray = runCatching { JSONArray(prefs.getString(key, "[]")) }.getOrDefault(JSONArray())
    fun manualLyrics(): Map<String,String> = runCatching { JSONObject(prefs.getString("manualLyrics", "{}")!!).let { obj -> obj.keys().asSequence().associateWith { obj.getString(it) } } }.getOrDefault(emptyMap())
    fun saveManualLyrics(values: Map<String,String>) { prefs.edit().putString("manualLyrics",JSONObject(values).toString()).apply() }
}

data class Document(val uri: Uri, val name: String, val size: Long, val modified: Long, val directory: Boolean)
data class ScanResult(val songs: List<Song>, val errors: List<String>)

class LibraryScanner(private val context: Context) {
    private val reader = TagReader(context)
    private val extensions = setOf("flac","mp3","wav","aac","m4a","ogg","opus","aiff","aif")
    fun single(uri: Uri, source: String = "files"): Song {
        val doc = info(uri)
        require(doc.name.substringAfterLast('.', "").lowercase() in extensions) { "请选择支持的音乐文件" }
        return reader.read(uri,doc.name,doc.size,doc.modified,source)
    }
    fun folder(uri: Uri, old: List<Song>, force: Boolean): ScanResult {
        val output = mutableListOf<Song>()
        val errors = mutableListOf<String>()
        val existing = old.associateBy { it.id }
        val byUri=old.associateBy {it.uri}
        val visited = mutableSetOf<String>()
        fun walk(documentId: String, depth: Int) {
            require(depth <= 100 && visited.size < 100_000) { "文件夹层级或数量超过扫描限制" }
            if (!visited.add(documentId)) return
            val children = children(uri, documentId)
            val lyrics = children.filter { !it.directory && it.name.endsWith(".lrc",true) }.associateBy { it.name.substringBeforeLast('.').lowercase() }
            children.forEach { doc ->
                if (doc.directory) walk(DocumentsContract.getDocumentId(doc.uri), depth+1)
                else if (doc.name.substringAfterLast('.', "").lowercase() in extensions) {
                    val previous = existing[documentIdentity(doc.uri)] ?: byUri[doc.uri.toString()]
                    try {
                        val lrcDoc = lyrics[doc.name.substringBeforeLast('.').lowercase()]
                        val revision=lrcDoc?.let {lyricRevision(it.uri.toString(),it.size,it.modified)}.orEmpty()
                        val cached=!force && previous != null && lrcDoc != null && revision.isNotEmpty() &&
                            previous.lyricFile == lrcDoc.uri.toString() && previous.lyricRevision == revision
                        val lrcResult=lrcDoc?.let {lyric ->
                            if(cached) Result.success(previous!!.lyrics) else runCatching {reader.lyricText(lyric.uri)}
                                .onFailure {e -> errors += "${lyric.name}：${e.message ?: "歌词无法读取"}"}
                        }
                        val lrc=lrcResult?.getOrNull()
                        val removedLyrics=lrcDoc == null && !previous?.lyricFile.isNullOrEmpty()
                        val song=if(reuseAudioTags(previous,doc.name,doc.size,doc.modified,force) && !removedLyrics) {
                            previous!!.copy(uri=doc.uri.toString(),name=doc.name,lyrics=lrc ?: previous.lyrics)
                        } else reader.read(doc.uri,doc.name,doc.size,doc.modified,uri.toString(),lrc)
                        val resolved=when {
                            // An unreadable LRC is not a deletion. Retain its last good text and
                            // clear the revision so the next check retries the failed read.
                            lrcResult?.isFailure == true && previous != null && previous.lyricFile.isNotEmpty() ->
                                song.copy(lyrics=previous.lyrics,lyricFile=previous.lyricFile,lyricRevision="")
                            lrc != null -> song.copy(lyricFile=lrcDoc!!.uri.toString(),lyricRevision=revision)
                            else -> song.copy(lyricFile="",lyricRevision="")
                        }
                        // Relinked backups retain their library ID across later folder checks.
                        output += resolved.copy(id=previous?.id ?: resolved.id)
                    } catch (e: Exception) {
                        errors += "${doc.name}：${e.message ?: "无法读取"}"
                        // A corrupt/unreadable existing file must not be treated as deleted.
                        if (previous != null) output += previous.copy(available=false)
                    }
                }
            }
        }
        walk(DocumentsContract.getTreeDocumentId(uri), 0)
        return ScanResult(output,errors)
    }
    private fun children(tree: Uri, id: String): List<Document> {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree,id)
        val projection = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_SIZE, DocumentsContract.Document.COLUMN_LAST_MODIFIED)
        val cursor = context.contentResolver.query(children, projection, null,null,null) ?: error("文件夹无法读取，请重新授权")
        return cursor.use { c -> buildList {
            while (c.moveToNext()) add(Document(DocumentsContract.buildDocumentUriUsingTree(tree,c.getString(0)),c.getString(1),c.getLong(3),c.getLong(4),c.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR))
        } }
    }
    fun info(uri: Uri): Document = context.contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE),null,null,null)?.use { c ->
        check(c.moveToFirst()) { "文件不可用" }; Document(uri,c.getString(0),c.getLong(1),0,false)
    } ?: error("文件不可用")
}
