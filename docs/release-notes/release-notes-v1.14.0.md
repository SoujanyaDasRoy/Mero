**Mero 1.14.0: short share links, and your playlist's own cover goes to your friends.**

> Install this over 1.13.0, 1.13.1 or 1.13.2: your library, playlists and downloads stay.
> **On 1.12.0 or older?** Android will not install this over it ("App not installed") because the signing key changed in 1.13.0. Uninstall Mero first. This only happens once.

---

## Download

Take **`mero-1.14.0-arm64-v8a.apk`**. It's the right one for almost every phone. Needs Android 9 or newer.

| File | For |
|---|---|
| `mero-1.14.0-arm64-v8a.apk` | **Almost every phone: take this one** |
| `mero-1.14.0-armeabi-v7a.apk` | Older 32-bit phones |
| `mero-1.14.0-x86_64.apk` · `mero-1.14.0-x86.apk` | Emulators |

---

## What's new in 1.14.0

### 🔗 Short links
A song now shares as `meroapp.pages.dev/s/…` and a playlist as `meroapp.pages.dev/p/…`, a few characters instead of a screen of letters. Tapping one on a phone with Mero opens it in Mero; for everyone else a song opens in YouTube Music and a playlist shows its name with a way to play it on YouTube. Links made by older versions keep working.

### 🖼️ Your playlist's cover goes with it
Share a playlist you gave a cover and your friend sees **your cover** in the link preview (WhatsApp shows it too). When they tap **Add to my library**, Mero keeps a copy of the cover on their phone, so it stays even after the link is gone.

### ⏳ Shared playlists last 30 days
To make short links possible, a shared playlist's name, songs and cover are stored on Mero's own share site for **30 days**, then deleted automatically. Nothing about you is sent, and there's no account. After 30 days the link says it has expired and your friend can ask you to share it again (their copy in Mero is theirs to keep).

### 📴 Offline still works
With no connection, **Share playlist** sends the long link (no cover, up to 150 songs) and tells you so. Songs share with no network at all.

### ✨ Also
Everything from [1.13.2](https://github.com/SoujanyaDasRoy/Mero/releases/tag/v1.13.2): covers kept by Mero and included in backups, removing a song from its ⋮ menu, a small pop-up for every playlist change, and the keyboard dropping when a menu opens.

---

**GPL-3.0.** Built on [InnerTune](https://github.com/z-huang/InnerTune), [yt-dlp](https://github.com/yt-dlp/yt-dlp), [youtubedl-android](https://github.com/yausername/youtubedl-android) and [LRCLIB](https://lrclib.net). Podcasts found through the Apple Podcasts directory and played from each show's own public feed. Typeface [Manrope](https://github.com/sharanda/manrope) (SIL OFL); FFT by [JTransforms](https://github.com/wendykierp/JTransforms) (BSD-2).
