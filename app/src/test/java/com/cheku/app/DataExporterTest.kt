package com.cheku.app

import com.cheku.app.data.EnergyRecordEntity
import com.cheku.app.data.EnergyRecordType
import com.cheku.app.data.EnergyType
import com.cheku.app.data.MileageRecordEntity
import com.cheku.app.data.TransmissionType
import com.cheku.app.data.VehicleCategory
import com.cheku.app.data.VehicleEntity
import com.cheku.app.data.PerformanceJerkEntity
import com.cheku.app.data.PerformanceNoiseEntity
import com.cheku.app.data.PerformanceQuality
import com.cheku.app.data.PerformanceSampleEntity
import com.cheku.app.data.PerformanceSessionEntity
import com.cheku.app.data.PerformanceSplitEntity
import com.cheku.app.data.PerformanceStatus
import com.cheku.app.data.PerformanceTestType
import com.cheku.app.data.JerkSeverity
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.zip.GZIPOutputStream
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
/** 导出/导入口令 */
private val TEST_PASSPHRASE = "cheku-test-passphrase".toCharArray()

class DataExporterTest {
    @Test
    fun importsLegacyEcbBackupFromEarlyReleases() {
        val csv = """
            === VEHICLES ===
            id,name,model,plate,category,energyType,transmissionType,fuelTankCapacity,batteryCapacity,defaultChargeLimitSoc,currentMileage,updatedAt
            v1,Legacy Car,Model,TEST,Car,Fuel,Automatic,50,,,1234.5,100
            === MILEAGE ===
            id,vehicleId,mileage,note,createdAt
            m1,v1,1234.5,legacy note,101
        """.trimIndent()
        val compressed = ByteArrayOutputStream().use { output ->
            GZIPOutputStream(output).use { it.write(csv.toByteArray(Charsets.UTF_8)) }
            output.toByteArray()
        }
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec("Ch3Ch3ngZh1!99!@".toByteArray(Charsets.UTF_8), "AES"))

        val imported = DataExporter.importData(TEST_PASSPHRASE, cipher.doFinal(compressed))!!.business

        assertEquals("Legacy Car", imported.vehicles.single().name)
        assertEquals(1234.5, imported.vehicles.single().currentMileage, 0.0)
        assertEquals("legacy note", imported.mileageRecords.single().note)
        assertEquals(0.0, imported.mileageRecords.single().roadFeeAmount, 0.0)
    }

    @Test
    fun roundTripPreservesPerformanceMeasurements() {
        val session = PerformanceSessionEntity(
            id = "p1",
            vehicleId = "v1",
            name = "封闭场地测试",
            testType = PerformanceTestType.Acceleration100,
            status = PerformanceStatus.Completed,
            startedAt = 1L,
            endedAt = 2L,
            resultSeconds = 4.2,
            resultDistanceMeters = 90.0,
            quality = PerformanceQuality.Valid,
            updatedAt = 2L
        )
        val sample = PerformanceSampleEntity(
            sessionId = "p1",
            timestampNanos = 10L,
            wallTime = 1L,
            gpsSpeedMps = 20.0,
            fusedSpeedMps = 20.1,
            distanceMeters = 30.0,
            latitude = 39.0,
            longitude = 116.0,
            gpsAccuracyMeters = 2f,
            altitudeMeters = 40.0,
            longitudinalAccelerationMps2 = 3f,
            lateralAccelerationMps2 = 0.1f,
            verticalAccelerationMps2 = 0.2f,
            jerkMps3 = 1f,
            noiseDbA = 42f
        )
        val bytes = DataExporter.exportData(TEST_PASSPHRASE,
            vehicles = listOf(vehicle("v1")),
            mileageRecords = emptyList(),
            energyRecords = emptyList(),
            maintenanceItems = emptyList(),
            maintenanceHistory = emptyList(),
            performanceSessions = listOf(session),
            performanceSamples = listOf(sample),
            performanceSplits = listOf(PerformanceSplitEntity("s1", "p1", "0-100", 100.0, 4.2, 90.0)),
            performanceNoise = listOf(PerformanceNoiseEntity(sessionId = "p1", timestamp = 3L, dbA = 42f)),
            performanceJerks = listOf(PerformanceJerkEntity("j1", "p1", 3L, 50.0, 2f, 4f, 200L, JerkSeverity.Noticeable))
        )
        val imported = DataExporter.importData(TEST_PASSPHRASE, bytes)!!.business

        assertEquals(session, imported.performanceSessions.single())
        assertEquals(sample, imported.performanceSamples.single())
        assertEquals(1, imported.performanceSplits.size)
        assertEquals(1, imported.performanceNoise.size)
        assertEquals(1, imported.performanceJerks.size)
    }
    @Test
    fun roundTripPreservesMultilineNotes() {
        val vehicle = VehicleEntity(
            id = "v1",
            name = "测试车",
            model = "Model",
            plate = "TEST",
            category = VehicleCategory.Car,
            energyType = EnergyType.Fuel,
            transmissionType = TransmissionType.Automatic,
            fuelTankCapacityLiters = 50.0,
            currentMileage = 123.0,
            updatedAt = 1L
        )
        val record = MileageRecordEntity(
            id = "m1",
            vehicleId = "v1",
            mileage = 123.0,
            note = "第一行\n第二行,带逗号和\"引号\"",
            createdAt = 1L,
            roadFeeAmount = 58.6
        )

        val bytes = DataExporter.exportData(TEST_PASSPHRASE, listOf(vehicle), listOf(record), emptyList(), emptyList(), emptyList())
        val imported = DataExporter.importData(TEST_PASSPHRASE, bytes)!!.business

        assertEquals(vehicle, imported.vehicles.single())
        assertEquals(record, imported.mileageRecords.single())
    }

    @Test
    fun roundTripPreservesChargeLimitAndPaymentFields() {
        val vehicle = VehicleEntity(
            id = "v1",
            name = "电车",
            model = "EV",
            plate = "TEST",
            category = VehicleCategory.Car,
            energyType = EnergyType.Electric,
            transmissionType = TransmissionType.ElectricDrive,
            batteryCapacityKwh = 60.0,
            defaultChargeLimitSoc = 90,
            currentMileage = 1_000.0,
            updatedAt = 1L
        )
        val record = EnergyRecordEntity(
            id = "e1",
            vehicleId = vehicle.id,
            type = EnergyRecordType.Charge,
            mileage = 1_000.0,
            amount = 20.0,
            orderAmount = 30.0,
            paidAmount = 18.0,
            isFull = true,
            missedPrevious = false,
            chargeMethod = "商充",
            electricityPrice = 1.0,
            serviceFee = 0.5,
            chargeLimitSocSnapshot = 90,
            note = "优惠券\n满30减12",
            createdAt = 2L
        )

        val bytes = DataExporter.exportData(TEST_PASSPHRASE, listOf(vehicle), emptyList(), listOf(record), emptyList(), emptyList())
        val imported = DataExporter.importData(TEST_PASSPHRASE, bytes)!!.business

        assertEquals(vehicle, imported.vehicles.single())
        assertEquals(record, imported.energyRecords.single())
    }

    /** 证书包往返 */
    @Test
    fun archivePackageRoundTripPreservesCertificatesByteForByte() {
        val cert = CertificateEntry(
            key = "id_front",
            fileName = "身份证正面.jpg",
            mimeType = "image/jpeg",
            kind = "file",
            data = ByteArray(4096) { (it % 251).toByte() }
        )
        val text = CertificateEntry(
            key = "vehicle_v1_vin",
            fileName = "vehicle_v1_vin.txt",
            mimeType = "text/plain",
            kind = "text",
            data = "LSVAA1234567890AB".toByteArray(Charsets.UTF_8)
        )

        val bytes = DataExporter.exportArchive(TEST_PASSPHRASE, listOf(cert, text), appVersion = "v0.6.4", dbSchema = 15)
        val imported = DataExporter.importArchive(TEST_PASSPHRASE, ByteArrayInputStream(bytes))!!

        assertEquals(2, imported.size)
        val byKey = imported.associateBy { it.key }
        val actual = byKey["id_front"]!!
        assertEquals(cert.key, actual.key)
        assertEquals(cert.fileName, actual.fileName)
        assertEquals(cert.mimeType, actual.mimeType)
        assertEquals(cert.kind, actual.kind)
        org.junit.Assert.assertArrayEquals(cert.data, actual.data)
        val actualText = byKey["vehicle_v1_vin"]!!
        assertEquals("text", actualText.kind)
        assertEquals("LSVAA1234567890AB", actualText.data.toString(Charsets.UTF_8))

        assertNull(DataExporter.importBusiness(TEST_PASSPHRASE, ByteArrayInputStream(bytes)))
    }

    @Test
    fun importsGcmBackupCarryingCertificateSection() {
        val csv = """
            === VEHICLES ===
            id,name,model,plate,category,energyType,transmissionType,fuelTankCapacity,batteryCapacity,defaultChargeLimitSoc,currentMileage,updatedAt,manufacturingDate,purchasePrice,purchaseTax
            v1,证书测试车,Model,TEST,Car,Fuel,Automatic,50,,,1234.5,100,,180000.0,15929.0
            === DOCUMENT ARCHIVE ===
            key,fileName,mimeType,kind,base64
            id_front,身份证正面.jpg,image/jpeg,file,AQIDBAU=
            vehicle_v1_vin,vehicle_v1_vin.txt,text/plain,text,TFNWQUExMjM0NTY3ODkwQUI=
        """.trimIndent()
        val compressed = ByteArrayOutputStream().use { output ->
            GZIPOutputStream(output).use { it.write(csv.toByteArray(Charsets.UTF_8)) }
            output.toByteArray()
        }
        val iv = ByteArray(12) { (it + 1).toByte() }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec("Ch3Ch3ngZh1!99!@".toByteArray(Charsets.UTF_8), "AES"),
            javax.crypto.spec.GCMParameterSpec(128, iv)
        )
        val blob = byteArrayOf('C'.code.toByte(), 'H'.code.toByte(), 'E'.code.toByte(), 3) +
            iv + cipher.doFinal(compressed)

        val imported = DataExporter.importData(TEST_PASSPHRASE, blob)!!.business

        assertEquals("证书测试车", imported.vehicles.single().name)
        assertEquals(180000.0, imported.vehicles.single().purchasePrice, 0.0)
        assertEquals(2, imported.certificates.size)
        val byKey = imported.certificates.associateBy { it.key }
        assertEquals("身份证正面.jpg", byKey["id_front"]!!.fileName)
        assertEquals("image/jpeg", byKey["id_front"]!!.mimeType)
        org.junit.Assert.assertArrayEquals(byteArrayOf(1, 2, 3, 4, 5), byKey["id_front"]!!.data)
        assertEquals("text", byKey["vehicle_v1_vin"]!!.kind)
        assertEquals("LSVAA1234567890AB", byKey["vehicle_v1_vin"]!!.data.toString(Charsets.UTF_8))
    }

    @Test
    fun streamsThroughRealFileHandles() {
        val business = File.createTempFile("cheku-stream-", ".che")
        val archive = File.createTempFile("cheku-archive-", ".cert.che")
        try {
            val cert = CertificateEntry(
                key = "id_front",
                fileName = "身份证正面.jpg",
                mimeType = "image/jpeg",
                kind = "file",
                data = ByteArray(2048) { (it % 241).toByte() }
            )
            FileOutputStream(archive).use { DataExporter.exportArchiveTo(TEST_PASSPHRASE, it, listOf(cert)) }
            FileOutputStream(business).use { out ->
                DataExporter.exportDataTo(TEST_PASSPHRASE,
                    out,
                    vehicles = listOf(vehicle("v1")),
                    mileageRecords = listOf(
                        MileageRecordEntity("m1", "v1", 1.0, "第一行\n第二行,带\"引号\"", 2L, 3.0)
                    ),
                    energyRecords = emptyList(),
                    maintenanceItems = emptyList(),
                    maintenanceHistory = emptyList()
                )
            }
            assertTrue("导出应产出非空文件", business.length() > 0)

            val imported = FileInputStream(business).use { DataExporter.importBusiness(TEST_PASSPHRASE, it) }!!

            assertEquals("v1", imported.vehicles.single().id)
            assertEquals("第一行\n第二行,带\"引号\"", imported.mileageRecords.single().note)
            assertEquals(3.0, imported.mileageRecords.single().roadFeeAmount, 0.0)
            assertTrue("业务包不应带证书", imported.certificates.isEmpty())

            val restored = FileInputStream(archive).use { DataExporter.importArchive(TEST_PASSPHRASE, it) }!!
            org.junit.Assert.assertArrayEquals(cert.data, restored.single().data)
        } finally {
            business.delete()
            archive.delete()
        }
    }

    /** 解压炸弹防护 */
    @Test
    fun rejectsBackupWhoseInflatedSizeExceedsBudget() {
        val bytes = DataExporter.exportData(TEST_PASSPHRASE,
            vehicles = listOf(vehicle("v1")),
            mileageRecords = listOf(
                MileageRecordEntity("m1", "v1", 1.0, "x".repeat(8192), 2L)
            ),
            energyRecords = emptyList(),
            maintenanceItems = emptyList(),
            maintenanceHistory = emptyList()
        )

        assertNotNull("正常预算下应可读", DataExporter.importBusiness(TEST_PASSPHRASE, ByteArrayInputStream(bytes)))
        assertNull(
            "预算收紧到 64 字节时必须拒绝",
            DataExporter.importBusiness(TEST_PASSPHRASE, ByteArrayInputStream(bytes), inflateLimit = 64L)
        )
    }

    @Test
    fun businessExportWritesManifestFirstInKeyValueFormAndNeverCarriesCertificates() {
        val csv = decryptToCsv(
            DataExporter.exportData(TEST_PASSPHRASE,
                vehicles = listOf(vehicle("v1")),
                mileageRecords = emptyList(),
                energyRecords = emptyList(),
                maintenanceItems = emptyList(),
                maintenanceHistory = emptyList(),
                appVersion = "v0.6.4",
                dbSchema = 15
            )
        )

        // MANIFEST 必须在最前：解析时先读到它，才知道要不要去找配套证书包
        assertTrue("MANIFEST 应是第一段：\n${csv.take(80)}", csv.startsWith("=== MANIFEST ==="))
        assertTrue("应写 format 行：\n$csv", csv.contains("format,che"))
        assertTrue("应写 version 行：\n$csv", csv.contains("version,4"))
        assertTrue("应写 createdAt 行：\n$csv", csv.contains("createdAt,"))
        assertTrue("应写 appVersion 行：\n$csv", csv.contains("appVersion,v0.6.4"))
        assertTrue("应写 dbSchema 行：\n$csv", csv.contains("dbSchema,15"))
        listOf("archiveFile,", "archiveSha256,", "archiveBytes,", "archiveEntries,").forEach {
            assertFalse("未导出证书包时不应出现 $it：\n$csv", csv.contains(it))
        }
        assertFalse("业务包不应含证书段", csv.contains("=== DOCUMENT ARCHIVE ==="))
    }

    @Test
    fun certificatePackageContainsNoBusinessData() {
        val out = ByteArrayOutputStream()
        DataExporter.exportArchiveTo(TEST_PASSPHRASE,
            out,
            listOf(sampleCertificate()),
            appVersion = "v0.6.4",
            dbSchema = 15
        )
        val csv = decryptToCsv(out.toByteArray())

        assertTrue(csv.startsWith("=== MANIFEST ==="))
        assertTrue("应标明是证书包：\n$csv", csv.contains("format,che-docs"))
        assertTrue("应标明版本 1：\n$csv", csv.contains("version,1"))
        assertFalse("证书包不应声明配套证书包：\n$csv", csv.contains("archiveFile,"))
        assertTrue(csv.contains("=== DOCUMENT ARCHIVE ==="))
        listOf("=== VEHICLES ===", "=== MILEAGE ===", "=== ENERGY ===", "=== MAINTENANCE ITEMS ===",
            "=== FIT ACTIVITIES ===", "=== PERFORMANCE SESSIONS ===").forEach { marker ->
            assertFalse("证书包不应含 $marker", csv.contains(marker))
        }
    }

    @Test
    fun certificatePackageRoundTripsAndIsNeverTreatedAsBusinessData() {
        val out = ByteArrayOutputStream()
        DataExporter.exportArchiveTo(TEST_PASSPHRASE, out, listOf(sampleCertificate()), appVersion = "v0.6.4", dbSchema = 15)
        val blob = out.toByteArray()

        val parsed = DataExporter.parseBackup(TEST_PASSPHRASE, ByteArrayInputStream(blob))!!
        assertEquals(CheBackupKind.Archive, parsed.kind)
        assertEquals(MANIFEST_FORMAT_ARCHIVE, parsed.manifest!!.format)
        assertEquals(MANIFEST_VERSION_ARCHIVE, parsed.manifest!!.version)
        assertEquals("dbSchema 应原样往返", 15, parsed.manifest!!.dbSchema)
        org.junit.Assert.assertArrayEquals(
            sampleCertificate().data,
            parsed.archive.single().data
        )

        assertNull("证书包在业务导入入口上必须返回 null", DataExporter.importBusiness(TEST_PASSPHRASE, ByteArrayInputStream(blob)))
        assertEquals(1, DataExporter.importArchive(TEST_PASSPHRASE, ByteArrayInputStream(blob))!!.size)
    }

    @Test
    fun businessExportRecordsArchivePackageReference() {
        val certBytes = DataExporter.exportArchive(TEST_PASSPHRASE, listOf(sampleCertificate()), appVersion = "v0.6.4", dbSchema = 15)
        // 固定时间戳，让文件名断言可判定
        val archiveName = DataExporter.archiveFileName(1_774_000_000_000L)
        val sha = DataExporter.sha256Hex(certBytes)

        val out = ByteArrayOutputStream()
        DataExporter.exportDataTo(TEST_PASSPHRASE,
            out,
            vehicles = listOf(vehicle("v1")),
            mileageRecords = emptyList(),
            energyRecords = emptyList(),
            maintenanceItems = emptyList(),
            maintenanceHistory = emptyList(),
            appVersion = "v0.6.4",
            dbSchema = 15,
            archiveFile = archiveName,
            archiveSha256 = sha,
            archiveBytes = certBytes.size.toLong(),
            archiveEntries = 1
        )

        val csv = decryptToCsv(out.toByteArray())
        assertTrue("应写证书包文件名：\n$csv", csv.contains("archiveFile,$archiveName"))
        assertTrue("应写证书包指纹：\n$csv", csv.contains("archiveSha256,$sha"))
        assertTrue("应写证书包字节数：\n$csv", csv.contains("archiveBytes,${certBytes.size}"))
        assertTrue("应写证书条目数：\n$csv", csv.contains("archiveEntries,1"))

        val parsed = DataExporter.parseBackup(TEST_PASSPHRASE, ByteArrayInputStream(out.toByteArray()))!!
        assertEquals(CheBackupKind.Business, parsed.kind)
        assertEquals(archiveName, parsed.manifest!!.archiveFile)
        assertEquals(sha, parsed.manifest!!.archiveSha256)
        assertEquals(certBytes.size.toLong(), parsed.manifest!!.archiveBytes)
        assertEquals(1, parsed.manifest!!.archiveEntries)
        assertTrue("四元组齐全，hasArchive 为真", parsed.manifest!!.hasArchive)

        // 校验判据：一致的通过；改一个字节就拒绝
        assertTrue("一致的证书包应通过校验", DataExporter.matchesArchive(certBytes, parsed.manifest))
        val tampered = certBytes.copyOf().also { it[it.size - 1] = (it[it.size - 1].toInt() xor 0xFF).toByte() }
        assertFalse("被篡改的证书包必须拒绝", DataExporter.matchesArchive(tampered, parsed.manifest))
        assertFalse("主包没声明配套证书包时应拒绝", DataExporter.matchesArchive(certBytes, null))
    }

    @Test
    fun partialArchiveReferenceIsNotWrittenAtAll() {
        val csv = decryptToCsv(
            DataExporter.exportData(TEST_PASSPHRASE,
                vehicles = listOf(vehicle("v1")),
                mileageRecords = emptyList(),
                energyRecords = emptyList(),
                maintenanceItems = emptyList(),
                maintenanceHistory = emptyList(),
                archiveFile = "CheKu_人车证书_20260920_1751.cert.che"
            )
        )
        assertFalse("只给文件名时不应写 archiveFile：\n$csv", csv.contains("archiveFile,"))
        assertTrue(csv.contains("format,che"))
    }

    @Test
    fun businessAndArchiveFileNamesShareTheSameTimestamp() {
        val stamp = 1_774_000_000_000L
        val business = DataExporter.businessFileName(stamp)
        val archive = DataExporter.archiveFileName(stamp)

        assertTrue("主包名：$business", business.startsWith("CheKu_用车记录_") && business.endsWith(".che"))
        assertTrue("证书包名：$archive", archive.startsWith("CheKu_人车证书_") && archive.endsWith(".cert.che"))
        val stampText = DataExporter.formatDate(stamp)
        assertTrue("两者应含同一时间戳 $stampText", business.contains(stampText) && archive.contains(stampText))
        // 证书包以 .che 结尾（同一套加解密与 SAF MIME 行为），但中间多一段 .cert 以区分用途
        assertFalse("两个文件名不应相同", business == archive)
    }

    /** 旧版整合包 */
    @Test
    fun legacyBundleIsRecognizedByMissingManifest() {
        val csv = """
            === VEHICLES ===
            id,name,model,plate,category,energyType,transmissionType,fuelTankCapacity,batteryCapacity,defaultChargeLimitSoc,currentMileage,updatedAt,manufacturingDate,purchasePrice,purchaseTax
            v1,旧包车,Model,TEST,Car,Fuel,Automatic,50,,,1234.5,100,,180000.0,15929.0
            === DOCUMENT ARCHIVE ===
            key,fileName,mimeType,kind,base64
            id_front,身份证正面.jpg,image/jpeg,file,AQIDBAU=
        """.trimIndent()
        val parsed = DataExporter.parseBackup(TEST_PASSPHRASE, ByteArrayInputStream(sealGcm(csv)))!!

        assertEquals(CheBackupKind.LegacyBundle, parsed.kind)
        assertNull("旧整合包没有 MANIFEST", parsed.manifest)
        assertEquals("旧包车", parsed.business.vehicles.single().name)
        assertEquals(1, parsed.business.certificates.size)
        assertEquals(parsed.business.certificates, parsed.archive)
        // 旧包仍然允许走业务导入（业务数据 + 证书一起进来）
        assertEquals("旧包车", DataExporter.importBusiness(TEST_PASSPHRASE, ByteArrayInputStream(sealGcm(csv)))!!.vehicles.single().name)
        assertEquals(1, DataExporter.importArchive(TEST_PASSPHRASE, ByteArrayInputStream(sealGcm(csv)))!!.size)
    }

    @Test
    fun appendBase64MatchesWholeEncode() {
        val cases = listOf(0, 1, 2, 3, 4, 5, 6, 7, 4095, 4096, 4097, 100_000)
        cases.forEach { size ->
            val data = ByteArray(size) { ((it * 31 + 7) % 251).toByte() }
            val default = StringBuilder().also { appendBase64(it, data) }.toString()
            assertEquals("size=$size 默认块长不一致", java.util.Base64.getEncoder().encodeToString(data), default)
            val tiny = StringBuilder().also { appendBase64(it, data, chunkBytes = 12) }.toString()
            assertEquals("size=$size 小块长不一致", java.util.Base64.getEncoder().encodeToString(data), tiny)
        }
        // 空数组不应产出任何字符（也不该抛）
        assertEquals("", StringBuilder().also { appendBase64(it, ByteArray(0)) }.toString())
        runCatching { appendBase64(StringBuilder(), ByteArray(9), chunkBytes = 10) }
            .onSuccess { fail("块长非 3 的倍数应被拒绝") }
    }

    @Test
    fun appendBase64StreamingSurvivesOneByteAtATimeReads() {
        val data = ByteArray(1000) { ((it * 17 + 3) % 251).toByte() }
        val expected = java.util.Base64.getEncoder().encodeToString(data)

        listOf(3, 6, 12, 24 * 1024).forEach { chunk ->
            assertEquals(
                "块长 $chunk 时逐字节流应产出与整份编码相同的结果",
                expected,
                StringBuilder().also { appendBase64(it, TrickleInputStream(data), chunkBytes = chunk) }.toString()
            )
        }
        // 空流也不该产出任何字符
        assertEquals("", StringBuilder().also { appendBase64(it, TrickleInputStream(ByteArray(0))) }.toString())
    }

    private class TrickleInputStream(private val data: ByteArray) : java.io.InputStream() {
        private var index = 0

        override fun read(): Int = if (index < data.size) data[index++].toInt() and 0xFF else -1

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (index >= data.size) return -1
            if (len <= 0) return 0
            b[off] = data[index++]
            return 1
        }
    }

    @Test
    fun parseArchiveStagedMatchesTheInMemoryImport() {
        val cert = sampleCertificate()
        val text = CertificateEntry(
            key = "vehicle_v1_vin", fileName = "vehicle_v1_vin.txt",
            mimeType = "text/plain", kind = "text", data = "LSVAA1234567890AB".toByteArray(Charsets.UTF_8)
        )
        val bytes = DataExporter.exportArchive(TEST_PASSPHRASE, listOf(cert, text), appVersion = "v0.6.4", dbSchema = 15)

        val sink = CollectingStagingSink()
        val staged = DataExporter.parseArchiveStaged(TEST_PASSPHRASE, ByteArrayInputStream(bytes), sink)!!

        assertEquals(MANIFEST_FORMAT_ARCHIVE, staged.manifest!!.format)
        assertEquals(MANIFEST_VERSION_ARCHIVE, staged.manifest!!.version)
        assertEquals(15, staged.manifest!!.dbSchema)
        assertEquals(2, staged.certificates.size)
        assertTrue("不应有失败项：${staged.failures}", staged.failures.isEmpty())
        // 元数据与字节都要与内存路径一致
        listOf(cert, text).forEach { entry ->
            assertEquals("${entry.key} 的字节数", entry.data.size.toLong(), sink.written.getValue(entry.key).size.toLong())
            assertArrayEquals("${entry.key} 的字节", entry.data, sink.written.getValue(entry.key))
        }
        assertEquals(listOf("身份证正面.jpg", "image/jpeg", "file"), sink.meta["id_front"])
        assertEquals(listOf("vehicle_v1_vin.txt", "text/plain", "text"), sink.meta["vehicle_v1_vin"])
        assertEquals(
            "条目数与字节数应写进 StagedCertificate",
            mapOf("id_front" to cert.data.size.toLong(), "vehicle_v1_vin" to text.data.size.toLong()),
            staged.certificates.associate { it.key to it.bytes }
        )
    }

    @Test
    fun parseArchiveStagedReportsBrokenCertificateInsteadOfDroppingIt() {
        val csv = """
            === MANIFEST ===
            format,che-docs
            version,1
            appVersion,v0.6.4
            dbSchema,15
            === DOCUMENT ARCHIVE ===
            key,fileName,mimeType,kind,base64
            id_front,身份证正面.jpg,image/jpeg,file,!!!这不是合法 base64!!!
        """.trimIndent()

        val staged = DataExporter.parseArchiveStaged(TEST_PASSPHRASE, ByteArrayInputStream(sealGcm(csv)), CollectingStagingSink())!!

        assertTrue("坏行不应产出证书", staged.certificates.isEmpty())
        assertEquals("必须报出失败项", 1, staged.failures.size)
        assertEquals("id_front", staged.failures.single().key)
    }

    private class CollectingStagingSink : CertificateStagingSink {
        val written = LinkedHashMap<String, ByteArray>()
        val meta = LinkedHashMap<String, List<String>>()

        override fun openFor(key: String, fileName: String, mimeType: String, kind: String): OutputStream {
            meta[key] = listOf(fileName, mimeType, kind)
            return object : ByteArrayOutputStream() {
                override fun close() {
                    written[key] = toByteArray()
                    super.close()
                }
            }
        }
    }

    /** 真正跨多块的包必须能往返 */
    @Test
    fun packageSpansMultipleChunksAndStillRoundTrips() {
        val payload = ByteArray(3 * 1024 * 1024).also { java.util.Random(7L).nextBytes(it) }
        val cert = CertificateEntry("id_front", "身份证正面.jpg", "image/jpeg", "file", payload)

        val blob = DataExporter.exportArchive(TEST_PASSPHRASE, listOf(cert), appVersion = "v0.6.4", dbSchema = 15)
        val records = containerRecordCount(blob)
        assertTrue("3MB 不可压缩明文应被切成多块，实际 $records 块", records >= 3)

        // 独立实现的解密路径与生产导入路径都要拿到原字节
        val restored = DataExporter.importArchive(TEST_PASSPHRASE, ByteArrayInputStream(blob))!!
        assertArrayEquals(payload, restored.single().data)
    }

    @Test
    fun packageIsRejectedWhenTruncatedAtARecordBoundary() {
        val cert = CertificateEntry("id_front", "身份证正面.jpg", "image/jpeg", "file", ByteArray(4096) { it.toByte() })
        val blob = DataExporter.exportArchive(TEST_PASSPHRASE, listOf(cert))

        // 包头 33 字节（v5），记录紧接其后
        var position = v5HeaderBytes
        var lastStart = v5HeaderBytes
        var records = 0
        while (true) {
            lastStart = position
            position++                                   // flag
            val length = ((blob[position].toInt() and 0xFF) shl 24) or
                ((blob[position + 1].toInt() and 0xFF) shl 16) or
                ((blob[position + 2].toInt() and 0xFF) shl 8) or
                (blob[position + 3].toInt() and 0xFF)
            position += 4 + length
            records++
            if (blob[lastStart].toInt() == 1) break
        }
        assertTrue("应至少有一条记录", records >= 1)

        val truncated = blob.copyOfRange(0, lastStart)     // 丢掉最后一块（它带着 last 标记）
        assertNull("截断的包必须被拒绝，而不是安静地少读一截", DataExporter.importData(TEST_PASSPHRASE, truncated))
    }

    @Test
    fun packageIsRejectedWhenAChunkIsTampered() {
        val cert = CertificateEntry("id_front", "身份证正面.jpg", "image/jpeg", "file", ByteArray(4096) { it.toByte() })
        val blob = DataExporter.exportArchive(TEST_PASSPHRASE, listOf(cert))
        val tampered = blob.copyOf()
        tampered[40] = (tampered[40].toInt() xor 0x01).toByte()
        assertNull("被篡改的包必须整体拒绝", DataExporter.importData(TEST_PASSPHRASE, tampered))
    }

    @Test
    fun sha256HexMatchesKnownVector() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            DataExporter.sha256Hex("abc".toByteArray(Charsets.UTF_8))
        )
        assertEquals(64, DataExporter.sha256Hex(ByteArray(0)).length)
    }

    @Test
    fun exportRejectsEmptyPassphrase() {
        try {
            DataExporter.exportData(
                "".toCharArray(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList()
            )
            fail("空口令必须被拒绝：用内置常量加密新数据的路径不允许再存在")
        } catch (expected: IllegalArgumentException) {
            assertTrue("异常应说明是口令问题：${expected.message}", expected.message.orEmpty().contains("口令"))
        }
    }

    @Test
    fun wrongOrMissingPassphraseCannotOpenEncryptedPackage() {
        val bytes = DataExporter.exportData(
            TEST_PASSPHRASE, emptyList(), emptyList(), emptyList(), emptyList(), emptyList(),
            appVersion = "v0.6.4", dbSchema = 15
        )
        assertNull("没给口令时不应读出任何数据", DataExporter.importData(null, bytes))
        assertNull("口令不对时不应读出任何数据", DataExporter.importData("other-passphrase".toCharArray(), bytes))
        assertNotNull("口令正确时应能读出", DataExporter.importData(TEST_PASSPHRASE, bytes))
        assertEquals("写入格式应为 v5", 5, bytes[3].toInt())
    }

    @Test
    fun importsLegacyV4BackupWithoutPassphrase() {
        val csv = """
            === MANIFEST ===
            format,che
            version,4
            createdAt,2026-09-20T17:30:55+08:00
            appVersion,v0.6.3
            dbSchema,15
            === VEHICLES ===
            id,name,model,plate,category,energyType,transmissionType,fuelTankCapacity,batteryCapacity,defaultChargeLimitSoc,currentMileage,updatedAt
            v1,旧包汽车,Model,TEST,Car,Fuel,Automatic,50,,,1234.5,100
        """.trimIndent()

        val imported = DataExporter.importData(null, sealLegacyV4(csv))!!

        assertEquals("旧包汽车", imported.business.vehicles.single().name)
        assertEquals(4, imported.manifest!!.version)
        assertEquals(CheBackupKind.Business, imported.kind)
    }

    private fun sealLegacyV4(csv: String): ByteArray {
        val compressed = ByteArrayOutputStream().use { output ->
            GZIPOutputStream(output).use { it.write(csv.toByteArray(Charsets.UTF_8)) }
            output.toByteArray()
        }
        val nonceBase = ByteArray(8) { (it + 1).toByte() }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec("Ch3Ch3ngZh1!99!@".toByteArray(Charsets.UTF_8), "AES"),
            javax.crypto.spec.GCMParameterSpec(128, nonceBase + byteArrayOf(0, 0, 0, 0))
        )
        cipher.updateAAD(byteArrayOf(0, 0, 0, 0))
        val ciphertext = cipher.doFinal(compressed)
        val header = byteArrayOf('C'.code.toByte(), 'H'.code.toByte(), 'E'.code.toByte(), 4) + nonceBase
        val recordHeader = byteArrayOf(
            1, // last
            (ciphertext.size ushr 24).toByte(), (ciphertext.size ushr 16).toByte(),
            (ciphertext.size ushr 8).toByte(), ciphertext.size.toByte()
        )
        return header + recordHeader + ciphertext
    }

    /** 只在这两条测试里用到的样本证书 */
    private fun sampleCertificate() = CertificateEntry(
        key = "id_front",
        fileName = "身份证正面.jpg",
        mimeType = "image/jpeg",
        kind = "file",
        data = ByteArray(1024) { (it % 251).toByte() }
    )

    private fun decryptToCsv(blob: ByteArray): String {
        val plain = java.io.ByteArrayOutputStream()
        walkRecords(blob) { index, _ -> plain.write(decryptRecord(blob, index)) }
        return java.util.zip.GZIPInputStream(plain.toByteArray().inputStream()).use {
            it.readBytes().toString(Charsets.UTF_8)
        }
    }

    private fun containerRecordCount(blob: ByteArray): Int {
        var count = 0
        walkRecords(blob) { _, _ -> count++ }
        return count
    }

    /** v5 包头长度 */
    private val v5HeaderBytes = 33

    /** 从 v5 包头派生 AES 密钥 */
    private fun v5Key(blob: ByteArray): SecretKeySpec {
        val iterations = ((blob[5].toInt() and 0xFF) shl 24) or ((blob[6].toInt() and 0xFF) shl 16) or
            ((blob[7].toInt() and 0xFF) shl 8) or (blob[8].toInt() and 0xFF)
        val salt = blob.copyOfRange(9, 25)
        val spec = javax.crypto.spec.PBEKeySpec(TEST_PASSPHRASE, salt, iterations, 256)
        return try {
            SecretKeySpec(
                javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded,
                "AES"
            )
        } finally {
            spec.clearPassword()
        }
    }

    /** 按记录格式走一遍 */
    private fun walkRecords(blob: ByteArray, onRecord: (Int, ByteArray) -> Unit) {
        require(blob.size > v5HeaderBytes) { "包过短" }
        assertEquals("魔数应为 CHE+5（v5 口令派生分块容器）", 5, blob[3].toInt())
        assertEquals('C'.code.toByte(), blob[0])
        assertEquals('H'.code.toByte(), blob[1])
        assertEquals('E'.code.toByte(), blob[2])
        assertEquals("kdfId 应为 1（PBKDF2-HMAC-SHA256）", 1, blob[4].toInt())
        var position = v5HeaderBytes
        var index = 0
        var last = false
        while (!last) {
            val flag = blob[position++].toInt()
            val length = ((blob[position].toInt() and 0xFF) shl 24) or
                ((blob[position + 1].toInt() and 0xFF) shl 16) or
                ((blob[position + 2].toInt() and 0xFF) shl 8) or
                (blob[position + 3].toInt() and 0xFF)
            position += 4
            onRecord(index, blob.copyOfRange(position, position + length))
            position += length
            last = flag == 1
            index++
        }
        assertEquals("记录应恰好铺满整个包（不多不少）", blob.size, position)
    }

    private fun decryptRecord(blob: ByteArray, index: Int): ByteArray {
        val nonceBase = blob.copyOfRange(25, 33)
        var position = v5HeaderBytes
        var current = 0
        while (true) {
            val flag = blob[position++].toInt()
            val length = ((blob[position].toInt() and 0xFF) shl 24) or
                ((blob[position + 1].toInt() and 0xFF) shl 16) or
                ((blob[position + 2].toInt() and 0xFF) shl 8) or
                (blob[position + 3].toInt() and 0xFF)
            position += 4
            if (current == index) {
                val nonce = nonceBase + byteArrayOf(
                    (index ushr 24).toByte(), (index ushr 16).toByte(),
                    (index ushr 8).toByte(), index.toByte()
                )
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(
                    Cipher.DECRYPT_MODE,
                    v5Key(blob),
                    javax.crypto.spec.GCMParameterSpec(128, nonce)
                )
                cipher.updateAAD(byteArrayOf(
                    (index ushr 24).toByte(), (index ushr 16).toByte(),
                    (index ushr 8).toByte(), index.toByte()
                ))
                return cipher.doFinal(blob.copyOfRange(position, position + length))
            }
            position += length
            if (flag == 1) error("记录 $index 不存在（共 $current 条）")
            current++
        }
    }

    private fun sealGcm(csv: String): ByteArray {
        val compressed = ByteArrayOutputStream().use { output ->
            GZIPOutputStream(output).use { it.write(csv.toByteArray(Charsets.UTF_8)) }
            output.toByteArray()
        }
        val iv = ByteArray(12) { (it + 1).toByte() }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec("Ch3Ch3ngZh1!99!@".toByteArray(Charsets.UTF_8), "AES"),
            javax.crypto.spec.GCMParameterSpec(128, iv)
        )
        return byteArrayOf('C'.code.toByte(), 'H'.code.toByte(), 'E'.code.toByte(), 3) +
            iv + cipher.doFinal(compressed)
    }

    private fun vehicle(id: String) = VehicleEntity(
        id = id,
        name = "测试车",
        model = "Model",
        plate = "TEST",
        category = VehicleCategory.Car,
        energyType = EnergyType.Fuel,
        transmissionType = TransmissionType.Automatic,
        currentMileage = 0.0,
        updatedAt = 1L
    )
}
