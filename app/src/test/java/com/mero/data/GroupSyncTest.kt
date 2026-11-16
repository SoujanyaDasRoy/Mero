package com.mero.data

import android.content.Context
import androidx.room.Room
import com.mero.data.db.GroupOutboxEntity
import com.mero.data.db.MeroDatabase
import com.mero.domain.Song
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.IOException

/** A group on the share site, held in memory: one ordered log, op ids deduplicated, pages of 1,000. */
private class FakeGroupApi : GroupApi {
    val logs = HashMap<String, MutableList<GroupStep>>()
    val names = HashMap<String, String>()
    private val seen = HashMap<String, MutableSet<String>>()
    var covers = HashMap<String, ByteArray>()
    var failSteps = false
    var loseNextSendReply = false
    private var next = 0

    fun append(code: String, kind: String, value: String?, opId: String = "srv-${next++}") {
        if (!seen.getOrPut(code) { HashSet() }.add(opId)) return
        val log = logs.getValue(code)
        log += GroupStep((log.lastOrNull()?.seq ?: 0) + 1, kind, value)
        if (kind == "rename" && value != null) names[code] = value
    }

    override suspend fun create(name: String, ids: List<String>, cover: ByteArray?): Result<Pair<String, Long>> {
        val code = "CODE%04d".format(next++)
        logs[code] = mutableListOf()
        names[code] = name
        ids.forEach { append(code, "add", it) }
        if (cover != null) covers[code] = cover
        return Result.success(code to logs.getValue(code).size.toLong())
    }

    override suspend fun steps(code: String, since: Long): Result<GroupPage> {
        if (failSteps) return Result.failure(IOException("offline"))
        val log = logs[code] ?: return Result.failure(GroupGone())
        val after = log.filter { it.seq > since }
        return Result.success(
            GroupPage(names.getValue(code), covers.containsKey(code), log.lastOrNull()?.seq ?: 0, after.take(1000), after.size > 1000),
        )
    }

    override suspend fun send(code: String, steps: List<GroupOutboxEntity>): Result<Long> {
        if (!logs.containsKey(code)) return Result.failure(GroupGone())
        steps.forEach { append(code, it.kind, it.value, it.opId) }
        if (loseNextSendReply) {
            loseNextSendReply = false
            return Result.failure(IOException("reply lost"))
        }
        return Result.success(logs.getValue(code).last().seq)
    }

    override suspend fun putCover(code: String, jpeg: ByteArray): Result<Long> {
        covers[code] = jpeg
        append(code, "cover", null)
        return Result.success(logs.getValue(code).last().seq)
    }

    override suspend fun cover(code: String): ByteArray? = covers[code]
}

@Config(sdk = [34], application = android.app.Application::class)
@RunWith(RobolectricTestRunner::class)
class GroupSyncTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private lateinit var db: MeroDatabase
    private lateinit var library: LibraryRepository
    private lateinit var api: FakeGroupApi
    private lateinit var sync: GroupSync
    private val unresolvable = HashSet<String>()
    private val savedCovers = HashMap<String, ByteArray>()

    private fun song(id: String) = Song(id = id, title = "Title $id", artist = "Artist", album = "", durationSec = 100, thumbnailUrl = null)

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, MeroDatabase::class.java).allowMainThreadQueries().build()
        library = LibraryRepository(db.dao())
        api = FakeGroupApi()
        sync = GroupSync(
            dao = db.dao(),
            library = library,
            api = api,
            resolve = { ids -> Result.success(ids.filter { it !in unresolvable }.map { song(it) }) },
            saveCover = { playlistId, bytes -> savedCovers[playlistId] = bytes; "file:///covers/$playlistId.jpg" },
            coverForUpload = { uri -> if (uri == null) null else byteArrayOf(1, 2, 3) },
        )
    }

    @After fun tearDown() = db.close()

    private suspend fun ids(playlistId: String) = library.playlistSongs(playlistId).first().map { it.id }
    private suspend fun group(playlistId: String) = db.dao().playlistNow(playlistId)!!
    private fun log(code: String) = api.logs.getValue(code).map { it.kind + " " + it.value }

    private suspend fun groupWith(vararg songIds: String): Pair<String, String> {
        val id = library.createPlaylist("Road trip")
        songIds.forEach { library.addToPlaylist(id, song(it)) }
        sync.makeGroup(id).getOrThrow()
        return id to group(id).groupCode!!
    }

    @Test fun `making a group uploads the songs and records the code`() = runBlocking {
        val id = library.createPlaylist("Road trip")
        library.addToPlaylist(id, song("a"))
        library.addToPlaylist(id, song("b"))
        library.addToPlaylist(id, song("local:1").copy(sourceUri = "content://phone/1"))

        val link = sync.makeGroup(id).getOrThrow()

        val code = group(id).groupCode!!
        assertEquals(ShareLink.shortGroup(code), link)
        assertEquals(listOf("add a", "add b"), log(code))
        assertEquals(2L, group(id).groupSeq)
        assertTrue(db.dao().outbox(id).isEmpty())
    }

    @Test fun `an edit to a group playlist is queued and sent`() = runBlocking {
        val (id, code) = groupWith("a", "b")
        library.addToPlaylist(id, song("c"))
        assertEquals(listOf("add"), db.dao().outbox(id).map { it.kind })

        sync.sync(id).getOrThrow()

        assertEquals("add c", log(code).last())
        assertTrue(db.dao().outbox(id).isEmpty())
        assertEquals(listOf("a", "b", "c"), ids(id))
    }

    @Test fun `a removal and a rename are queued too`() = runBlocking {
        val (id, code) = groupWith("a", "b")
        library.removeFromPlaylist(id, "a")
        library.renamePlaylist(id, "Night drive")
        sync.sync(id).getOrThrow()
        assertEquals(listOf("remove a", "rename Night drive"), log(code).takeLast(2))
    }

    @Test fun `edits to ordinary playlists queue nothing`() = runBlocking {
        val id = library.createPlaylist("Mine")
        library.addToPlaylist(id, song("a"))
        library.removeFromPlaylist(id, "a")
        library.renamePlaylist(id, "Still mine")
        assertTrue(db.dao().outbox(id).isEmpty())
    }

    @Test fun `a friend's add arrives and is counted`() = runBlocking {
        val (id, code) = groupWith("a")
        api.append(code, "add", "d")
        assertEquals(1, sync.sync(id).getOrThrow())
        assertEquals(listOf("a", "d"), ids(id))
    }

    @Test fun `songs from friends are announced with the playlist's name`() = runBlocking {
        val (id, code) = groupWith("a")
        val heard = ArrayList<Pair<String, Int>>()
        val listening = launch(kotlinx.coroutines.Dispatchers.Unconfined) { sync.arrivals.collect { heard += it } }
        api.append(code, "add", "d")
        api.append(code, "add", "e")
        sync.sync(id).getOrThrow()
        sync.sync(id).getOrThrow()
        listening.cancel()
        assertEquals(listOf("Road trip" to 2), heard)
    }

    @Test fun `resent outbox steps are applied once`() = runBlocking {
        val (id, code) = groupWith("a")
        library.addToPlaylist(id, song("c"))
        api.loseNextSendReply = true
        assertTrue(sync.sync(id).isFailure)

        sync.sync(id).getOrThrow()

        assertEquals(1, log(code).count { it == "add c" })
        assertEquals(listOf("a", "c"), ids(id))
        assertTrue(db.dao().outbox(id).isEmpty())
    }

    @Test fun `a failed sync leaves the playlist untouched and reports failure`() = runBlocking {
        val (id, code) = groupWith("a")
        library.addToPlaylist(id, song("c"))
        api.append(code, "add", "d")
        api.failSteps = true

        assertTrue(sync.sync(id).isFailure)
        assertEquals(listOf("a", "c"), ids(id))
        assertTrue(id in sync.failed.value)

        api.failSteps = false
        sync.sync(id).getOrThrow()
        assertEquals(listOf("a", "c", "d"), ids(id))
        assertFalse(id in sync.failed.value)
    }

    @Test fun `joining pages through more than 1000 steps`() = runBlocking {
        val (_, code) = groupWith("a")
        (2..1500).forEach { api.append(code, "add", "s$it") }
        db.dao().deletePlaylist(db.dao().playlistByGroup(code)!!.id)

        val joined = sync.join(code).getOrThrow()

        val songs = ids(joined)
        assertEquals(1500, songs.size)
        assertEquals("a", songs.first())
        assertEquals("s1500", songs.last())
        assertEquals(1500L, group(joined).groupSeq)
        assertEquals("Road trip", group(joined).name)
    }

    @Test fun `a preview names the group and counts its songs without joining`() = runBlocking {
        val (id, code) = groupWith("a", "b", "c")
        api.append(code, "remove", "b")
        db.dao().deletePlaylist(id)

        val preview = sync.preview(code).getOrThrow()

        assertEquals("Road trip", preview.name)
        assertEquals(listOf("a", "c"), preview.songIds)
        assertTrue(library.playlists.first().isEmpty())
    }

    @Test fun `joining an unknown code fails without creating a playlist`() = runBlocking {
        val before = library.playlists.first().size
        assertTrue(sync.join("NOPE1234").isFailure)
        assertEquals(before, library.playlists.first().size)
    }

    @Test fun `joining twice opens the same playlist`() = runBlocking {
        val (id, code) = groupWith("a")
        assertEquals(id, sync.join(code).getOrThrow())
        assertEquals(1, library.playlists.first().size)
    }

    @Test fun `a song that cannot be resolved is kept as unavailable and filled in later`() = runBlocking {
        val (id, code) = groupWith("a")
        unresolvable += "gone"
        api.append(code, "add", "gone")
        sync.sync(id).getOrThrow()
        assertEquals(listOf("a", "gone"), ids(id))
        assertEquals(UNAVAILABLE_SONG, library.song("gone")!!.title)

        unresolvable.clear()
        sync.sync(id).getOrThrow()
        assertEquals("Title gone", library.song("gone")!!.title)
    }

    @Test fun `a rename from a friend renames the playlist here`() = runBlocking {
        val (id, code) = groupWith("a")
        api.append(code, "rename", "Beach")
        sync.sync(id).getOrThrow()
        assertEquals("Beach", group(id).name)
        assertTrue(db.dao().outbox(id).isEmpty())
    }

    @Test fun `a cover step downloads the cover`() = runBlocking {
        val (id, code) = groupWith("a")
        api.putCover(code, byteArrayOf(9))
        sync.sync(id).getOrThrow()
        assertTrue(savedCovers.getValue(id).contentEquals(byteArrayOf(9)))
        assertEquals("file:///covers/$id.jpg", group(id).coverUri)
    }

    @Test fun `leaving keeps the songs and stops syncing`() = runBlocking {
        val (id, code) = groupWith("a", "b")
        library.addToPlaylist(id, song("c"))
        sync.leave(id)
        assertNull(group(id).groupCode)
        assertTrue(db.dao().outbox(id).isEmpty())
        assertEquals(listOf("a", "b", "c"), ids(id))
        library.addToPlaylist(id, song("d"))
        assertTrue(db.dao().outbox(id).isEmpty())
        assertEquals(listOf("add a", "add b"), log(code))
    }
}
