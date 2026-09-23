package com.mero.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.C
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.SessionError
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.mero.MainActivity
import com.mero.MeroApplication
import com.mero.data.LibraryRepository
import com.mero.data.SettingsStore
import com.mero.domain.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.guava.future

/**
 * Free, with no code of our own: notification, lock-screen controls,
 * Bluetooth/headset buttons, Android Auto. See docs/architecture.md,
 * "Why MediaSessionService rather than a plain foreground service".
 */
class MeroPlaybackService : MediaLibraryService() {

    /**
     * A library session rather than a plain one: Android Auto can control a
     * plain session, but it can only *browse* a library one, and a car screen
     * with nothing to pick from is a remote control for whatever the phone
     * happened to be playing. See [CarBrowseTree].
     */
    private var mediaSession: MediaLibrarySession? = null

    /** Outlives the composition; the session answers buttons the UI never sees. */
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var library: LibraryRepository
    private lateinit var settings: SettingsStore

    override fun onCreate() {
        super.onCreate()
        val container = (application as MeroApplication).container
        library = container.libraryRepository
        settings = container.settings

        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(
                    container.playbackDataSourceFactory(this),
                    // Podcast episodes are mostly MP3 without a seek table, and
                    // without this ExoPlayer refuses to seek in them at all —
                    // no scrubbing through a two-hour episode, no resuming in
                    // the middle. Estimating from the bitrate is what every
                    // podcast app does.
                    DefaultExtractorsFactory().setConstantBitrateSeekingEnabled(true),
                ),
            )
            .setLoadControl(
                // Defaults wait 2.5s of buffered audio before starting. For a
                // 160 kbps stream that is pure dead time on top of extraction.
                DefaultLoadControl.Builder()
                    .setBufferDurationsMs(
                        /* minBufferMs = */ 30_000,
                        /* maxBufferMs = */ 300_000,
                        /* bufferForPlaybackMs = */ 500,
                        /* bufferForPlaybackAfterRebufferMs = */ 1_500,
                    )
                    .setPrioritizeTimeOverSizeThresholds(true)
                    .build(),
            )
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setRenderersFactory(
                // Mero's own audio stages live in here — see MeroRenderersFactory.
                MeroRenderersFactory(
                    this,
                    container.audioEffects.processor,
                    container.audioEffects.crossfeedProcessor,
                    container.audioEffects.dynamics,
                    container.audioEffects.spectrum,
                ),
            )
            .setHandleAudioBecomingNoisy(true)
            .build()

        // The chosen speed was only ever applied by the UI, so playback that
        // starts without it — a headset button on a cold start — ran at 1x
        // whatever the setting said.
        player.setPlaybackSpeed(settings.float(SettingsStore.PLAYBACK_SPEED, 1f))

        // Remember where playback stopped. Without this a headset button
        // pressed the next morning would restart the queue from the top.
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (!isPlaying) saveResumePoint(player)
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                saveResumePoint(player)
            }
        })

        mediaSession = MediaLibrarySession.Builder(this, SkipIgnoresRepeatOne(player), LibraryCallback())
            // Without this the notification is not tappable: Media3 only makes
            // it open something if the session says what to open. Tapping it
            // did nothing at all.
            //
            // FLAG_ACTIVITY_SINGLE_TOP so it returns to the running instance
            // rather than stacking a second one, which would build a fresh
            // MediaController and leave the player mid-track behind it.
            .setSessionActivity(
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, MainActivity::class.java)
                        .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = mediaSession

    override fun onDestroy() {
        mediaSession?.run {
            saveResumePoint(player)
            player.release()
            release()
        }
        mediaSession = null
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun saveResumePoint(player: Player) {
        if (player.mediaItemCount == 0) return
        settings.putInt(SettingsStore.RESUME_INDEX, player.currentMediaItemIndex)
        settings.putLong(SettingsStore.RESUME_POSITION_MS, player.currentPosition)
    }

    /**
     * Answers a play button pressed when there is nothing loaded.
     *
     * A button on a pair of earbuds does not go to the app; it goes to
     * whichever media session Android thinks is yours. If Mero has just been
     * started by that press — or has been sitting with an empty player since
     * launch — the session exists but its queue does not, and a play command
     * against an empty timeline does nothing at all. Media3 asks this callback
     * what to play in exactly that case; without it, the press is silent.
     *
     * The queue is the one the app last persisted, resumed at the track and
     * position it stopped at.
     */
    private inner class LibraryCallback : MediaLibrarySession.Callback {

        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = serviceScope.future {
            val songs = library.queue.first()
            // Failing the future is how Media3 is told there is nothing to
            // resume; returning an empty list puts the player into a state it
            // cannot play out of.
            if (songs.isEmpty()) error("No queue to resume")
            val index = settings.int(SettingsStore.RESUME_INDEX, 0).coerceIn(0, songs.lastIndex)
            MediaSession.MediaItemsWithStartPosition(
                songs.map(::mediaItemFor),
                index,
                settings.long(SettingsStore.RESUME_POSITION_MS, 0L),
            )
        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> =
            Futures.immediateFuture(LibraryResult.ofItem(folder(CarBrowseTree.ROOT, "Mero"), params))

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = serviceScope.future {
            val children: List<MediaItem> = when (val node = CarBrowseTree.nodeOf(parentId)) {
                CarBrowseTree.Node.Root -> CarBrowseTree.sections.map { folder(it.id, it.title) }
                is CarBrowseTree.Node.SectionNode -> if (node.key == "playlists") {
                    library.playlists.first().map { folder(CarBrowseTree.playlistNodeId(it.id), it.name) }
                } else {
                    songsIn(parentId).map { playable(parentId, it) }
                }
                is CarBrowseTree.Node.PlaylistNode -> songsIn(parentId).map { playable(parentId, it) }
                null -> emptyList()
            }
            LibraryResult.ofItemList(ImmutableList.copyOf(pageOf(children, page, pageSize)), params)
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String,
        ): ListenableFuture<LibraryResult<MediaItem>> = serviceScope.future {
            val song = library.song(CarBrowseTree.parse(mediaId).second)
            if (song != null) {
                LibraryResult.ofItem(mediaItemFor(song), null)
            } else {
                LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
            }
        }

        /**
         * A tap on a song in the car. It arrives as one bare id; this turns it
         * into the whole list it was tapped in, starting at that song, so the
         * car keeps playing the list instead of stopping after one track.
         */
        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
            startIndex: Int,
            startPositionMs: Long,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val single = mediaItems.singleOrNull()
            val parsed = single?.let { CarBrowseTree.parse(it.mediaId) }
            // Items that already carry an address came from the app itself —
            // leave those exactly as they are.
            if (single == null || parsed?.first == null || single.localConfiguration != null) {
                return super.onSetMediaItems(mediaSession, controller, mediaItems, startIndex, startPositionMs)
            }
            val (parent, songId) = parsed
            return serviceScope.future {
                val list = songsIn(parent!!)
                val at = list.indexOfFirst { it.id == songId }
                if (at < 0) error("That song is no longer in this list")
                MediaSession.MediaItemsWithStartPosition(list.map(::mediaItemFor), at, startPositionMs)
            }
        }

        /** "Add to queue" from the car: fill in where each song actually is. */
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> = serviceScope.future {
            mediaItems.mapNotNull { item ->
                if (item.localConfiguration != null) {
                    item
                } else {
                    library.song(CarBrowseTree.parse(item.mediaId).second)?.let(::mediaItemFor)
                }
            }.toMutableList()
        }
    }

    private suspend fun songsIn(parentId: String): List<Song> =
        when (val node = CarBrowseTree.nodeOf(parentId)) {
            is CarBrowseTree.Node.SectionNode -> when (node.key) {
                "recent" -> library.recentlyPlayed.first()
                "liked" -> library.liked.first()
                "phone" -> library.onDevice.first()
                "downloads" -> library.downloads.first()
                else -> emptyList()
            }
            is CarBrowseTree.Node.PlaylistNode -> library.playlistSongs(node.playlistId).first()
            else -> emptyList()
        }

    private fun folder(id: String, title: String): MediaItem = MediaItem.Builder()
        .setMediaId(id)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                .build(),
        )
        .build()

    /** A song as the car lists it — with the list it sits in folded into its id. */
    private fun playable(parentId: String, song: Song): MediaItem {
        val item = mediaItemFor(song)
        return item.buildUpon()
            .setMediaId(CarBrowseTree.playableId(parentId, song.id))
            .setMediaMetadata(
                item.mediaMetadata.buildUpon()
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                    .build(),
            )
            .build()
    }
}

/**
 * Makes an explicit skip mean "the next track" even under repeat-one.
 *
 * `Timeline.getNextWindowIndex` returns the *current* index when the repeat
 * mode is ONE, so `seekToNext` replays the track instead of advancing. That is
 * the behaviour auto-advance wants — it is what makes repeat-one repeat — but
 * it is not what a person pressing next means, and it left the next button
 * dead whenever repeat-one was on, in the app and in the notification alike.
 *
 * Wrapping the player rather than fixing the button: the notification, lock
 * screen, headset and car controls all call the player directly and never see
 * the app's own handlers.
 *
 * Repeat-one is restored immediately, so it still governs the thing it should:
 * what happens when the track ends on its own.
 */
private class SkipIgnoresRepeatOne(player: Player) : ForwardingPlayer(player) {

    private inline fun ignoringRepeatOne(block: () -> Unit) {
        val original = repeatMode
        if (original == Player.REPEAT_MODE_ONE) repeatMode = Player.REPEAT_MODE_ALL
        try {
            block()
        } finally {
            if (original == Player.REPEAT_MODE_ONE) repeatMode = original
        }
    }

    override fun seekToNext() = ignoringRepeatOne { super.seekToNext() }

    override fun seekToNextMediaItem() = ignoringRepeatOne { super.seekToNextMediaItem() }

    override fun seekToPrevious() = ignoringRepeatOne { super.seekToPrevious() }

    override fun seekToPreviousMediaItem() = ignoringRepeatOne { super.seekToPreviousMediaItem() }

    // hasNext/hasPrevious are what enable the notification buttons, and they
    // consult the same repeat-aware calculation.
    override fun hasNextMediaItem(): Boolean {
        if (repeatMode == Player.REPEAT_MODE_ONE) return currentMediaItemIndex < mediaItemCount - 1
        return super.hasNextMediaItem()
    }

    override fun hasPreviousMediaItem(): Boolean {
        if (repeatMode == Player.REPEAT_MODE_ONE) return currentMediaItemIndex > 0
        return super.hasPreviousMediaItem()
    }
}
