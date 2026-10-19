package com.auramusic.app.ui.screens

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import com.auramusic.app.data.Artwork
import com.auramusic.app.data.AuraPrefs
import com.auramusic.app.data.LyricsManager
import com.auramusic.app.data.MusicItem
import com.auramusic.app.data.formatDuration
import com.auramusic.app.player.PlayerManager
import com.auramusic.app.player.SleepTimer
import com.auramusic.app.ui.LocalAuraController
import com.auramusic.app.ui.Route
import com.auramusic.app.ui.components.ArtworkImage
import com.auramusic.app.ui.components.NeonSlider
import com.auramusic.app.ui.components.PlayingBars
import com.auramusic.app.ui.theme.Aura
import com.auramusic.app.ui.theme.AuraCyan
import com.auramusic.app.ui.theme.AuraPink
import com.auramusic.app.ui.theme.AuraPurple
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun MiniPlayer(song: MusicItem, modifier: Modifier = Modifier) {
    val controller = LocalAuraController.current
    val c = Aura.colors
    val progress = if (PlayerManager.durationMs > 0) (PlayerManager.positionMs.toFloat() / PlayerManager.durationMs).coerceIn(0f, 1f) else 0f
    Column(
        modifier
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .shadow(16.dp, RoundedCornerShape(18.dp), ambientColor = AuraPurple, spotColor = AuraPurple)
            .clip(RoundedCornerShape(18.dp))
            .background(c.card2)
            .border(1.dp, c.stroke, RoundedCornerShape(18.dp))
            .clickable { controller.showPlayer = true }
            .pointerInput(Unit) {
                detectVerticalDragGestures { _, drag -> if (drag < -12) controller.showPlayer = true }
            }
    ) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            ArtworkImage(song, Modifier.size(46.dp), corner = 12.dp)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(song.title, color = c.text, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(song.artist, color = c.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = { PlayerManager.previous() }) { Icon(Icons.Default.SkipPrevious, "Previous", tint = c.text) }
            Box(
                Modifier.size(42.dp).clip(CircleShape).background(Brush.linearGradient(listOf(AuraCyan, AuraPurple))).clickable { PlayerManager.toggle() },
                contentAlignment = Alignment.Center
            ) {
                if (PlayerManager.isBuffering && !PlayerManager.isPlaying) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                else Icon(if (PlayerManager.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, if (PlayerManager.isPlaying) "Pause" else "Play", tint = Color.White)
            }
            IconButton(onClick = { PlayerManager.next() }) { Icon(Icons.Default.SkipNext, "Next", tint = c.text) }
        }
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(2.dp),
            color = AuraCyan,
            trackColor = Color.Transparent,
            drawStopIndicator = {}
        )
    }
}

@Composable
fun NowPlayingScreen(song: MusicItem, lyricsManager: LyricsManager) {
    val controller = LocalAuraController.current
    val context = LocalContext.current
    val c = Aura.colors
    val favorites by AuraPrefs.favorites.collectAsState()
    val sleep by SleepTimer.state.collectAsState()
    val isFav = song.key in favorites
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    var speedMenu by remember { mutableStateOf(false) }
    var moreMenu by remember { mutableStateOf(false) }

    val duration = PlayerManager.durationMs.coerceAtLeast(1L)
    val position = if (dragging) (dragValue * duration).toLong() else PlayerManager.positionMs

    val background by produceState<Bitmap?>(null, song.id) {
        value = if (song.id < 0) null else withContext(Dispatchers.IO) { Artwork.load(context, song, Artwork.SMALL) }
    }
    val lyrics by produceState<LyricsManager.Parsed?>(null, song.key) {
        value = withContext(Dispatchers.IO) { lyricsManager.load(song.key)?.let { lyricsManager.parse(it) } }
    }
    val lyricLine = lyrics?.let { p ->
        if (p.synced) p.lines.lastOrNull { it.timestampMs <= position }?.text ?: p.lines.firstOrNull()?.text
        else p.lines.firstOrNull { it.text.isNotBlank() }?.text
    }

    Box(Modifier.fillMaxSize().background(c.bg)) {
        // Blurred artwork glow behind everything (blur needs Android 12+; older devices get a soft fade).
        val bmp = background
        if (bmp != null) {
            Image(
                bmp.asImageBitmap(), null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().then(if (Build.VERSION.SDK_INT >= 31) Modifier.blur(60.dp) else Modifier).alpha(if (c.isDark) 0.45f else 0.3f)
            )
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(c.bg.copy(alpha = 0.35f), c.bg.copy(alpha = 0.85f), c.bg))))

        Column(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).windowInsetsPadding(WindowInsets.navigationBars)
                .pointerInput(Unit) { detectVerticalDragGestures { _, drag -> if (drag > 30) controller.showPlayer = false } }
                .padding(horizontal = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { controller.showPlayer = false }) { Icon(Icons.Default.KeyboardArrowDown, "Close player", tint = c.text, modifier = Modifier.size(30.dp)) }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("PLAYING FROM", color = c.muted, fontSize = 11.sp, letterSpacing = 1.2.sp)
                    Text(PlayerManager.queueTitle, color = c.text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Box {
                    IconButton(onClick = { moreMenu = true }) { Icon(Icons.Default.MoreVert, "More", tint = c.text) }
                    DropdownMenu(moreMenu, onDismissRequest = { moreMenu = false }) {
                        DropdownMenuItem(text = { Text("Add to playlist") }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null) },
                            onClick = { moreMenu = false; controller.addToPlaylist(listOf(song)) })
                        DropdownMenuItem(text = { Text("Go to album") }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.QueueMusic, null) },
                            onClick = { moreMenu = false; controller.navigate(Route.Album(song.album)) })
                        DropdownMenuItem(text = { Text("Share") }, leadingIcon = { Icon(Icons.Default.Share, null) },
                            onClick = { moreMenu = false; controller.share(song) })
                    }
                }
            }

            Spacer(Modifier.weight(0.6f))
            ArtworkImage(
                song,
                Modifier.fillMaxWidth().widthIn(max = 420.dp).aspectRatio(1f)
                    .shadow(30.dp, RoundedCornerShape(26.dp), ambientColor = AuraPurple, spotColor = AuraCyan),
                corner = 26.dp, large = true
            )
            Spacer(Modifier.weight(0.5f))

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(song.title, color = c.text, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(song.artist, color = c.muted, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable { controller.navigate(Route.Artist(song.artist)) })
                }
                if (song.id >= 0) {
                    IconButton(onClick = { controller.toggleFavorite(song) }) {
                        Icon(if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder, if (isFav) "Unlike" else "Like",
                            tint = if (isFav) AuraPink else c.text, modifier = Modifier.size(28.dp))
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            NeonSlider(
                value = if (dragging) dragValue else (PlayerManager.positionMs.toFloat() / duration).coerceIn(0f, 1f),
                onValueChange = { dragging = true; dragValue = it },
                onValueChangeFinished = {
                    PlayerManager.seekTo((dragValue * duration).toLong())
                    dragging = false
                },
                modifier = Modifier.fillMaxWidth()
            )
            Row(Modifier.fillMaxWidth()) {
                Text(formatDuration(position), color = c.muted, fontSize = 12.sp)
                Spacer(Modifier.weight(1f))
                Text(formatDuration(PlayerManager.durationMs), color = c.muted, fontSize = 12.sp)
            }

            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { PlayerManager.toggleShuffle() }) {
                    Icon(Icons.Default.Shuffle, "Shuffle", tint = if (PlayerManager.shuffle) AuraCyan else c.muted)
                }
                IconButton(onClick = { PlayerManager.previous() }, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Default.SkipPrevious, "Previous", tint = c.text, modifier = Modifier.size(38.dp))
                }
                Box(
                    Modifier.size(78.dp).shadow(24.dp, CircleShape, ambientColor = AuraCyan, spotColor = AuraPurple).clip(CircleShape)
                        .background(Brush.linearGradient(listOf(AuraCyan, AuraPurple, AuraPink)))
                        .clickable { PlayerManager.toggle() },
                    contentAlignment = Alignment.Center
                ) {
                    if (PlayerManager.isBuffering && !PlayerManager.isPlaying) CircularProgressIndicator(Modifier.size(30.dp), color = Color.White, strokeWidth = 3.dp)
                    else Icon(if (PlayerManager.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, if (PlayerManager.isPlaying) "Pause" else "Play",
                        tint = Color.White, modifier = Modifier.size(42.dp))
                }
                IconButton(onClick = { PlayerManager.next() }, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Default.SkipNext, "Next", tint = c.text, modifier = Modifier.size(38.dp))
                }
                IconButton(onClick = { PlayerManager.cycleRepeat() }) {
                    Icon(
                        if (PlayerManager.repeatMode == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                        "Repeat", tint = if (PlayerManager.repeatMode == Player.REPEAT_MODE_OFF) c.muted else AuraCyan
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            // Lyrics preview pill, like the design.
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.card.copy(alpha = 0.75f))
                    .border(1.dp, c.stroke, RoundedCornerShape(18.dp)).clickable { controller.openLyrics() }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Lyrics, null, tint = AuraCyan, modifier = Modifier.size(20.dp))
                Text(
                    lyricLine?.ifBlank { "♪" } ?: "Lyrics — tap to add or find",
                    color = if (lyricLine != null) c.text else c.muted, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(horizontal = 10.dp)
                )
            }

            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                IconButton(onClick = { controller.navigate(Route.Sleep) }) {
                    Icon(Icons.Default.Timer, "Sleep timer", tint = if (sleep.active) AuraCyan else c.muted)
                }
                Box {
                    IconButton(onClick = { speedMenu = true }) {
                        if (PlayerManager.speed != 1f) Text("${PlayerManager.speed}x", color = AuraCyan, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        else Icon(Icons.Default.Speed, "Playback speed", tint = c.muted)
                    }
                    DropdownMenu(speedMenu, onDismissRequest = { speedMenu = false }) {
                        listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f).forEach { s ->
                            DropdownMenuItem(text = { Text("${s}x", fontWeight = if (s == PlayerManager.speed) FontWeight.Bold else FontWeight.Normal) },
                                onClick = { speedMenu = false; PlayerManager.setPlaybackSpeed(s) })
                        }
                    }
                }
                IconButton(onClick = { controller.navigate(Route.Equalizer) }) { Icon(Icons.Default.Equalizer, "Equalizer", tint = c.muted) }
                IconButton(onClick = { controller.showQueue = true }) { Icon(Icons.AutoMirrored.Filled.QueueMusic, "Queue", tint = c.muted) }
            }
            Spacer(Modifier.weight(0.3f))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueSheet(onDismiss: () -> Unit) {
    val c = Aura.colors
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val listState = rememberLazyListState()
    val queue = PlayerManager.queue
    val current = PlayerManager.currentIndex
    LaunchedEffect(Unit) { if (current in queue.indices) listState.scrollToItem((current - 2).coerceAtLeast(0)) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = c.card) {
        Column(Modifier.fillMaxHeight(0.8f)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Up Next", color = c.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("${queue.size} songs • ${PlayerManager.queueTitle}", color = c.muted, fontSize = 13.sp)
                }
            }
            if (queue.isEmpty()) {
                Text("The queue is empty.", color = c.muted, modifier = Modifier.padding(20.dp), textAlign = TextAlign.Center)
            }
            LazyColumn(state = listState) {
                itemsIndexed(queue, key = { i, s -> "${s.id}-$i" }) { index, song ->
                    val isCurrent = index == current
                    Row(
                        Modifier.fillMaxWidth().background(if (isCurrent) AuraCyan.copy(alpha = 0.08f) else Color.Transparent)
                            .clickable { PlayerManager.jumpTo(index) }.padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            ArtworkImage(song, Modifier.size(44.dp))
                            if (isCurrent) PlayingBars(PlayerManager.isPlaying)
                        }
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(song.title, color = if (isCurrent) AuraCyan else c.text, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(song.artist, color = c.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (!isCurrent) {
                            IconButton(onClick = { PlayerManager.removeFromQueue(index) }) { Icon(Icons.Default.Close, "Remove from queue", tint = c.muted) }
                        }
                    }
                }
            }
        }
    }
}
