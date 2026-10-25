package com.cheku.app

import android.annotation.SuppressLint
import com.cheku.app.data.EnergyRecordEntity
import com.cheku.app.data.EnergyRecordType
import com.cheku.app.data.MaintenanceHistoryEntity
import com.cheku.app.data.MaintenanceItemEntity
import com.cheku.app.data.MaintenanceItemType
import com.cheku.app.data.MileageRecordEntity
import com.cheku.app.data.VehicleCategory
import com.cheku.app.data.EnergyType
import com.cheku.app.data.TransmissionType
import com.cheku.app.data.VehicleEntity
import com.cheku.app.data.FitActivityEntity
import com.cheku.app.data.FitSampleEntity
import com.cheku.app.data.FitTimerEventEntity
import com.cheku.app.data.FitActivityStatus
import com.cheku.app.data.FitTimerEventType
import com.cheku.app.data.JerkSeverity
import com.cheku.app.data.PerformanceJerkEntity
import com.cheku.app.data.PerformanceNoiseEntity
import com.cheku.app.data.PerformanceQuality
import com.cheku.app.data.PerformanceSampleEntity
import com.cheku.app.data.PerformanceSessionEntity
import com.cheku.app.data.PerformanceSplitEntity
import com.cheku.app.data.PerformanceStatus
import com.cheku.app.data.PerformanceTestType
import com.cheku.app.data.supportsCharging
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.io.IOException
import java.io.Reader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.security.MessageDigest
import java.time.Instant
import java.time.OffsetDateTime
import java.security.SecureRandom
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import javax.crypto.spec.GCMParameterSpec
import java.util.Base64

data class ExportedData(
    val vehicles: List<VehicleEntity>,
    val mileageRecords: List<MileageRecordEntity>,
    val energyRecords: List<EnergyRecordEntity>,
    val maintenanceItems: List<MaintenanceItemEntity>,
    val maintenanceHistory: List<MaintenanceHistoryEntity>,
    val fitActivities: List<FitActivityEntity> = emptyList(),
    val fitSamples: List<FitSampleEntity> = emptyList(),
    val fitEvents: List<FitTimerEventEntity> = emptyList(),
    val performanceSessions: List<PerformanceSessionEntity> = emptyList(),
    val performanceSamples: List<PerformanceSampleEntity> = emptyList(),
    val performanceSplits: List<PerformanceSplitEntity> = emptyList(),
    val performanceNoise: List<PerformanceNoiseEntity> = emptyList(),
    val performanceJerks: List<PerformanceJerkEntity> = emptyList(),
    val certificates: List<CertificateEntry> = emptyList()
)

/** 备份包的种类 */
enum class CheBackupKind {
    /** 用车记录包 */
    Business,

    /** 人车证书包 */
    Archive,

    /** 旧版整合包 */
    LegacyBundle,
}

data class BackupManifest(
    val format: String,
    val version: Int,
    val createdAt: String,
    val appVersion: String,
    val dbSchema: Int,
    val archiveFile: String? = null,
    val archiveSha256: String? = null,
    val archiveBytes: Long? = null,
    val archiveEntries: Int? = null,
) {
    /** 是否声明了配套证书包 */
    val hasArchive: Boolean
        get() = !archiveFile.isNullOrBlank() && !archiveSha256.isNullOrBlank() && archiveBytes != null
}

data class ParsedBackup(
    val business: ExportedData,
    val manifest: BackupManifest?,
    val kind: CheBackupKind,
) {
    /** 证书条目 */
    val archive: List<CertificateEntry> get() = business.certificates
}

data class ArchiveDigest(val sha256: String, val bytes: Long, val entries: Int)

data class StagedCertificate(
    val key: String,
    val fileName: String,
    val mimeType: String,
    val kind: String,
    val bytes: Long,
)

data class StagedArchive(
    val manifest: BackupManifest?,
    val certificates: List<StagedCertificate>,
    val failures: List<CertificateFailure>,
)

/** 流式读证书包时的落点 */
fun interface CertificateStagingSink {
    /** 返回写入目标 */
    fun openFor(key: String, fileName: String, mimeType: String, kind: String): OutputStream
}

internal const val MANIFEST_FORMAT_BUSINESS = "che"
internal const val MANIFEST_FORMAT_ARCHIVE = "che-docs"
internal const val MANIFEST_VERSION_BUSINESS = 4
internal const val MANIFEST_VERSION_ARCHIVE = 1

object DataExporter {
    private const val LEGACY_READ_KEY = "Ch3Ch3ngZh1!99!@"
    private const val KEY_ALGORITHM = "AES"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val LEGACY_TRANSFORMATION = "AES/ECB/PKCS5Padding"
    private const val IO_BUFFER = 64 * 1024

    private const val EXPORT_KDF_ID: Byte = 1
    internal const val EXPORT_KDF_ITERATIONS = 210_000
    private const val MIN_KDF_ITERATIONS = 10_000
    private const val MAX_KDF_ITERATIONS = 5_000_000
    private const val V5_SALT_BYTES = 16
    private const val KEY_BITS = 256
    private const val KDF_ALGORITHM = "PBKDF2WithHmacSHA256"

    private const val CHUNK_BYTES = 1 shl 20
    private const val GCM_TAG_BYTES = 16
    private const val NONCE_BASE_BYTES = 8

    private const val MAX_INFLATE_BYTES = 250L * 1024L * 1024L

    /** 容器 v3 */
    private val MAGIC_V3 = byteArrayOf('C'.code.toByte(), 'H'.code.toByte(), 'E'.code.toByte(), 3)

    /** 容器 v4 */
    private val MAGIC_V4 = byteArrayOf('C'.code.toByte(), 'H'.code.toByte(), 'E'.code.toByte(), 4)

    /** 容器 v5 */
    private val MAGIC_V5 = byteArrayOf('C'.code.toByte(), 'H'.code.toByte(), 'E'.code.toByte(), 5)

    /** v5 包头长度 */
    private const val V5_HEADER_BYTES = 4 + 1 + 4 + V5_SALT_BYTES + NONCE_BASE_BYTES

    fun exportData(
        passphrase: CharArray,
        vehicles: List<VehicleEntity>,
        mileageRecords: List<MileageRecordEntity>,
        energyRecords: List<EnergyRecordEntity>,
        maintenanceItems: List<MaintenanceItemEntity>,
        maintenanceHistory: List<MaintenanceHistoryEntity>,
        fitActivities: List<FitActivityEntity> = emptyList(),
        fitSamples: List<FitSampleEntity> = emptyList(),
        fitEvents: List<FitTimerEventEntity> = emptyList(),
        performanceSessions: List<PerformanceSessionEntity> = emptyList(),
        performanceSamples: List<PerformanceSampleEntity> = emptyList(),
        performanceSplits: List<PerformanceSplitEntity> = emptyList(),
        performanceNoise: List<PerformanceNoiseEntity> = emptyList(),
        performanceJerks: List<PerformanceJerkEntity> = emptyList(),
        appVersion: String = "",
        dbSchema: Int = 0,
        archiveFile: String? = null,
        archiveSha256: String? = null,
        archiveBytes: Long? = null,
        archiveEntries: Int? = null
    ): ByteArray = ByteArrayOutputStream().use { out ->
        exportDataTo(
            passphrase,
            out, vehicles, mileageRecords, energyRecords, maintenanceItems, maintenanceHistory,
            fitActivities, fitSamples, fitEvents, performanceSessions, performanceSamples,
            performanceSplits, performanceNoise, performanceJerks, appVersion, dbSchema,
            archiveFile, archiveSha256, archiveBytes, archiveEntries
        )
        out.toByteArray()
    }

    /** 流式导出 */
    fun exportDataTo(
        passphrase: CharArray,
        out: OutputStream,
        vehicles: List<VehicleEntity>,
        mileageRecords: List<MileageRecordEntity>,
        energyRecords: List<EnergyRecordEntity>,
        maintenanceItems: List<MaintenanceItemEntity>,
        maintenanceHistory: List<MaintenanceHistoryEntity>,
        fitActivities: List<FitActivityEntity> = emptyList(),
        fitSamples: List<FitSampleEntity> = emptyList(),
        fitEvents: List<FitTimerEventEntity> = emptyList(),
        performanceSessions: List<PerformanceSessionEntity> = emptyList(),
        performanceSamples: List<PerformanceSampleEntity> = emptyList(),
        performanceSplits: List<PerformanceSplitEntity> = emptyList(),
        performanceNoise: List<PerformanceNoiseEntity> = emptyList(),
        performanceJerks: List<PerformanceJerkEntity> = emptyList(),
        appVersion: String = "",
        dbSchema: Int = 0,
        archiveFile: String? = null,
        archiveSha256: String? = null,
        archiveBytes: Long? = null,
        archiveEntries: Int? = null
    ) {
        writePackage(out, passphrase) { writer ->
            writeCsv(
                writer, vehicles, mileageRecords, energyRecords, maintenanceItems, maintenanceHistory,
                fitActivities, fitSamples, fitEvents, performanceSessions, performanceSamples,
                performanceSplits, performanceNoise, performanceJerks, appVersion, dbSchema,
                archiveFile, archiveSha256, archiveBytes, archiveEntries
            )
        }
    }

    class CertificateSource(
        val key: String,
        val fileName: String,
        val mimeType: String,
        val kind: String,
        val open: () -> InputStream,
    )

    fun CertificateEntry.asCertificateSource(): CertificateSource =
        CertificateSource(key, fileName, mimeType, kind) { data.inputStream() }

    fun exportArchive(
        passphrase: CharArray,
        certificates: List<CertificateEntry>,
        appVersion: String = "",
        dbSchema: Int = 0
    ): ByteArray = ByteArrayOutputStream().use { out ->
        exportArchiveTo(passphrase, out, certificates, appVersion, dbSchema)
        out.toByteArray()
    }

    fun exportArchiveTo(
        passphrase: CharArray,
        out: OutputStream,
        sources: Sequence<CertificateSource>,
        appVersion: String = "",
        dbSchema: Int = 0
    ): ArchiveDigest {
        val digest = MessageDigest.getInstance("SHA-256")
        val counting = DigestCountingOutputStream(out, digest)
        var entries = 0
        writePackage(counting, passphrase) { writer ->
            writeManifest(writer, MANIFEST_FORMAT_ARCHIVE, MANIFEST_VERSION_ARCHIVE, appVersion, dbSchema)
            writer.appendLine("=== DOCUMENT ARCHIVE ===")
            writer.appendLine("key,fileName,mimeType,kind,base64")
            sources.forEach { source ->
                writer.append(csvEscape(source.key)).append(',')
                writer.append(csvEscape(source.fileName)).append(',')
                writer.append(csvEscape(source.mimeType)).append(',')
                writer.append(csvEscape(source.kind)).append(',')
                source.open().use { appendBase64(writer, it) }
                writer.appendLine()
                entries++
            }
        }
        counting.flush()
        return ArchiveDigest(toHex(digest.digest()), counting.count, entries)
    }

    /** 已在内存里的条目版本 */
    fun exportArchiveTo(
        passphrase: CharArray,
        out: OutputStream,
        certificates: List<CertificateEntry>,
        appVersion: String = "",
        dbSchema: Int = 0
    ): ArchiveDigest = exportArchiveTo(
        passphrase,
        out,
        certificates.asSequence().map { it.asCertificateSource() },
        appVersion,
        dbSchema
    )

    private fun writeManifest(
        out: Appendable,
        format: String,
        version: Int,
        appVersion: String,
        dbSchema: Int,
        archiveFile: String? = null,
        archiveSha256: String? = null,
        archiveBytes: Long? = null,
        archiveEntries: Int? = null
    ) {
        out.appendLine("=== MANIFEST ===")
        out.appendLine("format,${csvEscape(format)}")
        out.appendLine("version,$version")
        out.appendLine("createdAt,${OffsetDateTime.now()}")
        out.appendLine("appVersion,${csvEscape(appVersion)}")
        out.appendLine("dbSchema,$dbSchema")
        if (archiveFile != null && archiveSha256 != null && archiveBytes != null) {
            out.appendLine("archiveFile,${csvEscape(archiveFile)}")
            out.appendLine("archiveSha256,$archiveSha256")
            out.appendLine("archiveBytes,$archiveBytes")
            if (archiveEntries != null) out.appendLine("archiveEntries,$archiveEntries")
        }
    }

    /** 容器包装 */
    private fun writePackage(out: OutputStream, passphrase: CharArray, body: (Appendable) -> Unit) {
        require(passphrase.isNotEmpty()) {
            "导出备份必须提供非空口令：内置常量加密已废弃（见 MAGIC_V5 说明）"
        }
        val salt = ByteArray(V5_SALT_BYTES).also { SecureRandom().nextBytes(it) }
        val nonceBase = ByteArray(NONCE_BASE_BYTES).also { SecureRandom().nextBytes(it) }
        val key = deriveKey(passphrase, salt, EXPORT_KDF_ITERATIONS)
        out.write(MAGIC_V5)
        out.write(EXPORT_KDF_ID.toInt())
        out.write(int32(EXPORT_KDF_ITERATIONS))
        out.write(salt)
        out.write(nonceBase)
        val chunker = ChunkedGcmOutputStream(out, nonceBase, key)
        val gzip = GZIPOutputStream(chunker, IO_BUFFER)
        val writer = OutputStreamWriter(gzip, Charsets.UTF_8)
        body(writer)
        writer.flush()
        gzip.finish()
        chunker.finish()
        out.flush()
    }

    private fun deriveKey(passphrase: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        require(iterations in MIN_KDF_ITERATIONS..MAX_KDF_ITERATIONS) {
            "备份包声明的 KDF 迭代次数超出允许范围：$iterations"
        }
        val spec = PBEKeySpec(passphrase, salt, iterations, KEY_BITS)
        return try {
            SecretKeySpec(SecretKeyFactory.getInstance(KDF_ALGORITHM).generateSecret(spec).encoded, KEY_ALGORITHM)
        } finally {
            spec.clearPassword()
        }
    }

    /** 大端 int32 字节 */
    private fun int32(value: Int): ByteArray = byteArrayOf(
        (value ushr 24).toByte(), (value ushr 16).toByte(),
        (value ushr 8).toByte(), value.toByte()
    )

    private fun writeCsv(
        out: Appendable,
        vehicles: List<VehicleEntity>,
        mileageRecords: List<MileageRecordEntity>,
        energyRecords: List<EnergyRecordEntity>,
        maintenanceItems: List<MaintenanceItemEntity>,
        maintenanceHistory: List<MaintenanceHistoryEntity>,
        fitActivities: List<FitActivityEntity> = emptyList(),
        fitSamples: List<FitSampleEntity> = emptyList(),
        fitEvents: List<FitTimerEventEntity> = emptyList(),
        performanceSessions: List<PerformanceSessionEntity> = emptyList(),
        performanceSamples: List<PerformanceSampleEntity> = emptyList(),
        performanceSplits: List<PerformanceSplitEntity> = emptyList(),
        performanceNoise: List<PerformanceNoiseEntity> = emptyList(),
        performanceJerks: List<PerformanceJerkEntity> = emptyList(),
        appVersion: String = "",
        dbSchema: Int = 0,
        archiveFile: String? = null,
        archiveSha256: String? = null,
        archiveBytes: Long? = null,
        archiveEntries: Int? = null
    ) {
            // MANIFEST 放在最前：解析时先读到它，才知道要不要去找配套的证书包。
            writeManifest(
                out, MANIFEST_FORMAT_BUSINESS, MANIFEST_VERSION_BUSINESS, appVersion, dbSchema,
                archiveFile, archiveSha256, archiveBytes, archiveEntries
            )
            out.appendLine("=== VEHICLES ===")
            out.appendLine("id,name,model,plate,category,energyType,transmissionType,fuelTankCapacity,batteryCapacity,defaultChargeLimitSoc,currentMileage,updatedAt,manufacturingDate,purchasePrice,purchaseTax")
            vehicles.forEach { v ->
                out.appendLine("${v.id},${csvEscape(v.name)},${csvEscape(v.model)},${csvEscape(v.plate)},${v.category.name},${v.energyType.name},${v.transmissionType.name},${v.fuelTankCapacityLiters ?: ""},${v.batteryCapacityKwh ?: ""},${v.defaultChargeLimitSoc ?: ""},${v.currentMileage},${v.updatedAt},${v.manufacturingDate ?: ""},${v.purchasePrice},${v.purchaseTax}")
            }
            out.appendLine("=== MILEAGE ===")
            out.appendLine("id,vehicleId,mileage,note,roadFeeAmount,createdAt")
            mileageRecords.forEach { r ->
                out.appendLine("${r.id},${r.vehicleId},${r.mileage},${csvEscape(r.note)},${r.roadFeeAmount},${r.createdAt}")
            }
            out.appendLine("=== ENERGY ===")
            out.appendLine("id,vehicleId,type,mileage,amount,orderAmount,paidAmount,isFull,missedPrevious,fuelGrade,unitPrice,chargeMethod,electricityPrice,serviceFee,chargeLimitSoc,note,createdAt")
            energyRecords.forEach { r ->
                out.appendLine("${r.id},${r.vehicleId},${r.type.name},${r.mileage},${r.amount},${r.orderAmount},${r.paidAmount},${r.isFull},${r.missedPrevious},${csvEscape(r.fuelGrade)},${r.unitPrice},${csvEscape(r.chargeMethod)},${r.electricityPrice},${r.serviceFee},${r.chargeLimitSocSnapshot ?: ""},${csvEscape(r.note)},${r.createdAt}")
            }
            out.appendLine("=== MAINTENANCE ITEMS ===")
            out.appendLine("id,vehicleId,name,intervalKm,intervalDays,lastServiceMileage,lastServiceAt,updatedAt,lastPartNumber,itemType,baselineAt")
            maintenanceItems.forEach { m ->
                out.appendLine("${m.id},${m.vehicleId},${csvEscape(m.name)},${m.intervalKm},${m.intervalDays},${m.lastServiceMileage},${m.lastServiceAt},${m.updatedAt},${csvEscape(m.lastPartNumber)},${m.itemType.name},${m.baselineAt}")
            }
            out.appendLine("=== MAINTENANCE HISTORY ===")
            out.appendLine("id,vehicleId,itemId,itemName,serviceMileage,serviceAt,note,laborCost,partsCost,partNumber,recordName,insuranceAmount,repairAmount,expiresAt")
            maintenanceHistory.forEach { h ->
                out.appendLine("${h.id},${h.vehicleId},${h.itemId},${csvEscape(h.itemName)},${h.serviceMileage},${h.serviceAt},${csvEscape(h.note)},${h.laborCost},${h.partsCost},${csvEscape(h.partNumber)},${csvEscape(h.recordName)},${h.insuranceAmount},${h.repairAmount},${h.expiresAt ?: ""}")
            }
            out.appendLine("=== FIT ACTIVITIES ===")
            out.appendLine("id,vehicleId,name,status,startedAt,endedAt,pausedDurationMs,distanceMeters,maxSpeedMps,autoPause,lastSampleAt,exportedAt,createdAt,updatedAt")
            fitActivities.forEach { a -> out.appendLine("${a.id},${a.vehicleId},${csvEscape(a.name)},${a.status.name},${a.startedAt},${a.endedAt ?: ""},${a.pausedDurationMs},${a.distanceMeters},${a.maxSpeedMps},${a.autoPauseEnabled},${a.lastSampleAt ?: ""},${a.exportedAt ?: ""},${a.createdAt},${a.updatedAt}") }
            out.appendLine("=== FIT SAMPLES ===")
            out.appendLine("activityId,timestamp,lat,lon,speed,distance,altitude,accuracy,bearing,hr,cadence,power,motion")
            fitSamples.forEach { s -> out.appendLine("${s.activityId},${s.timestamp},${s.latitude},${s.longitude},${s.speedMps},${s.distanceMeters},${s.altitudeMeters ?: ""},${s.accuracyMeters},${s.bearingDegrees ?: ""},${s.heartRateBpm ?: ""},${s.cadenceRpm ?: ""},${s.powerWatts ?: ""},${s.motionMagnitude ?: ""}") }
            out.appendLine("=== FIT EVENTS ===")
            out.appendLine("activityId,timestamp,type")
            fitEvents.forEach { e -> out.appendLine("${e.activityId},${e.timestamp},${e.type.name}") }
            out.appendLine("=== PERFORMANCE SESSIONS ===")
            out.appendLine("id,vehicleId,name,testType,status,startedAt,endedAt,resultSeconds,resultDistance,maxAcceleration,averageAcceleration,averageNoise,maxNoise,quality,invalidReason,microphoneCalibrated,createdAt,updatedAt")
            performanceSessions.forEach { s -> out.appendLine("${s.id},${s.vehicleId.orEmpty()},${csvEscape(s.name)},${s.testType.name},${s.status.name},${s.startedAt},${s.endedAt ?: ""},${s.resultSeconds ?: ""},${s.resultDistanceMeters ?: ""},${s.maxAccelerationMps2},${s.averageAccelerationMps2},${s.averageNoiseDbA ?: ""},${s.maxNoiseDbA ?: ""},${s.quality.name},${csvEscape(s.invalidReason)},${s.microphoneCalibrated},${s.createdAt},${s.updatedAt}") }
            out.appendLine("=== PERFORMANCE SAMPLES ===")
            out.appendLine("sessionId,timestampNanos,wallTime,gpsSpeed,fusedSpeed,distance,latitude,longitude,accuracy,altitude,longitudinal,lateral,vertical,jerk,noise")
            performanceSamples.forEach { s -> out.appendLine("${s.sessionId},${s.timestampNanos},${s.wallTime},${s.gpsSpeedMps ?: ""},${s.fusedSpeedMps},${s.distanceMeters},${s.latitude ?: ""},${s.longitude ?: ""},${s.gpsAccuracyMeters ?: ""},${s.altitudeMeters ?: ""},${s.longitudinalAccelerationMps2},${s.lateralAccelerationMps2},${s.verticalAccelerationMps2},${s.jerkMps3},${s.noiseDbA ?: ""}") }
            out.appendLine("=== PERFORMANCE SPLITS ===")
            out.appendLine("id,sessionId,label,speed,seconds,distance")
            performanceSplits.forEach { s -> out.appendLine("${s.id},${s.sessionId},${csvEscape(s.label)},${s.speedKmh},${s.elapsedSeconds},${s.distanceMeters}") }
            out.appendLine("=== PERFORMANCE NOISE ===")
            out.appendLine("sessionId,timestamp,value")
            performanceNoise.forEach { n -> out.appendLine("${n.sessionId},${n.timestamp},${n.dbA}") }
            out.appendLine("=== PERFORMANCE JERKS ===")
            out.appendLine("id,sessionId,timestamp,speed,acceleration,jerk,duration,severity,manual")
            performanceJerks.forEach { j -> out.appendLine("${j.id},${j.sessionId},${j.timestamp},${j.speedKmh},${j.accelerationPeakMps2},${j.jerkPeakMps3},${j.durationMs},${j.severity.name},${j.manual}") }
    }

    /** 流式解析 */
    fun parseBackup(passphrase: CharArray?, input: InputStream, inflateLimit: Long = MAX_INFLATE_BYTES): ParsedBackup? = try {
        InputStreamReader(openBackup(input, inflateLimit, passphrase), Charsets.UTF_8).use { reader ->
            parseCsv(reader).also { validate(it.business) }
        }
    } catch (error: Exception) {
        null
    }

    /** 完整解析 */
    fun importData(passphrase: CharArray?, data: ByteArray, inflateLimit: Long = MAX_INFLATE_BYTES): ParsedBackup? =
        parseBackup(passphrase, ByteArrayInputStream(data), inflateLimit)

    fun importBusiness(passphrase: CharArray?, input: InputStream, inflateLimit: Long = MAX_INFLATE_BYTES): ExportedData? =
        parseBackup(passphrase, input, inflateLimit)
            ?.takeIf { it.kind != CheBackupKind.Archive }
            ?.business

    fun importArchive(passphrase: CharArray?, input: InputStream, inflateLimit: Long = MAX_INFLATE_BYTES): List<CertificateEntry>? =
        parseBackup(passphrase, input, inflateLimit)?.takeIf { it.archive.isNotEmpty() }?.archive

    fun sniffBackupKind(passphrase: CharArray?, input: InputStream, inflateLimit: Long = MAX_INFLATE_BYTES): CheBackupKind? = try {
        InputStreamReader(openBackup(input, inflateLimit, passphrase), Charsets.UTF_8).use { reader ->
            val records = CsvRecordReader(reader)
            var format: String? = null
            var sawCertificateSection = false
            while (true) {
                val line = records.next() ?: break
                if (line.isBlank()) continue
                if (line == "=== DOCUMENT ARCHIVE ===") {
                    sawCertificateSection = true
                    break
                }
                if (line.startsWith("=== ")) continue
                val cols = parseCsvLine(line)
                if (cols.size >= 2 && cols[0] == "format") {
                    format = cols[1]
                    break
                }
            }
            when {
                format == MANIFEST_FORMAT_ARCHIVE -> CheBackupKind.Archive
                format != null -> CheBackupKind.Business
                sawCertificateSection -> CheBackupKind.LegacyBundle
                else -> CheBackupKind.Business
            }
        }
    } catch (error: Exception) {
        null
    }

    fun parseArchiveStaged(
        passphrase: CharArray?,
        input: InputStream,
        sink: CertificateStagingSink,
        inflateLimit: Long = MAX_INFLATE_BYTES
    ): StagedArchive? = try {
        InputStreamReader(openBackup(input, inflateLimit, passphrase), Charsets.UTF_8).use { reader ->
            val manifestFields = LinkedHashMap<String, String>()
            val certificates = mutableListOf<StagedCertificate>()
            val failures = mutableListOf<CertificateFailure>()
            val records = CsvRecordReader(reader)
            var section = ""
            var expectHeader = false
            while (true) {
                val line = records.next() ?: break
                if (line.isBlank()) continue
                if (line.startsWith("=== ") && line.endsWith(" ===")) {
                    // 只有 `MANIFEST` 没有表头行，其余段都紧跟一行表头
                    expectHeader = line != "=== MANIFEST ==="
                    section = when (line) {
                        "=== MANIFEST ===" -> "manifest"
                        "=== DOCUMENT ARCHIVE ===" -> "certificates"
                        else -> ""
                    }
                    continue
                }
                if (expectHeader) {
                    expectHeader = false
                    continue
                }
                val cols = parseCsvLine(line)
                when (section) {
                    "manifest" -> if (cols.size >= 2) {
                        manifestFields[cols[0]] = cols.subList(1, cols.size).joinToString(",")
                    }
                    "certificates" -> if (cols.size >= 5) {
                        val staged = runCatching {
                            val encoded = cols[4].toByteArray(Charsets.ISO_8859_1)
                            val written = Base64.getDecoder().wrap(encoded.inputStream()).use { decoded ->
                                sink.openFor(cols[0], cols[1], cols[2], cols[3]).use { out ->
                                    decoded.copyTo(out)
                                }
                            }
                            StagedCertificate(cols[0], cols[1], cols[2], cols[3], written)
                        }.getOrNull()
                        if (staged == null) {
                            failures += CertificateFailure(cols[0], "证书段解不开（base64 损坏）")
                        } else {
                            certificates += staged
                        }
                    }
                }
            }
            StagedArchive(manifestFrom(manifestFields), certificates, failures)
        }
    } catch (error: Exception) {
        null
    }

    private fun legacyReadKey() = SecretKeySpec(LEGACY_READ_KEY.toByteArray(Charsets.UTF_8), KEY_ALGORITHM)

    /** 计算配套证书包的指纹 */
    fun sha256Hex(bytes: ByteArray): String =
        toHex(MessageDigest.getInstance("SHA-256").digest(bytes))

    fun matchesArchive(certificatePackageBytes: ByteArray, manifest: BackupManifest?): Boolean {
        val expectedSha = manifest?.archiveSha256 ?: return false
        val expectedBytes = manifest.archiveBytes ?: return false
        if (!manifest.hasArchive) return false
        return certificatePackageBytes.size.toLong() == expectedBytes &&
            sha256Hex(certificatePackageBytes) == expectedSha
    }

    fun matchesArchive(actualSha256: String, actualBytes: Long, manifest: BackupManifest?): Boolean {
        val expectedSha = manifest?.archiveSha256 ?: return false
        val expectedBytes = manifest.archiveBytes ?: return false
        if (!manifest.hasArchive) return false
        return actualBytes == expectedBytes && actualSha256 == expectedSha
    }

    fun sha256Of(input: InputStream, bufferBytes: Int = IO_BUFFER): Pair<String, Long> {
        val digest = MessageDigest.getInstance("SHA-256")
        var total = 0L
        val buffer = ByteArray(bufferBytes)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
            total += read
        }
        return toHex(digest.digest()) to total
    }

    private fun manifestFrom(fields: Map<String, String>): BackupManifest? =
        if (fields.isEmpty()) null else BackupManifest(
            format = fields["format"].orEmpty(),
            version = fields["version"]?.toIntOrNull() ?: 0,
            createdAt = fields["createdAt"].orEmpty(),
            appVersion = fields["appVersion"].orEmpty(),
            dbSchema = fields["dbSchema"]?.toIntOrNull() ?: 0,
            archiveFile = fields["archiveFile"],
            archiveSha256 = fields["archiveSha256"],
            archiveBytes = fields["archiveBytes"]?.toLongOrNull(),
            archiveEntries = fields["archiveEntries"]?.toIntOrNull(),
        )

    private fun toHex(bytes: ByteArray): String =
        bytes.joinToString("") { "%02x".format(it.toInt() and 0xFF) }

    @SuppressLint("GetInstance") // ECB 仅用于读取更早版本产出的备份（从不用于加密）。
    private fun openBackup(input: InputStream, inflateLimit: Long, passphrase: CharArray?): InputStream {
        val source = BufferedInputStream(NonClosingInputStream(input), IO_BUFFER)
        val headerSize = maxOf(MAGIC_V3.size + 12, V5_HEADER_BYTES)
        val header = ByteArray(headerSize)
        source.mark(headerSize)
        var read = 0
        while (read < headerSize) {
            val n = source.read(header, read, headerSize - read)
            if (n < 0) break
            read += n
        }
        source.reset()

        val isGzip = read >= 2 && header[0] == 0x1f.toByte() && header[1] == 0x8b.toByte()
        val isV5 = read >= V5_HEADER_BYTES && header.copyOfRange(0, MAGIC_V5.size).contentEquals(MAGIC_V5)
        val isV4 = !isV5 && read >= MAGIC_V4.size + NONCE_BASE_BYTES &&
            header.copyOfRange(0, MAGIC_V4.size).contentEquals(MAGIC_V4)
        val isV3 = !isV5 && read >= MAGIC_V3.size + 12 &&
            header.copyOfRange(0, MAGIC_V3.size).contentEquals(MAGIC_V3)

        val plain = when {
            isGzip -> source
            isV5 -> {
                skipExactly(source, MAGIC_V5.size)
                val kdfId = source.read()
                require(kdfId == EXPORT_KDF_ID.toInt()) { "备份文件使用了不支持的密钥派生方式（kdfId=$kdfId）" }
                val iterations = readInt32(source) ?: throw IOException("备份文件头不完整")
                require(iterations in MIN_KDF_ITERATIONS..MAX_KDF_ITERATIONS) {
                    "备份文件声明的 KDF 迭代次数超出允许范围：$iterations"
                }
                val salt = ByteArray(V5_SALT_BYTES)
                require(readFully(source, salt)) { "备份文件头不完整" }
                val nonceBase = ByteArray(NONCE_BASE_BYTES)
                require(readFully(source, nonceBase)) { "备份文件头不完整" }
                if (passphrase == null || passphrase.isEmpty()) {
                    throw IOException("备份文件已加密：需要提供口令（本次未提供）")
                }
                ChunkedGcmInputStream(source, nonceBase, deriveKey(passphrase, salt, iterations))
            }
            isV4 -> {
                skipExactly(source, MAGIC_V4.size)
                val nonceBase = ByteArray(NONCE_BASE_BYTES)
                require(readFully(source, nonceBase)) { "备份文件头不完整" }
                ChunkedGcmInputStream(source, nonceBase, legacyReadKey())
            }
            isV3 -> {
                skipExactly(source, MAGIC_V3.size)
                val iv = ByteArray(12)
                require(readFully(source, iv)) { "备份文件头不完整" }
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.DECRYPT_MODE, legacyReadKey(), GCMParameterSpec(128, iv))
                CipherSource(cipher, source)
            }
            else -> {
                val cipher = Cipher.getInstance(LEGACY_TRANSFORMATION)
                cipher.init(Cipher.DECRYPT_MODE, legacyReadKey())
                CipherSource(cipher, source)
            }
        }
        return InflateBudgetStream(GZIPInputStream(plain, IO_BUFFER), inflateLimit)
    }

    /** 读 4 字节大端整数 */
    private fun readInt32(input: InputStream): Int? {
        val a = input.read(); if (a < 0) return null
        val b = input.read(); if (b < 0) return null
        val c = input.read(); if (c < 0) return null
        val d = input.read(); if (d < 0) return null
        return (a shl 24) or (b shl 16) or (c shl 8) or d
    }

    private fun skipExactly(source: InputStream, count: Int) {
        var skipped = 0L
        while (skipped < count) {
            val n = source.skip(count - skipped)
            require(n > 0) { "备份文件头不完整" }
            skipped += n
        }
    }

    private fun readFully(input: InputStream, target: ByteArray): Boolean {
        var read = 0
        while (read < target.size) {
            val n = input.read(target, read, target.size - read)
            if (n < 0) return false
            read += n
        }
        return true
    }

    private class DigestCountingOutputStream(
        private val target: OutputStream,
        private val digest: MessageDigest
    ) : OutputStream() {
        var count: Long = 0L
            private set

        override fun write(b: Int) = write(byteArrayOf(b.toByte()), 0, 1)

        override fun write(b: ByteArray, off: Int, len: Int) {
            target.write(b, off, len)
            digest.update(b, off, len)
            count += len
        }

        override fun flush() = target.flush()
        override fun close() = Unit
    }

    private class ChunkedGcmOutputStream(
        private val target: OutputStream,
        private val nonceBase: ByteArray,
        private val key: SecretKeySpec
    ) : OutputStream() {
        private val cipher = Cipher.getInstance(TRANSFORMATION)
        private val buffer = ByteArray(CHUNK_BYTES)
        private var buffered = 0
        private var index = 0

        override fun write(b: Int) = write(byteArrayOf(b.toByte()), 0, 1)

        override fun write(b: ByteArray, off: Int, len: Int) {
            var position = off
            var remaining = len
            while (remaining > 0) {
                val take = minOf(remaining, CHUNK_BYTES - buffered)
                System.arraycopy(b, position, buffer, buffered, take)
                buffered += take
                position += take
                remaining -= take
                if (buffered == CHUNK_BYTES) emit(last = false)
            }
        }

        /** 吐出最后一块并打上 last 标记 */
        fun finish() = emit(last = true)

        override fun flush() = target.flush()
        override fun close() = Unit

        private fun emit(last: Boolean) {
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, nonceFor(index)))
            cipher.updateAAD(int32(index))
            val encrypted = cipher.doFinal(buffer, 0, buffered)
            target.write(if (last) 1 else 0)
            target.write(encrypted.size ushr 24 and 0xFF)
            target.write(encrypted.size ushr 16 and 0xFF)
            target.write(encrypted.size ushr 8 and 0xFF)
            target.write(encrypted.size and 0xFF)
            target.write(encrypted)
            index++
            buffered = 0
        }

        private fun nonceFor(chunkIndex: Int): ByteArray = ByteArray(12).also { nonce ->
            System.arraycopy(nonceBase, 0, nonce, 0, nonceBase.size)
            nonce[8] = (chunkIndex ushr 24).toByte()
            nonce[9] = (chunkIndex ushr 16).toByte()
            nonce[10] = (chunkIndex ushr 8).toByte()
            nonce[11] = chunkIndex.toByte()
        }
    }

    private class ChunkedGcmInputStream(
        private val source: InputStream,
        private val nonceBase: ByteArray,
        private val key: SecretKeySpec
    ) : InputStream() {
        private val cipher = Cipher.getInstance(TRANSFORMATION)
        private val plain = ByteArray(CHUNK_BYTES)
        private var plainLength = 0
        private var plainPosition = 0
        private var index = 0
        private var lastSeen = false
        private var ended = false

        override fun read(): Int {
            if (!fill()) return -1
            return plain[plainPosition++].toInt() and 0xFF
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (len == 0) return 0
            if (!fill()) return -1
            val take = minOf(len, plainLength - plainPosition)
            System.arraycopy(plain, plainPosition, b, off, take)
            plainPosition += take
            return take
        }

        override fun close() = Unit

        private fun fill(): Boolean {
            while (plainPosition >= plainLength) {
                if (ended) return false
                readRecord() || return false
            }
            return true
        }

        private fun readRecord(): Boolean {
            val flag = source.read()
            if (flag < 0) {
                if (!lastSeen) throw IOException("备份文件不完整（被截断）")
                ended = true
                return false
            }
            require(flag == 0 || flag == 1) { "备份文件损坏（块标记异常）" }
            val length = readInt() ?: throw IOException("备份文件不完整（块长度缺失）")
            require(length in GCM_TAG_BYTES..(CHUNK_BYTES + GCM_TAG_BYTES)) { "备份文件损坏（块长度异常：$length）" }
            val encrypted = ByteArray(length)
            require(readFully(source, encrypted)) { "备份文件不完整（块内容缺失）" }

            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, nonceFor(index)))
            cipher.updateAAD(int32(index))
            val decrypted = cipher.doFinal(encrypted)
            System.arraycopy(decrypted, 0, plain, 0, decrypted.size)
            plainLength = decrypted.size
            plainPosition = 0
            index++
            if (flag == 1) lastSeen = true
            return true
        }

        /** 与写入侧同构 */
        private fun nonceFor(chunkIndex: Int): ByteArray = ByteArray(12).also { nonce ->
            System.arraycopy(nonceBase, 0, nonce, 0, nonceBase.size)
            nonce[8] = (chunkIndex ushr 24).toByte()
            nonce[9] = (chunkIndex ushr 16).toByte()
            nonce[10] = (chunkIndex ushr 8).toByte()
            nonce[11] = chunkIndex.toByte()
        }

        private fun readInt(): Int? = readInt32(source)
    }

    /** 加密输出侧 */
    private class CipherSink(private val cipher: Cipher, private val out: OutputStream) : OutputStream() {
        override fun write(b: Int) = write(byteArrayOf(b.toByte()))

        override fun write(b: ByteArray, off: Int, len: Int) {
            val chunk = cipher.update(b, off, len) ?: return
            if (chunk.isNotEmpty()) out.write(chunk)
        }
    }

    /** 解密输入侧 */
    private class CipherSource(private val cipher: Cipher, private val input: InputStream) : InputStream() {
        private val raw = ByteArray(IO_BUFFER)
        private var buffer = ByteArray(0)
        private var pos = 0
        private var finished = false

        private fun fill(): Boolean {
            while (true) {
                if (finished) return false
                val n = input.read(raw)
                val chunk = if (n < 0) {
                    finished = true
                    cipher.doFinal()
                } else {
                    cipher.update(raw, 0, n)
                }
                if (chunk != null && chunk.isNotEmpty()) {
                    buffer = chunk
                    pos = 0
                    return true
                }
                if (finished) return false
            }
        }

        override fun read(): Int {
            if (pos >= buffer.size && !fill()) return -1
            return buffer[pos++].toInt() and 0xFF
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (len == 0) return 0
            if (pos >= buffer.size && !fill()) return -1
            val n = minOf(len, buffer.size - pos)
            System.arraycopy(buffer, pos, b, off, n)
            pos += n
            return n
        }
    }

    /** 解压预算 */
    private class InflateBudgetStream(private val input: InputStream, private val limit: Long) : InputStream() {
        private var total = 0L

        private fun account(count: Int) {
            if (count <= 0) return
            total += count
            require(total <= limit) { "备份内容异常：解压后体积超出安全上限" }
        }

        override fun read(): Int {
            val value = input.read()
            if (value >= 0) account(1)
            return value
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val count = input.read(b, off, len)
            account(count)
            return count
        }
    }

    /** 调用方持有底层流，关链不该把它一起关掉 */
    internal class NonClosingInputStream(private val delegate: InputStream) : InputStream() {
        override fun read(): Int = delegate.read()
        override fun read(b: ByteArray, off: Int, len: Int): Int = delegate.read(b, off, len)
        override fun available(): Int = delegate.available()
        override fun close() = Unit
    }

    private fun parseCsv(reader: Reader): ParsedBackup {
        val vehicles = mutableListOf<VehicleEntity>()
        val mileage = mutableListOf<MileageRecordEntity>()
        val energy = mutableListOf<EnergyRecordEntity>()
        val items = mutableListOf<MaintenanceItemEntity>()
        val history = mutableListOf<MaintenanceHistoryEntity>()
        val fitActivities = mutableListOf<FitActivityEntity>()
        val fitSamples = mutableListOf<FitSampleEntity>()
        val fitEvents = mutableListOf<FitTimerEventEntity>()
        val performanceSessions = mutableListOf<PerformanceSessionEntity>()
        val performanceSamples = mutableListOf<PerformanceSampleEntity>()
        val performanceSplits = mutableListOf<PerformanceSplitEntity>()
        val performanceNoise = mutableListOf<PerformanceNoiseEntity>()
        val performanceJerks = mutableListOf<PerformanceJerkEntity>()
        val certificates = mutableListOf<CertificateEntry>()
        val manifestFields = LinkedHashMap<String, String>()
        var sawCertificateSection = false

        val records = CsvRecordReader(reader)
        var section = ""
        var expectHeader = false
        while (true) {
            val line = records.next() ?: break
            if (line.isBlank()) continue
            if (line.startsWith("=== ") && line.endsWith(" ===")) {
                expectHeader = line != "=== MANIFEST ==="
            } else if (expectHeader) {
                expectHeader = false         // 跳过紧跟标记的表头行
                continue
            }
            when {
                line == "=== VEHICLES ===" -> section = "vehicles"
                line == "=== MILEAGE ===" -> section = "mileage"
                line == "=== ENERGY ===" -> section = "energy"
                line == "=== MAINTENANCE ITEMS ===" -> section = "items"
                line == "=== MAINTENANCE HISTORY ===" -> section = "history"
                line == "=== FIT ACTIVITIES ===" -> section = "fitActivities"
                line == "=== FIT SAMPLES ===" -> section = "fitSamples"
                line == "=== FIT EVENTS ===" -> section = "fitEvents"
                line == "=== PERFORMANCE SESSIONS ===" -> section = "performanceSessions"
                line == "=== PERFORMANCE SAMPLES ===" -> section = "performanceSamples"
                line == "=== PERFORMANCE SPLITS ===" -> section = "performanceSplits"
                line == "=== PERFORMANCE NOISE ===" -> section = "performanceNoise"
                line == "=== PERFORMANCE JERKS ===" -> section = "performanceJerks"
                line == "=== MANIFEST ===" -> section = "manifest"
                line == "=== DOCUMENT ARCHIVE ===" -> {
                    section = "certificates"
                    sawCertificateSection = true
                }
                else -> {
                    val cols = parseCsvLine(line)
                    when (section) {
                        "vehicles" -> if (cols.size >= 12) {
                            val energyType = runCatching { EnergyType.valueOf(cols[5]) }
                                .getOrDefault(EnergyType.Fuel)
                            vehicles.add(VehicleEntity(
                                id = cols[0], name = cols[1], model = cols[2], plate = cols[3],
                                category = runCatching { VehicleCategory.valueOf(cols[4]) }.getOrDefault(VehicleCategory.Car),
                                energyType = energyType,
                                transmissionType = runCatching { TransmissionType.valueOf(cols[6]) }.getOrDefault(TransmissionType.Automatic),
                                fuelTankCapacityLiters = cols[7].toDoubleOrNull(),
                                batteryCapacityKwh = cols[8].toDoubleOrNull(),
                                defaultChargeLimitSoc = cols[9].toIntOrNull()
                                    ?: 100.takeIf { energyType.supportsCharging },
                                currentMileage = cols[10].toDoubleOrNull() ?: 0.0,
                                updatedAt = cols[11].toLongOrNull() ?: 0L,
                                manufacturingDate = cols.getOrNull(12)?.toLongOrNull(),
                                purchasePrice = cols.getOrNull(13)?.toDoubleOrNull() ?: 0.0,
                                purchaseTax = cols.getOrNull(14)?.toDoubleOrNull() ?: 0.0
                            ))
                        }
                        "mileage" -> if (cols.size >= 5) {
                            val hasRoadFee = cols.size >= 6
                            mileage.add(MileageRecordEntity(
                                id = cols[0],
                                vehicleId = cols[1],
                                mileage = cols[2].toDoubleOrNull() ?: 0.0,
                                note = cols[3],
                                createdAt = cols[if (hasRoadFee) 5 else 4].toLongOrNull() ?: 0L,
                                roadFeeAmount = if (hasRoadFee) cols[4].toDoubleOrNull() ?: 0.0 else 0.0
                            ))
                        }
                        "energy" -> if (cols.size >= 17) energy.add(EnergyRecordEntity(
                            id = cols[0], vehicleId = cols[1],
                            type = runCatching { EnergyRecordType.valueOf(cols[2]) }.getOrDefault(EnergyRecordType.Fuel),
                            mileage = cols[3].toDoubleOrNull() ?: 0.0,
                            amount = cols[4].toDoubleOrNull() ?: 0.0,
                            orderAmount = cols[5].toDoubleOrNull() ?: 0.0,
                            paidAmount = cols[6].toDoubleOrNull() ?: 0.0,
                            isFull = cols[7].toBooleanStrictOrNull() ?: true,
                            missedPrevious = cols[8].toBooleanStrictOrNull() ?: false,
                            fuelGrade = cols[9],
                            unitPrice = cols[10].toDoubleOrNull() ?: 0.0,
                            chargeMethod = cols[11],
                            electricityPrice = cols[12].toDoubleOrNull() ?: 0.0,
                            serviceFee = cols[13].toDoubleOrNull() ?: 0.0,
                            chargeLimitSocSnapshot = cols[14].toIntOrNull(),
                            note = cols[15],
                            createdAt = cols[16].toLongOrNull() ?: 0L
                        ))
                        "items" -> if (cols.size >= 8) items.add(MaintenanceItemEntity(
                            id = cols[0], vehicleId = cols[1], name = cols[2],
                            intervalKm = cols[3].toIntOrNull() ?: 0, intervalDays = cols[4].toIntOrNull() ?: 0,
                            lastServiceMileage = cols[5].toIntOrNull() ?: 0,
                            lastServiceAt = cols[6].toLongOrNull() ?: 0L,
                            updatedAt = cols[7].toLongOrNull() ?: 0L,
                            lastPartNumber = cols.getOrNull(8).orEmpty(),
                            itemType = cols.getOrNull(9)?.let { runCatching { MaintenanceItemType.valueOf(it) }.getOrNull() }
                                ?: maintenanceTypeFromName(cols[2]),
                            baselineAt = cols.getOrNull(10)?.toLongOrNull()
                                ?: cols[6].toLongOrNull() ?: cols[7].toLongOrNull() ?: 0L
                        ))
                        "history" -> if (cols.size >= 10) history.add(MaintenanceHistoryEntity(
                            id = cols[0], vehicleId = cols[1], itemId = cols[2], itemName = cols[3],
                            serviceMileage = cols[4].toDoubleOrNull() ?: 0.0,
                            serviceAt = cols[5].toLongOrNull() ?: 0L, note = cols[6],
                            laborCost = cols[7].toDoubleOrNull() ?: 0.0,
                            partsCost = cols[8].toDoubleOrNull() ?: 0.0,
                            partNumber = cols[9],
                            recordName = cols.getOrNull(10).orEmpty().ifBlank { cols[3] },
                            insuranceAmount = cols.getOrNull(11)?.toDoubleOrNull() ?: 0.0,
                            repairAmount = cols.getOrNull(12)?.toDoubleOrNull() ?: 0.0,
                            expiresAt = cols.getOrNull(13)?.toLongOrNull()
                        ))
                        "fitActivities" -> if (cols.size >= 14) fitActivities.add(FitActivityEntity(cols[0], cols[1], cols[2], FitActivityStatus.valueOf(cols[3]), cols[4].toLong(), cols[5].toLongOrNull(), cols[6].toLong(), cols[7].toDouble(), cols[8].toDouble(), cols[9].toBoolean(), cols[10].toLongOrNull(), cols[11].toLongOrNull(), cols[12].toLong(), cols[13].toLong()))
                        "fitSamples" -> if (cols.size >= 13) fitSamples.add(FitSampleEntity(activityId=cols[0], timestamp=cols[1].toLong(), latitude=cols[2].toDouble(), longitude=cols[3].toDouble(), speedMps=cols[4].toDouble(), distanceMeters=cols[5].toDouble(), altitudeMeters=cols[6].toDoubleOrNull(), accuracyMeters=cols[7].toFloat(), bearingDegrees=cols[8].toFloatOrNull(), heartRateBpm=cols[9].toIntOrNull(), cadenceRpm=cols[10].toIntOrNull(), powerWatts=cols[11].toIntOrNull(), motionMagnitude=cols[12].toFloatOrNull()))
                        "fitEvents" -> if (cols.size >= 3) fitEvents.add(FitTimerEventEntity(activityId=cols[0], timestamp=cols[1].toLong(), type=FitTimerEventType.valueOf(cols[2])))
                        "performanceSessions" -> if (cols.size >= 18) performanceSessions.add(PerformanceSessionEntity(cols[0], cols[1].ifBlank { null }, cols[2], PerformanceTestType.valueOf(cols[3]), PerformanceStatus.valueOf(cols[4]), cols[5].toLong(), cols[6].toLongOrNull(), cols[7].toDoubleOrNull(), cols[8].toDoubleOrNull(), cols[9].toDouble(), cols[10].toDouble(), cols[11].toDoubleOrNull(), cols[12].toDoubleOrNull(), PerformanceQuality.valueOf(cols[13]), cols[14], cols[15].toBoolean(), cols[16].toLong(), cols[17].toLong()))
                        "performanceSamples" -> if (cols.size >= 15) performanceSamples.add(PerformanceSampleEntity(sessionId=cols[0], timestampNanos=cols[1].toLong(), wallTime=cols[2].toLong(), gpsSpeedMps=cols[3].toDoubleOrNull(), fusedSpeedMps=cols[4].toDouble(), distanceMeters=cols[5].toDouble(), latitude=cols[6].toDoubleOrNull(), longitude=cols[7].toDoubleOrNull(), gpsAccuracyMeters=cols[8].toFloatOrNull(), altitudeMeters=cols[9].toDoubleOrNull(), longitudinalAccelerationMps2=cols[10].toFloat(), lateralAccelerationMps2=cols[11].toFloat(), verticalAccelerationMps2=cols[12].toFloat(), jerkMps3=cols[13].toFloat(), noiseDbA=cols[14].toFloatOrNull()))
                        "performanceSplits" -> if (cols.size >= 6) performanceSplits.add(PerformanceSplitEntity(cols[0], cols[1], cols[2], cols[3].toDouble(), cols[4].toDouble(), cols[5].toDouble()))
                        "performanceNoise" -> if (cols.size >= 3) performanceNoise.add(PerformanceNoiseEntity(sessionId=cols[0], timestamp=cols[1].toLong(), dbA=cols[2].toFloat()))
                        "performanceJerks" -> if (cols.size >= 9) performanceJerks.add(PerformanceJerkEntity(cols[0], cols[1], cols[2].toLong(), cols[3].toDouble(), cols[4].toFloat(), cols[5].toFloat(), cols[6].toLong(), JerkSeverity.valueOf(cols[7]), cols[8].toBoolean()))
                        "manifest" -> if (cols.size >= 2) {
                            manifestFields[cols[0]] = cols.subList(1, cols.size).joinToString(",")
                        }
                        "certificates" -> if (cols.size >= 5) runCatching { certificates += CertificateEntry(cols[0], cols[1], cols[2], cols[3], Base64.getDecoder().decode(cols[4])) }
                    }
                }
            }
        }
        val manifest = manifestFrom(manifestFields)
        val kind = when {
            manifest == null && sawCertificateSection -> CheBackupKind.LegacyBundle
            manifest?.format == MANIFEST_FORMAT_ARCHIVE -> CheBackupKind.Archive
            else -> CheBackupKind.Business
        }
        return ParsedBackup(
            business = ExportedData(vehicles, mileage, energy, items, history, fitActivities, fitSamples, fitEvents, performanceSessions, performanceSamples, performanceSplits, performanceNoise, performanceJerks, certificates),
            manifest = manifest,
            kind = kind
        )
    }

    private fun validate(data: ExportedData) {
        val vehicles = data.vehicles.associateBy { it.id }
        data.vehicles.forEach { vehicle ->
            require(vehicle.id.isNotBlank())
            require(vehicle.purchasePrice >= 0.0 && vehicle.purchaseTax >= 0.0)
            if (vehicle.energyType.supportsCharging) {
                require(vehicle.defaultChargeLimitSoc in 1..100)
            }
        }
        data.mileageRecords.forEach { record ->
            require(record.vehicleId in vehicles)
            require(record.mileage >= 0)
        }
        data.energyRecords.forEach { record ->
            require(record.vehicleId in vehicles)
            require(record.amount > 0.0)
            require(record.orderAmount >= 0.0)
            require(record.paidAmount >= 0.0)
            if (record.type == EnergyRecordType.Charge) {
                require(record.chargeLimitSocSnapshot in 1..100)
            }
        }
        data.maintenanceItems.forEach { item ->
            require(item.vehicleId in vehicles)
            when {
                item.itemType.isInsurance -> require(item.intervalKm == 0 && item.intervalDays > 0)
                item.itemType == MaintenanceItemType.RepairInspection -> require(item.intervalKm == 0 && item.intervalDays == 0)
                else -> require(item.intervalKm > 0 && item.intervalDays > 0)
            }
        }
        data.maintenanceHistory.forEach { history ->
            require(history.vehicleId in vehicles)
            require(history.serviceMileage >= 0)
            require(history.insuranceAmount >= 0.0 && history.repairAmount >= 0.0)
        }
        val sessions = data.performanceSessions.associateBy { it.id }
        data.performanceSamples.forEach { require(it.sessionId in sessions) }
        data.performanceSplits.forEach { require(it.sessionId in sessions) }
        data.performanceNoise.forEach { require(it.sessionId in sessions) }
        data.performanceJerks.forEach { require(it.sessionId in sessions) }
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' && !inQuotes -> inQuotes = true
                c == '"' && inQuotes -> {
                    if (i + 1 < line.length && line[i + 1] == '"') {
                        current.append('"'); i++
                    } else {
                        inQuotes = false
                    }
                }
                c == ',' && !inQuotes -> { result.add(current.toString()); current.clear() }
                else -> current.append(c)
            }
            i++
        }
        result.add(current.toString())
        return result
    }

    /** 流式 CSV 记录读取 */
    private class CsvRecordReader(private val reader: Reader) {
        private val current = StringBuilder()
        private var inQuotes = false
        private var pending = -1

        private fun readChar(): Int {
            if (pending >= 0) {
                val p = pending
                pending = -1
                return p
            }
            return reader.read()
        }

        private fun unread(c: Int) {
            pending = c
        }

        fun next(): String? {
            while (true) {
                val ci = readChar()
                if (ci < 0) {
                    if (current.isEmpty()) return null
                    return current.toString().also { current.setLength(0) }
                }
                val c = ci.toChar()
                if (c == '"') {
                    current.append(c)
                    val n = readChar()
                    if (inQuotes && n == '"'.code) {
                        current.append('"')
                    } else {
                        inQuotes = !inQuotes
                        if (n >= 0) unread(n)
                    }
                } else if ((c == '\n' || c == '\r') && !inQuotes) {
                    if (c == '\r') {
                        val n = readChar()
                        if (n >= 0 && n != '\n'.code) unread(n)
                    }
                    if (current.isNotEmpty()) {
                        return current.toString().also { current.setLength(0) }
                    }
                } else {
                    current.append(c)
                }
            }
        }
    }

    private fun csvEscape(value: String): String {
        return if (value.contains(',') || value.contains('"') || value.contains('\n')) {
            "\"${value.replace("\"", "\"\"")}\""
        } else value
    }

    private fun maintenanceTypeFromName(name: String): MaintenanceItemType = when (name) {
        "商业险" -> MaintenanceItemType.CommercialInsurance
        "交强险" -> MaintenanceItemType.CompulsoryInsurance
        "维修/检查" -> MaintenanceItemType.RepairInspection
        else -> MaintenanceItemType.Periodic
    }

    fun formatDate(value: Long): String =
        SimpleDateFormat("yyyyMMdd_HHmmss", Locale.CHINA).format(Date(value))
    /** 用车记录备份包的文件名 */
    fun businessFileName(atMillis: Long = System.currentTimeMillis()): String =
        "CheKu_用车记录_${formatDate(atMillis)}.che"

    /** 人车证书配套包的文件名 */
    fun archiveFileName(atMillis: Long = System.currentTimeMillis()): String =
        "CheKu_人车证书_${formatDate(atMillis)}.cert.che"
}

internal fun appendBase64(out: Appendable, data: ByteArray, chunkBytes: Int = 24 * 1024) =
    appendBase64(out, data.inputStream(), chunkBytes)

internal fun appendBase64(out: Appendable, input: InputStream, chunkBytes: Int = 24 * 1024) {
    require(chunkBytes > 0 && chunkBytes % 3 == 0) { "块长必须是 3 的正倍数：$chunkBytes" }
    val encoder = Base64.getEncoder()
    val buffer = ByteArray(chunkBytes)
    var carry = 0
    while (true) {
        val read = input.read(buffer, carry, buffer.size - carry)
        if (read < 0) break
        val total = carry + read
        val usable = total - total % 3
        if (usable == 0) {
            carry = total
            continue
        }
        out.append(String(encoder.encode(buffer.copyOf(usable)), Charsets.US_ASCII))
        carry = total - usable
        if (carry > 0) System.arraycopy(buffer, usable, buffer, 0, carry)
    }
    if (carry > 0) out.append(String(encoder.encode(buffer.copyOf(carry)), Charsets.US_ASCII))
}
