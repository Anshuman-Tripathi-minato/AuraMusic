package com.auramusic.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.ScreenLockPortrait
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Timelapse
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auramusic.app.BuildConfig
import com.auramusic.app.data.AuraPrefs
import com.auramusic.app.data.DownloadEngineMode
import com.auramusic.app.data.LibraryRepository
import com.auramusic.app.data.ThemeMode
import com.auramusic.app.download.DownloadCenter
import com.auramusic.app.download.ServerEngine
import com.auramusic.app.download.YtDlpEngine
import com.auramusic.app.player.PlayerManager
import com.auramusic.app.ui.LocalAuraController
import com.auramusic.app.ui.Route
import com.auramusic.app.ui.UiEvents
import com.auramusic.app.ui.components.AuraTopBar
import com.auramusic.app.ui.components.GlassCard
import com.auramusic.app.ui.components.NeonSwitch
import com.auramusic.app.ui.components.SettingRow
import com.auramusic.app.ui.theme.Aura
import com.auramusic.app.ui.theme.AuraCyan
import com.auramusic.app.ui.theme.AuraPurple
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

@Composable
fun SettingsScreen(contentPadding: PaddingValues) {
    val controller = LocalAuraController.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val c = Aura.colors
    val settings by AuraPrefs.settings.collectAsState()
    val songs by LibraryRepository.songs.collectAsState()
    val engineBusy by DownloadCenter.engineBusy.collectAsState()
    val engineStatus by YtDlpEngine.status.collectAsState()
    val engineVersion by produceState<String?>(null, engineBusy, engineStatus) {
        value = withContext(Dispatchers.IO) { if (engineStatus == YtDlpEngine.Status.READY) YtDlpEngine.version(context) else null }
    }

    var dialog by remember { mutableStateOf<String?>(null) }
    var serverStatus by remember { mutableStateOf<String?>(null) }

    val backup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(AuraPrefs.exportJson().toString(2).toByteArray()) } ?: error("no stream")
                }.isSuccess
            }
            UiEvents.show(if (ok) "Backup saved" else "Couldn't write the backup file")
        }
    }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: error("Empty file")
                    val json = JSONObject(text)
                    require(json.has("favorites") || json.has("playlists") || json.has("settings")) { "Not an Aura Music backup" }
                    json
                }
            }
            result.onSuccess {
                AuraPrefs.importJson(it)
                UiEvents.show("Backup restored • ${AuraPrefs.playlists.value.size} playlists, ${AuraPrefs.favorites.value.size} liked songs")
            }.onFailure { UiEvents.show("Restore failed: ${it.message ?: "invalid file"}") }
        }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
        item { AuraTopBar("Settings", onBack = { controller.back() }) }
        item {
            GlassCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), onClick = { dialog = "name" }) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(54.dp).clip(CircleShape).background(Brush.linearGradient(listOf(AuraCyan, AuraPurple))), contentAlignment = Alignment.Center) {
                        Text(settings.userName.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                    }
                    Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                        Text(settings.userName, color = c.text, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Text("Local user • ${songs.size} songs", color = c.muted, fontSize = 13.sp)
                    }
                    Icon(Icons.Default.Edit, "Edit name", tint = c.muted)
                }
            }
        }

        item { Section("Audio") }
        item { SettingRow(Icons.Default.Equalizer, "Equalizer", "Presets, bass boost, virtualizer, loudness", onClick = { controller.navigate(Route.Equalizer) }) }
        item {
            SettingRow(Icons.Default.Speed, "Playback speed", "${settings.playbackSpeed}x", onClick = { dialog = "speed" })
        }
        item {
            SettingRow(Icons.Default.GraphicEq, "Skip silence", "Skip quiet parts automatically",
                trailing = { NeonSwitch(settings.skipSilence, { PlayerManager.setSkipSilence(it) }) })
        }
        item { SettingRow(Icons.Default.Timer, "Sleep timer", "Stop playback after a while", onClick = { controller.navigate(Route.Sleep) }) }

        item { Section("Appearance") }
        item {
            SettingRow(Icons.Default.DarkMode, "Dark mode", when (settings.themeMode) {
                ThemeMode.DARK -> "On"; ThemeMode.LIGHT -> "Off"; ThemeMode.SYSTEM -> "Follow system"
            }, onClick = { dialog = "theme" }, trailing = {
                NeonSwitch(settings.themeMode != ThemeMode.LIGHT, { on -> AuraPrefs.updateSettings { it.copy(themeMode = if (on) ThemeMode.DARK else ThemeMode.LIGHT) } })
            })
        }

        item { Section("Storage & Download") }
        item {
            SettingRow(Icons.Default.CloudDownload, "Download engine",
                if (settings.engineMode == DownloadEngineMode.ON_DEVICE) "On this phone (yt-dlp + FFmpeg)" else "Companion server",
                onClick = { dialog = "engine" })
        }
        if (settings.engineMode == DownloadEngineMode.ON_DEVICE) {
            item {
                SettingRow(Icons.Default.SystemUpdate, "Update download engine",
                    engineBusy ?: when (engineStatus) {
                        YtDlpEngine.Status.READY -> "yt-dlp ${engineVersion ?: ""} • update if downloads start failing"
                        YtDlpEngine.Status.PREPARING -> "Preparing…"
                        YtDlpEngine.Status.FAILED -> "Failed to start: ${YtDlpEngine.initError}"
                        YtDlpEngine.Status.NOT_STARTED -> "Not started yet"
                    },
                    onClick = { DownloadCenter.updateEngine(context) })
            }
        } else {
            item { SettingRow(Icons.Default.Dns, "Server URL", settings.serverUrl, onClick = { dialog = "server" }) }
            item {
                SettingRow(Icons.Default.Refresh, "Test server connection", serverStatus ?: "Checks FFmpeg + yt-dlp on the server", onClick = {
                    serverStatus = "Checking…"
                    scope.launch {
                        serverStatus = withContext(Dispatchers.IO) {
                            runCatching { ServerEngine(settings.serverUrl).health() }.getOrElse { "Offline • ${it.message ?: "connection failed"}" }
                        }
                    }
                })
            }
        }
        item { SettingRow(Icons.Default.Folder, "Download location", "Music/${settings.downloadFolder}", onClick = { dialog = "folder" }) }
        item {
            SettingRow(Icons.Default.HighQuality, "Default quality", "${settings.downloadFormat.uppercase()} • ${settings.downloadQuality} kbps",
                onClick = { controller.switchTab(Route.Downloads) })
        }
        item {
            SettingRow(Icons.Default.Image, "Embed cover art", "Add the video thumbnail as album art",
                trailing = { NeonSwitch(settings.embedArtwork, { v -> AuraPrefs.updateSettings { it.copy(embedArtwork = v) } }) })
        }

        item { Section("Library") }
        item { SettingRow(Icons.Default.Refresh, "Rescan library", "${songs.size} songs found", onClick = { LibraryRepository.refresh(); UiEvents.show("Scanning…") }) }
        item {
            SettingRow(Icons.Default.Timelapse, "Hide short clips",
                if (settings.minDurationSec == 0) "Show everything" else "Hide audio shorter than ${settings.minDurationSec}s",
                onClick = { dialog = "min" })
        }
        item { SettingRow(Icons.Default.History, "Clear listening history", "Resets Recently / Most played", onClick = { dialog = "clear" }) }

        item { Section("Backup & Restore") }
        item { SettingRow(Icons.Default.Backup, "Back up", "Playlists, likes, history, EQ and settings → JSON file", onClick = { backup.launch("aura-music-backup.json") }) }
        item { SettingRow(Icons.Default.Restore, "Restore", "Load a previous Aura Music backup", onClick = { restore.launch(arrayOf("application/json", "text/plain", "*/*")) }) }

        item { Section("More") }
        item { SettingRow(Icons.Default.DirectionsCar, "Car mode", "Big buttons for driving • Android Auto supported", onClick = { controller.navigate(Route.Car) }) }
        item {
            SettingRow(Icons.Default.ScreenLockPortrait, "Keep screen on in car mode", null,
                trailing = { NeonSwitch(settings.keepScreenOnInCar, { v -> AuraPrefs.updateSettings { it.copy(keepScreenOnInCar = v) } }) })
        }
        item {
            SettingRow(Icons.Default.Security, "App permissions", "Open system settings for Aura Music", onClick = {
                runCatching {
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
                }
            })
        }
        item { SettingRow(Icons.Default.Info, "About", "Aura Music ${BuildConfig.VERSION_NAME}", onClick = { dialog = "about" }) }
        item { Spacer(Modifier.height(24.dp)) }
    }

    when (dialog) {
        "name" -> TextInputDialog("Your name", settings.userName, "Save", onDismiss = { dialog = null }) { v ->
            AuraPrefs.updateSettings { it.copy(userName = v) }; dialog = null
        }
        "server" -> TextInputDialog("Server URL", settings.serverUrl, "Save", "http://192.168.1.10:8000", onDismiss = { dialog = null }) { v ->
            val url = if (v.startsWith("http://") || v.startsWith("https://")) v else "http://$v"
            AuraPrefs.updateSettings { it.copy(serverUrl = url) }; serverStatus = null; dialog = null
        }
        "folder" -> TextInputDialog("Save folder (inside Music/)", settings.downloadFolder, "Save", "AuraMusic", onDismiss = { dialog = null }) { v ->
            AuraPrefs.updateSettings { it.copy(downloadFolder = v) }; dialog = null
        }
        "theme" -> ChoiceDialog("Theme", listOf(ThemeMode.DARK to "Dark (neon)", ThemeMode.LIGHT to "Light", ThemeMode.SYSTEM to "Follow system"), settings.themeMode,
            onDismiss = { dialog = null }) { v -> AuraPrefs.updateSettings { it.copy(themeMode = v) }; dialog = null }
        "engine" -> ChoiceDialog(
            "Download engine",
            listOf(DownloadEngineMode.ON_DEVICE to "On this phone — works anywhere, no PC needed", DownloadEngineMode.SERVER to "Companion server — run /backend on your PC"),
            settings.engineMode, onDismiss = { dialog = null }
        ) { v -> AuraPrefs.updateSettings { it.copy(engineMode = v) }; DownloadCenter.clearPreview(); dialog = null }
        "speed" -> ChoiceDialog("Playback speed", listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f).map { it to "${it}x" }, settings.playbackSpeed,
            onDismiss = { dialog = null }) { v -> PlayerManager.setPlaybackSpeed(v); dialog = null }
        "min" -> ChoiceDialog("Hide short clips", listOf(0 to "Show everything", 15 to "Shorter than 15 seconds", 30 to "Shorter than 30 seconds", 60 to "Shorter than 1 minute"),
            settings.minDurationSec, onDismiss = { dialog = null }) { v -> AuraPrefs.updateSettings { it.copy(minDurationSec = v) }; dialog = null }
        "clear" -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Clear listening history?") },
            text = { Text("Recently played and most played will be reset. Playlists and likes are kept.") },
            confirmButton = { TextButton(onClick = { AuraPrefs.clearHistory(); dialog = null; UiEvents.show("History cleared") }) { Text("Clear") } },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancel") } }
        )
        "about" -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Aura Music ${BuildConfig.VERSION_NAME}") },
            text = {
                Text(
                    "Your Music. Your World.\n\nOffline-first music player with playlists, equalizer, synced lyrics, sleep timer, " +
                        "backup & restore, car mode and Android Auto.\n\nDownloads use yt-dlp and FFmpeg. Only download content you own or are allowed to download."
                )
            },
            confirmButton = { TextButton(onClick = { dialog = null }) { Text("OK") } }
        )
    }
}

@Composable
private fun Section(title: String) {
    Column {
        Spacer(Modifier.height(10.dp))
        HorizontalDivider(color = Aura.colors.stroke, modifier = Modifier.padding(horizontal = 16.dp))
        Text(title.uppercase(), color = AuraCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
            modifier = Modifier.padding(start = 20.dp, top = 14.dp, bottom = 2.dp))
    }
}

@Composable
fun <T> ChoiceDialog(title: String, options: List<Pair<T, String>>, selected: T, onDismiss: () -> Unit, onPick: (T) -> Unit) {
    val c = Aura.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                options.forEach { (value, label) ->
                    Row(Modifier.fillMaxWidth().clip(CircleShape), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(value == selected, { onPick(value) }, colors = RadioButtonDefaults.colors(selectedColor = AuraCyan, unselectedColor = c.muted))
                        Spacer(Modifier.width(4.dp))
                        Text(label, color = c.text, modifier = Modifier.padding(vertical = 8.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}
