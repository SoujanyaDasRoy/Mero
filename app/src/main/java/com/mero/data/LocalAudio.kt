package com.mero.data

import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import com.mero.domain.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Audio files on the phone, turned into songs Mero can play and file.
 *
 * Picked through Android's own file picker rather than by reading the whole
 * music folder, for two reasons. It needs no storage permission — the person
 * hands over exactly the files they chose and nothing else. And a phone's
 * "music" folder is mostly voice notes, WhatsApp audio and ringtones; a
 * library built by scanning it would be full of things nobody wants to hear.
 */
object LocalAudio {

    /**
     * Reads a file's tags into a [Song]. Falls back to the file name when a
     * file has no title tag, which is most files that did not come from a
     * music store.
     */
    suspend fun songFrom(context: Context, uri: Uri): Song = withContext(Dispatchers.IO) {
        // Kept across restarts where Android allows it, or the file would
        // stop opening the next time Mero starts. Files arriving through
        // "Open with" usually are not persistable; they still play now.
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val id = "local:" + stableId(uri.toString())
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            fun tag(key: Int) = retriever.extractMetadata(key)?.trim()?.takeIf { it.isNotEmpty() }

            Song(
                id = id,
                title = tag(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: displayName(context, uri),
                artist = tag(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                    ?: tag(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
                    ?: "On this phone",
                album = tag(MediaMetadataRetriever.METADATA_KEY_ALBUM).orEmpty(),
                durationSec = (tag(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L)
                    .div(1000).toInt(),
                thumbnailUrl = retriever.embeddedPicture?.let { saveArt(context, id, it) },
                sourceUri = uri.toString(),
            )
        } catch (e: RuntimeException) {
            // Not audio MediaMetadataRetriever understands. It may still play;
            // show it by its file name rather than refusing it.
            Song(
                id = id,
                title = displayName(context, uri),
                artist = "On this phone",
                sourceUri = uri.toString(),
            )
        } finally {
            runCatching { retriever.release() }
        }
    }

    /**
     * Whether Mero can still open this file after a restart. A file handed over
     * by "Open with" usually cannot — the permission lasts as long as that one
     * visit — so it is played but not kept, rather than kept and then broken.
     */
    fun isKept(context: Context, uri: Uri): Boolean =
        uri.scheme == "file" ||
            context.contentResolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission }

    private fun displayName(context: Context, uri: Uri): String {
        val name = runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { if (it.moveToFirst()) it.getString(0) else null }
        }.getOrNull() ?: uri.lastPathSegment.orEmpty()
        return name.substringAfterLast('/').substringBeforeLast('.').ifBlank { "Untitled" }
    }

    /**
     * Embedded cover art, written out so it can be drawn like any other cover.
     * Under filesDir rather than cacheDir: Android clears the cache when it
     * likes, and the song outlives it.
     */
    private fun saveArt(context: Context, id: String, bytes: ByteArray): String? = runCatching {
        val dir = File(context.filesDir, "local-art").apply { mkdirs() }
        val file = File(dir, id.substringAfter(':') + ".img")
        if (!file.exists()) file.writeBytes(bytes)
        Uri.fromFile(file).toString()
    }.getOrNull()
}
