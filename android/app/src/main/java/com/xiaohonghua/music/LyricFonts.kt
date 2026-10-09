package com.xiaohonghua.music

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontLoadingStrategy

internal val lyricFontOptions = listOf("system" to "系统无衬线", "serif" to "系统衬线", "mono" to "等宽字体",
    "kai" to "楷体 · 霞鹜文楷", "song" to "宋体 · 思源宋体", "fangsong" to "仿宋 · 朱雀仿宋")
private val kaiFont = FontFamily(Font(R.font.lxgw_wenkai,loadingStrategy=FontLoadingStrategy.Async))
private val songFont = FontFamily(Font(R.font.source_han_serif_cn,loadingStrategy=FontLoadingStrategy.Async))
private val fangsongFont = FontFamily(Font(R.font.zhuque_fangsong,loadingStrategy=FontLoadingStrategy.Async))
internal fun lyricFont(name: String) = when(name) {
    "serif" -> FontFamily.Serif
    "mono" -> FontFamily.Monospace
    "kai" -> kaiFont
    "song" -> songFont
    "fangsong" -> fangsongFont
    else -> FontFamily.SansSerif
}
