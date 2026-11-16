**Mero 1.4.0 — offline downloads, playlist import, and controls that actually work.**

Install over any earlier version. Nothing is lost.

---

## Download

Take **`app-arm64-v8a-debug.apk`** unless you know you need another. Requires Android 9 or newer.

---

## The player was only ever holding one track

This is the fix underneath most of the rest.

Mero handed the player a single song at a time and re-implemented everything else in the app's UI code. Because the player could only see one track, Android was told there was no "next" — so the notification, lock screen, Bluetooth buttons, car and watch controls all collapsed to play/pause. Repeat and shuffle were buttons that changed an icon and nothing else. And auto-advance only worked while the app's screen existed.

The player now gets the whole queue.

- **Notification and lock-screen controls have skip forward and back.** Measured: the media session's advertised actions gained `SKIP_TO_NEXT`, and the hardware next button now changes track.
- **Repeat works, including repeat-one.** Tap the repeat button twice for "keep playing this song".
- **Shuffle works**, as the player's own mode rather than a random pick.
- **Previous** goes to the previous track in the first three seconds and restarts the current one after that, like every other player.
- Auto-advance is the player's job now, so it keeps working with the app closed.

## Offline downloads

- **Download** in the ⋮ menu on any track; **Downloads** tab in Library.
- Downloads live in their own store with no eviction, separate from the streaming cache, so nothing you asked to keep gets thrown away to make room for something you streamed.
- A downloaded track never touches the network. Verified with wifi *and* mobile data switched off.

## Import playlists

**Settings → Import a playlist.** Paste a link.

- **YouTube Music** imports directly. Tested: 51 tracks pulled into a playlist in one go.
- **Spotify is metadata only** — its audio is DRM-protected and Mero doesn't touch it. Each track is re-found on YouTube by name, and the import tells you which ones it couldn't match rather than quietly giving you a shorter playlist.
- Spotify also needs API credentials. Create a free app at developer.spotify.com/dashboard and paste the Client ID and Secret into the import screen. Mero ships none of its own: a key baked into an APK passed around a group of friends is a key that leaks, and it would put everyone behind one rate limit.

## A real ⋮ menu

That button used to open the playlist picker and nothing else. Now: play next, add to queue, add to playlist, like, download, start radio, go to artist, share.

**Start radio** builds a queue of similar tracks from YouTube's own per-track recommendations — no account needed, which is why Mero can offer it at all.

## Infinite playback and idle pause

- **Infinite playback** (on by default): when the queue runs out, keep going with similar tracks instead of falling silent.
- **Pause when idle** (on by default): stops after an hour with no interaction. Any touch resets the clock.

Both in Settings.

## Audio quality

The equalizer defaults to Flat, so the ordinary case was running every sample through a 10-band processor and a limiter to achieve nothing — and a limiter is not transparent. **The DSP chain is now bypassed outright when the settings ask for nothing.** Audio also runs through the effects in 32-bit float instead of 16-bit, which matters when the equalizer is actually doing something.

The equalizer screen also showed +2.4 dB of preamp while applying 0 dB. Now it shows what it's doing.

## Also

**Search** offers browse topics when the box is empty, instead of a blank screen.

**Release builds work again.** R8 was failing on a missing slf4j binder. Worth knowing what that costs: the debug APK you're installing is **90 MB**; the same code as a release build is **23.5 MB**. Release builds need a signing key, which is yours to create — see the `ship-release` skill in the repo.

---

## About FLAC and audio quality generally

YouTube sends Opus or AAC and nothing else. There is no lossless stream to request, and converting Opus to FLAC afterwards would multiply the file size while recovering nothing. The 256 kbps Opus tier exists but is YouTube Music Premium, which needs a signed-in account — the one thing Mero will not do.

So the source ceiling is fixed. What was available was to stop degrading it after the fact, which is what this release does.

---

**GPL-3.0.** Built on [InnerTune](https://github.com/z-huang/InnerTune), [yt-dlp](https://github.com/yt-dlp/yt-dlp), [youtubedl-android](https://github.com/yausername/youtubedl-android) and [LRCLIB](https://lrclib.net).
