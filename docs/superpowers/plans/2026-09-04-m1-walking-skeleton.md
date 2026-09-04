# Mero M1 — Walking Skeleton Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A user types a song name, sees YouTube Music results, taps one, and audio plays through a background media service with lock-screen controls.

**Architecture:** Two Gradle modules — `:innertube` (vendored from upstream via git subtree, never edited) and `:app` (everything else, packaged by feature). Playback runs in a `MediaSessionService`; the Compose UI drives it through a `MediaController`. Stream URLs are never stored — a `ResolvingDataSource.Resolver` swaps a custom `mero://<videoId>` URI for a live CDN URL at open time.

**Tech Stack:** Kotlin · Jetpack Compose + Material 3 · Media3 (ExoPlayer + Session) · Ktor (transitive from `:innertube`) · kotlinx-serialization · Coil 3

**Spec:** `docs/superpowers/specs/2026-09-04-mero-v1-prd.md`

## Global Constraints

- **minSdk 28** (Android 9). Required by `DynamicsProcessing` in M4; set now to avoid a later migration. **targetSdk 36.**
- **No Google sign-in, ever.** All InnerTube requests are anonymous. Spec §3.1. Do not add an auth header, cookie jar, or account picker in any task.
- **Never persist a stream URL.** They expire in roughly six hours. Persist `videoId` only. Spec §6.
- **Mero is licensed GPL-3.0** — see Task 2, which explains why this is not optional.
- Language: Kotlin only. No Java sources.
- Commits end with:
  `Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>`

### Deliberate deviation from the spec

The PRD names **Koin** for DI. M1 uses a hand-written `AppContainer` instead — roughly ten lines, no dependency. M1's object graph is three objects; Koin earns its place when the graph grows in M2, and swapping a manual container for Koin later is mechanical. Do not add Koin in M1.

### Version policy

Do not hand-write version numbers into `libs.versions.toml` from this document. Task 1 generates the scaffold from Android Studio's current project wizard, which pins the AGP/Kotlin/Compose/core versions itself. For the four extra dependencies, resolve the current release at add-time from `maven.google.com` or `search.maven.org`. This plan names *artifacts*, never versions.

---

## File Structure

| File | Responsibility |
|---|---|
| `settings.gradle.kts` | Register `:app` and `:innertube` |
| `gradle/libs.versions.toml` | Version catalog |
| `app/build.gradle.kts` | App module config, dependencies |
| `app/src/main/AndroidManifest.xml` | Permissions, service declaration |
| `innertube/` | Vendored upstream subtree — **never edited** |
| `app/…/MeroApp.kt` | `Application` + `AppContainer` (manual DI) |
| `app/…/MainActivity.kt` | Single activity, Compose host |
| `app/…/domain/Song.kt` | Domain model — the app's own type, not upstream's |
| `app/…/data/FormatSelection.kt` | Pure: pick best audio format. **Tested** |
| `app/…/data/SearchRepository.kt` | Adapter: innertube search → `List<Song>` |
| `app/…/data/StreamRepository.kt` | Adapter: `videoId` → stream URL |
| `app/…/playback/MediaItems.kt` | Pure: `videoId` ⟷ `mero://` URI. **Tested** |
| `app/…/playback/StreamResolver.kt` | `ResolvingDataSource.Resolver` implementation |
| `app/…/playback/MeroPlaybackService.kt` | `MediaSessionService` + ExoPlayer |
| `app/…/ui/search/SearchViewModel.kt` | Search state |
| `app/…/ui/search/SearchScreen.kt` | Search UI |

Package root: `com.mero`

---

## A note on testing in this plan

Three pieces of M1 are pure functions with real edge cases, and those get
strict TDD: **format selection**, **URI round-tripping**, and **response
mapping**. They are where a bug hides silently.

The rest of M1 — a Compose screen, a bound service, an ExoPlayer wiring — is
integration whose only honest verification is running it and hearing sound.
Those tasks end in explicit **manual verification steps** with stated expected
results. Per spec §11, glue code does not get ceremonial tests. Do not write a
mock-heavy unit test that asserts ExoPlayer was called; it verifies nothing and
breaks on every refactor.

---

## Task 1: Project scaffold

**Files:**
- Create: whole Android project skeleton
- Modify: `app/build.gradle.kts`, `gradle/libs.versions.toml`, `.gitignore`

**Interfaces:**
- Consumes: nothing
- Produces: a buildable `:app` module, package `com.mero`, `MeroApp : Application` registered in the manifest

- [ ] **Step 1: Generate the project**

In Android Studio: **New Project → Empty Activity (Compose)**.

- Name: `Mero`
- Package name: `com.mero`
- Language: Kotlin
- Minimum SDK: **API 28**
- Build configuration language: Kotlin DSL (`build.gradle.kts`)

Generate it into the existing repo root (`Mero/`). The `docs/` directory and
`.git` already exist — keep them. If the wizard refuses a non-empty directory,
generate to a temp path and move the files in, preserving `docs/` and `.git`.

- [ ] **Step 2: Set targetSdk and confirm minSdk**

In `app/build.gradle.kts`, inside `android { defaultConfig { … } }`:

```kotlin
minSdk = 28
targetSdk = 36
```

- [ ] **Step 3: Add the extra dependencies**

Resolve current versions per the Version Policy above, then add to
`gradle/libs.versions.toml` and reference them in `app/build.gradle.kts`:

```
androidx.media3:media3-exoplayer
androidx.media3:media3-session
androidx.media3:media3-datasource
io.coil-kt.coil3:coil-compose
io.coil-kt.coil3:coil-network-okhttp
androidx.lifecycle:lifecycle-viewmodel-compose
org.jetbrains.kotlinx:kotlinx-coroutines-guava
```

`kotlinx-coroutines-guava` is required in Task 7 — `MediaController.Builder`
returns a Guava `ListenableFuture`, and `.await()` on it comes from this
artifact.

- [ ] **Step 4: Create the Application class**

Create `app/src/main/java/com/mero/MeroApp.kt`:

```kotlin
package com.mero

import android.app.Application

class MeroApp : Application() {
    val container: AppContainer by lazy { AppContainer() }
}

class AppContainer
```

`AppContainer` is empty for now; Tasks 4 and 6 add fields to it.

- [ ] **Step 5: Register it and add permissions**

In `app/src/main/AndroidManifest.xml`, add above `<application>`:

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

And on the `<application>` tag: `android:name=".MeroApp"`

- [ ] **Step 6: Build and run**

Run: `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`

Install and launch on a device or emulator. Expected: the wizard's default
"Hello Android" screen appears without crashing.

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "chore: scaffold Android project

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

## Task 2: Vendor the innertube module

**Files:**
- Create: `innertube/` (subtree), `LICENSE`, `innertube/VENDORED.md`
- Modify: `settings.gradle.kts`, `app/build.gradle.kts`

**Interfaces:**
- Consumes: Task 1's build
- Produces: `:innertube` module on the classpath. Its exact public API is **discovered in Step 4** and recorded for Task 4 to consume.

> ### License consequence — read before running anything
>
> Upstream `z-huang/InnerTune` is **GPL-3.0**. Vendoring its source into this
> repository makes **Mero a GPL-3.0 work**. This is compatible with the spec's
> "open source, non-commercial" intent, but it is a binding consequence, not a
> formality: Mero must ship a GPL-3.0 `LICENSE` file and keep upstream's
> copyright notices intact.
>
> **Step 1 verifies the license before any code is copied.** If upstream has
> relicensed, stop and report rather than proceeding on this plan's assumption.

- [ ] **Step 1: Verify upstream license and module path**

```bash
git clone --depth=50 https://github.com/z-huang/InnerTune.git /tmp/innertune
ls /tmp/innertune
cat /tmp/innertune/LICENSE | head -20
ls /tmp/innertune/innertube
```

Expected: a top-level `innertube/` directory exists, and `LICENSE` reads
GNU General Public License v3.

**If either expectation fails, stop and report.** Do not improvise a different
module path or a different upstream — the whole maintenance story in spec §9
depends on this specific module being re-syncable.

- [ ] **Step 2: Split the subdirectory into its own history**

`git subtree add` takes an entire source repo, not a subdirectory of one. To
vendor only the `innertube` module and keep it re-syncable, first split it out
upstream-side:

```bash
cd /tmp/innertune
git subtree split --prefix=innertube -b innertube-only
```

- [ ] **Step 3: Add it as a subtree**

From the Mero repo root:

```bash
git remote add innertube-upstream /tmp/innertune
git fetch innertube-upstream innertube-only
git subtree add --prefix=innertube innertube-upstream innertube-only --squash
```

Record how to re-sync later. Create `innertube/VENDORED.md`:

```markdown
# Vendored — do not edit

Source: https://github.com/z-huang/InnerTune — `innertube/` module (GPL-3.0)

Local edits will be lost on the next sync and will cause merge conflicts.
Anything Mero needs to change belongs in `:app`, wrapped around this module.

## Re-syncing when YouTube extraction breaks

    git clone https://github.com/z-huang/InnerTune.git /tmp/innertune
    cd /tmp/innertune && git subtree split --prefix=innertube -b innertube-only
    cd <mero repo>
    git fetch innertube-upstream innertube-only
    git subtree pull --prefix=innertube innertube-upstream innertube-only --squash
```

- [ ] **Step 4: Discover the module's public API**

The next tasks call into this module, so its real signatures must be known
before writing code against them. Read these and write down what you find:

```bash
cat innertube/build.gradle.kts
find innertube/src -name "*.kt" | head -40
```

Record, in the Task 4 scratch notes or a comment on the PR:

1. The module's Gradle **id** as declared in its `build.gradle.kts` — Step 5
   needs it, and it may not be literally `innertube`.
2. The **search entry point** — likely an object with a `search(...)` method.
   Note its exact name, parameters, filter/type arguments, and return type
   (upstream commonly returns Kotlin `Result<T>`).
3. The **player entry point** — the method taking a `videoId` and returning a
   response containing `streamingData.adaptiveFormats`.
4. The **format type's** field names — Task 3 needs the exact spelling of the
   itag, url, mimeType, and bitrate fields.
5. Which **Ktor engine**, if any, the module already depends on.

This is a read-and-record step, not a placeholder: Tasks 3–6 define Mero's own
types exactly, and only this one third-party boundary is discovered rather than
assumed.

- [ ] **Step 5: Register the module**

In `settings.gradle.kts`:

```kotlin
include(":app")
include(":innertube")
```

In `app/build.gradle.kts` dependencies:

```kotlin
implementation(project(":innertube"))
```

If Step 4 revealed the module has no bundled Ktor engine, add one:

```kotlin
implementation("io.ktor:ktor-client-okhttp:<resolve current>")
```

Apply the `kotlin-serialization` plugin to `:app` if the module's public types
require it at the call site.

- [ ] **Step 6: Add the GPL-3.0 license**

Download the GPL-3.0 text to `LICENSE` at the repo root. In `README.md`, state
that Mero is GPL-3.0 and that `innertube/` is vendored from `z-huang/InnerTune`
under the same license.

- [ ] **Step 7: Verify it compiles**

Run: `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`

Compilation failures here are almost always a missing Ktor engine or
serialization plugin — fix in `:app`, **never** by editing `innertube/`.

- [ ] **Step 8: Commit**

```bash
git add -A
git commit -m "feat: vendor innertube module as git subtree, license GPL-3.0

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

## Task 3: Domain model and format selection

**Files:**
- Create: `app/src/main/java/com/mero/domain/Song.kt`
- Create: `app/src/main/java/com/mero/data/FormatSelection.kt`
- Test: `app/src/test/java/com/mero/data/FormatSelectionTest.kt`

**Interfaces:**
- Consumes: nothing (deliberately — pure Kotlin, no innertube types)
- Produces:
  - `data class Song(val id: String, val title: String, val artist: String, val durationSec: Int, val thumbnailUrl: String?)`
  - `data class AudioFormat(val itag: Int, val url: String, val mimeType: String, val bitrate: Int)`
  - `enum class Quality { HIGH, MEDIUM, LOW }`
  - `fun selectAudioFormat(formats: List<AudioFormat>, quality: Quality): AudioFormat?`

This task takes no innertube types on purpose. Mero's domain model stays
independent of the vendored module so an upstream refactor cannot ripple past
the adapter in Task 4.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/mero/data/FormatSelectionTest.kt`:

```kotlin
package com.mero.data

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
}
```

- [ ] **Step 2: Run it and confirm it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.mero.data.FormatSelectionTest"`
Expected: FAIL — unresolved reference `AudioFormat` / `selectAudioFormat`.

- [ ] **Step 3: Write the domain model**

Create `app/src/main/java/com/mero/domain/Song.kt`:

```kotlin
package com.mero.domain

data class Song(
    val id: String,
    val title: String,
    val artist: String,
    val durationSec: Int,
    val thumbnailUrl: String?,
)
```

- [ ] **Step 4: Write the minimal implementation**

Create `app/src/main/java/com/mero/data/FormatSelection.kt`:

```kotlin
package com.mero.data

data class AudioFormat(
    val itag: Int,
    val url: String,
    val mimeType: String,
    val bitrate: Int,
)

enum class Quality(val preferredItag: Int) {
    HIGH(251),
    MEDIUM(250),
    LOW(249),
}

fun selectAudioFormat(formats: List<AudioFormat>, quality: Quality): AudioFormat? {
    val audioOnly = formats.filter { it.mimeType.startsWith("audio/") }
    return audioOnly.firstOrNull { it.itag == quality.preferredItag }
        ?: audioOnly.maxByOrNull { it.bitrate }
}
```

- [ ] **Step 5: Run the tests and confirm they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.mero.data.FormatSelectionTest"`
Expected: PASS, 7 tests.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/mero/domain app/src/main/java/com/mero/data app/src/test
git commit -m "feat: add domain model and audio format selection

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

## Task 4: Search repository

**Files:**
- Create: `app/src/main/java/com/mero/data/SearchRepository.kt`
- Test: `app/src/test/java/com/mero/data/SearchMappingTest.kt`
- Modify: `app/src/main/java/com/mero/MeroApp.kt`

**Interfaces:**
- Consumes: `Song` (Task 3); the innertube search entry point recorded in Task 2 Step 4
- Produces:
  - `fun interface SearchApi { suspend fun searchSongs(query: String): List<Song> }`
  - `class SearchRepository(private val api: SearchApi)` with `suspend fun search(query: String): Result<List<Song>>`
  - `AppContainer.searchRepository: SearchRepository`

The `SearchApi` seam is what makes mapping testable without a network call, and
it is the single place upstream's types are allowed to appear.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/mero/data/SearchMappingTest.kt`:

```kotlin
package com.mero.data

import com.mero.domain.Song
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class SearchMappingTest {

    private val song = Song("abc123", "Kesariya", "Arijit Singh", 268, null)

    @Test
    fun `returns songs from the api`() = runTest {
        val repo = SearchRepository(SearchApi { listOf(song) })
        val result = repo.search("kesariya")
        assertEquals(listOf(song), result.getOrNull())
    }

    @Test
    fun `blank query short-circuits without calling the api`() = runTest {
        var called = false
        val repo = SearchRepository(SearchApi { called = true; emptyList() })
        val result = repo.search("   ")
        assertEquals(emptyList<Song>(), result.getOrNull())
        assertTrue(!called)
    }

    @Test
    fun `network failure becomes a failed Result rather than a thrown exception`() = runTest {
        val repo = SearchRepository(SearchApi { throw IOException("offline") })
        val result = repo.search("kesariya")
        assertTrue(result.isFailure)
    }
}
```

Add the test dependency `org.jetbrains.kotlinx:kotlinx-coroutines-test`
(resolve current version) as `testImplementation`.

- [ ] **Step 2: Run it and confirm it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.mero.data.SearchMappingTest"`
Expected: FAIL — unresolved reference `SearchRepository`.

- [ ] **Step 3: Write the repository**

Create `app/src/main/java/com/mero/data/SearchRepository.kt`:

```kotlin
package com.mero.data

import com.mero.domain.Song

fun interface SearchApi {
    suspend fun searchSongs(query: String): List<Song>
}

class SearchRepository(private val api: SearchApi) {

    suspend fun search(query: String): Result<List<Song>> {
        if (query.isBlank()) return Result.success(emptyList())
        return runCatching { api.searchSongs(query.trim()) }
    }
}
```

- [ ] **Step 4: Run the tests and confirm they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.mero.data.SearchMappingTest"`
Expected: PASS, 3 tests.

- [ ] **Step 5: Write the innertube-backed implementation**

Append to `SearchRepository.kt` a `SearchApi` implementation that calls the
search entry point recorded in Task 2 Step 4 and maps each result to `Song`.

Mapping rules — apply these regardless of upstream's exact field names:

- `Song.id` ← the video id
- `Song.artist` ← join multiple artist names with `", "`
- `Song.durationSec` ← 0 when upstream reports no duration; never crash on null
- `Song.thumbnailUrl` ← the largest available thumbnail, or `null`
- Drop results with a blank video id rather than emitting a `Song` that cannot play

Request **song** results specifically, not videos — spec §4.4 prefers song
results for cleaner masters, and it is the same filter argument here.

- [ ] **Step 6: Wire it into the container**

In `MeroApp.kt`:

```kotlin
class AppContainer {
    val searchRepository: SearchRepository by lazy {
        SearchRepository(InnerTubeSearchApi())
    }
}
```

Name `InnerTubeSearchApi` to match whatever you created in Step 5.

- [ ] **Step 7: Verify against the real API**

Write a temporary instrumented test or a debug-only button that calls
`searchRepository.search("kesariya")` and logs the result.

Expected: a non-empty list of songs with plausible titles and non-blank ids.

**This is the moment extraction breakage would first appear.** If results come
back empty or the call throws, re-read `innertube/VENDORED.md` and re-sync
upstream before assuming your mapping is wrong.

Delete the temporary test or button before committing.

- [ ] **Step 8: Commit**

```bash
git add -A
git commit -m "feat: add search repository backed by innertube

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

## Task 5: Search screen

**Files:**
- Create: `app/src/main/java/com/mero/ui/search/SearchViewModel.kt`
- Create: `app/src/main/java/com/mero/ui/search/SearchScreen.kt`
- Modify: `app/src/main/java/com/mero/MainActivity.kt`

**Interfaces:**
- Consumes: `SearchRepository` (Task 4), `Song` (Task 3)
- Produces:
  - `data class SearchUiState(val query: String, val results: List<Song>, val loading: Boolean, val error: String?)`
  - `class SearchViewModel(private val repo: SearchRepository)` with `val state: StateFlow<SearchUiState>`, `fun onQueryChange(q: String)`, `fun onSearch()`
  - `@Composable fun SearchScreen(viewModel: SearchViewModel, onSongClick: (Song) -> Unit)`

`onSongClick` is a no-op parameter until Task 7 supplies playback. Wiring it now
keeps Task 7 to a one-line change.

- [ ] **Step 1: Write the ViewModel**

Create `app/src/main/java/com/mero/ui/search/SearchViewModel.kt`:

```kotlin
package com.mero.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mero.data.SearchRepository
import com.mero.domain.Song
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    val results: List<Song> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
)

class SearchViewModel(private val repo: SearchRepository) : ViewModel() {

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    private var searchJob: Job? = null

    fun onQueryChange(q: String) = _state.update { it.copy(query = q) }

    fun onSearch() {
        val query = _state.value.query
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val result = repo.search(query)
            _state.update {
                result.fold(
                    onSuccess = { songs -> it.copy(results = songs, loading = false) },
                    onFailure = { e ->
                        it.copy(
                            loading = false,
                            error = e.message ?: "Search failed. Tap to retry.",
                        )
                    },
                )
            }
        }
    }
}
```

Cancelling `searchJob` prevents a slow earlier query from overwriting the
results of a faster later one.

- [ ] **Step 2: Write the screen**

Create `app/src/main/java/com/mero/ui/search/SearchScreen.kt`:

```kotlin
package com.mero.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.mero.domain.Song

@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    onSongClick: (Song) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(
            value = state.query,
            onValueChange = viewModel::onQueryChange,
            label = { Text("Search") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { viewModel.onSearch() }),
        )

        when {
            state.loading -> CircularProgressIndicator(
                Modifier.padding(24.dp).align(Alignment.CenterHorizontally)
            )
            state.error != null -> Text(
                text = state.error!!,
                modifier = Modifier
                    .padding(24.dp)
                    .clickable { viewModel.onSearch() },
            )
            else -> LazyColumn {
                items(state.results, key = { it.id }) { song ->
                    ListItem(
                        headlineContent = { Text(song.title, maxLines = 1) },
                        supportingContent = { Text(song.artist, maxLines = 1) },
                        leadingContent = {
                            AsyncImage(
                                model = song.thumbnailUrl,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                            )
                        },
                        modifier = Modifier.clickable { onSongClick(song) },
                    )
                }
            }
        }
    }
}
```

The error branch is clickable to retry — spec §9 requires extraction failure to
be visible and recoverable, never a silent hang.

- [ ] **Step 3: Host it in MainActivity**

Rewrite `MainActivity.kt` to show `SearchScreen` inside the wizard-generated
theme, building the ViewModel from the container:

```kotlin
val app = application as MeroApp
val viewModel = viewModel {
    SearchViewModel(app.container.searchRepository)
}
SearchScreen(viewModel = viewModel, onSongClick = { /* Task 7 */ })
```

Add `androidx.lifecycle:lifecycle-runtime-compose` (resolve current version) for
`collectAsStateWithLifecycle`.

- [ ] **Step 4: Manually verify**

Run the app on a device. Type `kesariya`, press the keyboard's search action.

Expected:
- A spinner appears briefly
- A list of songs with titles, artists and cover thumbnails appears
- Enabling airplane mode and searching shows a tappable error message, not a
  crash or an indefinite spinner

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat: add search screen

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

## Task 6: Playback service

**Files:**
- Create: `app/src/main/java/com/mero/playback/MediaItems.kt`
- Create: `app/src/main/java/com/mero/data/StreamRepository.kt`
- Create: `app/src/main/java/com/mero/playback/StreamResolver.kt`
- Create: `app/src/main/java/com/mero/playback/MeroPlaybackService.kt`
- Test: `app/src/test/java/com/mero/playback/MediaItemsTest.kt`
- Modify: `AndroidManifest.xml`, `MeroApp.kt`

**Interfaces:**
- Consumes: `selectAudioFormat`, `AudioFormat`, `Quality` (Task 3); `Song` (Task 3)
- Produces:
  - `fun mediaItemFor(song: Song): MediaItem`
  - `fun videoIdFrom(uri: Uri): String`
  - `class StreamRepository` with `suspend fun streamUrl(videoId: String, quality: Quality): String`
  - `AppContainer.streamRepository: StreamRepository`
  - `MeroPlaybackService : MediaSessionService`

- [ ] **Step 1: Write the failing URI test**

Create `app/src/test/java/com/mero/playback/MediaItemsTest.kt`:

```kotlin
package com.mero.playback

import android.net.Uri
import com.mero.domain.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MediaItemsTest {

    private val song = Song("abc123", "Kesariya", "Arijit Singh", 268, null)

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
```

`Uri` is an Android framework class, so this test needs Robolectric. Add
`testImplementation("org.robolectric:robolectric:<resolve current>")` and enable
`testOptions { unitTests.isIncludeAndroidResources = true }` in
`app/build.gradle.kts`.

- [ ] **Step 2: Run it and confirm it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.mero.playback.MediaItemsTest"`
Expected: FAIL — unresolved reference `mediaItemFor`.

- [ ] **Step 3: Implement the URI mapping**

Create `app/src/main/java/com/mero/playback/MediaItems.kt`:

```kotlin
package com.mero.playback

import android.net.Uri
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.mero.domain.Song

private const val SCHEME = "mero"

fun mediaItemFor(song: Song): MediaItem =
    MediaItem.Builder()
        .setMediaId(song.id)
        .setUri("$SCHEME://${song.id}".toUri())
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(song.title)
                .setArtist(song.artist)
                .setArtworkUri(song.thumbnailUrl?.toUri())
                .build()
        )
        .build()

fun videoIdFrom(uri: Uri): String {
    require(uri.scheme == SCHEME) { "Not a mero uri: $uri" }
    return requireNotNull(uri.host) { "Missing video id in $uri" }
}
```

The custom scheme exists so the resolver can recognise which URIs need a live
CDN address substituted. Spec §6: stream URLs are never persisted.

- [ ] **Step 4: Run the tests and confirm they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.mero.playback.MediaItemsTest"`
Expected: PASS, 3 tests.

- [ ] **Step 5: Write the stream repository**

Create `app/src/main/java/com/mero/data/StreamRepository.kt`. It calls the
player entry point recorded in Task 2 Step 4, maps `streamingData.adaptiveFormats`
into `List<AudioFormat>`, and returns the URL chosen by `selectAudioFormat`.

```kotlin
package com.mero.data

class StreamRepository(private val api: PlayerApi) {

    suspend fun streamUrl(videoId: String, quality: Quality = Quality.HIGH): String {
        val formats = api.formatsFor(videoId)
        val chosen = selectAudioFormat(formats, quality)
            ?: error("No playable audio format for $videoId")
        return chosen.url
    }
}

fun interface PlayerApi {
    suspend fun formatsFor(videoId: String): List<AudioFormat>
}
```

Implement `PlayerApi` against innertube using the field names recorded in Task 2
Step 4. Do not cache the returned URL anywhere.

- [ ] **Step 6: Write the resolver**

Create `app/src/main/java/com/mero/playback/StreamResolver.kt`:

```kotlin
package com.mero.playback

import androidx.core.net.toUri
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource
import com.mero.data.StreamRepository
import kotlinx.coroutines.runBlocking

class StreamResolver(
    private val repo: StreamRepository,
) : ResolvingDataSource.Resolver {

    override fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
        if (dataSpec.uri.scheme != "mero") return dataSpec
        val videoId = videoIdFrom(dataSpec.uri)
        val url = runBlocking { repo.streamUrl(videoId) }
        return dataSpec.withUri(url.toUri())
    }
}
```

`runBlocking` is correct here and should not be "fixed" in review:
`resolveDataSpec` is invoked on ExoPlayer's loading thread, which is designed to
block, and the interface is synchronous. Resolving here rather than at enqueue
time is also what makes expired URLs self-heal — ExoPlayer re-resolves on
retry, fetching a fresh address.

- [ ] **Step 7: Write the service**

Create `app/src/main/java/com/mero/playback/MeroPlaybackService.kt`:

```kotlin
package com.mero.playback

import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.mero.MeroApp

class MeroPlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val container = (application as MeroApp).container

        val dataSourceFactory = ResolvingDataSource.Factory(
            DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true),
            StreamResolver(container.streamRepository),
        )

        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()

        mediaSession = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = mediaSession

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }
}
```

`handleAudioFocus = true` pauses for calls and other apps;
`setHandleAudioBecomingNoisy(true)` pauses when headphones are unplugged.

- [ ] **Step 8: Declare the service**

Inside `<application>` in `AndroidManifest.xml`:

```xml
<service
    android:name=".playback.MeroPlaybackService"
    android:foregroundServiceType="mediaPlayback"
    android:exported="true">
    <intent-filter>
        <action android:name="androidx.media3.session.MediaSessionService" />
    </intent-filter>
</service>
```

`android:exported="true"` is required for Android Auto and Bluetooth
controllers to bind.

- [ ] **Step 9: Add the repository to the container**

In `MeroApp.kt`, add alongside `searchRepository`:

```kotlin
val streamRepository: StreamRepository by lazy {
    StreamRepository(InnerTubePlayerApi())
}
```

- [ ] **Step 10: Build and commit**

Run: `./gradlew :app:assembleDebug :app:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`, all tests pass.

```bash
git add -A
git commit -m "feat: add playback service with resolving data source

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

## Task 7: Connect search to playback

**Files:**
- Create: `app/src/main/java/com/mero/playback/PlayerConnection.kt`
- Modify: `app/src/main/java/com/mero/MainActivity.kt`

**Interfaces:**
- Consumes: `mediaItemFor` (Task 6), `MeroPlaybackService` (Task 6), `SearchScreen`'s `onSongClick` (Task 5)
- Produces: `class PlayerConnection` with `suspend fun connect(context: Context)`, `fun play(song: Song)`, `fun release()`

This is the task that makes M1 real: audio comes out of the speaker.

- [ ] **Step 1: Write the controller connection**

Create `app/src/main/java/com/mero/playback/PlayerConnection.kt`:

```kotlin
package com.mero.playback

import android.content.ComponentName
import android.content.Context
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.mero.domain.Song
import kotlinx.coroutines.guava.await

class PlayerConnection {

    private var controller: MediaController? = null

    suspend fun connect(context: Context) {
        if (controller != null) return
        val token = SessionToken(
            context,
            ComponentName(context, MeroPlaybackService::class.java),
        )
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
```

- [ ] **Step 2: Wire it into MainActivity**

Hold one `PlayerConnection` for the activity's lifetime, connect on start,
release on stop, and pass `play` into the screen:

```kotlin
val connection = remember { PlayerConnection() }
LaunchedEffect(Unit) { connection.connect(context) }
DisposableEffect(Unit) { onDispose { connection.release() } }

SearchScreen(
    viewModel = viewModel,
    onSongClick = { song -> connection.play(song) },
)
```

- [ ] **Step 3: Request the notification permission**

On API 33+, the media notification is suppressed without
`POST_NOTIFICATIONS`. Request it on first launch using
`rememberLauncherForActivityResult` with
`ActivityResultContracts.RequestPermission()`.

Playback still works if denied — only the notification is missing — so do not
block the UI on the result.

- [ ] **Step 4: Manually verify the full path**

Run on a physical device.

Expected, in order:
1. Search `kesariya`, tap the first result
2. Audio plays within about three seconds
3. A media notification appears with title, artist and artwork
4. Lock the screen — playback continues, lock-screen controls work
5. Unplug or disconnect headphones — playback pauses
6. Receive or place a call — playback ducks or pauses, then resumes

If step 2 fails with a source error, check Logcat for the resolver: an
`IllegalStateException` naming "No playable audio format" means extraction
broke, not that this task's wiring is wrong. See `innertube/VENDORED.md`.

- [ ] **Step 5: Verify M1's success criterion**

Spec §10: cold start to audio under three seconds on a mid-range device.

Force-stop the app, then time from tapping the icon to first audio. Record the
figure in the commit message. If it substantially exceeds three seconds, note
where the time goes — do not optimise yet, M1 is a skeleton.

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "feat: connect search results to playback

Cold start to audio: <measured>s

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

## M1 Definition of Done

- [ ] `./gradlew :app:assembleDebug :app:testDebugUnitTest` passes
- [ ] 13 unit tests pass across three test classes
- [ ] Search returns real YouTube Music results
- [ ] Tapping a result plays audio
- [ ] Playback continues with the screen locked and survives leaving the app
- [ ] Notification and lock-screen controls work
- [ ] Headphone disconnect pauses playback
- [ ] `innertube/` is unmodified since the subtree add
- [ ] `LICENSE` is GPL-3.0 and `README.md` credits upstream

**Explicitly not in M1** — do not add these, they belong to later milestones:
queue, mini-player, library, playlists, downloads, EQ, import, lyrics.

---

## Self-Review Notes

**Spec coverage.** M1 implements the search and playback half of §4.1 plus the
§3.1 no-sign-in and §6 no-persisted-URL constraints. Queue, Now Playing,
mini-player, shuffle, repeat, sleep timer and autoplay radio are §4.1 features
deferred to M2 by the milestone table in §7 — not gaps in this plan.

**Two known unknowns, both deliberate.** The innertube API surface is discovered
in Task 2 Step 4 rather than assumed, because the vendored module's exact
signatures cannot be verified from here; every type Mero itself owns is
specified exactly. Library versions are resolved at add-time rather than
hard-coded, per the Version Policy.

**Type consistency.** `selectAudioFormat(List<AudioFormat>, Quality)` is defined
in Task 3 and consumed unchanged in Task 6. `Song` is defined in Task 3 and used
in Tasks 4, 5, 6, 7. `videoIdFrom` and `mediaItemFor` are defined in Task 6
Step 3 and consumed in Task 6 Step 6 and Task 7 Step 1.
