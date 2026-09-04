package com.mero.data

private val SIZE_PARAMS = Regex("""=w\d+-h\d+""")
private val YT_THUMB_NAME = Regex("""/(default|mqdefault|hqdefault|sddefault)\.jpg""")

/**
 * YouTube hands back deliberately small thumbnails — often 60 to 120 px, which
 * look soft blown up to a 330dp player artwork. Both of its CDNs encode the
 * requested size in the URL, so asking for a larger one costs nothing extra.
 */
fun String.atArtworkSize(px: Int = 544): String = when {
    // lh3.googleusercontent.com/...=w120-h120-l90-rj
    SIZE_PARAMS.containsMatchIn(this) -> replace(SIZE_PARAMS, "=w$px-h$px")

    // i.ytimg.com/vi/<id>/hqdefault.jpg
    contains("ytimg.com") -> replace(YT_THUMB_NAME, "/maxresdefault.jpg")

    else -> this
}
