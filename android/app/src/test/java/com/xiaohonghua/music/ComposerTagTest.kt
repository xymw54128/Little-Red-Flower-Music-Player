package com.xiaohonghua.music

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

class ComposerTagTest {
    private fun id3(version: Int, frames: List<Pair<String,ByteArray>>): ByteArray {
        fun sync(size: Int)=byteArrayOf(((size ushr 21) and 127).toByte(),((size ushr 14) and 127).toByte(),((size ushr 7) and 127).toByte(),(size and 127).toByte())
        val body=ByteArrayOutputStream()
        frames.forEach {(name,bytes) -> DataOutputStream(body).apply {
            write(name.toByteArray(Charsets.US_ASCII))
            if(version == 4) write(sync(bytes.size)) else writeInt(bytes.size)
            writeShort(0);write(bytes)
        }}
        return "ID3".toByteArray()+byteArrayOf(version.toByte(),0,0)+sync(body.size())+body.toByteArray()
    }

    @Test fun id3ComposerIsDistinctFromArtistInBothSupportedVersions() {
        for(version in 3..4) {
            val bytes=id3(version,listOf(
                "TPE1" to byteArrayOf(3)+"演唱者".toByteArray(),
                "TCOM" to byteArrayOf(1)+"作曲者 · Composer".toByteArray(Charsets.UTF_16)
            ))
            val values=TagParser.id3(ByteArrayInputStream(bytes)).values
            assertEquals("演唱者",values["ARTIST"])
            assertEquals("作曲者 · Composer",values["COMPOSER"])
        }
    }

    @Test fun missingComposerNeverFallsBackToArtist() {
        val bytes=id3(3,listOf("TPE1" to byteArrayOf(3)+"只有艺术家".toByteArray()))
        assertNull(TagParser.id3(ByteArrayInputStream(bytes)).values["COMPOSER"])
    }

    @Test fun flacVorbisComposerUsesItsOwnTagAndKeepsUnicode() {
        val body=ByteArrayOutputStream()
        fun little(size: Int) {repeat(4) {body.write(size ushr (it*8) and 255)}}
        val comments=listOf("TITLE=原始标题","ARTIST=艺术家","composer=作曲者 / Composer")
        little(0);little(comments.size)
        comments.forEach {text -> val bytes=text.toByteArray();little(bytes.size);body.write(bytes)}
        val out=ByteArrayOutputStream()
        out.write("fLaC".toByteArray());out.write(132)
        out.write(body.size() ushr 16);out.write(body.size() ushr 8);out.write(body.size());out.write(body.toByteArray())
        val values=TagParser.flac(ByteArrayInputStream(out.toByteArray())).values
        assertEquals("艺术家",values["ARTIST"])
        assertEquals("作曲者 / Composer",values["COMPOSER"])
    }
}
