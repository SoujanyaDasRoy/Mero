**Mero 1.13.0: recommendations that learn your taste, songs that open in Mero when shared, a backup that keeps itself, and a new look.**

> ## ⚠️ One-time reinstall
> 1.13.0 is signed with a new key, so Android **will not install it over 1.12.0 or older** (you'll see "App not installed"). Uninstall Mero first, then install this APK.
>
> Uninstalling clears your library, playlists and settings. If your phone's Google backup is on, Android may bring them back; otherwise, take a screenshot of your playlists first.
>
> **This only happens once.** From 1.13.0 on, updates install over the top and keep everything, and Mero can back itself up (see below).

---

## Download

Take **`mero-1.13.0-arm64-v8a.apk`**. It's the right one for almost every phone. Needs Android 9 or newer.

| File | For |
|---|---|
| `mero-1.13.0-arm64-v8a.apk` | **Almost every phone: take this one** |
| `mero-1.13.0-armeabi-v7a.apk` | Older 32-bit phones |
| `mero-1.13.0-x86_64.apk` · `mero-1.13.0-x86.apk` | Emulators |

It's also much smaller than before: about **26 MB instead of about 100 MB**.

---

## What's new

### 🎯 Recommendations that learn your taste
What plays next is now built from **what you actually listen to**, not from song or artist names:

- Mero finds the songs that best stand for your taste: ones you've **liked** and **replayed**, weighted toward what you've been playing **lately** (old habits fade, new ones take over). Songs you skip count against
- The queue after a song is made from **music like that song** *and* **music like your favourites**. Songs that fit both rise to the top, so it stays close to what's playing while leaning your way
- An artist's name only nudges the order a little; it no longer decides it
- Songs you skipped, or heard just now, stay out of the way
- **Your Mix** is the first tile under *Made for you* on Home: a queue built purely from your taste, different every time. It gets better as you listen and like more songs

It all happens on your phone: there's no account, and nothing about what you listen to is sent anywhere. Mero also keeps a private listening log on your phone (what you play, skip and replay, for the last 6 months). For now it is only stored; it will make recommendations sharper in a later update.

### 🔗 Songs you share open in Mero
**Share** on a song now sends a link that opens straight in Mero on a phone that has it (it plays the song, then music like it), and opens the same song in YouTube Music for anyone who doesn't have Mero.

You can also use **Share → Mero** on any message or YouTube Music link to play it in Mero. And you can **paste any YouTube or YouTube Music link into search** (a video, a share link, a Short, a playlist) and it plays, even when it isn't in YouTube Music's song catalogue.

### 📤 Share a playlist
On a playlist, tap ⋮ → **Share playlist**. It sends a small `.mero` file. Your friend opens it with Mero, or goes to Settings → Library → **Import a playlist file**. It's added as a new playlist and never replaces one they have.

### 💾 A backup that keeps itself
Settings → Library → **Automatic backup**: choose once where it lives, **a folder on your phone** or **a file in Google Drive**. Mero then keeps a fresh backup of your likes, playlists, play history and settings there whenever you leave the app (at most once an hour).

The backup stays put even if Mero is uninstalled, so a reinstalled Mero offers **Restore your library** on first launch and everything comes back. Drive does the uploading with the account already on your phone; Mero never signs in to Google. **Back up your library** (to any file) and **Restore from a backup** are there too.

### 🟣 A new Mero logo
A soft purple **M** on lavender. It's the app icon, the splash screen (now the logo's own gradient across the whole screen), the logo on Home, and the little icon on Mero's notifications. The default accent colour is now violet to match. Settings → **App icon** offers the logo, or **your own photo**. The extra coloured icons from earlier versions are gone; if you were using one, Mero moves you to the new logo.

### 🫧 A floating menu bar
The bottom bar is now a capsule that floats over your music with nothing behind it, and the tab you're on sits in a soft accent pill.

### ✨ Smoother
Screens fade and rise into place, the mini player grows in and away, everything you tap dips under your finger and springs back, and Home's sections lift in one after another when Mero opens.

### 🎚️ Better tabs for Theme and Song reminders
The Theme (System / Light / Dark) and Song reminders (Off / Now and then / Daily) choices are now a bar with an icon on every option and a highlight that slides to your pick.

---

**GPL-3.0.** Built on [InnerTune](https://github.com/z-huang/InnerTune), [yt-dlp](https://github.com/yt-dlp/yt-dlp), [youtubedl-android](https://github.com/yausername/youtubedl-android) and [LRCLIB](https://lrclib.net). Podcasts found through the Apple Podcasts directory and played from each show's own public feed. Typeface [Manrope](https://github.com/sharanda/manrope) (SIL OFL); FFT by [JTransforms](https://github.com/wendykierp/JTransforms) (BSD-2).
