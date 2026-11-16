package com.mero.data

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** A playlist fetched from a short link: [hasCover] says whether a cover can be downloaded for it. */
data class RemotePlaylist(val name: String, val ids: List<String>, val hasCover: Boolean)

/**
 * Mero's share site (docs/share-site/cloudflare, https://meroapp.pages.dev). A shared playlist is stored
 * there for 30 days, so its link can be a few characters long and WhatsApp can show its cover; the
 * friend's Mero copies everything it needs (songs and cover) into the phone and does not depend on the
 * site afterwards. Nothing here sends anything about the person: only the playlist being shared.
 */
object ShareApi {
    private const val BASE = "https://${ShareLink.SHORT_HOST}"
    private const val TIMEOUT_MS = 10_000

    /** The short link for the playlist, or null when the site cannot be reached (the caller falls back to a long link). */
    suspend fun createPlaylist(name: String, ids: List<String>, cover: ByteArray?): String? = withContext(Dispatchers.IO) {
        runCatching {
            val body = JSONObject()
                .put("name", name)
                .put("ids", JSONArray(ids))
                .apply { if (cover != null) put("cover", Base64.encodeToString(cover, Base64.NO_WRAP)) }
                .toString()
            val conn = open("/api/p").apply {
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("content-type", "application/json")
            }
            try {
                conn.outputStream.use { it.write(body.toByteArray()) }
                if (conn.responseCode != 200) return@runCatching null
                val code = JSONObject(conn.inputStream.bufferedReader().readText()).getString("code")
                ShareLink.shortPlaylist(code)
            } finally {
                conn.disconnect()
            }
        }.getOrNull()
    }

    /** The playlist behind a short link; failure when offline or when the link has expired. */
    suspend fun fetchPlaylist(code: String): Result<RemotePlaylist> = withContext(Dispatchers.IO) {
        runCatching {
            val conn = open("/api/p/$code")
            try {
                check(conn.responseCode == 200) { "expired" }
                val j = JSONObject(conn.inputStream.bufferedReader().readText())
                val ids = j.getJSONArray("ids")
                RemotePlaylist(j.getString("name"), List(ids.length()) { ids.getString(it) }, j.optBoolean("cover"))
            } finally {
                conn.disconnect()
            }
        }
    }

    /** The cover picture of a shared playlist, or null if it has none or cannot be fetched. */
    suspend fun fetchCover(code: String): ByteArray? = withContext(Dispatchers.IO) {
        runCatching {
            val conn = open("/c/$code.jpg")
            try {
                if (conn.responseCode != 200) null else conn.inputStream.readBytes()
            } finally {
                conn.disconnect()
            }
        }.getOrNull()
    }

    private fun open(path: String) = (URL(BASE + path).openConnection() as HttpURLConnection).apply {
        connectTimeout = TIMEOUT_MS
        readTimeout = TIMEOUT_MS
    }
}
