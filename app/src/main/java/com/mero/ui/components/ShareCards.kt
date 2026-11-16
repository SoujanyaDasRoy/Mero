package com.mero.ui.components

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.content.FileProvider
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.mero.R
import com.mero.data.ShareLink
import com.mero.data.atArtworkSize
import com.mero.domain.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.File

/**
 * What goes to WhatsApp and the rest when a song or a playlist is shared: a picture with the cover,
 * the name and Mero's logo, and under it the Mero link that opens the song or playlist in Mero.
 *
 * The picture is what people see. The link is there so it can be opened (a messaging app always
 * shows a link it was sent); making the link itself carry a preview would need a server that builds a
 * page for each song, which Mero does not have.
 */

private const val SIZE = 1080
private const val INK = 0xFF2A0A6B.toInt()
private const val ACCENT = 0xFF4B1FB0.toInt()

/** One try at [url], twice if the first fails (a card with a blank square is worse than a short wait). */
private suspend fun fetch(context: Context, url: String, px: Int): Bitmap? {
    val request = ImageRequest.Builder(context).data(url.atArtworkSize(px)).allowHardware(false).build()
    repeat(2) {
        val bitmap = runCatching { SingletonImageLoader.get(context).execute(request).image?.toBitmap() }.getOrNull()
        if (bitmap != null) return bitmap
    }
    return null
}

/**
 * A song's cover: its own thumbnail, else (or if that will not load) YouTube's standard picture for the
 * video id, which exists for every video. Null only for something with no picture anywhere.
 */
private suspend fun songCover(context: Context, song: Song, px: Int): Bitmap? =
    song.thumbnailUrl?.let { fetch(context, it, px) }
        ?: if (song.isYouTube) fetch(context, "https://i.ytimg.com/vi/${song.id}/hqdefault.jpg", px) else null

private fun canvasWithBackground(): Pair<Bitmap, Canvas> {
    val bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(0f, 0f, SIZE.toFloat(), SIZE.toFloat(), 0xFFE9DAFC.toInt(), 0xFFC19CFC.toInt(), Shader.TileMode.CLAMP)
    }
    canvas.drawRect(0f, 0f, SIZE.toFloat(), SIZE.toFloat(), paint)
    return bitmap to canvas
}

/** [source] centre-cropped into a rounded square at ([x], [y]). */
private fun Canvas.drawCover(source: Bitmap?, x: Float, y: Float, side: Float, radius: Float) {
    val rect = RectF(x, y, x + side, y + side)
    save()
    clipPath(Path().apply { addRoundRect(rect, radius, radius, Path.Direction.CW) })
    if (source == null) {
        drawRect(rect, Paint().apply { color = 0x55FFFFFF })
        val note = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = side * 0.5f
            color = 0x884B1FB0.toInt()
            textAlign = Paint.Align.CENTER
        }
        drawText("♪", rect.centerX(), rect.centerY() + side * 0.17f, note)
    } else {
        val s = minOf(source.width, source.height)
        val src = android.graphics.Rect((source.width - s) / 2, (source.height - s) / 2, (source.width + s) / 2, (source.height + s) / 2)
        drawBitmap(source, src, rect, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
    }
    restore()
}

private fun Canvas.drawCentered(text: String, y: Float, textSize: Float, color: Int, bold: Boolean, maxLines: Int, width: Int = 920): Int {
    val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        this.textSize = textSize
        this.color = color
        typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
    }
    val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
        .setAlignment(Layout.Alignment.ALIGN_CENTER)
        .setMaxLines(maxLines)
        .setEllipsize(TextUtils.TruncateAt.END)
        .build()
    save()
    translate((SIZE - width) / 2f, y)
    layout.draw(this)
    restore()
    return layout.height
}

private fun Canvas.drawFooter(context: Context) {
    val logo = BitmapFactory.decodeResource(context.resources, R.drawable.mero_logo)
    val side = 96f
    val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 54f
        color = INK
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    val label = "Mero"
    val total = side + 24f + textPaint.measureText(label)
    val x = (SIZE - total) / 2f
    drawCover(logo, x, 936f, side, 24f)
    drawText(label, x + side + 24f, 936f + side / 2f + 19f, textPaint)
}

/** The 1080 x 1080 card for one song. */
suspend fun songCard(context: Context, song: Song): Bitmap = withContext(Dispatchers.IO) {
    val art = songCover(context, song, 800)
    val (bitmap, canvas) = canvasWithBackground()
    canvas.drawCover(art, 270f, 56f, 540f, 56f)
    val titleHeight = canvas.drawCentered(song.title, 626f, 60f, INK, bold = true, maxLines = 2)
    canvas.drawCentered(song.artist, 626f + titleHeight + 14f, 42f, ACCENT, bold = false, maxLines = 1)
    canvas.drawFooter(context)
    bitmap
}

/**
 * The 1080 x 1080 card for a playlist, its name and how many songs.
 *
 * The picture is the playlist's own cover when it has one, large. Otherwise four *different* song
 * covers (an album's songs share one, and four copies of it looked like a bug), or one big cover when
 * fewer than four different ones can be found.
 */
suspend fun playlistCard(
    context: Context,
    name: String,
    songs: List<Song>,
    ownCover: String? = null,
): Bitmap = withContext(Dispatchers.IO) {
    val own = ownCover?.let { fetch(context, it, 800) }
    val covers: List<Bitmap> = if (own != null) {
        listOf(own)
    } else {
        // Candidates with different pictures, looked at together; the first four that load win.
        val candidates = songs.distinctBy { it.thumbnailUrl ?: it.id }.take(10)
        val loaded = coroutineScope { candidates.map { async { songCover(context, it, 500) } }.awaitAll() }.filterNotNull()
        if (loaded.size >= 4) loaded.take(4) else loaded.take(1)
    }
    val (bitmap, canvas) = canvasWithBackground()
    if (covers.size == 4) {
        val side = 262f
        val gap = 20f
        val left = (SIZE - (side * 2 + gap)) / 2f
        for (i in 0 until 4) {
            val x = left + (i % 2) * (side + gap)
            val y = 48f + (i / 2) * (side + gap)
            canvas.drawCover(covers[i], x, y, side, 36f)
        }
    } else {
        canvas.drawCover(covers.firstOrNull(), 270f, 56f, 540f, 56f)
    }
    val nameHeight = canvas.drawCentered(name, 626f, 60f, INK, bold = true, maxLines = 2)
    canvas.drawCentered(
        if (songs.size == 1) "1 song" else "${songs.size} songs",
        626f + nameHeight + 14f,
        42f,
        ACCENT,
        bold = false,
        maxLines = 1,
    )
    canvas.drawFooter(context)
    bitmap
}

/** Writes [card] to the shared cache folder (readable only through FileProvider) and opens the share sheet. */
private fun sendCard(context: Context, card: Bitmap, fileName: String, caption: String, chooserTitle: String) {
    val dir = File(context.cacheDir, "shared").apply {
        mkdirs()
        listFiles()?.forEach { it.delete() }
    }
    val file = File(dir, fileName)
    file.outputStream().use { card.compress(Bitmap.CompressFormat.PNG, 100, it) }
    val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TEXT, caption)
        clipData = ClipData.newRawUri("", uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, chooserTitle))
}

/** Share a song: its card, and under it the name and the Mero link. */
suspend fun shareSong(context: Context, song: Song) {
    val link = ShareLink.shortSong(song.id)
    val line = "${song.title} - ${song.artist}"
    if (link == null) {
        context.startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, line),
                "Share ${song.title}",
            ),
        )
        return
    }
    sendCard(context, songCard(context, song), "Mero song.png", "$line\n$link", "Share ${song.title}")
}

/** Share a group playlist's link: its card, and the link that lets a friend join it. */
suspend fun shareGroup(context: Context, name: String, songs: List<Song>, ownCover: String?, link: String) {
    val tracks = songs.filter { it.isYouTube }
    sendCard(context, playlistCard(context, name, tracks, ownCover), "Mero playlist.png", "$name - group playlist on Mero\n$link", "Share $name")
}

/** How a playlist went out: [shared] of [total] songs, as a short link ([short]) or the long fallback. */
data class PlaylistShare(val shared: Int, val total: Int, val short: Boolean)

/**
 * Share a playlist: its card, and under it the name and a Mero link. The link is a short one from Mero's
 * share site, which also keeps the playlist's cover for the friend; when the site cannot be reached it is
 * the long link that carries the songs itself (no cover, 150 songs at most). Null when there is nothing to share.
 */
suspend fun sharePlaylist(context: Context, name: String, songs: List<Song>, ownCover: String? = null): PlaylistShare? {
    val all = songs.filter { it.isYouTube }.distinctBy { it.id }
    if (all.isEmpty()) return null
    val uploaded = all.take(ShareLink.MAX_SHORT_SONGS)
    val cover = withContext(Dispatchers.IO) { com.mero.data.PlaylistCovers.forUpload(ownCover) }
    val short = com.mero.data.ShareApi.createPlaylist(name, uploaded.map { it.id }, cover)
    val tracks = if (short != null) uploaded else all.take(ShareLink.MAX_PLAYLIST_SONGS)
    val link = short ?: ShareLink.playlist(name, tracks.map { it.id }) ?: return null
    val caption = "$name - ${tracks.size} songs on Mero\n$link"
    sendCard(context, playlistCard(context, name, tracks, ownCover), "Mero playlist.png", caption, "Share $name")
    return PlaylistShare(tracks.size, all.size, short != null)
}
