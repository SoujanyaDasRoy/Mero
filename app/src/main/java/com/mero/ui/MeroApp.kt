package com.mero.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Search
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
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import com.mero.data.SampleData
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
import com.mero.ui.playlist.PlaylistScreen
import com.mero.ui.search.SearchScreen
import com.mero.ui.settings.SettingsScreen
import com.mero.ui.theme.MeroAccent
import com.mero.ui.theme.MeroTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

/* ---- Navigation destinations. Browse screens only — the player is never one. ---- */

@Serializable object Home
@Serializable object SearchRoute
@Serializable object Library
@Serializable data class PlaylistRoute(val playlistId: String)
@Serializable object Import
@Serializable object Equalizer
@Serializable object SettingsRoute

@Composable
fun MeroApp() {
    // UI-layer state only. Replaced by PlayerConnection over a MediaController in
    // M2 — see docs/architecture.md, "Playback state is not screen state".
    var accent by remember { mutableStateOf(MeroAccent.Violet) }
    var toggles by remember {
        mutableStateOf(
            mapOf(
                "dynamic" to false, "amoled" to false, "wifi" to true,
                "norm" to true, "silence" to false, "gapless" to true,
            ),
        )
    }

    MeroTheme(
        accent = accent,
        dynamicColor = toggles["dynamic"] == true,
        amoled = toggles["amoled"] == true,
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
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
    val scope = rememberCoroutineScope()
    val container = remember { (context.applicationContext as MeroApplication).container }
    val connection = remember { PlayerConnection() }
    LaunchedEffect(Unit) { connection.connect(context) }
    DisposableEffect(Unit) { onDispose { connection.release() } }

    var current by remember { mutableStateOf<Song?>(null) }
    var playing by remember { mutableStateOf(false) }
    var positionSec by remember { mutableIntStateOf(0) }
    var expanded by remember { mutableStateOf(false) }
    var overlay by remember { mutableStateOf<String?>(null) }
    var liked by remember { mutableStateOf(false) }
    var shuffle by remember { mutableStateOf(false) }
    var repeat by remember { mutableStateOf(RepeatMode.Off) }
    var queue by remember { mutableStateOf(SampleData.songs.drop(1)) }
    var playerVariant by remember { mutableStateOf(PlayerVariant.Standard) }

    var query by remember { mutableStateOf("") }
    var searchTab by remember { mutableStateOf("Songs") }
    var libraryTab by remember { mutableStateOf("Playlists") }
    var eqEnabled by remember { mutableStateOf(true) }
    var preset by remember { mutableStateOf("Bass Boost") }
    var bands by remember { mutableStateOf(SampleData.eqPresets.getValue("Bass Boost")) }
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
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing = isPlaying
            }
        }
        controller.addListener(listener)
        playing = controller.isPlaying
        onDispose { controller.removeListener(listener) }
    }

    // Media3 has no continuous position stream — poll while playing, same as any
    // other player UI (system Now Playing, browser <audio> controls, etc).
    LaunchedEffect(playing, current, connection.controller) {
        while (playing && current != null) {
            positionSec = ((connection.controller?.currentPosition ?: 0L) / 1000).toInt()
            delay(500)
        }
    }

    fun play(song: Song) {
        current = song
        positionSec = 0
        connection.play(song)
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
                            progress = if (song.durationSec == 0) {
                                0f
                            } else {
                                positionSec.toFloat() / song.durationSec
                            },
                            onExpand = { expanded = true },
                            onPlayPause = {
                                connection.controller?.let { if (it.isPlaying) it.pause() else it.play() }
                            },
                            onNext = { queue.firstOrNull()?.let(::play) },
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                    NavigationBar {
                    val items = listOf(
                        NavItem("Home", Icons.Rounded.Home, Home, Home::class),
                        NavItem("Search", Icons.Rounded.Search, SearchRoute, SearchRoute::class),
                        NavItem("Library", Icons.Rounded.LibraryMusic, Library, Library::class),
                    )
                    items.forEach { (label, icon, route, routeClass) ->
                        val selected = destination?.hierarchy?.any { it.hasRoute(routeClass) } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(route) {
                                    popUpTo(Home) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(icon, contentDescription = label) },
                            label = { Text(label) },
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
                        onSongClick = ::play,
                        onPlaylistClick = { navController.navigate(PlaylistRoute(it.id)) },
                        onSettingsClick = { navController.navigate(SettingsRoute) },
                        contentPadding = contentPadding,
                    )
                }

                composable<SearchRoute> {
                    var results by remember { mutableStateOf<List<Song>>(emptyList()) }
                    var searchError by remember { mutableStateOf<String?>(null) }
                    SearchScreen(
                        query = query,
                        onQueryChange = { query = it },
                        onSearch = {
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
                        onSongClick = ::play,
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
                        onPlaylistClick = { navController.navigate(PlaylistRoute(it.id)) },
                        onImportClick = { navController.navigate(Import) },
                        onSettingsClick = { navController.navigate(SettingsRoute) },
                        contentPadding = contentPadding,
                    )
                }

                composable<PlaylistRoute> { entry ->
                    val route: PlaylistRoute = entry.toRoute()
                    val playlist = remember(route.playlistId) {
                        SampleData.playlists.firstOrNull { it.id == route.playlistId }
                            ?: SampleData.playlists.first()
                    }
                    PlaylistScreen(
                        playlist = playlist,
                        nowPlayingId = current?.id,
                        onBack = { navController.popBackStack() },
                        onSongClick = ::play,
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
                        onEnabledChange = { eqEnabled = it },
                        preset = preset,
                        onPresetChange = { name ->
                            preset = name
                            bands = SampleData.eqPresets.getValue(name)
                        },
                        bands = bands,
                        onBandChange = { index, value ->
                            bands = bands.toMutableList().also { it[index] = value }
                            preset = "Custom"
                        },
                        preamp = preamp,
                        onPreampChange = { preamp = it },
                        crossfade = crossfade,
                        onCrossfadeChange = { crossfade = it },
                        toggles = toggles,
                        onToggle = onToggle,
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
                        playerVariant = playerVariant,
                        onPlayerVariantChange = { playerVariant = it },
                        onBack = { navController.popBackStack() },
                        contentPadding = contentPadding,
                    )
                }
            }
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
                            source = "Liked songs",
                            positionSec = positionSec,
                            playing = playing,
                            liked = liked,
                            shuffle = shuffle,
                            repeat = repeat,
                            upNext = queue,
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
                            onNext = { queue.firstOrNull()?.let(::play) },
                            onSeek = {
                                val newPosSec = (it * song.durationSec).toInt()
                                positionSec = newPosSec
                                connection.controller?.seekTo(newPosSec * 1000L)
                            },
                            onLike = { liked = !liked },
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
                    onClear = { queue = emptyList() },
                    onPlay = { play(it); overlay = null },
                    onRemove = { removed -> queue = queue.filterNot { it.id == removed.id } },
                )

                "lyrics" -> LyricsSheet(
                    song = song,
                    positionSec = positionSec,
                    lines = SampleData.lyrics,
                    onClose = { overlay = null },
                    onSeek = { positionSec = it },
                )
            }
        }
    }
}

private data class NavItem(
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val route: Any,
    val routeClass: kotlin.reflect.KClass<*>,
)
