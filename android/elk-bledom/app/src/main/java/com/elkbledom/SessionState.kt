package com.elkbledom

import android.content.Context
import org.json.JSONArray

/**
 * Persists session state (connected devices, last preset, playback) across
 * app restarts using SharedPreferences. On relaunch the app can restore
 * connections and resume pattern playback without user interaction.
 */
class SessionState(context: Context) {

    private val prefs = context.getSharedPreferences("elk_session", Context.MODE_PRIVATE)

    var connectedAddresses: List<String>
        get() {
            val json = prefs.getString("connected_addresses", null) ?: return emptyList()
            return try {
                val arr = JSONArray(json)
                (0 until arr.length()).map { arr.getString(it) }
            } catch (_: Exception) {
                emptyList()
            }
        }
        set(value) {
            val arr = JSONArray()
            value.forEach { arr.put(it) }
            prefs.edit().putString("connected_addresses", arr.toString()).apply()
        }

    var lastPreset: String
        get() = prefs.getString("last_preset", "St. Patrick's Day") ?: "St. Patrick's Day"
        set(value) { prefs.edit().putString("last_preset", value).apply() }

    var lastLoop: Boolean
        get() = prefs.getBoolean("last_loop", true)
        set(value) { prefs.edit().putBoolean("last_loop", value).apply() }

    var wasPlaying: Boolean
        get() = prefs.getBoolean("was_playing", false)
        set(value) { prefs.edit().putBoolean("was_playing", value).apply() }

    var controlMode: String
        get() = prefs.getString("control_mode", "ble") ?: "ble"
        set(value) { prefs.edit().putString("control_mode", value).apply() }

    var bridgeUrl: String?
        get() = prefs.getString("bridge_url", null)
        set(value) {
            if (value != null) prefs.edit().putString("bridge_url", value).apply()
            else prefs.edit().remove("bridge_url").apply()
        }

    /** Snapshot current state in one call. */
    fun save(
        addresses: List<String>,
        preset: String,
        loop: Boolean,
        playing: Boolean
    ) {
        val arr = JSONArray()
        addresses.forEach { arr.put(it) }
        prefs.edit()
            .putString("connected_addresses", arr.toString())
            .putString("last_preset", preset)
            .putBoolean("last_loop", loop)
            .putBoolean("was_playing", playing)
            .apply()
    }
}
