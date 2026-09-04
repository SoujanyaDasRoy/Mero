package com.mero

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.request.crossfade
import okio.Path.Companion.toOkioPath
import android.content.Context
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.room.Room
import com.mero.playback.MediaCache
import com.mero.playback.StreamResolver
import com.mero.data.HomeRepository
import com.mero.data.ImportRepository
import com.mero.data.InnerTubeSearchApi
import com.mero.data.LibraryRepository
import com.mero.data.RadioRepository
import com.mero.data.SearchRepository
import com.mero.data.LyricsRepository
import com.mero.data.db.MIGRATION_1_2
import com.mero.data.db.MIGRATION_2_3
import com.mero.data.db.MeroDatabase
import com.mero.playback.SleepTimer
import com.mero.playback.AudioEffects
import com.mero.data.StreamRepository
import com.mero.data.YtDlpPlayerApi
import com.zionhuang.innertube.YouTube
import com.zionhuang.innertube.models.YouTubeLocale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Hand-written DI container, not Koin — the object graph is small enough that a
 * dependency would cost more than it saves. See docs/architecture.md,
 * "Why a hand-written DI container".
 */
class AppContainer(context: Context) {

    private val database: MeroDatabase by lazy {
        Room.databaseBuilder(
            context.applicationContext,
            MeroDatabase::class.java,
            "mero.db",
        ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build()
    }

    val searchRepository: SearchRepository by lazy { SearchRepository(InnerTubeSearchApi) }
    val homeRepository: HomeRepository by lazy { HomeRepository() }
    val importRepository: ImportRepository by lazy { ImportRepository(searchRepository) }
    val radioRepository: RadioRepository by lazy { RadioRepository() }

    /** Shared between the equalizer screen and the playback service. */
    val audioEffects: AudioEffects by lazy { AudioEffects() }

    val lyricsRepository: LyricsRepository by lazy { LyricsRepository() }

    val sleepTimer: SleepTimer by lazy { SleepTimer() }
    val libraryRepository: LibraryRepository by lazy { LibraryRepository(database.dao()) }

    // YtDlpPlayerApi, not InnerTubePlayerApi — see StreamRepository.kt's note
    // on InnerTubePlayerApi for why.
    val ytDlpApi: YtDlpPlayerApi by lazy { YtDlpPlayerApi(context.applicationContext) }

    val streamRepository: StreamRepository by lazy { StreamRepository(ytDlpApi) }

    /**
     * HTTP -> resolve mero:// to a live CDN URL -> cache the bytes on disk.
     * Shared by the player and by the next-track warm-up so both fill and read
     * the same cache. See playback/MediaCache.kt.
     */
    fun mediaDataSourceFactory(ctx: Context): CacheDataSource.Factory {
        val resolving = ResolvingDataSource.Factory(
            DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true),
            StreamResolver(streamRepository),
        )
        val streaming = CacheDataSource.Factory()
            .setCache(MediaCache.get(ctx))
            .setUpstreamDataSourceFactory(resolving)
            .setCacheKeyFactory(MediaCache.keyFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        // Downloads are checked first, so a downloaded track never touches the
        // network — which is the entire point of having downloaded it.
        return CacheDataSource.Factory()
            .setCache(MediaCache.downloads(ctx))
            .setUpstreamDataSourceFactory(streaming)
            .setCacheKeyFactory(MediaCache.keyFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    /** Writes into the download cache rather than reading through it. */
    fun downloadDataSourceFactory(ctx: Context): CacheDataSource.Factory {
        val resolving = ResolvingDataSource.Factory(
            DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true),
            StreamResolver(streamRepository),
        )
        return CacheDataSource.Factory()
            .setCache(MediaCache.downloads(ctx))
            .setUpstreamDataSourceFactory(resolving)
            .setCacheKeyFactory(MediaCache.keyFactory)
    }
}

class MeroApplication : Application(), SingletonImageLoader.Factory {
    val container: AppContainer by lazy { AppContainer(this) }

    /**
     * Cover art is the bulk of Mero's network traffic, and the same covers come
     * back constantly — home shelves, search, queue, player. Coil's defaults
     * keep no disk cache worth the name, so scrolling re-downloaded artwork.
     */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .memoryCache {
                MemoryCache.Builder().maxSizePercent(context, 0.25).build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("artwork").toOkioPath())
                    .maxSizeBytes(192L * 1024 * 1024)
                    .build()
            }
            .crossfade(true)
            .build()

    override fun onCreate() {
        super.onCreate()
        initInnerTube()
        // Warm yt-dlp off the playback path — see YtDlpPlayerApi.prepare().
        CoroutineScope(Dispatchers.IO).launch { container.ytDlpApi.prepare() }
    }

    /**
     * YouTube's backend now rejects innertube requests that carry no locale or
     * visitorData context — anonymous per-session identifiers every client
     * (logged in or not) is expected to send, not credentials. Without this,
     * /player returns 400 "Precondition check failed". [YouTube.cookie] is
     * never touched here or anywhere in Mero — CLAUDE.md constraint 1, no
     * Google sign-in.
     *
     * ponytail: no DataStore-backed persistence of visitorData across app
     * restarts (upstream's own app caches it). A fresh fetch per cold start is
     * an extra network round-trip, not a correctness problem — add caching if
     * that latency ever actually matters.
     */
    private fun initInnerTube() {
        val locale = Locale.getDefault()
        YouTube.locale = YouTubeLocale(gl = locale.country.ifBlank { "US" }, hl = locale.language.ifBlank { "en" })
        YouTube.visitorData = YouTube.DEFAULT_VISITOR_DATA
        CoroutineScope(Dispatchers.IO).launch {
            YouTube.visitorData().getOrNull()?.let { YouTube.visitorData = it }
        }
    }
}
