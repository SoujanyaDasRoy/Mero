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

    /**
     * Hands the player the whole queue, not one track at a time.
     *
     * This is the difference between a real timeline and a single item, and it
     * is load-bearing: with one item the player advertises no next/previous
     * command, so the notification, lock screen, Bluetooth buttons, watch and
     * car controls all collapse to play/pause, repeat and shuffle have nothing
     * to act on, and auto-advance has to be re-implemented in the composition
     * (where it stops working the moment the UI is gone). Media3 does all of
     * that natively once it can see more than one item.
     */
    fun play(songs: List<Song>, startIndex: Int) {
        val c = controller ?: return
        if (songs.isEmpty()) return
        c.setMediaItems(songs.map(::mediaItemFor), startIndex.coerceIn(songs.indices), 0L)
        c.prepare()
        c.play()
    }

    /** Appends without disturbing what is playing. */
    fun addToQueue(songs: List<Song>) {
        val c = controller ?: return
        c.addMediaItems(songs.map(::mediaItemFor))
    }

    /** Inserts immediately after the current track. */
    fun playNextInQueue(song: Song) {
        val c = controller ?: return
        c.addMediaItem(c.currentMediaItemIndex + 1, mediaItemFor(song))
    }

    fun release() {
        controller?.release()
        controller = null
    }
}
