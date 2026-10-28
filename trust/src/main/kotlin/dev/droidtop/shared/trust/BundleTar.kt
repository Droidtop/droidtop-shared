package dev.droidtop.shared.trust

import java.io.BufferedInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream

/** A bundle that cannot be read safely; the message is for the person. */
class BundleTarException(message: String, cause: Throwable? = null) : IOException(message, cause)

/**
 * Reads a plugin bundle: a tar archive compressed with xz, the container both apps' bundles use
 * (the manifests inside differ on purpose, and stay in each app). This only reads; what an
 * entry means and whether the signature covers it is the caller's.
 *
 * Before the caller sees an entry, the entry is refused when it:
 * - names a path that could land outside the folder it is extracted to ([validatePath]),
 * - is a link, a device or anything but a regular file (directories are allowed and skipped),
 * - is larger than [Limits.maxEntryBytes] by its header,
 * - repeats a path an earlier entry already used (so a later entry cannot replace a verified one),
 * - or is more than [Limits.maxEntries] in number.
 *
 * The archive is streamed: nothing but the entry the caller asks to read whole is held in memory,
 * and the xz decoder is held to [Limits.xzMemoryKiB], so a bundle that asks for a huge
 * dictionary is refused instead of crashing the app.
 */
object BundleTar {
    class Limits(
        /** The most one entry may hold, by its tar header. */
        val maxEntryBytes: Long = 64L * 1024 * 1024,
        val maxEntries: Int = 20_000,
        /** The most memory the xz decoder may use, in KiB. */
        val xzMemoryKiB: Int = 512 * 1024,
        /**
         * The characters a path may be made of. The default is the strict set `A-Z a-z 0-9 . _ + @ - /`; an app
         * whose bundles carry other file names (spaces, say) widens it, never past [PERMISSIVE_PATH_CHARS].
         */
        val pathChars: Regex = STRICT_PATH_CHARS,
    )

    /** One regular file of the archive. [input] is valid only inside the callback. */
    class Entry internal constructor(
        val path: String,
        val size: Long,
        /** The tar mode bits (for example `0b111_101_101` for 0755). */
        val mode: Int,
        val input: InputStream,
    ) {
        /** The whole entry, refusing one bigger than [maximum]. For manifests and signatures, not payload. */
        fun readBytes(maximum: Long): ByteArray {
            if (size > maximum) throw BundleTarException("$path is larger than the allowed ${maximum / 1024} KiB")
            val bytes = ByteArray(size.toInt())
            var offset = 0
            while (offset < bytes.size) {
                val read = input.read(bytes, offset, bytes.size - offset)
                if (read <= 0) throw BundleTarException("$path is cut short")
                offset += read
            }
            return bytes
        }
    }

    /**
     * Calls [onEntry] for each regular file of [archive], in archive order. An entry the callback
     * does not read to the end is skipped. Throws [BundleTarException] for an archive that is
     * refused or cannot be read; an exception from [onEntry] passes through unchanged.
     */
    fun read(archive: File, limits: Limits = Limits(), onEntry: (Entry) -> Unit) {
        try {
            archive.inputStream().buffered().use { compressed ->
                @Suppress("DEPRECATION")
                XZCompressorInputStream(compressed, false, limits.xzMemoryKiB).use { xz ->
                    TarArchiveInputStream(BufferedInputStream(xz)).use { tar -> readEntries(tar, limits, onEntry) }
                }
            }
        } catch (e: BundleTarException) {
            throw e
        } catch (e: OutOfMemoryError) {
            throw BundleTarException("the bundle needs more memory than is available to read it", e)
        } catch (e: org.apache.commons.compress.MemoryLimitException) {
            throw BundleTarException("the bundle needs more memory to read than the limit allows", e)
        } catch (e: IOException) {
            throw BundleTarException("the bundle could not be read: ${e.message}", e)
        }
    }

    private fun readEntries(tar: TarArchiveInputStream, limits: Limits, onEntry: (Entry) -> Unit) {
        val seen = HashSet<String>()
        var count = 0
        while (true) {
            val entry: TarArchiveEntry = tar.nextEntry ?: return
            if (entry.isDirectory) {
                validatePath(entry.name.trimEnd('/'), limits.pathChars)
                continue
            }
            if (++count > limits.maxEntries) throw BundleTarException("the bundle holds more than ${limits.maxEntries} files")
            // isFile alone is true for a symbolic link in some versions, so the kinds are named.
            val special = entry.isSymbolicLink || entry.isLink || entry.isCharacterDevice || entry.isBlockDevice || entry.isFIFO
            if (special || !entry.isFile) throw BundleTarException("${entry.name} is a link or a special file, which a bundle may not hold")
            val path = validatePath(entry.name, limits.pathChars)
            if (entry.size > limits.maxEntryBytes) {
                throw BundleTarException("$path is larger than the per-file cap of ${limits.maxEntryBytes} bytes")
            }
            if (!seen.add(path)) throw BundleTarException("$path appears twice in the bundle")
            onEntry(Entry(path, entry.size, entry.mode, tar))
        }
    }

    /**
     * [path] with `\` read as `/`, or [BundleTarException]. A path is a relative `/`-separated name made of
     * [chars] (by default letters, digits and `. _ + @ -`), with no empty, `.` or `..` part: nothing that can
     * leave the folder a bundle is extracted into.
     */
    fun validatePath(path: String, chars: Regex = STRICT_PATH_CHARS): String {
        val clean = path.replace('\\', '/')
        if (clean.isBlank() || clean.startsWith('/') || clean.endsWith('/')) throw BundleTarException("\"$path\" is not a bundle path")
        if (clean.split('/').any { it.isBlank() || it == "." || it == ".." }) throw BundleTarException("\"$path\" is not a safe bundle path")
        if (!clean.matches(chars)) throw BundleTarException("\"$path\" has characters a bundle path may not have")
        return clean
    }

    val STRICT_PATH_CHARS = Regex("[A-Za-z0-9._+@/-]+")

    /** Anything but control characters: the widest set a bundle path may use. `..`, empty and absolute parts are refused whatever the set. */
    val PERMISSIVE_PATH_CHARS = Regex("[^\\u0000-\\u001f]+")
}
