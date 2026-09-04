package com.mero.data

data class AudioFormat(
    val itag: Int,
    val url: String,
    val mimeType: String,
    val bitrate: Int,
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
