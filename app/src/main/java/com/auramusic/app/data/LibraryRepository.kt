package com.auramusic.app.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Single source of truth for the local music library (MediaStore). Shared by the UI and
 * the playback service, and refreshed automatically whenever MediaStore changes — e.g.
 * right after a download finishes.
 */
object LibraryRepository {
    private lateinit var appContext: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()
    private var observerRegistered = false
    private var pendingRefresh: Job? = null
    private var allSongs: List<MusicItem> = emptyList()

    private val _songs = MutableStateFlow<List<MusicItem>>(emptyList())
    val songs: StateFlow<List<MusicItem>> = _songs.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _hasPermission = MutableStateFlow(false)
    val hasPermission: StateFlow<Boolean> = _hasPermission.asStateFlow()

    @Volatile private var loaded = false
    @Volatile private var byId: Map<Long, MusicItem> = emptyMap()
    @Volatile private var byKey: Map<String, MusicItem> = emptyMap()

    fun init(context: Context) {
        appContext = context.applicationContext
        _hasPermission.value = checkPermission()
        // Re-apply the "hide short clips" filter whenever that setting changes.
        scope.launch {
            AuraPrefs.settings.map { it.minDurationSec }.distinctUntilChanged().drop(1).collect { publish(allSongs) }
        }
    }

    val audioPermission: String
        get() = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE

    fun checkPermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, audioPermission) == PackageManager.PERMISSION_GRANTED

    fun song(id: Long): MusicItem? = byId[id]
    fun songByKey(key: String): MusicItem? = byKey[key]

    fun refresh() {
        scope.launch { scanNow() }
    }

    /** Blocking load used by the playback service when Android Auto connects before the UI. */
    fun ensureLoadedBlocking(): List<MusicItem> {
        if (!loaded) scanNow()
        return _songs.value
    }

    private fun scanNow() {
        val granted = checkPermission()
        _hasPermission.value = granted
        if (!granted) {
            publish(emptyList())
            return
        }
        registerObserver()
        _loading.value = true
        try {
            val result = runCatching { scan(appContext) }.getOrElse { emptyList() }
            synchronized(lock) { allSongs = result }
            publish(result)
            loaded = true
        } finally {
            _loading.value = false
        }
    }

    private fun publish(all: List<MusicItem>) {
        val minMs = AuraPrefs.settings.value.minDurationSec * 1000L
        val visible = if (minMs > 0) all.filter { it.durationMs <= 0 || it.durationMs >= minMs } else all
        byId = visible.associateBy { it.id }
        byKey = visible.associateBy { it.key }
        _songs.value = visible
    }

    private fun registerObserver() {
        if (observerRegistered) return
        observerRegistered = true
        Handler(Looper.getMainLooper()).post {
            appContext.contentResolver.registerContentObserver(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                true,
                object : ContentObserver(Handler(Looper.getMainLooper())) {
                    override fun onChange(selfChange: Boolean) {
                        pendingRefresh?.cancel()
                        pendingRefresh = scope.launch {
                            delay(1200) // debounce bursts of MediaStore events
                            scanNow()
                        }
                    }
                }
            )
        }
    }

    private fun scan(context: Context): List<MusicItem> {
        val resolver = context.contentResolver
        val collection = if (Build.VERSION.SDK_INT >= 29) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val pathColumn = if (Build.VERSION.SDK_INT >= 29) MediaStore.Audio.Media.RELATIVE_PATH else @Suppress("DEPRECATION") MediaStore.Audio.Media.DATA
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.DISPLAY_NAME,
            pathColumn
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sort = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"
        val storageRoot = runCatching { @Suppress("DEPRECATION") Environment.getExternalStorageDirectory().absolutePath }.getOrDefault("/storage/emulated/0")
        val result = ArrayList<MusicItem>()
        resolver.query(collection, projection, selection, null, sort)?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val durationCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val trackCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
            val yearCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
            val addedCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val sizeCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            val mimeCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val pathCol = c.getColumnIndexOrThrow(pathColumn)
            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val albumId = c.getLong(albumIdCol)
                val rawPath = c.getString(pathCol).orEmpty()
                val relative = if (Build.VERSION.SDK_INT >= 29) rawPath else {
                    rawPath.substringBeforeLast('/', "").removePrefix(storageRoot).trim('/')
                }
                val displayName = c.getString(nameCol).orEmpty().substringBeforeLast('.')
                val rawArtist = c.getString(artistCol).orEmpty()
                val rawAlbum = c.getString(albumCol).orEmpty()
                val track = c.getInt(trackCol)
                result += MusicItem(
                    id = id,
                    uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id),
                    title = c.getString(titleCol).orEmpty().ifBlank { displayName.ifBlank { "Unknown title" } },
                    artist = rawArtist.takeUnless { it.isBlank() || it == "<unknown>" } ?: "Unknown artist",
                    album = rawAlbum.takeUnless { it.isBlank() || it == "<unknown>" } ?: "Unknown album",
                    durationMs = c.getLong(durationCol).coerceAtLeast(0L),
                    albumId = albumId,
                    albumArtUri = if (albumId > 0) Uri.parse("content://media/external/audio/albumart/$albumId") else null,
                    relativePath = relative,
                    // MediaStore encodes disc number in the thousands (e.g. 2005 = disc 2, track 5).
                    track = if (track >= 1000) track % 1000 else track,
                    year = c.getInt(yearCol),
                    dateAddedSec = c.getLong(addedCol),
                    sizeBytes = c.getLong(sizeCol),
                    mimeType = c.getString(mimeCol).orEmpty()
                )
            }
        }
        return result
    }
}
