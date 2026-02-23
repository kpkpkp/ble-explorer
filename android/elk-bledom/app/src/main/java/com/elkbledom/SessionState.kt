package com.elkbledom

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persists session state (connected devices, last preset, playback) across
 * app restarts using SharedPreferences. On relaunch the app can restore
 * connections and resume pattern playback without user interaction.
 */
class SessionState(context: Context) {

    companion object {
        const val DEFAULT_BRIDGE_URL = "http://192.168.86.22"
    }

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
        get() = prefs.getString("control_mode", "bridge") ?: "bridge"
        set(value) { prefs.edit().putString("control_mode", value).apply() }

    var bridgeUrl: String?
        get() = prefs.getString("bridge_url", DEFAULT_BRIDGE_URL)
        set(value) {
            if (value != null) prefs.edit().putString("bridge_url", value).apply()
            else prefs.edit().remove("bridge_url").apply()
        }

    var autoResumePattern: Boolean
        get() = prefs.getBoolean("auto_resume_pattern", true)
        set(value) { prefs.edit().putBoolean("auto_resume_pattern", value).apply() }

    var powerOffOnExit: Boolean
        get() = prefs.getBoolean("power_off_on_exit", false)
        set(value) { prefs.edit().putBoolean("power_off_on_exit", value).apply() }

    var stripNames: Map<String, String>
        get() {
            val json = prefs.getString("strip_names", null) ?: return emptyMap()
            return try {
                val obj = JSONObject(json)
                obj.keys().asSequence().associateWith { obj.getString(it) }
            } catch (_: Exception) { emptyMap() }
        }
        set(value) {
            val obj = JSONObject()
            value.forEach { (k, v) -> obj.put(k, v) }
            prefs.edit().putString("strip_names", obj.toString()).apply()
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
