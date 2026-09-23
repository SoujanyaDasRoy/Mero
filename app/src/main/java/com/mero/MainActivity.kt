package com.mero

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.mero.ui.MeroApp
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Single activity. Compose and Navigation Compose assume it, and multiple
 * activities would destroy the persistent player on every transition —
 * docs/architecture.md, Part 2.
 */
class MainActivity : ComponentActivity() {

    /** An audio file another app asked Mero to open, until the UI has played it. */
    private val openedAudio = MutableStateFlow<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Not on a restore: the intent that opened a file is still attached
        // after a rotation, and replaying the file every time would be wrong.
        if (savedInstanceState == null) openedAudio.value = audioFrom(intent)
        setContent { MeroApp(openedAudio = openedAudio, onOpenedHandled = { openedAudio.value = null }) }
    }

    /** singleTask: a file opened while Mero is running arrives here, not in onCreate. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        audioFrom(intent)?.let { openedAudio.value = it }
    }

    private fun audioFrom(intent: Intent?): Uri? =
        intent?.takeIf { it.action == Intent.ACTION_VIEW }?.data
}
