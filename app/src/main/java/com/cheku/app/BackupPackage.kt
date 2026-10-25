package com.cheku.app

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** 新版导出包的容器层 */
object BackupPackage {
    const val FORMAT = "cheku-backup"
    const val CERTS_FORMAT = "cheku-certs"
    const val FORMAT_VERSION = 5
    const val SCOPE_BUSINESS = "business"
    const val SCOPE_CERTIFICATES = "certificates"

    const val MANIFEST_ENTRY = "manifest.json"
    const val DATA_DIR = "data/"
    const val CERTS_DIR = "certs/"

    /** BIFF8 单工作表行上限 */
    const val MAX_ROWS_PER_SHEET = 65_535

    /** 单卷数据行上限 */
    const val MAX_ROWS_PER_VOLUME = 100_000

    private const val IO_BUFFER = 64 * 1024
    private const val MAX_SHEET_NAME_CHARS = 31

    /** 外层文件名用的时间戳 */
    fun outerStampFor(atMillis: Long): String =
        SimpleDateFormat("yyyyMMdd_HHmmss", Locale.CHINA).format(Date(atMillis))

    fun innerStampFor(atMillis: Long): String =
        SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date(atMillis))

    /** 导出包外层名 */
    fun businessFileName(atMillis: Long): String =
        "CheKu_用车数据_${outerStampFor(atMillis)}.zip"

    /** 证书包外层名 */
    fun certsArchiveFileName(atMillis: Long): String =
        "CheKu_人车证书_${outerStampFor(atMillis)}.zip"

    /** 上传包外层名 */
    fun uploadFileName(atMillis: Long): String =
        "cheku-backup-${innerStampFor(atMillis)}.zip"

    fun dataPartPath(innerStamp: String, index: Int): String {
        val suffix = if (index == 0) "" else "-%03d".format(index + 1)
        return "${DATA_DIR}cheku-data-$innerStamp$suffix.xls"
    }

    /** 证书包在导出包里的条目路径 */
    fun certsEntryPath(innerStamp: String): String = "${CERTS_DIR}cheku-certs-$innerStamp.zip"

    /** 一张业务表要占多少数据行 */
    data class TableRows(val table: String, val rows: Int)

    /** 卷内的一张工作表 */
    data class SheetSlice(val sheet: String, val rows: Int)

    /** 一卷的构成 */
    data class VolumePlan(val slices: List<SheetSlice>) {
        val rows: Int get() = slices.sumOf { it.rows }
        val sheetNames: List<String> get() = slices.map { it.sheet }
    }

    fun planVolumes(
        tables: List<TableRows>,
        maxRowsPerSheet: Int = MAX_ROWS_PER_SHEET,
        maxRowsPerVolume: Int = MAX_ROWS_PER_VOLUME
    ): List<VolumePlan> {
        require(maxRowsPerSheet > 0) { "maxRowsPerSheet 必须为正" }
        require(maxRowsPerVolume > 0) { "maxRowsPerVolume 必须为正" }

        val segments = ArrayList<SheetSlice>()
        tables.forEach { table ->
            require(table.table.length <= MAX_SHEET_NAME_CHARS) {
                "工作表名超 BIFF8 上限（$MAX_SHEET_NAME_CHARS 字符）：${table.table}"
            }
            var remaining = table.rows.coerceAtLeast(0)
            var part = 1
            if (remaining == 0) {
                segments += SheetSlice(table.table, 0)
                return@forEach
            }
            while (remaining > 0) {
                val take = minOf(remaining, maxRowsPerSheet)
                val name = if (part == 1) table.table else "${table.table}.p$part"
                require(name.length <= MAX_SHEET_NAME_CHARS) { "分片名超长：$name" }
                segments += SheetSlice(name, take)
                remaining -= take
                part++
            }
        }

        val volumes = ArrayList<VolumePlan>()
        var current = ArrayList<SheetSlice>()
        var used = 0
        segments.forEach { slice ->
            if (current.isNotEmpty() && used + slice.rows > maxRowsPerVolume) {
                volumes += VolumePlan(current.toList())
                current = ArrayList()
                used = 0
            }
            current += slice
            used += slice.rows
        }
        if (current.isNotEmpty()) volumes += VolumePlan(current.toList())
        // 只有 `tables` 为空时才会走到这里（业务上不会发生，但不能返回空列表）
        if (volumes.isEmpty()) volumes += VolumePlan(listOf(SheetSlice("车辆", 0)))
        return volumes
    }

    /** 一卷的指纹与规模 */
    data class DataPart(
        val path: String,
        val bytes: Long,
        val sha256: String,
        val rows: Int,
        val sheets: Int
    )

    /** 根 manifest */
    data class BusinessManifest(
        val format: String,
        val formatVersion: Int,
        val scope: String,
        val createdAt: String,
        val appVersion: String,
        val dbSchema: Int,
        val data: List<DataPart>,
        val counts: Map<String, Int>
    )

    /** 证书包内的 manifest */
    data class CertEntry(val path: String, val key: String, val fileName: String, val bytes: Long, val sha256: String)

    data class CertsManifest(
        val format: String,
        val formatVersion: Int,
        val scope: String,
        val createdAt: String,
        val appVersion: String,
        val entries: List<CertEntry>
    )

    /** 时间戳口径 */
    private fun createdAtOf(atMillis: Long): String =
        OffsetDateTime.ofInstant(Instant.ofEpochMilli(atMillis), ZoneId.systemDefault()).toString()

    fun businessManifestJson(manifest: BusinessManifest): String {
        val data = JSONArray()
        manifest.data.forEach { part ->
            data.put(
                JSONObject()
                    .put("path", part.path)
                    .put("bytes", part.bytes)
                    .put("sha256", part.sha256)
                    .put("rows", part.rows)
                    .put("sheets", part.sheets)
            )
        }
        val counts = JSONObject()
        manifest.counts.entries.sortedBy { it.key }.forEach { (key, value) -> counts.put(key, value) }
        return JSONObject()
            .put("format", manifest.format)
            .put("formatVersion", manifest.formatVersion)
            .put("scope", manifest.scope)
            .put("createdAt", manifest.createdAt)
            .put("appVersion", manifest.appVersion)
            .put("dbSchema", manifest.dbSchema)
            .put("data", data)
            .put("counts", counts)
            .toString(2)
    }

    fun businessManifestFromJson(text: String): BusinessManifest? = runCatching {
        val root = JSONObject(text)
        val dataArray = root.getJSONArray("data")
        val data = (0 until dataArray.length()).map { index ->
            val part = dataArray.getJSONObject(index)
            DataPart(
                path = part.getString("path"),
                bytes = part.getLong("bytes"),
                sha256 = part.getString("sha256"),
                rows = part.optInt("rows", 0),
                sheets = part.optInt("sheets", 0)
            )
        }
        val countsObject = root.getJSONObject("counts")
        val counts = countsObject.keys().asSequence().associateWith { countsObject.getInt(it) }
        BusinessManifest(
            format = root.getString("format"),
            formatVersion = root.getInt("formatVersion"),
            scope = root.getString("scope"),
            createdAt = root.getString("createdAt"),
            appVersion = root.optString("appVersion", ""),
            dbSchema = root.optInt("dbSchema", 0),
            data = data,
            counts = counts
        )
    }.getOrNull()

    fun certsManifestJson(manifest: CertsManifest): String {
        val entries = JSONArray()
        manifest.entries.forEach { entry ->
            entries.put(
                JSONObject()
                    .put("path", entry.path)
                    .put("key", entry.key)
                    .put("fileName", entry.fileName)
                    .put("bytes", entry.bytes)
                    .put("sha256", entry.sha256)
            )
        }
        return JSONObject()
            .put("format", manifest.format)
            .put("formatVersion", manifest.formatVersion)
            .put("scope", manifest.scope)
            .put("createdAt", manifest.createdAt)
            .put("appVersion", manifest.appVersion)
            .put("entries", entries)
            .toString(2)
    }

    fun certsManifestFromJson(text: String): CertsManifest? = runCatching {
        val root = JSONObject(text)
        val array = root.getJSONArray("entries")
        CertsManifest(
            format = root.getString("format"),
            formatVersion = root.getInt("formatVersion"),
            scope = root.getString("scope"),
            createdAt = root.getString("createdAt"),
            appVersion = root.optString("appVersion", ""),
            entries = (0 until array.length()).map { index ->
                val entry = array.getJSONObject(index)
                CertEntry(
                    path = entry.getString("path"),
                    key = entry.optString("key", ""),
                    fileName = entry.optString("fileName", ""),
                    bytes = entry.getLong("bytes"),
                    sha256 = entry.getString("sha256")
                )
            }
        )
    }.getOrNull()

    /** 一卷已经渲染好的 xls 字节 */
    data class VolumeBytes(val sheetNames: List<String>, val rows: Int, val bytes: ByteArray) {
        override fun equals(other: Any?): Boolean =
            this === other || (other is VolumeBytes && rows == other.rows &&
                sheetNames == other.sheetNames && bytes.contentEquals(other.bytes))

        override fun hashCode(): Int =
            31 * (31 * sheetNames.hashCode() + rows) + bytes.contentHashCode()
    }

    fun writeExportPackage(
        out: OutputStream,
        atMillis: Long,
        appVersion: String,
        dbSchema: Int,
        counts: Map<String, Int>,
        volumes: List<VolumeBytes>,
        certsZip: ByteArray? = null
    ): BusinessManifest {
        require(volumes.isNotEmpty()) { "导出包至少要有一卷数据" }
        val inner = innerStampFor(atMillis)
        val parts = volumes.mapIndexed { index, volume ->
            DataPart(
                path = dataPartPath(inner, index),
                bytes = volume.bytes.size.toLong(),
                sha256 = sha256Hex(volume.bytes),
                rows = volume.rows,
                sheets = volume.sheetNames.size
            )
        }
        val manifest = BusinessManifest(
            format = FORMAT,
            formatVersion = FORMAT_VERSION,
            scope = SCOPE_BUSINESS,
            createdAt = createdAtOf(atMillis),
            appVersion = appVersion,
            dbSchema = dbSchema,
            data = parts,
            counts = counts
        )

        zipTo(out) { zip ->
            zip.putNextEntry(ZipEntry(MANIFEST_ENTRY))
            zip.write(businessManifestJson(manifest).toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            volumes.forEachIndexed { index, volume ->
                zip.putNextEntry(ZipEntry(parts[index].path))
                zip.write(volume.bytes)
                zip.closeEntry()
            }

            if (certsZip != null) {
                zip.putNextEntry(ZipEntry(certsEntryPath(inner)))
                zip.write(certsZip)
                zip.closeEntry()
            }
        }
        return manifest
    }

    fun writeExportPackageStreaming(
        out: OutputStream,
        atMillis: Long,
        appVersion: String,
        dbSchema: Int,
        counts: Map<String, Int>,
        scratchDir: File,
        plan: List<VolumePlan>,
        renderVolume: (index: Int, sink: OutputStream) -> Unit,
        writeCerts: ((ZipOutputStream) -> Unit)? = null
    ): BusinessManifest {
        require(plan.isNotEmpty()) { "导出包至少要有一卷数据" }
        val inner = innerStampFor(atMillis)

        val staged = ArrayList<RenderedVolume>()
        try {
            plan.forEachIndexed { index, volume ->
                val temp = File(scratchDir, "cheku-data-$inner-${index}.tmp")
                FileOutputStream(temp).use { fileOut ->
                    renderVolume(index, fileOut)
                    fileOut.flush()
                }
                staged += RenderedVolume(
                    part = DataPart(
                        path = dataPartPath(inner, index),
                        bytes = temp.length(),
                        sha256 = FileInputStream(temp).use { sha256Hex(it) },
                        rows = volume.rows,
                        sheets = volume.sheetNames.size
                    ),
                    file = temp
                )
            }

            val manifest = BusinessManifest(
                format = FORMAT,
                formatVersion = FORMAT_VERSION,
                scope = SCOPE_BUSINESS,
                createdAt = createdAtOf(atMillis),
                appVersion = appVersion,
                dbSchema = dbSchema,
                data = staged.map { it.part },
                counts = counts
            )

            // manifest 仍是第一个条目（与规格一致）；校验/剥离都不依赖顺序。
            zipTo(out) { zip ->
                zip.putNextEntry(ZipEntry(MANIFEST_ENTRY))
                zip.write(businessManifestJson(manifest).toByteArray(Charsets.UTF_8))
                zip.closeEntry()

                staged.forEach { rv ->
                    zip.putNextEntry(ZipEntry(rv.part.path))
                    FileInputStream(rv.file).use { copyChunked(it, zip) }
                    zip.closeEntry()
                }

                if (writeCerts != null) {
                    zip.putNextEntry(ZipEntry(certsEntryPath(inner)))
                    val innerZip = ZipOutputStream(NonClosingOutputStream(zip))
                    writeCerts(innerZip)
                    innerZip.finish()   // 写内层 zip 的 central directory（不 close，避免连带关外层）
                    innerZip.flush()    // 只 flush 到外层条目，不关外层
                    zip.closeEntry()
                }
            }
            return manifest
        } finally {
            // 任意失败（含渲染中途抛错）都要清干净，不留半成品临时文件。
            staged.forEach { it.file.delete() }
        }
    }

    /** 已渲染、落盘的卷 */
    private data class RenderedVolume(val part: DataPart, val file: File)

    /** 一份证书原件 */
    data class CertFile(
        val key: String,
        val fileName: String,
        val open: () -> InputStream
    )

    fun writeCertsArchive(
        out: OutputStream,
        atMillis: Long,
        appVersion: String,
        files: List<CertFile>
    ): CertsManifest {
        val entries = ArrayList<CertEntry>()
        zipTo(out) { zip -> writeCertsEntries(zip, atMillis, appVersion, files, entries) }
        return CertsManifest(
            format = CERTS_FORMAT,
            formatVersion = FORMAT_VERSION,
            scope = SCOPE_CERTIFICATES,
            createdAt = createdAtOf(atMillis),
            appVersion = appVersion,
            entries = entries
        )
    }

    internal fun writeCertsEntries(
        zip: ZipOutputStream,
        atMillis: Long,
        appVersion: String,
        files: List<CertFile>,
        entries: ArrayList<CertEntry>
    ) {
        files.forEach { file ->
            val path = certEntryName(file)
            val digest = MessageDigest.getInstance("SHA-256")
            var bytes = 0L
            zip.putNextEntry(ZipEntry(path))
            BufferedInputStream(file.open(), IO_BUFFER).use { input ->
                val buffer = ByteArray(IO_BUFFER)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                    zip.write(buffer, 0, read)
                    bytes += read
                }
            }
            zip.closeEntry()
            entries += CertEntry(
                path = path,
                key = file.key,
                fileName = file.fileName,
                bytes = bytes,
                sha256 = digest.digest().joinToString("") { "%02x".format(it) }
            )
        }
        zip.putNextEntry(ZipEntry(MANIFEST_ENTRY))
        zip.write(
            certsManifestJson(
                CertsManifest(
                    format = CERTS_FORMAT,
                    formatVersion = FORMAT_VERSION,
                    scope = SCOPE_CERTIFICATES,
                    createdAt = createdAtOf(atMillis),
                    appVersion = appVersion,
                    entries = entries
                )
            ).toByteArray(Charsets.UTF_8)
        )
        zip.closeEntry()
    }

    /** 证书包内的条目名 */
    fun certEntryName(file: CertFile): String = "files/${certificateEntryFileName(file.key, file.fileName)}"

    fun certificateEntryFileName(key: String, fileName: String): String {
        val base = fileName.substringAfterLast('/').substringAfterLast('\\').ifBlank { "certificate" }
        val safe = base.replace(Regex("[^A-Za-z0-9._\\-\\u4e00-\\u9fa5]"), "_").trim('.')
        val cleanedKey = key.replace(Regex("[^A-Za-z0-9._\\-]"), "_")
        val name = "${cleanedKey}__${if (safe.isBlank()) "certificate" else safe}"
        return truncateKeepingExtension(name, 240)
    }

    /** 剥离结果 */
    data class StripReport(val strippedEntries: List<String>, val keptEntries: List<String>)

    /** 把导出包流式剥离成上传包 */
    fun stripCertificates(input: InputStream, out: OutputStream): StripReport {
        val stripped = ArrayList<String>()
        val kept = ArrayList<String>()
        val zipIn = ZipInputStream(BufferedInputStream(input, IO_BUFFER))
        zipTo(out) { zipOut ->
            var entry = zipIn.nextEntry
            while (entry != null) {
                val name = entry.name
                if (name.startsWith(CERTS_DIR)) {
                    stripped += name
                } else {
                    kept += name
                    zipOut.putNextEntry(
                        ZipEntry(name).apply { if (entry.time >= 0) time = entry.time }
                    )
                    copyChunked(zipIn, zipOut)
                    zipOut.closeEntry()
                }
                zipIn.closeEntry()
                entry = zipIn.nextEntry
            }
        }
        return StripReport(stripped, kept)
    }

    /** 服务端错误码 */
    enum class PackageProblem {
        NOT_A_ZIP,
        UNSAFE_PATH,
        CERT_SECTION_PRESENT,
        UNEXPECTED_ENTRY,
        MISSING_MANIFEST,
        BAD_MANIFEST,
        UNSUPPORTED_FORMAT,
        BAD_SCOPE,
        DATA_PART_MISMATCH,
        CHECKSUM_MISMATCH,

        EMPTY_BACKUP
    }

    sealed interface Validation {
        data class Ok(val manifest: BusinessManifest, val entries: List<String>) : Validation
        data class Failed(val problem: PackageProblem, val detail: String) : Validation
    }

    fun validateUploadPackage(input: InputStream): Validation {
        return try {
            validateUploadPackageOrThrow(BufferedInputStream(input, IO_BUFFER))
        } catch (error: Exception) {
            Validation.Failed(PackageProblem.NOT_A_ZIP, error.message ?: error.javaClass.simpleName)
        }
    }

    private fun validateUploadPackageOrThrow(buffered: BufferedInputStream): Validation {
        if (!looksLikeZip(buffered)) return Validation.Failed(PackageProblem.NOT_A_ZIP, "缺少 ZIP 头")

        var manifestText: String? = null
        val entries = ArrayList<String>()
        val digests = HashMap<String, Long>()   // path -> bytes
        val hashes = HashMap<String, String>()

        val zip = ZipInputStream(buffered)
        zip.use {
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory) continue
                val name = entry.name
                if (isUnsafePath(name)) {
                    return Validation.Failed(PackageProblem.UNSAFE_PATH, name)
                }
                entries += name
                when {
                    name.startsWith(CERTS_DIR) ->
                        return Validation.Failed(PackageProblem.CERT_SECTION_PRESENT, name)

                    name == MANIFEST_ENTRY -> manifestText = readTextEntry(zip)

                    name.startsWith(DATA_DIR) && name.endsWith(".xls") -> {
                        val (sha, bytes) = digestEntry(zip)
                        digests[name] = bytes
                        hashes[name] = sha
                    }

                    else -> return Validation.Failed(PackageProblem.UNEXPECTED_ENTRY, name)
                }
            }
        }

        val text = manifestText
            ?: return Validation.Failed(PackageProblem.MISSING_MANIFEST, MANIFEST_ENTRY)
        val manifest = businessManifestFromJson(text)
            ?: return Validation.Failed(PackageProblem.BAD_MANIFEST, "manifest.json 无法解析")
        if (manifest.format != FORMAT || manifest.formatVersion != FORMAT_VERSION) {
            return Validation.Failed(
                PackageProblem.UNSUPPORTED_FORMAT,
                "${manifest.format}@${manifest.formatVersion}"
            )
        }
        if (manifest.scope != SCOPE_BUSINESS) {
            return Validation.Failed(PackageProblem.BAD_SCOPE, manifest.scope)
        }
        val declared = manifest.data.map { it.path }.toSet()
        if (declared != digests.keys) {
            return Validation.Failed(
                PackageProblem.DATA_PART_MISMATCH,
                "声明 ${declared.sorted()} / 实际 ${digests.keys.sorted()}"
            )
        }
        manifest.data.forEach { part ->
            if (digests[part.path] != part.bytes || hashes[part.path] != part.sha256) {
                return Validation.Failed(PackageProblem.CHECKSUM_MISMATCH, part.path)
            }
        }
        val totalRows = manifest.data.sumOf { it.rows }
        if (totalRows <= 0) {
            return Validation.Failed(PackageProblem.EMPTY_BACKUP, "总数据行数 $totalRows")
        }
        return Validation.Ok(manifest, entries)
    }

    fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    fun sha256Hex(input: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(IO_BUFFER)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /** 路径安全 */
    fun isUnsafePath(name: String): Boolean =
        name.startsWith("/") || name.contains("..") || name.contains('\\') || name.isBlank()

    private inline fun zipTo(out: OutputStream, block: (ZipOutputStream) -> Unit) {
        // 与 `ReadableExporter.exportZip` 同一手法：`use` 会在结尾 close zip，而 zip.close()
        // 会连带关掉底层 out；包一层 NonClosingOutputStream 后，close 退化为 flush，out 不被关，
        // 同时 Deflater 仍被正常释放（比只调 finish() 干净）。
        ZipOutputStream(NonClosingOutputStream(out)).use { zip -> block(zip) }
    }

    private fun copyChunked(input: InputStream, output: OutputStream) {
        val buffer = ByteArray(IO_BUFFER)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            output.write(buffer, 0, read)
        }
    }

    private fun readTextEntry(zip: ZipInputStream): String {
        val buffer = ByteArrayOutputStream()
        copyChunked(zip, buffer)
        return buffer.toString(Charsets.UTF_8.name())
    }

    private fun digestEntry(zip: ZipInputStream): Pair<String, Long> {
        val digest = MessageDigest.getInstance("SHA-256")
        var bytes = 0L
        val buffer = ByteArray(IO_BUFFER)
        while (true) {
            val read = zip.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
            bytes += read
        }
        return digest.digest().joinToString("") { "%02x".format(it) } to bytes
    }

    private fun looksLikeZip(input: BufferedInputStream): Boolean {
        input.mark(4)
        val head = ByteArray(4)
        var read = 0
        while (read < 4) {
            val n = input.read(head, read, 4 - read)
            if (n < 0) break
            read += n
        }
        input.reset()
        if (read < 4) return false
        // "PK\u0003\u0004"（有文件）或 "PK\u0005\u0006"（空 zip）
        return head[0] == 'P'.code.toByte() && head[1] == 'K'.code.toByte() &&
            ((head[2] == 3.toByte() && head[3] == 4.toByte()) ||
                (head[2] == 5.toByte() && head[3] == 6.toByte()))
    }

    private fun truncateKeepingExtension(name: String, maxBytes: Int): String {
        val raw = name.toByteArray(Charsets.UTF_8)
        if (raw.size <= maxBytes) return name
        val dot = name.lastIndexOf('.')
        val extension = if (dot > 0) name.substring(dot) else ""
        val stemBudget = maxBytes - extension.toByteArray(Charsets.UTF_8).size
        var stem = name.substring(0, if (dot > 0) dot else name.length)
        while (stem.toByteArray(Charsets.UTF_8).size > stemBudget && stem.isNotEmpty()) {
            stem = stem.dropLast(1)
        }
        return stem + extension
    }

    private class NonClosingOutputStream(private val delegate: OutputStream) : OutputStream() {
        override fun write(b: Int) = delegate.write(b)
        override fun write(b: ByteArray, off: Int, len: Int) = delegate.write(b, off, len)
        override fun flush() = delegate.flush()
        override fun close() = delegate.flush()
    }

    private class DigestCountingOutputStream(
        private val target: OutputStream,
        private val digest: MessageDigest
    ) : OutputStream() {
        var count: Long = 0L
            private set

        override fun write(b: Int) {
            target.write(b)
            digest.update(b.toByte())
            count += 1
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            target.write(b, off, len)
            digest.update(b, off, len)
            count += len
        }

        override fun flush() = target.flush()
        override fun close() = Unit
    }
}
