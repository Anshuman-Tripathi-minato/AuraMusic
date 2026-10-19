package com.auramusic.app.download

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.ContextCompat
import com.auramusic.app.data.AuraPrefs
import com.auramusic.app.data.DownloadEngineMode
import com.auramusic.app.data.DownloadRecord
import com.auramusic.app.data.LibraryRepository
import com.auramusic.app.ui.UiEvents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

/**
 * App-wide download queue. Lives outside the UI so downloads keep running while you browse
 * the app; [DownloadService] keeps the process alive and shows a progress notification.
 */
object DownloadCenter {
    enum class Status { QUEUED, RUNNING, DONE, FAILED, CANCELED }

    data class Preview(
        val loading: Boolean = false,
        val url: String = "",
        val info: MediaInfo? = null,
        val error: String? = null
    )

    data class Task(
        val id: String,
        val url: String,
        val title: String,
        val artist: String,
        val thumbnail: String?,
        val format: String,
        val quality: String,
        val status: Status = Status.QUEUED,
        val stage: DownloadStage = DownloadStage.FETCHING,
        val percent: Float = -1f,
        val etaSec: Long = -1,
        val error: String? = null,
        val resultUri: String? = null,
        val location: String? = null
    ) {
        val active get() = status == Status.QUEUED || status == Status.RUNNING
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var worker: Job? = null
    private var previewJob: Job? = null
    private val canceled = mutableSetOf<String>()

    private val _preview = MutableStateFlow(Preview())
    val preview: StateFlow<Preview> = _preview.asStateFlow()

    private val _tasks = MutableStateFlow<List<Task>>(emptyList())
    val tasks: StateFlow<List<Task>> = _tasks.asStateFlow()

    private val _engineBusy = MutableStateFlow<String?>(null)
    val engineBusy: StateFlow<String?> = _engineBusy.asStateFlow()

    // ---------------------------------------------------------------- preview

    fun fetchInfo(context: Context, raw: String, youtubeOnly: Boolean) {
        val app = context.applicationContext
        val url = when (val r = normalizeUrl(raw, youtubeOnly)) {
            is UrlResult.Ok -> r.url
            is UrlResult.Invalid -> {
                _preview.value = Preview(url = raw, error = r.message)
                return
            }
        }
        previewJob?.cancel()
        _preview.value = Preview(loading = true, url = url)
        previewJob = scope.launch {
            val settings = AuraPrefs.settings.value
            val result = runCatching {
                if (settings.engineMode == DownloadEngineMode.SERVER) ServerEngine(settings.serverUrl).fetchInfo(url)
                else YtDlpEngine.fetchInfo(app, url)
            }
            _preview.value = result.fold(
                onSuccess = { Preview(url = url, info = it) },
                onFailure = { Preview(url = url, error = it.message ?: "Couldn't read this link.") }
            )
        }
    }

    fun clearPreview() {
        previewJob?.cancel()
        _preview.value = Preview()
    }

    // ---------------------------------------------------------------- queue

    fun enqueue(context: Context, info: MediaInfo, url: String) {
        val settings = AuraPrefs.settings.value
        val task = Task(
            id = UUID.randomUUID().toString(),
            url = url,
            title = info.title,
            artist = info.artist,
            thumbnail = info.thumbnail,
            format = settings.downloadFormat,
            quality = settings.downloadQuality
        )
        _tasks.update { it + task }
        _preview.value = Preview()
        startWorker(context.applicationContext)
    }

    fun cancel(id: String) {
        val task = _tasks.value.firstOrNull { it.id == id } ?: return
        synchronized(canceled) { canceled += id }
        if (task.status == Status.QUEUED) setTask(id) { it.copy(status = Status.CANCELED) }
        else YtDlpEngine.cancel(id)
    }

    fun retry(context: Context, id: String) {
        synchronized(canceled) { canceled -= id }
        setTask(id) { it.copy(status = Status.QUEUED, error = null, percent = -1f, stage = DownloadStage.FETCHING) }
        startWorker(context.applicationContext)
    }

    fun dismiss(id: String) = _tasks.update { list -> list.filterNot { it.id == id && !it.active } }

    fun clearFinished() = _tasks.update { list -> list.filter { it.active } }

    fun hasActive() = _tasks.value.any { it.active }

    private fun setTask(id: String, transform: (Task) -> Task) =
        _tasks.update { list -> list.map { if (it.id == id) transform(it) else it } }

    @Synchronized
    private fun startWorker(context: Context) {
        runCatching { ContextCompat.startForegroundService(context, Intent(context, DownloadService::class.java)) }
        if (worker?.isActive == true) return
        worker = scope.launch {
            while (true) {
                val next = _tasks.value.firstOrNull { it.status == Status.QUEUED } ?: break
                runTask(context, next)
            }
        }
    }

    private fun isCanceled(id: String) = synchronized(canceled) { id in canceled }

    private fun runTask(context: Context, task: Task) {
        setTask(task.id) { it.copy(status = Status.RUNNING, stage = DownloadStage.FETCHING, percent = -1f) }
        val settings = AuraPrefs.settings.value
        val workDir = File(context.cacheDir, "downloads/${task.id}")
        val progress: ProgressFn = { percent, eta, stage ->
            setTask(task.id) { it.copy(percent = percent, etaSec = eta, stage = stage) }
        }
        try {
            val file = if (settings.engineMode == DownloadEngineMode.SERVER) {
                ServerEngine(settings.serverUrl).download(
                    task.url, task.format, task.quality,
                    File(workDir, "audio.${if (task.format == "m4a") "m4a" else "mp3"}"),
                    isCanceled = { isCanceled(task.id) }, onProgress = progress
                )
            } else {
                YtDlpEngine.download(context, task.url, task.format, task.quality, settings.embedArtwork, workDir, task.id, progress)
            }
            if (isCanceled(task.id)) throw CanceledByUser()
            progress(100f, 0, DownloadStage.SAVING)
            val saved = MediaSaver.saveAudio(context, file, task.title, task.artist, settings.downloadFolder)
            AuraPrefs.addDownload(
                DownloadRecord(
                    title = task.title, uri = saved.uri.toString(), timestamp = System.currentTimeMillis(),
                    artist = task.artist, sourceUrl = task.url, format = file.extension.lowercase(), location = saved.location
                )
            )
            setTask(task.id) { it.copy(status = Status.DONE, percent = 100f, resultUri = saved.uri.toString(), location = saved.location) }
            UiEvents.show("Saved “${task.title}” to ${saved.location}")
            LibraryRepository.refresh()
        } catch (e: CanceledByUser) {
            setTask(task.id) { it.copy(status = Status.CANCELED) }
        } catch (t: Throwable) {
            if (isCanceled(task.id)) setTask(task.id) { it.copy(status = Status.CANCELED) }
            else setTask(task.id) { it.copy(status = Status.FAILED, error = t.message ?: "Download failed") }
        } finally {
            workDir.deleteRecursively()
            synchronized(canceled) { canceled -= task.id }
        }
    }

    // ---------------------------------------------------------------- engine

    fun updateEngine(context: Context, onDone: (String) -> Unit = {}) {
        if (_engineBusy.value != null) return
        val app = context.applicationContext
        _engineBusy.value = "Updating download engine…"
        scope.launch {
            val msg = runCatching { YtDlpEngine.update(app) }.getOrElse { "Engine update failed: ${it.message ?: "network error"}" }
            AuraPrefs.raw().edit().putLong("engine_checked_at", System.currentTimeMillis()).apply()
            _engineBusy.value = null
            UiEvents.show(msg)
            onDone(msg)
        }
    }

    /** Called at app start: prepares the engine and refreshes yt-dlp every few days. */
    fun warmUp(context: Context) {
        val app = context.applicationContext
        scope.launch {
            if (!YtDlpEngine.ensureReady(app)) return@launch
            val last = AuraPrefs.raw().getLong("engine_checked_at", 0L)
            if (System.currentTimeMillis() - last > 3L * 24 * 60 * 60 * 1000 && _engineBusy.value == null) {
                runCatching { YtDlpEngine.update(app) }
                AuraPrefs.raw().edit().putLong("engine_checked_at", System.currentTimeMillis()).apply()
            }
        }
    }

    // ---------------------------------------------------------------- URLs

    sealed interface UrlResult {
        data class Ok(val url: String) : UrlResult
        data class Invalid(val message: String) : UrlResult
    }

    private val youtubeHosts = setOf("youtube.com", "m.youtube.com", "music.youtube.com", "youtu.be", "youtube-nocookie.com")

    fun normalizeUrl(raw: String, youtubeOnly: Boolean): UrlResult {
        val text = raw.trim()
        if (text.isBlank()) return UrlResult.Invalid("Paste a link first.")
        // Shared text often looks like "Song title https://youtu.be/xyz" — pick out the link.
        val found = Regex("https?://\\S+", RegexOption.IGNORE_CASE).find(text)?.value?.trimEnd(')', ']', '.', ',', '"', '\'')
        val candidate = when {
            found != null -> found
            text.matches(Regex("[A-Za-z0-9_-]{11}")) -> "https://www.youtube.com/watch?v=$text"
            Regex("^(www\\.|m\\.|music\\.)?(youtube\\.com|youtu\\.be)/", RegexOption.IGNORE_CASE).containsMatchIn(text) -> "https://$text"
            !youtubeOnly && Regex("^[\\w.-]+\\.[a-z]{2,}/\\S*", RegexOption.IGNORE_CASE).matches(text) -> "https://$text"
            else -> return UrlResult.Invalid(
                if (youtubeOnly) "That doesn't look like a YouTube link. Paste a youtube.com or youtu.be URL."
                else "That doesn't look like a web link."
            )
        }
        val uri = runCatching { Uri.parse(candidate) }.getOrNull()
        val host = uri?.host?.lowercase()?.removePrefix("www.")
            ?: return UrlResult.Invalid("That link is incomplete.")
        val isYoutube = host in youtubeHosts
        if (youtubeOnly && !isYoutube) {
            return UrlResult.Invalid("This tab is for YouTube links. Use the URL tab for other sites.")
        }
        if (isYoutube) {
            val path = uri.path.orEmpty()
            val videoId = when {
                host == "youtu.be" -> uri.pathSegments.firstOrNull()
                path.startsWith("/shorts/") || path.startsWith("/live/") || path.startsWith("/embed/") -> uri.pathSegments.getOrNull(1)
                else -> uri.getQueryParameter("v")
            }
            if (videoId.isNullOrBlank() || !videoId.matches(Regex("[A-Za-z0-9_-]{11}"))) {
                return UrlResult.Invalid(
                    if (uri.getQueryParameter("list") != null) "Playlists aren't supported. Open a single video and share/copy its link."
                    else "This YouTube link is incomplete. Copy the full video link and try again."
                )
            }
            return UrlResult.Ok("https://www.youtube.com/watch?v=$videoId")
        }
        return UrlResult.Ok(candidate)
    }
}
