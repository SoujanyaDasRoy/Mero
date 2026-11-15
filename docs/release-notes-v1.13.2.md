**Mero 1.13.2: playlist covers that stay and travel with you, safer song removal, and a small pop-up whenever a playlist changes.**

> Install this over 1.13.0 or 1.13.1: your library, playlists and downloads stay.
> **On 1.12.0 or older?** Android will not install this over it ("App not installed") because the signing key changed in 1.13.0. Uninstall Mero first. This only happens once.

---

## Download

Take **`mero-1.13.2-arm64-v8a.apk`**. It's the right one for almost every phone. Needs Android 9 or newer.

| File | For |
|---|---|
| `mero-1.13.2-arm64-v8a.apk` | **Almost every phone: take this one** |
| `mero-1.13.2-armeabi-v7a.apk` | Older 32-bit phones |
| `mero-1.13.2-x86_64.apk` · `mero-1.13.2-x86.apk` | Emulators |

---

## What's new in 1.13.2

### 🖼️ Your playlist's own cover is on the share card
If you gave a playlist a cover, the card you share shows **that cover, large**. Without one, the card shows four *different* song covers (no more four copies of the same album), or one big cover when there aren't four to show. Covers that failed to load used to leave a plain purple square; Mero now retries, then falls back to YouTube's own picture for the song, then to a clean ♪ tile.

### 💾 Covers are kept by Mero
A cover you pick is now copied into Mero's own storage, so it no longer disappears if the photo is deleted or Android forgets the permission. Covers you already set are copied automatically the first time you open 1.13.2. They are also part of **Back up your library** and the automatic backup, and come back on restore.

### ✋ Removing a song takes two deliberate taps
The ✕ beside every song in a playlist is gone: it was too easy to hit by mistake. To take a song out, tap its **⋮ → Remove from this playlist**.

### 💬 A small pop-up for every change
Adding a song, removing one, creating or deleting a playlist now shows Android's own small message at the bottom ("Added to *Road trip*", "Already in *Road trip*", "Removed from *Road trip*", "Playlist deleted"). Adding a song that is already in a playlist no longer moves it to the end.

### ⌨️ The keyboard gets out of the way
Opening a ⋮ menu or the add-to-playlist screen now drops the keyboard instead of leaving it over the options.

### 📤 Long playlists share their first 150 songs
A playlist over 150 songs used to refuse to share. It now shares the first 150, and tells you. **Share as a file** is gone from the playlist menu; an old `.mero` file still opens, and Settings → Library → **Import a playlist file** still works.

---

**GPL-3.0.** Built on [InnerTune](https://github.com/z-huang/InnerTune), [yt-dlp](https://github.com/yt-dlp/yt-dlp), [youtubedl-android](https://github.com/yausername/youtubedl-android) and [LRCLIB](https://lrclib.net). Podcasts found through the Apple Podcasts directory and played from each show's own public feed. Typeface [Manrope](https://github.com/sharanda/manrope) (SIL OFL); FFT by [JTransforms](https://github.com/wendykierp/JTransforms) (BSD-2).
