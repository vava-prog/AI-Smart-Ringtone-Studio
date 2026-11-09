package com.aismart.ringtone

import java.text.Normalizer

enum class AgentTarget { RINGTONE, NOTIFICATION, ALARM }

/** Một việc trợ lý sẽ làm; [order] quyết định thứ tự thực hiện (xuất file luôn cuối cùng). */
sealed class AgentAction(val order: Int) {
    data class SetLength(val ms: Long) : AgentAction(10)
    data class SetStart(val ms: Long) : AgentAction(11)
    data class SetRange(val startMs: Long, val endMs: Long) : AgentAction(12)
    data class Smart(val kind: PickKind?) : AgentAction(13)
    data class FadeIn(val seconds: Float) : AgentAction(20)
    data class FadeOut(val seconds: Float) : AgentAction(21)
    data class VolumeSet(val percent: Int) : AgentAction(22)
    data class VolumeAdjust(val delta: Int) : AgentAction(23)
    data class Format(val format: OutputFormat) : AgentAction(30)
    data class Bitrate(val kbps: Int) : AgentAction(31)
    data class Preset(val preset: EqPreset) : AgentAction(40)
    data class Normalize(val on: Boolean) : AgentAction(41)
    data class Limiter(val on: Boolean) : AgentAction(42)
    data class Target(val target: AgentTarget) : AgentAction(50)
    data class Search(val query: String) : AgentAction(60)
    object Export : AgentAction(70)
}

/**
 * Hiểu lệnh tiếng Việt/Anh đơn giản ngay trên máy bằng luật và từ khóa (không dùng Internet).
 * Trả về danh sách hành động đã sắp theo thứ tự thực hiện; rỗng nếu không hiểu.
 */
object AgentParser {
    private const val NUM = """\d+(?:[.,]\d+)?"""
    private const val TIME = """\d+:\d{1,2}(?:[.,]\d+)?|\d+(?:[.,]\d+)?"""
    private const val UNIT = """(?:giay|seconds?|secs?|s)"""

    private val searchRegex = Regex(
        """(?:^|\s)(?:tìm|tim|search|find)\s+(?:kiếm\s+)?(?:nhạc|nhac|bài hát|bai hat|bài|bai|music|songs?)?\s*(.+)$"""
    )
    private val minutesSeconds = Regex("""(\d+)\s*(?:phut|minutes?|min)\s*(\d+)\s*(?:giay|seconds?|secs?|s)?""")
    private val minutesOnly = Regex("""(\d+)\s*(?:phut|minutes?|min)\b""")

    private val range = Regex("""(?:tu|from)\s+($TIME)\s*$UNIT?\s*(?:den|toi|to|-+>?|>)\s*($TIME)\s*$UNIT?\b""")
    private val start = Regex("""(?:bat dau|start)\s*(?:tu|o|tai|luc|at|from)?\s*($TIME)\s*$UNIT?\b""")
    private val noFade = Regex("""(?:tat|bo|khong|no)\s+fade""")
    private val fadeIn = Regex("""(?:fade\s*in|mo dan)\s*($NUM)?\s*$UNIT?\b""")
    private val fadeOut = Regex("""(?:fade\s*out|tat dan|nho dan)\s*($NUM)?\s*$UNIT?\b""")
    private val volume = Regex("""(?:am luong|volume|vol)\s*(?:len|la|=|:|to)?\s*(\d{2,3})\s*%?""")
    private val volumeUp = Regex("""to hon|lon hon|tang am luong|louder|volume up""")
    private val volumeDown = Regex("""nho hon|giam am luong|quieter|volume down""")
    private val bitrate = Regex("""(\d{2,3})\s*kbps""")
    private val lengthColon = Regex("""(?:cat|doan|dai|do dai|length|clip)\s*(?:dai\s*)?(\d+:\d{1,2})""")
    private val lengthUnit = Regex("""(?:(?:cat|doan|dai|do dai|length|clip)\s*(?:dai\s*)?)?($NUM)\s*$UNIT\b""")

    private val chorus = Regex("""diep khuc|chorus""")
    private val peak = Regex("""cao trao|\bpeak\b|\bdrop\b""")
    private val intro = Regex("""mo dau|\bintro\b""")
    private val smart = Regex("""hay nhat|goi y|tu dong|thong minh|\bauto\b|\bbest\b|smart""")

    private val mp3 = Regex("""\bmp3\b""")
    private val wav = Regex("""\bwav\b""")
    private val m4a = Regex("""\b(?:m4a|aac)\b""")

    private val flat = Regex("""\bphang\b|\bflat\b|reset eq|khong eq""")
    private val vocal = Regex("""giong hat|\bvocals?\b""")
    private val speaker = Regex("""loa ngoai|\bloa\b|speaker""")
    private val bass = Regex("""\bbass\b""")

    private val normalizeOff = Regex("""(?:tat|bo|khong|off|huy|disable)\s+(?:chuan hoa|normali[sz]e)""")
    private val normalizeOn = Regex("""chuan hoa|normali[sz]e""")
    private val limiterOff = Regex("""(?:tat|bo|khong|off|huy|disable)\s+(?:chong re|gioi han dinh|limiter)""")
    private val limiterOn = Regex("""chong re|chong vo|gioi han dinh|limiter""")

    private val toAlarm = Regex("""bao thuc|alarm""")
    private val toNotification = Regex("""thong bao|notification""")
    private val toRingtone = Regex("""nhac chuong|ringtone""")

    private val export = Regex("""\b(?:xuat|export|render|luu|tao file)\b""")

    /** Bỏ dấu tiếng Việt, đổi về chữ thường và chuẩn hóa cách viết số phút. */
    fun normalize(input: String): String {
        val lower = input.lowercase().replace('đ', 'd')
        var text = Normalizer.normalize(lower, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
        text = minutesSeconds.replace(text) { m ->
            "${m.groupValues[1]}:${m.groupValues[2].padStart(2, '0')}"
        }
        text = minutesOnly.replace(text) { m -> "${m.groupValues[1]}:00" }
        return text
    }

    private fun seconds(token: String): Float = token.replace(',', '.').toFloat()

    private fun timeMs(token: String): Long {
        val clean = token.replace(',', '.')
        if (':' in clean) {
            val minutes = clean.substringBefore(':').toLong()
            val secs = clean.substringAfter(':').toDouble()
            return minutes * 60_000L + Math.round(secs * 1000.0)
        }
        return Math.round(clean.toDouble() * 1000.0)
    }

    fun parse(input: String): List<AgentAction> {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return emptyList()

        val found = searchRegex.find(trimmed.lowercase())
        if (found != null) {
            val query = found.groupValues[1].trim().trimEnd('.', ',', '!', '?')
            if (query.length >= 2) return listOf(AgentAction.Search(query))
        }

        var work = normalize(trimmed)
        fun consume(regex: Regex): MatchResult? {
            val m = regex.find(work) ?: return null
            work = work.replaceRange(m.range, " ".repeat(m.value.length))
            return m
        }

        val actions = ArrayList<AgentAction>()

        val rangeMatch = consume(range)
        if (rangeMatch != null) {
            actions.add(AgentAction.SetRange(timeMs(rangeMatch.groupValues[1]), timeMs(rangeMatch.groupValues[2])))
        } else {
            val startMatch = consume(start)
            if (startMatch != null) actions.add(AgentAction.SetStart(timeMs(startMatch.groupValues[1])))
        }

        if (consume(noFade) != null) {
            actions.add(AgentAction.FadeIn(0f))
            actions.add(AgentAction.FadeOut(0f))
        } else {
            val fadeInMatch = consume(fadeIn)
            if (fadeInMatch != null) {
                val raw = fadeInMatch.groupValues[1]
                val v = if (raw.isNotEmpty()) seconds(raw) else 1f
                actions.add(AgentAction.FadeIn(v.coerceIn(0f, 5f)))
            }
            val fadeOutMatch = consume(fadeOut)
            if (fadeOutMatch != null) {
                val raw = fadeOutMatch.groupValues[1]
                val v = if (raw.isNotEmpty()) seconds(raw) else 2f
                actions.add(AgentAction.FadeOut(v.coerceIn(0f, 5f)))
            }
        }

        val volumeMatch = consume(volume)
        if (volumeMatch != null) {
            actions.add(AgentAction.VolumeSet(volumeMatch.groupValues[1].toInt().coerceIn(50, 150)))
        } else if (consume(volumeUp) != null) {
            actions.add(AgentAction.VolumeAdjust(20))
        } else if (consume(volumeDown) != null) {
            actions.add(AgentAction.VolumeAdjust(-20))
        }

        val bitrateMatch = consume(bitrate)
        if (bitrateMatch != null) {
            val value = bitrateMatch.groupValues[1].toInt()
            val snapped = listOf(64, 96, 128, 192).minByOrNull { c -> kotlin.math.abs(c - value) } ?: 128
            actions.add(AgentAction.Bitrate(snapped))
        }

        val lengthMatch = consume(lengthColon) ?: consume(lengthUnit)
        if (lengthMatch != null) actions.add(AgentAction.SetLength(timeMs(lengthMatch.groupValues[1])))

        when {
            chorus.containsMatchIn(work) -> actions.add(AgentAction.Smart(PickKind.CHORUS))
            peak.containsMatchIn(work) -> actions.add(AgentAction.Smart(PickKind.PEAK))
            intro.containsMatchIn(work) -> actions.add(AgentAction.Smart(PickKind.INTRO))
            smart.containsMatchIn(work) -> actions.add(AgentAction.Smart(null))
        }

        when {
            mp3.containsMatchIn(work) -> actions.add(AgentAction.Format(OutputFormat.MP3))
            wav.containsMatchIn(work) -> actions.add(AgentAction.Format(OutputFormat.WAV))
            m4a.containsMatchIn(work) -> actions.add(AgentAction.Format(OutputFormat.M4A))
        }

        when {
            flat.containsMatchIn(work) -> actions.add(AgentAction.Preset(EqPreset.FLAT))
            vocal.containsMatchIn(work) -> actions.add(AgentAction.Preset(EqPreset.VOCAL))
            speaker.containsMatchIn(work) -> actions.add(AgentAction.Preset(EqPreset.SPEAKER))
            bass.containsMatchIn(work) -> actions.add(AgentAction.Preset(EqPreset.BASS))
        }

        when {
            normalizeOff.containsMatchIn(work) -> actions.add(AgentAction.Normalize(false))
            normalizeOn.containsMatchIn(work) -> actions.add(AgentAction.Normalize(true))
        }
        when {
            limiterOff.containsMatchIn(work) -> actions.add(AgentAction.Limiter(false))
            limiterOn.containsMatchIn(work) -> actions.add(AgentAction.Limiter(true))
        }

        when {
            toAlarm.containsMatchIn(work) -> actions.add(AgentAction.Target(AgentTarget.ALARM))
            toNotification.containsMatchIn(work) -> actions.add(AgentAction.Target(AgentTarget.NOTIFICATION))
            toRingtone.containsMatchIn(work) -> actions.add(AgentAction.Target(AgentTarget.RINGTONE))
        }

        if (export.containsMatchIn(work)) actions.add(AgentAction.Export)

        return actions.sortedBy { it.order }
    }
}
