package com.mero.playback

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Stops playback after a set time, or at the end of the current track.
 *
 * Lives at app scope rather than in the composition so it keeps counting while
 * the player UI is closed — the whole point is that you've put the phone down.
 */
class SleepTimer(private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main)) {

    private val _remainingSec = MutableStateFlow<Int?>(null)
    /** null when no timer is set. */
    val remainingSec: StateFlow<Int?> = _remainingSec.asStateFlow()

    private val _stopAfterTrack = MutableStateFlow(false)
    val stopAfterTrack: StateFlow<Boolean> = _stopAfterTrack.asStateFlow()

    private var job: Job? = null

    /** Invoked when the timer fires. Set by the player wiring. */
    var onExpired: () -> Unit = {}

    val isActive: Boolean get() = _remainingSec.value != null || _stopAfterTrack.value

    fun start(minutes: Int) {
        cancel()
        _stopAfterTrack.value = false
        _remainingSec.value = minutes * 60
        job = scope.launch {
            while (isActive) {
                delay(1_000)
                val left = (_remainingSec.value ?: 0) - 1
                if (left <= 0) {
                    _remainingSec.value = null
                    onExpired()
                    return@launch
                }
                _remainingSec.value = left
            }
        }
    }

    /** "Stop at end of track" — the player calls [trackEnded] when it does. */
    fun stopAfterCurrentTrack() {
        cancel()
        _stopAfterTrack.value = true
    }

    fun trackEnded(): Boolean {
        if (!_stopAfterTrack.value) return false
        _stopAfterTrack.value = false
        onExpired()
        return true
    }

    fun cancel() {
        job?.cancel()
        job = null
        _remainingSec.value = null
        _stopAfterTrack.value = false
    }
}
