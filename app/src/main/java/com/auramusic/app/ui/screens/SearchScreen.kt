package com.auramusic.app.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auramusic.app.data.AuraPrefs
import com.auramusic.app.data.LibraryRepository
import com.auramusic.app.ui.LibraryTab
import com.auramusic.app.ui.LocalAuraController
import com.auramusic.app.ui.Route
import com.auramusic.app.ui.SmartPlaylists
import com.auramusic.app.ui.components.ArtworkImage
import com.auramusic.app.ui.components.AuraTopBar
import com.auramusic.app.ui.components.EmptyState
import com.auramusic.app.ui.components.SectionHeader
import com.auramusic.app.ui.components.SongRow
import com.auramusic.app.ui.theme.Aura
import com.auramusic.app.ui.theme.AuraBlue
import com.auramusic.app.ui.theme.AuraCyan
import com.auramusic.app.ui.theme.AuraGreen
import com.auramusic.app.ui.theme.AuraOrange
import com.auramusic.app.ui.theme.AuraPink
import com.auramusic.app.ui.theme.AuraPurple

@Composable
fun SearchScreen(contentPadding: PaddingValues) {
    val controller = LocalAuraController.current
    val c = Aura.colors
    val focus = LocalFocusManager.current
    val songs by LibraryRepository.songs.collectAsState()
    val playlists by AuraPrefs.playlists.collectAsState()
    val recentSearches by AuraPrefs.recentSearches.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    val q = query.trim()
    val songResults = remember(songs, q) {
        if (q.isBlank()) emptyList() else songs.filter {
            it.title.contains(q, true) || it.artist.contains(q, true) || it.album.contains(q, true)
        }.sortedByDescending { if (it.title.startsWith(q, true)) 2 else if (it.title.contains(q, true)) 1 else 0 }
    }
    val artistResults = remember(songs, q) { if (q.isBlank()) emptyList() else songs.map { it.artist }.distinct().filter { it.contains(q, true) }.take(8) }
    val albumResults = remember(songs, q) {
        if (q.isBlank()) emptyList() else songs.filter { it.album.contains(q, true) }.distinctBy { it.album }.take(8)
    }
    val playlistResults = remember(playlists, q) { if (q.isBlank()) emptyList() else playlists.filter { it.name.contains(q, true) } }

    fun saveQuery() = AuraPrefs.addRecentSearch(query)

    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

    Column(Modifier.fillMaxSize()) {
        AuraTopBar("Search")
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).focusRequester(focusRequester),
            placeholder = { Text("Search songs, artists, albums…") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, "Clear") } },
            singleLine = true,
            shape = RoundedCornerShape(26.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { saveQuery(); focus.clearFocus() }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AuraCyan, unfocusedBorderColor = c.stroke,
                focusedContainerColor = c.card2, unfocusedContainerColor = c.card2
            )
        )
        Spacer(Modifier.height(10.dp))

        if (q.isBlank()) {
            LazyColumn(contentPadding = contentPadding) {
                item {
                    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            CategoryTile("Songs", Icons.Default.MusicNote, listOf(Color(0xFF13B38B), AuraGreen), Modifier.weight(1f)) { controller.switchTab(Route.Library(LibraryTab.SONGS)) }
                            CategoryTile("Artists", Icons.Default.Person, listOf(AuraBlue, AuraPurple), Modifier.weight(1f)) { controller.switchTab(Route.Library(LibraryTab.ARTISTS)) }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            CategoryTile("Albums", Icons.Default.Album, listOf(AuraOrange, Color(0xFFFF6B6B)), Modifier.weight(1f)) { controller.switchTab(Route.Library(LibraryTab.ALBUMS)) }
                            CategoryTile("Playlists", Icons.AutoMirrored.Filled.QueueMusic, listOf(Color(0xFF3D5BFF), AuraCyan), Modifier.weight(1f)) { controller.switchTab(Route.Library(LibraryTab.PLAYLISTS)) }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            CategoryTile("Liked", Icons.Default.Favorite, listOf(AuraPurple, AuraPink), Modifier.weight(1f)) { controller.navigate(Route.Playlist(SmartPlaylists.LIKED)) }
                            CategoryTile("Folders", Icons.Default.Folder, listOf(Color(0xFF0E7BD8), Color(0xFF29B6F6)), Modifier.weight(1f)) { controller.switchTab(Route.Library(LibraryTab.FOLDERS)) }
                        }
                    }
                }
                if (recentSearches.isNotEmpty()) {
                    item { SectionHeader("Recent Searches", "Clear", Modifier.padding(start = 20.dp, end = 12.dp, top = 16.dp)) { AuraPrefs.clearRecentSearches() } }
                    items(recentSearches, key = { "rs$it" }) { s ->
                        Row(Modifier.fillMaxWidth().clickable { query = s }.padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.History, null, tint = c.muted, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(14.dp))
                            Text(s, color = c.text)
                        }
                    }
                }
            }
        } else if (songResults.isEmpty() && artistResults.isEmpty() && playlistResults.isEmpty()) {
            EmptyState(Icons.Default.SearchOff, "No results", "Nothing in your library matches “$q”.")
        } else {
            LazyColumn(contentPadding = contentPadding) {
                if (artistResults.isNotEmpty()) {
                    item { SectionHeader("Artists", modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) }
                    items(artistResults, key = { "a$it" }) { artist ->
                        ResultRow(artist, "Artist", Icons.Default.Person) { saveQuery(); controller.navigate(Route.Artist(artist)) }
                    }
                }
                if (albumResults.isNotEmpty()) {
                    item { SectionHeader("Albums", modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) }
                    items(albumResults, key = { "al${it.album}" }) { song ->
                        Row(Modifier.fillMaxWidth().clickable { saveQuery(); controller.navigate(Route.Album(song.album)) }.padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            ArtworkImage(song, Modifier.size(48.dp))
                            Column(Modifier.padding(horizontal = 12.dp)) {
                                Text(song.album, color = c.text, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("Album • ${song.artist}", color = c.muted, fontSize = 13.sp, maxLines = 1)
                            }
                        }
                    }
                }
                if (playlistResults.isNotEmpty()) {
                    item { SectionHeader("Playlists", modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) }
                    items(playlistResults, key = { "p${it.name}" }) { p ->
                        ResultRow(p.name, "Playlist • ${p.songUris.size} songs", Icons.AutoMirrored.Filled.QueueMusic) {
                            saveQuery(); controller.navigate(Route.Playlist(SmartPlaylists.user(p.name)))
                        }
                    }
                }
                if (songResults.isNotEmpty()) {
                    item { SectionHeader("Songs", "${songResults.size}", Modifier.padding(start = 20.dp, end = 12.dp, top = 4.dp)) }
                    items(songResults.take(200), key = { "s${it.id}" }) { song ->
                        SongRow(song, onClick = {
                            saveQuery()
                            focus.clearFocus()
                            controller.play(songResults, songResults.indexOf(song), "Search: $q")
                        })
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultRow(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit) {
    val c = Aura.colors
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(48.dp).clip(RoundedCornerShape(24.dp)).background(c.card2), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = AuraCyan)
        }
        Column(Modifier.padding(horizontal = 12.dp)) {
            Text(title, color = c.text, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = c.muted, fontSize = 13.sp, maxLines = 1)
        }
    }
}

@Composable
private fun CategoryTile(label: String, icon: ImageVector, colors: List<Color>, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier.height(58.dp).clip(RoundedCornerShape(16.dp)).background(Brush.linearGradient(colors)).clickable(onClick = onClick).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = Color.White)
        Spacer(Modifier.width(12.dp))
        Text(label, color = Color.White, fontWeight = FontWeight.Bold)
    }
}
