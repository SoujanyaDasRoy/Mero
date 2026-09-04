package com.mero.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.activity.compose.BackHandler
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
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
import com.mero.data.HomeSection
import com.mero.domain.RepeatMode
import com.mero.domain.Song
import com.mero.playback.PlayerConnection
import com.mero.ui.equalizer.EqualizerScreen
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
import com.mero.ui.playlist.AddToPlaylistSheet
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
@Serializable data class PlaylistRoute(val playlistId: String)

@Composable
fun MeroApp() {
    // UI-layer state only. Replaced by PlayerConnection over a MediaController in
    // M2 — see docs/architecture.md, "Playback state is not screen state".
    var accent by remember { mutableStateOf(MeroAccent.Violet) }
    var toggles by remember {
        mutableStateOf(
            mapOf(
                "dynamic" to false, "amoled" to false, "wifi" to true,
                "norm" to false, "silence" to false, "gapless" to true, "spatial" to false,
            ),
        )
    }

    MeroTheme(
        accent = accent,
        dynamicColor = toggles["dynamic"] == true,
        amoled = toggles["amoled"] == true,
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
                onToggle = { key, value -> toggles = toggles + (key to value) },
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
    val resolved by container.streamRepository.lastResolved.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { connection.connect(context) }

    val library = container.libraryRepository
    val audioEffects = container.audioEffects
    val likedSongs by library.liked.collectAsStateWithLifecycle(emptyList())
    val recentlyPlayed by library.recentlyPlayed.collectAsStateWithLifecycle(emptyList())
    val mostPlayed by library.mostPlayed.collectAsStateWithLifecycle(emptyList())
    val persistedQueue by library.queue.collectAsStateWithLifecycle(emptyList())
    val playlists by library.playlists.collectAsStateWithLifecycle(emptyList())
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
                .split(SEARCH_SEP)
                .filter { it.isNotBlank() },
        )
    }
    LaunchedEffect(recentSearches) {
        searchPrefs.edit().putString("recent", recentSearches.joinToString(SEARCH_SEP)).apply()
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
    var playSource by remember { mutableStateOf("Mero") }

    // Same slice LibraryScreen shows, so tapping a row queues its siblings.
    val librarySongs = when (libraryTab) {
        "Recent" -> recentlyPlayed
        "Most played" -> mostPlayed
        else -> likedSongs
    }
    var homeError by remember { mutableStateOf<String?>(null) }
    var eqEnabled by remember { mutableStateOf(true) }
    var preset by remember { mutableStateOf("Flat") }
    var bands by remember { mutableStateOf(EqPresets.presets.getValue("Flat")) }
    var preamp by remember { mutableStateOf(0.6f) }
    var crossfade by remember { mutableStateOf(0.5f) }
    var importStep by remember { mutableIntStateOf(1) }
    var importPicked by remember { mutableStateOf(setOf(0, 1, 3)) }

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

            // A failed load leaves the player IDLE, where play() does nothing —
            // which is why a second press "worked". Re-prepare once so the
            // retry is automatic, and drop the spinner either way.
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                buffering = false
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

    // The queue lives in the DB now, so the UI reads it back rather than owning it.
    LaunchedEffect(persistedQueue) { queue = persistedQueue }

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
    BackHandler(enabled = addingToPlaylist != null || overlay != null || expanded) {
        when {
            addingToPlaylist != null -> addingToPlaylist = null
            overlay != null -> overlay = null
            else -> expanded = false
        }
    }

    fun start(song: Song) {
        current = song
        positionSec = 0
        playerDurationSec = 0
        playing = true
        buffering = true
        connection.play(song)
        scope.launch { library.onPlayed(song) }
    }

    /**
     * Playing a track from a list makes the rest of that list the queue, which
     * is what gives shuffle and auto-advance something to work with.
     */
    fun playFrom(song: Song, context: List<Song>, source: String = playSource) {
        playSource = source
        start(song)
        scope.launch { library.setQueue(context.filterNot { it.id == song.id }) }
    }

    fun playNext() {
        val q = queue
        if (q.isEmpty()) return
        val next = if (shuffle) q.random() else q.first()
        start(next)
        scope.launch { library.setQueue(q.filterNot { it.id == next.id }) }
    }

    // Auto-advance when a track finishes; nothing else moves the queue along.
    LaunchedEffect(endedTick) { if (endedTick > 0) playNext() }

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

    Box(Modifier.fillMaxSize()) {

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
                            onPlayPause = {
                                connection.controller?.let { if (it.isPlaying) it.pause() else it.play() }
                            },
                            onNext = { playNext() },
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
                    var results by remember { mutableStateOf<List<Song>>(emptyList()) }
                    var searchError by remember { mutableStateOf<String?>(null) }
                    SearchScreen(
                        recentSearches = recentSearches,
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
                                container.searchRepository.search(term).fold(
                                    onSuccess = { songs -> results = songs; searchError = null },
                                    onFailure = { e -> searchError = e.message ?: e.toString() },
                                )
                            }
                        },
                        onSearch = {
                            val term = query.trim()
                            submittedQuery = term
                            if (term.isNotEmpty()) {
                                recentSearches = (listOf(term) + (recentSearches - term)).take(10)
                            }
                            scope.launch {
                                container.searchRepository.search(query).fold(
                                    onSuccess = { songs ->
                                        results = songs
                                        searchError = null
                                    },
                                    onFailure = { e ->
                                        android.util.Log.e("MeroSearch", "search failed for '$query'", e)
                                        searchError = e.message ?: e.toString()
                                    },
                                )
                            }
                        },
                        selectedTab = searchTab,
                        onTabChange = { searchTab = it },
                        results = results,
                        nowPlayingId = current?.id,
                        onSongClick = { song -> playFrom(song, results, "Search") },
                        onSongMore = { addingToPlaylist = it },
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

                composable<Library> {
                    LibraryScreen(
                        selectedTab = libraryTab,
                        onTabChange = { libraryTab = it },
                        liked = likedSongs,
                        recentlyPlayed = recentlyPlayed,
                        mostPlayed = mostPlayed,
                        playlists = playlists,
                        onOpenPlaylist = { navController.navigate(PlaylistRoute(it)) },
                        onCreatePlaylist = { name -> scope.launch { library.createPlaylist(name) } },
                        onSongMore = { addingToPlaylist = it },
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
                        crossfade = crossfade,
                        onCrossfadeChange = { crossfade = it },
                        toggles = toggles,
                        onToggle = { key, value ->
                            onToggle(key, value)
                            when (key) {
                                "norm" -> audioEffects.setNormalization(value)
                                "spatial" -> audioEffects.setSpatial(value)
                            }
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

                composable<SettingsRoute> {
                    SettingsScreen(
                        accent = accent,
                        onAccentChange = onAccentChange,
                        toggles = toggles,
                        onToggle = onToggle,
                        onEqualizerClick = { navController.navigate(Equalizer) },
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
                        playerVariant = playerVariant,
                        onPlayerVariantChange = { playerVariant = it },
                        onBack = { navController.popBackStack() },
                        contentPadding = contentPadding,
                    )
                }
            }
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
                            onPlayPause = {
                                connection.controller?.let { if (it.isPlaying) it.pause() else it.play() }
                            },
                            onPrev = {
                                positionSec = 0
                                connection.controller?.seekTo(0)
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
                            onShuffle = { shuffle = !shuffle },
                            onRepeat = {
                                repeat = when (repeat) {
                                    RepeatMode.Off -> RepeatMode.All
                                    RepeatMode.All -> RepeatMode.One
                                    RepeatMode.One -> RepeatMode.Off
                                }
                            },
                            onQueue = { overlay = "queue" },
                            onLyrics = { overlay = "lyrics" },
                            onEqualizer = {
                                expanded = false
                                navController.navigate(Equalizer)
                            },
                            onSleepTimer = { overlay = "sleep" },
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
                    onClear = { scope.launch { library.clearQueue() } },
                    onPlay = { playFrom(it, queue, playSource); overlay = null },
                    onRemove = { removed -> scope.launch { library.removeFromQueue(removed.id) } },
                    onReorder = { reordered ->
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

/** Recent-search list separator; queries never contain a newline. */
private const val SEARCH_SEP = "\n"
