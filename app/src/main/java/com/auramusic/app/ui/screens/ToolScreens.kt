package com.auramusic.app.ui.screens

import android.os.SystemClock
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TimerOff
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auramusic.app.data.AuraPrefs
import com.auramusic.app.data.LyricsManager
import com.auramusic.app.data.MusicItem
import com.auramusic.app.data.formatDuration
import com.auramusic.app.player.AudioEffects
import com.auramusic.app.player.PlayerManager
import com.auramusic.app.player.SleepTimer
import com.auramusic.app.ui.LocalAuraController
import com.auramusic.app.ui.UiEvents
import com.auramusic.app.ui.components.ArtworkImage
import com.auramusic.app.ui.components.AuraChip
import com.auramusic.app.ui.components.AuraTopBar
import com.auramusic.app.ui.components.EmptyState
import com.auramusic.app.ui.components.GlassCard
import com.auramusic.app.ui.components.GradientButton
import com.auramusic.app.ui.components.NeonSlider
import com.auramusic.app.ui.components.NeonSwitch
import com.auramusic.app.ui.components.OutlineButton
import com.auramusic.app.ui.components.VerticalFader
import com.auramusic.app.ui.theme.Aura
import com.auramusic.app.ui.theme.AuraCyan
import com.auramusic.app.ui.theme.AuraPink
import com.auramusic.app.ui.theme.AuraPurple
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ================================================================== Equalizer

@Composable
fun EqualizerScreen(contentPadding: PaddingValues) {
    val controller = LocalAuraController.current
    val c = Aura.colors
    val state by AudioEffects.state.collectAsState()
    var savePreset by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        AuraTopBar("Equalizer", onBack = { controller.back() }) {
            NeonSwitch(state.enabled, { AudioEffects.setEnabled(it) }, enabled = state.available)
            Spacer(Modifier.width(8.dp))
        }
        if (!state.available) {
            EmptyState(
                Icons.Default.Tune, "Equalizer starting…",
                "Play any song once to connect the audio engine. Some devices or emulators don't provide Android audio effects.",
                actionLabel = if (PlayerManager.currentSong != null) "Play" else null,
                onAction = { PlayerManager.toggle() }
            )
            return
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState())
                .padding(bottom = contentPadding.calculateBottomPadding())
        ) {
            val presetNames = listOf("Flat") + state.devicePresets.filterNot { it.equals("Flat", true) } + state.userPresets.map { it.name }
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(presetNames) { name ->
                    AuraChip(name, state.preset == name, onClick = {
                        when {
                            name == "Flat" -> AudioEffects.reset()
                            state.userPresets.any { it.name == name } -> AudioEffects.useUserPreset(name)
                            else -> AudioEffects.useDevicePreset(name)
                        }
                    })
                }
                if (state.preset == "Custom") item { AuraChip("Custom", true, {}) }
            }

            GlassCard(Modifier.fillMaxWidth().padding(16.dp)) {
                Row(Modifier.fillMaxWidth().height(250.dp).padding(horizontal = 8.dp, vertical = 12.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    val range = (state.maxLevel - state.minLevel).coerceAtLeast(1)
                    state.bandLevels.forEachIndexed { i, level ->
                        Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("%+d".format(level / 100), color = c.muted, fontSize = 11.sp)
                            VerticalFader(
                                value = (level - state.minLevel).toFloat() / range,
                                onValueChange = { v -> AudioEffects.setBand(i, (state.minLevel + v * range).toInt()) },
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                enabled = state.enabled
                            )
                            Text(freqLabel(state.bandFrequenciesHz.getOrElse(i) { 0 }), color = c.muted, fontSize = 11.sp, maxLines = 1)
                        }
                    }
                }
            }

            GlassCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Column(Modifier.padding(16.dp)) {
                    EffectSlider("Bass Boost", state.bass, state.bassSupported && state.enabled) { AudioEffects.setBass(it) }
                    EffectSlider("Virtualizer", state.virtualizer, state.virtualizerSupported && state.enabled) { AudioEffects.setVirtualizer(it) }
                    EffectSlider("Loudness", state.loudness, state.loudnessSupported && state.enabled) { AudioEffects.setLoudness(it) }
                }
            }

            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlineButton("Reset", { AudioEffects.reset() }, Modifier.weight(1f).height(52.dp))
                GradientButton("Save Preset", { savePreset = true }, Modifier.weight(1.4f))
            }
            if (state.userPresets.any { it.name == state.preset }) {
                TextButton(onClick = { AudioEffects.deleteUserPreset(state.preset) }, modifier = Modifier.padding(horizontal = 8.dp)) {
                    Icon(Icons.Default.Delete, null, tint = AuraPink)
                    Spacer(Modifier.width(6.dp))
                    Text("Delete preset “${state.preset}”", color = AuraPink)
                }
            }
            Text(
                "Effects are applied by your device's audio engine and are remembered between sessions.",
                color = c.muted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )
        }
    }
    if (savePreset) {
        TextInputDialog("Save preset", "", "Save", "e.g. Night drive", onDismiss = { savePreset = false }) { name ->
            if (AudioEffects.saveUserPreset(name)) { savePreset = false; UiEvents.show("Preset “$name” saved") }
            else UiEvents.show("Choose a different name")
        }
    }
}

@Composable
private fun EffectSlider(label: String, value: Int, enabled: Boolean, onChange: (Int) -> Unit) {
    val c = Aura.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = if (enabled) c.text else c.muted, modifier = Modifier.width(96.dp), fontSize = 14.sp)
        NeonSlider(value / 1000f, { onChange((it * 1000).toInt()) }, Modifier.weight(1f), enabled = enabled)
        Text("${value / 10}%", color = c.muted, fontSize = 12.sp, modifier = Modifier.width(40.dp), textAlign = TextAlign.End)
    }
}

private fun freqLabel(hz: Int) = if (hz >= 1000) "%.1fK".format(hz / 1000f).replace(".0K", "K") else "$hz"

// ================================================================== Lyrics

@Composable
fun LyricsScreen(lyricsManager: LyricsManager) {
    val controller = LocalAuraController.current
    val context = LocalContext.current
    val c = Aura.colors
    val scope = rememberCoroutineScope()
    val song = PlayerManager.currentSong
    var version by remember { mutableIntStateOf(0) }
    var raw by remember { mutableStateOf<String?>(null) }
    var searching by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }

    LaunchedEffect(song?.key, version) {
        raw = song?.let { withContext(Dispatchers.IO) { lyricsManager.load(it.key) } }
    }
    val parsed = remember(raw) { raw?.let { lyricsManager.parse(it) } }

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val s = song ?: return@rememberLauncherForActivityResult
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: error("empty")
                    require(text.isNotBlank() && text.length < 500_000)
                    lyricsManager.save(s.key, text)
                }.isSuccess
            }
            UiEvents.show(if (ok) "Lyrics imported" else "That file couldn't be read as lyrics")
            version++
        }
    }

    fun findOnline() {
        val s = song ?: return
        searching = true
        scope.launch {
            val text = withContext(Dispatchers.IO) { runCatching { lyricsManager.fetchOnline(s) }.getOrNull() }
            searching = false
            if (text != null) {
                withContext(Dispatchers.IO) { lyricsManager.save(s.key, text) }
                UiEvents.show("Lyrics found")
                version++
            } else UiEvents.show("No lyrics found online for this song")
        }
    }

    Column(Modifier.fillMaxSize()) {
        AuraTopBar("Lyrics", onBack = { controller.back() }, subtitle = song?.let { "${it.title} • ${it.artist}" }) {
            if (song != null) {
                IconButton(onClick = { editing = true }) { Icon(Icons.Default.Edit, "Edit lyrics", tint = c.text) }
                if (raw != null) IconButton(onClick = {
                    lyricsManager.delete(song.key); version++; UiEvents.show("Lyrics removed")
                }) { Icon(Icons.Default.Delete, "Delete lyrics", tint = c.text) }
            }
        }
        when {
            song == null -> EmptyState(Icons.Default.Lyrics, "Nothing playing", "Start a song to see its lyrics.")
            parsed == null || parsed.lines.isEmpty() -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                EmptyState(Icons.Default.Lyrics, "No lyrics yet", "Find synced lyrics online, import an .lrc / .txt file, or paste them yourself.")
                Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    GradientButton("Find lyrics online", { findOnline() }, Modifier.fillMaxWidth(), icon = Icons.Default.CloudDownload, loading = searching)
                    OutlineButton("Import .lrc file", { importer.launch(arrayOf("*/*")) }, Modifier.fillMaxWidth(), icon = Icons.Default.FileOpen)
                    OutlineButton("Paste / type lyrics", { editing = true }, Modifier.fillMaxWidth(), icon = Icons.Default.Edit)
                }
            }
            else -> {
                val lines = parsed.lines
                val position = PlayerManager.positionMs
                val active = if (parsed.synced) lines.indexOfLast { it.timestampMs <= position + 250 } else -1
                val listState = rememberLazyListState()
                LaunchedEffect(active) {
                    if (active >= 0) listState.animateScrollToItem((active - 3).coerceAtLeast(0))
                }
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 24.dp, bottom = 220.dp)) {
                    itemsIndexed(lines) { index, line ->
                        val isActive = index == active
                        Text(
                            line.text.ifBlank { "♪" },
                            textAlign = TextAlign.Center,
                            fontSize = if (isActive) 24.sp else 20.sp,
                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                            style = if (isActive) androidx.compose.ui.text.TextStyle(brush = Brush.horizontalGradient(listOf(AuraCyan, AuraPurple))) else androidx.compose.ui.text.TextStyle.Default,
                            color = if (isActive) Color.Unspecified else if (parsed.synced && index < active) c.muted.copy(alpha = 0.6f) else c.muted,
                            modifier = Modifier.fillMaxWidth()
                                .clickable(enabled = parsed.synced) { PlayerManager.seekTo(line.timestampMs) }
                                .padding(horizontal = 28.dp, vertical = 10.dp)
                        )
                    }
                    item {
                        Row(Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.Center) {
                            TextButton(onClick = { findOnline() }, enabled = !searching) { Text(if (searching) "Searching…" else "Search online again") }
                        }
                    }
                }
            }
        }
    }

    if (editing && song != null) {
        var text by remember { mutableStateOf(raw.orEmpty()) }
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text("Edit lyrics") },
            text = {
                Column {
                    Text("Plain text or LRC, e.g. [00:12.50] First line", color = c.muted, fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(text, { text = it }, modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp, max = 360.dp))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (text.isBlank()) lyricsManager.delete(song.key) else lyricsManager.save(song.key, text)
                    editing = false; version++
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editing = false }) { Text("Cancel") } }
        )
    }
}

// ================================================================== Sleep timer

@Composable
fun SleepTimerScreen(contentPadding: PaddingValues) {
    val controller = LocalAuraController.current
    val c = Aura.colors
    val state by SleepTimer.state.collectAsState()
    var choice by remember { mutableIntStateOf(30) } // minutes; -1 = end of song; -2 = custom
    var customMinutes by remember { mutableStateOf("20") }
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(state.active) {
        while (state.active) { now = SystemClock.elapsedRealtime(); delay(1000) }
    }

    Column(Modifier.fillMaxSize()) {
        AuraTopBar("Sleep Timer", onBack = { controller.back() })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = contentPadding.calculateBottomPadding())) {
            if (state.active) {
                GlassCard(Modifier.fillMaxWidth().padding(16.dp)) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Timer, null, tint = AuraCyan, modifier = Modifier.size(36.dp))
                        Spacer(Modifier.height(8.dp))
                        if (state.endOfSong) {
                            Text("Stops after this song", color = c.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        } else {
                            Text(formatDuration(state.remainingMs(now)), color = c.text, fontSize = 44.sp, fontWeight = FontWeight.ExtraBold)
                            Text("until playback pauses", color = c.muted)
                        }
                        Spacer(Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (!state.endOfSong) OutlineButton("+10 min", { SleepTimer.extend(10) })
                            OutlineButton("Cancel timer", { SleepTimer.cancel(); UiEvents.show("Sleep timer off") }, icon = Icons.Default.TimerOff)
                        }
                    }
                }
            }
            val options = listOf(15 to "15 minutes", 30 to "30 minutes", 45 to "45 minutes", 60 to "1 hour", 90 to "1 hour 30 minutes", -1 to "End of current song", -2 to "Custom time")
            options.forEach { (value, label) ->
                Row(
                    Modifier.fillMaxWidth().clickable { choice = value }.padding(horizontal = 12.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(choice == value, { choice = value }, colors = RadioButtonDefaults.colors(selectedColor = AuraCyan, unselectedColor = c.muted))
                    Text(label, color = c.text, modifier = Modifier.padding(start = 6.dp))
                }
            }
            if (choice == -2) {
                OutlinedTextField(
                    customMinutes, { v -> customMinutes = v.filter(Char::isDigit).take(3) },
                    label = { Text("Minutes") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)
                )
            }
            Spacer(Modifier.height(20.dp))
            GradientButton(
                "Start Timer",
                {
                    when (choice) {
                        -1 -> {
                            if (PlayerManager.currentSong == null) return@GradientButton UiEvents.show("Start a song first")
                            SleepTimer.startEndOfSong()
                            UiEvents.show("Playback will stop after this song")
                        }
                        -2 -> {
                            val m = customMinutes.toIntOrNull()?.coerceIn(1, 600) ?: return@GradientButton UiEvents.show("Enter the number of minutes")
                            SleepTimer.start(m); UiEvents.show("Sleep timer set for $m min")
                        }
                        else -> { SleepTimer.start(choice); UiEvents.show("Sleep timer set for $choice min") }
                    }
                },
                Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                icon = Icons.Default.Timer
            )
            Text("Music fades out gently in the last few seconds.", color = c.muted, fontSize = 12.sp, modifier = Modifier.padding(24.dp))
        }
    }
}

// ================================================================== Car mode

@Composable
fun CarModeScreen() {
    val controller = LocalAuraController.current
    val c = Aura.colors
    val settings by AuraPrefs.settings.collectAsState()
    val favorites by AuraPrefs.favorites.collectAsState()
    val song: MusicItem? = PlayerManager.currentSong
    val view = LocalView.current
    DisposableEffect(settings.keepScreenOnInCar) {
        view.keepScreenOn = settings.keepScreenOnInCar
        onDispose { view.keepScreenOn = false }
    }
    Column(
        Modifier.fillMaxSize().background(Color.Black)
            .windowInsetsPadding(WindowInsets.statusBars).windowInsetsPadding(WindowInsets.navigationBars).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { controller.back() }, modifier = Modifier.size(56.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Exit car mode", tint = Color.White, modifier = Modifier.size(32.dp))
            }
            Text("Car Mode", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (song != null && song.id >= 0) {
                val fav = song.key in favorites
                IconButton(onClick = { controller.toggleFavorite(song) }, modifier = Modifier.size(56.dp)) {
                    Icon(if (fav) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "Like", tint = if (fav) AuraPink else Color.White, modifier = Modifier.size(32.dp))
                }
            }
        }
        Spacer(Modifier.weight(0.4f))
        ArtworkImage(song, Modifier.fillMaxWidth(0.62f).aspectRatio(1f), corner = 24.dp, large = true)
        Spacer(Modifier.height(20.dp))
        Text(song?.title ?: "Nothing playing", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        Text(song?.artist ?: "Pick music in the app, then come back", color = Color(0xFFB0B8C8), fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(16.dp))
        val dur = PlayerManager.durationMs.coerceAtLeast(1)
        NeonSlider((PlayerManager.positionMs.toFloat() / dur).coerceIn(0f, 1f), { PlayerManager.seekTo((it * dur).toLong()) }, Modifier.fillMaxWidth())
        Spacer(Modifier.weight(0.4f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            CarButton(Icons.Default.SkipPrevious, "Previous", 76) { PlayerManager.previous() }
            CarButton(Icons.Default.Replay10, "Back 10 seconds", 64) { PlayerManager.seekBy(-10_000) }
            Box(
                Modifier.size(110.dp).clip(CircleShape).background(Brush.linearGradient(listOf(AuraCyan, AuraPurple))).clickable { PlayerManager.toggle() },
                contentAlignment = Alignment.Center
            ) {
                if (PlayerManager.isBuffering && !PlayerManager.isPlaying) CircularProgressIndicator(color = Color.White)
                else Icon(if (PlayerManager.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, "Play/Pause", tint = Color.White, modifier = Modifier.size(64.dp))
            }
            CarButton(Icons.Default.Forward10, "Forward 10 seconds", 64) { PlayerManager.seekBy(10_000) }
            CarButton(Icons.Default.SkipNext, "Next", 76) { PlayerManager.next() }
        }
        Spacer(Modifier.height(16.dp))
        Text("Android Auto: connect your phone and pick Aura Music on the car screen.", color = Color(0xFF7D8799), fontSize = 12.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun CarButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, size: Int, onClick: () -> Unit) {
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(Color(0xFF1A1F2B)).border(1.dp, Color(0x33FFFFFF), CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, label, tint = Color.White, modifier = Modifier.size((size * 0.5f).dp)) }
}
