package com.mero.ui.playlist

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.mero.data.PlaylistFile
import com.mero.domain.Song
import java.io.File

/**
 * Hands the playlist to the share sheet as a `.mero` file. The file is written to the cache folder,
 * which the share target reads through a FileProvider address (no storage permission, and no other
 * file of Mero's reachable), and the previous one is cleared first so nothing piles up.
 *
 * False when there is nothing to share: phone files and podcast episodes are not shared.
 */
internal fun sharePlaylistFile(context: Context, name: String, description: String?, songs: List<Song>): Boolean {
    val shareable = songs.filter { it.isYouTube }
    if (shareable.isEmpty()) return false
    val dir = File(context.cacheDir, "shared").apply {
        mkdirs()
        listFiles()?.forEach { it.delete() }
    }
    val file = File(dir, PlaylistFile.fileName(name))
    file.writeText(PlaylistFile.encode(name, description, shareable))
    val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "application/octet-stream"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, "$name on Mero")
        putExtra(
            Intent.EXTRA_TEXT,
            "\"$name\": ${shareable.size} songs. Open the attached file with Mero, " +
                "or in Mero go to Settings > Library > Import a playlist file.",
        )
        clipData = ClipData.newRawUri("", uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, "Share playlist"))
    return true
}
