package com.xiaohonghua.music

import android.database.Cursor
import android.database.MatrixCursor
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract.Document
import android.provider.DocumentsProvider
import java.io.File
import java.io.FileNotFoundException

/** Disposable provider in the test APK, never packaged into the music player. */
class CollectionDocumentsProvider : DocumentsProvider() {
    private lateinit var audio: File
    private lateinit var lyric: File
    private var present=false
    private var readable=true
    private var failListing=false
    private var modified=1000L
    private var audioReads=0
    private var lyricReads=0
    override fun onCreate(): Boolean {
        audio=File(context!!.cacheDir,"collection-fixture.flac")
        lyric=File(context!!.cacheDir,"collection-fixture.lrc")
        resetAudio()
        lyric.writeText("[00:01]默认歌词")
        return true
    }
    @Synchronized override fun call(method: String,arg: String?,extras: Bundle?): Bundle? = when(method) {
        "reset" -> Bundle().also {resetAudio();present=false;readable=true;failListing=false;modified=1000;audioReads=0;lyricReads=0}
        "configure" -> Bundle().also {
            val data=requireNotNull(extras)
            failListing=data.getBoolean("failListing",false)
            present=data.getBoolean("present",true);readable=data.getBoolean("readable",true);modified=data.getLong("modified",1000)
            data.getString("text")?.let {lyric.writeText(it)}
            data.getByteArray("audioBytes")?.let {audio.writeBytes(it)}
        }
        "counts" -> Bundle().apply {putInt("audio",audioReads);putInt("lyrics",lyricReads)}
        else -> super.call(method,arg,extras)
    }
    private fun resetAudio() {
        context!!.assets.open("tone.flac").use {input -> audio.outputStream().use {out -> input.copyTo(out)}}
    }
    override fun queryRoots(projection: Array<out String>?): Cursor = MatrixCursor(projection ?: arrayOf("root_id","document_id","title")).apply {
        addRow(columnNames.map {when(it) {"root_id","document_id" -> "root";"title" -> "Collection test";else -> null}}.toTypedArray())
    }
    override fun queryDocument(documentId: String,projection: Array<out String>?): Cursor = cursor(projection,listOf(documentId))
    override fun queryChildDocuments(parentDocumentId: String,projection: Array<out String>?,sortOrder: String?): Cursor {
        if(failListing) throw FileNotFoundException("文件夹暂时不可读")
        return cursor(projection,if(present) listOf("audio","lyric") else listOf("audio"))
    }
    override fun isChildDocument(parentDocumentId: String,documentId: String): Boolean = parentDocumentId == "root" && documentId in setOf("audio","lyric")
    @Synchronized override fun openDocument(documentId: String,mode: String,cancellationSignal: CancellationSignal?): ParcelFileDescriptor {
        val file=when(documentId) {
            "audio","standalone" -> {audioReads++;audio}
            "lyric" -> {lyricReads++;if(!present || !readable) throw FileNotFoundException("暂时无法读取歌词");lyric}
            else -> throw FileNotFoundException(documentId)
        }
        require(mode == "r")
        return ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY)
    }
    private fun cursor(projection: Array<out String>?,ids: List<String>): Cursor = MatrixCursor(projection ?: arrayOf(
        Document.COLUMN_DOCUMENT_ID,Document.COLUMN_DISPLAY_NAME,Document.COLUMN_MIME_TYPE,Document.COLUMN_SIZE,Document.COLUMN_LAST_MODIFIED
    )).apply {
        ids.forEach {id -> addRow(columnNames.map {column -> when(column) {
            Document.COLUMN_DOCUMENT_ID -> id
            Document.COLUMN_DISPLAY_NAME -> if(id == "root") "Collection test" else "song."+if(id in setOf("audio","standalone")) "flac" else "lrc"
            Document.COLUMN_MIME_TYPE -> when(id) {"root" -> Document.MIME_TYPE_DIR;"audio","standalone" -> "audio/flac";else -> "text/plain"}
            Document.COLUMN_SIZE -> if(id in setOf("audio","standalone")) audio.length() else lyric.length()
            Document.COLUMN_LAST_MODIFIED -> if(id in setOf("audio","standalone")) 1000L else modified
            Document.COLUMN_FLAGS -> 0
            else -> null
        }}.toTypedArray())}
    }
    companion object {const val AUTHORITY="com.xiaohonghua.music.test.collectionprovider"}
}
