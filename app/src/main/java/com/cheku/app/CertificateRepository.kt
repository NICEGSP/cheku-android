package com.cheku.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.Locale

data class CertificateEntry(val key: String, val fileName: String, val mimeType: String, val kind: String, val data: ByteArray)

/** 暂存目录里的一份证书文件 */
private class StagedCertificateFile(
    val file: File,
    val fileName: String,
    val mimeType: String,
    val kind: String,
)

/** 某一份证书操作失败的原因 */
data class CertificateFailure(val key: String, val reason: String)

data class CertificateReadResult(
    val entries: List<CertificateEntry>,
    val failures: List<CertificateFailure>,
) {
    val isComplete: Boolean get() = failures.isEmpty()
}

data class CertificateSourceSet(
    val sources: List<DataExporter.CertificateSource>,
    val failures: List<CertificateFailure>,
) {
    val isComplete: Boolean get() = failures.isEmpty()
}

/** 人车证书的本地存储 */
class CertificateRepository(private val context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    private fun certificatesDir() = File(context.filesDir, DIRECTORY_NAME)

    fun uriFor(key: String): Uri? = preferences.getString(key, null)
        ?.takeIf { it.isNotBlank() }
        ?.let(Uri::parse)

    fun viewerUriFor(key: String): Uri? = uriFor(key)?.let { uri ->
        if (uri.scheme == "file") {
            val path = uri.path ?: return@let null
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(path))
        } else {
            uri
        }
    }

    fun displayName(key: String): String? = uriFor(key)?.let(::displayName)

    fun mimeTypeFor(key: String): String? = uriFor(key)?.let { uri ->
        context.contentResolver.getType(uri) ?: mimeTypeFromName(uri)
    }

    @Throws(IOException::class)
    fun save(key: String, uri: Uri) {
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (exception: SecurityException) {
            // Some document providers do not expose a persistable grant. Keep the
            // URI when it is still readable in the current app session.
            val readable = context.contentResolver.openInputStream(uri)?.use { true } ?: false
            if (!readable) throw exception
        }

        val directory = certificatesDir().apply { mkdirs() }
        val target = File(directory, internalCertificateFileName(key, displayName(uri)))
        context.contentResolver.openInputStream(uri)?.use { input ->
            writeToInternalFile(input, target, context.filesDir.usableSpace)
        } ?: throw IOException("无法读取证书文件")

        // 字节已进内部，外部 URI 的持久读权限不再需要。
        val previous = uriFor(key)
        if (previous != null && previous.scheme != "file") {
            runCatching {
                context.contentResolver.releasePersistableUriPermission(
                    previous,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
        }
        preferences.edit().putString(key, Uri.fromFile(target).toString()).apply()
    }

    fun remove(key: String) {
        uriFor(key)?.let { uri ->
            if (uri.scheme == "file") {
                deleteInternalFile(uri)
            } else {
                runCatching {
                    context.contentResolver.releasePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
            }
        }
        preferences.edit().remove(key).apply()
    }

    fun migrateExternalCertificates(): Int {
        if (preferences.getBoolean(KEY_MIGRATION_DONE, false)) return 0
        val directory = certificatesDir().apply { mkdirs() }
        val editor = preferences.edit()
        var failed = 0
        preferences.all.forEach { (key, raw) ->
            if (key == KEY_MIGRATION_DONE) return@forEach
            val value = raw as? String ?: return@forEach
            if (key.endsWith(VIN_KEY_SUFFIX)) return@forEach
            val uri = runCatching { Uri.parse(value) }.getOrNull() ?: return@forEach
            if (uri.scheme == "file") return@forEach
            val ok = runCatching {
                val target = File(directory, internalCertificateFileName(key, displayName(uri)))
                context.contentResolver.openInputStream(uri)?.use { input ->
                    writeToInternalFile(input, target, context.filesDir.usableSpace)
                } ?: throw IOException("无法读取原文件")
                editor.putString(key, Uri.fromFile(target).toString())
            }.isSuccess
            if (!ok) failed++
        }
        editor.putBoolean(KEY_MIGRATION_DONE, true).apply()
        return failed
    }

    /** 读取全部证书字节 */
    fun readAllCertificates(): CertificateReadResult {
        val entries = mutableListOf<CertificateEntry>()
        val failures = mutableListOf<CertificateFailure>()
        preferences.all.forEach { (key, raw) ->
            val value = raw as? String
            if (value == null) return@forEach
            runCatching {
                if (key.endsWith(VIN_KEY_SUFFIX)) {
                    // 车架号是纯文本项，直接存在 prefs 里，没有独立文件
                    entries += CertificateEntry(key, "$key.txt", "text/plain", "text", value.toByteArray())
                } else {
                    val uri = Uri.parse(value)
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: throw IOException("证书文件读不到（$value）")
                    if (bytes.size.toLong() > MAX_SINGLE_CERTIFICATE_BYTES) {
                        throw IOException("单个证书超过 ${formatBytes(MAX_SINGLE_CERTIFICATE_BYTES)}")
                    }
                    entries += CertificateEntry(
                        key = key,
                        fileName = displayName(uri),
                        mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream",
                        kind = "file",
                        data = bytes
                    )
                }
            }.exceptionOrNull()?.let { error ->
                failures += CertificateFailure(key, error.message ?: error.javaClass.simpleName)
            }
        }
        return CertificateReadResult(entries = entries, failures = failures)
    }

    /** 只要条目、忽略读取失败 */
    fun certificateEntries(): List<CertificateEntry> = readAllCertificates().entries

    fun certificateSources(): CertificateSourceSet {
        val sources = mutableListOf<DataExporter.CertificateSource>()
        val failures = mutableListOf<CertificateFailure>()
        preferences.all.forEach { (key, raw) ->
            val value = raw as? String
            if (value == null) return@forEach
            runCatching {
                if (key.endsWith(VIN_KEY_SUFFIX)) {
                    val bytes = value.toByteArray()
                    sources += DataExporter.CertificateSource(key, "$key.txt", "text/plain", "text") {
                        bytes.inputStream()
                    }
                } else {
                    val uri = Uri.parse(value)
                    certificateSize(uri)?.let { size ->
                        if (size > MAX_SINGLE_CERTIFICATE_BYTES) {
                            throw IOException("单个证书超过 ${formatBytes(MAX_SINGLE_CERTIFICATE_BYTES)}")
                        }
                    }
                    context.contentResolver.openInputStream(uri)?.close()
                        ?: throw IOException("证书文件读不到（$value）")
                    sources += DataExporter.CertificateSource(
                        key = key,
                        fileName = displayName(uri),
                        mimeType = context.contentResolver.getType(uri)
                            ?: mimeTypeFromName(uri)
                            ?: "application/octet-stream",
                        kind = "file",
                        open = {
                            context.contentResolver.openInputStream(uri)
                                ?: throw IOException("证书文件读不到（$value）")
                        }
                    )
                }
            }.exceptionOrNull()?.let { error ->
                failures += CertificateFailure(key, error.message ?: error.javaClass.simpleName)
            }
        }
        return CertificateSourceSet(sources = sources, failures = failures)
    }

    fun beginStaging(): CertificateStaging = CertificateStaging()

    /** 一次证书包导入的暂存区 */
    inner class CertificateStaging {
        private val dir = File(context.filesDir, STAGING_DIRECTORY_NAME)
        private val staged = LinkedHashMap<String, StagedCertificateFile>()
        private var finished = false

        fun sink(): CertificateStagingSink = CertificateStagingSink { key, fileName, mimeType, kind ->
            dir.mkdirs()
            val file = File(dir, internalCertificateFileName(key, fileName))
            staged[key] = StagedCertificateFile(file, fileName, mimeType, kind)
            file.outputStream()
        }

        /** 把暂存里的证书移入正式目录并写 prefs */
        fun commit(): List<CertificateFailure> {
            val directory = certificatesDir().apply { mkdirs() }
            val editor = preferences.edit()
            val failures = mutableListOf<CertificateFailure>()
            staged.forEach { (key, entry) ->
                runCatching {
                    if (entry.kind == "text") {
                        // 车架号这类纯文本项存 prefs，不留文件
                        editor.putString(key, entry.file.readText())
                    } else {
                        val target = File(directory, entry.file.name)
                        if (target.exists() && !target.delete()) {
                            throw IOException("无法替换已有证书文件")
                        }
                        if (!entry.file.renameTo(target)) throw IOException("无法写入证书文件")
                        editor.putString(key, Uri.fromFile(target).toString())
                    }
                }.exceptionOrNull()?.let { error ->
                    failures += CertificateFailure(key, error.message ?: error.javaClass.simpleName)
                }
            }
            editor.apply()
            discard()
            return failures
        }

        /** 校验没过 */
        fun discard() {
            dir.deleteRecursively()
            staged.clear()
            finished = true
        }

        /** 是否已经收尾 */
        val isFinished: Boolean get() = finished
    }

    private fun certificateSize(uri: Uri): Long? {        if (uri.scheme == "file") return uri.path?.let { File(it).length() }
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val index = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (index < 0 || cursor.isNull(index)) null else cursor.getLong(index)
            }
        }.getOrNull()
    }

    private fun mimeTypeFromName(uri: Uri): String? =
        when (uri.toString().substringAfterLast('.', "").lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "pdf" -> "application/pdf"
            "doc" -> "application/msword"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            else -> null
        }

    /** 从备份恢复证书 */
    @Throws(IOException::class)
    fun restoreCertificates(entries: List<CertificateEntry>): List<CertificateFailure> {
        if (entries.isEmpty()) return emptyList()
        val required = entries.filter { it.kind != "text" }.sumOf { it.data.size.toLong() }
        val free = context.filesDir.usableSpace
        if (required + MIN_FREE_RESERVE_BYTES > free) {
            throw IOException(
                "存储空间不足：证书需要 ${formatBytes(required)}，当前可用 ${formatBytes(free)}"
            )
        }
        val directory = certificatesDir().apply { mkdirs() }
        val editor = preferences.edit()
        val failures = mutableListOf<CertificateFailure>()
        entries.forEach { entry ->
            runCatching {
                if (entry.kind == "text") {
                    editor.putString(entry.key, String(entry.data))
                } else {
                    val file = File(directory, internalCertificateFileName(entry.key, entry.fileName))
                    writeToInternalFile(entry.data.inputStream(), file, context.filesDir.usableSpace)
                    editor.putString(entry.key, Uri.fromFile(file).toString())
                }
            }.exceptionOrNull()?.let { error ->
                failures += CertificateFailure(entry.key, error.message ?: error.javaClass.simpleName)
            }
        }
        editor.apply()
        return failures
    }

    @Throws(IOException::class)
    fun export(key: String, destination: Uri) {
        val source = uriFor(key) ?: throw IOException("未找到证书文件")
        context.contentResolver.openInputStream(source)?.use { input ->
            context.contentResolver.openOutputStream(destination)?.use(input::copyTo)
                ?: throw IOException("无法写入导出位置")
        } ?: throw IOException("无法读取证书文件")
    }

    private fun deleteInternalFile(uri: Uri) {
        val path = uri.path ?: return
        val directory = certificatesDir()
        val file = File(path)
        if (!file.absolutePath.startsWith(directory.absolutePath + File.separator)) return
        runCatching { file.delete() }
    }

    /** 显示名 */
    private fun displayName(uri: Uri): String {
        val queried = runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
                    } else null
                }
        }.getOrNull()
        return queried?.takeIf { it.isNotBlank() }
            ?: uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
            ?: "证书文件"
    }

    companion object {
        const val PREFERENCES_NAME = "local_document_archive"
        const val DIRECTORY_NAME = "document_archive"

        /** 证书包导入的暂存目录 */
        internal const val STAGING_DIRECTORY_NAME = "document_archive_staging"
        const val ID_FRONT = "id_front"
        const val ID_BACK = "id_back"
        const val LICENSE_FRONT = "license_front"
        const val LICENSE_BACK = "license_back"

        private const val KEY_MIGRATION_DONE = "external_migration_v1_done"

        internal const val VIN_KEY_SUFFIX = "_vin"

        fun vehicleVinKey(vehicleId: String) = "vehicle_${vehicleId}_vin"
        fun vehicleCertificateKey(vehicleId: String, suffix: String) = "vehicle_${vehicleId}_$suffix"
    }
}

internal const val MIN_FREE_RESERVE_BYTES = 16L * 1024L * 1024L
internal const val CERTIFICATE_IO_BUFFER = 64 * 1024
internal const val MAX_SINGLE_CERTIFICATE_BYTES: Long = 125L * 1024L * 1024L
private const val MAX_INTERNAL_FILE_NAME_BYTES = 240

internal fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024 * 1024 -> String.format(Locale.US, "%.1f GB", bytes / 1024.0 / 1024 / 1024)
    bytes >= 1024L * 1024 -> String.format(Locale.US, "%.1f MB", bytes / 1024.0 / 1024)
    bytes >= 1024 -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
    else -> "$bytes B"
}

internal fun truncateToUtf8Bytes(value: String, maxBytes: Int): String {
    if (value.toByteArray(Charsets.UTF_8).size <= maxBytes) return value
    var end = value.length
    while (end > 0 && value.substring(0, end).toByteArray(Charsets.UTF_8).size > maxBytes) end--
    return value.substring(0, end)
}

/** 只保留文件系统安全的字符，并去掉首尾的点 */
internal fun sanitizeCertificateFileName(name: String): String =
    name.replace(Regex("[^A-Za-z0-9._\\-\\u4e00-\\u9fa5]"), "_")
        .trim('.')
        .ifBlank { "certificate" }

internal fun internalCertificateFileName(key: String, fileName: String): String {
    val prefix = "${key}_"
    val safe = sanitizeCertificateFileName(fileName)
    val budget = (MAX_INTERNAL_FILE_NAME_BYTES - prefix.toByteArray(Charsets.UTF_8).size).coerceAtLeast(8)
    if (safe.toByteArray(Charsets.UTF_8).size <= budget) return prefix + safe

    val dot = safe.lastIndexOf('.')
    val extension = if (dot > 0 && safe.length - dot <= 9) safe.substring(dot) else ""
    val stem = if (extension.isEmpty()) safe else safe.substring(0, dot)
    val stemBudget = (budget - extension.toByteArray(Charsets.UTF_8).size).coerceAtLeast(8)
    return prefix + truncateToUtf8Bytes(stem, stemBudget) + extension
}

internal fun writeToInternalFile(
    input: InputStream,
    target: File,
    freeSpace: Long,
    reserveBytes: Long = MIN_FREE_RESERVE_BYTES,
    maxBytes: Long = MAX_SINGLE_CERTIFICATE_BYTES
): Long {
    if (freeSpace <= reserveBytes) {
        throw IOException("存储空间不足：可用 ${formatBytes(freeSpace)}，需保留 ${formatBytes(reserveBytes)}")
    }
    target.parentFile?.mkdirs()
    val temporary = File(target.parentFile, "${target.name}.part")
    try {
        var written = 0L
        BufferedInputStream(input, CERTIFICATE_IO_BUFFER).use { source ->
            BufferedOutputStream(temporary.outputStream(), CERTIFICATE_IO_BUFFER).use { output ->
                val buffer = ByteArray(CERTIFICATE_IO_BUFFER)
                while (true) {
                    val count = source.read(buffer)
                    if (count < 0) break
                    written += count
                    if (written > maxBytes) {
                        throw IOException("单个证书文件超过 ${formatBytes(maxBytes)}")
                    }
                    if (written + reserveBytes > freeSpace) {
                        throw IOException(
                            "存储空间不足：写入 ${formatBytes(written)} 后可用空间将低于保留值 ${formatBytes(reserveBytes)}"
                        )
                    }
                    output.write(buffer, 0, count)
                }
            }
        }
        if (target.exists() && !target.delete()) {
            throw IOException("无法替换已有证书文件")
        }
        if (!temporary.renameTo(target)) throw IOException("无法写入证书文件")
        return written
    } catch (error: Throwable) {
        temporary.delete()
        throw error
    }
}
