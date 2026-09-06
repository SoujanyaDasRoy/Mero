package com.mero.data

import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FormatSelectionTest {

    private fun opus(itag: Int, bitrate: Int) =
        AudioFormat(itag, "https://example/$itag", "audio/webm; codecs=\"opus\"", bitrate)

    private val allFormats = listOf(
        opus(249, 50_000),
        opus(250, 70_000),
        opus(251, 160_000),
        AudioFormat(140, "https://example/140", "audio/mp4; codecs=\"mp4a.40.2\"", 128_000),
    )

    @Test
    fun `high picks itag 251`() {
        assertEquals(251, selectAudioFormat(allFormats, Quality.HIGH)?.itag)
    }

    @Test
    fun `medium picks itag 250`() {
        assertEquals(250, selectAudioFormat(allFormats, Quality.MEDIUM)?.itag)
    }

    @Test
    fun `low picks itag 249`() {
        assertEquals(249, selectAudioFormat(allFormats, Quality.LOW)?.itag)
    }

    @Test
    fun `falls back to highest bitrate audio when preferred itag is absent`() {
        val onlyAac = listOf(allFormats.last())
        assertEquals(140, selectAudioFormat(onlyAac, Quality.HIGH)?.itag)
    }

    @Test
    fun `ignores video formats`() {
        val withVideo = allFormats + AudioFormat(137, "https://example/137", "video/mp4", 2_000_000)
        assertEquals(251, selectAudioFormat(withVideo, Quality.HIGH)?.itag)
    }

    @Test
    fun `returns null when no audio formats exist`() {
        val videoOnly = listOf(AudioFormat(137, "https://example/137", "video/mp4", 2_000_000))
        assertNull(selectAudioFormat(videoOnly, Quality.HIGH))
    }

    @Test
    fun `returns null for empty list`() {
        assertNull(selectAudioFormat(emptyList(), Quality.HIGH))
    }

    @Test
    fun `isUrlExpired detects expired URL based on expire query parameter`() {
        val nowSec = 1_700_000_000L
        val expiredUrl = "https://rr---sn.googlevideo.com/videoplayback?expire=1700000050&id=123"
        val validUrl = "https://rr---sn.googlevideo.com/videoplayback?expire=1700020000&id=123"
        val noExpireParam = "https://example.com/audio.webm"

        Assert.assertTrue(isUrlExpired(expiredUrl, thresholdSec = 120, nowSec = nowSec))
        Assert.assertFalse(isUrlExpired(validUrl, thresholdSec = 120, nowSec = nowSec))
        Assert.assertFalse(isUrlExpired(noExpireParam, thresholdSec = 120, nowSec = nowSec))
    }
}
