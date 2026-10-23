package dev.stash.app.data.backup

import dev.stash.app.data.Item
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.random.Random

class BackupArchiveTest {

    private val password = "hunter2hunter2".toCharArray()

    private fun tempDir(): File = Files.createTempDirectory("stash-test").toFile().apply { deleteOnExit() }

    private val passport = BackupItem(
        name = "Passport", place = "Top drawer, blue box", tags = listOf("document", "travel wallet"),
        note = "renew 2029", lentTo = null, createdAt = 1_700_000_000_123, updatedAt = 1_700_000_500_000,
        photo = "photos/0f8fad5b-d9cb-469f-a165-70867728950e.jpg",
    )
    private val drill = BackupItem(
        name = "Дрель", place = "Гараж", tags = listOf("инструмент"), note = "", lentTo = "Mark",
        createdAt = 1_700_000_000_999, updatedAt = 1_700_000_000_999, photo = null,
    )

    @Test fun `full backup file round trip with photos`() {
        val src = tempDir()
        val photoBytes = Random(7).nextBytes(2_500_000) // spans several encryption segments
        val photo = File(src, "0f8fad5b-d9cb-469f-a165-70867728950e.jpg").apply { writeBytes(photoBytes) }
        val manifest = BackupManifest(exportedAt = 42, items = listOf(passport, drill))

        val out = ByteArrayOutputStream()
        BackupFile.write(out, password.copyOf(), manifest, mapOf(passport.photo!! to photo), iterations = 1_000)

        val staging = tempDir()
        val back = BackupFile.read(ByteArrayInputStream(out.toByteArray()), password.copyOf(), staging)
        assertEquals(manifest, back.manifest)
        assertArrayEquals(photoBytes, back.photos.getValue(passport.photo!!).readBytes())
        assertEquals(staging, back.photos.getValue(passport.photo!!).parentFile)
    }

    @Test fun `a cut-off backup is rejected even though the zip part was complete`() {
        val manifest = BackupManifest(exportedAt = 1, items = listOf(drill))
        val out = ByteArrayOutputStream()
        BackupFile.write(out, password.copyOf(), manifest, emptyMap(), iterations = 1_000)
        val cut = out.toByteArray().let { it.copyOf(it.size - 3) }
        try {
            BackupFile.read(ByteArrayInputStream(cut), password.copyOf(), tempDir())
            fail("truncation not detected")
        } catch (e: BackupException) {
            assertEquals(BackupException.Reason.WRONG_PASSWORD_OR_DAMAGED, e.reason) // single-segment file
        }
    }

    @Test fun `manifest json is stable and readable`() {
        val json = BackupArchive.encode(BackupManifest(exportedAt = 5, items = listOf(drill)))
        assertTrue(json, json.contains("\"format\":1"))
        assertTrue(json, json.contains("\"lentTo\":\"Mark\""))
        assertTrue(json, json.contains("\"photo\":null"))
        assertEquals(listOf(drill), BackupArchive.decode(json).items)
    }

    @Test fun `unknown keys from a future minor version are ignored`() {
        val json = """{"format":1,"exportedAt":9,"future":true,"items":[{"name":"Keys","place":"Hook","tags":["key"],
            "createdAt":3,"updatedAt":4,"room":"Hall","photo":null}]}"""
        val item = BackupArchive.decode(json).items.single()
        assertEquals("Keys", item.name)
        assertEquals("", item.note)
        assertEquals(null, item.lentTo)
    }

    @Test fun `newer format asks for an update, junk is damaged`() {
        expect(BackupException.Reason.NEWER_VERSION) { BackupArchive.decode("""{"format":2,"items":[]}""") }
        expect(BackupException.Reason.DAMAGED) { BackupArchive.decode("not json") }
        expect(BackupException.Reason.DAMAGED) { BackupArchive.decode("""{"items":[]}""") }
    }

    @Test fun `zip slip and unexpected entries are never written`() {
        val zipBytes = ByteArrayOutputStream().also { bytes ->
            ZipOutputStream(bytes).use { zip ->
                for (name in listOf("../../evil.jpg", "photos/../../evil.jpg", "photos/ok.jpg", "other.txt")) {
                    zip.putNextEntry(ZipEntry(name)); zip.write(1); zip.closeEntry()
                }
                zip.putNextEntry(ZipEntry(BackupArchive.MANIFEST))
                zip.write(BackupArchive.encode(BackupManifest(0, emptyList())).toByteArray())
                zip.closeEntry()
            }
        }.toByteArray()
        val staging = tempDir()
        val contents = BackupArchive.read(ByteArrayInputStream(zipBytes), staging)
        assertEquals(setOf("photos/ok.jpg"), contents.photos.keys)
        assertEquals(listOf("ok.jpg"), staging.list()!!.toList())
        assertTrue(!File(staging.parentFile, "evil.jpg").exists())
    }

    @Test fun `item converts to backup and back without losing anything`() {
        val item = Item(
            id = 77, name = "Spare keys", place = "Kitchen jar", tags = "key,house keys", note = "blue tag",
            photoPath = "/data/x/photos/a.jpg", lentTo = "Anna", createdAt = 10, updatedAt = 20,
        )
        val restored = item.toBackupItem("photos/a.jpg").toItem("/data/y/photos/b.jpg")
        assertEquals(item.copy(id = 0, photoPath = "/data/y/photos/b.jpg"), restored)
    }

    private fun expect(reason: BackupException.Reason, block: () -> Unit) {
        try {
            block(); fail("expected $reason")
        } catch (e: BackupException) {
            assertEquals(reason, e.reason)
        }
    }
}
