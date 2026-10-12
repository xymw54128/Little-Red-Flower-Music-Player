package com.xiaohonghua.music

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontLoadingStrategy

internal val lyricFontOptions = listOf("system" to "系统无衬线", "kai" to "文楷", "song" to "宋体",
    "cute" to "花间萌趣", "handdrawn" to "圆润手绘")
private val kaiFont = FontFamily(Font(R.font.lxgw_wenkai,loadingStrategy=FontLoadingStrategy.Async))
private val songFont = FontFamily(Font(R.font.flower_serif,loadingStrategy=FontLoadingStrategy.Async))
private val cuteFont = FontFamily(Font(R.font.flower_cute,loadingStrategy=FontLoadingStrategy.Async))
private val handdrawnFont = FontFamily(Font(R.font.flower_handdrawn,loadingStrategy=FontLoadingStrategy.Async))

// One bundled face per choice is intentional: Android's NativePaint renderer sees
// the same multilingual face as Compose instead of silently losing fallback fonts.
internal fun lyricFont(name: String) = when(normalizeLyricFontName(name)) {
    "kai" -> kaiFont
    "song" -> songFont
    "cute" -> cuteFont
    "handdrawn" -> handdrawnFont
    else -> FontFamily.SansSerif
}
