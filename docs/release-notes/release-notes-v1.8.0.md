**Mero 1.8.0 — it updates itself now, and the home screen knows what you listen to.**

Install over 1.7.0. Your library, playlists and downloads are kept.

---

## Download

Take **`mero-1.8.0-arm64-v8a-debug.apk`** unless you know you need another. Requires Android 9 or newer.

**This is the last time you have to do this by hand.** From here Mero checks for new versions itself and offers them to you inside the app.

---

## No more going to GitHub

Mero now checks for updates on its own when you open it. When there's a new version:

1. A banner appears at the top of Home.
2. Tap it, then **Download**.
3. Tap **Install**.

Android asks once for permission to install apps — allow it and you're done forever.

The file is also saved to your **Downloads** folder, so if anything goes wrong you can open it from Files and tap it. No browser, no GitHub, no working out which file to take.

---

## The home screen is yours now

It used to show the same shuffled genre shelves to everybody. Now it's built from what you actually play:

- **Jump back in** — the last few songs you played, right at the top.
- **On repeat** — what you keep coming back to.
- **More like <the last song you played>** — songs that actually sound like it.
- **A shelf for each artist you play most.**

It updates as you listen rather than being fixed when the app opened. Genre browsing is still there, underneath.

**None of this involves an account.** Mero has never asked you to sign in and still doesn't — it just counts what you play, on your phone, and never sends it anywhere.

## Mero knows your name

It asks once what to call you, and says hello. Skippable, changeable in Settings, and it stays on your phone.

---

## Things that were broken

**Back needed two presses.** With the player open, back would close nothing and quietly change the screen behind it — so the first press looked dead and the second closed the player onto somewhere you didn't expect. One press now.

**Your earbuds' play button didn't start anything.** Pressing play on headphones when Mero wasn't already running did nothing at all. It now wakes the app and carries on from where you stopped — right track, right position.

**"Add to queue" looked like it did nothing.** It was working, but adding to the end of a queue twenty songs long with nothing on screen to show for it. It says so now. Same for "Play next".

**Cover art was blurry in the player.** Every screen was asking for a thumbnail the size of a list row, including the full-screen player, which draws it across the whole phone. It now asks for the size it's actually drawing.

**Two accent colours were unreachable** and the third Now Playing layout was invisible — both rows ran off the edge of the screen with nothing saying they could be scrolled.

**Choosing a Download folder always failed.** Android blocks the top level of storage, which is exactly where the picker opened, so the obvious action was the one that didn't work.

---

## Sound

**A Car profile.** Mero keeps a separate equalizer setting for headphones and for the speaker — but a car connects over Bluetooth exactly like earbuds do, so it was quietly using your headphone curve. Headphone curves are generous with bass; a car door full of speaker is not the place for it. Pick **Car** in the equalizer and it gets its own settings.

**The equalizer now tells you which profile you're editing.** It never did, which is how the above went unnoticed.

**Two new presets:**
- **Extended** — lifts the deep bass and the air at the top, and leaves the middle completely alone. Not a "V" — it doesn't scoop out the voices to fake excitement.
- **Less Bass** — cuts below 500 Hz and nothing else. For cars and boomy rooms.

**The equalizer, sleep timer and lyrics work in every Now Playing layout.** Two of them were only in one.

---

## Look and feel

- **Theme: System, Light or Dark.** It can follow your phone now, instead of a single "light mode" switch.
- **Pure black** is only offered when the app is actually dark, and says so when it isn't.
- **Twelve accent colours**, up from eight. Yellow by default, to match the icon.
- **Four Now Playing layouts**, each with a picture of what it looks like instead of a name you had to guess: Classic, Immersive, Up Next, and **One-handed** — everything within reach of a thumb.
- Softer corners and bigger headings throughout.
- Downloaded update files are named `mero-1.8.0-…` so you can tell versions apart.

---

**GPL-3.0.** Built on [InnerTune](https://github.com/z-huang/InnerTune), [yt-dlp](https://github.com/yt-dlp/yt-dlp), [youtubedl-android](https://github.com/yausername/youtubedl-android) and [LRCLIB](https://lrclib.net). Typeface [Manrope](https://github.com/sharanda/manrope) (SIL OFL); FFT by [JTransforms](https://github.com/wendykierp/JTransforms) (BSD-2).
