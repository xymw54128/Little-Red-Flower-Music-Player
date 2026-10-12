package com.xiaohonghua.music

import android.os.Build
import android.view.KeyEvent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Isolated settings and sheets only; never changes the stored library or user's files. */
@RunWith(AndroidJUnit4::class)
class SettingsInteractionUiTest {
    @get:Rule val compose=createComposeRule()
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val song=Song("interaction-test","content://example/interaction-test","test.flac","测试歌曲")

    @Before fun emulatorOnly() {
        check(Build.HARDWARE in listOf("ranchu","goldfish")) {"设置交互测试只能在模拟器中执行"}
    }
    private fun holdSlider(tag: String): SemanticsNodeInteraction {
        val slider=compose.onNodeWithTag(tag).performScrollTo()
        slider.performTouchInput {
            down(Offset(width*.3f,centerY))
            moveTo(Offset(width*.55f,centerY),delayMillis=16)
            moveTo(Offset(width*.82f,centerY),delayMillis=16)
        }
        return slider
    }
    @Test fun lyricImportIsDisabledWithoutCurrentSong() {
        var imports=0
        compose.setContent {MaterialTheme {
            LyricSettings(Settings(),onImport={imports++}) {}
        }}
        compose.onNodeWithText("先播放一首歌曲，再导入对应歌词。").assertIsDisplayed()
        compose.onNodeWithTag("import-current-lyrics").assertIsNotEnabled()
        compose.runOnIdle {assertEquals(0,imports)}
    }
    @Test fun lyricImportUsesCurrentSongAndOnlyInvokesImportAction() {
        var imports=0
        val saves=mutableListOf<Settings>()
        val current=song.copy(title="测试歌曲",artist="测试歌手")
        compose.setContent {MaterialTheme {
            LyricSettings(Settings(),song=current,onImport={imports++}) {saves+=it}
        }}
        compose.onNodeWithText("测试歌曲 - 测试歌手").assertIsDisplayed()
        compose.onNodeWithTag("import-current-lyrics").assertIsEnabled().performClick()
        compose.runOnIdle {
            assertEquals(1,imports)
            assertTrue(saves.isEmpty())
        }
    }
    @Test fun lyricDragPreviewsImmediatelyAndPersistsOnlyOnceOnRelease() {
        val stored=mutableStateOf(Settings())
        val saves=mutableListOf<Settings>()
        val feedback=mutableListOf<String>()
        compose.setContent {MaterialTheme {
            LyricSettings(stored.value,onComplete={feedback+=it}) {saves+=it;stored.value=it}
        }}
        val slider=holdSlider("setting-slider-字号")
        val preview=slider.fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current
        compose.runOnIdle {
            assertTrue(preview > Settings().lyricSize)
            assertEquals(0,saves.size)
            assertEquals(0,feedback.size)
        }
        slider.performTouchInput {up()}
        compose.runOnIdle {
            assertEquals(1,saves.size)
            assertEquals(preview,stored.value.lyricSize,.01f)
            assertEquals(listOf("已保存歌词样式"),feedback)
        }
    }
    @Test fun inlineCreationBackPreservesTheChosenPlaylist() {
        var dismisses=0
        var keyboard: SoftwareKeyboardController?=null
        val playlist=Playlist("target","夜晚")
        compose.setContent {MaterialTheme {
            keyboard=LocalSoftwareKeyboardController.current
            PlaylistPicker(song,listOf(playlist),onDismiss={dismisses++},onAdd={},onCreate={})
        }}
        compose.onNodeWithTag("playlist-choice-target").performClick().assertIsSelected()
        compose.onNodeWithText("新建歌单并添加").performClick()
        compose.onNodeWithTag("playlist-picker-name").performTextInput("新的歌单")
        compose.runOnIdle {keyboard?.hide()}
        instrumentation.waitForIdleSync()
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        compose.waitForIdle()
        compose.onNodeWithText("添加到歌单").assertIsDisplayed()
        compose.onNodeWithTag("playlist-picker-name").assertDoesNotExist()
        compose.onNodeWithTag("playlist-choice-target").assertIsSelected()
        compose.runOnIdle {assertEquals(0,dismisses)}
    }
    @Test fun alreadyAddedOrRemovedTargetsCannotBeSubmitted() {
        val playlists=mutableStateOf(listOf(Playlist("target","夜晚"),Playlist("existing","已有歌单",listOf(song.id))))
        var adds=0
        compose.setContent {MaterialTheme {
            PlaylistPicker(song,playlists.value,onDismiss={},onAdd={adds++},onCreate={})
        }}
        compose.onNodeWithTag("playlist-choice-existing").assertIsNotEnabled()
        compose.onNodeWithTag("playlist-add-confirm").assertIsNotEnabled()
        compose.onNodeWithTag("playlist-choice-target").performClick()
        compose.onNodeWithTag("playlist-add-confirm").assertIsEnabled()
        compose.runOnIdle {playlists.value=playlists.value.map {if(it.id == "target") it.copy(songIds=listOf(song.id)) else it}}
        compose.onNodeWithTag("playlist-choice-target").assertIsNotEnabled().assertIsNotSelected()
        compose.onNodeWithTag("playlist-add-confirm").assertIsNotEnabled()
        compose.runOnIdle {playlists.value=playlists.value.filter {it.id != "target"}}
        compose.onNodeWithTag("playlist-add-confirm").assertIsNotEnabled()
        compose.runOnIdle {assertEquals(0,adds)}
    }
    @Test fun quickRepeatedAddConfirmationOnlySubmitsOnce() {
        val added=mutableListOf<Playlist>()
        val playlist=Playlist("target","夜晚")
        compose.setContent {MaterialTheme {
            PlaylistPicker(song,listOf(playlist),onDismiss={},onAdd={added+=it},onCreate={})
        }}
        compose.onNodeWithTag("playlist-choice-target").performClick()
        // Invoke both clicks before recomposition can remove or disable the button.
        compose.onNodeWithTag("playlist-add-confirm").performSemanticsAction(SemanticsActions.OnClick) {click ->click();click()}
        compose.onNodeWithTag("playlist-add-confirm").assertIsNotEnabled()
        compose.runOnIdle {assertEquals(listOf(playlist),added)}
    }
    @Test fun quickRepeatedCreateConfirmationOnlySubmitsOneTrimmedName() {
        val names=mutableListOf<String>()
        compose.setContent {MaterialTheme {
            PlaylistPicker(song,emptyList(),onDismiss={},onAdd={},onCreate={names+=it})
        }}
        compose.onNodeWithText("新建歌单并添加").performClick()
        compose.onNodeWithTag("playlist-picker-name").performTextInput("  新歌单  ")
        compose.onNodeWithTag("playlist-create-confirm").performSemanticsAction(SemanticsActions.OnClick) {click ->click();click()}
        compose.onNodeWithTag("playlist-create-confirm").assertIsNotEnabled()
        compose.runOnIdle {assertEquals(listOf("新歌单"),names)}
    }
    @Test fun rejectedNameCanBeEditedAndSubmittedAgainWithKeyboardDone() {
        val names=mutableListOf<String>()
        compose.setContent {MaterialTheme {
            // Keeping the dialog open models the parent's validation rejection.
            NameDialog("重命名歌单",onDismiss={},onConfirm={names+=it})
        }}
        val field=compose.onNodeWithTag("playlist-name-field")
        field.performTextInput("我喜欢")
        field.performImeAction()
        compose.onNodeWithText("确定").assertIsNotEnabled()
        field.performTextReplacement("  新名字  ")
        field.performImeAction()
        compose.runOnIdle {assertEquals(listOf("我喜欢","新名字"),names)}
    }
}
