package com.mero.playback

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Where the sound is going, because it changes what the sound should be.
 *
 * A phone speaker has almost no bass and a peaky midrange; headphones have
 * real low end and a flat-ish response. One equalizer curve cannot serve both
 * — a setting that rescues the speaker sounds bloated on headphones, and the
 * curve someone dialled in for their earbuds is wasted the moment they
 * unplug. Every serious player keeps them separate, and the switch should not
 * be something anyone has to remember to do.
 */
enum class OutputRoute(val label: String) {
    Headphones("Headphones"),
    Speaker("Speaker"),
    ;

    /** Where this route's settings live. */
    val key: String get() = name.lowercase()
}

/**
 * Watches the audio output and reports which of the two it currently is.
 *
 * Anything worn or plugged in counts as headphones, including Bluetooth and
 * USB — what matters is a transducer next to an ear rather than the specific
 * connector.
 */
class OutputRouteWatcher(context: Context) {

    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val _route = MutableStateFlow(currentRoute())
    val route: StateFlow<OutputRoute> = _route.asStateFlow()

    private val callback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            _route.value = currentRoute()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            _route.value = currentRoute()
        }
    }

    fun start() {
        audioManager?.registerAudioDeviceCallback(callback, Handler(Looper.getMainLooper()))
        _route.value = currentRoute()
    }

    fun stop() {
        audioManager?.unregisterAudioDeviceCallback(callback)
    }

    private fun currentRoute(): OutputRoute {
        val devices = audioManager?.getDevices(AudioManager.GET_DEVICES_OUTPUTS) ?: return OutputRoute.Speaker
        val wearing = devices.any { it.type in HEADPHONE_TYPES }
        return if (wearing) OutputRoute.Headphones else OutputRoute.Speaker
    }

    private companion object {
        val HEADPHONE_TYPES = setOf(
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_USB_DEVICE,
            AudioDeviceInfo.TYPE_HEARING_AID,
        )
    }
}
