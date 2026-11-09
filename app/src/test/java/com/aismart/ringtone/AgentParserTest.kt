package com.aismart.ringtone

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentParserTest {
    private fun parse(text: String) = AgentParser.parse(text)

    @Test
    fun vietnameseCombinedCommand() {
        assertEquals(
            listOf(
                AgentAction.SetLength(30_000),
                AgentAction.Smart(PickKind.CHORUS),
                AgentAction.Format(OutputFormat.MP3),
                AgentAction.Preset(EqPreset.BASS),
                AgentAction.Export
            ),
            parse("Chọn điệp khúc 30 giây, bass mạnh, xuất mp3")
        )
    }

    @Test
    fun englishCombinedCommand() {
        assertEquals(
            listOf(
                AgentAction.SetLength(30_000),
                AgentAction.Smart(PickKind.CHORUS),
                AgentAction.Format(OutputFormat.MP3),
                AgentAction.Preset(EqPreset.BASS),
                AgentAction.Export
            ),
            parse("Pick the chorus, 30 seconds, bass boost, export mp3")
        )
    }

    @Test
    fun rangeAndFadeOut() {
        assertEquals(
            listOf(AgentAction.SetRange(45_000, 75_000), AgentAction.FadeOut(3f)),
            parse("từ 0:45 đến 1:15, fade out 3 giây")
        )
    }

    @Test
    fun startOnly() {
        assertEquals(listOf<AgentAction>(AgentAction.SetStart(30_000)), parse("bắt đầu từ 0:30"))
    }

    @Test
    fun minutesAndSecondsLength() {
        assertEquals(listOf<AgentAction>(AgentAction.SetLength(80_000)), parse("cắt 1 phút 20 giây"))
    }

    @Test
    fun searchReturnsOnlySearch() {
        assertEquals(
            listOf<AgentAction>(AgentAction.Search("piano nhẹ nhàng")),
            parse("Tìm nhạc piano nhẹ nhàng")
        )
        assertEquals(
            listOf<AgentAction>(AgentAction.Search("bass house")),
            parse("find music bass house")
        )
    }

    @Test
    fun normalizeAndLimiter() {
        assertEquals(
            listOf(AgentAction.Normalize(true), AgentAction.Limiter(true)),
            parse("chuẩn hóa âm lượng và chống rè")
        )
        assertEquals(listOf<AgentAction>(AgentAction.Normalize(false)), parse("tắt chuẩn hóa"))
    }

    @Test
    fun volumeAbsoluteAndRelative() {
        assertEquals(listOf<AgentAction>(AgentAction.VolumeSet(120)), parse("âm lượng 120%"))
        assertEquals(listOf<AgentAction>(AgentAction.VolumeSet(150)), parse("volume 200"))
        assertEquals(listOf<AgentAction>(AgentAction.VolumeAdjust(20)), parse("to hơn chút"))
        assertEquals(listOf<AgentAction>(AgentAction.VolumeAdjust(-20)), parse("nhỏ hơn"))
    }

    @Test
    fun fadesAndNoFade() {
        assertEquals(
            listOf(AgentAction.FadeIn(2f), AgentAction.FadeOut(4f)),
            parse("fade in 2 giây fade out 4 giây")
        )
        assertEquals(
            listOf(AgentAction.FadeIn(0f), AgentAction.FadeOut(0f)),
            parse("tắt fade")
        )
        assertEquals(listOf<AgentAction>(AgentAction.FadeIn(1f)), parse("fade in"))
    }

    @Test
    fun bitrateSnapsToChoice() {
        assertEquals(listOf<AgentAction>(AgentAction.Bitrate(192)), parse("chất lượng 200 kbps"))
        assertEquals(listOf<AgentAction>(AgentAction.Bitrate(128)), parse("128 kbps"))
    }

    @Test
    fun targetAndFormat() {
        assertEquals(
            listOf(
                AgentAction.Format(OutputFormat.WAV),
                AgentAction.Target(AgentTarget.ALARM),
                AgentAction.Export
            ),
            parse("xuất wav cho báo thức")
        )
    }

    @Test
    fun presetsAndGenericSmartPick() {
        assertEquals(listOf<AgentAction>(AgentAction.Preset(EqPreset.VOCAL)), parse("giọng hát rõ"))
        assertEquals(listOf<AgentAction>(AgentAction.Preset(EqPreset.SPEAKER)), parse("tối ưu cho loa ngoài"))
        assertEquals(listOf<AgentAction>(AgentAction.Smart(null)), parse("gợi ý đoạn hay nhất"))
        assertEquals(listOf<AgentAction>(AgentAction.Smart(PickKind.INTRO)), parse("chọn đoạn mở đầu"))
    }

    @Test
    fun unknownOrEmptyGivesNothing() {
        assertTrue(parse("").isEmpty())
        assertTrue(parse("   ").isEmpty())
        assertTrue(parse("xin chào bạn").isEmpty())
    }
}
