package com.auramusic.app.ui.screens

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil3.compose.AsyncImage
import com.auramusic.app.data.AuraPrefs
import com.auramusic.app.data.DownloadEngineMode
import com.auramusic.app.data.LibraryRepository
import com.auramusic.app.data.formatDuration
import com.auramusic.app.download.DownloadCenter
import com.auramusic.app.download.DownloadStage
import com.auramusic.app.download.YtDlpEngine
import com.auramusic.app.player.PlayerManager
import com.auramusic.app.ui.LocalAuraController
import com.auramusic.app.ui.UiEvents
import com.auramusic.app.ui.components.AuraTopBar
import com.auramusic.app.ui.components.EmptyState
import com.auramusic.app.ui.components.GlassCard
import com.auramusic.app.ui.components.GradientButton
import com.auramusic.app.ui.theme.Aura
import com.auramusic.app.ui.theme.AuraCyan
import com.auramusic.app.ui.theme.AuraGreen
import com.auramusic.app.ui.theme.AuraPink
import com.auramusic.app.ui.theme.AuraPurple
import java.text.DateFormat
import java.util.Date

private val qualityOptions = listOf("320" to "Best Available (320 kbps)", "256" to "High (256 kbps)", "192" to "Standard (192 kbps)", "128" to "Data saver (128 kbps)")
private val formatOptions = listOf("mp3" to "MP3 (Audio)", "m4a" to "M4A (AAC Audio)")

@Composable
fun DownloadScreen(contentPadding: PaddingValues) {
    val controller = LocalAuraController.current
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    val c = Aura.colors
    val settings by AuraPrefs.settings.collectAsState()
    val preview by DownloadCenter.preview.collectAsState()
    val tasks by DownloadCenter.tasks.collectAsState()
    val history by AuraPrefs.downloads.collectAsState()
    val engineStatus by YtDlpEngine.status.collectAsState()
    val engineBusy by DownloadCenter.engineBusy.collectAsState()

    var tab by rememberSaveable { mutableStateOf(0) } // 0 YouTube, 1 URL, 2 History
    var input by rememberSaveable { mutableStateOf("") }
    var folderDialog by remember { mutableStateOf(false) }

    // Link shared from the YouTube app (or another app) → prefill and fetch automatically.
    LaunchedEffect(controller.downloadPrefill) {
        val shared = controller.downloadPrefill ?: return@LaunchedEffect
        controller.downloadPrefill = null
        val isYoutube = DownloadCenter.normalizeUrl(shared, youtubeOnly = true) is DownloadCenter.UrlResult.Ok
        tab = if (isYoutube) 0 else 1
        input = shared
        DownloadCenter.fetchInfo(context, shared, youtubeOnly = isYoutube)
    }

    val legacyWrite = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) preview.info?.let { DownloadCenter.enqueue(context, it, preview.url) }
        else UiEvents.show("Storage permission is needed to save music on this Android version")
    }

    fun startDownload() {
        val info = preview.info ?: return
        if (Build.VERSION.SDK_INT < 29 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) {
            legacyWrite.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            return
        }
        DownloadCenter.enqueue(context, info, preview.url)
        input = ""
    }

    fun fetch() {
        focus.clearFocus()
        DownloadCenter.fetchInfo(context, input, youtubeOnly = tab == 0)
    }

    Column(Modifier.fillMaxSize()) {
        AuraTopBar("Download Music") {
            IconButton(onClick = { tab = 2 }) { Icon(Icons.Default.History, "History", tint = c.text) }
        }
        // Tabs
        Row(
            Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.card2).padding(4.dp)
        ) {
            listOf("YouTube", "URL", "History").forEachIndexed { i, label ->
                Box(
                    Modifier.weight(1f).height(38.dp).clip(RoundedCornerShape(11.dp))
                        .background(if (tab == i) Color(0xFF3D5BFF) else Color.Transparent)
                        .clickable { if (tab != i) { tab = i; if (i < 2) DownloadCenter.clearPreview() } },
                    contentAlignment = Alignment.Center
                ) { Text(label, color = if (tab == i) Color.White else c.muted, fontWeight = FontWeight.SemiBold, fontSize = 14.sp) }
            }
        }
        Spacer(Modifier.height(12.dp))

        if (tab == 2) {
            HistoryList(history, contentPadding)
            return@Column
        }

        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = contentPadding.calculateBottomPadding() + 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it; if (preview.error != null || preview.info != null) DownloadCenter.clearPreview() },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(if (tab == 0) "https://youtu.be/… or youtube.com/watch?v=…" else "Paste any audio/video link") },
                    leadingIcon = { Icon(Icons.Default.Link, null) },
                    trailingIcon = {
                        if (input.isNotEmpty()) IconButton(onClick = { input = ""; DownloadCenter.clearPreview() }) { Icon(Icons.Default.Close, "Clear") }
                        else IconButton(onClick = {
                            val clip = (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip
                            val text = clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                            if (text.isBlank()) UiEvents.show("Clipboard is empty") else { input = text.trim(); DownloadCenter.fetchInfo(context, input, tab == 0) }
                        }) { Icon(Icons.Default.ContentPaste, "Paste") }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { fetch() }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AuraCyan, unfocusedBorderColor = c.stroke,
                        focusedContainerColor = c.card, unfocusedContainerColor = c.card
                    )
                )
            }

            if (preview.info == null) {
                item {
                    GradientButton(
                        if (preview.loading) "Reading link…" else "Get audio",
                        { fetch() }, Modifier.fillMaxWidth(), icon = Icons.Default.Search,
                        enabled = input.isNotBlank(), loading = preview.loading
                    )
                }
            }

            if (settings.engineMode == DownloadEngineMode.ON_DEVICE && engineStatus == YtDlpEngine.Status.PREPARING) {
                item { InfoCard(Icons.Default.Info, "Preparing the download engine (first launch only)…", AuraCyan, loading = true) }
            }
            if (settings.engineMode == DownloadEngineMode.ON_DEVICE && engineStatus == YtDlpEngine.Status.FAILED) {
                item { InfoCard(Icons.Default.ErrorOutline, "Download engine failed to start: ${YtDlpEngine.initError ?: "unknown error"}", AuraPink) }
            }
            preview.error?.let { err ->
                item {
                    GlassCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.ErrorOutline, null, tint = AuraPink)
                                Spacer(Modifier.width(10.dp))
                                Text(err, color = c.text, fontSize = 14.sp)
                            }
                            if (YtDlpEngine.shouldSuggestUpdate(err) && settings.engineMode == DownloadEngineMode.ON_DEVICE) {
                                TextButton(onClick = { DownloadCenter.updateEngine(context) { fetch() } }, enabled = engineBusy == null) {
                                    Icon(Icons.Default.SystemUpdate, null); Spacer(Modifier.width(6.dp))
                                    Text(engineBusy ?: "Update engine & retry")
                                }
                            }
                        }
                    }
                }
            }

            preview.info?.let { info ->
                item {
                    GlassCard(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(width = 112.dp, height = 64.dp).clip(RoundedCornerShape(10.dp)).background(c.card2), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.MusicNote, null, tint = AuraPurple)
                                if (info.thumbnail != null) AsyncImage(info.thumbnail, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            }
                            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                Text(info.title, color = c.text, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
                                Text(info.artist, color = c.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (info.durationSec > 0) Text(formatDuration(info.durationSec * 1000L), color = c.muted, fontSize = 12.sp)
                            }
                        }
                    }
                }
                item {
                    GlassCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(vertical = 4.dp)) {
                            OptionRow("Format", formatOptions.firstOrNull { it.first == settings.downloadFormat }?.second ?: "MP3 (Audio)", formatOptions) { v ->
                                AuraPrefs.updateSettings { it.copy(downloadFormat = v) }
                            }
                            OptionRow("Quality", qualityOptions.firstOrNull { it.first == settings.downloadQuality }?.second ?: qualityOptions[0].second, qualityOptions) { v ->
                                AuraPrefs.updateSettings { it.copy(downloadQuality = v) }
                            }
                            Row(Modifier.fillMaxWidth().clickable { folderDialog = true }.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("Save to", color = c.muted, modifier = Modifier.width(76.dp), fontSize = 14.sp)
                                Text("Music/${settings.downloadFolder}", color = c.text, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Icon(Icons.Default.Folder, "Change folder", tint = AuraCyan)
                            }
                        }
                    }
                }
                item {
                    GradientButton("Download", { startDownload() }, Modifier.fillMaxWidth(), icon = Icons.Default.Download)
                }
            }

            item {
                Text(
                    "Download only content you own or have rights to. " +
                        if (settings.engineMode == DownloadEngineMode.ON_DEVICE) "Converted on your phone with yt-dlp + FFmpeg." else "Converted by your server at ${settings.serverUrl}.",
                    color = c.muted, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                )
            }

            if (tasks.isNotEmpty()) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                        Text("Downloads", color = c.text, fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.weight(1f))
                        if (tasks.any { !it.active }) TextButton(onClick = { DownloadCenter.clearFinished() }) { Text("Clear finished") }
                    }
                }
                items(tasks.reversed(), key = { it.id }) { task -> TaskCard(task) }
            }
        }
    }

    if (folderDialog) {
        TextInputDialog("Save folder (inside Music/)", settings.downloadFolder, "Save", "AuraMusic", onDismiss = { folderDialog = false }) { v ->
            AuraPrefs.updateSettings { it.copy(downloadFolder = v) }
            folderDialog = false
        }
    }
}

@Composable
private fun OptionRow(label: String, value: String, options: List<Pair<String, String>>, onPick: (String) -> Unit) {
    val c = Aura.colors
    var open by remember { mutableStateOf(false) }
    Box {
        Row(Modifier.fillMaxWidth().clickable { open = true }.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = c.muted, modifier = Modifier.width(76.dp), fontSize = 14.sp)
            Text(value, color = c.text, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Icon(Icons.Default.KeyboardArrowDown, null, tint = c.muted)
        }
        DropdownMenu(open, onDismissRequest = { open = false }) {
            options.forEach { (k, v) -> DropdownMenuItem(text = { Text(v) }, onClick = { open = false; onPick(k) }) }
        }
    }
}

@Composable
private fun InfoCard(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, tint: Color, loading: Boolean = false) {
    val c = Aura.colors
    GlassCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            if (loading) CircularProgressIndicator(Modifier.size(20.dp), color = tint, strokeWidth = 2.dp) else Icon(icon, null, tint = tint)
            Spacer(Modifier.width(12.dp))
            Text(text, color = c.text, fontSize = 14.sp)
        }
    }
}

@Composable
private fun TaskCard(task: DownloadCenter.Task) {
    val context = LocalContext.current
    val c = Aura.colors
    val engineBusy by DownloadCenter.engineBusy.collectAsState()
    GlassCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(52.dp).clip(RoundedCornerShape(10.dp)).background(c.card2), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.MusicNote, null, tint = AuraPurple)
                    if (task.thumbnail != null) AsyncImage(task.thumbnail, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(task.title, color = c.text, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
                    val status = when (task.status) {
                        DownloadCenter.Status.QUEUED -> "Waiting in queue"
                        DownloadCenter.Status.RUNNING -> when (task.stage) {
                            DownloadStage.FETCHING -> "Connecting…"
                            DownloadStage.DOWNLOADING -> if (task.percent >= 0) "Downloading ${task.percent.toInt()}%" + (if (task.etaSec > 0) " • ${task.etaSec}s left" else "") else "Downloading…"
                            DownloadStage.CONVERTING -> "Converting to ${task.format.uppercase()} with FFmpeg…"
                            DownloadStage.EMBEDDING -> "Adding title & cover art…"
                            DownloadStage.SAVING -> "Saving to your library…"
                        }
                        DownloadCenter.Status.DONE -> "Saved to ${task.location ?: "Music"}"
                        DownloadCenter.Status.FAILED -> task.error ?: "Failed"
                        DownloadCenter.Status.CANCELED -> "Canceled"
                    }
                    Text(
                        status, fontSize = 12.sp, maxLines = 3, overflow = TextOverflow.Ellipsis,
                        color = when (task.status) {
                            DownloadCenter.Status.DONE -> AuraGreen
                            DownloadCenter.Status.FAILED -> AuraPink
                            else -> c.muted
                        }
                    )
                }
                when (task.status) {
                    DownloadCenter.Status.QUEUED, DownloadCenter.Status.RUNNING ->
                        IconButton(onClick = { DownloadCenter.cancel(task.id) }) { Icon(Icons.Default.Close, "Cancel", tint = c.muted) }
                    DownloadCenter.Status.DONE -> {
                        IconButton(onClick = {
                            val uri = task.resultUri ?: return@IconButton
                            val song = LibraryRepository.songByKey(uri)
                            if (song != null) PlayerManager.playQueue(listOf(song), 0, "Downloads")
                            else PlayerManager.playExternal(Uri.parse(uri), task.title)
                        }) { Icon(Icons.Default.PlayArrow, "Play", tint = AuraCyan) }
                    }
                    DownloadCenter.Status.FAILED, DownloadCenter.Status.CANCELED -> {
                        IconButton(onClick = { DownloadCenter.retry(context, task.id) }) { Icon(Icons.Default.Refresh, "Retry", tint = AuraCyan) }
                        IconButton(onClick = { DownloadCenter.dismiss(task.id) }) { Icon(Icons.Default.Close, "Dismiss", tint = c.muted) }
                    }
                }
            }
            if (task.status == DownloadCenter.Status.RUNNING) {
                Spacer(Modifier.height(10.dp))
                val determinate = task.stage == DownloadStage.DOWNLOADING && task.percent in 0f..100f
                if (determinate) LinearProgressIndicator(progress = { task.percent / 100f }, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)), color = AuraCyan, trackColor = c.card2, drawStopIndicator = {})
                else LinearProgressIndicator(Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)), color = AuraPurple, trackColor = c.card2)
            }
            if (task.status == DownloadCenter.Status.FAILED && YtDlpEngine.shouldSuggestUpdate(task.error)) {
                TextButton(onClick = { DownloadCenter.updateEngine(context) { DownloadCenter.retry(context, task.id) } }, enabled = engineBusy == null) {
                    Icon(Icons.Default.SystemUpdate, null); Spacer(Modifier.width(6.dp)); Text(engineBusy ?: "Update engine & retry")
                }
            }
        }
    }
}

@Composable
private fun HistoryList(history: List<com.auramusic.app.data.DownloadRecord>, contentPadding: PaddingValues) {
    val c = Aura.colors
    if (history.isEmpty()) {
        EmptyState(Icons.Default.History, "No downloads yet", "Songs you download appear here and in your library.")
        return
    }
    LazyColumn(contentPadding = contentPadding) {
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${history.size} downloads", color = c.muted, modifier = Modifier.weight(1f))
                TextButton(onClick = { AuraPrefs.clearDownloads() }) { Text("Clear history") }
            }
        }
        items(history, key = { it.uri }) { record ->
            val song = LibraryRepository.songByKey(record.uri)
            Row(
                Modifier.fillMaxWidth().clickable {
                    if (song != null) PlayerManager.playQueue(listOf(song), 0, "Downloads")
                    else PlayerManager.playExternal(Uri.parse(record.uri), record.title)
                }.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(46.dp).clip(RoundedCornerShape(12.dp)).background(c.card2).border(1.dp, c.stroke, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) { Icon(if (song != null) Icons.Default.CheckCircle else Icons.Default.Download, null, tint = if (song != null) AuraGreen else AuraCyan) }
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(record.title, color = c.text, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOf(record.artist, record.format.uppercase(), DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(record.timestamp)))
                            .filter { it.isNotBlank() }.joinToString(" • "),
                        color = c.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = { AuraPrefs.removeDownload(record.uri) }) { Icon(Icons.Default.Delete, "Remove from history", tint = c.muted) }
            }
        }
    }
}
