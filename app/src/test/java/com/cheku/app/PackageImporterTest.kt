package com.cheku.app

import com.cheku.app.data.EnergyRecordEntity
import com.cheku.app.data.EnergyRecordType
import com.cheku.app.data.EnergyType
import com.cheku.app.data.FitActivityEntity
import com.cheku.app.data.FitActivityStatus
import com.cheku.app.data.FitSampleEntity
import com.cheku.app.data.MaintenanceHistoryEntity
import com.cheku.app.data.MaintenanceItemEntity
import com.cheku.app.data.MaintenanceItemType
import com.cheku.app.data.MileageRecordEntity
import com.cheku.app.data.PerformanceQuality
import com.cheku.app.data.PerformanceSampleEntity
import com.cheku.app.data.PerformanceSessionEntity
import com.cheku.app.data.PerformanceStatus
import com.cheku.app.data.PerformanceTestType
import com.cheku.app.data.TransmissionType
import com.cheku.app.data.VehicleCategory
import com.cheku.app.data.VehicleEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PackageImporterTest {
    private val dbSchema = 15
    private val atMillis = 1_774_000_000_000L

    private fun sampleData(): ReadableExporter.LedgerData {
        val vehicle = VehicleEntity(
            id = "v1",
            name = "测试车",
            model = "Model 3",
            plate = "沪A12345",
            category = VehicleCategory.Car,
            energyType = EnergyType.Electric,
            transmissionType = TransmissionType.Automatic,
            fuelTankCapacityLiters = null,
            batteryCapacityKwh = 60.0,
            defaultChargeLimitSoc = 90,
            currentMileage = 12345.6,
            updatedAt = 1_774_000_000_000L,
            manufacturingDate = null,
            purchasePrice = 250000.0,
            purchaseTax = 0.0
        )
        val mileage = MileageRecordEntity(
            id = "m1", vehicleId = "v1", mileage = 12345.6, note = "首保前",
            createdAt = 1_774_000_100_000L, roadFeeAmount = 15.0
        )
        val energy = EnergyRecordEntity(
            id = "e1", vehicleId = "v1", type = EnergyRecordType.Charge, mileage = 12000.0,
            amount = 45.6, orderAmount = 50.0, paidAmount = 45.6, isFull = true, missedPrevious = false,
            fuelGrade = "", unitPrice = 0.0, chargeMethod = "家充", electricityPrice = 0.3,
            serviceFee = 0.0, chargeLimitSocSnapshot = 90, note = "夜间", createdAt = 1_774_000_200_000L
        )
        val item = MaintenanceItemEntity(
            id = "i1", vehicleId = "v1", name = "机油", intervalKm = 10000, intervalDays = 365,
            lastServiceMileage = 8000, lastServiceAt = 1_774_000_300_000L, updatedAt = 1_774_000_300_000L,
            lastPartNumber = "P-1", itemType = MaintenanceItemType.Periodic, baselineAt = 1_774_000_300_000L
        )
        val history = MaintenanceHistoryEntity(
            id = "h1", vehicleId = "v1", itemId = "i1", itemName = "机油", serviceMileage = 8000.0,
            serviceAt = 1_774_000_300_000L, note = "4S", laborCost = 200.0, partsCost = 300.0,
            partNumber = "P-1", recordName = "机油", insuranceAmount = 0.0, repairAmount = 0.0, expiresAt = null
        )
        val activity = FitActivityEntity(
            id = "a1", vehicleId = "v1", name = "通勤", status = FitActivityStatus.Completed,
            startedAt = 1_774_000_400_000L, endedAt = 1_774_000_500_000L, pausedDurationMs = 0L,
            distanceMeters = 1234.5, maxSpeedMps = 12.3, autoPauseEnabled = true,
            lastSampleAt = 1_774_000_500_000L, exportedAt = null,
            createdAt = 1_774_000_400_000L, updatedAt = 1_774_000_400_000L
        )
        val sample = FitSampleEntity(
            activityId = "a1", timestamp = 1_774_000_410_000L, latitude = 31.23, longitude = 121.47,
            speedMps = 5.5, distanceMeters = 100.0, altitudeMeters = null, accuracyMeters = 3.0f,
            bearingDegrees = null, heartRateBpm = 120, cadenceRpm = null, powerWatts = null, motionMagnitude = null
        )
        val session = PerformanceSessionEntity(
            id = "s1", vehicleId = "v1", name = "加速测试", testType = PerformanceTestType.Acceleration100,
            status = PerformanceStatus.Completed, startedAt = 1_774_000_600_000L, endedAt = 1_774_000_610_000L,
            resultSeconds = 5.6, resultDistanceMeters = 100.0, maxAccelerationMps2 = 8.0,
            averageAccelerationMps2 = 5.0, averageNoiseDbA = null, maxNoiseDbA = null,
            quality = PerformanceQuality.Valid, invalidReason = "", microphoneCalibrated = false,
            createdAt = 1_774_000_600_000L, updatedAt = 1_774_000_600_000L
        )
        val perfSample = PerformanceSampleEntity(
            sessionId = "s1", timestampNanos = 1_774_000_605_000_000_000L, wallTime = 1_774_000_605_000L,
            gpsSpeedMps = null, fusedSpeedMps = 20.0, distanceMeters = 50.0, latitude = null, longitude = null,
            gpsAccuracyMeters = null, altitudeMeters = null, longitudinalAccelerationMps2 = 3.0f,
            lateralAccelerationMps2 = 0.2f, verticalAccelerationMps2 = 0.1f, jerkMps3 = 0.5f, noiseDbA = null
        )
        return ReadableExporter.LedgerData(
            vehicles = listOf(vehicle),
            mileageRecords = listOf(mileage),
            energyRecords = listOf(energy),
            maintenanceItems = listOf(item),
            maintenanceHistory = listOf(history),
            performanceSessions = listOf(session),
            fitActivities = listOf(activity),
            fitSamples = listOf(sample),
            performanceSamples = listOf(perfSample)
        )
    }

    private fun roundTrip(
        data: ReadableExporter.LedgerData,
        mutateManifest: (BackupPackage.BusinessManifest) -> BackupPackage.BusinessManifest = { it }
    ): PackageImporter.Result {
        val volumes = ReadableExporter.exportVolumes(data)
        // 逐卷读回并合并（模拟导入侧：分卷各自是一份 xls）
        val sheets = volumes.flatMap { volume -> XlsSheetReader.read(volume.bytes) }
        val manifest = mutateManifest(
            BackupPackage.BusinessManifest(
                format = BackupPackage.FORMAT,
                formatVersion = BackupPackage.FORMAT_VERSION,
                scope = BackupPackage.SCOPE_BUSINESS,
                createdAt = "2026-09-21T10:00:00+08:00",
                appVersion = "v0.6.4",
                dbSchema = dbSchema,
                data = emptyList(),
                counts = ReadableExporter.countsOf(data)
            )
        )
        return PackageImporter.parse(sheets, manifest, dbSchema)
    }

    @Test
    fun roundTripKeepsRowCounts() {
        val data = sampleData()
        val result = roundTrip(data)
        assertTrue("应解析成功，实际 $result", result is PackageImporter.Result.Ok)
        val out = (result as PackageImporter.Result.Ok).data

        assertEquals(data.vehicles.size, out.vehicles.size)
        assertEquals(data.mileageRecords.size, out.mileageRecords.size)
        assertEquals(data.energyRecords.size, out.energyRecords.size)
        assertEquals(data.maintenanceItems.size, out.maintenanceItems.size)
        assertEquals(data.maintenanceHistory.size, out.maintenanceHistory.size)
        assertEquals(data.fitActivities.size, out.fitActivities.size)
        assertEquals(data.fitSamples.size, out.fitSamples.size)
        assertEquals(data.performanceSessions.size, out.performanceSessions.size)
        assertEquals(data.performanceSamples.size, out.performanceSamples.size)
    }

    @Test
    fun roundTripKeepsKeyFields() {
        val data = sampleData()
        val out = (roundTrip(data) as PackageImporter.Result.Ok).data

        val vehicle = out.vehicles.single()
        assertEquals("v1", vehicle.id)
        assertEquals("测试车", vehicle.name)
        assertEquals(VehicleCategory.Car, vehicle.category)
        assertEquals(EnergyType.Electric, vehicle.energyType)
        assertEquals(12345.6, vehicle.currentMileage, 1e-6)
        assertEquals(60.0, vehicle.batteryCapacityKwh!!, 1e-6)
        assertEquals(90, vehicle.defaultChargeLimitSoc)
        assertEquals(250000.0, vehicle.purchasePrice, 1e-6)

        val energy = out.energyRecords.single()
        assertEquals(EnergyRecordType.Charge, energy.type)
        assertTrue("是否满充必须还原成 true", energy.isFull)
        assertEquals(0.3, energy.electricityPrice, 1e-6)
        assertEquals(90, energy.chargeLimitSocSnapshot)

        val item = out.maintenanceItems.single()
        assertEquals(MaintenanceItemType.Periodic, item.itemType)
        assertEquals("P-1", item.lastPartNumber)

        val history = out.maintenanceHistory.single()
        assertEquals(200.0, history.laborCost, 1e-6)
        assertEquals(300.0, history.partsCost, 1e-6)

        val session = out.performanceSessions.single()
        assertEquals(PerformanceTestType.Acceleration100, session.testType)
        assertEquals(PerformanceStatus.Completed, session.status)
        assertEquals(5.6, session.resultSeconds!!, 1e-6)

        val sample = out.fitSamples.single()
        assertEquals("a1", sample.activityId)
        assertEquals(120, sample.heartRateBpm)
        assertEquals(3.0f, sample.accuracyMeters, 1e-4f)
    }

    @Test
    fun roundTripTimeIsSecondPrecision() {
        val data = sampleData()
        val out = (roundTrip(data) as PackageImporter.Result.Ok).data
        val original = data.mileageRecords.single().createdAt
        val restored = out.mileageRecords.single().createdAt
        assertEquals("秒级必须相等", original / 1000, restored / 1000)
    }

    @Test
    fun derivedSheetsAreIgnoredNotImported() {
        val data = sampleData()
        val out = (roundTrip(data) as PackageImporter.Result.Ok)
        assertTrue(
            "派生表不应出现在 data 里（它们由 importData 末尾重算）",
            out.data.energyRecords.isNotEmpty()
        )
        assertEquals(0, out.warnings.size)
    }

    @Test
    fun countsMismatchIsRejected() {
        val data = sampleData()
        val result = roundTrip(data) { manifest ->
            manifest.copy(counts = manifest.counts + ("vehicles" to 2))
        }
        assertTrue(result is PackageImporter.Result.Failed)
        assertTrue(
            "错误信息应指出 vehicles 行数不符：$result",
            (result as PackageImporter.Result.Failed).reason.contains("vehicles")
        )
    }

    @Test
    fun newerDbSchemaIsRejected() {
        val data = sampleData()
        val result = roundTrip(data) { manifest -> manifest.copy(dbSchema = dbSchema + 1) }
        assertTrue(result is PackageImporter.Result.Failed)
        assertTrue((result as PackageImporter.Result.Failed).reason.contains("升级"))
    }

    @Test
    fun missingRequiredColumnIsRejected() {
        val data = sampleData()
        val volumes = ReadableExporter.exportVolumes(data)
        val sheets = volumes
            .flatMap { XlsSheetReader.read(it.bytes) }
            .map { sheet ->
                if (sheet.name == "车辆") sheet.copy(headers = sheet.headers.filter { it != "能源类型" })
                else sheet
            }
        val manifest = BackupPackage.BusinessManifest(
            format = BackupPackage.FORMAT,
            formatVersion = BackupPackage.FORMAT_VERSION,
            scope = BackupPackage.SCOPE_BUSINESS,
            createdAt = "2026-09-21T10:00:00+08:00",
            appVersion = "v0.6.4",
            dbSchema = dbSchema,
            data = emptyList(),
            counts = ReadableExporter.countsOf(data)
        )
        val result = PackageImporter.parse(sheets, manifest, dbSchema)
        assertTrue("缺列必须拒，实际 $result", result is PackageImporter.Result.Failed)
        assertTrue(
            (result as PackageImporter.Result.Failed).reason.contains("能源类型")
        )
    }

    @Test
    fun danglingForeignKeyIsRejected() {
        val data = sampleData().copy(
            mileageRecords = listOf(sampleData().mileageRecords.single().copy(vehicleId = "ghost"))
        )
        val result = roundTrip(data)
        assertTrue("外键悬空必须拒，实际 $result", result is PackageImporter.Result.Failed)
        assertTrue((result as PackageImporter.Result.Failed).reason.contains("引用不完整"))
    }

    @Test
    fun mergeSlicesPutsSplitSheetsBackTogether() {
        val sheets = listOf(
            XlsSheetReader.SheetData("fit_samples.p3", listOf("活动ID", "时间"), listOf(listOf("a", "3"))),
            XlsSheetReader.SheetData("fit_samples", listOf("活动ID", "时间"), listOf(listOf("a", "1"))),
            XlsSheetReader.SheetData("fit_samples.p2", listOf("活动ID", "时间"), listOf(listOf("a", "2")))
        )
        val merged = PackageImporter.mergeSlices(sheets)
        assertEquals(1, merged.size)
        assertEquals("fit_samples", merged.single().name)
        assertEquals(listOf("1", "2", "3"), merged.single().rows.map { it[1] })
    }
}
