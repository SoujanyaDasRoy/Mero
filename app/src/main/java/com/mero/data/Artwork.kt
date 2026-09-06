package com.mero.data

private val SIZE_PARAMS = Regex("""=w\d+-h\d+""")
private val YT_THUMB_NAME = Regex("""/(default|mqdefault|hqdefault|sddefault)\.jpg""")

/**
 * Optimized for list items, search rows, and shelves. Uses 220px / hqdefault.jpg
 * (~20KB) so lists load 10x faster even on slow 2G/3G connections.
 */
fun String.atArtworkSize(px: Int = 220): String = when {
    SIZE_PARAMS.containsMatchIn(this) -> replace(SIZE_PARAMS, "=w$px-h$px")
    contains("ytimg.com") -> replace(YT_THUMB_NAME, "/hqdefault.jpg")
    else -> this
}

/**
 * Used for full-screen player artwork where high resolution is needed.
 */
fun String.atHighResArtworkSize(px: Int = 544): String = when {
    SIZE_PARAMS.containsMatchIn(this) -> replace(SIZE_PARAMS, "=w$px-h$px")
    contains("ytimg.com") -> replace(YT_THUMB_NAME, "/maxresdefault.jpg")
    else -> this
}
