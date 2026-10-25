package com.cheku.app.carmode

import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Brightness6
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.VolumeOff
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.cheku.app.findActivity
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

/** 抽屉把手的尺寸 */
private val DrawerHandleWidth = 96.dp
private val DrawerHandleHeight = 30.dp

private val CompassHandleReserve = DrawerHandleHeight + 12.dp

/** 滑动切换抽屉的判定阈值 */
private const val DRAWER_SWIPE_THRESHOLD_PX = 48f

/** 指向圆环与窗口边缘之间的呼吸 */
private val CompassEdgePadding = 4.dp

@Composable
internal fun CarDisplayPane(
    landscape: Boolean,
    compact: Boolean,
    speedText: String,
    hasFix: Boolean,
    tripMeters: Double,
    elapsedSeconds: Double,
    averageKmh: Double,
    fitRecording: Boolean,
    /** 本次 FIT 会话已记录的点列 */
    trackPoints: List<CarTrackPoint>,
    modifier: Modifier = Modifier
) {
    var controlsOpen by rememberSaveable { mutableStateOf(false) }

    val pageSwipe: Modifier = Modifier.pointerInput(Unit) {
        var acc = 0f
        detectVerticalDragGestures(
            onDragStart = { acc = 0f },
            onVerticalDrag = { _, dy -> acc += dy },
            onDragCancel = { acc = 0f },
            onDragEnd = {
                if (abs(acc) > DRAWER_SWIPE_THRESHOLD_PX) controlsOpen = acc < 0f
                acc = 0f
            }
        )
    }

    Box(modifier = modifier.fillMaxSize().then(pageSwipe)) {
        if (compact) {
            CarCompass(
                speedText = speedText,
                hasFix = hasFix,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = CompassHandleReserve / 2)
                    .zIndex(1f)
            )
        } else if (landscape) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                CarCompass(
                    speedText = speedText,
                    hasFix = hasFix,
                    modifier = Modifier
                        .fillMaxHeight()
                        .aspectRatio(1f)
                        .padding(vertical = CompassEdgePadding)
                        .zIndex(1f)
                )
                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    CarTrackRoute(
                        points = trackPoints,
                        recording = fitRecording,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .zIndex(0f)
                    )
                    TripBlock(
                        tripMeters,
                        elapsedSeconds,
                        averageKmh,
                        fitRecording,
                        compact = false,
                        landscape = false,
                        dense = true
                    )
                    Spacer(Modifier.height(DrawerHandleHeight + 4.dp))
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = DrawerHandleHeight + 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 环拿剩余高度的全部（内部再按 min(宽,高) 等比缩），轨迹固定 150dp 一条。
                CarCompass(
                    speedText = speedText,
                    hasFix = hasFix,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(vertical = CompassEdgePadding)
                        .zIndex(1f)
                )
                CarTrackRoute(
                    points = trackPoints,
                    recording = fitRecording,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .zIndex(0f)
                )
                TripBlock(tripMeters, elapsedSeconds, averageKmh, fitRecording, compact = false, landscape = false)
            }
        }

        ControlsDrawer(
            compact = compact,
            open = controlsOpen,
            onOpenChange = { controlsOpen = it },
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun ControlsDrawer(
    compact: Boolean,
    open: Boolean,
    onOpenChange: (Boolean) -> Unit,
    /** 调用方给的对齐修饰符 */
    modifier: Modifier = Modifier
) {
    // 累计纵向位移。放在组合层是为了让 onDragEnd 能读到（手势回调不在组合上下文里）。
    val swipe = remember { mutableFloatStateOf(0f) }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AnimatedVisibility(
            visible = open,
            enter = slideInVertically(initialOffsetY = { it }) + expandVertically() + fadeIn(tween(200)),
            exit = slideOutVertically(targetOffsetY = { it }) + shrinkVertically() + fadeOut(tween(160))
        ) {
            Box(
                modifier = Modifier
                    .widthIn(max = 460.dp)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onVerticalDrag = { _, dy -> swipe.floatValue += dy },
                            onDragEnd = {
                                val delta = swipe.floatValue
                                swipe.floatValue = 0f
                                if (abs(delta) > DRAWER_SWIPE_THRESHOLD_PX) {
                                    onOpenChange(delta < 0f)
                                }
                            },
                            onDragCancel = { swipe.floatValue = 0f }
                        )
                    }
            ) {
                ControlBlock(compact)
            }
        }
        CarDrawerHandle(
            open = open,
            onToggle = onOpenChange,
            swipe = swipe,
            modifier = Modifier.padding(bottom = 8.dp)
        )
    }
}

/** 行程块 */
@Composable
private fun TripBlock(
    tripMeters: Double,
    elapsedSeconds: Double,
    averageKmh: Double,
    fitRecording: Boolean,
    compact: Boolean,
    landscape: Boolean,
    dense: Boolean = false
) {
    val distance = formatTripDistanceKm(tripMeters)
    val duration = formatTripDuration(elapsedSeconds)
    val average = formatSpeedKmh(averageKmh)

    if (dense) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TripMetric(distance, "本次里程", compact, Modifier.weight(1f), dense = true)
            TripMetric(duration, "本次时长", compact, Modifier.weight(1f), dense = true)
            TripMetric(average, "均速 km/h", compact, Modifier.weight(1f), dense = true)
        }
    } else {
        Column(
            horizontalAlignment = if (landscape) Alignment.End else Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (landscape) {
                TripMetric(distance, "本次里程", compact)
                TripMetric(duration, "本次时长", compact)
                TripMetric(average, "均速 km/h", compact)
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    TripMetric(distance, "本次里程", compact)
                    TripMetric(duration, "本次时长", compact)
                    TripMetric(average, "均速 km/h", compact)
                }
            }
        }
    }
}

@Composable
private fun TripMetric(
    value: String,
    caption: String,
    compact: Boolean,
    modifier: Modifier = Modifier,
    dense: Boolean = false
) {
    val valueSize = when {
        dense -> 20.sp
        compact -> 22.sp
        else -> 26.sp
    }
    val captionSize = if (dense || compact) 10.sp else 11.sp
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            color = HubTextPrimary,
            fontSize = valueSize,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            caption,
            color = HubTextSecondary,
            fontSize = captionSize,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

/** 控件块 */
@Composable
private fun ControlBlock(compact: Boolean) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val controller = remember(activity) {
        activity?.window?.let { WindowCompat.getInsetsController(it, it.decorView) }
    }

    val canWriteBrightness = remember(context) { canWriteSystemBrightness(context) }
    var brightness by remember { mutableStateOf(systemBrightnessFraction(context)) }
    var brightnessDragging by remember { mutableStateOf(false) }
    LaunchedEffect(brightnessDragging) {
        if (brightnessDragging) {
            delay(800L)
            brightnessDragging = false
        }
    }
    LaunchedEffect(activity, brightness, canWriteBrightness) {
        val appliedToSystem = canWriteBrightness && writeSystemBrightness(context, brightness)
        val window = activity?.window ?: return@LaunchedEffect
        window.attributes = window.attributes.apply {
            screenBrightness = if (appliedToSystem) {
                WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            } else {
                brightness
            }
        }
    }
    LaunchedEffect(canWriteBrightness) {
        if (!canWriteBrightness) return@LaunchedEffect
        while (true) {
            delay(1_000L)
            if (!brightnessDragging) brightness = systemBrightnessFraction(context)
        }
    }
    DisposableEffect(activity) {
        onDispose {
            val window = activity?.window ?: return@onDispose
            window.attributes = window.attributes.apply {
                screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            }
        }
    }

    val audio = remember(context) { CarMusicController(context.applicationContext) }
    var volume by remember { mutableIntStateOf(audio.musicVolume()) }
    var volumeMax by remember { mutableIntStateOf(audio.musicVolumeMax().coerceAtLeast(1)) }
    var volumeBeforeMute by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            volume = audio.musicVolume()
            volumeMax = audio.musicVolumeMax().coerceAtLeast(1)
            delay(1_000L)
        }
    }
    val muted = volume == 0

    var barsVisible by remember { mutableStateOf(false) }
    DisposableEffect(controller) {
        onDispose { controller?.hide(WindowInsetsCompat.Type.systemBars()) }
    }

    CarCardBlock(contentPadding = if (compact) 12.dp else 16.dp) {
        Text(
            "显示与声音",
            color = HubTextSecondary,
            fontSize = if (compact) 11.sp else 13.sp,
            fontWeight = FontWeight.Bold
        )

        CarSliderRow(
            label = "亮度",
            valueText = "${(brightness * 100).roundToInt()}%",
            value = { brightness },
            valueRange = CAR_MIN_BRIGHTNESS..1f,
            compact = compact
        ) {
            brightnessDragging = true
            brightness = it
        }

        CarListRowDivider()

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CarSliderRow(
                label = "音量",
                valueText = if (muted) "静音" else "$volume/$volumeMax",
                value = { volume.toFloat() / volumeMax },
                valueRange = 0f..1f,
                compact = compact,
                modifier = Modifier.weight(1f)
            ) { newFraction ->
                val target = (newFraction * volumeMax).roundToInt().coerceIn(0, volumeMax)
                if (target > 0) volumeBeforeMute = target
                volume = target
                audio.setMusicVolume(target)
            }
            Box(
                modifier = Modifier
                    .size(if (compact) 40.dp else 46.dp)
                    .clip(CircleShape)
                    .clickable {
                        if (muted) {
                            val restore = volumeBeforeMute.coerceAtLeast(volumeMax / 3)
                            volume = restore
                            audio.setMusicVolume(restore)
                        } else {
                            volumeBeforeMute = volume
                            volume = 0
                            audio.setMusicVolume(0)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (muted) Icons.Outlined.VolumeOff else Icons.Outlined.VolumeUp,
                    contentDescription = if (muted) "取消静音" else "一键静音",
                    tint = if (muted) HubError else HubPrimaryBlue,
                    modifier = Modifier.size(if (compact) 20.dp else 24.dp)
                )
            }
        }

        CarListRowDivider()

        CarSwitchRow(
            title = "状态栏",
            subtitle = if (barsVisible) "状态栏与导航栏当前可见" else "临时放出状态栏与导航栏",
            value = barsVisible,
            compact = compact
        ) { visible ->
            barsVisible = visible
            if (visible) controller?.show(WindowInsetsCompat.Type.systemBars())
            else controller?.hide(WindowInsetsCompat.Type.systemBars())
        }
    }
}
