package com.auramusic.app.player

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.auramusic.app.data.AuraPrefs
import com.auramusic.app.data.LibraryRepository
import com.auramusic.app.data.MusicItem

/**
 * Media ID scheme shared by the in-app player and the Android Auto browse tree.
 *
 * Browsable nodes: `root`, `songs`, `favorites`, `recent`, `most`, `added`, `playlists`,
 * `albums`, `artists`, `folders`, and `pl|<name>`, `al|<name>`, `ar|<name>`, `fo|<path>`.
 * Playable items: `<parent>#<songId>` (the parent gives Auto its queue context).
 * External files opened via "Open with": `ext#<uri>`.
 */
object MediaTree {
    const val ROOT = "root"
    private const val SEP = "#"

    private val topLevel = listOf(
        "songs" to "All songs",
        "favorites" to "Liked songs",
        "recent" to "Recently played",
        "most" to "Most played",
        "added" to "Recently added",
        "playlists" to "Playlists",
        "albums" to "Albums",
        "artists" to "Artists",
        "folders" to "Folders"
    )

    fun songIdOf(mediaId: String?): Long? = mediaId?.substringAfterLast(SEP, "")?.toLongOrNull()

    fun parentOf(mediaId: String): String = mediaId.substringBeforeLast(SEP, "")

    fun playableId(parent: String, song: MusicItem) = "$parent$SEP${song.id}"

    fun externalId(uri: Uri) = "ext$SEP$uri"

    fun toMediaItem(song: MusicItem, parent: String = "q"): MediaItem {
        val extras = Bundle().apply { putLong("songId", song.id) }
        val metadata = MediaMetadata.Builder()
            .setTitle(song.title)
            .setArtist(song.artist)
            .setAlbumTitle(song.album)
            .setDisplayTitle(song.title)
            .setSubtitle(song.artist)
            .setArtworkUri(song.uri) // resolved by ArtworkBitmapLoader
            .setTrackNumber(song.track.takeIf { it > 0 })
            .setRecordingYear(song.year.takeIf { it > 0 })
            .setDurationMs(song.durationMs.takeIf { it > 0 })
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
            .setExtras(extras)
            .build()
        return MediaItem.Builder()
            .setMediaId(playableId(parent, song))
            .setUri(song.uri)
            .setMimeType(song.mimeType.takeIf { it.isNotBlank() })
            .setMediaMetadata(metadata)
            .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(song.uri).build())
            .build()
    }

    fun externalItem(uri: Uri, title: String): MediaItem = MediaItem.Builder()
        .setMediaId(externalId(uri))
        .setUri(uri)
        .setMediaMetadata(
            MediaMetadata.Builder().setTitle(title).setDisplayTitle(title).setArtist("Opened file")
                .setIsBrowsable(false).setIsPlayable(true).setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC).build()
        )
        .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(uri).build())
        .build()

    private fun folderItem(id: String, title: String, subtitle: String? = null, type: Int = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED): MediaItem =
        MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setDisplayTitle(title)
                    .setSubtitle(subtitle)
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .setMediaType(type)
                    .build()
            )
            .build()

    fun rootItem(): MediaItem = folderItem(ROOT, "Aura Music")

    // ---------------------------------------------------------------- tree

    fun songsFor(parent: String): List<MusicItem> {
        val songs = LibraryRepository.songs.value
        val favorites = AuraPrefs.favorites.value
        return when {
            parent == "songs" || parent == "q" -> songs
            parent == "favorites" -> songs.filter { it.key in favorites }
            parent == "recent" -> {
                val last = AuraPrefs.lastPlayed.value
                songs.filter { (last[it.key] ?: 0L) > 0L }.sortedByDescending { last[it.key] }.take(100)
            }
            parent == "most" -> {
                val counts = AuraPrefs.playCounts.value
                songs.filter { (counts[it.key] ?: 0) > 0 }.sortedByDescending { counts[it.key] }.take(100)
            }
            parent == "added" -> songs.sortedByDescending { it.dateAddedSec }.take(100)
            parent.startsWith("pl|") -> {
                val name = Uri.decode(parent.removePrefix("pl|"))
                val keys = AuraPrefs.playlists.value.firstOrNull { it.name == name }?.songUris ?: emptyList()
                keys.mapNotNull { LibraryRepository.songByKey(it) }
            }
            parent.startsWith("al|") -> {
                val album = Uri.decode(parent.removePrefix("al|"))
                songs.filter { it.album == album }.sortedWith(compareBy({ it.track }, { it.title.lowercase() }))
            }
            parent.startsWith("ar|") -> {
                val artist = Uri.decode(parent.removePrefix("ar|"))
                songs.filter { it.artist == artist }
            }
            parent.startsWith("fo|") -> {
                val folder = Uri.decode(parent.removePrefix("fo|"))
                songs.filter { it.folder == folder }
            }
            else -> emptyList()
        }
    }

    fun children(parentId: String): List<MediaItem> {
        val songs = LibraryRepository.songs.value
        return when (parentId) {
            ROOT -> topLevel.map { (id, title) -> folderItem(id, title) }
            "playlists" -> AuraPrefs.playlists.value.map {
                folderItem("pl|" + Uri.encode(it.name), it.name, "${it.songUris.size} songs", MediaMetadata.MEDIA_TYPE_PLAYLIST)
            }
            "albums" -> songs.groupBy { it.album }.toSortedMap(String.CASE_INSENSITIVE_ORDER).map { (album, list) ->
                folderItem("al|" + Uri.encode(album), album, list.first().artist, MediaMetadata.MEDIA_TYPE_ALBUM)
            }
            "artists" -> songs.groupBy { it.artist }.toSortedMap(String.CASE_INSENSITIVE_ORDER).map { (artist, list) ->
                folderItem("ar|" + Uri.encode(artist), artist, "${list.size} songs", MediaMetadata.MEDIA_TYPE_ARTIST)
            }
            "folders" -> songs.groupBy { it.folder }.toSortedMap(String.CASE_INSENSITIVE_ORDER).map { (folder, list) ->
                folderItem("fo|" + Uri.encode(folder), folder.substringAfterLast('/'), "${list.size} songs")
            }
            else -> songsFor(parentId).map { toMediaItem(it, parentId) }
        }
    }

    fun item(mediaId: String): MediaItem? {
        if (mediaId == ROOT) return rootItem()
        songIdOf(mediaId)?.let { id -> LibraryRepository.song(id)?.let { return toMediaItem(it, parentOf(mediaId)) } }
        return topLevel.firstOrNull { it.first == mediaId }?.let { folderItem(it.first, it.second) }
    }

    /** Turns lightweight items from a controller (Auto, Assistant, the app) into playable ones. */
    fun resolve(item: MediaItem): MediaItem? {
        songIdOf(item.mediaId)?.let { id ->
            LibraryRepository.song(id)?.let { return toMediaItem(it, parentOf(item.mediaId).ifBlank { "q" }) }
        }
        val uri = item.localConfiguration?.uri ?: item.requestMetadata.mediaUri
        if (uri != null) {
            LibraryRepository.songByKey(uri.toString())?.let { return toMediaItem(it) }
            return item.buildUpon().setUri(uri).build()
        }
        if (item.mediaId.startsWith("ext$SEP")) {
            val u = Uri.parse(item.mediaId.removePrefix("ext$SEP"))
            return externalItem(u, item.mediaMetadata.title?.toString() ?: u.lastPathSegment ?: "Audio")
        }
        return null
    }

    fun search(query: String): List<MusicItem> {
        val q = query.trim()
        if (q.isBlank()) return emptyList()
        return LibraryRepository.songs.value.filter {
            it.title.contains(q, true) || it.artist.contains(q, true) || it.album.contains(q, true)
        }.sortedByDescending { if (it.title.startsWith(q, true)) 2 else if (it.title.contains(q, true)) 1 else 0 }
    }
}
