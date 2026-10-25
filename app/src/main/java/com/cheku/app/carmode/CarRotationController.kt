package com.cheku.app.carmode

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

internal object CarRotationController {
    private const val STALE_BACKUP_MS = 12 * 60 * 60 * 1000L

    fun canWrite(context: Context): Boolean = runCatching {
        Settings.System.canWrite(context)
    }.getOrDefault(false)

    fun settingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS)
            .setData(Uri.parse("package:${context.packageName}"))

    /** 进入车机模式时调用 */
    fun apply(context: Context): Boolean {
        if (!canWrite(context)) return false
        val settings = CarModeSettings(context)
        val resolver = context.contentResolver
        val current = runCatching {
            Settings.System.getInt(resolver, Settings.System.ACCELEROMETER_ROTATION, 1)
        }.getOrDefault(1)

        if (settings.rotationBackupValue() == CarModeSettings.NO_ROTATION_BACKUP) {
            settings.setRotationBackup(current, System.currentTimeMillis())
        }

        if (current == 1) return false
        return runCatching {
            Settings.System.putInt(resolver, Settings.System.ACCELEROMETER_ROTATION, 1)
        }.isSuccess
    }

    /** 退出车机模式时调用 */
    fun restore(context: Context) {
        val settings = CarModeSettings(context)
        val backup = settings.rotationBackupValue()
        if (backup == CarModeSettings.NO_ROTATION_BACKUP) return
        if (!canWrite(context)) return
        val restored = runCatching {
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.ACCELEROMETER_ROTATION,
                backup
            )
        }.isSuccess
        if (restored) settings.clearRotationBackup()
    }

    /** 启动自检 */
    fun recoverIfNeeded(context: Context) {
        val settings = CarModeSettings(context)
        if (settings.rotationBackupValue() == CarModeSettings.NO_ROTATION_BACKUP) return
        if (System.currentTimeMillis() - settings.rotationBackupAt() > STALE_BACKUP_MS) {
            settings.clearRotationBackup()
            return
        }
        restore(context)
    }
}
