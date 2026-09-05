package com.mero.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.mero.MeroApplication
import com.mero.data.EqPresets
import com.mero.data.CodecPreference
import com.mero.data.HomeSection
import com.mero.data.titleCase
import com.mero.domain.RepeatMode
import com.mero.domain.SearchItem
import com.mero.domain.SearchResultType
import com.mero.domain.Song
import com.mero.playback.PlayerConnection
import com.mero.playback.SpatialMode
import com.mero.playback.mediaItemFor
import com.mero.ui.equalizer.EqualizerScreen
import com.mero.ui.artist.ArtistScreen
import com.mero.ui.home.HomeScreen
import com.mero.ui.importer.ImportScreen
import com.mero.ui.library.LibraryScreen
import com.mero.ui.player.LyricsSheet
import com.mero.ui.player.MiniPlayer
import com.mero.ui.player.NowPlayingScreen
import com.mero.ui.player.PlayerActions
import com.mero.ui.player.PlayerUi
import com.mero.ui.player.PlayerVariant
import com.mero.ui.player.QueueSheet
import com.mero.ui.player.SleepTimerSheet
import com.mero.ui.components.SongMenuSheet
import com.mero.ui.playlist.AddToPlaylistSheet
import com.mero.ui.playlist.ImportScreen
import com.mero.ui.playlist.PlaylistDetailScreen
import com.mero.ui.search.SearchScreen
import com.mero.ui.settings.SettingsScreen
import com.mero.ui.theme.MeroAccent
import com.mero.ui.theme.MeroTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

/* ---- Navigation destinations. Browse screens only — the player is never one. ---- */

@Serializable object Home
@Serializable object SearchRoute
@Serializable object Library
@Serializable object Import
@Serializable object Equalizer
@Serializable object SettingsRoute
@Serializable object ImportRoute
@Serializable data class PlaylistRoute(val playlistId: String)
@Serializable data class SmartPlaylistRoute(val playlistId: String)
@Serializable data class ArtistRoute(val artistId: String)

@Composable
fun MeroApp() {
    // UI-layer state only. Replaced by PlayerConnection over a MediaController in
    // M2 — see docs/architecture.md, "Playback state is not screen state".
    var accent by remember { mutableStateOf(MeroAccent.Violet) }
    var toggles by remember {
        mutableStateOf(
            mapOf(
                "dynamic" to false, "dark" to true, "amoled" to false, "wifi" to true,
                "norm" to false, "silence" to false, "gapless" to true, "spatial" to false,
                // On by default: music that stops dead at the end of a queue is
                // the more surprising behaviour of the two.
                "infinite" to true, "autopause" to false,
            ),
        )
    }

    MeroTheme(
        accent = accent,
        dynamicColor = toggles["dynamic"] == true,
        amoled = toggles["amoled"] == true,
        darkMode = toggles["dark"] != false,
    ) {
        var splashDone by remember { mutableStateOf(false) }

        Surface(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars),
            color = MaterialTheme.colorScheme.background,
        ) {
            if (!splashDone) {
                MeroSplash(onFinished = { splashDone = true })
                return@Surface
            }
            MeroContent(
                accent = accent,
                onAccentChange = { accent = it },
                toggles = toggles,
                onToggle = { key, value ->
                    toggles = when {
                        key == "infinite" && value ->
                            toggles + ("infinite" to true) + ("autopause" to false)
                        key == "autopause" && value && toggles["infinite"] == true ->
                            toggles + ("autopause" to false)
                        key == "dark" && !value ->
                            toggles + ("dark" to false) + ("amoled" to false)
                        else -> toggles + (key to value)
                    }
                },
            )
        }
    }
}

@Composable
private fun MeroContent(
    accent: MeroAccent,
    onAccentChange: (MeroAccent) -> Unit,
    toggles: Map<String, Boolean>,
    onToggle: (String, Boolean) -> Unit,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination

    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val container = remember { (context.applicationContext as MeroApplication).container }
    val connection = remember { PlayerConnection() }
    val qualityPrefs = remember {
        context.getSharedPreferences("quality", android.content.Context.MODE_PRIVATE)
    }
    var streamCodec by remember {
        mutableStateOf(
            runCatching { CodecPreference.valueOf(qualityPrefs.getString("stream", CodecPreference.OPUS.name)!!) }
                .getOrDefault(CodecPreference.OPUS),
        )
    }
    var downloadCodec by remember {
        mutableStateOf(
            runCatching { CodecPreference.valueOf(qualityPrefs.getString("download", CodecPreference.OPUS.name)!!) }
                .getOrDefault(CodecPreference.OPUS),
        )
    }
    LaunchedEffect(streamCodec) { container.streamRepository.setCodecPreference(streamCodec) }
    val downloadPrefs = remember {
        context.getSharedPreferences("downloads", android.content.Context.MODE_PRIVATE)
    }
    var downloadFolderUri by remember {
        mutableStateOf(downloadPrefs.getString("folder", null))
    }
    var downloadFolderUris by remember {
        mutableStateOf(downloadPrefs.getStringSet("folders", emptySet()).orEmpty())
    }
    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            downloadFolderUri = uri.toString()
            downloadFolderUris = downloadFolderUris + uri.toString()
            downloadPrefs.edit()
                .putString("folder", downloadFolderUri)
                .putStringSet("folders", downloadFolderUris)
                .apply()
        }
    }
    val resolved by container.streamRepository.lastResolved.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { connection.connect(context) }

    val library = container.libraryRepository
    val audioEffects = container.audioEffects
    val likedSongs by library.liked.collectAsStateWithLifecycle(emptyList())
    val recentlyPlayed by library.recentlyPlayed.collectAsStateWithLifecycle(emptyList())
    val mostPlayed by library.mostPlayed.collectAsStateWithLifecycle(emptyList())
    val persistedQueue by library.queue.collectAsStateWithLifecycle(emptyList())
    val playlists by library.playlists.collectAsStateWithLifecycle(emptyList())
    val smartPlaylists by library.smartPlaylists.collectAsStateWithLifecycle(emptyList())
    val downloadedSongs by library.downloads.collectAsStateWithLifecycle(emptyList())
    val sleepRemaining by container.sleepTimer.remainingSec.collectAsStateWithLifecycle(null)
    val sleepAfterTrack by container.sleepTimer.stopAfterTrack.collectAsStateWithLifecycle(false)

    DisposableEffect(Unit) { onDispose { connection.release() } }

    var current by remember { mutableStateOf<Song?>(null) }
    var playing by remember { mutableStateOf(false) }
    var buffering by remember { mutableStateOf(false) }
    var playerDurationSec by remember { mutableIntStateOf(0) }
    var positionSec by remember { mutableIntStateOf(0) }
    var expanded by remember { mutableStateOf(false) }
    var overlay by remember { mutableStateOf<String?>(null) }
    var liked by remember { mutableStateOf(false) }
    var shuffle by remember { mutableStateOf(false) }
    var repeat by remember { mutableStateOf(RepeatMode.Off) }
    var queue by remember { mutableStateOf(emptyList<Song>()) }
    // MediaItem only carries ids and display metadata, so the domain objects
    // the UI needs are looked up by the id the player reports back.
    var songsById by remember { mutableStateOf(emptyMap<String, Song>()) }
    var playerVariant by remember { mutableStateOf(PlayerVariant.Standard) }

    var query by remember { mutableStateOf("") }
    var searchTab by remember { mutableStateOf("Songs") }
    var libraryTab by remember { mutableStateOf("Liked") }
    // Recent searches survive the process, not just the composition: the point
    // of a recent list is the search you ran yesterday.
    val searchPrefs = remember {
        context.getSharedPreferences("search", android.content.Context.MODE_PRIVATE)
    }
    var recentSearches by remember {
        mutableStateOf(
            searchPrefs.getString("recent", "")
                .orEmpty()
                .split(NL)
                .filter { it.isNotBlank() },
        )
    }
    LaunchedEffect(recentSearches) {
        searchPrefs.edit().putString("recent", recentSearches.joinToString(NL)).apply()
    }
    // A stable handful of the home seeds, Title Cased the same way the shelf
    // headings are, so an empty search box offers somewhere to start.
    val browseTopics = remember {
        container.homeRepository.seeds.shuffled().take(14).map { it.titleCase() }
    }
    var submittedQuery by remember { mutableStateOf("") }
    var suggestions by remember { mutableStateOf(com.mero.data.Suggestions(emptyList(), emptyList())) }
    var homeSections by remember { mutableStateOf(emptyList<HomeSection>()) }
    var homeLoading by remember { mutableStateOf(true) }
    var homeLoadingMore by remember { mutableStateOf(false) }
    var seedQueue by remember { mutableStateOf(emptyList<String>()) }
    var endedTick by remember { mutableIntStateOf(0) }
    var retriedForError by remember { mutableStateOf(0L) }
    var lyrics by remember { mutableStateOf(com.mero.data.Lyrics(emptyList(), false)) }
    var lyricsLoading by remember { mutableStateOf(false) }
    var addingToPlaylist by remember { mutableStateOf<Song?>(null) }
    var menuSong by remember { mutableStateOf<Song?>(null) }
    var importSource by remember { mutableStateOf("YouTube") }
    var importUrl by remember { mutableStateOf("") }
    var importBusy by remember { mutableStateOf(false) }
    var importStatus by remember { mutableStateOf<String?>(null) }
    // The user's own Spotify app credentials. Mero ships none: a key baked into
    // an APK handed round a group of friends is a key that leaks, and it would
    // put every import in the group behind one rate limit.
    val spotifyPrefs = remember {
        context.getSharedPreferences("spotify", android.content.Context.MODE_PRIVATE)
    }
    var spotifyId by remember { mutableStateOf(spotifyPrefs.getString("id", "").orEmpty()) }
    var spotifySecret by remember { mutableStateOf(spotifyPrefs.getString("secret", "").orEmpty()) }
    fun toast(text: String) {
        android.widget.Toast.makeText(context, text, android.widget.Toast.LENGTH_SHORT).show()
    }
    // Any touch anywhere counts as "still listening" for the inactivity pause.
    var lastInteractionMs by remember { mutableLongStateOf(android.os.SystemClock.elapsedRealtime()) }
    fun markInteraction() {
        lastInteractionMs = android.os.SystemClock.elapsedRealtime()
    }
    var playSource by remember { mutableStateOf("Mero") }

    // Same slice LibraryScreen shows, so tapping a row queues its siblings.
    val librarySongs = when (libraryTab) {
        "Recent" -> recentlyPlayed
        "Most played" -> mostPlayed
        "Downloads" -> downloadedSongs
        else -> likedSongs
    }
    var homeError by remember { mutableStateOf<String?>(null) }
    var eqEnabled by remember { mutableStateOf(true) }
    var preset by remember { mutableStateOf("Flat") }
    var bands by remember { mutableStateOf(EqPresets.presets.getValue("Flat")) }
    var preamp by remember { mutableStateOf(audioEffects.preamp) }
    var booster by remember { mutableStateOf(audioEffects.booster) }
    var reverb by remember { mutableStateOf(audioEffects.reverbIntensity) }
    var spatialMode by remember { mutableStateOf(audioEffects.spatialMode) }
    var hapticIntensity by remember { mutableStateOf(container.beatHaptics.currentIntensity) }
    var crossfade by remember { mutableStateOf(0.5f) }
    var importStep by remember { mutableIntStateOf(1) }
    var importPicked by remember { mutableStateOf(setOf(0, 1, 3)) }
    var radioRequests by remember { mutableStateOf(emptySet<String>()) }

    fun refillInfinitePlayback() {
        val controller = connection.controller ?: return
        if (toggles["infinite"] != true || controller.mediaItemCount == 0) return
        if (controller.currentMediaItemIndex != controller.mediaItemCount - 1) return
        val seed = controller.currentMediaItem?.mediaId ?: return
        if (seed in radioRequests) return
        radioRequests = radioRequests + seed
        scope.launch {
            container.radioRepository.radioFor(seed).onSuccess { more ->
                if (more.isEmpty()) return@onSuccess
                songsById = songsById + more.associateBy { it.id }
                connection.addToQueue(more)
                queue = queue + more
                scope.launch { library.setQueue(queue) }
            }
        }
    }

    // Mirrors the real MediaController rather than owning playback state itself —
    // docs/architecture.md, "Playback state is not screen state" (this is the
    // walking-skeleton version of that wrapper; see PlayerConnection's own note).
    DisposableEffect(connection.controller) {
        val controller = connection.controller ?: return@DisposableEffect onDispose {}
        val listener = object : Player.Listener {
            // playWhenReady, not isPlaying: it flips the instant play/pause is
            // pressed, whereas isPlaying stays false through the (multi-second)
            // extraction + buffering, which made the button look dead.
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                playing = playWhenReady
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                buffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_ENDED) endedTick++
            }

            // The player owns the queue now, so the UI follows it rather than
            // driving it. Auto-advance, repeat and shuffle all land here.
            override fun onMediaItemTransition(item: androidx.media3.common.MediaItem?, reason: Int) {
                val id = item?.mediaId ?: return
                current = songsById[id] ?: current
                positionSec = 0
                playerDurationSec = 0
                refillInfinitePlayback()
            }

            override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
                queue = upcomingFrom(controller, songsById)
                refillInfinitePlayback()
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                repeat = when (repeatMode) {
                    Player.REPEAT_MODE_ONE -> RepeatMode.One
                    Player.REPEAT_MODE_ALL -> RepeatMode.All
                    else -> RepeatMode.Off
                }
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                shuffle = shuffleModeEnabled
            }

            // A failed load leaves the player IDLE, where play() does nothing —
            // which is why a second press "worked". Re-prepare once so the
            // retry is automatic, and drop the spinner either way.
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                buffering = false
                current?.let {
                    com.mero.playback.MediaCache.invalidateStreaming(it.id)
                    container.streamRepository.invalidate(it.id)
                }
                if (retriedForError != error.timestampMs) {
                    retriedForError = error.timestampMs
                    controller.prepare()
                    controller.play()
                    return
                }
                // Second failure on the same error: stop retrying and say so.
                // Spec §9 — extraction failure is visible and retryable, never
                // an indefinite spinner. Retrying is pressing play again.
                android.widget.Toast.makeText(
                    context,
                    "Couldn't load that track. Tap play to try again.",
                    android.widget.Toast.LENGTH_LONG,
                ).show()
            }
        }
        controller.addListener(listener)
        playing = controller.playWhenReady
        buffering = controller.playbackState == Player.STATE_BUFFERING
        onDispose { controller.removeListener(listener) }
    }

    // Every refresh reshuffles the whole seed pool, so the feed is different
    // each time rather than cycling the same shelves.
    fun loadHome() {
        homeLoading = true
        homeSections = emptyList()
        val fresh = container.homeRepository.seeds.shuffled()
        val batch = fresh.take(FIRST_BATCH)
        seedQueue = fresh.drop(FIRST_BATCH)
        scope.launch {
            container.homeRepository.sectionsFor(batch).fold(
                onSuccess = { homeSections = it; homeError = null },
                onFailure = { homeError = it.message ?: it.toString() },
            )
            homeLoading = false
        }
    }

    fun loadMoreHome() {
        if (homeLoading || homeLoadingMore || seedQueue.isEmpty()) return
        homeLoadingMore = true
        val batch = seedQueue.take(NEXT_BATCH)
        seedQueue = seedQueue.drop(NEXT_BATCH)
        scope.launch {
            container.homeRepository.sectionsFor(batch)
                .onSuccess { homeSections = homeSections + it }
            homeLoadingMore = false
        }
    }
    LaunchedEffect(Unit) { loadHome() }

    LaunchedEffect(current?.id, likedSongs) {
        liked = current?.id?.let { id -> likedSongs.any { it.id == id } } == true
    }

    // The DB copy is for surviving a cold start, not for driving playback —
    // the player's timeline is the source of truth while the app is running.
    LaunchedEffect(persistedQueue) {
        if (current == null && queue.isEmpty()) queue = persistedQueue
        songsById = songsById + persistedQueue.associateBy { it.id }
    }

    // Media3 has no continuous position stream — poll while playing, same as any
    // other player UI (system Now Playing, browser <audio> controls, etc).
    LaunchedEffect(playing, current, connection.controller) {
        while (playing && current != null) {
            val c = connection.controller
            positionSec = ((c?.currentPosition ?: 0L) / 1000).toInt()
            // C.TIME_UNSET shows up as a negative duration until the stream is
            // actually loaded, hence the guard.
            val reported = c?.duration ?: 0L
            if (reported > 0) playerDurationSec = (reported / 1000).toInt()
            delay(500)
        }
    }

    // Without this, back propagates to the NavHost while the player is open —
    // which reads as "went to the previous song" because the browse screen
    // underneath changes. The player is not a destination (architecture.md
    // Part 1), so its dismissal has to be handled here.
    BackHandler(enabled = menuSong != null || addingToPlaylist != null || overlay != null || expanded) {
        when {
            menuSong != null -> menuSong = null
            addingToPlaylist != null -> addingToPlaylist = null
            overlay != null -> overlay = null
            else -> expanded = false
        }
    }

    /**
     * Playing a track from a list makes that whole list the player's timeline,
     * starting at the tapped track. Everything the player can then do for
     * itself — advance, repeat, shuffle, expose next/previous to the
     * notification and to Bluetooth — it does, instead of the composition
     * re-implementing it.
     */
    fun warmForPlayback(song: Song) {
        scope.launch(Dispatchers.IO) {
            // Resolve the URL ahead of time, but do not partially cache the
            // current track. A 1.5 MB partial span can end around 51 seconds
            // on high-bitrate files and strand playback at that boundary.
            container.streamRepository.prefetch(song.id)
        }
    }

    fun playFrom(song: Song, context: List<Song>, source: String = playSource) {
        playSource = source
        val list = if (context.any { it.id == song.id }) context else listOf(song) + context
        songsById = songsById + list.associateBy { it.id }
        current = song
        positionSec = 0
        playerDurationSec = 0
        playing = true
        buffering = true
        markInteraction()
        warmForPlayback(song)
        connection.play(list, list.indexOfFirst { it.id == song.id })
        queue = list.drop(list.indexOfFirst { it.id == song.id } + 1)
        scope.launch {
            library.onPlayed(song)
            library.setQueue(queue)
        }
    }

    fun playNext() {
        markInteraction()
        connection.controller?.seekToNextMediaItem()
    }

    fun togglePlayback() {
        markInteraction()
        connection.controller?.let { controller ->
            when {
                controller.isPlaying -> controller.pause()
                controller.playbackState == Player.STATE_IDLE -> {
                    controller.prepare()
                    controller.play()
                }
                controller.playbackState == Player.STATE_ENDED -> {
                    controller.seekTo(controller.currentMediaItemIndex, 0L)
                    controller.play()
                }
                else -> controller.play()
            }
        }
    }

    // Resolve the next track's URL while the current one plays, so skipping
    // doesn't pay the extraction cost. StreamRepository caches the result, so
    // the actual skip is then instant.
    // Keyed on the id, not the list: `queue` is a fresh List on every DB emit,
    // so keying on it restarted this effect constantly and fired an extraction
    // each time — which is what left the playing track stuck buffering.
    val nextId = queue.firstOrNull()?.id
    LaunchedEffect(nextId) {
        if (nextId == null) return@LaunchedEffect
        delay(2_000)
        container.streamRepository.prefetch(nextId)
        // Then pull the opening bytes down too, so pressing next is instant
        // rather than merely "already knows the URL".
        withContext(Dispatchers.IO) {
            runCatching {
                com.mero.playback.MediaCache.warm(
                    container.mediaDataSourceFactory(context),
                    nextId,
                )
            }
        }
    }

    // Lyrics are fetched lazily — only when the sheet is actually open.
    LaunchedEffect(current?.id, overlay) {
        val song = current
        if (overlay != "lyrics" || song == null) return@LaunchedEffect
        lyricsLoading = true
        lyrics = container.lyricsRepository.lyricsFor(song)
        lyricsLoading = false
    }

    // The sleep timer lives at app scope so it keeps counting with the player
    // closed. When it fires, pause rather than tearing the session down.
    DisposableEffect(connection.controller) {
        container.sleepTimer.onExpired = { connection.controller?.pause() }
        onDispose { container.sleepTimer.onExpired = {} }
    }
    LaunchedEffect(endedTick) { if (endedTick > 0) container.sleepTimer.trackEnded() }

    // Infinite playback is refilled from the controller's actual timeline by
    // the listener above, so it also works after Media3 advances tracks.
    LaunchedEffect(toggles["infinite"], current?.id, queue.size) {
        if (toggles["infinite"] == true) refillInfinitePlayback()
    }

    // Stop playing to an empty room. Any touch resets the clock; the check runs
    // once a minute rather than on a timer per interaction.
    LaunchedEffect(toggles["autopause"]) {
        if (toggles["autopause"] != true) return@LaunchedEffect
        // Turning the setting back on starts a fresh idle window rather than
        // inheriting time spent while the setting was disabled.
        markInteraction()
        while (true) {
            delay(60_000)
            val idleMs = android.os.SystemClock.elapsedRealtime() - lastInteractionMs
            if (idleMs >= INACTIVITY_PAUSE_MS && connection.controller?.isPlaying == true) {
                connection.controller?.pause()
            }
        }
    }

    // The first track of a session is the one that feels slow, so resolve the
    // most recently played one before anybody presses anything.
    LaunchedEffect(Unit) {
        val warm = recentlyPlayed.firstOrNull() ?: return@LaunchedEffect
        container.streamRepository.prefetch(warm.id)
    }

    // Suggestions as you type. Debounced so a fast typist doesn't fire a
    // request per keystroke, and skipped once the query has been submitted
    // (at that point the results list is what matters).
    LaunchedEffect(query) {
        if (query.isBlank() || query == submittedQuery) {
            suggestions = com.mero.data.Suggestions(emptyList(), emptyList())
            return@LaunchedEffect
        }
        delay(150)
        container.searchRepository.suggest(query)
            .onSuccess { suggestions = it }
    }

    // Scaffold measures the bottom bar and pads the NavHost by it, so the
    // mini-player lives in that slot rather than being positioned by hand — the
    // player still sits outside the NavHost, which is the actual constraint.
    val contentPadding = PaddingValues(bottom = 0.dp)

    Box(
        Modifier
            .fillMaxSize()
            // Initial pass and no consumption: this observes touches, it does
            // not take them from whatever is underneath.
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                        markInteraction()
                    }
                }
            },
    ) {

        Scaffold(
            bottomBar = {
                Column {
                    val song = current
                    if (song != null && !expanded) {
                        MiniPlayer(
                            song = song,
                            playing = playing,
                            buffering = buffering,
                            progress = run {
                                val d = if (playerDurationSec > 0) {
                                    playerDurationSec
                                } else {
                                    song.durationSec
                                }
                                if (d == 0) 0f else (positionSec.toFloat() / d).coerceIn(0f, 1f)
                            },
                            onExpand = { expanded = true },
                            onPlayPause = { togglePlayback() },
                            onPrevious = {
                                markInteraction()
                                connection.controller?.let {
                                    if (it.currentPosition < 3_000 && it.hasPreviousMediaItem()) {
                                        it.seekToPreviousMediaItem()
                                    } else {
                                        positionSec = 0
                                        it.seekTo(0)
                                    }
                                }
                            },
                            onNext = { playNext() },
                            onQueue = { overlay = "queue" },
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                    NavigationBar {
                        val items = listOf(
                            NavItem("Home", Icons.Rounded.Home, Icons.Outlined.Home, Home, Home::class),
                            NavItem("Search", Icons.Rounded.Search, Icons.Rounded.Search, SearchRoute, SearchRoute::class),
                            NavItem("Library", Icons.Rounded.LibraryMusic, Icons.Outlined.LibraryMusic, Library, Library::class),
                            NavItem("Settings", Icons.Rounded.Settings, Icons.Outlined.Settings, SettingsRoute, SettingsRoute::class),
                        )
                        items.forEach { item ->
                            val selected =
                                destination?.hierarchy?.any { it.hasRoute(item.routeClass) } == true
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    // Re-tapping the current tab shouldn't rebuild it.
                                    if (selected) return@NavigationBarItem
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    navController.navigate(item.route) {
                                        popUpTo(Home) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    Icon(
                                        if (selected) item.selectedIcon else item.icon,
                                        contentDescription = item.label,
                                    )
                                },
                                label = { Text(item.label) },
                                alwaysShowLabel = true,
                            )
                        }
                    }
                }
            },
        ) { scaffoldPadding ->

            NavHost(
                navController = navController,
                startDestination = Home,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = scaffoldPadding.calculateBottomPadding()),
            ) {
                composable<Home> {
                    HomeScreen(
                        sections = homeSections,
                        loading = homeLoading,
                        error = homeError,
                        onRetry = { loadHome() },
                        onLoadMore = { loadMoreHome() },
                        loadingMore = homeLoadingMore,
                        onSongClick = { song, context -> playFrom(song, context, "Home") },
                        onSettingsClick = { navController.navigate(SettingsRoute) },
                        contentPadding = contentPadding,
                    )
                }

                composable<SearchRoute> {
                    var results by remember { mutableStateOf<List<SearchItem>>(emptyList()) }
                    var searchError by remember { mutableStateOf<String?>(null) }
                    fun requestSearch(term: String) {
                        scope.launch {
                            container.searchRepository.searchItems(
                                term,
                                when (searchTab) {
                                    "Albums" -> SearchResultType.Album
                                    "Artists" -> SearchResultType.Artist
                                    "Playlists" -> SearchResultType.Playlist
                                    else -> SearchResultType.Song
                                },
                            ).fold(
                                onSuccess = { items ->
                                    results = items
                                    searchError = null
                                    items.firstOrNull()?.song?.let(::warmForPlayback)
                                },
                                onFailure = { e -> searchError = e.message ?: e.toString() },
                            )
                        }
                    }
                    SearchScreen(
                        recentSearches = recentSearches,
                        browseTopics = browseTopics,
                        onRemoveRecent = { term -> recentSearches = recentSearches - term },
                        query = query,
                        onQueryChange = { query = it },
                        suggestions = suggestions.queries,
                        suggestedSongs = suggestions.songs,
                        onSuggestionClick = { term ->
                            query = term
                            submittedQuery = term
                            recentSearches = (listOf(term) + (recentSearches - term)).take(10)
                            scope.launch {
                                requestSearch(term)
                            }
                        },
                        onSearch = {
                            val term = query.trim()
                            submittedQuery = term
                            if (term.isNotEmpty()) {
                                recentSearches = (listOf(term) + (recentSearches - term)).take(10)
                            }
                            scope.launch {
                                requestSearch(query)
                            }
                        },
                        selectedTab = searchTab,
                        onTabChange = {
                            searchTab = it
                            if (submittedQuery.isNotBlank()) requestSearch(submittedQuery)
                        },
                        results = results,
                        nowPlayingId = current?.id,
                        onResultClick = { item ->
                            when (item.type) {
                                SearchResultType.Song -> item.song?.let { song ->
                                    playFrom(song, results.mapNotNull { it.song }, "Search")
                                }
                                SearchResultType.Artist -> navController.navigate(ArtistRoute(item.browseId ?: item.id))
                                SearchResultType.Album -> scope.launch {
                                    container.artistRepository.albumSongs(item.browseId ?: item.id)
                                        .onSuccess { songs -> if (songs.isNotEmpty()) playFrom(songs.first(), songs, "Album") }
                                }
                                SearchResultType.Playlist -> Unit
                            }
                        },
                        onSongMore = { menuSong = it },
                        contentPadding = contentPadding,
                    )
                    // ponytail: plain overlay, not proper SearchScreen error UI —
                    // this is a diagnostic scaffold to see the real exception, not
                    // the final error surface. Replace with SearchScreen's own
                    // error state once the actual failure is known.
                    searchError?.let { msg ->
                        androidx.compose.material3.Text(
                            "Search failed: $msg",
                            modifier = Modifier
                                .padding(16.dp)
                                .background(androidx.compose.ui.graphics.Color.Red.copy(alpha = 0.85f))
                                .padding(12.dp),
                            color = androidx.compose.ui.graphics.Color.White,
                        )
                    }
                }

                composable<ArtistRoute> { entry ->
                    val route = entry.toRoute<ArtistRoute>()
                    var artistData by remember(route.artistId) { mutableStateOf<com.mero.domain.ArtistPageData?>(null) }
                    var artistLoading by remember(route.artistId) { mutableStateOf(true) }
                    var artistError by remember(route.artistId) { mutableStateOf<String?>(null) }
                    LaunchedEffect(route.artistId) {
                        artistLoading = true
                        container.artistRepository.artist(route.artistId).fold(
                            onSuccess = { artistData = it; artistError = null },
                            onFailure = { artistError = it.message ?: it.toString() },
                        )
                        artistLoading = false
                    }
                    ArtistScreen(
                        data = artistData,
                        loading = artistLoading,
                        error = artistError,
                        onBack = { navController.popBackStack() },
                        onAlbumClick = { album ->
                            scope.launch {
                                container.artistRepository.albumSongs(album.browseId)
                                    .onSuccess { songs -> if (songs.isNotEmpty()) playFrom(songs.first(), songs, "Album") }
                            }
                        },
                        onSongClick = { song -> playFrom(song, artistData?.songs.orEmpty(), "Artist") },
                        contentPadding = contentPadding,
                    )
                }

                composable<Library> {
                    LibraryScreen(
                        selectedTab = libraryTab,
                        onTabChange = { libraryTab = it },
                        liked = likedSongs,
                        recentlyPlayed = recentlyPlayed,
                        mostPlayed = mostPlayed,
                        downloads = downloadedSongs,
                        playlists = playlists,
                        smartPlaylists = smartPlaylists,
                        onOpenPlaylist = { navController.navigate(PlaylistRoute(it)) },
                        onOpenSmartPlaylist = { navController.navigate(SmartPlaylistRoute(it)) },
                        onCreatePlaylist = { name -> scope.launch { library.createPlaylist(name) } },
                        onCreateSmartPlaylist = { name, rule, minPlays, artist ->
                            scope.launch { library.createSmartPlaylist(name, rule, minPlays, artist) }
                        },
                        onSongMore = { menuSong = it },
                        nowPlayingId = current?.id,
                        onSongClick = { song -> playFrom(song, librarySongs, libraryTab) },
                        onSettingsClick = { navController.navigate(SettingsRoute) },
                        contentPadding = contentPadding,
                    )
                }

                composable<Import> {
                    ImportScreen(
                        step = importStep,
                        onStepChange = { importStep = it },
                        picked = importPicked,
                        onTogglePick = {
                            importPicked = if (it in importPicked) {
                                importPicked - it
                            } else {
                                importPicked + it
                            }
                        },
                        onBack = { navController.popBackStack() },
                        onDone = { importStep = 1; navController.popBackStack() },
                        contentPadding = contentPadding,
                    )
                }

                composable<Equalizer> {
                    EqualizerScreen(
                        enabled = eqEnabled,
                        onEnabledChange = { eqEnabled = it; audioEffects.setEnabled(it) },
                        preset = preset,
                        onPresetChange = { name ->
                            preset = name
                            bands = EqPresets.presets.getValue(name)
                            audioEffects.setBands(bands)
                        },
                        bands = bands,
                        onBandChange = { index, value ->
                            bands = bands.toMutableList().also { it[index] = value }
                            preset = "Custom"
                            audioEffects.setBand(index, value)
                        },
                        preamp = preamp,
                        onPreampChange = { preamp = it; audioEffects.setPreamp(it) },
                        booster = booster,
                        onBoosterChange = { booster = it; audioEffects.setBooster(it) },
                        reverb = reverb,
                        onReverbChange = { reverb = it; audioEffects.setReverb(it) },
                        hapticIntensity = hapticIntensity,
                        onHapticIntensityChange = {
                            hapticIntensity = it
                            container.beatHaptics.setIntensity(it)
                        },
                        crossfade = crossfade,
                        onCrossfadeChange = { crossfade = it },
                        toggles = toggles,
                        onToggle = { key, value ->
                            onToggle(key, value)
                            when (key) {
                                "norm" -> audioEffects.setNormalization(value)
                                "spatial" -> {
                                    spatialMode = if (value) SpatialMode.Wide else SpatialMode.Off
                                    audioEffects.setSpatial(value)
                                }
                            }
                        },
                        spatialMode = spatialMode,
                        onSpatialModeChange = {
                            spatialMode = it
                            audioEffects.setSpatialMode(it)
                            onToggle("spatial", it != SpatialMode.Off)
                        },
                        spatialSupported = audioEffects.spatialSupported,
                        onBack = { navController.popBackStack() },
                        contentPadding = contentPadding,
                    )
                }

                composable<PlaylistRoute> { entry ->
                    val route: PlaylistRoute = entry.toRoute()
                    val meta by library.playlist(route.playlistId)
                        .collectAsStateWithLifecycle(null)
                    val songs by library.playlistSongs(route.playlistId)
                        .collectAsStateWithLifecycle(emptyList())

                    PlaylistDetailScreen(
                        name = meta?.name ?: "Playlist",
                        songs = songs,
                        nowPlayingId = current?.id,
                        onBack = { navController.popBackStack() },
                        onPlay = { song -> playFrom(song, songs, meta?.name ?: "Playlist") },
                        onPlayAll = {
                            songs.firstOrNull()?.let {
                                playFrom(it, songs, meta?.name ?: "Playlist")
                            }
                        },
                        onShuffle = {
                            val shuffledQueue = songs.shuffled()
                            shuffledQueue.firstOrNull()?.let {
                                shuffle = true
                                playFrom(it, shuffledQueue, meta?.name ?: "Playlist")
                            }
                        },
                        onRemove = { song ->
                            scope.launch { library.removeFromPlaylist(route.playlistId, song.id) }
                        },
                        onRename = { name ->
                            scope.launch { library.renamePlaylist(route.playlistId, name) }
                        },
                        onDelete = {
                            scope.launch { library.deletePlaylist(route.playlistId) }
                            navController.popBackStack()
                        },
                        contentPadding = contentPadding,
                    )
                }

                composable<SmartPlaylistRoute> { entry ->
                    val route: SmartPlaylistRoute = entry.toRoute()
                    val summary = smartPlaylists.firstOrNull { it.id == route.playlistId }
                    val songs by if (summary == null) {
                        kotlinx.coroutines.flow.flowOf(emptyList())
                    } else {
                        library.smartPlaylistSongs(summary)
                    }.collectAsStateWithLifecycle(emptyList())

                    PlaylistDetailScreen(
                        name = summary?.name ?: "Smart playlist",
                        songs = songs,
                        nowPlayingId = current?.id,
                        onBack = { navController.popBackStack() },
                        onPlay = { song -> playFrom(song, songs, summary?.name ?: "Smart playlist") },
                        onPlayAll = { songs.firstOrNull()?.let { playFrom(it, songs, summary?.name ?: "Smart playlist") } },
                        onShuffle = {
                            val shuffled = songs.shuffled()
                            shuffled.firstOrNull()?.let { playFrom(it, shuffled, summary?.name ?: "Smart playlist") }
                        },
                        onRemove = {},
                        onRename = {},
                        onDelete = {
                            scope.launch { library.deleteSmartPlaylist(route.playlistId) }
                            navController.popBackStack()
                        },
                        contentPadding = contentPadding,
                    )
                }

                composable<ImportRoute> {
                    ImportScreen(
                        source = importSource,
                        onSourceChange = { importSource = it; importStatus = null },
                        url = importUrl,
                        onUrlChange = { importUrl = it },
                        clientId = spotifyId,
                        onClientIdChange = {
                            spotifyId = it
                            spotifyPrefs.edit().putString("id", it).apply()
                        },
                        clientSecret = spotifySecret,
                        onClientSecretChange = {
                            spotifySecret = it
                            spotifyPrefs.edit().putString("secret", it).apply()
                        },
                        busy = importBusy,
                        status = importStatus,
                        onImport = {
                            importBusy = true
                            importStatus = "Reading the playlist..."
                            scope.launch {
                                val result = if (importSource == "YouTube") {
                                    container.importRepository.importYouTube(importUrl)
                                } else {
                                    container.importRepository.importSpotify(
                                        importUrl,
                                        spotifyId,
                                        spotifySecret,
                                    ) { done, total ->
                                        importStatus = "Matching track $done of $total..."
                                    }
                                }
                                importBusy = false
                                result.fold(
                                    onSuccess = { imported ->
                                        val id = library.createPlaylist(imported.name)
                                        library.addToPlaylist(id, imported.songs)
                                        importStatus = buildString {
                                            append("Imported ")
                                            append(imported.songs.size)
                                            append(" tracks into \"")
                                            append(imported.name)
                                            append("\".")
                                            if (imported.unmatched.isNotEmpty()) {
                                                append(NL)
                                                append(NL)
                                                append("Could not find on YouTube:")
                                                append(NL)
                                                append(imported.unmatched.joinToString(NL))
                                            }
                                        }
                                        importUrl = ""
                                    },
                                    onFailure = { importStatus = it.message ?: it.toString() },
                                )
                            }
                        },
                        onBack = { navController.popBackStack() },
                        contentPadding = contentPadding,
                    )
                }

                composable<SettingsRoute> {
                    SettingsScreen(
                        accent = accent,
                        onAccentChange = onAccentChange,
                        toggles = toggles,
                        onToggle = onToggle,
                        onEqualizerClick = { navController.navigate(Equalizer) },
                        onImportClick = { navController.navigate(ImportRoute) },
                        onSleepTimerClick = { overlay = "sleep" },
                        sleepSummary = when {
                            sleepRemaining != null -> "Active"
                            sleepAfterTrack -> "Stops at end of track"
                            else -> "Off"
                        },
                        onClearCache = {
                            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                context.cacheDir.resolve("artwork").deleteRecursively()
                                context.cacheDir.resolve("media").deleteRecursively()
                            }
                        },
                        onClearDownloads = {
                            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                container.libraryRepository.clearDownloadedMarkers()
                                com.mero.playback.MediaCache.clearExportedDownloads(
                                    context,
                                    downloadFolderUris,
                                )
                                com.mero.playback.MediaCache.clearDownloads()
                            }
                        },
                        onChooseDownloadFolder = { folderPicker.launch(null) },
                        downloadFolderSelected = downloadFolderUri != null,
                        streamCodec = streamCodec,
                        onStreamCodecChange = {
                            streamCodec = it
                            qualityPrefs.edit().putString("stream", it.name).apply()
                            container.streamRepository.setCodecPreference(it)
                        },
                        downloadCodec = downloadCodec,
                        onDownloadCodecChange = {
                            downloadCodec = it
                            qualityPrefs.edit().putString("download", it.name).apply()
                        },
                        onBatterySettingsClick = {
                            runCatching {
                                context.startActivity(
                                    android.content.Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
                                )
                            }
                        },
                        playerVariant = playerVariant,
                        onPlayerVariantChange = { playerVariant = it },
                        onBack = { navController.popBackStack() },
                        contentPadding = contentPadding,
                    )
                }
            }
        }

        menuSong?.let { song ->
            SongMenuSheet(
                song = song,
                liked = likedSongs.any { it.id == song.id },
                onPlayNext = { connection.playNextInQueue(song) },
                onAddToQueue = { connection.addToQueue(listOf(song)) },
                onAddToPlaylist = { menuSong = null; addingToPlaylist = song },
                onToggleLike = { scope.launch { library.toggleLiked(song) } },
                downloaded = downloadedSongs.any { it.id == song.id },
                onDownload = {
                    val already = downloadedSongs.any { it.id == song.id }
                    // A download takes a minute and the only feedback available
                    // without a progress UI is saying so at both ends.
                    toast(if (already) "Removing from device" else "Downloading ${song.title} to device")
                    scope.launch(Dispatchers.IO) {
                        if (already) {
                            com.mero.playback.MediaCache.removeDownload(song.id)
                            library.markDownloaded(song, false)
                        } else {
                            runCatching {
                                com.mero.playback.MediaCache.download(
                                    container.downloadDataSourceFactory(context, downloadCodec),
                                    song.id,
                                ) {}
                                downloadFolderUri?.let { folder ->
                                    com.mero.playback.MediaCache.exportDownload(
                                        context = context,
                                        folderUri = android.net.Uri.parse(folder),
                                        videoId = song.id,
                                        title = song.title,
                                        artist = song.artist,
                                    )
                                }
                            }.fold(
                                onSuccess = {
                                    library.markDownloaded(song, true)
                                    withContext(Dispatchers.Main) { toast("Downloaded ${song.title}") }
                                },
                                onFailure = {
                                    com.mero.playback.MediaCache.removeDownload(song.id)
                                    withContext(Dispatchers.Main) {
                                        toast("Download failed: ${it.message}")
                                    }
                                },
                            )
                        }
                    }
                },
                onStartRadio = {
                    scope.launch {
                        container.radioRepository.radioFor(song.id)
                            .onSuccess { playFrom(song, listOf(song) + it, "Radio") }
                    }
                },
                onGoToArtist = {
                    query = song.artist
                    submittedQuery = ""
                    navController.navigate(SearchRoute)
                },
                onShare = {
                    context.startActivity(
                        android.content.Intent.createChooser(
                            android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(
                                    android.content.Intent.EXTRA_TEXT,
                                    "${song.title} - ${song.artist}" + NL +
                                        "https://music.youtube.com/watch?v=${song.id}",
                                )
                            },
                            "Share",
                        ),
                    )
                },
                onClose = { menuSong = null },
            )
        }

        addingToPlaylist?.let { song ->
            AddToPlaylistSheet(
                song = song,
                playlists = playlists,
                onAdd = { playlistId ->
                    scope.launch { library.addToPlaylist(playlistId, song) }
                    addingToPlaylist = null
                },
                onCreateAndAdd = { name ->
                    scope.launch {
                        val id = library.createPlaylist(name)
                        library.addToPlaylist(id, song)
                    }
                    addingToPlaylist = null
                },
                onClose = { addingToPlaylist = null },
            )
        }

        /* ---- Expanded player and its sheets. Outside the NavHost by design. ---- */

        val song = current
        if (song != null) {
            // ponytail: full-screen swap with a slide. The design's signature move is
            // the artwork animating from the mini bar into the full player — that
            // needs a shared-element transition, and lands when the player is real.
            AnimatedVisibility(
                visible = expanded,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
            ) {
                Surface(Modifier.fillMaxSize()) {
                    NowPlayingScreen(
                        variant = playerVariant,
                        ui = PlayerUi(
                            song = song,
                            source = playSource,
                            positionSec = positionSec,
                            playing = playing,
                            liked = liked,
                            shuffle = shuffle,
                            repeat = repeat,
                            upNext = queue,
                            qualityLabel = resolved?.label,
                            buffering = buffering,
                            durationSec = playerDurationSec,
                        ),
                        actions = PlayerActions(
                            onCollapse = { expanded = false },
                            onPlayPause = { togglePlayback() },
                            onPrev = {
                                // Below three seconds, "previous" means the
                                // previous track; after that it means restart —
                                // the convention every music player uses.
                                connection.controller?.let {
                                    if (it.currentPosition < 3_000 && it.hasPreviousMediaItem()) {
                                        it.seekToPreviousMediaItem()
                                    } else {
                                        positionSec = 0
                                        it.seekTo(0)
                                    }
                                }
                            },
                            onNext = { playNext() },
                            onSeek = {
                                val newPosSec = (it * song.durationSec).toInt()
                                positionSec = newPosSec
                                connection.controller?.seekTo(newPosSec * 1000L)
                            },
                            onLike = {
                                current?.let { song ->
                                    scope.launch { liked = library.toggleLiked(song) }
                                }
                            },
                            onShuffle = {
                                connection.controller?.let {
                                    it.shuffleModeEnabled = !it.shuffleModeEnabled
                                }
                            },
                            onRepeat = {
                                connection.controller?.let {
                                    it.repeatMode = when (it.repeatMode) {
                                        Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                                        Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                                        else -> Player.REPEAT_MODE_OFF
                                    }
                                }
                            },
                            onQueue = { overlay = "queue" },
                            onLyrics = { overlay = "lyrics" },
                            onEqualizer = {
                                expanded = false
                                navController.navigate(Equalizer)
                            },
                            onSleepTimer = { overlay = "sleep" },
                            onMore = { menuSong = song },
                        ),
                    )
                }
            }

            when (overlay) {
                "queue" -> QueueSheet(
                    current = song,
                    positionSec = positionSec,
                    queue = queue,
                    onClose = { overlay = null },
                    // Queue edits go to the player's timeline; the DB copy
                    // follows so the queue survives a cold start.
                    onClear = {
                        connection.controller?.let { c ->
                            for (i in c.mediaItemCount - 1 downTo c.currentMediaItemIndex + 1) {
                                c.removeMediaItem(i)
                            }
                        }
                        scope.launch { library.clearQueue() }
                    },
                    onPlay = { picked ->
                        connection.controller?.let { c ->
                            val at = (c.currentMediaItemIndex + 1 until c.mediaItemCount)
                                .firstOrNull { c.getMediaItemAt(it).mediaId == picked.id }
                            if (at != null) c.seekTo(at, 0L) else playFrom(picked, queue, playSource)
                        }
                        overlay = null
                    },
                    onRemove = { removed ->
                        connection.controller?.let { c ->
                            (c.currentMediaItemIndex + 1 until c.mediaItemCount)
                                .firstOrNull { c.getMediaItemAt(it).mediaId == removed.id }
                                ?.let(c::removeMediaItem)
                        }
                        scope.launch { library.removeFromQueue(removed.id) }
                    },
                    onReorder = { reordered ->
                        connection.controller?.let { c ->
                            val base = c.currentMediaItemIndex + 1
                            for (i in c.mediaItemCount - 1 downTo base) c.removeMediaItem(i)
                            c.addMediaItems(reordered.map { mediaItemFor(it) })
                        }
                        queue = reordered
                        scope.launch { library.reorderQueue(reordered) }
                    },
                )

                "sleep" -> SleepTimerSheet(
                    remainingSec = sleepRemaining,
                    stopAfterTrack = sleepAfterTrack,
                    onPick = { minutes -> container.sleepTimer.start(minutes) },
                    onStopAfterTrack = { container.sleepTimer.stopAfterCurrentTrack() },
                    onCancel = { container.sleepTimer.cancel() },
                    onClose = { overlay = null },
                )

                "lyrics" -> LyricsSheet(
                    song = song,
                    positionSec = positionSec,
                    lines = lyrics.lines,
                    synced = lyrics.synced,
                    loading = lyricsLoading,
                    onClose = { overlay = null },
                    onSeek = { sec ->
                        positionSec = sec
                        connection.controller?.seekTo(sec * 1000L)
                    },
                )
            }
        }
    }
}

private const val FIRST_BATCH = 4
private const val NEXT_BATCH = 3

private data class NavItem(
    val label: String,
    val selectedIcon: androidx.compose.ui.graphics.vector.ImageVector,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val route: Any,
    val routeClass: kotlin.reflect.KClass<*>,
)

/**
 * The tracks after the current one. Read straight off the player's timeline in
 * index order; when shuffle is on the timeline itself is what the player will
 * follow, so this stays an honest picture of what is coming.
 */
private fun upcomingFrom(
    controller: androidx.media3.session.MediaController,
    known: Map<String, Song>,
): List<Song> = buildList {
    for (i in (controller.currentMediaItemIndex + 1) until controller.mediaItemCount) {
        val item = controller.getMediaItemAt(i)
        add(
            known[item.mediaId] ?: Song(
                id = item.mediaId,
                title = item.mediaMetadata.title?.toString().orEmpty(),
                artist = item.mediaMetadata.artist?.toString().orEmpty(),
                thumbnailUrl = item.mediaMetadata.artworkUri?.toString(),
            ),
        )
    }
}

/** Newline. Separates persisted recent searches, which never contain one. */
private const val NL = "\n"

/** One hour of no interaction. */
private const val INACTIVITY_PAUSE_MS = 60L * 60 * 1000
