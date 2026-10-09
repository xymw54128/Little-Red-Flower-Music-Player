package com.xiaohonghua.music

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

class TagParserTest {
    private fun comments(values: List<String>): ByteArray {
        val out=ByteArrayOutputStream()
        fun little(value: Int) { repeat(4) {i->out.write(value ushr (i*8) and 255)} }
        little(0);little(values.size)
        values.forEach {val bytes=it.toByteArray();little(bytes.size);out.write(bytes)}
        return out.toByteArray()
    }
    @Test fun realFlacStreamInfoAndUnicodeComments() {
        val stream=ByteArray(34)
        val rate=96000L
        val packed=(rate shl 44) or (1L shl 41) or (23L shl 36) or (rate*183)
        repeat(8) {i->stream[10+i]=(packed ushr (56-8*i)).toByte()}
        val out=ByteArrayOutputStream()
        out.write("fLaC".toByteArray())
        fun block(type: Int,bytes: ByteArray) {out.write(type);out.write(bytes.size ushr 16);out.write(bytes.size ushr 8);out.write(bytes.size);out.write(bytes)}
        block(0,stream)
        block(128+4,comments(listOf("TITLE=测试歌曲","ARTIST=歌手甲","ALBUM=原始专辑","LYRICS=[00:01.25]第一行")))
        val tags=TagParser.flac(ByteArrayInputStream(out.toByteArray()))
        assertEquals(96000,tags.sampleRate);assertEquals(24,tags.bitDepth);assertEquals(183000L,tags.duration)
        assertEquals("测试歌曲",tags.values["TITLE"]);assertEquals("[00:01.25]第一行",tags.values["LYRICS"])
    }
    @Test(expected=IllegalArgumentException::class) fun rejectsTruncatedFlacBlock() {
        TagParser.flac(ByteArrayInputStream("fLaC".toByteArray()+byteArrayOf(0x84.toByte(),0,0,8,1,2)))
    }
    @Test fun readsId3LyricsAndTextEncoding() {
        val body=ByteArrayOutputStream()
        fun frame(id: String,bytes: ByteArray) {val data=DataOutputStream(body);data.write(id.toByteArray());data.writeInt(bytes.size);data.writeShort(0);data.write(bytes)}
        frame("TIT2",byteArrayOf(3)+"音乐".toByteArray())
        frame("USLT",byteArrayOf(3)+"eng".toByteArray()+byteArrayOf(0)+"[00:02.5]歌词".toByteArray())
        val size=body.size()
        val header="ID3".toByteArray()+byteArrayOf(3,0,0,((size ushr 21) and 127).toByte(),((size ushr 14) and 127).toByte(),((size ushr 7) and 127).toByte(),(size and 127).toByte())
        val tags=TagParser.id3(ByteArrayInputStream(header+body.toByteArray()))
        assertEquals("音乐",tags.values["TITLE"]);assertEquals("[00:02.5]歌词",tags.values["LYRICS"])
    }
    @Test fun readLimitedStopsAtBoundWithoutJava9Apis() { assertEquals(listOf<Byte>(1,2),ByteArrayInputStream(byteArrayOf(1,2,3)).readLimited(2).toList()) }
}
