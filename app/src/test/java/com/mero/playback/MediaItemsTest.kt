package com.mero.playback

import android.net.Uri
import com.mero.domain.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Pinned below targetSdk 36: Robolectric 4.14's bundled android-all jars only
// go up to API 35, and this test needs nothing from newer platform behavior —
// Uri parsing hasn't changed. Bump this once Robolectric ships API 36 support.
@Config(sdk = [34])
@RunWith(RobolectricTestRunner::class)
class MediaItemsTest {

    private val song = Song("abc123", "Kesariya", "Arijit Singh", durationSec = 268)

    @Test
    fun `media item carries the video id as media id`() {
        assertEquals("abc123", mediaItemFor(song).mediaId)
    }

    @Test
    fun `video id round-trips through the uri`() {
        val uri = mediaItemFor(song).localConfiguration!!.uri
        assertEquals("abc123", videoIdFrom(uri))
    }

    @Test
    fun `rejects a uri that is not a mero uri`() {
        assertThrows(IllegalArgumentException::class.java) {
            videoIdFrom(Uri.parse("https://example.com/abc123"))
        }
    }
}
