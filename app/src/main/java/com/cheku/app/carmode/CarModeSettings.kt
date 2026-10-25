package com.cheku.app.carmode

import android.content.Context

/** 车机界面的屏幕方向偏好 */
enum class CarModeOrientation(val key: String, val label: String) {
    /** 锁定横屏 */
    Landscape("landscape", "锁定横屏"),

    /** 锁定竖屏 */
    Portrait("portrait", "锁定竖屏"),

    /** 自动识别 */
    Auto("auto", "自动识别")
}

/** 解析持久化的方向 key */
internal fun carModeOrientationFromKey(key: String?): CarModeOrientation =
    CarModeOrientation.entries.firstOrNull { it.key == key } ?: CarModeOrientation.Auto

/** dock 里点开 App 时，那个 App 该以什么形态出现 */
enum class CarDockLaunchMode(val key: String, val label: String) {
    /** 自动识别 */
    Auto("auto", "自动识别"),

    /** 系统分屏并排 */
    Split("split", "分屏并排"),

    /** 悬浮 dock */
    Overlay("overlay", "悬浮 dock"),

    /** 直接打开 */
    Plain("plain", "直接打开")
}

/** 车机界面的背景样式 */
enum class CarBackground(val key: String, val label: String) {
    /** 纯色底 */
    Static("static", "静态"),

    /** 缓慢漂移的渐变网格 */
    Mesh("mesh", "动态"),

    Custom("custom", "自定义")
}

/** 解析持久化的背景 key */
internal fun carBackgroundFromKey(key: String?): CarBackground =
    CarBackground.entries.firstOrNull { it.key == key } ?: CarBackground.Static

/** 车机界面的明暗模式 */
enum class CarThemeMode(val key: String, val label: String) {
    Light("light", "浅色"),
    Dark("dark", "深色"),

    System("system", "跟随系统")
}

/** 解析持久化的明暗 key */
internal fun carThemeModeFromKey(key: String?): CarThemeMode =
    CarThemeMode.entries.firstOrNull { it.key == key } ?: CarThemeMode.System

internal fun carDockLaunchModeFromKey(key: String?): CarDockLaunchMode =
    CarDockLaunchMode.entries.firstOrNull { it.key == key } ?: CarDockLaunchMode.Auto

/** 车机模式的持久化设置 */
class CarModeSettings(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun orientation(): CarModeOrientation =
        carModeOrientationFromKey(prefs.getString(KEY_ORIENTATION, null))

    fun setOrientation(orientation: CarModeOrientation) {
        prefs.edit().putString(KEY_ORIENTATION, orientation.key).apply()
    }

    fun dockLaunchMode(): CarDockLaunchMode =
        carDockLaunchModeFromKey(prefs.getString(KEY_DOCK_LAUNCH_MODE, null))

    fun setDockLaunchMode(mode: CarDockLaunchMode) {
        prefs.edit().putString(KEY_DOCK_LAUNCH_MODE, mode.key).apply()
    }

    /** 开启软件后是否自动进入沉浸车机页 */
    fun autoStart(): Boolean = prefs.getBoolean(KEY_AUTO_START, false)

    fun setAutoStart(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_START, enabled).apply()
    }

    fun autoFitRecording(): Boolean = prefs.getBoolean(KEY_AUTO_FIT, false)

    fun setAutoFitRecording(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_FIT, enabled).apply()
    }

    /** 音乐面板是否显示可视化音频动画 */
    fun musicVisualizer(): Boolean = prefs.getBoolean(KEY_MUSIC_VISUALIZER, true)

    fun setMusicVisualizer(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_MUSIC_VISUALIZER, enabled).apply()
    }

    /** 两端渐隐强度 */
    fun visualizerFade(): Float = prefs.getFloat(KEY_VIS_FADE, 0.70f)

    fun setVisualizerFade(v: Float) {
        prefs.edit().putFloat(KEY_VIS_FADE, v.coerceIn(0f, 1f)).apply()
    }

    /** 频谱跟随平滑 */
    fun visualizerSmoothing(): Float = prefs.getFloat(KEY_VIS_SMOOTH, 0.70f)

    fun setVisualizerSmoothing(v: Float) {
        prefs.edit().putFloat(KEY_VIS_SMOOTH, v.coerceIn(0f, 0.95f)).apply()
    }

    /** 频谱增益 */
    fun visualizerGain(): Float = prefs.getFloat(KEY_VIS_GAIN, 1.30f)

    fun setVisualizerGain(v: Float) {
        prefs.edit().putFloat(KEY_VIS_GAIN, v.coerceIn(0.2f, 3f)).apply()
    }

    /** 频谱模糊半径，与专辑封面共用同一档模糊 */
    fun visualizerBlur(): Float = prefs.getFloat(KEY_VIS_BLUR, 12f)

    fun setVisualizerBlur(v: Float) {
        prefs.edit().putFloat(KEY_VIS_BLUR, v.coerceIn(0f, 24f)).apply()
    }

    /** 频谱整体透明度 */
    fun visualizerAlpha(): Float = prefs.getFloat(KEY_VIS_ALPHA, 1f)

    fun setVisualizerAlpha(v: Float) {
        prefs.edit().putFloat(KEY_VIS_ALPHA, v.coerceIn(0f, 1f)).apply()
    }

    private val DEFAULT_HUES = listOf(210f, 150f, 35f)

    fun visualizerStyle(): String = prefs.getString(KEY_VIS_STYLE, VisualizerStyle.CURVE.key)
        ?: VisualizerStyle.FILL_WAVE.key

    fun setVisualizerStyle(key: String) {
        prefs.edit().putString(KEY_VIS_STYLE, key).apply()
    }

    /** 配色模式 */
    fun visualizerPaletteMode(): String =
        prefs.getString(KEY_VIS_PALETTE_MODE, VisPaletteMode.Artwork.key)
            ?: VisPaletteMode.Artwork.key

    fun setVisualizerPaletteMode(key: String) {
        prefs.edit().putString(KEY_VIS_PALETTE_MODE, key).apply()
    }

    fun visualizerCustomHues(): List<Float> {
        val raw = prefs.getString(KEY_VIS_CUSTOM_HUES, null) ?: return DEFAULT_HUES
        val parsed = raw.split(',').mapNotNull { it.trim().toFloatOrNull() }
        return if (parsed.isNotEmpty()) parsed.take(3) else DEFAULT_HUES
    }

    fun setVisualizerCustomHues(hues: List<Float>) {
        prefs.edit().putString(KEY_VIS_CUSTOM_HUES, hues.joinToString(",")).apply()
    }

    /** 退出车机界面时，是否把本次行程里程写回当前车辆的里程记录 */
    fun autoAddMileage(): Boolean = prefs.getBoolean(KEY_AUTO_MILEAGE, false)

    fun setAutoAddMileage(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_MILEAGE, enabled).apply()
    }

    /** 车机背景样式 */
    fun backgroundMode(): CarBackground =
        carBackgroundFromKey(prefs.getString(KEY_BACKGROUND, null))

    fun setBackgroundMode(mode: CarBackground) {
        prefs.edit().putString(KEY_BACKGROUND, mode.key).apply()
    }

    fun customBackground(): String? = prefs.getString(KEY_CUSTOM_BACKGROUND, null)

    fun setCustomBackground(uri: String?) {
        prefs.edit().putString(KEY_CUSTOM_BACKGROUND, uri).apply()
    }

    /** 自定义背景的模糊半径 */
    fun backgroundBlur(): Float = prefs.getFloat(KEY_BACKGROUND_BLUR, DEFAULT_BACKGROUND_BLUR)

    fun setBackgroundBlur(value: Float) {
        prefs.edit().putFloat(KEY_BACKGROUND_BLUR, value).apply()
    }

    /** 明暗模式 */
    fun themeMode(): CarThemeMode =
        carThemeModeFromKey(prefs.getString(KEY_THEME_MODE, null))

    fun setThemeMode(mode: CarThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.key).apply()
    }

    fun artworkBlur(): Float = prefs.getFloat(KEY_ARTWORK_BLUR, DEFAULT_ARTWORK_BLUR)

    fun setArtworkBlur(value: Float) {
        prefs.edit().putFloat(KEY_ARTWORK_BLUR, value).apply()
    }

    fun navPackage(): String? = prefs.getString(KEY_NAV_PACKAGE, null)

    fun setNavPackage(packageName: String?) {
        prefs.edit().putString(KEY_NAV_PACKAGE, packageName).apply()
    }

    fun musicPackage(): String? = prefs.getString(KEY_MUSIC_PACKAGE, null)

    fun setMusicPackage(packageName: String?) {
        prefs.edit().putString(KEY_MUSIC_PACKAGE, packageName).apply()
    }

    fun carDesktopPackages(): List<String> =
        prefs.getString(KEY_CAR_DESKTOP, null)
            ?.split('\n')
            ?.filter { it.isNotBlank() }
            ?: emptyList()

    fun setCarDesktopPackages(packages: List<String>) {
        prefs.edit().putString(KEY_CAR_DESKTOP, packages.joinToString("\n")).apply()
    }

    /** 绑定的蓝牙设备地址 */
    fun boundBluetoothAddress(): String? = prefs.getString(KEY_BOUND_BLUETOOTH, null)

    /** 绑定设备的显示名 */
    fun boundBluetoothName(): String? = prefs.getString(KEY_BOUND_BLUETOOTH_NAME, null)

    fun setBoundBluetooth(address: String?, name: String?) {
        prefs.edit()
            .putString(KEY_BOUND_BLUETOOTH, address)
            .putString(KEY_BOUND_BLUETOOTH_NAME, name)
            .apply()
    }

    /** 进入车机模式前的自动旋转值 */
    internal fun rotationBackupValue(): Int = prefs.getInt(KEY_ROTATION_BACKUP, NO_ROTATION_BACKUP)

    /** 备份的写入时刻 */
    internal fun rotationBackupAt(): Long = prefs.getLong(KEY_ROTATION_BACKUP_AT, 0L)

    internal fun setRotationBackup(value: Int, at: Long) {
        prefs.edit()
            .putInt(KEY_ROTATION_BACKUP, value)
            .putLong(KEY_ROTATION_BACKUP_AT, at)
            .apply()
    }

    internal fun clearRotationBackup() {
        prefs.edit()
            .remove(KEY_ROTATION_BACKUP)
            .remove(KEY_ROTATION_BACKUP_AT)
            .apply()
    }

    companion object {
        const val PREFERENCES_NAME = "car_mode"
        private const val KEY_ORIENTATION = "orientation"
        private const val KEY_DOCK_LAUNCH_MODE = "dock_launch_mode"
        private const val KEY_AUTO_START = "auto_start"
        private const val KEY_AUTO_FIT = "auto_fit"
        private const val KEY_MUSIC_VISUALIZER = "music_visualizer"
        private const val KEY_VIS_FADE = "vis_fade"
        private const val KEY_VIS_SMOOTH = "vis_smooth"
        private const val KEY_VIS_GAIN = "vis_gain"
        private const val KEY_VIS_BLUR = "vis_blur"
        private const val KEY_VIS_ALPHA = "vis_alpha"
        private const val KEY_VIS_STYLE = "vis_style"

    /** 频谱配色模式 / 自定义色相 */
    private const val KEY_VIS_PALETTE_MODE = "vis_palette_mode"
    private const val KEY_VIS_CUSTOM_HUES = "vis_custom_hues"
        private const val KEY_AUTO_MILEAGE = "auto_mileage"
        private const val KEY_ROTATION_BACKUP = "rotation_backup"
        private const val KEY_ROTATION_BACKUP_AT = "rotation_backup_at"
        private const val KEY_BACKGROUND = "background"
        private const val KEY_CUSTOM_BACKGROUND = "custom_background"
        private const val KEY_BACKGROUND_BLUR = "background_blur"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_ARTWORK_BLUR = "artwork_blur"
        private const val KEY_NAV_PACKAGE = "nav_package"
        private const val KEY_MUSIC_PACKAGE = "music_package"
        private const val KEY_CAR_DESKTOP = "car_desktop_packages"
        private const val KEY_BOUND_BLUETOOTH = "bound_bluetooth"
        private const val KEY_BOUND_BLUETOOTH_NAME = "bound_bluetooth_name"

        /** 默认背景 */
        const val DEFAULT_BACKGROUND = "static"

        /** 默认模糊 */
        const val DEFAULT_BACKGROUND_BLUR = 12f

        /** 默认封面模糊 */
        const val DEFAULT_ARTWORK_BLUR = 26f

        const val NO_ROTATION_BACKUP = -1

        /** 自动写入里程记录的最小行程 */
        const val MIN_TRIP_KM_FOR_RECORD = 0.1
    }
}
