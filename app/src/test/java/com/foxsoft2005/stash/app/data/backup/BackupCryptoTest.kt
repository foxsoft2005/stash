package com.foxsoft2005.stash.app.data.backup

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.random.Random

class BackupCryptoTest {

    private val password = "correct horse battery".toCharArray()
    private val fastIterations = 1_000 // real backups use 600 000; tests stay fast

    private fun encrypt(plain: ByteArray, pw: CharArray = password): ByteArray {
        val out = ByteArrayOutputStream()
        BackupCrypto.encrypt(out, pw.copyOf(), fastIterations).use { it.write(plain) }
        return out.toByteArray()
    }

    private fun decrypt(file: ByteArray, pw: CharArray = password): ByteArray =
        BackupCrypto.decrypt(ByteArrayInputStream(file), pw.copyOf()).readBytes()

    private fun assertFails(reason: BackupException.Reason, block: () -> Unit) {
        try {
            block()
            fail("expected $reason")
        } catch (e: BackupException) {
            assertEquals(reason, e.reason)
        }
    }

    private val sizes = listOf(
        0, 1, 1000,
        BackupCrypto.SEGMENT_BYTES - 1, BackupCrypto.SEGMENT_BYTES, BackupCrypto.SEGMENT_BYTES + 1,
        3 * BackupCrypto.SEGMENT_BYTES + 12345,
    )

    @Test fun `round trip at every segment boundary`() {
        for (size in sizes) {
            val plain = Random(size).nextBytes(size)
            assertArrayEquals("size $size", plain, decrypt(encrypt(plain)))
        }
    }

    @Test fun `ciphertext reveals nothing obvious and differs every time`() {
        val plain = "Passport → top drawer, blue box".repeat(100).toByteArray()
        val a = encrypt(plain)
        val b = encrypt(plain)
        assertFalse(a.contentEquals(b)) // fresh salt + nonce per file
        assertFalse(String(a, Charsets.ISO_8859_1).contains("Passport"))
    }

    @Test fun `wrong password is reported as such`() {
        val file = encrypt("hello".toByteArray())
        assertFails(BackupException.Reason.WRONG_PASSWORD_OR_DAMAGED) { decrypt(file, "wrong".toCharArray()) }
    }

    @Test fun `any flipped byte is detected`() {
        val file = encrypt(Random(1).nextBytes(2 * BackupCrypto.SEGMENT_BYTES + 10))
        // header (salt), first segment, second segment, last segment
        for (pos in listOf(20, 100, BackupCrypto.SEGMENT_BYTES + 500, file.size - 1)) {
            val bad = file.copyOf().also { it[pos] = (it[pos].toInt() xor 1).toByte() }
            try {
                decrypt(bad)
                fail("flip at $pos not detected")
            } catch (e: BackupException) {
                assertTrue(e.reason == BackupException.Reason.DAMAGED || e.reason == BackupException.Reason.WRONG_PASSWORD_OR_DAMAGED)
            }
        }
    }

    @Test fun `truncated file is detected, even exactly at a segment boundary`() {
        val file = encrypt(Random(2).nextBytes(3 * BackupCrypto.SEGMENT_BYTES))
        val headerBytes = 36
        val segment = BackupCrypto.SEGMENT_BYTES + 16
        for (cut in listOf(file.size - 1, headerBytes + 2 * segment)) {
            assertFails(BackupException.Reason.DAMAGED) { decrypt(file.copyOf(cut)) }
        }
        // Cut right after the first segment: it fails on segment 0, reported like a wrong password.
        assertFails(BackupException.Reason.WRONG_PASSWORD_OR_DAMAGED) { decrypt(file.copyOf(headerBytes + segment)) }
    }

    @Test fun `dropping or swapping segments is detected`() {
        val file = encrypt(Random(3).nextBytes(3 * BackupCrypto.SEGMENT_BYTES))
        val h = 36
        val s = BackupCrypto.SEGMENT_BYTES + 16
        val header = file.copyOfRange(0, h)
        val seg = (0 until 4).map { i -> file.copyOfRange(h + i * s, minOf(file.size, h + (i + 1) * s)) }
        val swapped = header + seg[1] + seg[0] + seg[2] + seg[3]
        val dropped = header + seg[0] + seg[2] + seg[3]
        assertFails(BackupException.Reason.WRONG_PASSWORD_OR_DAMAGED) { decrypt(swapped) }
        assertFails(BackupException.Reason.DAMAGED) { decrypt(dropped) }
    }

    @Test fun `foreign files are recognized`() {
        assertFails(BackupException.Reason.NOT_A_BACKUP) { decrypt("PK\u0003\u0004 a zip file".toByteArray()) }
        assertFails(BackupException.Reason.NOT_A_BACKUP) { decrypt(ByteArray(0)) }
        assertFails(BackupException.Reason.NOT_A_BACKUP) { decrypt("STASHBK1".toByteArray()) }
    }

    @Test fun `newer format version asks for an app update`() {
        val file = encrypt("x".toByteArray()).also { it[8] = 2 }
        assertFails(BackupException.Reason.NEWER_VERSION) { decrypt(file) }
    }

    @Test fun `absurd iteration count in a crafted header is refused`() {
        val file = encrypt("x".toByteArray())
        file[9] = 0x7F // iterations ≈ 2 billion
        assertFails(BackupException.Reason.NOT_A_BACKUP) { decrypt(file) }
    }
}
