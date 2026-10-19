package com.auramusic.app.data

import android.net.Uri

data class MusicItem(
    val id: Long,
    val uri: Uri,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val albumId: Long = 0L,
    val albumArtUri: Uri? = null,
    val relativePath: String = "",
    val track: Int = 0,
    val year: Int = 0,
    val dateAddedSec: Long = 0L,
    val sizeBytes: Long = 0L,
    val mimeType: String = ""
) {
    /** Stable key used by favorites, playlists, play counts and backups. */
    val key: String get() = uri.toString()

    val durationLabel: String get() = formatDuration(durationMs)

    /** Folder shown in the folder browser, e.g. "Music/YouTube". */
    val folder: String
        get() = relativePath.trim().trim('/').ifBlank { "Internal storage" }

    val folderName: String get() = folder.substringAfterLast('/').ifBlank { folder }
}

data class Playlist(
    val name: String,
    val songUris: List<String>,
    val createdAt: Long = 0L
)

data class DownloadRecord(
    val title: String,
    val uri: String,
    val timestamp: Long,
    val artist: String = "",
    val sourceUrl: String = "",
    val format: String = "mp3",
    val location: String = ""
)

data class LyricsLine(val timestampMs: Long, val text: String)

enum class ThemeMode { DARK, LIGHT, SYSTEM }

enum class DownloadEngineMode { ON_DEVICE, SERVER }

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val userName: String = "Music Lover",
    val serverUrl: String = "http://10.0.2.2:8000",
    val engineMode: DownloadEngineMode = DownloadEngineMode.ON_DEVICE,
    val downloadFormat: String = "mp3",
    val downloadQuality: String = "320",
    val downloadFolder: String = "AuraMusic",
    val embedArtwork: Boolean = true,
    val minDurationSec: Int = 0,
    val playbackSpeed: Float = 1f,
    val skipSilence: Boolean = false,
    val keepScreenOnInCar: Boolean = true
)

fun formatDuration(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
