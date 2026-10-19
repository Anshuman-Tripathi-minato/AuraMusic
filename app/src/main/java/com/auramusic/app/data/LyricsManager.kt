package com.auramusic.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest

/**
 * Stores lyrics per song (private app storage). Supports synced `.lrc` lyrics (multiple
 * timestamps per line, `[offset:]` tag) and plain text. Optionally looks lyrics up on
 * LRCLIB (free, no API key).
 */
class LyricsManager(context: Context) {
    private val dir = File(context.filesDir, "lyrics").apply { mkdirs() }

    fun save(songKey: String, text: String) {
        File(dir, fileName(songKey)).writeText(text)
    }

    fun load(songKey: String): String? = File(dir, fileName(songKey)).takeIf { it.exists() }?.readText()

    fun delete(songKey: String) {
        File(dir, fileName(songKey)).delete()
    }

    data class Parsed(val synced: Boolean, val lines: List<LyricsLine>)

    fun parse(raw: String): Parsed {
        val timeTag = Regex("\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?]")
        var offset = 0L
        val synced = mutableListOf<LyricsLine>()
        val plain = mutableListOf<String>()
        raw.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            Regex("^\\[offset:\\s*([+-]?\\d+)]$", RegexOption.IGNORE_CASE).find(line)?.let {
                offset = it.groupValues[1].toLongOrNull() ?: 0L
                return@forEach
            }
            val tags = timeTag.findAll(line).toList()
            if (tags.isNotEmpty() && line.startsWith("[")) {
                val text = line.substring(tags.last().range.last + 1).trim()
                tags.forEach { m ->
                    val min = m.groupValues[1].toLongOrNull() ?: 0L
                    val sec = m.groupValues[2].toLongOrNull() ?: 0L
                    val frac = m.groupValues[3].takeIf { it.isNotEmpty() }?.padEnd(3, '0')?.take(3)?.toLongOrNull() ?: 0L
                    synced += LyricsLine(min * 60_000 + sec * 1_000 + frac, text)
                }
            } else if (!Regex("^\\[[a-zA-Z]+:.*]$").matches(line)) {
                plain += line
            }
        }
        return if (synced.isNotEmpty()) {
            Parsed(true, synced.map { it.copy(timestampMs = (it.timestampMs - offset).coerceAtLeast(0)) }.sortedBy { it.timestampMs })
        } else {
            Parsed(false, plain.dropWhile { it.isBlank() }.dropLastWhile { it.isBlank() }.map { LyricsLine(-1, it) })
        }
    }

    /** Looks up lyrics on lrclib.net. Blocking; call from a background thread. */
    fun fetchOnline(song: MusicItem): String? {
        val artist = song.artist.takeUnless { it == "Unknown artist" }.orEmpty()
        val title = cleanTitle(song.title)
        val durationSec = song.durationMs / 1000
        val exact = buildString {
            append("https://lrclib.net/api/get?track_name=").append(enc(title))
            append("&artist_name=").append(enc(artist))
            if (song.album != "Unknown album") append("&album_name=").append(enc(song.album))
            if (durationSec > 0) append("&duration=").append(durationSec)
        }
        if (artist.isNotBlank()) {
            httpGet(exact)?.let { body ->
                val o = runCatching { JSONObject(body) }.getOrNull()
                pick(o)?.let { return it }
            }
        }
        val query = "https://lrclib.net/api/search?q=" + enc("$title $artist".trim())
        val arr = httpGet(query)?.let { runCatching { JSONArray(it) }.getOrNull() } ?: return null
        var best: String? = null
        var bestScore = Long.MAX_VALUE
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val text = pick(o) ?: continue
            val diff = if (durationSec > 0) kotlin.math.abs(o.optDouble("duration", 0.0).toLong() - durationSec) else 0L
            val score = diff + if (o.optString("syncedLyrics").isBlank()) 30 else 0
            if (score < bestScore) { bestScore = score; best = text }
        }
        return best
    }

    private fun pick(o: JSONObject?): String? {
        if (o == null) return null
        val synced = o.optString("syncedLyrics").takeIf { it.isNotBlank() && it != "null" }
        val plain = o.optString("plainLyrics").takeIf { it.isNotBlank() && it != "null" }
        return synced ?: plain
    }

    private fun cleanTitle(title: String) = title
        .replace(Regex("\\s*[(\\[](official|lyric|lyrics|audio|video|hd|hq|4k|music video|visualizer)[^)\\]]*[)\\]]", RegexOption.IGNORE_CASE), "")
        .trim()

    private fun httpGet(url: String): String? = runCatching {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("User-Agent", "AuraMusic/2.0 (https://github.com/auramusic)")
        }
        try {
            if (conn.responseCode !in 200..299) null else conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }.getOrNull()

    private fun enc(v: String) = URLEncoder.encode(v, "UTF-8")

    private fun fileName(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray())
        .joinToString("") { "%02x".format(it) } + ".lrc"
}
