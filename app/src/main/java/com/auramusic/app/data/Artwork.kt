package com.auramusic.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.LruCache
import android.util.Size
import java.util.Collections

/**
 * Loads embedded / MediaStore album artwork. On Android 10+ the legacy `albumart` URIs are
 * unreliable, so [android.content.ContentResolver.loadThumbnail] is used on the song itself.
 */
object Artwork {
    private val cache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 1024 / 10).toInt()) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount / 1024
    }
    private val missing: MutableSet<String> = Collections.synchronizedSet(HashSet())

    const val SMALL = 192
    const val LARGE = 900

    fun cached(songId: Long, size: Int): Bitmap? = cache.get(key(songId, size)) ?: if (size == SMALL) cache.get(key(songId, LARGE)) else null

    fun isKnownMissing(songId: Long) = "$songId" in missing

    fun clearMissing() = missing.clear()

    /** Blocking; call from a background thread. */
    fun load(context: Context, song: MusicItem, size: Int): Bitmap? {
        cached(song.id, size)?.let { return it }
        if (isKnownMissing(song.id)) return null
        val bmp = loadUri(context, song.uri, song.albumArtUri, size)
        if (bmp != null) cache.put(key(song.id, size), bmp) else missing.add("${song.id}")
        return bmp
    }

    /** Loads artwork for an audio media URI (used by the media notification / Android Auto). */
    fun loadUri(context: Context, songUri: Uri, albumArtUri: Uri?, size: Int): Bitmap? {
        val resolver = context.contentResolver
        if (Build.VERSION.SDK_INT >= 29) {
            runCatching { resolver.loadThumbnail(songUri, Size(size, size), null) }.getOrNull()?.let { return it }
        }
        if (albumArtUri != null) {
            runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                resolver.openInputStream(albumArtUri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                if (bounds.outWidth <= 0) return@runCatching null
                var sample = 1
                while (bounds.outWidth / (sample * 2) >= size && bounds.outHeight / (sample * 2) >= size) sample *= 2
                val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                resolver.openInputStream(albumArtUri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            }.getOrNull()?.let { return it }
        }
        // Fallback: read the embedded picture directly from the file.
        return runCatching {
            val mmr = android.media.MediaMetadataRetriever()
            try {
                mmr.setDataSource(context, songUri)
                val bytes = mmr.embeddedPicture ?: return@runCatching null
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                var sample = 1
                while (bounds.outWidth / (sample * 2) >= size && bounds.outHeight / (sample * 2) >= size) sample *= 2
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            } finally {
                runCatching { mmr.release() }
            }
        }.getOrNull()
    }

    private fun key(id: Long, size: Int) = "$id@$size"
}
