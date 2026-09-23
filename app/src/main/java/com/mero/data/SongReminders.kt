package com.mero.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.mero.MeroApplication
import com.mero.domain.Song
import kotlinx.coroutines.flow.first
import java.util.Calendar
import java.util.concurrent.TimeUnit

/** How often Mero may nudge someone about their songs. Settings > You. */
enum class ReminderFrequency(
    val label: String,
    val description: String,
    /** Least time between two reminders. */
    val gapMs: Long,
    /** Not while someone is listening anyway: this long since Mero was last opened. */
    val quietAfterOpenMs: Long,
) {
    Off("Off", "No reminders", Long.MAX_VALUE, 0),
    Sometimes("Now and then", "At most every few days, only when Mero hasn't been opened", 3 * DAY, 2 * DAY),
    Daily("Daily", "Once a day at most, only when Mero hasn't been opened today", 20 * HOUR, 12 * HOUR),
    ;

    companion object {
        fun from(name: String): ReminderFrequency = entries.firstOrNull { it.name == name } ?: Sometimes
    }
}

private const val HOUR = 60 * 60 * 1000L
private const val DAY = 24 * HOUR

/** Daytime only: a notification about music at 7am or 11pm is an alarm, not a nudge. */
private val FRIENDLY_HOURS = 10..20

internal fun shouldRemind(
    frequency: ReminderFrequency,
    nowMs: Long,
    hour: Int,
    lastReminderMs: Long,
    lastOpenedMs: Long,
): Boolean = frequency != ReminderFrequency.Off &&
    hour in FRIENDLY_HOURS &&
    nowMs - lastReminderMs >= frequency.gapMs &&
    nowMs - lastOpenedMs >= frequency.quietAfterOpenMs

/**
 * A song worth being reminded of: liked first, then most played, never one
 * heard lately, and not the ones suggested last time. If every favourite has
 * had its turn, the turns start over rather than the reminders stopping.
 */
internal fun pickReminderSong(
    liked: List<Song>,
    mostPlayed: List<Song>,
    recentIds: Set<String>,
    remindedIds: List<String>,
    seed: Int,
): Song? {
    val pool = (liked + mostPlayed).distinctBy { it.id }.filter { it.id !in recentIds }
    val fresh = pool.filter { it.id !in remindedIds }.ifEmpty { pool }
    if (fresh.isEmpty()) return null
    return fresh[Math.floorMod(seed, fresh.size)]
}

/** Title and body. `{song}` and `{artist}` are filled in; every one says what it is about. */
internal val REMINDER_MESSAGES = listOf(
    "Remember this one? 🎶" to "{song} by {artist} is one tap away.",
    "A favourite, just for you" to "How about a little {song} right now?",
    "Music break? ☕" to "{song} would fit right in. Want to press play?",
    "From your favourites 💛" to "It's been a while since {song}. Fancy a listen?",
    "Your soundtrack is waiting" to "{song} by {artist}, ready when you are.",
    "Something you love" to "{artist} has {song} waiting for you.",
)

internal fun reminderText(song: Song, index: Int): Pair<String, String> {
    val (title, body) = REMINDER_MESSAGES[Math.floorMod(index, REMINDER_MESSAGES.size)]
    val name = LyricsRepository.cleanTitle(song.title)
    val artist = song.artist.substringBefore(",").substringBefore("&").trim().ifEmpty { "Your favourite artist" }
    fun fill(text: String) = text.replace("{song}", name).replace("{artist}", artist)
    return fill(title) to fill(body)
}

/**
 * Now and then, a friendly nudge about a song someone loves.
 *
 * Checks every few hours and almost always decides not to: only in the day,
 * only if Mero has not been opened for a while, and never more often than the
 * setting allows. Silent — it sits in the shade rather than buzzing.
 */
class SongReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext
        val container = (app as MeroApplication).container
        val settings = container.settings
        val frequency = ReminderFrequency.from(settings.string(SettingsStore.REMINDERS, ReminderFrequency.Sometimes.name))
        val now = System.currentTimeMillis()
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        if (!shouldRemind(
                frequency,
                now,
                hour,
                settings.long(SettingsStore.LAST_REMINDER, 0),
                settings.long(SettingsStore.LAST_OPENED, now),
            )
        ) {
            return Result.success()
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            app.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return Result.success()
        }

        val library = container.libraryRepository
        val reminded = settings.string(SettingsStore.REMINDED_IDS, "").split(",").filter { it.isNotBlank() }
        val count = settings.int(SettingsStore.REMINDER_COUNT, 0)
        val song = pickReminderSong(
            liked = library.liked.first(),
            mostPlayed = library.mostPlayed.first(),
            recentIds = library.recentlyPlayed.first().take(30).map { it.id }.toSet(),
            remindedIds = reminded,
            seed = count * 7 + reminded.size,
        ) ?: return Result.success()

        show(app, song, count, artwork(app, song.thumbnailUrl))
        settings.putLong(SettingsStore.LAST_REMINDER, now)
        settings.putInt(SettingsStore.REMINDER_COUNT, count + 1)
        settings.putString(SettingsStore.REMINDED_IDS, (listOf(song.id) + reminded).take(10).joinToString(","))
        return Result.success()
    }

    private suspend fun artwork(context: Context, url: String?): Bitmap? {
        if (url == null) return null
        val request = ImageRequest.Builder(context).data(url).allowHardware(false).build()
        return runCatching { SingletonImageLoader.get(context).execute(request).image?.toBitmap() }.getOrNull()
    }

    companion object {
        private const val CHANNEL = "song_reminders"
        private const val NOTIFICATION_ID = 4102
        private const val WORK = "song-reminders"

        /** The song a tapped reminder asks MainActivity to play. */
        const val EXTRA_PLAY_SONG = "com.mero.PLAY_SONG"

        fun schedule(context: Context) {
            // Every few hours, so "daytime only" always gets a chance to line
            // up; the decision above keeps the actual reminders rare.
            // Guarded: this runs in Application.onCreate, and a reminder that
            // cannot be scheduled must never be a Mero that cannot start.
            runCatching {
                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    WORK,
                    ExistingPeriodicWorkPolicy.KEEP,
                    PeriodicWorkRequestBuilder<SongReminderWorker>(6, TimeUnit.HOURS).build(),
                )
            }.onFailure { android.util.Log.w("MeroReminders", "could not schedule reminders", it) }
        }

        internal fun show(context: Context, song: Song, index: Int, art: Bitmap?) {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, "Song reminders", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "An occasional, friendly reminder of songs you love"
                },
            )
            val play = PendingIntent.getActivity(
                context,
                song.id.hashCode(),
                Intent()
                    .setClassName(context, "com.mero.MainActivity")
                    .putExtra(EXTRA_PLAY_SONG, song.id)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val (title, body) = reminderText(song, index)
            val notification = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle(title)
                .setContentText(body)
                .setLargeIcon(art)
                .setContentIntent(play)
                .addAction(android.R.drawable.ic_media_play, "Play", play)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
                .build()
            manager.notify(NOTIFICATION_ID, notification)
        }
    }
}
