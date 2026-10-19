package com.auramusic.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.app.SearchManager
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.auramusic.app.data.AuraPrefs
import com.auramusic.app.data.LibraryRepository
import com.auramusic.app.data.LyricsManager
import com.auramusic.app.data.MusicItem
import com.auramusic.app.data.formatDuration
import com.auramusic.app.player.MediaTree
import com.auramusic.app.player.PlayerManager
import com.auramusic.app.ui.AuraController
import com.auramusic.app.ui.LibraryTab
import com.auramusic.app.ui.LocalAuraController
import com.auramusic.app.ui.Route
import com.auramusic.app.ui.UiEvents
import com.auramusic.app.ui.components.AuraBackground
import com.auramusic.app.ui.components.GradientText
import com.auramusic.app.ui.screens.AlbumDetailScreen
import com.auramusic.app.ui.screens.ArtistDetailScreen
import com.auramusic.app.ui.screens.CarModeScreen
import com.auramusic.app.ui.screens.DownloadScreen
import com.auramusic.app.ui.screens.EqualizerScreen
import com.auramusic.app.ui.screens.FolderDetailScreen
import com.auramusic.app.ui.screens.HomeScreen
import com.auramusic.app.ui.screens.LibraryScreen
import com.auramusic.app.ui.screens.LyricsScreen
import com.auramusic.app.ui.screens.MiniPlayer
import com.auramusic.app.ui.screens.NowPlayingScreen
import com.auramusic.app.ui.screens.PlaylistDetailScreen
import com.auramusic.app.ui.screens.QueueSheet
import com.auramusic.app.ui.screens.SearchScreen
import com.auramusic.app.ui.screens.SettingsScreen
import com.auramusic.app.ui.screens.SleepTimerScreen
import com.auramusic.app.ui.screens.TextInputDialog
import com.auramusic.app.ui.theme.Aura
import com.auramusic.app.ui.theme.AuraCyan
import com.auramusic.app.ui.theme.AuraPurple
import com.auramusic.app.ui.theme.AuraTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private lateinit var controller: AuraController
    private lateinit var lyricsManager: LyricsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        AuraApp.ensureInit(this)
        controller = AuraController(this)
        lyricsManager = LyricsManager(this)
        val showSplash = savedInstanceState == null
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            val settings by AuraPrefs.settings.collectAsState()
            AuraTheme(settings.themeMode) {
                CompositionLocalProvider(LocalAuraController provides controller) {
                    AuraRoot(lyricsManager, showSplash)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        PlayerManager.connect(this)
    }

    override fun onStop() {
        PlayerManager.disconnect()
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent ?: return
        when {
            intent.action == Intent.ACTION_SEND && intent.type?.startsWith("text/") == true -> {
                val text = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim().orEmpty()
                if (text.isNotBlank()) {
                    controller.downloadPrefill = text
                    controller.switchTab(Route.Downloads)
                }
            }
            intent.action == Intent.ACTION_VIEW && intent.data != null -> {
                val uri = intent.data!!
                val song = LibraryRepository.songByKey(uri.toString())
                if (song != null) PlayerManager.playQueue(listOf(song), 0, "Opened file")
                else PlayerManager.playExternal(uri, displayName(uri))
                controller.showPlayer = true
            }
            intent.action == MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH -> {
                // "Play <song> on Aura Music" from Google Assistant.
                val query = intent.getStringExtra(SearchManager.QUERY).orEmpty()
                val results = if (query.isBlank()) LibraryRepository.songs.value.shuffled() else MediaTree.search(query)
                if (results.isNotEmpty()) PlayerManager.playQueue(results, 0, if (query.isBlank()) "Shuffle" else "Search: $query")
                else UiEvents.show("No songs match “$query”")
            }
            intent.getBooleanExtra(EXTRA_OPEN_DOWNLOADS, false) -> controller.switchTab(Route.Downloads)
            intent.getBooleanExtra(EXTRA_OPEN_PLAYER, false) -> controller.showPlayer = true
        }
    }

    private fun displayName(uri: Uri): String = runCatching {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()?.substringBeforeLast('.') ?: uri.lastPathSegment?.substringAfterLast('/')?.substringBeforeLast('.') ?: "Audio file"

    companion object {
        const val EXTRA_OPEN_PLAYER = "open_player"
        const val EXTRA_OPEN_DOWNLOADS = "open_downloads"
    }
}

@Composable
private fun AuraRoot(lyricsManager: LyricsManager, showSplashInitially: Boolean) {
    val controller = LocalAuraController.current
    val c = Aura.colors
    val activity = LocalActivity.current as ComponentActivity
    val snackbar = remember { SnackbarHostState() }
    val songs by LibraryRepository.songs.collectAsState()
    var splash by remember { mutableStateOf(showSplashInitially) }

    // ---------------------------------------------------------------- permissions
    val audioPermission = LibraryRepository.audioPermission
    var permanentlyDenied by remember { mutableStateOf(false) }
    val audioLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        LibraryRepository.refresh()
        if (!granted) {
            permanentlyDenied = !ActivityCompat.shouldShowRequestPermissionRationale(activity, audioPermission)
            UiEvents.show("Aura Music needs audio access to show your songs")
        }
    }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    fun requestAudio() {
        if (permanentlyDenied) {
            runCatching {
                activity.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", activity.packageName, null)))
            }
        } else audioLauncher.launch(audioPermission)
    }

    LaunchedEffect(Unit) {
        if (!LibraryRepository.checkPermission()) audioLauncher.launch(audioPermission)
        else if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    // Re-check on resume (the user may have granted access in system settings).
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && LibraryRepository.checkPermission() != LibraryRepository.hasPermission.value) {
                LibraryRepository.refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(songs.isNotEmpty()) { if (songs.isNotEmpty()) PlayerManager.onLibraryLoaded() }

    LaunchedEffect(Unit) {
        while (true) {
            delay(if (PlayerManager.isPlaying) 250 else 1000)
            PlayerManager.tick()
        }
    }

    LaunchedEffect(Unit) {
        UiEvents.messages.collect { msg ->
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(msg)
        }
    }

    BackHandler(enabled = controller.showPlayer || controller.showQueue || controller.stack.size > 1) { controller.back() }

    val current = PlayerManager.currentSong
    if (controller.showPlayer && current == null) controller.showPlayer = false
    val route = controller.current
    val fullScreen = route == Route.Car

    AuraBackground {
        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                if (!fullScreen) {
                    Column(Modifier.background(Brush.verticalGradient(listOf(Color.Transparent, c.bg.copy(alpha = 0.92f), c.bg)))) {
                        if (current != null) MiniPlayer(current)
                        AuraBottomBar(route) { controller.switchTab(it) }
                    }
                }
            }
        ) { padding ->
            val bottom = PaddingValues(bottom = padding.calculateBottomPadding() + 8.dp)
            Crossfade(targetState = route, label = "route", animationSpec = tween(180)) { r ->
                Box(Modifier.fillMaxSize()) {
                    when (r) {
                        Route.Home -> HomeScreen(bottom, onRequestPermission = { requestAudio() })
                        Route.Search -> SearchScreen(bottom)
                        is Route.Library -> LibraryScreen(r.tab, bottom)
                        Route.Downloads -> DownloadScreen(bottom)
                        Route.Settings -> SettingsScreen(bottom)
                        is Route.Playlist -> PlaylistDetailScreen(r.id, bottom)
                        is Route.Album -> AlbumDetailScreen(r.name, bottom)
                        is Route.Artist -> ArtistDetailScreen(r.name, bottom)
                        is Route.Folder -> FolderDetailScreen(r.path, bottom)
                        Route.Equalizer -> EqualizerScreen(bottom)
                        Route.Lyrics -> Column(Modifier.padding(bottom)) { LyricsScreen(lyricsManager) }
                        Route.Sleep -> SleepTimerScreen(bottom)
                        Route.Car -> CarModeScreen()
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = controller.showPlayer && current != null,
            enter = slideInVertically(tween(320, easing = FastOutSlowInEasing)) { it } + fadeIn(),
            exit = slideOutVertically(tween(260)) { it } + fadeOut()
        ) {
            val song = current ?: return@AnimatedVisibility
            NowPlayingScreen(song, lyricsManager)
        }

        if (controller.showQueue) QueueSheet(onDismiss = { controller.showQueue = false })

        AnimatedVisibility(visible = splash, enter = fadeIn(), exit = fadeOut(tween(450))) {
            SplashScreen(onFinished = { splash = false })
        }
    }

    AppDialogs()
}

@Composable
private fun AuraBottomBar(route: Route, onSelect: (Route) -> Unit) {
    val c = Aura.colors
    val items = listOf(
        Triple(Route.Home, "Home", Icons.Default.Home),
        Triple(Route.Search, "Search", Icons.Default.Search),
        Triple(Route.Library(LibraryTab.SONGS), "Library", Icons.Default.LibraryMusic),
        Triple(Route.Downloads, "Downloads", Icons.Default.Download)
    )
    NavigationBar(containerColor = c.navBar, tonalElevation = 0.dp, modifier = Modifier.heightIn(min = 0.dp)) {
        items.forEach { (dest, label, icon) ->
            val selected = when (dest) {
                is Route.Library -> route is Route.Library || route is Route.Playlist || route is Route.Album || route is Route.Artist || route is Route.Folder
                else -> route == dest
            }
            NavigationBarItem(
                selected = selected,
                onClick = { onSelect(dest) },
                icon = { Icon(icon, null) },
                label = { Text(label, fontSize = 11.sp) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = AuraCyan,
                    selectedTextColor = AuraCyan,
                    indicatorColor = AuraCyan.copy(alpha = 0.14f),
                    unselectedIconColor = c.muted,
                    unselectedTextColor = c.muted
                )
            )
        }
    }
}

@Composable
private fun SplashScreen(onFinished: () -> Unit) {
    val c = Aura.colors
    val scale = remember { Animatable(0.6f) }
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        alpha.animateTo(1f, tween(450))
        scale.animateTo(1f, tween(600, easing = FastOutSlowInEasing))
        delay(500)
        onFinished()
    }
    Box(
        Modifier.fillMaxSize().background(c.bg)
            .background(Brush.radialGradient(listOf(AuraPurple.copy(alpha = 0.35f), Color.Transparent), radius = 1100f))
            .clickable(enabled = false) {},
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.alpha(alpha.value)) {
            Box(
                Modifier.size(120.dp).scale(scale.value).clip(RoundedCornerShape(34.dp))
                    .background(Brush.linearGradient(listOf(AuraCyan, AuraPurple, Color(0xFFFF4FB3)))),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Default.MusicNote, null, tint = Color.White, modifier = Modifier.size(72.dp)) }
            Spacer(Modifier.height(24.dp))
            GradientText("Aura Music", fontSize = 36.sp)
            Text("Your Music. Your World.", color = c.muted, fontSize = 15.sp, modifier = Modifier.padding(top = 6.dp))
        }
        Text(
            "Play Anything • Anytime • Anywhere", color = c.muted, fontSize = 12.sp,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 36.dp).alpha(alpha.value)
        )
    }
}

@Composable
private fun AppDialogs() {
    val controller = LocalAuraController.current
    val c = Aura.colors
    val playlists by AuraPrefs.playlists.collectAsState()

    controller.playlistPickerFor?.let { songs ->
        AlertDialog(
            onDismissRequest = { controller.playlistPickerFor = null },
            title = { Text("Add to playlist") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(if (songs.size == 1) songs.first().title else "${songs.size} songs", color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(8.dp))
                    playlists.forEach { p ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                .clickable { controller.playlistPickerFor = null; controller.addSongsTo(p.name, songs) }
                                .padding(vertical = 12.dp, horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.AutoMirrored.Filled.PlaylistPlay, null, tint = AuraCyan)
                            Spacer(Modifier.width(12.dp))
                            Text(p.name, color = c.text, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${p.songUris.size}", color = c.muted, fontSize = 13.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { controller.createPlaylistFor = songs; controller.playlistPickerFor = null }) {
                    Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null); Spacer(Modifier.width(6.dp)); Text("New playlist")
                }
            },
            dismissButton = { TextButton(onClick = { controller.playlistPickerFor = null }) { Text("Cancel") } }
        )
    }

    controller.createPlaylistFor?.let { songs ->
        TextInputDialog("New playlist", "", "Create", "e.g. Workout", onDismiss = { controller.createPlaylistFor = null }) { name ->
            if (AuraPrefs.createPlaylist(name, songs.map { it.key })) {
                controller.createPlaylistFor = null
                UiEvents.show(if (songs.isEmpty()) "Playlist “$name” created" else "Created “$name” with ${songs.size} song${if (songs.size == 1) "" else "s"}")
            } else UiEvents.show("A playlist named “$name” already exists")
        }
    }

    controller.songInfoFor?.let { song -> SongInfoDialog(song) { controller.songInfoFor = null } }
}

@Composable
private fun SongInfoDialog(song: MusicItem, onDismiss: () -> Unit) {
    val c = Aura.colors
    val rows = listOf(
        "Title" to song.title,
        "Artist" to song.artist,
        "Album" to song.album,
        "Duration" to formatDuration(song.durationMs),
        "Track" to (song.track.takeIf { it > 0 }?.toString() ?: "—"),
        "Year" to (song.year.takeIf { it > 0 }?.toString() ?: "—"),
        "Format" to song.mimeType.ifBlank { "—" },
        "Size" to if (song.sizeBytes > 0) "%.1f MB".format(song.sizeBytes / 1_048_576f) else "—",
        "Folder" to song.folder
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Song info") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                rows.forEach { (k, v) ->
                    Row {
                        Text(k, color = c.muted, modifier = Modifier.width(76.dp), fontSize = 14.sp)
                        Text(v, color = c.text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } }
    )
}
