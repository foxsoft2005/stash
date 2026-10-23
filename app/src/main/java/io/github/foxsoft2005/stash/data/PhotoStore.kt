package io.github.foxsoft2005.stash.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** Photos live in app-private storage: never in the gallery, never uploaded. */
class PhotoStore(context: Context) {

    private val dir = File(context.filesDir, "photos").apply { mkdirs() }

    fun newPhotoFile(): File = File(dir, "${UUID.randomUUID()}.jpg")

    /** The photo file for [path] if it exists and belongs to us; null otherwise. */
    fun existing(path: String?): File? =
        path?.let(::File)?.takeIf { it.parentFile == dir && it.isFile }

    /** Moves a restored photo into the photo folder under a fresh name. */
    suspend fun adopt(staged: File): File = withContext(Dispatchers.IO) {
        val target = newPhotoFile()
        // Same filesystem in practice (app-private storage), so this is a cheap rename.
        if (!staged.renameTo(target)) {
            staged.copyTo(target)
            staged.delete()
        }
        target
    }

    suspend fun delete(path: String?) = withContext(Dispatchers.IO) {
        if (path != null) File(path).takeIf { it.parentFile == dir }?.delete()
    }
}
