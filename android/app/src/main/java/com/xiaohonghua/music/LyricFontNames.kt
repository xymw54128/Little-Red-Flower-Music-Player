package com.xiaohonghua.music

/** Old selections retain a deliberate counterpart after the multilingual font reset. */
internal fun normalizeLyricFontName(name: String): String = when (name) {
    "serif" -> "song"
    "fangsong" -> "kai"
    "mono", "cute" -> "kiwi"
    "handdrawn" -> "kurenaido"
    "system", "kai", "song", "xiaowei", "zenmaru", "kiwi", "kurenaido", "longcang" -> name
    else -> "system"
}

internal val lyricFontOptions = listOf(
    "system" to "系统无衬线", "kai" to "文楷", "song" to "宋体",
    "xiaowei" to "小薇宋意", "zenmaru" to "禅丸圆体", "kiwi" to "奇异果圆体",
    "kurenaido" to "红道手写", "longcang" to "龙藏手书"
)

internal val lyricFontDescriptions = mapOf(
    "system" to "System Sans Serif", "kai" to "LXGW WenKai", "song" to "Source Han Serif",
    "xiaowei" to "ZCOOL XiaoWei", "zenmaru" to "Zen Maru Gothic", "kiwi" to "Kiwi Maru",
    "kurenaido" to "Zen Kurenaido", "longcang" to "Long Cang"
)

/** Asset names are shared by the license screen and bundled font provenance records. */
internal val lyricFontLicenses = listOf(
    "文楷 · 霞鹜文楷" to "lxgw_wenkai.ttf.OFL.txt",
    "宋体 · 多语组合" to "flower_serif.ttf.OFL.txt",
    "小薇宋意 · ZCOOL XiaoWei 多语组合" to "flower_xiaowei.ttf.OFL.txt",
    "禅丸圆体 · Zen Maru Gothic 多语组合" to "flower_zenmaru.ttf.OFL.txt",
    "奇异果圆体 · Kiwi Maru 多语组合" to "flower_kiwi.ttf.OFL.txt",
    "红道手写 · Zen Kurenaido 多语组合" to "flower_kurenaido.ttf.OFL.txt",
    "龙藏手书 · Long Cang 多语组合" to "flower_longcang.ttf.OFL.txt"
)
