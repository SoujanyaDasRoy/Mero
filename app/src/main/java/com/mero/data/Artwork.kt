package com.mero.data

private val SIZE_PARAMS = Regex("""=w\d+-h\d+""")
private val YT_THUMB_NAME = Regex("""/(default|mqdefault|hqdefault|sddefault|maxresdefault)\.jpg""")

/**
 * Rewrites a thumbnail URL to ask for roughly the number of pixels it will
 * actually be drawn at.
 *
 * Both hosts YouTube serves art from take a size in the URL, and neither
 * scales it up for you: ask for 220 pixels and you get 220 pixels, however
 * large the view is. Everything used to ask for 220 regardless — fine for a
 * 48dp list row, and a quarter of the resolution the full-screen player needs,
 * which is why the artwork behind Now Playing looked soft.
 *
 * The size is chosen by [com.mero.ui.components.Artwork] from the composable's
 * own dimensions, so a screen cannot ask for the wrong thing by forgetting to.
 */
fun String.atArtworkSize(px: Int = 220): String = when {
    // lh3.googleusercontent.com and friends: any size, exactly as asked.
    SIZE_PARAMS.containsMatchIn(this) -> replace(SIZE_PARAMS, "=w$px-h$px")
    // i.ytimg.com: a fixed set of named sizes, so round up to the next one.
    contains("ytimg.com") -> replace(YT_THUMB_NAME, "/${ytThumbnailName(px)}.jpg")
    else -> this
}

/**
 * The named ytimg size whose *shorter* edge covers [px].
 *
 * Shorter edge because the art is drawn cropped to a square, so it is the
 * height of a 16:9 frame that decides whether the result is sharp.
 *
 * `maxresdefault` is deliberately not in this list. It is the only one YouTube
 * does not generate for every video, and a missing one is a 404 — an empty
 * square rather than a slightly soft one. `sddefault` is 640x480 and is the
 * largest that can be relied on.
 */
private fun ytThumbnailName(px: Int): String = when {
    px <= 180 -> "mqdefault" // 320x180
    px <= 360 -> "hqdefault" // 480x360
    else -> "sddefault" // 640x480
}

/**
 * Past this, a bigger request costs bandwidth and decode time and buys nothing
 * a phone panel can show.
 */
const val MAX_ARTWORK_PX = 1200
