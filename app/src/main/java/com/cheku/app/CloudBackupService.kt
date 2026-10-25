package com.cheku.app

import java.io.InputStream
import java.io.OutputStream
import com.cheku.app.config.ServerConfig
import com.cheku.app.net.HttpPinning
import com.cheku.app.net.MAX_API_BODY_BYTES
import com.cheku.app.net.readBodyLimited
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** 云备份默认接口地址 */
internal val DEFAULT_BACKUP_BASE_URL: String get() = ServerConfig.websiteUrl + "/api/backup"

/** 单份备份硬上限 */
internal val MAX_BACKUP_BYTES: Long = 16L * 1024L * 1024L

/** 云端现有备份的元数据 */
internal data class BackupMeta(
    val createdAt: String?,
    val sizeBytes: Long,
    val appVersion: String?,
)

internal data class BeginResult(val uploadId: String)
internal data class CommitResult(val ok: Boolean, val sizeBytes: Long?)

internal sealed interface SizeCheck {
    data object Valid : SizeCheck
    data class TooLarge(val maxBytes: Long, val message: String) : SizeCheck
}

/** 客户端体积预检 */
internal fun validateBackupSize(bytes: Long): SizeCheck =
    if (bytes in 1..MAX_BACKUP_BYTES) {
        SizeCheck.Valid
    } else {
        SizeCheck.TooLarge(
            MAX_BACKUP_BYTES,
            "备份文件超过 ${MAX_BACKUP_BYTES / (1024L * 1024L)} MB 上限，无法上传",
        )
    }

internal fun buildBeginBody(sizeBytes: Long): String =
    JSONObject().put("sizeBytes", sizeBytes).toString()

internal fun buildCommitBody(uploadId: String, sha256: String, bytes: Long): String =
    JSONObject()
        .put("uploadId", uploadId)
        .put("sha256", sha256)
        .put("bytes", bytes)
        .toString()

internal fun parseBeginResponse(body: String): BeginResult {
    val uploadId = JSONObject(body).optString("uploadId").trim()
    require(uploadId.isNotBlank()) { "开始上传响应缺少 uploadId" }
    return BeginResult(uploadId)
}

internal fun parseMetaResponse(body: String): BackupMeta {
    val json = JSONObject(body)
    val meta = json.optJSONObject("meta") ?: json
    val manifest = meta.optJSONObject("manifest")
    fun text(obj: JSONObject?, key: String): String? =
        obj?.optString(key)?.takeIf { it.isNotBlank() && it != "null" }
    return BackupMeta(
        // 上传时间用 uploadedAt（比 manifest 里的构建时间更贴切），回落到旧字段
        createdAt = text(meta, "uploadedAt") ?: text(manifest, "createdAt") ?: text(meta, "createdAt"),
        // 服务端字段名是 bytes；兼容旧的 sizeBytes
        sizeBytes = meta.optLong("bytes", -1L).takeIf { it >= 0L } ?: meta.optLong("sizeBytes", 0L),
        appVersion = text(manifest, "appVersion") ?: text(meta, "appVersion"),
    )
}

internal fun parseCommitResponse(body: String): CommitResult {
    val json = JSONObject(body)
    return CommitResult(
        ok = json.optBoolean("ok", true),
        sizeBytes = json.optLong("sizeBytes", -1L).takeIf { it >= 0L },
    )
}

internal fun buildConfirmReplacePrompt(meta: BackupMeta): String {
    val time = meta.createdAt?.takeIf { it.isNotBlank() } ?: "未知时间"
    val size = if (meta.sizeBytes > 0) formatBackupSize(meta.sizeBytes) else "未知大小"
    val ver = meta.appVersion?.takeIf { it.isNotBlank() } ?: "未知版本"
    return "将替换云端现有备份（$time / $size / $ver）。此操作不可撤销，确定要继续吗？"
}

internal fun formatBackupSize(bytes: Long): String = when {
    bytes <= 0 -> "0 B"
    bytes < 1024 -> "$bytes B"
    bytes < 1024L * 1024L -> "${bytes / 1024L} KB"
    bytes < 1024L * 1024L * 1024L -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
    else -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
}

internal fun isExcludedSegmentError(body: String): Boolean =
    body.contains("已排除") ||
        body.contains("DOCUMENT ARCHIVE", ignoreCase = true) ||
        body.contains("excluded", ignoreCase = true) ||
        body.contains("证书包")

/** 备份请求的方向 */
internal enum class BackupFlow { Upload, Restore }

internal fun mapBackupError(code: Int, body: String, flow: BackupFlow = BackupFlow.Restore): ApiError {
    val serverMsg = extractServerMessage(body)
    val retryable = code == 429 || code in 500..599
    val message = when (code) {
        400 -> if (isExcludedSegmentError(body)) {
            "备份包中包含不允许上传的数据（人车证书等），云端已拒绝。" +
                "请只上传用车记录备份包（.che 主包），不要包含证书包。"
        } else {
            serverMsg?.let { "请求被拒绝：$it" } ?: "请求被拒绝（HTTP 400）"
        }
        401, 403 -> "登录已失效，请重新登录后再试"
        404 -> if (flow == BackupFlow.Upload) {
            "上传会话已失效（服务端可能刚重启），请重新上传一次"
        } else {
            "未找到云端备份，请先上传"
        }
        413 -> "备份文件超过 ${MAX_BACKUP_BYTES / (1024L * 1024L)} MB 上限，无法上传"
        429 -> serverMsg?.let { "操作过于频繁：$it" } ?: "操作过于频繁，请稍后再试"
        in 500..599 -> "服务器错误，请稍后重试"
        else -> serverMsg?.let { "备份失败（HTTP $code）：$it" } ?: "备份失败（HTTP $code）"
    }
    return ApiError(code, body, message, retryable)
}

/** 超过上限抛出的异常 */
internal class BackupTooLargeException(maxBytes: Long) :
    Exception("备份超过上限（${maxBytes / (1024L * 1024L)} MB）")

internal fun copyStreamWithLimit(
    input: InputStream,
    output: OutputStream,
    maxBytes: Long,
    onProgress: (Long) -> Unit = {},
): Long {
    val buffer = ByteArray(8 * 1024)
    var total = 0L
    while (true) {
        val read = input.read(buffer)
        if (read < 0) break
        total += read
        if (total > maxBytes) throw BackupTooLargeException(maxBytes)
        output.write(buffer, 0, read)
        onProgress(total)
    }
    return total
}

/** 大文件数据上传 */
internal fun interface BackupDataUploader {
    suspend fun upload(
        url: String,
        token: String,
        input: InputStream,
        contentLength: Long,
        onProgress: (Long) -> Unit,
    )
}

internal fun interface BackupDownloader {
    suspend fun download(
        url: String,
        token: String,
        output: OutputStream,
        onProgress: (Long) -> Unit,
    ): Long
}

/** 默认数据上传实现 */
internal class HttpBackupDataUploader : BackupDataUploader {
    override suspend fun upload(
        url: String,
        token: String,
        input: InputStream,
        contentLength: Long,
        onProgress: (Long) -> Unit,
    ) = withContext(Dispatchers.IO) {
        require(contentLength <= Int.MAX_VALUE) { "上传体积超过单次传输限制" }
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            HttpPinning.configure(connection)
            connection.requestMethod = "POST"
            connection.connectTimeout = 15_000
            connection.readTimeout = 300_000
            connection.useCaches = false
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.setRequestProperty("Content-Type", "application/octet-stream")
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Cache-Control", "no-store")
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setFixedLengthStreamingMode(contentLength.toInt())
            connection.doOutput = true
            connection.outputStream.use { out ->
                // 流式拷贝：8KB 缓冲，绝不把整包读进 ByteArray。
                copyStreamWithLimit(input, out, MAX_BACKUP_BYTES, onProgress)
            }
            val code = connection.responseCode
            if (code !in 200..299) {
                val err = connection.errorStream?.use { it.readBodyLimited(MAX_API_BODY_BYTES) }.orEmpty()
                // 这一步是"上传数据分片"：404 = 上传会话失效（服务端重启会清掉内存会话）
                throw CloudApiException(mapBackupError(code, err, BackupFlow.Upload))
            }
        } finally {
            connection.disconnect()
        }
    }
}

/** 默认数据下载实现 */
internal class HttpBackupDownloader : BackupDownloader {
    override suspend fun download(
        url: String,
        token: String,
        output: OutputStream,
        onProgress: (Long) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            HttpPinning.configure(connection)
            connection.requestMethod = "GET"
            connection.connectTimeout = 15_000
            connection.readTimeout = 300_000
            connection.useCaches = false
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.setRequestProperty("Accept", "application/octet-stream")
            connection.setRequestProperty("Cache-Control", "no-store")
            connection.setRequestProperty("Authorization", "Bearer $token")
            val code = connection.responseCode
            if (code !in 200..299) {
                val err = connection.errorStream?.use { it.readBodyLimited(MAX_API_BODY_BYTES) }.orEmpty()
                throw CloudApiException(mapBackupError(code, err))
            }
            connection.inputStream.use { inp ->
                // 下载同样走流式拷贝，并仍以 200MB 作为安全上限。
                copyStreamWithLimit(inp, output, MAX_BACKUP_BYTES, onProgress)
            }
        } finally {
            connection.disconnect()
        }
    }
}

/** 上传流程状态 */
internal sealed interface BackupUploadState {
    data object Idle : BackupUploadState
    data class NeedsConfirm(val meta: BackupMeta) : BackupUploadState
    data class Ready(val meta: BackupMeta) : BackupUploadState
    data class Begun(val uploadId: String) : BackupUploadState
    data class Uploaded(val uploadId: String) : BackupUploadState
    data class Completed(val sizeBytes: Long) : BackupUploadState
    data class Failed(val message: String, val retryable: Boolean) : BackupUploadState
}

internal sealed interface BackupUploadEvent {
    data class MetaLoaded(val meta: BackupMeta) : BackupUploadEvent
    data object Confirmed : BackupUploadEvent
    data class Begun(val uploadId: String) : BackupUploadEvent
    data object DataUploaded : BackupUploadEvent
    data class Committed(val sizeBytes: Long) : BackupUploadEvent
    data class Errored(val message: String, val retryable: Boolean) : BackupUploadEvent
}

internal fun advanceBackupState(state: BackupUploadState, event: BackupUploadEvent): BackupUploadState =
    when (event) {
        is BackupUploadEvent.MetaLoaded -> BackupUploadState.NeedsConfirm(event.meta)
        is BackupUploadEvent.Confirmed -> (state as? BackupUploadState.NeedsConfirm)
            ?.let { BackupUploadState.Ready(it.meta) } ?: state
        is BackupUploadEvent.Begun -> BackupUploadState.Begun(event.uploadId)
        is BackupUploadEvent.DataUploaded -> (state as? BackupUploadState.Begun)
            ?.let { BackupUploadState.Uploaded(it.uploadId) } ?: state
        is BackupUploadEvent.Committed -> BackupUploadState.Completed(event.sizeBytes)
        is BackupUploadEvent.Errored -> BackupUploadState.Failed(event.message, event.retryable)
    }

/** 云备份 App 端客户端 */
internal class CloudBackupService(
    private val tokenProvider: () -> String?,
    private val client: ApiClient = HttpUrlConnectionClient(),
    private val uploader: BackupDataUploader = HttpBackupDataUploader(),
    private val downloader: BackupDownloader = HttpBackupDownloader(),
    private val backupBaseUrl: String = DEFAULT_BACKUP_BASE_URL,
) {
    /** 取云端现有备份元数据 */
    suspend fun loadMeta(): BackupMeta {
        val token = requireToken()
        val resp = client.call(ApiRequest("GET", "$backupBaseUrl/meta", authToken = token))
        if (resp.code !in 200..299) throw CloudApiException(mapBackupError(resp.code, resp.body))
        return parseMetaResponse(resp.body)
    }

    /** 开始上传 */
    suspend fun beginUpload(sizeBytes: Long): BeginResult {
        val token = requireToken()
        val resp = client.call(
            ApiRequest("POST", "$backupBaseUrl/begin", buildBeginBody(sizeBytes), token),
        )
        // 上传方向：404 的含义是"上传会话失效"，不是"没有备份"
        if (resp.code !in 200..299) throw CloudApiException(mapBackupError(resp.code, resp.body, BackupFlow.Upload))
        return parseBeginResponse(resp.body)
    }

    /** 提交上传 */
    suspend fun commitUpload(uploadId: String, sha256: String, bytes: Long): CommitResult {
        val token = requireToken()
        val resp = client.call(
            ApiRequest("POST", "$backupBaseUrl/commit?uploadId=$uploadId", buildCommitBody(uploadId, sha256, bytes), token),
        )
        if (resp.code !in 200..299) throw CloudApiException(mapBackupError(resp.code, resp.body, BackupFlow.Upload))
        return parseCommitResponse(resp.body)
    }

    /** 上传业务数据主包 */
    suspend fun uploadBackup(
        input: InputStream,
        contentLength: Long,
        sha256: String,
        onProgress: (Long) -> Unit = {},
    ): CommitResult {
        when (val check = validateBackupSize(contentLength)) {
            is SizeCheck.Valid -> Unit
            is SizeCheck.TooLarge -> throw CloudApiException(
                ApiError(413, "", check.message, false),
            )
        }
        val token = requireToken()
        val begin = beginUpload(contentLength)
        uploader.upload(
            url = "$backupBaseUrl/data?uploadId=${begin.uploadId}",
            token = token,
            input = input,
            contentLength = contentLength,
            onProgress = onProgress,
        )
        return commitUpload(begin.uploadId, sha256, contentLength)
    }

    suspend fun downloadBackup(output: OutputStream, onProgress: (Long) -> Unit = {}) {
        val token = requireToken()
        downloader.download("$backupBaseUrl/download", token, output, onProgress)
    }

    /** 清空云端备份 */
    suspend fun purgeBackup() {
        val token = requireToken()
        val resp = client.call(ApiRequest("POST", "$backupBaseUrl/purge", "{}", token))
        if (resp.code !in 200..299) throw CloudApiException(mapBackupError(resp.code, resp.body))
    }

    private fun requireToken(): String =
        tokenProvider() ?: throw CloudApiException(ApiError(401, "", "请先登录账户", false))
}
