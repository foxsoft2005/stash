package io.github.foxsoft2005.stash.data

import android.content.Context
import androidx.room3.AutoMigration
import androidx.room3.Database
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers

/**
 * Schema changes: bump [version] and add a migration, e.g.
 * `autoMigrations = [AutoMigration(from = 2, to = 3)]` for simple column additions,
 * or a manual one in Migrations.kt when stored data has to change.
 * Never ship a schema change without one, or existing users lose their stash.
 * Exported schemas live in app/schemas/ and must be committed.
 */
@Database(
    entities = [Item::class],
    version = 4,
    exportSchema = true,
    autoMigrations = [
        // v3 adds items.placePhotoPath (nullable, so existing rows simply have none).
        AutoMigration(from = 2, to = 3),
        // v4 adds items.checkedAt (nullable: nothing has been confirmed yet).
        AutoMigration(from = 3, to = 4),
    ],
)
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
                .addMigrations(*ALL_MIGRATIONS)
                .build()
    }
}
