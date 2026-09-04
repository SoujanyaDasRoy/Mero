**Mero 1.0.0 — the first build.**

A free, ad-free music player for Android. Streams from YouTube Music, no account, no ads, no subscription.

---

## Download

| File | Who it's for |
|---|---|
| **`app-arm64-v8a-debug.apk`** | **Every modern Android phone — take this one** |
| `app-armeabi-v7a-debug.apk` | Older 32-bit devices |
| `app-x86_64-debug.apk` | Emulators |

**Installing:** open the APK on your phone, allow "install unknown apps" when Android asks, and install. Requires **Android 9 or newer**.

---

## What's in it

**Playback**
- Search all of YouTube Music and play anything
- Background playback with media notification and lock-screen controls
- Bluetooth, wired headset and Android Auto support
- Pauses when you unplug headphones, ducks for calls
- Queue with auto-advance, shuffle, repeat, and drag-to-reorder
- Shows the real stream quality, e.g. `Opus · 160 kbps`

**Discovery**
- Home feed of real, playable tracks with real artwork
- Reshuffles from 44 genres and moods every refresh — different each time
- Infinite scroll, more shelves load as you go
- Search with history

**Library** — persists across restarts, stored on your device
- Liked songs, recently played, most played

**Sound**
- 10-band equalizer with presets, built on `DynamicsProcessing`
- Preamp with automatic headroom compensation, so boosting bands doesn't clip
- Loudness normalization, spatial audio, gapless playback, silence skipping

**Look**
- Material 3, four accent palettes, Material You dynamic colour, AMOLED black
- Three selectable Now Playing layouts

---

## Not built yet

The UI exists for some of these, but they aren't wired up:

- Offline downloads
- Spotify and YouTube playlist import
- Synced lyrics
- Scrobbling
- User-created playlists

---

## Things worth knowing

**This build is debug-signed.** It installs and runs normally, but Android treats a debug signature as a different app from a release one. When a properly signed build ships later, you'll need to uninstall this first — and your library won't carry over.

**The first song you play is slow.** Mero unpacks and updates its extraction runtime once, on first use. Later plays start quickly, and anything already played in the same session starts instantly.

**Quality tops out at Opus ~160 kbps.** That's YouTube's ceiling without a Premium account. It sounds better than the number implies — Opus at 160 is broadly comparable to MP3 at 320, because it's a much more efficient codec.

**No Dolby Atmos.** It needs a Dolby licence, phone-maker firmware support and Atmos-encoded source audio, and YouTube serves stereo Opus. The spatial audio toggle is the real equivalent Android offers.

**Extraction breaks from time to time.** YouTube changes things on purpose. Mero's extractor updates itself at runtime, which usually recovers it without a new APK.

**Some phones kill background playback.** Xiaomi, Oppo, Vivo, Realme and OnePlus are aggressive about this no matter what. If music stops when the screen goes off, exempt Mero from battery optimisation in system settings.

---

## Legal

Mero streams through YouTube's internal API, which violates YouTube's Terms of Service. It's non-commercial, serves no ads, and is meant for private use. It does not circumvent DRM and does not touch Spotify's protected audio. If that isn't for you, don't install it.

**GPL-3.0.** Built on [InnerTune](https://github.com/z-huang/InnerTune), [yt-dlp](https://github.com/yt-dlp/yt-dlp) and [youtubedl-android](https://github.com/yausername/youtubedl-android).
