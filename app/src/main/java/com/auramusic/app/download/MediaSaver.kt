package com.auramusic.app.download

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Publishes a finished file into the shared Music collection so every player can see it. */
object MediaSaver {
    data class Saved(val uri: Uri, val location: String)

    fun saveAudio(context: Context, source: File, title: String, artist: String, folder: String): Saved {
        val ext = source.extension.lowercase().ifBlank { "mp3" }
        val mime = if (ext == "m4a") "audio/mp4" else "audio/mpeg"
        val name = safeFileName(title).ifBlank { "Aura download" } + ".$ext"
        val relative = "${Environment.DIRECTORY_MUSIC}/$folder"
        return if (Build.VERSION.SDK_INT >= 29) saveScoped(context, source, name, mime, relative, title, artist)
        else saveLegacy(context, source, name, relative)
    }

    private fun saveScoped(context: Context, source: File, name: String, mime: String, relative: String, title: String, artist: String): Saved {
        val resolver = context.contentResolver
        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, name)
            put(MediaStore.Audio.Media.MIME_TYPE, mime)
            put(MediaStore.Audio.Media.RELATIVE_PATH, "$relative/")
            put(MediaStore.Audio.Media.TITLE, title)
            put(MediaStore.Audio.Media.ARTIST, artist)
            put(MediaStore.Audio.Media.IS_MUSIC, 1)
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values) ?: error("Couldn't create the file in $relative")
        try {
            resolver.openOutputStream(uri, "w")?.use { out -> source.inputStream().use { it.copyTo(out, 256 * 1024) } }
                ?: error("Couldn't write to $relative")
            resolver.update(uri, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null)
        } catch (t: Throwable) {
            runCatching { resolver.delete(uri, null, null) }
            throw t
        }
        return Saved(uri, relative)
    }

    @Suppress("DEPRECATION")
    private fun saveLegacy(context: Context, source: File, name: String, relative: String): Saved {
        val dir = File(Environment.getExternalStorageDirectory(), relative).apply { mkdirs() }
        var target = File(dir, name)
        var n = 1
        while (target.exists()) target = File(dir, "${name.substringBeforeLast('.')} ($n).${name.substringAfterLast('.')}").also { n++ }
        source.copyTo(target)
        var scanned: Uri? = null
        val latch = CountDownLatch(1)
        MediaScannerConnection.scanFile(context, arrayOf(target.absolutePath), null) { _, uri -> scanned = uri; latch.countDown() }
        latch.await(10, TimeUnit.SECONDS)
        return Saved(scanned ?: Uri.fromFile(target), relative)
    }

    fun safeFileName(name: String): String =
        name.replace(Regex("[\\\\/:*?\"<>|\\u0000-\\u001F]"), "_").replace(Regex("\\s+"), " ").trim().trim('.').take(120)
}
