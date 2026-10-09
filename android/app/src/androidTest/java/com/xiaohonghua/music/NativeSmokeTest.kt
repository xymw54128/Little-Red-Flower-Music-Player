package com.xiaohonghua.music

import android.content.ContentValues
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import java.io.File

/** Run only on an emulator: prepares and replaces its disposable app library. */
@RunWith(AndroidJUnit4::class)
class NativeSmokeTest {
    private val compose=createAndroidComposeRule<MainActivity>()
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val context=instrumentation.targetContext
    private var originalUris=emptyList<Uri>()
    private val fixture=object : ExternalResource() {
        override fun before() {
            check(Build.HARDWARE in listOf("ranchu","goldfish")) { "测试只能在模拟器中执行，避免改动手机音乐库" }
            originalUris=assets("Music/Xiaohonghua-test/")
            val names=listOf("test-tone.flac","test-highres.flac","test-tone.wav")
            val songs=originalUris.mapIndexed {index,uri -> TagReader(context).read(uri,names[index],size(uri),0,"files") }
            val store=LibraryStore(context)
            store.prefs.edit().clear().commit()
            store.saveSettings(Settings(monitor=false))
            store.replaceSongs(songs)
            assertEquals(44100,songs[0].sampleRate);assertEquals(16,songs[0].bitDepth)
            assertEquals(96000,songs[1].sampleRate);assertEquals(24,songs[1].bitDepth)
        }
    }
    @get:Rule val rules: RuleChain=RuleChain.outerRule(fixture).around(compose)
    private fun size(uri: Uri)=context.contentResolver.openAssetFileDescriptor(uri,"r")!!.use {it.length}
    private fun assets(relative: String): List<Uri> = listOf("tone.flac" to "test-tone.flac","tone-24bit-96k.flac" to "test-highres.flac","tone.wav" to "test-tone.wav").map { (asset,name) ->
        val uri=context.contentResolver.insert(MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME,name);put(MediaStore.MediaColumns.MIME_TYPE,if(name.endsWith("flac")) "audio/flac" else "audio/wav")
            put(MediaStore.MediaColumns.RELATIVE_PATH,relative);put(MediaStore.MediaColumns.IS_PENDING,1)
        })!!
        context.contentResolver.openOutputStream(uri)!!.use {out->instrumentation.context.assets.open(asset).use {it.copyTo(out)}}
        context.contentResolver.update(uri,ContentValues().apply {put(MediaStore.MediaColumns.IS_PENDING,0)},null,null)
        uri
    }
    private fun snapshot(name: String) {
        val folder=File(context.getExternalFilesDir(null),"screenshots").apply {mkdirs()}
        File(folder,"$name.png").outputStream().use {compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}
    }
    private fun lyricsCentered() {
        compose.waitUntil(30_000) {
            val viewport=compose.onNodeWithTag("lyrics-list").getUnclippedBoundsInRoot()
            val line=compose.onNodeWithTag("current-lyric").getUnclippedBoundsInRoot()
            kotlin.math.abs((line.top.value+line.bottom.value-viewport.top.value-viewport.bottom.value)/2) < 3
        }
    }
    @Test fun audioUiSettingsAndMigration() {
        val model=ViewModelProvider(compose.activity)[MusicModel::class.java]
        compose.waitUntil(60_000) {model.library.value.songs.size == 3 && model.playback.value.connected && compose.onAllNodesWithTag("launch-animation").fetchSemanticsNodes().isEmpty()}
        val songs=model.library.value.songs
        val flac=songs.first {it.format == "FLAC" && it.bitDepth == 16}
        val high=songs.first {it.bitDepth == 24}
        val wav=songs.first {it.format == "WAV"}
        snapshot("library")
        for(song in listOf(flac,high,wav)) {
            compose.runOnIdle {model.play(songs,song.id)}
            compose.waitUntil(60_000) {model.playback.value.currentId == song.id && model.playback.value.playing && model.playback.value.position > 300}
            assertNull("${song.format}/${song.bitDepth}",model.playback.value.error)
        }
        val queueSnapshot=model.playback.value.queue
        val tickStart=model.playback.value.position
        compose.waitUntil(30_000) {model.playback.value.position > tickStart+300}
        assertSame("进度更新不应重复分配整个播放队列",queueSnapshot,model.playback.value.queue)
        compose.runOnIdle {model.play(songs,songs.last().id);model.seek(4500)}
        compose.waitUntil(30_000) {model.playback.value.currentId == songs.last().id && model.playback.value.position >= 4500}
        compose.onNodeWithContentDescription("上一首").performClick()
        compose.waitUntil(30_000) {model.playback.value.currentId == songs[songs.lastIndex-1].id}
        compose.runOnIdle {model.play(songs,flac.id);model.seek(3500)}
        compose.waitUntil(30_000) {model.playback.value.position >= 3500}
        compose.runOnIdle {model.toggle()}
        compose.waitUntil(30_000) {!model.playback.value.playing}
        compose.onNodeWithTag("mini-player").performClick()
        compose.onNodeWithContentDescription("收起播放器").assertIsDisplayed()
        compose.onNodeWithTag("player-artist-album").assertTextEquals("${flac.artist} · ${flac.album}")
        compose.onNodeWithTag("playback-seek").performSemanticsAction(SemanticsActions.SetProgress) {it(7000f)}
        compose.waitUntil(30_000) {kotlin.math.abs(model.playback.value.position-7000) < 200}
        compose.runOnIdle {model.seek(3500)}
        compose.waitUntil(30_000) {kotlin.math.abs(model.playback.value.position-3500) < 200}
        snapshot("player")
        compose.onNodeWithTag("player-pages").performTouchInput {swipeLeft(durationMillis=450)}
        compose.waitUntil(30_000) {compose.onAllNodesWithTag("lyrics-list").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("current-lyric").assertTextEquals("歌词跟随真实播放时间")
        lyricsCentered()
        val wrapped=(1..20).joinToString("\n") {index ->
            val stamp="[00:${index.toString().padStart(2,'0')}]"
            "$stamp 第 $index 行，较长的歌词应当自动换行，并保持整行歌词居中显示\n${stamp}Translation $index"
        }
        compose.runOnIdle {model.repository.lyrics(flac.id,wrapped);model.seek(12000)}
        compose.waitUntil(30_000) {compose.onAllNodesWithText("第 12 行，较长的歌词应当自动换行，并保持整行歌词居中显示\nTranslation 12").fetchSemanticsNodes().isNotEmpty()}
        lyricsCentered()
        compose.onNodeWithTag("lyrics-list").performTouchInput {swipeUp(durationMillis=450)}
        compose.onNodeWithText("回到当前").performClick()
        lyricsCentered()
        compose.onNodeWithTag("lyrics-list").performTouchInput {swipeDown(durationMillis=450)}
        compose.waitUntil(30_000) {compose.onAllNodesWithText("回到当前").fetchSemanticsNodes().isEmpty()}
        lyricsCentered()
        snapshot("lyrics")
        compose.onNodeWithTag("player-pages").performTouchInput {swipeRight(durationMillis=450)}
        compose.onNodeWithContentDescription("收起播放器").performClick()
        for(mode in listOf(1,2,3,0)) {
            compose.runOnIdle {model.mode()}
            compose.waitUntil(30_000) {model.playback.value.mode == mode}
        }
        val first=orderedSongs(songs,SortMode.TITLE,emptyList(),emptyList()).first()
        compose.onAllNodesWithContentDescription("歌曲操作").onFirst().performClick()
        compose.onNodeWithText("添加到歌单").performClick()
        compose.onNodeWithText("新建歌单并添加").performClick()
        compose.onNodeWithText("歌单名称").performTextInput("原生验证歌单")
        compose.onNodeWithText("确定").performClick()
        compose.waitUntil(30_000) {model.library.value.playlists.size == 1}
        assertEquals(listOf(first.id),model.library.value.playlists.single().songIds)
        val savedPlaylist=model.library.value.playlists.single()
        compose.runOnIdle {
            val other=model.repository.createPlaylist("不同默认封面")
            assertNotEquals(savedPlaylist.coverPreset,other.coverPreset)
            model.repository.deletePlaylist(other.id)
            model.repository.playlistCover(savedPlaylist.id,2)
        }
        val oldPlaylist=org.json.JSONObject().put("id","old-list").put("name","旧歌单").put("songIds",org.json.JSONArray(listOf(first.id)))
        assertEquals(defaultPlaylistCover("old-list"),playlistFromJson(oldPlaylist).coverPreset)
        val coverStore=LibraryStore(context)
        val currentJson=coverStore.prefs.getString("playlists","[]")!!
        try {
            val legacy=org.json.JSONArray().put(oldPlaylist).put(org.json.JSONObject(oldPlaylist.toString()).put("id","another-old-list"))
            coverStore.prefs.edit().putString("playlists",legacy.toString()).commit()
            val migrated=coverStore.playlists()
            assertNotEquals(migrated[0].coverPreset,migrated[1].coverPreset)
            assertEquals(listOf(first.id),migrated[0].songIds)
            assertEquals(migrated,coverStore.playlists())
        } finally {coverStore.prefs.edit().putString("playlists",currentJson).commit()}
        assertEquals(listOf(first.id),LibraryStore(context).playlists().single().songIds)
        assertEquals(2,LibraryStore(context).playlists().single().coverPreset)
        compose.onNodeWithText("设置").performClick()
        compose.onNodeWithTag("app-author").performScrollTo().assertTextEquals("作者：浪雨林兴云魔王")
        compose.onNodeWithText("歌词样式").performScrollTo().performClick()
        compose.onNodeWithText("等宽字体").performClick()
        for((font,label) in lyricFontOptions.takeLast(3)) {
            compose.onNodeWithText(label).performClick()
            assertEquals(font,model.library.value.settings.lyricFont)
            assertEquals(font,LibraryStore(context).settings().lyricFont)
        }
        compose.onNodeWithText("等宽字体").performClick()
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)).onFirst().performSemanticsAction(SemanticsActions.SetProgress) {it(30f)}
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))[1].performSemanticsAction(SemanticsActions.SetProgress) {it(36f)}
        assertEquals("mono",model.library.value.settings.lyricFont)
        assertEquals(30f,model.library.value.settings.lyricSize)
        assertEquals(36f,model.library.value.settings.lyricGap)
        snapshot("lyric-settings")
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText("主题颜色").performClick()
        compose.onNodeWithText("森林").performClick()
        assertEquals(1,model.library.value.settings.theme)
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText("播放背景").performClick()
        val photo=context.contentResolver.insert(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME,"xiaohonghua-test.png");put(MediaStore.MediaColumns.MIME_TYPE,"image/png");put(MediaStore.MediaColumns.RELATIVE_PATH,"Pictures/Xiaohonghua-test/")
        })!!
        context.contentResolver.openOutputStream(photo)!!.use {out-> Bitmap.createBitmap(320,400,Bitmap.Config.ARGB_8888).apply {eraseColor(android.graphics.Color.rgb(172,196,181))}.compress(Bitmap.CompressFormat.PNG,100,out)}
        var result=""
        compose.runOnIdle {model.importBackground(photo) {result=it}}
        compose.waitUntil(30_000) {result.isNotBlank()}
        assertEquals("photo",model.library.value.settings.background)
        result=""
        compose.runOnIdle {model.importPlaylistCover(savedPlaylist.id,photo) {result=it}}
        compose.waitUntil(30_000) {result.isNotBlank()}
        assertEquals("歌单封面已更新",result)
        val photoPlaylist=model.library.value.playlists.single()
        assertEquals(listOf(first.id),photoPlaylist.songIds)
        assertTrue(File(context.filesDir,"playlist-covers/${photoPlaylist.coverPhoto}").isFile)
        assertEquals(photoPlaylist,LibraryStore(context).playlists().single())
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)).onFirst().performSemanticsAction(SemanticsActions.SetProgress) {it(.65f)}
        assertEquals(.65f,model.library.value.settings.opacity)
        snapshot("background")
        val backup=model.repository.backup()
        val oldIds=model.library.value.songs.map {it.id}.toSet()
        val playlistId=model.library.value.playlists.single().id
        runBlocking {model.repository.restore(backup)}
        compose.runOnIdle {model.stopForRestore()}
        assertTrue(model.library.value.songs.all {!it.available})
        val newUris=assets("Music/Xiaohonghua-new-phone/")
        model.repository.importFiles(newUris)
        compose.waitUntil(60_000) {model.library.value.songs.all {it.available}}
        assertEquals(oldIds,model.library.value.songs.map {it.id}.toSet())
        assertEquals(playlistId,model.library.value.playlists.single().id)
        assertEquals(listOf(first.id),model.library.value.playlists.single().songIds)
        assertEquals(2,model.library.value.playlists.single().coverPreset)
        assertEquals("",model.library.value.playlists.single().coverPhoto)
        assertTrue(model.library.value.songs.all {it.uri in newUris.map(Uri::toString)})
        assertEquals(36f,LibraryStore(context).settings().lyricGap)
        File(context.getExternalFilesDir(null),"native-test-results.txt").writeText("PASS: native FLAC 16bit/44.1kHz, FLAC 24bit/96kHz, WAV playback; seek; modes; swipe lyrics; playlist picker; font/size/gap/theme/photo; backup and new-provider-ID relinking.")
    }
}
