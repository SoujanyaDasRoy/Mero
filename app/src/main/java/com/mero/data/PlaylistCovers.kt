package com.mero.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File

/**
 * A playlist's own cover, kept as a small JPEG inside Mero's private storage.
 *
 * Storing the picker's `content://` URI instead was fragile (the permission can lapse, the source
 * photo can be deleted) and meant the cover could not travel with a backup or a share. A copy that
 * Mero owns has neither problem.
 */
object PlaylistCovers {

    private const val MAX_SIDE = 800
    private val SAFE_NAME = Regex("""[A-Za-z0-9_-]{1,64}\.jpg""")

    fun dir(context: Context) = File(context.filesDir, "covers")

    /** True for a file name [Backup] may restore: no separators, so no path traversal. */
    fun isSafeName(name: String) = SAFE_NAME.matches(name)

    /** Copies [source] in, scaled down and re-encoded. The `file://` URI to store, or null if it can't be read. */
    fun save(context: Context, playlistId: String, source: Uri): String? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(source)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
        val bitmap = context.contentResolver.openInputStream(source)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null
        val scale = MAX_SIDE.toFloat() / maxOf(bitmap.width, bitmap.height)
        val scaled = if (scale < 1f) {
            Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
        } else bitmap
        // A new name each time: the image cache keys on the URI, so reusing one would keep showing the old cover.
        delete(context, playlistId)
        val file = File(dir(context), "${safeId(playlistId)}_${System.currentTimeMillis().toString(36)}.jpg")
        file.parentFile?.mkdirs()
        file.outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        Uri.fromFile(file).toString()
    }.getOrNull()

    fun delete(context: Context, playlistId: String) {
        dir(context).listFiles { f -> f.name.startsWith(safeId(playlistId) + "_") }?.forEach { it.delete() }
    }

    private fun safeId(playlistId: String) = playlistId.filter { it.isLetterOrDigit() || it == '-' }
}
