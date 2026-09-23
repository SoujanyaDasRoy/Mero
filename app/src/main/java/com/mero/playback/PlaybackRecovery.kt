package com.mero.playback

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlaybackException
import androidx.media3.exoplayer.ExoPlayer

/** What to do about a track that failed to load. */
internal sealed interface Recovery {
    data class RetryIn(val ms: Long) : Recovery
    data object WaitForNetwork : Recovery
    data object Skip : Recovery
    data object GiveUp : Recovery
}

private val BACKOFF_MS = longArrayOf(1_000, 4_000, 10_000)

/** [failures] counts only the attempts made while online. */
internal fun recoveryFor(failures: Int, online: Boolean, hasOtherTrack: Boolean): Recovery = when {
    !online -> Recovery.WaitForNetwork
    failures <= BACKOFF_MS.size -> Recovery.RetryIn(BACKOFF_MS[(failures - 1).coerceAtLeast(0)])
    hasOtherTrack -> Recovery.Skip
    else -> Recovery.GiveUp
}

/**
 * Gets playback going again after a load fails, without anyone touching the app.
 *
 * A failed load leaves the player in an error state where it does nothing.
 * The only retry used to be in the UI: one attempt, immediately — so a network
 * drop of more than a second killed the music for good, and with the screen
 * off there was no UI to retry at all. Nothing noticed the network coming back,
 * and the app kept showing a pause button over a player that had stopped. The
 * way out people found was to close Mero and open it again.
 *
 * It lives in the service because that is what is alive when the phone is in
 * a pocket: offline, it waits for a network and resumes where it stopped;
 * online, it retries with backoff; a track that keeps failing online (removed,
 * region-blocked) is dropped from the queue so the rest still plays.
 *
 * The failing track is not always the one playing. ExoPlayer opens the next
 * track while the current one is still buffered, and that failure stopped a
 * song that had its whole file in memory. [failedIndex] finds the right one.
 */
class PlaybackRecovery(
    context: Context,
    private val player: ExoPlayer,
    /** Forget a stream URL and partial cache that may be what broke. */
    private val invalidate: (mediaId: String) -> Unit,
) : Player.Listener {

    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val failures = HashMap<String, Int>()
    private var waitingForNetwork = false
    private val retry = Runnable {
        if (player.playerError != null && player.playWhenReady) player.prepare()
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            handler.post {
                if (!waitingForNetwork) return@post
                waitingForNetwork = false
                // A network being up is not yet DNS answering; give it a moment.
                handler.postDelayed(retry, NETWORK_SETTLE_MS)
            }
        }
    }

    fun start() {
        player.addListener(this)
        runCatching { connectivity.registerDefaultNetworkCallback(networkCallback) }
    }

    fun stop() {
        player.removeListener(this)
        runCatching { connectivity.unregisterNetworkCallback(networkCallback) }
        handler.removeCallbacksAndMessages(null)
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        // Only the track that played is cleared. Clearing everything would let a
        // broken *next* track reset its own count on every retry and loop forever.
        if (playbackState == Player.STATE_READY) player.currentMediaItem?.let { failures.remove(it.mediaId) }
    }

    override fun onPlayerError(error: PlaybackException) {
        handler.removeCallbacks(retry)
        waitingForNetwork = false
        // Paused: nobody is waiting on it, and play will prepare it anyway.
        if (!player.playWhenReady || player.mediaItemCount == 0) return

        val index = failedIndex(error)
        val id = player.getMediaItemAt(index).mediaId
        val online = isOnline()
        // Offline says nothing about the URL, and the cached audio is exactly
        // what can still play — keep both until there is a network to blame.
        if (online) invalidate(id)
        val count = if (online) (failures[id] ?: 0) + 1 else failures[id] ?: 0
        failures[id] = count

        when (val next = recoveryFor(count, online, player.mediaItemCount > 1)) {
            is Recovery.RetryIn -> handler.postDelayed(retry, next.ms)
            Recovery.WaitForNetwork -> waitingForNetwork = true
            Recovery.Skip -> {
                failures.remove(id)
                player.removeMediaItem(index)
                player.prepare()
            }
            Recovery.GiveUp -> Unit
        }
    }

    @OptIn(UnstableApi::class)
    private fun failedIndex(error: PlaybackException): Int {
        val timeline = player.currentTimeline
        val periodUid = (error as? ExoPlaybackException)?.mediaPeriodId?.periodUid
        val period = periodUid?.let(timeline::getIndexOfPeriod)?.takeIf { it != C.INDEX_UNSET }
        val window = period?.let { timeline.getPeriod(it, Timeline.Period()).windowIndex }
        return (window ?: player.currentMediaItemIndex).coerceIn(0, player.mediaItemCount - 1)
    }

    private fun isOnline(): Boolean =
        connectivity.getNetworkCapabilities(connectivity.activeNetwork)
            ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true

    private companion object {
        const val NETWORK_SETTLE_MS = 1_500L
    }
}
