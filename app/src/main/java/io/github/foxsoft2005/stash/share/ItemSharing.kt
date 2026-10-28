package io.github.foxsoft2005.stash.share

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import io.github.foxsoft2005.stash.R
import java.io.File

/**
 * Hands "where it is" to another app through Android's share sheet. Nothing leaves the
 * phone unless the user picks an app there. Photos are shared as temporary, read-only
 * content:// links (FileProvider), limited to the photo folder.
 */
object ItemSharing {

    fun share(context: Context, name: String, place: String, lentTo: String?, note: String, photos: List<String>) {
        val text = ShareText.of(
            name, place, lentTo, note,
            placeFormat = context.getString(R.string.share_text),
            lentFormat = context.getString(R.string.share_text_lent),
        )
        val uris = photos.mapNotNull { path ->
            File(path).takeIf { it.isFile }?.let { FileProvider.getUriForFile(context, "${context.packageName}.photos", it) }
        }
        val send = if (uris.isEmpty()) {
            Intent(Intent.ACTION_SEND).setType("text/plain")
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).setType("image/jpeg").apply {
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                // The grant only reaches the chosen app if the URIs are in ClipData too.
                clipData = ClipData.newUri(context.contentResolver, name, uris.first()).apply {
                    uris.drop(1).forEach { addItem(ClipData.Item(it)) }
                }
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }.putExtra(Intent.EXTRA_TEXT, text)
        context.startActivity(Intent.createChooser(send, context.getString(R.string.share_title, name)))
    }
}
