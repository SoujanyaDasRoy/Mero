**Mero 1.3.0 — songs you've heard before now start instantly.**

Install over the top of any earlier version. Nothing is lost.

---

## Download

Take **`app-arm64-v8a-debug.apk`** unless you know you need one of the others. Requires Android 9 or newer.

---

## Playback is cached now

Mero keeps the actual audio on disk, up to 512 MB, evicting whatever you played least recently.

- **A song you've played before starts immediately.** No extraction, no download — measured on a fresh launch, playing from the first frame in under two seconds.
- **The next track in the queue is pre-downloaded** while the current one plays, so skipping forward is instant too.
- Playback also starts sooner in general: the player used to wait for 2.5 seconds of buffered audio before making a sound, which is dead time on top of everything else. It's now half a second.

The cache is keyed on the video id rather than the URL. That sounds like a detail; it isn't. YouTube's URLs rotate every few hours, so keying on the URL — which is what the library does by default — would quietly treat every play of the same song as a brand new file and re-download all of it.

**What is still slow:** the very first play of a song Mero has never seen. That costs a yt-dlp extraction, around 8–10 seconds, and nothing in this release removes it. It's a real subprocess doing real work against YouTube's anti-bot machinery. Everything after that first play is fast.

## Search returns far more

Search used to stop at one page — roughly twenty tracks. It now follows YouTube's continuation tokens up to five pages, so a broad query like "arijit singh" comes back with the better part of a hundred results instead of running out immediately.

## Search suggestions

- Suggestions appear faster while typing (150 ms after you stop, down from 250 ms)
- Both suggested searches and suggested songs, as before
- **Recent searches now survive closing the app.** They were kept in memory only, so they vanished on every restart — which rather defeats the point of a recent list.

## Also

**Format selection is more forgiving.** Mero required a format's id to be a plain number and threw away anything else. YouTube labels some streams `251-drc` and similar, so those were being silently discarded. It now reads the leading digits.

**Clear cache** in Settings now clears cached audio as well as cover art.

---

## Why not FLAC

Asked, and worth answering properly: Mero can't serve FLAC, and no version of it will.

YouTube only ever sends Opus or AAC. There is no lossless stream on the other end to request. Converting Opus to FLAC after the fact would multiply the file size several times over and recover nothing — the detail is gone before Mero ever sees the audio.

Opus at ~160 kbps is roughly equivalent to MP3 at 320. The number looks small because Opus is a much more efficient codec than the one that number comes from.

Lossless needs a lossless source: Tidal, Qobuz, Apple Music, or your own rips. Playing local FLAC files off the phone is a feature Mero *could* have — ask if you want it.

---

**GPL-3.0.** Built on [InnerTune](https://github.com/z-huang/InnerTune), [yt-dlp](https://github.com/yt-dlp/yt-dlp), [youtubedl-android](https://github.com/yausername/youtubedl-android) and [LRCLIB](https://lrclib.net).
