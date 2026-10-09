package com.mero.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

data class ImportedPlaylist(val name: String, val songCount: Int)

private const val MAX_FILE_BYTES = 8 * 1024 * 1024

/**
 * Adds the playlist in a `.mero` file as a new playlist: never over an existing one. A name that is
 * already taken becomes "Name (2)". Null when the file is not a Mero playlist (or is too big to be
 * one), in which case nothing is added.
 */
suspend fun importPlaylistFile(context: Context, library: LibraryRepository, uri: Uri): ImportedPlaylist? =
    withContext(Dispatchers.IO) {
        val text = runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val bytes = input.readNBytesCapped(MAX_FILE_BYTES + 1)
                if (bytes.size > MAX_FILE_BYTES) null else String(bytes, Charsets.UTF_8)
            }
        }.getOrNull() ?: return@withContext null
        val shared = PlaylistFile.decode(text)?.takeIf { it.songs.isNotEmpty() } ?: return@withContext null

        val taken = library.playlists.first().map { it.name.lowercase() }.toSet()
        var name = shared.name
        var n = 2
        while (name.lowercase() in taken) name = "${shared.name} (${n++})"

        val id = library.createPlaylist(name)
        shared.description?.let { library.setPlaylistDescription(id, it) }
        library.addToPlaylist(id, shared.songs)
        ImportedPlaylist(name, shared.songs.size)
    }

private fun java.io.InputStream.readNBytesCapped(limit: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(16 * 1024)
    while (out.size() < limit) {
        val n = read(buffer, 0, minOf(buffer.size, limit - out.size()))
        if (n < 0) break
        out.write(buffer, 0, n)
    }
    return out.toByteArray()
}
