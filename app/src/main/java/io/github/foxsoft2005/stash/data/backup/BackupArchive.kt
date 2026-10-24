package io.github.foxsoft2005.stash.data.backup

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * One item as stored in a backup. [photo] and [placePhoto] are the zip entry names
 * of its photos (the item, and where it is), if any.
 */
data class BackupItem(
    val name: String,
    val place: String,
    val tags: List<String>,
    val note: String,
    val lentTo: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val photo: String?,
    val placePhoto: String? = null,
)

data class BackupManifest(
    val exportedAt: Long,
    val items: List<BackupItem>,
    val format: Int = FORMAT,
) {
    companion object {
        /**
         * 1: first version. 2: adds "placePhoto" per item. Older apps refuse newer
         * formats ("update Stash") instead of silently dropping what they don't know.
         */
        const val FORMAT = 2
    }
}

/** What came out of a backup: the manifest plus photos extracted to a staging folder. */
class BackupContents(val manifest: BackupManifest, val photos: Map<String, File>)

/**
 * The plaintext inside a `.stash` file: a zip with `photos/<file>.jpg` entries and
 * `manifest.json` written last.
 *
 * The JSON is mapped by hand rather than with @Serializable on purpose: it's a file
 * format people keep for years, so key names must never change just because a
 * Kotlin property was renamed, and unknown keys from newer versions are ignored.
 */
object BackupArchive {

    const val MANIFEST = "manifest.json"
    private const val PHOTO_DIR = "photos/"
    private const val MAX_MANIFEST_BYTES = 32L shl 20 // 32 MiB ≈ hundreds of thousands of items

    // Only names we write ourselves. Also blocks "zip slip" (../../ paths) outright.
    private val photoEntry = Regex("""photos/[A-Za-z0-9_-]{1,64}\.jpg""")

    fun photoEntryName(file: File): String = PHOTO_DIR + file.name

    /** Writes the archive and finishes the zip, without closing [out]. */
    fun write(out: OutputStream, manifest: BackupManifest, photos: Map<String, File>) {
        val zip = ZipOutputStream(out)
        // JPEGs don't shrink; fast compression still helps the JSON.
        zip.setLevel(Deflater.BEST_SPEED)
        for ((entry, file) in photos) {
            require(photoEntry.matches(entry)) { "bad photo entry $entry" }
            zip.putNextEntry(ZipEntry(entry))
            file.inputStream().use { it.copyTo(zip) }
            zip.closeEntry()
        }
        zip.putNextEntry(ZipEntry(MANIFEST))
        zip.write(encode(manifest).toByteArray(Charsets.UTF_8))
        zip.closeEntry()
        zip.finish()
    }

    /**
     * Reads entries from [input] into [staging] (photos) and memory (manifest).
     * Does not close or drain [input]; see [BackupFile.read] for why that matters.
     */
    fun read(input: InputStream, staging: File): BackupContents {
        val zip = ZipInputStream(input)
        val photos = mutableMapOf<String, File>()
        var manifest: BackupManifest? = null
        while (true) {
            val entry = zip.nextEntry ?: break
            when {
                entry.isDirectory -> Unit
                entry.name == MANIFEST -> manifest = decode(readLimited(zip, MAX_MANIFEST_BYTES))
                photoEntry.matches(entry.name) -> {
                    val target = insideStaging(staging, entry.name.removePrefix(PHOTO_DIR))
                    target.outputStream().use { zip.copyTo(it) }
                    photos[entry.name] = target
                }
                else -> Unit // from a newer app version, or junk: ignore
            }
        }
        val found = manifest ?: throw BackupException(BackupException.Reason.DAMAGED)
        return BackupContents(found, photos)
    }

    /**
     * The file [name] inside [staging], refusing anything that would land outside it
     * ("Zip Slip": an entry like `photos/../../databases/stash.db`).
     *
     * [photoEntry] already only lets through plain `photos/<letters, digits, _ or ->.jpg`
     * names, so this can't trigger today; it's the second, independent lock, and the
     * one that doesn't depend on keeping that regex strict forever.
     */
    internal fun insideStaging(staging: File, name: String): File {
        val root = staging.toPath().toAbsolutePath().normalize()
        val target = root.resolve(name).normalize()
        if (!target.startsWith(root) || target == root) throw BackupException(BackupException.Reason.DAMAGED)
        return target.toFile()
    }

    internal fun encode(manifest: BackupManifest): String = Json.encodeToString(
        JsonObject.serializer(),
        buildJsonObject {
            put("format", manifest.format)
            put("app", "io.github.foxsoft2005.stash")
            put("exportedAt", manifest.exportedAt)
            put(
                "items",
                buildJsonArray {
                    manifest.items.forEach { item ->
                        add(
                            buildJsonObject {
                                put("name", item.name)
                                put("place", item.place)
                                put("tags", JsonArray(item.tags.map(::JsonPrimitive)))
                                put("note", item.note)
                                put("lentTo", item.lentTo?.let(::JsonPrimitive) ?: JsonNull)
                                put("createdAt", item.createdAt)
                                put("updatedAt", item.updatedAt)
                                put("photo", item.photo?.let(::JsonPrimitive) ?: JsonNull)
                                put("placePhoto", item.placePhoto?.let(::JsonPrimitive) ?: JsonNull)
                            },
                        )
                    }
                },
            )
        },
    )

    internal fun decode(text: String): BackupManifest {
        try {
            val root = Json.parseToJsonElement(text).jsonObject
            val format = root["format"]?.jsonPrimitive?.int ?: throw BackupException(BackupException.Reason.DAMAGED)
            if (format > BackupManifest.FORMAT) throw BackupException(BackupException.Reason.NEWER_VERSION)
            val items = root["items"]?.jsonArray.orEmpty().map { element ->
                val o = element.jsonObject
                fun str(key: String) = o[key]?.jsonPrimitive?.contentOrNull
                BackupItem(
                    name = str("name").orEmpty(),
                    place = str("place").orEmpty(),
                    tags = o["tags"]?.jsonArray.orEmpty().mapNotNull { it.jsonPrimitive.contentOrNull },
                    note = str("note").orEmpty(),
                    lentTo = str("lentTo")?.takeIf { it.isNotBlank() },
                    createdAt = o["createdAt"]?.jsonPrimitive?.long ?: 0L,
                    updatedAt = o["updatedAt"]?.jsonPrimitive?.long ?: 0L,
                    photo = str("photo")?.takeIf(photoEntry::matches),
                    placePhoto = str("placePhoto")?.takeIf(photoEntry::matches), // absent in format 1
                )
            }.filter { it.name.isNotBlank() }
            return BackupManifest(
                exportedAt = root["exportedAt"]?.jsonPrimitive?.long ?: 0L,
                items = items,
                format = format,
            )
        } catch (e: BackupException) {
            throw e
        } catch (e: Exception) { // malformed JSON / wrong types
            throw BackupException(BackupException.Reason.DAMAGED, e)
        }
    }

    private fun readLimited(input: InputStream, limit: Long): String {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val n = input.read(buf)
            if (n == -1) break
            total += n
            if (total > limit) throw BackupException(BackupException.Reason.DAMAGED)
            out.write(buf, 0, n)
        }
        return out.toString(Charsets.UTF_8.name())
    }
}

/** A whole `.stash` file: [BackupCrypto] around [BackupArchive]. */
object BackupFile {

    /** Writes an encrypted backup to [out] and closes it. */
    fun write(
        out: OutputStream,
        password: CharArray,
        manifest: BackupManifest,
        photos: Map<String, File>,
        iterations: Int = BackupCrypto.DEFAULT_ITERATIONS,
    ) {
        BackupCrypto.encrypt(out, password, iterations).use { encrypted ->
            BackupArchive.write(encrypted, manifest, photos)
        }
    }

    /**
     * Decrypts and unpacks a backup into [staging]. Returns only once the **whole**
     * file has been authenticated: a zip reader stops at the central directory, so
     * the rest is drained here; that's what checks the final segment. Until this
     * returns, nothing from the file may be trusted or saved.
     */
    fun read(input: InputStream, password: CharArray, staging: File): BackupContents {
        val plain = BackupCrypto.decrypt(input, password)
        val contents = BackupArchive.read(plain, staging)
        val sink = ByteArray(64 * 1024)
        while (plain.read(sink) != -1) Unit
        return contents
    }
}
