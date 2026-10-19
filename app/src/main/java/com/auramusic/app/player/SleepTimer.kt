package com.auramusic.app.player

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Same-process handle to the service's ExoPlayer for features MediaController can't reach. */
object PlaybackBridge {
    @Volatile var player: ExoPlayer? = null
}

/**
 * Sleep timer that runs next to the player (not in the UI), so it keeps working when the
 * app is in the background. The last few seconds fade the volume out smoothly.
 */
@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
object SleepTimer {
    data class State(
        val active: Boolean = false,
        val endOfSong: Boolean = false,
        val endAtElapsed: Long = 0L,
        val totalMs: Long = 0L
    ) {
        fun remainingMs(now: Long = SystemClock.elapsedRealtime()) = if (active && !endOfSong) (endAtElapsed - now).coerceAtLeast(0L) else 0L
    }

    private const val FADE_MS = 8_000L
    private val handler = Handler(Looper.getMainLooper())
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()
    private var fading = false

    private val tick = object : Runnable {
        override fun run() {
            val s = _state.value
            if (!s.active || s.endOfSong) return
            val remaining = s.remainingMs()
            val player = PlaybackBridge.player
            if (remaining <= 0L) {
                player?.pause()
                restoreVolume()
                cancel()
                return
            }
            if (remaining <= FADE_MS && player != null) {
                fading = true
                player.volume = (remaining.toFloat() / FADE_MS).coerceIn(0.05f, 1f)
            }
            handler.postDelayed(this, if (remaining <= FADE_MS) 250L else 1000L)
        }
    }

    fun start(minutes: Int) {
        if (minutes <= 0) return
        cancel()
        val total = minutes * 60_000L
        _state.value = State(active = true, endAtElapsed = SystemClock.elapsedRealtime() + total, totalMs = total)
        handler.post(tick)
    }

    /** Pause when the current song finishes. */
    fun startEndOfSong() {
        cancel()
        PlaybackBridge.player?.pauseAtEndOfMediaItems = true
        _state.value = State(active = true, endOfSong = true)
    }

    fun extend(minutes: Int) {
        val s = _state.value
        if (!s.active || s.endOfSong) return start(minutes)
        restoreVolume()
        _state.value = s.copy(endAtElapsed = s.endAtElapsed + minutes * 60_000L, totalMs = s.totalMs + minutes * 60_000L)
    }

    fun cancel() {
        handler.removeCallbacks(tick)
        if (_state.value.endOfSong) PlaybackBridge.player?.pauseAtEndOfMediaItems = false
        restoreVolume()
        _state.value = State()
    }

    /** Called by the service when the player paused at the end of an item. */
    fun onPausedAtEndOfItem() {
        if (_state.value.endOfSong) cancel()
    }

    private fun restoreVolume() {
        if (fading) {
            PlaybackBridge.player?.volume = 1f
            fading = false
        }
    }
}
