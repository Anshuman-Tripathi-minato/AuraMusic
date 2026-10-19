package com.auramusic.app.service

import android.app.PendingIntent
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.datasource.DataSourceBitmapLoader
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CacheBitmapLoader
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionError
import com.auramusic.app.AuraApp
import com.auramusic.app.MainActivity
import com.auramusic.app.data.Artwork
import com.auramusic.app.data.AuraPrefs
import com.auramusic.app.data.LibraryRepository
import com.auramusic.app.player.AudioEffects
import com.auramusic.app.player.MediaTree
import com.auramusic.app.player.PlaybackBridge
import com.auramusic.app.player.SleepTimer
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.ListeningExecutorService
import com.google.common.util.concurrent.MoreExecutors
import java.util.concurrent.Executors

@UnstableApi
class PlaybackService : MediaLibraryService() {
    private var session: MediaLibrarySession? = null
    private lateinit var player: ExoPlayer
    private val executor: ListeningExecutorService = MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor())
    private var pendingCountId: String? = null

    override fun onCreate() {
        super.onCreate()
        AuraApp.ensureInit(this)

        player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
                /* handleAudioFocus = */ true
            )
            .setHandleAudioBecomingNoisy(true) // pause when headphones are unplugged
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()

        val audioSessionId = Util.generateAudioSessionId(this)
        if (audioSessionId != C.AUDIO_SESSION_ID_UNSET) player.audioSessionId = audioSessionId
        AudioEffects.attach(player.audioSessionId)

        val settings = AuraPrefs.settings.value
        player.playbackParameters = PlaybackParameters(settings.playbackSpeed.coerceIn(0.25f, 3f))
        player.skipSilenceEnabled = settings.skipSilence
        PlaybackBridge.player = player

        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                pendingCountId = mediaItem?.mediaId
                if (player.isPlaying) countPlay()
                saveResumeState()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) countPlay() else saveResumeState()
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (!playWhenReady && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM) {
                    player.pauseAtEndOfMediaItems = false
                    SleepTimer.onPausedAtEndOfItem()
                }
            }

            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                AudioEffects.attach(audioSessionId)
            }
        })

        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra(MainActivity.EXTRA_OPEN_PLAYER, true),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        session = MediaLibrarySession.Builder(this, player, LibraryCallback())
            .setId("AuraMusicSession")
            .setSessionActivity(openApp)
            .setBitmapLoader(CacheBitmapLoader(ArtworkBitmapLoader()))
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = session

    override fun onDestroy() {
        saveResumeState()
        SleepTimer.cancel()
        PlaybackBridge.player = null
        AudioEffects.detach()
        session?.run {
            player.release()
            release()
        }
        session = null
        executor.shutdown()
        super.onDestroy()
    }

    private fun countPlay() {
        val id = pendingCountId ?: return
        if (player.currentMediaItem?.mediaId != id) return
        pendingCountId = null
        val key = MediaTree.songIdOf(id)?.let { LibraryRepository.song(it)?.key } ?: return
        AuraPrefs.trackPlayed(key)
    }

    private fun saveResumeState() {
        val ids = (0 until player.mediaItemCount).mapNotNull { MediaTree.songIdOf(player.getMediaItemAt(it).mediaId) }
        if (ids.isEmpty() || ids.size != player.mediaItemCount) return
        AuraPrefs.saveQueue(ids, player.currentMediaItemIndex, player.currentPosition.coerceAtLeast(0L))
    }

    private fun <T> background(block: () -> T): ListenableFuture<T> = executor.submit<T> {
        LibraryRepository.ensureLoadedBlocking()
        block()
    }

    private inner class LibraryCallback : MediaLibrarySession.Callback {
        override fun onGetLibraryRoot(
            session: MediaLibrarySession, browser: MediaSession.ControllerInfo, params: LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> = Futures.immediateFuture(LibraryResult.ofItem(MediaTree.rootItem(), params))

        override fun onGetItem(
            session: MediaLibrarySession, browser: MediaSession.ControllerInfo, mediaId: String
        ): ListenableFuture<LibraryResult<MediaItem>> = background {
            MediaTree.item(mediaId)?.let { LibraryResult.ofItem(it, null) } ?: LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
        }

        override fun onGetChildren(
            session: MediaLibrarySession, browser: MediaSession.ControllerInfo, parentId: String,
            page: Int, pageSize: Int, params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = background {
            val all = MediaTree.children(parentId)
            val from = (page.toLong() * pageSize).coerceAtMost(all.size.toLong()).toInt()
            val to = (from + pageSize).coerceAtMost(all.size)
            LibraryResult.ofItemList(ImmutableList.copyOf(all.subList(from, to)), params)
        }

        override fun onSearch(
            session: MediaLibrarySession, browser: MediaSession.ControllerInfo, query: String, params: LibraryParams?
        ): ListenableFuture<LibraryResult<Void>> = Futures.transform(
            background { MediaTree.search(query).size },
            { count ->
                session.notifySearchResultChanged(browser, query, count, params)
                LibraryResult.ofVoid()
            },
            ContextCompat.getMainExecutor(this@PlaybackService)
        )

        override fun onGetSearchResult(
            session: MediaLibrarySession, browser: MediaSession.ControllerInfo, query: String,
            page: Int, pageSize: Int, params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = background {
            val all = MediaTree.search(query).map { MediaTree.toMediaItem(it, "songs") }
            val from = (page.toLong() * pageSize).coerceAtMost(all.size.toLong()).toInt()
            LibraryResult.ofItemList(ImmutableList.copyOf(all.subList(from, (from + pageSize).coerceAtMost(all.size))), params)
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession, controller: MediaSession.ControllerInfo, mediaItems: List<MediaItem>
        ): ListenableFuture<List<MediaItem>> = background { mediaItems.mapNotNull { MediaTree.resolve(it) } }

        override fun onSetMediaItems(
            mediaSession: MediaSession, controller: MediaSession.ControllerInfo, mediaItems: List<MediaItem>,
            startIndex: Int, startPositionMs: Long
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = background {
            val single = mediaItems.singleOrNull()
            // Voice: "Play <song> on Aura Music".
            val query = single?.requestMetadata?.searchQuery
            if (single != null && single.mediaId.isBlank() && query != null) {
                val results = if (query.isBlank()) LibraryRepository.songs.value.shuffled() else MediaTree.search(query)
                return@background MediaSession.MediaItemsWithStartPosition(results.map { MediaTree.toMediaItem(it, "songs") }, 0, C.TIME_UNSET)
            }
            // A single song chosen from an Android Auto list: queue the whole list.
            if (single != null) {
                val parent = MediaTree.parentOf(single.mediaId)
                val songId = MediaTree.songIdOf(single.mediaId)
                if (songId != null && parent.isNotBlank() && parent != "q") {
                    val context = MediaTree.songsFor(parent)
                    val index = context.indexOfFirst { it.id == songId }
                    if (index >= 0) {
                        return@background MediaSession.MediaItemsWithStartPosition(
                            context.map { MediaTree.toMediaItem(it, parent) }, index, startPositionMs
                        )
                    }
                }
            }
            val resolved = mediaItems.mapNotNull { MediaTree.resolve(it) }
            val safeStart = if (startIndex == C.INDEX_UNSET) C.INDEX_UNSET else startIndex.coerceIn(0, (resolved.size - 1).coerceAtLeast(0))
            MediaSession.MediaItemsWithStartPosition(resolved, safeStart, startPositionMs)
        }

        override fun onPlaybackResumption(
            mediaSession: MediaSession, controller: MediaSession.ControllerInfo, isForPlayback: Boolean
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = resumption()

        private fun resumption() = background {
            val (ids, index, position) = AuraPrefs.loadQueue()
            val songs = ids.mapNotNull { LibraryRepository.song(it) }
            if (songs.isEmpty()) throw IllegalStateException("Nothing to resume")
            val idx = index.coerceIn(0, songs.lastIndex)
            val samePosition = songs.getOrNull(idx)?.id == ids.getOrNull(index)
            MediaSession.MediaItemsWithStartPosition(songs.map { MediaTree.toMediaItem(it) }, idx, if (samePosition) position else 0L)
        }
    }

    /** Loads artwork from the song file itself (works on every Android version). */
    private inner class ArtworkBitmapLoader : BitmapLoader {
        private val fallback = DataSourceBitmapLoader.Builder(this@PlaybackService).build()

        override fun supportsMimeType(mimeType: String): Boolean = fallback.supportsMimeType(mimeType)

        override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> = fallback.decodeBitmap(data)

        override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> {
            val isLocalAudio = uri.scheme == "content" && uri.authority == "media" && uri.path?.contains("/audio/media/") == true
            if (!isLocalAudio) return fallback.loadBitmap(uri)
            return executor.submit<Bitmap> {
                val id = uri.lastPathSegment?.toLongOrNull()
                val song = id?.let { LibraryRepository.song(it) }
                Artwork.loadUri(this@PlaybackService, uri, song?.albumArtUri, 512)
                    ?: throw IllegalStateException("No artwork")
            }
        }
    }
}
