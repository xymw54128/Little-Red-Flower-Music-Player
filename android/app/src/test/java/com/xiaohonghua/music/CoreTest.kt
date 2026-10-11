package com.xiaohonghua.music

import org.junit.Assert.*
import org.junit.Test

class CoreTest {
    @Test fun irregularTimestampsAndBoundaries() {
        val lines = Lrc.parse("[01:31.75]第二行\n[01:05.2]第一行\n[03:02.123]尾行")
        assertEquals(listOf(65200L, 91750L, 182123L), lines.map { it.time })
        assertEquals(lines.map {it.time},lines.map {it.startTime})
        assertEquals(listOf(91750L,182123L,Long.MAX_VALUE),lines.map {it.endTime})
        assertEquals(-1, Lrc.current(lines, 65200 - 1))
        assertEquals(0, Lrc.current(lines, 91749))
        assertEquals(1, Lrc.current(lines, 91750))
        assertEquals(2, Lrc.current(lines, Long.MAX_VALUE))
    }
    @Test fun repeatedTimestampsOffsetsAndTranslation() {
        val lines = Lrc.parse("[offset:-200]\n[00:01.5][00:02]Hello\n[00:02.00]你好\n[00:65]错误")
        assertEquals(listOf(1300L, 1800L), lines.map { it.time })
        assertEquals("Hello\n你好", lines[1].text)
        assertEquals(1800L,lines[0].endTime)
        assertEquals(emptyList<LyricLine>(), Lrc.parse("[ar:作者]\n纯文本歌词"))
        assertEquals(0L, Lrc.parse("[offset:-9000]\n[00:01]开始")[0].time)
    }
    @Test fun knownSongDurationClosesLastIntervalAndDenseLinesRemainExact() {
        val lines=Lrc.parse("[offset:500]\n[00:01.00]开始\n[00:01.001]下一句",4000)
        assertEquals(listOf(1500L,1501L),lines.map {it.startTime})
        assertEquals(listOf(1501L,4000L),lines.map {it.endTime})
        assertEquals(-1,Lrc.current(lines,1499))
        assertEquals(0,Lrc.current(lines,1500))
        assertEquals(1,Lrc.current(lines,1501))
        assertEquals(1500L,Lrc.parse("[00:01.50]开始",1000).single().endTime)
        assertEquals(emptyList<LyricLine>(),Lrc.parse("[ar:作者]",4000))
    }
    @Test fun customAndRandomOrderRemainSeparate() {
        val songs = listOf(song("1", "春"), song("2", "风"), song("3", "雨"))
        val custom = listOf("3", "1", "2")
        val draft = custom.toMutableList().apply { add(0, removeAt(2)) }
        assertEquals(listOf("2", "3", "1"), draft)
        assertEquals(listOf("3", "1", "2"), orderedSongs(songs, SortMode.CUSTOM, custom, listOf("2", "1", "3")).map { it.id })
        assertEquals(listOf("2", "1", "3"), orderedSongs(songs, SortMode.RANDOM, custom, listOf("2", "1", "3")).map { it.id })
        assertEquals(listOf("1", "2", "3"), orderedSongs(songs, SortMode.TITLE, custom, emptyList()).map { it.id })
    }
    @Test fun overlappingSourcesDeletionAndTagChanges() {
        val shared = song("1", "旧标签").copy(sources=setOf("folderA", "folderB"))
        val deleted = song("2", "删除").copy(sources=setOf("folderA"))
        val unrelated = song("3", "独立").copy(sources=setOf("files"))
        val fresh = song("1", "新标签")
        val result = reconcileSongs(listOf(shared, deleted, unrelated), listOf(fresh), "folderA")
        assertEquals(listOf("1", "3"), result.map { it.id })
        assertEquals("新标签", result[0].title)
        assertEquals(setOf("folderA", "folderB"), result[0].sources)
        assertEquals(setOf("folderB"), reconcileSongs(result, emptyList(), "folderA")[0].sources)
    }
    private fun song(id: String, title: String) = Song(id, "content://example/$id", "$id.flac", title)
}
