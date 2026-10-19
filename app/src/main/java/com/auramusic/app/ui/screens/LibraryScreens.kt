package com.auramusic.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.auramusic.app.data.formatDuration
import com.auramusic.app.ui.LibraryTab
import com.auramusic.app.ui.LocalAuraController
import com.auramusic.app.ui.Route
import com.auramusic.app.ui.SmartPlaylists
import com.auramusic.app.ui.UiEvents
import com.auramusic.app.ui.components.ArtworkImage
import com.auramusic.app.ui.components.AuraChip
import com.auramusic.app.ui.components.AuraTopBar
import com.auramusic.app.ui.components.EmptyState
import com.auramusic.app.ui.components.GradientButton
import com.auramusic.app.ui.components.GradientIconTile
import com.auramusic.app.ui.components.MenuAction
import com.auramusic.app.ui.components.OutlineButton
import com.auramusic.app.ui.components.SectionHeader
import com.auramusic.app.ui.components.SongRow
import com.auramusic.app.ui.theme.Aura
import com.auramusic.app.ui.theme.AuraBlue
import com.auramusic.app.ui.theme.AuraCyan
import com.auramusic.app.ui.theme.AuraGreen
import com.auramusic.app.ui.theme.AuraOrange
import com.auramusic.app.ui.theme.AuraPink
import com.auramusic.app.ui.theme.AuraPurple

private enum class SongSort(val label: String) { TITLE("Title"), ARTIST("Artist"), ALBUM("Album"), ADDED("Recently added"), DURATION("Duration") }

@Composable
fun LibraryScreen(tab: LibraryTab, contentPadding: PaddingValues) {
    val controller = LocalAuraController.current
    Column(Modifier.fillMaxSize()) {
        AuraTopBar("Library") {
            if (tab == LibraryTab.PLAYLISTS) {
                Box(
                    Modifier.padding(end = 8.dp).size(40.dp).clip(CircleShape)
                        .background(Brush.linearGradient(listOf(AuraPurple, AuraPink)))
                        .clickable { controller.createPlaylistFor = emptyList() },
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Default.Add, "New playlist", tint = Color.White) }
            }
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(LibraryTab.entries) { t ->
                AuraChip(t.label, t == tab, onClick = { controller.replace(Route.Library(t)) })
            }
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.weight(1f)) {
            when (tab) {
                LibraryTab.SONGS -> SongsTab(contentPadding)
                LibraryTab.PLAYLISTS -> PlaylistsTab(contentPadding)
                LibraryTab.FAVORITES -> FavoritesTab(contentPadding)
                LibraryTab.ALBUMS -> AlbumsTab(contentPadding)
                LibraryTab.ARTISTS -> ArtistsTab(contentPadding)
                LibraryTab.FOLDERS -> FoldersTab(contentPadding)
            }
        }
    }
}

@Composable
private fun SongsTab(contentPadding: PaddingValues) {
    val controller = LocalAuraController.current
    val c = Aura.colors
    val songs by LibraryRepository.songs.collectAsState()
    var sort by rememberSaveable { mutableStateOf(SongSort.TITLE) }
    var sortMenu by remember { mutableStateOf(false) }
    val sorted = remember(songs, sort) {
        when (sort) {
            SongSort.TITLE -> songs
            SongSort.ARTIST -> songs.sortedBy { it.artist.lowercase() }
            SongSort.ALBUM -> songs.sortedWith(compareBy({ it.album.lowercase() }, { it.track }))
            SongSort.ADDED -> songs.sortedByDescending { it.dateAddedSec }
            SongSort.DURATION -> songs.sortedByDescending { it.durationMs }
        }
    }
    if (songs.isEmpty()) {
        EmptyState(Icons.Default.LibraryMusic, "No songs yet", "Music on your device appears here automatically.", actionLabel = "Rescan", onAction = { LibraryRepository.refresh() })
        return
    }
    LazyColumn(contentPadding = contentPadding) {
        item {
            Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable { controller.shuffle(sorted, "All songs") }.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Shuffle, null, tint = AuraCyan)
                    Spacer(Modifier.width(12.dp))
                    Text("Shuffle All", color = c.text, fontWeight = FontWeight.SemiBold)
                    Text("  •  ${sorted.size} songs", color = c.muted, fontSize = 13.sp)
                }
                Box {
                    IconButton(onClick = { sortMenu = true }) { Icon(Icons.AutoMirrored.Filled.Sort, "Sort", tint = c.muted) }
                    DropdownMenu(sortMenu, onDismissRequest = { sortMenu = false }) {
                        SongSort.entries.forEach { s ->
                            DropdownMenuItem(text = { Text(s.label, fontWeight = if (s == sort) FontWeight.Bold else FontWeight.Normal) }, onClick = { sort = s; sortMenu = false })
                        }
                    }
                }
            }
        }
        itemsIndexed(sorted, key = { _, s -> s.id }) { index, song ->
            SongRow(song, onClick = { controller.play(sorted, index, "All songs") })
        }
    }
}

@Composable
private fun FavoritesTab(contentPadding: PaddingValues) {
    val controller = LocalAuraController.current
    val songs by LibraryRepository.songs.collectAsState()
    val favorites by AuraPrefs.favorites.collectAsState()
    val liked = remember(songs, favorites) { songs.filter { it.key in favorites } }
    if (liked.isEmpty()) {
        EmptyState(Icons.Default.Favorite, "No liked songs", "Tap ♥ on any song and it will be saved here.")
        return
    }
    LazyColumn(contentPadding = contentPadding) {
        item { PlayShuffleRow(liked, "Liked songs") }
        itemsIndexed(liked, key = { _, s -> s.id }) { index, song ->
            SongRow(song, onClick = { controller.play(liked, index, "Liked songs") }, showFavorite = true)
        }
    }
}

@Composable
private fun PlaylistsTab(contentPadding: PaddingValues) {
    val controller = LocalAuraController.current
    val c = Aura.colors
    val playlists by AuraPrefs.playlists.collectAsState()
    val favorites by AuraPrefs.favorites.collectAsState()
    val counts by AuraPrefs.playCounts.collectAsState()
    val songs by LibraryRepository.songs.collectAsState()
    var filter by rememberSaveable { mutableStateOf(0) } // 0 all, 1 by you, 2 smart

    LazyColumn(contentPadding = contentPadding) {
        item {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("All", "By You", "Smart").forEachIndexed { i, label -> AuraChip(label, filter == i, { filter = i }) }
            }
        }
        if (filter != 1) {
            item {
                PlaylistRow("Liked Songs", "${songs.count { it.key in favorites }} songs", Icons.Default.Favorite, Brush.linearGradient(listOf(AuraPurple, AuraPink))) {
                    controller.navigate(Route.Playlist(SmartPlaylists.LIKED))
                }
            }
            item {
                PlaylistRow("Most Played", "${counts.count { it.value > 0 }} songs", Icons.AutoMirrored.Filled.TrendingUp, Brush.linearGradient(listOf(AuraOrange, Color(0xFFFF6B6B)))) {
                    controller.navigate(Route.Playlist(SmartPlaylists.MOST))
                }
            }
            item {
                PlaylistRow("Recently Played", "Your listening history", Icons.Default.History, Brush.linearGradient(listOf(AuraBlue, AuraCyan))) {
                    controller.navigate(Route.Playlist(SmartPlaylists.RECENT))
                }
            }
            item {
                PlaylistRow("Recently Added", "Newest files on your device", Icons.Default.NewReleases, Brush.linearGradient(listOf(Color(0xFF13B38B), AuraGreen))) {
                    controller.navigate(Route.Playlist(SmartPlaylists.ADDED))
                }
            }
        }
        if (filter != 2) {
            item { SectionHeader("Your playlists", "New", Modifier.padding(start = 20.dp, end = 12.dp, top = 8.dp)) { controller.createPlaylistFor = emptyList() } }
            if (playlists.isEmpty()) {
                item {
                    Text("No playlists yet — tap + to create one, then use a song's ⋮ menu → Add to playlist.",
                        color = c.muted, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                }
            }
            items(playlists, key = { "pl" + it.name }) { p ->
                val cover = p.songUris.firstNotNullOfOrNull { LibraryRepository.songByKey(it) }
                Row(
                    Modifier.fillMaxWidth().clickable { controller.navigate(Route.Playlist(SmartPlaylists.user(p.name))) }.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ArtworkImage(cover, Modifier.size(56.dp), corner = 12.dp)
                    Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                        Text(p.name, color = c.text, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${p.songUris.size} songs", color = c.muted, fontSize = 13.sp)
                    }
                    Icon(Icons.Default.PlayArrow, "Play", tint = AuraCyan, modifier = Modifier.clip(CircleShape).clickable {
                        val list = p.songUris.mapNotNull { LibraryRepository.songByKey(it) }
                        if (list.isEmpty()) UiEvents.show("This playlist is empty") else controller.play(list, 0, p.name)
                    }.padding(8.dp))
                }
            }
        }
    }
}

@Composable
private fun PlaylistRow(title: String, subtitle: String, icon: ImageVector, brush: Brush, onClick: () -> Unit) {
    val c = Aura.colors
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        GradientIconTile(icon, brush, size = 56.dp)
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(title, color = c.text, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = c.muted, fontSize = 13.sp)
        }
    }
}

@Composable
private fun AlbumsTab(contentPadding: PaddingValues) {
    val controller = LocalAuraController.current
    val c = Aura.colors
    val songs by LibraryRepository.songs.collectAsState()
    val albums = remember(songs) { songs.groupBy { it.album }.toSortedMap(String.CASE_INSENSITIVE_ORDER).map { it.key to it.value } }
    if (albums.isEmpty()) {
        EmptyState(Icons.Default.Album, "No albums", "Albums appear once you have music on your device.")
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(150.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = contentPadding.calculateBottomPadding() + 8.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(albums, key = { it.first }) { (album, list) ->
            Column(Modifier.clip(RoundedCornerShape(14.dp)).clickable { controller.navigate(Route.Album(album)) }) {
                ArtworkImage(list.first(), Modifier.fillMaxWidth().aspectRatio(1f), corner = 14.dp, large = true)
                Text(album, color = c.text, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
                Text("${list.first().artist} • ${list.size} songs", color = c.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun ArtistsTab(contentPadding: PaddingValues) {
    val controller = LocalAuraController.current
    val c = Aura.colors
    val songs by LibraryRepository.songs.collectAsState()
    val artists = remember(songs) { songs.groupBy { it.artist }.toSortedMap(String.CASE_INSENSITIVE_ORDER).map { it.key to it.value } }
    if (artists.isEmpty()) {
        EmptyState(Icons.Default.Person, "No artists", "Artists appear once you have music on your device.")
        return
    }
    LazyColumn(contentPadding = contentPadding) {
        items(artists, key = { it.first }) { (artist, list) ->
            Row(Modifier.fillMaxWidth().clickable { controller.navigate(Route.Artist(artist)) }.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                ArtworkImage(list.first(), Modifier.size(54.dp), corner = 27.dp)
                Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                    Text(artist, color = c.text, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val albums = list.map { it.album }.distinct().size
                    Text("${list.size} songs • $albums album${if (albums == 1) "" else "s"}", color = c.muted, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun FoldersTab(contentPadding: PaddingValues) {
    val controller = LocalAuraController.current
    val c = Aura.colors
    val songs by LibraryRepository.songs.collectAsState()
    val folders = remember(songs) { songs.groupBy { it.folder }.toSortedMap(String.CASE_INSENSITIVE_ORDER).map { it.key to it.value } }
    if (folders.isEmpty()) {
        EmptyState(Icons.Default.Folder, "No folders", "Folders that contain music show up here.")
        return
    }
    LazyColumn(contentPadding = contentPadding) {
        item {
            Text("Internal storage", color = c.muted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
        }
        items(folders, key = { it.first }) { (folder, list) ->
            Row(Modifier.fillMaxWidth().clickable { controller.navigate(Route.Folder(folder)) }.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Folder, null, tint = Color(0xFFFFC233), modifier = Modifier.size(44.dp))
                Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                    Text(folder.substringAfterLast('/'), color = c.text, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${list.size} songs • $folder", color = c.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

// ------------------------------------------------------------------ details

@Composable
fun PlayShuffleRow(songs: List<MusicItem>, title: String) {
    val controller = LocalAuraController.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        GradientButton("Play All", { controller.play(songs, 0, title) }, Modifier.weight(1f), icon = Icons.Default.PlayArrow, enabled = songs.isNotEmpty())
        OutlineButton("Shuffle", { controller.shuffle(songs, title) }, Modifier.weight(1f).height(52.dp), icon = Icons.Default.Shuffle, enabled = songs.isNotEmpty())
    }
}

@Composable
private fun CollectionHeader(cover: MusicItem?, title: String, subtitle: String) {
    val c = Aura.colors
    Column(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        ArtworkImage(cover, Modifier.size(190.dp), corner = 20.dp, large = true)
        Text(title, color = c.text, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 14.dp))
        Text(subtitle, color = c.muted, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 24.dp))
    }
}

@Composable
private fun SongCollection(
    title: String,
    subtitle: String,
    songs: List<MusicItem>,
    queueTitle: String,
    contentPadding: PaddingValues,
    numbered: Boolean = false,
    emptyMessage: String = "Nothing here yet.",
    extraActions: (MusicItem, Int) -> List<MenuAction> = { _, _ -> emptyList() },
    topBarActions: @Composable () -> Unit = {}
) {
    val controller = LocalAuraController.current
    Column(Modifier.fillMaxSize()) {
        AuraTopBar(title, onBack = { controller.back() }) { topBarActions() }
        LazyColumn(Modifier.weight(1f), contentPadding = contentPadding) {
            item { CollectionHeader(songs.firstOrNull(), title, subtitle) }
            item { PlayShuffleRow(songs, queueTitle) }
            if (songs.isEmpty()) {
                item { EmptyState(Icons.AutoMirrored.Filled.QueueMusic, "Empty", emptyMessage) }
            }
            itemsIndexed(songs, key = { i, s -> "${s.id}-$i" }) { index, song ->
                SongRow(
                    song,
                    onClick = { controller.play(songs, index, queueTitle) },
                    index = if (numbered) (song.track.takeIf { it > 0 } ?: (index + 1)) else null,
                    extraActions = extraActions(song, index)
                )
            }
        }
    }
}

private fun totalDuration(songs: List<MusicItem>) = formatDuration(songs.sumOf { it.durationMs })

@Composable
fun PlaylistDetailScreen(id: String, contentPadding: PaddingValues) {
    val controller = LocalAuraController.current
    val songs by LibraryRepository.songs.collectAsState()
    val favorites by AuraPrefs.favorites.collectAsState()
    val counts by AuraPrefs.playCounts.collectAsState()
    val lastPlayed by AuraPrefs.lastPlayed.collectAsState()
    val playlists by AuraPrefs.playlists.collectAsState()

    when (id) {
        SmartPlaylists.LIKED -> {
            val list = remember(songs, favorites) { songs.filter { it.key in favorites } }
            SongCollection("Liked Songs", "${list.size} songs • ${totalDuration(list)}", list, "Liked songs", contentPadding,
                emptyMessage = "Tap ♥ on any song to like it.")
        }
        SmartPlaylists.MOST -> {
            val list = remember(songs, counts) { songs.filter { (counts[it.key] ?: 0) > 0 }.sortedByDescending { counts[it.key] }.take(100) }
            SongCollection("Most Played", "Your top ${list.size}", list, "Most played", contentPadding, emptyMessage = "Play some music first.")
        }
        SmartPlaylists.RECENT -> {
            val list = remember(songs, lastPlayed) { songs.filter { (lastPlayed[it.key] ?: 0L) > 0L }.sortedByDescending { lastPlayed[it.key] }.take(100) }
            SongCollection("Recently Played", "${list.size} songs", list, "Recently played", contentPadding,
                emptyMessage = "Songs you play will appear here.",
                topBarActions = {
                    if (list.isNotEmpty()) TextButton(onClick = { AuraPrefs.clearHistory(); UiEvents.show("Listening history cleared") }) { Text("Clear") }
                })
        }
        SmartPlaylists.ADDED -> {
            val list = remember(songs) { songs.sortedByDescending { it.dateAddedSec }.take(100) }
            SongCollection("Recently Added", "${list.size} songs", list, "Recently added", contentPadding)
        }
        else -> {
            val name = id.removePrefix("user:")
            val playlist = playlists.firstOrNull { it.name == name }
            if (playlist == null) {
                Column(Modifier.fillMaxSize()) {
                    AuraTopBar("Playlist", onBack = { controller.back() })
                    EmptyState(Icons.AutoMirrored.Filled.QueueMusic, "Playlist not found", "It may have been deleted.")
                }
                return
            }
            val list = remember(playlist, songs) { playlist.songUris.mapNotNull { LibraryRepository.songByKey(it) } }
            var menu by remember { mutableStateOf(false) }
            var rename by remember { mutableStateOf(false) }
            var confirmDelete by remember { mutableStateOf(false) }
            val missing = playlist.songUris.size - list.size
            SongCollection(
                playlist.name,
                "${list.size} songs • ${totalDuration(list)}" + if (missing > 0) " • $missing unavailable" else "",
                list, playlist.name, contentPadding,
                emptyMessage = "Use a song's ⋮ menu → Add to playlist.",
                extraActions = { song, index ->
                    listOfNotNull(
                        MenuAction("Remove from playlist", Icons.Default.RemoveCircleOutline) { AuraPrefs.removeFromPlaylist(playlist.name, song.key) },
                        if (index > 0) MenuAction("Move up", Icons.Default.ArrowUpward) { AuraPrefs.moveInPlaylist(playlist.name, song.key, -1) } else null,
                        if (index < list.lastIndex) MenuAction("Move down", Icons.Default.ArrowDownward) { AuraPrefs.moveInPlaylist(playlist.name, song.key, 1) } else null
                    )
                },
                topBarActions = {
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Playlist options", tint = Aura.colors.text) }
                        DropdownMenu(menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Add songs from library") }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null) },
                                onClick = { menu = false; controller.switchTab(Route.Library(LibraryTab.SONGS)); UiEvents.show("Use ⋮ → Add to playlist on any song") })
                            DropdownMenuItem(text = { Text("Rename") }, leadingIcon = { Icon(Icons.Default.Edit, null) }, onClick = { menu = false; rename = true })
                            DropdownMenuItem(text = { Text("Delete playlist") }, leadingIcon = { Icon(Icons.Default.Delete, null) }, onClick = { menu = false; confirmDelete = true })
                        }
                    }
                }
            )
            if (rename) {
                TextInputDialog("Rename playlist", playlist.name, "Rename", onDismiss = { rename = false }) { newName ->
                    if (AuraPrefs.renamePlaylist(playlist.name, newName)) {
                        rename = false
                        controller.replace(Route.Playlist(SmartPlaylists.user(newName.trim())))
                    } else UiEvents.show("A playlist with that name already exists")
                }
            }
            if (confirmDelete) {
                AlertDialog(
                    onDismissRequest = { confirmDelete = false },
                    title = { Text("Delete “${playlist.name}”?") },
                    text = { Text("The songs stay on your device; only the playlist is removed.") },
                    confirmButton = {
                        TextButton(onClick = { confirmDelete = false; AuraPrefs.deletePlaylist(playlist.name); controller.back() }) { Text("Delete", color = AuraPink) }
                    },
                    dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
                )
            }
        }
    }
}

@Composable
fun AlbumDetailScreen(album: String, contentPadding: PaddingValues) {
    val songs by LibraryRepository.songs.collectAsState()
    val list = remember(songs, album) { songs.filter { it.album == album }.sortedWith(compareBy({ it.track == 0 }, { it.track }, { it.title.lowercase() })) }
    val first = list.firstOrNull()
    val year = list.map { it.year }.firstOrNull { it > 0 }
    SongCollection(album, listOfNotNull(first?.artist, year?.toString(), "${list.size} songs").joinToString(" • "), list, album, contentPadding, numbered = true)
}

@Composable
fun ArtistDetailScreen(artist: String, contentPadding: PaddingValues) {
    val songs by LibraryRepository.songs.collectAsState()
    val list = remember(songs, artist) { songs.filter { it.artist == artist }.sortedWith(compareBy({ it.album.lowercase() }, { it.track })) }
    val albums = list.map { it.album }.distinct().size
    SongCollection(artist, "${list.size} songs • $albums album${if (albums == 1) "" else "s"}", list, artist, contentPadding)
}

@Composable
fun FolderDetailScreen(path: String, contentPadding: PaddingValues) {
    val songs by LibraryRepository.songs.collectAsState()
    val list = remember(songs, path) { songs.filter { it.folder == path } }
    SongCollection(path.substringAfterLast('/'), "$path • ${list.size} songs", list, path.substringAfterLast('/'), contentPadding)
}

@Composable
fun TextInputDialog(title: String, initial: String, confirm: String, placeholder: String = "", onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(value, { value = it }, singleLine = true, placeholder = { Text(placeholder) }, modifier = Modifier.fillMaxWidth()) },
        confirmButton = { TextButton(onClick = { if (value.isNotBlank()) onConfirm(value.trim()) }, enabled = value.isNotBlank()) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
