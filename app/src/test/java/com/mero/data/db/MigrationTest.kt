package com.mero.data.db

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import com.mero.data.Backup
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Real SQLite under Robolectric. A database of the old version is built from the exported schema
 * (app/schemas), the migration is run by opening it through Room, and Room itself then checks that
 * the result matches the new schema exactly (tables, columns, defaults, indices).
 *
 * Room's MigrationTestHelper is not used: under unit tests it can only read schema files from the
 * app's own assets, which would mean shipping the schemas inside the APK.
 */
// Pinned below targetSdk 36 for the same reason as MediaItemsTest.
// A plain Application: MeroApplication opens the real mero.db in the background as it starts, which these tests must own.
@Config(sdk = [34], application = android.app.Application::class)
@RunWith(RobolectricTestRunner::class)
class MigrationTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    /** Robolectric keeps the app's database folder between tests; start each one empty. */
    @Before
    fun clean() {
        listOf(DB, SOURCE, "mero.db").forEach { context.deleteDatabase(it) }
        assertFalse("a database left over from another test", context.getDatabasePath("mero.db").exists())
    }

    /** A database exactly as Room created it at [version], from app/schemas/.../[version].json. */
    private fun createFromSchema(name: String, version: Int): SupportSQLiteDatabase {
        val json = JSONObject(File("schemas/com.mero.data.db.MeroDatabase/$version.json").readText())
        val entities = json.getJSONObject("database").getJSONArray("entities")
        val callback = object : SupportSQLiteOpenHelper.Callback(version) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                for (i in 0 until entities.length()) {
                    val entity = entities.getJSONObject(i)
                    val table = entity.getString("tableName")
                    db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                    val indices = entity.optJSONArray("indices") ?: continue
                    for (j in 0 until indices.length()) {
                        db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
                    }
                }
            }

            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }
        val config = SupportSQLiteOpenHelper.Configuration.builder(context).name(name).callback(callback).build()
        return FrameworkSQLiteOpenHelperFactory().create(config).writableDatabase
    }

    /** Closes with everything folded into the one file, as Backup.write expects of a live database. */
    private fun SupportSQLiteDatabase.closeCheckpointed() {
        query("PRAGMA wal_checkpoint(TRUNCATE)").close()
        close()
    }

    private fun insertSongV7(db: SupportSQLiteDatabase, id: String) = db.execSQL(
        "INSERT INTO songs (id, title, artist, album, durationSec, liked, playCount, skipCount) " +
            "VALUES ('$id', 'Title $id', 'Artist', 'Album', 200, 1, 3, 0)",
    )

    private fun openWithRoom(name: String): MeroDatabase =
        Room.databaseBuilder(context, MeroDatabase::class.java, name)
            .addMigrations(MIGRATION_7_8)
            .allowMainThreadQueries()
            .build()

    @Test
    fun `7 to 8 keeps songs and queue, defaults origin, and adds an empty play log`() {
        createFromSchema(DB, 7).apply {
            insertSongV7(this, "a")
            insertSongV7(this, "b")
            execSQL("INSERT INTO queue (songId, position) VALUES ('a', 0)")
            execSQL("INSERT INTO queue (songId, position) VALUES ('b', 1)")
            closeCheckpointed()
        }

        // Opening runs the migration, and Room validates the result against the version 8 schema.
        val room = openWithRoom(DB)
        try {
            val db = room.openHelper.writableDatabase
            db.query("SELECT songId, position, origin FROM queue ORDER BY position").use {
                assertEquals(2, it.count)
                it.moveToFirst()
                assertEquals("a", it.getString(0))
                assertEquals("USER_CHOSE", it.getString(2))
            }
            db.query("SELECT COUNT(*) FROM songs").use { it.moveToFirst(); assertEquals(2, it.getInt(0)) }
            runBlocking {
                val dao = room.dao()
                assertEquals(0, dao.playEventCount())
                val id = dao.insertPlayEvent(
                    PlayEventEntity(
                        trackId = "a", artistKey = "artist", startedAtMs = 1, listenedMs = 2,
                        durationMs = 3, origin = "AUTO_QUEUE", restarted = false, finalized = false,
                    ),
                )
                dao.updatePlayEvent(id, 9, 10, true)
                val row = dao.lastPlayEvents(5).single()
                assertEquals(9L, row.listenedMs)
                assertTrue(row.finalized)
            }
        } finally {
            room.close()
        }
    }

    @Test
    fun `a backup made at schema 7 still restores, then migrates when opened`() {
        // A database exactly as Mero 1.13 left it, with a song in it.
        // Built under another name, because Backup.restore writes mero.db and nothing else may hold it.
        createFromSchema(SOURCE, 7).apply {
            insertSongV7(this, "kept")
            closeCheckpointed()
        }
        val backup = zipOf(context.getDatabasePath(SOURCE).readBytes())

        assertTrue(Backup.restore(context, ByteArrayInputStream(backup), currentDbVersion = 8))

        val room = openWithRoom("mero.db")
        try {
            runBlocking {
                assertEquals("kept", room.dao().song("kept")?.id)
                assertEquals(0, room.dao().playEventCount())
            }
        } finally {
            room.close()
        }
    }

    @Test
    fun `a backup from a newer schema than the app is refused and changes nothing`() {
        createFromSchema(SOURCE, 7).closeCheckpointed()
        val bytes = context.getDatabasePath(SOURCE).readBytes()
        bytes[63] = 9    // user_version 9, which this app does not know

        assertFalse(Backup.restore(context, ByteArrayInputStream(zipOf(bytes)), currentDbVersion = 8))
        assertFalse("nothing was written", context.getDatabasePath("mero.db").exists())
    }

    private fun zipOf(db: ByteArray): ByteArray = ByteArrayOutputStream().also { out ->
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("mero.db"))
            zip.write(db)
            zip.closeEntry()
        }
    }.toByteArray()

    private companion object {
        const val DB = "migration-test"
        const val SOURCE = "backup-source.db"
    }
}
