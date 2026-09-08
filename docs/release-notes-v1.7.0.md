**Mero 1.7.0 — the equalizer is a real one now.**

Install over 1.6.0. Your library, playlists and downloads are kept.

---

## Download

Take **`app-arm64-v8a-debug.apk`** unless you know you need another. Requires Android 9 or newer.

---

## The equalizer, rebuilt from nothing

1.6.0 told you the audio glitch was fixed by not loading effects unless you asked for one. That was true, and it was also an admission: the effects themselves were the problem. All of them were Android's `audiofx` — you set a value, the phone does the work, and the phone kept refusing. The reverb wouldn't even start. Simply having anything switched on pushed playback onto a smaller audio buffer, which is what the crackling was. And every phone behaved differently, so nobody's settings meant the same thing as anyone else's.

**None of that is Android's job any more.** Mero now processes the audio itself, inside the player. Same result on every phone, no crackling, and nothing to switch off to make the music sound right.

### Drag the curve

Ten vertical sliders are a picture of the control panel, not of the sound. They can't show what they add up to — two neighbouring bands at +6 dB make more than +6 dB between them — and they leave a band nowhere to go but up and down.

The curve is the control now. Each band is a handle: **drag up and down for how loud, left and right for where.** The line shows what you're actually doing.

- **The spectrum of what's playing sits behind it**, so you can aim a boost at something you can hear instead of guessing.
- **Bands are properly adjustable** — not just how much, but which frequency and how wide. The boxiness in a recording is at 340 Hz, not at 250 or 500.
- **Boosting no longer clips.** It works out the loudest point your curve can produce and leaves exactly that much room, so a heavy bass boost doesn't turn into distortion.

### Separate settings for headphones and speaker

Switched automatically when you plug in, unplug, or connect Bluetooth. A curve that rescues the phone speaker sounds bloated on headphones, and there's no reason you should have to remember which one you're on.

### Crossfeed — new

On headphones, hard-panned recordings — most things from the sixties, where a whole instrument sits in one ear — feel like they're happening inside your head rather than in front of you. This bleeds a little of each channel into the other, dulled and delayed the way your head would do it. Try it on old records.

### Loudness matching that actually works

It used to be a fixed lift for quiet tracks, which can't do the job: a compressed pop master and a quiet orchestral recording can peak identically and still be twenty decibels apart in how loud they *feel*. Mero now measures each track the way broadcasters do, and matches it. A quiet recording no longer disappears after a loud one.

### Beat haptics are back, and this time they work

They were pulled in 1.6.0 because they needed microphone permission, which a music player has no business asking for. Mero reads its own audio now, so there's nothing to ask for.

### Playback speed

0.75× to 2×, for podcasts and long mixes.

### Removed

**Sound booster**, **reverb** and **spatial audio**. None ever worked — the reverb couldn't start at all, and the other two were unsupported on most phones while still showing you a switch. Removing a control that does nothing is worth more than leaving it there looking capable.

---

## Three small fixes

- **Back from the equalizer returns to the song**, not to the home screen.
- **Tapping a search result closes the keyboard.** It used to stay up, covering half the result you'd just picked.
- The equalizer screen is **new** — laid out around the curve instead of a wall of sliders.

---

## About sound quality generally

YouTube sends Opus or AAC and nothing else. There's no lossless stream to ask for, and the 256 kbps tier needs a signed-in Premium account, which is the one thing Mero won't do. So the source ceiling is fixed. What was available was to stop degrading it afterwards and to give you real tools for shaping it. That's this release.

---

**GPL-3.0.** Built on [InnerTune](https://github.com/z-huang/InnerTune), [yt-dlp](https://github.com/yt-dlp/yt-dlp), [youtubedl-android](https://github.com/yausername/youtubedl-android) and [LRCLIB](https://lrclib.net). Typeface [Manrope](https://github.com/sharanda/manrope) (SIL OFL); FFT by [JTransforms](https://github.com/wendykierp/JTransforms) (BSD-2).
