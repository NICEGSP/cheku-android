package com.cheku.app

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.cheku.app.data.AppDatabase
import com.cheku.app.data.EnergyRecordType
import com.cheku.app.data.EnergyType
import com.cheku.app.data.FitActivityEntity
import com.cheku.app.data.FitActivityStatus
import com.cheku.app.data.FitSampleEntity
import com.cheku.app.data.LedgerRepository
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
import com.cheku.app.data.EnergyRecordEntity
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PackageRoundTripRuntimeTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val database: AppDatabase =
        Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    private val repository = LedgerRepository(database.ledgerDao(), database.performanceDao()) { block ->
        database.withTransaction { block() }
    }

    private val atMillis = 1_774_000_000_000L

    private fun seed() = runBlocking {
        val dao = database.ledgerDao()
        dao.upsertVehicle(
            VehicleEntity(
                id = "v1", name = "运行时测试车", model = "M3", plate = "沪A00001",
                category = VehicleCategory.Car, energyType = EnergyType.Electric,
                transmissionType = TransmissionType.Automatic, batteryCapacityKwh = 60.0,
                defaultChargeLimitSoc = 90, currentMileage = 2222.0, updatedAt = atMillis,
                purchasePrice = 200000.0
            )
        )
        dao.insertMileageRecord(
            MileageRecordEntity(
                id = "m1", vehicleId = "v1", mileage = 2222.0, note = "备注",
                createdAt = atMillis, roadFeeAmount = 10.0
            )
        )
        dao.insertEnergyRecord(
            EnergyRecordEntity(
                id = "e1", vehicleId = "v1", type = EnergyRecordType.Charge, mileage = 2100.0,
                amount = 30.0, orderAmount = 32.0, paidAmount = 30.0, isFull = true,
                missedPrevious = false, chargeMethod = "家充", electricityPrice = 0.3,
                chargeLimitSocSnapshot = 90, createdAt = atMillis
            )
        )
        dao.upsertMaintenanceItem(
            MaintenanceItemEntity(
                id = "i1", vehicleId = "v1", name = "自定义保养项", intervalKm = 10000, intervalDays = 365,
                lastServiceMileage = 2000, lastServiceAt = atMillis, updatedAt = atMillis,
                itemType = MaintenanceItemType.Periodic, baselineAt = atMillis
            )
        )
        dao.insertMaintenanceHistory(
            MaintenanceHistoryEntity(
                id = "h1", vehicleId = "v1", itemId = "i1", itemName = "自定义保养项",
                serviceMileage = 2000.0, serviceAt = atMillis, note = "4S", laborCost = 100.0,
                partsCost = 200.0, recordName = "机油"
            )
        )
        dao.upsertFitActivity(
            FitActivityEntity(
                id = "a1", vehicleId = "v1", name = "通勤", status = FitActivityStatus.Completed,
                startedAt = atMillis, endedAt = atMillis + 60_000, distanceMeters = 1000.0,
                maxSpeedMps = 10.0, autoPauseEnabled = true, createdAt = atMillis, updatedAt = atMillis
            )
        )
        dao.insertFitSample(
            FitSampleEntity(
                activityId = "a1", timestamp = atMillis + 1_000, latitude = 31.2, longitude = 121.4,
                speedMps = 5.0, distanceMeters = 50.0, altitudeMeters = null, accuracyMeters = 3.0f,
                bearingDegrees = null, heartRateBpm = 110
            )
        )
        database.performanceDao()!!.apply {
            upsertSession(
                PerformanceSessionEntity(
                    id = "s1", vehicleId = "v1", name = "加速", testType = PerformanceTestType.Acceleration100,
                    status = PerformanceStatus.Completed, startedAt = atMillis, endedAt = atMillis + 6_000,
                    resultSeconds = 5.6, maxAccelerationMps2 = 8.0, averageAccelerationMps2 = 5.0,
                    quality = PerformanceQuality.Valid, createdAt = atMillis, updatedAt = atMillis
                )
            )
            insertSample(
                PerformanceSampleEntity(
                    sessionId = "s1", timestampNanos = 1_774_000_005_000_000_000L, wallTime = atMillis,
                    gpsSpeedMps = null, fusedSpeedMps = 20.0, distanceMeters = 50.0, latitude = null,
                    longitude = null, gpsAccuracyMeters = null, altitudeMeters = null,
                    longitudinalAccelerationMps2 = 3.0f, lateralAccelerationMps2 = 0.2f,
                    verticalAccelerationMps2 = 0.1f, jerkMps3 = 0.5f, noiseDbA = null
                )
            )
        }
    }

    private suspend fun readAll(): ReadableExporter.LedgerData = ReadableExporter.LedgerData(
        vehicles = repository.getAllVehicles(),
        mileageRecords = repository.getAllMileageRecords(),
        energyRecords = repository.getAllEnergyRecords(),
        energySettlements = repository.getAllEnergySettlements(),
        maintenanceItems = repository.getAllMaintenanceItems(),
        maintenanceHistory = repository.getAllMaintenanceHistory(),
        performanceSessions = repository.getAllPerformanceSessions(),
        fitActivities = repository.getAllFitActivities(),
        fitSamples = repository.getAllFitSamples(),
        fitEvents = repository.getAllFitEvents(),
        performanceSamples = repository.getAllPerformanceSamples(),
        performanceSplits = repository.getAllPerformanceSplits(),
        performanceNoise = repository.getAllPerformanceNoise(),
        performanceJerks = repository.getAllPerformanceJerks()
    )

    /** 全链路 */
    @Test
    fun roundTripThroughRoomKeepsRowsAndKeyFields() = runBlocking {
        seed()
        val before = readAll()

        val zip = ByteArrayOutputStream().use { out ->
            BackupPackage.writeExportPackage(
                out = out,
                atMillis = atMillis,
                appVersion = "v0.6.4-runtime-test",
                dbSchema = com.cheku.app.data.DB_SCHEMA_VERSION,
                counts = ReadableExporter.countsOf(before),
                volumes = ReadableExporter.exportVolumes(before),
                certsZip = null
            )
            out.toByteArray()
        }
        assertTrue("导出包不应为空", zip.isNotEmpty())

        val sheets = ArrayList<XlsSheetReader.SheetData>()
        var manifest: BackupPackage.BusinessManifest? = null
        java.util.zip.ZipInputStream(java.io.ByteArrayInputStream(zip)).use { zin ->
            while (true) {
                val entry = zin.nextEntry ?: break
                when {
                    entry.name == BackupPackage.MANIFEST_ENTRY ->
                        manifest = BackupPackage.businessManifestFromJson(zin.readBytes().toString(Charsets.UTF_8))

                    entry.name.startsWith(BackupPackage.DATA_DIR) && entry.name.endsWith(".xls") ->
                        sheets += XlsSheetReader.read(zin.readBytes())
                }
            }
        }
        val parsed = PackageImporter.parse(
            sheets = sheets,
            manifest = manifest!!,
            localDbSchema = com.cheku.app.data.DB_SCHEMA_VERSION
        )
        assertTrue("解析应当成功，实际 $parsed", parsed is PackageImporter.Result.Ok)
        val data = (parsed as PackageImporter.Result.Ok).data

        repository.importData(data)

        val after = readAll()
        assertEquals("车辆", before.vehicles.size, after.vehicles.size)
        assertEquals("里程", before.mileageRecords.size, after.mileageRecords.size)
        assertEquals("能源", before.energyRecords.size, after.energyRecords.size)
        assertTrue(
            "导入的保养项目应存在",
            after.maintenanceItems.any { it.id == "i1" }
        )
        assertTrue(
            "保养项目应 ≥ 导入前（默认模板会被补齐）：before=${before.maintenanceItems.size} after=${after.maintenanceItems.size}",
            after.maintenanceItems.size >= before.maintenanceItems.size
        )
        assertEquals("保养记录", before.maintenanceHistory.size, after.maintenanceHistory.size)
        assertEquals("轨迹活动", before.fitActivities.size, after.fitActivities.size)
        assertEquals("FIT 采样", before.fitSamples.size, after.fitSamples.size)
        assertEquals("性能会话", before.performanceSessions.size, after.performanceSessions.size)
        assertEquals("性能采样", before.performanceSamples.size, after.performanceSamples.size)

        val vehicle = after.vehicles.single()
        assertEquals("v1", vehicle.id)
        assertEquals("运行时测试车", vehicle.name)
        assertEquals(EnergyType.Electric, vehicle.energyType)
        assertEquals(VehicleCategory.Car, vehicle.category)
        assertEquals(200000.0, vehicle.purchasePrice, 1e-6)

        assertEquals(EnergyRecordType.Charge, after.energyRecords.single().type)
        assertTrue("是否满充必须还原", after.energyRecords.single().isFull)
        assertEquals(100.0, after.maintenanceHistory.single().laborCost, 1e-6)
        assertEquals(110, after.fitSamples.single().heartRateBpm)
        assertEquals(PerformanceTestType.Acceleration100, after.performanceSessions.single().testType)
    }
}
