package dev.stash.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** Photos live in app-private storage: never in the gallery, never uploaded. */
class PhotoStore(context: Context) {

    private val dir = File(context.filesDir, "photos").apply { mkdirs() }

    fun newPhotoFile(): File = File(dir, "${UUID.randomUUID()}.jpg")

    suspend fun delete(path: String?) = withContext(Dispatchers.IO) {
        if (path != null) File(path).takeIf { it.parentFile == dir }?.delete()
    }
}
