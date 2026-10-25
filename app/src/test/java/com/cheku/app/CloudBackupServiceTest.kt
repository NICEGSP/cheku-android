package com.cheku.app

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudBackupServiceTest {
    private class StubDataUploader : BackupDataUploader {
        var lastUrl: String? = null
        var lastToken: String? = null
        var lastContentLength: Long = -1L
        var bytesSeen: Long = 0L
        override suspend fun upload(
            url: String,
            token: String,
            input: java.io.InputStream,
            contentLength: Long,
            onProgress: (Long) -> Unit,
        ) {
            lastUrl = url
            lastToken = token
            lastContentLength = contentLength
            // 通过底层流式拷贝排空输入，证明数据是按块消费的，而非整包读进 ByteArray。
            val sink = ByteArrayOutputStream()
            bytesSeen = copyStreamWithLimit(input, sink, MAX_BACKUP_BYTES, onProgress)
        }
    }

    private class StubDownloader(private val payload: ByteArray) : BackupDownloader {
        override suspend fun download(url: String, token: String, output: java.io.OutputStream, onProgress: (Long) -> Unit): Long {
            output.write(payload)
            onProgress(payload.size.toLong())
            return payload.size.toLong()
        }
    }

    private class StubApiClient(private val responder: (ApiRequest) -> ApiResponse) : ApiClient {
        override suspend fun call(request: ApiRequest): ApiResponse = responder(request)
    }

    @Test
    fun backupSizeValidation() {
        assertTrue(validateBackupSize(1L) is SizeCheck.Valid)
        assertTrue(validateBackupSize(MAX_BACKUP_BYTES) is SizeCheck.Valid) // 边界含
        assertTrue(validateBackupSize(MAX_BACKUP_BYTES + 1) is SizeCheck.TooLarge) // 超 1 字节即拒
        assertTrue(validateBackupSize(0L) is SizeCheck.TooLarge) // 空包不允许
    }

    @Test
    fun tooLargeMessageMentionsLimit() {
        val check = validateBackupSize(MAX_BACKUP_BYTES + 1)
        assertTrue(check is SizeCheck.TooLarge)
        val limitMb = MAX_BACKUP_BYTES / (1024 * 1024)
        assertEquals("备份文件超过 $limitMb MB 上限，无法上传", (check as SizeCheck.TooLarge).message)
    }

    @Test
    fun formatBackupSizeRendersHumanReadable() {
        assertEquals("0 B", formatBackupSize(0))
        assertEquals("512 B", formatBackupSize(512))
        assertEquals("10 KB", formatBackupSize(10L * 1024L))
        assertEquals("1.0 MB", formatBackupSize(1024L * 1024L))
        assertEquals("${MAX_BACKUP_BYTES / (1024.0 * 1024.0)} MB", formatBackupSize(MAX_BACKUP_BYTES))
    }

    @Test
    fun beginAndCommitBodies() {
        JSONObject(buildBeginBody(1234L)).let { assertEquals(1234L, it.getLong("sizeBytes")) }
        JSONObject(buildCommitBody("up1", "abc", 1234L)).let {
            assertEquals("up1", it.getString("uploadId"))
            assertEquals("abc", it.getString("sha256"))
            assertEquals(1234L, it.getLong("bytes"))
        }
    }

    @Test
    fun parseResponses() {
        assertEquals("up1", parseBeginResponse("""{"uploadId":"up1"}""").uploadId)
        val meta = parseMetaResponse("""{"createdAt":"2026-01-01T00:00:00Z","sizeBytes":1024,"appVersion":"v0.6.4"}""")
        assertEquals("2026-01-01T00:00:00Z", meta.createdAt)
        assertEquals(1024L, meta.sizeBytes)
        assertEquals("v0.6.4", meta.appVersion)
        val commit = parseCommitResponse("""{"ok":true,"sizeBytes":1024}""")
        assertEquals(true, commit.ok)
        assertEquals(1024L, commit.sizeBytes)
    }

    @Test
    fun excludedSegmentErrorDetection() {
        assertTrue(isExcludedSegmentError("""{"message":"包含已排除的数据段"}"""))
        assertTrue(isExcludedSegmentError("package contains DOCUMENT ARCHIVE segment"))
        assertTrue(isExcludedSegmentError("""{"error":"excluded segment"}"""))
    }

    @Test
    fun mapBackupErrorGivesClearTextForExcludedSegment() {
        val err = mapBackupError(400, """{"message":"包含已排除的数据段（证书）"}""")
        assertTrue(err.userMessage.contains("人车证书"))
        assertTrue(err.userMessage.contains("用车记录备份包"))
        assertFalse(err.retryable)
    }

    @Test
    fun mapBackupErrorOtherCodes() {
        assertEquals("备份文件超过 ${MAX_BACKUP_BYTES / (1024 * 1024)} MB 上限，无法上传", mapBackupError(413, "{}").userMessage)
        assertTrue(mapBackupError(503, "{}").retryable) // 云盘不可用可重试
        assertEquals("登录已失效，请重新登录后再试", mapBackupError(401, "{}").userMessage)
        assertTrue(mapBackupError(429, "{}").retryable)
        assertFalse(mapBackupError(400, """{"message":"其它"}""").userMessage.contains("证书"))
    }

    @Test
    fun confirmReplacePromptInlinesMeta() {
        val prompt = buildConfirmReplacePrompt(
            BackupMeta(createdAt = "2026-01-01T00:00:00Z", sizeBytes = 1024L * 1024L, appVersion = "v0.6.4"),
        )
        assertTrue(prompt.contains("2026-01-01T00:00:00Z"))
        assertTrue(prompt.contains("1.0 MB"))
        assertTrue(prompt.contains("v0.6.4"))
        assertTrue(prompt.contains("不可撤销"))
    }

    @Test
    fun copyStreamWithLimitCopiesFully() {
        val src = ByteArrayInputStream(ByteArray(1000) { it.toByte() })
        val out = ByteArrayOutputStream()
        val n = copyStreamWithLimit(src, out, 10_000)
        assertEquals(1000L, n)
        assertArrayEquals(ByteArray(1000) { it.toByte() }, out.toByteArray())
    }

    @Test(expected = BackupTooLargeException::class)
    fun copyStreamWithLimitAbortsWhenOverLimit() {
        val src = ByteArrayInputStream(ByteArray(1000) { 1 })
        val out = ByteArrayOutputStream()
        copyStreamWithLimit(src, out, 500) // 上限 500 < 1000，应在中途中止
    }

    @Test
    fun copyStreamWithLimitBoundaryInclusive() {
        val src = ByteArrayInputStream(ByteArray(500) { 1 })
        val out = ByteArrayOutputStream()
        assertEquals(500L, copyStreamWithLimit(src, out, 500)) // 恰好等于上限不抛
    }

    @Test
    fun backupUploadStateMachine() {
        var state: BackupUploadState = BackupUploadState.Idle
        val meta = BackupMeta("t", 1, "v")
        state = advanceBackupState(state, BackupUploadEvent.MetaLoaded(meta))
        assertTrue(state is BackupUploadState.NeedsConfirm)
        state = advanceBackupState(state, BackupUploadEvent.Confirmed)
        assertTrue(state is BackupUploadState.Ready)
        state = advanceBackupState(state, BackupUploadEvent.Begun("up1"))
        assertTrue(state is BackupUploadState.Begun)
        state = advanceBackupState(state, BackupUploadEvent.DataUploaded)
        assertTrue(state is BackupUploadState.Uploaded)
        state = advanceBackupState(state, BackupUploadEvent.Committed(123))
        assertTrue(state is BackupUploadState.Completed)
        state = advanceBackupState(state, BackupUploadEvent.Errored("x", true))
        assertTrue(state is BackupUploadState.Failed)
    }

    @Test
    fun uploadBackupOrchestratesBeginDataCommit() = runTest {
        val uploader = StubDataUploader()
        // 业务数据主包内容（仅业务，无证书）
        val payload = ByteArray(2048) { 7 }
        val client = StubApiClient { req ->
            when {
                req.url.endsWith("/begin") -> ApiResponse(200, """{"uploadId":"up_xyz"}""")
                req.url.contains("/commit") -> {
                    assertTrue(
                        "commit 必须带 uploadId 查询参数，实际 URL=${req.url}",
                        req.url.contains("uploadId=up_xyz"),
                    )
                    ApiResponse(200, """{"ok":true,"sizeBytes":2048}""")
                }
                else -> ApiResponse(500, "{}")
            }
        }
        val service = CloudBackupService(
            tokenProvider = { "tok" },
            client = client,
            uploader = uploader,
            downloader = StubDownloader(byteArrayOf()),
        )
        val result = service.uploadBackup(
            input = ByteArrayInputStream(payload),
            contentLength = payload.size.toLong(),
            sha256 = "deadbeef",
        )
        assertEquals(true, result.ok)
        assertEquals(2048L, result.sizeBytes)
        assertEquals("tok", uploader.lastToken)
        assertEquals(payload.size.toLong(), uploader.lastContentLength)
        assertEquals(payload.size.toLong(), uploader.bytesSeen)
        assertTrue(uploader.lastUrl!!.contains("uploadId=up_xyz"))
    }

    @Test(expected = CloudApiException::class)
    fun uploadBackupRejectsOver200MBBeforeNetwork() = runTest {
        val uploader = StubDataUploader()
        val service = CloudBackupService(
            tokenProvider = { "tok" },
            client = StubApiClient { ApiResponse(200, "{}") },
            uploader = uploader,
            downloader = StubDownloader(byteArrayOf()),
        )
        // 超过 200MB：应在任何网络请求前抛 413，uploader 不应被调用
        try {
            service.uploadBackup(
                input = ByteArrayInputStream(ByteArray(1) { 0 }),
                contentLength = MAX_BACKUP_BYTES + 1,
                sha256 = "x",
            )
        } finally {
            assertEquals(-1L, uploader.lastContentLength) // uploader 未被调用
        }
    }

    @Test
    fun downloadBackupStreamsToOutput() = runTest {
        val payload = byteArrayOf(1, 2, 3, 4)
        val service = CloudBackupService(
            tokenProvider = { "tok" },
            client = StubApiClient { ApiResponse(200, "{}") },
            uploader = StubDataUploader(),
            downloader = StubDownloader(payload),
        )
        val out = ByteArrayOutputStream()
        service.downloadBackup(out)
        assertArrayEquals(payload, out.toByteArray())
    }

    @Test
    fun requireTokenThrowsWhenNotLoggedIn() = runTest {
        val service = CloudBackupService(
            tokenProvider = { null },
            client = StubApiClient { ApiResponse(200, "{}") },
            uploader = StubDataUploader(),
            downloader = StubDownloader(byteArrayOf()),
        )
        try {
            service.loadMeta()
            org.junit.Assert.fail("expected CloudApiException")
        } catch (e: CloudApiException) {
            assertEquals("请先登录账户", e.error.userMessage)
        }
    }
}
