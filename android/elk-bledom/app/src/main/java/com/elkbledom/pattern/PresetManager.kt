package com.elkbledom.pattern

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

class PresetManager(context: Context) {

    private val prefs = context.getSharedPreferences("elk_presets", Context.MODE_PRIVATE)
    private val _allPresets = MutableStateFlow<Map<String, List<PatternStep>>>(emptyMap())
    val allPresets: StateFlow<Map<String, List<PatternStep>>> = _allPresets.asStateFlow()

    init {
        load()
    }

    fun isBuiltin(name: String): Boolean = name in BUILTIN_PRESETS

    fun save(name: String, steps: List<PatternStep>) {
        val userPresets = loadUserPresets().toMutableMap()
        userPresets[name] = steps
        persistUserPresets(userPresets)
        load()
    }

    fun delete(name: String) {
        val userPresets = loadUserPresets().toMutableMap()
        userPresets.remove(name)
        persistUserPresets(userPresets)
        load()
    }

    fun rename(oldName: String, newName: String) {
        val userPresets = loadUserPresets().toMutableMap()
        val steps = userPresets[oldName] ?: _allPresets.value[oldName] ?: return
        userPresets.remove(oldName)
        userPresets[newName] = steps
        persistUserPresets(userPresets)
        load()
    }

    fun copy(sourceName: String, newName: String) {
        val steps = _allPresets.value[sourceName] ?: return
        save(newName, steps.map { it.copy() })
    }

    private fun load() {
        val merged = linkedMapOf<String, List<PatternStep>>()
        merged.putAll(BUILTIN_PRESETS)
        merged.putAll(loadUserPresets())
        _allPresets.value = merged
    }

    private fun loadUserPresets(): Map<String, List<PatternStep>> {
        val json = prefs.getString("user_presets", null) ?: return emptyMap()
        return try {
            val obj = JSONObject(json)
            val result = linkedMapOf<String, List<PatternStep>>()
            for (name in obj.keys()) {
                val stepsArr = obj.getJSONArray(name)
                val steps = mutableListOf<PatternStep>()
                for (i in 0 until stepsArr.length()) {
                    val s = stepsArr.getJSONObject(i)
                    steps.add(
                        PatternStep(
                            color = s.getString("color"),
                            brightness = s.getInt("brightness"),
                            duration = s.getDouble("duration").toFloat(),
                            transition = s.getString("transition"),
                            fadeTime = s.getDouble("fadeTime").toFloat()
                        )
                    )
                }
                result[name] = steps
            }
            result
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun persistUserPresets(userPresets: Map<String, List<PatternStep>>) {
        val obj = JSONObject()
        for ((name, steps) in userPresets) {
            val arr = JSONArray()
            for (step in steps) {
                arr.put(JSONObject().apply {
                    put("color", step.color)
                    put("brightness", step.brightness)
                    put("duration", step.duration.toDouble())
                    put("transition", step.transition)
                    put("fadeTime", step.fadeTime.toDouble())
                })
            }
            obj.put(name, arr)
        }
        prefs.edit().putString("user_presets", obj.toString()).apply()
    }
}
