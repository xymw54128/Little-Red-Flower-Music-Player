package com.xiaohonghua.music

import android.content.ContentValues
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.view.KeyEvent
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Job
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
            store.saveSettings(Settings())
            store.replaceSongs(songs)
            val repository=MusicRepository.get(context)
            runBlocking {
                withTimeout(30_000) {repository.state.first {it.ready}}
                val backup=JSONObject(repository.backup()).apply {
                    put("songs",JSONArray(songs.map {it.toJson(true)}))
                    put("playlists",JSONArray())
                    put("folders",JSONArray())
                    put("manualLyrics",JSONObject())
                    put("lyricOffsets",JSONObject(songs.associate {it.id to 500L}))
                    put("recentPlayed",JSONArray())
                    put("settings",Settings().toJson())
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
            model.library.value.ready && !model.library.value.busy && model.library.value.songs.size == 2 && model.playback.value.connected &&
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

    private fun playlistOption(label: String) {
        compose.onNodeWithTag("playlist-menu").performClick()
        compose.onNodeWithText(label).performClick()
    }


    private fun sequentialPlayback(model: MusicModel) {
        repeat(4) {
            val previous=model.playback.value.mode
            if(previous == 0) return
            compose.runOnIdle {model.mode()}
            compose.waitUntil(30_000) {model.playback.value.mode != previous}
        }
        assertEquals(0,model.playback.value.mode)
    }

    // Await the actual worker rather than a sleep: its result can only commit on Main
    // after the same-turn user action below. No production-only scheduler hook is needed.
    private fun sourceSortJob(model: MusicModel): Job = MusicModel::class.java.getDeclaredField("sortJob")
        .apply {isAccessible=true}.get(model) as Job

    private fun awaitSourceSort(job: Job) {runBlocking {withTimeout(30_000) {job.join()}}}

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
        compose.onNodeWithTag("playlist-search-field").assertDoesNotExist()
        compose.onNodeWithText("播放全部").assertDoesNotExist()
        compose.onNodeWithTag("playlist-menu").performClick()
        compose.onNodeWithText("删除歌单").assertDoesNotExist()
        compose.onNodeWithText("编辑歌单").performClick()
        compose.onNodeWithTag("playlist-edit-name").assertIsNotEnabled()
        back()
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

    @Test fun favoritesHeartAndRowExitSurviveFullAppAsyncSortReload() {
        val model=ready()
        val songs=model.library.value.songs
        compose.runOnIdle {model.repository.addPlaylistSongs(FAVORITES_PLAYLIST_ID,songs.map {it.id})}
        compose.onNodeWithTag("nav-playlists").performClick()
        home("playlists")
        compose.onNodeWithTag("playlist-"+FAVORITES_PLAYLIST_ID).performClick()
        compose.waitUntil(30_000) {compose.onAllNodesWithTag("favorite-"+songs.first().id).fetchSemanticsNodes().isNotEmpty()}
        compose.waitForIdle()
        compose.onNodeWithTag("page-playlist").assertIsDisplayed()
        compose.mainClock.autoAdvance=false
        for((index,song) in songs.withIndex()) {
            val button=compose.onNodeWithTag("favorite-"+song.id)
            val row=compose.onNodeWithTag("favorite-row-exit-"+song.id)
            val initialHeight=row.fetchSemanticsNode().boundsInRoot.height
            button.assertIsSelected().performClick()
            compose.runOnIdle {assertFalse(song.id in model.library.value.playlists.first {it.isFavorites}.songIds)}
            compose.mainClock.advanceTimeBy(400)
            button.assertExists().assertIsNotSelected()
            assertEquals(initialHeight,row.fetchSemanticsNode().boundsInRoot.height,1f)
            compose.onNodeWithTag("song-"+song.id).performClick() // A retained outgoing row must not play another source item.
            compose.onNodeWithTag("playlist-song-count").assertTextEquals("共 "+(songs.size-index)+" 首")
            compose.onNodeWithText("歌单还没有歌曲").assertDoesNotExist()
            compose.mainClock.advanceTimeBy(272)
            val shrinkingHeight=row.fetchSemanticsNode().boundsInRoot.height
            assertTrue("Row exit follows the heart and is visibly collapsing",shrinkingHeight > 0f && shrinkingHeight < initialHeight)
            compose.mainClock.advanceTimeBy(320)
            button.assertDoesNotExist()
        }
        compose.onNodeWithText("歌单还没有歌曲").assertIsDisplayed()
        compose.runOnIdle {
            assertTrue(model.library.value.playlists.first {it.isFavorites}.songIds.isEmpty())
            assertNull(model.playback.value.currentId)
        }
        compose.mainClock.autoAdvance=true
    }

    @Test fun playlistCoverOpensDetailsWithoutChangingPlaybackAndManagementStaysInItsRoute() {
        val model=ready()
        val songs=model.library.value.songs
        lateinit var playlist: Playlist
        compose.runOnIdle {
            playlist=model.repository.createPlaylist("歌单交互测试")
            model.repository.playlist(playlist.copy(songIds=songs.map {it.id}.reversed(),sort=SortMode.CUSTOM,description="春日里的音乐"))
            model.play(songs,songs.first().id)
        }
        compose.waitUntil(30_000) {model.playback.value.currentId == songs.first().id && model.playback.value.playing}
        compose.runOnIdle {model.toggle();model.seek(400)}
        compose.waitUntil(30_000) {!model.playback.value.playing && model.playback.value.position == 400L}
        val playingId=model.playback.value.currentId
        val queue=model.playback.value.queue
        val queueSource=model.playback.value.queueSource
        val position=model.playback.value.position
        compose.onNodeWithTag("nav-playlists").performClick()
        home("playlists")
        compose.onNodeWithTag("playlist-card-cover-${playlist.id}",useUnmergedTree=true)
            .performScrollTo().performTouchInput {click()}
        compose.onNodeWithTag("page-playlist").assertIsDisplayed()
        compose.onNodeWithTag("playlist-summary-cover").assertHasNoClickAction().performTouchInput {click()}
        compose.runOnIdle {
            assertEquals(playingId,model.playback.value.currentId)
            assertEquals(queue,model.playback.value.queue)
            assertEquals(queueSource,model.playback.value.queueSource)
            assertEquals(position,model.playback.value.position)
            assertFalse(model.playback.value.playing)
        }
        compose.onNodeWithText("播放全部").assertDoesNotExist()
        compose.onNodeWithTag("playlist-description").assertTextEquals("春日里的音乐")
        compose.onNodeWithTag("playlist-song-count").assertTextEquals("共 2 首")
        compose.onNodeWithTag("playlist-search-field").assertDoesNotExist()
        compose.onNodeWithTag("open-playlist-search").performClick()
        compose.onNodeWithTag("playlist-search-field").performTextInput("no-playlist-song-match")
        compose.onNodeWithText("没有找到匹配的歌曲").assertIsDisplayed()
        compose.onNodeWithTag("playlist-empty-add").assertDoesNotExist()
        assertEquals(playingId,model.playback.value.currentId)
        assertFalse(model.playback.value.playing)
        compose.onNodeWithTag("close-playlist-search").performClick()
        compose.onNodeWithTag("playlist-search-field").assertDoesNotExist()
        playlistOption("编辑歌单")
        compose.onNodeWithTag("playlist-edit-name").performTextReplacement("修改后的歌单")
        compose.onNodeWithTag("playlist-edit-description").performTextReplacement("夜晚的音乐")
        compose.onNodeWithTag("playlist-edit-cover").performScrollTo().performClick()
        back()
        compose.onNodeWithTag("playlist-edit-name").assertTextContains("修改后的歌单")
        compose.onNodeWithTag("playlist-edit-description").assertTextContains("夜晚的音乐")
        compose.onNodeWithTag("playlist-edit-save").performClick()
        compose.waitUntil(30_000) {model.library.value.playlists.first {it.id == playlist.id}.description == "夜晚的音乐"}
        compose.onNodeWithTag("playlist-description").assertTextEquals("夜晚的音乐")
        playlistOption("批量添加音乐")
        songs.forEach {song -> compose.onNodeWithTag("playlist-song-choice-${song.id}").assertIsNotEnabled()}
        compose.onNodeWithTag("playlist-songs-confirm").assertIsNotEnabled()
        back()
        compose.onNodeWithTag("page-playlist").assertIsDisplayed()
        playlistOption("删除歌曲")
        compose.onNodeWithTag("playlist-song-choice-${songs.last().id}").performClick()
        compose.onNodeWithTag("playlist-songs-confirm").performClick()
        compose.waitUntil(30_000) {songs.last().id !in model.library.value.playlists.first {it.id == playlist.id}.songIds}
        compose.onNodeWithTag("playlist-song-count").assertTextEquals("共 1 首")
        playlistOption("批量添加音乐")
        compose.onNodeWithTag("playlist-song-choice-${songs.last().id}").assertIsEnabled().performClick()
        compose.onNodeWithTag("playlist-songs-confirm").performClick()
        compose.waitUntil(30_000) {model.library.value.playlists.first {it.id == playlist.id}.songIds.size == 2}
        compose.onNodeWithTag("page-playlist").assertIsDisplayed()
        compose.onNodeWithTag("playlist-song-count").assertTextEquals("共 2 首")
        assertEquals(playingId,model.playback.value.currentId)
        assertFalse(model.playback.value.playing)
        assertEquals(songs,model.library.value.songs)
        back()
        home("playlists")
    }

    @Test fun childPagesAndSongSheetsReturnToTheirCaller() {
        val model=ready()
        compose.onNodeWithTag("nav-settings").performClick()
        home("settings")
        for((label,page) in listOf("歌词样式" to "lyric-style","主题颜色" to "theme","歌曲文件夹" to "folders","备份与恢复" to "backup")) {
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
        playlistOption("删除歌单")
        back()
        assertTrue(model.library.value.playlists.any {it.id == playlist.id})
        playlistOption("删除歌单")
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
            model.repository.playlist(playlist.copy(songIds=songs.map {it.id}.reversed(),randomIds=songs.map {it.id},coverPreset=8))
            model.repository.createPlaylist("撤销后面的歌单")
            assertNull(model.repository.deletePlaylist(FAVORITES_PLAYLIST_ID))
        }
        val playlistOrder=model.library.value.playlists.map {it.id}
        compose.onNodeWithTag("nav-playlists").performClick()
        home("playlists")
        compose.onNodeWithTag("playlist-${playlist.id}").performScrollTo().performClick()
        playlistOption("编辑歌单")
        compose.onNodeWithTag("playlist-edit-name").performTextReplacement("已编辑的撤销测试歌单")
        compose.onNodeWithTag("playlist-edit-save").performClick()
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
            laterEdit=current.copy(name="撤销保留后来的编辑",coverPreset=9,sort=SortMode.ARTIST)
            model.repository.playlist(laterEdit)
        }
        compose.onNodeWithText("撤销").assertIsDisplayed().performClick()
        compose.waitUntil(30_000) {removedId in model.library.value.playlists.first {it.id == playlist.id}.songIds}
        val restoredSongList=model.library.value.playlists.first {it.id == playlist.id}
        assertEquals(laterEdit.copy(songIds=beforeRemoval.songIds,randomIds=beforeRemoval.randomIds),restoredSongList)
        assertEquals(playlistOrder,model.library.value.playlists.map {it.id})

        playlistOption("删除歌单")
        compose.onNodeWithText("删除").performClick()
        home("playlists")
        assertFalse(model.library.value.playlists.any {it.id == playlist.id})
        compose.onNodeWithText("撤销").assertIsDisplayed().performClick()
        compose.waitUntil(30_000) {model.library.value.playlists.any {it.id == playlist.id}}
        assertEquals(playlistOrder,model.library.value.playlists.map {it.id})
        assertEquals(restoredSongList,model.library.value.playlists.first {it.id == playlist.id})
        assertEquals(restoredSongList,LibraryStore(context).playlists().first {it.id == playlist.id})
        compose.onNodeWithTag("playlist-$FAVORITES_PLAYLIST_ID").performScrollTo().performClick()
        compose.onNodeWithTag("playlist-menu").performClick()
        compose.onNodeWithText("删除歌单").assertDoesNotExist()
        compose.onNodeWithText("编辑歌单").performClick()
        compose.onNodeWithTag("playlist-edit-name").assertIsNotEnabled()
        back()
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
            model.repository.lyrics(song.id,"[00:00.00]导航测试第一行\n[00:01.00]导航测试第二行")
            model.play(model.library.value.songs,song.id)
        }
        compose.waitUntil(30_000) {model.playback.value.currentId == song.id && model.playback.value.playing}
        compose.runOnIdle {model.toggle();model.seek(300)}
        compose.waitUntil(30_000) {!model.playback.value.playing}
        compose.onNodeWithTag("mini-player").performClick()
        compose.onNodeWithTag("player-page-background").assertDoesNotExist()
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
    @Test fun albumSourceUsesSafePersistenceAndLibrarySortDoesNotReorderItsQueue() {
        val model=ready()
        val song=model.library.value.songs.first()
        val expected=albumSongs(model.library.value.songs.filter {it.albumKey == song.albumKey})
        compose.onNodeWithText("专辑").performClick()
        compose.onNodeWithText(song.album).performClick()
        compose.onNodeWithText("盘号 · 曲号").assertIsDisplayed()
        compose.onNodeWithTag("song-${song.id}").performClick()
        val source="album:"+Uri.encode(song.albumKey)
        compose.waitUntil(30_000) {model.playback.value.queueSource == source && model.playback.value.playing}
        assertFalse(source.contains('\u0000')) // The album separator must not enter SharedPreferences XML.
        assertEquals(song.albumKey,Uri.decode(source.substringAfter("album:")))
        compose.runOnIdle {model.toggle();model.seek(400)}
        compose.waitUntil(30_000) {!model.playback.value.playing}
        val queue=model.playback.value.queue
        assertEquals(expected.map {it.id},queue)
        compose.runOnIdle {model.applySort(SortMode.RANDOM)}
        compose.waitForIdle()
        assertEquals(queue,model.playback.value.queue)
        assertEquals(song.id,model.playback.value.currentId)
        assertEquals(source,model.playback.value.queueSource)
        assertFalse(model.playback.value.playing)
        compose.waitUntil(30_000) {LibraryStore(context).prefs.getString("queueSource",null) == source}
    }
    @Test fun songMetadataPlaylistDescriptionAndArtworkReturnPreserveQueue() {
        val model=ready()
        val songs=model.library.value.songs
        val song=songs.first()
        val row=hasAnyAncestor(hasTestTag("song-${song.id}"))
        compose.onNode(hasText("${song.artist} · ${song.album}") and row,useUnmergedTree=true).assertIsDisplayed()
        if(song.format.isNotBlank()) compose.onAllNodes(hasText(song.format) and row,useUnmergedTree=true).assertCountEquals(0)
        // Preparing an empty queue through the menu must stay silent and must not count as a listen.
        compose.onNode(hasContentDescription("歌曲操作") and row).performClick()
        compose.onNodeWithText("追加队尾").performClick()
        compose.waitUntil(30_000) {model.playback.value.queue == listOf(song.id)}
        assertFalse(model.playback.value.playing)
        lateinit var playlist: Playlist
        compose.runOnIdle {
            playlist=model.repository.createPlaylist("升级功能导航测试")
            model.repository.playlist(playlist.copy(songIds=songs.map {it.id},description="让花朵陪伴每一段旋律"))
        }
        compose.onNodeWithTag("nav-playlists").performClick()
        home("playlists")
        compose.onNodeWithTag("playlist-card-description-${playlist.id}").assertTextEquals("让花朵陪伴每一段旋律")
        compose.onNodeWithTag("playlist-${playlist.id}").performClick()
        compose.onNodeWithTag("playlist-description").assertTextEquals("让花朵陪伴每一段旋律")
        compose.onNode(hasContentDescription("歌曲操作") and row).performClick()
        compose.onNodeWithText("歌曲信息").performClick()
        compose.onNodeWithTag("song-info-cover").performScrollTo().performClick()
        compose.onNodeWithTag("artwork-viewer").assertIsDisplayed()
        compose.onNodeWithTag("artwork-close").assertExists()
        back()
        compose.onNodeWithTag("artwork-viewer").assertDoesNotExist()
        compose.onNodeWithText("歌曲信息").assertIsDisplayed()
        compose.onNodeWithTag("song-info-cover").assertIsDisplayed()
        back()
        compose.onNodeWithTag("page-playlist").assertIsDisplayed()
        compose.onNodeWithTag("song-${song.id}").performClick()
        compose.waitUntil(30_000) {model.playback.value.playing}
        compose.runOnIdle {model.toggle();model.seek(400)}
        compose.waitUntil(30_000) {!model.playback.value.playing}
        assertEquals(songs,model.library.value.songs)
        assertTrue(model.library.value.playlists.any {it.id == playlist.id})
    }

    @Test fun nightThemeAndStandardLyricTimingSurviveReturningToSettings() {
        val model=ready()
        val song=model.library.value.songs.first()
        compose.onNodeWithTag("nav-settings").performClick()
        compose.onNodeWithText("主题颜色").performScrollTo().performClick()
        compose.onNodeWithTag("night-theme").assertIsOff().performClick()
        compose.waitUntil(30_000) {model.library.value.settings.nightMode}
        assertTrue(LibraryStore(context).settings().nightMode)
        val nightPixel=compose.onNodeWithTag("page-theme").captureToImage().toPixelMap()[0,0]
        assertTrue("The actual page background must become dark",nightPixel.red < .2f && nightPixel.green < .2f && nightPixel.blue < .2f)
        compose.activityRule.scenario.recreate()
        compose.waitUntil(30_000) {compose.onAllNodesWithTag("launch-animation").fetchSemanticsNodes().isEmpty()}
        compose.onNodeWithTag("page-theme").assertIsDisplayed()
        compose.onNodeWithTag("night-theme").assertIsOn()
        back()
        home("settings")
        compose.onNodeWithText("睡眠计时").assertDoesNotExist()
        compose.onNodeWithText("最近播放").assertDoesNotExist()
        compose.onNodeWithText("播放背景").assertDoesNotExist()
        compose.onNodeWithTag("nav-library").performClick()
        compose.runOnIdle {
            model.repository.lyrics(song.id,"""[00:00.00]标准第一行
[00:01.00]标准第二行""")
            model.play(model.library.value.songs,song.id)
        }
        compose.waitUntil(30_000) {model.playback.value.playing}
        compose.runOnIdle {model.toggle();model.seek(1_050)}
        compose.waitUntil(30_000) {!model.playback.value.playing}
        compose.onNodeWithTag("mini-player").performClick()
        compose.onNodeWithTag("player-page-lyrics").performClick()
        compose.onNodeWithTag("player-sleep-timer").assertDoesNotExist()
        compose.onNodeWithContentDescription("添加到歌单").assertDoesNotExist()
        compose.onNodeWithText("${song.title} - ${song.artist}").assertIsDisplayed()
        compose.waitUntil(30_000) {
            compose.onAllNodes(hasTestTag("current-lyric") and hasText("标准第二行")).fetchSemanticsNodes().size == 1
        }
        compose.onNodeWithTag("current-lyric").assertTextEquals("标准第二行")
        compose.onNodeWithContentDescription("收起播放器").performClick()
        compose.onNodeWithTag("nav-settings").performClick()
        compose.onNodeWithText("歌词样式").performScrollTo().performClick()
        compose.onNodeWithText("歌词时间校准").assertDoesNotExist()
        compose.onNodeWithTag("lyric-offset-slider").assertDoesNotExist()
        compose.onNodeWithTag("lyric-offset-plus").assertDoesNotExist()
        compose.onNodeWithTag("lyric-offset-reset").assertDoesNotExist()
        compose.onNodeWithTag("import-current-lyrics").assertIsDisplayed()
        assertFalse(model.playback.value.playing)
        assertEquals(1_050L,model.playbackPosition())
        back()
        home("settings")
    }


    @Test fun sharedSongSwitchesToTheNewPlaylistsWholeQueueAndItsNextTrack() {
        val model=ready()
        sequentialPlayback(model)
        val songs=orderedSongs(model.library.value.songs,SortMode.TITLE,emptyList(),emptyList())
        val ids=songs.map {it.id}
        lateinit var first: Playlist
        lateinit var second: Playlist
        compose.runOnIdle {
            first=model.repository.createPlaylist("来源 A")
            second=model.repository.createPlaylist("来源 B")
            model.repository.playlist(first.copy(songIds=ids,sort=SortMode.CUSTOM))
            model.repository.playlist(second.copy(songIds=ids.reversed(),sort=SortMode.CUSTOM))
            model.play(songs,ids.last(),first.id)
        }
        compose.waitUntil(30_000) {
            model.playback.value.queue == ids && model.playback.value.currentId == ids.last() &&
                model.playback.value.queueSource == first.id
        }
        // Selecting the same song from another playlist must replace the origin too.
        compose.runOnIdle {model.play(songs.reversed(),ids.last(),second.id)}
        compose.waitUntil(30_000) {
            model.playback.value.queue == ids.reversed() && model.playback.value.currentId == ids.last() &&
                model.playback.value.queueSource == second.id
        }
        compose.runOnIdle {model.next()}
        compose.waitUntil(30_000) {model.playback.value.currentId == ids.first()}
        compose.runOnIdle {
            assertEquals(ids.reversed(),model.playback.value.queue)
            assertEquals(second.id,model.playback.value.queueSource)
        }
    }

    @Test fun inactivePlaylistSortCannotCancelThePlayingLibraryOrPlaylistOrder() {
        val model=ready()
        sequentialPlayback(model)
        val songs=orderedSongs(model.library.value.songs,SortMode.TITLE,emptyList(),emptyList())
        val ids=songs.map {it.id}
        lateinit var first: Playlist
        lateinit var second: Playlist
        compose.runOnIdle {
            first=model.repository.createPlaylist("活动顺序")
            second=model.repository.createPlaylist("非活动顺序")
            model.repository.playlist(first.copy(songIds=ids,sort=SortMode.CUSTOM))
            model.repository.playlist(second.copy(songIds=ids.reversed(),sort=SortMode.CUSTOM))
        }
        for(source in listOf<String?>(null,first.id)) {
            val expectedSource=source ?: LIBRARY_QUEUE_SOURCE
            val inactive=if(source == null) first.id else second.id
            compose.runOnIdle {
                model.repository.customOrder(ids,source)
                model.play(songs,ids.first(),source)
            }
            compose.waitUntil(30_000) {
                model.playback.value.queueSource == expectedSource && model.playback.value.queue == ids && model.playback.value.playing
            }
            compose.runOnIdle {model.toggle();model.seek(500)}
            compose.waitUntil(30_000) {!model.playback.value.playing && model.playback.value.position == 500L}
            if(model.playback.value.mode == 0) {
                compose.runOnIdle {model.mode()}
                compose.waitUntil(30_000) {model.playback.value.mode == 1}
            }
            lateinit var pending: Job
            compose.runOnIdle {
                model.customOrder(ids.reversed(),source)
                pending=sourceSortJob(model)
                model.customOrder(ids,inactive)
                assertFalse("Editing another playlist must not cancel the active source worker",pending.isCancelled)
            }
            awaitSourceSort(pending)
            compose.waitUntil(30_000) {model.playback.value.queue == ids.reversed()}
            compose.runOnIdle {
                assertEquals(expectedSource,model.playback.value.queueSource)
                assertEquals(ids.first(),model.playback.value.currentId)
                assertEquals(500L,model.playbackPosition())
                assertFalse(model.playback.value.playing)
                assertEquals(1,model.playback.value.mode)
            }
        }
        compose.waitUntil(30_000) {
            val saved=LibraryStore(context).playlists()
            saved.first {it.id == first.id}.songIds == ids.reversed() && saved.first {it.id == second.id}.songIds == ids
        }
    }

    @Test fun aLaterManualQueueOrderWinsOverAnAlreadyScheduledSourceSort() {
        val model=ready()
        sequentialPlayback(model)
        val songs=orderedSongs(model.library.value.songs,SortMode.TITLE,emptyList(),emptyList())
        val ids=songs.map {it.id}
        lateinit var playlist: Playlist
        compose.runOnIdle {
            playlist=model.repository.createPlaylist("队列覆盖回归")
            model.repository.playlist(playlist.copy(songIds=ids,sort=SortMode.CUSTOM))
            model.play(songs,ids.first(),playlist.id)
        }
        compose.waitUntil(30_000) {model.playback.value.queue == ids && model.playback.value.playing}
        compose.runOnIdle {model.toggle();model.seek(600)}
        compose.waitUntil(30_000) {!model.playback.value.playing && model.playback.value.position == 600L}
        lateinit var obsolete: Job
        compose.runOnIdle {
            model.customOrder(ids.reversed(),playlist.id)
            obsolete=sourceSortJob(model)
            // The requested manual order equals the current timeline, but must still
            // cancel the older worker before it can move that timeline in reverse.
            model.queueOrder(ids)
            assertTrue("A newer explicit queue order cancels the older source sort",obsolete.isCancelled)
        }
        awaitSourceSort(obsolete)
        compose.waitUntil(30_000) {model.playback.value.queue == ids}
        compose.runOnIdle {
            assertEquals(playlist.id,model.playback.value.queueSource)
            assertEquals(ids.first(),model.playback.value.currentId)
            assertEquals(600L,model.playbackPosition())
            assertFalse(model.playback.value.playing)
            assertEquals(ids.reversed(),model.library.value.playlists.first {it.id == playlist.id}.songIds)
        }
    }

    @Test fun latestFavoriteSortWinsOverRetainedRowsAndPreservesTheSelectedSubset() {
        val model=ready()
        sequentialPlayback(model)
        val staleRows=orderedSongs(model.library.value.songs,SortMode.TITLE,emptyList(),emptyList())
        val ids=staleRows.map {it.id}
        compose.runOnIdle {
            model.repository.addPlaylistSongs(FAVORITES_PLAYLIST_ID,ids)
            // The favorite UI deliberately retains these old rows while its new sort
            // prepares. The playback entry point must use the latest stored source.
            model.customOrder(ids.reversed(),FAVORITES_PLAYLIST_ID)
            model.play(staleRows,ids.last(),FAVORITES_PLAYLIST_ID)
        }
        compose.waitUntil(30_000) {
            model.playback.value.queue == ids.reversed() && model.playback.value.currentId == ids.last() &&
                model.playback.value.queueSource == FAVORITES_PLAYLIST_ID
        }
        compose.waitUntil(30_000) {LibraryStore(context).playlists().first {it.isFavorites}.songIds == ids.reversed()}
        compose.runOnIdle {model.play(listOf(staleRows.first()),ids.first(),FAVORITES_PLAYLIST_ID)}
        compose.waitUntil(30_000) {
            model.playback.value.queue == listOf(ids.first()) && model.playback.value.currentId == ids.first()
        }
        compose.runOnIdle {assertEquals(FAVORITES_PLAYLIST_ID,model.playback.value.queueSource)}
    }


    @Test fun rapidModeCycleUsesControllerStateAndSourceSortKeepsTheLaterMode() {
        val model=ready()
        sequentialPlayback(model)
        val songs=orderedSongs(model.library.value.songs,SortMode.TITLE,emptyList(),emptyList())
        val ids=songs.map {it.id}
        lateinit var playlist: Playlist
        compose.runOnIdle {
            playlist=model.repository.createPlaylist("快速播放模式")
            model.repository.playlist(playlist.copy(songIds=ids,sort=SortMode.CUSTOM))
            model.play(songs,ids.first(),playlist.id)
        }
        compose.waitUntil(30_000) {model.playback.value.queue == ids && model.playback.value.playing}
        compose.runOnIdle {model.toggle();model.seek(700)}
        compose.waitUntil(30_000) {!model.playback.value.playing && model.playback.value.position == 700L}
        lateinit var firstSort: Job
        compose.runOnIdle {
            model.customOrder(ids.reversed(),playlist.id)
            firstSort=sourceSortJob(model)
            repeat(4) {model.mode()}
            // Pull a fresh controller snapshot without waiting for Flow between
            // presses. Returning to sequential mode must not discard this sort.
            model.seek(700)
            assertEquals(0,model.playback.value.mode)
            assertFalse("Cycling modes must not discard the pending source order",firstSort.isCancelled)
        }
        awaitSourceSort(firstSort)
        compose.waitUntil(30_000) {model.playback.value.queue == ids.reversed()}
        compose.runOnIdle {
            model.seek(700)
            assertEquals(0,model.playback.value.mode)
            assertEquals(ids.reversed(),model.playback.value.queue)
        }
        lateinit var secondSort: Job
        compose.runOnIdle {
            model.customOrder(ids,playlist.id)
            secondSort=sourceSortJob(model)
            repeat(3) {model.mode()}
            model.seek(700)
            assertEquals(3,model.playback.value.mode)
            assertFalse("The saved source order still applies after a newer mode choice",secondSort.isCancelled)
        }
        awaitSourceSort(secondSort)
        compose.waitUntil(30_000) {model.playback.value.queue == ids}
        compose.runOnIdle {
            model.seek(700)
            assertEquals(3,model.playback.value.mode)
            assertEquals(ids,model.playback.value.queue)
            assertEquals(playlist.id,model.playback.value.queueSource)
            assertFalse(model.playback.value.playing)
        }
    }

    @Test fun enqueueingTheCurrentSongIsANoOpAndDoesNotCancelItsSourceSort() {
        val model=ready()
        sequentialPlayback(model)
        val songs=orderedSongs(model.library.value.songs,SortMode.TITLE,emptyList(),emptyList())
        val ids=songs.map {it.id}
        lateinit var playlist: Playlist
        compose.runOnIdle {
            playlist=model.repository.createPlaylist("当前歌曲不改队列")
            model.repository.playlist(playlist.copy(songIds=ids,sort=SortMode.CUSTOM))
            model.play(songs,ids.first(),playlist.id)
        }
        compose.waitUntil(30_000) {model.playback.value.queue == ids && model.playback.value.playing}
        compose.runOnIdle {model.toggle();model.seek(650)}
        compose.waitUntil(30_000) {!model.playback.value.playing && model.playback.value.position == 650L}
        lateinit var pending: Job
        compose.runOnIdle {
            model.customOrder(ids.reversed(),playlist.id)
            pending=sourceSortJob(model)
            assertTrue(model.appendQueue(songs.first()))
            assertTrue(model.playNext(songs.first()))
            assertFalse("A no-op current-song request must not discard the saved source order",pending.isCancelled)
        }
        awaitSourceSort(pending)
        compose.waitUntil(30_000) {model.playback.value.queue == ids.reversed()}
        compose.runOnIdle {
            assertEquals(playlist.id,model.playback.value.queueSource)
            assertEquals(ids.first(),model.playback.value.currentId)
            assertEquals(650L,model.playbackPosition())
            assertFalse(model.playback.value.playing)
        }
    }

}
