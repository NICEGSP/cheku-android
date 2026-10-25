package com.cheku.app

import android.content.SharedPreferences

/** 一次导出的留痕 */
data class BackupExportRecord(
    val at: Long,
    val fileName: String,
    val bytes: Long,
    /** 这次导出涵盖了哪些范围 */
    val scope: String,
)

internal const val EXPORT_HISTORY_LIMIT = 20

// 用真实不可见字符做分隔，避免文件名里出现分隔符时把一条记录劈成两半。
private const val FIELD_SEPARATOR = '\u0001'
private const val RECORD_SEPARATOR = '\u0002'

/** 把条目编码成一行文本 */
internal fun encodeExportHistory(records: List<BackupExportRecord>): String =
    records.joinToString(RECORD_SEPARATOR.toString()) { record ->
        listOf(
            record.at.toString(),
            record.bytes.toString(),
            // 文本字段里真出现分隔符时替换掉：宁可显示得难看一点，也不能让记录解析错位
            record.fileName.replace(FIELD_SEPARATOR, '_').replace(RECORD_SEPARATOR, '_'),
            record.scope.replace(FIELD_SEPARATOR, '_').replace(RECORD_SEPARATOR, '_')
        ).joinToString(FIELD_SEPARATOR.toString())
    }

/** 解码 */
internal fun decodeExportHistory(raw: String?): List<BackupExportRecord> {
    if (raw.isNullOrBlank()) return emptyList()
    return raw.split(RECORD_SEPARATOR)
        .mapNotNull { line ->
            val parts = line.split(FIELD_SEPARATOR)
            if (parts.size < 4) return@mapNotNull null
            val at = parts[0].toLongOrNull() ?: return@mapNotNull null
            val bytes = parts[1].toLongOrNull() ?: return@mapNotNull null
            BackupExportRecord(at = at, fileName = parts[2], bytes = bytes, scope = parts[3])
        }
}

internal fun appendExportHistory(
    existing: List<BackupExportRecord>,
    record: BackupExportRecord
): List<BackupExportRecord> = (listOf(record) + existing).take(EXPORT_HISTORY_LIMIT)

/** 导出历史的存取 */
class BackupExportHistoryStore(private val preferences: SharedPreferences) {
    object Key {
        const val PREFERENCES_NAME = "backup_export_history"

        const val BUSINESS = "business"

        const val ARCHIVE = "archive"
    }

    /** 最新的在最前面 */
    fun read(kind: String): List<BackupExportRecord> = decodeExportHistory(preferences.getString(kind, null))

    fun append(kind: String, record: BackupExportRecord) {
        preferences.edit().putString(kind, encodeExportHistory(appendExportHistory(read(kind), record))).apply()
    }

    fun clear(kind: String) {
        preferences.edit().remove(kind).apply()
    }
}

/** 把字节数写成给人看的样子 */
internal fun formatExportBytes(bytes: Long): String = formatBytes(bytes)
