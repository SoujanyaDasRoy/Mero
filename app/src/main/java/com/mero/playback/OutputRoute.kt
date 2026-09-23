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

/** The physical thing the sound is coming out of, named the way a person would. */
data class OutputDevice(val name: String, val kind: Kind) {
    enum class Kind { Speaker, Wired, Bluetooth, Usb }
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

    private val _device = MutableStateFlow(currentDevice())

    /** What the Now Playing screen shows on its output button. */
    val device: StateFlow<OutputDevice> = _device.asStateFlow()

    /** What is actually plugged in, regardless of any override. */
    val detected: OutputRoute get() = detectedRoute()

    fun setOverride(route: OutputRoute?) {
        override = route
        _route.value = currentRoute()
    }

    private fun refresh() {
        _route.value = currentRoute()
        _device.value = currentDevice()
    }

    private val callback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) = refresh()

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) = refresh()
    }

    fun start() {
        audioManager?.registerAudioDeviceCallback(callback, Handler(Looper.getMainLooper()))
        refresh()
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

    /**
     * The output Android is most likely using. It does not say outright, but
     * it routes media to the most recently connected personal device ahead of
     * the built-in speaker, so the first match down this list is the one.
     */
    private fun currentDevice(): OutputDevice {
        val devices = audioManager?.getDevices(AudioManager.GET_DEVICES_OUTPUTS).orEmpty()
        fun named(info: AudioDeviceInfo, fallback: String) =
            info.productName?.toString()?.trim()
                // The phone's own model name is what Android reports for the
                // built-in speaker and for anonymous wired jacks, which reads
                // as nonsense on a button — "Pixel 7" is not an output.
                ?.takeIf { it.isNotEmpty() && it != android.os.Build.MODEL }
                ?: fallback
        devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP }
            ?.let { return OutputDevice(named(it, "Bluetooth"), OutputDevice.Kind.Bluetooth) }
        devices.firstOrNull { it.type in USB_TYPES }
            ?.let { return OutputDevice(named(it, "USB audio"), OutputDevice.Kind.Usb) }
        devices.firstOrNull { it.type in WIRED_TYPES }
            ?.let { return OutputDevice(named(it, "Headphones"), OutputDevice.Kind.Wired) }
        return OutputDevice("This phone", OutputDevice.Kind.Speaker)
    }

    private companion object {
        val WIRED_TYPES = setOf(
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
        )
        val USB_TYPES = setOf(
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_USB_DEVICE,
        )
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

/**
 * Opens Android's own "play on" panel — the list of speakers, earbuds and
 * cast devices the phone can route media to.
 *
 * The system panel rather than a list of Mero's own: switching output is
 * something Android does for the whole phone, it already knows every device
 * including ones Mero cannot see, and a home-made list would go stale the
 * first time someone paired a new pair of earbuds. Three ways in, newest
 * first, because the public API only arrived in Android 14.
 */
fun openOutputSwitcher(context: Context) {
    if (android.os.Build.VERSION.SDK_INT >= 34) {
        val shown = runCatching {
            android.media.MediaRouter2.getInstance(context).showSystemOutputSwitcher()
        }.getOrDefault(false)
        if (shown) return
    }
    // Android 11 to 13 on Pixels and most phones built close to stock.
    val panel = android.content.Intent("com.android.systemui.action.LAUNCH_MEDIA_OUTPUT_DIALOG")
        .setPackage("com.android.systemui")
        .putExtra("package_name", context.packageName)
        .addFlags(android.content.Intent.FLAG_RECEIVER_FOREGROUND)
    val sent = runCatching { context.sendBroadcast(panel) }.isSuccess
    if (sent && android.os.Build.VERSION.SDK_INT >= 30) return
    // Everything else: the Bluetooth screen, which is where a person would go.
    runCatching {
        context.startActivity(
            android.content.Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
