<div align="center">

<img src="mero.png" alt="Mero" width="140" />

# Mero

**A free, ad-free music player for Android.**

No account. No ads. No subscription.

</div>

---

Mero plays music from YouTube Music, imports playlists from Spotify, downloads
for offline listening, and ships a real DSP suite. It is built for a small group
of friends rather than for an app store, and it never asks anyone to sign in.

**[Download the latest release →](../../releases/latest)**

---

## Features

### Playback

- Streams from YouTube Music — tap a song and it starts in about a second
- Plays in the background, with lock-screen and notification controls
- Works with headset buttons, Bluetooth and Android Auto — including waking the
  app and resuming where you left off when Mero isn't already running
- Queue with drag-to-reorder, shuffle, repeat, and "play next"
- Keeps going with similar tracks when the queue runs out, or pauses after an
  hour of no interaction — your choice
- Playback speed from 0.75× to 2×
- Shows what the stream actually resolved to, e.g. `Opus · 160 kbps`

### A home screen that learns

Mero has no account and never will, so recommendations come from the only record
there is: what you have played on this phone.

- **Jump back in** — the last few songs you played
- **On repeat** — what you keep coming back to
- **More like <song>** — YouTube's own per-track similarity, no identity needed
- **A shelf per artist you play most**

It rebuilds as you listen. Nothing leaves the device, and a fresh install simply
gets the genre feed instead.

### Search

- Results from the first letter, updating as you type
- Songs, albums, artists and playlists, each scrolling on effectively forever
- Genre tiles with real cover art when the search box is empty

### Library

- Liked songs, recently played and most played
- Your own playlists, plus **smart playlists** that fill themselves from rules —
  play count, artist, recency
- **Import** a playlist from a YouTube Music or Spotify link

### Offline

- Download any track for offline playback
- Optionally copy downloads to a folder of your choosing
- Wi-Fi-only downloading, on by default

### Sound

All processing happens inside Mero rather than being handed to the device, so it
behaves identically on every phone.

- **Ten-band parametric equalizer you drag** — each band moves in frequency and
  width, not just volume, with the spectrum of what's playing drawn behind it
- **Separate profiles** for headphones, speaker and car, switched automatically
  or pinned by hand
- **Presets** including Extended (both ends lifted, midrange untouched) and Less
  Bass, alongside the usual genre curves
- **Crossfeed** for headphones — stops hard-panned recordings happening inside
  your head
- **Loudness matching** measured the way broadcasters do it, so a quiet
  recording doesn't vanish after a loud one
- **Preamp with automatic headroom**, so boosting never clips
- **Beat haptics**, driven by Mero's own audio rather than the microphone

### Lyrics

Synced and plain lyrics, fetched on demand from LRCLIB.

### Look and feel

- Material 3, with twelve accent colours and Material You on Android 12+
- System / Light / Dark, plus pure black for AMOLED panels
- Four Now Playing layouts — Classic, Immersive, Up Next and One-handed
- Manrope throughout

### Updates

Mero is not on the Play Store, so it checks GitHub itself when it opens. When
there is a new version, Home shows a banner: **Download**, then **Install**.
Android asks once for permission to install apps. The file is also kept in your
Downloads folder in case you would rather tap it there.

---

## Install

Grab the APK from the [latest release](../../releases/latest).

| File | For |
|---|---|
| `mero-<version>-arm64-v8a-debug.apk` | **Every modern Android phone — take this one** |
| `mero-<version>-armeabi-v7a-debug.apk` | Older 32-bit devices |
| `mero-<version>-x86_64-debug.apk` | Emulators |
| `mero-<version>-x86-debug.apk` | Old 32-bit emulators |

Then on your phone:

1. Open the downloaded APK.
2. Allow Android to install apps from wherever you opened it.
3. Play something.

**Requires Android 9 (API 28) or newer.**

> Every release is signed with the same key. Installing over a previous version
> keeps your library, playlists and downloads.

---

## Build it yourself

```bash
git clone https://github.com/SoujanyaDasRoy/Mero.git
cd Mero
./gradlew :app:assembleDebug
```

Needs the Android SDK and **JDK 17 or 21** — not 25, which the Android Gradle
Plugin does not yet accept.

APKs land in `app/build/outputs/apk/debug/`, split per architecture and named
after the version inside them.

```bash
./gradlew :app:testDebugUnitTest
```

---

## How it works

Two Gradle modules: `innertube/`, vendored unmodified from
[z-huang/InnerTune](https://github.com/z-huang/InnerTune), and `app/`, which holds
everything else.

**Search and metadata** come from YouTube's InnerTube API, anonymously.

**Stream URLs** are resolved by asking YouTube directly, with an embedded
[yt-dlp](https://github.com/yt-dlp/yt-dlp) as the fallback when that fails. The
direct path is roughly ten times faster; the fallback is what keeps playback
working when YouTube changes something.

**Stream URLs are never stored.** They expire in about six hours, so Mero keeps
only the `videoId` and resolves a fresh URL each time a track opens.

**Two caches**, deliberately separate: temporary streaming in `cacheDir`, and
downloads you asked for in `filesDir`, so clearing one never touches the other.

**Audio processing runs inside ExoPlayer's own pipeline** — biquad filters,
crossfeed, loudness measurement and limiting, all Mero's own code. The Android
`audiofx` effects it used to rely on were rejected outright by some devices and
degraded playback on others.

**No Google sign-in, ever.** Signing in would move the failure mode from "an IP
got rate-limited" to "a friend lost their Google account". Spotify is metadata
only — its own audio is DRM-protected and Mero does not touch it; imported
tracks are matched to YouTube.

Full rationale, including the alternatives rejected and why, is in
[`docs/architecture.md`](docs/architecture.md).

---

## Known limitations

- **Quality tops out at Opus ~160 kbps.** That is YouTube's ceiling without a
  signed-in Premium account, which is the one thing Mero will not do. There is
  no lossless stream to ask for.
- **No Dolby Atmos.** It needs OEM licensing and Atmos-encoded sources; YouTube
  serves stereo.
- **Extraction breaks periodically.** YouTube changes its backend; the bundled
  yt-dlp updates itself to keep up, and occasionally a new release is needed.
- **Some OEMs kill background playback.** Xiaomi, Oppo, Vivo, Realme and OnePlus
  are aggressive about it regardless of foreground-service status. Settings →
  Background playback settings goes straight to the exemption screen.
- **A car cannot be told apart from earbuds automatically.** Both connect over
  Bluetooth A2DP, and distinguishing them needs a permission Mero has no other
  use for. Pin the Car profile in the equalizer instead.

---

## Legal

Mero streams audio through YouTube's internal API. It is non-commercial, carries
no advertising, and is distributed privately. It does not circumvent DRM and does
not access protected audio from Spotify.

## Security

The `AIzaSy…` strings in `innertube/` are YouTube's public client keys, not
secret credentials — see [`SECURITY.md`](SECURITY.md).

## Credits

- [z-huang/InnerTune](https://github.com/z-huang/InnerTune) — the `innertube` module
- [yt-dlp](https://github.com/yt-dlp/yt-dlp) and [youtubedl-android](https://github.com/yausername/youtubedl-android) — fallback stream extraction
- [LRCLIB](https://lrclib.net) — lyrics
- [Calvin-LL/Reorderable](https://github.com/Calvin-LL/Reorderable) — queue drag-and-drop
- [JTransforms](https://github.com/wendykierp/JTransforms) — FFT for the spectrum display
- [Manrope](https://github.com/sharanda/manrope) — typeface, SIL OFL

## License

**GPL-3.0** — see [`LICENSE`](LICENSE).
