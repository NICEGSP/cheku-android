package com.cheku.app.net

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

/** API 接口响应体上限 */
internal const val MAX_API_BODY_BYTES = 2 * 1024 * 1024

internal const val MAX_PAGE_BODY_BYTES = 8 * 1024 * 1024

internal fun InputStream.readBodyLimited(maxBytes: Int): String =
    String(readBytesLimited(maxBytes), Charsets.UTF_8)

internal fun InputStream.readBytesLimited(maxBytes: Int): ByteArray {
    val buffer = ByteArray(8 * 1024)
    val out = ByteArrayOutputStream()
    while (true) {
        val read = read(buffer)
        if (read < 0) break
        if (out.size() + read > maxBytes) {
            throw IOException("内容超过上限 $maxBytes 字节，已中止读取")
        }
        out.write(buffer, 0, read)
    }
    return out.toByteArray()
}
