package com.auramusic.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Process-wide preference store. Every collection is exposed as a [StateFlow] so the UI,
 * the playback service and Android Auto always see the same, live data.
 */
object AuraPrefs {
    private lateinit var prefs: SharedPreferences
    @Volatile private var initialized = false

    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val _favorites = MutableStateFlow<Set<String>>(emptySet())
    val favorites: StateFlow<Set<String>> = _favorites.asStateFlow()

    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    private val _playCounts = MutableStateFlow<Map<String, Int>>(emptyMap())
    val playCounts: StateFlow<Map<String, Int>> = _playCounts.asStateFlow()

    private val _lastPlayed = MutableStateFlow<Map<String, Long>>(emptyMap())
    val lastPlayed: StateFlow<Map<String, Long>> = _lastPlayed.asStateFlow()

    private val _downloads = MutableStateFlow<List<DownloadRecord>>(emptyList())
    val downloads: StateFlow<List<DownloadRecord>> = _downloads.asStateFlow()

    private val _recentSearches = MutableStateFlow<List<String>>(emptyList())
    val recentSearches: StateFlow<List<String>> = _recentSearches.asStateFlow()

    @Synchronized
    fun init(context: Context) {
        if (initialized) return
        prefs = context.applicationContext.getSharedPreferences("aura_music_prefs", Context.MODE_PRIVATE)
        _settings.value = readSettings()
        _favorites.value = prefs.getStringSet("favorites", emptySet())?.toSet() ?: emptySet()
        _playlists.value = parsePlaylists(prefs.getString("playlists", "[]"))
        _playCounts.value = parseIntMap(prefs.getString("play_counts", "{}"))
        _lastPlayed.value = parseLongMap(prefs.getString("last_played", "{}"))
        _downloads.value = parseDownloads(prefs.getString("downloads", "[]"))
        _recentSearches.value = parseStringList(prefs.getString("recent_searches", "[]"))
        initialized = true
    }

    fun raw(): SharedPreferences = prefs

    // ---------------------------------------------------------------- settings

    private fun readSettings(): AppSettings {
        val d = AppSettings()
        return AppSettings(
            themeMode = runCatching { ThemeMode.valueOf(prefs.getString("theme_mode", null) ?: "") }
                .getOrElse { if (prefs.getBoolean("dark_theme", true)) ThemeMode.DARK else ThemeMode.LIGHT },
            userName = prefs.getString("user_name", d.userName) ?: d.userName,
            serverUrl = prefs.getString("server_url", d.serverUrl) ?: d.serverUrl,
            engineMode = runCatching { DownloadEngineMode.valueOf(prefs.getString("engine_mode", "") ?: "") }.getOrDefault(d.engineMode),
            downloadFormat = prefs.getString("download_format", d.downloadFormat) ?: d.downloadFormat,
            downloadQuality = prefs.getString("download_quality", d.downloadQuality) ?: d.downloadQuality,
            downloadFolder = prefs.getString("download_folder", d.downloadFolder) ?: d.downloadFolder,
            embedArtwork = prefs.getBoolean("embed_artwork", d.embedArtwork),
            minDurationSec = prefs.getInt("min_duration_sec", d.minDurationSec),
            playbackSpeed = prefs.getFloat("playback_speed", d.playbackSpeed),
            skipSilence = prefs.getBoolean("skip_silence", d.skipSilence),
            keepScreenOnInCar = prefs.getBoolean("car_keep_screen_on", d.keepScreenOnInCar)
        )
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        val s = transform(_settings.value).let {
            it.copy(
                serverUrl = it.serverUrl.trim().trimEnd('/'),
                downloadFolder = sanitizeFolder(it.downloadFolder),
                userName = it.userName.trim().take(40).ifBlank { "Music Lover" }
            )
        }
        _settings.value = s
        prefs.edit {
            putString("theme_mode", s.themeMode.name)
            putString("user_name", s.userName)
            putString("server_url", s.serverUrl)
            putString("engine_mode", s.engineMode.name)
            putString("download_format", s.downloadFormat)
            putString("download_quality", s.downloadQuality)
            putString("download_folder", s.downloadFolder)
            putBoolean("embed_artwork", s.embedArtwork)
            putInt("min_duration_sec", s.minDurationSec)
            putFloat("playback_speed", s.playbackSpeed)
            putBoolean("skip_silence", s.skipSilence)
            putBoolean("car_keep_screen_on", s.keepScreenOnInCar)
        }
    }

    fun sanitizeFolder(value: String): String =
        value.split('/', '\\')
            .map { part -> part.replace(Regex("[:*?\"<>|\\u0000-\\u001F]"), "").trim().trim('.') }
            .filter { it.isNotBlank() }
            .joinToString("/")
            .take(80)
            .ifBlank { "AuraMusic" }

    // ---------------------------------------------------------------- favorites

    fun isFavorite(key: String) = key in _favorites.value

    fun toggleFavorite(key: String): Boolean {
        val set = _favorites.value.toMutableSet()
        val added = set.add(key)
        if (!added) set.remove(key)
        _favorites.value = set
        prefs.edit { putStringSet("favorites", set) }
        return added
    }

    // ---------------------------------------------------------------- playlists

    private fun savePlaylists(list: List<Playlist>) {
        _playlists.value = list
        prefs.edit { putString("playlists", playlistsToJson(list).toString()) }
    }

    /** Returns false when a playlist with the same name already exists. */
    fun createPlaylist(name: String, initialSongs: List<String> = emptyList()): Boolean {
        val clean = name.trim().take(60)
        if (clean.isBlank() || _playlists.value.any { it.name.equals(clean, true) }) return false
        savePlaylists(_playlists.value + Playlist(clean, initialSongs.distinct(), System.currentTimeMillis()))
        return true
    }

    fun renamePlaylist(old: String, new: String): Boolean {
        val clean = new.trim().take(60)
        if (clean.isBlank()) return false
        if (!clean.equals(old, true) && _playlists.value.any { it.name.equals(clean, true) }) return false
        savePlaylists(_playlists.value.map { if (it.name == old) it.copy(name = clean) else it })
        return true
    }

    fun deletePlaylist(name: String) = savePlaylists(_playlists.value.filterNot { it.name == name })

    /** Returns false if the song was already in the playlist. */
    fun addToPlaylist(name: String, songKey: String): Boolean {
        val target = _playlists.value.firstOrNull { it.name == name } ?: return false
        if (songKey in target.songUris) return false
        savePlaylists(_playlists.value.map { if (it.name == name) it.copy(songUris = it.songUris + songKey) else it })
        return true
    }

    fun removeFromPlaylist(name: String, songKey: String) =
        savePlaylists(_playlists.value.map { if (it.name == name) it.copy(songUris = it.songUris - songKey) else it })

    fun moveInPlaylist(name: String, songKey: String, delta: Int) {
        savePlaylists(_playlists.value.map { p ->
            if (p.name != name) return@map p
            val list = p.songUris.toMutableList()
            val from = list.indexOf(songKey)
            if (from < 0) return@map p
            val to = (from + delta).coerceIn(0, list.lastIndex)
            list.add(to, list.removeAt(from))
            p.copy(songUris = list)
        })
    }

    // ---------------------------------------------------------------- statistics

    fun trackPlayed(songKey: String) {
        val counts = _playCounts.value.toMutableMap().apply { this[songKey] = (this[songKey] ?: 0) + 1 }
        val times = _lastPlayed.value.toMutableMap().apply { this[songKey] = System.currentTimeMillis() }
        // Keep the history bounded so preferences never grow without limit.
        val trimmedTimes = if (times.size > 1500) times.entries.sortedByDescending { it.value }.take(1000).associate { it.toPair() } else times
        _playCounts.value = counts
        _lastPlayed.value = trimmedTimes
        prefs.edit {
            putString("play_counts", JSONObject(counts as Map<*, *>).toString())
            putString("last_played", JSONObject(trimmedTimes as Map<*, *>).toString())
        }
    }

    fun clearHistory() {
        _playCounts.value = emptyMap()
        _lastPlayed.value = emptyMap()
        prefs.edit { putString("play_counts", "{}"); putString("last_played", "{}") }
    }

    // ---------------------------------------------------------------- resume state

    fun saveQueue(ids: List<Long>, index: Int, positionMs: Long) {
        prefs.edit {
            putString("resume_queue", JSONArray(ids.take(2000)).toString())
            putInt("resume_index", index)
            putLong("resume_position", positionMs)
        }
    }

    fun loadQueue(): Triple<List<Long>, Int, Long> {
        val arr = runCatching { JSONArray(prefs.getString("resume_queue", "[]")) }.getOrDefault(JSONArray())
        val ids = List(arr.length()) { arr.optLong(it, -1L) }.filter { it >= 0 }
        return Triple(ids, prefs.getInt("resume_index", 0), prefs.getLong("resume_position", 0L))
    }

    // ---------------------------------------------------------------- downloads

    fun addDownload(record: DownloadRecord) {
        val list = (listOf(record) + _downloads.value.filterNot { it.uri == record.uri }).take(200)
        _downloads.value = list
        prefs.edit { putString("downloads", downloadsToJson(list).toString()) }
    }

    fun removeDownload(uri: String) {
        val list = _downloads.value.filterNot { it.uri == uri }
        _downloads.value = list
        prefs.edit { putString("downloads", downloadsToJson(list).toString()) }
    }

    fun clearDownloads() {
        _downloads.value = emptyList()
        prefs.edit { putString("downloads", "[]") }
    }

    // ---------------------------------------------------------------- searches

    fun addRecentSearch(query: String) {
        val q = query.trim()
        if (q.length < 2) return
        val list = (listOf(q) + _recentSearches.value.filterNot { it.equals(q, true) }).take(12)
        _recentSearches.value = list
        prefs.edit { putString("recent_searches", JSONArray(list).toString()) }
    }

    fun clearRecentSearches() {
        _recentSearches.value = emptyList()
        prefs.edit { putString("recent_searches", "[]") }
    }

    // ---------------------------------------------------------------- backup

    fun exportJson(): JSONObject = JSONObject()
        .put("app", "AuraMusic")
        .put("version", 2)
        .put("exportedAt", System.currentTimeMillis())
        .put("settings", JSONObject()
            .put("themeMode", _settings.value.themeMode.name)
            .put("userName", _settings.value.userName)
            .put("serverUrl", _settings.value.serverUrl)
            .put("engineMode", _settings.value.engineMode.name)
            .put("downloadFormat", _settings.value.downloadFormat)
            .put("downloadQuality", _settings.value.downloadQuality)
            .put("downloadFolder", _settings.value.downloadFolder)
            .put("embedArtwork", _settings.value.embedArtwork)
            .put("minDurationSec", _settings.value.minDurationSec)
            .put("playbackSpeed", _settings.value.playbackSpeed.toDouble())
            .put("skipSilence", _settings.value.skipSilence))
        .put("favorites", JSONArray(_favorites.value.toList()))
        .put("playlists", playlistsToJson(_playlists.value))
        .put("playCounts", JSONObject(_playCounts.value as Map<*, *>))
        .put("lastPlayed", JSONObject(_lastPlayed.value as Map<*, *>))
        .put("downloads", downloadsToJson(_downloads.value))
        .put("equalizer", prefs.getString("eq_state", null)?.let { runCatching { JSONObject(it) }.getOrNull() } ?: JSONObject())

    fun importJson(obj: JSONObject) {
        val s = obj.optJSONObject("settings")
        if (s != null) {
            updateSettings { cur ->
                cur.copy(
                    themeMode = runCatching { ThemeMode.valueOf(s.optString("themeMode")) }.getOrDefault(cur.themeMode),
                    userName = s.optString("userName", cur.userName),
                    serverUrl = s.optString("serverUrl", cur.serverUrl),
                    engineMode = runCatching { DownloadEngineMode.valueOf(s.optString("engineMode")) }.getOrDefault(cur.engineMode),
                    downloadFormat = s.optString("downloadFormat", cur.downloadFormat),
                    downloadQuality = s.optString("downloadQuality", cur.downloadQuality),
                    downloadFolder = s.optString("downloadFolder", cur.downloadFolder),
                    embedArtwork = s.optBoolean("embedArtwork", cur.embedArtwork),
                    minDurationSec = s.optInt("minDurationSec", cur.minDurationSec),
                    playbackSpeed = s.optDouble("playbackSpeed", cur.playbackSpeed.toDouble()).toFloat().coerceIn(0.25f, 3f),
                    skipSilence = s.optBoolean("skipSilence", cur.skipSilence)
                )
            }
        } else if (obj.has("darkTheme") || obj.has("serverUrl")) {
            // Version 1 backups.
            updateSettings { cur ->
                cur.copy(
                    themeMode = if (obj.optBoolean("darkTheme", true)) ThemeMode.DARK else ThemeMode.LIGHT,
                    serverUrl = obj.optString("serverUrl", cur.serverUrl)
                )
            }
        }
        obj.optJSONArray("favorites")?.let { arr ->
            val set = List(arr.length()) { arr.optString(it) }.filter { it.isNotBlank() }.toSet()
            _favorites.value = set
            prefs.edit { putStringSet("favorites", set) }
        }
        obj.optJSONArray("playlists")?.let { savePlaylists(parsePlaylists(it.toString())) }
        obj.optJSONObject("playCounts")?.let {
            _playCounts.value = parseIntMap(it.toString())
            prefs.edit { putString("play_counts", it.toString()) }
        }
        obj.optJSONObject("lastPlayed")?.let {
            _lastPlayed.value = parseLongMap(it.toString())
            prefs.edit { putString("last_played", it.toString()) }
        }
        obj.optJSONArray("downloads")?.let {
            _downloads.value = parseDownloads(it.toString())
            prefs.edit { putString("downloads", it.toString()) }
        }
        obj.optJSONObject("equalizer")?.takeIf { it.length() > 0 }?.let {
            prefs.edit { putString("eq_state", it.toString()) }
        }
    }

    // ---------------------------------------------------------------- JSON helpers

    private fun playlistsToJson(list: List<Playlist>) = JSONArray().also { arr ->
        list.forEach { p ->
            arr.put(JSONObject().put("name", p.name).put("songs", JSONArray(p.songUris)).put("created", p.createdAt))
        }
    }

    private fun parsePlaylists(raw: String?): List<Playlist> = runCatching {
        val arr = JSONArray(raw ?: "[]")
        val seen = mutableSetOf<String>()
        buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val name = o.optString("name").trim()
                if (name.isBlank() || !seen.add(name.lowercase())) continue
                val songs = o.optJSONArray("songs") ?: JSONArray()
                add(Playlist(name, List(songs.length()) { songs.optString(it) }.filter { it.isNotBlank() }.distinct(), o.optLong("created", 0L)))
            }
        }
    }.getOrDefault(emptyList())

    private fun downloadsToJson(list: List<DownloadRecord>) = JSONArray().also { arr ->
        list.forEach {
            arr.put(JSONObject().put("title", it.title).put("uri", it.uri).put("time", it.timestamp)
                .put("artist", it.artist).put("source", it.sourceUrl).put("format", it.format).put("location", it.location))
        }
    }

    private fun parseDownloads(raw: String?): List<DownloadRecord> = runCatching {
        val arr = JSONArray(raw ?: "[]")
        buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val uri = o.optString("uri")
                if (uri.isBlank()) continue
                add(DownloadRecord(
                    title = o.optString("title").ifBlank { "Aura download" },
                    uri = uri,
                    timestamp = o.optLong("time"),
                    artist = o.optString("artist"),
                    sourceUrl = o.optString("source"),
                    format = o.optString("format", "mp3"),
                    location = o.optString("location")
                ))
            }
        }.sortedByDescending { it.timestamp }
    }.getOrDefault(emptyList())

    private fun parseIntMap(raw: String?): Map<String, Int> = runCatching {
        val o = JSONObject(raw ?: "{}")
        buildMap { o.keys().forEach { k -> put(k, o.optInt(k, 0).coerceAtLeast(0)) } }
    }.getOrDefault(emptyMap())

    private fun parseLongMap(raw: String?): Map<String, Long> = runCatching {
        val o = JSONObject(raw ?: "{}")
        buildMap { o.keys().forEach { k -> put(k, o.optLong(k, 0L).coerceAtLeast(0L)) } }
    }.getOrDefault(emptyMap())

    private fun parseStringList(raw: String?): List<String> = runCatching {
        val arr = JSONArray(raw ?: "[]")
        List(arr.length()) { arr.optString(it) }.filter { it.isNotBlank() }
    }.getOrDefault(emptyList())
}
