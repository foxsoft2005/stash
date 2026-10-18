package dev.stash.app.data

import android.content.Context
import androidx.room3.Database
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers

@Database(entities = [Item::class], version = 1, exportSchema = false)
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
