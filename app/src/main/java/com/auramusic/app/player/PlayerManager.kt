package com.auramusic.app.player

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.auramusic.app.data.AuraPrefs
import com.auramusic.app.data.LibraryRepository
import com.auramusic.app.data.MusicItem
import com.auramusic.app.service.PlaybackService
import com.auramusic.app.ui.UiEvents
import com.google.common.util.concurrent.ListenableFuture

/**
 * UI-facing wrapper around a [MediaController] connected to [PlaybackService].
 * Exposes Compose state; actions issued before the controller connects are queued.
 */
@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
object PlayerManager {
    private var controller: MediaController? = null
    private var future: ListenableFuture<MediaController>? = null
    private val pending = mutableListOf<(MediaController) -> Unit>()
    private var restoreAttempted = false

    var currentSong by mutableStateOf<MusicItem?>(null)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var isBuffering by mutableStateOf(false)
        private set
    var positionMs by mutableLongStateOf(0L)
        private set
    var durationMs by mutableLongStateOf(0L)
        private set
    var shuffle by mutableStateOf(false)
        private set
    var repeatMode by mutableIntStateOf(Player.REPEAT_MODE_OFF)
        private set
    var speed by mutableFloatStateOf(1f)
        private set
    var queue by mutableStateOf<List<MusicItem>>(emptyList())
        private set
    var currentIndex by mutableIntStateOf(0)
        private set
    var queueTitle by mutableStateOf("Library")
        private set
    var connected by mutableStateOf(false)
        private set

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = sync()

        override fun onTimelineChanged(timeline: Timeline, reason: Int) = rebuildQueue()

        override fun onPlayerError(error: PlaybackException) {
            UiEvents.show("Can't play this file: ${error.errorCodeName.removePrefix("ERROR_CODE_").lowercase().replace('_', ' ')}")
            controller?.let { c -> if (c.hasNextMediaItem()) { c.seekToNextMediaItem(); c.prepare(); c.play() } }
        }
    }

    fun connect(context: Context) {
        if (controller != null || future != null) return
        val app = context.applicationContext
        val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
        val f = MediaController.Builder(app, token).buildAsync()
        future = f
        f.addListener({
            val c = runCatching { f.get() }.getOrNull()
            if (c == null) {
                future = null
                return@addListener
            }
            controller = c
            connected = true
            c.addListener(listener)
            rebuildQueue()
            sync()
            if (c.mediaItemCount == 0 && pending.isEmpty()) restoreLastSession(c)
            val actions = pending.toList()
            pending.clear()
            actions.forEach { it(c) }
        }, ContextCompat.getMainExecutor(app))
    }

    fun disconnect() {
        future?.let { MediaController.releaseFuture(it) }
        controller?.removeListener(listener)
        controller = null
        future = null
        connected = false
    }

    private fun withController(action: (MediaController) -> Unit) {
        val c = controller
        if (c != null) action(c) else pending += action
    }

    /** Puts the previous session's queue back (paused), so the mini player shows it. */
    private fun restoreLastSession(c: MediaController) {
        if (restoreAttempted) return
        restoreAttempted = true
        val (ids, index, position) = AuraPrefs.loadQueue()
        if (ids.isEmpty()) return
        val songs = ids.mapNotNull { LibraryRepository.song(it) }
        if (songs.isEmpty()) {
            restoreAttempted = false // library may not be loaded yet; retried by onLibraryLoaded()
            return
        }
        val idx = index.coerceIn(0, songs.lastIndex)
        val pos = if (songs[idx].id == ids.getOrNull(index)) position else 0L
        c.setMediaItems(songs.map { MediaTree.toMediaItem(it) }, idx, pos)
        c.prepare()
    }

    fun onLibraryLoaded() {
        val c = controller ?: return
        if (c.mediaItemCount == 0) restoreLastSession(c) else rebuildQueue()
    }

    // ---------------------------------------------------------------- state sync

    fun sync() {
        val c = controller ?: return
        isPlaying = c.isPlaying
        isBuffering = c.playbackState == Player.STATE_BUFFERING
        positionMs = c.currentPosition.coerceAtLeast(0L)
        val d = c.duration
        durationMs = if (d == C.TIME_UNSET || d <= 0) (currentSong?.durationMs ?: 0L) else d
        shuffle = c.shuffleModeEnabled
        repeatMode = c.repeatMode
        speed = c.playbackParameters.speed
        currentIndex = c.currentMediaItemIndex
        currentSong = c.currentMediaItem?.let { toSong(it) }
    }

    fun tick() {
        val c = controller ?: return
        positionMs = c.currentPosition.coerceAtLeast(0L)
        val d = c.duration
        if (d != C.TIME_UNSET && d > 0) durationMs = d
    }

    private fun rebuildQueue() {
        val c = controller ?: return
        queue = (0 until c.mediaItemCount).mapNotNull { toSong(c.getMediaItemAt(it)) }
        currentIndex = c.currentMediaItemIndex
    }

    private fun toSong(item: MediaItem): MusicItem? {
        MediaTree.songIdOf(item.mediaId)?.let { id -> LibraryRepository.song(id)?.let { return it } }
        val uri = item.localConfiguration?.uri ?: item.requestMetadata.mediaUri ?: return null
        val md = item.mediaMetadata
        return MusicItem(
            id = -(uri.hashCode().toLong().let { if (it < 0) -it else it } + 1),
            uri = uri,
            title = md.title?.toString() ?: uri.lastPathSegment ?: "Audio",
            artist = md.artist?.toString() ?: "Unknown artist",
            album = md.albumTitle?.toString() ?: "Unknown album",
            durationMs = md.durationMs ?: 0L
        )
    }

    // ---------------------------------------------------------------- commands

    fun playQueue(songs: List<MusicItem>, startIndex: Int = 0, title: String = "Library") {
        if (songs.isEmpty()) return
        // Keep very large libraries responsive: queue a window around the chosen song.
        val start = startIndex.coerceIn(songs.indices)
        val from = (start - 1500).coerceAtLeast(0)
        val list = songs.subList(from, (from + 3000).coerceAtMost(songs.size))
        val index = start - from
        queueTitle = title
        currentSong = list[index]
        withController { c ->
            c.setMediaItems(list.map { MediaTree.toMediaItem(it) }, index, 0L)
            c.prepare()
            c.play()
        }
    }

    /** Plays a freshly shuffled copy of [songs] (shuffle mode stays off so "next" follows it). */
    fun shuffleAll(songs: List<MusicItem>, title: String = "Shuffle") {
        if (songs.isEmpty()) return
        withController { it.shuffleModeEnabled = false }
        playQueue(songs.shuffled(), 0, title)
    }

    fun playExternal(uri: Uri, title: String) {
        queueTitle = "Opened file"
        withController { c ->
            c.setMediaItem(MediaTree.externalItem(uri, title))
            c.prepare()
            c.play()
        }
    }

    fun playNext(song: MusicItem) = withController { c ->
        if (c.mediaItemCount == 0) return@withController playQueue(listOf(song), 0, "Queue")
        c.addMediaItem((c.currentMediaItemIndex + 1).coerceAtMost(c.mediaItemCount), MediaTree.toMediaItem(song))
    }

    fun addToQueue(song: MusicItem) = withController { c ->
        if (c.mediaItemCount == 0) return@withController playQueue(listOf(song), 0, "Queue")
        c.addMediaItem(MediaTree.toMediaItem(song))
    }

    fun removeFromQueue(index: Int) = withController { c -> if (index in 0 until c.mediaItemCount) c.removeMediaItem(index) }

    fun moveInQueue(from: Int, to: Int) = withController { c ->
        if (from in 0 until c.mediaItemCount && to in 0 until c.mediaItemCount) c.moveMediaItem(from, to)
    }

    fun jumpTo(index: Int) = withController { c ->
        if (index in 0 until c.mediaItemCount) {
            c.seekTo(index, 0L)
            c.play()
        }
    }

    fun toggle() = withController { c ->
        when {
            c.isPlaying -> c.pause()
            c.playbackState == Player.STATE_ENDED -> { c.seekToDefaultPosition(0); c.play() }
            c.playbackState == Player.STATE_IDLE -> { c.prepare(); c.play() }
            else -> c.play()
        }
    }

    fun pause() = withController { it.pause() }
    fun seekTo(position: Long) {
        positionMs = position.coerceAtLeast(0L)
        withController { it.seekTo(position.coerceAtLeast(0L)) }
    }
    fun seekBy(deltaMs: Long) = withController { it.seekTo((it.currentPosition + deltaMs).coerceAtLeast(0L)) }
    fun next() = withController { if (it.hasNextMediaItem()) it.seekToNextMediaItem() else if (it.repeatMode == Player.REPEAT_MODE_ALL) it.seekTo(0, 0) }
    fun previous() = withController { c ->
        if (c.currentPosition > 3_000 || !c.hasPreviousMediaItem()) c.seekTo(0L) else c.seekToPreviousMediaItem()
    }

    fun toggleShuffle() = withController { it.shuffleModeEnabled = !it.shuffleModeEnabled }

    fun cycleRepeat() = withController {
        it.repeatMode = when (it.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun setPlaybackSpeed(value: Float) {
        val v = value.coerceIn(0.25f, 3f)
        AuraPrefs.updateSettings { it.copy(playbackSpeed = v) }
        withController { it.setPlaybackSpeed(v) }
    }

    fun setSkipSilence(enabled: Boolean) {
        AuraPrefs.updateSettings { it.copy(skipSilence = enabled) }
        PlaybackBridge.player?.skipSilenceEnabled = enabled
    }
}
