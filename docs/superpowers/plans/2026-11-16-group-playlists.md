# Group Playlists Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Playlists that several friends can all add songs to and remove songs from, kept the same on every member's phone through Mero's share site.

**Architecture:** The Cloudflare Pages site (`docs/share-site/cloudflare`, `meroapp.pages.dev`) gains a D1 database holding, per group, an ordered log of steps (`add`/`remove`/`rename`/`cover`). Each phone keeps an outbox of its own unsent steps, sends them, fetches steps since the last `seq` it applied, and merges with a pure function (last step per song wins; local order kept; new songs appended). Sync runs on app start, on opening the playlist, shortly after an edit, and every 6 hours via WorkManager.

**Tech Stack:** Kotlin, Room (schema 8 → 9), WorkManager (already a dependency), `HttpURLConnection` + `org.json` (as `ShareApi` does), Robolectric for DB tests; Cloudflare Pages Functions (JS), D1, KV.

**Spec:** `docs/superpowers/specs/2026-11-16-group-playlists-design.md`

## Global Constraints

- No account or login of any kind; the 8-character group code is the only key (spec §3.1).
- Never edit `innertube/`. Domain types (`Song` etc.) never expose server or upstream types.
- Never persist a stream URL; persist video ids only.
- No limit on songs per group. Requests carry **at most 1,000 steps** each; `GET` pages **1,000** steps at a time.
- Groups are **never deleted** from the server. No clean-up job.
- Cover: JPEG, **≤ 200 KB**, checked by header `FF D8 FF`; stored in KV as `g:<code>` with no expiry.
- Group code alphabet and length: same as share codes (`ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789`, 8 chars).
- Phone files and podcast episodes (no YouTube id) never become steps.
- Background sync every **6 hours**, network required. Sync failure only means "not up to date"; it never blocks playback or the library.
- Failures are visible and retryable in the UI (spec §9): the playlist screen shows "Not synced yet" with Retry after a failed attempt.
- Repositories return `Result<T>`; they don't throw across their boundary.
- DI is the hand-written `AppContainer` (`MeroApplication.kt`), no Koin.
- Commits end with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- `JAVA_HOME=/c/Users/sdroy/.jdks/jbr-21.0.11` for Gradle; deploy with `wrangler pages deploy public --project-name meroapp --branch main` from `docs/share-site/cloudflare`.
- Ships as **1.15.0**, after 1.14.0 (short links) is released. If recommender Phase 2b ships first, it takes schema 9 and this becomes 10.

## Review Focus

1. **A step resent after a crash** (app killed after the server accepted the outbox but before it was cleared): the server must skip the repeated `op_id` and the phone must end up with the song once. Test: Task 1 script step "repeat op_id"; Task 5 `resent outbox steps are applied once`.
2. **The server unreachable, or a code that doesn't exist (404)**: the playlist still opens and plays; the screen shows "Not synced yet · Retry"; joining a bad code says so. Test: Task 5 `a failed sync leaves the playlist untouched and reports failure`, `joining an unknown code fails without creating a playlist`.
3. **Joining a very long group** (more than one 1,000-step page): every page is fetched and every song appears, in `seq` order. Test: Task 5 `joining pages through more than 1000 steps`.
4. **Your own reorder survives a sync**, and your unsent edits don't flicker away while a sync is in flight. Test: Task 3 `local order is kept and new songs go to the end`, `pending steps apply on top of fetched ones`.
5. **Deleting a group playlist on the phone** drops its outbox rows, so nothing is sent for a playlist that's gone. Test: Task 2 `deleting a playlist removes its outbox rows`.

---

### Task 1: Server: group storage and endpoints

**Files:**
- Create: `docs/share-site/cloudflare/schema.sql`
- Create: `docs/share-site/cloudflare/functions/api/g.js` (POST create)
- Create: `docs/share-site/cloudflare/functions/api/g/[code].js` (GET steps)
- Create: `docs/share-site/cloudflare/functions/api/g/[code]/steps.js` (POST steps)
- Create: `docs/share-site/cloudflare/functions/api/g/[code]/cover.js` (PUT cover)
- Create: `docs/share-site/cloudflare/functions/c/g/[code].js` (GET cover)
- Create: `docs/share-site/cloudflare/functions/g/[code].js` (preview page)
- Modify: `docs/share-site/cloudflare/functions/_lib.js` (export `newCode()`, `MAX_STEPS = 1000`; move `ALPHABET`/`newCode` out of `api/p.js` and import them there)
- Modify: `docs/share-site/cloudflare/wrangler.toml` (add `[[d1_databases]] binding = "DB", database_name = "mero", database_id = <from create>`)
- Modify: `docs/share-site/cloudflare/README.md` (the new routes and `wrangler d1 execute mero --remote --file schema.sql`)
- Test: `docs/share-site/cloudflare/test-groups.sh`

**Interfaces:**
- Produces (JSON, all `content-type: application/json`, `cache-control: no-store`):
  - `POST /api/g` body `{name: string, ids: string[] (≤1000), cover?: base64 jpeg}` → `200 {code, head}`; `400 {error}` on bad input
  - `GET /api/g/<code>?since=<int>` → `200 {name, hasCover, head, steps:[{seq, kind, value}]}` (≤1000 steps with `seq > since`, ascending; `more: true` when more remain) ; `404 {error:"gone"}`
  - `POST /api/g/<code>/steps` body `{steps:[{opId, kind, value}]}` (≤1000) → `200 {head}`; repeated `opId` skipped
  - `PUT /api/g/<code>/cover` body raw JPEG → `200 {head}` (appends a `cover` step)
  - `GET /c/g/<code>.jpg` → image or 404
  - `GET /g/<code>` → HTML via `page()` with og:title = name, og:description = "`N songs · group playlist on Mero`", og:image = cover or logo, line "Get Mero 1.15 or newer to join".

- [ ] **Step 1: Create the D1 database and schema**

`schema.sql` holds the two tables from the spec (`groups(code PK, name, has_cover, head)`, `steps(code, seq, op_id, kind, value, at, PK(code,seq), UNIQUE(code,op_id))`). Run `wrangler d1 create mero`, put the id in `wrangler.toml`, then `wrangler d1 execute mero --remote --file schema.sql`. Expected: "Executed 2 commands".

- [ ] **Step 2: Write `test-groups.sh` (fails until deployed)**

A bash script against `${H:-https://meroapp.pages.dev}` using `curl` and `grep`/`sed`, exiting non-zero on the first failed check. Checks, in order:
1. create `{name:"T",ids:[A,B]}` → has `code`, `head` = 2
2. `GET ?since=0` → steps `add A` (seq 1), `add B` (seq 2), `more` absent
3. post `[{opId:"x1",kind:"remove",value:A},{opId:"x2",kind:"add",value:C}]` → `head` 4
4. **repeat op_id**: post `x1` again → `head` still 4
5. `GET ?since=2` → exactly seq 3 and 4
6. bad input → 400: id `"bad"`, kind `"drop"`, name of 81 chars, 1,001 steps
7. `GET /api/g/AAAAAAAA` → 404
8. `PUT cover` with a 3-byte non-JPEG → 400; with a real small JPEG (download `https://i.ytimg.com/vi/dQw4w9WgXcQ/default.jpg` to a temp file first) → 200 and `GET /c/g/<code>.jpg` 200 `image/jpeg`
9. `GET /g/<code>` contains `og:title" content="T"`
10. paging: create a group with 1,000 ids, post 500 more → `GET ?since=0` returns 1,000 steps and `more:true`; `?since=1000` returns 500

- [ ] **Step 3: Implement the functions**

Validation exactly as `/api/p` (ID regex, name trimmed 1..80, JPEG header, cover ≤ 200 KB, `content-length` ≤ 400 KB). `kind` ∈ {add, remove, rename}; `rename` value is a name 1..80; `cover` steps are only created by `PUT cover`. Appending steps happens in one `env.DB.batch([...])`: read `head`, insert each step with `seq = head + i` using `INSERT OR IGNORE` (skips a repeated `op_id`), then `UPDATE groups SET head = (SELECT MAX(seq) ...)`. A `rename` also updates `groups.name`.

- [ ] **Step 4: Deploy and run the script**

Run: `wrangler pages deploy public --project-name meroapp --branch main && bash test-groups.sh`
Expected: script prints `all group checks passed` and exits 0.

- [ ] **Step 5: Commit**

```bash
git add docs/share-site/cloudflare
git commit -m "feat(share-site): group playlists on D1 (create, steps, cover, preview)"
```

---

### Task 2: App database: schema 9

**Files:**
- Modify: `app/src/main/java/com/mero/data/db/MeroDatabase.kt`
- Test: `app/src/test/java/com/mero/data/db/MigrationTest.kt`

**Interfaces:**
- Produces:
  - `PlaylistEntity.groupCode: String? = null`, `PlaylistEntity.groupSeq: Long = 0`
  - `PlaylistSummary.groupCode: String? = null` (selected in the summary query)
  - `@Entity("group_outbox") data class GroupOutboxEntity(@PrimaryKey val opId: String, val playlistId: String, val kind: String, val value: String?, val createdAt: Long)`
  - DAO: `suspend fun insertOutbox(e: GroupOutboxEntity)`, `suspend fun outbox(playlistId: String): List<GroupOutboxEntity>` (by `createdAt`), `suspend fun deleteOutbox(opIds: List<String>)`, `suspend fun setGroup(playlistId: String, code: String?, seq: Long)`, `suspend fun groupPlaylists(): List<PlaylistEntity>` (`groupCode IS NOT NULL`), `suspend fun playlistByGroup(code: String): PlaylistEntity?`, `suspend fun playlistNow(id: String): PlaylistEntity?`
  - `deletePlaylist(id)` transaction also deletes that playlist's outbox rows
  - `MIGRATION_8_9`, `DB_VERSION = 9`, schema file `app/schemas/com.mero.data.db.MeroDatabase/9.json`

- [ ] **Step 1: Write the failing tests** in `MigrationTest`:
  - `` `8 to 9 keeps playlists, adds no group, and an empty outbox` ``: build v8 from schema, insert a playlist and a playlist_song, open with Room; assert the playlist has `groupCode == null`, `groupSeq == 0`, its song is still there, `outbox(id)` is empty.
  - `` `deleting a playlist removes its outbox rows` ``: in a fresh v9 db, insert a playlist and two outbox rows, `deletePlaylist`, assert `outbox(id)` is empty.

- [ ] **Step 2: Run them, expect FAIL** (`./gradlew :app:testDebugUnitTest --tests "com.mero.data.db.MigrationTest"`: unresolved `outbox`).

- [ ] **Step 3: Implement** the entity fields, `GroupOutboxEntity`, DAO methods and `MIGRATION_8_9` (`ALTER TABLE playlists ADD COLUMN groupCode TEXT`, `ADD COLUMN groupSeq INTEGER NOT NULL DEFAULT 0`, `CREATE TABLE group_outbox (...)`), add it to `ALL_MIGRATIONS`, bump `DB_VERSION`.

- [ ] **Step 4: Run all DB tests, expect PASS** (`--tests "com.mero.data.db.*"`; `MigrationChainTest` confirms 9.json exists and the chain is unbroken).

- [ ] **Step 5: Commit** `feat(db): schema 9 for group playlists (group code, seq, outbox)`, including `app/schemas/.../9.json`.

---

### Task 3: Merge logic (pure, strict TDD)

**Files:**
- Create: `app/src/main/java/com/mero/data/GroupMerge.kt`
- Test: `app/src/test/java/com/mero/data/GroupMergeTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  data class GroupStep(val seq: Long, val kind: String, val value: String?)
  data class PendingStep(val kind: String, val value: String?)
  data class Merged(val songIds: List<String>, val name: String?, val coverChanged: Boolean, val added: Int)
  fun mergeGroup(local: List<String>, steps: List<GroupStep>, pending: List<PendingStep>): Merged
  ```
  `name` is the last `rename` value in `steps` then `pending` (null if none). `added` counts songs present after the merge that were not in `local`.

- [ ] **Step 1: Write the failing tests** (ids `a`..`e` stand for video ids):
  - `` `adds append in seq order` ``: local `[]`, steps add a(1), add b(2) → `[a, b]`, added 2
  - `` `last step per song wins` ``: local `[a]`, steps remove a(5), add a(6) → `[a]`; steps add a(5), remove a(6) → `[]`
  - `` `local order is kept and new songs go to the end` ``: local `[c, a, b]`, steps add d(9), add a(10) → `[c, a, b, d]` (a already present keeps its place)
  - `` `a removed song re-added goes to the end` ``: local `[a, b]`, steps remove a(3), add a(4) → `[b, a]`
  - `` `pending steps apply on top of fetched ones` ``: local `[a]`, steps add b(2), pending remove a, add c → `[b, c]`
  - `` `duplicate steps are harmless` ``: steps add a(1), add a(1) → `[a]`
  - `` `rename takes the last name; cover is flagged` ``: steps rename "X"(1), cover(2), rename "Y"(3) → name "Y", coverChanged true
  - `` `ten thousand adds merge quickly` ``: 10,000 add steps onto `[]` → size 10,000, completes under 1 s

- [ ] **Step 2: Run, expect FAIL** (`--tests "com.mero.data.GroupMergeTest"`).

- [ ] **Step 3: Implement `mergeGroup`.** Approach: start from a `LinkedHashSet(local)`; for each step in order (fetched then pending), `add` → remove-then-append if absent, keep place if present; `remove` → remove. Exception to "keep place": a song removed earlier in this same merge and then re-added goes to the end (falls out of the set naturally).

- [ ] **Step 4: Run, expect PASS.**

- [ ] **Step 5: Commit** `feat(groups): pure merge of group steps`.

---

### Task 4: Links and the group API client

**Files:**
- Modify: `app/src/main/java/com/mero/data/ShareLink.kt`
- Create: `app/src/main/java/com/mero/data/GroupApi.kt`
- Test: `app/src/test/java/com/mero/data/ShareLinkTest.kt`, `app/src/test/java/com/mero/data/GroupApiTest.kt`

**Interfaces:**
- Produces:
  - `ShareLink.shortGroup(code: String): String` = `https://meroapp.pages.dev/g/<code>`; `ShareLink.parseGroup(url: String): String?` (the code, or null; same rules as `parseShortPlaylist`)
  - `data class GroupPage(val name: String, val hasCover: Boolean, val head: Long, val steps: List<GroupStep>, val more: Boolean)`
  - `interface GroupApi` with:
    `suspend fun create(name: String, ids: List<String>, cover: ByteArray?): Result<Pair<String, Long>>` (code, head),
    `suspend fun steps(code: String, since: Long): Result<GroupPage>`,
    `suspend fun send(code: String, steps: List<GroupOutboxEntity>): Result<Long>` (new head),
    `suspend fun putCover(code: String, jpeg: ByteArray): Result<Long>`,
    `suspend fun cover(code: String): ByteArray?`
  - `object HttpGroupApi : GroupApi` (HttpURLConnection, 10 s timeouts, like `ShareApi`); `internal fun parseGroupPage(json: String): GroupPage` (pure)
  - A 404 from `steps` fails with `GroupGone` (`class GroupGone : Exception()`).

- [ ] **Step 1: Failing tests:** `ShareLinkTest`: `` `a group link is read back as its code` `` and rejects other hosts, `http`, wrong length, extra path. `GroupApiTest`: `` `a page of steps is parsed in order` `` (JSON with 3 steps incl. `value: null` for cover → 3 `GroupStep`s, `more` false when absent), `` `more is read when present` ``.

- [ ] **Step 2: Run, expect FAIL.**

- [ ] **Step 3: Implement.** `create` with more than 1,000 ids sends the first 1,000, then the rest through `send` in 1,000-step chunks of `add` steps with fresh `opId`s (`UUID.randomUUID().toString()`). `send` also chunks at 1,000.

- [ ] **Step 4: Run, expect PASS.**

- [ ] **Step 5: Commit** `feat(groups): group links and API client`.

---

### Task 5: Group sync in the repository

**Files:**
- Modify: `app/src/main/java/com/mero/data/LibraryRepository.kt`
- Create: `app/src/main/java/com/mero/data/GroupSync.kt`
- Modify: `app/src/main/java/com/mero/MeroApplication.kt` (`AppContainer.groupSync`)
- Test: `app/src/test/java/com/mero/data/GroupSyncTest.kt` (Robolectric, in-memory Room, a `FakeGroupApi` in the test file)

**Interfaces:**
- Consumes: Task 2 DAO, Task 3 `mergeGroup`, Task 4 `GroupApi`, `SearchRepository.resolveIds(ids): Result<List<Song>>`, `PlaylistCovers.saveBytes/forUpload`.
- Produces:
  - `LibraryRepository`: when the playlist has a `groupCode`, `addToPlaylist`, `removeFromPlaylist`, `renamePlaylist`, also insert a `GroupOutboxEntity` (kind `add`/`remove`/`rename`) in the same `@Transaction`; songs with `!isYouTube` never produce a step. `onGroupEdit: (playlistId: String) -> Unit` constructor callback (default no-op) is invoked after such an edit.
  - `class GroupSync(dao: MeroDao, library: LibraryRepository, api: GroupApi, resolve: suspend (List<String>) -> Result<List<Song>>, saveCover: (playlistId: String, bytes: ByteArray) -> String?, coverForUpload: (uri: String?) -> ByteArray?)`
    - `suspend fun makeGroup(playlistId: String): Result<String>` (the link)
    - `suspend fun join(code: String): Result<String>` (local playlist id; existing one if already joined)
    - `suspend fun sync(playlistId: String): Result<Int>` (songs added by others in this sync)
    - `suspend fun syncAll(): Int`
    - `suspend fun leave(playlistId: String)` (clears `groupCode`, deletes its outbox)
    - `suspend fun pushCover(playlistId: String)`
    - `val failed: StateFlow<Set<String>>` (playlist ids whose last sync failed)
  - Unavailable songs: a `SongEntity(id, title = "Unavailable song", artist = "", album = "", durationSec = 0, thumbnailUrl = null)`; `sync` re-resolves rows whose title is exactly that.
  - One sync at a time per playlist (`Mutex` per id).

- [ ] **Step 1: Failing tests** (each builds an in-memory DB and a `FakeGroupApi` holding one server-side log in memory):
  - `` `making a group uploads the songs and records the code` ``: playlist [a, b] + a phone file → server log add a, add b; playlist `groupCode` set, `groupSeq` = 2
  - `` `an edit to a group playlist is queued and sent` ``: add c locally → outbox has `add c`; `sync` → server log ends `add c`, outbox empty
  - `` `edits to ordinary playlists queue nothing` ``
  - `` `a friend's add arrives and is counted` ``: server gets add d from elsewhere; `sync` returns 1, playlist ends with d
  - `` `resent outbox steps are applied once` ``: fake accepts the send but throws before returning once; second `sync` → server has `add c` once (fake dedupes by `opId`), playlist has c once
  - `` `a failed sync leaves the playlist untouched and reports failure` ``: fake fails `steps`; songs unchanged, outbox kept, id in `failed`; next good sync removes it from `failed`
  - `` `joining pages through more than 1000 steps` ``: server log of 1,500 adds; `join` → local playlist of 1,500 in `seq` order, `groupSeq` 1,500
  - `` `joining an unknown code fails without creating a playlist` ``: `GroupGone` → `Result.failure`, playlist count unchanged
  - `` `joining twice opens the same playlist` ``
  - `` `a song that cannot be resolved is kept as unavailable and filled in later` ``
  - `` `a rename from a friend renames the playlist here` ``; `` `a cover step downloads the cover` ``
  - `` `leaving keeps the songs and stops syncing` ``

- [ ] **Step 2: Run, expect FAIL** (`--tests "com.mero.data.GroupSyncTest"`).

- [ ] **Step 3: Implement.** `sync` order: lock; send outbox (on success delete those `opId`s); fetch pages from `groupSeq` until `more` is false; `mergeGroup(localIds, steps, remainingOutbox)`; resolve and `ensure` any new ids (missing → unavailable row); write the order with `reorderPlaylist`; apply `name`; on `coverChanged` download and `setPlaylistCover`; `setGroup(id, code, lastSeq)`. Any failure → `failed += id`, return failure; nothing partial is written (write steps happen in one `@Transaction` after all fetching).

- [ ] **Step 4: Run, expect PASS**, then the full suite (`./gradlew :app:testDebugUnitTest`).

- [ ] **Step 5: Commit** `feat(groups): sync, join, leave, with outbox`.

---

### Task 6: When sync runs

**Files:**
- Create: `app/src/main/java/com/mero/data/GroupSyncWorker.kt`
- Modify: `app/src/main/java/com/mero/MeroApplication.kt` (schedule the worker; give `LibraryRepository` an `onGroupEdit` that debounces)
- Modify: `app/src/main/java/com/mero/MainActivity.kt:96` (`onStart` → `syncAll` in the app scope)

**Interfaces:**
- Consumes: `GroupSync.syncAll()`, `GroupSync.sync(id)`.
- Produces: `GroupSyncWorker.schedule(context)` (unique periodic work `"group-sync"`, 6 h, `NetworkType.CONNECTED`, `KEEP`), and `AppContainer.requestGroupSync(playlistId: String)` (cancels any pending request for that id, waits **3 s**, then `sync`).

- [ ] **Step 1: Implement** the worker (`syncAll()`, always `Result.success()`, since failures are retried next time), scheduling at app start next to `SongReminders.schedule`, the debounce, and the `onStart` call.
- [ ] **Step 2: Verify by running the app** (no mock-heavy tests, per CLAUDE.md): on a phone, edit a group playlist, and the server log (`GET /api/g/<code>?since=0`) shows the step within ~5 s; `adb shell dumpsys jobscheduler | grep -i group` lists the periodic job.
- [ ] **Step 3: Commit** `feat(groups): sync on start, after edits, and every 6 hours`.

---

### Task 7: Screens and links

**Files:**
- Modify: `app/src/main/java/com/mero/ui/playlist/PlaylistScreens.kt` (menu items, group icon, "Not synced yet · Retry" row)
- Modify: `app/src/main/java/com/mero/ui/library/LibraryScreen.kt` (group icon on rows with `groupCode`)
- Modify: `app/src/main/java/com/mero/ui/MeroApp.kt` (wiring, join dialog, toasts, sync when a group playlist opens)
- Modify: `app/src/main/AndroidManifest.xml` (`<data android:pathPrefix="/g/" />` in the `meroapp.pages.dev` filter)

**Interfaces:**
- Consumes: Task 5 `GroupSync` API and `failed`; Task 4 `ShareLink.parseGroup/shortGroup`; existing `sendCard`/`playlistCard` in `ShareCards.kt` (add `suspend fun shareGroup(context, name, songs, ownCover, link)` there, sending the card with caption `"$name - group playlist on Mero\n$link"`).
- Produces (copy, exact):
  - Menu (ordinary playlist): **Make it a group playlist** → dialog title "Make it a group playlist?", text "Anyone you send the link to can add and remove songs, and everyone's copy stays the same.", buttons **Make group** / **Cancel**; then the share sheet.
  - Menu (group playlist): **Invite friends**, **Leave group** (dialog "Leave this group? Your songs stay here as an ordinary playlist." **Leave** / **Cancel**). **Share playlist** stays.
  - Group icon: `Icons.Rounded.Group`, 16 dp, after the name.
  - Failed row: "Not synced yet" + **Retry** (calls `sync`).
  - Join dialog (from a `/g/` link): title = group name, text "A group playlist: N songs. Join to add and remove songs together.", buttons **Join**, **Just add a copy**, **Not now**. Already joined → opens it, toast "You're already in this group".
  - Toasts: "Made a group playlist", "Joined ‹name›", "Left the group", "‹name›: N new songs" (only when N > 0), "Couldn't reach Mero's share site. Try again." (make/join failures).

- [ ] **Step 1: Implement** the above. Opening a group playlist screen calls `sync(id)` once. Smart playlists don't show the group items. Picking a cover on a group playlist calls `pushCover(id)` after saving it; clearing a cover stays on this phone only ("no cover" is not a picture to share).
- [ ] **Step 2: Build and run on two devices** (phone + emulator, or both phones): make a group on one, join on the other via the link, add on each, remove on each, edit offline on one then reconnect; both end with the same songs; reorder on one doesn't move the other; leave keeps the songs. Check the "Not synced yet" row appears in airplane mode after a failed sync and Retry clears it.
- [ ] **Step 3: Commit** `feat(groups): make, join, invite, leave; group icon and sync status`.

---

### Task 8: Docs, version, release

**Files:**
- Modify: `README.md` (feature line; Privacy: "A group playlist's name, song ids and cover are kept on Mero's share site so everyone's copy stays the same. Nothing about the people in it is stored.")
- Modify: `SECURITY.md` (same data note)
- Modify: `docs/architecture.md` (decision record: "Mero's share site", alternatives: no server / a hosted backend / peer-to-peer; revisit trigger: D1 or KV storage past half the free tier, or more than ~50 users)
- Modify: `app/build.gradle.kts` (`versionCode` +1, `versionName = "1.15.0"`)
- Create: `docs/release-notes-v1.15.0.md` (friends' language, same layout as `release-notes-v1.14.0.md`)

- [ ] **Step 1: Write the docs and bump the version.**
- [ ] **Step 2: Full test suite and release build:** `./gradlew :app:testDebugUnitTest :app:assembleRelease` → BUILD SUCCESSFUL; `apksigner verify --print-certs` on each APK → SHA-256 `69503704057dd39bae34323a7a77a9626337795ca50ffe407702885864512200`.
- [ ] **Step 3: Install the release APK on a real phone** and repeat Task 7 Step 2's two-device check with the release build (R8).
- [ ] **Step 4: Commit, tag `v1.15.0`, push, and publish** with the `ship-release` skill (title "Mero v1.15.0", notes from the file, the four APKs).
