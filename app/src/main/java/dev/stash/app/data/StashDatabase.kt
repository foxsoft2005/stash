package dev.stash.app.data

import android.content.Context
import androidx.room3.Database
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers

/**
 * Schema changes: bump [version] and add a migration, e.g.
 * `autoMigrations = [AutoMigration(from = 1, to = 2)]` for simple column additions.
 * Never ship a schema change without one, or existing users lose their stash.
 * Exported schemas live in app/schemas/ and must be committed.
 */
@Database(entities = [Item::class], version = 1, exportSchema = true)
abstract class StashDatabase : RoomDatabase() {

    abstract fun itemDao(): ItemDao

    companion object {
        fun create(context: Context): StashDatabase =
            Room.databaseBuilder<StashDatabase>(
                context = context.applicationContext,
                name = context.getDatabasePath("stash.db").absolutePath,
            )
                // Bundled SQLite: same, recent SQLite version on every device.
                .setDriver(BundledSQLiteDriver())
                .setQueryCoroutineContext(Dispatchers.IO)
                .build()
    }
}
