package com.mero

import android.app.Application
import com.mero.data.InnerTubePlayerApi
import com.mero.data.InnerTubeSearchApi
import com.mero.data.SearchRepository
import com.mero.data.StreamRepository

/**
 * Hand-written DI container, not Koin — the object graph is small enough that a
 * dependency would cost more than it saves. See docs/architecture.md,
 * "Why a hand-written DI container".
 */
class AppContainer {
    val searchRepository: SearchRepository by lazy { SearchRepository(InnerTubeSearchApi) }
    val streamRepository: StreamRepository by lazy { StreamRepository(InnerTubePlayerApi) }
}

class MeroApplication : Application() {
    val container: AppContainer by lazy { AppContainer() }
}
