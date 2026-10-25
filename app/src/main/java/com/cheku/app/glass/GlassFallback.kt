package com.cheku.app.glass

import android.os.Build
import android.util.Log

object GlassFallback {
    const val TAG: String = "GlassFallback"

    private val reported: MutableSet<String> = LinkedHashSet()

    fun isFullPath(): Boolean = isRuntimeShaderSupported()

    fun logOnce(tag: String, message: String) {
        val first = synchronized(reported) { reported.add("$tag|$message") }
        if (first) {
            Log.i(tag, message)
        }
    }

    fun reportLensDisabledOnce(key: String) {
        logOnce(
            TAG,
            "lens/dispersion disabled: SDK=${Build.VERSION.SDK_INT} < 33 (RuntimeShader unavailable) key=$key"
        )
    }

    /** Full AGSL path established */
    fun reportCompiledOnce(key: String) {
        logOnce(TAG, "AGSL compiled: key=$key (API ${Build.VERSION.SDK_INT})")
    }

    /** AGSL compilation failed */
    fun reportCompileFailure(key: String, throwable: Throwable?) {
        Log.w(TAG, "AGSL compile failed: key=$key", throwable)
    }

    fun reportAttachFailure(key: String, throwable: Throwable?) {
        Log.w(TAG, "renderEffect attach failed: key=$key", throwable)
    }

    /** A glass material degraded to a cheaper tier */
    fun reportMaterialFallback(material: String, message: String) {
        logOnce(TAG, "material fallback: $material - $message")
    }
}
