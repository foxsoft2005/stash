package io.github.foxsoft2005.stash.data.backup

import android.content.Context
import android.net.Uri
import io.github.foxsoft2005.stash.data.ItemDao
import io.github.foxsoft2005.stash.data.PhotoStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.util.UUID

data class ExportResult(val items: Int, val photos: Int)

data class RestoreResult(val added: Int, val alreadyOnPhone: Int)

/**
 * Backs the stash up to, and merges it back from, a file the user picked with the
 * system file picker (Storage Access Framework). Where the file goes (Downloads,
 * a USB stick, a cloud drive's picker entry) is the user's choice; Stash itself
 * still has no INTERNET permission, and the file is encrypted before it leaves.
 */
class BackupManager(
    context: Context,
    private val dao: ItemDao,
    private val photos: PhotoStore,
    private val status: BackupStatusStore,
) {
    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver

    suspend fun export(target: Uri, password: CharArray): ExportResult = withContext(Dispatchers.IO) {
        val items = dao.getAll()
        val photoFiles = linkedMapOf<String, File>()
        fun entryFor(path: String?): String? {
            val photo = photos.existing(path) ?: return null
            return BackupArchive.photoEntryName(photo).also { photoFiles[it] = photo }
        }
        val backupItems = items.map { item ->
            item.toBackupItem(entryFor(item.photoPath), entryFor(item.placePhotoPath))
        }
        val manifest = BackupManifest(exportedAt = System.currentTimeMillis(), items = backupItems)
        // "wt" = write + truncate, so overwriting an older backup can't leave its tail behind.
        val out = resolver.openOutputStream(target, "wt") ?: throw FileNotFoundException(target.toString())
        BackupFile.write(out.buffered(), password, manifest, photoFiles) // closes the stream
        status.markBackedUp(manifest.exportedAt) // only after the file is complete
        ExportResult(items = items.size, photos = photoFiles.size)
    }

    /**
     * Merges a backup into the stash (see [BackupMerge]). The file is fully
     * decrypted and authenticated into a private staging folder first; only then is
     * anything added, so a wrong password or a damaged file changes nothing.
     */
    suspend fun restore(source: Uri, password: CharArray): RestoreResult = withContext(Dispatchers.IO) {
        // Leftovers from a restore that was killed midway.
        appContext.cacheDir.listFiles { f -> f.name.startsWith(STAGING_PREFIX) }?.forEach { it.deleteRecursively() }
        val staging = File(appContext.cacheDir, STAGING_PREFIX + UUID.randomUUID()).apply { mkdirs() }
        try {
            val contents = (resolver.openInputStream(source) ?: throw FileNotFoundException(source.toString()))
                .buffered()
                .use { BackupFile.read(it, password, staging) }

            val toAdd = BackupMerge.itemsToAdd(dao.getAll(), contents.manifest.items)
            for (item in toAdd) {
                suspend fun adopt(entry: String?) = entry?.let(contents.photos::get)?.let { photos.adopt(it).absolutePath }
                dao.upsert(item.toItem(adopt(item.photo), adopt(item.placePhoto)))
            }
            RestoreResult(added = toAdd.size, alreadyOnPhone = contents.manifest.items.size - toAdd.size)
        } finally {
            withContext(NonCancellable) { staging.deleteRecursively() }
        }
    }

    private companion object {
        const val STAGING_PREFIX = "restore-"
    }
}
