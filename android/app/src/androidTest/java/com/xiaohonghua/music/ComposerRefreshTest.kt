package com.xiaohonghua.music

import android.os.Bundle
import android.provider.DocumentsContract
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream

/** Changes tags in a disposable provider while preserving the fixture's FLAC audio frames. */
@RunWith(AndroidJUnit4::class)
class ComposerRefreshTest {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()

    private fun taggedFlac(composer: String?): ByteArray {
        val original=instrumentation.context.assets.open("tone.flac").use {it.readBytes()}
        val out=ByteArrayOutputStream()
        out.write(original,0,4)
        var cursor=4
        while(true) {
            val type=original[cursor].toInt() and 255
            val length=((original[cursor+1].toInt() and 255) shl 16) or
                ((original[cursor+2].toInt() and 255) shl 8) or (original[cursor+3].toInt() and 255)
            if(type and 127 != 4) {
                out.write(type and 127);out.write(original,cursor+1,length+3)
            }
            cursor+=length+4
            if(type and 128 != 0) break
        }
        val comments=ByteArrayOutputStream()
        fun little(size: Int) {repeat(4) {comments.write(size ushr (it*8) and 255)}}
        val fields=listOf("TITLE=标签刷新","ARTIST=演唱者")+listOfNotNull(composer?.let {"COMPOSER=$it"})
        little(0);little(fields.size)
        fields.forEach {value -> val bytes=value.toByteArray();little(bytes.size);comments.write(bytes)}
        out.write(132);out.write(comments.size() ushr 16);out.write(comments.size() ushr 8);out.write(comments.size());out.write(comments.toByteArray())
        out.write(original,cursor,original.size-cursor)
        return out.toByteArray()
    }

    @Test fun manualRefreshReadsComposerChangesEvenWithEqualSizeAndTimestamp() {
        val automation=instrumentation.uiAutomation
        automation.adoptShellPermissionIdentity("android.permission.MANAGE_DOCUMENTS")
        val context=instrumentation.targetContext
        val resolver=context.contentResolver
        val authority=CollectionDocumentsProvider.AUTHORITY
        val base=android.net.Uri.parse("content://$authority")
        val tree=DocumentsContract.buildTreeDocumentUri(authority,"root")
        try {
            resolver.call(base,"reset",null,null)
            fun configure(composer: String?) {resolver.call(base,"configure",null,Bundle().apply {
                putBoolean("present",false);putByteArray("audioBytes",taggedFlac(composer))
            })}
            configure("甲甲")
            val scanner=LibraryScanner(context)
            val first=scanner.folder(tree,emptyList(),true).songs.single()
            assertEquals("演唱者",first.artist);assertEquals("甲甲",first.composer)
            configure("乙乙")
            val cached=scanner.folder(tree,listOf(first),false).songs.single()
            assertEquals(first,cached)
            val refreshed=scanner.folder(tree,listOf(cached),true).songs.single()
            assertEquals(first.id,refreshed.id);assertEquals(first.size,refreshed.size);assertEquals(first.modified,refreshed.modified)
            assertEquals("乙乙",refreshed.composer)
            assertEquals("乙乙",songFromJson(refreshed.toJson()).composer)
            assertEquals("乙乙",songFromJson(refreshed.toJson(true)).composer)
            assertEquals("",songFromJson(refreshed.toJson().apply {remove("composer")}).composer)
            configure(null)
            assertEquals("",scanner.folder(tree,listOf(refreshed),true).songs.single().composer)
        } finally {
            resolver.call(base,"reset",null,null)
            automation.dropShellPermissionIdentity()
        }
    }
}
