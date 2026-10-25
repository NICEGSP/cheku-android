package com.cheku.app.carmode

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Navigation
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

/** dock 条目的横向尺寸 */
internal val CarDockWidth: Dp = 116.dp
internal val CarDockHeight: Dp = 96.dp

/** 分屏态下的 dock 宽度 */
internal val CarDockCompactWidth: Dp = 88.dp

internal val CarDockCollapsedWidth: Dp = 12.dp

internal object CarDockMetrics {
    /** 页内条目内容高 */
    val pageItemContent: Dp = 47.dp

    /** 浮层条目内容高 */
    val overlayItemContent: Dp = 42.dp

    /** 只画图标时的内容高 */
    val iconOnlyContent: Dp = 26.dp

    /** 条目之间的间距 */
    val itemSpacing: Dp = 2.dp

    /** 条目到 dock 边缘的内边距 */
    val railPadding: Dp = 4.dp

    val itemMinWidth: Dp = 26.dp

    val overlayCollapseBar: Dp = 52.dp

    val overlayCollapseBarWidth: Dp = 64.dp

    val landscapeRailExtent: Dp = 384.dp

    /** 竖屏时贴底横条的可用宽度 */
    val portraitBarExtent: Dp = 384.dp
}

internal fun carDockRailHeight(count: Int, itemContent: Dp): Dp =
    itemContent * count +
        CarDockMetrics.itemSpacing * (count - 1).coerceAtLeast(0) +
        CarDockMetrics.railPadding * 2

internal fun carDockBarWidth(count: Int, itemWidth: Dp = CarDockMetrics.itemMinWidth): Dp =
    itemWidth * count +
        CarDockMetrics.itemSpacing * (count - 1).coerceAtLeast(0) +
        CarDockMetrics.railPadding * 2

internal data class CarDockOverlayGeometry(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int
)

internal data class CarWindowBounds(
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int
) {
    val isValid: Boolean get() = width > 0 && height > 0
}

internal fun carDockOverlayGeometry(
    landscape: Boolean,
    expanded: Boolean,
    inMultiWindow: Boolean,
    windowLeft: Int,
    windowTop: Int,
    windowWidth: Int,
    windowHeight: Int,
    density: Float
): CarDockOverlayGeometry {
    fun px(value: Dp): Int = (value.value * density).toInt()
    return if (landscape) {
        val rail = px(if (expanded) (if (inMultiWindow) CarDockCompactWidth else CarDockWidth) else CarDockCollapsedWidth)
        CarDockOverlayGeometry(
            x = windowLeft,
            y = windowTop,
            width = minOf(rail, windowWidth),
            height = windowHeight
        )
    } else {
        val bar = px(if (expanded) CarDockHeight else CarDockCollapsedWidth)
            .coerceAtMost(windowHeight)
        CarDockOverlayGeometry(
            x = windowLeft,
            y = windowTop + windowHeight - bar,
            width = windowWidth,
            height = bar
        )
    }
}

internal typealias LaunchBounds = android.graphics.Rect
internal const val FREEFORM_SUPPORT_SETTING = "enable_freeform_support"

internal const val AMAP_AUTO_PACKAGE = "com.autonavi.amapauto"

internal const val AMAP_PHONE_PACKAGE = "com.autonavi.minimap"

internal const val AMAP_AUTO_DOWNLOAD_URL = "https://auto.amap.com/download"

internal enum class CarPane(
    val id: String,
    val label: String,
    val icon: ImageVector,
    /** 本页面显示时，dock 上高亮的那一格 */
    val dockItem: CarDockItem
) {
    Apps("apps", "软件", Icons.Outlined.Apps, CarDockItem.Apps),

    Display("display", "屏显", Icons.Outlined.Speed, CarDockItem.Display),

    Settings("settings", "设置", Icons.Outlined.Settings, CarDockItem.Settings),

    Sms("sms", "短信", Icons.Outlined.Sms, CarDockItem.Apps),
    Phone("phone", "电话", Icons.Outlined.Phone, CarDockItem.Apps),
    Contacts("contacts", "联系人", Icons.Outlined.Contacts, CarDockItem.Apps),

    Music("music", "音乐", Icons.Outlined.MusicNote, CarDockItem.Music);

    companion object {
        /** 从持久化/通道里带回来的 id 反解页面 */
        fun fromId(id: String?): CarPane? = entries.firstOrNull { it.id == id }
    }
}

/** 没有选中任何页面时，车机主区显示什么 */
internal val CarModeDefaultPane: CarPane = CarPane.Display

/** dock 条目点下去的去向 */
internal enum class CarDockKind {
    Exit,

    /** 把主区换成对应页面 */
    Pane,

    Launch
}

internal enum class CarDockItem(
    val label: String,
    val icon: ImageVector,
    val kind: CarDockKind
) {
    Exit("退出", Icons.Outlined.PowerSettingsNew, CarDockKind.Exit),
    Apps("软件", Icons.Outlined.Apps, CarDockKind.Pane),

    Music("音乐", Icons.Outlined.MusicNote, CarDockKind.Launch),

    Nav("导航", Icons.Outlined.Navigation, CarDockKind.Launch),
    Display("屏显", Icons.Outlined.Speed, CarDockKind.Pane),
    Settings("设置", Icons.Outlined.Settings, CarDockKind.Pane);

    val pane: CarPane?
        get() = if (kind == CarDockKind.Pane) {
            CarPane.entries.firstOrNull { it.dockItem == this }
        } else {
            null
        }

    fun isActiveFor(activePaneId: String?): Boolean =
        (CarPane.fromId(activePaneId) ?: CarModeDefaultPane).dockItem == this
}

internal object CarModeDockBus {
    /** 当前主区页面 id */
    val selected = MutableStateFlow<String?>(null)

    /** 轨迹是否正在记录 */
    val fitRecording = MutableStateFlow(false)

    val inMultiWindow = MutableStateFlow(false)

    /** 车机页窗口的实际边界 */
    val carWindowBounds = MutableStateFlow(CarWindowBounds(0, 0, 0, 0))

    val focusCarMode = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    val launchRequest = MutableSharedFlow<CarLaunchTarget>(extraBufferCapacity = 1)

    val launchStageItem = MutableStateFlow<CarDockItem?>(null)

    val launchStage = MutableStateFlow(CarDockLaunchStage.None)

    val itemClickRequest = MutableSharedFlow<CarDockItem>(extraBufferCapacity = 1)

    val exitCarMode = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** 车机页退出时清场，避免下次进入时带着上一次的选中态 */
    fun reset() {
        selected.value = null
        fitRecording.value = false
        launchStageItem.value = null
        launchStage.value = CarDockLaunchStage.None
    }
}

internal class CarLaunchTarget(val label: String, val intent: Intent)

/** 解析 dock 条目要启动的目标 */
internal fun resolveCarLaunchTarget(context: Context, item: CarDockItem): CarLaunchTarget? =
    when (item) {
        CarDockItem.Nav -> resolveNavTarget(context)

        CarDockItem.Music -> resolveMusicTarget(context)

        else -> null
    }

internal fun resolveMusicTarget(context: Context): CarLaunchTarget? {
    val packageName = CarModeSettings(context).musicPackage() ?: return null
    return resolvePackageTarget(context, packageName, appLabel(context.packageManager, packageName))
}

internal fun resolveNavTarget(context: Context): CarLaunchTarget? {
    val pm = context.packageManager
    val preferred = CarModeSettings(context).navPackage()
    if (preferred != null) {
        launchIntentForPackage(pm, preferred)?.let { return CarLaunchTarget(navLabelFor(pm, preferred), it) }
    }
    launchIntentForPackage(pm, AMAP_AUTO_PACKAGE)?.let {
        return CarLaunchTarget("高德地图车机版", it)
    }
    launchIntentForPackage(pm, AMAP_PHONE_PACKAGE)?.let {
        return CarLaunchTarget("高德地图", it)
    }
    val download = Intent(Intent.ACTION_VIEW, Uri.parse(AMAP_AUTO_DOWNLOAD_URL))
    return if (download.resolveActivity(pm) != null) CarLaunchTarget("高德地图车机版下载页", download) else null
}

internal fun resolvePackageTarget(context: Context, packageName: String, label: String): CarLaunchTarget? {
    val pm = context.packageManager
    val intent = launchIntentForPackage(pm, packageName) ?: return null
    return CarLaunchTarget(label, intent)
}

private fun launchIntentForPackage(pm: PackageManager, packageName: String): Intent? {
    val intent = pm.getLaunchIntentForPackage(packageName) ?: return null
    return if (intent.resolveActivity(pm) != null) intent else null
}

/** 某包的显示名，读不到就退回包名本身 */
internal fun appLabel(pm: PackageManager, packageName: String): String {
    val viaIntent = runCatching {
        launchIntentForPackage(pm, packageName)
            ?.let { pm.resolveActivity(it, 0) }
            ?.activityInfo
            ?.loadLabel(pm)
            ?.toString()
    }.getOrNull()
    if (!viaIntent.isNullOrBlank()) return viaIntent
    val viaAppInfo = runCatching {
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    }.getOrNull()
    return if (!viaAppInfo.isNullOrBlank()) viaAppInfo else packageName
}

private fun navLabelFor(pm: PackageManager, packageName: String): String = when (packageName) {
    AMAP_AUTO_PACKAGE -> "高德地图车机版"
    AMAP_PHONE_PACKAGE -> "高德地图"
    else -> appLabel(pm, packageName)
}

internal fun launchCarTarget(
    context: Context,
    target: CarLaunchTarget,
    mode: CarDockLaunchMode,
    rightBounds: LaunchBounds?
): Boolean {
    // 每次都从 `base` 拷一份新的：Intent 是可变对象，复用同一个实例会把上一次的 flag 带过去。
    val intent = Intent(target.intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val options: Bundle? = when (mode) {
        CarDockLaunchMode.Split -> {
            intent.addFlags(Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT)

            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            null
        }

        CarDockLaunchMode.Overlay -> {
            if (rightBounds == null) {
                null
            } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                // 自由窗口本身是 Android 7.0（API 24）才有的特性，低版本传了 bounds 也无效。
                // 不传 = 全屏启动，与该版本真实能力一致；Overlay 模式另有悬浮 dock 兜底。
                ActivityOptions.makeBasic().toBundle()
            } else {
                val freeformOn =
                    Settings.Global.getInt(context.contentResolver, FREEFORM_SUPPORT_SETTING, 0) != 0
                if (!freeformOn && !CarDockOverlayService.isRunning) {
                    Toast.makeText(context, "未开启自由窗口，「${target.label}」将全屏打开", Toast.LENGTH_SHORT).show()
                }
                ActivityOptions.makeBasic().apply { setLaunchBounds(rightBounds) }.toBundle()
            }
        }

        CarDockLaunchMode.Auto, CarDockLaunchMode.Plain -> null
    }

    return runCatching {
        if (options != null) context.startActivity(intent, options) else context.startActivity(intent)
    }.onFailure {
        Toast.makeText(context, "无法打开「${target.label}」：${it.message ?: "未知原因"}", Toast.LENGTH_LONG).show()
    }.isSuccess
}

internal fun decideDockLaunchMode(
    setting: CarDockLaunchMode,
    deviceLandscape: Boolean,
    overlayAvailable: Boolean
): CarDockLaunchMode = when {
    setting != CarDockLaunchMode.Auto -> setting
    deviceLandscape -> CarDockLaunchMode.Split
    !overlayAvailable -> CarDockLaunchMode.Split
    else -> CarDockLaunchMode.Overlay
}

internal enum class CarDockLaunchStage { None, Split, Fullscreen }

internal fun nextDockLaunchStage(
    currentItem: CarDockItem?,
    currentStage: CarDockLaunchStage,
    clicked: CarDockItem
): CarDockLaunchStage = when {
    currentItem != clicked -> CarDockLaunchStage.Split
    currentStage == CarDockLaunchStage.Split -> CarDockLaunchStage.Fullscreen
    else -> CarDockLaunchStage.Split
}

internal fun focusCarModeExclusive(context: Context): Boolean = runCatching {
    context.startActivity(
        Intent(context, com.cheku.app.MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
    )
}.isSuccess
