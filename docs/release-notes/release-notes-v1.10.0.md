**Mero 1.10.0 — playlists you can make your own, and an updater that remembers what it was doing.**

Install over 1.9.0. Your library, playlists and downloads are kept.

**On 1.9.0 already? You don't need this page.** Open Mero, tap the banner on Home, then Download and Install.

---

## Download

Take **`mero-1.10.0-arm64-v8a-debug.apk`** unless you know you need another. Requires Android 9 or newer.

> Yes, 1.10.0 comes after 1.9.0 — it is the tenth update after 1.0, not the first. Mero knows this; it compares versions properly rather than alphabetically.

---

## Your playlists, your covers

Give a playlist **a picture of your own** and **a description**. Tap the cover to change it, or use the ⋮ menu. Leave it alone and it keeps borrowing the first track's artwork, exactly as before.

## Updating actually behaves now

Before, Mero forgot a download the moment it was closed. The file sat in your Downloads doing nothing, the app offered to fetch the same 90-odd megabytes again, and nothing ever said whether the install had worked.

Now it remembers, and works out for itself what happened:

- **It installed** → the file is deleted and Mero says *"Updated to Mero 1.10.0"*
- **You stopped halfway** → it says so, and offers the file it already has rather than downloading it twice
- **The file is gone** → it quietly forgets and offers a fresh download

A download that refuses to install can be **discarded**, so you are never stuck being offered the same broken file. And there is now a **notification** when a new version appears — once per version, so it never nags.

## The search screen

**Artists, with their photographs**, instead of a wall of coloured boxes. If you have been listening, they are the artists you play. On a fresh install they are the artists behind the genres.

The four tabs — Songs, Albums, Artists, Playlists — are now proper pills that share the width evenly. They used to sit in a row that scrolled off the edge mid-word.

## The Library

- It tells you what is in it: *"2 playlists · 12 liked · 4 downloaded"*
- **New playlist is a button at the top**, on every tab — not hidden inside the Playlists tab
- An empty tab now offers you somewhere to go instead of just saying it is empty

## Also

Playlist imports from YouTube Music were tested end to end this time: a real playlist, 55 tracks, into a named playlist. Spotify imports still need your own Spotify client id and secret in Settings — Mero deliberately ships none, because a shared key handed round a group of friends is a key that leaks.

---

**GPL-3.0.** Built on [InnerTune](https://github.com/z-huang/InnerTune), [yt-dlp](https://github.com/yt-dlp/yt-dlp), [youtubedl-android](https://github.com/yausername/youtubedl-android) and [LRCLIB](https://lrclib.net). Typeface [Manrope](https://github.com/sharanda/manrope) (SIL OFL); FFT by [JTransforms](https://github.com/wendykierp/JTransforms) (BSD-2).
