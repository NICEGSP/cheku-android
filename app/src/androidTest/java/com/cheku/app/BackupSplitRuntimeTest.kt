package com.cheku.app

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.cheku.app.data.AppDatabase
import com.cheku.app.data.EnergyType
import com.cheku.app.data.LedgerRepository
import com.cheku.app.data.MileageRecordEntity
import com.cheku.app.data.TransmissionType
import com.cheku.app.data.VehicleCategory
import com.cheku.app.data.VehicleEntity
import java.io.File
import java.io.OutputStream
import java.security.MessageDigest
import java.util.Random
import java.util.zip.GZIPOutputStream
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
/** 导出/导入口令 */
private val TEST_PASSPHRASE = "cheku-test-passphrase".toCharArray()

@RunWith(AndroidJUnit4::class)
class BackupSplitRuntimeTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val database: AppDatabase = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    private val repository = LedgerRepository(database.ledgerDao(), database.performanceDao()) { block ->
        database.withTransaction { block() }
    }
    private val certificates = CertificateRepository(context)
    private val workDir: File = File(context.cacheDir, "backup-runtime-test").apply {
        deleteRecursively()
        mkdirs()
    }

    /** 每个测试自己往这里登记要清理的证书 key */
    private val keysToClean = mutableSetOf("id_front", "license_front")

    @After
    fun tearDown() {
        database.close()
        workDir.deleteRecursively()
        keysToClean.forEach { certificates.remove(it) }
    }

    @Test
    fun businessPackageNeverCarriesCertificatesAndDeclaresTheArchiveItWasPairedWith() = runBlocking {
        seedVehicles("v1")
        certificates.restoreCertificates(listOf(fileCertificate("id_front", "身份证正面.jpg", 4096)))

        val archiveBytes = DataExporter.exportArchive(TEST_PASSPHRASE,
            certificates.certificateEntries(),
            appVersion = "v0.6.4",
            dbSchema = 15
        )
        val businessBytes = exportBusinessToBytes(archiveBytes)

        val parsed = DataExporter.importData(TEST_PASSPHRASE, businessBytes)!!
        assertEquals(CheBackupKind.Business, parsed.kind)
        assertTrue("主包不应携带任何证书", parsed.business.certificates.isEmpty())
        assertEquals("车辆数据应完整", 1, parsed.business.vehicles.size)

        val manifest = parsed.manifest!!
        assertTrue("主包应声明配套证书包", manifest.hasArchive)
        assertEquals("che", manifest.format)
        assertEquals(4, manifest.version)
        assertEquals(15, manifest.dbSchema)
        assertEquals("条目数应是证书实际数量", 1, manifest.archiveEntries)
        assertEquals(archiveBytes.size.toLong(), manifest.archiveBytes)
        assertTrue("真实证书包必须通过校验", DataExporter.matchesArchive(archiveBytes, manifest))

        // 证书包只含证书，且字节逐份保住
        val restored = DataExporter.importArchive(TEST_PASSPHRASE, archiveBytes.inputStream())!!
        assertEquals(1, restored.size)
        assertArrayEquals(fileCertificate("id_front", "身份证正面.jpg", 4096).data, restored.single().data)
        assertNull("证书包不能被当作业务包导入", DataExporter.importBusiness(TEST_PASSPHRASE, archiveBytes.inputStream()))
    }

    @Test
    fun twoStepImportRestoresBusinessAndCertificatesFromRealFiles() = runBlocking {
        seedVehicles("v1", "v2")
        certificates.restoreCertificates(listOf(fileCertificate("id_front", "身份证正面.jpg", 2048)))

        val archiveFile = workDir.resolve("CheKu_人车证书_20260920_1900.cert.che")
        val businessFile = workDir.resolve("CheKu_用车记录_20260920_1900.che")
        writeThroughResolver(archiveFile) {
            DataExporter.exportArchiveTo(TEST_PASSPHRASE, it, certificates.certificateEntries(), appVersion = "v0.6.4", dbSchema = 15)
        }
        writeThroughResolver(businessFile) {
            DataExporter.exportDataTo(TEST_PASSPHRASE,
                it,
                vehicles = repository.getAllVehicles(),
                mileageRecords = repository.getAllMileageRecords(),
                energyRecords = emptyList(),
                maintenanceItems = emptyList(),
                maintenanceHistory = emptyList(),
                appVersion = "v0.6.4",
                dbSchema = 15,
                archiveFile = archiveFile.name,
                archiveSha256 = DataExporter.sha256Hex(archiveFile.readBytes()),
                archiveBytes = archiveFile.length(),
                archiveEntries = 1
            )
        }
        assertTrue("两个文件都应真实落盘", archiveFile.length() > 0 && businessFile.length() > 0)

        wipeAll()
        assertEquals(0, repository.getAllVehicles().size)
        certificates.remove("id_front")

        val preview = readThroughResolver(businessFile).use { DataExporter.parseBackup(TEST_PASSPHRASE, it) }!!
        assertTrue("主包声明了配套证书包，应提示用户再选一次", preview.manifest!!.hasArchive)

        val archiveBytes = archiveFile.readBytes()
        assertTrue("配套证书包应通过校验", DataExporter.matchesArchive(archiveBytes, preview.manifest))
        val archive = DataExporter.importArchive(TEST_PASSPHRASE, archiveBytes.inputStream())!!

        repository.importData(preview.business)
        certificates.restoreCertificates(archive)

        assertEquals("业务数据应恢复", listOf("v1", "v2"), repository.getAllVehicles().map { it.id }.sorted())
        val restoredEntries = certificates.certificateEntries()
        assertEquals(1, restoredEntries.size)
        assertArrayEquals(
            fileCertificate("id_front", "身份证正面.jpg", 2048).data,
            restoredEntries.single().data
        )
    }

    @Test
    fun mismatchedArchiveIsRejectedAndNoDataIsWritten() = runBlocking {
        seedVehicles("v1")
        certificates.restoreCertificates(listOf(fileCertificate("id_front", "身份证正面.jpg", 1024)))

        val archiveBytes = DataExporter.exportArchive(TEST_PASSPHRASE, certificates.certificateEntries())
        val businessBytes = exportBusinessToBytes(archiveBytes)

        val wrongArchive = DataExporter.exportArchive(TEST_PASSPHRASE,
            listOf(fileCertificate("license_front", "驾驶证正页.jpg", 1024))
        )

        val preview = DataExporter.importData(TEST_PASSPHRASE, businessBytes)!!
        assertFalse(
            "内容不一致的证书包必须被拒绝（sha256/字节数判据）",
            DataExporter.matchesArchive(wrongArchive, preview.manifest)
        )
        // 只给文件名、没有指纹的半截声明也必须拒绝
        assertFalse(
            "缺少 archive* 声明时必须拒绝",
            DataExporter.matchesArchive(archiveBytes, preview.manifest!!.copy(archiveSha256 = null))
        )

        assertEquals("校验失败后不得写入业务数据", listOf("v1"), repository.getAllVehicles().map { it.id })
        assertEquals("校验失败后不得动证书", 1, certificates.certificateEntries().size)
    }

    @Test
    fun skippingTheArchiveKeepsExistingLocalCertificates() = runBlocking {
        certificates.restoreCertificates(listOf(fileCertificate("id_front", "身份证正面.jpg", 1024)))

        val businessBytes = exportBusinessToBytes(archiveBytes = null)
        val preview = DataExporter.importData(TEST_PASSPHRASE, businessBytes)!!
        assertFalse("未勾选证书包时不应声明配套包", preview.manifest!!.hasArchive)

        repository.importData(preview.business)
        certificates.restoreCertificates(emptyList())

        assertEquals("跳过后本机证书必须原样保留", 1, certificates.certificateEntries().size)
        assertArrayEquals(
            fileCertificate("id_front", "身份证正面.jpg", 1024).data,
            certificates.certificateEntries().single().data
        )
    }

    @Test
    fun legacyBundleWithoutManifestStillRestoresBusinessAndCertificates() = runBlocking {
        val legacyCsv = """
            === VEHICLES ===
            id,name,model,plate,category,energyType,transmissionType,fuelTankCapacity,batteryCapacity,defaultChargeLimitSoc,currentMileage,updatedAt,manufacturingDate,purchasePrice,purchaseTax
            v9,旧包车,Model,TEST,Car,Fuel,Automatic,50,,,1234.5,100,,180000.0,15929.0
            === DOCUMENT ARCHIVE ===
            key,fileName,mimeType,kind,base64
            id_front,身份证正面.jpg,image/jpeg,file,AQIDBAU=
        """.trimIndent()
        val blob = sealLegacyGcm(legacyCsv)

        val preview = DataExporter.parseBackup(TEST_PASSPHRASE, blob.inputStream())!!
        assertEquals(CheBackupKind.LegacyBundle, preview.kind)
        assertNull("旧整合包没有 MANIFEST", preview.manifest)
        assertEquals("旧包内嵌的证书应被读出来", 1, preview.archive.size)

        // 旧包仍允许走业务导入：业务数据 + 证书一起恢复
        repository.importData(preview.business)
        certificates.restoreCertificates(preview.archive)

        assertEquals("旧包车", repository.getAllVehicles().single().name)
        assertArrayEquals(byteArrayOf(1, 2, 3, 4, 5), certificates.certificateEntries().single().data)
    }

    @Test
    fun certificateSurvivesDeletionOfTheOriginalSourceFile() {
        val source = workDir.resolve("相册原图.jpg").apply { writeBytes(ByteArray(3000) { (it % 199).toByte() }) }

        certificates.save("id_front", Uri.fromFile(source))
        assertTrue("保存后源文件应仍在", source.exists())

        assertTrue(source.delete())
        assertFalse(source.exists())

        val entries = certificates.certificateEntries()
        assertEquals("删掉源文件后证书仍应可读", 1, entries.size)
        assertArrayEquals(ByteArray(3000) { (it % 199).toByte() }, entries.single().data)

        val stored = certificates.uriFor("id_front")!!.toString()
        assertTrue("prefs 应记内部 file://，实际：$stored", stored.startsWith("file://"))
        assertTrue("内部文件应真的落在 document_archive/ 下", stored.contains("document_archive"))

        // 删除证书时内部文件也要清掉（否则 filesDir 里会留孤儿）
        val internal = File(Uri.parse(stored).path!!)
        assertTrue(internal.exists())
        certificates.remove("id_front")
        assertFalse("remove 应删掉内部文件", internal.exists())
        assertNull(certificates.uriFor("id_front"))
    }

    @Test
    fun exportingEightyMegabytesOfCertificatesStaysUnderTheHeapLimit() = runBlocking {
        val perCertificate = 8 * 1024 * 1024
        val count = 10                                   // 80MB，覆盖现实量级 60–95MB
        val totalBytes = perCertificate.toLong() * count

        seedLargeCertificates(perCertificate, count)

        val preflight = certificates.certificateSources()
        assertTrue("预检不应有失败项：${preflight.failures}", preflight.isComplete)
        assertEquals("应列出全部证书源", count, preflight.sources.size)

        val outFile = workDir.resolve("large.cert.che")
        val digest = writeThroughResolver(outFile) { stream ->
            DataExporter.exportArchiveTo(TEST_PASSPHRASE,
                stream, preflight.sources.asSequence(), appVersion = "v0.6.4", dbSchema = 15
            )
        }
        android.util.Log.i(
            "ChekuProbe",
            "80MB 证书导出：文件 ${outFile.length() / 1024 / 1024}MB / ${digest.entries} 份"
        )

        assertTrue(
            "证书包体积应与证书同量级（实际 ${outFile.length() / 1024 / 1024}MB，证书 ${totalBytes / 1024 / 1024}MB）",
            outFile.length() > totalBytes / 2
        )
        assertEquals("流式统计的字节数应等于文件长度", outFile.length(), digest.bytes)
        assertEquals("条目数应等于证书数", count, digest.entries)
        assertEquals("流式摘要应等于文件真实 sha256", streamingSha256(outFile), digest.sha256)
    }

    @Test
    fun largeCertificatePackageExportsAndRoundTripsAt26Megabytes() = runBlocking {
        val perCertificate = 6_500_000                    // 6.5MB × 4 ≈ 26MB
        val count = 4
        val totalBytes = perCertificate.toLong() * count

        // 走真实路径：先落盘进 App 内部，再经 certificateEntries() 读成内存集合
        val entries = (0 until count).map { index ->
            val key = "cert_$index"
            keysToClean += key
            CertificateEntry(
                key = key, fileName = "证书$index.jpg", mimeType = "image/jpeg", kind = "file",
                data = ByteArray(perCertificate).also { Random(index.toLong()).nextBytes(it) }
            )
        }
        certificates.restoreCertificates(entries)
        var loaded = certificates.certificateEntries()
        assertEquals("大证书应全部装入", count, loaded.size)

        val heapBefore = usedHeapBytes()
        val outFile = workDir.resolve("large.cert.che")
        val digest = writeThroughResolver(outFile) { stream ->
            DataExporter.exportArchiveTo(TEST_PASSPHRASE, stream, loaded, appVersion = "v0.6.4", dbSchema = 15)
        }
        val growth = usedHeapBytes() - heapBefore
        android.util.Log.i(
            "ChekuProbe",
            "26MB 证书包导出：文件 ${outFile.length() / 1024 / 1024}MB，堆增长 ${growth / 1024 / 1024}MB"
        )

        assertEquals("证书总量", totalBytes, loaded.sumOf { it.data.size.toLong() })
        assertTrue(
            "证书包体积应与证书同量级（实际 ${outFile.length() / 1024 / 1024}MB）",
            outFile.length() > totalBytes / 2
        )
        assertEquals("流式统计的字节数应等于文件长度", outFile.length(), digest.bytes)
        assertEquals("条目数应等于证书数", count, digest.entries)

        assertEquals("流式摘要应等于文件真实 sha256", streamingSha256(outFile), digest.sha256)

        loaded = emptyList()
        System.gc()
        val restored = DataExporter.importArchive(TEST_PASSPHRASE, outFile.inputStream())!!
        assertEquals(count, restored.size)
        assertArrayEquals(
            "大证书字节应逐份保住",
            ByteArray(perCertificate).also { Random(3L).nextBytes(it) },
            restored.first { it.key == "cert_3" }.data
        )
    }

    @Test
    fun unreadableCertificateIsReportedInsteadOfSilentlyDropped() {
        val missing = File(context.filesDir, "document_archive/不存在_${System.nanoTime()}.jpg")
        assertFalse(missing.exists())
        context.getSharedPreferences(CertificateRepository.PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString("id_front", Uri.fromFile(missing).toString())
            .apply()
        keysToClean += "id_front"

        val result = certificates.readAllCertificates()
        assertTrue("读不到的证书不应出现在成功列表里", result.entries.isEmpty())
        assertEquals("必须报出失败项", 1, result.failures.size)
        assertEquals("id_front", result.failures.single().key)
        assertFalse("有失败就不算完整读取", result.isComplete)
        assertTrue("失败原因应非空：${result.failures.single().reason}", result.failures.single().reason.isNotBlank())
    }

    /** 正常路径的对照 */
    @Test
    fun readableCertificatesReportACompleteRead() {
        certificates.restoreCertificates(listOf(fileCertificate("id_front", "身份证正面.jpg", 512)))

        val result = certificates.readAllCertificates()
        assertEquals(1, result.entries.size)
        assertTrue("不应有失败项：${result.failures}", result.isComplete)
        assertEquals("id_front", result.entries.single().key)
    }

    @Test
    fun certificateSourcesPreflightExcludesUnreadableCertificates() {
        certificates.restoreCertificates(listOf(fileCertificate("id_front", "身份证正面.jpg", 256)))
        val missing = File(context.filesDir, "document_archive/不存在_${System.nanoTime()}.jpg")
        context.getSharedPreferences(CertificateRepository.PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString("license_front", Uri.fromFile(missing).toString())
            .apply()

        val set = certificates.certificateSources()
        assertEquals("只应给出能读的那一份", 1, set.sources.size)
        assertEquals("id_front", set.sources.single().key)
        assertEquals("读不到的必须在预检里报出来", 1, set.failures.size)
        assertEquals("license_front", set.failures.single().key)
        assertFalse(set.isComplete)
        // 惰性打开器真的能打开并读到字节
        assertEquals(256, set.sources.single().open().use { it.readBytes() }.size)
    }

    @Test
    fun platformGcmBufferingAmplifiesCipherMemoryWellBeyondThePlaintext() {
        val chunk = ByteArray(4 * 1024 * 1024).also { Random(11L).nextBytes(it) }
        val rounds = 6
        val plaintext = chunk.size.toLong() * rounds
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec("Ch3Ch3ngZh1!99!@".toByteArray(Charsets.UTF_8), "AES"),
            GCMParameterSpec(128, ByteArray(12))
        )

        val heapBefore = usedHeapBytes()
        var producedByUpdate = 0L
        repeat(rounds) { producedByUpdate += (cipher.update(chunk)?.size ?: 0).toLong() }
        val heapAfterUpdates = usedHeapBytes()
        val finalBytes = cipher.doFinal()
        val heapAfterFinal = usedHeapBytes()

        val updateGrowth = heapAfterUpdates - heapBefore
        val totalGrowth = heapAfterFinal - heapBefore
        val amplification = totalGrowth.toDouble() / plaintext
        val report = buildString {
            appendLine("AES/GCM 缓冲探针")
            appendLine("明文 = ${plaintext / 1024 / 1024}MB")
            appendLine("update() 产出 = $producedByUpdate 字节（0，平台不流式）")
            appendLine("doFinal() 产出 = ${finalBytes.size} 字节")
            appendLine("堆增长 = update ${updateGrowth / 1024 / 1024}MB + doFinal ${(totalGrowth - updateGrowth) / 1024 / 1024}MB = ${totalGrowth / 1024 / 1024}MB")
            appendLine("放大倍数 = ${"%.2f".format(amplification)}×")
            appendLine("堆上限 = ${Runtime.getRuntime().maxMemory() / 1024 / 1024}MB")
        }
        File(context.getExternalFilesDir(null), "aes-gcm-probe.txt").writeText(report)
        android.util.Log.i("ChekuProbe", report.replace("\n", " | "))

        assertEquals(
            "GCM 的 update() 本应产出 0 字节（输出被平台缓存在内部 ByteArrayOutputStream）；" +
                "若此断言失败说明平台改为流式了，可以重新评估「76MB 不 OOM」这条判据。实测 $producedByUpdate",
            0L,
            producedByUpdate
        )
        assertTrue("doFinal 应一次吐出全部密文", finalBytes.size > plaintext / 2)
        assertTrue(
            "平台 GCM 的缓冲放大倍数应 ≥2× 明文（实测 ${"%.2f".format(amplification)}×）；" +
                "若变小说明平台行为变了，应重新评估体积上限",
            amplification >= 2.0
        )
    }

    @Test
    fun stagedImportRoundTripsAt80MegabytesAndClearsStaging() = runBlocking {
        val perCertificate = 8 * 1024 * 1024
        val count = 10
        seedLargeCertificates(perCertificate, count)

        val packageFile = workDir.resolve("import-staged.cert.che")
        writeThroughResolver(packageFile) { stream ->
            DataExporter.exportArchiveTo(TEST_PASSPHRASE,
                stream,
                certificates.certificateSources().sources.asSequence(),
                appVersion = "v0.6.4",
                dbSchema = 15
            )
        }
        android.util.Log.i("ChekuProbe", "暂存导入：证书包 ${packageFile.length() / 1024 / 1024}MB")

        // 挪走本机证书，让"导进来"成为可观测的事实（remove 会连内部文件一起删）
        repeat(count) { certificates.remove("cert_$it") }
        assertEquals(0, certificates.certificateSources().sources.size)

        val staging = certificates.beginStaging()
        val stagingDir = File(context.filesDir, CertificateRepository.STAGING_DIRECTORY_NAME)
        val parsed = DataExporter.parseArchiveStaged(TEST_PASSPHRASE, packageFile.inputStream(), staging.sink())
        assertNotNull("80MB 证书包应能流式解析", parsed)
        assertEquals("应逐份收下全部证书", count, parsed!!.certificates.size)
        assertTrue("不应有失败项：${parsed.failures}", parsed.failures.isEmpty())
        assertTrue("解析过程中确实用了暂存目录", stagingDir.exists())

        val failures = staging.commit()
        assertTrue("就位不应有失败项：$failures", failures.isEmpty())

        assertEquals("全部证书应就位", count, certificates.certificateSources().sources.size)
        assertTrue("就位后暂存目录必须清干净", !stagingDir.exists())
        assertTrue(staging.isFinished)

        // 抽一份核对字节
        val restored = DataExporter.importArchive(TEST_PASSPHRASE, packageFile.inputStream())!!
        assertArrayEquals(
            "大证书字节应逐份保住",
            ByteArray(perCertificate).also { Random(3L).nextBytes(it) },
            restored.first { it.key == "cert_3" }.data
        )
    }

    @Test
    fun tamperedCertificatePackageLeavesNoStagedOrRestoredCertificates() = runBlocking {
        certificates.restoreCertificates(listOf(fileCertificate("id_front", "身份证正面.jpg", 512)))
        val packageFile = workDir.resolve("tampered.cert.che")
        writeThroughResolver(packageFile) { stream ->
            DataExporter.exportArchiveTo(TEST_PASSPHRASE,
                stream, listOf(sampleSource("id_front", "身份证正面.jpg", 512)).asSequence()
            )
        }
        val bytes = packageFile.readBytes()
        bytes[bytes.size - 1] = (bytes[bytes.size - 1].toInt() xor 0xFF).toByte()
        packageFile.writeBytes(bytes)

        val stagingDir = File(context.filesDir, CertificateRepository.STAGING_DIRECTORY_NAME)
        val staging = certificates.beginStaging()
        val parsed = try {
            DataExporter.parseArchiveStaged(TEST_PASSPHRASE, packageFile.inputStream(), staging.sink())
        } catch (error: Throwable) {
            staging.discard()
            throw error
        }
        assertNull("篡改的包不应解析成功", parsed)
        staging.discard()

        assertTrue("暂存目录必须清干净", !stagingDir.exists())
        // 本机原有那份证书不受影响
        assertEquals(1, certificates.certificateSources().sources.size)
    }

    private suspend fun seedVehicles(vararg ids: String) {
        repository.importData(
            ExportedData(
                vehicles = ids.map { id ->
                    VehicleEntity(
                        id = id, name = "测试车$id", model = "Model", plate = "TEST$id",
                        category = VehicleCategory.Car, energyType = EnergyType.Fuel,
                        transmissionType = TransmissionType.Automatic,
                        currentMileage = 100.0, updatedAt = 1L
                    )
                },
                mileageRecords = ids.map { MileageRecordEntity("m_$it", it, 100.0, "初始", 2L, 0.0) },
                energyRecords = emptyList(),
                maintenanceItems = emptyList(),
                maintenanceHistory = emptyList()
            )
        )
    }

    private suspend fun wipeAll() {
        repository.importData(
            ExportedData(
                vehicles = emptyList(), mileageRecords = emptyList(), energyRecords = emptyList(),
                maintenanceItems = emptyList(), maintenanceHistory = emptyList()
            )
        )
    }

    private suspend fun exportBusinessToBytes(archiveBytes: ByteArray?): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        DataExporter.exportDataTo(TEST_PASSPHRASE,
            out,
            vehicles = repository.getAllVehicles(),
            mileageRecords = repository.getAllMileageRecords(),
            energyRecords = repository.getAllEnergyRecords(),
            maintenanceItems = repository.getAllMaintenanceItems(),
            maintenanceHistory = repository.getAllMaintenanceHistory(),
            appVersion = "v0.6.4",
            dbSchema = 15,
            archiveFile = archiveBytes?.let { "CheKu_人车证书_20260920_1900.cert.che" },
            archiveSha256 = archiveBytes?.let { DataExporter.sha256Hex(it) },
            archiveBytes = archiveBytes?.size?.toLong(),
            archiveEntries = archiveBytes?.let { 1 }
        )
        return out.toByteArray()
    }

    private suspend fun <R> writeThroughResolver(target: File, body: suspend (OutputStream) -> R): R {
        val stream = context.contentResolver.openOutputStream(Uri.fromFile(target))
            ?: error("无法打开输出流")
        return stream.use { body(it) }
    }

    private fun readThroughResolver(source: File) = context.contentResolver.openInputStream(Uri.fromFile(source))
        ?: error("无法打开输入流")

    /** 只在这两条测试里用不到的样本证书源 */
    private fun sampleSource(key: String, fileName: String, size: Int) = DataExporter.CertificateSource(
        key = key, fileName = fileName, mimeType = "image/jpeg", kind = "file",
        open = { ByteArray(size) { (it % 199).toByte() }.inputStream() }
    )

    private suspend fun seedLargeCertificates(perCertificate: Int, count: Int) {
        repeat(count) { index ->
            keysToClean += "cert_$index"
            certificates.restoreCertificates(
                listOf(
                    CertificateEntry(
                        key = "cert_$index", fileName = "证书$index.jpg",
                        mimeType = "image/jpeg", kind = "file",
                        data = ByteArray(perCertificate).also { Random(index.toLong()).nextBytes(it) }
                    )
                )
            )
        }
    }

    private fun fileCertificate(key: String, fileName: String, size: Int) = CertificateEntry(
        key = key, fileName = fileName, mimeType = "image/jpeg", kind = "file",
        data = ByteArray(size) { (it % 199).toByte() }
    )

    /** 当前已用 Java 堆 */
    private fun usedHeapBytes(): Long = Runtime.getRuntime().let { it.totalMemory() - it.freeMemory() }

    private fun streamingSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xFF) }
    }

    /** 手工拼旧版容器，用来验证兼容读取 */
    private fun sealLegacyGcm(csv: String): ByteArray {
        val compressed = java.io.ByteArrayOutputStream().use { output ->
            GZIPOutputStream(output).use { it.write(csv.toByteArray(Charsets.UTF_8)) }
            output.toByteArray()
        }
        val iv = ByteArray(12) { (it + 1).toByte() }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec("Ch3Ch3ngZh1!99!@".toByteArray(Charsets.UTF_8), "AES"),
            GCMParameterSpec(128, iv)
        )
        return byteArrayOf('C'.code.toByte(), 'H'.code.toByte(), 'E'.code.toByte(), 3) +
            iv + cipher.doFinal(compressed)
    }
}
