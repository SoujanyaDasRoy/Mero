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

    /**
     * A car stereo, which is neither of the other two.
     *
     * Headphone curves compensate for drivers a centimetre across and are
     * usually generous with bass; a car has a door full of speaker and often a
     * subwoofer, so the same curve there is what makes the mirror buzz. Cars
     * connect over Bluetooth A2DP and Android reports that identically to a
     * pair of earbuds — telling them apart needs the Bluetooth device class,
     * which needs a runtime permission this app has no other use for. So this
     * one is chosen, not detected.
     */
    Car("Car"),
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

    /** Set when someone has picked a profile rather than letting it follow the output. */
    @Volatile
    private var override: OutputRoute? = null

    private val _route = MutableStateFlow(currentRoute())
    val route: StateFlow<OutputRoute> = _route.asStateFlow()

    /** What is actually plugged in, regardless of any override. */
    val detected: OutputRoute get() = detectedRoute()

    fun setOverride(route: OutputRoute?) {
        override = route
        _route.value = currentRoute()
    }

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

    private fun currentRoute(): OutputRoute = override ?: detectedRoute()

    private fun detectedRoute(): OutputRoute {
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
