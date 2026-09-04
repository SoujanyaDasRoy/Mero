**Mero 1.2.0 — playlists, sleep timer, and live lyrics.**

A free, ad-free music player for Android. Streams from YouTube Music, no account, no ads, no subscription.

---

## Download

| File | Who it's for |
|---|---|
| **`app-arm64-v8a-debug.apk`** | **Every modern Android phone — take this one** |
| `app-armeabi-v7a-debug.apk` | Older 32-bit devices |
| `app-x86_64-debug.apk` | Emulators |

**Installing:** open the APK on your phone, allow "install unknown apps" when Android asks, and install. Requires **Android 9 or newer**.

Upgrading from 1.1.0 or 1.0.x installs over the top — your liked songs, history and queue are kept.

---

## Playlists

The big one. You can finally make your own.

- Create, rename and delete playlists
- Add any song from the **⋮** menu wherever tracks are listed — search, library, anywhere
- Play, shuffle, or remove tracks from inside a playlist
- New **Playlists** tab in Library; each playlist borrows cover art from its first track

## Sleep timer

- 15, 30, 45, 60 or 90 minutes, or **stop at the end of the current track**
- Live countdown, cancel any time
- Keeps running with the player closed — the point is that the phone is face down
- Reachable from the moon button in the player, or from Settings

## Live lyrics

- Synced lyrics that scroll with the song, current line highlighted
- Tap any line to jump there
- Falls back to unsynced lyrics when nobody has timed the track, and tells you which you're getting

---

## Also new

**The player takes its colour from the album art.** Now Playing's background is pulled from the cover and fades in as the track changes.

**Sharper cover art.** Mero was showing YouTube's 60–120px thumbnails; it now asks for 544px. Costs nothing extra — the size is just part of the URL.

**Titles and credits scroll** instead of being cut off mid-word.

**Eight accent colours** instead of four, including Mero's own yellow.

**Faster.** Cover art now has a real disk cache instead of being re-downloaded while you scroll, and the next track in the queue is prepared in the background so skipping is quick.

**Smaller.** 123 MB → **90 MB**, by dropping an ffmpeg build Mero never used.

**Clear artwork cache** added to Settings.

---

## Everything else

**Playback** — search all of YouTube Music, background playback with media notification and lock-screen controls, Bluetooth/headset/Android Auto, queue with auto-advance, shuffle, repeat, drag-to-reorder, and a readout of the real stream quality (`Opus · 160 kbps`).

**Discovery** — search suggests as you type; home feed of real, playable tracks reshuffled from 44 genres and moods, with infinite scroll.

**Library** — liked songs, recently played, most played, and now playlists. All stored on your device.

**Sound** — 10-band equalizer with presets, preamp with automatic headroom compensation so boosting bands doesn't clip, loudness normalization, spatial audio where supported, gapless playback, silence skipping.

---

## Not built yet

Offline downloads, Spotify and YouTube playlist import, and scrobbling.

---

## Things worth knowing

**This build is debug-signed.** It installs and runs normally, but Android treats a debug signature as a different app from a release one. When a properly signed build ships later, you'll need to uninstall first, and your library won't carry over.

**The first song after installing takes a few seconds.** Extraction is real work. Later plays are quicker, and anything already played in the session starts instantly.

**Quality tops out at Opus ~160 kbps** — YouTube's ceiling without a Premium account. It sounds better than the number implies; Opus at 160 is broadly comparable to MP3 at 320, because it's a much more efficient codec.

**No Dolby Atmos.** It needs a Dolby licence, phone-maker firmware support and Atmos-encoded source audio, and YouTube serves stereo Opus. The spatial audio toggle is the real equivalent Android offers.

**Some phones kill background playback.** Xiaomi, Oppo, Vivo, Realme and OnePlus are aggressive about this regardless. If music stops when the screen goes off, exempt Mero from battery optimisation.

---

## Legal

Mero streams through YouTube's internal API, which violates YouTube's Terms of Service. It's non-commercial, serves no ads, and is meant for private use. It does not circumvent DRM and does not touch Spotify's protected audio. If that isn't for you, don't install it.

**GPL-3.0.** Built on [InnerTune](https://github.com/z-huang/InnerTune), [yt-dlp](https://github.com/yt-dlp/yt-dlp), [youtubedl-android](https://github.com/yausername/youtubedl-android) and [LRCLIB](https://lrclib.net).
