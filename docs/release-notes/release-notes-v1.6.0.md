**Mero 1.6.0 — songs start in about a second instead of ten.**

Install over any earlier version. Your library, playlists and downloads are kept.

---

## Download

Take **`app-arm64-v8a-release.apk`** unless you know you need another. Requires Android 9 or newer.

---

## Music actually loads now

If tracks had stopped playing for you, that's fixed — and the reason turned out to be worth explaining.

Mero extracts audio using a bundled copy of yt-dlp. It was set up to mark itself "already updated" on the first launch after install, to avoid a download getting in the way of your first song. The effect was that it **never updated at all**. An out-of-date extractor doesn't fail loudly: YouTube hands it links that look fine and then refuses to serve more than about a megabyte of them, so a song plays for a minute and dies. That's the same "pauses after ~51 seconds" some of you reported months ago.

It updates properly now, in the background, and waits for that to finish before the first song rather than using the copy being replaced.

## …and about ten times faster

Getting a song ready used to take **around ten seconds**. It now takes **about one**.

The old path ran a small Python program for every track. The new one asks YouTube directly and only falls back to the slow path if that doesn't work.

| | before | now |
|---|---|---|
| tapping a song | ~10 s | **~1 s** |
| skipping to the next | 2–3 s | **~0.3 s** |
| opening the app and pressing play | ~10 s | **~2 s** |

Tested across 50 tracks spanning Hindi, English, Punjabi, Tamil, electronic and classical: **every single one** took the fast path. Long mixes are fine too — 3-hour and even 10-hour sets play through to the end.

## Seeking was completely broken

Dragging the progress bar didn't work. At all. Two separate faults stacked on top of each other, and both are fixed — you can now scrub to any point in a track, including 59 minutes into an hour-long mix.

## Search, rebuilt

- **Results appear from the first letter**, updating as you type.
- **An empty search box now shows genre tiles** with real cover art, instead of a row of grey word-chips.
- **Playlists in search results used to do nothing when tapped.** They open and play now — and there are far more of them, because Mero was only looking at YouTube's own curated list and ignoring everything people had made.
- Artist pages list their **playlists**, at the top rather than buried under a few hundred albums.
- Every tab scrolls on effectively forever.

## Buttons that do what they say

- **Skip now works while "repeat one" is on.** It used to replay the same song — in the app and from the notification.
- **Shuffle no longer dead-ends.** Reaching the end of a shuffled queue left "next" doing nothing, silently.
- **Tapping the Mero notification opens Mero.** It genuinely did nothing before.
- **Tapping an artist's name** in the player searches for them.

## Audio

There was a real glitch, and a real cause: Mero was loading four audio effects onto every song whether or not you were using any of them. That pushes Android onto a smaller audio buffer, which is what you were hearing. Effects now load only when a setting actually asks for one — so on the default Flat equalizer, nothing is in the way of the music.

## Look and feel

- **A new typeface** throughout — Manrope, instead of Android's default.
- **The clock and status icons are visible again.** In light mode they were being drawn dark on a black band; in dark mode the same band merely hid it.
- **Now Playing works properly in light mode.** The top of the screen took its colour from the album art at full strength, so a dark cover blacked out the header.
- **Your appearance settings survive a restart.** Light mode, pure black, accent colour and Material You were all being forgotten every time you closed the app.
- The four buttons under the player — Lyrics, Equalizer, Sleep, Queue — are consistent now, instead of two labelled and two mystery icons.

## Removed

Two sliders that never did anything: **beat haptics** (it needed microphone permission, which a music player has no business asking for) and **crossfade** (wired to nothing at all). Both showed a value and changed no audio.

---

**GPL-3.0.** Built on [InnerTune](https://github.com/z-huang/InnerTune), [yt-dlp](https://github.com/yt-dlp/yt-dlp), [youtubedl-android](https://github.com/yausername/youtubedl-android) and [LRCLIB](https://lrclib.net). Typeface: [Manrope](https://github.com/sharanda/manrope), SIL OFL.
