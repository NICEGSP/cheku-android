package com.cheku.app

import com.cheku.app.ReadableExporter.CertificateBinary
import com.cheku.app.ReadableExporter.LedgerData
import com.cheku.app.data.EnergyRecordEntity
import com.cheku.app.data.EnergyRecordType
import com.cheku.app.data.EnergySettlementEntity
import com.cheku.app.data.FitActivityEntity
import com.cheku.app.data.FitActivityStatus
import com.cheku.app.data.MaintenanceHistoryEntity
import com.cheku.app.data.MaintenanceItemEntity
import com.cheku.app.data.MaintenanceItemType
import com.cheku.app.data.MileageRecordEntity
import com.cheku.app.data.PerformanceQuality
import com.cheku.app.data.PerformanceSessionEntity
import com.cheku.app.data.PerformanceStatus
import com.cheku.app.data.PerformanceTestType
import com.cheku.app.data.SettlementStatus
import com.cheku.app.data.TransmissionType
import com.cheku.app.data.VehicleCategory
import com.cheku.app.data.VehicleEntity
import com.cheku.app.data.EnergyType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Random
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import jxl.Workbook as JxlWorkbook

class ReadableExporterTest {
    private fun sampleData(): LedgerData {
        val v1 = VehicleEntity(
            id = "v1", name = "我的车", model = "M3", plate = "京A1",
            category = VehicleCategory.Car, energyType = EnergyType.Electric,
            transmissionType = TransmissionType.ElectricDrive,
            fuelTankCapacityLiters = null, batteryCapacityKwh = 75.0,
            defaultChargeLimitSoc = 90, currentMileage = 12000.0,
            updatedAt = 15000L, manufacturingDate = null, purchasePrice = 200000.0, purchaseTax = 0.0
        )
        val mileage = listOf(
            MileageRecordEntity("m1", "v1", 100.0, "x", 1000L, 5.0)
        )
        val energyRecords = listOf(
            EnergyRecordEntity(
                "e1", "v1", EnergyRecordType.Charge, 200.0, 30.0, 32.0, 30.0,
                isFull = true, missedPrevious = false, fuelGrade = "", unitPrice = 0.0,
                chargeMethod = "快充", electricityPrice = 1.2, serviceFee = 2.0,
                chargeLimitSocSnapshot = 90, note = "", createdAt = 2000L
            )
        )
        val settlements = listOf(
            EnergySettlementEntity(
                id = "s1", vehicleId = "v1", type = EnergyRecordType.Charge,
                status = SettlementStatus.Valid, startRecordId = null, endRecordId = null,
                startMileage = 1000.0, endMileage = 2000.0, distanceKm = 1000.0,
                totalAmount = 30.0, totalOrderAmount = 32.0, totalPaidAmount = 30.0,
                consumptionPer100Km = 15.0, costPerKm = 0.03, chargeLimitSoc = 90,
                invalidReason = "", createdAt = 3000L
            )
        )
        val items = listOf(
            MaintenanceItemEntity("itemP", "v1", "机油", 10000, 365, 2000, 5000L, 5000L, "",
                MaintenanceItemType.Periodic),
            MaintenanceItemEntity("itemR", "v1", "年检检修", 0, 0, 0, 6000L, 6000L, "",
                MaintenanceItemType.RepairInspection)
        )
        val history = listOf(
            MaintenanceHistoryEntity("hP", "v1", "itemP", "机油", 12000.0, 7000L, "",
                50.0, 200.0, "OX-1", "机油", 0.0, 0.0, null),
            MaintenanceHistoryEntity("hR", "v1", "itemR", "年检检修", 12000.0, 8000L, "检修",
                0.0, 0.0, "", "年检检修", 0.0, 300.0, null)
        )
        val sessions = listOf(
            PerformanceSessionEntity("p1", "v1", "0-100", PerformanceTestType.Acceleration100,
                PerformanceStatus.Completed, 9000L, 9100L, 7.5, 0.0, 3.0, 2.0,
                null, null, PerformanceQuality.Valid, "", false, 9000L, 9000L),
            // 未关联车辆的会话：vehicleId 为空，必须保留
            PerformanceSessionEntity("p2", null, "场地测试", PerformanceTestType.Combined,
                PerformanceStatus.Completed, 9500L, 9600L, 5.0, 0.0, 2.0, 1.0,
                null, null, PerformanceQuality.Valid, "", false, 9500L, 9500L)
        )
        val fit = listOf(
            FitActivityEntity("f1", "v1", "通勤", FitActivityStatus.Completed, 11000L, 12000L,
                0L, 5000.0, 20.0, true, 11900L, null, 11000L, 11000L)
        )
        return LedgerData(
            vehicles = listOf(v1), mileageRecords = mileage, energyRecords = energyRecords,
            energySettlements = settlements, maintenanceItems = items,
            maintenanceHistory = history, performanceSessions = sessions, fitActivities = fit
        )
    }

    private fun csvSection(csv: String, header: String): String {
        val start = csv.indexOf("## $header")
        require(start >= 0) { "CSV 缺少段落: $header" }
        val next = csv.indexOf("## ", start + 1)
        return if (next < 0) csv.substring(start) else csv.substring(start, next)
    }

    @Test
    fun csvExportsNineCategoriesInFourGroups() {
        val csv = ReadableExporter.exportCsv(sampleData())
        listOf("车辆", "里程记录", "能源记录", "能源结算", "保养项目", "保养记录",
            "检修记录", "费用汇总", "性能记录", "轨迹记录").forEach { name ->
            assertTrue("CSV 应含段落: $name", csv.contains("## $name"))
        }
        assertTrue("CSV 应标注分组: 车辆与行驶", csv.contains("分组: 车辆与行驶"))
        assertTrue("CSV 应标注分组: 能源", csv.contains("分组: 能源"))
        assertTrue("CSV 应标注分组: 保养与费用", csv.contains("分组: 保养与费用"))
        assertTrue("CSV 应标注分组: 测试与轨迹", csv.contains("分组: 测试与轨迹"))
    }

    @Test
    fun repairAndMaintenanceHistoryAreSeparated() {
        val csv = ReadableExporter.exportCsv(sampleData())
        val periodic = csvSection(csv, "保养记录")
        val repair = csvSection(csv, "检修记录")
        // 保养表含「机油」、不含「年检检修」
        assertTrue("保养记录表应包含机油", periodic.contains("机油"))
        assertFalse("保养记录表不应包含检修项「年检检修」", periodic.contains("年检检修"))
        // 检修表含「年检检修」、不含「机油」
        assertTrue("检修记录表应包含年检检修", repair.contains("年检检修"))
        assertFalse("检修记录表不应包含保养项「机油」", repair.contains("机油"))
    }

    @Test
    fun costSummaryIncludesNeverExportedSettlementFields() {
        val csv = ReadableExporter.exportCsv(sampleData())
        val summary = csvSection(csv, "费用汇总")
        assertTrue("费用汇总应含总金额(30)", summary.contains("30"))
        assertTrue("费用汇总应含订单总额(32)", summary.contains("32"))
        assertTrue("费用汇总应含实付总额(30)", summary.contains("30"))
        assertTrue("费用汇总应含每公里成本(0.03)", summary.contains("0.03"))
        assertTrue("费用汇总应含每百公里能耗(15)", summary.contains("15"))
        // 保养费用汇总
        assertTrue("费用汇总应含零件费合计(200)", summary.contains("200"))
        assertTrue("费用汇总应含维修金额合计(300)", summary.contains("300"))
        // 能源结算明细表也直出这些字段
        val settle = csvSection(csv, "能源结算")
        assertTrue("能源结算应含每公里成本(0.03)", settle.contains("0.03"))
        assertTrue("能源结算应含每百公里能耗(15)", settle.contains("15"))
    }

    @Test
    fun unlinkedPerformanceSessionIsNotDropped() {
        val csv = ReadableExporter.exportCsv(sampleData())
        val perf = csvSection(csv, "性能记录")
        assertTrue("性能记录应保留未关联车辆的会话", perf.contains("场地测试"))
        assertTrue("未关联车辆应记为「未关联车辆」", perf.contains("未关联车辆"))
    }

    @Test
    fun xlsHasOverviewAndAllSheetsWithCostFields() {
        val bytes = ReadableExporter.exportXls(sampleData())
        assertTrue("XLS 不应为空", bytes.isNotEmpty())
        val wb = JxlWorkbook.getWorkbook(ByteArrayInputStream(bytes))
        val names = wb.sheetNames.toSet()
        listOf("总览", "车辆", "里程记录", "能源记录", "能源结算", "保养项目",
            "保养记录", "检修记录", "费用汇总", "性能记录", "轨迹记录").forEach { name ->
            assertTrue("XLS 应含工作表: $name", name in names)
        }
        val settle = wb.getSheet("能源结算")
        val totalCol = (0 until settle.columns).indexOfFirst { settle.getCell(it, 0).contents == "总金额(元)" }
        assertTrue("能源结算表应有总金额列", totalCol >= 0)
        assertEquals("能源结算总金额应为 30.0", 30.0,
            settle.getCell(totalCol, 1).contents.toDouble(), 0.0001)
        // 检修 / 保养 分离在 XLS 同样成立
        val periodic = wb.getSheet("保养记录")
        val repair = wb.getSheet("检修记录")
        val periodicText = (0 until periodic.rows).flatMap { r ->
            (0 until periodic.columns).map { periodic.getCell(it, r).contents }
        }.joinToString(" ")
        val repairText = (0 until repair.rows).flatMap { r ->
            (0 until repair.columns).map { repair.getCell(it, r).contents }
        }.joinToString(" ")
        assertTrue("XLS 检修表应含年检检修", repairText.contains("年检检修"))
        assertFalse("XLS 保养表不应含年检检修", periodicText.contains("年检检修"))
        // 未关联车辆性能记录保留
        val perf = wb.getSheet("性能记录")
        val perfText = (0 until perf.rows).flatMap { r ->
            (0 until perf.columns).map { perf.getCell(it, r).contents }
        }.joinToString(" ")
        assertTrue("XLS 性能表应含未关联车辆标记", perfText.contains("未关联车辆"))
        wb.close()
    }

    @Test
    fun zipPacksCertificatesUnencryptedAndPreservesBytes() {
        val original = ByteArray(2_000_000).also { Random(42).nextBytes(it) }
        val certs = listOf(CertificateBinary("身份证.jpg") { ByteArrayInputStream(original) })
        val zip = ReadableExporter.exportZip(sampleData(), certs)
        assertTrue("ZIP 不应为空", zip.isNotEmpty())

        val entries = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(zip)).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                val buf = ByteArrayOutputStream()
                val tmp = ByteArray(64 * 1024)
                var n: Int
                while (zis.read(tmp).also { n = it } >= 0) buf.write(tmp, 0, n)
                entries[entry.name] = buf.toByteArray()
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        assertTrue("ZIP 应含可读台账.csv", "可读台账.csv" in entries)
        assertTrue("ZIP 应含说明.txt", "说明.txt" in entries)
        assertTrue("ZIP 应含证书原件", "身份证.jpg" in entries)
        // 证书原件不加密：字节与原始完全一致
        assertEquals("证书原件字节必须保真", original.toList(), entries["身份证.jpg"]?.toList())
    }

    @Test
    fun zipHandlesLargeCertificateWithChunkedCopy() {
        // 大二进制分块写入，避免整份读进内存；此处验证分块拷贝后字节完整。
        val original = ByteArray(5_000_000).also { Random(7).nextBytes(it) }
        val certs = listOf(CertificateBinary("big.bin") { ByteArrayInputStream(original) })
        val zip = ReadableExporter.exportZip(sampleData(), certs)
        val extracted = readZipEntry(zip, "big.bin")
        assertNotNull("应能从 ZIP 取出大证书", extracted)
        assertEquals("大证书字节必须保真(${original.size})", original.size, extracted!!.size)
        assertEquals("大证书内容一致", original.toList(), extracted.toList())
    }

    private fun readZipEntry(zipBytes: ByteArray, name: String): ByteArray? {
        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (entry.name == name) {
                    val buf = ByteArrayOutputStream()
                    val tmp = ByteArray(64 * 1024)
                    var n: Int
                    while (zis.read(tmp).also { n = it } >= 0) buf.write(tmp, 0, n)
                    return buf.toByteArray()
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        return null
    }

    @Test
    fun xlsDoesNotCloseTheCallersStream() {
        val sink = object : java.io.ByteArrayOutputStream() {
            var closed = false
            override fun close() {
                closed = true
                super.close()
            }

            override fun write(b: ByteArray, off: Int, len: Int) {
                check(!closed) { "调用方的流被导出器关掉了 —— 后续写入必然失败" }
                super.write(b, off, len)
            }
        }

        ReadableExporter.exportXls(LedgerData(), sink)

        assertFalse("exportXls 不应关闭调用方的流", sink.closed)
        assertTrue("应仍然产出了 XLS 内容", sink.size() > 0)
        // 关流之后调用方仍应能自己继续写（这正是"流归调用方所有"的意义）
        sink.write(byteArrayOf(1, 2, 3))
        assertEquals("关流后调用方仍可追加", sink.size() > 0, true)
    }

    /** CSV 同样不许关调用方的流 */
    @Test
    fun csvDoesNotCloseTheCallersStream() {
        val sink = object : java.io.ByteArrayOutputStream() {
            var closed = false
            override fun close() {
                closed = true
                super.close()
            }

            override fun write(b: ByteArray, off: Int, len: Int) {
                check(!closed) { "调用方的流被导出器关掉了" }
                super.write(b, off, len)
            }
        }

        ReadableExporter.exportCsv(LedgerData(), sink)
        assertFalse("exportCsv 不应关闭调用方的流", sink.closed)
        assertTrue(sink.size() > 0)
    }

    /** ZIP 也不许关调用方的流 */
    @Test
    fun zipDoesNotCloseTheCallersStream() {
        val sink = object : java.io.ByteArrayOutputStream() {
            var closed = false
            override fun close() {
                closed = true
                super.close()
            }

            override fun write(b: ByteArray, off: Int, len: Int) {
                check(!closed) { "调用方的流被导出器关掉了" }
                super.write(b, off, len)
            }
        }

        ReadableExporter.exportZip(LedgerData(), emptyList(), sink)

        assertFalse("exportZip 不应关闭调用方的流", sink.closed)
        assertTrue("应产出 ZIP 内容", sink.size() > 0)
    }
}
