**Mero 1.5.0 — Real-Time Active Search, Endless Songs & Resilient Playback Engine**

Install over any earlier version. Nothing is lost.

---

## Download

Take **`app-arm64-v8a-debug.apk`** unless you know you need another architecture. Requires Android 9 (API 28) or newer.

---

## Real-time Active Search

- **Search as you write**: As you type in the search bar, active search executes automatically with live results across YouTube Music (250 ms debounce). No need to press enter or hit submit.
- **Endless Songs & Infinite Scroll**: Search results now support continuation pagination. As you scroll down, Mero automatically loads page after page of songs, albums, artists, and playlists.
- **Clean, Music-Focused UI**: Removed search history clutter to dedicate 100% of the screen area to music and results.
- **Browse Genres & Topics**: When the search box is idle, tap any topic chip to instantly trigger active search.

## Resilient Playback Engine (Fixed ~51s Pause)

- **Fixed mid-stream pauses/stalls**: Previously, playback could pause around 50–51 seconds when ExoPlayer requested subsequent byte chunks. YouTube CDN requires matching `&range=...` URL parameters on range requests; without it, YouTube returned HTTP 403 Forbidden. `StreamResolver` now dynamically appends URL range parameters to signed `googlevideo.com` URLs, guaranteeing `206 Partial Content` responses.
- **Extended 5-minute Buffer**: Increased max buffer duration to 5 minutes (`300_000 ms`) while preserving 500 ms instant playback start. Typical tracks now stream in one continuous request without mid-song disconnections.
- **Reliable Error Recovery**: Replaced timestamp-based exception tracking with track ID failure state tracking to guarantee clean single-attempt playback recovery.

## Direct Stream Extraction & Fast Cold Startup

- **Direct `/player` API Resolution**: Updated `yt-dlp` options (`youtube:skip=hls,dash,translated_subs,webpage`) to bypass downloading YouTube's heavy HTML webpage, cutting stream extraction latency by up to 3x.
- **Instant Home Screen Render**: Reduced initial home seed batch size for instant home screen loading (~300 ms), with additional sections streaming in seamlessly as you scroll.
- **Zero Startup Lock Contention**: Removed un-delayed speculative startup prefetching so tapping your first song plays immediately.

---

**GPL-3.0.** Built on [InnerTune](https://github.com/z-huang/InnerTune), [yt-dlp](https://github.com/yt-dlp/yt-dlp), and [youtubedl-android](https://github.com/yausername/youtubedl-android).
