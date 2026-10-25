package com.cheku.app.carmode
import android.Manifest

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.ElectricBike
import androidx.compose.material.icons.outlined.ElectricCar
import androidx.compose.material.icons.outlined.ElectricMoped
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Navigation
import androidx.compose.material.icons.outlined.PedalBike
import androidx.compose.material.icons.outlined.TwoWheeler
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cheku.app.glass.components.GlassSegmentedControl
import com.cheku.app.glass.components.glassPressFeedback
import com.cheku.app.glass.rememberLayerAwareBackdrop
import kotlin.math.roundToInt

@Composable
internal fun CarSettingsPane(
    compact: Boolean,
    appearance: CarAppearanceState,
    orientation: CarModeOrientation,
    onOrientationChange: (CarModeOrientation) -> Unit,
    launchMode: CarDockLaunchMode,
    onLaunchModeChange: (CarDockLaunchMode) -> Unit,
    vehicles: List<CarVehicleChoice>,
    selectedVehicleId: String?,
    onSelectVehicle: (String) -> Unit,
    navChoices: List<CarAppTile.App>,
    musicChoices: List<CarAppTile.App>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val settings = remember(context) { CarModeSettings(context.applicationContext) }

    var autoFit by remember { mutableStateOf(settings.autoFitRecording()) }
    var autoStart by remember { mutableStateOf(settings.autoStart()) }
    var musicVisualizer by remember { mutableStateOf(settings.musicVisualizer()) }

    var vehiclePickerOpen by remember { mutableStateOf(false) }
    var navPickerOpen by remember { mutableStateOf(false) }
    var musicPickerOpen by remember { mutableStateOf(false) }

    var btPickerOpen by remember { mutableStateOf(false) }
    var boundAddress by remember { mutableStateOf(settings.boundBluetoothAddress()) }
    var boundName by remember { mutableStateOf(settings.boundBluetoothName()) }
    var btDevices by remember { mutableStateOf(emptyList<CarBluetoothChoice>()) }
    // `BLUETOOTH_CONNECT` 是运行时权限（API 31+）：没授权时先只显示"未绑定"，点开时才申请。
    val (btGranted, requestBtPermission) =
        rememberHubPermissions(android.Manifest.permission.BLUETOOTH_CONNECT)
    // 展开蓝牙列表 / 刚授到权限时都重读一次已配对设备。
    LaunchedEffect(btPickerOpen, btGranted) {
        if (btPickerOpen) btDevices = bondedCarBluetoothDevices(context)
    }

    val currentVehicle = vehicles.firstOrNull { it.id == selectedVehicleId }

    var navPkg by remember { mutableStateOf(settings.navPackage()) }
    var musicPkg by remember { mutableStateOf(settings.musicPackage()) }

    val allKnownChoices = navChoices + musicChoices
    fun labelFor(pkg: String): String =
        allKnownChoices.firstOrNull { it.packageName == pkg }?.let { choice ->
            choice.caption?.let { "${choice.label} · $it" } ?: choice.label
        } ?: appLabel(context.packageManager, pkg)

    /** 选项文案 */
    fun optionsOf(choices: List<CarAppTile.App>): List<Pair<String, String>> = choices.map { choice ->
        choice.packageName to (choice.caption?.let { "${choice.label} · $it" } ?: choice.label)
    }

    val navLabel = navPkg?.let { labelFor(it) } ?: navTargetLabel(context, settings)
    val musicLabel = musicPkg?.let { labelFor(it) }
    val navOptions = optionsOf(navChoices)
    val musicOptions = optionsOf(musicChoices)

    HubPaneFrame(title = "设置", count = null, compact = compact, onRefresh = null, modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 14.dp)
        ) {
            Spacer(Modifier.height(2.dp))

            CarSectionTitle("外观")
            CarCardBlock {
                Text("主题", color = HubTextSecondary, fontSize = 13.sp)
                GlassSegmentedControl(
                    options = CarThemeMode.entries.map { it.label },
                    selectedIndex = CarThemeMode.entries.indexOf(appearance.themeMode).coerceAtLeast(0),
                    onSelect = { appearance.chooseThemeMode(CarThemeMode.entries[it]) },
                    backdrop = rememberLayerAwareBackdrop(),
                    accent = HubPrimaryBlue,
                    selectedTextColor = HubPrimaryBlue,
                    unselectedTextColor = HubTextPrimary.copy(alpha = 0.82f),
                    modifier = Modifier.fillMaxWidth()
                )

                CarListRowDivider()

                Text("背景", color = HubTextSecondary, fontSize = 13.sp)
                GlassSegmentedControl(
                    options = CarBackground.entries.map { it.label },
                    selectedIndex = CarBackground.entries.indexOf(appearance.mode).coerceAtLeast(0),
                    onSelect = { appearance.chooseMode(CarBackground.entries[it]) },
                    backdrop = rememberLayerAwareBackdrop(),
                    accent = HubPrimaryBlue,
                    selectedTextColor = HubPrimaryBlue,
                    unselectedTextColor = HubTextPrimary.copy(alpha = 0.82f),
                    modifier = Modifier.fillMaxWidth()
                )

                if (appearance.mode == CarBackground.Custom) {
                    BackgroundPicker(compact = compact, appearance = appearance)
                }
            }

            CarSectionTitle("默认应用")
            CarCardBlock {
                CarListRow(
                    icon = Icons.Outlined.Navigation,
                    tint = HubPrimaryBlue,
                    title = "默认导航",
                    subtitle = "点 dock 的「导航」直接启动它",
                    trailingText = navLabel,
                    chevron = true
                ) { navPickerOpen = !navPickerOpen }
                if (navPickerOpen) {
                    CarOptionPicker(
                        compact = compact,
                        emptyLabel = "自动（车机版优先）",
                        options = navOptions,
                        selected = navPkg,
                        onSelect = {
                            navPkg = it
                            settings.setNavPackage(it)
                            navPickerOpen = false
                        }
                    )
                    if (navOptions.isEmpty()) {
                        CarPickerEmptyHint("没有检测到地图/导航类应用", compact)
                    }
                }

                CarListRowDivider()

                CarListRow(
                    icon = Icons.Outlined.MusicNote,
                    tint = HubPrimaryBlue,
                    title = "默认音乐",
                    subtitle = "没有播放时，音乐面板里那个「打开」按钮的目标",
                    trailingText = musicLabel ?: "未指定",
                    chevron = true
                ) { musicPickerOpen = !musicPickerOpen }
                if (musicPickerOpen) {
                    CarOptionPicker(
                        compact = compact,
                        emptyLabel = "未指定",
                        options = musicOptions,
                        selected = musicPkg,
                        onSelect = {
                            musicPkg = it
                            settings.setMusicPackage(it)
                            musicPickerOpen = false
                        }
                    )
                    if (musicOptions.isEmpty()) {
                        CarPickerEmptyHint("没有检测到音乐类应用（电台/播客不在此列）", compact)
                    }
                }
            }

            CarSectionTitle("显示")
            CarCardBlock {
                Text("屏幕方向", color = HubTextSecondary, fontSize = 13.sp)
                GlassSegmentedControl(
                    options = CarModeOrientation.entries.map { it.label },
                    selectedIndex = CarModeOrientation.entries.indexOf(orientation).coerceAtLeast(0),
                    onSelect = { onOrientationChange(CarModeOrientation.entries[it]) },
                    backdrop = rememberLayerAwareBackdrop(),
                    accent = HubPrimaryBlue,
                    selectedTextColor = HubPrimaryBlue,
                    unselectedTextColor = HubTextPrimary.copy(alpha = 0.82f),
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    when (orientation) {
                        CarModeOrientation.Landscape -> "进入车机界面时锁定横屏；手机左右倒置会自动翻转"
                        CarModeOrientation.Portrait -> "进入车机界面时锁定竖屏；手机上下倒置会自动翻转"
                        CarModeOrientation.Auto -> "进入车机界面时跟随设备重力感应"
                    },
                    color = HubTextTertiary, fontSize = 12.sp
                )

                CarListRowDivider()

                Text("打开方式", color = HubTextSecondary, fontSize = 13.sp)
                GlassSegmentedControl(
                    options = CarDockLaunchMode.entries.map { it.label },
                    selectedIndex = CarDockLaunchMode.entries.indexOf(launchMode).coerceAtLeast(0),
                    onSelect = { onLaunchModeChange(CarDockLaunchMode.entries[it]) },
                    backdrop = rememberLayerAwareBackdrop(),
                    accent = HubPrimaryBlue,
                    selectedTextColor = HubPrimaryBlue,
                    unselectedTextColor = HubTextPrimary.copy(alpha = 0.82f),
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    when (launchMode) {
                        CarDockLaunchMode.Auto ->
                            "横屏时一律与目标 App 分屏并排（防它把屏幕掰竖）；竖屏时改成 dock 悬浮在 App 之上"
                        CarDockLaunchMode.Split ->
                            "点开的 App 与车机页各占半屏，由系统负责排布，结构上不会互相遮挡"
                        CarDockLaunchMode.Overlay ->
                            "车机 dock 浮在 App 之上，App 全屏显示（需要悬浮窗权限）"
                        CarDockLaunchMode.Plain ->
                            "App 全屏打开，会盖住车机页与 dock"
                    },
                    color = HubTextTertiary, fontSize = 12.sp
                )
            }

            CarSectionTitle("车辆")
            CarCardBlock {
                CarListRow(
                    icon = Icons.Outlined.Bluetooth,
                    tint = HubPrimaryBlue,
                    title = "当连接到以下蓝牙时",
                    subtitle = boundName ?: "未绑定（不随蓝牙自动记录）",
                    chevron = true
                ) {
                    // `BLUETOOTH_CONNECT` 是运行时权限（API 31+）：点开时才申请。
                    if (!btGranted) requestBtPermission()
                    btPickerOpen = !btPickerOpen
                }
                if (btPickerOpen) {
                    CarOptionPicker(
                        compact = compact,
                        emptyLabel = "不绑定",
                        options = btDevices.map { it.address to it.name },
                        selected = boundAddress,
                        onSelect = { address ->
                            val device = btDevices.firstOrNull { it.address == address }
                            boundAddress = address
                            boundName = device?.name
                            settings.setBoundBluetooth(address, device?.name)
                            btPickerOpen = false
                        }
                    )
                    if (btDevices.isEmpty()) {
                        Text(
                            if (btGranted) "没有已配对的蓝牙设备（请先在系统设置里配对）"
                            else "需要蓝牙权限才能列出已配对设备",
                            color = HubTextTertiary,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                        )
                    }
                }

                CarListRowDivider()

                Text(
                    "改动在下次进入车机界面时生效",
                    color = HubTextTertiary, fontSize = 11.sp
                )
                CarOptionToggleRow(
                    title = "自动记录本次行程",
                    subtitle = "连接上述蓝牙后开始记录 fit 轨迹，退出时结束",
                    value = autoFit
                ) { autoFit = it; settings.setAutoFitRecording(it) }

                CarListRowDivider()

                Text("保存到下方车辆", color = HubTextSecondary, fontSize = 13.sp)
                if (vehicles.isEmpty()) {
                    Text("车库暂无可选车辆", color = HubTextTertiary, fontSize = 13.sp)
                } else {
                    val current = currentVehicle
                    if (current != null) {
                        CarVehicleCard(choice = current, selected = true) {
                            vehiclePickerOpen = !vehiclePickerOpen
                        }
                    } else {
                        CarListRow(
                            icon = Icons.Outlined.Dashboard,
                            tint = HubPrimaryBlue,
                            title = "尚未选择车辆",
                            subtitle = "点这里选一辆；车机行程就写进它的里程表",
                            chevron = true
                        ) { vehiclePickerOpen = !vehiclePickerOpen }
                    }
                    AnimatedVisibility(
                        visible = vehiclePickerOpen,
                        enter = expandVertically(tween(320)) + fadeIn(tween(200)),
                        exit = shrinkVertically(tween(240)) + fadeOut(tween(160))
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            vehicles
                                .filter { it.id != selectedVehicleId }
                                .forEach { choice ->
                                    CarVehicleCard(choice = choice, selected = false) {
                                        onSelectVehicle(choice.id)
                                        vehiclePickerOpen = false
                                    }
                                }
                        }
                    }
                }
            }

            CarSectionTitle("启动")
            CarCardBlock {
                CarOptionToggleRow(
                    title = "开启 app 自启车机页",
                    subtitle = "打开 App 后直接进入沉浸车机界面",
                    value = autoStart
                ) { autoStart = it; settings.setAutoStart(it) }
            }

            Spacer(Modifier.height(6.dp))
        }
    }
}

/** 背景图选择 + 模糊滑块 */
@Composable
private fun BackgroundPicker(compact: Boolean, appearance: CarAppearanceState) {
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            appearance.chooseCustomUri(uri.toString())
        }
    }

    CarActionButton(
        text = if (appearance.customUri == null) "选择背景图片" else "更换背景图片",
        icon = Icons.Outlined.FolderOpen,
        color = HubPrimaryBlue
    ) { picker.launch(arrayOf("image/*")) }

    if (appearance.customUri != null) {
        CarSliderRow(
            label = "背景模糊",
            valueText = "${appearance.blur.roundToInt()} dp",
            value = { appearance.blur },
            // 上限 32dp 与 app 设置页一致：再大就只剩一团色块，照片本身失去意义。
            valueRange = CarBackgroundBlurRange,
            compact = compact
        ) { appearance.previewBlur(it) }
    }
}

@Composable
private fun CarOptionPicker(
    compact: Boolean,
    emptyLabel: String?,
    options: List<Pair<String, String>>,
    selected: String?,
    onSelect: (String?) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = if (compact) 160.dp else 220.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        if (emptyLabel != null) {
            PickerRow(emptyLabel, selected == null, compact) { onSelect(null) }
        }
        options.forEach { (id, label) ->
            PickerRow(label, selected == id, compact) { onSelect(id) }
        }
        if (options.isEmpty() && emptyLabel == null) {
            Text(
                "没有可选项",
                color = HubTextTertiary,
                fontSize = 12.sp,
                modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
            )
        }
    }
}

@Composable
private fun CarPickerEmptyHint(text: String, compact: Boolean) {
    Text(
        text,
        color = HubTextTertiary,
        fontSize = if (compact) 11.sp else 12.sp,
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
    )
}

@Composable
private fun PickerRow(
    label: String,
    selected: Boolean,
    compact: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = if (compact) 7.dp else 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            color = if (selected) HubPrimaryBlue else HubTextPrimary,
            fontSize = if (compact) 13.sp else 14.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (selected) {
            Text("✓", color = HubPrimaryBlue, fontSize = if (compact) 13.sp else 15.sp)
        }
    }
}

private fun navTargetLabel(context: Context, settings: CarModeSettings): String =
    resolveNavTarget(context)?.label ?: "未找到可用的导航应用"

internal enum class CarVehicleKind {
    Car, ElectricCar, Motorcycle, ElectricMotorcycle, ElectricBike, Bicycle
}

internal class CarVehicleChoice(
    val id: String,
    val name: String,
    val model: String,
    val plate: String,
    val mileageKm: Double,
    val typeLine: String,
    val kind: CarVehicleKind
)

private fun carVehicleIcon(kind: CarVehicleKind): ImageVector = when (kind) {
    CarVehicleKind.ElectricCar -> Icons.Outlined.ElectricCar
    CarVehicleKind.Car -> Icons.Outlined.DirectionsCar
    CarVehicleKind.Motorcycle -> Icons.Outlined.TwoWheeler
    CarVehicleKind.ElectricMotorcycle -> Icons.Outlined.ElectricMoped
    CarVehicleKind.ElectricBike -> Icons.Outlined.ElectricBike
    CarVehicleKind.Bicycle -> Icons.Outlined.PedalBike
}

@Composable
private fun carVehicleColor(kind: CarVehicleKind): Color = when (kind) {
    CarVehicleKind.ElectricCar -> Color(0xFF0070EB)
    CarVehicleKind.Car -> HubPrimaryBlue
    CarVehicleKind.Motorcycle -> Color(0xFFE68A2E)
    CarVehicleKind.ElectricMotorcycle -> HubSuccess
    CarVehicleKind.ElectricBike -> HubSuccess
    CarVehicleKind.Bicycle -> HubSuccess
}

@Composable
private fun CarVehicleCard(
    choice: CarVehicleChoice,
    selected: Boolean,
    onClick: () -> Unit
) {
    val accent = carVehicleColor(choice.kind)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) accent.copy(alpha = 0.10f) else Color.Transparent)
            .glassPressFeedback(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // 第 1 行：徽章 + 名字/车型 + 里程。
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    carVehicleIcon(choice.kind),
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(25.dp)
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    choice.name,
                    color = HubTextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (choice.model.isNotBlank()) {
                    Text(
                        choice.model,
                        color = HubTextSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Text(
                "${choice.mileageKm.roundToInt()} km",
                color = accent,
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1
            )
        }
        // 第 2 行：车牌（左）+ 类别·能源·变速箱（右）。
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                choice.plate.ifBlank { "未登记车牌" },
                color = HubTextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (choice.typeLine.isNotBlank()) {
                Text(
                    choice.typeLine,
                    color = HubTextTertiary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1.35f)
                )
            }
        }
    }
}

/** 车机背景 / 明暗 / 模糊的可观测状态 */
@Stable
internal class CarAppearanceState(context: Context) {
    private val settings = CarModeSettings(context.applicationContext)

    var mode by mutableStateOf(settings.backgroundMode())
        private set

    var customUri by mutableStateOf(settings.customBackground())
        private set

    /** 模糊半径 */
    var blur by mutableStateOf(settings.backgroundBlur())
        private set

    /** 明暗模式 */
    var themeMode by mutableStateOf(settings.themeMode())
        private set

    /** 音乐面板封面的模糊半径 */
    var artworkBlur by mutableStateOf(settings.artworkBlur())
        private set

    fun chooseMode(value: CarBackground) {
        mode = value
        settings.setBackgroundMode(value)
    }

    fun chooseThemeMode(value: CarThemeMode) {
        themeMode = value
        settings.setThemeMode(value)
    }

    fun chooseCustomUri(value: String) {
        customUri = value
        settings.setCustomBackground(value)
        chooseMode(CarBackground.Custom)
    }

    fun previewBlur(value: Float) {
        blur = value
    }

    fun persistBlur() {
        settings.setBackgroundBlur(blur)
    }

    /** 封面模糊的拖动中预览值 */
    fun previewArtworkBlur(value: Float) {
        artworkBlur = value
    }

    /** 封面模糊的防抖落盘 */
    fun persistArtworkBlur() {
        settings.setArtworkBlur(artworkBlur)
    }
}

internal val CarBackgroundBlurRange: ClosedFloatingPointRange<Float> = 0f..32f

/** 专辑封面模糊的取值域 */
internal val CarArtworkBlurRange: ClosedFloatingPointRange<Float> = 0f..40f
