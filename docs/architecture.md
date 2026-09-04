# Mero — UI and Architecture Decisions

Companion to the [v1 PRD](superpowers/specs/2026-09-04-mero-v1-prd.md). The PRD
says *what* Mero does; this says *how it is built and why*.

Every decision below records the alternatives that were considered and a
**revisit trigger** — the condition under which the choice stops being correct.
None of this is permanent. It is correct for one developer, ten users, and a
feature list driven by whoever texts first.

---

## Part 1 — How the UI is built

### The structural decision everything else follows from

**The player is not a navigation destination.**

This is the single most consequential UI choice in a music app, and getting it
wrong is unrecoverable without a rewrite.

A song plays while you browse. You open Search, then an album, then Library —
the music never stops and the mini-player stays docked at the bottom, expanding
to full-screen Now Playing on tap. If Now Playing were a `NavHost` destination,
you would get:

- Pressing back from the player navigates *away from playback*, which is
  nonsense — the player is a state, not a place
- The player unmounting and remounting on every navigation
- Back-stack entries accumulating one per song

So the layout is two independent layers:

```
Scaffold(
    bottomBar = { NavigationBar }        // Home · Search · Library
) { padding ->

    NavHost(...)                          // browse destinations ONLY
                                          // home, search, library, album,
                                          // artist, playlist, settings, import
}

// OUTSIDE the NavHost, above everything, app-scoped:
PlayerSheet(state)                        // MiniPlayer  <->  NowPlaying
```

`NavHost` owns browsing. The player is an overlay anchored above it, driven by
playback state that lives at app scope. They never interact through navigation.

**Implementation:** start with `BottomSheetScaffold` and its two anchor states.
Move to a custom `AnchoredDraggable` only if the mini→full transition needs
finer control than the sheet allows — that animation is the app's signature
interaction and is worth owning eventually, but not on day one.

*Revisit trigger:* the collapse/expand transition feels cheap next to the apps
your friends left.

### Navigation — Navigation Compose, type-safe routes

Since Navigation Compose 2.8, destinations are `@Serializable` data classes
rather than interpolated strings:

```kotlin
@Serializable data class Album(val browseId: String)

navController.navigate(Album(browseId = song.albumId))
```

**Why not hand-roll it?** For three screens, a `when` over a state enum beats a
library — that was genuinely the lighter option and was considered. Mero has
nine destinations, five of which take arguments (`albumId`, `artistId`,
`playlistId`, import step, settings section), plus a back stack users expect to
behave like Android. Hand-rolling that is reimplementing a back stack, badly.
This is the rung where the library wins.

**Why typed routes over string routes?** String routes push argument names and
types into interpolated strings checked at runtime. Typed routes make a
misspelled or missing argument a compile error.

*Revisit trigger:* none expected. This is stable Android platform guidance.

### State — unidirectional, but playback is different

**Screen state:** one `ViewModel` per screen exposing `StateFlow<UiState>`,
collected with `collectAsStateWithLifecycle()`.

Specifically **not** `collectAsState()`. It keeps collecting while the app is
backgrounded — and a music app is backgrounded most of the time it is running.
Every screen would keep recomposing behind a locked screen, burning battery
during exactly the use case the app is designed for. This is a real bug in this
app, not a style rule.

**Playback state is not screen state.** It lives in the `MediaController`, which
is the source of truth and outlives every screen. It is wrapped in an
app-scoped `PlayerConnection` exposing `StateFlow`s, because at least three
places need it simultaneously: the mini-player, the full player, and the
now-playing indicator on list rows. Hanging it off any one screen's ViewModel
would mean the other two read stale state.

```
MediaController (truth)
  └─ PlayerConnection (app scope, StateFlow)
       ├─ MiniPlayer
       ├─ NowPlaying
       └─ list row indicators
```

### The rest of the UI stack

| Choice | Why it, and not the alternative |
|---|---|
| **Jetpack Compose** | Views/XML is legacy per Google's own guidance. More to the point, a music UI is continuously state-driven — position, buffering, queue, playback state all change constantly — and Compose's reactive model fits that natively where Views require manual listener-to-widget syncing. The entire reference ecosystem (InnerTune, Metrolist, RiMusic) is Compose, so patterns are borrowable. |
| **Material 3** | Gives dynamic color, correct dark theme, and accessible touch targets for free. Building a custom design system is weeks of work that ten users will not notice. |
| **Coil 3** | Compose-native `AsyncImage`, Kotlin-first, actively maintained, and shares the OkHttp client already present. Glide's Compose support is an afterthought; Picasso is unmaintained. |
| **Material You dynamic color** | One line (`dynamicDarkColorScheme`), API 31+. Each friend's install picks up their own wallpaper palette — personalization at near-zero cost. Static fallback scheme for API 28–30. |
| **Pure-black AMOLED theme** | Not decoration. These phones are AMOLED, and a music app sits on-screen with the display on. Real battery saving, one extra color scheme. |

### Visual direction

Opinionated where it counts, plain everywhere else.

**Artwork is the color source.** Album art is the only real color in a music
app. Extract an accent from the current track's artwork and tint the player
background with it. That single technique is most of what makes a player feel
alive rather than like a file browser.

**Dense browse, expressive player.** Search results and library lists are
information-dense and boring on purpose — people scan them. The Now Playing
screen is the one surface that gets to be lavish.

**Motion is where the polish budget goes.** The mini→full expansion and the
artwork crossfade on track change. Compose shared-element transitions handle
the artwork continuity between mini-player and full player.

**Deliberately skipped:** onboarding carousels, empty-state illustrations, a
custom icon set, a splash animation. Ten users who already know what the app is
and why they installed it.

---

## Part 2 — Architecture

### Shape

```
mero/
├── innertube/                 vendored, GPL-3.0, never edited
└── app/
    └── com.mero/
        ├── domain/            Song, Album, Artist — Mero's own types
        ├── data/              repositories + the adapters to innertube
        ├── playback/          MediaSessionService, resolver, controller
        └── ui/<feature>/      one package per screen
```

### Why two modules and not the standard eight

Mainstream Android guidance — and Google's own Now in Android sample — splits
into `:core:model`, `:core:data`, `:core:network`, `:core:database`, and a
`:feature:*` module per screen.

**What that buys:** enforced boundaries between teams, parallel Gradle builds,
and the ability for people to work without stepping on each other.

**What it costs:** a `build.gradle.kts` per module, dependencies re-declared
across all of them, cross-module refactoring friction, and version-catalog
maintenance multiplied by module count.

Every benefit on that list is a *team* benefit. Mero has one developer. The
boundaries can be maintained with packages and discipline, and build
parallelism is meaningless at this size.

`:innertube` is a separate module for a different and real reason: it is
**vendored code that must never be edited**. That is a genuine boundary, and a
module makes it enforceable rather than aspirational.

*Revisit trigger:* clean builds exceed roughly two minutes, or a second
developer joins.

### Why there is no `SongRepository` interface

The reflex in Android codebases is `interface SongRepository` +
`SongRepositoryImpl` + `FakeSongRepository`, justified by testability and
swappability.

There will only ever be one implementation. An interface with a single
implementation is indirection that buys nothing — every reader jumps through an
extra file to reach the code, and no one ever swaps anything.

Testability is a real concern, but the untestable thing is **the network**, not
the repository. So the seam goes exactly there:

```kotlin
fun interface SearchApi {
    suspend fun searchSongs(query: String): List<Song>
}

class SearchRepository(private val api: SearchApi)
```

Tests inject a lambda. One tiny interface at the boundary that actually needs
one, instead of mirroring every class in the app.

### Why domain models never expose innertube types — the one abstraction that earns its place

This looks like the same kind of ceremony just rejected, and the distinction is
the whole architectural philosophy:

> **Abstract where change is known to arrive. Nowhere else.**

A repository interface insulates against an implementation swap that will never
happen. The innertube adapter insulates against upstream refactors that
**happen on a schedule** — spec §9 establishes that YouTube breaks extraction
periodically and the fix arrives as an upstream sync that may reshape the
module's API.

If `Song` were upstream's type, every sync could ripple through every screen,
ViewModel and database entity. With Mero owning `Song`, an upstream refactor
touches exactly two files: `SearchRepository` and `StreamRepository`.

That is not dogma. It is insulation against a specific, scheduled, already-known
source of churn.

### Why `MediaSessionService` rather than a plain foreground service

Free, with no code: notification with transport controls, lock-screen controls,
Bluetooth and wired headset buttons, Android Auto, Google Assistant voice
control, Wear OS. Each of those is days of work to hand-roll and several are
effectively impossible to do correctly.

It is also the only playback approach that survives modern Android background
restrictions without fighting them.

**Cost:** the UI must talk to it through `MediaController`, which connects
asynchronously and returns a Guava `ListenableFuture`. Mildly awkward, and the
reason `kotlinx-coroutines-guava` is a dependency. Worth it several times over.

### Why `ResolvingDataSource` for stream URLs

**The constraint:** YouTube stream URLs expire in roughly six hours.

| Option | Verdict |
|---|---|
| Resolve the URL when a track is queued | **Broken.** A track queued now and reached seven hours later has a dead URL. Same failure on resume-after-long-pause. |
| Write a custom `DataSource` | Reimplements HTTP range requests, redirects, retry and error handling — all of which Media3 already does correctly. |
| **`ResolvingDataSource.Resolver`** | **Chosen.** A Media3 hook that exists for precisely this case. |

`ResolvingDataSource` resolves the URI at *open* time rather than enqueue time,
and re-resolves on retry. Expiry therefore self-heals: a URL that dies
mid-playback triggers a retry, the retry re-resolves, and a fresh URL arrives
without any expiry-tracking code.

Mero enqueues `mero://<videoId>` and the resolver swaps in a live CDN URL at the
last possible moment. Nothing anywhere stores a URL, which is what makes the
PRD §6 constraint structurally enforced rather than merely remembered.

### Why a hand-written DI container

| Option | Assessment |
|---|---|
| **Hilt** | KSP code generation, build-time cost, annotation ceremony. Pays off with many scopes and many developers. Neither applies. |
| **Koin** | Lighter, but still a dependency, and a missing binding fails at runtime resolution rather than compile time. |
| **`AppContainer`** | **Chosen for M1.** About ten lines, compile-time safe, zero dependencies, readable start to finish. |

The PRD names Koin; M1 deviates deliberately and the deviation is recorded
there. Swapping a manual container for Koin later is mechanical.

*Revisit trigger:* the container exceeds roughly fifteen objects, or real
scoping beyond app-and-screen becomes necessary.

### Smaller decisions

**`Result<T>` at repository boundaries, not exceptions.** Errors become values
in the type signature, so a ViewModel cannot silently forget to handle one.
Throwing would require every call site to remember a `try`/`catch`, and one
forgotten `catch` is a crash. Kotlin's stdlib `Result` and `runCatching` cover
this — no custom `Resource<T>` sealed class needed.

**Single Activity.** Compose and Navigation Compose assume it, and multiple
activities would fight the persistent-player design directly: the player would
be destroyed on every activity transition.

**No Room until M2.** M1 has nothing to persist.

---

## Part 3 — Revisit triggers

Decisions here are correct for the current constraints. When a constraint
changes, so does the answer.

| Decision | Revisit when |
|---|---|
| Two Gradle modules | Clean build > ~2 min, or a second developer joins |
| Hand-written DI container | Container > ~15 objects, or non-trivial scoping needed |
| `BottomSheetScaffold` for the player | The mini→full transition feels cheap |
| No repository interfaces | A second implementation genuinely appears |
| Single audio source (YouTube) | A second source is added — *then* build the abstraction, not before |
| Client-side extraction only | Upstream is repeatedly slow to fix breakage → evaluate the `youtubedl-android` fallback (PRD §9) |
| No shared backend | Friends actually ask for shared playlists |

---

## The thread running through all of it

Every choice above resolves the same way: **take the platform feature, take the
library that already exists, write the smallest thing that works — and abstract
only where churn is already known to be coming.**

The one place that rule bends is the innertube adapter, because there the churn
is scheduled. Everywhere else, indirection added "for flexibility" is
speculative work against a future that has not been asked for by anyone,
including the ten people this app is for.
