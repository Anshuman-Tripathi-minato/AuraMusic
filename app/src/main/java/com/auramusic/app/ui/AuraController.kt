package com.auramusic.app.ui

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.auramusic.app.data.AuraPrefs
import com.auramusic.app.data.MusicItem
import com.auramusic.app.player.PlayerManager

enum class LibraryTab(val label: String) {
    SONGS("All Songs"), PLAYLISTS("Playlists"), FAVORITES("Favorites"), ALBUMS("Albums"), ARTISTS("Artists"), FOLDERS("Folders")
}

sealed interface Route {
    data object Home : Route
    data object Search : Route
    data class Library(val tab: LibraryTab = LibraryTab.SONGS) : Route
    data object Downloads : Route
    data object Settings : Route
    data class Playlist(val id: String) : Route
    data class Album(val name: String) : Route
    data class Artist(val name: String) : Route
    data class Folder(val path: String) : Route
    data object Equalizer : Route
    data object Lyrics : Route
    data object Sleep : Route
    data object Car : Route
}

/** Smart playlist ids used by [Route.Playlist]; user playlists use "user:<name>". */
object SmartPlaylists {
    const val LIKED = "smart:liked"
    const val RECENT = "smart:recent"
    const val MOST = "smart:most"
    const val ADDED = "smart:added"
    fun user(name: String) = "user:$name"
}

/** Navigation back stack + app-wide actions, shared with every screen via [LocalAuraController]. */
class AuraController(private val context: Context) {
    val stack = mutableStateListOf<Route>(Route.Home)
    var showPlayer by mutableStateOf(false)
    var showQueue by mutableStateOf(false)
    /** Non-null → show "Add to playlist" picker for these songs. */
    var playlistPickerFor by mutableStateOf<List<MusicItem>?>(null)
    /** Non-null → show "Create playlist" dialog; the songs are added to the new playlist. */
    var createPlaylistFor by mutableStateOf<List<MusicItem>?>(null)
    var songInfoFor by mutableStateOf<MusicItem?>(null)
    var downloadPrefill by mutableStateOf<String?>(null)

    val current: Route get() = stack.last()

    fun navigate(route: Route) {
        showPlayer = false
        showQueue = false
        if (current != route) stack.add(route)
    }

    /** Bottom-navigation destinations reset the stack to Home → destination. */
    fun switchTab(route: Route) {
        showPlayer = false
        showQueue = false
        stack.clear()
        stack.add(Route.Home)
        if (route != Route.Home) stack.add(route)
    }

    fun replace(route: Route) {
        stack[stack.lastIndex] = route
    }

    fun back(): Boolean {
        when {
            showQueue -> showQueue = false
            showPlayer -> showPlayer = false
            stack.size > 1 -> stack.removeAt(stack.lastIndex)
            else -> return false
        }
        return true
    }

    // ---------------------------------------------------------------- actions

    fun play(songs: List<MusicItem>, index: Int, title: String, openPlayer: Boolean = false) {
        PlayerManager.playQueue(songs, index, title)
        if (openPlayer) showPlayer = true
    }

    fun shuffle(songs: List<MusicItem>, title: String) {
        if (songs.isEmpty()) return UiEvents.show("Nothing to shuffle yet")
        PlayerManager.shuffleAll(songs, title)
    }

    fun playNext(song: MusicItem) {
        PlayerManager.playNext(song)
        UiEvents.show("“${song.title}” will play next")
    }

    fun addToQueue(song: MusicItem) {
        PlayerManager.addToQueue(song)
        UiEvents.show("Added to queue")
    }

    fun toggleFavorite(song: MusicItem) {
        val added = AuraPrefs.toggleFavorite(song.key)
        UiEvents.show(if (added) "Added to Liked Songs" else "Removed from Liked Songs")
    }

    fun addToPlaylist(songs: List<MusicItem>) {
        if (songs.isEmpty()) return
        if (AuraPrefs.playlists.value.isEmpty()) createPlaylistFor = songs else playlistPickerFor = songs
    }

    fun addSongsTo(playlist: String, songs: List<MusicItem>) {
        val added = songs.count { AuraPrefs.addToPlaylist(playlist, it.key) }
        UiEvents.show(
            when {
                added == 0 -> "Already in “$playlist”"
                songs.size == 1 -> "Added to “$playlist”"
                else -> "Added $added songs to “$playlist”"
            }
        )
    }

    fun share(song: MusicItem) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = song.mimeType.ifBlank { "audio/*" }
            putExtra(Intent.EXTRA_STREAM, song.uri)
            putExtra(Intent.EXTRA_TITLE, song.title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching {
            context.startActivity(Intent.createChooser(send, "Share “${song.title}”").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure { UiEvents.show("No app available to share this file") }
    }

    fun openLyrics() = navigate(Route.Lyrics)
}

val LocalAuraController = staticCompositionLocalOf<AuraController> { error("AuraController not provided") }
