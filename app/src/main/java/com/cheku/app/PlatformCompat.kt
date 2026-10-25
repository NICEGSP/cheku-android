package com.cheku.app

import android.app.Activity
import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast

/** Android 6.0 / 7.0 兼容层 */
@ChecksSdkIntAtLeast(Build.VERSION_CODES.N)
fun Activity?.inMultiWindowCompat(): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
    return this?.isInMultiWindowMode == true
}

/** 同上 */
@ChecksSdkIntAtLeast(Build.VERSION_CODES.N)
fun Activity?.inPictureInPictureCompat(): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
    return this?.isInPictureInPictureMode == true
}
