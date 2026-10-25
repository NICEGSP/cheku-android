package com.cheku.app

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.cheku.app.config.RuntimeConfigProvider
import java.io.BufferedOutputStream
import java.io.File
import com.cheku.app.net.HttpPinning
import com.cheku.app.net.MAX_API_BODY_BYTES
import com.cheku.app.net.readBodyLimited
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class AppUpdateInfo(
    val version: String,
    val publishedAt: String?,
    val notes: String,
    val forceUpdate: Boolean,
    val forceUpdateVersion: String? = null,
    val downloadUrl: String? = null,
)

object AppUpdateService {
    private const val MAX_APK_BYTES = 250L * 1024L * 1024L
    private const val MAX_REDIRECTS = 5
    // 出口地址来自运行时配置；拉不到配置时 RuntimeConfigProvider 内部回落 BuildConfig。
    private val websiteBaseUrl get() = RuntimeConfigProvider.websiteBaseUrl()
    val downloadPageUrl get() = RuntimeConfigProvider.downloadUrl()

    suspend fun loadLatest(): AppUpdateInfo = withContext(Dispatchers.IO) {
        check(websiteBaseUrl.isNotBlank()) { "官网地址未配置" }
        val connection = URL(RuntimeConfigProvider.latestUrl()).openConnection() as HttpURLConnection
        try {
            HttpPinning.configure(connection)
            connection.connectTimeout = 8_000; connection.readTimeout = 8_000; connection.useCaches = false
            connection.setRequestProperty("User-Agent", "CheKu/${BuildConfig.VERSION_NAME} (Android)")
            require(connection.responseCode in 200..299) { "官网返回 HTTP ${connection.responseCode}" }
            parseLatestUpdate(connection.inputStream.use { it.readBodyLimited(MAX_API_BODY_BYTES) })
        } finally { connection.disconnect() }
    }

    suspend fun loadTransferPolicyKbps(): Int? = withContext(Dispatchers.IO) {
        if (websiteBaseUrl.isBlank()) return@withContext null
        val connection = try {
            URL("$websiteBaseUrl/api/app/policy").openConnection() as HttpURLConnection
        } catch (error: Throwable) {
            return@withContext null
        }
        try {
            HttpPinning.configure(connection)
            connection.connectTimeout = 6_000
            connection.readTimeout = 6_000
            connection.useCaches = false
            connection.setRequestProperty("User-Agent", "CheKu/${BuildConfig.VERSION_NAME} (Android)")
            if (connection.responseCode !in 200..299) return@withContext null
            val body = connection.inputStream.use { it.readBodyLimited(MAX_API_BODY_BYTES) }
            JSONObject(body).optInt("backupRateLimitKbps", -1).takeIf { it >= 0 }
        } catch (error: Throwable) {
            null
        } finally {
            connection.disconnect()
        }
    }

    suspend fun downloadLatestApk(context: Context, version: String, onProgress: (Long, Long?) -> Unit = { _, _ -> }): File = withContext(Dispatchers.IO) {
        require(websiteBaseUrl.startsWith("https://")) { "官网更新地址必须使用 HTTPS" }
        val directory = File(context.cacheDir, "app_updates").apply { mkdirs() }
        val safeVersion = version.replace(Regex("[^0-9A-Za-z._-]"), "_")
        val target = File(directory, "CheKu_$safeVersion.apk")
        val temporary = File(directory, "${target.name}.download").apply { delete() }
        var url = URL(downloadPageUrl)
        var connection: HttpURLConnection? = null
        try {
            for (redirect in 0..MAX_REDIRECTS) {
                require(url.protocol.equals("https", true)) { "下载地址必须使用 HTTPS" }
                connection?.disconnect()
                connection = (url.openConnection() as HttpURLConnection).apply {
                    HttpPinning.configure(this)
                    instanceFollowRedirects = false; connectTimeout = 15_000; readTimeout = 30_000; useCaches = false
                    setRequestProperty("User-Agent", "CheKu/${BuildConfig.VERSION_NAME} (Android updater)")
                }
                val code = connection.responseCode
                if (code in 300..399) {
                    val location = connection.getHeaderField("Location")?.trim().orEmpty()
                    require(location.isNotBlank()) { "下载地址重定向无效" }
                    require(redirect < MAX_REDIRECTS) { "下载地址重定向次数过多" }
                    url = URL(url, location)
                    continue
                }
                require(code in 200..299) { "下载失败：HTTP $code" }
                val total = contentLengthOrNull(connection)
                require(total == null || total <= MAX_APK_BYTES) { "安装包过大" }
                var downloaded = 0L
                connection.inputStream.buffered().use { input -> BufferedOutputStream(temporary.outputStream(), 8 * 1024).use { output ->
                    val buffer = ByteArray(8 * 1024)
                    while (true) {
                        val count = input.read(buffer); if (count < 0) break
                        downloaded += count; require(downloaded <= MAX_APK_BYTES) { "安装包过大" }
                        output.write(buffer, 0, count); onProgress(downloaded, total)
                    }
                } }
                require(downloaded > 0 && (total == null || downloaded == total)) { "安装包下载不完整" }
                if (target.exists()) target.delete()
                require(temporary.renameTo(target)) { "无法保存安装包" }
                return@withContext target
            }
            error("下载地址重定向无效")
        } catch (error: Throwable) { temporary.delete(); throw error } finally { connection?.disconnect() }
    }

    fun launchInstaller(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** 探测更新包体积 */
    suspend fun probeApkSize(url: String): Long? = withContext(Dispatchers.IO) {
        if (!url.startsWith("https://")) return@withContext null
        var current = url
        try {
            for (redirect in 0..MAX_REDIRECTS) {
                val head = (URL(current).openConnection() as HttpURLConnection).apply {
                    HttpPinning.configure(this)
                    requestMethod = "HEAD"
                    instanceFollowRedirects = false
                    connectTimeout = 8_000
                    readTimeout = 8_000
                    useCaches = false
                    setRequestProperty("User-Agent", "CheKu/${BuildConfig.VERSION_NAME} (updater probe)")
                }
                val code = head.responseCode
                if (code in 300..399) {
                    val location = head.getHeaderField("Location")?.trim().orEmpty()
                    head.disconnect()
                    if (location.isBlank()) return@withContext null
                    current = URL(URL(current), location).toString()
                    continue
                }
                val declared = if (code in 200..299) contentLengthOrNull(head) ?: -1L else -1L
                head.disconnect()
                if (declared > 0L) return@withContext declared
                if (code in 200..299) return@withContext probeSizeByRange(current)
                return@withContext null
            }
            null
        } catch (error: Throwable) {
            android.util.Log.d("CheKuUpdate", "探测安装包体积失败：${error.message}")
            null
        }
    }

    /** 取响应的整包字节数 */
    private fun contentLengthOrNull(connection: java.net.URLConnection): Long? {
        val len = connection.contentLength.toLong()
        return if (len > 0) len else null
    }

    private fun probeSizeByRange(url: String): Long? {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            HttpPinning.configure(this)
            connectTimeout = 8_000
            readTimeout = 8_000
            useCaches = false
            setRequestProperty("Range", "bytes=0-0")
            setRequestProperty("User-Agent", "CheKu/${BuildConfig.VERSION_NAME} (updater probe)")
        }
        return try {
            if (connection.responseCode !in 200..299) {
                null
            } else {
                val fromRange = connection.getHeaderField("Content-Range")
                    ?.substringAfterLast('/')
                    ?.trim()
                    ?.toLongOrNull()
                fromRange?.takeIf { it > 0L } ?: contentLengthOrNull(connection)
            }
        } catch (error: Throwable) {
            null
        } finally {
            connection.disconnect()
        }
    }
}

internal fun parseLatestUpdate(body: String): AppUpdateInfo {
    val json = JSONObject(body); val version = json.optString("version").trim()
    require(version.isNotBlank()) { "版本号为空" }
    fun text(key: String): String? =
        json.optString(key).trim().takeUnless { it.isBlank() || it == "null" }
    return AppUpdateInfo(
        version = version,
        publishedAt = text("publishedAt"),
        notes = json.optString("notes").trim(),
        forceUpdate = json.optBoolean("forceUpdate", false),
        forceUpdateVersion = text("forceUpdateVersion"),
        downloadUrl = text("downloadUrl"),
    )
}
internal fun isVersionNewer(latest: String, current: String) = compareVersionNames(latest, current) > 0
internal fun isForceUpdateRequired(forceVersion: String?, current: String) = !forceVersion.isNullOrBlank() && isVersionNewer(forceVersion, current)
internal fun compareVersionNames(left: String, right: String): Int { val a=versionNumberParts(left); val b=versionNumberParts(right); if(a.isEmpty()||b.isEmpty()) return 0; return (0 until maxOf(a.size,b.size)).firstNotNullOfOrNull { i -> a.getOrElse(i){0}.compareTo(b.getOrElse(i){0}).takeIf{it!=0} } ?: 0 }
private fun versionNumberParts(value: String) = Regex("\\d+").findAll(value).mapNotNull { it.value.toLongOrNull() }.toList()
internal fun formatReleaseDay(value: String?) = if(value.isNullOrBlank()) "待发布" else runCatching { Instant.parse(value).atZone(ZoneId.of("Asia/Shanghai")).toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE) }.getOrElse { value.take(10) }
