package com.cheku.app

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 新版导出包的容器层测试 */
class BackupPackageTest {
    private val stampMillis = 1_774_000_000_000L

    @Test
    fun namesFollowTheSpec() {
        val outer = BackupPackage.outerStampFor(stampMillis)
        val inner = BackupPackage.innerStampFor(stampMillis)

        assertEquals(BackupPackage.outerStampFor(stampMillis), outer)
        assertTrue("外层名应含「用车数据」与 .zip：${BackupPackage.businessFileName(stampMillis)}",
            BackupPackage.businessFileName(stampMillis).startsWith("CheKu_用车数据_"))
        assertTrue(BackupPackage.businessFileName(stampMillis).endsWith(".zip"))
        assertTrue(BackupPackage.certsArchiveFileName(stampMillis).startsWith("CheKu_人车证书_"))
        assertTrue(BackupPackage.uploadFileName(stampMillis).endsWith(".zip"))
        assertTrue("上传包外层名必须纯 ASCII：${BackupPackage.uploadFileName(stampMillis)}",
            BackupPackage.uploadFileName(stampMillis).all { it.code < 128 })

        // 外层用下划线、包内用连字符（两者不能混，否则路径判断会出错）
        assertTrue(outer.contains('_'))
        assertTrue(inner.contains('-'))
        assertTrue(!inner.contains('_'))
    }

    @Test
    fun innerPathsAreAsciiAndDeterministic() {
        val inner = BackupPackage.innerStampFor(stampMillis)
        val p0 = BackupPackage.dataPartPath(inner, 0)
        val p1 = BackupPackage.dataPartPath(inner, 1)

        assertEquals("data/cheku-data-$inner.xls", p0)
        assertEquals("data/cheku-data-$inner-002.xls", p1)
        assertTrue("包内路径必须 ASCII", (p0 + p1).all { it.code < 128 })
        assertEquals("certs/cheku-certs-$inner.zip", BackupPackage.certsEntryPath(inner))
    }

    @Test
    fun planVolumesSplitsOversizedTablesAndPacksSequentially() {
        // 上限调小到可读：单表 10 行、单卷 25 行
        val plan = BackupPackage.planVolumes(
            tables = listOf(
                BackupPackage.TableRows("X", 10),   // 正好一片
                BackupPackage.TableRows("Y", 25),   // 切成 10 + 10 + 5
                BackupPackage.TableRows("Z", 0)     // 空表也要有表头
            ),
            maxRowsPerSheet = 10,
            maxRowsPerVolume = 25
        )

        assertEquals("应装成 2 卷", 2, plan.size)
        assertEquals(listOf("X", "Y"), plan[0].sheetNames)
        assertEquals(20, plan[0].rows)
        assertEquals(listOf("Y.p2", "Y.p3", "Z"), plan[1].sheetNames)
        assertEquals(15, plan[1].rows)
        plan.forEach { assertTrue("单卷 ${it.rows} 行超上限", it.rows <= 25) }
    }

    @Test
    fun planVolumesNeverSpansASliceAcrossVolumes() {
        val plan = BackupPackage.planVolumes(
            tables = listOf(BackupPackage.TableRows("T", 30)),
            maxRowsPerSheet = 10,
            maxRowsPerVolume = 15
        )
        assertEquals(listOf("T", "T.p2", "T.p3"), plan.flatMap { it.sheetNames })
        assertEquals("每卷只放得下一片", 3, plan.size)
    }

    @Test
    fun planVolumesKeepsEmptyBackupAsOneVolume() {
        val plan = BackupPackage.planVolumes(listOf(BackupPackage.TableRows("车辆", 0)))
        assertEquals("空备份也要出一卷（空备份是合法备份）", 1, plan.size)
        assertEquals(0, plan[0].rows)
    }

    @Test
    fun businessManifestRoundTrips() {
        val manifest = BackupPackage.BusinessManifest(
            format = BackupPackage.FORMAT,
            formatVersion = BackupPackage.FORMAT_VERSION,
            scope = BackupPackage.SCOPE_BUSINESS,
            createdAt = "2026-09-21T07:30:00+08:00",
            appVersion = "v0.6.4",
            dbSchema = 15,
            data = listOf(
                BackupPackage.DataPart("data/a.xls", 12L, "aa", 3, 2),
                BackupPackage.DataPart("data/b.xls", 34L, "bb", 5, 1)
            ),
            counts = mapOf("vehicles" to 2, "fitSamples" to 5120)
        )
        val json = BackupPackage.businessManifestJson(manifest)
        val back = BackupPackage.businessManifestFromJson(json)

        assertEquals(manifest, back)
        assertNull("坏 JSON 必须返回 null 而不是抛异常", BackupPackage.businessManifestFromJson("{oops"))
    }

    @Test
    fun certsManifestRoundTrips() {
        val manifest = BackupPackage.CertsManifest(
            format = BackupPackage.CERTS_FORMAT,
            formatVersion = BackupPackage.FORMAT_VERSION,
            scope = BackupPackage.SCOPE_CERTIFICATES,
            createdAt = "2026-09-21T07:30:00+08:00",
            appVersion = "v0.6.4",
            entries = listOf(
                BackupPackage.CertEntry("files/k__行驶证.jpg", "k", "行驶证.jpg", 9L, "cc")
            )
        )
        assertEquals(manifest, BackupPackage.certsManifestFromJson(BackupPackage.certsManifestJson(manifest)))
    }

    private fun volume(sheets: List<String>, rows: Int, fill: Byte): BackupPackage.VolumeBytes =
        BackupPackage.VolumeBytes(sheets, rows, ByteArray(64) { fill })

    private fun exportPackage(certsZip: ByteArray?): ByteArray =
        ByteArrayOutputStream().use { out ->
            BackupPackage.writeExportPackage(
                out = out,
                atMillis = stampMillis,
                appVersion = "v0.6.4",
                dbSchema = 15,
                counts = mapOf("vehicles" to 1),
                volumes = listOf(
                    volume(listOf("车辆", "里程记录"), 4, 7),
                    volume(listOf("fit_samples"), 3, 9)
                ),
                certsZip = certsZip
            )
            out.toByteArray()
        }

    private fun entryBytes(zip: ByteArray, name: String): ByteArray? {
        ZipInputStream(ByteArrayInputStream(zip)).use { input ->
            while (true) {
                val entry = input.nextEntry ?: return null
                if (entry.name == name) return input.readBytes()
            }
        }
    }

    @Test
    fun exportPackageContainsManifestDataVolumesAndOptionalCerts() {
        val withCerts = exportPackage("cert-zip".toByteArray())
        val names = mutableListOf<String>()
        ZipInputStream(ByteArrayInputStream(withCerts)).use { input ->
            while (true) {
                val entry = input.nextEntry ?: break
                names += entry.name
            }
        }
        val inner = BackupPackage.innerStampFor(stampMillis)
        assertEquals(
            listOf(
                "manifest.json",
                "data/cheku-data-$inner.xls",
                "data/cheku-data-$inner-002.xls",
                BackupPackage.certsEntryPath(inner)
            ),
            names
        )
        assertNull("不含证书时不应出现 certs/", entryBytes(exportPackage(null), BackupPackage.certsEntryPath(inner)))
    }

    @Test
    fun streamingExportPackageNestsCertsAsAnInnerZipNotTopLevelEntries() {
        val scratch = File(System.getProperty("java.io.tmpdir"), "cheku-bkp-${System.nanoTime()}").apply { mkdirs() }
        try {
            val plan = BackupPackage.planVolumes(listOf(BackupPackage.TableRows("车辆", 4)))
            val withCerts = ByteArrayOutputStream().use { out ->
                BackupPackage.writeExportPackageStreaming(
                    out = out,
                    atMillis = stampMillis,
                    appVersion = "v0.6.4",
                    dbSchema = 15,
                    counts = mapOf("vehicles" to 1),
                    scratchDir = scratch,
                    plan = plan,
                    renderVolume = { _, sink -> sink.write(ByteArray(32) { 1 }) },
                    writeCerts = { zip ->
                        val entries = ArrayList<BackupPackage.CertEntry>()
                        BackupPackage.writeCertsEntries(
                            zip, stampMillis, "v0.6.4",
                            listOf(
                                BackupPackage.CertFile("vehicle_a", "行驶证.jpg") { ByteArrayInputStream("cert-a".toByteArray()) },
                                BackupPackage.CertFile("idcard", "正面.png") { ByteArrayInputStream("cert-b".toByteArray()) }
                            ),
                            entries
                        )
                    }
                )
                out.toByteArray()
            }

            val inner = BackupPackage.innerStampFor(stampMillis)
            val certsPath = BackupPackage.certsEntryPath(inner)

            val outerNames = mutableListOf<String>()
            var certsBytes: ByteArray? = null
            ZipInputStream(ByteArrayInputStream(withCerts)).use { input ->
                while (true) {
                    val e = input.nextEntry ?: break
                    outerNames += e.name
                    if (e.name == certsPath) certsBytes = input.readBytes()
                }
            }
            assertTrue("应有 certs/ 条目：$outerNames", certsPath in outerNames)
            assertTrue("证书绝不能散成顶层 files/ 条目：$outerNames", outerNames.none { it.startsWith("files/") })

            // 2) certs/ 条目本身是一个完整 zip，内含 files/... + manifest.json
            assertNotNull("certs/ 条目应有字节", certsBytes)
            val innerNames = mutableListOf<String>()
            ZipInputStream(ByteArrayInputStream(certsBytes!!)).use { input ->
                while (true) {
                    val e = input.nextEntry ?: break
                    innerNames += e.name
                }
            }
            assertTrue("内层 zip 应有 manifest.json：$innerNames", "manifest.json" in innerNames)
            assertTrue("内层 zip 应有 files/ 证书条目：$innerNames", innerNames.any { it.startsWith("files/") })
        } finally {
            scratch.deleteRecursively()
        }
    }

    @Test
    fun serverValidationRejectsAFullExportPackageBecauseItCarriesCertificates() {
        val full = exportPackage("cert-zip".toByteArray())
        val result = BackupPackage.validateUploadPackage(ByteArrayInputStream(full))
        assertTrue("应为 Failed，实际 $result", result is BackupPackage.Validation.Failed)
        assertEquals(
            BackupPackage.PackageProblem.CERT_SECTION_PRESENT,
            (result as BackupPackage.Validation.Failed).problem
        )
    }

    @Test
    fun strippedPackagePassesValidationAndKeepsManifestByteIdentical() {
        val full = exportPackage("cert-zip".toByteArray())
        val stripped = ByteArrayOutputStream().use { out ->
            BackupPackage.stripCertificates(ByteArrayInputStream(full), out).let { out.toByteArray() }
        }

        val manifestBefore = entryBytes(full, "manifest.json")!!
        val manifestAfter = entryBytes(stripped, "manifest.json")!!
        assertTrue("manifest 必须逐字节相同", manifestBefore.contentEquals(manifestAfter))

        val report = BackupPackage.stripCertificates(
            ByteArrayInputStream(full),
            ByteArrayOutputStream()
        )
        assertEquals(1, report.strippedEntries.size)
        assertTrue(report.strippedEntries.single().startsWith("certs/"))
        assertEquals(3, report.keptEntries.size)

        val result = BackupPackage.validateUploadPackage(ByteArrayInputStream(stripped))
        assertTrue("剥离后的包应通过校验，实际 $result", result is BackupPackage.Validation.Ok)
        val manifest = (result as BackupPackage.Validation.Ok).manifest
        assertEquals(2, manifest.data.size)
        assertEquals(BackupPackage.SCOPE_BUSINESS, manifest.scope)
    }

    @Test
    fun validationRejectsEmptyBackup() {
        val empty = ByteArrayOutputStream().use { out ->
            BackupPackage.writeExportPackage(
                out = out,
                atMillis = stampMillis,
                appVersion = "v0.6.4",
                dbSchema = 15,
                counts = emptyMap(),
                volumes = listOf(volume(listOf("车辆", "里程记录"), 0, 0))
            )
            out.toByteArray()
        }
        val stripped = ByteArrayOutputStream().use { out ->
            BackupPackage.stripCertificates(ByteArrayInputStream(empty), out)
            out.toByteArray()
        }
        val result = BackupPackage.validateUploadPackage(ByteArrayInputStream(stripped))
        assertTrue("空备份必须被拒，实际 $result", result is BackupPackage.Validation.Failed)
        assertEquals(
            BackupPackage.PackageProblem.EMPTY_BACKUP,
            (result as BackupPackage.Validation.Failed).problem
        )
    }

    @Test
    fun validationRejectsTamperedVolume() {
        val stripped = ByteArrayOutputStream().use { out ->
            BackupPackage.stripCertificates(ByteArrayInputStream(exportPackage(null)), out)
            out.toByteArray()
        }
        val target = "data/cheku-data-${BackupPackage.innerStampFor(stampMillis)}.xls"
        val offset = indexOfEntryData(stripped, target)
        assertTrue("应能定位到数据卷内容", offset > 0)
        stripped[offset + 5] = (stripped[offset + 5] + 1).toByte()

        val result = BackupPackage.validateUploadPackage(ByteArrayInputStream(stripped))
        assertTrue("篡改必须被拒，实际 $result", result is BackupPackage.Validation.Failed)
        val problem = (result as BackupPackage.Validation.Failed).problem
        assertTrue(
            "问题码应为 NOT_A_ZIP 或 CHECKSUM_MISMATCH，实际 $problem",
            problem == BackupPackage.PackageProblem.NOT_A_ZIP ||
                problem == BackupPackage.PackageProblem.CHECKSUM_MISMATCH
        )
    }

    private fun indexOfEntryData(zip: ByteArray, name: String): Int {
        val needle = name.toByteArray(Charsets.UTF_8)
        outer@ for (i in 0..zip.size - needle.size - 30) {
            if (zip[i] != 0x50.toByte() || zip[i + 1] != 0x4B.toByte() ||
                zip[i + 2] != 3.toByte() || zip[i + 3] != 4.toByte()
            ) {
                continue
            }
            for (j in needle.indices) if (zip[i + 30 + j] != needle[j]) continue@outer
            val compressed = (zip[i + 18].toInt() and 0xFF) or ((zip[i + 19].toInt() and 0xFF) shl 8)
            return i + 30 + needle.size + compressed
        }
        return -1
    }

    @Test
    fun validationRejectsNonZipAndUnsafePaths() {
        val plain = ByteArray(64) { 'x'.code.toByte() }
        assertEquals(
            BackupPackage.PackageProblem.NOT_A_ZIP,
            (BackupPackage.validateUploadPackage(ByteArrayInputStream(plain)) as BackupPackage.Validation.Failed).problem
        )

        assertTrue(BackupPackage.isUnsafePath("../escape"))
        assertTrue(BackupPackage.isUnsafePath("/absolute"))
        assertTrue(BackupPackage.isUnsafePath("a\\b"))
        assertTrue(!BackupPackage.isUnsafePath("data/cheku-data-20260921-073000.xls"))
    }

    @Test
    fun validationRejectsUnexpectedEntry() {
        val zip = ByteArrayOutputStream().use { out ->
            java.util.zip.ZipOutputStream(out).use { z ->
                z.putNextEntry(ZipEntry("说明.txt"))
                z.write("hi".toByteArray())
                z.closeEntry()
            }
            out.toByteArray()
        }
        val result = BackupPackage.validateUploadPackage(ByteArrayInputStream(zip))
        assertEquals(
            BackupPackage.PackageProblem.UNEXPECTED_ENTRY,
            (result as BackupPackage.Validation.Failed).problem
        )
    }

    @Test
    fun certsArchiveWritesFilesThenManifestAndHashesContent() {
        val archive = ByteArrayOutputStream().use { out ->
            BackupPackage.writeCertsArchive(
                out = out,
                atMillis = stampMillis,
                appVersion = "v0.6.4",
                files = listOf(
                    BackupPackage.CertFile("vehicle_a_driving_front", "行驶证.jpg") {
                        ByteArrayInputStream("cert-a".toByteArray())
                    },
                    BackupPackage.CertFile("idcard_front", "身份证/正面.png") {
                        ByteArrayInputStream("cert-b".toByteArray())
                    }
                )
            )
            out.toByteArray()
        }

        val manifest = BackupPackage.certsManifestFromJson(
            String(entryBytes(archive, "manifest.json")!!, Charsets.UTF_8)
        )
        assertNotNull(manifest)
        assertEquals(BackupPackage.CERTS_FORMAT, manifest!!.format)
        assertEquals(2, manifest.entries.size)
        assertEquals("cert-a".toByteArray().size.toLong(), manifest.entries[0].bytes)
        assertEquals(
            BackupPackage.sha256Hex("cert-a".toByteArray()),
            manifest.entries[0].sha256
        )
        assertTrue(
            "条目名不应含路径分隔：${manifest.entries[1].path}",
            !manifest.entries[1].path.removePrefix("files/").contains('/')
        )
    }

    @Test
    fun certificateEntryFileNameSanitizesAndTruncatesKeepingExtension() {
        val name = BackupPackage.certificateEntryFileName("vehicle_a_driving_front", "行 驶证.jpg")
        assertTrue("key 与名之间应是双下划线：$name", name.contains("__"))
        assertTrue(name.endsWith(".jpg"))

        // 超长中文名按 UTF-8 字节截断到 240 且保留扩展名（不截断会 ENAMETOOLONG）
        val long = BackupPackage.certificateEntryFileName("k", "长".repeat(300) + ".pdf")
        assertTrue("字节数应 ≤240，实际 ${long.toByteArray(Charsets.UTF_8).size}",
            long.toByteArray(Charsets.UTF_8).size <= 240)
        assertTrue(long.endsWith(".pdf"))

        // key 里的非法字符要被替换（它会被用作文件名）
        assertTrue(!BackupPackage.certificateEntryFileName("a/b:c", "x.jpg").contains('/'))
    }

    @Test
    fun sha256MatchesKnownVector() {
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            BackupPackage.sha256Hex(ByteArray(0))
        )
    }
}
