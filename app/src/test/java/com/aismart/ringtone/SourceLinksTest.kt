package com.aismart.ringtone

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SourceLinksTest {

    @Test
    fun parse_recognizesYoutubeVariants() {
        assertEquals(
            SourceLinks.Link("https://www.youtube.com/watch?v=abc", SourceLinks.Platform.YOUTUBE),
            SourceLinks.parse("https://www.youtube.com/watch?v=abc")
        )
        assertEquals(SourceLinks.Platform.YOUTUBE, SourceLinks.parse("https://youtu.be/abc")?.platform)
        assertEquals(SourceLinks.Platform.YOUTUBE, SourceLinks.parse("https://music.youtube.com/watch?v=1")?.platform)
        assertEquals(SourceLinks.Platform.YOUTUBE, SourceLinks.parse("https://m.youtube.com/shorts/xyz")?.platform)
    }

    @Test
    fun parse_recognizesTiktokVariants() {
        assertEquals(SourceLinks.Platform.TIKTOK, SourceLinks.parse("https://www.tiktok.com/@a/video/1")?.platform)
        assertEquals(SourceLinks.Platform.TIKTOK, SourceLinks.parse("https://vm.tiktok.com/ZM123/")?.platform)
    }

    @Test
    fun parse_addsHttpsWhenSchemeIsMissing() {
        assertEquals("https://youtu.be/abc", SourceLinks.parse("youtu.be/abc")?.url)
    }

    @Test
    fun parse_extractsUrlFromSharedText() {
        val link = SourceLinks.parse("Xem nè https://www.tiktok.com/@a/video/1. Hay quá!")
        assertEquals("https://www.tiktok.com/@a/video/1", link?.url)
        assertEquals(SourceLinks.Platform.TIKTOK, link?.platform)
    }

    @Test
    fun parse_rejectsOtherHostsAndLookalikes() {
        assertNull(SourceLinks.parse("https://evil.com/youtube.com"))
        assertNull(SourceLinks.parse("https://notyoutube.com/x"))
        assertNull(SourceLinks.parse("https://example.com/tiktok.com"))
    }

    @Test
    fun parse_rejectsEmptyAndPlainText() {
        assertNull(SourceLinks.parse(""))
        assertNull(SourceLinks.parse("   "))
        assertNull(SourceLinks.parse(null))
        assertNull(SourceLinks.parse("hello world"))
    }

    @Test
    fun extractUrl_findsFirstLinkAndTrimsPunctuation() {
        assertEquals("https://youtu.be/a", SourceLinks.extractUrl("(https://youtu.be/a), cảm ơn"))
        assertNull(SourceLinks.extractUrl("không có link"))
        assertNull(SourceLinks.extractUrl(null))
    }
}
