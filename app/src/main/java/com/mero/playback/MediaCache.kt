package com.mero.playback

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.media3.datasource.DataSource
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheKeyFactory
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

/**
 * On-disk cache of the audio itself, so a track played once starts instantly
 * the next time — no extraction, no download.
 *
 * Keyed on `videoId`, never on URL. Media3's default key factory uses the URI,
 * and YouTube's signed URLs rotate roughly every six hours, so the default
 * would treat every play of the same song as a new file and silently
 * re-download all of it. CLAUDE.md constraint 5.
 *
 * The cache sits *above* [StreamResolver]: what reaches it is still
 * `mero://<videoId>`, and the swap for a real CDN URL happens below, only on a
 * miss. That is what makes the key stable across URL expiry for free.
 */
object MediaCache {

    private const val MAX_BYTES = 512L * 1024 * 1024

    /** Enough for playback to start while the rest streams in behind it. */
    const val WARM_BYTES = 1_500_000L

    @Volatile
    private var instance: SimpleCache? = null

    @Volatile
    private var downloads: SimpleCache? = null

    fun get(context: Context): SimpleCache = instance ?: synchronized(this) {
        instance ?: SimpleCache(
            File(context.applicationContext.cacheDir, "media"),
            LeastRecentlyUsedCacheEvictor(MAX_BYTES),
            StandaloneDatabaseProvider(context.applicationContext),
        ).also { instance = it }
    }

    /**
     * Downloads live in their own cache with no evictor, and under filesDir
     * rather than cacheDir: content the user asked to keep must not be thrown
     * away to make room for something streamed, and Android is entitled to
     * delete cacheDir whenever it likes.
     */
    fun downloads(context: Context): SimpleCache = downloads ?: synchronized(this) {
        downloads ?: SimpleCache(
            File(context.applicationContext.filesDir, "downloads"),
            androidx.media3.datasource.cache.NoOpCacheEvictor(),
            StandaloneDatabaseProvider(context.applicationContext),
        ).also { downloads = it }
    }

    fun isDownloaded(videoId: String): Boolean =
        downloads?.getCachedSpans(videoId)?.isNotEmpty() == true

    fun removeDownload(videoId: String) {
        val cache = downloads ?: return
        cache.getCachedSpans(videoId).toList().forEach { runCatching { cache.removeSpan(it) } }
    }

    /** Removes every persistent download while keeping the cache handle valid. */
    fun clearDownloads() {
        downloads?.let { cache ->
            cache.keys.toList().forEach { key ->
                runCatching { cache.removeResource(key) }
            }
        }
    }

    /** Drops a partial streaming resource after a CDN/cache read failure. */
    fun invalidateStreaming(videoId: String) {
        instance?.removeResource(videoId)
    }

    /**
     * Pulls a whole track into the download cache. Blocking, and reports
     * progress as a fraction so the UI can show something moving.
     */
    fun download(
        factory: CacheDataSource.Factory,
        videoId: String,
        onProgress: (Float) -> Unit,
    ) {
        val spec = DataSpec.Builder().setUri("mero://" + videoId).build()
        CacheWriter(
            factory.createDataSource(),
            spec,
            null,
        ) { requestLength, bytesCached, _ ->
            if (requestLength > 0) onProgress(bytesCached.toFloat() / requestLength)
        }.cache()
    }

    /** Copies a fully cached track into the user's selected SAF folder. */
    fun exportDownload(
        context: Context,
        folderUri: Uri,
        videoId: String,
        title: String,
        artist: String,
    ) {
        val cache = downloads ?: error("Download cache is not initialized")
        val folder = DocumentFile.fromTreeUri(context, folderUri)
            ?: error("Selected download folder is no longer available")
        val fileName = "Mero-${videoId}-${safeName(title)}-${safeName(artist)}.webm"
        folder.findFile(fileName)?.delete()
        val file = folder.createFile("audio/webm", fileName)
            ?: error("Could not create a file in the selected folder")
        val source = CacheDataSource.Factory()
            .setCache(cache)
            .setCacheKeyFactory(keyFactory)
            .setUpstreamDataSourceFactory(DataSource.Factory { error("Track is not fully cached") })
            .createDataSource()
        val spec = DataSpec.Builder().setUri("mero://$videoId").build()
        source.open(spec)
        try {
            context.contentResolver.openOutputStream(file.uri)?.use { output ->
                val buffer = ByteArray(32 * 1024)
                while (true) {
                    val count = source.read(buffer, 0, buffer.size)
                    if (count == -1) break
                    output.write(buffer, 0, count)
                }
            } ?: error("Could not open the selected folder")
        } finally {
            source.close()
        }
    }

    fun clearExportedDownloads(context: Context, folderUris: Set<String>) {
        folderUris.forEach { rawUri ->
            DocumentFile.fromTreeUri(context, Uri.parse(rawUri))?.listFiles()
                ?.filter { it.name?.startsWith("Mero-") == true }
                ?.forEach { runCatching { it.delete() } }
        }
    }

    val keyFactory = CacheKeyFactory { spec -> spec.key ?: spec.uri.host ?: spec.uri.toString() }

    fun isWarm(videoId: String): Boolean =
        instance?.isCached(videoId, 0, WARM_BYTES) == true

    /**
     * Pulls the opening of a track into the cache so pressing next plays it
     * immediately. Blocking — call it from a background dispatcher.
     */
    fun warm(factory: CacheDataSource.Factory, videoId: String) {
        if (isWarm(videoId)) return
        val spec = DataSpec.Builder()
            .setUri("mero://$videoId")
            .setLength(WARM_BYTES)
            .build()
        CacheWriter(factory.createDataSource(), spec, null, null).cache()
    }

    private fun safeName(value: String): String = value
        .replace(Regex("[^A-Za-z0-9 ._-]"), "_")
        .trim()
        .take(80)
        .ifBlank { "track" }
}
