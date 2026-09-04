package com.mero.data

import com.mero.domain.Song
import com.zionhuang.innertube.models.SongItem

/**
 * The single place innertube's types are allowed to cross into Mero's domain —
 * see docs/architecture.md, "Why domain models never expose innertube types".
 */
fun SongItem.toDomain(): Song = Song(
    id = id,
    title = title,
    artist = artists.joinToString(", ") { it.name },
    album = album?.name.orEmpty(),
    durationSec = duration ?: 0,
    thumbnailUrl = thumbnail,
)
