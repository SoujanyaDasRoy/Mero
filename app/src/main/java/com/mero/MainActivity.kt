package com.mero

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.mero.ui.MeroApp

/**
 * Single activity. Compose and Navigation Compose assume it, and multiple
 * activities would destroy the persistent player on every transition —
 * docs/architecture.md, Part 2.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { MeroApp() }
    }
}
