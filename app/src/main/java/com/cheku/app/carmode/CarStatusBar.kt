package com.cheku.app.carmode

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.util.Log
import android.location.GnssStatus
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material.icons.outlined.SettingsEthernet
import androidx.compose.material.icons.outlined.SignalCellularAlt
import androidx.compose.material.icons.outlined.SignalCellularOff
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import java.util.Calendar
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** 电量百分比 */
internal fun batteryLevelToPercent(level: Int, scale: Int): Int? {
    if (level < 0 || scale <= 0) return null
    return ((level * 100f) / scale).roundToInt().coerceIn(0, 100)
}

/** WiFi 信号格 */
internal fun wifiRssiToBars(rssi: Int): Int = when {
    rssi >= -55 -> 4
    rssi >= -65 -> 3
    rssi >= -75 -> 2
    rssi >= -85 -> 1
    else -> 0
}

/** 蜂窝信号格 */
internal fun cellularDbmToBars(dbm: Int): Int = when {
    dbm >= -85 -> 4
    dbm >= -95 -> 3
    dbm >= -105 -> 2
    dbm >= -115 -> 1
    else -> 0
}

internal fun gnssToBars(usedInFix: Int, avgCn0DbHz: Float): Int = when {
    usedInFix <= 0 -> 0
    avgCn0DbHz >= 35f -> 4
    avgCn0DbHz >= 28f -> 3
    avgCn0DbHz >= 20f -> 2
    else -> 1
}

/** 信号格上限 */
internal const val CAR_SIGNAL_MAX_BARS = 4

/** 网络传输类型 */
internal enum class CarNetworkKind { None, Wifi, Cellular, Ethernet, Other }

internal data class CarStatusTelemetry(
    val batteryPercent: Int? = null,
    val batteryCharging: Boolean = false,
    val powerSaveMode: Boolean = false,
    val networkKind: CarNetworkKind = CarNetworkKind.None,
    val networkLabel: String? = null,
    val networkBars: Int? = null,
    val gpsBars: Int? = null,
    val gpsSatellites: Int? = null,
    val gpsSatellitesVisible: Int? = null,
)

/** 采集状态栏读数 */
@Composable
internal fun rememberCarStatusTelemetry(): CarStatusTelemetry {
    val context = LocalContext.current
    var telemetry by remember { mutableStateOf(CarStatusTelemetry()) }

    DisposableEffect(context) {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        fun powerSave(): Boolean = runCatching { powerManager?.isPowerSaveMode == true }.getOrDefault(false)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                intent ?: return
                if (intent.action == PowerManager.ACTION_POWER_SAVE_MODE_CHANGED) {
                    telemetry = telemetry.copy(powerSaveMode = powerSave())
                    return
                }
                telemetry = telemetry.copy(
                    batteryPercent = batteryLevelToPercent(
                        intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1),
                        intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                    ),
                    batteryCharging = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1).let {
                        it == BatteryManager.BATTERY_STATUS_CHARGING ||
                            it == BatteryManager.BATTERY_STATUS_FULL
                    },
                    powerSaveMode = powerSave()
                )
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        }
        runCatching { context.registerReceiver(receiver, filter) }
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }

    // 网络（轮询）。
    LaunchedEffect(context) {
        while (true) {
            val (kind, label, bars) = readCarNetwork(context)
            telemetry = telemetry.copy(networkKind = kind, networkLabel = label, networkBars = bars)
            delay(2_000L)
        }
    }

    // GPS（GnssStatus 回调；未授予定位权限时静默保持 null）。
    DisposableEffect(context) {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (manager == null || !granted) return@DisposableEffect onDispose {}
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val callback = object : GnssStatus.Callback() {
                override fun onSatelliteStatusChanged(status: GnssStatus) {
                    var used = 0
                    var cn0Sum = 0f
                    var withSignal = 0
                    var weakSignal = 0
                    for (i in 0 until status.satelliteCount) {
                        val cn0 = runCatching { status.getCn0DbHz(i) }.getOrDefault(0f)
                        if (cn0 > 0f) withSignal++
                        if (cn0 in 8f..20f) weakSignal++
                        if (status.usedInFix(i)) {
                            used++
                            cn0Sum += cn0
                        }
                    }
                    val avg = if (used > 0) cn0Sum / used else 0f
                    val visible = withSignal
                    Log.i(
                        "CarStatusBar",
                        "GNSS: capacity=${status.satelliteCount} withSignal=$withSignal " +
                            "weak=$weakSignal usedInFix=$used avgCn0=$avg"
                    )
                    telemetry = telemetry.copy(
                        gpsSatellites = used,
                        gpsSatellitesVisible = visible,
                        gpsBars = gnssToBars(used, avg)
                    )
                }
            }
            @Suppress("DEPRECATION")
            val ok = runCatching { manager.registerGnssStatusCallback(callback) }.isSuccess
            onDispose { if (ok) runCatching { manager.unregisterGnssStatusCallback(callback) } }
        } else {
            onDispose {}
        }
    }

    return telemetry
}

/** 读当前网络传输 + 信号 */
@Suppress("DEPRECATION") // `WifiInfo.rssi` 已废弃但仍是取当前连接强度的唯一稳定读法。
private fun readCarNetwork(context: Context): Triple<CarNetworkKind, String?, Int?> = runCatching {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        ?: return@runCatching Triple(CarNetworkKind.None, "无网络", null)
    val caps = cm.activeNetwork?.let { cm.getNetworkCapabilities(it) }
        ?: return@runCatching Triple(CarNetworkKind.None, "无网络", null)
    when {
        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
            val wifi = context.applicationContext
                .getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val rssi = runCatching { wifi?.connectionInfo?.rssi }.getOrNull()
            Triple(CarNetworkKind.Wifi, "WiFi", rssi?.let { wifiRssiToBars(it) })
        }

        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
            val tm = dataSimTelephony(context)
            val type = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) tm?.dataNetworkType ?: 0
                else @Suppress("DEPRECATION") tm?.networkType ?: 0
            }.getOrDefault(0)
            val bars = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                runCatching { tm?.signalStrength?.level }.getOrNull()
            } else null
            Triple(CarNetworkKind.Cellular, cellularLabel(type), bars)
        }

        caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ->
            Triple(CarNetworkKind.Ethernet, "有线", CAR_SIGNAL_MAX_BARS)

        else -> Triple(CarNetworkKind.Other, "已连接", null)
    }
}.getOrElse { Triple(CarNetworkKind.None, "无网络", null) }

private fun dataSimTelephony(context: Context): TelephonyManager? {
    val base = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager ?: return null
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return base
    val subId = runCatching { SubscriptionManager.getDefaultDataSubscriptionId() }.getOrNull()
    if (subId == null || subId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) return base
    return runCatching { base.createForSubscriptionId(subId) }.getOrElse { base }
}

/** 蜂窝制式文案 */
@Suppress("DEPRECATION")
private fun cellularLabel(type: Int): String = when (type) {
    TelephonyManager.NETWORK_TYPE_NR -> "5G"
    TelephonyManager.NETWORK_TYPE_LTE -> "4G"
    TelephonyManager.NETWORK_TYPE_HSPAP,
    TelephonyManager.NETWORK_TYPE_HSPA,
    TelephonyManager.NETWORK_TYPE_HSDPA,
    TelephonyManager.NETWORK_TYPE_HSUPA,
    TelephonyManager.NETWORK_TYPE_UMTS,
    TelephonyManager.NETWORK_TYPE_EVDO_0,
    TelephonyManager.NETWORK_TYPE_EVDO_A,
    TelephonyManager.NETWORK_TYPE_EVDO_B,
    TelephonyManager.NETWORK_TYPE_TD_SCDMA -> "3G"
    TelephonyManager.NETWORK_TYPE_EDGE,
    TelephonyManager.NETWORK_TYPE_GPRS,
    TelephonyManager.NETWORK_TYPE_CDMA,
    TelephonyManager.NETWORK_TYPE_1xRTT,
    TelephonyManager.NETWORK_TYPE_GSM -> "2G"
    else -> "移动网络"
}

internal data class CarStatusPalette(
    val primary: Color,
    val secondary: Color,
    val hint: Color,
    val success: Color,
    val error: Color,
    val warning: Color,
)

/** 页内 dock 用的配色 */
@Composable
internal fun rememberHubStatusPalette(): CarStatusPalette = CarStatusPalette(
    primary = HubTextPrimary,
    secondary = HubTextSecondary,
    hint = HubTextHint,
    success = HubSuccess,
    error = HubError,
    // 信号"中等"档用的琥珀色：Hub* 里没有这一档，固定取一个在浅底/深底都看得清的值。
    warning = Color(0xFFE8930C)
)

/** 车机侧时钟 */
@Composable
internal fun rememberCarClock(): String {
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            val now = System.currentTimeMillis()
            nowMs = now
            delay(1_000L - now % 1_000L)
        }
    }
    val calendar = Calendar.getInstance().apply { timeInMillis = nowMs }
    return formatClock(calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE))
}

@Composable
internal fun CarDockStatusPanel(
    vertical: Boolean,
    compact: Boolean,
    palette: CarStatusPalette,
    modifier: Modifier = Modifier,
    enablePhonePermissionRequest: Boolean = true
) {
    val telemetry = rememberCarStatusTelemetry()
    val clock = rememberCarClock()

    if (enablePhonePermissionRequest) {
        val (phoneGranted, requestPhone) =
            rememberHubPermissions(Manifest.permission.READ_PHONE_STATE)
        var askedPhone by rememberSaveable { mutableStateOf(false) }
        LaunchedEffect(telemetry.networkKind, phoneGranted) {
            if (telemetry.networkKind == CarNetworkKind.Cellular && !phoneGranted && !askedPhone) {
                askedPhone = true
                requestPhone()
            }
        }
    }

    val outer = if (vertical) {
        modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 4.dp)
    } else {
        modifier.width(if (compact) 104.dp else 116.dp).padding(horizontal = 4.dp)
    }
    Column(
        modifier = outer,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 4.dp, Alignment.CenterVertically)
    ) {
        // 第 1 行：时间 + 电池（"电池数字合到一起"）。
        Row(
            modifier = if (vertical) {
                Modifier.offset(x = if (compact) 4.dp else 6.dp)
            } else {
                Modifier
            },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 5.dp)
        ) {
            Text(
                clock,
                color = palette.primary,
                fontSize = if (compact) 12.sp else 15.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1
            )
            BatteryIndicator(
                percent = telemetry.batteryPercent,
                charging = telemetry.batteryCharging,
                powerSave = telemetry.powerSaveMode,
                compact = compact,
                palette = palette
            )
        }
        // 第 2 行：网络 + GPS（同一时刻只画一个网络类型：有 WiFi 就是 WiFi）。
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 9.dp)
        ) {
            NetworkIndicator(telemetry, compact, palette)
            GpsIndicator(
                telemetry.gpsBars,
                telemetry.gpsSatellitesVisible ?: telemetry.gpsSatellites,
                compact,
                palette
            )
            val satsVisible = telemetry.gpsSatellitesVisible ?: 0
            val digitWidth = if (compact) 13.dp else 16.dp
            Box(
                modifier = Modifier.width(digitWidth),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "$satsVisible",
                    color = if ((telemetry.gpsBars ?: 0) > 0) palette.primary else palette.hint,
                    fontSize = if (compact) 10.sp else 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    // 一位数时补一个空格占住右半，让字符本身也保持在同一个位置。
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

internal fun signalLevelColor(level: Int, palette: CarStatusPalette): Color = when {
    level <= 1 -> palette.error
    level == 2 -> palette.warning
    else -> palette.success
}

@Composable
internal fun GpsIndicator(
    bars: Int?,
    satellites: Int?,
    compact: Boolean,
    palette: CarStatusPalette
) {
    val hasFix = (satellites ?: 0) > 0
    val level = if (hasFix) (bars ?: 0).coerceIn(0, CAR_SIGNAL_MAX_BARS) else 0
    val litColor = if (level <= 0) palette.hint else signalLevelColor(level, palette)
    val offColor = palette.hint.copy(alpha = 0.28f)
    val side = if (compact) 16.dp else 19.dp
    Canvas(
        modifier = Modifier
            .size(side)
            .semantics { contentDescription = "GPS 信号" }
    ) {
        val stroke = size.minDimension * 0.10f
        val cx = size.width / 2f
        val cy = size.height / 2f
        val ringR = size.minDimension * 0.29f
        val tickLen = size.minDimension * 0.13f
        val centers = floatArrayOf(315f, 45f, 135f, 225f)
        val segSpan = 64f   // 每段弧的角度；缺口合计 26° 留给四个刻度
        for (i in 0 until CAR_SIGNAL_MAX_BARS) {
            val start = centers[i] - segSpan / 2f
            drawArc(
                color = if (i < level) litColor else offColor,
                startAngle = start,
                sweepAngle = segSpan,
                useCenter = false,
                topLeft = Offset(cx - ringR, cy - ringR),
                size = Size(ringR * 2f, ringR * 2f),
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
        }
        // 四个刻度：上下左右向外的小短棒（与四段弧的缺口对齐）。
        for (k in 0 until 4) {
            val rad = Math.toRadians((90.0 * k))
            val dirX = cos(rad).toFloat()
            val dirY = sin(rad).toFloat()
            drawLine(
                color = if (level > 0) litColor else offColor,
                start = Offset(cx + dirX * ringR, cy + dirY * ringR),
                end = Offset(cx + dirX * (ringR + tickLen), cy + dirY * (ringR + tickLen)),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
        }
    }
}

/** 网络指示 */
@Composable
private fun NetworkIndicator(
    telemetry: CarStatusTelemetry,
    compact: Boolean,
    palette: CarStatusPalette
) {
    val icon = when (telemetry.networkKind) {
        CarNetworkKind.Wifi -> Icons.Outlined.Wifi
        CarNetworkKind.Cellular -> Icons.Outlined.SignalCellularAlt
        CarNetworkKind.Ethernet -> Icons.Outlined.SettingsEthernet
        CarNetworkKind.None -> Icons.Outlined.SignalCellularOff
        CarNetworkKind.Other -> Icons.Outlined.SignalCellularAlt
    }
    val connected = telemetry.networkKind != CarNetworkKind.None
    val level = telemetry.networkBars
    val tint = when {
        !connected -> palette.hint
        level == null -> palette.secondary
        level <= 0 -> palette.error
        else -> signalLevelColor(level, palette)
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Icon(
            icon,
            contentDescription = telemetry.networkLabel ?: "网络信号",
            tint = tint,
            modifier = Modifier.size(if (compact) 14.dp else 17.dp)
        )
        // 只有拿到强度时才画信号格（蜂窝无 READ_PHONE_STATE 时是 null）。
        level?.let { SignalBars(level = it, compact = compact, palette = palette) }
    }
}

/** 电池 */
@Composable
private fun BatteryIndicator(
    percent: Int?,
    charging: Boolean,
    powerSave: Boolean,
    compact: Boolean,
    palette: CarStatusPalette
) {
    val bodyWidth = if (compact) 34.dp else 40.dp
    val bodyHeight = if (compact) 15.dp else 17.dp
    val accent = when {
        charging -> palette.success
        percent != null && percent < 15 -> palette.error
        powerSave || (percent != null && percent < 30) -> palette.warning
        else -> palette.secondary
    }
    val level = (percent ?: 0).coerceIn(0, 100)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.size(bodyWidth, bodyHeight),
            contentAlignment = Alignment.Center
        ) {
            // 外框（微圆角，与 dock 的直角语言不冲突）。
            Box(
                Modifier
                    .fillMaxSize()
                    .border(1.2.dp, accent, RoundedCornerShape(3.dp))
            )
            // 电量填充：左起按百分比铺满内腔。
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .padding(2.dp)
                    .fillMaxHeight()
                    .fillMaxWidth(level / 100f)
                    .background(
                        accent.copy(alpha = if (charging) 0.45f else 0.30f),
                        RoundedCornerShape(1.dp)
                    )
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                Text(
                    percent?.let { "$it%" } ?: "--",
                    color = palette.primary,
                    fontSize = if (compact) 8.5.sp else 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                    style = TextStyle(
                        platformStyle = PlatformTextStyle(includeFontPadding = false)
                    )
                )
                if (charging) {
                    Icon(
                        Icons.Outlined.FlashOn,
                        contentDescription = "充电中",
                        tint = accent,
                        modifier = Modifier.size(if (compact) 8.dp else 9.dp)
                    )
                }
            }
        }
        // 正极凸点。
        Box(
            Modifier
                .width(2.dp)
                .height(bodyHeight / 3)
                .background(accent, RoundedCornerShape(topEnd = 1.dp, bottomEnd = 1.dp))
        )
    }
}

/** 信号格 */
@Composable
internal fun SignalBars(
    level: Int,
    compact: Boolean,
    palette: CarStatusPalette
) {
    val maxBar = CAR_SIGNAL_MAX_BARS
    val lit = level.coerceIn(0, maxBar)
    val litColor = if (lit <= 0) palette.hint else signalLevelColor(lit, palette)
    val baseHeight = if (compact) 10.dp else 14.dp
    val barWidth = if (compact) 2.dp else 3.dp
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        for (i in 1..maxBar) {
            Box(
                Modifier
                    .width(barWidth)
                    .height(baseHeight * i / maxBar)
                    .clip(RoundedCornerShape(1.dp))
                    .background(if (i <= lit) litColor else palette.hint.copy(alpha = 0.28f))
            )
        }
    }
}
