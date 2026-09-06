<div align="center">

<img src="mero.png" alt="Mero" width="140" />

# Mero

**A free, ad-free music player for Android.**

Streams from YouTube Music · imports from Spotify · offline downloads · user & smart playlists · lyrics · real DSP · no account, ever.

</div>

---

## What it does

Mero plays music from YouTube Music without ads, without an account, and without
paying for anything. It is built for a small group of friends, not for an app
store.

### Features

**Playback**
- Instant stream resolution via embedded `yt-dlp` using YouTube's direct `/player` API
- Resilient byte-range streaming with dynamic URL range parameter handling
- Extended 5-minute buffer duration with 500 ms fast startup for continuous playback
- Background playback via `MediaSessionService` with lock-screen controls and notification media controls
- Bluetooth, wired headset, and Android Auto controls
- Pauses when headphones are unplugged, ducks audio for incoming calls
- Auto-advance through the queue, plus shuffle and repeat
- Displays exact stream format and bitrate (e.g. `Opus · 160 kbps`)

**Search & Discovery**
- **Active search as you write** with live, debounced search results across YouTube Music
- **Infinite scrolling** search results — seamlessly load page after page of songs, artists, albums, and playlists
- Clean search interface free of history clutter, dedicated entirely to displaying music
- Interactive "Browse Music & Genres" topics grid when the search bar is idle
- Home feed reshuffles from 44 genres and moods on every refresh, with fast 2-seed startup rendering

**Library & Playlists**
- Liked songs, recently played, and most played track lists
- Full queue management with drag-to-reorder
- User-created custom playlists
- **Smart Playlists** — auto-generating playlists driven by custom rules, play counts, and artist filters
- **Playlist Import** — import playlists directly from YouTube Music or Spotify links

**Offline Downloads**
- On-disk track downloading with isolated download cache
- Export downloaded audio files to custom folders via Android's Storage Access Framework (SAF)
- Offline playback requiring zero network connectivity

**Lyrics**
- Synced and plain lyrics fetched lazily on demand

**Sound & DSP**
- 10-band equalizer built on `DynamicsProcessing`, with built-in presets
- Preamp with automatic headroom compensation to prevent clipping
- Loudness normalization
- Spatial audio modes where supported by the device

**Look & Feel**
- Material 3 throughout, with four accent palettes
- Material You dynamic color support (Android 12+)
- Pure-black AMOLED dark mode
- Three selectable Now Playing sheet layouts

---

## Install

Grab the APK from the [latest release](../../releases/latest).

**Which file?**

| File | For |
|---|---|
| `app-arm64-v8a-*.apk` | **Every modern Android phone — take this one** |
| `app-armeabi-v7a-*.apk` | Older 32-bit devices |
| `app-x86_64-*.apk` | Emulators |

Then on your phone:

1. Open the downloaded APK
2. Android will ask permission to install from that app — allow it
3. Install, open, and search for something

**Requires Android 9 (API 28) or newer.**

> The first track you play unpacks and updates its extraction runtime once on first use. After that, stream resolution happens quickly, and tracks played in the same session start instantly.

---

## Build it yourself

```bash
git clone https://github.com/SoujanyaDasRoy/Mero.git
cd Mero
./gradlew :app:assembleDebug
```

Needs JDK 17 or 21 and the Android SDK. Gradle fetches a JDK 17 toolchain
automatically for the vendored `innertube` module.

Output lands in `app/build/outputs/apk/debug/`, split per architecture.

Run unit tests with:

```bash
./gradlew :app:testDebugUnitTest
```

---

## How it works

Mero is composed of two Gradle modules: `innertube/`, vendored unmodified from
[z-huang/InnerTune](https://github.com/z-huang/InnerTune), and `app/`, which contains
all application logic, UI, and playback services.

- **Search and metadata** come from YouTube's InnerTube API anonymously.
- **Stream URLs** are resolved by an embedded [yt-dlp](https://github.com/yt-dlp/yt-dlp) binary,
  configured with `youtube:skip=hls,dash,translated_subs,webpage` for direct `/player` API resolution.
- **Resilient Range Streaming** uses Media3's `ResolvingDataSource` to dynamically append `&range=...` parameters to signed `googlevideo.com` CDN URLs, preventing HTTP 403 Forbidden errors when buffering across byte boundaries.
- **Dual-Cache Architecture** separates temporary streaming cache (`cacheDir`) from persistent offline downloads (`filesDir`).
- **Stream URLs are never stored.** They expire in roughly six hours, so Mero stores only the `videoId` and resolves a fresh CDN URL at playback-open time.
- **No Google sign-in, ever.** Signing in would risk account rate-limiting or suspension. Spotify integration is strictly metadata-based; imported Spotify tracks are resolved directly to YouTube streams.

Full rationale and architectural decisions are documented in [`docs/architecture.md`](docs/architecture.md).

---

## Known limitations

- **Maximum quality is Opus ~160 kbps.** That's YouTube's ceiling without a Premium account. Opus at 160 kbps delivers quality comparable to 320 kbps MP3 due to superior compression efficiency.
- **Dolby Atmos is not supported.** It requires hardware OEM licensing and Atmos-encoded source streams; YouTube serves stereo audio. Mero provides software spatial audio DSP instead.
- **Extraction updates.** YouTube periodically modifies its backend endpoints. The embedded `yt-dlp` runtime self-updates daily to maintain stream extraction capability.
- **Background process management on certain OEMs.** Aggressive battery optimization on brands like Xiaomi, Oppo, Vivo, and OnePlus may terminate background services. Exempt Mero from battery optimization if background playback stops when the screen is turned off.

---

## Legal

Mero streams audio through YouTube's internal API. It is non-commercial, serves no ads, and is distributed privately. It does not circumvent DRM, nor does it access protected audio from Spotify.

---

## Security

The `AIzaSy…` strings in `innertube/` are YouTube's public client keys, not secret credentials — see [`SECURITY.md`](SECURITY.md) for details.

## Credits

- [z-huang/InnerTune](https://github.com/z-huang/InnerTune) — `innertube` module
- [yt-dlp](https://github.com/yt-dlp/yt-dlp) and [youtubedl-android](https://github.com/yausername/youtubedl-android) — stream extraction
- [Calvin-LL/Reorderable](https://github.com/Calvin-LL/Reorderable) — queue drag-and-drop

## License

**GPL-3.0** — see [`LICENSE`](LICENSE).
