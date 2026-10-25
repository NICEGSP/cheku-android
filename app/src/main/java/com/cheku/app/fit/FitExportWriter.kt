package com.cheku.app.fit

import java.io.OutputStream

object FitExportWriter {
    fun write(bytes: ByteArray, openStream: () -> OutputStream?): Int {
        val output = openStream() ?: error("无法打开保存文件")
        output.use {
            it.write(bytes)
            it.flush()
        }
        return bytes.size
    }
}
