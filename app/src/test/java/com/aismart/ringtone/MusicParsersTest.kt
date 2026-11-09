package com.aismart.ringtone

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicParsersTest {

    @Test
    fun allowsAdaptation_rejectsNoDerivatives() {
        assertTrue(MusicParsers.allowsAdaptation("by"))
        assertTrue(MusicParsers.allowsAdaptation("by-nc-sa"))
        assertTrue(MusicParsers.allowsAdaptation("https://creativecommons.org/licenses/by-nc/4.0/"))
        assertTrue(MusicParsers.allowsAdaptation("cc0"))
        assertFalse(MusicParsers.allowsAdaptation("by-nd"))
        assertFalse(MusicParsers.allowsAdaptation("by-nc-nd"))
        assertFalse(MusicParsers.allowsAdaptation("https://creativecommons.org/licenses/by-nd/3.0/"))
        assertFalse(MusicParsers.allowsAdaptation(""))
        assertFalse(MusicParsers.allowsAdaptation(null))
    }

    @Test
    fun licenseLabelFromUrl_formatsCcLicenses() {
        assertEquals("CC BY-NC 3.0", MusicParsers.licenseLabelFromUrl("http://creativecommons.org/licenses/by-nc/3.0/"))
        assertEquals("CC0", MusicParsers.licenseLabelFromUrl("https://creativecommons.org/publicdomain/zero/1.0/"))
        assertEquals("CC", MusicParsers.licenseLabelFromUrl(null))
    }

    @Test
    fun openverse_parsesAndFiltersResults() {
        val json = """{"result_count":3,"results":[
          {"id":"a1","title":"Piano Dream","creator":"Ann","license":"by-sa","license_version":"4.0",
           "license_url":"https://creativecommons.org/licenses/by-sa/4.0/","url":"https://cdn.example/a.mp3",
           "foreign_landing_url":"https://example.org/a","duration":95000,"filetype":"mp3"},
          {"id":"b2","title":"No derivs","creator":"Bob","license":"by-nd","license_version":"4.0",
           "url":"https://cdn.example/b.mp3","duration":1000},
          {"id":"c3","title":"No url","creator":"Cy","license":"by","license_version":"4.0"}
        ]}"""
        val tracks = MusicParsers.openverse(json)
        assertEquals(1, tracks.size)
        val t = tracks[0]
        assertEquals("ov:a1", t.id)
        assertEquals("CC BY-SA 4.0", t.license)
        assertEquals(95_000L, t.durationMs)
        assertEquals("https://cdn.example/a.mp3", t.audioUrl)
        assertEquals("https://example.org/a", t.pageUrl)
        assertTrue(t.canUse)
    }

    @Test
    fun jamendo_usesDownloadOnlyWhenAllowed() {
        val json = """{"headers":{"status":"success"},"results":[
          {"id":"11","name":"Song","artist_name":"Art","duration":120,
           "license_ccurl":"http://creativecommons.org/licenses/by-nc/3.0/",
           "audio":"https://s.jamendo/stream.mp3","audiodownload":"https://s.jamendo/dl.mp3",
           "audiodownload_allowed":true,"shareurl":"https://www.jamendo.com/track/11"},
          {"id":"12","name":"Locked","artist_name":"Art","duration":60,
           "license_ccurl":"http://creativecommons.org/licenses/by/3.0/",
           "audio":"https://s.jamendo/s2.mp3","audiodownload":"https://s.jamendo/d2.mp3",
           "audiodownload_allowed":false},
          {"id":"13","name":"ND","artist_name":"Art","duration":60,
           "license_ccurl":"http://creativecommons.org/licenses/by-nd/3.0/",
           "audio":"https://s.jamendo/s3.mp3","audiodownload_allowed":true}
        ]}"""
        val tracks = MusicParsers.jamendo(json)
        assertEquals(2, tracks.size)
        assertEquals("https://s.jamendo/dl.mp3", tracks[0].audioUrl)
        assertEquals(120_000L, tracks[0].durationMs)
        assertEquals("CC BY-NC 3.0", tracks[0].license)
        assertNull(tracks[1].audioUrl)
        assertEquals("https://s.jamendo/s2.mp3", tracks[1].previewUrl)
        assertFalse(tracks[1].canUse)
        assertEquals("https://www.jamendo.com/track/12", tracks[1].pageUrl)
    }

    @Test
    fun archive_parsesDocsWithArrayFieldsAndPicksMp3() {
        val docs = """{"response":{"numFound":2,"docs":[
          {"identifier":"song-1","title":"Old Tune","creator":["Zed","Other"],
           "licenseurl":"https://creativecommons.org/licenses/by/4.0/"},
          {"identifier":"song-2","title":"Locked","creator":"Q",
           "licenseurl":"https://creativecommons.org/licenses/by-nc-nd/4.0/"}
        ]}}"""
        val tracks = MusicParsers.archiveDocs(docs)
        assertEquals(1, tracks.size)
        assertEquals("Zed", tracks[0].artist)
        assertEquals("https://archive.org/details/song-1", tracks[0].pageUrl)
        assertTrue(tracks[0].canUse)

        val meta = """{"files":[{"name":"cover.jpg","format":"JPEG"},
          {"name":"folder/My Song.mp3","format":"VBR MP3"},{"name":"x.ogg","format":"Ogg Vorbis"}]}"""
        val file = MusicParsers.archiveAudioFile(meta, "song-1")
        assertEquals("https://archive.org/download/song-1/folder/My%20Song.mp3" to "mp3", file)
        assertNull(MusicParsers.archiveAudioFile("""{"files":[{"name":"a.jpg"}]}""", "x"))
    }

    @Test
    fun deezerChart_parsesTitlesAndArtists() {
        val json = """{"data":[{"id":1,"title":"Hit","artist":{"name":"Star"}},{"id":2,"title":"Solo"}],"total":2}"""
        val list = MusicParsers.deezerChart(json)
        assertEquals(listOf(TrendingTrack("Hit", "Star"), TrendingTrack("Solo", "")), list)
    }
}
