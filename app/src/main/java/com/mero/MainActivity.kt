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

    /** A song a tapped reminder asked to play, until the UI has played it. */
    private val remindedSong = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Not on a restore: the intent that opened a file is still attached
        // after a rotation, and replaying the file every time would be wrong.
        if (savedInstanceState == null) {
            openedAudio.value = audioFrom(intent)
            remindedSong.value = remindedFrom(intent)
            playFromSearch(intent)
        }
        setContent {
            MeroApp(
                openedAudio = openedAudio,
                onOpenedHandled = { openedAudio.value = null },
                remindedSong = remindedSong,
                onRemindedHandled = { remindedSong.value = null },
            )
        }
    }

    /** singleTask: a file opened while Mero is running arrives here, not in onCreate. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        audioFrom(intent)?.let { openedAudio.value = it }
        remindedFrom(intent)?.let { remindedSong.value = it }
        playFromSearch(intent)
    }

    /**
     * A spoken "play X on Mero". Handed to the playback service as a search
     * request, the same way Android Auto hands it over, so there is one place
     * that turns words into music (MeroPlaybackService.playFromVoice). An empty
     * query — "play music on Mero" — carries on where things stopped.
     */
    private fun playFromSearch(intent: Intent?) {
        if (intent?.action != android.provider.MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH) return
        val query = intent.getStringExtra(android.app.SearchManager.QUERY).orEmpty()
        val token = androidx.media3.session.SessionToken(
            this,
            android.content.ComponentName(this, com.mero.playback.MeroPlaybackService::class.java),
        )
        val future = androidx.media3.session.MediaController.Builder(this, token).buildAsync()
        future.addListener({
            val controller = runCatching { future.get() }.getOrNull() ?: return@addListener
            controller.setMediaItem(
                androidx.media3.common.MediaItem.Builder()
                    .setRequestMetadata(
                        androidx.media3.common.MediaItem.RequestMetadata.Builder().setSearchQuery(query).build(),
                    )
                    .build(),
            )
            controller.prepare()
            controller.play()
            // Released once the request is on its way; the app's own
            // controller follows whatever the service then plays.
            android.os.Handler(mainLooper).postDelayed({ controller.release() }, 5_000)
        }, androidx.core.content.ContextCompat.getMainExecutor(this))
    }

    /** Reminders wait until Mero has gone unopened for a while; this is the clock. */
    override fun onStart() {
        super.onStart()
        (application as MeroApplication).container.settings
            .putLong(com.mero.data.SettingsStore.LAST_OPENED, System.currentTimeMillis())
    }

    private fun remindedFrom(intent: Intent?): String? =
        intent?.getStringExtra(com.mero.data.SongReminderWorker.EXTRA_PLAY_SONG)?.takeIf { it.isNotBlank() }

    /**
     * A new app icon is switched here, on the way out, not when it is picked:
     * disabling the launcher alias this task was started from makes Android
     * close the task, which on the Settings screen looks exactly like a crash.
     */
    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) com.mero.ui.settings.AppIcon.applyPending(this)
    }

    private fun audioFrom(intent: Intent?): Uri? =
        intent?.takeIf { it.action == Intent.ACTION_VIEW }?.data
}
