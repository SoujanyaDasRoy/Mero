package com.mero

import android.app.Application
import android.content.Context
import com.mero.data.InnerTubeSearchApi
import com.mero.data.SearchRepository
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
    val searchRepository: SearchRepository by lazy { SearchRepository(InnerTubeSearchApi) }

    // YtDlpPlayerApi, not InnerTubePlayerApi — see StreamRepository.kt's note
    // on InnerTubePlayerApi for why.
    val streamRepository: StreamRepository by lazy {
        StreamRepository(YtDlpPlayerApi(context.applicationContext))
    }
}

class MeroApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        initInnerTube()
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
