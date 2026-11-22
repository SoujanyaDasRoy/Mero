package com.mero.data

import android.util.Base64
import com.mero.data.db.GroupOutboxEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/** One page of a group's steps, as the share site returns them. */
data class GroupPage(val name: String, val hasCover: Boolean, val head: Long, val steps: List<GroupStep>, val more: Boolean)

/** The share site has no group with that code. */
class GroupGone : Exception("That group playlist doesn't exist")

/** The share site refused what was sent (HTTP 400): resending it as it is will never work. */
class GroupRejected : Exception("Mero's share site refused that change")

/**
 * Mero's share site, for group playlists (docs/share-site/cloudflare, /api/g). An interface so the sync
 * can be tested against a group held in memory.
 */
interface GroupApi {
    /** Makes a group from these songs; the code and the newest step's seq. */
    suspend fun create(name: String, ids: List<String>, cover: ByteArray?): Result<Pair<String, Long>>

    /** Up to 1,000 steps after [since]. Fails with [GroupGone] for an unknown code. */
    suspend fun steps(code: String, since: Long): Result<GroupPage>

    /** Sends this phone's steps; the group's new head. */
    suspend fun send(code: String, steps: List<GroupOutboxEntity>): Result<Long>

    suspend fun putCover(code: String, jpeg: ByteArray): Result<Long>

    suspend fun cover(code: String): ByteArray?
}

/** A request carries at most this many steps (the site refuses more). */
const val MAX_GROUP_STEPS = 1000

object HttpGroupApi : GroupApi {
    private const val BASE = "https://${ShareLink.SHORT_HOST}"

    override suspend fun create(name: String, ids: List<String>, cover: ByteArray?): Result<Pair<String, Long>> = call {
        val body = JSONObject()
            .put("name", name.trim().take(MAX_GROUP_NAME))
            .put("ids", JSONArray(ids.take(MAX_GROUP_STEPS)))
            .apply { if (cover != null) put("cover", Base64.encodeToString(cover, Base64.NO_WRAP)) }
        val reply = JSONObject(request("POST", "/api/g", "application/json", body.toString().toByteArray()))
        val code = reply.getString("code")
        var head = reply.getLong("head")
        // The rest of a long playlist goes in as ordinary steps.
        val rest = ids.drop(MAX_GROUP_STEPS).map { GroupOutboxEntity(UUID.randomUUID().toString(), "", "add", it, 0) }
        if (rest.isNotEmpty()) head = send(code, rest).getOrThrow()
        code to head
    }

    override suspend fun steps(code: String, since: Long): Result<GroupPage> = call {
        parseGroupPage(request("GET", "/api/g/$code?since=$since", null, null))
    }

    override suspend fun send(code: String, steps: List<GroupOutboxEntity>): Result<Long> = call {
        var head = 0L
        steps.chunked(MAX_GROUP_STEPS).forEach { chunk ->
            val body = JSONObject().put(
                "steps",
                JSONArray(chunk.map { JSONObject().put("opId", it.opId).put("kind", it.kind).put("value", it.value) }),
            )
            head = JSONObject(request("POST", "/api/g/$code/steps", "application/json", body.toString().toByteArray())).getLong("head")
        }
        head
    }

    override suspend fun putCover(code: String, jpeg: ByteArray): Result<Long> = call {
        JSONObject(request("PUT", "/api/g/$code/cover", "image/jpeg", jpeg)).getLong("head")
    }

    override suspend fun cover(code: String): ByteArray? = withContext(Dispatchers.IO) {
        runCatching {
            val conn = open("/c/g/$code.jpg")
            try {
                if (conn.responseCode == 200) conn.inputStream.readBytes() else null
            } finally {
                conn.disconnect()
            }
        }.getOrNull()
    }

    private suspend fun <T> call(block: suspend () -> T): Result<T> = withContext(Dispatchers.IO) {
        try {
            Result.success(block())
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** The response body; throws [GroupGone] on 404 and an IOException on anything else that isn't 200. */
    private fun request(method: String, path: String, type: String?, body: ByteArray?): String {
        val conn = open(path).apply {
            requestMethod = method
            if (body != null) {
                doOutput = true
                setRequestProperty("content-type", type)
            }
        }
        try {
            if (body != null) conn.outputStream.use { it.write(body) }
            when (conn.responseCode) {
                200 -> return conn.inputStream.bufferedReader().readText()
                404 -> throw GroupGone()
                400 -> throw GroupRejected()
                429 -> throw java.io.IOException("Mero's share site is busy; trying again later")
                else -> throw java.io.IOException("Share site said ${conn.responseCode}")
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun open(path: String) = (URL(BASE + path).openConnection() as HttpURLConnection).apply {
        connectTimeout = 10_000
        readTimeout = 20_000
    }
}

internal fun parseGroupPage(json: String): GroupPage {
    val j = JSONObject(json)
    val steps = j.getJSONArray("steps")
    return GroupPage(
        name = j.getString("name"),
        hasCover = j.optBoolean("hasCover"),
        head = j.getLong("head"),
        steps = List(steps.length()) { i ->
            val s = steps.getJSONObject(i)
            GroupStep(s.getLong("seq"), s.getString("kind"), if (s.isNull("value")) null else s.getString("value"))
        },
        more = j.optBoolean("more"),
    )
}
