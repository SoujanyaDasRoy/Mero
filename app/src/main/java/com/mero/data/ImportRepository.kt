package com.mero.data

import com.mero.domain.Song
import com.zionhuang.innertube.YouTube
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** What an import produced, including what it could not match. */
data class ImportResult(
    val name: String,
    val songs: List<Song>,
    val unmatched: List<String>,
)

/**
 * Brings a playlist in from YouTube Music or Spotify.
 *
 * The two are not symmetric, and the asymmetry is inherent rather than a gap
 * to close later:
 *
 *  - **YouTube** is a direct import. The tracks are the tracks, ids included.
 *  - **Spotify is metadata only** (CLAUDE.md constraint 6). Its audio is
 *    Widevine-protected and Mero does not touch it, so every Spotify track has
 *    to be *re-found* on YouTube by title and artist. Some will not match, and
 *    a few will match the wrong thing; [ImportResult.unmatched] reports the
 *    first case honestly rather than silently shipping a shorter playlist.
 */
class ImportRepository(private val searchRepository: SearchRepository) {

    // ---------------------------------------------------------------- YouTube

    suspend fun importYouTube(url: String): Result<ImportResult> = runCatching {
        val id = youTubePlaylistId(url) ?: error("That does not look like a YouTube playlist link.")
        var page = YouTube.playlist(id).getOrThrow()
        val songs = LinkedHashMap<String, Song>()
        page.songs.forEach { songs.putIfAbsent(it.id, it.toDomain()) }

        var next = page.songsContinuation
        var fetched = 1
        while (next != null && fetched < MAX_PAGES) {
            val more = YouTube.playlistContinuation(next).getOrNull() ?: break
            more.songs.forEach { songs.putIfAbsent(it.id, it.toDomain()) }
            next = more.continuation
            fetched++
        }
        ImportResult(page.playlist.title, songs.values.toList(), emptyList())
    }

    /** Handles `?list=`, `/playlist/`, and a bare id. */
    fun youTubePlaylistId(input: String): String? {
        val trimmed = input.trim()
        Regex("[?&]list=([A-Za-z0-9_-]+)").find(trimmed)?.let { return it.groupValues[1] }
        Regex("playlist/([A-Za-z0-9_-]+)").find(trimmed)?.let { return it.groupValues[1] }
        return trimmed.takeIf { it.matches(Regex("[A-Za-z0-9_-]{12,}")) }
    }

    // ---------------------------------------------------------------- Spotify

    fun spotifyPlaylistId(input: String): String? {
        val trimmed = input.trim()
        Regex("playlist[/:]([A-Za-z0-9]+)").find(trimmed)?.let { return it.groupValues[1] }
        return trimmed.takeIf { it.matches(Regex("[A-Za-z0-9]{22}")) }
    }

    /**
     * Needs a Spotify client id and secret, which the user registers for
     * themselves at developer.spotify.com and enters in Settings.
     *
     * Mero ships no credentials of its own: an app-wide key embedded in an APK
     * handed to friends is a key that leaks, and it would put every import in
     * the group under one rate limit. This uses the client-credentials flow,
     * which reads public playlists and never sees a Spotify account.
     */
    suspend fun importSpotify(
        url: String,
        clientId: String,
        clientSecret: String,
        onProgress: (Int, Int) -> Unit = { _, _ -> },
    ): Result<ImportResult> = runCatching {
        val id = spotifyPlaylistId(url) ?: error("That does not look like a Spotify playlist link.")
        require(clientId.isNotBlank() && clientSecret.isNotBlank()) {
            "Add your Spotify client id and secret in Settings first."
        }
        val token = withContext(Dispatchers.IO) { spotifyToken(clientId, clientSecret) }
        val (name, tracks) = withContext(Dispatchers.IO) { spotifyTracks(id, token) }

        val found = mutableListOf<Song>()
        val missing = mutableListOf<String>()
        tracks.forEachIndexed { index, track ->
            onProgress(index + 1, tracks.size)
            val match = searchRepository.search("${track.title} ${track.artist}")
                .getOrNull()
                ?.firstOrNull()
            if (match != null) found += match else missing += "${track.title} — ${track.artist}"
        }
        ImportResult(name, found, missing)
    }

    private data class SpotifyTrack(val title: String, val artist: String)

    private fun spotifyToken(clientId: String, clientSecret: String): String {
        val basic = android.util.Base64.encodeToString(
            "$clientId:$clientSecret".toByteArray(),
            android.util.Base64.NO_WRAP,
        )
        val conn = (URL("https://accounts.spotify.com/api/token").openConnection() as HttpURLConnection)
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setRequestProperty("Authorization", "Basic $basic")
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        conn.outputStream.use { it.write("grant_type=client_credentials".toByteArray()) }
        if (conn.responseCode != 200) {
            throw IOException("Spotify rejected those credentials (${conn.responseCode}).")
        }
        val body = conn.inputStream.bufferedReader().use { it.readText() }
        return json.parseToJsonElement(body).jsonObject["access_token"]!!.jsonPrimitive.content
    }

    private fun spotifyTracks(playlistId: String, token: String): Pair<String, List<SpotifyTrack>> {
        val name = getJson("https://api.spotify.com/v1/playlists/$playlistId?fields=name", token)["name"]
            ?.jsonPrimitive?.content ?: "Spotify playlist"

        val tracks = mutableListOf<SpotifyTrack>()
        var url: String? =
            "https://api.spotify.com/v1/playlists/$playlistId/tracks" +
                "?limit=100&fields=next,items(track(name,artists(name)))"
        while (url != null && tracks.size < MAX_SPOTIFY_TRACKS) {
            val page = getJson(url, token)
            page["items"]?.jsonArray?.forEach { item ->
                val track = item.jsonObject["track"]?.jsonObject ?: return@forEach
                val title = track["name"]?.jsonPrimitive?.content ?: return@forEach
                val artist = track["artists"]?.jsonArray
                    ?.firstOrNull()?.jsonObject?.get("name")?.jsonPrimitive?.content.orEmpty()
                tracks += SpotifyTrack(title, artist)
            }
            url = page["next"]?.jsonPrimitive?.contentOrNullSafe()
        }
        return name to tracks
    }

    private fun getJson(url: String, token: String): JsonObject {
        val conn = (URL(url).openConnection() as HttpURLConnection)
        conn.setRequestProperty("Authorization", "Bearer $token")
        if (conn.responseCode != 200) {
            throw IOException("Spotify returned ${conn.responseCode} for that playlist.")
        }
        val body = conn.inputStream.bufferedReader().use { it.readText() }
        return json.parseToJsonElement(body).jsonObject
    }

    private companion object {
        const val MAX_PAGES = 10
        const val MAX_SPOTIFY_TRACKS = 500
        val json = Json { ignoreUnknownKeys = true }
    }
}

/** `next` is null-valued rather than absent on the last page. */
private fun kotlinx.serialization.json.JsonPrimitive.contentOrNullSafe(): String? =
    content.takeIf { it.isNotBlank() && it != "null" }
