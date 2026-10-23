package dev.stash.app.data

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * v1 → v2: `items.tags` changes from space-separated to comma-separated (see [Tags]).
 * The table's columns don't change, only the stored format, so this is a data
 * migration that Room can't generate on its own.
 *
 * In v1 every tag was a single word (multi-word tags were already split apart on
 * save), so turning each space into a comma keeps every user's tags exactly as
 * they appeared before. Runs of spaces become empty entries, which [Tags.split]
 * drops on read.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL(MIGRATION_1_2_SQL)
    }
}

internal const val MIGRATION_1_2_SQL =
    "UPDATE items SET tags = REPLACE(TRIM(tags), ' ', '${Tags.SEPARATOR}')"

val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2)
