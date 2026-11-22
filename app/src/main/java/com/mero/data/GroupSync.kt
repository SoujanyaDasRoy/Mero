package com.mero.data

import com.mero.data.db.MeroDao
import com.mero.domain.Song
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/** A group seen from its link before joining. */
data class GroupPreview(val name: String, val songIds: List<String>, val hasCover: Boolean)

/** The share site's limit on a playlist name. */
const val MAX_GROUP_NAME = 80

/**
 * False for a step the share site could never accept (no song, or a name it won't take); those are dropped
 * rather than left to block the outbox. Anything subtler is the site's call: see [GroupSync.send].
 */
internal fun isSendable(step: com.mero.data.db.GroupOutboxEntity): Boolean = when (step.kind) {
    "add", "remove" -> !step.value.isNullOrBlank()
    "rename" -> !step.value.isNullOrBlank() && step.value.trim().length <= MAX_GROUP_NAME
    else -> false
}

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

    private val _arrivals = MutableSharedFlow<Pair<String, Int>>(extraBufferCapacity = 8)

    /** (playlist name, songs that arrived from friends), after a sync that brought any. */
    val arrivals: SharedFlow<Pair<String, Int>> = _arrivals

    /** Turns a playlist into a group playlist; the link to send friends. */
    suspend fun makeGroup(playlistId: String): Result<String> = lockFor(playlistId).withLock {
        attempt {
            val playlist = dao.playlistNow(playlistId) ?: error("No such playlist")
            playlist.groupCode?.let { return@attempt ShareLink.shortGroup(it) }
            val uploaded = youTubeSongIds(playlistId)
            require(uploaded.isNotEmpty()) { "Add a song first" }
            val name = playlist.name.trim().take(MAX_GROUP_NAME)
            val (code, head) = api.create(name, uploaded, coverForUpload(playlist.coverUri)).getOrThrow()
            // Edits made while the group was being created had no group to go to: queue the difference
            // now, in the same transaction that makes this a group playlist, so none slips between.
            var queued = false
            dao.runInTransaction {
                dao.setGroup(playlistId, code, head)
                val now = youTubeSongIds(playlistId)
                val steps = (now - uploaded.toSet()).map { "add" to it } + (uploaded - now.toSet()).map { "remove" to it }
                steps.forEach { (kind, id) ->
                    dao.insertOutbox(
                        com.mero.data.db.GroupOutboxEntity(java.util.UUID.randomUUID().toString(), playlistId, kind, id, System.currentTimeMillis()),
                    )
                }
                queued = steps.isNotEmpty()
            }
            if (queued) library.onGroupEdit(playlistId)
            ShareLink.shortGroup(code)
        }
    }

    private suspend fun youTubeSongIds(playlistId: String): List<String> =
        dao.playlistSongIds(playlistId).filter { dao.song(it)?.sourceUri == null }

    /** What a group link holds, without joining it: its name and songs (for the join dialog). */
    suspend fun preview(code: String): Result<GroupPreview> = attempt {
        val pages = fetchAll(code, 0)
        GroupPreview(pages.first().name, mergeGroup(emptyList(), pages.flatMap { it.steps }, emptyList()).songIds, pages.first().hasCover)
    }

    /** Joins the group behind [code]; the local playlist's id (the existing one if already joined). */
    suspend fun join(code: String): Result<String> = attempt {
        dao.playlistByGroup(code)?.let { return@attempt it.id }
        val pages = fetchAll(code, 0)
        val first = pages.first()
        val id = library.createPlaylist(first.name)
        try {
            apply(id, code, pages)
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
            // A new cover goes up as a picture, not as a step (the site adds the step itself).
            val covers = outbox.filter { it.kind == "cover" }
            if (covers.isNotEmpty()) {
                coverForUpload(playlist.coverUri)?.let { api.putCover(code, it).getOrThrow() }
                dao.deleteOutbox(covers.map { it.opId })
            }
            send(code, outbox - covers.toSet())
            val pages = fetchAll(code, playlist.groupSeq)
            val merged = apply(playlistId, code, pages)
            if (merged.coverChanged) downloadCover(playlistId, code)
            if (merged.added > 0) _arrivals.tryEmit((merged.name ?: playlist.name) to merged.added)
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

    /**
     * Queues this playlist's new cover for the group, like any other edit: it is uploaded by the next
     * sync, retried if that fails, and members fetch it on theirs. Nothing for an ordinary playlist.
     */
    suspend fun pushCover(playlistId: String) {
        if (dao.playlistNow(playlistId)?.groupCode == null) return
        dao.insertOutbox(
            com.mero.data.db.GroupOutboxEntity(java.util.UUID.randomUUID().toString(), playlistId, "cover", null, System.currentTimeMillis()),
        )
        library.onGroupEdit(playlistId)
    }

    /**
     * Sends [steps] and clears them from the outbox. A step the site would refuse is dropped first; if
     * the site still refuses the batch, the steps go one by one and only the refused one is dropped, so a
     * single bad step can never stop a playlist syncing. Any other failure keeps them for next time.
     */
    private suspend fun send(code: String, steps: List<com.mero.data.db.GroupOutboxEntity>) {
        val (good, bad) = steps.partition(::isSendable)
        if (bad.isNotEmpty()) dao.deleteOutbox(bad.map { it.opId })
        if (good.isEmpty()) return
        val sent = api.send(code, good)
        if (sent.isSuccess) {
            dao.deleteOutbox(good.map { it.opId })
            return
        }
        if (sent.exceptionOrNull() !is GroupRejected) sent.getOrThrow()
        for (step in good) {
            val one = api.send(code, listOf(step))
            if (one.isSuccess || one.exceptionOrNull() is GroupRejected) dao.deleteOutbox(listOf(step.opId)) else one.getOrThrow()
        }
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

    /**
     * Fetches details for songs this phone hasn't got (a network call, so first, outside any
     * transaction), then reads the playlist and the outbox, merges and writes in ONE transaction.
     * An edit made while the sync was running lands either wholly before the read or after the write,
     * so it is never overwritten: phone files in a group playlist have no step to bring them back.
     */
    private suspend fun apply(playlistId: String, code: String, pages: List<GroupPage>): Merged {
        val steps = pages.flatMap { it.steps }
        val candidates = (steps.filter { it.kind == "add" }.mapNotNull { it.value } + dao.playlistSongIds(playlistId))
            .distinct()
            .filter { id -> dao.song(id).let { it == null || (it.title == UNAVAILABLE_SONG && it.sourceUri == null) } }
        if (candidates.isNotEmpty()) library.upsertSongs(resolve(candidates).getOrDefault(emptyList()))

        var merged: Merged? = null
        dao.runInTransaction {
            val pending = dao.outbox(playlistId).filter { it.kind != "cover" }.map { PendingStep(it.kind, it.value) }
            val result = mergeGroup(dao.playlistSongIds(playlistId), steps, pending)
            result.songIds.forEach { library.placeholder(it) }
            val seq = steps.lastOrNull()?.seq ?: dao.playlistNow(playlistId)?.groupSeq ?: 0
            dao.applyGroupState(playlistId, result.songIds, result.name, code, seq)
            merged = result
        }
        return merged!!
    }

    /** Keeps the group's cover for [playlistId] (also used for "Just add a copy"). */
    suspend fun downloadCover(playlistId: String, code: String) {
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
