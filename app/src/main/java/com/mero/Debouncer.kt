package com.mero

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Runs [action] for a key [waitMs] after the last [request] for it, so a burst of edits becomes one sync.
 *
 * A new request cancels only a run that is still waiting. Once the wait is over the run is left alone
 * (cancelling a sync halfway through wastes it, and a cancelled sync never reports success or failure),
 * and the new request starts a wait of its own; the per-playlist lock in GroupSync puts the two in order.
 */
class Debouncer(private val scope: CoroutineScope, private val waitMs: Long, private val action: suspend (String) -> Unit) {
    private val waiting = ConcurrentHashMap<String, Job>()

    fun request(key: String) {
        lateinit var job: Job
        job = scope.launch(start = CoroutineStart.LAZY) {
            delay(waitMs)
            waiting.remove(key, job)
            action(key)
        }
        waiting.put(key, job)?.cancel()
        job.start()
    }
}
