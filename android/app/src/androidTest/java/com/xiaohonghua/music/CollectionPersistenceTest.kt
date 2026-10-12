package com.xiaohonghua.music

import android.content.Context
import android.content.ContextWrapper
import android.os.Bundle
import android.provider.DocumentsContract
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CollectionPersistenceTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun oldSongsAndSettingsDefaultNewFieldsWithoutDroppingExistingData() {
        val old=JSONObject().put("id","old").put("uri","content://test/old").put("name","old.flac")
            .put("title","旧歌").put("track","10/12").put("lyrics","[00:01]歌词")
        val song=songFromJson(old)
        assertEquals("",song.albumArtist);assertEquals("",song.disc);assertEquals("",song.lyricRevision)
        assertEquals("10/12",song.track);assertEquals("[00:01]歌词",song.lyrics)
        val settings=settingsFromJson(JSONObject().put("font","kai").put("size",29).put("theme",2))
        assertFalse(settings.nightMode);assertEquals(29f,settings.lyricSize);assertEquals(2,settings.theme)
        val retired=settingsFromJson(JSONObject().put("monitor",true).put("background","photo")
            .put("opacity",.9).put("scale",2).put("x",1).put("y",1).put("nightMode",true))
        assertEquals(Settings(nightMode=true),retired)
        assertEquals(setOf("theme","font","size","gap","dynamic","nightMode"),retired.toJson().keys().asSequence().toSet())
    }
    @Test fun metadataNightAndOffsetsPersistWithoutIncludingCacheRevisionInBackup() {
        val unique="collection-test-"+java.util.UUID.randomUUID()
        val isolated=object : ContextWrapper(context) {
            override fun getSharedPreferences(name: String,mode: Int)=super.getSharedPreferences(unique,mode)
        }
        val store=LibraryStore(isolated)
        try {
            val song=Song("id","content://test/id","album.flac","歌曲",albumArtist="群星",disc="2/3",lyricRevision="cached-revision")
            assertEquals(song,songFromJson(song.toJson()))
            val portable=songFromJson(song.toJson(true))
            assertEquals("群星",portable.albumArtist);assertEquals("2/3",portable.disc);assertEquals("",portable.lyricRevision)
            store.saveSettings(Settings(nightMode=true));assertTrue(LibraryStore(isolated).settings().nightMode)
            store.saveLyricOffsets(mapOf("id" to 500,"deleted" to -500))
            assertEquals(mapOf("id" to 500L),LibraryStore(isolated).lyricOffsets(setOf("id")))
            val values=lyricOffsetsFromJson(JSONObject().put("id",99_999).put("string","oops").put("deleted",100),setOf("id","string"))
            assertEquals(mapOf("id" to 10_000L),values)
        } finally {store.close();context.deleteSharedPreferences(unique)}
    }

    @Test fun siblingLrcAddModifyDeleteAndRetryRefreshWithoutRereadingUnchangedAudio() {
        val automation=InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.adoptShellPermissionIdentity("android.permission.MANAGE_DOCUMENTS")
        try {
        val authority=CollectionDocumentsProvider.AUTHORITY
        val base=android.net.Uri.parse("content://$authority")
        val resolver=context.contentResolver
        fun configure(text: String?=null,present: Boolean=true,readable: Boolean=true,modified: Long=1000) {
            resolver.call(base,"configure",null,Bundle().apply {
                putBoolean("present",present);putBoolean("readable",readable);putLong("modified",modified)
                if(text != null) putString("text",text)
            })
        }
        fun audioReads()=resolver.call(base,"counts",null,null)!!.getInt("audio")
        fun lyricReads()=resolver.call(base,"counts",null,null)!!.getInt("lyrics")
        resolver.call(base,"reset",null,null)
        configure(present=false)
        val tree=DocumentsContract.buildTreeDocumentUri(authority,"root")
        val scanner=LibraryScanner(context)
        val original=scanner.folder(tree,emptyList(),false).songs.single()
        configure("[00:01]第一版")
        val added=scanner.folder(tree,listOf(original),false).songs.single()
        assertEquals("[00:01]第一版",added.lyrics);assertTrue(added.lyricFile.isNotBlank())
        val firstAudio=audioReads();val firstLyrics=lyricReads()
        val cached=scanner.folder(tree,listOf(added),false).songs.single()
        assertEquals(added,cached);assertEquals(firstAudio,audioReads());assertEquals(firstLyrics,lyricReads())
        val relinked=scanner.folder(tree,listOf(cached.copy(id="restored-id")),false).songs.single()
        assertEquals("restored-id",relinked.id);assertEquals(firstAudio,audioReads())
        configure("[00:01]第二版",modified=1001)
        val changed=scanner.folder(tree,listOf(cached),false).songs.single()
        assertEquals("[00:01]第二版",changed.lyrics);assertEquals(firstAudio,audioReads());assertEquals(firstLyrics+1,lyricReads())
        configure(readable=false,modified=1002)
        val failure=scanner.folder(tree,listOf(changed),false)
        assertFalse(failure.errors.isEmpty());assertEquals(changed.lyrics,failure.songs.single().lyrics)
        assertEquals(changed.lyricFile,failure.songs.single().lyricFile);assertEquals("",failure.songs.single().lyricRevision)
        configure("[00:01]恢复成功",modified=1002)
        val recovered=scanner.folder(tree,failure.songs,false).songs.single()
        assertEquals("[00:01]恢复成功",recovered.lyrics)
        configure(present=false)
        val deleted=scanner.folder(tree,listOf(recovered),false).songs.single()
        assertEquals(original.lyrics,deleted.lyrics);assertEquals("",deleted.lyricFile);assertTrue(audioReads() > firstAudio)
        configure("[00:01]未知日期",modified=0)
        val unknown=scanner.folder(tree,listOf(deleted),false).songs.single()
        val beforeUnknown=lyricReads()
        scanner.folder(tree,listOf(unknown),false)
        assertEquals(beforeUnknown+1,lyricReads())
        val beforeForce=audioReads()
        scanner.folder(tree,listOf(unknown),true)
        assertTrue(audioReads() > beforeForce)
        } finally {automation.dropShellPermissionIdentity()}
    }

    @Test fun failedFolderListingPreservesExternalLyricsDuringStandaloneMetadataRefresh() {
        val automation=InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.adoptShellPermissionIdentity("android.permission.MANAGE_DOCUMENTS")
        val unique="collection-refresh-"+java.util.UUID.randomUUID()
        val isolated=object : ContextWrapper(context) {
            override fun getSharedPreferences(name: String,mode: Int)=super.getSharedPreferences(unique,mode)
            override fun openOrCreateDatabase(name: String,mode: Int,factory: android.database.sqlite.SQLiteDatabase.CursorFactory?)=
                super.openOrCreateDatabase("$unique-$name",mode,factory)
            override fun openOrCreateDatabase(name: String,mode: Int,factory: android.database.sqlite.SQLiteDatabase.CursorFactory?,handler: android.database.DatabaseErrorHandler?)=
                super.openOrCreateDatabase("$unique-$name",mode,factory,handler)
        }
        val authority=CollectionDocumentsProvider.AUTHORITY
        val base=android.net.Uri.parse("content://$authority")
        val tree=DocumentsContract.buildTreeDocumentUri(authority,"root")
        var repository: MusicRepository?=null
        try {
            context.contentResolver.call(base,"reset",null,null)
            context.contentResolver.call(base,"configure",null,Bundle().apply {putString("text","[00:01]最后可用的歌词")})
            val original=LibraryScanner(isolated).folder(tree,emptyList(),false).songs.single()
                .copy(title="旧元数据",sources=setOf("files",tree.toString()))
            val initial=LibraryStore(isolated)
            initial.replaceSongs(listOf(original));initial.saveSettings(Settings(nightMode=true))
            initial.saveFolders(listOf(Folder(tree.toString(),"Test")));initial.close()
            val constructor=MusicRepository::class.java.getDeclaredConstructor(Context::class.java).apply {isAccessible=true}
            val repo=constructor.newInstance(isolated)
            repository=repo
            assertTrue(repo.state.value.settings.nightMode)
            runBlocking {withTimeout(10_000) {repo.state.first {it.ready}}}
            context.contentResolver.call(base,"configure",null,Bundle().apply {putBoolean("failListing",true)})
            repo.requestRefresh()
            val refreshed=runBlocking {withTimeout(10_000) {repo.state.first {!it.busy && it.status.startsWith("新增 ")}}}.songs.single()
            assertEquals(original.id,refreshed.id);assertEquals(original.sources,refreshed.sources)
            assertNotEquals("旧元数据",refreshed.title);assertTrue(refreshed.available)
            assertEquals(original.lyrics,refreshed.lyrics);assertEquals(original.lyricFile,refreshed.lyricFile)
            assertEquals("",refreshed.lyricRevision)
        } finally {
            repository?.let {repo ->
                val scope=MusicRepository::class.java.getDeclaredField("scope").apply {isAccessible=true}.get(repo) as CoroutineScope
                scope.cancel()
                repo.store.close()
            }
            context.deleteSharedPreferences(unique);context.deleteDatabase("$unique-music.db")
            automation.dropShellPermissionIdentity()
        }
    }

    @Test fun startupAndProviderNotificationsDoNotScanWhileEachManualRefreshReadsAllTags() {
        val automation=InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.adoptShellPermissionIdentity("android.permission.MANAGE_DOCUMENTS")
        val unique="collection-manual-"+java.util.UUID.randomUUID()
        val isolated=object : ContextWrapper(context) {
            override fun getSharedPreferences(name: String,mode: Int)=super.getSharedPreferences(unique,mode)
            override fun openOrCreateDatabase(name: String,mode: Int,factory: android.database.sqlite.SQLiteDatabase.CursorFactory?)=
                super.openOrCreateDatabase("$unique-$name",mode,factory)
            override fun openOrCreateDatabase(name: String,mode: Int,factory: android.database.sqlite.SQLiteDatabase.CursorFactory?,handler: android.database.DatabaseErrorHandler?)=
                super.openOrCreateDatabase("$unique-$name",mode,factory,handler)
        }
        val authority=CollectionDocumentsProvider.AUTHORITY
        val base=android.net.Uri.parse("content://$authority")
        val tree=DocumentsContract.buildTreeDocumentUri(authority,"root")
        val resolver=context.contentResolver
        fun counts()=resolver.call(base,"counts",null,null)!!
        var repository: MusicRepository?=null
        try {
            resolver.call(base,"reset",null,null)
            resolver.call(base,"configure",null,Bundle().apply {putString("text","[00:01]第一版本")})
            val scanner=LibraryScanner(isolated)
            val folderSong=scanner.folder(tree,emptyList(),true).songs.single().copy(title="旧文件夹标签")
            val standalone=scanner.single(DocumentsContract.buildDocumentUri(authority,"standalone")).copy(title="旧单文件标签")
            val initial=LibraryStore(isolated)
            initial.replaceSongs(listOf(folderSong,standalone));initial.saveFolders(listOf(Folder(tree.toString(),"Test")))
            initial.saveLyricOffsets(mapOf(folderSong.id to 500L))
            initial.prefs.edit().putString("settings",JSONObject().put("monitor",true).put("background","photo").toString())
                .putBoolean("albumMetadataV1",false).apply()
            initial.close()
            val before=counts()
            val constructor=MusicRepository::class.java.getDeclaredConstructor(Context::class.java).apply {isAccessible=true}
            val repo=constructor.newInstance(isolated)
            repository=repo
            runBlocking {withTimeout(10_000) {repo.state.first {it.ready}}}
            resolver.notifyChange(tree,null)
            runBlocking {delay(800)} // Exceeds the retired observer's debounce period.
            assertEquals(before.getInt("audio"),counts().getInt("audio"))
            assertEquals(before.getInt("lyrics"),counts().getInt("lyrics"))
            assertEquals(listOf("旧文件夹标签","旧单文件标签"),repo.state.value.songs.map {it.title})
            assertFalse(repo.state.value.busy)
            repo.requestRefresh()
            runBlocking {withTimeout(10_000) {repo.state.first {!it.busy && it.status.startsWith("新增 ")}}}
            assertTrue(counts().getInt("audio") > before.getInt("audio"))
            assertEquals(before.getInt("lyrics")+1,counts().getInt("lyrics"))
            assertTrue(repo.state.value.songs.none {it.title.startsWith("旧")})
            assertEquals(setOf(folderSong.id,standalone.id),repo.state.value.songs.map {it.id}.toSet())
            // Same provider timestamps and same lyric byte size still require a fresh read.
            val first=counts()
            resolver.call(base,"configure",null,Bundle().apply {putString("text","[00:01]第二版本");putLong("modified",1000)})
            repo.requestRefresh()
            runBlocking {withTimeout(10_000) {
                repo.state.first {!it.busy && it.songs.find {song -> song.id == folderSong.id}?.lyrics == "[00:01]第二版本"}
            }}
            assertTrue(counts().getInt("audio") > first.getInt("audio"))
            assertEquals(first.getInt("lyrics")+1,counts().getInt("lyrics"))
            val portable=JSONObject(repo.backup())
            assertFalse(portable.has("recentPlayed"));assertFalse(portable.getJSONObject("settings").has("monitor"))
            // Schema-1 backups may contain removed fields; ignore them without enabling old features.
            portable.put("recentPlayed",JSONArray().put(folderSong.id))
            portable.getJSONObject("settings").put("monitor",true).put("background","photo")
            runBlocking {repo.restore(portable.toString())}
            assertEquals(500L,repo.state.value.lyricOffsets[folderSong.id])
            assertTrue(repo.state.value.songs.all {!it.available})
            assertFalse(JSONObject(repo.backup()).has("recentPlayed"))
        } finally {
            repository?.let {repo ->
                (MusicRepository::class.java.getDeclaredField("scope").apply {isAccessible=true}.get(repo) as CoroutineScope).cancel()
                repo.store.close()
            }
            context.deleteSharedPreferences(unique);context.deleteDatabase("$unique-music.db")
            automation.dropShellPermissionIdentity()
        }
    }
}
