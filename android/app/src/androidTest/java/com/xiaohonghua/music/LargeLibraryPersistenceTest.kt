package com.xiaohonghua.music

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Collections
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class LargeLibraryPersistenceTest {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val context=instrumentation.targetContext

    private fun isolated(name: String,onPreferenceAccess: () -> Unit = {}): Context = object : ContextWrapper(context) {
        override fun getSharedPreferences(key: String,mode: Int): SharedPreferences {
            onPreferenceAccess()
            val delegate=super.getSharedPreferences(name,mode)
            return object : SharedPreferences by delegate {
                override fun edit(): SharedPreferences.Editor {
                    val editor=delegate.edit()
                    return object : SharedPreferences.Editor by editor {
                        override fun putString(key: String,value: String?): SharedPreferences.Editor {
                            onPreferenceAccess();editor.putString(key,value);return this
                        }
                    }
                }
            }
        }
        override fun openOrCreateDatabase(key: String,mode: Int,factory: android.database.sqlite.SQLiteDatabase.CursorFactory?)=
            super.openOrCreateDatabase("$name-$key",mode,factory)
        override fun openOrCreateDatabase(key: String,mode: Int,factory: android.database.sqlite.SQLiteDatabase.CursorFactory?,handler: android.database.DatabaseErrorHandler?)=
            super.openOrCreateDatabase("$name-$key",mode,factory,handler)
    }

    @Test fun thousandSongEditsNeverAccessPreferencesOnMainAndLastWriteWinsAcrossRestore() {
        val name="large-library-"+UUID.randomUUID()
        val mainAccesses=Collections.synchronizedList(mutableListOf<String>())
        val isolated=isolated(name) {if(Looper.myLooper() == Looper.getMainLooper()) mainAccesses += Thread.currentThread().name}
        var repository: MusicRepository?=null
        try {
            val songs=(0 until 1000).map {Song("song-$it","content://test/song-$it","$it.flac","歌曲 $it")}
            val ids=songs.map {it.id}
            LibraryStore(isolated).use {store ->
                store.replaceSongs(songs)
                store.savePlaylists(listOf(Playlist("large","千首收藏",songIds=ids)))
                store.saveSettings(Settings(theme=2,nightMode=true))
            }
            val constructor=MusicRepository::class.java.getDeclaredConstructor(Context::class.java).apply {isAccessible=true}
            instrumentation.runOnMainSync {repository=constructor.newInstance(isolated)}
            val repo=repository!!
            runBlocking {withTimeout(10_000) {repo.state.first {it.ready}}}
            assertEquals(1000,repo.state.value.songs.size)
            assertTrue(repo.state.value.settings.nightMode)
            val backup=repo.backup()
            instrumentation.runOnMainSync {
                repeat(30) {repo.toggleFavorite("song-$it");repo.settings(Settings(theme=it%5))}
                repo.customOrder(ids.reversed(),"large")
            }
            runBlocking {repo.restore(backup)}
            instrumentation.runOnMainSync {
                repo.customOrder(ids.reversed(),"large")
                repo.addPlaylistSongs(FAVORITES_PLAYLIST_ID,ids)
                repeat(30) {repo.settings(Settings(theme=it%5,nightMode=true))}
                repo.lyrics("song-999","[00:01]最终歌词")
            }
            runBlocking {withTimeout(10_000) {
                while(true) {
                    val stored=LibraryStore(isolated)
                    val persisted=try {
                        stored.settings() == Settings(theme=4,nightMode=true) &&
                            stored.playlists().first {it.id == "large"}.songIds == ids.reversed() &&
                            stored.playlists().first {it.isFavorites}.songIds.size == 1000 &&
                            stored.manualLyrics()["song-999"] == "[00:01]最终歌词"
                    } finally {stored.close()}
                    if(persisted) break
                    delay(20)
                }
            }}
            assertTrue("Repository construction or an edit touched preferences on main: $mainAccesses",mainAccesses.isEmpty())
        } finally {
            repository?.let {repo ->
                val scope=MusicRepository::class.java.getDeclaredField("scope").apply {isAccessible=true}.get(repo) as CoroutineScope
                runBlocking {scope.coroutineContext[Job]!!.cancelAndJoin()}
                repo.store.close()
            }
            context.deleteSharedPreferences(name);context.deleteDatabase("$name-music.db")
        }
    }

    @Test fun thousandSongMetadataUpdatesPreserveOrderAndCancelledReplacementRollsBack() {
        val name="large-transaction-"+UUID.randomUUID()
        val store=LibraryStore(isolated(name))
        try {
            val songs=(0 until 1000).map {Song("song-$it","content://test/song-$it","$it.flac","歌曲 $it")}
            store.replaceSongs(songs)
            val changed=songs.mapIndexed {index,song -> if(index%10 == 0) song.copy(album="已刷新专辑") else song}
            store.replaceSongs(changed)
            assertEquals(changed,store.songs())
            var checked=0
            try {
                store.replaceSongs(changed.take(500)) {if(++checked == 20) throw CancellationException("Cancel test")}
                fail("Cancellation was swallowed")
            } catch(_: CancellationException) {}
            assertEquals(changed,store.songs())
        } finally {
            store.close();context.deleteSharedPreferences(name);context.deleteDatabase("$name-music.db")
        }
    }
}
