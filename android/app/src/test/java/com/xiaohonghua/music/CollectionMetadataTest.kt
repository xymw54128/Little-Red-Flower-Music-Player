package com.xiaohonghua.music

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

class CollectionMetadataTest {
    @Test fun id3AlbumArtistDiscAndTrackNumbersArePreserved() {
        val body=ByteArrayOutputStream()
        fun frame(id: String,text: String) {
            val bytes=byteArrayOf(3)+text.toByteArray()
            DataOutputStream(body).apply {write(id.toByteArray());writeInt(bytes.size);writeShort(0);write(bytes)}
        }
        frame("TPE1","曲目歌手");frame("TPE2","群星");frame("TALB","合集")
        frame("TPOS","2/3");frame("TRCK","10/12")
        val size=body.size()
        val header="ID3".toByteArray()+byteArrayOf(3,0,0,((size ushr 21) and 127).toByte(),((size ushr 14) and 127).toByte(),((size ushr 7) and 127).toByte(),(size and 127).toByte())
        val values=TagParser.id3(ByteArrayInputStream(header+body.toByteArray())).values
        assertEquals("群星",values["ALBUMARTIST"])
        assertEquals("2/3",values["DISCNUMBER"])
        assertEquals("10/12",values["TRACKNUMBER"])
    }

    @Test fun compilationGroupsUseAlbumArtistAndFallbackWithoutMergingUnrelatedArtists() {
        val first=song("a").copy(artist="歌手甲",album="合集",albumArtist="群星")
        val second=song("b").copy(artist="歌手乙",album="合集",albumArtist="群星")
        assertEquals(first.albumKey,second.albumKey)
        assertEquals("合集",first.albumKey.substringAfter('\u0000'))
        assertEquals("群星",first.albumDisplayArtist)
        assertNotEquals(first.copy(albumArtist="").albumKey,second.copy(albumArtist="").albumKey)
        assertNotEquals(first.albumKey,first.copy(album="另一张合集").albumKey)
    }
    @Test fun multiDiscAlbumUsesNumericTrackOrderAndTotalNumberSuffixes() {
        val songs=listOf(
            song("disc2").copy(disc="2/2",track="1/12"),
            song("track10").copy(disc="1",track="10/12"),
            song("track2").copy(disc="1/2",track="02"),
            song("unknown").copy(disc="1",track="unknown"),
            song("track1").copy(track="1")
        )
        assertEquals(listOf("track1","track2","track10","unknown","disc2"),albumSongs(songs).map {it.id})
        assertNull(tagNumber("0/12"));assertNull(tagNumber("-1"));assertNull(tagNumber("999999999999"))
        assertEquals(2,tagNumber(" 02 / 12 "))
    }
    @Test fun vorbisCompilationFieldsRemainAvailableForFlacReader() {
        val out=ByteArrayOutputStream()
        fun little(value: Int) {repeat(4) {i -> out.write(value ushr (i*8) and 255)}}
        val comments=listOf("ALBUMARTIST=群星","DISCNUMBER=2/3","TRACKNUMBER=10/12")
        little(0);little(comments.size)
        comments.forEach {text -> val bytes=text.toByteArray();little(bytes.size);out.write(bytes)}
        val values=TagParser.vorbis(out.toByteArray())
        assertEquals("群星",values["ALBUMARTIST"]);assertEquals("2/3",values["DISCNUMBER"])
    }
    private fun song(id: String) = Song(id,"content://test/$id","$id.flac",id)
}
