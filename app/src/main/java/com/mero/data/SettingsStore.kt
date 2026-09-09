package com.mero.data

import android.content.Context
import android.content.SharedPreferences

/**
 * The preferences that should outlive the process.
 *
 * These lived in `remember` inside the composition, which meant every cold
 * start silently threw them away: a user who set light mode and a peach accent
 * reopened Mero to violet-on-dark, with no indication they had ever chosen
 * anything. Appearance is the most visible kind of setting to lose.
 *
 * SharedPreferences rather than DataStore: this is a handful of scalars read
 * once at startup and written on a tap, and the app already keeps its Spotify
 * credentials and recent searches here. A second persistence mechanism for
 * eight booleans would be ceremony.
 */
class SettingsStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun boolean(key: String, default: Boolean): Boolean = prefs.getBoolean(key, default)

    fun putBoolean(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }

    fun string(key: String, default: String): String = prefs.getString(key, default) ?: default

    /** Whether anything was ever stored, as opposed to falling back to a default. */
    fun has(key: String): Boolean = prefs.contains(key)

    fun putString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    /** Equalizer band gains, stored as one comma-separated row. */
    fun ints(key: String, default: List<Int>): List<Int> {
        val raw = prefs.getString(key, null) ?: return default
        val parsed = raw.split(",").mapNotNull { it.trim().toIntOrNull() }
        return if (parsed.size == default.size) parsed else default
    }

    fun putInts(key: String, value: List<Int>) {
        prefs.edit().putString(key, value.joinToString(",")).apply()
    }

    fun int(key: String, default: Int): Int = prefs.getInt(key, default)

    fun putInt(key: String, value: Int) {
        prefs.edit().putInt(key, value).apply()
    }

    fun long(key: String, default: Long): Long = prefs.getLong(key, default)

    fun putLong(key: String, value: Long) {
        prefs.edit().putLong(key, value).apply()
    }

    fun float(key: String, default: Float): Float = prefs.getFloat(key, default)

    fun putFloat(key: String, value: Float) {
        prefs.edit().putFloat(key, value).apply()
    }

    /**
     * Reads every toggle in one pass, falling back to each default.
     *
     * Defaults are supplied by the caller rather than duplicated here so that
     * "what Mero does out of the box" stays in one place.
     */
    fun toggles(defaults: Map<String, Boolean>): Map<String, Boolean> =
        defaults.mapValues { (key, default) -> boolean("toggle_$key", default) }

    fun putToggle(key: String, value: Boolean) = putBoolean("toggle_$key", value)

    companion object {
        const val ACCENT = "accent"
        const val THEME_MODE = "theme_mode"
        const val PLAYER_VARIANT = "player_variant"
        const val EQ_ENABLED = "eq_enabled"
        const val EQ_PRESET = "eq_preset"
        const val EQ_BANDS = "eq_bands"

        /** Equalizer settings are stored per output route, not globally. */
        fun bandsKey(route: String) = "eq_bands_$route"
        fun presetKey(route: String) = "eq_preset_$route"
        fun preampKey(route: String) = "eq_preamp_$route"
        const val PREAMP = "preamp"
        const val BOOSTER = "booster"
        const val REVERB = "reverb"
        const val SPATIAL_MODE = "spatial_mode"
        /**
         * Where playback had got to, so a headset button pressed hours later
         * carries on rather than starting the queue again.
         */
        const val PLAYBACK_SPEED = "speed"
        const val RESUME_INDEX = "resume_index"
        const val RESUME_POSITION_MS = "resume_position_ms"
        const val STREAM_CODEC = "stream_codec"
        const val DOWNLOAD_CODEC = "download_codec"
    }
}
