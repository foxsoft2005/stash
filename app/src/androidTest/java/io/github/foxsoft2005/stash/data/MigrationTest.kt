package io.github.foxsoft2005.stash.data

import androidx.room3.testing.MigrationTestHelper
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Existing stashes must survive every app update. Each test builds an old database
 * from the exported schema (app/schemas/), puts an item in with plain SQL, upgrades
 * it, and checks the item is intact; the helper also checks the upgraded schema
 * matches what the app expects.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    // A fresh file for every test: the helper refuses to "create" an old version
    // over a database a previous test left behind.
    private val dbFile = instrumentation.targetContext.getDatabasePath("migration-test.db").also { db ->
        listOf("", "-wal", "-shm", "-journal").forEach { File(db.path + it).delete() }
    }

    @get:Rule
    val helper = MigrationTestHelper(
        instrumentation = instrumentation,
        databaseClass = StashDatabase::class,
        driver = BundledSQLiteDriver(), // same driver as the app
        file = dbFile,
    )

    private fun SQLiteConnection.insertPassport(tags: String) = execSQL(
        "INSERT INTO items (id, name, place, tags, note, photoPath, lentTo, createdAt, updatedAt) " +
            "VALUES (1, 'Passport', 'Top drawer', '$tags', 'renew 2029', '/p/a.jpg', NULL, 10, 20)",
    )

    private fun SQLiteConnection.row(sql: String): List<String?> = prepare(sql).use { st ->
        assertTrue("no row for: $sql", st.step())
        (0 until st.getColumnCount()).map { if (st.isNull(it)) null else st.getText(it) }
    }

    @Test fun v2ToLatestKeepsItems() = runBlocking {
        helper.createDatabase(2).use { it.insertPassport("document,travel wallet") }
        helper.runMigrationsAndValidate(LATEST, emptyList()).use { db ->
            assertEquals(
                listOf("Passport", "Top drawer", "document,travel wallet", "renew 2029", "/p/a.jpg", "10", "20", null, null),
                db.row("SELECT name, place, tags, note, photoPath, createdAt, updatedAt, placePhotoPath, checkedAt FROM items WHERE id = 1"),
            )
        }
    }

    @Test fun v3ToV4KeepsPlacePhoto() = runBlocking {
        helper.createDatabase(3).use {
            it.execSQL(
                "INSERT INTO items (id, name, place, tags, note, photoPath, lentTo, placePhotoPath, createdAt, updatedAt) " +
                    "VALUES (1, 'Drill', 'Garage', '', '', '/p/d.jpg', NULL, '/p/shelf.jpg', 10, 20)",
            )
        }
        helper.runMigrationsAndValidate(4, emptyList()).use { db ->
            assertEquals(listOf("Drill", "/p/shelf.jpg", null), db.row("SELECT name, placePhotoPath, checkedAt FROM items WHERE id = 1"))
        }
    }

    @Test fun v1ToLatestRunsEveryStep() = runBlocking {
        helper.createDatabase(1).use { it.insertPassport("document travel") }
        helper.runMigrationsAndValidate(LATEST, listOf(MIGRATION_1_2)).use { db ->
            assertEquals(listOf("document,travel", null), db.row("SELECT tags, placePhotoPath FROM items WHERE id = 1"))
        }
    }

    private companion object {
        /** Bump with StashDatabase.version (and add a test for the new step). */
        const val LATEST = 4
    }
}
