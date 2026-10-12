package com.xiaohonghua.music

/** Old selections retain a deliberate counterpart after the multilingual font reset. */
internal fun normalizeLyricFontName(name: String): String = when (name) {
    "serif" -> "song"
    "fangsong" -> "kai"
    "mono" -> "cute"
    "system", "kai", "song", "cute", "handdrawn" -> name
    else -> "system"
}

/** Asset names are shared by the license screen and bundled font provenance records. */
internal val lyricFontLicenses = listOf(
    "文楷 · 霞鹜文楷" to "lxgw_wenkai.ttf.OFL.txt",
    "宋体 · 多语组合" to "flower_serif.ttf.OFL.txt",
    "花间萌趣 · 多语组合" to "flower_cute.ttf.OFL.txt",
    "圆润手绘 · 多语组合" to "flower_handdrawn.ttf.OFL.txt"
)
