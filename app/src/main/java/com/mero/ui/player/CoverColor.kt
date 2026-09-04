package com.mero.ui.player

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.Color
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

private val cache = ConcurrentHashMap<String, Int>()

/**
 * Pulls a background colour out of the cover art so Now Playing takes on the
 * character of whatever is playing.
 *
 * The bitmap is decoded heavily downsampled — we only need an average, not the
 * picture — and results are cached per URL, so this costs almost nothing after
 * the first look at a track.
 */
suspend fun coverAccentColor(url: String?): Color? {
    if (url.isNullOrBlank()) return null
    cache[url]?.let { return Color(it) }

    return withContext(Dispatchers.IO) {
        runCatching {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 5_000
                readTimeout = 5_000
            }
            val bitmap = conn.inputStream.use { stream ->
                BitmapFactory.decodeStream(
                    stream,
                    null,
                    // 1/8 scale is plenty for colour extraction.
                    BitmapFactory.Options().apply { inSampleSize = 8 },
                )
            } ?: return@runCatching null

            val palette = Palette.from(bitmap).clearFilters().generate()
            val picked = palette.darkMutedSwatch?.rgb
                ?: palette.darkVibrantSwatch?.rgb
                ?: palette.mutedSwatch?.rgb
                ?: palette.dominantSwatch?.rgb
                ?: return@runCatching null

            cache[url] = picked
            Color(picked)
        }.getOrNull()
    }
}

/**
 * Keeps the extracted colour usable as a background: cover art is often far too
 * bright or saturated to sit behind white text.
 */
fun Color.asPlayerBackdrop(): Color {
    val factor = 0.42f
    return Color(
        red = red * factor,
        green = green * factor,
        blue = blue * factor,
        alpha = 1f,
    )
}
