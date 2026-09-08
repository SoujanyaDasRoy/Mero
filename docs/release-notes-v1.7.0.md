**Mero 1.7.0 — songs load in about a second, and the equalizer is a real one.**

Install over any earlier version. Your library, playlists and downloads are kept.

This is everything since **v1.5.0**, which is the last build most of you actually have.

---

## Download

Take **`app-arm64-v8a-debug.apk`** unless you know you need another. Requires Android 9 or newer.

---

## Music loads properly now, and about ten times faster

If tracks had stopped playing, or died a minute in, that's fixed — and the cause is worth explaining.

Mero extracts audio using a bundled copy of yt-dlp. It was set up to mark itself "already updated" on first launch, to keep a download out of the way of your first song. The effect was that it **never updated at all**. An out-of-date extractor doesn't fail loudly: YouTube hands it links that look fine and then refuses to serve more than about a megabyte of them, so a song plays for a minute and stops. That's the same "pauses after about a minute" some of you reported months ago.

On top of that, getting a song ready used to run a small Python program every time. Mero now asks YouTube directly and only falls back to the slow path if that doesn't work.

| | before | now |
|---|---|---|
| tapping a song | ~10 s | **~1 s** |
| skipping to the next | 2–3 s | **~0.3 s** |
| opening the app and pressing play | ~10 s | **~2 s** |

Tested across 50 tracks spanning Hindi, English, Punjabi, Tamil, electronic and classical — every one took the fast path. Long mixes work too: 3-hour and even 10-hour sets play through to the end.

## Seeking was completely broken

Dragging the progress bar didn't work at all. Two separate faults stacked on each other. You can now scrub to any point, including 59 minutes into an hour-long mix.

## A real equalizer

The old one was built on an Android feature that hands the actual work to the phone, and the phone kept refusing: the reverb was rejected outright, and simply having effects switched on pushed playback onto a smaller audio buffer — which is what the crackling was.

All of it now runs inside Mero, so it behaves the same on every phone.

- **Drag the curve.** No more ten sliders that can't show what they add up to. Each band is a handle: drag it up and down for how loud, left and right for where. The line shows what you're actually doing to the sound.
- **The spectrum of what's playing sits behind it**, so you can aim a boost at something you can hear rather than guessing.
- **Bands are adjustable now** — not just how much, but which frequency and how wide.
- **Separate settings for headphones and speaker**, switched automatically when you plug in or unplug. A curve that rescues the phone speaker sounds bloated on headphones.
- **Crossfeed** — new. On headphones, hard-panned recordings (most things from the sixties, where a whole instrument sits in one ear) feel like they're happening inside your head. This bleeds a little of each channel into the other, dulled and delayed the way your head would do it. Worth trying on old records.
- **Loudness matching that actually works.** It used to be a fixed nudge. It now measures each track the way broadcasters do, so a quiet recording doesn't disappear after a loud one.
- **Playback speed** — 0.75× to 2×.
- **Beat haptics are back**, and this time they work. They never did before: they needed microphone permission, which Mero doesn't ask for and shouldn't. Mero now reads its own audio instead.

Removed: **sound booster**, **reverb** and **spatial audio**. None of them ever worked — the reverb couldn't even start, and the others were unsupported on most phones. **Crossfade** went too; it showed "6 s" and changed nothing.

## Search

- **Results appear from the first letter**, updating as you type.
- **An empty search box shows genre tiles** with real cover art instead of grey word-chips.
- **Playlists in results used to do nothing when tapped.** They open and play now — and there are far more of them, because Mero was only looking at YouTube's curated list and ignoring everything people had made.
- Artist pages list their **playlists**, at the top rather than buried under hundreds of albums.
- **Tapping a result closes the keyboard.** It used to stay up.

## Buttons that do what they say

- **Skip works while "repeat one" is on.** It used to replay the same song, in the app and from the notification.
- **Shuffle no longer dead-ends** at the end of a shuffled queue.
- **Tapping the Mero notification opens Mero.** It genuinely did nothing before.
- **Back from the equalizer returns to the song**, not to the home screen.
- **Tapping an artist's name** in the player searches for them.

## Look and feel

- **A new typeface** throughout — Manrope, instead of Android's default.
- **The clock and status icons are visible again.** In light mode they were being drawn dark on a black band.
- **Now Playing works in light mode.** The top of the screen took its colour from the album art at full strength, so a dark cover blacked out the header.
- **Your settings survive a restart.** Light mode, pure black, accent colour and Material You were forgotten every time you closed the app.

---

## About sound quality generally

YouTube sends Opus or AAC and nothing else — there is no lossless stream to ask for, and the 256 kbps tier needs a signed-in Premium account, which is the one thing Mero won't do. So the source ceiling is fixed. What was available was to stop degrading it afterwards, and to give you real tools for shaping it. That's what this release is.

---

**GPL-3.0.** Built on [InnerTune](https://github.com/z-huang/InnerTune), [yt-dlp](https://github.com/yt-dlp/yt-dlp), [youtubedl-android](https://github.com/yausername/youtubedl-android) and [LRCLIB](https://lrclib.net). Typeface [Manrope](https://github.com/sharanda/manrope) (SIL OFL); FFT by [JTransforms](https://github.com/wendykierp/JTransforms) (BSD-2).
