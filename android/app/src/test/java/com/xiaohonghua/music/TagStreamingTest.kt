package com.xiaohonghua.music

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class TagStreamingTest {
    private fun flacWithPadding(padding: ByteArray): ByteArray = ByteArrayOutputStream().apply {
        write("fLaC".toByteArray())
        write(1);write(padding.size ushr 16);write(padding.size ushr 8);write(padding.size);write(padding)
        val comment="TITLE=After padding".toByteArray()
        val tags=ByteArrayOutputStream().apply {
            fun little(value: Int) {repeat(4) {write(value ushr (it*8) and 255)}}
            little(0);little(1);little(comment.size);write(comment)
        }.toByteArray()
        write(132);write(tags.size ushr 16);write(tags.size ushr 8);write(tags.size);write(tags)
    }.toByteArray()

    @Test fun largePaddingDoesNotConsumeFollowingTags() {
        val parsed=TagParser.flac(ByteArrayInputStream(flacWithPadding(ByteArray(2*1024*1024))))
        assertEquals("After padding",parsed.values["TITLE"])
    }

    @Test(expected=IllegalArgumentException::class) fun truncatedUnusedBlockStillFailsInsteadOfAcceptingCorruptFile() {
        TagParser.flac(ByteArrayInputStream("fLaC".toByteArray()+byteArrayOf(129.toByte(),0,1,0,1,2,3)))
    }
}
