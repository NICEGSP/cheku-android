package com.cheku.app.glass

object GlassDebug {
    @JvmField
    var diagnosticTint: Boolean = false

    @JvmField
    var diagnosticInvert: Boolean = false

    val isTinting: Boolean get() = diagnosticTint || diagnosticInvert

    /** Extra key used by the QA debug launch intent */
    const val DEBUG_LAUNCH_ENABLED: Boolean = false
}
