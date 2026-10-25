package com.cheku.app.carmode

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

internal class CarAppPair(
    /** 稳定 id，用于日志与去重 */
    val id: String,
    /** 显示名 */
    val label: String,
    /** 车机版包名 */
    val carPackage: String,
    /** 手机版包名 */
    val phonePackage: String
)

internal val CAR_APP_PAIRS = listOf(
    CarAppPair(
        id = "amap",
        label = "高德地图",
        carPackage = AMAP_AUTO_PACKAGE,
        phonePackage = AMAP_PHONE_PACKAGE
    )
)

/** 桌面里的一张瓦片 */
internal sealed class CarAppTile {
    abstract val id: String
    abstract val label: String

    class Pane(val pane: CarPane) : CarAppTile() {
        override val id: String = "pane:${pane.id}"
        override val label: String = pane.label
    }

    /** 已安装的应用 */
    class App(
        val packageName: String,
        override val label: String,
        /** 版本说明 */
        val caption: String?
    ) : CarAppTile() {
        override val id: String = "pkg:$packageName"
    }
}

internal class CarAppCatalog(
    val tiles: List<CarAppTile>,
    /** key = 包名 */
    val icons: Map<String, ImageBitmap>,
    val categories: Map<String, CarAppCategory>
)

/** 读一遍本机应用并组装桌面 */
internal fun buildCarAppCatalog(context: Context, iconSizePx: Int): CarAppCatalog {
    val pm = context.packageManager

    val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val activities = runCatching {
        pm.queryIntentActivities(launcherIntent, PackageManager.MATCH_ALL)
    }.getOrDefault(emptyList())

    val installed = LinkedHashMap<String, String>()
    activities.forEach { info ->
        val pkg = info.activityInfo?.packageName ?: return@forEach
        if (pkg == context.packageName) return@forEach          // 不把自己列进自己的桌面
        if (installed.containsKey(pkg)) return@forEach
        installed[pkg] = info.loadLabel(pm).toString()
    }

    val tiles = ArrayList<CarAppTile>(installed.size + 8)
    val consumed = HashSet<String>()
    val icons = HashMap<String, ImageBitmap>()

    val categoryIndex = buildCarAppCategoryIndex(pm)
    val categories = HashMap<String, CarAppCategory>(installed.size)
    installed.forEach { (pkg, label) -> categories[pkg] = categoryIndex.classify(pkg, label) }

    tiles += CarAppTile.Pane(CarPane.Settings)

    listOf(CarPane.Sms, CarPane.Phone, CarPane.Contacts, CarPane.Music).forEach {
        tiles += CarAppTile.Pane(it)
    }

    CAR_APP_PAIRS.forEach { pair ->
        listOf(pair.carPackage to "车机版", pair.phonePackage to "手机版")
            .filter { (pkg, _) -> installed.containsKey(pkg) }
            .forEach { (pkg, caption) ->
                tiles += CarAppTile.App(pkg, pair.label, caption)
                consumed += pkg
                loadIcon(pm, pkg, iconSizePx)?.let { icons[pkg] = it }
            }
    }

    installed
        .filterKeys { it !in consumed }
        .entries
        .sortedBy { it.value.lowercase() }
        .forEach { (pkg, label) ->
            tiles += CarAppTile.App(pkg, label, null)
            loadIcon(pm, pkg, iconSizePx)?.let { icons[pkg] = it }
        }

    return CarAppCatalog(tiles, icons, categories)
}

/** 读一个包的应用图标 */
private fun loadIcon(pm: PackageManager, packageName: String, sizePx: Int): ImageBitmap? = runCatching {
    val drawable = pm.getApplicationIcon(packageName)
    // `toBitmap` 会把 `AdaptiveIconDrawable` 也拍平（车机/国内 ROM 上自适应图标是常态）。
    drawable.toBitmap(width = sizePx, height = sizePx).asImageBitmap()
}.getOrNull()

internal fun carAppChoices(catalog: CarAppCatalog): List<CarAppTile.App> =
    catalog.tiles.filterIsInstance<CarAppTile.App>()

internal fun carNavChoices(catalog: CarAppCatalog): List<CarAppTile.App> =
    catalog.tiles.filterIsInstance<CarAppTile.App>()
        .filter { catalog.categories[it.packageName] == CarAppCategory.Map }

internal fun carMusicChoices(catalog: CarAppCatalog): List<CarAppTile.App> =
    catalog.tiles.filterIsInstance<CarAppTile.App>()
        .filter { catalog.categories[it.packageName] == CarAppCategory.Music }
