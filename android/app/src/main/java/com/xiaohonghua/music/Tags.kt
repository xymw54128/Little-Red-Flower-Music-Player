package com.xiaohonghua.music

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.File
import java.io.InputStream
import java.nio.charset.Charset
import java.security.MessageDigest

fun InputStream.readLimited(limit: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream(minOf(limit, 8192))
    val buffer = ByteArray(minOf(limit, 8192).coerceAtLeast(1))
    var remaining = limit
    while (remaining > 0) {
        val count = read(buffer, 0, minOf(buffer.size, remaining))
        if (count < 0) break
        if (count == 0) continue
        out.write(buffer, 0, count); remaining -= count
    }
    return out.toByteArray()
}
private fun InputStream.skipFully(length: Int) {
    var remaining = length
    while (remaining > 0) {
        val skipped = skip(remaining.toLong()).toInt()
        if(skipped > 0) remaining -= skipped else { check(read() >= 0); remaining-- }
    }
}

data class AudioTags(val values: Map<String, String> = emptyMap(), val duration: Long = 0, val sampleRate: Int = 0, val bitDepth: Int = 0, val picture: ByteArray? = null)

object TagParser {
    private const val MAX_BLOCK = 16 * 1024 * 1024
    fun flac(input: InputStream): AudioTags {
        val stream = DataInputStream(input)
        require(String(stream.readLimited(4), Charsets.US_ASCII) == "fLaC") { "FLAC 文件头无效" }
        val values = mutableMapOf<String, String>()
        var duration = 0L
        var rate = 0
        var bits = 0
        var picture: ByteArray? = null
        var total = 0
        while (true) {
            val type = stream.readUnsignedByte()
            val length = (stream.readUnsignedByte() shl 16) or (stream.readUnsignedByte() shl 8) or stream.readUnsignedByte()
            total += length
            require(length <= MAX_BLOCK && total <= 64 * 1024 * 1024) { "FLAC 标签过大" }
            val blockType=type and 127
            // Padding/application/seek blocks can be large and contain no displayed tags.
            val block = if(blockType == 0 || blockType == 4 || blockType == 6 && picture == null) stream.readLimited(length) else {
                val scratch=ByteArray(minOf(length,8192))
                var remaining=length
                while(remaining > 0) {
                    val read=stream.read(scratch,0,minOf(scratch.size,remaining))
                    require(read > 0) {"FLAC 标签被截断"}
                    remaining-=read
                }
                null
            }
            require(block == null || block.size == length) { "FLAC 标签被截断" }
            when (blockType) {
                0 -> if (block != null && block.size >= 18) {
                    var packed = 0L
                    for (i in 10..17) packed = (packed shl 8) or (block[i].toLong() and 255)
                    rate = (packed ushr 44).toInt()
                    bits = ((packed ushr 36) and 31).toInt() + 1
                    val samples = packed and 0xFFFFFFFFFL
                    if (rate > 0) duration = samples * 1000 / rate
                }
                4 -> values.putAll(vorbis(block!!))
                6 -> if (block != null) picture = flacPicture(block)
            }
            if (type and 128 != 0) break
        }
        return AudioTags(values, duration, rate, bits, picture)
    }
    fun vorbis(bytes: ByteArray): Map<String, String> {
        var cursor = 0
        fun length(): Int {
            require(cursor + 4 <= bytes.size)
            val value = (0..3).fold(0L) { v, i -> v or ((bytes[cursor+i].toLong() and 255) shl (8*i)) }
            cursor += 4
            require(value <= MAX_BLOCK)
            return value.toInt()
        }
        val vendor = length()
        require(cursor + vendor <= bytes.size)
        cursor += vendor
        val count = length().coerceAtMost(100_000)
        val tags = mutableMapOf<String, String>()
        repeat(count) {
            val length = length()
            require(cursor + length <= bytes.size)
            val comment = String(bytes, cursor, length, Charsets.UTF_8)
            cursor += length
            val split = comment.indexOf('=')
            if (split > 0) tags[comment.substring(0, split).uppercase()] = comment.substring(split+1)
        }
        return tags
    }
    private fun flacPicture(bytes: ByteArray): ByteArray? = runCatching {
        val data = DataInputStream(ByteArrayInputStream(bytes))
        data.readInt()
        val mimeLength = data.readInt()
        require(mimeLength in 0..1000)
        data.skipFully(mimeLength)
        val descriptionLength = data.readInt()
        require(descriptionLength in 0..MAX_BLOCK)
        data.skipFully(descriptionLength)
        repeat(4) { data.readInt() }
        val imageLength = data.readInt()
        require(imageLength in 1..MAX_BLOCK && imageLength <= data.available())
        data.readLimited(imageLength)
    }.getOrNull()

    fun id3(input: InputStream): AudioTags {
        val head = input.readLimited(10)
        if (head.size != 10 || String(head, 0, 3, Charsets.US_ASCII) != "ID3") return AudioTags()
        val version = head[3].toInt()
        if (version !in 3..4) return AudioTags()
        fun sync(bytes: ByteArray, start: Int): Int = (0..3).fold(0) { v, i -> (v shl 7) or (bytes[start+i].toInt() and 127) }
        val length = sync(head, 6)
        require(length <= MAX_BLOCK)
        var body = input.readLimited(length)
        if (head[5].toInt() and 128 != 0) {
            val out = java.io.ByteArrayOutputStream()
            var i = 0
            while (i < body.size) { out.write(body[i].toInt()); if (body[i].toInt() and 255 == 255 && i+1 < body.size && body[i+1] == 0.toByte()) i++; i++ }
            body = out.toByteArray()
        }
        var cursor = 0
        if (head[5].toInt() and 64 != 0 && body.size >= 4) {
            val size = if (version == 4) sync(body, 0) else DataInputStream(ByteArrayInputStream(body)).readInt() + 4
            if (size !in 4..body.size) return AudioTags()
            cursor = size
        }
        val values = mutableMapOf<String, String>()
        var picture: ByteArray? = null
        while (cursor+10 <= body.size) {
            val name = String(body, cursor, 4, Charsets.US_ASCII)
            val size = if (version == 4) sync(body, cursor+4) else DataInputStream(ByteArrayInputStream(body, cursor+4, 4)).readInt()
            val flags = body[cursor+9].toInt() and 255
            cursor += 10
            if (size <= 0 || size > body.size-cursor) break
            val frame = body.copyOfRange(cursor, cursor+size)
            cursor += size
            if (flags != 0) continue // Skip compressed/encrypted frames rather than misread their payload.
            if (name == "USLT" && frame.size > 4) {
                val encoding = frame[0].toInt()
                val start = terminator(frame, 4, encoding)
                values["LYRICS"] = decodeText(frame.copyOfRange(start, frame.size), encoding)
            } else if (name == "APIC" && picture == null && frame.size > 4) {
                val mimeEnd = frame.indexOf(0, 1)
                if (mimeEnd > 0 && mimeEnd+2 < frame.size) {
                    val start = terminator(frame, mimeEnd+2, frame[0].toInt())
                    picture = frame.copyOfRange(start, frame.size)
                }
            } else if (name in listOf("TIT2", "TPE1", "TPE2", "TALB", "TPOS", "TRCK") && frame.isNotEmpty()) {
                values[mapOf("TIT2" to "TITLE", "TPE1" to "ARTIST", "TPE2" to "ALBUMARTIST", "TALB" to "ALBUM", "TPOS" to "DISCNUMBER", "TRCK" to "TRACKNUMBER").getValue(name)] = decodeText(frame.copyOfRange(1, frame.size), frame[0].toInt())
            }
        }
        return AudioTags(values, picture=picture)
    }
    private fun ByteArray.indexOf(value: Byte, start: Int): Int = (start until size).firstOrNull { this[it] == value } ?: -1
    private fun terminator(bytes: ByteArray, start: Int, encoding: Int): Int {
        var i = start
        val width = if (encoding in 1..2) 2 else 1
        while (i+width <= bytes.size) {
            if ((0 until width).all { bytes[i+it] == 0.toByte() }) return i+width
            i += width
        }
        return bytes.size
    }
    private fun decodeText(bytes: ByteArray, encoding: Int): String = String(bytes, when (encoding) {
        1 -> Charsets.UTF_16; 2 -> Charsets.UTF_16BE; 3 -> Charsets.UTF_8; else -> Charsets.ISO_8859_1
    }).trimEnd('\u0000')
}

fun documentIdentity(uri: Uri): String {
    val document = runCatching { android.provider.DocumentsContract.getDocumentId(uri) }.getOrElse { uri.toString() }
    return MessageDigest.getInstance("SHA-256").digest("${uri.authority}:$document".toByteArray()).joinToString("") { "%02x".format(it) }
}

class TagReader(private val context: Context) {
    fun read(uri: Uri, name: String, size: Long, modified: Long, source: String, externalLyrics: String? = null): Song {
        val id = documentIdentity(uri)
        val format = name.substringAfterLast('.', "").uppercase()
        val tags = mutableMapOf<String, String>()
        var duration = 0L
        var rate = 0
        var bits = 0
        var picture: ByteArray? = null
        // FLAC contains the complete tags, duration and artwork in its metadata blocks.
        // Avoid extracting the same multi-megabyte cover twice through two decoders.
        if(format != "FLAC") {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            listOf("TITLE" to MediaMetadataRetriever.METADATA_KEY_TITLE, "ARTIST" to MediaMetadataRetriever.METADATA_KEY_ARTIST,
                "ALBUM" to MediaMetadataRetriever.METADATA_KEY_ALBUM, "ALBUMARTIST" to MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST, "DISCNUMBER" to MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER, "TRACKNUMBER" to MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER).forEach { (key, code) ->
                retriever.extractMetadata(code)?.takeIf { it.isNotBlank() }?.let { tags[key] = it }
            }
            duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0
            if(android.os.Build.VERSION.SDK_INT >= 31) {
                rate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)?.toIntOrNull() ?: 0
                bits = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITS_PER_SAMPLE)?.toIntOrNull() ?: 0
            }
            picture = retriever.embeddedPicture
        } catch (_: RuntimeException) {
            // FLAC's own metadata can still be read when a provider rejects the retriever.
        } finally { retriever.release() }
        }
        context.contentResolver.openInputStream(uri)?.buffered()?.use { input ->
            val parsed = when (format) {
                "FLAC" -> TagParser.flac(input)
                "MP3", "WAV" -> TagParser.id3(input)
                "OGG", "OPUS" -> {
                    val bytes = input.readLimited(2 * 1024 * 1024)
                    val latin = String(bytes, Charsets.ISO_8859_1)
                    val prefix = if (format == "OPUS") "OpusTags" else "\u0003vorbis"
                    val start = latin.indexOf(prefix)
                    if (start < 0) AudioTags() else runCatching { AudioTags(TagParser.vorbis(bytes.copyOfRange(start+prefix.length, bytes.size))) }.getOrDefault(AudioTags())
                }
                else -> AudioTags()
            }
            tags.putAll(parsed.values)
            if (parsed.duration > 0) duration = parsed.duration
            if (parsed.sampleRate > 0) rate = parsed.sampleRate
            if (parsed.bitDepth > 0) bits = parsed.bitDepth
            picture = parsed.picture ?: picture
        }
        val cover = File(context.filesDir, "covers/$id.img")
        if (picture != null && picture!!.size <= 16 * 1024 * 1024) {
            cover.parentFile!!.mkdirs()
            val unchanged=cover.exists() && cover.length() == picture!!.size.toLong() && cover.inputStream().buffered().use {input ->
                val buffer=ByteArray(8192)
                var offset=0
                while(offset < picture!!.size) {
                    val count=input.read(buffer,0,minOf(buffer.size,picture!!.size-offset))
                    if(count <= 0 || (0 until count).any {buffer[it] != picture!![offset+it]}) return@use false
                    offset+=count
                }
                true
            }
            if(!unchanged) cover.writeBytes(picture!!)
        }
        else if (cover.exists()) cover.delete()
        val revision=picture?.takeIf {cover.exists()}?.let {MessageDigest.getInstance("SHA-256").digest(it).joinToString("") {byte->"%02x".format(byte)}}.orEmpty()
        return Song(id, uri.toString(), name, tags["TITLE"].orEmpty().ifBlank { name.substringBeforeLast('.') },
            tags["ARTIST"].orEmpty().ifBlank { "未知歌手" }, tags["ALBUM"].orEmpty().ifBlank { "未知专辑" }, duration, size, modified,
            format, rate, bits, tags["TRACKNUMBER"].orEmpty(), externalLyrics ?: tags["LYRICS"] ?: tags["UNSYNCEDLYRICS"] ?: "",
            if (cover.exists()) cover.absolutePath else "", setOf(source),artworkRevision=revision,
            albumArtist=(tags["ALBUMARTIST"] ?: tags["ALBUM ARTIST"] ?: tags["ALBUM_ARTIST"]).orEmpty(),disc=(tags["DISCNUMBER"] ?: tags["DISC"]).orEmpty())
    }
    fun lyricText(uri: Uri): String = context.contentResolver.openInputStream(uri)!!.use { input ->
        val bytes = input.readLimited(1024 * 1024 + 1)
        require(bytes.size <= 1024 * 1024) { "歌词文件不能超过 1 MB" }
        when {
            bytes.size >= 2 && bytes[0] == 0xff.toByte() && bytes[1] == 0xfe.toByte() -> String(bytes, Charsets.UTF_16LE).trimStart('\uFEFF')
            bytes.size >= 2 && bytes[0] == 0xfe.toByte() && bytes[1] == 0xff.toByte() -> String(bytes, Charsets.UTF_16BE).trimStart('\uFEFF')
            else -> String(bytes, Charsets.UTF_8).let { if ('\uFFFD' in it) String(bytes, Charset.forName("GB18030")) else it }.trimStart('\uFEFF')
        }
    }
}
