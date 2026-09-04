package com.mero

import android.app.Application

/**
 * Hand-written DI container, not Koin — the object graph is small enough that a
 * dependency would cost more than it saves. See docs/architecture.md,
 * "Why a hand-written DI container".
 *
 * Empty until M1 Task 4 adds the first repository.
 */
class AppContainer

class MeroApplication : Application() {
    val container: AppContainer by lazy { AppContainer() }
}
