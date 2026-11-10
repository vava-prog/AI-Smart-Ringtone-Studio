package com.aismart.ringtone

import org.json.JSONArray
import org.json.JSONObject

/** Preset equalizer do người dùng tự lưu. */
data class CustomPreset(val name: String, val preampDb: Float, val bands: List<Float>)

object CustomPresets {
    const val MAX_PRESETS = 20
    const val MAX_NAME = 24

    fun cleanName(raw: String): String = raw.trim().replace(Regex("\\s+"), " ").take(MAX_NAME)

    fun parse(json: String?): List<CustomPreset> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(json)
            val out = ArrayList<CustomPreset>()
            for (i in 0 until array.length()) {
                val o = array.optJSONObject(i) ?: continue
                val name = cleanName(o.optString("name"))
                val bandsJson = o.optJSONArray("bands") ?: continue
                if (name.isEmpty() || bandsJson.length() != EqConfig.COUNT) continue
                val bands = List(EqConfig.COUNT) {
                    bandsJson.optDouble(it, 0.0).toFloat().coerceIn(-EqConfig.MAX_DB, EqConfig.MAX_DB)
                }
                val preamp = o.optDouble("preamp", 0.0).toFloat().coerceIn(-EqConfig.MAX_DB, EqConfig.MAX_DB)
                out.add(CustomPreset(name, preamp, bands))
            }
            out
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun toJson(list: List<CustomPreset>): String {
        val array = JSONArray()
        list.forEach { p ->
            array.put(
                JSONObject()
                    .put("name", p.name)
                    .put("preamp", p.preampDb.toDouble())
                    .put("bands", JSONArray(p.bands.map { it.toDouble() }))
            )
        }
        return array.toString()
    }

    /** Thêm hoặc thay preset cùng tên (không phân biệt hoa thường); giữ tối đa [MAX_PRESETS]. */
    fun upsert(list: List<CustomPreset>, preset: CustomPreset): List<CustomPreset> {
        val without = list.filterNot { it.name.equals(preset.name, ignoreCase = true) }
        return (without + preset).takeLast(MAX_PRESETS)
    }
}
