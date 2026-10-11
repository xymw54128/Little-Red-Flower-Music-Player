package com.xiaohonghua.music

import android.content.ContentValues
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.view.KeyEvent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import java.io.File

/** Emulator-only: replaces its disposable library with original synthetic test tones. */
@RunWith(AndroidJUnit4::class)
class MainNavigationUiTest {
    private val compose=createAndroidComposeRule<MainActivity>()
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val context=instrumentation.targetContext
    private val fixture=object : ExternalResource() {
        override fun before() {
            check(Build.HARDWARE in listOf("ranchu","goldfish")) {"导航测试只能在模拟器中执行，避免改动手机音乐库"}
            val relative="Music/Xiaohonghua-navigation-test/${java.util.UUID.randomUUID()}/"
            val uris=listOf("navigation-one.wav","navigation-two.wav").map {name ->
                val uri=context.contentResolver.insert(MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME,name)
                    put(MediaStore.MediaColumns.MIME_TYPE,"audio/wav")
                    put(MediaStore.MediaColumns.RELATIVE_PATH,relative)
                    put(MediaStore.MediaColumns.IS_PENDING,1)
                })!!
                context.contentResolver.openOutputStream(uri)!!.use {out -> instrumentation.context.assets.open("tone.wav").use {it.copyTo(out)}}
                context.contentResolver.update(uri,ContentValues().apply {put(MediaStore.MediaColumns.IS_PENDING,0)},null,null)
                uri
            }
            val songs=uris.map {uri -> LibraryScanner(context).single(uri)}
            val store=LibraryStore(context)
            store.prefs.edit().clear().commit()
            store.saveSettings(Settings(monitor=false))
            store.replaceSongs(songs)
            val repository=MusicRepository.get(context)
            runBlocking {
                withTimeout(30_000) {repository.state.first {it.ready}}
                val backup=JSONObject(repository.backup()).apply {
                    put("songs",JSONArray(songs.map {it.toJson(true)}))
                    put("playlists",JSONArray())
                    put("folders",JSONArray())
                    put("manualLyrics",JSONObject())
                    put("settings",Settings(monitor=false).toJson())
                    put("sort",SortMode.TITLE.name)
                    put("custom",JSONArray())
                    put("random",JSONArray())
                }
                repository.restore(backup.toString())
                repository.importFiles(uris).join()
            }
        }
    }
    @get:Rule val rules: RuleChain=RuleChain.outerRule(fixture).around(compose)

    private fun ready(): MusicModel {
        val model=ViewModelProvider(compose.activity)[MusicModel::class.java]
        compose.waitUntil(60_000) {
            model.library.value.ready && model.library.value.songs.size == 2 && model.playback.value.connected &&
                compose.onAllNodesWithTag("launch-animation").fetchSemanticsNodes().isEmpty()
        }
        compose.runOnIdle {model.stopForRestore()}
        compose.waitUntil(30_000) {model.playback.value.currentId == null && model.playback.value.queue.isEmpty()}
        assertEquals(1,model.library.value.playlists.count {it.isFavorites})
        return model
    }
    private fun home(page: String) {
        compose.waitForIdle()
        compose.onNodeWithTag("page-$page").assertIsDisplayed()
        compose.onNodeWithTag("nav-$page").assertIsSelected()
    }
    private fun back() {
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        compose.waitForIdle()
    }

    @Test fun horizontalMainPagesSearchAndFavoritesPreservePlayback() {
        val model=ready()
        home("library")
        compose.onNodeWithContentDescription("导入音乐").assertDoesNotExist()
        compose.onNodeWithText("播放全部").assertDoesNotExist()
        for(page in listOf("playlists","settings")) {
            compose.onNodeWithTag("home-pager").performTouchInput {swipeLeft(durationMillis=450)}
            home(page)
        }
        for(page in listOf("playlists","library")) {
            compose.onNodeWithTag("home-pager").performTouchInput {swipeRight(durationMillis=450)}
            home(page)
        }
        val song=model.library.value.songs.first()
        compose.onNodeWithTag("open-library-search").performClick()
        compose.onNodeWithTag("library-search-field").performTextInput("no-matching-test-song")
        compose.onNodeWithText("没有找到匹配的歌曲").assertIsDisplayed()
        compose.onNodeWithTag("close-library-search").performClick()
        compose.onNodeWithTag("library-search-field").assertDoesNotExist()
        compose.onNodeWithTag("favorite-${song.id}").assertIsNotSelected().performClick()
        compose.waitUntil(30_000) {song.id in model.library.value.playlists.first {it.isFavorites}.songIds}
        compose.onNodeWithTag("favorite-${song.id}").assertIsSelected()
        compose.onNodeWithText("已加入「我喜欢」").assertDoesNotExist()
        assertNull(model.playback.value.currentId)
        assertTrue(model.playback.value.queue.isEmpty())
        assertFalse(model.playback.value.playing)
        assertEquals(listOf(song.id),LibraryStore(context).playlists().first {it.isFavorites}.songIds)
        compose.onNodeWithTag("nav-playlists").performClick()
        home("playlists")
        compose.onNodeWithTag("playlist-$FAVORITES_PLAYLIST_ID").performClick()
        compose.onNodeWithTag("page-playlist").assertIsDisplayed()
        compose.onNodeWithContentDescription("重命名歌单").assertDoesNotExist()
        compose.onNodeWithContentDescription("删除歌单").assertDoesNotExist()
        compose.onNodeWithTag("favorite-${song.id}").assertIsSelected().performClick()
        compose.waitUntil(30_000) {model.library.value.playlists.first {it.isFavorites}.songIds.isEmpty()}
        compose.onNodeWithText("歌单还没有歌曲").assertIsDisplayed()
        assertNull(model.playback.value.currentId)
        assertTrue(model.playback.value.queue.isEmpty())
        back()
        home("playlists")
        compose.onNodeWithTag("nav-library").performClick()
        home("library")
        compose.onNodeWithTag("favorite-${song.id}").assertIsNotSelected()
        compose.onNodeWithTag("open-library-search").performClick()
        compose.onNodeWithTag("library-search-field").performTextInput("another-no-match")
        compose.onNodeWithTag("library-search-field").performImeAction()
        back()
        compose.onNodeWithTag("library-search-field").assertDoesNotExist()
        compose.onNodeWithTag("favorite-${song.id}").assertIsDisplayed()
    }

    @Test fun childPagesAndSongSheetsReturnToTheirCaller() {
        val model=ready()
        compose.onNodeWithTag("nav-settings").performClick()
        home("settings")
        for((label,page) in listOf("歌词样式" to "lyric-style","主题颜色" to "theme","播放背景" to "background","歌曲文件夹" to "folders","备份与恢复" to "backup")) {
            compose.onNodeWithText(label).performScrollTo().performClick()
            compose.onNodeWithTag("page-$page").assertIsDisplayed()
            if(page == "theme") back() else compose.onNodeWithContentDescription("返回").performClick()
            home("settings")
        }
        lateinit var playlist: Playlist
        compose.runOnIdle {
            playlist=model.repository.createPlaylist("导航返回测试")
            model.repository.playlist(playlist.copy(songIds=listOf(model.library.value.songs.first().id)))
        }
        compose.onNodeWithTag("nav-playlists").performClick()
        home("playlists")
        compose.onNodeWithTag("playlist-${playlist.id}").performClick()
        compose.onNodeWithTag("page-playlist").assertIsDisplayed()
        compose.onAllNodesWithContentDescription("歌曲操作").onFirst().performClick()
        compose.onNodeWithText("歌曲信息").performClick()
        compose.onNodeWithText("歌曲信息").assertIsDisplayed()
        back()
        compose.onNodeWithText("歌曲信息").assertDoesNotExist()
        compose.onNodeWithTag("page-playlist").assertIsDisplayed()
        compose.onAllNodesWithContentDescription("歌曲操作").onFirst().performClick()
        compose.onNodeWithText("添加到歌单").performClick()
        compose.onNodeWithText("确认添加").assertIsDisplayed()
        back()
        compose.onNodeWithText("确认添加").assertDoesNotExist()
        compose.onNodeWithTag("page-playlist").assertIsDisplayed()
        compose.onNodeWithContentDescription("删除歌单").performClick()
        back()
        assertTrue(model.library.value.playlists.any {it.id == playlist.id})
        compose.onNodeWithContentDescription("删除歌单").performClick()
        compose.onNodeWithText("删除").performClick()
        home("playlists")
        assertFalse(model.library.value.playlists.any {it.id == playlist.id})
    }

    @Test fun playlistRemovalAndDeletionUndoPreserveFilesAndLaterEdits() {
        val model=ready()
        val songs=model.library.value.songs
        val originalFiles=songs.associate {song -> song.id to context.contentResolver.openInputStream(Uri.parse(song.uri))!!.use {it.readBytes()}}
        lateinit var playlist: Playlist
        compose.runOnIdle {
            model.repository.createPlaylist("撤销前面的歌单")
            playlist=model.repository.createPlaylist("撤销测试歌单")
            model.repository.playlist(playlist.copy(songIds=songs.map {it.id}.reversed(),randomIds=songs.map {it.id},coverPreset=6))
            model.repository.createPlaylist("撤销后面的歌单")
            assertNull(model.repository.deletePlaylist(FAVORITES_PLAYLIST_ID))
        }
        val playlistOrder=model.library.value.playlists.map {it.id}
        compose.onNodeWithTag("nav-playlists").performClick()
        home("playlists")
        compose.onNodeWithTag("playlist-${playlist.id}").performScrollTo().performClick()
        compose.onNodeWithContentDescription("重命名歌单").performClick()
        compose.onNodeWithTag("playlist-name-field").performTextReplacement("已编辑的撤销测试歌单")
        compose.onNodeWithText("确定").performClick()
        compose.waitUntil(30_000) {model.library.value.playlists.first {it.id == playlist.id}.name == "已编辑的撤销测试歌单"}
        assertEquals(playlistOrder,model.library.value.playlists.map {it.id})

        val beforeRemoval=model.library.value.playlists.first {it.id == playlist.id}
        val removedId=beforeRemoval.songIds.first()
        compose.onNode(hasContentDescription("歌曲操作") and hasAnyAncestor(hasTestTag("song-$removedId"))).performClick()
        compose.onNodeWithText("从歌单移除").performClick()
        compose.waitUntil(30_000) {removedId !in model.library.value.playlists.first {it.id == playlist.id}.songIds}
        lateinit var laterEdit: Playlist
        compose.runOnIdle {
            val current=model.library.value.playlists.first {it.id == playlist.id}
            laterEdit=current.copy(name="撤销保留后来的编辑",coverPreset=7,sort=SortMode.ARTIST)
            model.repository.playlist(laterEdit)
        }
        compose.onNodeWithText("撤销").assertIsDisplayed().performClick()
        compose.waitUntil(30_000) {removedId in model.library.value.playlists.first {it.id == playlist.id}.songIds}
        val restoredSongList=model.library.value.playlists.first {it.id == playlist.id}
        assertEquals(laterEdit.copy(songIds=beforeRemoval.songIds,randomIds=beforeRemoval.randomIds),restoredSongList)
        assertEquals(playlistOrder,model.library.value.playlists.map {it.id})

        compose.onNodeWithContentDescription("删除歌单").performClick()
        compose.onNodeWithText("删除").performClick()
        home("playlists")
        assertFalse(model.library.value.playlists.any {it.id == playlist.id})
        compose.onNodeWithText("撤销").assertIsDisplayed().performClick()
        compose.waitUntil(30_000) {model.library.value.playlists.any {it.id == playlist.id}}
        assertEquals(playlistOrder,model.library.value.playlists.map {it.id})
        assertEquals(restoredSongList,model.library.value.playlists.first {it.id == playlist.id})
        assertEquals(restoredSongList,LibraryStore(context).playlists().first {it.id == playlist.id})
        compose.onNodeWithTag("playlist-$FAVORITES_PLAYLIST_ID").performScrollTo().performClick()
        compose.onNodeWithContentDescription("重命名歌单").assertDoesNotExist()
        compose.onNodeWithContentDescription("删除歌单").assertDoesNotExist()
        assertEquals(1,model.library.value.playlists.count {it.isFavorites})
        for(song in songs) {
            val bytes=context.contentResolver.openInputStream(Uri.parse(song.uri))!!.use {it.readBytes()}
            assertArrayEquals("Undo must not change the original music file",originalFiles.getValue(song.id),bytes)
        }
        assertEquals(songs,model.library.value.songs)
    }

    @Test fun restoredChildCanReturnUsingBottomNavigation() {
        ready()
        compose.onNodeWithTag("nav-settings").performClick()
        home("settings")
        compose.onNodeWithText("歌曲文件夹").performScrollTo().performClick()
        compose.onNodeWithTag("page-folders").assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("page-folders").assertIsDisplayed()
        compose.onNodeWithTag("nav-library").performClick()
        home("library")
        compose.onNodeWithTag("open-library-search").assertIsDisplayed()
    }

    @Test fun openingAChildDuringMainPageAnimationReturnsToItsTargetPage() {
        ready()
        compose.mainClock.autoAdvance=false
        compose.onNodeWithTag("nav-settings").performClick()
        compose.mainClock.advanceTimeBy(200)
        compose.onNodeWithText("歌词样式").performClick()
        compose.mainClock.autoAdvance=true
        compose.onNodeWithTag("page-lyric-style").assertIsDisplayed()
        compose.onNodeWithContentDescription("返回").performClick()
        home("settings")
    }

    @Test fun cleanLyricsAndSettingsImportKeepPlayerReturnRoutesAndPausedSeekCentered() {
        val model=ready()
        val song=model.library.value.songs.first()
        val photo=Bitmap.createBitmap(16,16,Bitmap.Config.ARGB_8888).apply {eraseColor(android.graphics.Color.RED)}
        File(context.filesDir,"player-background.img").outputStream().use {photo.compress(Bitmap.CompressFormat.PNG,100,it)}
        photo.recycle()
        compose.runOnIdle {
            model.repository.settings(model.library.value.settings.copy(background="photo"))
            model.repository.lyrics(song.id,"[00:00.00]导航测试第一行\n[00:01.00]导航测试第二行")
            model.play(model.library.value.songs,song.id)
        }
        compose.waitUntil(30_000) {model.playback.value.currentId == song.id && model.playback.value.playing}
        compose.runOnIdle {model.toggle();model.seek(300)}
        compose.waitUntil(30_000) {!model.playback.value.playing}
        compose.onNodeWithTag("mini-player").performClick()
        compose.onNodeWithTag("player-page-background").assertIsDisplayed()
        compose.onNodeWithTag("player-artwork").performClick()
        compose.onNodeWithTag("player-lyrics-page").assertIsDisplayed()
        compose.onNodeWithTag("player-page-background").assertDoesNotExist()
        compose.onNodeWithText("${song.title} - ${song.artist}").assertIsDisplayed()
        compose.onNodeWithText("导入歌词").assertDoesNotExist()
        compose.onNodeWithText("回到当前").assertDoesNotExist()
        compose.onNodeWithContentDescription("歌词字体、字号与行间距").assertDoesNotExist()
        compose.onNodeWithContentDescription("添加到歌单").assertDoesNotExist()
        fun centered(text: String) {
            compose.waitUntil(3_000) {
                val viewport=compose.onNodeWithTag("lyrics-list").getUnclippedBoundsInRoot()
                val line=compose.onNodeWithTag("current-lyric").getUnclippedBoundsInRoot()
                kotlin.math.abs((line.top.value+line.bottom.value-viewport.top.value-viewport.bottom.value)/2) < 3 &&
                    compose.onAllNodes(hasTestTag("current-lyric") and hasText(text)).fetchSemanticsNodes().size == 1
            }
            compose.onNodeWithTag("current-lyric").assertTextEquals(text)
        }
        centered("导航测试第一行")
        // Seeking while paused must complete the same centering transition as playback.
        compose.runOnIdle {model.seek(1_300)}
        centered("导航测试第二行")
        assertFalse(model.playback.value.playing)
        compose.onNodeWithTag("player-page-cover").performClick()
        compose.onNodeWithTag("player-page-lyrics").performClick()
        centered("导航测试第二行")
        compose.onNodeWithContentDescription("收起播放器").performClick()
        home("library")
        compose.onNodeWithTag("nav-settings").performClick()
        home("settings")
        compose.onNodeWithText("歌词样式").performScrollTo().performClick()
        compose.onNodeWithTag("page-lyric-style").assertIsDisplayed()
        compose.onNodeWithTag("import-current-lyrics").assertIsEnabled()
        compose.onNodeWithContentDescription("返回").performClick()
        home("settings")
        // Opening the player from a settings child returns to that child, then settings.
        compose.onNodeWithText("歌词样式").performScrollTo().performClick()
        compose.onNodeWithTag("mini-player").performClick()
        compose.onNodeWithTag("player-page-lyrics").performClick()
        centered("导航测试第二行")
        compose.onNodeWithContentDescription("收起播放器").performClick()
        compose.onNodeWithTag("page-lyric-style").assertIsDisplayed()
        compose.onNodeWithContentDescription("返回").performClick()
        home("settings")
        // A retained album group still receives the player collapse action.
        compose.onNodeWithTag("nav-library").performClick()
        compose.onNodeWithText("专辑").performClick()
        compose.onNodeWithText(song.album).performClick()
        compose.onNodeWithTag("mini-player").performClick()
        compose.onNodeWithTag("player-page-lyrics").performClick()
        centered("导航测试第二行")
        compose.onNodeWithContentDescription("收起播放器").performClick()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText("歌曲").assertIsDisplayed()
        home("library")
    }
}
