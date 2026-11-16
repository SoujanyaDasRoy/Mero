**Mero 1.2.1 — fixes the songs that never started.**

A bug-fix release on top of [1.2.0](https://github.com/SoujanyaDasRoy/Mero/releases/tag/v1.2.0). Install it over the top; nothing is lost.

---

## Download

Take **`app-arm64-v8a-debug.apk`** unless you know you need one of the others. Requires Android 9 or newer.

---

## Fixed: tracks stuck buffering forever

1.2.0 added a background prefetch that warms the *next* track's URL while the current one plays. Three things were wrong with it, and together they were enough to stop playback starting at all:

- **The prefetch restarted constantly.** It was keyed on the queue list rather than the next track's id, and the queue is a fresh list object on every database emit. Every emit fired another extraction.
- **Extractions ran on top of each other.** Each one spawns a Python subprocess. Several at once on a phone starve each other, and the track that is supposed to be playing waits behind them.
- **yt-dlp self-updated on every launch.** The update rewrites the same directory extraction reads from, so the first play after opening the app could race a half-written binary.

Now: one extraction at a time, the prefetch steps aside whenever the playing track needs to resolve, and the self-update runs at most once a day.

## Fixed: the spinner that never ended

Extraction had no time limit. If the subprocess hung, the player waited on it forever — no error, no retry, just a spinner. It now gives up after 45 seconds, retries once by itself, and if that also fails it tells you instead of spinning. Pressing play tries again.

## Fixed: Title Case

Shelf headings were sentence case — "Bollywood hits", "Lo-fi beats". Now "Bollywood Hits", "Lo-Fi Beats", "EDM Bangers", "R&B", "90s Bollywood".

## Also

Settings showed "Mero 1.0.0" and a placeholder source link. Both corrected.

---

## About FLAC

Mero can't serve FLAC, and no build of it will. YouTube only ever sends Opus or AAC — there is no lossless stream on the other end to ask for. Converting Opus to FLAC afterwards would multiply the file size several times over while recovering nothing: the detail is already gone before Mero sees the audio.

Opus at ~160 kbps is roughly equivalent to MP3 at 320 — the number looks small because Opus is a far more efficient codec than the ones that number came from.

---

**GPL-3.0.** Built on [InnerTune](https://github.com/z-huang/InnerTune), [yt-dlp](https://github.com/yt-dlp/yt-dlp), [youtubedl-android](https://github.com/yausername/youtubedl-android) and [LRCLIB](https://lrclib.net).
