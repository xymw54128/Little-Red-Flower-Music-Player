package com.xiaohonghua.music

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontLoadingStrategy

private val kaiFont = FontFamily(Font(R.font.lxgw_wenkai,loadingStrategy=FontLoadingStrategy.Async))
private val songFont = FontFamily(Font(R.font.flower_serif,loadingStrategy=FontLoadingStrategy.Async))
private val xiaoweiFont = FontFamily(Font(R.font.flower_xiaowei,loadingStrategy=FontLoadingStrategy.Async))
private val zenmaruFont = FontFamily(Font(R.font.flower_zenmaru,loadingStrategy=FontLoadingStrategy.Async))
private val kiwiFont = FontFamily(Font(R.font.flower_kiwi,loadingStrategy=FontLoadingStrategy.Async))
private val kurenaidoFont = FontFamily(Font(R.font.flower_kurenaido,loadingStrategy=FontLoadingStrategy.Async))
private val longcangFont = FontFamily(Font(R.font.flower_longcang,loadingStrategy=FontLoadingStrategy.Async))

// One bundled face per choice is intentional: Android's NativePaint renderer sees
// the same multilingual face as Compose instead of silently losing fallback fonts.
internal fun lyricFont(name: String) = when(normalizeLyricFontName(name)) {
    "kai" -> kaiFont
    "song" -> songFont
    "xiaowei" -> xiaoweiFont
    "zenmaru" -> zenmaruFont
    "kiwi" -> kiwiFont
    "kurenaido" -> kurenaidoFont
    "longcang" -> longcangFont
    else -> FontFamily.SansSerif
}
