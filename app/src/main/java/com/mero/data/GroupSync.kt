package com.mero.data

import com.mero.data.db.MeroDao
import com.mero.domain.Song
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/** The title a group song is stored under until its details can be fetched. */
const val UNAVAILABLE_SONG = "Unavailable song"

/**
 * Keeps group playlists the same on every member's phone (design: docs/superpowers/specs/
 * 2026-11-16-group-playlists-design.md). Edits wait in the outbox until the share site accepts them;
 * a sync sends them, fetches everyone else's steps, and merges ([mergeGroup]). A failed sync changes
 * nothing on the phone: it is only "not up to date yet", and [failed] says so to the screen.
 */
class GroupSync(
    private val dao: MeroDao,
    private val library: LibraryRepository,
    private val api: GroupApi,
    private val resolve: suspend (List<String>) -> Result<List<Song>>,
    private val saveCover: (playlistId: String, bytes: ByteArray) -> String?,
    private val coverForUpload: (uri: String?) -> ByteArray?,
) {
    private val locks = ConcurrentHashMap<String, Mutex>()
    private val _failed = MutableStateFlow<Set<String>>(emptySet())

    /** Group playlists whose last sync failed. */
    val failed: StateFlow<Set<String>> = _failed

    /** Turns a playlist into a group playlist; the link to send friends. */
    suspend fun makeGroup(playlistId: String): Result<String> = attempt {
        val playlist = dao.playlistNow(playlistId) ?: error("No such playlist")
        playlist.groupCode?.let { return@attempt ShareLink.shortGroup(it) }
        val ids = library.playlistSongs(playlistId).first().filter { it.isYouTube }.map { it.id }
        require(ids.isNotEmpty()) { "Add a song first" }
        val (code, head) = api.create(playlist.name, ids, coverForUpload(playlist.coverUri)).getOrThrow()
        dao.setGroup(playlistId, code, head)
        ShareLink.shortGroup(code)
    }

    /** Joins the group behind [code]; the local playlist's id (the existing one if already joined). */
    suspend fun join(code: String): Result<String> = attempt {
        dao.playlistByGroup(code)?.let { return@attempt it.id }
        val pages = fetchAll(code, 0)
        val first = pages.first()
        val id = library.createPlaylist(first.name)
        try {
            apply(id, code, emptyList(), pages, emptyList())
            if (first.hasCover) downloadCover(id, code)
        } catch (e: Exception) {
            dao.deletePlaylist(id)
            throw e
        }
        id
    }

    /** Sends this phone's edits and takes in everyone else's; how many songs arrived. */
    suspend fun sync(playlistId: String): Result<Int> = lockFor(playlistId).withLock {
        val result = attempt {
            val playlist = dao.playlistNow(playlistId) ?: return@attempt 0
            val code = playlist.groupCode ?: return@attempt 0
            val outbox = dao.outbox(playlistId)
            if (outbox.isNotEmpty()) {
                api.send(code, outbox).getOrThrow()
                dao.deleteOutbox(outbox.map { it.opId })
            }
            val pages = fetchAll(code, playlist.groupSeq)
            val local = library.playlistSongs(playlistId).first().map { it.id }
            // Edits made while this sync was running stay on top of what arrived.
            val pending = dao.outbox(playlistId).map { PendingStep(it.kind, it.value) }
            val merged = apply(playlistId, code, local, pages, pending)
            if (merged.coverChanged) downloadCover(playlistId, code)
            merged.added
        }
        _failed.update { if (result.isSuccess) it - playlistId else it + playlistId }
        result
    }

    /** Syncs every group playlist; how many songs arrived in all. */
    suspend fun syncAll(): Int = dao.groupPlaylists().sumOf { sync(it.id).getOrDefault(0) }

    /** Stops syncing; the songs stay as an ordinary playlist. */
    suspend fun leave(playlistId: String) = lockFor(playlistId).withLock {
        dao.setGroup(playlistId, null, 0)
        dao.deleteOutboxOf(playlistId)
        _failed.update { it - playlistId }
    }

    /** Sends this playlist's cover to the group (members fetch it on their next sync). */
    suspend fun pushCover(playlistId: String): Result<Unit> = attempt {
        val playlist = dao.playlistNow(playlistId) ?: return@attempt
        val code = playlist.groupCode ?: return@attempt
        val jpeg = coverForUpload(playlist.coverUri) ?: return@attempt
        api.putCover(code, jpeg).getOrThrow()
    }

    private suspend fun fetchAll(code: String, since: Long): List<GroupPage> {
        val pages = ArrayList<GroupPage>()
        var from = since
        do {
            val page = api.steps(code, from).getOrThrow()
            pages += page
            from = page.steps.lastOrNull()?.seq ?: from
        } while (page.more && page.steps.isNotEmpty())
        return pages
    }

    /** Merges, fetches details for songs this phone hasn't got, and writes it all in one transaction. */
    private suspend fun apply(
        playlistId: String,
        code: String,
        local: List<String>,
        pages: List<GroupPage>,
        pending: List<PendingStep>,
    ): Merged {
        val steps = pages.flatMap { it.steps }
        val merged = mergeGroup(local, steps, pending)
        val needDetails = merged.songIds.filter { id -> dao.song(id).let { it == null || it.title == UNAVAILABLE_SONG } }
        if (needDetails.isNotEmpty()) {
            val found = resolve(needDetails).getOrDefault(emptyList())
            library.upsertSongs(found)
            val foundIds = found.mapTo(HashSet()) { it.id }
            needDetails.filter { it !in foundIds }.forEach { library.placeholder(it) }
        }
        val seq = steps.lastOrNull()?.seq ?: dao.playlistNow(playlistId)?.groupSeq ?: 0
        dao.applyGroupState(playlistId, merged.songIds, merged.name, code, seq)
        return merged
    }

    private suspend fun downloadCover(playlistId: String, code: String) {
        val bytes = api.cover(code) ?: return
        saveCover(playlistId, bytes)?.let { library.setPlaylistCover(playlistId, it) }
    }

    private fun lockFor(playlistId: String) = locks.getOrPut(playlistId) { Mutex() }

    private suspend fun <T> attempt(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }
}
