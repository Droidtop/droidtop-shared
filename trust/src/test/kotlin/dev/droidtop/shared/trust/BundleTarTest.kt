package dev.droidtop.shared.trust

import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.xz.XZCompressorOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class BundleTarTest {
    private class Item(val name: String, val bytes: ByteArray = ByteArray(0), val type: Byte = TarConstants.LF_NORMAL, val link: String? = null)

    private object TarConstants {
        const val LF_NORMAL = '0'.code.toByte()
        const val LF_SYMLINK = '2'.code.toByte()
        const val LF_DIR = '5'.code.toByte()
    }

    private fun bundle(vararg items: Item): File {
        val raw = ByteArrayOutputStream()
        TarArchiveOutputStream(raw).use { tar ->
            tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)
            for (item in items) {
                val entry = TarArchiveEntry(item.name, item.type)
                if (item.type == TarConstants.LF_NORMAL) entry.size = item.bytes.size.toLong()
                if (item.link != null) entry.linkName = item.link
                entry.mode = 0b111_101_101
                tar.putArchiveEntry(entry)
                if (item.type == TarConstants.LF_NORMAL) tar.write(item.bytes)
                tar.closeArchiveEntry()
            }
        }
        val file = Files.createTempFile("bundle", ".tar.xz").toFile().apply { deleteOnExit() }
        file.outputStream().use { out -> XZCompressorOutputStream(out).use { it.write(raw.toByteArray()) } }
        return file
    }

    private fun readAll(archive: File, limits: BundleTar.Limits = BundleTar.Limits()): Map<String, ByteArray> {
        val out = linkedMapOf<String, ByteArray>()
        BundleTar.read(archive, limits) { out[it.path] = it.readBytes(1L shl 20) }
        return out
    }

    private fun refused(archive: File, limits: BundleTar.Limits = BundleTar.Limits()): String {
        try {
            readAll(archive, limits)
        } catch (e: BundleTarException) {
            return e.message.orEmpty()
        }
        fail("the archive should have been refused")
        return ""
    }

    @Test
    fun `entries come in archive order with their bytes and mode`() {
        val archive = bundle(Item("manifest.json", "{}".toByteArray()), Item("manifest.jws", "sig".toByteArray()), Item("bin/run", byteArrayOf(1, 2, 3)))
        val seen = mutableListOf<Triple<String, Long, Int>>()
        BundleTar.read(archive) { seen += Triple(it.path, it.size, it.mode and 0b111_111_111) }
        assertEquals(listOf("manifest.json", "manifest.jws", "bin/run"), seen.map { it.first })
        assertEquals(listOf(2L, 3L, 3L), seen.map { it.second })
        assertEquals(0b111_101_101, seen[2].third)
        assertEquals("sig", String(readAll(archive)["manifest.jws"]!!))
    }

    @Test
    fun `an entry the callback leaves unread is skipped`() {
        val archive = bundle(Item("a.bin", ByteArray(5000) { 7 }), Item("b.txt", "after".toByteArray()))
        val read = mutableListOf<String>()
        BundleTar.read(archive) { if (it.path == "b.txt") read += String(it.readBytes(100)) }
        assertEquals(listOf("after"), read)
    }

    @Test
    fun `directories are allowed and not reported`() {
        val archive = bundle(Item("bin/", type = TarConstants.LF_DIR), Item("bin/run", byteArrayOf(9)))
        assertEquals(listOf("bin/run"), readAll(archive).keys.toList())
    }

    @Test
    fun `a path that could leave the folder is refused`() {
        for (name in listOf("../escape", "a/../../escape", "a//b", "a/./b", "has space", "ünï")) {
            val reason = refused(bundle(Item(name, byteArrayOf(1))))
            assertTrue("$name: $reason", reason.contains("bundle path"))
        }
    }

    @Test
    fun `validatePath reads backslashes as slashes and keeps safe names`() {
        assertEquals("lib/arm64-v8a/libx.so", BundleTar.validatePath("lib\\arm64-v8a\\libx.so"))
        assertEquals("a+b@1.2_3-4/c", BundleTar.validatePath("a+b@1.2_3-4/c"))
        for (bad in listOf("", " ", "/x", "x/", "a/../b", "..", "a//b", "a b")) {
            try {
                BundleTar.validatePath(bad)
                fail("\"$bad\" should be refused")
            } catch (_: BundleTarException) {
            }
        }
    }

    @Test
    fun `an app can widen the characters but never the structure of a path`() {
        val wide = BundleTar.Limits(pathChars = BundleTar.PERMISSIVE_PATH_CHARS)
        assertEquals(
            mapOf("flutter assets/Icons #1.ttf" to 1),
            readAll(bundle(Item("flutter assets/Icons #1.ttf", byteArrayOf(1))), wide).mapValues { it.value.size },
        )
        for (name in listOf("../escape", "a//b", "a/./b", "a/../b")) {
            assertTrue(name, refused(bundle(Item(name, byteArrayOf(1))), wide).contains("bundle path"))
        }
        assertTrue(refused(bundle(Item("has space", byteArrayOf(1)))).contains("characters"))
        try {
            BundleTar.validatePath("tab\there", BundleTar.PERMISSIVE_PATH_CHARS)
            fail("control characters are refused")
        } catch (_: BundleTarException) {
        }
    }

    @Test
    fun `a link is refused`() {
        val reason = refused(bundle(Item("link", type = TarConstants.LF_SYMLINK, link = "/etc/passwd")))
        assertTrue(reason, reason.contains("link"))
    }

    @Test
    fun `an entry may not repeat a path`() {
        val reason = refused(bundle(Item("manifest.json", "one".toByteArray()), Item("manifest.json", "two".toByteArray())))
        assertTrue(reason, reason.contains("twice"))
    }

    @Test
    fun `size and count caps refuse the archive`() {
        assertTrue(refused(bundle(Item("big", ByteArray(2000))), BundleTar.Limits(maxEntryBytes = 1000)).contains("per-file cap"))
        val many = Array(5) { Item("f$it", byteArrayOf(1)) }
        assertTrue(refused(bundle(*many), BundleTar.Limits(maxEntries = 4)).contains("more than 4"))
    }

    @Test
    fun `readBytes refuses an entry bigger than its allowance`() {
        val archive = bundle(Item("manifest.json", ByteArray(100)))
        try {
            BundleTar.read(archive) { it.readBytes(50) }
            fail("should be refused")
        } catch (e: BundleTarException) {
            assertTrue(e.message, e.message!!.contains("larger"))
        }
    }

    @Test
    fun `a file that is not an xz tar is refused, not thrown raw`() {
        val junk = Files.createTempFile("junk", ".tar.xz").toFile().apply { writeBytes(ByteArray(64) { it.toByte() }); deleteOnExit() }
        assertTrue(refused(junk).contains("could not be read"))
    }

    @Test
    fun `an error in the callback passes through unchanged`() {
        val archive = bundle(Item("a", byteArrayOf(1)))
        try {
            BundleTar.read(archive) { throw IllegalStateException("mine") }
            fail("should throw")
        } catch (e: IllegalStateException) {
            assertEquals("mine", e.message)
        }
    }
}
