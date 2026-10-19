@file:Suppress("DEPRECATION") // Virtualizer is deprecated on Android 13+ but still the only cross-version option.

package com.auramusic.app.player

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import androidx.core.content.edit
import com.auramusic.app.data.AuraPrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Equalizer, bass boost, virtualizer and loudness, bound to the player's audio session.
 * Settings are persisted and re-applied every time the playback service starts.
 */
object AudioEffects {
    data class UserPreset(val name: String, val levels: List<Int>)

    data class State(
        val available: Boolean = false,
        val enabled: Boolean = true,
        val bandFrequenciesHz: List<Int> = emptyList(),
        val bandLevels: List<Int> = emptyList(),
        val minLevel: Int = -1500,
        val maxLevel: Int = 1500,
        val devicePresets: List<String> = emptyList(),
        val userPresets: List<UserPreset> = emptyList(),
        val preset: String = "Custom",
        val bass: Int = 0,          // 0..1000
        val virtualizer: Int = 0,   // 0..1000
        val loudness: Int = 0,      // 0..1000 → 0..10 dB gain
        val bassSupported: Boolean = false,
        val virtualizerSupported: Boolean = false,
        val loudnessSupported: Boolean = false
    )

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var loudness: LoudnessEnhancer? = null
    private var sessionId = 0

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    @Synchronized
    fun attach(audioSessionId: Int) {
        if (audioSessionId <= 0 || audioSessionId == sessionId) return
        releaseEffects()
        sessionId = audioSessionId
        equalizer = runCatching { Equalizer(0, audioSessionId) }.getOrNull()
        bassBoost = runCatching { BassBoost(0, audioSessionId) }.getOrNull()
        virtualizer = runCatching { Virtualizer(0, audioSessionId) }.getOrNull()
        loudness = runCatching { LoudnessEnhancer(audioSessionId) }.getOrNull()

        val eq = equalizer
        val bands = runCatching { eq?.numberOfBands?.toInt() ?: 0 }.getOrDefault(0)
        val range = runCatching { eq?.bandLevelRange }.getOrNull() ?: shortArrayOf(-1500, 1500)
        val freqs = List(bands) { i -> runCatching { eq!!.getCenterFreq(i.toShort()) / 1000 }.getOrDefault(0) }
        val presets = runCatching { List(eq?.numberOfPresets?.toInt() ?: 0) { eq!!.getPresetName(it.toShort()).trim { c -> c.isWhitespace() || c == '\u0000' } } }.getOrDefault(emptyList())

        val saved = loadSaved()
        val levels = List(bands) { i -> saved.optJSONArray("levels")?.optInt(i, 0) ?: 0 }
            .map { it.coerceIn(range[0].toInt(), range[1].toInt()) }

        _state.value = State(
            available = eq != null && bands > 0,
            enabled = saved.optBoolean("enabled", true),
            bandFrequenciesHz = freqs,
            bandLevels = levels,
            minLevel = range[0].toInt(),
            maxLevel = range[1].toInt(),
            devicePresets = presets,
            userPresets = parseUserPresets(saved.optJSONArray("userPresets")),
            preset = saved.optString("preset", "Custom"),
            bass = saved.optInt("bass", 0).coerceIn(0, 1000),
            virtualizer = saved.optInt("virtualizer", 0).coerceIn(0, 1000),
            loudness = saved.optInt("loudness", 0).coerceIn(0, 1000),
            bassSupported = bassBoost != null && runCatching { bassBoost!!.strengthSupported }.getOrDefault(false),
            virtualizerSupported = virtualizer != null && runCatching { virtualizer!!.strengthSupported }.getOrDefault(false),
            loudnessSupported = loudness != null
        )
        applyAll()
    }

    @Synchronized
    fun detach() {
        releaseEffects()
        sessionId = 0
        _state.value = _state.value.copy(available = false)
    }

    private fun releaseEffects() {
        runCatching { equalizer?.release() }
        runCatching { bassBoost?.release() }
        runCatching { virtualizer?.release() }
        runCatching { loudness?.release() }
        equalizer = null; bassBoost = null; virtualizer = null; loudness = null
    }

    private fun applyAll() {
        val s = _state.value
        runCatching { equalizer?.enabled = s.enabled }
        s.bandLevels.forEachIndexed { i, level -> runCatching { equalizer?.setBandLevel(i.toShort(), level.toShort()) } }
        runCatching { bassBoost?.setStrength(s.bass.toShort()); bassBoost?.enabled = s.enabled && s.bass > 0 }
        runCatching { virtualizer?.setStrength(s.virtualizer.toShort()); virtualizer?.enabled = s.enabled && s.virtualizer > 0 }
        runCatching { loudness?.setTargetGain(s.loudness); loudness?.enabled = s.enabled && s.loudness > 0 }
    }

    fun setEnabled(enabled: Boolean) = update { it.copy(enabled = enabled) }

    fun setBand(index: Int, level: Int) = update { s ->
        if (index !in s.bandLevels.indices) s
        else s.copy(bandLevels = s.bandLevels.toMutableList().also { it[index] = level.coerceIn(s.minLevel, s.maxLevel) }, preset = "Custom")
    }

    fun setBass(value: Int) = update { it.copy(bass = value.coerceIn(0, 1000)) }
    fun setVirtualizer(value: Int) = update { it.copy(virtualizer = value.coerceIn(0, 1000)) }
    fun setLoudness(value: Int) = update { it.copy(loudness = value.coerceIn(0, 1000)) }

    fun useDevicePreset(name: String) {
        val eq = equalizer ?: return
        val index = _state.value.devicePresets.indexOf(name)
        if (index < 0) return
        runCatching { eq.usePreset(index.toShort()) }
        val levels = List(_state.value.bandLevels.size) { i -> runCatching { eq.getBandLevel(i.toShort()).toInt() }.getOrDefault(0) }
        update { it.copy(bandLevels = levels, preset = name) }
    }

    fun useUserPreset(name: String) {
        val preset = _state.value.userPresets.firstOrNull { it.name == name } ?: return
        update { s ->
            s.copy(bandLevels = List(s.bandLevels.size) { i -> preset.levels.getOrElse(i) { 0 }.coerceIn(s.minLevel, s.maxLevel) }, preset = name)
        }
    }

    fun saveUserPreset(name: String): Boolean {
        val clean = name.trim().take(24)
        if (clean.isBlank() || clean in _state.value.devicePresets || clean == "Flat") return false
        update { s ->
            val list = s.userPresets.filterNot { it.name.equals(clean, true) } + UserPreset(clean, s.bandLevels)
            s.copy(userPresets = list, preset = clean)
        }
        return true
    }

    fun deleteUserPreset(name: String) = update { s ->
        s.copy(userPresets = s.userPresets.filterNot { it.name == name }, preset = if (s.preset == name) "Custom" else s.preset)
    }

    fun reset() = update { s -> s.copy(bandLevels = List(s.bandLevels.size) { 0 }, bass = 0, virtualizer = 0, loudness = 0, preset = "Flat") }

    @Synchronized
    private fun update(transform: (State) -> State) {
        _state.value = transform(_state.value)
        applyAll()
        persist()
    }

    private fun persist() {
        val s = _state.value
        val obj = JSONObject()
            .put("enabled", s.enabled)
            .put("levels", JSONArray(s.bandLevels))
            .put("preset", s.preset)
            .put("bass", s.bass)
            .put("virtualizer", s.virtualizer)
            .put("loudness", s.loudness)
            .put("userPresets", JSONArray().also { arr ->
                s.userPresets.forEach { arr.put(JSONObject().put("name", it.name).put("levels", JSONArray(it.levels))) }
            })
        AuraPrefs.raw().edit { putString("eq_state", obj.toString()) }
    }

    private fun loadSaved(): JSONObject =
        runCatching { JSONObject(AuraPrefs.raw().getString("eq_state", "{}") ?: "{}") }.getOrDefault(JSONObject())

    private fun parseUserPresets(arr: JSONArray?): List<UserPreset> {
        if (arr == null) return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val levels = o.optJSONArray("levels") ?: JSONArray()
                add(UserPreset(o.optString("name"), List(levels.length()) { levels.optInt(it) }))
            }
        }.filter { it.name.isNotBlank() }
    }
}
