package com.auramusic.app.download

import android.content.Context
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.File
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write
import java.net.HttpURLConnection
import java.net.URL

data class MediaInfo(
    val id: String,
    val title: String,
    val artist: String,
    val durationSec: Int,
    val thumbnail: String?,
    val webpageUrl: String
)

/** Progress callback: percent in 0..100 (or -1 when unknown), ETA seconds, status text. */
typealias ProgressFn = (percent: Float, etaSec: Long, stage: DownloadStage) -> Unit

enum class DownloadStage { FETCHING, DOWNLOADING, CONVERTING, EMBEDDING, SAVING }

class CanceledByUser : Exception("Download canceled")

/**
 * On-device engine: bundled Python + yt-dlp extracts the best audio stream and FFmpeg
 * converts it to MP3 (or M4A) with embedded title/artist/cover.
 */
object YtDlpEngine {
    enum class Status { NOT_STARTED, PREPARING, READY, FAILED }

    private val _status = MutableStateFlow(Status.NOT_STARTED)
    val status: StateFlow<Status> = _status.asStateFlow()
    var initError: String? = null
        private set

    /**
     * YouTube forces SABR streaming on its web client, which leaves no downloadable audio
     * when a JS runtime is present. Mixing in clients that still serve direct HTTPS formats
     * fixes "Requested format is not available"; the second set is tried if the first fails.
     */
    private val clientSets = listOf("default,visionos,android_vr", "visionos,android_vr,tv_simply,mweb")

    private fun YoutubeDLRequest.withClients(url: String, attempt: Int): YoutubeDLRequest =
        if (url.contains("youtu")) addOption("--extractor-args", "youtube:player_client=${clientSets[attempt.coerceIn(clientSets.indices)]}") else this

    private fun isClientProblem(msg: String?) = msg != null &&
        (msg.contains("Requested format is not available", true) || msg.contains("Sign in to confirm", true) ||
            msg.contains("HTTP Error 403", true) || msg.contains("SABR", true))

    /** Updating replaces the yt-dlp file, so it must never overlap a running extraction. */
    private val lock = ReentrantReadWriteLock()

    /** Unpacks Python/FFmpeg on first launch (a few seconds). Blocking; safe to call repeatedly. */
    @Synchronized
    fun ensureReady(context: Context): Boolean {
        if (_status.value == Status.READY) return true
        _status.value = Status.PREPARING
        return try {
            YoutubeDL.init(context.applicationContext)
            FFmpeg.init(context.applicationContext)
            initError = null
            _status.value = Status.READY
            true
        } catch (t: Throwable) {
            initError = t.message ?: t.javaClass.simpleName
            _status.value = Status.FAILED
            false
        }
    }

    fun version(context: Context): String? = runCatching { YoutubeDL.versionName(context) ?: YoutubeDL.version(context) }.getOrNull()

    /** Updates yt-dlp from GitHub. YouTube changes often; updating fixes most extraction errors. */
    fun update(context: Context): String {
        if (!ensureReady(context)) error(initError ?: "Download engine unavailable")
        return when (lock.write { YoutubeDL.updateYoutubeDL(context.applicationContext, YoutubeDL.UpdateChannel.STABLE) }) {
            YoutubeDL.UpdateStatus.DONE -> "Engine updated to ${version(context) ?: "latest"}"
            YoutubeDL.UpdateStatus.ALREADY_UP_TO_DATE -> "Engine already up to date (${version(context) ?: "latest"})"
            else -> "Engine update finished"
        }
    }

    fun fetchInfo(context: Context, url: String): MediaInfo {
        if (!ensureReady(context)) error(initError ?: "Download engine unavailable")
        fun request(attempt: Int) = YoutubeDLRequest(url)
            .addOption("--no-playlist")
            .addOption("--socket-timeout", "20")
            .withClients(url, attempt)
        val info = try {
            lock.read {
                try {
                    YoutubeDL.getInfo(request(0))
                } catch (t: Throwable) {
                    if (!isClientProblem(t.message)) throw t
                    YoutubeDL.getInfo(request(1))
                }
            }
        } catch (t: Throwable) {
            throw IllegalStateException(friendlyError(t.message))
        }
        return MediaInfo(
            id = info.id.orEmpty(),
            title = info.title ?: info.fulltitle ?: "Unknown title",
            artist = info.uploader ?: "Unknown artist",
            durationSec = info.duration,
            thumbnail = info.thumbnail,
            webpageUrl = info.webpageUrl ?: url
        )
    }

    /** Downloads + converts into [workDir]; returns the produced audio file. */
    fun download(
        context: Context,
        url: String,
        format: String,
        quality: String,
        embedArtwork: Boolean,
        workDir: File,
        processId: String,
        onProgress: ProgressFn
    ): File {
        if (!ensureReady(context)) error(initError ?: "Download engine unavailable")
        return lock.read { downloadLocked(url, format, quality, embedArtwork, workDir, processId, onProgress) }
    }

    private fun downloadLocked(
        url: String, format: String, quality: String, embedArtwork: Boolean,
        workDir: File, processId: String, onProgress: ProgressFn
    ): File {
        return try {
            runWithClientFallback(url, format, quality, embedArtwork, workDir, processId, onProgress)
        } catch (e: CanceledByUser) {
            throw e
        } catch (t: Throwable) {
            if (!embedArtwork || isClientProblem(t.message)) throw IllegalStateException(friendlyError(t.message))
            // Cover embedding is a nice-to-have: retry once without it before failing.
            workDir.listFiles()?.forEach { it.deleteRecursively() }
            try {
                runWithClientFallback(url, format, quality, false, workDir, processId, onProgress)
            } catch (e: CanceledByUser) {
                throw e
            } catch (t2: Throwable) {
                throw IllegalStateException(friendlyError(t2.message ?: t.message))
            }
        }
    }

    private fun runWithClientFallback(
        url: String, format: String, quality: String, embedArtwork: Boolean,
        workDir: File, processId: String, onProgress: ProgressFn
    ): File = try {
        runDownload(url, format, quality, embedArtwork, workDir, processId, onProgress, attempt = 0)
    } catch (t: Throwable) {
        if (t is CanceledByUser || !isClientProblem(t.message)) throw t
        workDir.listFiles()?.forEach { it.deleteRecursively() }
        runDownload(url, format, quality, embedArtwork, workDir, processId, onProgress, attempt = 1)
    }

    private fun runDownload(
        url: String, format: String, quality: String, embedArtwork: Boolean,
        workDir: File, processId: String, onProgress: ProgressFn, attempt: Int
    ): File {
        workDir.mkdirs()
        val audioFormat = if (format == "m4a") "m4a" else "mp3"
        val request = YoutubeDLRequest(url)
            .addOption("--no-playlist")
            .addOption("--no-mtime")
            .addOption("--socket-timeout", "20")
            .addOption("--retries", "5")
            .addOption("--fragment-retries", "5")
            .addOption("-f", if (audioFormat == "m4a") "bestaudio[ext=m4a]/bestaudio/best" else "bestaudio/best")
            .addOption("-x")
            .addOption("--audio-format", audioFormat)
            .addOption("--audio-quality", qualityArg(quality))
            .addOption("--embed-metadata")
            .addOption("-o", File(workDir, "%(id)s.%(ext)s").absolutePath)
            .withClients(url, attempt)
        if (embedArtwork) {
            request.addOption("--embed-thumbnail")
                .addOption("--convert-thumbnails", "jpg")
                // Square-crop the video thumbnail so it looks like real album art.
                .addOption("--ppa", "ThumbnailsConvertor+FFmpeg_o:-c:v mjpeg -qmin 1 -qscale:v 1 -vf crop=\"'if(gt(ih,iw),iw,ih)':'if(gt(iw,ih),ih,iw)'\"")
        }
        try {
            YoutubeDL.execute(request, processId) { progress, eta, line ->
                val stage = when {
                    line.contains("[ExtractAudio]") || line.contains("[FFmpeg") -> DownloadStage.CONVERTING
                    line.contains("[EmbedThumbnail]") || line.contains("[Metadata]") || line.contains("[ThumbnailsConvertor]") -> DownloadStage.EMBEDDING
                    else -> DownloadStage.DOWNLOADING
                }
                onProgress(progress, eta, stage)
            }
        } catch (e: YoutubeDL.CanceledException) {
            throw CanceledByUser()
        }
        return workDir.listFiles()
            ?.filter { it.isFile && it.extension.lowercase() == audioFormat }
            ?.maxByOrNull { it.length() }
            ?: error("FFmpeg finished but no ${audioFormat.uppercase()} file was produced.")
    }

    fun cancel(processId: String) {
        runCatching { YoutubeDL.destroyProcessById(processId) }
    }

    /** "320" → "320K", "best" → VBR quality 0. */
    private fun qualityArg(q: String) = if (q == "best" || q.isBlank()) "0" else "${q.filter(Char::isDigit).ifBlank { "320" }}K"

    fun friendlyError(raw: String?): String {
        val text = raw.orEmpty()
        val line = text.lineSequence().map { it.trim() }.lastOrNull { it.startsWith("ERROR:") }?.removePrefix("ERROR:")?.trim()
            ?: text.lineSequence().map { it.trim() }.lastOrNull { it.isNotBlank() }.orEmpty()
        return when {
            text.contains("Sign in to confirm", true) && text.contains("bot", true) ->
                "YouTube asked for a bot check. Update the engine in Settings → Downloads, or try again later."
            text.contains("Sign in to confirm your age", true) || text.contains("age-restricted", true) ->
                "This video is age-restricted and can't be downloaded without signing in."
            text.contains("Private video", true) -> "This video is private."
            text.contains("Video unavailable", true) -> "The video is unavailable, removed, or blocked in your region."
            text.contains("Unsupported URL", true) -> "This link isn't supported. Paste a normal video or music link."
            text.contains("Unable to resolve host", true) || text.contains("Failed to resolve", true) ||
                text.contains("Temporary failure in name resolution", true) || text.contains("Network is unreachable", true) ->
                "No internet connection."
            text.contains("HTTP Error 403", true) || text.contains("nsig", true) || text.contains("Unable to extract", true) ||
                text.contains("Requested format is not available", true) ->
                "YouTube changed something. Update the download engine (Settings → Downloads → Update engine) and retry."
            text.contains("No space left", true) -> "Not enough free storage space."
            line.isBlank() -> "Download failed."
            else -> line.take(300)
        }
    }

    /** True when updating yt-dlp is the likely fix. */
    fun shouldSuggestUpdate(message: String?): Boolean =
        message?.contains("Update the", true) == true || message?.contains("bot check", true) == true
}

/** Optional companion server (see /backend) for users who prefer converting on a PC. */
class ServerEngine(private val baseUrl: String) {
    private fun open(path: String, method: String, timeoutMs: Int): HttpURLConnection =
        (URL("${baseUrl.trim().trimEnd('/')}$path").openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = timeoutMs
            requestMethod = method
            useCaches = false
            setRequestProperty("User-Agent", "AuraMusic/2.0 Android")
            setRequestProperty("Accept", "application/json, audio/mpeg, audio/mp4")
            if (method == "POST") {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        }

    fun health(): String {
        val conn = open("/health", "GET", 10_000)
        try {
            val json = readJson(conn)
            if (!json.optBoolean("ok")) error("Server reachable, but FFmpeg is missing on the server.")
            return "Connected • FFmpeg ready • yt-dlp ${json.optString("yt_dlp", "?")}"
        } finally {
            conn.disconnect()
        }
    }

    fun fetchInfo(url: String): MediaInfo {
        val conn = open("/info", "POST", 60_000)
        try {
            conn.outputStream.use { it.write(JSONObject().put("url", url).toString().toByteArray()) }
            val o = readJson(conn)
            return MediaInfo(
                id = o.optString("id"),
                title = o.optString("title", "Unknown title"),
                artist = o.optString("artist", "Unknown artist"),
                durationSec = o.optInt("duration"),
                thumbnail = o.optString("thumbnail").takeIf { it.isNotBlank() && it != "null" },
                webpageUrl = o.optString("webpage_url", url)
            )
        } finally {
            conn.disconnect()
        }
    }

    fun download(url: String, format: String, quality: String, out: File, isCanceled: () -> Boolean, onProgress: ProgressFn): File {
        val conn = open("/convert", "POST", 20 * 60_000)
        try {
            onProgress(-1f, -1, DownloadStage.CONVERTING)
            conn.outputStream.use {
                it.write(JSONObject().put("url", url).put("format", format).put("quality", quality).toString().toByteArray())
            }
            if (conn.responseCode !in 200..299) throw IllegalStateException(errorBody(conn))
            val total = conn.contentLengthLong
            out.parentFile?.mkdirs()
            conn.inputStream.use { input ->
                out.outputStream().buffered().use { output ->
                    val buf = ByteArray(64 * 1024)
                    var copied = 0L
                    while (true) {
                        if (isCanceled()) throw CanceledByUser()
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        copied += n
                        onProgress(if (total > 0) copied * 100f / total else -1f, -1, DownloadStage.DOWNLOADING)
                    }
                }
            }
            if (out.length() == 0L) error("The server returned an empty file.")
            return out
        } finally {
            conn.disconnect()
        }
    }

    private fun readJson(conn: HttpURLConnection): JSONObject {
        if (conn.responseCode !in 200..299) throw IllegalStateException(errorBody(conn))
        return JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
    }

    private fun errorBody(conn: HttpURLConnection): String {
        val body = runCatching { conn.errorStream?.bufferedReader()?.use { it.readText() } }.getOrNull().orEmpty()
        val detail = runCatching { JSONObject(body).optString("detail") }.getOrNull()?.takeIf { it.isNotBlank() }
        return "Server ${conn.responseCode}: ${detail ?: body.take(200).ifBlank { conn.responseMessage ?: "request failed" }}"
    }
}
