package com.cheku.app

import com.cheku.app.data.FitSampleEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadableExporterScaleTest {
    private val sampleCount = 130_000

    private fun bigFitSamples(): List<FitSampleEntity> = (0 until sampleCount).map { index ->
        FitSampleEntity(
            activityId = "a1",
            timestamp = 1_774_000_000_000L + index * 1_000L,
            latitude = 31.0 + index * 1e-6,
            longitude = 121.0 + index * 1e-6,
            speedMps = 5.0, distanceMeters = index * 1.4, altitudeMeters = null,
            accuracyMeters = 3.0f, bearingDegrees = null, heartRateBpm = 120
        )
    }

    @Test
    fun volumePlanSplitsAtSheetLimitAndPacksAtVolumeLimit() {
        val plan = BackupPackage.planVolumes(
            listOf(BackupPackage.TableRows("fit_samples", sampleCount))
        )
        assertEquals("应切成 2 段", 2, plan.size)
        assertEquals(listOf("fit_samples"), plan[0].sheetNames)
        assertEquals(65_535, plan[0].rows)
        assertEquals(listOf("fit_samples.p2"), plan[1].sheetNames)
        assertEquals(sampleCount - 65_535, plan[1].rows)
        plan.forEach { assertTrue("单卷不得超过护栏：${it.rows}", it.rows <= BackupPackage.MAX_ROWS_PER_VOLUME) }
    }

    @Test
    fun exportingAndReadingBackAtScaleKeepsEveryRow() {
        val data = ReadableExporter.LedgerData(
            vehicles = listOf(
                com.cheku.app.data.VehicleEntity(
                    id = "v1", name = "规模化测试车", model = "M3", plate = "沪A00002",
                    category = com.cheku.app.data.VehicleCategory.Car,
                    energyType = com.cheku.app.data.EnergyType.Electric,
                    transmissionType = com.cheku.app.data.TransmissionType.Automatic,
                    currentMileage = 200_000.0, updatedAt = 1_774_000_000_000L
                )
            ),
            fitActivities = listOf(
                com.cheku.app.data.FitActivityEntity(
                    id = "a1", vehicleId = "v1", name = "规模化轨迹",
                    status = com.cheku.app.data.FitActivityStatus.Completed,
                    startedAt = 1_774_000_000_000L, endedAt = 1_774_000_500_000L,
                    distanceMeters = 180_000.0, createdAt = 1_774_000_000_000L,
                    updatedAt = 1_774_000_000_000L
                )
            ),
            fitSamples = bigFitSamples()
        )

        val startedAt = System.currentTimeMillis()
        val volumes = ReadableExporter.exportVolumes(data)
        val exportMs = System.currentTimeMillis() - startedAt

        assertEquals("13 万行应产出 2 卷", 2, volumes.size)
        val totalBytes = volumes.sumOf { it.bytes.size.toLong() }
        println(
            "[分卷实测] $sampleCount 行 FIT 采样 → ${volumes.size} 卷 / " +
                "${totalBytes / 1024}KB / 渲染 ${exportMs}ms；单卷最高 ${volumes.maxOf { it.rows }} 行"
        )

        val sheets = volumes.flatMap { volume -> XlsSheetReader.read(volume.bytes) }
        val names = sheets.map { it.name }
        assertTrue("应含 fit_samples 主片：$names", names.contains("fit_samples"))
        assertTrue("应含 fit_samples 第二片（跨卷）：$names", names.contains("fit_samples.p2"))

        val merged = PackageImporter.mergeSlices(sheets)
        assertEquals("16 张表都应存在", 16, merged.size)
        assertEquals("fit_samples 不应再分成两张", 1, merged.count { it.name == "fit_samples" })
        val samples = merged.single { it.name == "fit_samples" }
        assertEquals("合并后行数必须与投入一致", sampleCount, samples.rows.size)

        val headers = samples.headers
        val timeColumn = headers.indexOf("时间")
        assertTrue("应有「时间」列", timeColumn >= 0)
        val firstRow = samples.rows.first()
        val lastRow = samples.rows.last()
        assertTrue(
            "首条应来自第 0 段，末条应来自第 2 段（顺序不能倒）",
            firstRow[timeColumn] < lastRow[timeColumn]
        )

        // 端到端再走一步：真解析成实体，行数一致
        val manifest = BackupPackage.BusinessManifest(
            format = BackupPackage.FORMAT,
            formatVersion = BackupPackage.FORMAT_VERSION,
            scope = BackupPackage.SCOPE_BUSINESS,
            createdAt = "2026-09-21T11:00:00+08:00",
            appVersion = "v0.6.4",
            dbSchema = 15,
            data = emptyList(),
            counts = ReadableExporter.countsOf(data)
        )
        val parsed = PackageImporter.parse(sheets, manifest, 15)
        assertTrue("应解析成功，实际 $parsed", parsed is PackageImporter.Result.Ok)
        assertEquals(sampleCount, (parsed as PackageImporter.Result.Ok).data.fitSamples.size)

        assertTrue("13 万行渲染不应超过 120s（实测 ${exportMs}ms）", exportMs < 120_000)
    }
}
