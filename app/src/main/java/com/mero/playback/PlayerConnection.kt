package com.mero.playback

import android.content.ComponentName
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.mero.domain.Song
import kotlinx.coroutines.guava.await

/**
 * Holds one [MediaController] for the app's lifetime. `controller` is exposed as
 * Compose state so screens recompose once the (async) connection completes.
 *
 * Deliberately not the app-scoped StateFlow wrapper docs/architecture.md
 * describes for the finished design — this is the M1 walking-skeleton version:
 * real audio out, minimal ceremony. The richer wrapper is a mechanical
 * follow-up once more than one screen needs to read playback state directly.
 */
class PlayerConnection {

    var controller: MediaController? by mutableStateOf(null)
        private set

    suspend fun connect(context: Context) {
        if (controller != null) return
        val token = SessionToken(context, ComponentName(context, MeroPlaybackService::class.java))
        controller = MediaController.Builder(context, token).buildAsync().await()
    }

    fun play(song: Song) {
        val c = controller ?: return
        c.setMediaItem(mediaItemFor(song))
        c.prepare()
        c.play()
    }

    fun release() {
        controller?.release()
        controller = null
    }
}
