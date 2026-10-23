package dev.stash.app.data.backup

import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.EOFException
import java.io.FilterOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.PushbackInputStream
import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Password-based encryption for `.stash` backup files.
 *
 * File layout (all integers big-endian):
 * ```
 * header  : "STASHBK1" | version (1 byte) | PBKDF2 iterations (4) | salt (16) | nonce prefix (7)
 * body    : segment 0 | segment 1 | ... | last segment
 * segment : AES-256-GCM(plaintext ≤ 1 MiB) incl. 16-byte tag
 * ```
 * - Key = PBKDF2-HMAC-SHA256(password, salt, iterations). A fresh random salt per
 *   file means a fresh key per file.
 * - Segment nonce = nonce prefix (7) | segment index (4) | last-segment flag (1).
 *   This is the "STREAM" construction (Hoang et al., also used by Google Tink's
 *   AesGcmHkdfStreaming): reordering, dropping or truncating segments breaks a tag.
 * - Every segment authenticates the whole header as associated data, so the
 *   salt/iteration count can't be swapped either.
 *
 * Why chunks: Android's AES-GCM decryption buffers the entire ciphertext before
 * it releases any plaintext, which would mean holding all photos in memory.
 */
object BackupCrypto {

    const val DEFAULT_ITERATIONS = 600_000 // OWASP 2023 recommendation for PBKDF2-HMAC-SHA256

    private val MAGIC = "STASHBK1".toByteArray(Charsets.US_ASCII)
    private const val FORMAT_VERSION: Byte = 1
    private const val SALT_BYTES = 16
    private const val NONCE_PREFIX_BYTES = 7
    private const val TAG_BITS = 128
    private const val TAG_BYTES = TAG_BITS / 8
    internal const val SEGMENT_BYTES = 1 shl 20 // plaintext per segment
    private const val KEY_BITS = 256

    // A header comes from an untrusted file: cap the work it can ask for, so a crafted
    // file can't freeze the phone. (A *low* count only weakens that file itself.)
    private const val MAX_ITERATIONS = 10_000_000

    /**
     * Wraps [out]: everything written is encrypted. **Must be closed**: closing writes
     * the final segment, without which the file can't be restored.
     */
    fun encrypt(
        out: OutputStream,
        password: CharArray,
        iterations: Int = DEFAULT_ITERATIONS,
        random: SecureRandom = SecureRandom(),
    ): OutputStream {
        require(iterations in 1..MAX_ITERATIONS) { "iterations out of range" }
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val noncePrefix = ByteArray(NONCE_PREFIX_BYTES).also(random::nextBytes)
        val header = header(iterations, salt, noncePrefix)
        out.write(header)
        return EncryptingStream(out, deriveKey(password, salt, iterations), header, noncePrefix)
    }

    /**
     * Wraps [input]: reads decrypted plaintext. Reading to the end (-1) is what
     * authenticates the final segment, so callers must read until EOF before
     * trusting anything they read.
     *
     * @throws BackupException NOT_A_BACKUP for a foreign file, NEWER_VERSION for a
     *   future format; reads throw WRONG_PASSWORD_OR_DAMAGED (first segment) or DAMAGED.
     */
    fun decrypt(input: InputStream, password: CharArray): InputStream {
        val data = DataInputStream(input)
        val magic = ByteArray(MAGIC.size)
        try {
            data.readFully(magic)
        } catch (e: EOFException) {
            throw BackupException(BackupException.Reason.NOT_A_BACKUP, e)
        }
        if (!magic.contentEquals(MAGIC)) throw BackupException(BackupException.Reason.NOT_A_BACKUP)
        try {
            val version = data.readByte()
            if (version > FORMAT_VERSION) throw BackupException(BackupException.Reason.NEWER_VERSION)
            if (version != FORMAT_VERSION) throw BackupException(BackupException.Reason.NOT_A_BACKUP)
            val iterations = data.readInt()
            if (iterations !in 1..MAX_ITERATIONS) throw BackupException(BackupException.Reason.NOT_A_BACKUP)
            val salt = ByteArray(SALT_BYTES).also(data::readFully)
            val noncePrefix = ByteArray(NONCE_PREFIX_BYTES).also(data::readFully)
            val header = header(iterations, salt, noncePrefix)
            return DecryptingStream(input, deriveKey(password, salt, iterations), header, noncePrefix)
        } catch (e: EOFException) {
            throw BackupException(BackupException.Reason.NOT_A_BACKUP, e)
        }
    }

    private fun header(iterations: Int, salt: ByteArray, noncePrefix: ByteArray): ByteArray =
        ByteArrayOutputStream().apply {
            write(MAGIC)
            write(FORMAT_VERSION.toInt())
            write(ByteBuffer.allocate(4).putInt(iterations).array())
            write(salt)
            write(noncePrefix)
        }.toByteArray()

    private fun deriveKey(password: CharArray, salt: ByteArray, iterations: Int): SecretKey {
        val spec = PBEKeySpec(password, salt, iterations, KEY_BITS)
        try {
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            return SecretKeySpec(bytes, "AES").also { bytes.fill(0) }
        } finally {
            spec.clearPassword()
        }
    }

    private fun nonce(prefix: ByteArray, index: Int, last: Boolean): GCMParameterSpec {
        val nonce = ByteBuffer.allocate(NONCE_PREFIX_BYTES + 5)
            .put(prefix)
            .putInt(index)
            .put(if (last) 1 else 0)
            .array()
        return GCMParameterSpec(TAG_BITS, nonce)
    }

    private class EncryptingStream(
        out: OutputStream,
        private val key: SecretKey,
        private val header: ByteArray,
        private val noncePrefix: ByteArray,
    ) : FilterOutputStream(out) {
        private val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        private val buffer = ByteArray(SEGMENT_BYTES)
        private var filled = 0
        private var index = 0
        private var closed = false

        override fun write(b: Int) = write(byteArrayOf(b.toByte()), 0, 1)

        override fun write(b: ByteArray, off: Int, len: Int) {
            check(!closed) { "stream closed" }
            var offset = off
            var remaining = len
            while (remaining > 0) {
                // A full buffer is only sealed once more data arrives, because only
                // close() knows which segment is the last one.
                if (filled == SEGMENT_BYTES) sealSegment(last = false)
                val n = minOf(remaining, SEGMENT_BYTES - filled)
                System.arraycopy(b, offset, buffer, filled, n)
                filled += n
                offset += n
                remaining -= n
            }
        }

        private fun sealSegment(last: Boolean) {
            if (index == Int.MAX_VALUE) throw IOException("backup too large")
            cipher.init(Cipher.ENCRYPT_MODE, key, nonce(noncePrefix, index, last))
            cipher.updateAAD(header)
            out.write(cipher.doFinal(buffer, 0, filled))
            index++
            filled = 0
        }

        override fun flush() = out.flush() // never seals early: segments stay full-size

        override fun close() {
            if (closed) return
            closed = true
            try {
                sealSegment(last = true)
                buffer.fill(0)
            } finally {
                out.close()
            }
        }
    }

    private class DecryptingStream(
        input: InputStream,
        private val key: SecretKey,
        private val header: ByteArray,
        private val noncePrefix: ByteArray,
    ) : InputStream() {
        private val source = PushbackInputStream(input, 1)
        private val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        private val segment = ByteArray(SEGMENT_BYTES + TAG_BYTES)
        private var plain = ByteArray(0)
        private var pos = 0
        private var index = 0
        private var finished = false

        override fun read(): Int {
            val one = ByteArray(1)
            return if (read(one, 0, 1) == -1) -1 else one[0].toInt() and 0xFF
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (len == 0) return 0
            while (pos == plain.size) {
                if (finished) return -1
                openNextSegment()
            }
            val n = minOf(len, plain.size - pos)
            System.arraycopy(plain, pos, b, off, n)
            pos += n
            return n
        }

        private fun openNextSegment() {
            val read = readUpTo(segment)
            // A segment shorter than full size, or a full one followed by EOF, is the last.
            val last = read < segment.size || peekEof()
            if (read < TAG_BYTES) throw damaged()
            try {
                cipher.init(Cipher.DECRYPT_MODE, key, nonce(noncePrefix, index, last))
                cipher.updateAAD(header)
                plain = cipher.doFinal(segment, 0, read)
            } catch (e: AEADBadTagException) {
                throw damaged(e)
            } catch (e: GeneralSecurityException) {
                throw damaged(e)
            }
            pos = 0
            index++
            finished = last
        }

        // On the very first segment a bad tag almost always means a wrong password.
        private fun damaged(cause: Throwable? = null) = BackupException(
            if (index == 0) BackupException.Reason.WRONG_PASSWORD_OR_DAMAGED else BackupException.Reason.DAMAGED,
            cause,
        )

        private fun readUpTo(buf: ByteArray): Int {
            var total = 0
            while (total < buf.size) {
                val n = source.read(buf, total, buf.size - total)
                if (n == -1) break
                total += n
            }
            return total
        }

        private fun peekEof(): Boolean {
            val next = source.read()
            if (next == -1) return true
            source.unread(next)
            return false
        }

        override fun close() = source.close()
    }
}

/** Why a backup couldn't be read. Mapped to user-facing messages by the UI. */
class BackupException(val reason: Reason, cause: Throwable? = null) : IOException(reason.name, cause) {
    enum class Reason { NOT_A_BACKUP, WRONG_PASSWORD_OR_DAMAGED, DAMAGED, NEWER_VERSION }
}
