package com.mero.data

import com.mero.domain.Song
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** A playlist as it travels between phones. */
data class SharedPlaylist(val name: String, val description: String?, val songs: List<Song>)

/**
 * The `.mero` file a playlist is shared as: JSON with the name, the description and each song's
 * id, title, artist, album, length and cover. No stream addresses (CLAUDE.md constraint 2), and
 * nothing that identifies who made it.
 *
 * A received file is untrusted input from another person's phone, so reading it checks everything:
 * it must say it is a Mero playlist of a version this app knows, every song id must look like a
 * YouTube video id, text is capped in length, covers must be https, and the list is capped in size.
 * Phone files and podcast episodes are not shared: their addresses mean nothing on another phone.
 */
object PlaylistFile {
    const val EXTENSION = "mero"
    private const val KIND = "playlist"
    private const val VERSION = 1
    private const val MAX_SONGS = 2_000
    private const val MAX_TEXT = 300
    private val VIDEO_ID = Regex("[A-Za-z0-9_-]{11}")

    fun encode(name: String, description: String?, songs: List<Song>): String = buildJsonObject {
        put("mero", KIND)
        put("version", VERSION)
        put("name", name)
        if (!description.isNullOrBlank()) put("description", description)
        put(
            "songs",
            buildJsonArray {
                songs.filter { it.isYouTube && VIDEO_ID.matches(it.id) }.forEach { s ->
                    add(
                        buildJsonObject {
                            put("id", s.id)
                            put("title", s.title)
                            put("artist", s.artist)
                            if (s.album.isNotBlank()) put("album", s.album)
                            if (s.durationSec > 0) put("durationSec", s.durationSec)
                            s.thumbnailUrl?.let { put("thumbnailUrl", it) }
                        },
                    )
                }
            },
        )
    }.toString()

    /** The playlist in [text], or null when it is not a Mero playlist this version can read. */
    fun decode(text: String): SharedPlaylist? = runCatching {
        val root = Json.parseToJsonElement(text).jsonObject
        if (root["mero"]?.jsonPrimitive?.contentOrNull != KIND) return null
        if ((root["version"]?.jsonPrimitive?.intOrNull ?: return null) > VERSION) return null
        val name = root["name"]?.jsonPrimitive?.contentOrNull?.trim()?.take(MAX_TEXT)?.takeIf { it.isNotEmpty() } ?: return null
        val songs = (root["songs"] as? JsonArray ?: return null).take(MAX_SONGS).mapNotNull { element ->
            val o = element as? JsonObject ?: return@mapNotNull null
            val id = o.str("id")?.takeIf { VIDEO_ID.matches(it) } ?: return@mapNotNull null
            Song(
                id = id,
                title = o.str("title") ?: return@mapNotNull null,
                artist = o.str("artist").orEmpty(),
                album = o.str("album").orEmpty(),
                durationSec = (o["durationSec"] as? JsonPrimitive)?.intOrNull?.coerceIn(0, 24 * 3600) ?: 0,
                thumbnailUrl = o.str("thumbnailUrl")?.takeIf { it.startsWith("https://") },
            )
        }.distinctBy { it.id }
        SharedPlaylist(name, root.str("description"), songs)
    }.getOrNull()

    /** A file name for [playlistName]: letters, digits and a few separators, so every phone accepts it. */
    fun fileName(playlistName: String): String {
        val safe = playlistName.trim().replace(Regex("[^\\p{L}\\p{N} _.-]"), "").trim().take(60).ifEmpty { "Mero playlist" }
        return "$safe.$EXTENSION"
    }

    private fun JsonObject.str(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.trim()?.take(MAX_TEXT)?.takeIf { it.isNotEmpty() }
}
