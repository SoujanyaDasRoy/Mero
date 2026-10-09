package com.mero.data.db

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards against the way an update wipes a library: a schema change shipped without a migration (or
 * with a gap in the chain), or a "start again" fallback added to make a crash go away.
 */
class MigrationChainTest {

    @Test
    fun `the migrations form an unbroken chain from the first version to the current one`() {
        val chain = ALL_MIGRATIONS.sortedBy { it.startVersion }
        assertEquals("the chain starts at version 1", 1, chain.first().startVersion)
        chain.zipWithNext().forEach { (a, b) ->
            assertEquals("no gap between ${a.endVersion} and ${b.startVersion}", a.endVersion, b.startVersion)
        }
        assertEquals("the chain reaches DB_VERSION", DB_VERSION, chain.last().endVersion)
        chain.forEach { assertEquals("each step goes up by one", it.startVersion + 1, it.endVersion) }
    }

    @Test
    fun `every version since schemas were exported has its schema file, up to the current one`() {
        val dir = File("schemas/com.mero.data.db.MeroDatabase")
        val versions = dir.listFiles { f -> f.extension == "json" }.orEmpty().map { it.nameWithoutExtension.toInt() }.sorted()
        assertTrue("schemas are exported", versions.isNotEmpty())
        assertEquals("the newest schema file is DB_VERSION", DB_VERSION, versions.last())
        assertEquals("no version missing between the first and the last", (versions.first()..versions.last()).toList(), versions)
    }

    @Test
    fun `the database is never opened with a destructive fallback`() {
        val source = File("src/main/java").walkTopDown().filter { it.extension == "kt" }
        val offenders = source.filter { it.readText().contains("fallbackToDestructiveMigration") }.map { it.name }.toList()
        assertTrue("destructive fallback found in $offenders", offenders.isEmpty())
    }

    @Test
    fun `the production database uses every migration`() {
        val app = File("src/main/java/com/mero/MeroApplication.kt").readText()
        assertTrue(app.contains("addMigrations(*com.mero.data.db.ALL_MIGRATIONS)"))
        assertFalse(app.contains("MIGRATION_1_2,"))
    }
}
