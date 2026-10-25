package com.cheku.app

import java.io.File
import java.io.IOException
import java.io.InputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CertificateRepositoryTest {
    @Test
    fun sanitizeKeepsSafeCharactersAndReplacesTheRest() {
        assertEquals("身份证正面.jpg", sanitizeCertificateFileName("身份证正面.jpg"))
        assertEquals("a_b_c.pdf", sanitizeCertificateFileName("a/b\\c.pdf"))
        assertEquals("保单__2026_.pdf", sanitizeCertificateFileName("保单*?2026|.pdf"))
        assertEquals("___.pdf", sanitizeCertificateFileName("   .pdf"))
    }

    @Test
    fun sanitizeNeverReturnsBlankOrHiddenName() {
        assertEquals("certificate", sanitizeCertificateFileName(""))
        assertEquals("certificate", sanitizeCertificateFileName("..."))
        assertEquals("jpg", sanitizeCertificateFileName(".jpg"))
        assertEquals("green_book", sanitizeCertificateFileName("..green_book.."))
        // 全非法字符会退化成下划线串，但仍是个合法文件名（不是空名、也不是隐藏文件）
        assertEquals("___", sanitizeCertificateFileName("***"))
    }

    @Test
    fun truncateToUtf8BytesCountsBytesNotCharacters() {
        // 每个汉字 3 字节；按"字符数"截断会低估，这里必须按字节切
        val chinese = "机动车行驶证"
        assertEquals(chinese, truncateToUtf8Bytes(chinese, 100))
        val sixBytes = truncateToUtf8Bytes(chinese, 6)
        assertEquals(6, sixBytes.toByteArray(Charsets.UTF_8).size)
        assertEquals("机动", sixBytes)
        assertEquals("", truncateToUtf8Bytes(chinese, 2))
    }

    @Test
    fun internalNameKeepsKeyPrefixAndOriginalName() {
        val name = internalCertificateFileName("id_front", "身份证正面.jpg")
        assertEquals("id_front_身份证正面.jpg", name)
    }

    /** 历史缺陷 */
    @Test
    fun internalNameStaysUnderFileSystemLimitForVeryLongChineseName() {
        val longName = "机动车行驶证正反面高清扫描件".repeat(20) + ".pdf"
        val name = internalCertificateFileName("vehicle_0123456789abcdef0123456789abcdef_driving_front", longName)
        val bytes = name.toByteArray(Charsets.UTF_8).size
        assertTrue("内部文件名 $bytes 字节，应不超过 240", bytes <= 240)
        assertTrue("必须保留 key 前缀", name.startsWith("vehicle_0123456789abcdef0123456789abcdef_driving_front_"))
        assertTrue("必须保留后缀里的扩展名", name.endsWith(".pdf"))
    }

    @Test
    fun internalNameNeverBlowsUpOnAbsurdKey() {
        // key 由本项目的常量与 `vehicle_<id>_<suffix>` 生成，正常不会很长；
        // 这里只保证极端输入下不抛异常、仍带扩展名（不保证总长，因为 key 本身已越界）。
        val name = internalCertificateFileName("k".repeat(300), "a.pdf")
        assertTrue(name.startsWith("k".repeat(300) + "_"))
        assertTrue(name.endsWith(".pdf"))
    }

    @Test
    fun writeToInternalFileCopiesBytesAndReplacesExistingTarget() {
        val directory = createTempDir()
        try {
            val target = File(directory, "id_front_身份证正面.jpg")
            target.writeBytes(ByteArray(16) { 9 })      // 已有旧文件，应被替换
            val payload = ByteArray(200_000) { (it % 251).toByte() }

            val written = writeToInternalFile(
                payload.inputStream(), target, freeSpace = 512L * 1024 * 1024
            )

            assertEquals(payload.size.toLong(), written)
            assertTrue("内容应与源一致", payload.contentEquals(target.readBytes()))
            assertFalse("不应留下 .part 临时文件", File(directory, "${target.name}.part").exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun writeToInternalFileRejectsWhenFreeSpaceIsBelowReserve() {
        val directory = createTempDir()
        try {
            val target = File(directory, "x.jpg")
            val error = runCatching {
                writeToInternalFile(ByteArray(16).inputStream(), target, freeSpace = 1024L)
            }.exceptionOrNull()
            assertTrue("应抛 IOException，实际 ${error?.javaClass?.simpleName}", error is IOException)
            assertTrue("文案应说明空间不足", error!!.message!!.contains("存储空间不足"))
            assertFalse(target.exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun writeToInternalFileAbortsWhenContentExceedsFreeSpace() {
        val directory = createTempDir()
        try {
            val target = File(directory, "big.jpg")
            // 可用空间只比保留值大一点，传入 200 KB 内容必然中途撞线
            val freeSpace = MIN_FREE_RESERVE_BYTES + 4096
            val error = runCatching {
                writeToInternalFile(ByteArray(200_000).inputStream(), target, freeSpace)
            }.exceptionOrNull()
            assertTrue("应抛 IOException，实际 ${error?.javaClass?.simpleName}", error is IOException)
            assertFalse("不应留下目标文件", target.exists())
            assertFalse("不应留下 .part 临时文件", File(directory, "${target.name}.part").exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun writeToInternalFileRejectsFilesOverTheSingleCertificateLimit() {
        val directory = createTempDir()
        try {
            val target = File(directory, "huge.pdf")
            val error = runCatching {
                writeToInternalFile(
                    ByteArray(200_000).inputStream(), target,
                    freeSpace = 512L * 1024 * 1024,
                    maxBytes = 100_000
                )
            }.exceptionOrNull()
            assertTrue("应抛 IOException，实际 ${error?.javaClass?.simpleName}", error is IOException)
            assertTrue("错误信息应说明是体积问题：${error?.message}", error?.message?.contains("超过") == true)
            assertFalse("不应留下目标文件", target.exists())
            assertFalse("不应留下 .part 临时文件", File(directory, "${target.name}.part").exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun writeToInternalFileAcceptsExactlyTheLimit() {
        val directory = createTempDir()
        try {
            val target = File(directory, "exact.jpg")
            val written = writeToInternalFile(
                ByteArray(100_000).inputStream(), target,
                freeSpace = 512L * 1024 * 1024,
                maxBytes = 100_000
            )
            assertEquals(100_000L, written)
            assertEquals(100_000L, target.length())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun writeToInternalFileCleansUpTemporaryFileWhenSourceFails() {
        val directory = createTempDir()
        try {
            val target = File(directory, "broken.jpg")
            val error = runCatching {
                writeToInternalFile(FailingInputStream(failAfter = 100_000), target, 512L * 1024 * 1024)
            }.exceptionOrNull()
            assertTrue("应把源异常上抛，实际 ${error?.javaClass?.simpleName}", error is IOException)
            assertFalse("失败后不应留下目标文件", target.exists())
            assertFalse("失败后不应留下 .part 临时文件", File(directory, "${target.name}.part").exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun formatBytesUsesReadableUnits() {
        assertEquals("512 B", formatBytes(512))
        assertEquals("1 KB", formatBytes(1024))
        assertEquals("1.5 MB", formatBytes(1_572_864))
        assertEquals("2.0 GB", formatBytes(2L * 1024 * 1024 * 1024))
    }

    private fun createTempDir(): File =
        File(System.getProperty("java.io.tmpdir"), "cheku-cert-test-${System.nanoTime()}").apply { mkdirs() }

    private class FailingInputStream(private val failAfter: Int) : InputStream() {
        private var consumed = 0

        override fun read(): Int {
            if (consumed >= failAfter) throw IOException("source failed")
            consumed++
            return 0
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (consumed >= failAfter) throw IOException("source failed")
            val count = minOf(len, failAfter - consumed)
            for (i in 0 until count) b[off + i] = 0
            consumed += count
            return count
        }
    }
}
