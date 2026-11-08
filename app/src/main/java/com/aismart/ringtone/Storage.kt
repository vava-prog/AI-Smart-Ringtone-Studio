package com.aismart.ringtone

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Một nhạc chuông đã xuất, lưu lâu dài trong bộ nhớ riêng của ứng dụng. */
data class HistoryEntry(
    val id: String,
    val title: String,
    val createdAt: Long,
    val clipMs: Long,
    val bitrateKbps: Int,
    val sizeBytes: Long,
    val fileName: String,
    val sourceUrl: String? = null
)

/** Lưu lịch sử dưới dạng JSON và giữ bản sao file M4A trong filesDir/ringtones. */
class HistoryStore(context: Context) {
    private val dir = File(context.filesDir, "ringtones").apply { mkdirs() }
    private val index = File(context.filesDir, "history.json")

    fun file(entry: HistoryEntry): File = File(dir, entry.fileName)

    @Synchronized
    fun load(): List<HistoryEntry> {
        if (!index.exists()) return emptyList()
        return try {
            val array = JSONArray(index.readText())
            val result = ArrayList<HistoryEntry>()
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                val entry = HistoryEntry(
                    id = o.getString("id"),
                    title = o.getString("title"),
                    createdAt = o.getLong("createdAt"),
                    clipMs = o.getLong("clipMs"),
                    bitrateKbps = o.getInt("bitrateKbps"),
                    sizeBytes = o.getLong("sizeBytes"),
                    fileName = o.getString("fileName"),
                    sourceUrl = o.optString("sourceUrl").takeIf { it.isNotEmpty() }
                )
                if (file(entry).exists()) result.add(entry)
            }
            result.sortedByDescending { it.createdAt }
        } catch (e: Exception) {
            emptyList()
        }
    }

    @Synchronized
    fun addFrom(
        source: File,
        title: String,
        clipMs: Long,
        bitrateKbps: Int,
        sourceUrl: String? = null
    ): HistoryEntry {
        val id = UUID.randomUUID().toString()
        val entry = HistoryEntry(
            id = id,
            title = title,
            createdAt = System.currentTimeMillis(),
            clipMs = clipMs,
            bitrateKbps = bitrateKbps,
            sizeBytes = source.length(),
            fileName = "$id.m4a",
            sourceUrl = sourceUrl
        )
        source.copyTo(file(entry), overwrite = true)
        val updated = listOf(entry) + load()
        updated.drop(MAX_ENTRIES).forEach { file(it).delete() }
        write(updated.take(MAX_ENTRIES))
        return entry
    }

    @Synchronized
    fun delete(entry: HistoryEntry) {
        file(entry).delete()
        write(load().filter { it.id != entry.id })
    }

    private fun write(entries: List<HistoryEntry>) {
        val array = JSONArray()
        entries.forEach { e ->
            array.put(
                JSONObject()
                    .put("id", e.id)
                    .put("title", e.title)
                    .put("createdAt", e.createdAt)
                    .put("clipMs", e.clipMs)
                    .put("bitrateKbps", e.bitrateKbps)
                    .put("sizeBytes", e.sizeBytes)
                    .put("fileName", e.fileName)
                    .put("sourceUrl", e.sourceUrl ?: "")
            )
        }
        val tmp = File(index.parentFile, "history.json.tmp")
        tmp.writeText(array.toString())
        if (!tmp.renameTo(index)) {
            index.writeText(array.toString())
            tmp.delete()
        }
    }

    companion object {
        const val MAX_ENTRIES = 200
    }
}

/** Cài đặt mặc định và giao diện, lưu bằng SharedPreferences. */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var bitrateKbps: Int
        get() = prefs.getInt("bitrate", 128)
        set(value) = prefs.edit().putInt("bitrate", value).apply()

    var fadeInSec: Float
        get() = prefs.getFloat("fade_in", 1f)
        set(value) = prefs.edit().putFloat("fade_in", value).apply()

    var fadeOutSec: Float
        get() = prefs.getFloat("fade_out", 2f)
        set(value) = prefs.edit().putFloat("fade_out", value).apply()

    var volumePercent: Int
        get() = prefs.getInt("volume", 100)
        set(value) = prefs.edit().putInt("volume", value).apply()

    /** 0 = theo hệ thống, 1 = sáng, 2 = tối. */
    var themeMode: Int
        get() = prefs.getInt("theme", 0)
        set(value) = prefs.edit().putInt("theme", value).apply()
}
