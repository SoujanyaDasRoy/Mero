<div align="center">

<img src="mero.png" alt="Mero" width="140" />

# Mero

**A free, ad-free music player for Android.**

Streams from YouTube Music · imports from Spotify · real DSP · no account, ever.

</div>

---

## What it does

Mero plays music from YouTube Music without ads, without an account, and without
paying for anything. It is built for a small group of friends, not for an app
store.

### Features

**Playback**
- Search all of YouTube Music and play any track
- Background playback with a proper media notification and lock-screen controls
- Bluetooth, wired headset and Android Auto controls, free via `MediaSession`
- Pauses when headphones are unplugged, ducks for calls
- Auto-advance through the queue, plus shuffle and repeat
- Shows the real stream quality you're getting, e.g. `Opus · 160 kbps`

**Discovery**
- Home feed of real, playable tracks with real artwork
- Reshuffles from a pool of 44 genres and moods on every refresh, so it's
  different each time
- Infinite scroll — more shelves load as you reach the bottom
- Search with history

**Library** (persists across restarts, stored locally)
- Liked songs
- Recently played
- Most played
- Queue, with drag-to-reorder

**Sound**
- 10-band equalizer built on `DynamicsProcessing`, with presets
- Preamp with automatic headroom compensation, so boosting bands doesn't clip
- Loudness normalization
- Spatial audio where the device supports it
- Gapless playback and silence skipping

**Look**
- Material 3 throughout, four accent palettes
- Material You dynamic colour (Android 12+)
- Pure-black AMOLED mode
- Three selectable Now Playing layouts

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

> The first track you play takes longer than the rest — Mero unpacks and updates
> its extraction runtime once on first use. After that, playback starts quickly,
> and anything you've already played in the session starts instantly.

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

Run the tests with:

```bash
./gradlew :app:testDebugUnitTest
```

---

## How it works

Mero is two Gradle modules: `innertube/`, vendored unmodified from
[z-huang/InnerTune](https://github.com/z-huang/InnerTune), and `app/`, which is
everything else.

- **Search and metadata** come from YouTube's InnerTube API, anonymously.
- **Stream URLs** are resolved by an embedded [yt-dlp](https://github.com/yt-dlp/yt-dlp),
  which updates itself at runtime — so when YouTube changes something, Mero can
  recover without shipping a new APK.
- **Stream URLs are never stored.** They expire in about six hours, so Mero keeps
  only the `videoId` and resolves at the moment playback opens.
- **No Google sign-in, ever.** That's a deliberate constraint, not an oversight:
  signing in would move the failure mode from "an IP got rate-limited" to "someone
  lost their Google account". The cost is no personalised recommendations.
- **Spotify would be metadata only.** Its audio is DRM-protected and Mero does not
  touch it; playlist import resolves each track to YouTube instead.

Full rationale, including the alternatives rejected and when each decision should
be revisited, is in [`docs/architecture.md`](docs/architecture.md).

---

## Not built yet

Honest status — the UI exists for some of these, but they aren't wired up:

- Offline downloads
- Spotify and YouTube playlist import
- Synced lyrics (LRCLIB)
- Scrobbling
- User-created playlists

---

## Known limitations

- **Maximum quality is Opus ~160 kbps.** That's YouTube's ceiling without a
  Premium account. It sounds better than the number suggests — Opus at 160 is
  broadly comparable to MP3 at 320, because it's a far more efficient codec.
- **Dolby Atmos is not possible.** It needs a Dolby licence, OEM firmware support
  and Atmos-encoded source audio. YouTube serves stereo Opus. The spatial audio
  toggle is the real equivalent Android exposes.
- **Extraction breaks periodically.** YouTube actively changes things. yt-dlp's
  runtime self-update usually recovers it.
- **Some phones kill background playback.** Xiaomi, Oppo, Vivo, Realme and
  OnePlus are aggressive about this regardless of foreground-service status. If
  playback stops when the screen goes off, exempt Mero from battery optimisation.

---

## Legal

Mero streams audio through YouTube's internal API, which violates YouTube's Terms
of Service. It is non-commercial, serves no ads, and is distributed privately to
a handful of people. It does not circumvent DRM, and it does not touch Spotify's
protected audio.

If you're not comfortable with that, don't use it.

---

## Credits

- [z-huang/InnerTune](https://github.com/z-huang/InnerTune) — the `innertube` module
- [yt-dlp](https://github.com/yt-dlp/yt-dlp) and
  [youtubedl-android](https://github.com/yausername/youtubedl-android) — stream extraction
- [Calvin-LL/Reorderable](https://github.com/Calvin-LL/Reorderable) — queue drag-and-drop

## License

**GPL-3.0** — see [`LICENSE`](LICENSE). Required, because Mero vendors the
`innertube` module from InnerTune, which is GPL-3.0. `innertube/` is unmodified
upstream code; see [`innertube/VENDORED.md`](innertube/VENDORED.md).
