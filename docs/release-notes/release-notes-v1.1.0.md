**Mero 1.1.0 — search as you type, and three bugs gone.**

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

## New

**Search suggests as you type.** Previously you had to finish the query and press search. Now typing `night` immediately offers completions — *night changes*, *night we met*, *nightcall* — alongside matching songs with artwork you can play straight from the list.

**New app logo,** rendered larger across the launcher icon, the splash screen and the home header.

---

## Fixed

**The progress bar ran ahead of the audio.** It was dividing the player's real position by the *metadata* duration reported by YouTube's API, which often disagrees with the actual stream. It now uses the duration the player itself reports.

**The first song after installing wouldn't play.** The play button spun, eventually failed, and only worked on a second press. Mero was unpacking its extraction runtime *and* downloading a yt-dlp update on the playback thread, so the first play blocked on a network download until it gave up — and the second press only worked because that download had already been marked done. Both now happen in the background when the app starts, well before you press anything. A failed load also recovers on its own instead of leaving the button spinning forever.

---

## Everything else

**Playback** — search all of YouTube Music, background playback with media notification and lock-screen controls, Bluetooth/headset/Android Auto, queue with auto-advance, shuffle, repeat and drag-to-reorder, and a readout of the real stream quality (`Opus · 160 kbps`).

**Discovery** — home feed of real, playable tracks with real artwork, reshuffled from 44 genres and moods on every refresh, with infinite scroll.

**Library** — liked songs, recently played, most played. Persists across restarts, stored on your device.

**Sound** — 10-band equalizer on `DynamicsProcessing` with presets, preamp with automatic headroom compensation so boosting bands doesn't clip, loudness normalization, spatial audio where supported, gapless playback, silence skipping.

**Look** — Material 3, four accent palettes, Material You dynamic colour, AMOLED black, three selectable Now Playing layouts.

---

## Not built yet

The UI exists for some of these, but they aren't wired up: offline downloads, Spotify and YouTube playlist import, synced lyrics, scrobbling, and user-created playlists.

---

## Things worth knowing

**This build is debug-signed.** It installs and runs normally, but Android treats a debug signature as a different app from a release one. When a properly signed build ships later, you'll need to uninstall this first, and your library won't carry over.

**The first song still takes a few seconds.** Extraction is real work. Subsequent plays are quicker, and anything already played in the same session starts instantly.

**Quality tops out at Opus ~160 kbps** — YouTube's ceiling without a Premium account. It sounds better than the number implies; Opus at 160 is broadly comparable to MP3 at 320, because it's a much more efficient codec.

**No Dolby Atmos.** It needs a Dolby licence, phone-maker firmware support and Atmos-encoded source audio, and YouTube serves stereo Opus. The spatial audio toggle is the real equivalent Android offers.

**Some phones kill background playback.** Xiaomi, Oppo, Vivo, Realme and OnePlus are aggressive about this regardless. If music stops when the screen goes off, exempt Mero from battery optimisation.

---

## Legal

Mero streams through YouTube's internal API, which violates YouTube's Terms of Service. It's non-commercial, serves no ads, and is meant for private use. It does not circumvent DRM and does not touch Spotify's protected audio. If that isn't for you, don't install it.

**GPL-3.0.** Built on [InnerTune](https://github.com/z-huang/InnerTune), [yt-dlp](https://github.com/yt-dlp/yt-dlp) and [youtubedl-android](https://github.com/yausername/youtubedl-android).
