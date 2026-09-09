package com.mero.data

import android.app.DownloadManager
import android.app.PendingIntent
import android.app.NotificationManager
import android.app.NotificationChannel
import android.app.Notification
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL

/**
 * In-app updates, because the alternative was a group chat message saying
 * "new build, go to the GitHub page, find the right file, download it".
 *
 * Mero is not on Play, so nothing tells anyone a new version exists. That is
 * survivable for one person and hopeless for ten: the friend who reported a
 * bug has no way of knowing it was fixed a month ago. This asks GitHub what
 * the latest release is and, when it is newer than what is installed, offers
 * to fetch and install it.
 *
 * Anonymous, like everything else here — GitHub's release endpoint needs no
 * token for a public repository, and its 60 requests an hour per address is
 * far more than one check per launch.
 */
class UpdateRepository(
    private val context: Context,
    private val settings: SettingsStore,
) {

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    /** What is running right now, read from the package rather than BuildConfig. */
    val installedVersion: String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull().orEmpty().ifBlank { "0" }

    /**
     * Set when the version now running is one this app downloaded, so the UI
     * can say so once. Read and cleared by whoever shows it.
     */
    var justInstalled: String? = null
        private set

    fun consumeJustInstalled(): String? = justInstalled.also { justInstalled = null }

    init {
        settleLastDownload()
    }

    /**
     * Works out what happened to the last APK this app downloaded.
     *
     * A download used to be forgotten the moment the process died: the file sat
     * in Downloads, the card went back to offering a fresh download of the same
     * bytes, and nothing anywhere said whether the install had worked. The
     * answer is knowable without asking anyone — compare what was downloaded
     * with what is now running.
     *
     * Installed: delete the file and say so. Still pending: offer the file we
     * already have rather than fetching it twice. Gone from disk: forget it.
     */
    private fun settleLastDownload() {
        val version = settings.string(SettingsStore.UPDATE_FILE_VERSION, "")
        if (version.isBlank()) return
        val id = settings.long(SettingsStore.UPDATE_DOWNLOAD_ID, -1L)
        val name = settings.string(SettingsStore.UPDATE_FILE_NAME, "")

        if (!isNewer(version, installedVersion)) {
            // It is running. Nothing left to install, and no reason to keep a
            // hundred megabytes in Downloads.
            justInstalled = version
            removeDownload(id)
            forgetDownload()
            return
        }

        val uri = runCatching {
            context.getSystemService(DownloadManager::class.java)?.getUriForDownloadedFile(id)
        }.getOrNull()
        if (uri == null || !canRead(uri)) {
            forgetDownload()
            return
        }
        _state.value = UpdateState.Downloaded(
            release = Release(version, "", "", settings.long(SettingsStore.UPDATE_FILE_SIZE, 0L)),
            uri = uri,
            fileName = name,
            resumed = true,
        )
    }

    private fun canRead(uri: Uri): Boolean = runCatching {
        context.contentResolver.openInputStream(uri)?.use { true } ?: false
    }.getOrDefault(false)

    private fun removeDownload(id: Long) {
        if (id <= 0) return
        runCatching { context.getSystemService(DownloadManager::class.java)?.remove(id) }
    }

    private fun forgetDownload() {
        settings.putString(SettingsStore.UPDATE_FILE_VERSION, "")
        settings.putString(SettingsStore.UPDATE_FILE_NAME, "")
        settings.putLong(SettingsStore.UPDATE_DOWNLOAD_ID, -1L)
        settings.putLong(SettingsStore.UPDATE_FILE_SIZE, 0L)
    }

    /** Throws away a half-finished or unusable download so it can be fetched again. */
    fun discardDownload() {
        removeDownload(settings.long(SettingsStore.UPDATE_DOWNLOAD_ID, -1L))
        forgetDownload()
        _state.value = UpdateState.Idle
    }

    /**
     * @param manual true when a person tapped "Check for updates". A silent
     *   launch check has no business announcing that it found nothing.
     */
    suspend fun check(manual: Boolean = false) {
        if (_state.value is UpdateState.Downloading) return
        _state.value = UpdateState.Checking
        val result = withContext(Dispatchers.IO) { runCatchingCancellable { fetchLatest() } }
        _state.value = result.fold(
            onSuccess = { release ->
                if (release != null && isNewer(release.versionName, installedVersion)) {
                    announce(release)
                }
                when {
                    release == null ->
                        if (manual) UpdateState.Failed("No releases published yet") else UpdateState.Idle
                    isNewer(release.versionName, installedVersion) -> {
                        // Already on disk from a previous run? Offer that
                        // rather than fetching the same hundred megabytes.
                        val held = _state.value as? UpdateState.Downloaded
                        if (held != null && held.release.versionName == release.versionName) {
                            held.copy(release = release)
                        } else {
                            UpdateState.Available(release)
                        }
                    }
                    manual -> UpdateState.UpToDate
                    else -> UpdateState.Idle
                }
            },
            onFailure = { error ->
                if (manual) UpdateState.Failed(error.message ?: "Couldn't reach GitHub") else UpdateState.Idle
            },
        )
    }

    /** Hands the APK to Android's downloader, then polls it for progress. */
    suspend fun download(release: Release) {
        val manager = context.getSystemService(DownloadManager::class.java)
        if (manager == null) {
            _state.value = UpdateState.Failed("No download manager on this device")
            return
        }
        _state.value = UpdateState.Downloading(release, 0f)

        // Into the phone's own Downloads folder, not Mero's private one.
        // Installing straight from the card is the intended path, but if
        // anything goes wrong there — a permission declined, an OEM installer
        // being difficult — the file has to be somewhere a person can find it
        // in Files and tap. Android/data/com.mero/files is not that place.
        val fileName = "mero-" + release.versionName + ".apk"
        val request = DownloadManager.Request(Uri.parse(release.apkUrl))
            .setTitle("Mero " + release.versionName)
            .setDescription("Downloading update")
            .setMimeType(APK_MIME)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)

        val id = runCatching { manager.enqueue(request) }.getOrElse {
            _state.value = UpdateState.Failed(it.message ?: "Couldn't start the download")
            return
        }
        // Written before the download finishes, so a process death mid-way
        // still leaves something to clean up rather than an orphan file.
        settings.putString(SettingsStore.UPDATE_FILE_VERSION, release.versionName)
        settings.putString(SettingsStore.UPDATE_FILE_NAME, fileName)
        settings.putLong(SettingsStore.UPDATE_DOWNLOAD_ID, id)
        settings.putLong(SettingsStore.UPDATE_FILE_SIZE, release.sizeBytes)

        while (true) {
            val progress = withContext(Dispatchers.IO) { poll(manager, id) }
            when (progress) {
                null -> {
                    _state.value = UpdateState.Failed("The download disappeared")
                    return
                }
                is Progress.Running -> _state.value = UpdateState.Downloading(release, progress.fraction)
                is Progress.Done -> {
                    _state.value = UpdateState.Downloaded(release, progress.uri, fileName)
                    return
                }
                is Progress.Failed -> {
                    // A failed download leaves a partial file that will never
                    // install; forget it so the next attempt starts clean.
                    discardDownload()
                    _state.value = UpdateState.Failed(progress.reason)
                    return
                }
            }
            delay(POLL_MS)
        }
    }

    /**
     * Opens the system installer. Android refuses this for an app that has not
     * been allowed to install packages, so the first stop is that permission
     * screen — asking outright is clearer than an installer that silently
     * never appears.
     */
    fun install(uri: Uri) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            val permission = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                .setData(Uri.parse("package:" + context.packageName))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(permission) }
            return
        }
        val installer = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, APK_MIME)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(installer) }
            .onFailure { _state.value = UpdateState.Failed("Couldn't open the installer") }
    }

    fun dismiss() {
        _state.value = UpdateState.Idle
    }

    /**
     * Tells someone once, outside the app.
     *
     * The banner on Home only works for someone who opens Mero, and the people
     * most in need of an update are the ones who have not opened it in a
     * month. Said once per version: a notification that returns every launch
     * is one people learn to swipe away without reading.
     */
    private fun announce(release: Release) {
        if (settings.string(SettingsStore.UPDATE_NOTIFIED_VERSION, "") == release.versionName) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        runCatching {
            manager.createNotificationChannel(
                NotificationChannel(
                    UPDATE_CHANNEL,
                    "Updates",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply { description = "Tells you when a new version of Mero is out" },
            )
            val open = PendingIntent.getActivity(
                context,
                0,
                context.packageManager.getLaunchIntentForPackage(context.packageName)
                    ?.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val notification = Notification.Builder(context, UPDATE_CHANNEL)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("Mero " + release.versionName + " is out")
                .setContentText("Open Mero to download and install it")
                .setAutoCancel(true)
                .setContentIntent(open)
                .build()
            manager.notify(UPDATE_NOTIFICATION_ID, notification)
            settings.putString(SettingsStore.UPDATE_NOTIFIED_VERSION, release.versionName)
        }
    }

    private fun fetchLatest(): Release? {
        val connection = (URL(LATEST_RELEASE_URL).openConnection() as HttpURLConnection).apply {
            // GitHub rejects a request without one, with a 403 that reads like
            // a rate limit and is not.
            setRequestProperty("User-Agent", "Mero")
            setRequestProperty("Accept", "application/vnd.github+json")
            connectTimeout = 10_000
            readTimeout = 10_000
        }
        try {
            if (connection.responseCode == 404) return null
            if (connection.responseCode !in 200..299) {
                error("GitHub returned HTTP " + connection.responseCode)
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            return parseRelease(body, Build.SUPPORTED_ABIS.orEmpty().toList())
        } finally {
            connection.disconnect()
        }
    }

    private fun poll(manager: DownloadManager, id: Long): Progress? {
        val cursor = manager.query(DownloadManager.Query().setFilterById(id)) ?: return null
        cursor.use {
            if (!it.moveToFirst()) return null
            val status = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            return when (status) {
                DownloadManager.STATUS_SUCCESSFUL -> {
                    val uri = manager.getUriForDownloadedFile(id)
                    if (uri == null) {
                        Progress.Failed("The download finished but the file is missing")
                    } else {
                        Progress.Done(uri)
                    }
                }

                DownloadManager.STATUS_FAILED -> Progress.Failed("The download failed")

                else -> {
                    val soFar =
                        it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                    val total =
                        it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                    Progress.Running(if (total > 0) (soFar.toFloat() / total).coerceIn(0f, 1f) else 0f)
                }
            }
        }
    }

    private sealed interface Progress {
        data class Running(val fraction: Float) : Progress
        data class Done(val uri: Uri) : Progress
        data class Failed(val reason: String) : Progress
    }

    private companion object {
        const val LATEST_RELEASE_URL =
            "https://api.github.com/repos/SoujanyaDasRoy/Mero/releases/latest"
        const val APK_MIME = "application/vnd.android.package-archive"
        const val POLL_MS = 400L
        const val UPDATE_CHANNEL = "mero-updates"
        const val UPDATE_NOTIFICATION_ID = 4201
    }
}

/** Where the repository lives, so the UI can link to it. */
const val MERO_SOURCE_URL = "https://github.com/SoujanyaDasRoy/Mero"

data class Release(
    val versionName: String,
    val notes: String,
    val apkUrl: String,
    val sizeBytes: Long,
)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val release: Release) : UpdateState
    data class Downloading(val release: Release, val fraction: Float) : UpdateState
    data class Downloaded(
        val release: Release,
        val uri: Uri,
        /** Where it landed, so the card can say so if installing goes wrong. */
        val fileName: String,
        /**
         * True when this file was found on disk at startup rather than fetched
         * just now — which means an install was started and did not finish.
         */
        val resumed: Boolean = false,
    ) : UpdateState
    data class Failed(val message: String) : UpdateState
}

/* ---- The parts worth testing, kept free of Android ---- */

@Serializable
private data class GithubRelease(
    @SerialName("tag_name") val tagName: String = "",
    val name: String = "",
    val body: String = "",
    val draft: Boolean = false,
    val assets: List<GithubAsset> = emptyList(),
)

@Serializable
private data class GithubAsset(
    val name: String = "",
    val size: Long = 0,
    @SerialName("browser_download_url") val url: String = "",
)

private val updateJson = Json { ignoreUnknownKeys = true }

/**
 * Turns GitHub's release JSON into the one asset this phone should install.
 *
 * Mero ships a split APK per architecture, so the right file depends on the
 * device: handing an arm64 phone the x86 build produces an install failure
 * with no useful explanation. [abis] comes from `Build.SUPPORTED_ABIS`, most
 * preferred first.
 */
fun parseRelease(body: String, abis: List<String>): Release? {
    val release = runCatching { updateJson.decodeFromString<GithubRelease>(body) }.getOrNull()
        ?: return null
    if (release.draft) return null
    val apks = release.assets.filter { it.name.endsWith(".apk", ignoreCase = true) }
    if (apks.isEmpty()) return null

    // Matched on a whole name segment before falling back to a loose contains:
    // "x86" is a substring of "x86_64", so a 32-bit device could otherwise be
    // handed the 64-bit build by nothing more than asset ordering.
    val asset = abis.firstNotNullOfOrNull { abi -> apks.firstOrNull { it.name.hasAbiSegment(abi) } }
        ?: abis.firstNotNullOfOrNull { abi -> apks.firstOrNull { it.name.contains(abi, true) } }
        ?: apks.firstOrNull { it.name.contains("universal", true) }
        ?: apks.first()

    val version = release.tagName.removePrefix("v").trim()
    return Release(
        versionName = version.ifBlank { release.name.trim() },
        notes = release.body.trim(),
        apkUrl = asset.url,
        sizeBytes = asset.size,
    )
}

/**
 * True when [candidate] is a later version than [installed].
 *
 * Compared number by number rather than as text, because "1.10.0" sorts before
 * "1.9.0" as a string — which would strand everyone on the older build the
 * first time a minor version reaches double digits.
 */
fun isNewer(candidate: String, installed: String): Boolean {
    val left = versionParts(candidate)
    val right = versionParts(installed)
    if (left.isEmpty()) return false
    for (index in 0 until maxOf(left.size, right.size)) {
        val a = left.getOrElse(index) { 0 }
        val b = right.getOrElse(index) { 0 }
        if (a != b) return a > b
    }
    return false
}

/**
 * True when [abi] appears in the filename as a whole part of it.
 *
 * Split on "-" and "." only, never "_": "x86_64" is one ABI name, and breaking
 * it apart would make an x86_64 filename look like it contains "x86". Some ABI
 * names contain a hyphen themselves ("arm64-v8a"), which the delimited
 * contains-checks cover.
 */
private fun String.hasAbiSegment(abi: String): Boolean =
    split('-', '.').any { it.equals(abi, ignoreCase = true) } ||
        contains("-$abi-", ignoreCase = true) ||
        contains("-$abi.", ignoreCase = true)

private fun versionParts(version: String): List<Int> =
    version.trim().removePrefix("v")
        .takeWhile { it.isDigit() || it == '.' }
        .split(".")
        .mapNotNull { it.toIntOrNull() }
