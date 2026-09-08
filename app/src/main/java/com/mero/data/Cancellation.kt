package com.mero.data

import kotlin.coroutines.cancellation.CancellationException

/**
 * [runCatching], minus the bug.
 *
 * `runCatching` catches `Throwable`, and coroutine cancellation *is* a
 * `Throwable` — `CancellationException`. So a cancelled call does not unwind
 * quietly the way cancellation is supposed to; it comes back as
 * `Result.failure`, indistinguishable from the server refusing.
 *
 * That is how "The coroutine scope left the composition" — the message
 * Compose cancels a `rememberCoroutineScope` with — ended up rendered to the
 * user as a search error. Live search cancels the in-flight request on every
 * keystroke, so the path was hit constantly; it only surfaced when a
 * cancellation happened to land between the request finishing and the result
 * being read.
 *
 * Cancellation is rethrown here so it stays cancellation: the caller's
 * coroutine ends, no error is reported, and nothing is shown.
 */
inline fun <T> runCatchingCancellable(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Throwable) {
        Result.failure(error)
    }
