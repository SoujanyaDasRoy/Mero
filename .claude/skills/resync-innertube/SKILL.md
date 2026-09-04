---
name: resync-innertube
description: Use when YouTube extraction breaks in Mero — search returns empty, playback fails with 403 or "No playable audio format", tracks stop resolving, or upstream z-huang/InnerTune has shipped a fix. Re-syncs the vendored innertube git subtree from upstream.
---

# Re-syncing the innertube subtree

YouTube changes its signature scheme and PO token requirements periodically.
When it does, extraction breaks for every app in this category at once, upstream
fixes it, and Mero merges the fix. That merge is this skill.

**The fix is never written in `:app`.** Patching around broken extraction
locally guarantees a merge conflict on the next sync and leaves the real bug in
place. If upstream has not fixed it yet, wait or escalate (see below) — do not
reimplement extraction.

## First: confirm it is actually extraction

Three unrelated failures look identical from the user's side. Check in this
order, cheapest first:

1. **OEM task-killer** — playback stops only when the screen is off, on a
   Xiaomi/Oppo/Vivo/Realme/OnePlus device. Battery settings, not extraction.
   Nothing here will help.
2. **No network / airplane mode** — obvious, still worth ruling out.
3. **Extraction** — search returns empty for queries that certainly have
   results, or Logcat shows `IllegalStateException: No playable audio format`,
   or stream requests return HTTP 403.

Only 3 is this skill's problem.

## Check whether upstream has fixed it

```bash
git clone --depth=50 https://github.com/z-huang/InnerTune.git /tmp/innertune
cd /tmp/innertune && git log --oneline -20 -- innertube/
```

Look for recent commits touching extraction, signature handling, PO tokens or
client context. Also check upstream's open issues — if the breakage is fresh,
a fix may not exist yet.

**If upstream has no fix:** stop. Report that to the user and offer the
escape hatch below. Do not improvise one.

## Re-sync

From the Mero repo root, with a clean working tree:

```bash
cd /tmp/innertune
git subtree split --prefix=innertube -b innertube-only

cd <mero repo root>
git fetch innertube-upstream innertube-only
git subtree pull --prefix=innertube innertube-upstream innertube-only --squash
```

If the `innertube-upstream` remote is missing (fresh clone of Mero):

```bash
git remote add innertube-upstream /tmp/innertune
```

The `subtree split` step is required because `git subtree` operates on whole
repositories — `--prefix` names the destination in *our* repo, not a
subdirectory of the source. Skipping the split pulls all of InnerTune in.

## Verify

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

Compilation failures after a sync are usually one of:

- **Changed method signatures** in the module's public API. Fix the adapter in
  `app/data/` — `SearchRepository` and `StreamRepository` are the only files
  that should ever touch upstream types.
- **A new transitive dependency** upstream added. Add it to `:app`.
- **A missing Ktor engine or serialization plugin.** Add to `:app`.

Never fix any of these by editing `innertube/`.

Then verify on a device, since a green build proves nothing about extraction:

1. Search a well-known song — expect non-empty results
2. Play it — expect audio within a few seconds
3. Let it play past 30 seconds — expect no mid-stream failure

## Commit

```bash
git add -A
git commit -m "chore: resync innertube subtree

Fixes <symptom>. Upstream: <commit sha or release>.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

Then ship it — friends are currently running a broken build. Use the
`ship-release` skill.

## Escape hatch when upstream is also broken

The spec (§9) defers a fallback extractor: `youtubedl-android` embeds yt-dlp
and calls `YoutubeDL.updateYoutubeDL()` at runtime, so extraction fixes arrive
**without shipping an APK**. Costs roughly +30 MB.

This was deliberately deferred until breakage became annoying enough to justify
it. If upstream is repeatedly slow to fix, that condition has been met — raise
it with the user as a scoped piece of work, don't add it mid-emergency.
