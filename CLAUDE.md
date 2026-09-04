# Mero

Free, ad-free Android music player. Streams YouTube Music audio through a
vendored anonymous extraction library, imports playlists from Spotify and
YouTube, plays offline, ships a real DSP suite. Private distribution to ~10
friends. Non-commercial.

**Spec:** `docs/superpowers/specs/2026-09-04-mero-v1-prd.md`
**Architecture and rationale:** `docs/architecture.md`
**Plans:** `docs/superpowers/plans/`

Read `docs/architecture.md` before proposing anything structural. Every decision
there records its alternatives and a revisit trigger; several look wrong without
the rationale, and a few look like ceremony worth deleting until you read why
they exist.

## Hard constraints

Violating any of these is a bug, not a style disagreement.

1. **No Google sign-in. Ever.** All InnerTube requests are anonymous — no auth
   header, no cookie jar, no account picker. Signing in moves the failure mode
   from "an IP got rate-limited" to "a friend lost their Google account." The
   cost is no personalized recommendations and no YouTube library sync. That
   trade is deliberate; do not quietly reverse it. (Spec §3.1)

2. **Never persist a stream URL.** They expire in roughly six hours. Persist
   `videoId` only and resolve at open time via `ResolvingDataSource.Resolver`.
   (Spec §6)

3. **Never edit `innertube/`.** It is a git subtree vendored from
   `z-huang/InnerTune`. Local edits are lost on the next sync and cause merge
   conflicts. Anything Mero needs to change belongs in `:app`, wrapped around
   the module. See `innertube/VENDORED.md`.

4. **Mero is GPL-3.0** — forced by vendoring `innertube`. Keep the `LICENSE`
   file and upstream's copyright notices intact.

5. **Media3 cache keys use `videoId`, not URL.** The default `CacheKeyFactory`
   keys on URI; since URLs rotate every six hours, defaults silently
   re-download everything. Applies from M3 onward.

6. **Spotify is metadata only.** Its audio is Widevine-protected and Mero does
   not touch it. Playlist import resolves each track to a YouTube video.

7. **The player is never a navigation destination.** `NavHost` owns browse
   screens only; the mini-player/Now Playing sheet lives outside it at app
   scope. Making the player a destination breaks back-stack behaviour
   irrecoverably — back would navigate *away from playback*, and the player
   would unmount on every navigation. (`docs/architecture.md`, Part 1)

## Build

```bash
./gradlew :app:assembleDebug          # build
./gradlew :app:testDebugUnitTest      # unit tests
./gradlew :app:assembleRelease        # release — behaves differently, see below
```

minSdk 28, targetSdk 36. Kotlin only, no Java sources.

minSdk 28 is load-bearing: `DynamicsProcessing`, the multiband EQ backing the
whole DSP suite, is API 28+. Do not lower it.

## Architecture

Two modules:

- `innertube/` — vendored, never edited
- `app/` — everything else, packaged by feature

Two modules, not seven. Multi-module Gradle buys build parallelism and team
boundaries; this is one developer and one app, so package-by-feature inside
`:app` gets the same clarity with no build-config tax.

Package root `com.mero`, split `domain/ data/ playback/ ui/<feature>/`.

Playback runs in a `MediaSessionService`; Compose drives it through a
`MediaController`.

**DI:** a hand-written `AppContainer` in `MeroApp.kt`, not Koin. The spec names
Koin; M1 deviates deliberately because the object graph is three objects.
Introduce Koin when the graph actually justifies it, not before.

## Testing

Pure logic with real edge cases gets strict TDD — format selection, URI
round-tripping, response mapping, and later the playlist-import resolver.
That's where bugs hide silently.

Compose screens, bound services and ExoPlayer wiring are verified by running
the app. **Do not write mock-heavy tests asserting that ExoPlayer was called** —
they verify nothing and break on every refactor.

## Conventions

- Repositories return `Result<T>`; they do not throw across their boundary
- Domain types (`Song`, etc.) never expose innertube types — the adapter is the
  only place upstream types appear
- Extraction failure is always **visible and retryable** in the UI, never a
  silent hang or an indefinite spinner (Spec §9)
- Commits end with:
  `Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>`

## Two things that will actually break

**YouTube extraction.** PO tokens and signature changes break playback
periodically. This is upstream's problem to fix and ours to merge — use the
`resync-innertube` skill, don't patch around it in `:app`.

**OEM task-killers.** Xiaomi, Oppo, Vivo, Realme and OnePlus kill background
services regardless of foreground-service status. On the phones this group
actually owns, "playback stops when the screen is off" is a battery-settings
problem, not a code problem. Check that before debugging the service.
