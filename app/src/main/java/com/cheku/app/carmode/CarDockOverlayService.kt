package com.cheku.app.carmode

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.cheku.app.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** 悬浮车机 dock */
internal class CarDockOverlayService : Service(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry

    override val viewModelStore: ViewModelStore = ViewModelStore()

    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    private var composeView: ComposeView? = null
    private var windowManager: WindowManager? = null

    /** 当前窗口参数 */
    private var dockParams: WindowManager.LayoutParams? = null

    /** 正在跑的尺寸动画 */
    private var widthAnimator: ValueAnimator? = null

    /** 展开态/ 收起态 */
    private val dockExpanded = mutableStateOf(true)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val isLandscape: Boolean
        get() = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    /** 此刻 dock 该多宽 */
    private fun currentDockWidth(): Dp =
        if (CarModeDockBus.inMultiWindow.value) CarDockCompactWidth else CarDockWidth

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        startForegroundCompat()
        addDockWindow()
        scope.launch { CarModeDockBus.inMultiWindow.collect { refreshDockGeometry() } }
        scope.launch { CarModeDockBus.carWindowBounds.collect { refreshDockGeometry() } }
        isRunning = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        // 先停订阅：窗口都要拆了，再来一次"分屏态变化"就会往一个已移除的 view 上调 updateViewLayout。
        scope.cancel()
        widthAnimator?.cancel()
        widthAnimator = null
        composeView?.let { view ->
            runCatching { windowManager?.removeView(view) }
        }
        composeView = null
        dockParams = null
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        viewModelStore.clear()
        isRunning = false
        super.onDestroy()
    }

    private fun startForegroundCompat() {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && manager != null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "车机 dock",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "在车机界面下把左侧 dock 常驻在其它应用之上"
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentTitle("车机 dock 运行中")
            .setContentText("左侧 dock 会一直浮在其他应用之上，点「退出」可结束")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setSilent(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun addDockWindow() {
        val wm = getSystemService(WindowManager::class.java) ?: return
        windowManager = wm

        val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            flags,
            PixelFormat.TRANSLUCENT
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            params.fitInsetsTypes = 0
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            params.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
        computeDockGeometry(params)
        dockParams = params

        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@CarDockOverlayService)
            setViewTreeViewModelStoreOwner(this@CarDockOverlayService)
            setViewTreeSavedStateRegistryOwner(this@CarDockOverlayService)
            setContent {
                CarDockOverlayContent(
                    expanded = dockExpanded.value,
                    onToggle = { setExpanded(it) }
                )
            }
        }
        composeView = view
        runCatching { wm.addView(view, params) }
    }

    private fun computeDockGeometry(params: WindowManager.LayoutParams) {
        val metrics = resources.displayMetrics
        val bounds = carWindowBoundsFor(metrics)
        val geometry = carDockOverlayGeometry(
            landscape = isLandscape,
            expanded = dockExpanded.value,
            inMultiWindow = CarModeDockBus.inMultiWindow.value,
            windowLeft = bounds.left,
            windowTop = bounds.top,
            windowWidth = bounds.width,
            windowHeight = bounds.height,
            density = metrics.density
        )
        params.gravity = Gravity.START or Gravity.TOP
        params.x = geometry.x
        params.y = geometry.y
        params.width = geometry.width
        params.height = geometry.height
    }

    private fun carWindowBoundsFor(metrics: android.util.DisplayMetrics): CarWindowBounds {
        val published = CarModeDockBus.carWindowBounds.value
        val multi = CarModeDockBus.inMultiWindow.value
        return if (multi && published.isValid) {
            published
        } else {
            CarWindowBounds(0, 0, metrics.widthPixels, metrics.heightPixels)
        }
    }

    /** 重新套用几何并刷新窗口 */
    private fun refreshDockGeometry() {
        val params = dockParams ?: return
        val view = composeView ?: return
        val wm = windowManager ?: return
        computeDockGeometry(params)
        runCatching { wm.updateViewLayout(view, params) }
    }

    /** 展开 / 收起悬浮 dock */
    private fun setExpanded(expanded: Boolean) {
        if (dockExpanded.value == expanded) return
        dockExpanded.value = expanded

        val params = dockParams ?: return
        val view = composeView ?: return
        val wm = windowManager ?: return
        val metrics = resources.displayMetrics
        val bounds = carWindowBoundsFor(metrics)
        fun geometryAt(target: Boolean) = carDockOverlayGeometry(
            landscape = isLandscape,
            expanded = target,
            inMultiWindow = CarModeDockBus.inMultiWindow.value,
            windowLeft = bounds.left,
            windowTop = bounds.top,
            windowWidth = bounds.width,
            windowHeight = bounds.height,
            density = metrics.density
        )
        val from = geometryAt(!expanded)
        val to = geometryAt(expanded)

        widthAnimator?.cancel()
        widthAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 180L
            addUpdateListener { animator ->
                val t = animator.animatedValue as Float
                params.x = lerpInt(from.x, to.x, t)
                params.y = lerpInt(from.y, to.y, t)
                params.width = lerpInt(from.width, to.width, t)
                params.height = lerpInt(from.height, to.height, t)
                runCatching { wm.updateViewLayout(view, params) }
            }
            start()
        }
    }

    /** 动画里的整数插值 */
    private fun lerpInt(from: Int, to: Int, t: Float): Int =
        (from + (to - from) * t).roundToInt()

    /** 悬浮窗口自身的配置变化 */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        widthAnimator?.cancel()
        refreshDockGeometry()
    }

    companion object {
        private const val CHANNEL_ID = "car_dock_overlay"
        private const val NOTIFICATION_ID = 7302

        /** dock 是否正浮在屏幕上 */
        @Volatile
        var isRunning: Boolean = false
            private set

        fun canDrawOverlays(context: Context): Boolean = Settings.canDrawOverlays(context)

        /** 跳去系统的悬浮窗授权页 */
        fun overlayPermissionIntent(context: Context): Intent =
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                .setData(android.net.Uri.parse("package:${context.packageName}"))

        /** 起 dock */
        fun start(context: Context): Boolean {
            if (!canDrawOverlays(context)) return false
            return runCatching {
                ContextCompat.startForegroundService(context, Intent(context, CarDockOverlayService::class.java))
                true
            }.getOrDefault(false)
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, CarDockOverlayService::class.java)) }
        }
    }
}

private val DockFill = Color(0xCC0B0F14)

private val DockElevated = Color(0x1FFFFFFF)
private val DockTextPrimary = Color(0xFFF2F5F8)
private val DockTextSecondary = Color(0xFFA8B2C0)
private val DockAccent = Color(0xFF4DA3FF)
private val DockSuccess = Color(0xFF43D17C)
private val DockError = Color(0xFFFF6B6B)

private val DockStatusPalette = CarStatusPalette(
    primary = DockTextPrimary,
    secondary = DockTextSecondary,
    hint = DockTextSecondary.copy(alpha = 0.55f),
    success = DockSuccess,
    error = DockError,
    warning = Color(0xFFFFB74D)
)

/** 悬浮 dock 的内容 */
@Composable
private fun CarDockOverlayContent(
    expanded: Boolean,
    onToggle: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val fitRecording by CarModeDockBus.fitRecording.collectAsStateWithLifecycle()
    val selected by CarModeDockBus.selected.collectAsStateWithLifecycle()
    // 分屏态：状态读数块据此收一档字号（与页内 dock 一致）。
    val inMultiWindow by CarModeDockBus.inMultiWindow.collectAsStateWithLifecycle()

    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    if (!expanded) {
        CarDockHandle(landscape = landscape, onExpand = { onToggle(true) })
        return
    }

    val onItem: (CarDockItem) -> Unit = onItem@{ item ->
        when (item.kind) {
            CarDockKind.Exit -> {
                CarModeDockBus.exitCarMode.tryEmit(Unit)
                CarDockOverlayService.stop(context)
            }

            CarDockKind.Pane -> {
                val pane = item.pane
                CarModeDockBus.selected.value =
                    if (pane != null && CarModeDockBus.selected.value == pane.id) null else pane?.id
                onToggle(true)
                runCatching {
                    context.startActivity(
                        Intent(context, MainActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                    )
                }
            }

            CarDockKind.Launch -> {
                val mode = decideDockLaunchMode(
                    setting = CarModeSettings(context).dockLaunchMode(),
                    deviceLandscape = landscape,
                    overlayAvailable = true
                )
                if (CarModeDockBus.inMultiWindow.value || mode == CarDockLaunchMode.Split) {
                    // 先把车机页拉回前台（分屏需要一个在前台的发起方；且本流无回放、
                    // 只有订阅者在场才投递），再把"这一格被点了"交给它跑循环。
                    onToggle(true)
                    runCatching {
                        context.startActivity(
                            Intent(context, MainActivity::class.java)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                        )
                    }
                    CarModeDockBus.itemClickRequest.tryEmit(item)
                    return@onItem
                }

                val target = resolveCarLaunchTarget(context, item)
                if (target == null) {
                    if (item == CarDockItem.Music) {
                        CarModeDockBus.selected.value = CarPane.Music.id
                        onToggle(true)
                        runCatching {
                            context.startActivity(
                                Intent(context, MainActivity::class.java)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                            )
                        }
                    } else {
                        Toast.makeText(context, "未找到可用的导航应用", Toast.LENGTH_SHORT).show()
                    }
                    return@onItem
                }
                // 悬浮 / 直接打开：目标 App 全屏，点完收起 dock（否则就是盖在人家内容上）。
                launchCarTarget(context, target, mode, null)
                onToggle(false)
            }
        }
    }

    if (landscape) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(DockFill)
        ) {
            CarDockStatusPanel(
                vertical = true,
                compact = inMultiWindow,
                palette = DockStatusPalette,
                modifier = Modifier.fillMaxWidth(),
                enablePhonePermissionRequest = false
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                CarDockItem.entries.forEach { item ->
                    OverlayDockButton(
                        item = item,
                        selected = item.isActiveFor(selected),
                        recording = fitRecording,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        onClick = { onItem(item) }
                    )
                }
            }
            CollapseBar(landscape = true, onCollapse = { onToggle(false) })
        }
    } else {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(DockFill)
        ) {
            // 状态读数块：收在横条左端（上排时间+日期、下排电池/网络/GPS）。
            CarDockStatusPanel(
                vertical = false,
                compact = inMultiWindow,
                palette = DockStatusPalette,
                modifier = Modifier.fillMaxHeight(),
                enablePhonePermissionRequest = false
            )
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                CarDockItem.entries.forEach { item ->
                    OverlayDockButton(
                        item = item,
                        selected = item.isActiveFor(selected),
                        recording = fitRecording,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        onClick = { onItem(item) }
                    )
                }
            }
            CollapseBar(landscape = false, onCollapse = { onToggle(false) })
        }
    }
}

/** 收起态 */
@Composable
private fun CarDockHandle(landscape: Boolean, onExpand: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DockFill)
            .clickable(onClick = onExpand),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            if (landscape) {
                Icons.AutoMirrored.Outlined.KeyboardArrowRight
            } else {
                Icons.Outlined.KeyboardArrowUp
            },
            contentDescription = "展开车机 dock",
            tint = DockTextSecondary,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun CollapseBar(landscape: Boolean, onCollapse: () -> Unit) {
    if (landscape) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(DockElevated)
                .clickable(onClick = onCollapse)
                .padding(vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
                contentDescription = null,
                tint = DockTextSecondary,
                modifier = Modifier.size(18.dp)
            )
            Text(
                "收起",
                color = DockTextSecondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .width(64.dp)
                .background(DockElevated)
                .clickable(onClick = onCollapse)
                .padding(horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically)
        ) {
            Icon(
                Icons.Outlined.KeyboardArrowDown,
                contentDescription = null,
                tint = DockTextSecondary,
                modifier = Modifier.size(18.dp)
            )
            Text(
                "收起",
                color = DockTextSecondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
    }
}

/** 浮层 dock 的一条 */
@Composable
private fun OverlayDockButton(
    item: CarDockItem,
    selected: Boolean,
    recording: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val tint = when {
        item.kind == CarDockKind.Exit -> DockError
        item == CarDockItem.Display && recording -> DockSuccess
        selected -> DockAccent
        else -> DockTextSecondary
    }
    val fill = if (selected) DockAccent.copy(alpha = 0.20f) else Color.Transparent
    BoxWithConstraints(
        modifier = modifier
            .clipToBounds()
            .background(fill)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        val iconSize = (minOf(maxWidth, maxHeight) * 0.62f).coerceIn(20.dp, 46.dp)
        Icon(item.icon, contentDescription = item.label, tint = tint, modifier = Modifier.size(iconSize))
    }
}
