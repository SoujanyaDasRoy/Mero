package com.mero.data

data class AudioFormat(
    val itag: Int,
    val url: String,
    val mimeType: String,
    val bitrate: Int,
    /**
     * Headers the extractor used when it resolved this URL. YouTube's CDN
     * returns 403 if the media request doesn't carry the same User-Agent the
     * extraction was performed with, so these must be forwarded to the player.
     */
    val headers: Map<String, String> = emptyMap(),
)

enum class Quality(val preferredItag: Int) {
    HIGH(251),
    MEDIUM(250),
    LOW(249),
}

fun selectAudioFormat(formats: List<AudioFormat>, quality: Quality): AudioFormat? {
    val audioOnly = formats.filter { it.mimeType.startsWith("audio/") }
    return audioOnly.firstOrNull { it.itag == quality.preferredItag }
        ?: audioOnly.maxByOrNull { it.bitrate }
}

/** Human-readable codec name derived from the format's mime type / itag. */
fun AudioFormat.codecLabel(): String = when {
    mimeType.contains("opus", ignoreCase = true) -> "Opus"
    mimeType.contains("webm", ignoreCase = true) -> "Opus"
    mimeType.contains("mp4", ignoreCase = true) -> "AAC"
    mimeType.contains("m4a", ignoreCase = true) -> "AAC"
    itag == 251 || itag == 250 || itag == 249 -> "Opus"
    itag == 140 -> "AAC"
    else -> "Audio"
}
