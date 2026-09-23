package com.mero.playback

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings

/**
 * Asks Android to leave Mero out of battery optimisation.
 *
 * Doze and "optimised" battery mode are what stop playback with the screen
 * off on a lot of phones: the network is cut while the next track is loading,
 * and the music stops in someone's pocket. Exempt, Mero keeps its network and
 * its service.
 *
 * The request is the system's own "Let Mero run in the background?" dialog —
 * an app cannot switch this off by itself, only ask. Play Store policy
 * restricts this permission to certain app types; Mero is not on the Play
 * Store, and background playback is exactly the case it exists for.
 */
object BatteryExemption {

    fun isExempt(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java)
            ?.isIgnoringBatteryOptimizations(context.packageName) == true

    /** The one-tap system dialog for Mero specifically. */
    @SuppressLint("BatteryLife")
    fun requestIntent(context: Context): Intent =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            .setData(Uri.parse("package:" + context.packageName))

    /** The full list, for phones whose settings app does not handle the dialog. */
    fun listIntent(): Intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
}
