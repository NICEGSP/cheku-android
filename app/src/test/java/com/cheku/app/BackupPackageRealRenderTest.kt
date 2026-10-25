package com.cheku.app

import com.cheku.app.data.EnergyType
import com.cheku.app.data.MileageRecordEntity
import com.cheku.app.data.TransmissionType
import com.cheku.app.data.VehicleCategory
import com.cheku.app.data.VehicleEntity
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/** 回归 */
class BackupPackageRealRenderTest {
    private val stampMillis = 1_790_000_000_000L

    @Test
    fun realRenderedPackagePassesStripAndValidate() {
        val scratch = File(System.getProperty("java.io.tmpdir"), "cheku-real-render-${System.nanoTime()}")
            .apply { mkdirs() }
        try {
            val vehicle = VehicleEntity(
                id = "v-real-1",
                name = "验证用车",
                model = "MODEL-X",
                plate = "京A12345",
                category = VehicleCategory.Car,
                energyType = EnergyType.Fuel,
                transmissionType = TransmissionType.Manual,
                fuelTankCapacityLiters = 50.0,
                currentMileage = 12_345.0,
                updatedAt = stampMillis,
            )
            val data = ReadableExporter.LedgerData(
                vehicles = listOf(vehicle),
                mileageRecords = listOf(
                    MileageRecordEntity(
                        id = "m-real-1", vehicleId = vehicle.id,
                        mileage = 12_000.0, note = "首保里程", createdAt = stampMillis - 86_400_000L,
                    ),
                    MileageRecordEntity(
                        id = "m-real-2", vehicleId = vehicle.id,
                        mileage = 12_345.0, note = "当前里程", createdAt = stampMillis,
                    ),
                ),
            )
            val plan = ReadableExporter.planVolumesFor(data)

            val exported = ByteArrayOutputStream().use { out ->
                BackupPackage.writeExportPackageStreaming(
                    out = out,
                    atMillis = stampMillis,
                    appVersion = "v0.6.4",
                    dbSchema = 15,
                    counts = ReadableExporter.countsOf(data),
                    scratchDir = scratch,
                    plan = plan,
                    renderVolume = { index, sink -> ReadableExporter.renderVolume(data, plan[index], sink) },
                    writeCerts = null,
                )
                out.toByteArray()
            }
            assertTrue("导出包不该为空", exported.isNotEmpty())

            val stripped = ByteArrayOutputStream().use { out ->
                BackupPackage.stripCertificates(ByteArrayInputStream(exported), out)
                out.toByteArray()
            }
            val validation = BackupPackage.validateUploadPackage(ByteArrayInputStream(stripped))

            if (validation !is BackupPackage.Validation.Ok) {
                val declared = LinkedHashMap<String, Pair<Long, String>>()
                val measured = LinkedHashMap<String, Pair<Long, String>>()
                var manifestJson: String? = null
                java.util.zip.ZipInputStream(ByteArrayInputStream(stripped)).use { zip ->
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        if (entry.name == BackupPackage.MANIFEST_ENTRY) {
                            manifestJson = zip.readBytes().toString(Charsets.UTF_8)
                        } else if (entry.name.startsWith("data/")) {
                            val digest = java.security.MessageDigest.getInstance("SHA-256")
                            var count = 0L
                            val buffer = ByteArray(8192)
                            while (true) {
                                val read = zip.read(buffer)
                                if (read < 0) break
                                digest.update(buffer, 0, read)
                                count += read
                            }
                            measured[entry.name] =
                                count to digest.digest().joinToString("") { "%02x".format(it) }
                        }
                    }
                }
                manifestJson?.let { text ->
                    BackupPackage.businessManifestFromJson(text)?.data?.forEach { part ->
                        declared[part.path] = part.bytes to part.sha256
                    }
                }
                val report = buildString {
                    append("判定=$validation\n")
                    declared.forEach { (path, d) ->
                        val m = measured[path]
                        append("声明 $path bytes=${d.first} sha=${d.second}\n")
                        append("实测 $path bytes=${m?.first} sha=${m?.second}\n")
                    }
                    append("包内条目=${measured.keys}\n")
                }
                throw AssertionError("真实渲染的导出包未能通过自检：\n$report")
            }
        } finally {
            scratch.deleteRecursively()
        }
    }
}
