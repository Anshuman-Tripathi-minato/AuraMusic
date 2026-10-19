package com.auramusic.app.ui.screens

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auramusic.app.data.AuraPrefs
import com.auramusic.app.data.LibraryRepository
import com.auramusic.app.data.MusicItem
import com.auramusic.app.ui.LibraryTab
import com.auramusic.app.ui.LocalAuraController
import com.auramusic.app.ui.Route
import com.auramusic.app.ui.SmartPlaylists
import com.auramusic.app.ui.components.ArtworkImage
import com.auramusic.app.ui.components.EmptyState
import com.auramusic.app.ui.components.GlassCard
import com.auramusic.app.ui.components.GradientIconTile
import com.auramusic.app.ui.components.SectionHeader
import com.auramusic.app.ui.components.SongRow
import com.auramusic.app.ui.theme.Aura
import com.auramusic.app.ui.theme.AuraBlue
import com.auramusic.app.ui.theme.AuraCyan
import com.auramusic.app.ui.theme.AuraGreen
import com.auramusic.app.ui.theme.AuraOrange
import com.auramusic.app.ui.theme.AuraPink
import com.auramusic.app.ui.theme.AuraPurple
import java.util.Calendar

@Composable
fun HomeScreen(contentPadding: PaddingValues, onRequestPermission: () -> Unit) {
    val controller = LocalAuraController.current
    val c = Aura.colors
    val songs by LibraryRepository.songs.collectAsState()
    val hasPermission by LibraryRepository.hasPermission.collectAsState()
    val loading by LibraryRepository.loading.collectAsState()
    val settings by AuraPrefs.settings.collectAsState()
    val playlists by AuraPrefs.playlists.collectAsState()
    val counts by AuraPrefs.playCounts.collectAsState()
    val lastPlayed by AuraPrefs.lastPlayed.collectAsState()

    val recent = remember(songs, lastPlayed) {
        songs.filter { (lastPlayed[it.key] ?: 0L) > 0L }.sortedByDescending { lastPlayed[it.key] }.take(6)
    }
    val most = remember(songs, counts) {
        songs.filter { (counts[it.key] ?: 0) > 0 }.sortedByDescending { counts[it.key] }.take(12)
    }
    val added = remember(songs) { songs.sortedByDescending { it.dateAddedSec }.take(12) }
    val albumCount = remember(songs) { songs.map { it.album }.distinct().size }
    val artistCount = remember(songs) { songs.map { it.artist }.distinct().size }

    LazyColumn(contentPadding = contentPadding) {
        item {
            Row(
                Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(start = 20.dp, end = 8.dp, top = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(greeting(), color = c.muted, fontSize = 14.sp)
                    Text("${settings.userName} 👋", color = c.text, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = { controller.navigate(Route.Equalizer) }) { Icon(Icons.Default.Equalizer, "Equalizer", tint = c.text) }
                Box(
                    Modifier.padding(end = 8.dp).size(42.dp).clip(CircleShape)
                        .background(Brush.linearGradient(listOf(AuraCyan, AuraPurple)))
                        .clickable { controller.navigate(Route.Settings) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(settings.userName.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            }
        }
        item {
            Row(
                Modifier.padding(horizontal = 20.dp, vertical = 14.dp).fillMaxWidth().height(48.dp)
                    .clip(RoundedCornerShape(24.dp)).background(c.card2).border(1.dp, c.stroke, RoundedCornerShape(24.dp))
                    .clickable { controller.switchTab(Route.Search) }.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Search, null, tint = c.muted)
                Spacer(Modifier.width(10.dp))
                Text("Search songs, artists, albums…", color = c.muted)
            }
        }

        if (!hasPermission) {
            item {
                EmptyState(
                    Icons.Default.LibraryMusic, "Let Aura find your music",
                    "Allow access to audio files so Aura Music can build your offline library.",
                    actionLabel = "Allow access", onAction = onRequestPermission
                )
            }
            return@LazyColumn
        }

        item {
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile("Songs", songs.size, Icons.Default.MusicNote, Brush.linearGradient(listOf(AuraBlue, AuraCyan)), Modifier.weight(1f)) {
                        controller.switchTab(Route.Library(LibraryTab.SONGS))
                    }
                    StatTile("Albums", albumCount, Icons.Default.Album, Brush.linearGradient(listOf(Color(0xFF13B38B), AuraGreen)), Modifier.weight(1f)) {
                        controller.switchTab(Route.Library(LibraryTab.ALBUMS))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile("Artists", artistCount, Icons.Default.Person, Brush.linearGradient(listOf(AuraPurple, AuraPink)), Modifier.weight(1f)) {
                        controller.switchTab(Route.Library(LibraryTab.ARTISTS))
                    }
                    StatTile("Playlists", playlists.size, Icons.AutoMirrored.Filled.QueueMusic, Brush.linearGradient(listOf(AuraOrange, Color(0xFFFF6B6B))), Modifier.weight(1f)) {
                        controller.switchTab(Route.Library(LibraryTab.PLAYLISTS))
                    }
                }
            }
        }
        item {
            Row(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickAction("Shuffle", Icons.Default.Shuffle, Modifier.weight(1f)) { controller.shuffle(songs, "Shuffle all") }
                QuickAction("Sleep", Icons.Default.Timer, Modifier.weight(1f)) { controller.navigate(Route.Sleep) }
                QuickAction("Car mode", Icons.Default.DirectionsCar, Modifier.weight(1f)) { controller.navigate(Route.Car) }
                QuickAction("Settings", Icons.Default.Settings, Modifier.weight(1f)) { controller.navigate(Route.Settings) }
            }
        }

        if (songs.isEmpty()) {
            item {
                EmptyState(
                    Icons.Default.LibraryMusic,
                    if (loading) "Scanning your music…" else "No music found",
                    if (loading) "This only takes a moment." else "Copy audio files to your phone or download a song from the Downloads tab.",
                    actionLabel = if (loading) null else "Rescan", onAction = { LibraryRepository.refresh() }
                )
            }
            return@LazyColumn
        }

        item {
            SectionHeader("Recently Played", "See all", Modifier.padding(start = 20.dp, end = 12.dp)) {
                controller.navigate(Route.Playlist(SmartPlaylists.RECENT))
            }
        }
        if (recent.isEmpty()) {
            item { Text("Songs you play will show up here.", color = c.muted, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) }
        } else {
            items(recent, key = { "r" + it.id }) { song ->
                SongRow(song, onClick = { controller.play(recent, recent.indexOf(song), "Recently played") })
            }
        }

        if (most.isNotEmpty()) {
            item {
                SectionHeader("Most Played", "See all", Modifier.padding(start = 20.dp, end = 12.dp, top = 12.dp)) {
                    controller.navigate(Route.Playlist(SmartPlaylists.MOST))
                }
            }
            item { SongCarousel(most, "Most played") }
        }

        item {
            SectionHeader("Recently Added", "See all", Modifier.padding(start = 20.dp, end = 12.dp, top = 12.dp)) {
                controller.navigate(Route.Playlist(SmartPlaylists.ADDED))
            }
        }
        item { SongCarousel(added, "Recently added") }
    }
}

@Composable
private fun SongCarousel(songs: List<MusicItem>, title: String) {
    val controller = LocalAuraController.current
    val c = Aura.colors
    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        items(songs, key = { title + it.id }) { song ->
            Column(
                Modifier.width(132.dp).clip(RoundedCornerShape(14.dp))
                    .clickable { controller.play(songs, songs.indexOf(song), title) }
            ) {
                ArtworkImage(song, Modifier.size(132.dp), corner = 14.dp)
                Text(song.title, color = c.text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
                Text(song.artist, color = c.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: Int, icon: ImageVector, brush: Brush, modifier: Modifier, onClick: () -> Unit) {
    val c = Aura.colors
    GlassCard(modifier, onClick = onClick) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            GradientIconTile(icon, brush, size = 42.dp)
            Column(Modifier.padding(start = 12.dp)) {
                Text(label, color = c.text, fontWeight = FontWeight.SemiBold)
                Text("%,d".format(value), color = c.muted, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun QuickAction(label: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    val c = Aura.colors
    GlassCard(modifier, onClick = onClick, corner = 16.dp) {
        Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = AuraCyan, modifier = Modifier.size(22.dp))
            Spacer(Modifier.height(4.dp))
            Text(label, color = c.text, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private fun greeting(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 5..11 -> "Good Morning"
    in 12..16 -> "Good Afternoon"
    in 17..21 -> "Good Evening"
    else -> "Good Night"
}
