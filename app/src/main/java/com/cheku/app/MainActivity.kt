package com.cheku.app

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.graphics.drawable.ColorDrawable
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CarRepair
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DiscFull
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.ElectricBike
import androidx.compose.material.icons.outlined.ElectricCar
import androidx.compose.material.icons.outlined.ElectricMoped
import androidx.compose.material.icons.outlined.Handyman
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Login
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.LocalGasStation
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Policy
import androidx.compose.material.icons.outlined.PedalBike
import androidx.compose.material.icons.outlined.ScreenRotation
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.TwoWheeler
import androidx.compose.material.icons.outlined.Undo
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Shapes
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.key
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import kotlin.random.Random
import com.cheku.app.data.EnergyRecordType
import com.cheku.app.data.EnergyType
import com.cheku.app.net.readBytesLimited
import com.cheku.app.performance.PerformanceService
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.layout.RowScope
import com.cheku.glass.ui.components.LiquidButton
import com.cheku.glass.ui.components.LiquidToggle
import com.cheku.glass.ui.components.LiquidSlider
import androidx.compose.ui.draw.scale
import com.cheku.app.data.hasBattery
import com.cheku.app.data.supportsCharging
import com.cheku.app.data.supportsFuel
import com.cheku.app.data.MaintenanceHistoryEntity
import com.cheku.app.data.MaintenanceStatus
import com.cheku.app.data.MaintenanceItemType
import com.cheku.app.data.TransmissionType
import com.cheku.app.data.VehicleCategory
import com.cheku.app.glass.BackgroundBlur
import com.cheku.app.glass.GlassBackdrops
import com.cheku.glass.ui.components.LiquidRangeSlider
import com.cheku.app.glass.components.LiquidGlassDialog
import com.cheku.app.glass.GlassLevel
import com.cheku.app.glass.components.applyGlassLens
import com.cheku.app.glass.components.GlassDialogMotionHost
import com.cheku.app.glass.components.glassPressFeedback
import com.cheku.app.glass.components.glassHighlightOf
import com.cheku.app.glass.components.glassShadowOf
import com.cheku.app.glass.components.glassInnerShadowOf
import com.kyant.backdrop.effects.colorControls
import com.cheku.app.glass.GlassMotion
import com.cheku.app.glass.GlassTokens
import com.cheku.app.glass.rememberLayerAwareBackdrop
import com.cheku.app.glass.aboveDock
import com.cheku.app.glass.dockBottomMargin
import com.cheku.app.glass.LocalAppDarkLevel
import com.cheku.app.glass.LocalGlassBackdrops
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.emptyBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.cheku.app.glass.components.LiquidGlassDialogButton
import com.cheku.app.glass.components.GlassSegmentedControl
import com.cheku.app.glass.components.LiquidGlassPill
import com.cheku.app.glass.components.GlassPanel
import com.cheku.app.glass.components.LiquidGlassListItem
import com.cheku.glass.ui.components.LiquidBottomTab
import com.cheku.glass.ui.components.LiquidBottomTabs
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.cheku.app.data.VehicleEntity
import com.cheku.app.domain.MaintenanceSnapshot
import com.cheku.app.fit.FitViewModel
import com.cheku.app.fit.FitLocationPolicy
import com.cheku.app.fit.FitRecordingService
import com.cheku.app.fit.FitExportState
import com.cheku.app.fit.FitExportWriter
import com.cheku.app.carmode.CarAppCatalog
import com.cheku.app.carmode.CarAppTile
import com.cheku.app.carmode.CarTrackPoint
import com.cheku.app.carmode.CarAppearanceState
import com.cheku.app.carmode.CarAppsPane
import com.cheku.app.carmode.CarBackground
import com.cheku.app.carmode.CarCallLogPane
import com.cheku.app.carmode.CarContactsPane
import com.cheku.app.carmode.CarDisplayPane
import com.cheku.app.carmode.CarDockCompactWidth
import com.cheku.app.carmode.CarDockHeight
import com.cheku.app.carmode.CarDockItem
import com.cheku.app.carmode.CarDockKind
import com.cheku.app.carmode.CarDockLaunchMode
import com.cheku.app.carmode.CarDockLaunchStage
import com.cheku.app.carmode.CarDockOverlayService
import com.cheku.app.carmode.CarDockWidth
import com.cheku.app.carmode.CarLaunchTarget
import com.cheku.app.carmode.CarModeDefaultPane
import com.cheku.app.carmode.CarModeDockBus
import com.cheku.app.carmode.CarModeOrientation
import com.cheku.app.carmode.CarModeSettings
import com.cheku.app.carmode.CarMusicPane
import com.cheku.app.carmode.CarPane
import com.cheku.app.carmode.CarRotationController
import com.cheku.app.carmode.CarSettingsPane
import com.cheku.app.carmode.CarSmsPane
import com.cheku.app.carmode.CarDockStatusPanel
import com.cheku.app.carmode.rememberHubStatusPalette
import com.cheku.app.carmode.CarThemeMode
import com.cheku.app.carmode.CarVehicleChoice
import com.cheku.app.carmode.CarVehicleKind
import com.cheku.app.carmode.CarWindowBounds
import com.cheku.app.carmode.FREEFORM_SUPPORT_SETTING
import com.cheku.app.carmode.LaunchBounds
import com.cheku.app.carmode.buildCarAppCatalog
import com.cheku.app.carmode.carMusicChoices
import com.cheku.app.carmode.carNavChoices
import com.cheku.app.carmode.decideDockLaunchMode
import com.cheku.app.carmode.formatClock
import com.cheku.app.carmode.formatSpeedKmh
import com.cheku.app.carmode.formatTripDistanceKm
import com.cheku.app.carmode.formatTripDuration
import com.cheku.app.carmode.focusCarModeExclusive
import com.cheku.app.carmode.haversineMeters
import com.cheku.app.carmode.CarMotionDetector
import com.cheku.app.carmode.derivedSpeedMps
import com.cheku.app.carmode.isLikelyStationary
import com.cheku.app.carmode.isSuspectZeroSpeed
import com.cheku.app.carmode.nextDockLaunchStage
import com.cheku.app.carmode.launchCarTarget
import com.cheku.app.carmode.mpsToKmh
import com.cheku.app.carmode.resolveCarLaunchTarget
import com.cheku.app.carmode.resolvePackageTarget
import com.cheku.app.carmode.TripAccumulator
import com.cheku.app.carmode.tripAverageSpeedKmh
import com.cheku.app.performance.PerformanceViewModel
import com.cheku.app.data.PerformanceTestType
import com.cheku.app.data.PerformanceStatus
import com.cheku.app.data.PerformanceQuality
import com.cheku.app.data.FitActivityEntity
import com.cheku.app.data.FitActivityStatus
import com.cheku.app.data.FitSampleEntity
import com.cheku.app.replay.ReplaySeries
import com.cheku.app.speed.SpeedEstimator
import java.util.Locale
import kotlin.math.abs
import kotlin.math.pow
import androidx.compose.animation.core.animate
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Shape
import kotlinx.coroutines.Job
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    private val viewModel: LedgerViewModel by viewModels()

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (BuildConfig.DEBUG) ChekuDebugNav.readFrom(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestHighestRefreshRate()
        CarRotationController.recoverIfNeeded(this)
        if (BuildConfig.DEBUG) ChekuDebugNav.readFrom(intent)
        setContent {
            val preferences = remember { getSharedPreferences("appearance", Context.MODE_PRIVATE) }
            var themeMode by rememberSaveable { mutableStateOf(preferences.getString("theme", "system") ?: "system") }
            var backgroundMode by rememberSaveable { mutableStateOf(preferences.getString("background", "static") ?: "static") }
            var customBackground by rememberSaveable { mutableStateOf(preferences.getString("custom_background", null)) }
            var backgroundBlur by rememberSaveable { mutableStateOf(preferences.getFloat("background_blur", 12f)) }
            LaunchedEffect(Unit) {
                snapshotFlow { backgroundBlur }.collect {
                    delay(250)
                    preferences.edit().putFloat("background_blur", it).apply()
                }
            }
            val backgroundPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                if (uri != null) runCatching {
                    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    customBackground = uri.toString(); backgroundMode = "custom"
                    preferences.edit().putString("custom_background", customBackground).putString("background", backgroundMode).apply()
                }
            }
            CheKuTheme(themeMode) {
                LedgerApp(
                    viewModel,
                    themeMode = themeMode,
                    onThemeModeChange = { value -> themeMode = value; preferences.edit().putString("theme", value).apply() },
                    backgroundMode = backgroundMode,
                    onBackgroundModeChange = { value -> backgroundMode = value; preferences.edit().putString("background", value).apply() },
                    customBackground = customBackground,
                    onChooseCustomBackground = { backgroundPicker.launch(arrayOf("image/*")) },
                    backgroundBlurProvider = { backgroundBlur },
                    onBackgroundBlurChange = { value -> backgroundBlur = value }   // 落盘已由上方 LaunchedEffect 防抖
                )
            }
        }
    }

    private fun requestHighestRefreshRate() {
        @Suppress("DEPRECATION")
        val appDisplay = windowManager.defaultDisplay
        val current = appDisplay.mode
        val fastest = appDisplay.supportedModes
            .filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
            .maxByOrNull { it.refreshRate }
            ?: return
        window.attributes = window.attributes.apply { preferredDisplayModeId = fastest.modeId }
    }
}

/** 0 = fully light, 1 = fully dark */
private var AppDarkLevel by mutableStateOf(0f)

/** Structural decisions  still need a plain yes/no */
private val AppDarkMode: Boolean get() = AppDarkLevel >= 0.5f

private fun themeColor(light: Color, dark: Color): Color =
    androidx.compose.ui.graphics.lerp(light, dark, AppDarkLevel)

private val PrimaryBlue get() = themeColor(Color(0xFF0058BC), Color(0xFF79AFFF))
private val PageBackground get() = themeColor(Color(0xFFE9F0F8), Color(0xFF0C121B))
private val Warning = Color(0xFFFF9500)
private val Error = Color(0xFFFF3B30)
private val Success = Color(0xFF1D9B58)
private val TextPrimary get() = themeColor(Color(0xFF333333), Color(0xFFF3F6FA))
private val TextSecondary get() = themeColor(Color(0xFF666666), Color(0xFFC4CDD8))
private val TextTertiary get() = themeColor(Color(0xFF999999), Color(0xFF98A6B7))
private val TextHint get() = themeColor(Color(0xFFAAAAAA), Color(0xFF8391A2))

/** 亮色与暗色的 Material 配色端点 */
private val LightScheme = lightColorScheme(
    primary = Color(0xFF0058BC),
    background = Color(0xFFE9F0F8),
    surface = Color.White,
    error = Color(0xFFFF3B30)
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF8CB4FF),
    onPrimary = Color(0xFF07111F),
    primaryContainer = Color(0xFF263548),
    onPrimaryContainer = Color(0xFFF1F5FA),
    background = Color(0xFF0D121A),
    onBackground = Color(0xFFF1F5FA),
    surface = Color(0xFF161D27),
    onSurface = Color(0xFFF1F5FA),
    surfaceVariant = Color(0xFF263548),
    onSurfaceVariant = Color(0xFFC4CDD8),
    error = Color(0xFFFF8A8A),
    errorContainer = Color(0xFF3A0000),
    outline = Color(0xFF75869A),
    outlineVariant = Color(0xFF435166)
)

private fun lerpColorScheme(t: Float): ColorScheme {
    val from = LightScheme
    val to = DarkScheme
    fun mix(a: Color, b: Color) = androidx.compose.ui.graphics.lerp(a, b, t)
    return from.copy(
        primary = mix(from.primary, to.primary),
        onPrimary = mix(from.onPrimary, to.onPrimary),
        primaryContainer = mix(from.primaryContainer, to.primaryContainer),
        onPrimaryContainer = mix(from.onPrimaryContainer, to.onPrimaryContainer),
        inversePrimary = mix(from.inversePrimary, to.inversePrimary),
        secondary = mix(from.secondary, to.secondary),
        onSecondary = mix(from.onSecondary, to.onSecondary),
        secondaryContainer = mix(from.secondaryContainer, to.secondaryContainer),
        onSecondaryContainer = mix(from.onSecondaryContainer, to.onSecondaryContainer),
        tertiary = mix(from.tertiary, to.tertiary),
        onTertiary = mix(from.onTertiary, to.onTertiary),
        tertiaryContainer = mix(from.tertiaryContainer, to.tertiaryContainer),
        onTertiaryContainer = mix(from.onTertiaryContainer, to.onTertiaryContainer),
        background = mix(from.background, to.background),
        onBackground = mix(from.onBackground, to.onBackground),
        surface = mix(from.surface, to.surface),
        onSurface = mix(from.onSurface, to.onSurface),
        surfaceVariant = mix(from.surfaceVariant, to.surfaceVariant),
        onSurfaceVariant = mix(from.onSurfaceVariant, to.onSurfaceVariant),
        surfaceTint = mix(from.surfaceTint, to.surfaceTint),
        inverseSurface = mix(from.inverseSurface, to.inverseSurface),
        inverseOnSurface = mix(from.inverseOnSurface, to.inverseOnSurface),
        error = mix(from.error, to.error),
        onError = mix(from.onError, to.onError),
        errorContainer = mix(from.errorContainer, to.errorContainer),
        onErrorContainer = mix(from.onErrorContainer, to.onErrorContainer),
        outline = mix(from.outline, to.outline),
        outlineVariant = mix(from.outlineVariant, to.outlineVariant),
        scrim = mix(from.scrim, to.scrim),
        surfaceBright = mix(from.surfaceBright, to.surfaceBright),
        surfaceDim = mix(from.surfaceDim, to.surfaceDim),
        surfaceContainer = mix(from.surfaceContainer, to.surfaceContainer),
        surfaceContainerHigh = mix(from.surfaceContainerHigh, to.surfaceContainerHigh),
        surfaceContainerHighest = mix(from.surfaceContainerHighest, to.surfaceContainerHighest),
        surfaceContainerLow = mix(from.surfaceContainerLow, to.surfaceContainerLow),
        surfaceContainerLowest = mix(from.surfaceContainerLowest, to.surfaceContainerLowest)
    )
}

internal object ChekuDebugNav {
    var pendingScreen: String? = null
    var pendingDialog: String? = null
    var pendingTab: Int? = null
    var pendingPage: String? = null

    fun readFrom(intent: Intent?) {
        if (intent == null) {
            android.util.Log.i("ChekuDebugNav", "readFrom: intent == null")
            return
        }
        intent.getStringExtra("cheku_debug_tab")?.toIntOrNull()?.let { pendingTab = it }
        intent.getStringExtra("cheku_debug_screen")?.let { pendingScreen = it }
        intent.getStringExtra("cheku_debug_dialog")?.let { pendingDialog = it }
        intent.getStringExtra("cheku_debug_page")?.let { pendingPage = it }
        android.util.Log.i(
            "ChekuDebugNav",
            "readFrom: tab=$pendingTab screen=$pendingScreen page=$pendingPage dialog=$pendingDialog action=${intent.action}"
        )
    }

    fun takeTab(): Int? = pendingTab.also { pendingTab = null }

    fun takeScreen(name: String): Boolean =
        if (pendingScreen == name) {
            pendingScreen = null
            true
        } else {
            false
        }

    fun takePage(name: String): Boolean =
        if (pendingPage == name) {
            pendingPage = null
            true
        } else {
            false
        }

    fun takeDialog(name: String): Boolean =
        if (pendingDialog == name) {
            pendingDialog = null
            true
        } else {
            false
        }
}

private enum class AppTab(val title: String, val icon: ImageVector) {
    Garage("车库", Icons.Outlined.DirectionsCar),
    Record("记录", Icons.Outlined.Speed),
    Maintenance("保养", Icons.Outlined.Build),
    Tools("工具", Icons.Outlined.Handyman),
    Settings("设置", Icons.Outlined.Settings)
}

private enum class VehiclePicker {
    Kind,
    Energy,
    Transmission
}

internal enum class VehicleKind(
    val label: String,
    val category: VehicleCategory,
    val defaultEnergy: EnergyType,
    val defaultTransmission: TransmissionType
) {
    FuelCar("\u71c3\u6cb9\u6c7d\u8f66", VehicleCategory.Car, EnergyType.Fuel, TransmissionType.Automatic),
    FuelMotorcycle("\u71c3\u6cb9\u6469\u6258", VehicleCategory.Motorcycle, EnergyType.Fuel, TransmissionType.Cvt),
    ElectricMotorcycle("\u7535\u52a8\u6469\u6258", VehicleCategory.ElectricMotorcycle, EnergyType.Electric, TransmissionType.ElectricDrive),
    ElectricBike("\u7535\u52a8\u81ea\u884c\u8f66", VehicleCategory.ElectricBike, EnergyType.Electric, TransmissionType.ElectricDrive),
    Bicycle("\u81ea\u884c\u8f66", VehicleCategory.Bicycle, EnergyType.Human, TransmissionType.None),
    NewEnergyCar("\u65b0\u80fd\u6e90\u6c7d\u8f66", VehicleCategory.Car, EnergyType.Electric, TransmissionType.ElectricDrive)
}

private enum class RecordMode(val title: String) {
    Mileage("里程"),
    Fuel("加油"),
    Charge("充电")
}

private data class PendingRecordSave(
    val mode: RecordMode,
    val mileage: String,
    val note: String,
    val roadFee: String,
    val recordTime: String,
    val energyForm: EnergyRecordFormInput? = null
)

@Composable
private fun CheKuTheme(themeMode: String, content: @Composable () -> Unit) {
    val dark = themeMode == "dark" || (themeMode == "system" && isSystemInDarkTheme())
    val level by animateFloatAsState(
        targetValue = if (dark) 1f else 0f,
        animationSpec = tween(
            durationMillis = 560,
            easing = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)
        ),
        label = "theme-dark-level"
    )
    AppDarkLevel = level
    MaterialTheme(
        colorScheme = lerpColorScheme(level),
        shapes = Shapes(
            small = RoundedCornerShape(14.dp),
            medium = GlassTokens.shapeCard,
            large = GlassTokens.shapeCard
        ),
        content = {
            // Expose the animated theme level to the glass layer so every glass token
            // interpolates on the same 560ms timeline as the rest of the UI.
            CompositionLocalProvider(
                LocalContentColor provides TextPrimary,
                LocalAppDarkLevel provides level
            ) { content() }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LedgerApp(
    viewModel: LedgerViewModel,
    themeMode: String,
    onThemeModeChange: (String) -> Unit,
    backgroundMode: String,
    onBackgroundModeChange: (String) -> Unit,
    customBackground: String?,
    onChooseCustomBackground: () -> Unit,
    backgroundBlurProvider: () -> Float = { 12f },
    onBackgroundBlurChange: (Float) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val undoAvailable by viewModel.undoAvailable.collectAsStateWithLifecycle()
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()
    // 更新弹窗的"安装包大小"与下载进度（都在 ViewModel 里，见 UpdateDownloadState）
    val updatePackageBytes by viewModel.updatePackageBytes.collectAsStateWithLifecycle()
    val updatePackageProbing by viewModel.updatePackageProbing.collectAsStateWithLifecycle()
    val updateDownload by viewModel.updateDownload.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    var tab by rememberSaveable {
        mutableStateOf(
            if (BuildConfig.DEBUG) {
                ChekuDebugNav.takeTab()?.let { i -> AppTab.entries.getOrNull(i) } ?: AppTab.Garage
            } else {
                AppTab.Garage
            }
        )
    }
    if (BuildConfig.DEBUG) {
        LaunchedEffect(Unit) {
            // 保留：处理「应用已在运行时 am start」经 onNewIntent 送达的深链（冷启动已由 tab 初始值覆盖）
            val idx = ChekuDebugNav.takeTab()
            android.util.Log.i("ChekuDebugNav", "effect takeTab=$idx")
            idx?.let { index ->
                AppTab.entries.getOrNull(index)?.let { tab = it }
            }
        }
    }
    // 「本地数据管理」二级页选定的导出范围（默认全选）。二级页写入、导出时读取。
    var pendingRange by remember { mutableStateOf(BackupExportRange()) }
    var showDataManagement by rememberSaveable {
        mutableStateOf(BuildConfig.DEBUG && ChekuDebugNav.takeScreen("DataManagement"))
    }
    var showCloudBackup by rememberSaveable {
        mutableStateOf(BuildConfig.DEBUG && ChekuDebugNav.takeScreen("CloudBackup"))
    }
    var showFeedback by rememberSaveable {
        mutableStateOf(BuildConfig.DEBUG && ChekuDebugNav.takeScreen("Feedback"))
    }
    // 「反馈与意见」的编辑内容：挂在应用壳这一层，
    // 返回设置页再进来不会丢草稿（提交状态由二级页自己持有，见 FeedbackScreen）。
    var feedbackText by rememberSaveable { mutableStateOf("") }
    val accountEmail by viewModel.accountEmail.collectAsState()
    val cloudBusy by viewModel.cloudBusy.collectAsState()
    // 云端用量（配额/已用）与传输进度：账户页头像下方配额区、上传/恢复进度条用
    val cloudUsage by viewModel.cloudUsage.collectAsState()
    val cloudProgress by viewModel.cloudProgress.collectAsState()
    // 注销宽限期到期时间（非 null = 待注销，账户页显示撤销入口）
    val accountDeletionAt by viewModel.accountDeletionAt.collectAsState()
    // 备份限速（后台可配）：进账户页时连后台设置一起刷新，保证界面文字与后台一致
    val backupRateLimitKbps by viewModel.backupRateLimitKbps.collectAsState()
    // 进入账户与云备份页时刷新一次：云端用量（配额/备份大小/最近备份时间）+ 传输策略（限速）
    LaunchedEffect(showCloudBackup) {
        if (showCloudBackup) {
            viewModel.refreshCloudUsage()
            viewModel.refreshTransferPolicy()
        }
    }
    val registerCodeState by viewModel.registerCodeState.collectAsState()
    val forgotState by viewModel.forgotState.collectAsState()
    /** 远程功能开关，后台可关闭 */
    val runtimeFeatures by viewModel.runtimeFeatures.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    /** 导出历史的存储 */
    val exportHistoryStore = remember(context) {
        BackupExportHistoryStore(
            context.getSharedPreferences(BackupExportHistoryStore.Key.PREFERENCES_NAME, Context.MODE_PRIVATE)
        )
    }
    val carModeSettings = remember(context) { CarModeSettings(context.applicationContext) }
    var carMode by rememberSaveable {
        mutableStateOf(
            (BuildConfig.DEBUG && ChekuDebugNav.takeScreen("CarMode")) ||
                carModeSettings.autoStart()
        )
    }
    BackHandler(enabled = tab != AppTab.Garage) {
        tab = AppTab.Garage
    }
    var exportStamp by remember { mutableStateOf(0L) }

    /** 真正写盘 */
    val performExport: (Uri) -> Unit = { businessUri ->
        viewModel.viewModelScope.launch {
            try {
                val output = context.contentResolver.openOutputStream(businessUri)
                    ?: error("无法打开导出文件")
                val counting = CountingOutputStream(output)
                counting.use { out -> viewModel.exportPackageTo(out, pendingRange, exportStamp) }
                exportHistoryStore.append(
                    BackupExportHistoryStore.Key.BUSINESS,
                    BackupExportRecord(
                        at = System.currentTimeMillis(),
                        fileName = BackupPackage.businessFileName(exportStamp),
                        bytes = counting.count,
                        scope = pendingRange.summary
                    )
                )
                viewModel.showMessage("已导出用车数据（可用 Excel 打开）")
            } catch (e: OutOfMemoryError) {
                viewModel.showMessage("备份内容太大，内存不足，导出失败；可减少包含的数据后重试")
            } catch (e: Exception) {
                viewModel.showMessage("导出失败: ${e.message}")
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        performExport(uri)
    }

    var certsStamp by remember { mutableStateOf(0L) }
    val certsArchiveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        viewModel.viewModelScope.launch {
            try {
                val output = context.contentResolver.openOutputStream(uri)
                    ?: error("无法打开导出文件")
                // 逐份读逐份写，峰值只有一块 64KB 缓冲（不整份物化）
                output.use { viewModel.exportCertsArchiveTo(it, certsStamp) }
                viewModel.showMessage("已导出人车证书包")
            } catch (e: OutOfMemoryError) {
                viewModel.showMessage("证书太多，内存不足，导出失败")
            } catch (e: Exception) {
                viewModel.showMessage("证书包导出失败: ${e.message}")
            }
        }
    }

    var pendingImport by remember { mutableStateOf<LedgerViewModel.ImportPreview?>(null) }

    val commitImport: (LedgerViewModel.ImportPreview, List<CertificateEntry>) -> Unit = { preview, archive ->
        viewModel.viewModelScope.launch {
            try {
                viewModel.showMessage(viewModel.commitImport(preview, archive))
            } catch (e: OutOfMemoryError) {
                viewModel.showMessage("备份太大，内存不足，导入失败")
            } catch (e: Exception) {
                viewModel.showMessage("导入失败: ${e.message}")
            }
        }
    }

    val importArchiveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { archiveUri ->
        val preview = pendingImport
        pendingImport = null
        if (preview == null) return@rememberLauncherForActivityResult
        if (archiveUri == null) {
            commitImport(preview, emptyList())
            return@rememberLauncherForActivityResult
        }
        viewModel.viewModelScope.launch {
            try {
                val message = viewModel.importWithArchive(preview) {
                    context.contentResolver.openInputStream(archiveUri)
                }
                viewModel.showMessage(message)
            } catch (e: OutOfMemoryError) {
                viewModel.showMessage("证书包太大，内存不足，导入失败")
            } catch (e: Exception) {
                viewModel.showMessage("证书包导入失败: ${e.message}")
            }
        }
    }

    /** 新版包的待确认预览 + 它的来源 URI */
    var pendingPackageImport by remember { mutableStateOf<LedgerViewModel.PackageImportPreview.Ready?>(null) }
    var pendingPackageUri by remember { mutableStateOf<Uri?>(null) }

    pendingPackageImport?.let { preview ->
        GlassDialog(
            onDismissRequest = {
                pendingPackageImport = null
                pendingPackageUri = null
            },
            title = "从新版包恢复",
            text = {
                Column {
                    Text(
                        "将**覆盖**当前全部数据（导入前会自动存一份安全备份）。",
                        fontSize = 13.sp, color = TextSecondary
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "车辆 ${preview.data.vehicles.size} · 里程 ${preview.data.mileageRecords.size} · " +
                            "能源 ${preview.data.energyRecords.size} · 保养 ${preview.data.maintenanceHistory.size} · " +
                            "轨迹 ${preview.data.fitActivities.size} · 性能 ${preview.data.performanceSessions.size}",
                        fontSize = 13.sp, color = TextPrimary
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "已知有损：时间只到秒；保养/性能/轨迹的创建与更新时间用业务时间兜底；" +
                            "保养记录的保险/维修金额无法还原（导出里没有这两列）；" +
                            "能源结算与费用汇总会**重算**。" +
                            (if (preview.certsEntryName != null) "\n本包含证书，将一并恢复。" else "\n本包不含证书。"),
                        fontSize = 12.sp, color = TextTertiary
                    )
                    if (preview.warnings.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "${preview.warnings.size} 处无法识别，已回落默认值：" +
                                preview.warnings.take(2).joinToString("；") {
                                    "${it.sheet} 第 ${it.row + 2} 行 ${it.column}=${it.raw}"
                                },
                            fontSize = 12.sp, color = Warning
                        )
                    }
                }
            },
            confirmButton = {
                GlassDialogAction(onClick = {
                    val target = pendingPackageImport
                    val sourceUri = pendingPackageUri
                    pendingPackageImport = null
                    pendingPackageUri = null
                    if (target == null || sourceUri == null) return@GlassDialogAction
                    viewModel.viewModelScope.launch {
                        try {
                            viewModel.showMessage(viewModel.commitPackageImport(target, context.cacheDir))
                            if (target.certsEntryName != null) {
                                val certs = viewModel.restoreCertificatesFromPackage {
                                    context.contentResolver.openInputStream(sourceUri)
                                }
                                if (certs != null) viewModel.showMessage(certs)
                            }
                        } catch (e: Throwable) {
                            viewModel.showMessage("导入失败：${e.message ?: e.javaClass.simpleName}")
                        }
                    }
                }) { Text("覆盖并恢复", color = Error) }
            },
            dismissButton = {
                GlassDialogAction(onClick = {
                    pendingPackageImport = null
                    pendingPackageUri = null
                }) { Text("取消") }
            }
        )
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        viewModel.viewModelScope.launch {
            try {
                when (val step = viewModel.importAuto { context.contentResolver.openInputStream(uri) }) {
                    is LedgerViewModel.ImportStep.Done -> viewModel.showMessage(step.message)

                    is LedgerViewModel.ImportStep.NeedPackageCommit -> {
                        pendingPackageImport = step.preview
                        pendingPackageUri = uri
                    }
                    is LedgerViewModel.ImportStep.NeedBusinessCommit -> {
                        val preview = step.preview
                        if (preview.needsArchiveFile) {
                            pendingImport = preview
                            viewModel.showMessage("备份包声明了配套证书包，请接着选择它（可取消）")
                            importArchiveLauncher.launch(arrayOf("application/octet-stream", "*/*"))
                        } else {
                            // 旧整合包自带证书段（`inlineArchive`）；新主包没有配套包时这里是空列表
                            commitImport(preview, preview.inlineArchive)
                        }
                    }
                }
            } catch (e: OutOfMemoryError) {
                viewModel.showMessage("备份太大，内存不足，导入失败")
            } catch (e: Exception) {
                viewModel.showMessage("导入失败: ${e.message}")
            }
        }
    }

    var xiongVehicleId by rememberSaveable { mutableStateOf<String?>(null) }
    val xiongLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val vehicleId = xiongVehicleId
        if (uri != null && vehicleId != null) {
            viewModel.viewModelScope.launch {
                runCatching {
                    context.contentResolver.openInputStream(uri)
                        ?.use { it.readBytesLimited(XlsSheetReader.MAX_XLS_BYTES) }
                        ?: error("无法读取小熊油耗文件")
                }.onSuccess { viewModel.importXiongFuel(vehicleId, it) }
                    .onFailure { viewModel.showMessage("小熊油耗导入失败：${it.message}") }
            }
        }
        xiongVehicleId = null
    }

    var showXiongVehiclePicker by rememberSaveable { mutableStateOf(false) }
    var xiongVehiclePickerExpanded by rememberSaveable { mutableStateOf(false) }
    var showClearConfirm by rememberSaveable { mutableStateOf(BuildConfig.DEBUG && ChekuDebugNav.takeDialog("ClearData")) }
    val xiongSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val xiongPanelHeight = LocalConfiguration.current.screenHeightDp.dp * 0.75f

    if (showClearConfirm) {
        GlassDialog(
            onDismissRequest = { showClearConfirm = false },
            title = "清除所有数据",
            text = { Text("确认清除所有车辆、里程、加油/充电和保养数据？此操作不可撤销。") },
            confirmButton = {
                GlassDialogAction(onClick = {
                    viewModel.clearAllData()
                    showClearConfirm = false
                }) { Text("确认清除", color = Error) }
            },
            dismissButton = {
                GlassDialogAction(onClick = { showClearConfirm = false }) { Text("取消") }
            }
        )
    }
    val backgroundBackdrop = rememberLayerBackdrop()
    CompositionLocalProvider(
        LocalGlassBackdrops provides GlassBackdrops(background = backgroundBackdrop)
    ) {
    if (showXiongVehiclePicker) {
        val selectedXiongVehicle = state.vehicles.firstOrNull { it.id == xiongVehicleId }
        ModalBottomSheet(
            onDismissRequest = { showXiongVehiclePicker = false },
            sheetState = xiongSheetState,
            modifier = Modifier.padding(top = 12.dp),
            shape = GlassTokens.shapeCard,
            containerColor = Color.Transparent,
            // 与 5 个 L3 弹窗同一档遮罩（LiquidGlassDialog 的 0.32 黑），避免 sheet 弹出时
            // 页面内容毫无压暗、和弹窗两套浮层语汇。
            scrimColor = Color.Black.copy(alpha = 0.32f),
            dragHandle = null
        ) {
            GlassFloatingSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(xiongPanelHeight)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("选择导入车辆", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Black)
                    Text("选择小熊油耗记录要导入到的车辆", color = TextSecondary, fontSize = 13.sp)
                    GlassActionButton(
                        "选择文件",
                        Icons.Outlined.FolderOpen,
                        PrimaryBlue,
                        enabled = selectedXiongVehicle != null
                    ) {
                        selectedXiongVehicle?.let { vehicle ->
                            xiongVehicleId = vehicle.id
                            xiongLauncher.launch(arrayOf("application/vnd.ms-excel", "application/xls", "*/*"))
                        }
                        showXiongVehiclePicker = false
                    }
                    VehicleSelectorCard(
                        vehicle = selectedXiongVehicle,
                        open = xiongVehiclePickerExpanded,
                        vehicles = state.vehicles,
                        consumptions = state.vehicleConsumptions,
                        solid = true,
                        onToggle = { xiongVehiclePickerExpanded = !xiongVehiclePickerExpanded },
                        onSelect = {
                            xiongVehicleId = it.id
                            xiongVehiclePickerExpanded = false
                        }
                    )
                    GlassDialogAction(
                        modifier = Modifier.align(Alignment.End),
                        onClick = { showXiongVehiclePicker = false }) { Text("取消") }
                }
            }
        }
    }

    LaunchedEffect(state.message) {
        state.message?.let { msg ->
            val result = snackbarHostState.showSnackbar(
                message = msg,
                actionLabel = if (undoAvailable && msg == "保养已完成") "撤销" else null,
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.undoLastMaintenance()
            viewModel.clearMessage()
        }
    }

    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().layerBackdrop(backgroundBackdrop)) {
            Box(Modifier.fillMaxSize().meshBackground())
            if (backgroundMode == "mesh") AnimatedMeshBackground()
            if (backgroundMode == "custom" && !customBackground.isNullOrBlank()) CustomBackground(customBackground, backgroundBlurProvider)
        }
        Box(modifier = Modifier.fillMaxSize()) {
            SerialContentTransition(
                target = tab,
                directionOf = { from, to -> if (to.ordinal > from.ordinal) 1 else -1 }
            ) { currentTab ->
            when (currentTab) {
                AppTab.Garage -> GarageScreen(
                    state = state,
                    onSelectVehicle = viewModel::selectVehicle,
                    onAddVehicle = viewModel::addVehicle,
                    onUpdateVehicle = viewModel::updateVehicle,
                    onDeleteVehicle = viewModel::deleteVehicle
                )
                AppTab.Record -> RecordScreen(
                    state = state,
                    onSelectVehicle = viewModel::selectVehicle,
                    onSaveMileage = viewModel::saveMileage,
                    onSaveEnergy = viewModel::saveEnergy,
                    onDeleteRecord = viewModel::deleteRecord,
                    onUpdateMileageRecord = viewModel::updateMileageRecord,
                    onUpdateEnergyRecord = viewModel::updateEnergyRecord
                )
                AppTab.Maintenance -> MaintenanceScreen(
                    state = state,
                    onSelectVehicle = viewModel::selectVehicle,
                    onCompleteMaintenance = viewModel::completeMaintenance,
                    onAddCustomMaintenance = viewModel::addCustomMaintenance,
                    onUpdateMaintenanceItem = viewModel::updateMaintenanceItem,
                    onDeleteMaintenanceItem = viewModel::deleteMaintenanceItem,
                    getHistoryFlow = viewModel::getHistoryFlow,
                    onUpdateHistory = viewModel::updateMaintenanceHistory,
                    onDeleteHistory = viewModel::deleteMaintenanceHistory,
                    onCompleteInsurance = viewModel::completeInsurance,
                    onAddRepair = viewModel::addRepairInspection,
                    onUpdateSpecialHistory = viewModel::updateSpecialMaintenanceHistory
                )
                AppTab.Tools -> ToolsScreen(
                    state, viewModel::selectVehicle, viewModel::updateEnergyRecord,
                    viewModel::updateMaintenanceHistory, viewModel::updateSpecialMaintenanceHistory,
                    viewModel::deleteRecord, viewModel::deleteMaintenanceHistory,
                    onStartCarMode = { carMode = true }
                )
                AppTab.Settings -> {
                    val settingsPage = when {
                        showDataManagement -> "data"
                        showCloudBackup -> "cloud"
                        showFeedback -> "feedback"
                        else -> "home"
                    }
                    AnimatedContent(
                        targetState = settingsPage,
                        transitionSpec = { hierarchyMotion(enteringDetail = targetState != "home") },
                        label = "settings-hierarchy"
                    ) { settingsTarget ->
                    when (settingsTarget) {
                    "data" -> DataManagementScreen(
                    state = state,
                    range = pendingRange,
                    onRangeChange = { pendingRange = it },
                    onExportBusiness = {
                        exportStamp = System.currentTimeMillis()
                        exportLauncher.launch(BackupPackage.businessFileName(exportStamp))
                    },
                    onExportArchiveZip = {
                        certsStamp = System.currentTimeMillis()
                        certsArchiveLauncher.launch(BackupPackage.certsArchiveFileName(certsStamp))
                    },
                    onImportFile = { importLauncher.launch(arrayOf("application/octet-stream", "*/*")) },
                    // 本机数据的进/出也在这页（原在设置页；两个弹层的状态已上提到这里）
                    onImportXiong = {
                        xiongVehicleId = state.selectedVehicle?.id ?: state.vehicles.firstOrNull()?.id
                        xiongVehiclePickerExpanded = false
                        showXiongVehiclePicker = true
                    },
                    onClearAllData = { showClearConfirm = true },
                    onBack = { showDataManagement = false }
                )
                    "cloud" -> CloudBackupScreen(
                    email = accountEmail,
                    busy = cloudBusy,
                    cloudBackupEnabled = runtimeFeatures.cloudBackup,
                    cloudUsage = cloudUsage,
                    cloudProgress = cloudProgress,
                    accountDeletionAt = accountDeletionAt,
                    backupRateLimitKbps = backupRateLimitKbps,
                    onRefreshCloudUsage = { viewModel.refreshCloudUsage() },
                    onPurgeCloudBackup = viewModel::purgeCloudBackup,
                    onDeleteAccount = viewModel::deleteAccount,
                    onCancelDeletion = viewModel::cancelAccountDeletion,
                    registerCodeState = registerCodeState,
                    forgotState = forgotState,
                    onSendCode = viewModel::sendRegisterCode,
                    onClearRegisterCodeState = viewModel::clearRegisterCodeState,
                    onSendResetCode = viewModel::sendResetCode,
                    onClearForgotState = viewModel::clearForgotState,
                    onResetPassword = viewModel::resetPassword,
                    onRegister = viewModel::registerAccount,
                    onLogin = viewModel::loginAccount,
                    onLogout = viewModel::logoutAccount,
                    onUpload = { viewModel.uploadToCloud(pendingRange) },
                    onRestore = viewModel::restoreFromCloud,
                    onBack = { showCloudBackup = false }
                )
                    "feedback" -> FeedbackScreen(
                    text = feedbackText,
                    onTextChange = { feedbackText = it },
                    onBack = { showFeedback = false }
                )
                    else -> SettingsScreen(
                    state = state,
                    updateState = updateState,
                    onUpdateChargePreset = viewModel::updateChargePreset,
                    onUpdateDistanceUnit = viewModel::updateDistanceUnit,
                    onCheckForUpdates = { viewModel.checkForUpdates(manual = true) },
                    onOpenDataManagement = { showDataManagement = true },
                    onOpenCloudBackup = { showCloudBackup = true },
                    onOpenFeedback = { showFeedback = true },
                    accountEmail = accountEmail,
                    accountsEnabled = runtimeFeatures.accounts,
                    themeMode = themeMode,
                    onThemeModeChange = onThemeModeChange,
                    backgroundMode = backgroundMode,
                    onBackgroundModeChange = onBackgroundModeChange,
                    customBackground = customBackground,
                    onChooseCustomBackground = onChooseCustomBackground,
                    backgroundBlurProvider = backgroundBlurProvider,
                    onBackgroundBlurChange = onBackgroundBlurChange
                )
                    }
                    }
                }
            }
            }
            BrandTopBar(tab)
            FloatingDock(
                selectedTab = tab,
                onSelectTab = { tab = it },
                backdrop = backgroundBackdrop,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .aboveDock()
                    .padding(start = 16.dp, end = 16.dp)
            ) { data ->
                GlassFloatingSurface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = data.visuals.message,
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        data.visuals.actionLabel?.let { label ->
                            GlassDialogAction(onClick = data::performAction) {
                                Text(label, color = PrimaryBlue, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
            AnimatedVisibility(
                visible = carMode,
                enter = fadeIn(tween(220)),
                exit = fadeOut(tween(160))
            ) {
                val carVehicles = remember(state.vehicles) {
                    state.vehicles.map { vehicle ->
                        CarVehicleChoice(
                            id = vehicle.id,
                            name = vehicle.name,
                            model = vehicle.model,
                            plate = vehicle.plate,
                            mileageKm = vehicle.currentMileage,
                            typeLine = listOf(
                                vehicle.category.label,
                                vehicle.energyType.label,
                                vehicle.transmissionType.label
                            ).joinToString(" · "),
                            // 与主 app `vehicleTypeIcon` 同一套判据：汽车 + 非燃油 = 电车图标。
                            kind = when {
                                vehicle.category == VehicleCategory.Car &&
                                    vehicle.energyType != EnergyType.Fuel -> CarVehicleKind.ElectricCar
                                vehicle.category == VehicleCategory.Car -> CarVehicleKind.Car
                                vehicle.category == VehicleCategory.Motorcycle -> CarVehicleKind.Motorcycle
                                vehicle.category == VehicleCategory.ElectricMotorcycle ->
                                    CarVehicleKind.ElectricMotorcycle
                                vehicle.category == VehicleCategory.ElectricBike -> CarVehicleKind.ElectricBike
                                vehicle.category == VehicleCategory.Bicycle -> CarVehicleKind.Bicycle
                                else -> CarVehicleKind.Car
                            }
                        )
                    }
                }
                CarModeOverlay(
                    settings = carModeSettings,
                    vehicles = carVehicles,
                    selectedVehicleId = state.selectedVehicle?.id,
                    onSelectVehicle = viewModel::selectVehicle,
                    onCommitTrip = viewModel::addCarModeTrip,
                    onExit = { carMode = false }
                )
            }
        }
        val pendingUpdate = updateState.availableUpdate
        val pendingNotice = notice
        when {
            pendingUpdate != null -> UpdateDialog(
                update = pendingUpdate,
                apkBytes = updatePackageBytes,
                apkProbing = updatePackageProbing,
                download = updateDownload,
                // 体积探测走服务端下发的安装包真实地址（老服务端没有该字段时回落为"未知"）
                onProbeSize = { viewModel.probeUpdatePackageSize(pendingUpdate.version, pendingUpdate.downloadUrl) },
                onDownload = viewModel::downloadUpdatePackage,
                onInstall = viewModel::installDownloadedUpdate,
                onDismiss = viewModel::dismissAvailableUpdate
            )
            updateState.showUpToDateDialog -> GlassDialog(
                onDismissRequest = viewModel::dismissUpToDateDialog,
                title = "已是最新版本",
                text = {
                    Text(
                        "当前版本 ${BuildConfig.VERSION_NAME} 已是官网发布的最新版本。",
                        color = TextSecondary
                    )
                },
                confirmButton = {
                    GlassDialogAction(onClick = viewModel::dismissUpToDateDialog) {
                        Text("知道了", color = PrimaryBlue, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {}
            )
            pendingNotice != null -> AnnouncementDialog(
                notice = pendingNotice,
                onDismiss = viewModel::dismissNotice
            )
        }
    }
    }
}

private enum class ToolEntry(val title: String, val description: String, val icon: ImageVector) {
    CarMode("车机模式", "把手机当作车机使用", Icons.Outlined.Dashboard),
    Statistics("数据统计", "查看车辆里程、能耗与费用趋势", Icons.Outlined.Analytics),
    FitRecords(".fit 文件记录", "运动轨迹文件管理与导出", Icons.Outlined.FolderOpen),
    Performance("车辆性能测试", "记录加速、制动与车辆性能数据", Icons.Outlined.Speed),
    Archive("人车证书", "人车证书收纳管理", Icons.Outlined.CreditCard)
}

@Composable
private fun ToolsScreen(
    state: LedgerUiState,
    onSelectVehicle: (String) -> Unit,
    onUpdateEnergy: (String, String, EnergyRecordFormInput) -> Unit,
    onUpdateMaintenance: (String, String, String, String, String, String, String) -> Unit,
    onUpdateSpecial: (String, String, String, String, String, String, String, String) -> Unit,
    onDeleteRecord: (String, RecentRecordType) -> Unit,
    onDeleteHistory: (String) -> Unit,
    onStartCarMode: () -> Unit
) {
    var selected by rememberSaveable { mutableStateOf<ToolEntry?>(null) }
    var debugStartCarMode by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (BuildConfig.DEBUG && ChekuDebugNav.takePage("CarMode")) debugStartCarMode = true
    }
    LaunchedEffect(debugStartCarMode) {
        if (debugStartCarMode) {
            debugStartCarMode = false
            onStartCarMode()
        }
    }
    val fitViewModel: FitViewModel = viewModel()
    val performanceViewModel: PerformanceViewModel = viewModel()
    BackHandler(enabled = selected != null) { selected = null }
    AnimatedContent(
        targetState = selected,
        transitionSpec = {
            hierarchyMotion(targetState != null)
        },
        label = "tool-hierarchy"
    ) { current ->
        if (current == ToolEntry.Performance) {
            PerformanceScreen(state, performanceViewModel) { selected = null }
        } else if (current == null) LazyPage("工具") {
            pageSection("hint") { Text("常用车辆数据工具", color = TextSecondary, fontSize = 13.sp) }
            items(ToolEntry.entries, key = { it.name }) { tool ->
                CardBlock(
                    onClick = { if (tool == ToolEntry.CarMode) onStartCarMode() else selected = tool },
                    contentPadding = 18.dp
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(RoundedCornerShape(15.dp))
                                .background(GlassTokens.accentWash),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(tool.icon, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(23.dp))
                            }
                        }
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(tool.title, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text(tool.description, color = TextTertiary, fontSize = 12.sp)
                        }
                        Text("›", color = TextTertiary, fontSize = 24.sp)
                    }
                }
            }
        } else Page(current.title) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (current == ToolEntry.Statistics) {
                    StatisticsScreen(state, onSelectVehicle, onUpdateEnergy, onUpdateMaintenance, onUpdateSpecial, onDeleteRecord, onDeleteHistory) { selected = null }
                } else if (current == ToolEntry.FitRecords) {
                    FitRecordsScreen(state, fitViewModel) { selected = null }
                } else if (current == ToolEntry.Archive) {
                    ArchiveScreen(state) { selected = null }
                } else {
                    BackButton { selected = null }
                    InfoCard(current.title, "${current.description}。功能入口已建立，后续数据将在这里展示。")
                }
            }
        }
    }
}

/** 全屏沉浸车机界面 */
@Composable
private fun CarModeOverlay(
    settings: CarModeSettings,
    vehicles: List<CarVehicleChoice>,
    selectedVehicleId: String?,
    onSelectVehicle: (String) -> Unit,
    onCommitTrip: (Double, String) -> Unit,
    onExit: () -> Unit
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val density = LocalDensity.current
    val fitViewModel: FitViewModel = viewModel()
    val fitActivities by fitViewModel.activities.collectAsStateWithLifecycle()

    val fitActiveSamples by fitViewModel.activeSamples.collectAsStateWithLifecycle()
    val carTrackPoints = remember(fitActiveSamples) {
        fitActiveSamples.map { CarTrackPoint(it.latitude, it.longitude) }
    }

    var orientation by remember { mutableStateOf(settings.orientation()) }
    var launchMode by remember { mutableStateOf(settings.dockLaunchMode()) }
    val appearance = remember { CarAppearanceState(context) }
    val autoFit = remember { settings.autoFitRecording() }

    var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            val now = System.currentTimeMillis()
            nowMs = now
            delay(1_000L - now % 1_000L)
        }
    }

    var startedAtMs by rememberSaveable { mutableStateOf(System.currentTimeMillis()) }
    val tripAcc = remember { TripAccumulator() }
    var tripMeters by rememberSaveable { mutableStateOf(0.0) }
    var speedKmh by remember { mutableStateOf<Double?>(null) }
    var hasFix by remember { mutableStateOf(false) }
    var fitAutoStarted by rememberSaveable { mutableStateOf(false) }

    DisposableEffect(activity) {
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    DisposableEffect(activity, orientation) {
        val target = when (orientation) {
            CarModeOrientation.Landscape -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            CarModeOrientation.Portrait -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            CarModeOrientation.Auto -> ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
        }
        activity?.requestedOrientation = target
        onDispose {
            val current = activity?.requestedOrientation
            if (
                current == target ||
                current == ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE ||
                current == ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            ) {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
    }

    DisposableEffect(activity) {
        val manager = context.getSystemService(LocationManager::class.java)
        var previousSpeed: Double? = null
        var zeroHeld = false
        var lastGpsElapsedMs: Long? = null
        val previousByProvider = HashMap<String, Location>()
        var previousUsed: Location? = null

        fun elapsedNanos(location: Location): Long =
            location.elapsedRealtimeNanos.takeIf { it > 0L } ?: SystemClock.elapsedRealtimeNanos()

        // 手机是否静止（放在桌上 / 车停稳）：室内 GPS 漂移时用它把车速压回 0。
        val motion = CarMotionDetector(context).also { it.start() }

        val listener = LocationListener { location ->
            val provider = location.provider.orEmpty()
            val isGps = provider == LocationManager.GPS_PROVIDER
            val atNanos = elapsedNanos(location)
            val atMs = atNanos / 1_000_000L
            if (isGps) lastGpsElapsedMs = atMs
            val previousSame = previousByProvider.put(provider, Location(location))
            if (!FitLocationPolicy.shouldUseFix(isGps, atMs, lastGpsElapsedMs)) return@LocationListener
            hasFix = true

            val accuracy = if (location.hasAccuracy()) location.accuracy else 0f
            val lat = location.latitude
            val lon = location.longitude

            val sameDt = previousSame?.let { (atNanos - elapsedNanos(it)) / 1e9 } ?: 0.0
            val sameSegment = previousSame?.let { haversineMeters(it.latitude, it.longitude, lat, lon) } ?: 0.0
            // 不带多普勒速度时用位移推算；位移落在定位精度以内只是漂移，推算结果为 0。
            val rawMps: Double? = when {
                location.hasSpeed() -> location.speed.toDouble()
                previousSame != null -> derivedSpeedMps(
                    sameSegment,
                    sameDt,
                    accuracy,
                    if (previousSame.hasAccuracy()) previousSame.accuracy else 0f
                )
                else -> null
            }
            val speedAccuracy = PerformanceService.speedAccuracyOrNull(location)
            val still = motion.isStill
            val stationary = rawMps != null && isLikelyStationary(rawMps, accuracy, speedAccuracy, still)
            if (rawMps != null) {
                if (!still && isSuspectZeroSpeed(rawMps, previousSpeed, zeroHeld)) {
                    zeroHeld = true
                } else if (stationary) {
                    zeroHeld = false
                    previousSpeed = 0.0
                    speedKmh = 0.0
                } else {
                    zeroHeld = false
                    val estimate = SpeedEstimator.update(
                        rawSpeedMps = rawMps,
                        previousSpeedMps = previousSpeed,
                        dtSeconds = sameDt,
                        speedAccuracyMps = speedAccuracy,
                        locationAccuracyM = accuracy
                    )
                    previousSpeed = estimate.speedMps
                    speedKmh = mpsToKmh(estimate.speedMps)
                }
            }

            val used = previousUsed
            val segment = used?.let { haversineMeters(it.latitude, it.longitude, lat, lon) } ?: 0.0
            val dtSeconds = used?.let { (atNanos - elapsedNanos(it)) / 1e9 } ?: 0.0
            // 静止的帧不累计里程，但仍更新上一个点，免得之后一次性补进来。
            if (!stationary && tripAcc.add(
                    timeSeconds = atNanos / 1e9,
                    lat = lat,
                    lon = lon,
                    segmentMeters = segment,
                    dtSeconds = dtSeconds,
                    accuracyMeters = accuracy
                )
            ) tripMeters = tripAcc.meters
            previousUsed = Location(location)
        }

        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (granted && manager != null) {
            // GPS 与 NETWORK 都注册：地库/隧道里 GPS 会断，网络定位还能给出粗略位置。
            runCatching {
                manager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER, 1_000L, 0f, listener, Looper.getMainLooper()
                )
            }
            runCatching {
                manager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER, 1_000L, 0f, listener, Looper.getMainLooper()
                )
            }
        }
        onDispose {
            runCatching { manager?.removeUpdates(listener) }
            motion.stop()
        }
    }

    LaunchedEffect(autoFit) {
        if (!autoFit) return@LaunchedEffect
        val started = runCatching {
            fitViewModel.start(
                name = "车机行程 " + SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date()),
                autoPause = true
            )
        }.onFailure {
            Toast.makeText(context, "无法开始轨迹记录：${it.message ?: "未知原因"}", Toast.LENGTH_LONG).show()
        }.isSuccess
        fitAutoStarted = started
    }

    val elapsedSeconds = (nowMs - startedAtMs) / 1000.0
    val averageKmh = tripAverageSpeedKmh(tripMeters, elapsedSeconds)
    val calendar = Calendar.getInstance().apply { timeInMillis = nowMs }
    val clock = formatClock(calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE))
    val dayStamp = calendar.get(Calendar.YEAR) * 1000 + calendar.get(Calendar.DAY_OF_YEAR)
    val carDate = remember(dayStamp) { SimpleDateFormat("M月d日 EEEE", Locale.CHINA).format(Date(nowMs)) }
    val fitRecording = fitActivities.any { it.status != FitActivityStatus.Completed }

    /** 结束本次车机会话 */
    val finishAndExit: () -> Unit = {
        if (fitAutoStarted) {
            runCatching { fitViewModel.finish() }
                .onFailure { Toast.makeText(context, "轨迹记录结束失败：${it.message ?: "未知原因"}", Toast.LENGTH_LONG).show() }
        }
        val tripKm = tripMeters / 1000.0
        if (tripKm >= CarModeSettings.MIN_TRIP_KM_FOR_RECORD) {
            onCommitTrip(
                tripKm,
                "车机行程 ${formatTripDuration(elapsedSeconds)} · 均速 " +
                    "${formatSpeedKmh(averageKmh)} km/h"
            )
        }
        onExit()
    }
    BackHandler { finishAndExit() }

    val overlayNeeded = launchMode == CarDockLaunchMode.Overlay || launchMode == CarDockLaunchMode.Auto

    var overlayGranted by remember { mutableStateOf(CarDockOverlayService.canDrawOverlays(context)) }

    var writeSettingsGranted by remember { mutableStateOf(CarRotationController.canWrite(context)) }

    DisposableEffect(activity) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                overlayGranted = CarDockOverlayService.canDrawOverlays(context)
                writeSettingsGranted = CarRotationController.canWrite(context)
            }
        }
        val owner = activity as? ComponentActivity
        owner?.lifecycle?.addObserver(observer)
        onDispose { owner?.lifecycle?.removeObserver(observer) }
    }

    var dockFloating by remember { mutableStateOf(overlayNeeded && overlayGranted) }
    DisposableEffect(overlayNeeded, overlayGranted) {
        dockFloating = overlayNeeded && overlayGranted && CarDockOverlayService.start(context)
        onDispose {
            CarDockOverlayService.stop(context)
            dockFloating = false
            // 清场，免得下次进车机页带着上一次的选中态与记录状态。
            CarModeDockBus.reset()
        }
    }

    LaunchedEffect(overlayNeeded, overlayGranted) {
        if (!overlayNeeded || !overlayGranted) return@LaunchedEffect
        while (true) {
            if (!CarDockOverlayService.isRunning && CarDockOverlayService.start(context)) {
                dockFloating = true
            }
            delay(5_000L)
        }
    }

    DisposableEffect(writeSettingsGranted) {
        if (writeSettingsGranted) CarRotationController.apply(context)
        onDispose { CarRotationController.restore(context) }
    }

    val activePaneId by CarModeDockBus.selected.collectAsStateWithLifecycle()

    val currentFinishAndExit by rememberUpdatedState(finishAndExit)
    LaunchedEffect(Unit) {
        CarModeDockBus.exitCarMode.collect { currentFinishAndExit() }
    }

    // 轨迹记录状态推给悬浮 dock（点亮它上面「屏显」那抹绿）。
    LaunchedEffect(fitRecording) {
        CarModeDockBus.fitRecording.value = fitRecording
    }

    val configuration = LocalConfiguration.current
    val landscapeNow = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    var inMultiWindow by remember { mutableStateOf(activity?.inMultiWindowCompat() == true) }
    LaunchedEffect(
        configuration.screenWidthDp,
        configuration.screenHeightDp,
        configuration.orientation
    ) {
        inMultiWindow = activity?.inMultiWindowCompat() == true
    }
    LaunchedEffect(inMultiWindow) { CarModeDockBus.inMultiWindow.value = inMultiWindow }

    var soloAxis by rememberSaveable { mutableStateOf(Configuration.ORIENTATION_UNDEFINED) }
    LaunchedEffect(inMultiWindow, configuration.orientation) {
        // 只在"独屏"时记录：分屏里这个值可能已被目标 App 拽走，不可信。
        if (!inMultiWindow) soloAxis = configuration.orientation
    }
    LaunchedEffect(inMultiWindow) {
        if (inMultiWindow || orientation != CarModeOrientation.Auto) return@LaunchedEffect
        val axis = soloAxis
        val act = activity
        if (axis == Configuration.ORIENTATION_UNDEFINED || act == null) return@LaunchedEffect
        val pinned = if (axis == Configuration.ORIENTATION_LANDSCAPE) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        }
        if (act.requestedOrientation == pinned) return@LaunchedEffect
        act.requestedOrientation = pinned
        delay(600L)
        if (act.requestedOrientation == pinned) {
            act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
        }
    }

    LaunchedEffect(
        configuration.screenWidthDp,
        configuration.screenHeightDp,
        configuration.orientation
    ) {
        val metrics = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            activity?.windowManager?.currentWindowMetrics?.bounds
        } else {
            null
        }
        val dm = context.resources.displayMetrics
        CarModeDockBus.carWindowBounds.value = if (metrics != null && metrics.width() > 0) {
            CarWindowBounds(metrics.left, metrics.top, metrics.width(), metrics.height())
        } else {
            // 拿不到就退回整块屏（与浮层自己的兜底一致），绝不推 (0,0,0,0)。
            CarWindowBounds(0, 0, dm.widthPixels, dm.heightPixels)
        }
    }

    // 分屏下 dock 收窄一档：半屏只有约 426dp，常态的 116dp 要吃掉 27%，主区
    // 会被压到 262dp。88dp 只占 21%，且仍装得下 26dp 图标 + 「设置」这种 2 字标签。
    val dockWidth = if (inMultiWindow) CarDockCompactWidth else CarDockWidth

    val dockFreeBounds = remember(
        configuration.screenWidthDp, configuration.screenHeightDp, landscapeNow, dockWidth
    ) {
        with(density) {
            val width = configuration.screenWidthDp.dp.roundToPx()
            val height = configuration.screenHeightDp.dp.roundToPx()
            if (landscapeNow) {
                LaunchBounds(dockWidth.roundToPx(), 0, width, height)
            } else {
                LaunchBounds(0, 0, width, height - CarDockHeight.roundToPx())
            }
        }
    }

    var splitCheckToken by remember { mutableStateOf(0) }
    SplitLaunchSelfCheck(splitCheckToken)

    var appCatalog by remember { mutableStateOf<CarAppCatalog?>(null) }
    LaunchedEffect(Unit) {
        val sizePx = with(density) { 56.dp.roundToPx() }
        appCatalog = withContext(Dispatchers.IO) { buildCarAppCatalog(context, sizePx) }
    }

    var desktopPackages by remember { mutableStateOf(settings.carDesktopPackages()) }
    val onSaveDesktop: (List<String>) -> Unit = { packages ->
        settings.setCarDesktopPackages(packages)
        desktopPackages = packages
    }

    /** 启动一个目标 */
    val launchTarget: (CarLaunchTarget) -> Unit = { target ->
        val mode = decideDockLaunchMode(
            setting = launchMode,
            deviceLandscape = landscapeNow,
            overlayAvailable = dockFloating
        )
        val bounds = if (mode == CarDockLaunchMode.Overlay && !dockFloating) dockFreeBounds else null
        if (launchCarTarget(context, target, mode, bounds)) {
            if (mode == CarDockLaunchMode.Split) splitCheckToken++
        }
    }

    val handleLaunchDockItem: (CarDockItem) -> Unit = { item ->
        val next = nextDockLaunchStage(
            CarModeDockBus.launchStageItem.value,
            CarModeDockBus.launchStage.value,
            item
        )
        CarModeDockBus.launchStageItem.value = item
        CarModeDockBus.launchStage.value = next
        android.util.Log.i(
            "CarModeDockCycle",
            "点击 $item：上一格=${CarModeDockBus.launchStageItem.value} 本次阶段=$next " +
                "inSplit=${context.findActivity()?.inMultiWindowCompat()}"
        )
        if (item == CarDockItem.Music) CarModeDockBus.selected.value = CarPane.Music.id

        when (next) {
            CarDockLaunchStage.Split -> {
                val target = resolveCarLaunchTarget(context, item)
                if (target != null) {
                    launchTarget(target)
                } else if (item == CarDockItem.Music) {
                } else {
                    Toast.makeText(context, "未找到可用的导航应用", Toast.LENGTH_SHORT).show()
                }
            }

            CarDockLaunchStage.Fullscreen -> {
                // 独占全屏：让 App 自己请求退出分屏（车机页铺满整屏，左半屏的自绘面板不变）。
                // 细节与两级降级见 [requestCarModeFullscreen]。
                requestCarModeFullscreen(context)
            }

            CarDockLaunchStage.None -> Unit
        }
    }

    /** dock 点击的三种去向 */
    val onDockSelect: (CarDockItem) -> Unit = { item ->
        when (item.kind) {
            CarDockKind.Exit -> finishAndExit()
            CarDockKind.Pane -> {
                val pane = item.pane
                CarModeDockBus.selected.value =
                    if (pane != null && activePaneId == pane.id) null else pane?.id
            }

            CarDockKind.Launch -> handleLaunchDockItem(item)
        }
    }

    // 悬浮 dock 转交过来、需要由本页启动的条目（分屏启动）。
    // `rememberUpdatedState` 与上面 `exitCarMode` 那条同理：收集协程只启动一次，
    // 而 `launchTarget` 每帧都是新的闭包（它读的都是 `remember` 出来的状态，但外层 lambda 实例会换）。
    val currentLaunchTarget by rememberUpdatedState(launchTarget)
    LaunchedEffect(Unit) {
        CarModeDockBus.launchRequest.collect { currentLaunchTarget(it) }
    }

    // 悬浮 dock 转交过来的点击（分屏模式下的音乐 / 导航，由本页执行三态切换）。
    // `rememberUpdatedState` 同上一条：收集协程只启动一次，而 `handleLaunchDockItem` 每帧是新闭包。
    val currentLaunchItem by rememberUpdatedState(handleLaunchDockItem)
    LaunchedEffect(Unit) {
        CarModeDockBus.itemClickRequest.collect { currentLaunchItem(it) }
    }

    /** 桌面里的一张应用瓦片 */
    val onLaunchApp: (CarAppTile.App) -> Unit = { tile ->
        val target = resolvePackageTarget(context, tile.packageName, tile.label)
        if (target == null) {
            Toast.makeText(context, "无法打开「${tile.label}」", Toast.LENGTH_SHORT).show()
        } else {
            launchTarget(target)
        }
    }

    LaunchedEffect(appearance.blur) {
        if (appearance.blur == settings.backgroundBlur()) return@LaunchedEffect
        delay(400L)
        appearance.persistBlur()
    }

    LaunchedEffect(appearance.artworkBlur) {
        if (appearance.artworkBlur == settings.artworkBlur()) return@LaunchedEffect
        delay(400L)
        appearance.persistArtworkBlur()
    }

    val appDarkLevel = LocalAppDarkLevel.current
    val carDark = when (appearance.themeMode) {
        CarThemeMode.Light -> false
        CarThemeMode.Dark -> true
        CarThemeMode.System -> isSystemInDarkTheme()
    }
    val carDarkLevel by animateFloatAsState(
        targetValue = if (carDark) 1f else 0f,
        animationSpec = tween(
            durationMillis = 560,
            easing = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)
        ),
        label = "car-theme-dark-level"
    )
    // 与 CheKuTheme 同一写法：直接写，而不是塞进 SideEffect（原因见那里的注释）。
    AppDarkLevel = carDarkLevel
    DisposableEffect(Unit) {
        onDispose { AppDarkLevel = appDarkLevel }
    }

    val overlayBackdrop = rememberLayerBackdrop()
    val appsStatusText =
        if (inMultiWindow) "已分屏：目标 App 在另一侧，由系统排版，互不遮挡" else null
    CompositionLocalProvider(LocalAppDarkLevel provides carDarkLevel) {
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().layerBackdrop(overlayBackdrop)) {
            Box(Modifier.fillMaxSize().meshBackground())
            when (appearance.mode) {
                CarBackground.Static -> Unit
                CarBackground.Mesh -> AnimatedMeshBackground()
                CarBackground.Custom -> appearance.customUri
                    ?.takeIf { it.isNotBlank() }
                    ?.let { uri -> CustomBackground(uri) { appearance.blur } }
            }
        }
        CompositionLocalProvider(LocalGlassBackdrops provides GlassBackdrops(background = overlayBackdrop)) {
            val dockInsetStart = if (dockFloating && landscapeNow) dockWidth else 0.dp
            val dockInsetBottom = if (dockFloating && !landscapeNow) CarDockHeight else 0.dp
            val paneGap = if (inMultiWindow) 14.dp else 24.dp
            val fullBleedPane = CarPane.fromId(activePaneId) == CarPane.Music
            val panePad = if (fullBleedPane) 0.dp else paneGap
            val paneSafe =
                if (fullBleedPane) Modifier
                else Modifier.windowInsetsPadding(WindowInsets.displayCutout)

            val mainPane: @Composable (Modifier) -> Unit = { paneModifier ->
                CarMainPane(
                    activePaneId = activePaneId,
                    landscape = landscapeNow,
                    compact = inMultiWindow,
                    speedText = formatSpeedKmh(speedKmh),
                    hasFix = hasFix,
                    tripMeters = tripMeters,
                    elapsedSeconds = elapsedSeconds,
                    averageKmh = averageKmh,
                    fitRecording = fitRecording,
                    trackPoints = carTrackPoints,
                    appCatalog = appCatalog,
                    appsStatusText = appsStatusText,
                    appearance = appearance,
                    artworkBlur = appearance.artworkBlur.dp,
                    orientation = orientation,
                    onOrientationChange = {
                        // 先落盘再改状态：转屏会让 Activity 重建，重建后从 prefs 读回新值。
                        settings.setOrientation(it)
                        orientation = it
                    },
                    launchMode = launchMode,
                    onLaunchModeChange = { settings.setDockLaunchMode(it); launchMode = it },
                    vehicles = vehicles,
                    selectedVehicleId = selectedVehicleId,
                    onSelectVehicle = onSelectVehicle,
                    onOpenPane = { CarModeDockBus.selected.value = it.id },
                    onLaunchApp = onLaunchApp,
                    desktopPackages = desktopPackages,
                    onSaveDesktop = onSaveDesktop,
                    modifier = paneModifier
                )
            }

            if (landscapeNow) {
                Row(Modifier.fillMaxSize()) {
                    if (!dockFloating) {
                        CarDock(
                            vertical = true,
                            compact = inMultiWindow,
                            activePaneId = activePaneId,
                            fitRecording = fitRecording,
                            onSelect = onDockSelect,
                            modifier = Modifier.fillMaxHeight().width(dockWidth)
                        )
                    }
                    mainPane(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .then(paneSafe)
                            .padding(
                                start = panePad + dockInsetStart,
                                top = panePad,
                                end = panePad,
                                bottom = panePad
                            )
                    )
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    mainPane(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .then(paneSafe)
                            .padding(
                                start = panePad,
                                top = panePad,
                                end = panePad,
                                bottom = panePad + dockInsetBottom
                            )
                    )
                    if (!dockFloating) {
                        CarDock(
                            vertical = false,
                            compact = inMultiWindow,
                            activePaneId = activePaneId,
                            fitRecording = fitRecording,
                            onSelect = onDockSelect,
                            modifier = Modifier.fillMaxWidth().height(CarDockHeight)
                        )
                    }
                }
            }
            if (!overlayGranted || !writeSettingsGranted) {
                Row(
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (!overlayGranted) {
                        PermissionHint(
                            icon = Icons.Outlined.Layers,
                            title = "开启悬浮 dock",
                            caption = "左侧 dock 常驻在其他应用之上",
                            onRequest = {
                                runCatching {
                                    context.startActivity(CarDockOverlayService.overlayPermissionIntent(context))
                                }
                            }
                        )
                    }
                    if (!writeSettingsGranted) {
                        PermissionHint(
                            icon = Icons.Outlined.ScreenRotation,
                            title = "开启自动旋转权限",
                            caption = "点开的应用才能跟着横过来",
                            onRequest = {
                                runCatching {
                                    context.startActivity(CarRotationController.settingsIntent(context))
                                }
                            }
                        )
                    }
                }
            }
        }
    }
    }
}

private val GlassSquareCorner = RoundedCornerShape(0.dp)

@Composable
private fun PermissionHint(
    icon: ImageVector,
    title: String,
    caption: String,
    onRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    GlassPanel(
        level = GlassLevel.S1_Control,
        backdrop = rememberLayerAwareBackdrop(),
        modifier = modifier,
        shape = GlassTokens.shapeControl,
        onClick = onRequest
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = PrimaryBlue,
                modifier = Modifier.size(20.dp)
            )
            Column {
                Text(
                    title,
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                Text(
                    caption,
                    color = TextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1
                )
            }
        }
    }
}

/** 车机 dock */
@Composable
private fun CarDock(
    vertical: Boolean,
    compact: Boolean,
    activePaneId: String?,
    fitRecording: Boolean,
    onSelect: (CarDockItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val statusPalette = rememberHubStatusPalette()
    GlassPanel(
        level = GlassLevel.L4_Nav,
        backdrop = rememberLayerAwareBackdrop(),
        modifier = modifier,
        shape = GlassSquareCorner,
        onClick = null
    ) {
        val items = CarDockItem.entries
        if (vertical) {
            Column(
                modifier = Modifier.fillMaxSize().padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                CarDockStatusPanel(
                    vertical = true,
                    compact = compact,
                    palette = statusPalette,
                    modifier = Modifier.fillMaxWidth()
                )
                items.forEach { item ->
                    CarDockButton(
                        item = item,
                        selected = item.isActiveFor(activePaneId),
                        recording = fitRecording,
                        onClick = { onSelect(item) },
                        modifier = Modifier.weight(1f).fillMaxWidth()
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                CarDockStatusPanel(
                    vertical = false,
                    compact = compact,
                    palette = statusPalette,
                    modifier = Modifier.fillMaxHeight()
                )
                items.forEach { item ->
                    CarDockButton(
                        item = item,
                        selected = item.isActiveFor(activePaneId),
                        recording = fitRecording,
                        onClick = { onSelect(item) },
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                }
            }
        }
    }
}

/** dock 里的一条 */
@Composable
private fun CarDockButton(
    item: CarDockItem,
    selected: Boolean,
    recording: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tint = when {
        item.kind == CarDockKind.Exit -> Error
        item == CarDockItem.Display && recording -> Success
        selected -> PrimaryBlue
        else -> TextSecondary
    }
    val fill = if (selected) PrimaryBlue.copy(alpha = 0.16f) else Color.Transparent
    // 条目与 dock 同形（直角）：dock 是直角矩形，里面嵌圆角方块就成了两套形状语言。
    // 这里只是 `Modifier.clip`（不过 lens），所以用 `RectangleShape` 也安全；
    // 仍然沿用 [GlassSquareCorner] 是为了"直角"这件事在本文件里只有一个写法。
    BoxWithConstraints(
        modifier = modifier
            .clip(GlassSquareCorner)
            .clipToBounds()
            .background(fill)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        val iconSize = (minOf(maxWidth, maxHeight) * 0.62f).coerceIn(20.dp, 46.dp)
        Icon(item.icon, contentDescription = item.label, tint = tint, modifier = Modifier.size(iconSize))
    }
}

/** 车机主区 */
@Composable
private fun CarMainPane(
    activePaneId: String?,
    landscape: Boolean,
    compact: Boolean,
    speedText: String,
    hasFix: Boolean,
    tripMeters: Double,
    elapsedSeconds: Double,
    averageKmh: Double,
    fitRecording: Boolean,
    /** 本次 FIT 轨迹的点列 */
    trackPoints: List<CarTrackPoint>,
    appCatalog: CarAppCatalog?,
    appsStatusText: String?,
    appearance: CarAppearanceState,
    artworkBlur: Dp,
    orientation: CarModeOrientation,
    onOrientationChange: (CarModeOrientation) -> Unit,
    launchMode: CarDockLaunchMode,
    onLaunchModeChange: (CarDockLaunchMode) -> Unit,
    vehicles: List<CarVehicleChoice>,
    selectedVehicleId: String?,
    onSelectVehicle: (String) -> Unit,
    onOpenPane: (CarPane) -> Unit,
    onLaunchApp: (CarAppTile.App) -> Unit,
    desktopPackages: List<String>,
    onSaveDesktop: (List<String>) -> Unit,
    modifier: Modifier = Modifier
) {
    CompositionLocalProvider(
        LocalTextSelectionColors provides TextSelectionColors(
            handleColor = Color.Transparent,
            backgroundColor = Color.Transparent
        )
    ) {
    Crossfade(
        targetState = CarPane.fromId(activePaneId) ?: CarModeDefaultPane,
        modifier = modifier,
        label = "carPane"
    ) { pane ->
        when (pane) {
            CarPane.Display -> CarDisplayPane(
                landscape = landscape,
                compact = compact,
                speedText = speedText,
                hasFix = hasFix,
                tripMeters = tripMeters,
                elapsedSeconds = elapsedSeconds,
                averageKmh = averageKmh,
                fitRecording = fitRecording,
                trackPoints = trackPoints
            )

            CarPane.Apps -> CarAppsPane(
                compact = compact,
                catalog = appCatalog,
                desktopPackages = desktopPackages,
                statusText = appsStatusText,
                onOpenPane = onOpenPane,
                onLaunchApp = onLaunchApp,
                onSaveDesktop = onSaveDesktop
            )

            CarPane.Settings -> CarSettingsPane(
                compact = compact,
                appearance = appearance,
                orientation = orientation,
                onOrientationChange = onOrientationChange,
                launchMode = launchMode,
                onLaunchModeChange = onLaunchModeChange,
                vehicles = vehicles,
                selectedVehicleId = selectedVehicleId,
                onSelectVehicle = onSelectVehicle,
                navChoices = appCatalog?.let { carNavChoices(it) } ?: emptyList(),
                musicChoices = appCatalog?.let { carMusicChoices(it) } ?: emptyList()
            )

            CarPane.Sms -> CarSmsPane(compact)
            CarPane.Phone -> CarCallLogPane(compact)
            CarPane.Contacts -> CarContactsPane(compact)
            CarPane.Music -> CarMusicPane(landscape, compact, artworkBlur)
        }
    }
    }
}

@Composable
private fun SplitLaunchSelfCheck(token: Int) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    LaunchedEffect(token) {
        if (token == 0) return@LaunchedEffect
        delay(1_200L)
        if (activity?.inMultiWindowCompat() != true) {
            Toast.makeText(
                context,
                "没能分屏：这个应用声明了「不可调整大小」（微信、部分聊天类 App 都是）。" +
                    "小米/红米可在「设置 → 更多设置 → 开发者选项」里打开" +
                    "「强制将活动设置为可调整大小」，重启后即可；" +
                    "也可以在最近任务里手动分屏，或改用「悬浮 dock」。",
                Toast.LENGTH_LONG
            ).show()
        }
    }
}

@Composable
private fun ArchiveScreen(state: LedgerUiState, onBack: () -> Unit) {
    val context = LocalContext.current
    val repository = remember(context) { CertificateRepository(context.applicationContext) }
    var revision by rememberSaveable { mutableStateOf(0) }
    var selectedSection by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingKey by remember { mutableStateOf<String?>(null) }
    var pendingExportKey by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var expandedPreviewKey by rememberSaveable { mutableStateOf<String?>(null) }
    val importDocument = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val key = pendingKey ?: return@rememberLauncherForActivityResult
        pendingKey = null
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            repository.save(key, uri)
            expandedPreviewKey = key
            revision++
        } catch (exception: Exception) {
            error = exception.message ?: "无法导入证书文件"
        }
    }
    val exportDocument = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val key = pendingExportKey ?: return@rememberLauncherForActivityResult
        pendingExportKey = null
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            repository.export(key, uri)
        } catch (exception: Exception) {
            error = exception.message ?: "无法导出证书文件"
        }
    }

    fun choose(key: String) {
        pendingKey = key
        importDocument.launch(arrayOf("image/jpeg", "image/png", "application/pdf", "application/msword", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
    }
    fun export(key: String) {
        val name = repository.displayName(key) ?: "证书文件"
        pendingExportKey = key
        exportDocument.launch(name)
    }
    fun viewExternal(key: String) {
        val uri = repository.viewerUriFor(key) ?: return
        runCatching {
            context.startActivity(Intent.createChooser(
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, repository.mimeTypeFor(key) ?: "*/*")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                "查看证书"
            ))
        }.onFailure { error = it.message ?: "当前设备无法查看此文件" }
    }

    BackButton(onBack)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("驾驶员证书", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        error?.let { message ->
            InfoCard("证书操作失败", message)
        }
        AnimatedContent(
            targetState = selectedSection,
            transitionSpec = { hierarchyMotion(enteringDetail = targetState != null) },
            label = "archive-hierarchy"
        ) { visibleSection ->
        if (visibleSection == null) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CertificateSectionEntry("身份证", "正反面证书照片", Icons.Outlined.CreditCard) { selectedSection = "id" }
                CertificateSectionEntry("驾驶证", "正反面证书照片", Icons.Outlined.CreditCard) { selectedSection = "driver" }
                SectionTitle("车辆证书")
                if (state.vehicles.isEmpty()) {
                    InfoCard("暂无车辆", "添加车辆后可保存行驶证、保单、绿本和 VIN。")
                } else state.vehicles.forEach { vehicle ->
                    CertificateSectionEntry(
                        vehicle.name,
                        vehicle.plate.ifBlank { "车辆证书" },
                        Icons.Outlined.DirectionsCar
                    ) { selectedSection = "vehicle:${vehicle.id}" }
                }
            }
        } else {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val section = visibleSection
                val vehicle = section.removePrefix("vehicle:").takeIf { section.startsWith("vehicle:") }
                    ?.let { id -> state.vehicles.firstOrNull { it.id == id } }
                val title = when (section) {
                    "id" -> "身份证"
                    "driver" -> "驾驶证"
                    else -> vehicle?.name ?: "车辆证书"
                }
                Text(title, color = TextPrimary, fontWeight = FontWeight.Black, fontSize = 18.sp)
                if (vehicle != null) {
                    var vin by remember(vehicle.id, revision) { mutableStateOf(context.getSharedPreferences(CertificateRepository.PREFERENCES_NAME, Context.MODE_PRIVATE).getString(CertificateRepository.vehicleVinKey(vehicle.id), "").orEmpty()) }
                    CardBlock(contentPadding = 14.dp) {
                        Text("车辆识别代号 (VIN)", color = TextSecondary, fontSize = 13.sp)
                        AppTextField("VIN", vin, { value ->
                            vin = value.uppercase(Locale.ROOT)
                            context.getSharedPreferences(CertificateRepository.PREFERENCES_NAME, Context.MODE_PRIVATE)
                                .edit().putString(CertificateRepository.vehicleVinKey(vehicle.id), vin).apply()
                        }, "", KeyboardType.Ascii)
                    }
                }
                val fields = when (section) {
                    "id" -> listOf("正面" to CertificateRepository.ID_FRONT, "反面" to CertificateRepository.ID_BACK)
                    "driver" -> listOf("正面" to CertificateRepository.LICENSE_FRONT, "反面" to CertificateRepository.LICENSE_BACK)
                    else -> vehicle?.let {
                        listOf(
                            "行驶证正面" to CertificateRepository.vehicleCertificateKey(it.id, "driving_front"),
                            "行驶证反面" to CertificateRepository.vehicleCertificateKey(it.id, "driving_back"),
                            "交强险保单" to CertificateRepository.vehicleCertificateKey(it.id, "compulsory"),
                            "商业险保单" to CertificateRepository.vehicleCertificateKey(it.id, "commercial"),
                            "绿本" to CertificateRepository.vehicleCertificateKey(it.id, "registration")
                        )
                    }.orEmpty()
                }
                fields.forEach { (label, key) ->
                    ArchiveDocumentSlot(
                        label = label,
                        uri = repository.uriFor(key),
                        fileName = repository.displayName(key),
                        mimeType = repository.mimeTypeFor(key),
                        onChoose = { choose(key) },
                        expanded = expandedPreviewKey == key,
                        onTogglePreview = { expandedPreviewKey = if (expandedPreviewKey == key) null else key },
                        onViewExternal = { viewExternal(key) },
                        onExport = { export(key) }
                    )
                }
            }
        }
        }
    }
}

@Composable
private fun CertificateSectionEntry(title: String, subtitle: String, icon: ImageVector, onOpen: () -> Unit) {
    CardBlock(modifier = Modifier.fillMaxWidth(), onClick = onOpen, contentPadding = 15.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(GlassTokens.accentWash),
                contentAlignment = Alignment.Center
            ) {
                Box(contentAlignment = Alignment.Center) { Icon(icon, null, tint = PrimaryBlue) }
            }
            Column(Modifier.weight(1f)) {
                Text(title, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = TextTertiary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text("›", color = TextTertiary, fontSize = 24.sp)
        }
    }
}

@Composable
private fun ArchiveDocumentSlot(
    label: String,
    uri: Uri?,
    fileName: String?,
    mimeType: String?,
    onChoose: () -> Unit,
    expanded: Boolean,
    onTogglePreview: () -> Unit,
    onViewExternal: () -> Unit,
    onExport: () -> Unit
) {
    CardBlock(contentPadding = 14.dp) {
        AnimatedContent(
            targetState = expanded && uri != null && mimeType?.startsWith("image/") == true,
            transitionSpec = { (fadeIn(tween(260, easing = FastOutSlowInEasing)) + expandVertically(tween(420, easing = FastOutSlowInEasing))) togetherWith (fadeOut(tween(160)) + shrinkVertically(tween(260, easing = FastOutSlowInEasing))) },
            label = "archive-preview-$label"
        ) { showLarge ->
            if (showLarge) {
                ArchiveDocumentPreview(uri, mimeType, label, Modifier.fillMaxWidth().height(240.dp).clip(GlassTokens.shapeControl).glassClickable(onClick = onTogglePreview))
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ArchiveDocumentPreview(uri, mimeType, label, Modifier.size(56.dp).glassClickable(onClick = onTogglePreview))
                    Column(Modifier.weight(1f)) {
                        Text(label, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                        Text(fileName ?: "尚未添加", color = TextTertiary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GlassDialogAction(
                onClick = onChoose,
                modifier = Modifier.weight(1f).height(38.dp)) { Text(if (fileName == null) "添加" else "更换", maxLines = 1) }
            if (fileName != null) {
                GlassDialogAction(onClick = if (mimeType?.startsWith("image/") == true) onTogglePreview else onViewExternal, modifier = Modifier.weight(1f).height(38.dp)) {
                    Text(if (mimeType?.startsWith("image/") == true && expanded) "收起" else "查看", maxLines = 1)
                }
                GlassDialogAction(onClick = onExport, modifier = Modifier.weight(1f).height(38.dp)) { Text("导出", maxLines = 1) }
            }
        }
    }
}

@Composable
private fun ArchiveDocumentPreview(uri: Uri?, mimeType: String?, label: String, modifier: Modifier = Modifier.size(56.dp)) {
    val context = LocalContext.current
    val bitmap = remember(uri?.toString(), mimeType) {
        if (uri != null && mimeType?.startsWith("image/") == true) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    BitmapFactory.decodeStream(input)
                }
            }.getOrNull()
        } else null
    }
    Box(
        modifier
            .clip(RoundedCornerShape(11.dp))
            .background(GlassTokens.accentWash),
        contentAlignment = Alignment.Center
    ) {
        when {
            bitmap != null -> Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "$label 预览",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            mimeType == "application/pdf" -> Box(contentAlignment = Alignment.Center) {
                Text("PDF", color = Error, fontWeight = FontWeight.Black, fontSize = 12.sp)
            }
            uri != null -> Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.FolderOpen, null, tint = PrimaryBlue, modifier = Modifier.size(22.dp))
            }
            else -> Box(contentAlignment = Alignment.Center) {
                Icon(archiveDocumentIcon(label), null, tint = PrimaryBlue, modifier = Modifier.size(22.dp))
            }
        }
    }
}

private fun archiveDocumentIcon(label: String): ImageVector = when {
    label.contains("身份证") -> Icons.Outlined.Badge
    label.contains("驾驶证") || label.contains("行驶证") -> Icons.Outlined.CreditCard
    label.contains("险") || label.contains("保单") -> Icons.Outlined.Shield
    label.contains("绿本") -> Icons.Outlined.MenuBook
    else -> Icons.Outlined.CreditCard
}

private enum class StatisticsPeriod(val label: String) {
    Month("本月"), ThreeMonths("近3月"), Year("近12月"), All("全部")
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun PerformanceScreen(state:LedgerUiState, vm:PerformanceViewModel, onBack:()->Unit){
    Page("性能测试") {
        PerformanceScreenBody(state, vm, onBack)
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun PerformanceScreenBody(state:LedgerUiState, vm:PerformanceViewModel, onBack:()->Unit){
    val sessions by vm.sessions.collectAsStateWithLifecycle(); val selected by vm.selected.collectAsStateWithLifecycle(); val samples by vm.samples.collectAsStateWithLifecycle(); val jerks by vm.jerks.collectAsStateWithLifecycle(); val splits by vm.splits.collectAsStateWithLifecycle(); val activeSamples by vm.activeSamples.collectAsStateWithLifecycle()
    val performanceMessage by vm.message.collectAsStateWithLifecycle()
    val performanceSnackbar = remember { SnackbarHostState() }
    LaunchedEffect(performanceMessage) {
        performanceMessage?.let {
            performanceSnackbar.showSnackbar(it)
            vm.clearMessage()
        }
    }
    var showStart by remember{mutableStateOf(BuildConfig.DEBUG && ChekuDebugNav.takeScreen("PerformanceSetup"))}; var pending by remember{mutableStateOf<Triple<PerformanceTestType,String?,String>?>(null)}; var deleting by remember{mutableStateOf<com.cheku.app.data.PerformanceSessionEntity?>(null)}
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){ grants->
        pending?.let{
            if(grants[Manifest.permission.ACCESS_FINE_LOCATION]==true) vm.start(it.first,it.second,it.third)
            else vm.message.value="未授予精确定位权限，无法开始性能测试"
        }
        pending=null
    }
    BackHandler {
        when {
            selected != null -> vm.select(null)
            showStart -> showStart = false
            else -> onBack()
        }
    }
    val hierarchy = when {
        selected != null -> "detail"
        showStart -> "setup"
        else -> "list"
    }
    AnimatedContent(
        targetState = hierarchy,
        transitionSpec = {
            hierarchyMotion(targetState != "list")
        },
        label = "performance-hierarchy"
    ) { screen ->
        when (screen) {
            "detail" -> Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                selected?.let { session ->
                    BackButton { vm.select(null) }
                    PerformanceDetail(session, state.vehicles.firstOrNull { it.id == session.vehicleId }, samples, jerks, splits)
                }
            }
            "setup" -> PerformanceSetupScreen(
                state = state,
                onBack = { showStart = false },
                onStart = { vehicleId, name ->
                    showStart = false
                    pending = Triple(PerformanceTestType.Combined, vehicleId, name)
                    val permissions = buildList {
                        add(Manifest.permission.ACCESS_FINE_LOCATION)
                        add(Manifest.permission.RECORD_AUDIO)
                        if (android.os.Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
                    }.toTypedArray()
                    permission.launch(permissions)
                }
            )
            else -> Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                BackButton(onBack)
                val active=sessions.firstOrNull{it.status!=PerformanceStatus.Completed&&it.status!=PerformanceStatus.Interrupted}
                if(active!=null){ val latest=activeSamples.lastOrNull(); CardBlock(accentWash = PrimaryBlue.copy(alpha = .09f)){
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(active.testType.performanceLabel(),fontWeight=FontWeight.Black,fontSize=18.sp)
                            Text(active.status.performanceStatusLabel(),color=PrimaryBlue,fontWeight=FontWeight.Bold)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("${latest?.fusedSpeedMps?.times(3.6)?.let(::formatDecimal)?:"--"}",fontWeight=FontWeight.Black,fontSize=32.sp,color=PrimaryBlue)
                            Text("km/h",color=TextSecondary,fontSize=12.sp)
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StatCard("纵向加速度",latest?.longitudinalAccelerationMps2?.toDouble()?.let(::formatDecimal)?:"--","m/s²",Modifier.weight(1f),Warning)
                        StatCard("相对噪音",latest?.noiseDbA?.toDouble()?.let(::formatDecimal)?:"--","dB rel.",Modifier.weight(1f),Success)
                    }
                    Text("请将手机固定在稳定支架上；驾驶员不得操作手机。",color=Error,fontSize=11.sp)
                    GlassActionButton("结束保存",Icons.Outlined.Save,PrimaryBlue){vm.stop()}
                }} else GlassActionButton("开始性能测试",Icons.Outlined.Speed,PrimaryBlue){showStart=true}
                val valid=sessions.filter{it.status==PerformanceStatus.Completed&&it.quality==PerformanceQuality.Valid&&it.resultSeconds!=null}
                if(valid.isNotEmpty()){SectionTitle("最佳成绩");valid.filter{it.resultSeconds != null}.groupBy{it.testType}.forEach{(type,list)-> val best=list.minBy{it.resultSeconds!!};InfoCard(type.performanceLabel(),"0–100 km/h · ${formatDecimal(best.resultSeconds!!)} 秒 · ${formatDecimal((best.resultDistanceMeters?:0.0))} 米")}}
                SectionTitle("最近测试")
                sessions.filter{it.status==PerformanceStatus.Completed||it.status==PerformanceStatus.Interrupted}.forEach{s->CardBlock(onClick={vm.select(s.id)},onLongClick={deleting=s}){Row(verticalAlignment=Alignment.CenterVertically){Icon(Icons.Outlined.Speed,null,tint=if(s.quality==PerformanceQuality.Valid)PrimaryBlue else Warning);Spacer(Modifier.width(10.dp));Column(Modifier.weight(1f)){Text(s.testType.performanceLabel(),fontWeight=FontWeight.Bold);Text(SimpleDateFormat("yyyy/MM/dd HH:mm",Locale.CHINA).format(Date(s.startedAt)),color=TextTertiary,fontSize=11.sp)};Text(s.resultSeconds?.let{"${formatDecimal(it)} s"}?:if(s.status==PerformanceStatus.Interrupted)"已中断" else "已完成",color=PrimaryBlue,fontWeight=FontWeight.Bold)};if(s.quality!=PerformanceQuality.Valid)Text(s.invalidReason.ifBlank{"低精度数据"},color=Warning,fontSize=11.sp)}}
                if(sessions.none{it.status==PerformanceStatus.Completed||it.status==PerformanceStatus.Interrupted})InfoCard("暂无测试记录","完成一次加速、制动或舒适性监测后显示在这里。")
            }
        }
    }
    SnackbarHost(hostState=performanceSnackbar,modifier=Modifier.padding(vertical=8.dp))
    deleting?.let{s->GlassDialog(onDismissRequest={deleting=null},title="删除测试记录",text={Text("确认删除 ${s.name} 及全部高频传感器数据？")},confirmButton={GlassDialogAction(onClick={vm.delete(s.id);deleting=null}){Text("删除",color=Error)}},dismissButton={GlassDialogAction(onClick={deleting=null}){Text("取消")}})}
}

@Composable
private fun PerformanceSetupScreen(
    state: LedgerUiState,
    onBack: () -> Unit,
    onStart: (String?, String) -> Unit
) {
    var vehicleId by rememberSaveable { mutableStateOf<String?>(null) }
    var name by rememberSaveable { mutableStateOf("综合性能测试") }
    var vehiclePickerOpen by rememberSaveable { mutableStateOf(false) }
    var bindVehicle by rememberSaveable { mutableStateOf(false) }
    val selectedVehicle = state.vehicles.firstOrNull { it.id == vehicleId }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, top = 8.dp, end = 20.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        BackButton(onBack)
        Text("综合性能测试", color = TextPrimary, fontSize = 30.sp, fontWeight = FontWeight.Black)
        Text(
            "一次测试自动记录四项速度成绩，并全程记录噪音与顿挫。",
            color = TextSecondary,
            fontSize = 15.sp,
            lineHeight = 22.sp
        )
        CardBlock(accentWash = PrimaryBlue.copy(alpha = 0.09f)) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("自动记录", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text("加速：0–50、0–100 km/h", color = TextSecondary)
                Text("制动：100–0、50–0 km/h", color = TextSecondary)
                Text("舒适性：车内噪音、顿挫全程记录", color = TextSecondary)
                Text("结束条件：完成 100 km/h 以上加速后，再减速至 0。", color = TextSecondary)
            }
        }
        GlassActionButton("开始综合测试", Icons.Outlined.Speed, PrimaryBlue) {
            onStart(if (bindVehicle) vehicleId else null, name)
        }
        AppTextField("回放名称", name, { name = it }, "", KeyboardType.Text)
        SectionTitle("绑定车辆")
        CardBlock(contentPadding = 14.dp) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("绑定到车辆", color = TextPrimary, fontWeight = FontWeight.Bold)
                    Text(
                        if (bindVehicle) "回放归入所选车辆" else "关闭后仅保存为独立测试",
                        color = TextTertiary,
                        fontSize = 12.sp
                    )
                }
                LiquidToggle(
                        selected = { bindVehicle },
                        onSelect = { checked ->
                            bindVehicle = checked
                            if (!checked) {
                                vehiclePickerOpen = false
                                vehicleId = null
                                name = "综合性能测试"
                            } else {
                                selectedVehicle?.let { vehicle ->
                                    name = "${vehicle.name} · 综合性能测试"
                                }
                            }
                        },
                        backdrop = rememberLayerAwareBackdrop(),
                        modifier = Modifier.padding(end = 2.dp)
                    )
            }
        }
        if (bindVehicle) {
            VehicleSelectorCard(
                vehicle = selectedVehicle,
                open = vehiclePickerOpen,
                vehicles = state.vehicles,
                consumptions = state.vehicleConsumptions,
                onToggle = { vehiclePickerOpen = !vehiclePickerOpen },
                onSelect = {
                    vehicleId = it.id
                    name = "${it.name} · 综合性能测试"
                    vehiclePickerOpen = false
                }
            )
        }
        InfoCard("安全提示", "请在封闭、安全、允许测试的场地进行。手机必须固定在支架上，驾驶员不得操作手机。")
    }
}

@Composable private fun PerformanceDetail(session:com.cheku.app.data.PerformanceSessionEntity,vehicle:VehicleEntity?,samples:List<com.cheku.app.data.PerformanceSampleEntity>,jerks:List<com.cheku.app.data.PerformanceJerkEntity>,splits:List<com.cheku.app.data.PerformanceSplitEntity>){
    InfoCard(
        session.testType.performanceLabel(),
        "${vehicle?.name ?: "未绑定车辆"}${vehicle?.model?.let { " · $it" }.orEmpty()} · ${session.resultSeconds?.let{"${formatDecimal(it)} 秒 · ${formatDecimal(session.resultDistanceMeters?:0.0)} 米"}?:"综合回放"}"
    )
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
        StatCard("最大加速度",formatDecimal(session.maxAccelerationMps2),"m/s²",Modifier.weight(1f),Warning)
        StatCard("最大速度",samples.maxOfOrNull{it.fusedSpeedMps*3.6}?.let(::formatDecimal)?:"--","km/h",Modifier.weight(1f),PrimaryBlue)
    }
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
        StatCard("平均相对噪音",session.averageNoiseDbA?.let(::formatDecimal)?:"--","dB rel.",Modifier.weight(1f),Success)
        StatCard("最大相对噪音",session.maxNoiseDbA?.let(::formatDecimal)?:"--","dB rel.",Modifier.weight(1f),Success)
    }
    PerformanceReplayChart(samples)
    if(session.testType==PerformanceTestType.Combined){SectionTitle("五项指标");splits.forEach{s->InfoCard(s.label,"${formatDecimal(s.elapsedSeconds)} 秒 · ${formatDecimal(s.distanceMeters)} 米")};if(splits.isEmpty())InfoCard("指标生成中","继续完成加速和制动阶段后，这里会显示四项分段成绩。")}
    SectionTitle("噪音与顿挫回放");if(jerks.isEmpty())InfoCard("未发现明显顿挫","自动识别会排除转弯、垂直颠簸和支架晃动。") else jerks.forEach{j->InfoCard(if(j.manual)"手动标记" else when(j.severity){com.cheku.app.data.JerkSeverity.Mild->"轻微顿挫";com.cheku.app.data.JerkSeverity.Noticeable->"明显顿挫";com.cheku.app.data.JerkSeverity.Strong->"强烈顿挫"},"${SimpleDateFormat("HH:mm:ss",Locale.CHINA).format(Date(j.timestamp))} · ${formatDecimal(j.speedKmh)} km/h · jerk ${formatDecimal(j.jerkPeakMps3.toDouble())} m/s³")}
    if(session.quality!=PerformanceQuality.Valid)InfoCard("数据质量提示",session.invalidReason.ifBlank{"本次数据精度不足，不计入最佳成绩。"})
}

@Composable
private fun PerformanceReplayChart(samples: List<com.cheku.app.data.PerformanceSampleEntity>) {
    var selectedIndex by remember(samples.size) { mutableStateOf<Int?>(null) }
    var showSpeed by remember(samples.size) { mutableStateOf(true) }
    var showAcceleration by remember(samples.size) { mutableStateOf(true) }
    var showNoise by remember(samples.size) { mutableStateOf(true) }
    val selected = selectedIndex?.let(samples::getOrNull)
    val firstTimestamp = samples.firstOrNull()?.timestampNanos ?: 0L
    CardBlock(
        modifier = Modifier.pointerInput(selectedIndex) {
            detectTapGestures(onTap = {
                if (selectedIndex != null) selectedIndex = null
            })
        }
    ) {
        Text("速度 / 加速度 / 噪音", fontWeight = FontWeight.Bold)
        if (selected != null) {
            val elapsed = (selected.timestampNanos - firstTimestamp).coerceAtLeast(0L) / 1e9
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard("时间", formatDecimal(elapsed), "s", Modifier.weight(1f), TextPrimary, 10.dp)
                StatCard("速度", formatDecimal(selected.fusedSpeedMps * 3.6), "km/h", Modifier.weight(1f), PrimaryBlue, 10.dp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard("纵向加速度", formatDecimal(selected.longitudinalAccelerationMps2.toDouble()), "m/s²", Modifier.weight(1f), Warning, 10.dp)
                StatCard("相对噪音", selected.noiseDbA?.toDouble()?.let(::formatDecimal) ?: "--", "dB rel.", Modifier.weight(1f), Success, 10.dp)
            }
        } else if (samples.isNotEmpty()) {
            val duration = (samples.last().timestampNanos - firstTimestamp).coerceAtLeast(0L) / 1e9
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard("总时长", formatDecimal(duration), "s", Modifier.weight(1f), TextPrimary, 10.dp)
                StatCard("最大速度", formatDecimal(samples.maxOf { it.fusedSpeedMps * 3.6 }), "km/h", Modifier.weight(1f), PrimaryBlue, 10.dp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard("最大加速度", formatDecimal(samples.maxOf { abs(it.longitudinalAccelerationMps2).toDouble() }), "m/s²", Modifier.weight(1f), Warning, 10.dp)
                StatCard("平均噪音", samples.mapNotNull { it.noiseDbA }.average().takeIf { !it.isNaN() }?.let(::formatDecimal) ?: "--", "dB rel.", Modifier.weight(1f), Success, 10.dp)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AppFilterChip(showSpeed, { showSpeed = !showSpeed }, "速度", Modifier.weight(1f), PrimaryBlue)
            AppFilterChip(showAcceleration, { showAcceleration = !showAcceleration }, "加速度", Modifier.weight(1f), Warning)
            AppFilterChip(showNoise, { showNoise = !showNoise }, "噪音", Modifier.weight(1f), Success)
        }
        Text(if (selectedIndex == null) "长按图表后拖动" else "轻触图表外区域退出", color = TextTertiary, fontSize = 11.sp)
        // 放大镜：图表自身作为采样源（layerBackdrop），拖动时在指针上方浮出镜片。
        val chartBackdrop = rememberLayerBackdrop()
        var dragPosition by remember { mutableStateOf<Offset?>(null) }
        Box(Modifier.fillMaxWidth()) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(210.dp)
                    .pointerInput(samples.size) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { offset ->
                            dragPosition = offset
                            if (samples.isNotEmpty()) {
                                selectedIndex = ((offset.x / size.width) * samples.lastIndex)
                                    .roundToInt().coerceIn(0, samples.lastIndex)
                            }
                        },
                        onDrag = { change, _ ->
                            dragPosition = change.position
                            if (samples.isNotEmpty()) {
                                selectedIndex = ((change.position.x / size.width) * samples.lastIndex)
                                    .roundToInt().coerceIn(0, samples.lastIndex)
                            }
                            change.consume()
                        },
                        onDragEnd = { dragPosition = null },
                        onDragCancel = { dragPosition = null }
                    )
                }
        ) {
            if (samples.size < 2) return@Canvas
            val labelArea = 26.dp.toPx()
            val plotHeight = size.height - labelArea
            val maxSpeed = samples.maxOf { it.fusedSpeedMps }.coerceAtLeast(1.0)
            val maxAccel = samples.maxOf { abs(it.longitudinalAccelerationMps2) }.coerceAtLeast(1f)
            val smoothSpeeds = ReplaySeries.movingAverage(samples.map { it.fusedSpeedMps })
            val noiseValues = samples.mapNotNull { it.noiseDbA }
            val minNoise = noiseValues.minOrNull() ?: 0f
            val maxNoise = noiseValues.maxOrNull()?.coerceAtLeast(minNoise + 1f) ?: 1f
            samples.indices.drop(1).forEach { index ->
                val x0 = size.width * (index - 1) / samples.lastIndex
                val x1 = size.width * index / samples.lastIndex
                if (showSpeed) drawLine(
                    PrimaryBlue,
                    Offset(x0, plotHeight - (smoothSpeeds[index - 1] / maxSpeed).toFloat() * plotHeight),
                    Offset(x1, plotHeight - (smoothSpeeds[index] / maxSpeed).toFloat() * plotHeight),
                    3.dp.toPx()
                )
                if (showAcceleration) drawLine(
                    Warning,
                    Offset(x0, plotHeight / 2 - samples[index - 1].longitudinalAccelerationMps2 / maxAccel * plotHeight / 2),
                    Offset(x1, plotHeight / 2 - samples[index].longitudinalAccelerationMps2 / maxAccel * plotHeight / 2),
                    2.dp.toPx()
                )
                val oldNoise = samples[index - 1].noiseDbA
                val newNoise = samples[index].noiseDbA
                if (showNoise && oldNoise != null && newNoise != null) {
                    drawLine(
                        Success,
                        Offset(x0, plotHeight - (oldNoise - minNoise) / (maxNoise - minNoise) * plotHeight),
                        Offset(x1, plotHeight - (newNoise - minNoise) / (maxNoise - minNoise) * plotHeight),
                        2.dp.toPx()
                    )
                }
            }
            drawLine(TextTertiary.copy(alpha = 0.35f), Offset(0f, plotHeight), Offset(size.width, plotHeight), 1.dp.toPx())
            val textPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.rgb(120, 130, 145)
                textSize = 10.sp.toPx()
                isAntiAlias = true
            }
            val totalSeconds = (samples.last().timestampNanos - firstTimestamp).coerceAtLeast(0L) / 1e9
            drawContext.canvas.nativeCanvas.drawText("0.0 s", 0f, size.height - 3.dp.toPx(), textPaint)
            val endLabel = "${formatDecimal(totalSeconds)} s"
            drawContext.canvas.nativeCanvas.drawText(
                endLabel,
                size.width - textPaint.measureText(endLabel),
                size.height - 3.dp.toPx(),
                textPaint
            )
            selectedIndex?.let { index ->
                val x = size.width * index / samples.lastIndex
                drawLine(TextPrimary, Offset(x, 0f), Offset(x, plotHeight), 1.dp.toPx())
                drawCircle(Color.White, 5.dp.toPx(), Offset(x, plotHeight - (smoothSpeeds[index] / maxSpeed).toFloat() * plotHeight))
                val currentSeconds = (samples[index].timestampNanos - firstTimestamp).coerceAtLeast(0L) / 1e9
                val currentLabel = "${formatDecimal(currentSeconds)} s"
                val labelX = (x - textPaint.measureText(currentLabel) / 2)
                    .coerceIn(0f, size.width - textPaint.measureText(currentLabel))
                drawContext.canvas.nativeCanvas.drawText(
                    currentLabel,
                    labelX,
                    size.height - 3.dp.toPx(),
                    textPaint
                )
            }
        }
        }
    }
}

private fun PerformanceTestType.performanceLabel()=when(this){PerformanceTestType.Combined->"综合性能测试";PerformanceTestType.Acceleration50->"0–50 km/h 加速";PerformanceTestType.Acceleration100->"0–100 km/h 加速";PerformanceTestType.Braking50->"50–0 km/h 制动";PerformanceTestType.Braking100->"100–0 km/h 制动";PerformanceTestType.Comfort->"噪音与顿挫监测"}
private fun PerformanceStatus.performanceStatusLabel()=when(this){PerformanceStatus.Preparing->"正在校准";PerformanceStatus.Armed->"已待发";PerformanceStatus.Running->"测试中";PerformanceStatus.Accelerating->"加速阶段";PerformanceStatus.BrakingArmed->"已达 100 km/h，等待制动";PerformanceStatus.Braking->"制动阶段";PerformanceStatus.Completed->"已完成";PerformanceStatus.Interrupted->"已中断"}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun FitRecordsScreen(state: LedgerUiState, fitViewModel: FitViewModel, onBack: () -> Unit) {
    val activities by fitViewModel.activities.collectAsStateWithLifecycle()
    val selected by fitViewModel.selectedActivity.collectAsStateWithLifecycle()
    val samples by fitViewModel.samples.collectAsStateWithLifecycle()
    val activeSamples by fitViewModel.activeSamples.collectAsStateWithLifecycle()
    val bleDevices by fitViewModel.bleDevices.collectAsStateWithLifecycle()
    val selectedBle by fitViewModel.selectedBleAddresses.collectAsStateWithLifecycle()
    val signalStatus by fitViewModel.signalStatus.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var showStart by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<FitActivityEntity?>(null) }
    var pendingExport by remember { mutableStateOf<Triple<String, ByteArray, String>?>(null) }
    val exportState by fitViewModel.exportState.collectAsStateWithLifecycle()
    val fitMessage by fitViewModel.message.collectAsStateWithLifecycle()
    val exportSnackbar = remember { SnackbarHostState() }
    LaunchedEffect(exportState) {
        val message = when (val state = exportState) {
            FitExportState.Idle, FitExportState.Preparing -> null
            is FitExportState.AwaitingFile -> "请选择保存位置"
            is FitExportState.Success -> "FIT 已导出：${state.fileName}"
            is FitExportState.Failure -> "FIT 导出失败：${state.message}"
            FitExportState.Cancelled -> "已取消 FIT 导出"
        }
        if (message != null) {
            exportSnackbar.showSnackbar(message)
            fitViewModel.resetExportState()
        }
    }
    LaunchedEffect(fitMessage) {
        fitMessage?.let {
            exportSnackbar.showSnackbar(it)
            fitViewModel.clearMessage()
        }
    }
    val createFit = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val pending = pendingExport
        pendingExport = null
        if (pending == null) return@rememberLauncherForActivityResult
        if (uri == null) {
            fitViewModel.exportCancelled()
            return@rememberLauncherForActivityResult
        }
        runCatching {
            FitExportWriter.write(pending.second) { context.contentResolver.openOutputStream(uri) }
        }.onSuccess {
            fitViewModel.markExported(pending.first, pending.third)
        }.onFailure {
            fitViewModel.exportFailed(it)
        }
    }
    var pendingStart by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    var requestingBleScan by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        val config = pendingStart
        if (config != null) {
            if (grants[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
                fitViewModel.start(config.first, config.second)
            } else {
                fitViewModel.message.value = "未授予精确定位权限，无法开始 FIT 记录"
            }
        }
        if (requestingBleScan) {
            if (android.os.Build.VERSION.SDK_INT < 31 || grants[Manifest.permission.BLUETOOTH_SCAN] == true) {
                fitViewModel.scanBle()
            } else {
                fitViewModel.message.value = "未授予蓝牙扫描权限"
            }
        }
        pendingStart = null
        requestingBleScan = false
    }
    BackHandler { if (selected != null) fitViewModel.select(null) else onBack() }
    AnimatedContent(
        targetState = selected?.id,
        transitionSpec = {
            hierarchyMotion(targetState != null)
        },
        label = "fit-hierarchy"
    ) { selectedId ->
        val selectedActivity = selected?.takeIf { it.id == selectedId }
        if (selectedActivity != null) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FitActivityDetail(
                    selectedActivity,
                    samples,
                    exporting = exportState is FitExportState.Preparing || exportState is FitExportState.AwaitingFile,
                    onBack = { fitViewModel.select(null) },
                    onExport = { start, end ->
                        if (exportState !is FitExportState.Preparing && exportState !is FitExportState.AwaitingFile) {
                            fitViewModel.beginExport()
                            scope.launch {
                                runCatching { fitViewModel.buildFit(selectedActivity.id, start, end) }
                                    .onSuccess { bytes ->
                                        val fileName = "${selectedActivity.name}_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.CHINA).format(Date(selectedActivity.startedAt))}.fit"
                                        pendingExport = Triple(selectedActivity.id, bytes, fileName)
                                        fitViewModel.awaitingExportFile(fileName)
                                        createFit.launch(fileName)
                                    }
                                    .onFailure { fitViewModel.exportFailed(it) }
                            }
                        }
                    }
                )
                SnackbarHost(
                    hostState = exportSnackbar,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        } else Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            BackButton(onBack)
            val active = activities.firstOrNull { it.status != FitActivityStatus.Completed }
            active?.let { activity ->
        CardBlock(accentWash = PrimaryBlue.copy(alpha = 0.10f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("正在记录", color = PrimaryBlue, fontWeight = FontWeight.Black, fontSize = 17.sp, modifier = Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    Text(if (signalStatus.level == 0) "GPS 不可用" else "GPS " + "●".repeat(signalStatus.level) + "○".repeat(3 - signalStatus.level), color = if (signalStatus.level == 0) Warning else Success, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text(signalStatus.mode, color = if (signalStatus.mode.contains("传感器")) Color(0xFF8A4B00) else TextSecondary, fontWeight = FontWeight.Medium, fontSize = 10.sp)
                }
            }
            Text(activity.name, fontWeight = FontWeight.Bold)
            Text("${activity.status.fitLabel()} · ${formatDecimal(activity.distanceMeters / 1000.0)} km", color = TextSecondary)
            FitRoutePreview(activeSamples, 0f, 1f, null)
            FitTimelineChart(activeSamples, 0f, 1f, null, interactive = false) {}
            val latest = activeSamples.lastOrNull()
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard("实时速度", latest?.speedMps?.times(3.6)?.let(::formatDecimal) ?: "--", "km/h", Modifier.weight(1f), PrimaryBlue)
                StatCard("当前海拔", latest?.altitudeMeters?.let(::formatDecimal) ?: "--", "m", Modifier.weight(1f), Success)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard("累计距离", formatDecimal(activity.distanceMeters / 1000.0), "km", Modifier.weight(1f), Warning)
                StatCard("GPS 精度", latest?.accuracyMeters?.toDouble()?.let(::formatDecimal) ?: "--", "m", Modifier.weight(1f), TextSecondary)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard("当前心率", signalStatus.heartRate?.toString() ?: "--", "bpm", Modifier.weight(1f), Error)
                StatCard(
                    "蓝牙传感器",
                    if (signalStatus.connectedSensors > 0) "已连接" else "未连接",
                    "${signalStatus.connectedSensors} 台",
                    Modifier.weight(1f),
                    if (signalStatus.connectedSensors > 0) Success else TextSecondary
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (activity.status == FitActivityStatus.Recording) GlassActionButton("暂停", Icons.Outlined.Speed, Warning, Modifier.weight(1f)) { fitViewModel.pause() }
                else GlassActionButton("继续", Icons.Outlined.Speed, Success, Modifier.weight(1f)) { fitViewModel.resume() }
                GlassActionButton("结束保存", Icons.Outlined.Save, PrimaryBlue, Modifier.weight(1f)) { fitViewModel.finish() }
            }
        }
            }
            if (active == null) GlassActionButton("开始新记录", Icons.Outlined.Add, PrimaryBlue) { showStart = true }
            SectionTitle("已保存活动")
            activities.filter { it.status == FitActivityStatus.Completed }.forEach { activity ->
        CardBlock(onClick = { fitViewModel.select(activity.id) }, onLongClick = { deleting = activity }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Route, null, tint = PrimaryBlue)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(activity.name, fontWeight = FontWeight.Bold)
                    Text(SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.CHINA).format(Date(activity.startedAt)), color = TextTertiary, fontSize = 11.sp)
                }
                Text("${formatDecimal(activity.distanceMeters / 1000)} km", color = PrimaryBlue, fontWeight = FontWeight.Bold)
            }
            Text(if (activity.exportedAt == null) "尚未导出" else "已导出 FIT", color = if (activity.exportedAt == null) Warning else Success, fontSize = 11.sp)
        }
            }
            if (activities.none { it.status == FitActivityStatus.Completed }) InfoCard("暂无轨迹", "完成一次实时记录后会保存在这里。")
            if (showStart) {
        var name by remember { mutableStateOf("运动轨迹") }
        var autoPause by remember { mutableStateOf(true) }
        GlassDialog(onDismissRequest = { showStart = false }, title = "开始 FIT 记录", text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AppTextField("活动名称", name, { name = it }, "", KeyboardType.Text)
                SettlementOptionRow("自动暂停", "低于 2 km/h 持续 5 秒暂停", autoPause) { autoPause = it }
                InfoCard("蓝牙传感器", "支持广播标准 BLE 心率、踏频和功率服务的设备；未配置时仅记录手机 GPS。")
                GlassDialogAction(onClick = {
                    requestingBleScan = true
                    if (android.os.Build.VERSION.SDK_INT >= 31) permissionLauncher.launch(arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT))
                    else fitViewModel.scanBle()
                }, modifier = Modifier.fillMaxWidth()) { Text("扫描 10 秒") }
                bleDevices.forEach { (address, label) ->
                    AppFilterChip(address in selectedBle, { fitViewModel.toggleBle(address) }, "$label · ${address.takeLast(5)}")
                }
            }
        }, confirmButton = { GlassDialogAction(onClick = {
            showStart = false
            pendingStart = name to autoPause
            val permissions = buildList {
                add(Manifest.permission.ACCESS_FINE_LOCATION)
                if (android.os.Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
                if (android.os.Build.VERSION.SDK_INT >= 31) { add(Manifest.permission.BLUETOOTH_SCAN); add(Manifest.permission.BLUETOOTH_CONNECT) }
            }.toTypedArray()
            permissionLauncher.launch(permissions)
        }) { Text("开始记录") } }, dismissButton = { GlassDialogAction(onClick = { showStart = false }) { Text("取消") } })
            }
            deleting?.let { activity -> GlassDialog(onDismissRequest = { deleting = null }, title = "删除轨迹", text = { Text("确认删除 ${activity.name} 及全部 GPS 数据？") }, confirmButton = { GlassDialogAction(onClick = { fitViewModel.delete(activity.id); deleting = null }) { Text("删除", color = Error) } }, dismissButton = { GlassDialogAction(onClick = { deleting = null }) { Text("取消") } }) }
        }
    }
}

@Composable
private fun FitActivityDetail(
    activity: FitActivityEntity,
    samples: List<FitSampleEntity>,
    exporting: Boolean,
    onBack: () -> Unit,
    onExport: (Float, Float) -> Unit
) {
    var trimRange by remember(activity.id) { mutableStateOf(0f..1f) }
    var previewIndex by remember(activity.id) { mutableStateOf<Int?>(null) }
    val startTrim = trimRange.start
    val endTrim = trimRange.endInclusive
    val preview = previewIndex?.let(samples::getOrNull)
    BackButton(onBack)
    val altitude = samples.mapNotNull { it.altitudeMeters }
    val hr = samples.mapNotNull { it.heartRateBpm }
    val altitudeMax = altitude.maxOrNull()?.let(::formatDecimal) ?: "--"
    InfoCard(activity.name, "FIT 时间轴回放 · ${samples.size} 个采样点")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatCard("距离", formatDecimal(activity.distanceMeters / 1000), "km", Modifier.weight(1f), PrimaryBlue)
        StatCard("最高速度", formatDecimal(activity.maxSpeedMps * 3.6), "km/h", Modifier.weight(1f), Warning)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatCard("最高海拔", altitudeMax, "m", Modifier.weight(1f), Success)
        StatCard("平均心率", hr.average().takeIf { !it.isNaN() }?.let(::formatDecimal) ?: "--", "bpm", Modifier.weight(1f), Error)
    }
    FitRoutePreview(samples, startTrim, endTrim, previewIndex)
    FitTimelineChart(samples, startTrim, endTrim, previewIndex, interactive = true) { previewIndex = it }
    val previewTime = preview?.timestamp?.let { SimpleDateFormat("HH:mm:ss", Locale.CHINA).format(Date(it)) } ?: "全局数据"
    CardBlock {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(previewTime, fontWeight = FontWeight.Black, fontSize = 18.sp, modifier = Modifier.weight(1f))
            Text(if (preview == null) "整段回放" else "当前采样", color = PrimaryBlue, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard("当前速度", preview?.speedMps?.times(3.6)?.let(::formatDecimal) ?: "--", "km/h", Modifier.weight(1f), PrimaryBlue)
            StatCard("当前海拔", preview?.altitudeMeters?.let(::formatDecimal) ?: "--", "m", Modifier.weight(1f), Success)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard("累计距离", preview?.distanceMeters?.div(1000.0)?.let(::formatDecimal) ?: "--", "km", Modifier.weight(1f), Warning)
            StatCard("当前心率", preview?.heartRateBpm?.toString() ?: "--", "bpm", Modifier.weight(1f), Error)
        }
    }
    SectionTitle("隐私裁剪")
    Text("保留 ${formatDecimal(startTrim * 100.0)}% — ${formatDecimal(endTrim * 100.0)}%", color = TextSecondary, fontSize = 12.sp)
    LiquidRangeSlider(
        value = { trimRange },
        onValueChange = { range ->
            if (range.endInclusive - range.start >= .02f) {
                trimRange = range
                previewIndex = previewIndex?.coerceIn(
                    (samples.lastIndex * range.start).toInt(),
                    (samples.lastIndex * range.endInclusive).toInt().coerceAtLeast(0)
                )
            }
        },
        valueRange = 0f..1f,
        visibilityThreshold = 0.001f,
        backdrop = rememberLayerAwareBackdrop(),
        modifier = Modifier.fillMaxWidth()
    )
    InfoCard("GPS 隐私", "导出的 FIT 包含精确轨迹，请确认首尾位置没有暴露住宅或单位。")
    GlassActionButton(
        if (exporting) "正在生成 FIT" else "校验并导出 FIT",
        Icons.Outlined.Upload,
        PrimaryBlue,
        enabled = !exporting
    ) { onExport(startTrim, endTrim) }
}

@Composable
private fun FitTimelineChart(
    samples: List<FitSampleEntity>,
    start: Float,
    end: Float,
    selectedIndex: Int?,
    interactive: Boolean = true,
    onSelectedIndex: (Int?) -> Unit
) {
    var showSpeed by remember(samples.size, start, end) { mutableStateOf(true) }
    var showAltitude by remember(samples.size, start, end) { mutableStateOf(true) }
    var showHeartRate by remember(samples.size, start, end) { mutableStateOf(true) }
    val from = if (samples.isEmpty()) 0 else (samples.lastIndex * start).toInt()
    val to = if (samples.isEmpty()) 0 else (samples.lastIndex * end).toInt().coerceAtLeast(from + 1).coerceAtMost(samples.lastIndex)
    val visible = if (samples.isEmpty()) emptyList() else samples.subList(from, to + 1)
    CardBlock(
        modifier = Modifier.pointerInput(selectedIndex, from, to) {
            detectTapGestures(onTap = { if (selectedIndex != null) onSelectedIndex(null) })
        }
    ) {
        Text("时间轴回放", fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AppFilterChip(showSpeed, { showSpeed = !showSpeed }, "速度", Modifier.weight(1f), PrimaryBlue)
            AppFilterChip(showAltitude, { showAltitude = !showAltitude }, "海拔", Modifier.weight(1f), Success)
            AppFilterChip(showHeartRate, { showHeartRate = !showHeartRate }, "心率", Modifier.weight(1f), Error)
        }
        if (interactive) {
            Text(if (selectedIndex == null) "长按后拖动" else "轻触退出单点", color = TextTertiary, fontSize = 11.sp)
        }
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(220.dp)
                .then(
                    if (interactive) Modifier.pointerInput(samples.size, start, end) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { offset ->
                                if (visible.isNotEmpty()) {
                                    val index = (offset.x / size.width * (visible.lastIndex)).roundToInt().coerceIn(0, visible.lastIndex)
                                    onSelectedIndex(from + index)
                                }
                            },
                            onDrag = { change, _ ->
                                if (visible.isNotEmpty()) {
                                    val index = (change.position.x / size.width * visible.lastIndex).roundToInt().coerceIn(0, visible.lastIndex)
                                    onSelectedIndex(from + index)
                                }
                                change.consume()
                            }
                        )
                    } else Modifier
                )
        ) {
            if (visible.size < 2) return@Canvas
            val labelArea = 26.dp.toPx()
            val plotHeight = size.height - labelArea
            val maxSpeed = visible.maxOf { it.speedMps }.coerceAtLeast(1.0)
            val minAltitude = visible.minOfOrNull { it.altitudeMeters ?: 0.0 } ?: 0.0
            val maxAltitude = visible.maxOfOrNull { it.altitudeMeters ?: 1.0 }?.coerceAtLeast(minAltitude + 1.0) ?: 1.0
            val hrValues = visible.mapNotNull { it.heartRateBpm?.toDouble() }
            val minHr = hrValues.minOrNull() ?: 0.0
            val maxHr = hrValues.maxOrNull()?.coerceAtLeast(minHr + 1.0) ?: 1.0
            val smoothSpeeds = ReplaySeries.movingAverage(visible.map { it.speedMps })
            visible.indices.drop(1).forEach { index ->
                val x0 = size.width * (index - 1) / visible.lastIndex
                val x1 = size.width * index / visible.lastIndex
                fun ySpeed(value: Double) = plotHeight - (value / maxSpeed).toFloat() * plotHeight
                fun yAltitude(value: Double) = plotHeight - ((value - minAltitude) / (maxAltitude - minAltitude)).toFloat() * plotHeight
                if (showSpeed) drawLine(PrimaryBlue, Offset(x0, ySpeed(smoothSpeeds[index - 1])), Offset(x1, ySpeed(smoothSpeeds[index])), 3.dp.toPx())
                if (showAltitude) drawLine(Success, Offset(x0, yAltitude(visible[index - 1].altitudeMeters ?: minAltitude)), Offset(x1, yAltitude(visible[index].altitudeMeters ?: minAltitude)), 2.dp.toPx())
                val oldHr = visible[index - 1].heartRateBpm?.toDouble()
                val newHr = visible[index].heartRateBpm?.toDouble()
                if (showHeartRate && oldHr != null && newHr != null) {
                    fun yHr(value: Double) = plotHeight - ((value - minHr) / (maxHr - minHr)).toFloat() * plotHeight
                    drawLine(Error, Offset(x0, yHr(oldHr)), Offset(x1, yHr(newHr)), 2.dp.toPx())
                }
            }
            drawLine(TextTertiary.copy(alpha = .35f), Offset(0f, plotHeight), Offset(size.width, plotHeight), 1.dp.toPx())
            val paint = android.graphics.Paint().apply {
                color = android.graphics.Color.rgb(120, 130, 145)
                textSize = 10.sp.toPx()
                isAntiAlias = true
            }
            val duration = (visible.last().timestamp - visible.first().timestamp).coerceAtLeast(0L) / 1000.0
            drawContext.canvas.nativeCanvas.drawText("0.0 s", 0f, size.height - 3.dp.toPx(), paint)
            val endText = "${formatDecimal(duration)} s"
            drawContext.canvas.nativeCanvas.drawText(endText, size.width - paint.measureText(endText), size.height - 3.dp.toPx(), paint)
            selectedIndex?.takeIf { it in from..to }?.let { absolute ->
                val index = absolute - from
                val x = size.width * index / visible.lastIndex
                drawLine(TextPrimary, Offset(x, 0f), Offset(x, plotHeight), 1.dp.toPx())
                val currentTime = (visible[index].timestamp - visible.first().timestamp).coerceAtLeast(0L) / 1000.0
                val currentText = "${formatDecimal(currentTime)} s"
                val textX = (x - paint.measureText(currentText) / 2).coerceIn(0f, size.width - paint.measureText(currentText))
                drawContext.canvas.nativeCanvas.drawText(currentText, textX, size.height - 3.dp.toPx(), paint)
            }
        }
    }
}

@Composable
private fun FitRoutePreview(
    samples: List<FitSampleEntity>,
    start: Float,
    end: Float,
    selectedIndex: Int?
) {
    CardBlock {
        Text("轨迹方向", fontWeight = FontWeight.Bold)
        Canvas(Modifier.fillMaxWidth().height(190.dp)) {
            if (samples.size < 2) return@Canvas
            val from = (samples.lastIndex * start).toInt()
            val to = (samples.lastIndex * end).toInt().coerceAtLeast(from + 1).coerceAtMost(samples.lastIndex)
            val points = samples.subList(from, to + 1)
            val minLat = points.minOf { it.latitude }
            val maxLat = points.maxOf { it.latitude }
            val minLon = points.minOf { it.longitude }
            val maxLon = points.maxOf { it.longitude }
            val latRange = (maxLat - minLat).takeIf { it > 0 } ?: 1.0
            val lonRange = (maxLon - minLon).takeIf { it > 0 } ?: 1.0
            fun point(sample: FitSampleEntity) = Offset(
                ((sample.longitude - minLon) / lonRange).toFloat() * size.width,
                size.height - ((sample.latitude - minLat) / latRange).toFloat() * size.height
            )
            points.indices.drop(1).forEach { index ->
                drawLine(PrimaryBlue, point(points[index - 1]), point(points[index]), 4.dp.toPx())
            }
            drawCircle(Success, 7.dp.toPx(), point(points.first()))
            drawCircle(Error, 7.dp.toPx(), point(points.last()))
            selectedIndex?.takeIf { it in from..to }?.let {
                drawCircle(Color.White, 9.dp.toPx(), point(samples[it]))
                drawCircle(PrimaryBlue, 5.dp.toPx(), point(samples[it]))
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("起点", color = Success, fontSize = 11.sp)
            Text("终点", color = Error, fontSize = 11.sp)
        }
    }
}

private fun FitActivityStatus.fitLabel() = when(this) {
    FitActivityStatus.Recording -> "记录中"; FitActivityStatus.ManualPaused -> "手动暂停"; FitActivityStatus.AutoPaused -> "自动暂停"; FitActivityStatus.Completed -> "已完成"; FitActivityStatus.Interrupted -> "可恢复"
}

private data class StatisticsResult(
    val distance: Double,
    val totalFuel: Double,
    val totalCharge: Double,
    val energyCost: Double,
    val periodicCost: Double,
    val insuranceCost: Double,
    val repairCost: Double,
    val roadFeeCost: Double,
    val fuelConsumption: Double?,
    val chargeConsumption: Double?,
    val trend: List<StatisticsTrendBucket>,
    val energyTrend: List<StatisticsEnergyPoint>,
    val expenses: List<StatisticsExpense>,
    val warnings: List<String>
) {
    val totalCost: Double get() = energyCost + periodicCost + insuranceCost + repairCost + roadFeeCost
    val costPerKm: Double get() = if (distance > 0.0) totalCost / distance else 0.0
}

private data class StatisticsEnergyPoint(
    val time: Long,
    val fuel: Double,
    val charge: Double,
    val mileage: Double,
    val distanceKm: Double,
    val fuelEconomy: Double?,
    val chargeEconomy: Double?
)

private data class StatisticsTrendBucket(
    val dayStart: Long,
    var energy: Double = 0.0,
    var periodic: Double = 0.0,
    var insurance: Double = 0.0,
    var repair: Double = 0.0,
    var roadFee: Double = 0.0
) {
    val total: Double get() = energy + periodic + insurance + repair + roadFee
}

private enum class StatisticsExpenseCategory { Energy, Periodic, Insurance, Repair }

private data class StatisticsExpense(
    val category: StatisticsExpenseCategory,
    val id: String,
    val vehicleName: String,
    val title: String,
    val time: Long,
    val cost: Double,
    val energy: com.cheku.app.data.EnergyRecordEntity? = null,
    val history: MaintenanceHistoryEntity? = null,
    val itemType: MaintenanceItemType = MaintenanceItemType.Periodic
)

@Composable
private fun StatisticsScreen(
    state: LedgerUiState,
    onSelectVehicle: (String) -> Unit,
    onUpdateEnergy: (String, String, EnergyRecordFormInput) -> Unit,
    onUpdateMaintenance: (String, String, String, String, String, String, String) -> Unit,
    onUpdateSpecial: (String, String, String, String, String, String, String, String) -> Unit,
    onDeleteRecord: (String, RecentRecordType) -> Unit,
    onDeleteHistory: (String) -> Unit,
    onBack: () -> Unit
) {
    var allVehicles by rememberSaveable { mutableStateOf(false) }
    var period by rememberSaveable { mutableStateOf(StatisticsPeriod.Month) }
    var pickerOpen by rememberSaveable { mutableStateOf(false) }
    var expandedCategory by rememberSaveable { mutableStateOf<StatisticsExpenseCategory?>(null) }
    var enabledTrendMetrics by rememberSaveable { mutableStateOf(setOf("mileage", "fuel", "charge", "fuelEconomy", "chargeEconomy")) }
    val trendDrawProgress = mapOf(
        "mileage" to animateFloatAsState(if ("mileage" in enabledTrendMetrics) 1f else 0f, tween(420, easing = FastOutSlowInEasing), label = "trend-mileage").value,
        "fuel" to animateFloatAsState(if ("fuel" in enabledTrendMetrics) 1f else 0f, tween(420, easing = FastOutSlowInEasing), label = "trend-fuel").value,
        "charge" to animateFloatAsState(if ("charge" in enabledTrendMetrics) 1f else 0f, tween(420, easing = FastOutSlowInEasing), label = "trend-charge").value,
        "fuelEconomy" to animateFloatAsState(if ("fuelEconomy" in enabledTrendMetrics) 1f else 0f, tween(420, easing = FastOutSlowInEasing), label = "trend-fuel-economy").value,
        "chargeEconomy" to animateFloatAsState(if ("chargeEconomy" in enabledTrendMetrics) 1f else 0f, tween(420, easing = FastOutSlowInEasing), label = "trend-charge-economy").value
    )
    var editingExpense by remember { mutableStateOf<StatisticsExpense?>(null) }
    var deletingExpense by remember { mutableStateOf<StatisticsExpense?>(null) }
    val result = remember(state, allVehicles, period) { calculateStatistics(state, allVehicles, period) }
    val animatedDistance by animateFloatAsState(result.distance.toFloat(), tween(360, easing = FastOutSlowInEasing), label = "stats-distance")
    val animatedFuel by animateFloatAsState(result.totalFuel.toFloat(), tween(360), label = "stats-fuel")
    val animatedCharge by animateFloatAsState(result.totalCharge.toFloat(), tween(360), label = "stats-charge")
    val animatedEnergyCost by animateFloatAsState(result.energyCost.toFloat(), tween(360), label = "stats-energy-cost")
    val animatedPeriodicCost by animateFloatAsState(result.periodicCost.toFloat(), tween(360), label = "stats-periodic-cost")
    val animatedInsuranceCost by animateFloatAsState(result.insuranceCost.toFloat(), tween(360), label = "stats-insurance-cost")
    val animatedRepairCost by animateFloatAsState(result.repairCost.toFloat(), tween(360), label = "stats-repair-cost")
    val animatedRoadFeeCost by animateFloatAsState(result.roadFeeCost.toFloat(), tween(360), label = "stats-road-fee-cost")
    val shown = result.copy(
        distance = animatedDistance.toDouble(), totalFuel = animatedFuel.toDouble(), totalCharge = animatedCharge.toDouble(),
        energyCost = animatedEnergyCost.toDouble(), periodicCost = animatedPeriodicCost.toDouble(),
        insuranceCost = animatedInsuranceCost.toDouble(), repairCost = animatedRepairCost.toDouble(),
        roadFeeCost = animatedRoadFeeCost.toDouble()
    )
    BackButton(onBack)
    SectionTitle("统计范围")
    GlassSegmentedControl(
        options = listOf("当前车辆", "全部车辆"),
        selectedIndex = if (allVehicles) 1 else 0,
        onSelect = { allVehicles = it == 1 },
        backdrop = rememberLayerAwareBackdrop(),
        accent = PrimaryBlue,
        selectedTextColor = PrimaryBlue,
        unselectedTextColor = TextPrimary.copy(alpha = 0.82f),
        modifier = Modifier.fillMaxWidth()
    )
    if (!allVehicles) {
        VehicleSelectorCard(
            vehicle = state.selectedVehicle,
            open = pickerOpen,
            vehicles = state.vehicles,
            consumptions = state.vehicleConsumptions,
            onToggle = { pickerOpen = !pickerOpen },
            onSelect = { onSelectVehicle(it.id); pickerOpen = false }
        )
    }
    GlassSegmentedControl(
        options = StatisticsPeriod.entries.map { it.label },
        selectedIndex = StatisticsPeriod.entries.indexOf(period).coerceAtLeast(0),
        onSelect = { period = StatisticsPeriod.entries[it] },
        backdrop = rememberLayerAwareBackdrop(),
        accent = PrimaryBlue,
        selectedTextColor = PrimaryBlue,
        unselectedTextColor = TextPrimary.copy(alpha = 0.82f),
        modifier = Modifier.fillMaxWidth()
    )
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatCard("行驶里程", formatDecimal(shown.distance), "km", Modifier.weight(1f), PrimaryBlue, 11.dp, unitInHeader = true)
        MoneyStatCard(shown.totalCost, Modifier.weight(1f))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ConsumptionSummaryCard("燃油", shown.totalFuel, "L", if (shown.distance > 0) shown.totalFuel / shown.distance else 0.0, Warning, Modifier.weight(1f))
        ConsumptionSummaryCard("电能", shown.totalCharge, "kWh", if (shown.distance > 0) shown.totalCharge / shown.distance else 0.0, PrimaryBlue, Modifier.weight(1f))
    }
    CostPerKmCard(shown)
    SectionTitle("用车趋势")
    CardBlock {
        ColorMetricToggleRow(
            options = listOf(
                "mileage" to "里程" to Color(0xFF00A6A6),
                "fuel" to "油量" to Warning,
                "charge" to "电量" to PrimaryBlue,
                "fuelEconomy" to "油耗" to Success,
                "chargeEconomy" to "电耗" to Color(0xFF7C5CE7)
            ),
            selected = enabledTrendMetrics,
            onToggle = { key ->
                enabledTrendMetrics = if (key in enabledTrendMetrics) enabledTrendMetrics - key else enabledTrendMetrics + key
            },
            compact = true
        )
        StatisticsEnergyChart(result.energyTrend, enabledTrendMetrics, trendDrawProgress)
    }
    SectionTitle("花费趋势")
    CardBlock {
        AnimatedContent(
            targetState = result.trend,
            transitionSpec = { fadeIn(tween(260)) togetherWith fadeOut(tween(180)) },
            label = "statistics-chart"
        ) { trend -> StatisticsBarChart(trend) }
    }
    SectionTitle("费用构成")
    listOf(
        Triple(StatisticsExpenseCategory.Energy, "补能", PrimaryBlue),
        Triple(StatisticsExpenseCategory.Periodic, "周期性保养", Success),
        Triple(StatisticsExpenseCategory.Insurance, "保险", Color(0xFF7C5CE7)),
        Triple(StatisticsExpenseCategory.Repair, "检修", Warning)
    ).forEach { (category, label, color) ->
        val value = when (category) {
            StatisticsExpenseCategory.Energy -> shown.energyCost
            StatisticsExpenseCategory.Periodic -> shown.periodicCost
            StatisticsExpenseCategory.Insurance -> shown.insuranceCost
            StatisticsExpenseCategory.Repair -> shown.repairCost
        }
        StatisticsCostCard(
            label, value, color, shown.totalCost,
            expanded = expandedCategory == category,
            records = result.expenses.filter { it.category == category },
            onToggle = { expandedCategory = if (expandedCategory == category) null else category },
            onEdit = { editingExpense = it },
            onDelete = { deletingExpense = it }
        )
    }
    if (result.warnings.isNotEmpty()) {
        InfoCard("数据提示", result.warnings.joinToString("\n"))
    }
    editingExpense?.let { expense ->
        StatisticsExpenseEditDialog(
            expense = expense,
            onDismiss = { editingExpense = null },
            onUpdateEnergy = onUpdateEnergy,
            onUpdateMaintenance = onUpdateMaintenance,
            onUpdateSpecial = onUpdateSpecial
        )
    }
    deletingExpense?.let { expense ->
        GlassDialog(
            onDismissRequest = { deletingExpense = null },
            title = "删除记录",
            text = { Text("确认删除“${expense.title}”这笔记录？") },
            confirmButton = {
                GlassDialogAction(onClick = {
                    if (expense.category == StatisticsExpenseCategory.Energy) {
                        val type = if (expense.energy?.type == EnergyRecordType.Fuel) RecentRecordType.Fuel else RecentRecordType.Charge
                        onDeleteRecord(expense.id, type)
                    } else onDeleteHistory(expense.id)
                    deletingExpense = null
                }) { Text("删除", color = Error) }
            },
            dismissButton = { GlassDialogAction(onClick = { deletingExpense = null }) { Text("取消") } }
        )
    }
}

@Composable
private fun ConsumptionSummaryCard(label: String, total: Double, unit: String, perKm: Double, color: Color, modifier: Modifier = Modifier) {
    CardBlock(modifier = modifier, contentPadding = 11.dp) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("总${label}耗量", color = TextSecondary, fontSize = 13.sp)
            Text(unit, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Text(formatDecimal(total), color = color, fontSize = 18.sp, fontWeight = FontWeight.Black, maxLines = 1)
        Text("每公里 ${formatDecimal(perKm)} $unit/km", color = TextTertiary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun CostPerKmCard(result: StatisticsResult) {
    var enabled by rememberSaveable { mutableStateOf(setOf("energy", "periodic", "insurance", "repair", "roadFee")) }
    val parts = listOf(
        Triple("energy", "补能", result.energyCost to PrimaryBlue),
        Triple("periodic", "保养", result.periodicCost to Success),
        Triple("insurance", "保险", result.insuranceCost to Color(0xFF7C5CE7)),
        Triple("repair", "检修", result.repairCost to Warning),
        Triple("roadFee", "ETC", result.roadFeeCost to Color(0xFF0F9F6E))
    )
    val selectedTotal = parts.filter { it.first in enabled }.sumOf { it.third.first }
    val selectedPerKm = if (result.distance > 0.0) selectedTotal / result.distance else 0.0
    CardBlock(contentPadding = 13.dp) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("每公里成本", color = TextSecondary, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Text("¥${formatMoney(selectedPerKm)} /km", color = Warning, fontSize = 20.sp, fontWeight = FontWeight.Black)
        }
        ColorMetricToggleRow(
            options = parts.map { (key, label, amountAndColor) -> key to label to amountAndColor.second },
            selected = enabled,
            onToggle = { key -> enabled = if (key in enabled) enabled - key else enabled + key },
            compact = true
        )
        Row(Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(4.dp))) {
            parts.filter { it.first in enabled }.forEach { (_, _, amountAndColor) ->
                val (cost, color) = amountAndColor
                if (cost > 0.0 && selectedTotal > 0.0) Box(Modifier.weight((cost / selectedTotal).toFloat()).fillMaxHeight().background(color))
            }
            if (selectedTotal <= 0.0) Box(Modifier.weight(1f).fillMaxHeight().background(GlassTokens.trackFill))
        }
    }
}

@Composable
private fun ColorMetricToggleRow(
    options: List<Pair<Pair<String, String>, Color>>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    compact: Boolean = false
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        options.forEach { (identity, color) ->
            val (key, label) = identity
            val active = key in selected
            LiquidButton(
                onClick = { onToggle(key) },
                backdrop = rememberLayerAwareBackdrop(),
                modifier = Modifier
                    .weight(1f)
                    .height(if (compact) 32.dp else 36.dp),
                tint = if (active) color else Color.Unspecified
            ) {
                Text(
                    label,
                    color = if (active) Color.White else color,
                    fontSize = if (compact) 11.sp else 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun StatisticsCostCard(
    label: String,
    value: Double,
    color: Color,
    total: Double,
    expanded: Boolean,
    records: List<StatisticsExpense>,
    onToggle: () -> Unit,
    onEdit: (StatisticsExpense) -> Unit,
    onDelete: (StatisticsExpense) -> Unit
) {
    val ratio = if (total > 0.0) (value / total).toFloat() else 0f
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(180, easing = FastOutSlowInEasing),
        label = "expense-arrow"
    )
    CardBlock(
        onClick = onToggle,
        modifier = Modifier
            .animateContentSize(animationSpec = tween(220, easing = FastOutSlowInEasing), alignment = Alignment.TopCenter),
        contentPadding = 12.dp
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Box(Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).background(color))
            Spacer(Modifier.width(8.dp))
            Text(label, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
            Text("¥${formatMoney(value)}", color = TextSecondary)
            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.Outlined.KeyboardArrowDown,
                contentDescription = if (expanded) "收起" else "展开",
                tint = TextTertiary,
                modifier = Modifier.size(20.dp).graphicsLayer { rotationZ = arrowRotation }
            )
        }
        Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)).background(GlassTokens.trackFill)) {
            Box(Modifier.fillMaxWidth(ratio).fillMaxHeight().background(color))
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(animationSpec = tween(220, easing = FastOutSlowInEasing), expandFrom = Alignment.Top) + fadeIn(tween(120)),
            exit = shrinkVertically(animationSpec = tween(170, easing = FastOutSlowInEasing), shrinkTowards = Alignment.Top) + fadeOut(tween(100))
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.padding(top = 3.dp)) {
                if (records.isEmpty()) {
                    Text("该周期暂无记录", color = TextTertiary, fontSize = 12.sp)
                } else records.sortedByDescending { it.time }.forEach { record ->
                    LiquidGlassListItem(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onEdit(record) },
                        onLongClick = { onDelete(record) }
                    ) {
                        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(record.title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                Text("${record.vehicleName} · ${SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.CHINA).format(Date(record.time))}", color = TextTertiary, fontSize = 11.sp)
                            }
                            Text("¥${formatMoney(record.cost)}", color = color, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatisticsExpenseEditDialog(
    expense: StatisticsExpense,
    onDismiss: () -> Unit,
    onUpdateEnergy: (String, String, EnergyRecordFormInput) -> Unit,
    onUpdateMaintenance: (String, String, String, String, String, String, String) -> Unit,
    onUpdateSpecial: (String, String, String, String, String, String, String, String) -> Unit
) {
    val energy = expense.energy
    val history = expense.history
    var name by remember(expense.id) { mutableStateOf(history?.recordName ?: expense.title) }
    var time by remember(expense.id) { mutableStateOf(SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date(expense.time))) }
    var expires by remember(expense.id) { mutableStateOf(history?.expiresAt?.let { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date(it)) }.orEmpty()) }
    var mileage by remember(expense.id) { mutableStateOf(formatDecimal(energy?.mileage ?: history?.serviceMileage ?: 0.0)) }
    var amount by remember(expense.id) {
        val initialAmount = when (expense.category) {
            StatisticsExpenseCategory.Energy -> energy?.amount ?: 0.0
            StatisticsExpenseCategory.Periodic -> history?.partsCost ?: 0.0
            StatisticsExpenseCategory.Insurance -> history?.insuranceAmount ?: 0.0
            StatisticsExpenseCategory.Repair -> history?.repairAmount ?: 0.0
        }
        mutableStateOf(formatDecimal(initialAmount))
    }
    var orderAmount by remember(expense.id) { mutableStateOf(formatDecimal(energy?.orderAmount ?: 0.0)) }
    var paidAmount by remember(expense.id) { mutableStateOf(formatDecimal(energy?.paidAmount ?: 0.0)) }
    var labor by remember(expense.id) { mutableStateOf(formatDecimal(history?.laborCost ?: 0.0)) }
    var part by remember(expense.id) { mutableStateOf(history?.partNumber.orEmpty()) }
    var note by remember(expense.id) { mutableStateOf(energy?.note ?: history?.note.orEmpty()) }
    var showTimePicker by remember(expense.id) { mutableStateOf(false) }
    var showExpiresPicker by remember(expense.id) { mutableStateOf(false) }
    var timeMillis by remember(expense.id) { mutableStateOf(expense.time) }
    var expiresMillis by remember(expense.id) { mutableStateOf(history?.expiresAt ?: expense.time) }
    if (!showTimePicker && !showExpiresPicker) GlassDialog(
        onDismissRequest = onDismiss,
        title = "编辑${expense.title}",
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (expense.category) {
                    StatisticsExpenseCategory.Energy -> {
                        SelectRow("时间", time) { showTimePicker = true }
                        AppTextField("里程", mileage, { mileage = it }, "km", KeyboardType.Decimal)
                        AppTextField(if (energy?.type == EnergyRecordType.Fuel) "油量" else "电量", amount, { amount = it }, energy?.type?.unit.orEmpty(), KeyboardType.Decimal)
                        AppTextField("订单金额", orderAmount, { orderAmount = it }, "元", KeyboardType.Decimal)
                        AppTextField("实付金额", paidAmount, { paidAmount = it }, "元", KeyboardType.Decimal)
                    }
                    StatisticsExpenseCategory.Periodic -> {
                        SelectRow("时间", time) { showTimePicker = true }
                        AppTextField("里程", mileage, { mileage = it }, "km", KeyboardType.Decimal)
                        AppTextField("零件型号/名称", part, { part = it }, "", KeyboardType.Text)
                        AppTextField("零件费", amount, { amount = it }, "元", KeyboardType.Decimal)
                        AppTextField("工时费", labor, { labor = it }, "元", KeyboardType.Decimal)
                    }
                    StatisticsExpenseCategory.Insurance -> {
                        SelectRow("缴费时间", time) { showTimePicker = true }
                        SelectRow("到期时间", expires.ifBlank { "请选择" }) { showExpiresPicker = true }
                        AppTextField("金额", amount, { amount = it }, "元", KeyboardType.Decimal)
                    }
                    StatisticsExpenseCategory.Repair -> {
                        AppTextField("名称", name, { name = it }, "", KeyboardType.Text)
                        SelectRow("时间", time) { showTimePicker = true }
                        AppTextField("里程", mileage, { mileage = it }, "km", KeyboardType.Decimal)
                        AppTextField("金额", amount, { amount = it }, "元", KeyboardType.Decimal)
                        AppTextField("工时费", labor, { labor = it }, "元", KeyboardType.Decimal)
                    }
                }
                AppTextField("备注", note, { note = it }, "", KeyboardType.Text)
            }
        },
        confirmButton = {
            GlassDialogAction(onClick = {
                when (expense.category) {
                    StatisticsExpenseCategory.Energy -> energy?.let {
                        onUpdateEnergy(expense.id, time, EnergyRecordFormInput(
                            type = it.type, mileage = mileage, amount = amount,
                            orderAmount = orderAmount, paidAmount = paidAmount,
                            isFull = it.isFull, missedPrevious = it.missedPrevious,
                            fuelGrade = it.fuelGrade, unitPrice = it.unitPrice.toString(),
                            chargeMethod = it.chargeMethod, electricityPrice = it.electricityPrice.toString(),
                            serviceFee = it.serviceFee.toString(), chargeLimitSoc = it.chargeLimitSocSnapshot?.toString().orEmpty(),
                            note = note
                        ))
                    }
                    StatisticsExpenseCategory.Periodic -> onUpdateMaintenance(expense.id, mileage, time, part, amount, labor, note)
                    StatisticsExpenseCategory.Insurance -> onUpdateSpecial(expense.id, expense.title, time, expires, mileage, amount, "0.00", note)
                    StatisticsExpenseCategory.Repair -> onUpdateSpecial(expense.id, name, time, "", mileage, amount, labor, note)
                }
                onDismiss()
            }) { Text("保存修改", color = PrimaryBlue) }
        },
        dismissButton = { GlassDialogAction(onClick = onDismiss) { Text("取消", color = TextSecondary) } }
    )
    if (showTimePicker) {
        GlassDateTimePickerDialog(
            initialMillis = timeMillis,
            onDismissRequest = { showTimePicker = false },
            onConfirm = { millis ->
                timeMillis = millis
                time = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date(millis))
                showTimePicker = false
            }
        )
    }
    if (showExpiresPicker) {
        GlassDateTimePickerDialog(
            initialMillis = expiresMillis,
            onDismissRequest = { showExpiresPicker = false },
            onConfirm = { millis ->
                expiresMillis = millis
                expires = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date(millis))
                showExpiresPicker = false
            }
        )
    }
}

@Composable
private fun StatisticsBarChart(values: List<StatisticsTrendBucket>) {
    val chartValues = values.filter { it.total > 0.0 }
    if (chartValues.isEmpty()) {
        Text("暂无花费记录", color = TextTertiary, fontSize = 13.sp)
        return
    }
    var chartWidth by remember { mutableStateOf(0) }
    var selectedIndex by rememberSaveable(chartValues) { mutableStateOf<Int?>(null) }
    Column(Modifier.fillMaxWidth()) {
    Box(Modifier.fillMaxWidth()) {
    // 放大镜采样源与指针位置（图表 126dp）
    val chartBackdrop = rememberLayerBackdrop()
    var dragPosition by remember { mutableStateOf<Offset?>(null) }
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(126.dp)
            .onSizeChanged { chartWidth = it.width }
            .pointerInput(chartValues, chartWidth) {
                detectTapGestures(onTap = { selectedIndex = null })
            }
            .pointerInput(chartValues, chartWidth) {
                fun selectAt(x: Float) {
                    if (chartWidth <= 0) return
                    val plotLeft = 43.dp.toPx()
                    val plotWidth = (chartWidth - plotLeft).coerceAtLeast(1f)
                    selectedIndex = (((x - plotLeft).coerceIn(0f, plotWidth) / plotWidth) * chartValues.lastIndex)
                        .roundToInt()
                        .coerceIn(0, chartValues.lastIndex)
                }
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        dragPosition = it
                        selectAt(it.x)
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        dragPosition = change.position
                        selectAt(change.position.x)
                    },
                    onDragEnd = { dragPosition = null },
                    onDragCancel = { dragPosition = null }
                )
            }
    ) {
        val maxValue = chartValues.maxOf { it.total }
        val plotLeft = 43.dp.toPx()
        val plotTop = 10.dp.toPx()
        val plotBottom = size.height - 2.dp.toPx()
        val plotHeight = plotBottom - plotTop
        val plotWidth = size.width - plotLeft
        val gap = when {
            chartValues.size <= 35 -> 3.dp.toPx()
            chartValues.size <= 100 -> 1.dp.toPx()
            chartValues.size <= 370 -> 0.35.dp.toPx()
            else -> 0f
        }
        val barWidth = ((plotWidth - gap * (chartValues.size - 1)) / chartValues.size.coerceAtLeast(1)).coerceAtLeast(0.15f)
        val labelPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.rgb(120, 128, 138)
            textSize = 9.dp.toPx()
            isAntiAlias = true
        }
        drawContext.canvas.nativeCanvas.apply {
            drawText(formatMoney(maxValue), 0f, plotTop + labelPaint.textSize * 0.35f, labelPaint)
            drawText(formatMoney(maxValue / 2.0), 0f, plotTop + plotHeight / 2f + labelPaint.textSize * 0.35f, labelPaint)
            drawText("0", 0f, plotBottom, labelPaint)
        }
        drawLine(
            color = TextTertiary.copy(alpha = 0.55f),
            start = Offset(plotLeft, plotTop),
            end = Offset(size.width, plotTop),
            strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))
        )
        chartValues.forEachIndexed { index, bucket ->
            var bottom = plotBottom
            val segments = listOf(
                bucket.energy to PrimaryBlue,
                bucket.periodic to Success,
                bucket.insurance to Color(0xFF7C5CE7),
                bucket.repair to Warning,
                bucket.roadFee to Color(0xFF0F9F6E)
            )
            val topSegmentIndex = segments.indexOfLast { it.first > 0.0 }
            segments.forEachIndexed { segmentIndex, (value, color) ->
                if (value > 0.0) {
                    val height = (value / maxValue).toFloat() * plotHeight
                    val left = plotLeft + index * (barWidth + gap)
                    val top = bottom - height
                    if (segmentIndex == topSegmentIndex) {
                        val radius = minOf(barWidth / 2f, height / 2f, 4.dp.toPx())
                        drawRoundRect(
                            color = color.copy(alpha = 0.82f),
                            topLeft = Offset(left, top),
                            size = Size(barWidth, height),
                            cornerRadius = CornerRadius(radius, radius)
                        )
                        if (height > radius) {
                            drawRect(
                                color = color.copy(alpha = 0.82f),
                                topLeft = Offset(left, top + radius),
                                size = Size(barWidth, height - radius)
                            )
                        }
                    } else {
                        drawRect(
                            color = color.copy(alpha = 0.82f),
                            topLeft = Offset(left, top),
                            size = Size(barWidth, height)
                        )
                    }
                    bottom -= height
                }
            }
        }
    }
    selectedIndex?.let { index ->
        val bucket = chartValues[index]
        CardBlock(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 8.dp)
                .clip(GlassTokens.shapeCard)
                .glassClickable { selectedIndex = null },
            level = GlassLevel.L2_Panel,
            contentPadding = 9.dp
        ) {
            Text(SimpleDateFormat("yyyy/MM/dd", Locale.CHINA).format(Date(bucket.dayStart)), color = TextSecondary, fontSize = 11.sp)
            Text("总计 ¥${formatMoney(bucket.total)}", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Black)
            listOf(
                Triple("补能", bucket.energy, PrimaryBlue),
                Triple("保养", bucket.periodic, Success),
                Triple("保险", bucket.insurance, Color(0xFF7C5CE7)),
                Triple("检修", bucket.repair, Warning),
                Triple("ETC", bucket.roadFee, Color(0xFF0F9F6E))
            ).filter { it.second > 0.0 }.forEach { (label, value, color) ->
                Text("$label ¥${formatMoney(value)}", color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
    }
    Row(Modifier.fillMaxWidth()) {
        val formatter = SimpleDateFormat("yyyy/MM/dd", Locale.CHINA)
        Text(chartValues.firstOrNull()?.let { formatter.format(Date(it.dayStart)) }.orEmpty(), color = TextTertiary, fontSize = 9.sp, modifier = Modifier.weight(1f))
        Text(chartValues.lastOrNull()?.let { formatter.format(Date(it.dayStart)) }.orEmpty(), color = TextTertiary, fontSize = 9.sp, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
    }
    }
}

@Composable
private fun StatisticsEnergyChart(values: List<StatisticsEnergyPoint>, enabled: Set<String>, drawProgress: Map<String, Float>) {
    if (values.isEmpty()) {
        Text("暂无有效满油或满电结算区间", color = TextTertiary, fontSize = 13.sp)
        return
    }
    val colors = mapOf("mileage" to Color(0xFF00A6A6), "fuel" to Warning, "charge" to PrimaryBlue, "fuelEconomy" to Success, "chargeEconomy" to Color(0xFF7C5CE7))
    var chartWidth by remember { mutableStateOf(0) }
    var selectedIndex by rememberSaveable(values) { mutableStateOf<Int?>(null) }
    Box(Modifier.fillMaxWidth()) {
    // 放大镜采样源与指针位置（图表 155dp）
    val chartBackdrop = rememberLayerBackdrop()
    var dragPosition by remember { mutableStateOf<Offset?>(null) }
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(155.dp)
            .padding(top = 10.dp)
            .onSizeChanged { chartWidth = it.width }
            .pointerInput(values, chartWidth) {
                detectTapGestures(onTap = { selectedIndex = null })
            }
            .pointerInput(values, chartWidth) {
                fun selectAt(x: Float) {
                    if (chartWidth > 0) {
                        selectedIndex = ((x.coerceIn(0f, chartWidth.toFloat()) / chartWidth) * values.lastIndex)
                            .roundToInt()
                            .coerceIn(0, values.lastIndex)
                    }
                }
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        dragPosition = it
                        selectAt(it.x)
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        dragPosition = change.position
                        selectAt(change.position.x)
                    },
                    onDragEnd = { dragPosition = null },
                    onDragCancel = { dragPosition = null }
                )
            }
    ) {
        fun raw(key: String, p: StatisticsEnergyPoint) = when (key) {
            "mileage" -> p.mileage; "fuel" -> p.fuel; "charge" -> p.charge; "fuelEconomy" -> p.fuelEconomy ?: Double.NaN; else -> p.chargeEconomy ?: Double.NaN
        }
        val cumulative = HashMap<String, DoubleArray>()
        for (cumulativeKey in listOf("fuel", "charge")) {
            val sums = DoubleArray(values.size)
            var running = 0.0
            values.forEachIndexed { index, point ->
                val v = if (cumulativeKey == "fuel") point.fuel else point.charge
                if (v.isFinite()) running += v
                sums[index] = running
            }
            cumulative[cumulativeKey] = sums
        }
        val maxDrawnPoints = (size.width / 6.dp.toPx()).toInt().coerceIn(24, 240)
        val sampleStep = if (values.size > maxDrawnPoints) {
            (values.size + maxDrawnPoints - 1) / maxDrawnPoints
        } else 1
        val sampledIndices = if (sampleStep <= 1) {
            values.indices.toList()
        } else {
            (values.indices step sampleStep).toList().let { if (it.last() != values.lastIndex) it + values.lastIndex else it }
        }
        val active = enabled.filter { key -> values.any { raw(key, it).isFinite() } }
        active.forEach { key ->
            val progress = drawProgress[key] ?: 1f
            if (progress <= 0.01f) return@forEach
            val points = sampledIndices.mapNotNull { index ->
                val point = values[index]
                val value = if (key == "fuel" || key == "charge") {
                    cumulative[key]!![index].takeIf { raw(key, point).isFinite() }
                } else {
                    raw(key, point).takeIf(Double::isFinite)
                }
                value?.let { index to it }
            }
            val max = points.maxOfOrNull { it.second }?.takeIf { it > 0.0 } ?: 1.0
            val offsets = points.map { (index, value) ->
                Offset(if (values.size == 1) size.width / 2 else index.toFloat() / (values.size - 1) * size.width, size.height - (value / max * size.height).toFloat())
            }
            if (offsets.size >= 2) {
                val path = Path().apply {
                    moveTo(offsets.first().x, offsets.first().y)
                    for (index in 0 until offsets.lastIndex) {
                        val current = offsets[index]
                        val next = offsets[index + 1]
                        val dx = next.x - current.x
                        cubicTo(
                            current.x + dx * 0.45f,
                            current.y,
                            next.x - dx * 0.45f,
                            next.y,
                            next.x,
                            next.y
                        )
                    }
                }
                val leftToRight = key == "mileage" || key == "fuel" || key == "fuelEconomy"
                val clipLeft = if (leftToRight) 0f else size.width * (1f - progress)
                val clipRight = if (leftToRight) size.width * progress else size.width
                drawContext.canvas.save()
                drawContext.canvas.clipRect(clipLeft, 0f, clipRight, size.height)
                drawPath(path, colors.getValue(key), style = androidx.compose.ui.graphics.drawscope.Stroke(2.5.dp.toPx()))
                drawContext.canvas.restore()
            }
        }
    }
    selectedIndex?.let { index ->
        val point = values[index]
        CardBlock(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 12.dp)
                .clip(GlassTokens.shapeCard)
                .glassClickable { selectedIndex = null },
            level = GlassLevel.L2_Panel,
            contentPadding = 10.dp
        ) {
            Text(SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.CHINA).format(Date(point.time)), color = TextSecondary, fontSize = 11.sp)
            enabled.forEach { key ->
                trendMetricText(key, point)?.let { value -> Text(value, color = colors.getValue(key), fontSize = 12.sp, fontWeight = FontWeight.Bold) }
            }
        }
    }
    }
    Text("按已结算的满油/满电记录生成；油量/电量为累计曲线", color = TextTertiary, fontSize = 11.sp)
}

private fun trendMetricText(key: String, point: StatisticsEnergyPoint): String? = when (key) {
    "mileage" -> "里程 ${formatDecimal(point.mileage)} km"
    "fuel" -> point.fuel.takeIf { it > 0.0 }?.let { "本次加油 ${formatDecimal(it)} L" }
    "charge" -> point.charge.takeIf { it > 0.0 }?.let { "本次充电 ${formatDecimal(it)} kWh" }
    "fuelEconomy" -> point.fuelEconomy?.let { "油耗 ${formatDecimal(it)} L/100km" }
    "chargeEconomy" -> point.chargeEconomy?.let { "电耗 ${formatDecimal(it)} kWh/100km" }
    else -> null
}

private fun calculateStatistics(state: LedgerUiState, allVehicles: Boolean, period: StatisticsPeriod): StatisticsResult {
    val now = System.currentTimeMillis()
    val calendar = Calendar.getInstance().apply { timeInMillis = now; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
    val vehicleIds = if (allVehicles) state.vehicles.mapTo(mutableSetOf()) { it.id }
        else setOfNotNull(state.selectedVehicle?.id)
    val fixedStart = when (period) {
        StatisticsPeriod.Month -> calendar.apply { set(Calendar.DAY_OF_MONTH, 1) }.timeInMillis
        StatisticsPeriod.ThreeMonths -> calendar.apply { set(Calendar.DAY_OF_MONTH, 1); add(Calendar.MONTH, -2) }.timeInMillis
        StatisticsPeriod.Year -> calendar.apply { set(Calendar.DAY_OF_MONTH, 1); add(Calendar.MONTH, -11) }.timeInMillis
        StatisticsPeriod.All -> 0L
    }
    val earliestDataAt = (state.mileageRecords.filter { it.vehicleId in vehicleIds }.map { it.createdAt } +
        state.energyRecords.filter { it.vehicleId in vehicleIds }.map { it.createdAt } +
        state.maintenanceHistory.filter { it.vehicleId in vehicleIds }.map { it.serviceAt }).minOrNull() ?: now
    val start = if (period == StatisticsPeriod.All) Calendar.getInstance().apply {
        timeInMillis = earliestDataAt
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis else fixedStart
    val energy = state.energyRecords.filter { it.vehicleId in vehicleIds && it.createdAt in start..now }
    val mileageRecords = state.mileageRecords.filter { it.vehicleId in vehicleIds && it.createdAt in start..now }
    val itemTypeById = state.maintenance.associate { it.item.id to it.item.itemType }
    val histories = state.maintenanceHistory.filter { it.vehicleId in vehicleIds && it.serviceAt in start..now }
    val validSettlements = state.energySettlements.filter {
        it.vehicleId in vehicleIds && it.status == com.cheku.app.data.SettlementStatus.Valid &&
            it.createdAt in start..now && (it.distanceKm ?: 0.0) > 0.0
    }
    val points = (state.mileageRecords.map { Triple(it.vehicleId, it.createdAt, it.mileage) } +
        state.energyRecords.map { Triple(it.vehicleId, it.createdAt, it.mileage) }).filter { it.first in vehicleIds }
    var incompleteBoundary = false
    val measuredDistance = vehicleIds.sumOf { vehicleId ->
        val vehiclePoints = points.filter { it.first == vehicleId }.sortedBy { it.second }
        val endPoint = vehiclePoints.lastOrNull { it.second <= now }?.third
        val beforeStart = vehiclePoints.lastOrNull { it.second < start }?.third
        val firstInside = vehiclePoints.firstOrNull { it.second >= start }?.third
        if (beforeStart == null && period != StatisticsPeriod.All && firstInside != null) incompleteBoundary = true
        ((endPoint ?: 0.0) - (beforeStart ?: firstInside ?: endPoint ?: 0.0)).coerceAtLeast(0.0)
    }
    val settlementDistance = validSettlements.sumOf { (it.distanceKm ?: 0.0).coerceAtLeast(0.0) }
    val distance = when {
        measuredDistance > 0.0 -> measuredDistance
        settlementDistance > 0.0 -> settlementDistance
        period == StatisticsPeriod.All -> state.vehicles.filter { it.id in vehicleIds }.sumOf { it.currentMileage.coerceAtLeast(0.0) }
        else -> 0.0
    }
    val periodicCost = histories.filter { itemTypeById[it.itemId] == MaintenanceItemType.Periodic }.sumOf { it.partsCost + it.laborCost }
    val insuranceCost = histories.filter { itemTypeById[it.itemId]?.isInsurance == true }.sumOf { it.insuranceAmount }
    val repairCost = histories.filter { itemTypeById[it.itemId] == MaintenanceItemType.RepairInspection }.sumOf { it.repairAmount + it.laborCost }
    val roadFeeCost = mileageRecords.sumOf { it.roadFeeAmount }
    val energyById = state.energyRecords.associateBy { it.id }
    val rawEnergyTrend = validSettlements.sortedBy { it.createdAt }.map { settlement ->
        val record = settlement.endRecordId?.let(energyById::get)
        StatisticsEnergyPoint(
            time = settlement.createdAt,
            fuel = if (settlement.type == EnergyRecordType.Fuel) settlement.totalAmount else 0.0,
            charge = if (settlement.type == EnergyRecordType.Charge) settlement.totalAmount else 0.0,
            mileage = record?.mileage ?: settlement.endMileage ?: settlement.startMileage,
            distanceKm = settlement.distanceKm ?: 0.0,
            fuelEconomy = settlement.consumptionPer100Km.takeIf { settlement.type == EnergyRecordType.Fuel },
            chargeEconomy = settlement.consumptionPer100Km.takeIf { settlement.type == EnergyRecordType.Charge }
        )
    }
    val energyTrend = if (allVehicles) {
        var totalDistance = 0.0
        rawEnergyTrend.map { point ->
            totalDistance += point.distanceKm.coerceAtLeast(0.0)
            point.copy(mileage = totalDistance)
        }
    } else rawEnergyTrend
    fun consumption(type: EnergyRecordType): Double? {
        val matches = validSettlements.filter { it.type == type }
        val km = matches.sumOf { it.distanceKm ?: 0.0 }
        return if (km > 0.0) matches.sumOf { it.totalAmount } / km * 100.0 else null
    }
    val dayStarts = buildList {
        val day = Calendar.getInstance().apply { timeInMillis = start }
        while (day.timeInMillis <= now) {
            add(day.timeInMillis)
            day.add(Calendar.DAY_OF_MONTH, 1)
        }
    }
    val trend = dayStarts.map { StatisticsTrendBucket(it) }
    fun dayIndex(time: Long): Int = dayStarts.binarySearch(time).let { found ->
        if (found >= 0) found else (-found - 2).coerceIn(0, trend.lastIndex)
    }
    energy.forEach { trend[dayIndex(it.createdAt)].energy += it.paidAmount }
    mileageRecords.forEach { trend[dayIndex(it.createdAt)].roadFee += it.roadFeeAmount }
    histories.forEach {
        val cost = when (itemTypeById[it.itemId]) {
            MaintenanceItemType.CommercialInsurance, MaintenanceItemType.CompulsoryInsurance -> it.insuranceAmount
            MaintenanceItemType.RepairInspection -> it.repairAmount + it.laborCost
            else -> it.partsCost + it.laborCost
        }
        val bucket = trend[dayIndex(it.serviceAt)]
        when (itemTypeById[it.itemId]) {
            MaintenanceItemType.CommercialInsurance, MaintenanceItemType.CompulsoryInsurance -> bucket.insurance += cost
            MaintenanceItemType.RepairInspection -> bucket.repair += cost
            else -> bucket.periodic += cost
        }
    }
    val warnings = buildList {
        if (vehicleIds.isEmpty()) add("请先添加并选择车辆")
        if (incompleteBoundary) add("周期开始前缺少里程记录，行驶里程按周期内首条记录估算")
        if (validSettlements.isEmpty()) add("暂无有效满油/满电结算区间，平均能耗暂不可计算")
        if (state.vehicles.any { it.id in vehicleIds && it.manufacturingDate == null }) add("部分车辆未填写出厂日期，保险周期使用车辆创建日期")
    }
    val vehicleNames = state.vehicles.associate { it.id to it.name }
    val expenses = buildList {
        energy.forEach { record ->
            add(StatisticsExpense(
                StatisticsExpenseCategory.Energy, record.id, vehicleNames[record.vehicleId].orEmpty(),
                if (record.type == EnergyRecordType.Fuel) "加油 ${formatDecimal(record.amount)} L" else "充电 ${formatDecimal(record.amount)} kWh",
                record.createdAt, record.paidAmount, energy = record
            ))
        }
        histories.forEach { history ->
            val type = itemTypeById[history.itemId] ?: MaintenanceItemType.Periodic
            val category = when {
                type.isInsurance -> StatisticsExpenseCategory.Insurance
                type == MaintenanceItemType.RepairInspection -> StatisticsExpenseCategory.Repair
                else -> StatisticsExpenseCategory.Periodic
            }
            val cost = when (category) {
                StatisticsExpenseCategory.Insurance -> history.insuranceAmount
                StatisticsExpenseCategory.Repair -> history.repairAmount + history.laborCost
                else -> history.partsCost + history.laborCost
            }
            if (cost > 0.0) add(StatisticsExpense(
                category, history.id, vehicleNames[history.vehicleId].orEmpty(),
                if (category == StatisticsExpenseCategory.Repair) history.recordName else history.itemName,
                history.serviceAt, cost, history = history, itemType = type
            ))
        }
    }
    return StatisticsResult(
        distance,
        energy.filter { it.type == EnergyRecordType.Fuel }.sumOf { it.amount },
        energy.filter { it.type == EnergyRecordType.Charge }.sumOf { it.amount },
        energy.sumOf { it.paidAmount }, periodicCost, insuranceCost, repairCost, roadFeeCost,
        consumption(EnergyRecordType.Fuel), consumption(EnergyRecordType.Charge), trend, energyTrend, expenses, warnings)
}

@Composable
private fun rememberDialogChrome(onExited: () -> Unit): DialogChrome {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    return DialogChrome(shown = shown, dismiss = { shown = false }, onExited = onExited)
}

private class DialogChrome(
    val shown: Boolean,
    val dismiss: () -> Unit,
    val onExited: () -> Unit
)

@Composable
private fun UpdateDialog(
    update: AppUpdateInfo,
    apkBytes: Long?,
    apkProbing: Boolean,
    download: UpdateDownloadState,
    onProbeSize: () -> Unit,
    onDownload: () -> Unit,
    onInstall: () -> Boolean,
    onDismiss: () -> Unit
) {
    BackHandler(enabled = update.forceUpdate) {}
    val chrome = rememberDialogChrome(onDismiss)
    // 弹窗出现就探一次安装包体积（同一版本只探一次，见 ViewModel.probeUpdatePackageSize）
    LaunchedEffect(update.version, update.downloadUrl) { onProbeSize() }
    Dialog(
        onDismissRequest = {
            if (!update.forceUpdate) chrome.dismiss()
        },
        properties = DialogProperties(
            dismissOnBackPress = !update.forceUpdate,
            dismissOnClickOutside = !update.forceUpdate,
            usePlatformDefaultWidth = false
        )
    ) {
        GlassDialogMotionHost(shown = chrome.shown, onExited = chrome.onExited) {
        GlassFloatingSurface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .widthIn(max = 520.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 620.dp)          // 上限：短屏/超长说明都不会溢出屏幕
                    .padding(horizontal = 20.dp)
                    .padding(top = 22.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "检测到新版本",
                            color = TextPrimary,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            if (update.forceUpdate) "请更新后继续使用" else "新版本已发布，可直接在应用内更新",
                            color = TextPrimary.copy(alpha = 0.68f),
                            fontSize = 15.sp
                        )
                    }
                    if (update.forceUpdate) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Error.copy(alpha = 0.12f))
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text("强制更新", color = Error, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    UpdateInfoRow("新版本", update.version, valueColor = PrimaryBlue, emphasize = true)
                    UpdateInfoRow(
                        "安装包大小",
                        when {
                            apkBytes != null && apkBytes > 0L -> formatBackupSize(apkBytes)
                            apkProbing -> "读取中…"
                            else -> "未知"
                        }
                    )
                    UpdateInfoRow("当前版本", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                    UpdateInfoRow("发布时间", formatReleaseDay(update.publishedAt))
                }

                Text("更新说明", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        update.notes.ifBlank { "暂无更新说明" },
                        color = TextPrimary.copy(alpha = 0.68f),
                        fontSize = 15.sp,
                        lineHeight = 22.sp
                    )
                }

                when (download) {
                    is UpdateDownloadState.Downloading -> {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "正在下载更新包",
                                    color = TextSecondary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    download.fraction?.let { "${(it * 100f).roundToInt()}%  ·  " } ?: "",
                                    color = TextPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    if (download.totalBytes != null && download.totalBytes > 0L) {
                                        "${formatBackupSize(download.doneBytes)} / ${formatBackupSize(download.totalBytes)}"
                                    } else {
                                        formatBackupSize(download.doneBytes)
                                    },
                                    color = TextPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            // 拿不到总长度时（对端没给 Content-Length）用不确定态：不给假百分比
                            TransferBarTrack(fraction = download.fraction ?: 0f, height = 6.dp)
                        }
                    }

                    is UpdateDownloadState.Ready -> Text(
                        "安装包已下载完成（${formatBackupSize(download.bytes)}），正在打开系统安装程序。",
                        color = Success,
                        fontSize = 12.sp
                    )

                    is UpdateDownloadState.Failed -> Text(
                        "下载失败：${download.message}。可再点一次重试。",
                        color = Error,
                        fontSize = 12.sp
                    )

                    UpdateDownloadState.Idle -> Unit
                }

                val downloading = download is UpdateDownloadState.Downloading
                val ready = download is UpdateDownloadState.Ready
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(GlassTokens.shapeCapsule)
                            .background(if (downloading) PrimaryBlue.copy(alpha = 0.5f) else PrimaryBlue)
                            .glassClickable(enabled = !downloading) {
                                if (ready) {
                                    if (!onInstall()) onDownload()
                                } else {
                                    onDownload()
                                }
                            }
                            .height(48.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            when {
                                downloading -> "下载中…"
                                ready -> "安装更新"
                                else -> "下载并安装更新"
                            },
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (!update.forceUpdate) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clip(GlassTokens.shapeCapsule)
                                .background(GlassTokens.l3Fill.copy(alpha = 0.55f))
                                .glassClickable(onClick = onDismiss)
                                .height(48.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("稍后更新", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            }
        }
    }
}

@Composable
private fun UpdateInfoRow(
    label: String,
    value: String,
    valueColor: Color = TextPrimary,
    emphasize: Boolean = false
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            color = TextPrimary.copy(alpha = 0.68f),
            fontSize = 15.sp,
            modifier = Modifier.weight(1f)
        )
        Text(
            value,
            color = valueColor,
            fontSize = if (emphasize) 17.sp else 15.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/** 公告弹窗 */
@Composable
private fun AnnouncementDialog(notice: LedgerViewModel.Notice, onDismiss: () -> Unit) {
    GlassDialog(
        onDismissRequest = onDismiss,
        title = notice.title.ifBlank { "公告" },
        dismissible = false,
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    notice.body,
                    color = TextSecondary,
                    fontSize = 15.sp,
                    lineHeight = 23.sp
                )
            }
        },
        confirmButton = {
            GlassDialogAction(onClick = onDismiss) {
                Text("确定", color = PrimaryBlue, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {}
    )
}

private fun Modifier.meshBackground(): Modifier = this.background(PageBackground)

@Composable
private fun CustomBackground(uriText: String, blurProvider: () -> Float) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val source = remember(uriText) {
        runCatching { BackgroundBlur.decodeCapped(context, Uri.parse(uriText)) }.getOrNull()
    }
    val renderer = remember { BackgroundBlur.LiveBlurRenderer() }
    val frame = remember { mutableStateOf<ImageBitmap?>(null, androidx.compose.runtime.neverEqualPolicy()) }
    val screenWidthPx = with(density) { LocalConfiguration.current.screenWidthDp.dp.toPx().roundToInt() }
    LaunchedEffect(source, screenWidthPx) {
        val src = source ?: run {
            android.util.Log.w(
                "ChekuBackground",
                "decode failed, nothing to draw. uri=${uriText.take(64)}"
            )
            return@LaunchedEffect
        }
        snapshotFlow { with(density) { blurProvider().dp.toPx() } }
            // 亚像素抖动不重算（视觉上无差别，能省掉近一半的渲染次数）
            .distinctUntilChanged { a, b -> kotlin.math.abs(a - b) < 1.5f }
            .conflate()
            .collect { radiusPx ->
                val bitmap = if (radiusPx < 1f) {
                    src
                } else {
                    withContext(Dispatchers.Default) { renderer.render(src, radiusPx, screenWidthPx) } ?: src
                }
                frame.value = bitmap.asImageBitmap()
            }
    }
    frame.value?.let {
        Image(
            bitmap = it,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun AnimatedMeshBackground() {
    val transition = rememberInfiniteTransition(label = "ambient-gradient")
    val driftA by transition.animateFloat(
        initialValue = -0.28f,
        targetValue = 0.34f,
        animationSpec = infiniteRepeatable(tween(1_840, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "gradient-a"
    )
    val driftB by transition.animateFloat(
        initialValue = 0.31f,
        targetValue = -0.30f,
        animationSpec = infiniteRepeatable(tween(2_340, easing = LinearEasing), RepeatMode.Reverse),
        label = "gradient-b"
    )
    val driftC by transition.animateFloat(
        initialValue = -0.25f,
        targetValue = 0.29f,
        animationSpec = infiniteRepeatable(tween(2_860, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "gradient-c"
    )
    val driftD by transition.animateFloat(
        initialValue = 0.22f,
        targetValue = -0.33f,
        animationSpec = infiniteRepeatable(tween(2_020, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "gradient-d"
    )
    val driftE by transition.animateFloat(
        initialValue = -0.19f,
        targetValue = 0.32f,
        animationSpec = infiniteRepeatable(tween(3_180, easing = LinearEasing), RepeatMode.Reverse),
        label = "gradient-e"
    )
    Canvas(Modifier.fillMaxSize()) {
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFFE4EEFA), Color(0xFFF1E8F8), Color(0xFFE4F3EF), Color(0xFFF8EAE4)),
                start = Offset.Zero,
                end = Offset(size.width, size.height)
            )
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0x785F9DE0), Color(0x406D9FD8), Color(0x126D9FD8), Color.Transparent),
                center = Offset(size.width * (0.12f + driftA), size.height * (0.16f + driftD * 0.72f)),
                radius = size.minDimension * 1.794f
            ),
            radius = size.minDimension * 1.794f,
            center = Offset(size.width * (0.12f + driftA), size.height * (0.16f + driftD * 0.72f))
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0x6AAE7FD8), Color(0x36B18BD6), Color(0x10B18BD6), Color.Transparent),
                center = Offset(size.width * (0.90f + driftB), size.height * (0.44f + driftE * 0.68f)),
                radius = size.minDimension * 1.716f
            ),
            radius = size.minDimension * 1.716f,
            center = Offset(size.width * (0.90f + driftB), size.height * (0.44f + driftE * 0.68f))
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0x6A6CCDB2), Color(0x3278CBB6), Color(0x0E78CBB6), Color.Transparent),
                center = Offset(size.width * (0.26f + driftC), size.height * (0.90f + driftA * 0.62f)),
                radius = size.minDimension * 1.872f
            ),
            radius = size.minDimension * 1.872f,
            center = Offset(size.width * (0.26f + driftC), size.height * (0.90f + driftA * 0.62f))
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0x64E7A18F), Color(0x32E6B08D), Color(0x0CE6B08D), Color.Transparent),
                center = Offset(size.width * (0.78f - driftD), size.height * (0.78f - driftB * 0.66f)),
                radius = size.minDimension * 1.521f
            ),
            radius = size.minDimension * 1.521f,
            center = Offset(size.width * (0.78f - driftD), size.height * (0.78f - driftB * 0.66f))
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0x506DCFD5), Color(0x206DCFD5), Color.Transparent),
                center = Offset(size.width * (0.08f - driftE * 0.82f), size.height * (0.58f + driftC * 0.74f)),
                radius = size.minDimension * 1.014f
            ),
            radius = size.minDimension * 1.014f,
            center = Offset(size.width * (0.08f - driftE * 0.82f), size.height * (0.58f + driftC * 0.74f))
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0x48E8D78D), Color(0x18E8D78D), Color.Transparent),
                center = Offset(size.width * (0.72f + driftA * 0.78f), size.height * (0.20f - driftD * 0.70f)),
                radius = size.minDimension * 0.897f
            ),
            radius = size.minDimension * 0.897f,
            center = Offset(size.width * (0.72f + driftA * 0.78f), size.height * (0.20f - driftD * 0.70f))
        )
        // The mesh used to stay pastel in dark mode because every gradient stop above was
        // hard-coded for the light palette. This animated veil uses the same theme level as
        // the rest of the UI, so light <-> dark is one continuous colour transition.
        drawRect(Color(0xFF0C121B).copy(alpha = AppDarkLevel * 0.86f))
    }
}

private const val DOCK_PRESS_PREVIEW = false

@Composable
private fun BrandTopBar(tab: AppTab) {
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(90.dp)
    ) {
        val topBarBackdrop = LocalGlassBackdrops.current?.background
        val topBarSpec = GlassTokens.specFor(GlassLevel.L3_Dialog)
        val topBarFill = GlassTokens.l3Fill
        val topBarStrokeHairline = GlassTokens.strokeHairline
        Box(
            modifier = Modifier
                .requiredWidth(screenWidth + 48.dp)
                .align(Alignment.TopCenter)
                .height(102.dp)
                .offset(y = (-12).dp)
                .background(PageBackground)
                // 消费共享背景捕获（不再是每块自己 setupWith 的 BlurView）。
                .drawBackdrop(
                    backdrop = topBarBackdrop ?: emptyBackdrop(),
                    shape = { androidx.compose.ui.graphics.RectangleShape },
                    effects = {
                        if (topBarBackdrop != null) {
                            vibrancy()
                            blur(topBarSpec.blurRadiusDp.dp.toPx())
                        }
                    },
                    highlight = { glassHighlightOf(topBarSpec, 0f) },
                    shadow = null,
                    onDrawSurface = { drawRect(topBarFill) }
                )
                // 均匀洗色 + 底部 1px 分隔线。
                .drawWithContent {
                    drawContent()
                    drawLine(
                        color = topBarStrokeHairline,
                        start = Offset(0f, size.height - 1f),
                        end = Offset(size.width, size.height - 1f),
                        strokeWidth = 1f
                    )
                }
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .offset(y = (-1).dp)
                .padding(horizontal = 20.dp, vertical = 0.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Crossfade(
                targetState = tab,
                animationSpec = tween(NAV_FADE_IN_MS, easing = FastOutSlowInEasing),
                label = "topbar-title"
            ) { shownTab ->
            Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(shownTab.icon, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(21.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                shownTab.title,
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
                color = TextPrimary,
                modifier = Modifier.offset(y = (-1).dp)
            )
            }
            }
        }
    }
}

@Composable
private fun FloatingDock(
    selectedTab: AppTab,
    onSelectTab: (AppTab) -> Unit,
    backdrop: LayerBackdrop,
    modifier: Modifier = Modifier
) {
    LocalConfiguration.current // Recompose when MIUI switches freeform/fullscreen bounds.
    val bottomSpacing = dockBottomMargin()
    val tabs = AppTab.entries

    val selectedTabState = rememberUpdatedState(selectedTab)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 24.dp, end = 24.dp, bottom = bottomSpacing)
    ) {
        LiquidBottomTabs(
            selectedTabIndex = { selectedTabState.value.ordinal },
            onTabSelected = { onSelectTab(tabs[it]) },
            backdrop = backdrop,
            tabsCount = tabs.size,
            modifier = Modifier.fillMaxWidth()
        ) {
            tabs.forEach { item ->
                LiquidBottomTab(onClick = { onSelectTab(item) }) {
                    Icon(
                        item.icon,
                        contentDescription = item.title,
                        modifier = Modifier.size(28.75.dp),
                        tint = if (selectedTab == item) PrimaryBlue else TextSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun Modifier.glassClickable(
    enabled: Boolean = true,
    onClick: () -> Unit
): Modifier = glassPressFeedback(enabled = enabled, onClick = onClick)

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.glassCombinedClickable(
    enabled: Boolean = true,
    onClick: () -> Unit,
    onLongClick: () -> Unit
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.985f else 1f,
        animationSpec = tween(if (pressed) 90 else 180, easing = FastOutSlowInEasing),
        label = "glass-combined-press-scale"
    )
    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
            alpha = if (pressed) 0.92f else if (enabled) 1f else 0.55f
        }
        .combinedClickable(
            enabled = enabled,
            interactionSource = interactionSource,
            indication = LocalIndication.current,
            onClick = onClick,
            onLongClick = onLongClick
        )
}

@Composable
private fun GlassFloatingSurface(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = GlassTokens.shapeDialog,
    content: @Composable BoxScope.() -> Unit
) {
    val backdrop = LocalGlassBackdrops.current?.background
    val spec = GlassTokens.specFor(GlassLevel.L3_Dialog)
    val fill = spec.fill()
    val isLightTheme = !isSystemInDarkTheme()
    Box(
        modifier = modifier.drawBackdrop(
            backdrop = backdrop ?: emptyBackdrop(),
            shape = { shape },
            effects = {
                if (backdrop != null) {
                    vibrancy()
                    colorControls(
                        brightness = if (isLightTheme) 0.2f else 0f,
                        saturation = 1.5f
                    )
                    blur((if (isLightTheme) spec.blurRadiusDp else spec.blurRadiusDp / 2f).dp.toPx())
                    applyGlassLens(spec, 0f)
                }
            },
            highlight = { Highlight.Plain },
            shadow = { glassShadowOf(spec, 0f) },
            innerShadow = { glassInnerShadowOf(spec, 0f) },
            onDrawSurface = { drawRect(fill) }
        )
    ) {
        content()
    }
}

/** 弹层与内联动作键 */
@Composable
private fun GlassDialogAction(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accent: Color? = null,
    content: @Composable RowScope.() -> Unit
) {
    LiquidGlassDialogButton(
        onClick = { if (enabled) onClick() },
        backdrop = rememberLayerAwareBackdrop(),
        modifier = if (enabled) modifier else modifier.graphicsLayer { alpha = 0.42f },
        surfaceColor = accent ?: Color.Unspecified,
        content = content
    )
}

@Composable
private fun GlassDialog(
    onDismissRequest: () -> Unit,
    title: String,
    text: @Composable ColumnScope.() -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable () -> Unit,
    dismissible: Boolean = true
) {
    LiquidGlassDialog(
        onDismissRequest = onDismissRequest,
        title = title,
        text = text,
        confirmButton = confirmButton,
        dismissButton = dismissButton,
        dismissible = dismissible
    )
}

@Composable
private fun GlassDatePickerDialog(
    initialMillis: Long,
    onDismissRequest: () -> Unit,
    onConfirm: (Long) -> Unit
) = GlassCalendarPickerDialog(initialMillis, false, onDismissRequest, onConfirm)

@Composable
private fun GlassDateTimePickerDialog(
    initialMillis: Long,
    onDismissRequest: () -> Unit,
    onConfirm: (Long) -> Unit
) = GlassCalendarPickerDialog(initialMillis, true, onDismissRequest, onConfirm)

@Composable
private fun GlassCalendarPickerDialog(
    initialMillis: Long,
    includeTime: Boolean,
    onDismissRequest: () -> Unit,
    onConfirm: (Long) -> Unit
) {
    val initial = remember(initialMillis) { Calendar.getInstance().apply { timeInMillis = initialMillis } }
    var displayedYear by rememberSaveable(initialMillis) { mutableStateOf(initial.get(Calendar.YEAR)) }
    var displayedMonth by rememberSaveable(initialMillis) { mutableStateOf(initial.get(Calendar.MONTH)) }
    var selectedYear by rememberSaveable(initialMillis) { mutableStateOf(initial.get(Calendar.YEAR)) }
    var selectedMonth by rememberSaveable(initialMillis) { mutableStateOf(initial.get(Calendar.MONTH)) }
    var selectedDay by rememberSaveable(initialMillis) { mutableStateOf(initial.get(Calendar.DAY_OF_MONTH)) }
    var selectedHour by rememberSaveable(initialMillis) { mutableStateOf(initial.get(Calendar.HOUR_OF_DAY)) }
    var selectedMinute by rememberSaveable(initialMillis) { mutableStateOf(initial.get(Calendar.MINUTE)) }
    var selectingMonth by rememberSaveable { mutableStateOf(false) }

    fun changeMonth(delta: Int) {
        val calendar = Calendar.getInstance().apply {
            clear()
            set(displayedYear, displayedMonth, 1)
            add(Calendar.MONTH, delta)
        }
        displayedYear = calendar.get(Calendar.YEAR)
        displayedMonth = calendar.get(Calendar.MONTH)
    }

    val chrome = rememberDialogChrome(onDismissRequest)
    Dialog(
        onDismissRequest = chrome.dismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center
        ) {
          GlassDialogMotionHost(shown = chrome.shown, onExited = chrome.onExited) {
            GlassFloatingSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 420.dp)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        if (includeTime) "选择日期与时间" else "选择日期",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        color = TextPrimary
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // 箭头用圆形玻璃按钮
                        GlassCircleIconButton(onClick = { changeMonth(-1) }) {
                            Text("‹", fontSize = 24.sp, color = PrimaryBlue)
                        }
                        // 顶部「年月」选择标签
                        GlassPillLabel(
                            shape = GlassTokens.shapeControl,
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp),
                            onClick = { selectingMonth = !selectingMonth }
                        ) {
                            Text(
                                "${displayedYear} 年 ${displayedMonth + 1} 月",
                                color = TextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        GlassCircleIconButton(onClick = { changeMonth(1) }) {
                            Text("›", fontSize = 24.sp, color = PrimaryBlue)
                        }
                    }

                    if (selectingMonth) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            GlassCircleIconButton(onClick = { displayedYear-- }) {
                                Text("‹", fontSize = 24.sp, color = PrimaryBlue)
                            }
                            Text(
                                "$displayedYear 年",
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.Center,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            GlassCircleIconButton(onClick = { displayedYear++ }) {
                                Text("›", fontSize = 24.sp, color = PrimaryBlue)
                            }
                        }
                        repeat(4) { row ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                repeat(3) { column ->
                                    val month = row * 3 + column
                                    val active = displayedMonth == month
                                    val pick: () -> Unit = {
                                        displayedMonth = month
                                        selectingMonth = false
                                    }
                                    if (active) {
                                        GlassPillLabel(
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(38.dp),
                                            onClick = pick
                                        ) {
                                            Text(
                                                "${month + 1} 月",
                                                color = PrimaryBlue,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(38.dp)
                                                .glassClickable(onClick = pick),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                "${month + 1} 月",
                                                color = TextSecondary,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        Row(Modifier.fillMaxWidth()) {
                            listOf("一", "二", "三", "四", "五", "六", "日").forEach { weekday ->
                                Text(
                                    weekday,
                                    modifier = Modifier.weight(1f),
                                    textAlign = TextAlign.Center,
                                    color = TextTertiary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        val monthCalendar = Calendar.getInstance().apply {
                            clear()
                            set(displayedYear, displayedMonth, 1)
                        }
                        val firstOffset = (monthCalendar.get(Calendar.DAY_OF_WEEK) + 5) % 7
                        val daysInMonth = monthCalendar.getActualMaximum(Calendar.DAY_OF_MONTH)
                        repeat(6) { week ->
                            Row(Modifier.fillMaxWidth()) {
                                repeat(7) { weekday ->
                                    val day = week * 7 + weekday - firstOffset + 1
                                    if (day in 1..daysInMonth) {
                                        val selected = selectedYear == displayedYear &&
                                            selectedMonth == displayedMonth &&
                                            selectedDay == day
                                        val pick: () -> Unit = {
                                            selectedYear = displayedYear
                                            selectedMonth = displayedMonth
                                            selectedDay = day
                                        }
                                        if (selected) {
                                            GlassPillLabel(
                                                shape = RoundedCornerShape(12.dp),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(38.dp)
                                                    .padding(2.dp),
                                                onClick = pick
                                            ) {
                                                Text(
                                                    day.toString(),
                                                    color = PrimaryBlue,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(38.dp)
                                                    .padding(2.dp)
                                                    .glassClickable(onClick = pick),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    day.toString(),
                                                    color = TextPrimary,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                        }
                                    } else {
                                        Spacer(Modifier.weight(1f).height(38.dp))
                                    }
                                }
                            }
                        }
                    }

                    if (includeTime && !selectingMonth) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            GlassWheelPicker(
                                label = "小时",
                                value = selectedHour,
                                range = 0..23,
                                onValueChange = { selectedHour = it },
                                modifier = Modifier.weight(1f)
                            )
                            GlassWheelPicker(
                                label = "分钟",
                                value = selectedMinute,
                                range = 0..59,
                                onValueChange = { selectedMinute = it },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clip(GlassTokens.shapeCapsule)
                                .background(GlassTokens.l3Fill.copy(alpha = 0.55f))
                                .glassClickable(onClick = onDismissRequest)
                                .height(48.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("取消", color = TextPrimary, fontSize = 16.sp)
                        }
                        val confirmModifier = if (!selectingMonth) {
                            Modifier
                        } else {
                            Modifier.graphicsLayer { alpha = 0.42f }
                        }
                        Row(
                            modifier = confirmModifier
                                .weight(1f)
                                .clip(GlassTokens.shapeCapsule)
                                .background(PrimaryBlue)
                                .then(
                                    if (!selectingMonth) Modifier.glassClickable(onClick = {
                                        val result = Calendar.getInstance().apply {
                                            clear()
                                            set(
                                                selectedYear,
                                                selectedMonth,
                                                selectedDay,
                                                if (includeTime) selectedHour else 0,
                                                if (includeTime) selectedMinute else 0
                                            )
                                        }
                                        onConfirm(result.timeInMillis)
                                    }) else Modifier
                                )
                                .height(48.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("确定", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
          }   // AnimatedVisibility（日历弹窗出入场）
        }
    }
}

/** 上下拨轮选择器 */
@Composable
private fun GlassWheelPicker(
    label: String,
    value: Int,
    range: IntRange,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val items = remember(range) { range.toList() }
    val maxIndex = items.lastIndex
    val itemHeight = 34.dp
    val itemHeightPx = with(LocalDensity.current) { itemHeight.toPx() }

    var index by remember { mutableStateOf(value.toFloat().coerceIn(0f, maxIndex.toFloat())) }
    val scope = rememberCoroutineScope()
    var settleJob by remember { mutableStateOf<Job?>(null) }

    // 松手吸附到最近一行，并把结果上报给外部。
    fun settle() {
        val target = index.roundToInt().coerceIn(0, maxIndex)
        settleJob?.cancel()
        settleJob = scope.launch {
            animate(
                initialValue = index,
                targetValue = target.toFloat(),
                initialVelocity = 0f,
                animationSpec = spring(0.7f, 400f, 0.01f)
            ) { v, _ -> index = v }
        }
        if (items[target] != value) onValueChange(items[target])
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(label, color = TextTertiary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Box(
            modifier = Modifier
                .weight(1f)
                .height(itemHeight * 5)
                .pointerInput(items) {
                    detectVerticalDragGestures(
                        onDragStart = { settleJob?.cancel() },
                        onDragEnd = { settle() },
                        onDragCancel = { settle() }
                    ) { change, dragAmount ->
                        change.consume()
                        index = (index - dragAmount / itemHeightPx).coerceIn(0f, maxIndex.toFloat())
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            items.forEachIndexed { i, v ->
                val d = i - index
                val ad = abs(d)
                // 只绘制中心 ±2 行（外圈已完全透明，不必浪费布局）。
                if (ad <= 2.6f) {
                    Text(
                        text = v.toString().padStart(2, '0'),
                        color = TextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.graphicsLayer {
                            translationY = d * itemHeightPx
                            alpha = wheelFade(ad)
                            val s = 1f - 0.2f * (ad / 2.6f).coerceIn(0f, 1f)
                            scaleX = s
                            scaleY = s
                        }
                    )
                }
            }
        }
    }
}

private fun wheelFade(distance: Float): Float {
    val t = (distance / 2.6f).coerceIn(0f, 1f)
    return (1f - t).pow(1.15f)
}

/** 圆形玻璃图标按钮 */
@Composable
private fun GlassCircleIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val press by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = spring(0.7f, 400f, 0.01f),
        label = "circle-button-press"
    )
    LiquidGlassPill(
        backdrop = rememberLayerAwareBackdrop(),
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        shape = CircleShape,
        pressProgress = { press },
        content = content
    )
}

@Composable
private fun GlassPillLabel(
    shape: Shape,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val press by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = spring(0.7f, 400f, 0.01f),
        label = "pill-label-press"
    )
    LiquidGlassPill(
        backdrop = rememberLayerAwareBackdrop(),
        modifier = if (onClick != null) {
            modifier.clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
        } else {
            modifier
        },
        shape = shape,
        pressProgress = { press },
        content = content
    )
}

// internal（原 private）：dock 几何收敛到 `glass/DockLayout.kt` 后，那里需要判断
// 多窗口模式来决定底边距，否则会出现"同一套数字两处各判一次、其中一处忘了判"的老问题。
private fun requestCarModeFullscreen(context: Context) {
    val tag = "CarModeFullscreen"
    val host = context.findActivity()
    if (host == null || !host.inMultiWindowCompat()) {
        // 已经不在分屏：只需把车机页提到前台。
        android.util.Log.i(tag, "不在分屏，直接提到前台（inSplit=${host?.inMultiWindowCompat()}）")
        focusCarModeExclusive(context)
        return
    }

    val cfg = host.resources.configuration
    val winBounds = if (Build.VERSION.SDK_INT >= 30) {
        host.windowManager.currentWindowMetrics.bounds.toString()
    } else "n/a"
    val dm = android.util.DisplayMetrics().also { host.windowManager.defaultDisplay.getRealMetrics(it) }
    android.util.Log.i(
        tag,
        "state: inSplit=${host?.inMultiWindowCompat()} pip=${host.inPictureInPictureCompat()} " +
            "winBounds=$winBounds display=${dm.widthPixels}x${dm.heightPixels} " +
            "cfg=${cfg.screenWidthDp}x${cfg.screenHeightDp} smallest=${cfg.smallestScreenWidthDp} " +
            "orientation=${cfg.orientation}"
    )
    android.util.Log.i(tag, "请求退出分屏：sdk=${Build.VERSION.SDK_INT}")
    val handler = android.os.Handler(android.os.Looper.getMainLooper())
    var handled = false

    fun fallbackOnce(reason: String) {
        if (handled) return
        handled = true
        android.util.Log.w(tag, "$reason → 走兜底")
        exitSplitFallback(context, context.findActivity() ?: host)
    }

    val accepted = Build.VERSION.SDK_INT >= 35 && runCatching {
        host.requestFullscreenMode(
            Activity.FULLSCREEN_MODE_REQUEST_ENTER,
            object : android.os.OutcomeReceiver<Void, Throwable> {
                override fun onResult(result: Void?) {
                    android.util.Log.i(tag, "系统回调：已进入全屏")
                }

                override fun onError(error: Throwable) {
                    android.util.Log.w(tag, "系统回调：拒绝（$error）")
                    handler.post { fallbackOnce("系统回调：拒绝（${error.message}）") }
                }
            }
        )
    }.onFailure {
        android.util.Log.w(tag, "requestFullscreenMode 调用抛错：$it")
    }.isSuccess

    if (!accepted) {
        fallbackOnce("平台不支持/调用失败")
        return
    }

    handler.postDelayed({
        val now = context.findActivity()
        if (now != null && now.inMultiWindowCompat()) {
            fallbackOnce("800ms 后仍在分屏")
        } else {
            android.util.Log.i(tag, "800ms 后已不在分屏 ✓")
        }
    }, 800L)
}

private fun exitSplitFallback(context: Context, host: Activity) {
    val tag = "CarModeFullscreen"
    android.util.Log.i(tag, "兜底①：moveTaskToBack（让分屏解散）")
    host.moveTaskToBack(true)

    val handler = android.os.Handler(android.os.Looper.getMainLooper())
    var attempt = 0
    fun relaunch() {
        attempt++
        android.util.Log.i(tag, "兜底②-$attempt：把车机页拉回前台")
        runCatching {
            context.startActivity(
                Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            )
        }.onFailure { android.util.Log.w(tag, "拉回前台失败：$it") }

        // 回读校验：真的还没退出分屏才补拉，别做无意义的重试。
        if (attempt < SPLIT_EXIT_ATTEMPTS) {
            handler.postDelayed({
                if (context.findActivity()?.inMultiWindowCompat() == true) relaunch()
            }, 260L)
        }
    }
    relaunch()
}

private const val SPLIT_EXIT_ATTEMPTS = 3

internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun Page(title: String, content: @Composable ColumnScope.() -> Unit) {
    LazyPage(title) {
        item { Column(verticalArrangement = Arrangement.spacedBy(12.dp), content = content) }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun LazyPage(title: String, content: LazyListScope.() -> Unit) {
    CompositionLocalProvider(LocalOverscrollConfiguration provides null) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, top = 96.dp, end = 16.dp, bottom = 144.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )
    }
}

private fun LazyListScope.pageSection(key: Any, content: @Composable ColumnScope.() -> Unit) {
    item(key = key) { Column(verticalArrangement = Arrangement.spacedBy(12.dp), content = content) }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun GarageScreen(
    state: LedgerUiState,
    onSelectVehicle: (String) -> Unit,
    onAddVehicle: (String, String, String, VehicleCategory, EnergyType, TransmissionType, String, String, String, String, String, String, String) -> Unit,
    onUpdateVehicle: (String, String, String, String, VehicleCategory, EnergyType, TransmissionType, String, String, String, String, String, String, String) -> Unit,
    onDeleteVehicle: (String) -> Unit
) {
    var detailId by rememberSaveable { mutableStateOf<String?>(null) }
    var creating by rememberSaveable { mutableStateOf(BuildConfig.DEBUG && ChekuDebugNav.takeScreen("VehicleCreate")) }
    var deletingId by rememberSaveable { mutableStateOf<String?>(null) }
    val detailVehicle = state.vehicles.firstOrNull { it.id == detailId }
    val deletingVehicle = state.vehicles.firstOrNull { it.id == deletingId }
    BackHandler(enabled = creating || detailVehicle != null) {
        if (creating) creating = false else detailId = null
    }

    AnimatedContent(
        targetState = when {
            creating -> "create"
            detailVehicle != null -> "detail"
            else -> "list"
        },
        transitionSpec = {
            hierarchyMotion(targetState != "list")
        },
        label = "garage-hierarchy"
    ) { screen ->
        when (screen) {
            "create" -> VehicleDetailScreen(
                title = "新增车辆",
                vehicle = null,
                onBack = { creating = false },
                onAddVehicle = { name, model, plate, category, energy, transmission, fuelTank, battery, chargeLimit, mileage, purchasePrice, purchaseTax, mfgDate ->
                    onAddVehicle(name, model, plate, category, energy, transmission, fuelTank, battery, chargeLimit, mileage, purchasePrice, purchaseTax, mfgDate)
                    creating = false
                },
                onUpdateVehicle = onUpdateVehicle
            )
            "detail" -> detailVehicle?.let { vehicle ->
                VehicleDetailScreen(
                    title = "车辆详情",
                    vehicle = vehicle,
                    onBack = { detailId = null },
                    onAddVehicle = onAddVehicle,
                    onUpdateVehicle = { id, name, model, plate, category, energy, transmission, fuelTank, battery, chargeLimit, mileage, purchasePrice, purchaseTax, mfgDate ->
                        onUpdateVehicle(id, name, model, plate, category, energy, transmission, fuelTank, battery, chargeLimit, mileage, purchasePrice, purchaseTax, mfgDate)
                        detailId = null
                    }
                )
            } ?: Box(Modifier.fillMaxSize())
            else -> CompositionLocalProvider(LocalOverscrollConfiguration provides null) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, top = 96.dp, end = 16.dp, bottom = 86.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item(key = "oil-price") {
                        OilPriceModule(
                            snapshot = state.oilPrice,
                            isFetching = state.isFetchingOilPrice
                        )
                    }
                    item(key = "stats-primary") {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            StatCard("车辆总数", state.vehicles.size.toString(), "辆", Modifier.weight(1f), unitInHeader = true)
                            StatCard("总里程", formatDecimal(state.vehicles.sumOf { it.currentMileage }), "km", Modifier.weight(1f), unitInHeader = true)
                        }
                    }
                    item(key = "stats-energy") {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            StatCard("累计加油", state.totalFuelLiters, "L", Modifier.weight(1f), unitInHeader = true)
                            StatCard("累计充电", state.totalChargeKwh, "kWh", Modifier.weight(1f), unitInHeader = true)
                        }
                    }
                    item(key = "add-vehicle") {
                        GlassActionButton("新增车辆", Icons.Outlined.Add, PrimaryBlue) { creating = true }
                    }
                    items(state.vehicles, key = { it.id }) { vehicle ->
                        VehicleCard(
                            modifier = Modifier.animateItem(),
                            vehicle = vehicle,
                            selected = vehicle.id == state.selectedVehicle?.id,
                            consumption = state.vehicleConsumptions[vehicle.id],
                            onClick = {
                                onSelectVehicle(vehicle.id)
                                detailId = vehicle.id
                            },
                            onLongDelete = { deletingId = vehicle.id }
                        )
                    }
                }
            }
        }
    }

    if (deletingVehicle != null) {
        GlassDialog(
            onDismissRequest = { deletingId = null },
            title = "删除车辆",
            text = { Text("确认删除 ${deletingVehicle.name}？关联记录会一起删除。") },
            confirmButton = {
                GlassDialogAction(onClick = {
                    onDeleteVehicle(deletingVehicle.id)
                    deletingId = null
                }) { Text("删除", color = Error) }
            },
            dismissButton = { GlassDialogAction(onClick = { deletingId = null }) { Text("取消") } }
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun VehicleDetailScreen(
    title: String,
    vehicle: VehicleEntity?,
    onBack: () -> Unit,
    onAddVehicle: (String, String, String, VehicleCategory, EnergyType, TransmissionType, String, String, String, String, String, String, String) -> Unit,
    onUpdateVehicle: (String, String, String, String, VehicleCategory, EnergyType, TransmissionType, String, String, String, String, String, String, String) -> Unit
) {
    var name by rememberSaveable(vehicle?.id) { mutableStateOf(vehicle?.name ?: "") }
    var model by rememberSaveable(vehicle?.id) { mutableStateOf(vehicle?.model ?: "") }
    var plate by rememberSaveable(vehicle?.id) { mutableStateOf(vehicle?.plate ?: "") }
    var mileage by rememberSaveable(vehicle?.id) { mutableStateOf(formatDecimal(vehicle?.currentMileage ?: 0.0)) }
    var category by rememberSaveable(vehicle?.id) { mutableStateOf(vehicle?.category ?: VehicleCategory.Car) }
    var energy by rememberSaveable(vehicle?.id) { mutableStateOf(vehicle?.energyType ?: EnergyType.Fuel) }
    var transmission by rememberSaveable(vehicle?.id) { mutableStateOf(vehicle?.transmissionType ?: TransmissionType.Automatic) }
    var fuelTankCapacity by rememberSaveable(vehicle?.id) { mutableStateOf(vehicle?.fuelTankCapacityLiters?.trimOne() ?: "") }
    var batteryCapacity by rememberSaveable(vehicle?.id) { mutableStateOf(vehicle?.batteryCapacityKwh?.trimOne() ?: "") }
    var chargeLimitSoc by rememberSaveable(vehicle?.id) {
        mutableStateOf(vehicle?.defaultChargeLimitSoc?.toString() ?: "100")
    }
    var purchasePrice by rememberSaveable(vehicle?.id) { mutableStateOf(formatDecimal(vehicle?.purchasePrice ?: 0.0)) }
    var purchaseTax by rememberSaveable(vehicle?.id) { mutableStateOf(formatDecimal(vehicle?.purchaseTax ?: 0.0)) }
    var mfgDateText by rememberSaveable(vehicle?.id) {
        mutableStateOf(vehicle?.manufacturingDate?.let { SimpleDateFormat("yyyy-MM-dd", java.util.Locale.CHINA).format(Date(it)) } ?: "")
    }
    var showDatePicker by remember { mutableStateOf(false) }
    var picker by rememberSaveable(vehicle?.id) { mutableStateOf<VehiclePicker?>(null) }
    BackHandler(enabled = picker != null) {
        picker = null
    }

    AnimatedContent(
        targetState = picker,
        transitionSpec = {
            hierarchyMotion(targetState != null)
        },
        label = "vehicle-selection-subpage"
    ) { currentPicker ->
        when (currentPicker) {
            VehiclePicker.Kind -> SelectionPage("选择车辆类型", VehicleKind.entries, { it.label }, { picker = null }) {
                val nextEnergy = resolvedEnergyFor(it, energy)
                category = it.category
                energy = nextEnergy
                transmission = it.defaultTransmission
                if (!nextEnergy.supportsFuel) fuelTankCapacity = ""
                if (!nextEnergy.hasBattery) batteryCapacity = ""
                if (nextEnergy.supportsCharging && chargeLimitSoc.isBlank()) chargeLimitSoc = "100"
                picker = null
            }
            VehiclePicker.Energy -> SelectionPage(
                title = "选择动力类型",
                options = energyOptionsFor(vehicleKindFor(category, energy)),
                label = { it.label },
                onBack = { picker = null },
                supportingLabel = { it.capabilityLabel() }
            ) {
                energy = it
                if (it == EnergyType.Human) transmission = TransmissionType.None
                if (!it.supportsFuel) fuelTankCapacity = ""
                if (!it.hasBattery) batteryCapacity = ""
                if (it.supportsCharging && chargeLimitSoc.isBlank()) chargeLimitSoc = "100"
                picker = null
            }
            VehiclePicker.Transmission -> SelectionPage("选择变速结构", TransmissionType.entries, { it.label }, { picker = null }) {
                transmission = it
                picker = null
            }
            null -> Page(title) {
                BackButton(onBack)
                AppTextField("车辆名称", name, { name = it }, "", KeyboardType.Text)
                AppTextField("型号", model, { model = it }, "", KeyboardType.Text)
                AppTextField("车牌号", plate, { plate = it }, "", KeyboardType.Text)
                AppTextField("当前里程", mileage, { mileage = it }, "km", KeyboardType.Decimal)
                SelectRow("车辆类型", vehicleKindLabel(category, energy)) { picker = VehiclePicker.Kind }
                SelectRow("动力类型", energy.label) { picker = VehiclePicker.Energy }
                SelectRow("变速结构", transmission.label) { picker = VehiclePicker.Transmission }
                if (energy.supportsFuel) AppTextField("油箱容积", fuelTankCapacity, { fuelTankCapacity = it }, "L", KeyboardType.Decimal)
                if (energy.hasBattery) AppTextField("电池容量", batteryCapacity, { batteryCapacity = it }, "kWh", KeyboardType.Decimal)
                if (energy.supportsCharging) {
                    AppTextField(
                        "默认充电上限",
                        chargeLimitSoc,
                        { chargeLimitSoc = it },
                        "%",
                        KeyboardType.Number,
                        maxNumericValue = 100.0
                    )
                }
                AppTextField("购车价", purchasePrice, { purchasePrice = it }, "元", KeyboardType.Decimal)
                AppTextField("购置税", purchaseTax, { purchaseTax = it }, "元", KeyboardType.Decimal)
                SelectRow("出厂日期", mfgDateText.ifBlank { "未设置" }) { showDatePicker = true }
                GlassActionButton("保存车辆", Icons.Outlined.Save, PrimaryBlue) {
                    if (vehicle == null) onAddVehicle(name, model, plate, category, energy, transmission, fuelTankCapacity, batteryCapacity, chargeLimitSoc, mileage, purchasePrice, purchaseTax, mfgDateText)
                    else onUpdateVehicle(vehicle.id, name, model, plate, category, energy, transmission, fuelTankCapacity, batteryCapacity, chargeLimitSoc, mileage, purchasePrice, purchaseTax, mfgDateText)
                }
            }
        }
    }

    if (showDatePicker) {
        val initialDate = vehicle?.manufacturingDate ?: runCatching {
            SimpleDateFormat("yyyy-MM-dd", java.util.Locale.CHINA).parse(mfgDateText)?.time
        }.getOrNull() ?: System.currentTimeMillis()
        GlassDatePickerDialog(
            initialMillis = initialDate,
            onDismissRequest = { showDatePicker = false },
            onConfirm = { millis ->
                mfgDateText = SimpleDateFormat("yyyy-MM-dd", java.util.Locale.CHINA).format(Date(millis))
                showDatePicker = false
            }
        )
    }
}

@Composable
private fun <T> SelectionPage(
    title: String,
    options: List<T>,
    label: (T) -> String,
    onBack: () -> Unit,
    supportingLabel: (T) -> String? = { null },
    onSelect: (T) -> Unit
) {
    Page(title) {
        BackButton(onBack)
        options.forEach { option ->
            CardBlock(onClick = { onSelect(option) }) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = label(option),
                        modifier = Modifier.weight(1f),
                        color = TextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    supportingLabel(option)?.let { supportingText ->
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = supportingText,
                            color = TextTertiary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.End
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecordScreen(
    state: LedgerUiState,
    onSelectVehicle: (String) -> Unit,
    onSaveMileage: (String, String, String, String, Boolean) -> Unit,
    onSaveEnergy: (EnergyRecordFormInput, String, Boolean) -> Unit,
    onDeleteRecord: (String, RecentRecordType) -> Unit = { _, _ -> },
    onUpdateMileageRecord: (String, String, String, String, String) -> Unit = { _, _, _, _, _ -> },
    onUpdateEnergyRecord: (String, String, EnergyRecordFormInput) -> Unit = { _, _, _ -> }
) {
    var vehiclePickerOpen by rememberSaveable { mutableStateOf(false) }
    var mode by rememberSaveable { mutableStateOf(RecordMode.Mileage) }
    var editingRecord by remember { mutableStateOf<RecentRecordItem?>(null) }
    var deleteConfirmRecord by remember { mutableStateOf<RecentRecordItem?>(null) }
    var mileage by rememberSaveable { mutableStateOf("") }
    var subtotalMileage by rememberSaveable { mutableStateOf("") }
    var roadFee by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var recordTime by rememberSaveable {
        mutableStateOf(SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date()))
    }
    var recordTimeMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    var showRecordTimePicker by remember { mutableStateOf(false) }
    var orderAmount by rememberSaveable { mutableStateOf("") }
    var paidAmount by rememberSaveable { mutableStateOf("") }
    var paidAmountManuallyEdited by rememberSaveable { mutableStateOf(false) }
    var amount by rememberSaveable { mutableStateOf("") }
    var fuelGrade by rememberSaveable { mutableStateOf("95") }
    var fuelPrice by rememberSaveable { mutableStateOf(state.settings.oilPrices["95"] ?: "7.64") }
    var chargeMethod by rememberSaveable { mutableStateOf("家充") }
    var electricityPrice by rememberSaveable { mutableStateOf(state.settings.homeChargePrice) }
    var serviceFee by rememberSaveable { mutableStateOf("0.00") }
    var chargeLimitSoc by rememberSaveable { mutableStateOf("100") }
    var isFull by rememberSaveable { mutableStateOf(true) }
    var missedPrevious by rememberSaveable { mutableStateOf(false) }
    var modeHint by rememberSaveable { mutableStateOf("") }
    var pending by remember { mutableStateOf<PendingRecordSave?>(null) }
    val vehicle = state.selectedVehicle
    val enabledModes = buildSet {
        add(RecordMode.Mileage)
        if (vehicle?.energyType?.supportsFuel == true && (vehicle.fuelTankCapacityLiters ?: 0.0) > 0.0) add(RecordMode.Fuel)
        if (vehicle?.energyType?.supportsCharging == true && (vehicle.batteryCapacityKwh ?: 0.0) > 0.0) add(RecordMode.Charge)
    }

    BackHandler(enabled = editingRecord != null || vehiclePickerOpen) {
        if (editingRecord != null) editingRecord = null else vehiclePickerOpen = false
    }

    LaunchedEffect(vehicle?.id) {
        mileage = vehicle?.currentMileage?.let(::formatDecimal).orEmpty()
        subtotalMileage = ""
        chargeLimitSoc = vehicle?.defaultChargeLimitSoc?.toString() ?: "100"
        if (mode !in enabledModes) mode = RecordMode.Mileage
        modeHint = ""
    }

    LaunchedEffect(subtotalMileage, vehicle?.currentMileage) {
        val subtotal = subtotalMileage.toDoubleOrNull()
        val base = vehicle?.currentMileage ?: 0.0
        if (subtotal != null && subtotal >= 0) {
            mileage = formatDecimal(base + subtotal)
        }
    }

    LaunchedEffect(fuelGrade, state.settings.oilPrices) {
        fuelPrice = state.settings.oilPrices[fuelGrade] ?: fuelPrice
    }

    fun chargeUnitPrice(): String = combinedChargePrice(electricityPrice, serviceFee)

    fun updateOrderAmount(value: String) {
        orderAmount = value
        if (!paidAmountManuallyEdited) paidAmount = value
    }

    fun recalculateOrderFromAmount(unitPrice: String) {
        if (amount.isNotBlank()) updateOrderAmount(deriveCost(unitPrice, amount))
    }

    fun energyForm(type: EnergyRecordType) = EnergyRecordFormInput(
        type = type,
        mileage = mileage,
        amount = amount,
        orderAmount = orderAmount,
        paidAmount = paidAmount.ifBlank { orderAmount },
        isFull = isFull,
        missedPrevious = missedPrevious,
        fuelGrade = fuelGrade,
        unitPrice = fuelPrice,
        chargeMethod = chargeMethod,
        electricityPrice = electricityPrice,
        serviceFee = serviceFee,
        chargeLimitSoc = chargeLimitSoc,
        note = note
    )

    fun submit(allowDecrease: Boolean = false) {
        val currentMileage = vehicle?.currentMileage
        val inputMileage = mileage.toDoubleOrNull()
        if (!allowDecrease && currentMileage != null && inputMileage != null && inputMileage < currentMileage) {
            pending = PendingRecordSave(
                mode = mode,
                mileage = mileage,
                note = note,
                roadFee = roadFee,
                recordTime = recordTime,
                energyForm = when (mode) {
                    RecordMode.Mileage -> null
                    RecordMode.Fuel -> energyForm(EnergyRecordType.Fuel)
                    RecordMode.Charge -> energyForm(EnergyRecordType.Charge)
                }
            )
            return
        }
        when (mode) {
            RecordMode.Mileage -> onSaveMileage(mileage, note, roadFee, recordTime, allowDecrease)
            RecordMode.Fuel -> onSaveEnergy(energyForm(EnergyRecordType.Fuel), recordTime, allowDecrease)
            RecordMode.Charge -> onSaveEnergy(energyForm(EnergyRecordType.Charge), recordTime, allowDecrease)
        }
    }

    AnimatedContent(
        targetState = editingRecord,
        transitionSpec = {
            hierarchyMotion(targetState != null)
        },
        label = "record-hierarchy"
    ) { record ->
        if (record != null) {
            RecordEditScreen(
                record = record,
                vehicleName = state.vehicles.firstOrNull { it.id == record.vehicleId }?.name ?: "",
                onBack = { editingRecord = null },
                onSaveMileage = { mile, time, nt, fee ->
                    onUpdateMileageRecord(record.recordId, mile, time, nt, fee)
                    editingRecord = null
                },
                onSaveEnergy = { time, form ->
                    onUpdateEnergyRecord(record.recordId, time, form)
                    editingRecord = null
                }
            )
        } else {
            LazyPage("记录") {
                pageSection("vehicle") {
                SectionTitle("选择车辆")
                VehicleSelectorCard(
                    vehicle = vehicle,
                    open = vehiclePickerOpen,
                    vehicles = state.vehicles,
                    consumptions = state.vehicleConsumptions,
                    onToggle = { vehiclePickerOpen = !vehiclePickerOpen },
                    onSelect = {
                        onSelectVehicle(it.id)
                        vehiclePickerOpen = false
                    }
                )
                }
                pageSection("mode") {
                RecordModePicker(
                    selected = mode,
                    options = RecordMode.entries,
                    enabledOptions = enabledModes,
                    onSelect = {
                        mode = it
                        modeHint = ""
                    },
                    onUnavailable = { unavailable ->
                        modeHint = when (unavailable) {
                            RecordMode.Mileage -> ""
                            RecordMode.Fuel -> if (vehicle?.energyType?.supportsFuel != true) {
                                "当前动力类型不支持加油"
                            } else {
                                "请先在车辆资料中填写油箱容量"
                            }
                            RecordMode.Charge -> if (vehicle?.energyType?.supportsCharging != true) {
                                "当前动力类型不支持外接充电"
                            } else {
                                "请先在车辆资料中填写电池容量"
                            }
                        }
                    }
                )
                }
                if (modeHint.isNotBlank()) pageSection("mode-hint") { InfoCard("暂不可用", modeHint) }
                pageSection("mileage") {
                RecordSectionTitle("里程信息")
                AppTextField("小计里程（可选）", subtotalMileage, { subtotalMileage = it }, "km", KeyboardType.Decimal)
                AppTextField("总里程", mileage, { mileage = it }, "km", KeyboardType.Decimal)
                }
                when (mode) {
                    RecordMode.Mileage -> pageSection("mileage-fields") {
                        AppTextField("ETC/路桥/停车/罚款", roadFee, { roadFee = it }, "元", KeyboardType.Decimal)
                        AppTextField("备注", note, { note = it }, "", KeyboardType.Text)
                        SelectRow("记录时间", recordTime) { showRecordTimePicker = true }
                        GlassActionButton("保存里程", Icons.Outlined.Save, PrimaryBlue, enabled = !state.isSaving && vehicle != null) { submit() }
                    }
                    RecordMode.Fuel -> {
                        pageSection("fuel-fields") {
                        FuelRecordFields(
                            orderAmount = orderAmount,
                            onOrderAmountChange = {
                                updateOrderAmount(it)
                                amount = deriveAmount(it, fuelPrice)
                            },
                            paidAmount = paidAmount,
                            onPaidAmountChange = {
                                paidAmount = it
                                paidAmountManuallyEdited = true
                            },
                            onRestorePaidAmount = {
                                paidAmountManuallyEdited = false
                                paidAmount = orderAmount
                            },
                            fuelGrade = fuelGrade,
                            onFuelGradeChange = {
                                fuelGrade = it
                                fuelPrice = state.settings.oilPrices[it] ?: fuelPrice
                                recalculateOrderFromAmount(fuelPrice)
                            },
                            fuelPrice = fuelPrice,
                            onFuelPriceChange = {
                                fuelPrice = it
                                recalculateOrderFromAmount(fuelPrice)
                            },
                            amount = amount,
                            onAmountChange = {
                                amount = it
                                updateOrderAmount(deriveCost(fuelPrice, amount))
                            },
                            isFull = isFull,
                            onFullChange = { isFull = it },
                            missedPrevious = missedPrevious,
                            onMissedPreviousChange = { missedPrevious = it }
                        )
                        }
                        pageSection("fuel-note") {
                        RecordSectionTitle("备注")
                        AppTextField("备注", note, { note = it }, "", KeyboardType.Text)
                        SelectRow("记录时间", recordTime) { showRecordTimePicker = true }
                        GlassActionButton("保存加油", Icons.Outlined.Save, PrimaryBlue, enabled = !state.isSaving && vehicle != null) { submit() }
                        }
                    }
                    RecordMode.Charge -> {
                        pageSection("charge-fields") {
                        ChargeRecordFields(
                            orderAmount = orderAmount,
                            onOrderAmountChange = {
                                updateOrderAmount(it)
                                amount = deriveAmount(it, chargeUnitPrice())
                            },
                            paidAmount = paidAmount,
                            onPaidAmountChange = {
                                paidAmount = it
                                paidAmountManuallyEdited = true
                            },
                            onRestorePaidAmount = {
                                paidAmountManuallyEdited = false
                                paidAmount = orderAmount
                            },
                            method = chargeMethod,
                            onMethodChange = {
                                chargeMethod = it
                                electricityPrice = if (it == "家充") state.settings.homeChargePrice else state.settings.commercialChargePrice
                                serviceFee = if (it == "家充") "0.00" else state.settings.commercialServiceFee
                                recalculateOrderFromAmount(chargeUnitPrice())
                            },
                            electricityPrice = electricityPrice,
                            onElectricityPriceChange = {
                                electricityPrice = it
                                recalculateOrderFromAmount(chargeUnitPrice())
                            },
                            serviceFee = serviceFee,
                            onServiceFeeChange = {
                                serviceFee = if (chargeMethod == "家充") "0.00" else it
                                recalculateOrderFromAmount(chargeUnitPrice())
                            },
                            amount = amount,
                            onAmountChange = {
                                amount = it
                                updateOrderAmount(deriveCost(chargeUnitPrice(), amount))
                            },
                            chargeLimitSoc = chargeLimitSoc,
                            onChargeLimitChange = { chargeLimitSoc = it },
                            chargeLimitHint = "保存后设为车辆默认值",
                            isFull = isFull,
                            onFullChange = { isFull = it },
                            missedPrevious = missedPrevious,
                            onMissedPreviousChange = { missedPrevious = it }
                        )
                        }
                        pageSection("charge-note") {
                        RecordSectionTitle("备注")
                        AppTextField("备注", note, { note = it }, "", KeyboardType.Text)
                        SelectRow("记录时间", recordTime) { showRecordTimePicker = true }
                        GlassActionButton("保存充电", Icons.Outlined.Save, PrimaryBlue, enabled = !state.isSaving && vehicle != null) { submit() }
                        }
                    }
                }
                pageSection("recent-title") { SectionTitle("最近记录") }
                pageSection("recent") {
                if (state.recentRecords.isEmpty()) {
                    InfoCard("暂无记录", "保存里程、加油或充电后会显示在这里。")
                } else {
                    Column(Modifier.animateContentSize()) {
                        state.recentRecords.forEach { item ->
                            key(item.type, item.recordId) {
                                RecordCard(item, { editingRecord = item }, { deleteConfirmRecord = item })
                            }
                        }
                    }
                }
                }
            }
        }
    }

    pending?.let { save ->
        GlassDialog(
            onDismissRequest = { pending = null },
            title = "确认低于当前里程？",
            text = { Text("当前车辆里程为 ${vehicle?.currentMileage ?: 0} km，本次输入 ${save.mileage} km。确认后会按本次输入更新当前里程。") },
            confirmButton = {
                GlassDialogAction(onClick = {
                    when (save.mode) {
                        RecordMode.Mileage -> onSaveMileage(save.mileage, save.note, save.roadFee, save.recordTime, true)
                        RecordMode.Fuel, RecordMode.Charge -> save.energyForm?.let { onSaveEnergy(it, save.recordTime, true) }
                    }
                    pending = null
                }) { Text("确认保存") }
            },
            dismissButton = { GlassDialogAction(onClick = { pending = null }) { Text("取消") } }
        )
    }

    deleteConfirmRecord?.let { record ->
        GlassDialog(
            onDismissRequest = { deleteConfirmRecord = null },
            title = "删除记录",
            text = { Text("确认删除此${if (record.type == RecentRecordType.Mileage) "里程" else "加油/充电"}记录？") },
            confirmButton = {
                GlassDialogAction(onClick = {
                    onDeleteRecord(record.recordId, record.type)
                    deleteConfirmRecord = null
                }) { Text("删除", color = Error) }
            },
            dismissButton = {
                GlassDialogAction(onClick = { deleteConfirmRecord = null }) { Text("取消") }
            }
        )
    }

    if (showRecordTimePicker) {
        GlassDateTimePickerDialog(
            initialMillis = recordTimeMillis,
            onDismissRequest = { showRecordTimePicker = false },
            onConfirm = { millis ->
                recordTimeMillis = millis
                recordTime = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date(millis))
                showRecordTimePicker = false
            }
        )
    }
}

@Composable
private fun VehicleSelectorCard(
    vehicle: VehicleEntity?,
    open: Boolean,
    vehicles: List<VehicleEntity>,
    consumptions: Map<String, VehicleConsumptionSummary> = emptyMap(),
    onToggle: () -> Unit,
    onSelect: (VehicleEntity) -> Unit,
    /** 弹层内为 true */
    solid: Boolean = false
) {
    val selectableVehicles = vehicles.filterNot { it.id == vehicle?.id }
    val showEmptyMessage = vehicle == null && vehicles.isEmpty()
    val hasPickerContent = selectableVehicles.isNotEmpty() || showEmptyMessage

    Column {
        if (vehicle != null) {
            VehicleCard(
                vehicle = vehicle,
                selected = true,
                consumption = consumptions[vehicle.id],
                onClick = if (hasPickerContent) onToggle else ({}),
                solid = solid
            )
        } else {
            CardBlock(
                onClick = onToggle,
                solidFill = if (solid) solidCardFill() else null
            ) {
                Text("选择车辆", fontSize = 17.sp, fontWeight = FontWeight.Black, color = TextPrimary)
                Text("车库暂无可选车辆", color = TextTertiary, fontSize = 13.sp)
            }
        }
        AnimatedVisibility(
            visible = open && hasPickerContent,
            enter = expandVertically(
                animationSpec = tween(360, easing = FastOutSlowInEasing),
                expandFrom = Alignment.Top,
                clip = true
            ) + fadeIn(tween(180)),
            exit = shrinkVertically(
                animationSpec = tween(360, easing = FastOutSlowInEasing),
                shrinkTowards = Alignment.Top,
                clip = true
            ) + fadeOut(tween(140))
        ) {
            Column {
                Spacer(Modifier.height(12.dp))
                if (showEmptyMessage) {
                    InfoCard("暂无车辆", "请先到车库新增车辆。")
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        selectableVehicles.forEach { item ->
                            VehicleCard(
                                vehicle = item,
                                selected = item.id == vehicle?.id,
                                consumption = consumptions[item.id],
                                onClick = { onSelect(item) },
                                solid = solid
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FuelRecordFields(
    orderAmount: String,
    onOrderAmountChange: (String) -> Unit,
    paidAmount: String,
    onPaidAmountChange: (String) -> Unit,
    onRestorePaidAmount: () -> Unit,
    fuelGrade: String,
    onFuelGradeChange: (String) -> Unit,
    fuelPrice: String,
    onFuelPriceChange: (String) -> Unit,
    amount: String,
    onAmountChange: (String) -> Unit,
    isFull: Boolean,
    onFullChange: (Boolean) -> Unit,
    missedPrevious: Boolean,
    onMissedPreviousChange: (Boolean) -> Unit
) {
    Text(
        "本次加油",
        color = TextSecondary,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1
    )
    val fuelGrades = listOf("92", "95", "98", "0")
    GlassSegmentedControl(
        options = fuelGrades.map { "#" + it },
        selectedIndex = fuelGrades.indexOf(fuelGrade).coerceAtLeast(0),
        onSelect = { onFuelGradeChange(fuelGrades[it]) },
        backdrop = rememberLayerAwareBackdrop(),
        accent = PrimaryBlue,
        selectedTextColor = PrimaryBlue,
        unselectedTextColor = TextPrimary.copy(alpha = 0.82f),
        modifier = Modifier.fillMaxWidth()
    )
    RecordSectionTitle("费用")
    PaymentFields(orderAmount, onOrderAmountChange, paidAmount, onPaidAmountChange, onRestorePaidAmount)
    AppTextField("加油量", amount, onAmountChange, "L", KeyboardType.Decimal)
    AppTextField("油价", fuelPrice, onFuelPriceChange, "元/L", KeyboardType.Decimal)
    RecordSectionTitle("结算选项")
    SettlementOptions(
        fullLabel = "本次加满",
        fullSubtitle = "用于油耗结算",
        isFull = isFull,
        onFullChange = onFullChange,
        missedPrevious = missedPrevious,
        onMissedPreviousChange = onMissedPreviousChange
    )
}

@Composable
private fun ChargeRecordFields(
    orderAmount: String,
    onOrderAmountChange: (String) -> Unit,
    paidAmount: String,
    onPaidAmountChange: (String) -> Unit,
    onRestorePaidAmount: () -> Unit,
    method: String,
    onMethodChange: (String) -> Unit,
    electricityPrice: String,
    onElectricityPriceChange: (String) -> Unit,
    serviceFee: String,
    onServiceFeeChange: (String) -> Unit,
    amount: String,
    onAmountChange: (String) -> Unit,
    chargeLimitSoc: String,
    onChargeLimitChange: (String) -> Unit,
    chargeLimitHint: String,
    isFull: Boolean,
    onFullChange: (Boolean) -> Unit,
    missedPrevious: Boolean,
    onMissedPreviousChange: (Boolean) -> Unit
) {
    Text(
        "本次充电",
        color = TextSecondary,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1
    )
    val chargeMethods = listOf("家充", "商充")
    GlassSegmentedControl(
        options = chargeMethods,
        selectedIndex = chargeMethods.indexOf(method).coerceAtLeast(0),
        onSelect = { onMethodChange(chargeMethods[it]) },
        backdrop = rememberLayerAwareBackdrop(),
        accent = PrimaryBlue,
        selectedTextColor = PrimaryBlue,
        unselectedTextColor = TextPrimary.copy(alpha = 0.82f),
        modifier = Modifier.fillMaxWidth()
    )
    RecordSectionTitle("费用")
    PaymentFields(orderAmount, onOrderAmountChange, paidAmount, onPaidAmountChange, onRestorePaidAmount)
    AppTextField("充电量", amount, onAmountChange, "kWh", KeyboardType.Decimal)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AppTextField("电价", electricityPrice, onElectricityPriceChange, "元/kWh", KeyboardType.Decimal, Modifier.weight(1f))
        AppTextField("服务费", serviceFee, onServiceFeeChange, "元/kWh", KeyboardType.Decimal, Modifier.weight(1f), enabled = method == "商充")
    }
    RecordSectionTitle("结算选项")
    AppTextField(
        "充电上限",
        chargeLimitSoc,
        onChargeLimitChange,
        "%",
        KeyboardType.Number,
        maxNumericValue = 100.0
    )
    Text(
        chargeLimitHint,
        color = TextTertiary,
        fontSize = 12.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
    SettlementOptions(
        fullLabel = "本次充满",
        fullSubtitle = "充至设定上限",
        isFull = isFull,
        onFullChange = onFullChange,
        missedPrevious = missedPrevious,
        onMissedPreviousChange = onMissedPreviousChange
    )
}

@Composable
private fun PaymentFields(
    orderAmount: String,
    onOrderAmountChange: (String) -> Unit,
    paidAmount: String,
    onPaidAmountChange: (String) -> Unit,
    onRestorePaidAmount: () -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AppTextField("订单金额", orderAmount, onOrderAmountChange, "元", KeyboardType.Decimal, Modifier.weight(1f))
        AppTextField("实付金额", paidAmount, onPaidAmountChange, "元", KeyboardType.Decimal, Modifier.weight(1f))
    }
    if (paidAmount != orderAmount) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            GlassDialogAction(onClick = onRestorePaidAmount) {
                Icon(Icons.Outlined.Refresh, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("恢复为订单金额")
            }
        }
    }
}

@Composable
private fun SettlementOptions(
    fullLabel: String,
    fullSubtitle: String,
    isFull: Boolean,
    onFullChange: (Boolean) -> Unit,
    missedPrevious: Boolean,
    onMissedPreviousChange: (Boolean) -> Unit
) {
    CardBlock(level = GlassLevel.L1_Card) {
        SettlementOptionRow(fullLabel, fullSubtitle, isFull, onFullChange)
        SettlementOptionRow(
            "此前有漏记",
            "本周期不参与统计",
            missedPrevious,
            onMissedPreviousChange
        )
    }
}

@Composable
private fun SettlementOptionRow(
    title: String,
    subtitle: String,
    value: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        LiquidToggle(
            selected = { value },
            onSelect = onChange,
            backdrop = rememberLayerAwareBackdrop(),
            modifier = Modifier.scale(0.85f)
        )
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(
                subtitle,
                color = TextTertiary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun RecordSectionTitle(text: String) {
    Text(
        text = text,
        color = TextSecondary,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 4.dp)
    )
}

@Composable
private fun MaintenanceScreen(
    state: LedgerUiState,
    onSelectVehicle: (String) -> Unit,
    onCompleteMaintenance: (String, Double, Double, String, String, Long) -> Unit,
    onAddCustomMaintenance: (String, String, String) -> Unit,
    onUpdateMaintenanceItem: (String, String, String, String) -> Unit = { _, _, _, _ -> },
    onDeleteMaintenanceItem: (String) -> Unit = {},
    getHistoryFlow: (String) -> kotlinx.coroutines.flow.Flow<List<MaintenanceHistoryEntity>> = { kotlinx.coroutines.flow.flowOf(emptyList()) },
    onUpdateHistory: (String, String, String, String, String, String, String) -> Unit = { _, _, _, _, _, _, _ -> },
    onDeleteHistory: (String) -> Unit = {},
    onCompleteInsurance: (String, String, String, String, String) -> Unit = { _, _, _, _, _ -> },
    onAddRepair: (String, String, String, String, String, String, String) -> Unit = { _, _, _, _, _, _, _ -> },
    onUpdateSpecialHistory: (String, String, String, String, String, String, String, String) -> Unit = { _, _, _, _, _, _, _, _ -> }
) {
    var vehiclePickerOpen by rememberSaveable { mutableStateOf(false) }
    var managing by remember { mutableStateOf(false) }
    var viewingHistoryItemId by remember { mutableStateOf<String?>(null) }
    val vehicle = state.selectedVehicle
    val items = if (vehicle != null) state.maintenance.filter { it.item.vehicleId == vehicle.id } else state.maintenance
    val overdue = items.count { it.status == MaintenanceStatus.Overdue }
    val dueSoon = items.count { it.status == MaintenanceStatus.DueSoon }
    BackHandler(enabled = viewingHistoryItemId != null || managing || vehiclePickerOpen) {
        when {
            viewingHistoryItemId != null -> viewingHistoryItemId = null
            managing -> managing = false
            else -> vehiclePickerOpen = false
        }
    }

    AnimatedContent(
        targetState = when {
            viewingHistoryItemId != null -> "history"
            managing -> "manage"
            else -> "list"
        },
        transitionSpec = {
            hierarchyMotion(targetState != "list")
        },
        label = "maintenance-hierarchy"
    ) { screen ->
        when (screen) {
            "history" -> viewingHistoryItemId?.let { itemId ->
                val selectedItem = items.firstOrNull { it.item.id == itemId }?.item
                val itemName = selectedItem?.name ?: "历史记录"
                MaintenanceHistoryScreen(
                    itemName = itemName,
                    itemType = selectedItem?.itemType ?: MaintenanceItemType.Periodic,
                    historyFlow = getHistoryFlow(itemId),
                    onBack = { viewingHistoryItemId = null },
                    onUpdateHistory = onUpdateHistory,
                    onUpdateSpecialHistory = onUpdateSpecialHistory,
                    onDeleteHistory = onDeleteHistory
                )
            } ?: Box(Modifier.fillMaxSize())
            "manage" -> MaintenanceEditScreen(
                items = items,
                onBack = { managing = false },
                onUpdate = onUpdateMaintenanceItem,
                onDelete = onDeleteMaintenanceItem,
                onAdd = { name, km, days -> onAddCustomMaintenance(name, km, days) }
            )
            else -> LazyPage("保养") {
                pageSection("vehicle") {
                SectionTitle("选择车辆")
                VehicleSelectorCard(
                    vehicle = vehicle,
                    open = vehiclePickerOpen,
                    vehicles = state.vehicles,
                    consumptions = state.vehicleConsumptions,
                    onToggle = { vehiclePickerOpen = !vehiclePickerOpen },
                    onSelect = { onSelectVehicle(it.id); vehiclePickerOpen = false }
                )
                }
                pageSection("summary") {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard("已逾期", overdue.toString(), "项", Modifier.weight(1f), Error, 10.dp)
                    StatCard("即将到期", dueSoon.toString(), "项", Modifier.weight(1f), Warning, 10.dp)
                }
                GlassActionButton("管理全部项目", Icons.Outlined.Build, PrimaryBlue) { managing = true }
                }
                if (items.isEmpty()) {
                    pageSection("empty") { InfoCard("暂无保养项目", "新增车辆时会自动生成保养模板，也可手动添加。") }
                } else {
                    this.items(items, key = { it.item.id }) { snapshot ->
                        MaintenanceProgressCard(
                            snapshot = snapshot,
                            currentMileage = vehicle?.currentMileage ?: 0.0,
                            hasHistory = state.maintenanceHistory.any { it.itemId == snapshot.item.id },
                            onComplete = { id, labor, parts, note, part, serviceAt -> onCompleteMaintenance(id, labor, parts, note, part, serviceAt) },
                            onCompleteInsurance = onCompleteInsurance,
                            onAddRepair = onAddRepair,
                            onViewHistory = { viewingHistoryItemId = it }
                        )
                    }
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun MaintenanceProgressCard(
    snapshot: MaintenanceSnapshot,
    currentMileage: Double,
    hasHistory: Boolean,
    onComplete: (String, Double, Double, String, String, Long) -> Unit,
    onCompleteInsurance: (String, String, String, String, String) -> Unit,
    onAddRepair: (String, String, String, String, String, String, String) -> Unit,
    onViewHistory: (String) -> Unit = {}
) {
    var showConfirm by remember { mutableStateOf(false) }
    val mileageRatio = if (snapshot.item.intervalKm > 0)
        (snapshot.remainingKm.toFloat() / snapshot.item.intervalKm).coerceIn(0f, 1f) else 1f
    val calendarRatio = if (snapshot.item.intervalDays > 0)
        (snapshot.remainingDays.toFloat() / snapshot.item.intervalDays).coerceIn(0f, 1f) else 1f
    val remainText = buildString {
        if (snapshot.item.intervalKm > 0) {
            if (snapshot.remainingKm > 0) append("${snapshot.remainingKm} km") else append("已超 ${kotlin.math.abs(snapshot.remainingKm)} km")
        }
        if (snapshot.item.intervalDays > 0) {
            if (snapshot.item.intervalKm > 0) append(" · ")
            if (snapshot.remainingDays > 0) append("${snapshot.remainingDays} 天") else append("已超 ${kotlin.math.abs(snapshot.remainingDays)} 天")
        }
    }
    CardBlock(
        onClick = { onViewHistory(snapshot.item.id) }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(maintenanceIcon(snapshot.item.name), contentDescription = null, tint = maintenanceIconColor(snapshot.item.name), modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text(snapshot.item.name, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            GlassDialogAction(
                onClick = { showConfirm = true }) { Text(if (snapshot.item.itemType.isInsurance) "续保" else "维护", fontWeight = FontWeight.Bold) }
        }
        val displayedPart = if (snapshot.item.itemType == MaintenanceItemType.Periodic)
            snapshot.item.lastPartNumber.ifBlank { defaultPartHint(snapshot.item.name) } else ""
        if (displayedPart.isNotBlank()) {
            Text(displayedPart, color = TextTertiary, fontSize = 12.sp)
        }
        if (snapshot.item.intervalKm > 0) {
            CompactProgress("里程寿命", mileageRatio, Modifier.fillMaxWidth())
        }
        if (snapshot.item.intervalDays > 0) {
            CompactProgress("日历寿命", calendarRatio, Modifier.fillMaxWidth())
        }
        if (snapshot.item.itemType == MaintenanceItemType.RepairInspection) {
            Text("临时维修与检查记账", color = TextTertiary, fontSize = 12.sp)
        } else {
            if (snapshot.item.itemType.isInsurance && !hasHistory) {
                Text("尚未记录续保", color = Warning, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
            Row(Modifier.fillMaxWidth()) {
                Text("剩余 $remainText", color = TextSecondary, fontSize = 12.sp, modifier = Modifier.weight(1f))
                if (snapshot.item.intervalKm > 0) Text("到期 ${formatMileage(snapshot.dueMileage)} km", color = TextTertiary, fontSize = 11.sp)
                if (snapshot.item.itemType.isInsurance) Text("到期 ${SimpleDateFormat("yyyy/MM/dd", Locale.CHINA).format(Date(snapshot.dueAt))}", color = TextTertiary, fontSize = 11.sp)
            }
        }
    }
    if (showConfirm) {
        var laborText by remember { mutableStateOf("") }
        var partsText by remember { mutableStateOf("") }
        var noteText by remember { mutableStateOf("") }
        var partNumberText by remember { mutableStateOf(defaultPartHint(snapshot.item.name)) }
        var recordName by remember { mutableStateOf("") }
        var mileageText by remember { mutableStateOf(formatDecimal(currentMileage)) }
        var timeText by remember { mutableStateOf(SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date())) }
        var timeMillis by remember { mutableStateOf(System.currentTimeMillis()) }
        var showTimePicker by remember { mutableStateOf(false) }
        var expiresMillis by remember { mutableStateOf(Calendar.getInstance().apply { add(Calendar.YEAR, 1) }.timeInMillis) }
        var expiresText by remember { mutableStateOf(SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date(expiresMillis))) }
        var showExpiresPicker by remember { mutableStateOf(false) }
        GlassDialog(
            onDismissRequest = { showConfirm = false },
            title = when {
                snapshot.item.itemType.isInsurance -> "记录续保"
                snapshot.item.itemType == MaintenanceItemType.RepairInspection -> "维修/检查记账"
                else -> "确认维护"
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    when {
                        snapshot.item.itemType.isInsurance -> {
                            Text(snapshot.item.name)
                            SelectRow("缴费时间", timeText) { showTimePicker = true }
                            SelectRow("到期时间", expiresText) { showExpiresPicker = true }
                            AppTextField("金额", partsText, { partsText = it }, "元", KeyboardType.Decimal)
                        }
                        snapshot.item.itemType == MaintenanceItemType.RepairInspection -> {
                            AppTextField("名称", recordName, { recordName = it }, "", KeyboardType.Text)
                            SelectRow("发生时间", timeText) { showTimePicker = true }
                            AppTextField("里程", mileageText, { mileageText = it }, "km", KeyboardType.Decimal)
                            AppTextField("金额", partsText, { partsText = it }, "元", KeyboardType.Decimal)
                            AppTextField("工时费", laborText, { laborText = it }, "元", KeyboardType.Decimal)
                        }
                        else -> {
                            Text("${snapshot.item.name} 已完成？")
                            SelectRow("维护时间", timeText) { showTimePicker = true }
                            AppTextField("零件型号/名称", partNumberText, { partNumberText = it }, "", KeyboardType.Text)
                            AppTextField("零件费", partsText, { partsText = it }, "元", KeyboardType.Decimal)
                            AppTextField("工时费", laborText, { laborText = it }, "元", KeyboardType.Decimal)
                        }
                    }
                    AppTextField("备注", noteText, { noteText = it }, "", KeyboardType.Text)
                }
            },
            confirmButton = {
                GlassDialogAction(onClick = {
                    showConfirm = false
                    when {
                        snapshot.item.itemType.isInsurance -> onCompleteInsurance(snapshot.item.id, timeText, expiresText, partsText.ifBlank { "0.00" }, noteText)
                        snapshot.item.itemType == MaintenanceItemType.RepairInspection -> onAddRepair(snapshot.item.id, recordName, timeText, mileageText, partsText.ifBlank { "0.00" }, laborText.ifBlank { "0.00" }, noteText)
                        else -> onComplete(snapshot.item.id, laborText.toDoubleOrNull() ?: 0.0, partsText.toDoubleOrNull() ?: 0.0, noteText, partNumberText, timeMillis)
                    }
                }) { Text("确认", color = PrimaryBlue) }
            },
            dismissButton = { GlassDialogAction(onClick = { showConfirm = false }) { Text("取消", color = TextSecondary) } }
        )
        if (showTimePicker) {
            GlassDateTimePickerDialog(
                initialMillis = timeMillis,
                onDismissRequest = { showTimePicker = false },
                onConfirm = { millis ->
                    timeMillis = millis
                    timeText = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date(millis))
                    expiresMillis = Calendar.getInstance().apply { timeInMillis = millis; add(Calendar.YEAR, 1) }.timeInMillis
                    expiresText = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date(expiresMillis))
                    showTimePicker = false
                }
            )
        }
        if (showExpiresPicker) {
            GlassDateTimePickerDialog(
                initialMillis = expiresMillis,
                onDismissRequest = { showExpiresPicker = false },
                onConfirm = { millis ->
                    expiresMillis = millis
                    expiresText = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date(millis))
                    showExpiresPicker = false
                }
            )
        }
    }
}

@Composable
private fun CompactProgress(label: String, ratio: Float, modifier: Modifier = Modifier) {
    val isCalendar = label.startsWith("日历")
    val status = if (isCalendar) when {
        ratio > 0.8f -> "崭新"
        ratio > 0.6f -> "较新"
        ratio > 0.4f -> "正常"
        ratio > 0.2f -> "偏旧"
        ratio > 0f -> "陈旧"
        else -> "过期"
    } else when {
        ratio > 0.8f -> "健康"
        ratio > 0.6f -> "良好"
        ratio > 0.4f -> "正常"
        ratio > 0.2f -> "较差"
        ratio > 0f -> "极差"
        else -> "高危"
    }
    val color = when {
        ratio > 0.8f -> Success
        ratio > 0.6f -> Color(0xFF4CAF50)
        ratio > 0.4f -> PrimaryBlue
        ratio > 0.2f -> Warning
        else -> Error
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        Text(label, color = TextTertiary, fontSize = 12.sp, modifier = Modifier.width(52.dp))
        Spacer(Modifier.width(6.dp))
        Box(modifier = Modifier.weight(1f).height(5.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFFE0E0E0))) {
            Box(modifier = Modifier.fillMaxWidth(ratio).fillMaxHeight().background(color))
        }
        Spacer(Modifier.width(6.dp))
        Text(status, color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun MaintenanceEditScreen(
    items: List<MaintenanceSnapshot>,
    onBack: () -> Unit,
    onUpdate: (String, String, String, String) -> Unit,
    onDelete: (String) -> Unit,
    onAdd: (String, String, String) -> Unit
) {
    var showAdd by remember { mutableStateOf(false) }
    var deleteConfirmId by remember { mutableStateOf<String?>(null) }
    Page("管理保养项目") {
        BackButton(onBack)
        GlassActionButton("新增保养项目", Icons.Outlined.Add, PrimaryBlue) { showAdd = true }
        if (items.isEmpty()) {
            InfoCard("暂无项目", "点击顶部按钮新增保养项目。")
        } else {
            items.forEach { snapshot ->
                val item = snapshot.item
                var name by rememberSaveable(item.id) { mutableStateOf(item.name) }
                var km by rememberSaveable(item.id) { mutableStateOf(item.intervalKm.toString()) }
                var days by rememberSaveable(item.id) { mutableStateOf(item.intervalDays.toString()) }
                CardBlock {
                    AppTextField("项目名称", name, { name = it }, "", KeyboardType.Text, enabled = !item.itemType.isProtected)
                    if (item.itemType == MaintenanceItemType.Periodic) {
                        AppTextField("里程周期", km, { km = it }, "km", KeyboardType.Number)
                    }
                    if (item.itemType != MaintenanceItemType.RepairInspection) {
                        AppTextField("时间周期", days, { days = it }, "天", KeyboardType.Number)
                    } else {
                        Text("无里程和日历寿命 · 每次维护作为检修记账", color = TextTertiary, fontSize = 12.sp)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GlassActionButton("保存修改", Icons.Outlined.Save, PrimaryBlue, Modifier.weight(1f)) { onUpdate(item.id, name, km, days) }
                        if (!item.itemType.isProtected) {
                            GlassDialogAction(onClick = { deleteConfirmId = item.id }) { Text("删除", color = Error) }
                        }
                    }
                }
            }
        }
    }
    if (showAdd) {
        MaintenanceEditorDialog(
            onDismiss = { showAdd = false },
            onSave = { name, km, days -> onAdd(name, km, days); showAdd = false }
        )
    }
    deleteConfirmId?.let { id ->
        GlassDialog(
            onDismissRequest = { deleteConfirmId = null },
            title = "删除项目",
            text = { Text("确认删除此保养项目？") },
            confirmButton = { GlassDialogAction(onClick = { onDelete(id); deleteConfirmId = null }) { Text("删除", color = Error) } },
            dismissButton = { GlassDialogAction(onClick = { deleteConfirmId = null }) { Text("取消") } }
        )
    }
}

@Composable
private fun MaintenanceEditorDialog(onDismiss: () -> Unit, onSave: (String, String, String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var km by rememberSaveable { mutableStateOf("5000") }
    var days by rememberSaveable { mutableStateOf("180") }
    GlassDialog(
        onDismissRequest = onDismiss,
        title = "新增保养项目",
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AppTextField("项目名称", name, { name = it }, "", KeyboardType.Text)
                AppTextField("里程周期", km, { km = it }, "km", KeyboardType.Number)
                AppTextField("时间周期", days, { days = it }, "天", KeyboardType.Number)
            }
        },
        confirmButton = { GlassDialogAction(onClick = { onSave(name, km, days) }) { Text("保存") } },
        dismissButton = { GlassDialogAction(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun MaintenanceHistoryScreen(
    itemName: String,
    itemType: MaintenanceItemType,
    historyFlow: kotlinx.coroutines.flow.Flow<List<MaintenanceHistoryEntity>>,
    onBack: () -> Unit,
    onUpdateHistory: (String, String, String, String, String, String, String) -> Unit = { _, _, _, _, _, _, _ -> },
    onUpdateSpecialHistory: (String, String, String, String, String, String, String, String) -> Unit = { _, _, _, _, _, _, _, _ -> },
    onDeleteHistory: (String) -> Unit = {}
) {
    val history by historyFlow.collectAsState(initial = emptyList())
    var editingId by remember { mutableStateOf<String?>(null) }
    var deletingRecord by remember { mutableStateOf<MaintenanceHistoryEntity?>(null) }
    val editingRecord = editingId?.let { id -> history.firstOrNull { it.id == id } }

    fun <T> emit(vararg values: T) {} // no-op for context

    Page("$itemName · 维护记录") {
        BackButton(onBack)
        if (history.isEmpty()) {
            InfoCard("暂无记录", if (itemType.isInsurance) "首次续保后才会生成第一条记录。" else "完成维护后记录会显示在这里。")
        } else {
            history.forEach { h ->
                CardBlock(onClick = { editingId = h.id }, onLongClick = { deletingRecord = h }) {
                    Row(Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f)) {
                            val serviceTime = remember(h.serviceAt) {
                                SimpleDateFormat("yyyy/MM/dd HH:mm", java.util.Locale.CHINA).format(Date(h.serviceAt))
                            }
                            Text(if (itemType == MaintenanceItemType.RepairInspection) h.recordName else if (itemType.isInsurance) h.itemName else "${formatMileage(h.serviceMileage)} km", fontWeight = FontWeight.Bold)
                            Text(serviceTime, color = TextTertiary, fontSize = 13.sp)
                            if (itemType == MaintenanceItemType.RepairInspection) Text("${formatMileage(h.serviceMileage)} km", color = TextSecondary, fontSize = 13.sp)
                            if (itemType.isInsurance && h.expiresAt != null) {
                                Text("到期 ${SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.CHINA).format(Date(h.expiresAt))}", color = TextTertiary, fontSize = 12.sp)
                            }
                            if (itemType == MaintenanceItemType.Periodic && h.partNumber.isNotBlank()) Text(h.partNumber, color = TextSecondary, fontSize = 13.sp)
                            if (h.note.isNotBlank()) Text(h.note, color = TextTertiary, fontSize = 12.sp)
                            when {
                                itemType.isInsurance -> Text("金额 ¥${formatMoney(h.insuranceAmount)}", color = TextSecondary, fontSize = 13.sp)
                                itemType == MaintenanceItemType.RepairInspection -> Text("金额 ¥${formatMoney(h.repairAmount)}  工时 ¥${formatMoney(h.laborCost)}", color = TextSecondary, fontSize = 13.sp)
                                h.laborCost > 0 || h.partsCost > 0 -> Text("零件 ¥${formatMoney(h.partsCost)}  工时 ¥${formatMoney(h.laborCost)}", color = TextSecondary, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    editingRecord?.let { r ->
        var editKm by remember(r.id) { mutableStateOf(r.serviceMileage.toString()) }
        var editPart by remember(r.id) { mutableStateOf(if (itemType == MaintenanceItemType.RepairInspection) r.recordName else r.partNumber) }
        var editPartsCost by remember(r.id) {
            val value = when {
                itemType.isInsurance -> r.insuranceAmount
                itemType == MaintenanceItemType.RepairInspection -> r.repairAmount
                else -> r.partsCost
            }
            mutableStateOf(formatDecimal(value))
        }
        var editLaborCost by remember(r.id) { mutableStateOf(if (r.laborCost > 0) r.laborCost.trimOne() else "") }
        var editNote by remember(r.id) { mutableStateOf(r.note) }
        var editTime by remember(r.id) {
            mutableStateOf(SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date(r.serviceAt)))
        }
        var editTimeMillis by remember(r.id) { mutableStateOf(r.serviceAt) }
        var showEditTimePicker by remember(r.id) { mutableStateOf(false) }
        var editExpiresMillis by remember(r.id) {
            mutableStateOf(r.expiresAt ?: Calendar.getInstance().apply { timeInMillis = r.serviceAt; add(Calendar.YEAR, 1) }.timeInMillis)
        }
        var editExpires by remember(r.id) {
            mutableStateOf(SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date(editExpiresMillis)))
        }
        var showEditExpiresPicker by remember(r.id) { mutableStateOf(false) }
        if (!showEditTimePicker) GlassDialog(
            onDismissRequest = { editingId = null },
            title = "编辑记录",
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (itemType == MaintenanceItemType.RepairInspection) AppTextField("名称", editPart, { editPart = it }, "", KeyboardType.Text)
                    if (!itemType.isInsurance) AppTextField("里程", editKm, { editKm = it }, "km", KeyboardType.Decimal)
                    SelectRow(if (itemType.isInsurance) "缴费时间" else "维护时间", editTime) { showEditTimePicker = true }
                    if (itemType.isInsurance) SelectRow("到期时间", editExpires) { showEditExpiresPicker = true }
                    when {
                        itemType.isInsurance -> AppTextField("金额", editPartsCost, { editPartsCost = it }, "元", KeyboardType.Decimal)
                        itemType == MaintenanceItemType.RepairInspection -> {
                            AppTextField("金额", editPartsCost, { editPartsCost = it }, "元", KeyboardType.Decimal)
                            AppTextField("工时费", editLaborCost, { editLaborCost = it }, "元", KeyboardType.Decimal)
                        }
                        else -> {
                            AppTextField("零件型号/名称", editPart, { editPart = it }, "", KeyboardType.Text)
                            AppTextField("零件费", editPartsCost, { editPartsCost = it }, "元", KeyboardType.Decimal)
                            AppTextField("工时费", editLaborCost, { editLaborCost = it }, "元", KeyboardType.Decimal)
                        }
                    }
                    AppTextField("备注", editNote, { editNote = it }, "", KeyboardType.Text)
                }
            },
            confirmButton = {
                GlassDialogAction(onClick = {
                    if (itemType == MaintenanceItemType.Periodic) {
                        onUpdateHistory(r.id, editKm, editTime, editPart, editPartsCost, editLaborCost, editNote)
                    } else {
                        onUpdateSpecialHistory(r.id, if (itemType == MaintenanceItemType.RepairInspection) editPart else itemName, editTime, if (itemType.isInsurance) editExpires else "", editKm, editPartsCost, editLaborCost, editNote)
                    }
                    editingId = null
                }) { Text("保存修改") }
            },
            dismissButton = { GlassDialogAction(onClick = { editingId = null }) { Text("取消") } }
        )
        if (showEditTimePicker) {
            GlassDateTimePickerDialog(
                initialMillis = editTimeMillis,
                onDismissRequest = { showEditTimePicker = false },
                onConfirm = { millis ->
                    editTimeMillis = millis
                    editTime = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date(millis))
                    showEditTimePicker = false
                }
            )
        }
        if (showEditExpiresPicker) {
            GlassDateTimePickerDialog(
                initialMillis = editExpiresMillis,
                onDismissRequest = { showEditExpiresPicker = false },
                onConfirm = { millis ->
                    editExpiresMillis = millis
                    editExpires = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date(millis))
                    showEditExpiresPicker = false
                }
            )
        }
    }
    deletingRecord?.let { record ->
        GlassDialog(
            onDismissRequest = { deletingRecord = null },
            title = "删除保养记录",
            text = { Text("确认删除“${record.recordName}”这条记录？") },
            confirmButton = {
                GlassDialogAction(onClick = {
                    onDeleteHistory(record.id)
                    deletingRecord = null
                }) { Text("删除", color = Error) }
            },
            dismissButton = { GlassDialogAction(onClick = { deletingRecord = null }) { Text("取消") } }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VehicleCard(
    vehicle: VehicleEntity,
    selected: Boolean,
    consumption: VehicleConsumptionSummary? = null,
    onClick: () -> Unit,
    onLongDelete: (() -> Unit)? = null,
    /** 外层修饰符 */
    modifier: Modifier = Modifier,
    /** 弹窗/弹层内使用 */
    solid: Boolean = false
) {
    val typeColor = vehicleTypeColor(vehicle)
    val interactionModifier = if (onLongDelete == null) {
        Modifier
            .clip(GlassTokens.shapeCard)
            .glassClickable(onClick = onClick)
    } else {
        Modifier
            .clip(GlassTokens.shapeCard)
            .glassCombinedClickable(onClick = onClick, onLongClick = onLongDelete)
    }
    CardBlock(
        modifier = modifier.then(interactionModifier),
        level = if (selected) GlassLevel.L2_Panel else GlassLevel.L1_Card,
        solidFill = if (solid) solidCardFill() else null
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                VehicleIconBadge(
                    vehicle,
                    size = 50.dp,
                    iconSize = 27.dp,
                    modifier = Modifier.offset(y = (-3).dp)
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            vehicle.name,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "${formatMileage(vehicle.currentMileage)} km",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black,
                            color = typeColor,
                            maxLines = 1
                        )
                    }
                    Text(
                        text = vehicle.model,
                        color = TextSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = vehicle.plate,
                    modifier = Modifier.weight(1f),
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "${vehicle.category.label} · ${vehicle.energyType.label} · ${vehicle.transmissionType.label}",
                    modifier = Modifier.weight(1.35f),
                    color = TextTertiary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.End
                )
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = consumption?.fuel?.let { "油耗 $it" }.orEmpty(),
                        color = typeColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.width(12.dp))
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Text(
                        text = consumption?.charge?.let { "电耗 $it" }.orEmpty(),
                        color = typeColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.End
                    )
                }
            }
        }
    }
}

private fun consumptionLines(summary: VehicleConsumptionSummary?): List<String> = buildList {
    summary?.fuel?.let { add("油耗 $it") }
    summary?.charge?.let { add("电耗 $it") }
}

@Composable
private fun VehicleIconBadge(
    vehicle: VehicleEntity?,
    size: androidx.compose.ui.unit.Dp,
    iconSize: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier
) {
    val color = vehicleTypeColor(vehicle)
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size / 2))
            .background(color.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(vehicleTypeIcon(vehicle), contentDescription = null, tint = color, modifier = Modifier.size(iconSize))
    }
}

private fun vehicleTypeIcon(vehicle: VehicleEntity?): ImageVector = when {
    vehicle == null -> Icons.Outlined.DirectionsCar
    vehicle.category == VehicleCategory.Car && vehicle.energyType != EnergyType.Fuel -> Icons.Outlined.ElectricCar
    vehicle.category == VehicleCategory.Car -> Icons.Outlined.DirectionsCar
    vehicle.category == VehicleCategory.Motorcycle -> Icons.Outlined.TwoWheeler
    vehicle.category == VehicleCategory.ElectricMotorcycle -> Icons.Outlined.ElectricMoped
    vehicle.category == VehicleCategory.ElectricBike -> Icons.Outlined.ElectricBike
    vehicle.category == VehicleCategory.Bicycle -> Icons.Outlined.PedalBike
    else -> Icons.Outlined.DirectionsCar
}

private fun vehicleTypeColor(vehicle: VehicleEntity?): Color = when {
    vehicle == null -> PrimaryBlue
    vehicle.category == VehicleCategory.Car && vehicle.energyType != EnergyType.Fuel -> Color(0xFF0070EB)
    vehicle.category == VehicleCategory.Car -> PrimaryBlue
    vehicle.category == VehicleCategory.Motorcycle -> Color(0xFFE68A2E)
    vehicle.category == VehicleCategory.ElectricMotorcycle -> Color(0xFF1D9B58)
    vehicle.category == VehicleCategory.ElectricBike -> Color(0xFF1D9B58)
    vehicle.category == VehicleCategory.Bicycle -> Success
    else -> PrimaryBlue
}

private fun defaultPartHint(name: String): String = when {
    name.contains("机油") -> "5W-30 全合成"
    name.contains("机滤") -> "机油滤芯"
    name.contains("空滤") -> "原厂空气滤芯"
    name.contains("空调") -> "活性炭空调滤芯"
    name.contains("火花") -> "NGK 铱金火花塞"
    name.contains("刹车油") -> "DOT4 刹车油"
    name.contains("刹车片") -> "陶瓷刹车片"
    name.contains("轮胎") -> "原厂规格轮胎"
    name.contains("链条") -> "DID 520 链条"
    name.contains("皮带") || name.contains("传动") -> "原厂传动皮带"
    name.contains("电池") || name.contains("电瓶") -> "12V 标准电瓶"
    name.contains("防冻") || name.contains("冷却") -> "长效防冻液"
    name.contains("变速") -> "原厂变速箱油"
    name.contains("减速器") -> "减速器油"
    name.contains("普利珠") -> "原厂普利珠"
    name.contains("前叉") || name.contains("减震") -> "原厂减震油"
    name.contains("刹车线") -> "刹车线"
    name.contains("中轴") -> "中轴轴承"
    name.contains("花鼓") -> "花鼓轴承"
    else -> ""
}

private fun formatMileage(value: Int): String = formatMileage(value.toDouble())

private fun formatMileage(value: Double): String =
    String.format(java.util.Locale.CHINA, "%,.2f", value)

private fun formatMoney(value: Double): String =
    String.format(java.util.Locale.CHINA, "%.2f", value)

private fun maintenanceIcon(name: String): ImageVector = when {
    name == "商业险" || name == "交强险" -> Icons.Outlined.Shield
    name == "维修/检查" -> Icons.Outlined.CarRepair
    name.contains("机油") -> Icons.Outlined.WaterDrop
    name.contains("机油滤") || name.contains("燃油滤") -> Icons.Outlined.LocalGasStation
    name.contains("空气") || name.contains("空滤") -> Icons.Outlined.Air
    name.contains("空调") || name.contains("滤芯") -> Icons.Outlined.AcUnit
    name.contains("火花") -> Icons.Outlined.Bolt
    name.contains("制动液") || name.contains("刹车油") -> Icons.Outlined.WaterDrop
    name.contains("制动") || name.contains("刹车") -> Icons.Outlined.DiscFull
    name.contains("防冻") || name.contains("冷却") -> Icons.Outlined.AcUnit
    name.contains("轮胎") -> Icons.Outlined.DiscFull
    name.contains("链条") || name.contains("皮带") -> Icons.Outlined.Link
    name.contains("电池") || name.contains("充电") -> Icons.Outlined.BatteryChargingFull
    name.contains("电机") || name.contains("轮毂") || name.contains("控制器") -> Icons.Outlined.Bolt
    name.contains("变速") || name.contains("变速箱") || name.contains("离合") || name.contains("普利珠") || name.contains("传动") -> Icons.Outlined.Settings
    name.contains("前叉") || name.contains("减震") || name.contains("车架") || name.contains("螺丝") -> Icons.Outlined.Handyman
    name.contains("中轴") || name.contains("花鼓") || name.contains("轴承") -> Icons.Outlined.DiscFull
    else -> Icons.Outlined.Build
}

private fun maintenanceIconColor(name: String): Color = when {
    name == "商业险" || name == "交强险" -> Color(0xFF7C5CE7)
    name == "维修/检查" -> Warning
    name.contains("机油") || name.contains("制动液") || name.contains("刹车油") || name.contains("冷却") || name.contains("防冻") -> Color(0xFF0070EB)
    name.contains("空气") || name.contains("空调") || name.contains("滤芯") -> Color(0xFF0F9F6E)
    name.contains("火花") || name.contains("电池") || name.contains("充电") || name.contains("电机") || name.contains("控制器") -> Color(0xFF8A5CF6)
    name.contains("制动") || name.contains("刹车") || name.contains("轮胎") || name.contains("轮毂") -> Color(0xFFE68A2E)
    name.contains("链条") || name.contains("皮带") || name.contains("变速") || name.contains("离合") || name.contains("传动") || name.contains("轴") || name.contains("花鼓") -> Color(0xFF6B7280)
    else -> PrimaryBlue
}

private val ArchiveAccent = Color(0xFF7C5CE7)

/** 通用行 */
@Composable
private fun ListRow(
    icon: ImageVector,
    tint: Color,
    title: String,
    subtitle: String? = null,
    footer: String? = null,
    trailingText: String? = null,
    chevron: Boolean = false,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null && enabled) Modifier.glassClickable(onClick = onClick) else Modifier
            )
            .then(if (enabled) Modifier else Modifier.graphicsLayer { alpha = 0.42f })
            .padding(horizontal = 8.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(tint.copy(alpha = 0.13f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(21.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = if (enabled) TextPrimary else TextTertiary,
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold
            )
            if (subtitle != null) {
                Text(subtitle, color = TextTertiary, fontSize = 12.sp, lineHeight = 17.sp)
            }
            if (footer != null) {
                Text(footer, color = TextTertiary, fontSize = 11.sp, lineHeight = 16.sp)
            }
        }
        if (trailingText != null) {
            Text(trailingText, color = tint, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        if (chevron) {
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = TextTertiary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/** 行与行之间的发丝分隔线 */
@Composable
private fun ListRowDivider() {
    Box(
        Modifier
            .padding(horizontal = 8.dp, vertical = 3.dp)
            .fillMaxWidth()
            .height(1.dp)
            .background(GlassTokens.strokeHairline)
    )
}

/** 概览卡里的一个数字格 */
@Composable
private fun StatCell(value: String, label: String) {
    Column {
        Text(value, color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Black)
        Text(label, color = TextTertiary, fontSize = 12.sp)
    }
}

@Composable
private fun RowAction(
    text: String,
    icon: ImageVector?,
    color: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    GlassDialogAction(onClick = onClick, enabled = enabled, modifier = modifier.height(36.dp)) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, color = color, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun AuthActionButton(
    text: String,
    primary: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    onClick: () -> Unit
) {
    val solid = primary && enabled
    val ink = when {
        solid -> Color.White
        enabled -> PrimaryBlue
        else -> TextTertiary
    }
    GlassDialogAction(
        onClick = onClick,
        enabled = enabled,
        accent = if (solid) PrimaryBlue else null,
        modifier = modifier.fillMaxWidth().height(GlassTokens.actionHeightDp.dp)
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = ink, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text,
            color = ink,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

/** 子页统一的返回行 */
@Composable
private fun BackToSettingsRow(onBack: () -> Unit) {
    GlassActionButton(
        "返回设置", Icons.AutoMirrored.Outlined.ArrowBack, TextSecondary, onClick = onBack
    )
}

/** 只数写了多少字节 */
private class CountingOutputStream(target: java.io.OutputStream) : java.io.FilterOutputStream(target) {
    var count: Long = 0L
        private set

    override fun write(b: Int) {
        super.write(b)
        count++
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        super.write(b, off, len)
        count += len
    }
}

private enum class CloudAuthPage {
    Home,
    Login,
    RegisterEmail,
    RegisterVerify,
    Privacy,

    /** 忘记密码第 1 步 */
    ForgotEmail,

    /** 忘记密码第 2 步 */
    ForgotVerify,
}

private val CloudAuthPage.depth: Int
    get() = when (this) {
        CloudAuthPage.Home -> 0
        CloudAuthPage.Login, CloudAuthPage.RegisterEmail, CloudAuthPage.ForgotEmail, CloudAuthPage.Privacy -> 1
        CloudAuthPage.RegisterVerify, CloudAuthPage.ForgotVerify -> 2
    }

private const val CODE_RESEND_SECONDS = 60

private const val CODE_TTL_SECONDS = 600

/** 验证码位数 */
private const val CODE_LENGTH = 6

@Composable
private fun CloudBackupScreen(
    email: String?,
    busy: String?,
    cloudBackupEnabled: Boolean,
    cloudUsage: CloudUsage?,
    cloudProgress: CloudProgress?,
    accountDeletionAt: Long?,
    backupRateLimitKbps: Int?,
    onRefreshCloudUsage: () -> Unit,
    onPurgeCloudBackup: () -> Unit,
    onDeleteAccount: (String) -> Unit,
    onCancelDeletion: () -> Unit,
    registerCodeState: RegisterCodeState,
    forgotState: ForgotPasswordState,
    onSendCode: (String) -> Unit,
    onClearRegisterCodeState: () -> Unit,
    onSendResetCode: (String) -> Unit,
    onClearForgotState: () -> Unit,
    onResetPassword: (String, String, String) -> Unit,
    onRegister: (String, String, String) -> Unit,
    onLogin: (String, String) -> Unit,
    onLogout: () -> Unit,
    onUpload: () -> Unit,
    onRestore: () -> Unit,
    onBack: () -> Unit
) {
    var page by rememberSaveable {
        mutableStateOf(
            if (!BuildConfig.DEBUG) {
                CloudAuthPage.Home
            } else {
                when {
                    ChekuDebugNav.takePage("Login") -> CloudAuthPage.Login
                    ChekuDebugNav.takePage("Register") -> CloudAuthPage.RegisterEmail
                    ChekuDebugNav.takePage("Verify") -> CloudAuthPage.RegisterVerify
                    ChekuDebugNav.takePage("Privacy") -> CloudAuthPage.Privacy
                    else -> CloudAuthPage.Home
                }
            }
        )
    }
    var inputEmail by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirmPassword by rememberSaveable { mutableStateOf("") }
    /** 注销账号的二次确认弹窗 */
    var showDeleteAccountDialog by rememberSaveable { mutableStateOf(false) }
    var deleteAccountPassword by rememberSaveable { mutableStateOf("") }
    var showRestoreConfirm by rememberSaveable { mutableStateOf(false) }
    var code by rememberSaveable { mutableStateOf("") }
    /** 验证码发往哪个邮箱 */
    var codeMail by rememberSaveable { mutableStateOf("") }
    /** 验证码发送时刻 */
    var codeSentAt by rememberSaveable { mutableStateOf(0L) }
    /** 距上次发码过了多少秒，由 1Hz 心跳推进 */
    var secondsSinceSend by rememberSaveable { mutableStateOf(-1) }

    var forgotPassword by rememberSaveable { mutableStateOf("") }
    var forgotConfirmPassword by rememberSaveable { mutableStateOf("") }

    var awaitingCode by rememberSaveable { mutableStateOf(false) }
    var awaitingForgotCode by rememberSaveable { mutableStateOf(false) }

    var rateLimitUntil by rememberSaveable { mutableStateOf(0L) }
    var rateLimitNow by rememberSaveable { mutableStateOf(0L) }
    var rateLimitMessage by rememberSaveable { mutableStateOf("") }
    var forgotRateLimitUntil by rememberSaveable { mutableStateOf(0L) }
    var forgotRateLimitNow by rememberSaveable { mutableStateOf(0L) }
    var forgotRateLimitMessage by rememberSaveable { mutableStateOf("") }

    val working = busy != null

    // 1Hz 心跳：驱动「重发冷却」与「剩余有效期」两个倒计时。到 TTL 自动停，不留常驻循环。
    LaunchedEffect(codeSentAt) {
        if (codeSentAt <= 0L) {
            secondsSinceSend = -1
            return@LaunchedEffect
        }
        while (true) {
            delay(1000)
            val s = ((System.currentTimeMillis() - codeSentAt) / 1000).toInt()
            secondsSinceSend = s
            if (s >= CODE_TTL_SECONDS) break
        }
    }
    // 登录 / 注册成功后（email 变非空）回到首页，并清掉表单里的凭据。
    LaunchedEffect(email) {
        if (email != null) {
            page = CloudAuthPage.Home
            password = ""
            confirmPassword = ""
            code = ""
        }
    }

    // 限流倒计时：只在「当前时刻」推进，用于显示剩余等待时间（到点自动解除按钮禁用）。
    LaunchedEffect(rateLimitUntil) {
        while (rateLimitUntil > 0L && System.currentTimeMillis() < rateLimitUntil) {
            rateLimitNow = System.currentTimeMillis()
            delay(1000)
        }
        rateLimitNow = System.currentTimeMillis()
    }
    LaunchedEffect(forgotRateLimitUntil) {
        while (forgotRateLimitUntil > 0L && System.currentTimeMillis() < forgotRateLimitUntil) {
            forgotRateLimitNow = System.currentTimeMillis()
            delay(1000)
        }
        forgotRateLimitNow = System.currentTimeMillis()
    }
    LaunchedEffect(registerCodeState, awaitingCode) {
        if (!awaitingCode) return@LaunchedEffect
        when (val state = registerCodeState) {
            is RegisterCodeState.Sent -> {
                awaitingCode = false
                codeMail = state.email
                code = ""
                codeSentAt = System.currentTimeMillis()
                secondsSinceSend = 0
                rateLimitMessage = ""
                rateLimitUntil = 0L
                page = CloudAuthPage.RegisterVerify
            }
            is RegisterCodeState.RateLimited -> {
                awaitingCode = false
                applyRateLimit(state.retryAfterSeconds) { until, message ->
                    rateLimitUntil = until
                    rateLimitMessage = message
                }
            }
            is RegisterCodeState.Failed -> {
                awaitingCode = false
                rateLimitUntil = 0L
                rateLimitMessage = state.message
            }
            RegisterCodeState.Idle -> Unit
        }
    }
    // 忘记密码 · 发码结果。
    LaunchedEffect(forgotState, awaitingForgotCode) {
        if (!awaitingForgotCode) return@LaunchedEffect
        when (val state = forgotState) {
            is ForgotPasswordState.CodeSent -> {
                awaitingForgotCode = false
                codeMail = state.email
                code = ""
                codeSentAt = System.currentTimeMillis()
                secondsSinceSend = 0
                forgotRateLimitMessage = ""
                forgotRateLimitUntil = 0L
                page = CloudAuthPage.ForgotVerify
            }
            is ForgotPasswordState.RateLimited -> {
                awaitingForgotCode = false
                applyRateLimit(state.retryAfterSeconds) { until, message ->
                    forgotRateLimitUntil = until
                    forgotRateLimitMessage = message
                }
            }
            is ForgotPasswordState.Failed -> {
                awaitingForgotCode = false
                forgotRateLimitUntil = 0L
                forgotRateLimitMessage = state.message
            }
            ForgotPasswordState.PasswordReset -> awaitingForgotCode = false
            ForgotPasswordState.Idle -> Unit
        }
    }
    // 重置成功后回到登录页（服务端已让旧登录状态失效，不自动登录）。
    LaunchedEffect(forgotState) {
        if (forgotState is ForgotPasswordState.PasswordReset) {
            page = CloudAuthPage.Login
            password = ""
            forgotPassword = ""
            forgotConfirmPassword = ""
            code = ""
        }
    }

    BackHandler {
        when (page) {
            CloudAuthPage.Home -> onBack()
            CloudAuthPage.RegisterVerify -> {
                onClearRegisterCodeState()
                page = CloudAuthPage.RegisterEmail
            }
            CloudAuthPage.ForgotVerify -> {
                onClearForgotState()
                page = CloudAuthPage.ForgotEmail
            }
            else -> page = CloudAuthPage.Home
        }
    }

    val resendLeft = if (secondsSinceSend < 0) 0 else (CODE_RESEND_SECONDS - secondsSinceSend).coerceAtLeast(0)
    val ttlLeft = if (secondsSinceSend < 0) CODE_TTL_SECONDS else (CODE_TTL_SECONDS - secondsSinceSend).coerceAtLeast(0)
    val rateLimitLeft = rateLimitSecondsLeft(rateLimitUntil, rateLimitNow)
    val forgotRateLimitLeft = rateLimitSecondsLeft(forgotRateLimitUntil, forgotRateLimitNow)

    AnimatedContent(
        targetState = page,
        transitionSpec = { hierarchyMotion(enteringDetail = targetState.depth > initialState.depth) },
        label = "cloud-auth-hierarchy"
    ) { authPage ->
    // 注入后台下发的限速值：未登录/已登录两条分支共用同一张「备份规则」卡，
    // 卡片直接读 CompositionLocal，省去在多层签名里各加一遍参数。
    CompositionLocalProvider(LocalBackupRateLimitKbps provides backupRateLimitKbps) {
    Page("账户与云备份") {
        when (authPage) {
            CloudAuthPage.Home -> {
                BackToSettingsRow(onBack)
                // 登录状态变化（含刚登录/注册成功）后刷新云端用量：配额与备份大小都会变
                LaunchedEffect(email) { if (email != null) onRefreshCloudUsage() }
                if (email == null) {
                    CloudNotLoggedIn(
                        working = working,
                        onLogin = { password = ""; page = CloudAuthPage.Login },
                        onRegister = {
                            code = ""
                            confirmPassword = ""
                            onClearRegisterCodeState()
                            page = CloudAuthPage.RegisterEmail
                        },
                        onPrivacy = { page = CloudAuthPage.Privacy }
                    )
                } else {
                    CloudLoggedIn(
                        email = email,
                        working = working,
                        cloudBackupEnabled = cloudBackupEnabled,
                        usage = cloudUsage,
                        progress = cloudProgress,
                        deletionScheduledAt = accountDeletionAt,
                        onUpload = onUpload,
                        // 恢复先弹确认（覆盖本机数据不可撤销），确认后才真正开始
                        onRestore = { showRestoreConfirm = true },
                        onPurgeCloudBackup = onPurgeCloudBackup,
                        onLogout = onLogout,
                        onPrivacy = { page = CloudAuthPage.Privacy },
                        onDeleteAccount = {
                            deleteAccountPassword = ""
                            showDeleteAccountDialog = true
                        },
                        onCancelDeletion = onCancelDeletion
                    )
                }
            }

            CloudAuthPage.Login -> {
                AuthBackRow("返回") { page = CloudAuthPage.Home }
                AuthHeader("登录", "登录后可使用云端备份")
                CardBlock(level = GlassLevel.L2_Panel) {
                    AppTextField(
                        label = "邮箱", value = inputEmail, onValueChange = { inputEmail = it },
                        suffix = "", keyboardType = KeyboardType.Email, enabled = !working
                    )
                    AppTextField(
                        label = "密码", value = password, onValueChange = { password = it },
                        suffix = "", keyboardType = KeyboardType.Password, enabled = !working
                    )
                }
                AuthActionButton(
                    text = "登录",
                    primary = true,
                    enabled = !working && canSubmitLogin(inputEmail, password),
                ) { onLogin(inputEmail.trim(), password) }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AuthActionButton(
                        text = "注册账号",
                        primary = false,
                        modifier = Modifier.weight(1f),
                        enabled = !working,
                    ) {
                        code = ""
                        onClearRegisterCodeState()
                        page = CloudAuthPage.RegisterEmail
                    }
                    AuthActionButton(
                        text = "忘记密码",
                        primary = false,
                        modifier = Modifier.weight(1f),
                        enabled = !working,
                    ) {
                        forgotPassword = ""
                        forgotConfirmPassword = ""
                        code = ""
                        onClearForgotState()
                        page = CloudAuthPage.ForgotEmail
                    }
                }
            }

            CloudAuthPage.RegisterEmail -> {
                AuthBackRow("返回") { page = CloudAuthPage.Home }
                AuthHeader("注册", "填写邮箱，获取验证码", step = 1)
                CardBlock(level = GlassLevel.L2_Panel) {
                    AppTextField(
                        label = "邮箱", value = inputEmail, onValueChange = { inputEmail = it },
                        suffix = "", keyboardType = KeyboardType.Email, enabled = !working
                    )
                    if (inputEmail.isNotBlank() && !isLikelyEmail(inputEmail)) {
                        Text("邮箱格式不正确", color = Error, fontSize = 12.sp)
                    }
                }
                AuthActionButton(
                    text = "发送验证码",
                    primary = true,
                    icon = Icons.Outlined.MailOutline,
                    enabled = !working && isLikelyEmail(inputEmail) && rateLimitLeft <= 0
                ) {
                    codeMail = inputEmail.trim()
                    rateLimitMessage = ""
                    awaitingCode = true
                    onSendCode(inputEmail.trim())
                }
                CodeNoticeCard(
                    secondsLeft = rateLimitLeft,
                    message = rateLimitMessage,
                    nextStep = "下一步填写邮件中的 6 位验证码，并设置登录密码。",
                    rule = "验证码 10 分钟内有效；每小时最多发送 10 封。"
                )
                AuthSwitchLine(
                    question = "已有账号？",
                    action = "直接登录",
                    onAction = { page = CloudAuthPage.Login }
                )
            }

            CloudAuthPage.RegisterVerify -> {
                AuthBackRow("上一步") { page = CloudAuthPage.RegisterEmail }
                AuthHeader(
                    title = "验证邮箱",
                    subtitle = "验证码已发送至 ${maskEmail(codeMail.ifBlank { inputEmail })}",
                    step = 2
                )
                VerificationCodeField(value = code, onValueChange = { code = it }, enabled = !working)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (ttlLeft > 0) "剩余 ${countdownText(ttlLeft)}" else "验证码已过期，请重新发送",
                        color = if (ttlLeft > 0) TextTertiary else Error,
                        fontSize = 12.sp, modifier = Modifier.weight(1f)
                    )
                    if (resendLeft > 0) {
                        Text("未收到？重新发送 (${resendLeft}s)", color = TextTertiary, fontSize = 12.sp)
                    } else {
                        Text(
                            "未收到？重新发送", color = PrimaryBlue, fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.glassClickable(enabled = !working && rateLimitLeft <= 0) {
                                rateLimitMessage = ""
                                awaitingCode = true
                                onSendCode(codeMail.trim())
                            }
                        )
                    }
                }
                CodeNoticeCard(
                    secondsLeft = rateLimitLeft,
                    message = rateLimitMessage,
                    nextStep = "输入邮件中的 6 位验证码，并设置登录密码。",
                    rule = "验证码 10 分钟内有效；每小时最多发送 10 封。"
                )

                Text("设置密码", color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                CardBlock(level = GlassLevel.L2_Panel) {
                    AppTextField(
                        label = "密码", value = password, onValueChange = { password = it },
                        suffix = "", keyboardType = KeyboardType.Password, enabled = !working
                    )
                    PasswordStrengthBar(password)
                    Text("至少 8 位，需包含字母与数字。", color = TextTertiary, fontSize = 12.sp)
                    AppTextField(
                        label = "确认密码", value = confirmPassword, onValueChange = { confirmPassword = it },
                        suffix = "", keyboardType = KeyboardType.Password, enabled = !working
                    )
                    if (confirmPassword.isNotEmpty() && confirmPassword != password) {
                        Text("两次输入的密码不一致", color = Error, fontSize = 12.sp)
                    }
                }
                AuthActionButton(
                    text = "完成注册",
                    primary = true,
                    icon = Icons.Outlined.PersonAdd,
                    enabled = !working && canSubmitRegister(code, password, confirmPassword)
                ) { onRegister(codeMail.trim().ifBlank { inputEmail.trim() }, code, password) }
                CardBlock {
                    Row(Modifier.fillMaxWidth()) {
                        Text("注册即表示同意 ", color = TextTertiary, fontSize = 12.sp)
                        Text(
                            "《隐私政策》", color = PrimaryBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.glassClickable(enabled = !working) {
                                page = CloudAuthPage.Privacy
                            }
                        )
                        Text("。账号仅保存邮箱与密码。", color = TextTertiary, fontSize = 12.sp)
                    }
                }
            }

            CloudAuthPage.ForgotEmail -> {
                AuthBackRow("返回") { page = CloudAuthPage.Login }
                AuthHeader("忘记密码", "填写邮箱，获取验证码")
                CardBlock(level = GlassLevel.L2_Panel) {
                    AppTextField(
                        label = "邮箱", value = inputEmail, onValueChange = { inputEmail = it },
                        suffix = "", keyboardType = KeyboardType.Email, enabled = !working
                    )
                    if (inputEmail.isNotBlank() && !isLikelyEmail(inputEmail)) {
                        Text("邮箱格式不正确", color = Error, fontSize = 12.sp)
                    }
                }
                AuthActionButton(
                    text = "发送验证码",
                    primary = true,
                    icon = Icons.Outlined.MailOutline,
                    enabled = !working && isLikelyEmail(inputEmail) && forgotRateLimitLeft <= 0
                ) {
                    codeMail = inputEmail.trim()
                    forgotRateLimitMessage = ""
                    awaitingForgotCode = true
                    onSendResetCode(inputEmail.trim())
                }
                CodeNoticeCard(
                    secondsLeft = forgotRateLimitLeft,
                    message = forgotRateLimitMessage,
                    nextStep = "下一步填写邮件中的 6 位验证码，并设置新密码。",
                    rule = "验证码 10 分钟内有效。"
                )
            }

            CloudAuthPage.ForgotVerify -> {
                AuthBackRow("上一步") {
                    onClearForgotState()
                    page = CloudAuthPage.ForgotEmail
                }
                AuthHeader(
                    title = "重置密码",
                    subtitle = "验证码已发送至 ${maskEmail(codeMail.ifBlank { inputEmail })}"
                )
                VerificationCodeField(value = code, onValueChange = { code = it }, enabled = !working)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (ttlLeft > 0) "剩余 ${countdownText(ttlLeft)}" else "验证码已过期，请重新发送",
                        color = if (ttlLeft > 0) TextTertiary else Error,
                        fontSize = 12.sp, modifier = Modifier.weight(1f)
                    )
                    if (resendLeft > 0) {
                        Text("未收到？重新发送 (${resendLeft}s)", color = TextTertiary, fontSize = 12.sp)
                    } else {
                        Text(
                            "未收到？重新发送", color = PrimaryBlue, fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.glassClickable(enabled = !working && forgotRateLimitLeft <= 0) {
                                forgotRateLimitMessage = ""
                                awaitingForgotCode = true
                                onSendResetCode(codeMail.trim())
                            }
                        )
                    }
                }
                CodeNoticeCard(
                    secondsLeft = forgotRateLimitLeft,
                    message = forgotRateLimitMessage,
                    nextStep = "输入邮件中的 6 位验证码，并设置新密码。",
                    rule = "验证码 10 分钟内有效。"
                )

                Text("设置新密码", color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                CardBlock(level = GlassLevel.L2_Panel) {
                    AppTextField(
                        label = "新密码", value = forgotPassword, onValueChange = { forgotPassword = it },
                        suffix = "", keyboardType = KeyboardType.Password, enabled = !working
                    )
                    PasswordStrengthBar(forgotPassword)
                    Text("至少 8 位，需包含字母与数字。", color = TextTertiary, fontSize = 12.sp)
                    AppTextField(
                        label = "确认新密码", value = forgotConfirmPassword,
                        onValueChange = { forgotConfirmPassword = it },
                        suffix = "", keyboardType = KeyboardType.Password, enabled = !working
                    )
                    if (forgotConfirmPassword.isNotEmpty() && forgotConfirmPassword != forgotPassword) {
                        Text("两次输入的密码不一致", color = Error, fontSize = 12.sp)
                    }
                }
                AuthActionButton(
                    text = "重置密码",
                    primary = true,
                    icon = Icons.Outlined.Refresh,
                    enabled = !working && canSubmitRegister(code, forgotPassword, forgotConfirmPassword)
                ) {
                    onResetPassword(
                        codeMail.trim().ifBlank { inputEmail.trim() },
                        code,
                        forgotPassword
                    )
                }
            }

            CloudAuthPage.Privacy -> {
                AuthBackRow("返回") { page = CloudAuthPage.Home }
                AuthHeader("隐私政策", "数据范围、权利与加密说明")
                CardBlock {
                    Text("数据收集", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                    Text(
                        "账号：只保存邮箱与密码。密码加密保存，连我们也看不到原文。\n\n" +
                            "云端备份：只有你主动上传时才保存，放在你自己的云端空间。\n\n" +
                            "平时不上传任何数据；只有你主动点「上传到云端」时，才会把车辆、里程、" +
                            "加油与充电记录、轨迹（含位置）等用车数据打包上传，同一账号只留最新一份。\n\n" +
                            "人车证书不会上传：打包时会被自动剔除，服务器也会拒绝接收人车证件数据包，" +
                            "云端备份里不含这些数据。",
                        color = TextTertiary, fontSize = 12.sp, lineHeight = 17.sp
                    )
                    ListRowDivider()
                    Text("你的权利", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                    Text(
                        "· 删除云端备份；也可以再上传一次，直接替换成最新一份\n" +
                            "· 注销账号：有 24 小时宽限期，到期后账号与云端数据一并删除\n" +
                            "· 随时把本机数据导出成文件，不依赖账号\n" +
                            "· 不登录不影响本机任何功能",
                        color = TextTertiary, fontSize = 12.sp, lineHeight = 17.sp
                    )
                    ListRowDivider()
                    Text("关于加密", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                    Text(
                        "传输：每次连接都加密，并核对对方身份，防止被中途截取或冒充。\n\n" +
                            "本机：登录凭证加密保存，加密密钥留在系统安全区，不随数据一起被带走。\n\n" +
                            "云端：账号与运行数据都是加密保存的。\n\n" +
                            "导出文件：新版备份包不加密，方便你用 Excel 打开；" +
                            "请自己妥善保管，不要转发他人或生成公开分享链接。",
                        color = TextTertiary, fontSize = 12.sp, lineHeight = 17.sp
                    )
                }
                Text(
                    "2026-09-22 更新 · 版本 3",
                    color = TextTertiary, fontSize = 12.sp,
                    modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center
                )
            }
        }

        if (busy != null) {
            Text("正在$busy…", fontSize = 13.sp, color = TextSecondary, modifier = Modifier.padding(horizontal = 4.dp))
        }
    }
    }   // CompositionLocalProvider（限速值注入）
    }

    if (showRestoreConfirm) {
        val sizeText = cloudUsage?.let { if (it.usedBytes > 0L) formatBackupSize(it.usedBytes) else "未知大小" } ?: "未知大小"
        val atText = cloudUsage?.backupAt?.take(10) ?: "未知时间"
        GlassDialog(
            onDismissRequest = { if (!working) showRestoreConfirm = false },
            title = "恢复云端备份",
            text = {
                Text(
                    "将从云端恢复这份备份：\n" +
                        "· 大小 $sizeText\n" +
                        "· 备份时间 $atText\n\n" +
                        "恢复会覆盖本机现有数据，且不可撤销。\n" +
                        "建议先在「本地数据管理」里导出到本机留一份。",
                    color = TextSecondary, fontSize = 14.sp, lineHeight = 21.sp
                )
            },
            confirmButton = {
                GlassDialogAction(
                    onClick = {
                        showRestoreConfirm = false
                        onRestore()
                    },
                    enabled = !working
                ) { Text("覆盖并恢复", color = Error, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                GlassDialogAction(onClick = { showRestoreConfirm = false }, enabled = !working) {
                    Text("取消", color = TextSecondary, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // 注销账号的二次确认（弹窗放在 AnimatedContent 之外：它不该参与页面转场）
    if (showDeleteAccountDialog) {
        GlassDialog(
            onDismissRequest = { if (!working) showDeleteAccountDialog = false },
            title = "注销账号",
            text = {
                Text(
                    "提交注销后：\n" +
                        "· 账号与云端数据仍保留 24 小时；\n" +
                        "· 24 小时内可随时在本页点「撤销注销」反悔；\n" +
                        "· 24 小时后账号与云端数据将被彻底删除，无法恢复。\n\n" +
                        "请输入登录密码以确认。",
                    color = TextSecondary, fontSize = 14.sp, lineHeight = 21.sp
                )
                Spacer(Modifier.height(12.dp))
                AppTextField(
                    label = "登录密码",
                    value = deleteAccountPassword,
                    onValueChange = { deleteAccountPassword = it },
                    suffix = "",
                    keyboardType = KeyboardType.Password,
                    enabled = !working
                )
            },
            confirmButton = {
                GlassDialogAction(
                    onClick = {
                        val pwd = deleteAccountPassword
                        showDeleteAccountDialog = false
                        deleteAccountPassword = ""
                        onDeleteAccount(pwd)
                    },
                    enabled = !working && deleteAccountPassword.isNotBlank()
                ) {
                    Text("确认注销", color = Error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                GlassDialogAction(onClick = { showDeleteAccountDialog = false }, enabled = !working) {
                    Text("取消", color = TextSecondary, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

/** 备份限速在账户页内的传递载体 */
private val LocalBackupRateLimitKbps = compositionLocalOf<Int?> { null }

@Composable
private fun ColumnScope.CloudBackupRulesSection(onPrivacy: () -> Unit) {
    SectionTitle("备份规则")
    CardBlock {
        ListRow(
            icon = Icons.Outlined.Upload, tint = TextSecondary,
            title = "仅手动上传", subtitle = "不会自动备份，也不在后台传输"
        )
        ListRowDivider()
        ListRow(
            icon = Icons.Outlined.CloudUpload, tint = TextSecondary,
            title = "只上传用车记录数据",
            // 「证书不上云」并入这里：与"只上传用车记录数据"本来就是同一条规则的两种说法
            subtitle = "上传前会在本机剥离证书包，人车证书数据会被服务器拒绝"
        )
        ListRowDivider()
        ListRow(
            icon = Icons.Outlined.Refresh, tint = TextSecondary,
            title = "云端仅保留 1 份，每账号 16 MB",
            subtitle = "再次上传会替换原有备份，替换前需确认"
        )
        ListRowDivider()
        val rateLimitKbps = LocalBackupRateLimitKbps.current
        ListRow(
            icon = Icons.Outlined.Speed, tint = TextSecondary,
            title = when {
                rateLimitKbps == null -> "云端备份与恢复限速"
                rateLimitKbps == 0 -> "云端备份与恢复不限速"
                else -> "云端备份与恢复限速 $rateLimitKbps KB/s"
            },
            subtitle = when {
                rateLimitKbps == null -> "正在读取后台设置…"
                rateLimitKbps == 0 -> "传输速度取决于网络状况"
                else -> "传输耗时取决于备份体积，请保持本页在前台"
            }
        )
        ListRowDivider()
        ListRow(
            icon = Icons.Outlined.Shield, tint = TextSecondary,
            title = "恢复前先导出一份本机数据",
            subtitle = "恢复会覆盖本机数据，不可撤销"
        )
        ListRowDivider()
        // 末行是隐私政策入口，点击跳到页内隐私页
        ListRow(
            icon = Icons.Outlined.Policy, tint = PrimaryBlue,
            title = "隐私政策",
            subtitle = "数据范围、权利与加密说明",
            chevron = true,
            onClick = onPrivacy
        )
    }
}

/** 未登录首页 */
@Composable
private fun ColumnScope.CloudNotLoggedIn(
    working: Boolean,
    onLogin: () -> Unit,
    onRegister: () -> Unit,
    onPrivacy: () -> Unit
) {
    CardBlock(level = GlassLevel.L2_Panel) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(PrimaryBlue.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.CloudUpload, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(26.dp))
            }
            Text(
                "未登录", color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Black,
                modifier = Modifier.padding(top = 12.dp)
            )
            Text(
                "登录后可把用车记录备份到云端，更换设备时恢复。证书始终留在本机。",
                color = TextTertiary, fontSize = 12.sp, lineHeight = 17.sp,
                textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp, bottom = 14.dp)
            )
            AuthActionButton(
                text = "登录",
                primary = true,
                modifier = Modifier.fillMaxWidth(),
                enabled = !working,
                onClick = onLogin
            )
            Spacer(Modifier.height(10.dp))
            AuthActionButton(
                text = "注册",
                primary = false,
                modifier = Modifier.fillMaxWidth(),
                enabled = !working,
                onClick = onRegister
            )
        }
    }

    CloudBackupRulesSection(onPrivacy)
}

/** 配额区 */
@Composable
private fun ColumnScope.CloudQuotaBlock(usage: CloudUsage?) {
    Spacer(Modifier.height(12.dp))
    Box(Modifier.fillMaxWidth().height(1.dp).background(GlassTokens.strokeHairline))
    Spacer(Modifier.height(11.dp))
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            "云端空间", color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        Text(
            if (usage == null) "读取中…"
            else "${formatBackupSize(usage.usedBytes)} / ${formatBackupSize(usage.quotaBytes)}",
            color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold
        )
    }
    Spacer(Modifier.height(8.dp))
    val fraction = usage?.let {
        if (it.quotaBytes > 0L) (it.usedBytes.toFloat() / it.quotaBytes.toFloat()).coerceIn(0f, 1f) else 0f
    } ?: 0f
    TransferBarTrack(fraction = fraction, height = 4.dp)
    // 备份时间/来源：只在真有备份时出现（从未上传过就只显示 0/配额）
    val backupAt = usage?.backupAt?.take(10)
    if (!backupAt.isNullOrBlank()) {
        Spacer(Modifier.height(7.dp))
        Text(
            "最近备份：$backupAt" + (usage?.appVersion?.takeIf { it.isNotBlank() }?.let { " · 来自 $it" } ?: ""),
            color = TextTertiary, fontSize = 11.5.sp
        )
    }
}

/** 细进度条，上传/恢复与配额共用同一形状语言 */
@Composable
private fun TransferBarTrack(fraction: Float, height: androidx.compose.ui.unit.Dp) {
    Box(
        Modifier.fillMaxWidth().height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(PrimaryBlue.copy(alpha = 0.16f))
    ) {
        if (fraction > 0f) {
            Box(
                Modifier.fillMaxWidth(fraction).fillMaxHeight()
                    .clip(RoundedCornerShape(height / 2))
                    .background(PrimaryBlue)
            )
        }
    }
}

/** 上传 / 恢复进度条 */
@Composable
private fun ColumnScope.CloudTransferProgressBar(progress: CloudProgress) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            "${progress.label}中", color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        Text(
            if (progress.totalBytes > 0L) {
                "${(progress.fraction * 100f).roundToInt()}%  ·  " +
                    "${formatBackupSize(progress.doneBytes)} / ${formatBackupSize(progress.totalBytes)}"
            } else {
                formatBackupSize(progress.doneBytes)
            },
            color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold
        )
    }
    Spacer(Modifier.height(9.dp))
    TransferBarTrack(fraction = progress.fraction, height = 6.dp)
}

/** 已登录首页 */
@Composable
private fun ColumnScope.CloudLoggedIn(
    email: String,
    working: Boolean,
    cloudBackupEnabled: Boolean,
    usage: CloudUsage?,
    progress: CloudProgress?,
    deletionScheduledAt: Long?,
    onUpload: () -> Unit,
    onRestore: () -> Unit,
    onPurgeCloudBackup: () -> Unit,
    onLogout: () -> Unit,
    onPrivacy: () -> Unit,
    onDeleteAccount: () -> Unit,
    onCancelDeletion: () -> Unit
) {
    // 「清空云端备份」的二次确认（删除不可恢复，与"从云端恢复"同一档风险）
    var showPurgeConfirm by rememberSaveable { mutableStateOf(false) }
    SectionTitle("账户")
    CardBlock {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(PrimaryBlue.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.Person, contentDescription = null,
                    tint = PrimaryBlue, modifier = Modifier.size(20.dp)
                )
            }
            Column(Modifier.weight(1f)) {
                Text(email, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                Text("账号仅保存邮箱与密码", color = TextTertiary, fontSize = 12.sp)
            }
            Text("已登录", color = Success, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        CloudQuotaBlock(usage)
    }

    if (cloudBackupEnabled) {
        SectionTitle("云端备份")
        if (progress != null) {
            CardBlock { CloudTransferProgressBar(progress) }
        }
        CardBlock(contentPadding = 8.dp) {
            ListRow(
                icon = Icons.Outlined.CloudUpload, tint = PrimaryBlue,
                title = "上传到云端",
                subtitle = if (working) "正在传输，请勿离开本页" else "云端仅保留 1 份，再次上传会替换原有备份",
                enabled = !working, onClick = onUpload
            )
            ListRowDivider()
            ListRow(
                icon = Icons.Outlined.CloudDownload, tint = Success,
                title = "从云端恢复",
                subtitle = if (working) "正在传输，请勿离开本页" else "会覆盖本机数据，操作前先导出一份",
                enabled = !working, onClick = onRestore
            )
            ListRowDivider()
            val hasCloudBackup = (usage?.usedBytes ?: 0L) > 0L
            ListRow(
                icon = Icons.Outlined.DeleteSweep, tint = Error,
                title = "清空云端备份",
                subtitle = when {
                    working -> "正在传输，请勿离开本页"
                    !hasCloudBackup -> "云端还没有备份"
                    else -> "删除云端全部备份以释放空间；账号保留，不可恢复"
                },
                enabled = !working && hasCloudBackup,
                onClick = { showPurgeConfirm = true }
            )
        }
    }

    CloudBackupRulesSection(onPrivacy)

    SectionTitle("账户操作")
    if (deletionScheduledAt != null) {
        val hoursLeft = ((deletionScheduledAt - System.currentTimeMillis()) / 3_600_000L).coerceAtLeast(0L)
        CardBlock(level = GlassLevel.L2_Panel) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Warning, contentDescription = null, tint = Error, modifier = Modifier.size(19.dp))
                Spacer(Modifier.width(9.dp))
                Text(
                    "账号已提交注销",
                    color = Error, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Text("剩余约 $hoursLeft 小时", color = TextTertiary, fontSize = 12.sp)
            }
            Text(
                "账号与云端数据都会保留 24 小时，期间可随时撤销；到期后将彻底删除，无法恢复。",
                color = TextSecondary, fontSize = 12.sp, lineHeight = 17.sp,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
        CardBlock(contentPadding = 8.dp) {
            ListRow(
                icon = Icons.Outlined.Undo, tint = PrimaryBlue,
                title = "撤销注销",
                subtitle = "账号与云端数据仍在，点此恢复使用",
                enabled = !working, onClick = onCancelDeletion
            )
        }
    }
    CardBlock(contentPadding = 8.dp) {
        ListRow(
            icon = Icons.Outlined.Logout, tint = Error,
            title = "退出登录",
            subtitle = "退出后需重新验证才能使用云端备份",
            enabled = !working, onClick = onLogout
        )
        ListRowDivider()
        ListRow(
            icon = Icons.Outlined.DeleteForever, tint = Error,
            title = "注销账号",
            // 文案必须与真实语义一致：提交 ≠ 立即删除，有 24 小时反悔窗口
            subtitle = if (deletionScheduledAt != null) "已提交注销，可撤销" else "提交后账号与云端数据保留 24 小时，可随时撤销",
            enabled = !working && deletionScheduledAt == null,
            onClick = onDeleteAccount
        )
    }

    if (showPurgeConfirm) {
        GlassDialog(
            onDismissRequest = { if (!working) showPurgeConfirm = false },
            title = "清空云端备份",
            text = {
                Text(
                    "将删除云端的全部备份（包括上一版安全网）。账号与登录状态保留，" +
                        "云端空间会立即释放。此操作不可恢复，若本机数据有需要，请先导出一份备份。",
                    color = TextSecondary
                )
            },
            confirmButton = {
                GlassDialogAction(
                    enabled = !working,
                    onClick = {
                        showPurgeConfirm = false
                        onPurgeCloudBackup()
                    }
                ) { Text("清空", color = Error, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                GlassDialogAction(enabled = !working, onClick = { showPurgeConfirm = false }) {
                    Text("取消", color = TextSecondary)
                }
            }
        )
    }
}

/** 登录 / 注册页顶部的返回行 */
@Composable
private fun AuthBackRow(text: String, onClick: () -> Unit) {
    GlassActionButton(text, Icons.AutoMirrored.Outlined.ArrowBack, TextSecondary, onClick = onClick)
}

/** 独立页面的标题区 */
@Composable
private fun AuthHeader(title: String, subtitle: String, step: Int? = null) {
    Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        if (step != null) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(2) { i ->
                    Box(
                        Modifier
                            .width(if (step == i + 1) 34.dp else 26.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(if (step > i) PrimaryBlue else GlassTokens.trackFill)
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
        }
        Text(title, color = TextPrimary, fontSize = 26.sp, fontWeight = FontWeight.Black)
        Text(subtitle, color = TextTertiary, fontSize = 12.5.sp, modifier = Modifier.padding(top = 3.dp))
    }
}

/** 验证码 6 格 */
@Composable
private fun VerificationCodeField(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean
) {
    val focusRequester = remember { FocusRequester() }
    Box(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(CODE_LENGTH) { i ->
                val filled = i < value.length
                val active = i == value.length && enabled
                GlassPillLabel(
                    shape = GlassTokens.shapeControl,
                    modifier = Modifier.weight(1f).height(52.dp)
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = if (filled) value[i].toString() else if (active) "|" else "·",
                            color = if (active) PrimaryBlue else TextPrimary,
                            fontSize = 21.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }
        }
        BasicTextField(
            value = value,
            onValueChange = { raw -> onValueChange(raw.filter { it.isDigit() }.take(CODE_LENGTH)) },
            enabled = enabled,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            textStyle = LocalTextStyle.current.copy(color = Color.Transparent, fontSize = 1.sp),
            cursorBrush = SolidColor(Color.Transparent),
            modifier = Modifier.matchParentSize().focusRequester(focusRequester)
        )
    }
}

/** 密码强度条 */
@Composable
private fun PasswordStrengthBar(password: String) {
    val kinds = listOf(
        password.any { it.isLetter() },
        password.any { it.isDigit() },
        password.any { !it.isLetterOrDigit() }
    ).count { it }
    val (label, color, fraction) = when {
        password.isEmpty() -> Triple("", TextTertiary, 0f)
        password.length < 8 -> Triple("弱", Error, 0.33f)
        password.length >= 12 && kinds >= 3 -> Triple("强", Success, 1f)
        kinds >= 2 -> Triple("中", Warning, 0.66f)
        else -> Triple("弱", Error, 0.33f)
    }
    if (password.isEmpty()) return
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .weight(1f)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(GlassTokens.trackFill)
        ) {
            Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(color))
        }
        Spacer(Modifier.width(8.dp))
        Text(label, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun AuthSwitchLine(question: String, action: String, onAction: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .glassClickable(onClick = onAction)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(question, color = TextTertiary, fontSize = 12.5.sp)
        Text(action, color = PrimaryBlue, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
    }
}

/** 邮箱形状校验 */
private fun isLikelyEmail(raw: String): Boolean {
    val s = raw.trim()
    val at = s.indexOf('@')
    return at > 0 && at < s.length - 1 && !s.contains(' ') &&
        s.substring(at + 1).substringBefore('.').isNotEmpty() &&
        s.substringAfterLast('.', "").length >= 2
}

private fun canSubmitLogin(email: String, password: String): Boolean =
    isLikelyEmail(email) && password.isNotEmpty()

/** 注册 / 重置密码的提交条件 */
internal fun canSubmitRegister(code: String, password: String, confirmPassword: String): Boolean =
    code.length == CODE_LENGTH &&
        isStrongEnoughPassword(password) &&
        password == confirmPassword

/** 密码规则 */
internal fun isStrongEnoughPassword(password: String): Boolean =
    password.length >= 8 && password.any { it.isLetter() } && password.any { it.isDigit() }

@Composable
private fun CodeNoticeCard(
    secondsLeft: Int,
    message: String,
    nextStep: String,
    rule: String
) {
    val limited = secondsLeft > 0
    CardBlock(
        level = GlassLevel.L1_Card,
        contentPadding = 12.dp,
        // 限流是"等待"而非"错误"：用琥珀洗色与提示行同色（失败态不洗色，走红字即可）。
        accentWash = if (limited) Warning.copy(alpha = 0.10f) else null
    ) {
        if (limited || message.isNotBlank()) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    if (limited) "发送过于频繁" else "发送失败",
                    color = if (limited) Warning else Error,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (limited) {
                        "同一邮箱与网络每小时最多发送 10 封，请于 ${retryWaitText(secondsLeft)}后重试。"
                    } else {
                        message
                    },
                    color = TextTertiary,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Text(
            if (limited) "$nextStep\n如未收到验证码，请检查垃圾邮件。" else "$nextStep\n$rule",
            color = TextTertiary,
            fontSize = 12.sp,
            lineHeight = 17.sp
        )
    }
}

/** 限流剩余等待秒数 */
internal fun rateLimitSecondsLeft(until: Long, now: Long): Int =
    if (until <= 0L) 0 else (((until - now) / 1000L).toInt()).coerceAtLeast(0)

/** 把 429 的剩余秒数落到界面状态 */
private fun applyRateLimit(seconds: Long, set: (until: Long, message: String) -> Unit) {
    if (seconds > 0L) set(System.currentTimeMillis() + seconds * 1000L, "")
    else set(0L, "发送过于频繁，请稍后重试")
}

internal fun retryWaitText(seconds: Int): String =
    if (seconds >= 60) "${(seconds + 59) / 60} 分钟" else "$seconds 秒"

private fun maskEmail(raw: String): String {
    val s = raw.trim()
    val at = s.indexOf('@')
    if (at <= 0) return s
    return s.first() + "*".repeat((at - 1).coerceAtLeast(0)) + s.substring(at)
}

private fun countdownText(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return if (m > 0) "$m 分 $s 秒" else "$s 秒"
}

@Composable
private fun DataManagementScreen(
    state: LedgerUiState,
    range: BackupExportRange,
    onRangeChange: (BackupExportRange) -> Unit,
    onExportBusiness: () -> Unit,
    onExportArchiveZip: () -> Unit,
    onImportFile: () -> Unit,
    onImportXiong: () -> Unit,
    onClearAllData: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val certificateSources = remember(state.vehicles) {
        runCatching { CertificateRepository(context).certificateSources().sources }.getOrDefault(emptyList())
    }
    val historyStore = remember(context) {
        BackupExportHistoryStore(
            context.getSharedPreferences(BackupExportHistoryStore.Key.PREFERENCES_NAME, Context.MODE_PRIVATE)
        )
    }
    // 直接读、不 `remember`：导出是在这个页面上发起的，返回后必须看到新的那条记录。历史最多 20 条。
    val lastBusinessExport = historyStore.read(BackupExportHistoryStore.Key.BUSINESS)
    val lastArchiveExport = historyStore.read(BackupExportHistoryStore.Key.ARCHIVE)

    var selectedCertificateKeys by remember(certificateSources) {
        mutableStateOf(certificateSources.map { it.key }.toSet())
    }
    val latestRange by rememberUpdatedState(range)
    val latestCertificateKeys by rememberUpdatedState(selectedCertificateKeys)

    BackHandler { onBack() }

    Page("本地数据管理") {
        BackToSettingsRow(onBack)

        CardBlock {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "本机数据", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Black,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    latestExportSummary(lastBusinessExport),
                    color = TextTertiary, fontSize = 12.sp
                )
            }
            ListRowDivider()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(26.dp)) {
                StatCell(state.vehicles.size.toString(), "辆车")
                StatCell(
                    (state.mileageRecords.size + state.energyRecords.size + state.maintenanceHistory.size).toString(),
                    "条记录"
                )
                StatCell(certificateSources.size.toString(), "个证书")
            }
        }

        SectionTitle("导出用车记录数据")
        CardBlock {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "导出范围", color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                RowAction(
                    text = "全选",
                    icon = Icons.Outlined.Check,
                    color = TextSecondary,
                    enabled = !(range.vehiclesAndMileage && range.energy && range.maintenance && range.performance && range.fit)
                ) { onRangeChange(BackupExportRange()) }
            }
            rangeGroupRow("车辆与行驶", "车辆信息 + 里程记录", range.vehiclesAndMileage) {
                onRangeChange(latestRange.copy(vehiclesAndMileage = it))
            }
            rangeGroupRow("能源", "加油 + 充电", range.energy) {
                onRangeChange(latestRange.copy(energy = it))
            }
            rangeGroupRow("保养与费用", "保养 + 保险 + 检修", range.maintenance) {
                onRangeChange(latestRange.copy(maintenance = it))
            }
            rangeGroupRow("测试与轨迹", "性能测试 + FIT 轨迹", range.performance || range.fit) {
                onRangeChange(latestRange.copy(performance = it, fit = it))
            }
        }

        CardBlock(accentWash = PrimaryBlue.copy(alpha = 0.09f), contentPadding = 8.dp) {
            ListRow(
                icon = Icons.Outlined.Analytics,
                tint = PrimaryBlue,
                title = "导出用车记录数据",
                subtitle = "车辆信息、里程、能耗、保养与费用、性能与轨迹",
                footer = latestExportSummary(lastBusinessExport),
                enabled = !range.isEmpty,
                onClick = onExportBusiness
            )
        }

        SectionTitle("导出人车证书数据")
        CardBlock(accentWash = ArchiveAccent.copy(alpha = 0.09f)) {
            Text(
                "仅导出已有文件的项（共 ${certificateSources.size} 份）；证书包不含任何用车记录数据。",
                color = TextTertiary, fontSize = 12.sp
            )

            // 通用证书（不随车）：身份证 / 驾驶证
            val generalKeys = certificateSources.filter { vehicleIdOfKey(it.key, state.vehicles) == null }
            if (generalKeys.isNotEmpty()) {
                Text("通用证书", color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                generalKeys.forEach { source ->
                    SettlementOptionRow(
                        source.fileName.ifBlank { source.key },
                        "不随车辆",
                        source.key in selectedCertificateKeys
                    ) { checked -> selectedCertificateKeys = toggleKey(latestCertificateKeys, source.key, checked) }
                }
            }

            val perVehicle = certificateSources.groupBy { vehicleIdOfKey(it.key, state.vehicles) }
            state.vehicles.forEach { vehicle ->
                val keys = perVehicle[vehicle.id].orEmpty()
                if (keys.isEmpty()) return@forEach
                Text(
                    "${vehicle.plate.ifBlank { vehicle.name }} · ${keys.size}/${keys.size}",
                    color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                )
                keys.forEach { source ->
                    SettlementOptionRow(
                        source.fileName.ifBlank { source.key },
                        "该车名下",
                        source.key in selectedCertificateKeys
                    ) { checked -> selectedCertificateKeys = toggleKey(latestCertificateKeys, source.key, checked) }
                }
            }
            if (certificateSources.isEmpty()) {
                Text("一个证书都还没有。先去工具页「人车证书」补传。", color = TextTertiary, fontSize = 12.sp)
            }
        }
        CardBlock(accentWash = ArchiveAccent.copy(alpha = 0.09f), contentPadding = 8.dp) {
            ListRow(
                icon = Icons.Outlined.FolderOpen,
                tint = ArchiveAccent,
                title = "打包证书原件（ZIP）",
                subtitle = "未加密原件、保持原格式，解压即可查看；含证件，请勿转发他人",
                footer = latestExportSummary(lastArchiveExport),
                enabled = selectedCertificateKeys.isNotEmpty(),
                onClick = onExportArchiveZip
            )
        }

        SectionTitle("备份与恢复")
        CardBlock(contentPadding = 8.dp, onClick = onImportFile) {
            ListRow(
                icon = Icons.Outlined.Download,
                tint = PrimaryBlue,
                title = "从备份文件恢复",
                subtitle = "备份包与人车证书包都可以选，App 自动识别包类型",
                footer = "备份包若声明了配套证书包，会再让你选一次它。",
                chevron = true
            )
        }

        SectionTitle("导入与清理")
        CardBlock(contentPadding = 8.dp) {
            ListRow(
                icon = Icons.Outlined.LocalGasStation,
                tint = PrimaryBlue,
                title = "导入小熊油耗",
                subtitle = "支持小熊油耗导出的 .xls 文件",
                footer = "导入前会校验日期、里程、油量和金额。",
                onClick = onImportXiong
            )
        }
        SectionTitle("危险操作")
        CardBlock(contentPadding = 8.dp) {
            ListRow(
                icon = Icons.Outlined.DeleteForever,
                tint = Error,
                title = "清除所有数据",
                subtitle = "车辆、记录、证书与设置一并删除，不可恢复",
                footer = "数据仅存储在本机。卸载应用或清理应用数据后无法恢复。",
                onClick = onClearAllData
            )
        }
    }
}

@Composable
private fun FeedbackScreen(
    text: String,
    onTextChange: (String) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val submitStore = remember(context) {
        FeedbackSubmitStore(
            context.getSharedPreferences(FeedbackSubmitStore.Key.PREFERENCES_NAME, Context.MODE_PRIVATE)
        )
    }
    // 直接读、不 remember：提交成功后必须立刻看到"今天已提交"。
    var lastAt by remember { mutableStateOf(submitStore.lastSubmittedAt()) }
    var submittedTodayNow by remember { mutableStateOf(submitStore.submittedToday()) }
    var submitting by remember { mutableStateOf(false) }
    var resultMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val canSubmit = !submitting && !submittedTodayNow && text.isNotBlank()

    BackHandler { onBack() }

    Page("反馈与意见") {
        BackToSettingsRow(onBack)

        CardBlock {
            AppTextArea(
                label = "问题反馈或希望增加的功能",
                value = text,
                onValueChange = onTextChange,
                minLines = 6,
                maxChars = FeedbackService.MAX_TEXT_CHARS,
                enabled = !submittedTodayNow,
                placeholder = if (submittedTodayNow) null else "如实描述问题与复现步骤；或写下你希望增加的功能"
            )
        }

        GlassActionButton(
            text = when {
                submitting -> "正在提交"
                submittedTodayNow -> "今天已提交，明天再来"
                else -> "提交问题反馈或希望增加的功能"
            },
            icon = Icons.AutoMirrored.Outlined.Send,
            color = if (canSubmit) PrimaryBlue else TextTertiary,
            enabled = canSubmit,
            onClick = {
                if (!canSubmit) return@GlassActionButton
                submitting = true
                scope.launch {
                    when (val result = FeedbackService.submit(text)) {
                        is FeedbackResult.Success -> {
                            submitStore.markSubmitted()
                            lastAt = System.currentTimeMillis()
                            submittedTodayNow = true
                            onTextChange("")
                            resultMessage = "已收到，谢谢你的反馈"
                        }
                        is FeedbackResult.LimitReached -> {
                            submitStore.markSubmitted()
                            submittedTodayNow = true
                            lastAt = System.currentTimeMillis()
                            resultMessage = result.message
                        }
                        is FeedbackResult.Failed -> resultMessage = result.message
                    }
                    submitting = false
                }
            }
        )

        CardBlock {
            Text(
                resultMessage
                    ?: if (submittedTodayNow && lastAt > 0L) {
                        "本机上次提交：${formatExportTimestamp(lastAt)}"
                    } else {
                        "每个设备每天可提交一条。内容会直接送到作者手里，但不会收到回复。"
                    },
                color = if (resultMessage != null) PrimaryBlue else TextTertiary,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun rangeGroupRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    SettlementOptionRow(title, subtitle, checked, onChange)
}

private fun latestExportSummary(history: List<BackupExportRecord>): String =
    history.firstOrNull()?.let { "上次导出：${formatExportTimestamp(it.at)} · ${formatBytesForUi(it.bytes)}" }
        ?: "还没有导出过"

/** 已删 */
private fun formatExportTimestamp(at: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date(at))

private fun formatBytesForUi(bytes: Long): String = when {
    bytes >= 1024L * 1024 -> String.format(Locale.US, "%.1f MB", bytes / 1024.0 / 1024)
    bytes >= 1024 -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
    else -> "$bytes B"
}

/** 从证书 key 反查它属于哪辆车 */
private fun vehicleIdOfKey(key: String, vehicles: List<VehicleEntity>): String? =
    vehicles.firstOrNull { key.startsWith("vehicle_${it.id}_") }?.id

private fun toggleKey(current: Set<String>, key: String, checked: Boolean): Set<String> =
    if (checked) current + key else current - key

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(
    state: LedgerUiState,
    updateState: UpdateUiState,
    onUpdateChargePreset: (String, String, String) -> Unit,
    onUpdateDistanceUnit: (DistanceUnit) -> Unit,
    onCheckForUpdates: () -> Unit = {},
    themeMode: String = "system",
    onThemeModeChange: (String) -> Unit = {},
    backgroundMode: String = "static",
    onBackgroundModeChange: (String) -> Unit = {},
    customBackground: String? = null,
    onChooseCustomBackground: () -> Unit = {},
    /** 可观测的模糊值读取入口 */
    backgroundBlurProvider: () -> Float = { 12f },
    onBackgroundBlurChange: (Float) -> Unit = {},
    onOpenDataManagement: () -> Unit = {},
    onOpenCloudBackup: () -> Unit = {},
    onOpenFeedback: () -> Unit = {},
    /** 已登录邮箱 */
    accountEmail: String? = null,
    /** 远程功能开关 */
    accountsEnabled: Boolean = true
) {
    val context = LocalContext.current
    val certificateCount = remember(state.vehicles) {
        runCatching { CertificateRepository(context).certificateSources().sources.size }.getOrDefault(0)
    }
    val recordCount = state.mileageRecords.size + state.energyRecords.size + state.maintenanceHistory.size
    var home by rememberSaveable(state.settings.homeChargePrice) { mutableStateOf(state.settings.homeChargePrice) }
    var commercial by rememberSaveable(state.settings.commercialChargePrice) { mutableStateOf(state.settings.commercialChargePrice) }
    var service by rememberSaveable(state.settings.commercialServiceFee) { mutableStateOf(state.settings.commercialServiceFee) }
    var showWechatDonationQr by rememberSaveable { mutableStateOf(false) }

    LazyPage("设置") {
        pageSection("account") {
        SectionTitle("账户云备份与本地数据")
        CardBlock(contentPadding = 8.dp) {
            if (accountsEnabled) {
                ListRow(
                    icon = Icons.Outlined.CloudUpload,
                    tint = PrimaryBlue,
                    title = "账户与云备份",
                    subtitle = accountEmail?.let { "$it · 已登录" } ?: "未登录 · 登录后可备份数据到云端",
                    chevron = true,
                    onClick = onOpenCloudBackup
                )
                ListRowDivider()
            }
            ListRow(
                icon = Icons.Outlined.FolderOpen,
                tint = TextSecondary,
                title = "本地数据管理",
                subtitle = "备份、恢复与导出",
                footer = "${state.vehicles.size} 辆车 · $recordCount 条记录 · $certificateCount 个证书",
                chevron = true,
                onClick = onOpenDataManagement
            )
        }
        }
        pageSection("appearance") {
        SectionTitle("外观")
        CardBlock {
            Text("主题", color = TextSecondary, fontSize = 13.sp)
            GlassSegmentedControl(
                options = listOf("浅色", "深色", "跟随系统"),
                selectedIndex = listOf("light", "dark", "system").indexOf(themeMode).coerceAtLeast(0),
                onSelect = { onThemeModeChange(listOf("light", "dark", "system")[it]) },
        backdrop = rememberLayerAwareBackdrop(),
        accent = PrimaryBlue,
        selectedTextColor = PrimaryBlue,
        unselectedTextColor = TextPrimary.copy(alpha = 0.82f),
        modifier = Modifier.fillMaxWidth()
    )
            Text("背景", color = TextSecondary, fontSize = 13.sp)
            GlassSegmentedControl(
                options = listOf("静态", "动态", "自定义"),
                selectedIndex = listOf("static", "mesh", "custom").indexOf(backgroundMode).coerceAtLeast(0),
                onSelect = { onBackgroundModeChange(listOf("static", "mesh", "custom")[it]) },
        backdrop = rememberLayerAwareBackdrop(),
        accent = PrimaryBlue,
        selectedTextColor = PrimaryBlue,
        unselectedTextColor = TextPrimary.copy(alpha = 0.82f),
        modifier = Modifier.fillMaxWidth()
    )
            if (backgroundMode == "custom") {
                GlassActionButton(if (customBackground == null) "选择背景图片" else "更换背景图片", Icons.Outlined.FolderOpen, PrimaryBlue, onClick = onChooseCustomBackground)
                Text("背景模糊 ${backgroundBlurProvider().roundToInt()} dp", color = TextTertiary, fontSize = 12.sp)
                LiquidSlider(
                    value = backgroundBlurProvider,
                    onValueChange = onBackgroundBlurChange,
                    valueRange = 0f..32f,
                    visibilityThreshold = 0.001f,
                    backdrop = rememberLayerAwareBackdrop()
                )
            }
        }
        }
        pageSection("sponsor-version") {
        SectionTitle("版本权益与赞助作者")
        CardBlock(level = GlassLevel.L2_Panel, contentPadding = 14.dp) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("公益版", color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Black)
                    Text("永久使用", color = TextTertiary, fontSize = 12.sp)
                }
                Text("0 元", color = Success, fontSize = 18.sp, fontWeight = FontWeight.Black)
            }
            Text("赞助作者", color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassDialogAction(
                    onClick = { showWechatDonationQr = true },
                    modifier = Modifier.weight(1f).height(44.dp)) { Text("微信", fontWeight = FontWeight.Bold, color = Success) }
                GlassDialogAction(
                    onClick = { openAlipayPayment(context) },
                    modifier = Modifier.weight(1f).height(44.dp)) { Text("支付宝", fontWeight = FontWeight.Bold, color = PrimaryBlue) }
            }
        }
        }
        pageSection("community") {
        SectionTitle("社区共创")
        val feedbackStore = remember(context) {
            FeedbackSubmitStore(
                context.getSharedPreferences(FeedbackSubmitStore.Key.PREFERENCES_NAME, Context.MODE_PRIVATE)
            )
        }
        CardBlock(contentPadding = 8.dp) {
            ListRow(
                icon = Icons.AutoMirrored.Outlined.Chat,
                tint = PrimaryBlue,
                title = "提交反馈",
                subtitle = if (feedbackStore.submittedToday()) "今天已提交过 · 明天再来" else "问题反馈与功能建议都写在这里",
                footer = "每个设备每天一条 · 提交后不会收到回复",
                chevron = true,
                onClick = onOpenFeedback
            )
        }
        }
        pageSection("update") {
        SectionTitle("检查更新")
        CardBlock {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("当前版本", color = TextSecondary, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Text(BuildConfig.VERSION_NAME, color = TextPrimary, fontWeight = FontWeight.Bold)
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("最新版本", color = TextSecondary, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Text(
                    when {
                        updateState.isChecking -> "检查中"
                        updateState.checkFailed -> "检查失败"
                        updateState.latestVersion != null -> updateState.latestVersion
                        else -> "暂未检查"
                    },
                    color = when {
                        updateState.checkFailed -> Error
                        updateState.hasChecked -> Success
                        else -> TextTertiary
                    },
                    fontWeight = FontWeight.Bold
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                GlassActionButton(
                    text = when {
                        updateState.isChecking -> "正在检查"
                        updateState.hasChecked -> "重新检查"
                        else -> "检查更新"
                    },
                    icon = Icons.Outlined.Refresh,
                    color = PrimaryBlue,
                    modifier = Modifier.weight(1f),
                    enabled = !updateState.isChecking,
                    onClick = onCheckForUpdates
                )
                GlassActionButton(
                    text = "访问官网",
                    icon = Icons.Outlined.Public,
                    color = PrimaryBlue,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        val url = com.cheku.app.config.RuntimeConfigProvider.websiteBaseUrl()
                        if (url.isNotBlank()) {
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                            }
                        }
                    }
                )
            }
        }
        }
        pageSection("charge") {
        SectionTitle("默认电价")
        AppTextField("家充电费", home, { home = it }, "元/度", KeyboardType.Decimal)
        AppTextField("商充电费", commercial, { commercial = it }, "元/度", KeyboardType.Decimal)
        AppTextField("商充服务费", service, { service = it }, "元/度", KeyboardType.Decimal)
        GlassActionButton("保存电价预设", Icons.Outlined.Save, PrimaryBlue) {
            onUpdateChargePreset(home, commercial, service)
        }
        }
        pageSection("unit") {
        SectionTitle("单位制度")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DistanceUnit.entries.forEach { unit ->
                val selected = state.settings.distanceUnit == unit
                GlassActionButton(
                    text = unit.label,
                    icon = if (unit == DistanceUnit.KmPer100) Icons.Outlined.WaterDrop else Icons.Outlined.Speed,
                    color = if (selected) PrimaryBlue else TextSecondary,
                    modifier = Modifier.weight(1f)
                ) { onUpdateDistanceUnit(unit) }
            }
        }
        }
        pageSection("sponsors") {
        SectionTitle("赞助名单")
        CardBlock {
            var sponsorNamesOnScreen by remember { mutableStateOf(true) }
            val windowHeightPx = with(LocalDensity.current) {
                LocalConfiguration.current.screenHeightDp.dp.toPx()
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .graphicsLayer { }
                    .onGloballyPositioned { coordinates ->
                        val bounds = coordinates.boundsInWindow()
                        val onScreen = bounds.bottom > 0f && bounds.top < windowHeightPx
                        if (onScreen != sponsorNamesOnScreen) sponsorNamesOnScreen = onScreen
                    }
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    GoldenSponsorNames(
                        names = listOf("NICEDD", "DYoung"),
                        running = sponsorNamesOnScreen
                    )
                }
            }
        }
        }
    }

    if (showWechatDonationQr) {
        val qrChrome = rememberDialogChrome { showWechatDonationQr = false }
        Dialog(onDismissRequest = qrChrome.dismiss) {
            GlassDialogMotionHost(shown = qrChrome.shown, onExited = qrChrome.onExited) {
            GlassFloatingSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 380.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("微信赞助", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Black)
                    Image(
                        painter = painterResource(R.drawable.wechat_donation_qr),
                        contentDescription = "微信赞助二维码",
                        modifier = Modifier.size(320.dp)
                    )
                    Text(
                        "点击下方按钮，将二维码图片直接交给手机系统扫码识别。",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                    GlassActionButton("系统扫码识别", Icons.Outlined.QrCodeScanner, Success) {
                        openWechatDonationQrInSystemScanner(context)
                    }
                    GlassDialogAction(onClick = { showWechatDonationQr = false }) { Text("关闭") }
                }
            }
            }   // AnimatedVisibility（捐赠二维码弹窗出入场）
        }
    }
}

@Composable
private fun GoldenSponsorNames(
    names: List<String>,
    /** 是否允许扫光继续跑 */
    running: Boolean = true
) {
    val phase = remember { Animatable(0f) }
    var angleDegrees by remember { mutableStateOf(0f) }
    val edgeFastEasing = remember {
        Easing { fraction ->
            val wave = kotlin.math.sin(fraction * Math.PI * 2.0).toFloat()
            (fraction + wave * 0.045f).coerceIn(0f, 1f)
        }
    }
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        while (true) {
            angleDegrees = Random.nextFloat() * 360f
            phase.snapTo(0f)
            phase.animateTo(
                targetValue = 1f,
                animationSpec = tween(3_400, easing = edgeFastEasing)
            )
        }
    }
    val position = phase.value
    val radians = Math.toRadians(angleDegrees.toDouble())
    val directionX = kotlin.math.cos(radians).toFloat()
    val directionY = kotlin.math.sin(radians).toFloat()
    val travel = (position - 0.5f) * 1_120f
    val center = Offset(210f + directionX * travel, 14f + directionY * travel)
    val halfBeam = 125f
    val start = Offset(center.x - directionX * halfBeam, center.y - directionY * halfBeam)
    val end = Offset(center.x + directionX * halfBeam, center.y + directionY * halfBeam)
    names.forEach { name ->
        Text(
            text = name,
            modifier = Modifier.fillMaxWidth(),
            style = LocalTextStyle.current.copy(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF9A6A10), Color(0xFFFFE7A3), Color(0xFFD49A25), Color(0xFF9A6A10)),
                    start = start,
                    end = end
                )
            ),
            fontSize = 16.sp,
            fontWeight = FontWeight.Black
        )
    }
}

@Composable
private fun GlassActionButton(
    text: String,
    icon: ImageVector?,
    color: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingText: String? = null,
    onClick: () -> Unit
) {
    GlassDialogAction(onClick = onClick, modifier = modifier.fillMaxWidth(), enabled = enabled) {
        if (leadingText != null) {
            Text(leadingText, fontSize = 18.sp, color = color)
        } else if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = color)
        }
        Spacer(Modifier.width(9.dp))
        Text(text, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
private fun PlanCard(
    title: String,
    priceOptions: List<String>,
    badge: String?,
    features: List<String>,
    modifier: Modifier = Modifier,
    accentColor: Color = Success,
    pendingFeatures: Set<String> = emptySet(),
    onWechat: (() -> Unit)? = null,
    onAlipay: (() -> Unit)? = null
) {
    val readyFeatures = features.filterNot { it in pendingFeatures }
    val developingFeatures = features.filter { it in pendingFeatures }
    CardBlock(modifier = modifier, level = GlassLevel.L1_Card) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontSize = 17.sp, fontWeight = FontWeight.Black, color = TextPrimary)
                if (badge != null) {
                    Spacer(Modifier.weight(1f))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(accentColor.copy(alpha = 0.12f))
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(badge, color = accentColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            PriceOptions(priceOptions, accentColor)
            if (onWechat != null || onAlipay != null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    onWechat?.let { onClick ->
                        GlassDialogAction(
                            onClick = onClick,
                            modifier = Modifier.weight(1f).height(42.dp)) { Text("微信", fontWeight = FontWeight.Bold, color = Success) }
                    }
                    onAlipay?.let { onClick ->
                        GlassDialogAction(
                            onClick = onClick,
                            modifier = Modifier.weight(1f).height(42.dp)) { Text("支付宝", fontWeight = FontWeight.Bold, color = PrimaryBlue) }
                    }
                }
            }

            if (readyFeatures.isNotEmpty()) {
                readyFeatures.forEach { feature ->
                    FeatureLine(feature)
                }
            }

            if (developingFeatures.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(1.dp)
                            .background(GlassTokens.strokeHairline)
                    )
                    Text(
                        "开发中",
                        modifier = Modifier.padding(horizontal = 8.dp),
                        color = Warning,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(1.dp)
                            .background(GlassTokens.strokeHairline)
                    )
                }
                developingFeatures.forEach { feature ->
                    FeatureLine(feature, pending = true)
                }
            }
        }
    }
}

@Composable
private fun PriceOptions(options: List<String>, accentColor: Color) {
    var selectedIndex by rememberSaveable(options.joinToString("|")) { mutableStateOf(0) }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.chunked(2).forEachIndexed { rowIndex, rowOptions ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowOptions.forEachIndexed { columnIndex, option ->
                    val index = rowIndex * 2 + columnIndex
                    val selected = selectedIndex == index
                    GlassPanel(
                        level = GlassLevel.S1_Control,
                        backdrop = rememberLayerAwareBackdrop(),
                        shape = RoundedCornerShape(12.dp),
                        accentWash = if (selected) accentColor.copy(alpha = 0.55f) else null,
                        onClick = { selectedIndex = index },
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                            .padding(horizontal = 2.dp)
                    ) {
                        Text(
                            option,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black,
                            color = if (selected) Color.White else accentColor,
                            maxLines = 1,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                if (rowOptions.size == 1 && options.size > 1) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun FeatureLine(text: String, modifier: Modifier = Modifier, pending: Boolean = false) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 0.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(5.dp)
                .clip(RoundedCornerShape(2.5.dp))
                .background(if (pending) Warning else PrimaryBlue)
        )
        Spacer(Modifier.width(7.dp))
        Text(text, color = TextSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** 涨跌色 */
private val OilRise get() = themeColor(Color(0xFFE5453C), Color(0xFFFF6F63))
private val OilFall get() = themeColor(Color(0xFF1D9B58), Color(0xFF3ED598))

/** 油号配色 */
private fun oilGradeColor(grade: String): Color = when (grade) {
    "95" -> themeColor(Color(0xFF7B5BD6), Color(0xFFB9A5FF))
    "98" -> themeColor(Color(0xFFE08A00), Color(0xFFFFC061))
    "0" -> themeColor(Color(0xFF2E8B7A), Color(0xFF6FD3BE))
    else -> PrimaryBlue
}

private fun oilGradeLabel(grade: String): String = "$grade#"

private fun oilPriceText(value: Double?): String =
    value?.takeIf { it > 0.0 }?.let { String.format(Locale.CHINA, "%.2f", it) } ?: "--"

/** 涨跌文案 */
private fun oilDeltaText(quote: OilGradeQuote?): String {
    if (quote == null || quote.price <= 0.0) return "—"
    if (kotlin.math.abs(quote.delta) < 0.005) return "持平"
    val arrow = if (quote.delta > 0) "↑" else "↓"
    return "$arrow ${String.format(Locale.CHINA, "%.2f", kotlin.math.abs(quote.delta))}"
}

private fun oilShortDate(date: String): String {
    val parts = date.split("-")
    if (parts.size < 3) return date
    return "${parts[1].trimStart('0')}/${parts[2].trimStart('0')}"
}

@Composable
private fun OilPriceModule(snapshot: OilPriceSnapshot?, isFetching: Boolean) {
    var pickedGrade by rememberSaveable { mutableStateOf("92") }
    val grades = snapshot?.grades.orEmpty()
    val activeGrade = grades.firstOrNull { it.grade == pickedGrade }?.grade
        ?: grades.firstOrNull()?.grade
        ?: "92"
    val clock = snapshot?.updatedClock().orEmpty()
    val meta = when {
        snapshot == null && isFetching -> "正在联网更新"
        snapshot == null -> "尚未联网"
        isFetching -> "正在更新"
        else -> buildString {
            append(snapshot.province)
            if (clock.isNotBlank()) append(" · ").append(clock)
            append(" 自动更新")
        }
    }

    CardBlock(level = GlassLevel.L1_Card, contentPadding = 12.dp) {
        // 整块只放一个 Column：CardBlock 内部的 Column 是 spacedBy(7dp)，
        // 这里的行距全部按稿子手控，不能被外层再插一层间距。
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(PrimaryBlue.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.LocalGasStation,
                        contentDescription = null,
                        tint = PrimaryBlue,
                        modifier = Modifier.size(17.dp)
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    "今日油价",
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.2).sp,
                    lineHeight = 19.sp
                )
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(if (isFetching) Warning else Success)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    meta,
                    color = TextTertiary,
                    fontSize = 10.sp,
                    lineHeight = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.height(9.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                OilPriceService.GRADE_ORDER.forEach { grade ->
                    OilGradeCell(
                        grade = grade,
                        quote = grades.firstOrNull { it.grade == grade },
                        selected = grade == activeGrade,
                        accent = oilGradeColor(grade),
                        modifier = Modifier.weight(1f),
                        onClick = { pickedGrade = grade }
                    )
                }
            }

            val points = snapshot?.let { item ->
                item.history.mapNotNull { point -> point.prices[activeGrade]?.let { point.date to it } }
            }.orEmpty()

            if (snapshot != null && points.size >= 2 && snapshot.adjustment != null) {
                OilChartMetaRow(snapshot = snapshot, grade = activeGrade)
                Spacer(Modifier.height(4.dp))
                OilTrendChart(
                    snapshot = snapshot,
                    grade = activeGrade,
                    dates = points.map { it.first },
                    values = points.map { it.second },
                    accent = oilGradeColor(activeGrade)
                )
            } else {
                // 兜底（稿子第 05 节）：历史不足 2 个点就只留四卡 + 一行说明，不画空坐标轴。
                Spacer(Modifier.height(12.dp))
                Text(
                    if (isFetching) "正在获取历史价格…" else "暂无历史数据，联网后自动补全",
                    color = TextTertiary,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

/** 单档油价卡 */
@Composable
private fun OilGradeCell(
    grade: String,
    quote: OilGradeQuote?,
    selected: Boolean,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val background = if (selected) {
        themeColor(Color.White.copy(alpha = 0.66f), Color.White.copy(alpha = 0.13f))
    } else {
        themeColor(Color.White.copy(alpha = 0.30f), Color.White.copy(alpha = 0.05f))
    }
    val border = if (selected) {
        accent.copy(alpha = 0.50f)
    } else {
        themeColor(Color.White.copy(alpha = 0.55f), Color.White.copy(alpha = 0.10f))
    }
    Box(
        modifier = modifier
            .padding(2.dp)
            .drawBehind {
                if (selected) {
                    drawRoundRect(
                        color = accent.copy(alpha = 0.11f),
                        cornerRadius = CornerRadius(15.dp.toPx(), 15.dp.toPx()),
                        style = Stroke(width = 2.dp.toPx())
                    )
                }
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(13.dp))
                .background(background)
                .border(1.dp, border, RoundedCornerShape(13.dp))
                .clickable { onClick() }
                .padding(vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                oilGradeLabel(grade),
                color = if (selected) accent else TextTertiary,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.4.sp,
                lineHeight = 10.sp,
                maxLines = 1
            )
            Text(
                oilPriceText(quote?.price),
                color = if (quote == null) TextTertiary else TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = (-0.3).sp,
                lineHeight = 21.sp,
                maxLines = 1,
                modifier = Modifier.padding(top = 3.dp)
            )
            Text(
                oilDeltaText(quote),
                color = when (quote?.trend) {
                    "up" -> OilRise
                    "down" -> OilFall
                    else -> TextTertiary
                },
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 10.sp,
                maxLines = 1,
                modifier = Modifier.padding(top = 3.dp)
            )
        }
    }
}

/** 图表节标题行 */
@Composable
private fun OilChartMetaRow(snapshot: OilPriceSnapshot, grade: String) {
    val count = snapshot.history.count { it.prices[grade] != null }
    val accent = oilGradeColor(grade)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Text(
            "近 $count 次调价 · ${oilGradeLabel(grade)}",
            color = TextSecondary,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 13.sp,
            maxLines = 1
        )
        Spacer(Modifier.weight(1f))
        OilLegendSwatch(accent, dashed = false)
        Text("历史", color = TextTertiary, fontSize = 9.5.sp, lineHeight = 12.sp)
        Spacer(Modifier.width(1.dp))
        OilLegendSwatch(accent, dashed = true)
        Text("预测", color = TextTertiary, fontSize = 9.5.sp, lineHeight = 12.sp)
    }
}

@Composable
private fun OilLegendSwatch(color: Color, dashed: Boolean) {
    Canvas(Modifier.width(12.dp).height(2.dp)) {
        drawLine(
            color = color,
            start = Offset(0f, size.height / 2f),
            end = Offset(size.width, size.height / 2f),
            strokeWidth = 2.dp.toPx(),
            cap = androidx.compose.ui.graphics.StrokeCap.Round,
            pathEffect = if (dashed) {
                PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 2.dp.toPx()))
            } else {
                null
            }
        )
    }
}

/** 历史折线 + 预测虚线 + 调价进度条 */
@Composable
private fun OilTrendChart(
    snapshot: OilPriceSnapshot,
    grade: String,
    dates: List<String>,
    values: List<Double>,
    accent: Color
) {
    val forecast = snapshot.forecastOf(grade)
    val window = snapshot.adjustment
    if (forecast == null || window == null || values.size < 2) return

    val dark = AppDarkMode
    // 颜色在组合期取好：Canvas 的 draw 阶段只做几何计算与绘制，不再读主题状态。
    val labelArgb = TextTertiary.toArgb()
    val accentArgb = accent.toArgb()
    val gridColor = themeColor(Color(0x33788CAA), Color(0x1AFFFFFF))
    val cardBg = themeColor(Color(0xEBFFFFFF), Color(0xEB141A22))
    val trackColor = themeColor(Color(0x38788CAA), Color(0x29FFFFFF))
    val areaAlpha = if (dark) 0.30f else 0.20f

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // 328:117 是稿子的比例；宽度由父级决定，高度跟着算。
        val chartHeight = maxWidth * (117f / 328f)
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(chartHeight)
        ) {
            val unit = size.width / 328f
            val x0 = 36f * unit
            val x1 = 322f * unit
            val y0 = 10f * unit
            val y1 = 74f * unit
            val barY = 94f * unit

            val all = values + forecast
            var minValue = all.min()
            var maxValue = all.max()
            // 上下留白至少 0.06，刻度对齐到 0.2 网格（端点读起来是整值，不是 8.71/7.29）
            val padding = maxOf(0.06, (maxValue - minValue) * 0.18)
            minValue = kotlin.math.floor((minValue - padding) * 5.0) / 5.0
            maxValue = kotlin.math.ceil((maxValue + padding) * 5.0) / 5.0
            val span = (maxValue - minValue).takeIf { it > 0.0 } ?: 1.0

            fun yOf(value: Double): Float =
                (y1 - ((value - minValue) / span).toFloat() * (y1 - y0))

            val n = all.size
            fun xOf(index: Int): Float = x0 + index * (x1 - x0) / (n - 1)

            val numeric = android.graphics.Paint().apply {
                isAntiAlias = true
                textSize = 9.5f * unit
                color = labelArgb
                typeface = android.graphics.Typeface.MONOSPACE
            }

            for (index in 0..2) {
                val value = maxValue - (maxValue - minValue) * index / 2.0
                val yy = yOf(value)
                drawLine(
                    color = gridColor,
                    start = Offset(x0, yy),
                    end = Offset(x1, yy),
                    strokeWidth = 1f * unit,
                    pathEffect = if (index < 2) {
                        PathEffect.dashPathEffect(floatArrayOf(3f * unit, 4f * unit))
                    } else {
                        null
                    }
                )
                numeric.textAlign = android.graphics.Paint.Align.RIGHT
                drawContext.canvas.nativeCanvas.drawText(
                    String.format(Locale.CHINA, "%.2f", value),
                    x0 - 7f * unit,
                    yy + 3.3f * unit,
                    numeric
                )
            }

            val areaPath = Path().apply {
                moveTo(x0, y1)
                values.forEachIndexed { index, value -> lineTo(xOf(index), yOf(value)) }
                lineTo(xOf(values.lastIndex), y1)
                close()
            }
            drawPath(
                areaPath,
                Brush.verticalGradient(
                    colors = listOf(accent.copy(alpha = areaAlpha), accent.copy(alpha = 0f)),
                    startY = y0,
                    endY = y1
                )
            )

            val linePath = Path().apply {
                values.forEachIndexed { index, value ->
                    if (index == 0) moveTo(xOf(index), yOf(value)) else lineTo(xOf(index), yOf(value))
                }
            }
            drawPath(
                linePath,
                accent,
                style = Stroke(
                    width = 2.2f * unit,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round,
                    join = androidx.compose.ui.graphics.StrokeJoin.Round
                )
            )

            values.forEachIndexed { index, value ->
                val center = Offset(xOf(index), yOf(value))
                drawCircle(cardBg, radius = 2.6f * unit, center = center)
                drawCircle(accent, radius = 2.6f * unit, center = center, style = Stroke(1.8f * unit))
            }

            val lastIndex = values.lastIndex
            val from = Offset(xOf(lastIndex), yOf(values.last()))
            val to = Offset(xOf(lastIndex + 1), yOf(forecast))
            drawLine(
                color = accent.copy(alpha = 0.9f),
                start = from,
                end = to,
                strokeWidth = 2f * unit,
                cap = androidx.compose.ui.graphics.StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f * unit, 4f * unit))
            )
            drawCircle(accent.copy(alpha = 0.12f), radius = 9f * unit, center = to)
            drawCircle(cardBg, radius = 4f * unit, center = to)
            drawCircle(accent, radius = 4f * unit, center = to, style = Stroke(2.2f * unit))

            val bubbleWidth = 54f * unit
            val bubbleHeight = 19f * unit
            val bubbleLeft = minOf(x1 - bubbleWidth, to.x - bubbleWidth + 8f * unit)
            val bubbleTop = to.y - 28f * unit
            drawRoundRect(
                color = cardBg,
                topLeft = Offset(bubbleLeft, bubbleTop),
                size = Size(bubbleWidth, bubbleHeight),
                cornerRadius = CornerRadius(9.5f * unit, 9.5f * unit)
            )
            drawRoundRect(
                color = accent.copy(alpha = 0.35f),
                topLeft = Offset(bubbleLeft, bubbleTop),
                size = Size(bubbleWidth, bubbleHeight),
                cornerRadius = CornerRadius(9.5f * unit, 9.5f * unit),
                style = Stroke(1f * unit)
            )
            val bubblePaint = android.graphics.Paint().apply {
                isAntiAlias = true
                textSize = 10f * unit
                color = accentArgb
                textAlign = android.graphics.Paint.Align.CENTER
                typeface = android.graphics.Typeface.create(
                    android.graphics.Typeface.MONOSPACE,
                    android.graphics.Typeface.BOLD
                )
            }
            drawContext.canvas.nativeCanvas.drawText(
                "预计 ${oilPriceText(forecast)}",
                bubbleLeft + bubbleWidth / 2f,
                bubbleTop + 13.2f * unit,
                bubblePaint
            )

            listOf(0, 2, 5).filter { it <= lastIndex }.forEach { index ->
                numeric.textAlign = if (index == 0) {
                    android.graphics.Paint.Align.LEFT
                } else {
                    android.graphics.Paint.Align.CENTER
                }
                drawContext.canvas.nativeCanvas.drawText(
                    oilShortDate(dates.getOrElse(index) { "" }),
                    xOf(index),
                    y1 + 12f * unit,
                    numeric
                )
            }

            val barWidth = x1 - x0
            val progress = (window.elapsedDays.toFloat() / window.windowDays.toFloat().coerceAtLeast(1f))
                .coerceIn(0f, 1f)
            drawRoundRect(
                color = trackColor,
                topLeft = Offset(x0, barY),
                size = Size(barWidth, 4.5f * unit),
                cornerRadius = CornerRadius(2.25f * unit, 2.25f * unit)
            )
            if (progress > 0f) {
                drawRoundRect(
                    color = accent,
                    topLeft = Offset(x0, barY),
                    size = Size(barWidth * progress, 4.5f * unit),
                    cornerRadius = CornerRadius(2.25f * unit, 2.25f * unit)
                )
            }
            val knob = Offset(x0 + barWidth * progress, barY + 2.25f * unit)
            drawCircle(cardBg, radius = 4.4f * unit, center = knob)
            drawCircle(accent, radius = 4.4f * unit, center = knob, style = Stroke(2f * unit))

            val baseline = barY + 17f * unit
            numeric.textSize = 9.5f * unit
            numeric.textAlign = android.graphics.Paint.Align.LEFT
            drawContext.canvas.nativeCanvas.drawText(
                "${oilShortDate(window.lastDate)} 上次调价",
                x0,
                baseline,
                numeric
            )
            numeric.textAlign = android.graphics.Paint.Align.RIGHT
            drawContext.canvas.nativeCanvas.drawText(
                "${oilShortDate(window.nextDate)} 24时",
                x1,
                baseline,
                numeric
            )
            val daysPaint = android.graphics.Paint().apply {
                isAntiAlias = true
                textSize = 10f * unit
                color = accentArgb
                textAlign = android.graphics.Paint.Align.CENTER
                typeface = android.graphics.Typeface.create(
                    android.graphics.Typeface.MONOSPACE,
                    android.graphics.Typeface.BOLD
                )
            }
            drawContext.canvas.nativeCanvas.drawText(
                "${window.daysLeft} 天后",
                (x0 + x1) / 2f,
                baseline,
                daysPaint
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecordCard(record: RecentRecordItem, onClick: () -> Unit, onLongClick: () -> Unit) {
    val typeColor = when (record.type) {
        RecentRecordType.Fuel -> Color(0xFFE68A2E)
        RecentRecordType.Charge -> PrimaryBlue
        RecentRecordType.Mileage -> Success
    }
    val icon = when (record.type) {
        RecentRecordType.Mileage -> Icons.Outlined.Speed
        RecentRecordType.Fuel -> Icons.Outlined.LocalGasStation
        RecentRecordType.Charge -> Icons.Outlined.BatteryChargingFull
    }
    CardBlock(
        onClick = onClick,
        onLongClick = onLongClick
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = typeColor, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(record.title, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(record.timeText, color = TextTertiary, fontSize = 12.sp)
            }

            if (record.type == RecentRecordType.Mileage) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("${formatMileage(record.mileageValue)} km", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = typeColor)
                    Spacer(Modifier.weight(1f))
                    if (record.tripText.isNotBlank()) {
                        Text(record.tripText, color = TextSecondary, fontSize = 14.sp)
                    }
                }
                Text(record.vehicleName, color = TextTertiary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            } else {
                Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(record.amountLabel, color = TextTertiary, fontSize = 12.sp)
                        Text(
                            "${formatDecimal(record.amountValue)} ${record.amountUnit}",
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                            color = typeColor,
                            maxLines = 1
                        )
                    }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                        Text("实付金额", color = TextTertiary, fontSize = 12.sp)
                        Text(
                            "¥${formatMoney(record.paidAmountValue)}",
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                            color = typeColor,
                            maxLines = 1
                        )
                    }
                }

                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${record.vehicleName} · ${formatMileage(record.mileageValue)} km",
                        color = TextTertiary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (record.tripText.isNotBlank()) {
                        Spacer(Modifier.width(8.dp))
                        Text(record.tripText, color = TextHint, fontSize = 12.sp)
                    }
                }

                Text(
                    record.statusText,
                    color = if (record.statusText.startsWith("已结算")) Success else TextSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                val discount = record.orderAmountValue - record.paidAmountValue
                if (discount > 0.005) {
                    Text(
                        "订单 ¥${formatMoney(record.orderAmountValue)} · 优惠 ¥${formatMoney(discount)}",
                        color = TextTertiary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun RecordEditScreen(
    record: RecentRecordItem,
    vehicleName: String,
    onBack: () -> Unit,
    onSaveMileage: (String, String, String, String) -> Unit,
    onSaveEnergy: (String, EnergyRecordFormInput) -> Unit
) {
    var mileage by rememberSaveable { mutableStateOf(formatDecimal(record.mileageValue)) }
    var roadFee by rememberSaveable { mutableStateOf(formatDecimal(record.roadFeeAmount)) }
    var note by rememberSaveable { mutableStateOf(record.note) }
    var amount by rememberSaveable { mutableStateOf(formatDecimal(record.amountValue)) }
    var orderAmount by rememberSaveable { mutableStateOf(record.orderAmountValue.trimOne()) }
    var paidAmount by rememberSaveable { mutableStateOf(record.paidAmountValue.trimOne()) }
    var paidAmountManuallyEdited by rememberSaveable {
        mutableStateOf(kotlin.math.abs(record.orderAmountValue - record.paidAmountValue) > 0.005)
    }
    var isFull by rememberSaveable { mutableStateOf(record.isFull) }
    var missedPrevious by rememberSaveable { mutableStateOf(record.missedPrevious) }
    var fuelGrade by rememberSaveable { mutableStateOf(record.fuelGrade.ifBlank { "95" }) }
    var unitPrice by rememberSaveable { mutableStateOf(record.unitPriceValue.trimOne()) }
    var chargeMethod by rememberSaveable { mutableStateOf(record.chargeMethod.ifBlank { "家充" }) }
    var electricityPrice by rememberSaveable { mutableStateOf(record.electricityPriceValue.trimOne()) }
    var serviceFee by rememberSaveable { mutableStateOf(record.serviceFeeValue.trimOne()) }
    var chargeLimitSoc by rememberSaveable { mutableStateOf(record.chargeLimitSoc?.toString() ?: "100") }
    var recordTime by rememberSaveable(record.recordId) {
        mutableStateOf(SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date(record.createdAt)))
    }
    var recordTimeMillis by remember(record.recordId) { mutableStateOf(record.createdAt) }
    var showRecordTimePicker by remember { mutableStateOf(false) }

    fun updateOrder(value: String) {
        orderAmount = value
        if (!paidAmountManuallyEdited) paidAmount = value
    }

    fun form(type: EnergyRecordType) = EnergyRecordFormInput(
        type = type,
        mileage = mileage,
        amount = amount,
        orderAmount = orderAmount,
        paidAmount = paidAmount.ifBlank { orderAmount },
        isFull = isFull,
        missedPrevious = missedPrevious,
        fuelGrade = fuelGrade,
        unitPrice = unitPrice,
        chargeMethod = chargeMethod,
        electricityPrice = electricityPrice,
        serviceFee = serviceFee,
        chargeLimitSoc = chargeLimitSoc,
        note = note
    )

    fun chargeUnitPrice(): String = combinedChargePrice(electricityPrice, serviceFee)

    Page("编辑记录") {
        BackButton(onBack)
        InfoCard("车辆", vehicleName)
        RecordSectionTitle("里程信息")
        AppTextField("里程", mileage, { mileage = it }, "km", KeyboardType.Decimal)
        SelectRow("记录时间", recordTime) { showRecordTimePicker = true }
        when (record.type) {
            RecentRecordType.Mileage -> {
                AppTextField("ETC/路桥/停车/罚款", roadFee, { roadFee = it }, "元", KeyboardType.Decimal)
                AppTextField("备注", note, { note = it }, "", KeyboardType.Text)
                GlassActionButton("保存修改", Icons.Outlined.Save, PrimaryBlue) { onSaveMileage(mileage, recordTime, note, roadFee) }
            }
            RecentRecordType.Fuel -> {
                FuelRecordFields(
                    orderAmount = orderAmount,
                    onOrderAmountChange = {
                        updateOrder(it)
                        amount = deriveAmount(it, unitPrice)
                    },
                    paidAmount = paidAmount,
                    onPaidAmountChange = {
                        paidAmount = it
                        paidAmountManuallyEdited = true
                    },
                    onRestorePaidAmount = {
                        paidAmountManuallyEdited = false
                        paidAmount = orderAmount
                    },
                    fuelGrade = fuelGrade,
                    onFuelGradeChange = { fuelGrade = it },
                    fuelPrice = unitPrice,
                    onFuelPriceChange = {
                        unitPrice = it
                        if (amount.isNotBlank()) updateOrder(deriveCost(unitPrice, amount))
                    },
                    amount = amount,
                    onAmountChange = {
                        amount = it
                        updateOrder(deriveCost(unitPrice, amount))
                    },
                    isFull = isFull,
                    onFullChange = { isFull = it },
                    missedPrevious = missedPrevious,
                    onMissedPreviousChange = { missedPrevious = it }
                )
                RecordSectionTitle("备注")
                AppTextField("备注", note, { note = it }, "", KeyboardType.Text)
                GlassActionButton("保存修改", Icons.Outlined.Save, PrimaryBlue) {
                    onSaveEnergy(recordTime, form(EnergyRecordType.Fuel))
                }
            }
            RecentRecordType.Charge -> {
                ChargeRecordFields(
                    orderAmount = orderAmount,
                    onOrderAmountChange = {
                        updateOrder(it)
                        amount = deriveAmount(it, chargeUnitPrice())
                    },
                    paidAmount = paidAmount,
                    onPaidAmountChange = {
                        paidAmount = it
                        paidAmountManuallyEdited = true
                    },
                    onRestorePaidAmount = {
                        paidAmountManuallyEdited = false
                        paidAmount = orderAmount
                    },
                    method = chargeMethod,
                    onMethodChange = { chargeMethod = it },
                    electricityPrice = electricityPrice,
                    onElectricityPriceChange = {
                        electricityPrice = it
                        if (amount.isNotBlank()) updateOrder(deriveCost(chargeUnitPrice(), amount))
                    },
                    serviceFee = serviceFee,
                    onServiceFeeChange = {
                        serviceFee = it
                        if (amount.isNotBlank()) updateOrder(deriveCost(chargeUnitPrice(), amount))
                    },
                    amount = amount,
                    onAmountChange = {
                        amount = it
                        updateOrder(deriveCost(chargeUnitPrice(), amount))
                    },
                    chargeLimitSoc = chargeLimitSoc,
                    onChargeLimitChange = { chargeLimitSoc = it },
                    chargeLimitHint = "仅修改本条记录",
                    isFull = isFull,
                    onFullChange = { isFull = it },
                    missedPrevious = missedPrevious,
                    onMissedPreviousChange = { missedPrevious = it }
                )
                RecordSectionTitle("备注")
                AppTextField("备注", note, { note = it }, "", KeyboardType.Text)
                GlassActionButton("保存修改", Icons.Outlined.Save, PrimaryBlue) {
                    onSaveEnergy(recordTime, form(EnergyRecordType.Charge))
                }
            }
        }
    }

    if (showRecordTimePicker) {
        GlassDateTimePickerDialog(
            initialMillis = recordTimeMillis,
            onDismissRequest = { showRecordTimePicker = false },
            onConfirm = { millis ->
                recordTimeMillis = millis
                recordTime = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date(millis))
                showRecordTimePicker = false
            }
        )
    }
}

@Composable
private fun BackButton(onBack: () -> Unit) {
    GlassDialogAction(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null, tint = PrimaryBlue)
        Spacer(Modifier.width(8.dp))
        Text("返回", color = PrimaryBlue, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SelectRow(
    label: String,
    value: String,
    valueMaxLines: Int = 1,
    supportingValue: String? = null,
    onClick: () -> Unit
) {
    CardBlock(onClick = onClick) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = TextSecondary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    value,
                    color = PrimaryBlue,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = valueMaxLines,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.End
                )
                supportingValue?.let {
                    Text(
                        text = it,
                        color = TextTertiary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.End
                    )
                }
            }
        }
    }
}

@Composable
private fun RecordModePicker(
    selected: RecordMode,
    options: List<RecordMode>,
    enabledOptions: Set<RecordMode>,
    onSelect: (RecordMode) -> Unit,
    onUnavailable: (RecordMode) -> Unit
) {
    CardBlock(level = GlassLevel.L1_Card, contentPadding = 6.dp) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val optionWidth = maxWidth / options.size
            val selectedIndex = options.indexOf(selected).coerceAtLeast(0)
            var pressedIndex by remember { mutableStateOf(-1) }
            val selectionX by animateDpAsState(
                targetValue = optionWidth * selectedIndex,
                animationSpec = GlassMotion.selectionSpec(),
                label = "record-mode-position"
            )
            LiquidGlassPill(
                backdrop = rememberLayerAwareBackdrop(),
                modifier = Modifier
                    .offset { IntOffset(selectionX.roundToPx(), 0) }
                    .width(optionWidth)
                    .height(38.dp)
                    .padding(horizontal = 2.dp),
                shape = RoundedCornerShape(18.dp),
                pressProgress = { if (pressedIndex == selectedIndex) 1f else 0f }
            ) {}
            Row(Modifier.fillMaxWidth()) {
                options.forEachIndexed { index, option ->
                    val active = selected == option
                    val interaction = remember { MutableInteractionSource() }
                    val isPressed by interaction.collectIsPressedAsState()
                    LaunchedEffect(isPressed) {
                        pressedIndex = if (isPressed) index else -1
                    }
                    Box(
                        modifier = Modifier
                            .width(optionWidth)
                            .height(38.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .clickable(
                                interactionSource = interaction,
                                indication = null
                            ) {
                                if (option in enabledOptions) onSelect(option) else onUnavailable(option)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            option.title,
                            color = when {
                                active -> TextPrimary
                                option !in enabledOptions -> TextHint
                                else -> TextSecondary
                            },
                            fontSize = 17.sp,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCard(
    label: String,
    value: String,
    unit: String,
    modifier: Modifier = Modifier,
    color: Color = PrimaryBlue,
    contentPadding: androidx.compose.ui.unit.Dp = 14.dp,
    valueFontSize: androidx.compose.ui.unit.TextUnit = 20.sp,
    valueMaxLines: Int = 1,
    unitInHeader: Boolean = false
) {
    CardBlock(modifier = modifier, level = GlassLevel.L1_Card, contentPadding = contentPadding) {
        if (unitInHeader) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(label, color = TextSecondary, fontSize = 13.sp, maxLines = 1)
                if (unit.isNotBlank()) {
                    Text(
                        unit,
                        color = color,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }
        } else {
            Text(label, color = TextSecondary, fontSize = 13.sp)
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val unitSpace = if (unitInHeader || unit.isBlank()) 0f else unit.length * 7f + 8f
            val valueSpace = (maxWidth.value - unitSpace).coerceAtLeast(24f)
            val fittedFontSize = if (value.isBlank()) {
                valueFontSize.value
            } else {
                minOf(valueFontSize.value, valueSpace / (value.length * 0.58f))
            }.coerceAtLeast(9f)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Text(
                    value,
                    fontSize = fittedFontSize.sp,
                    fontWeight = FontWeight.Black,
                    color = color,
                    maxLines = valueMaxLines,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier.weight(1f)
                )
                if (!unitInHeader && unit.isNotBlank()) {
                    Spacer(Modifier.width(4.dp))
                    Text(
                        unit,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = color,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun MoneyStatCard(
    value: Double,
    modifier: Modifier = Modifier
) {
    val text = formatMoney(value)
    CardBlock(modifier = modifier, level = GlassLevel.L1_Card, contentPadding = 11.dp) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("总花费", color = TextSecondary, fontSize = 13.sp)
            Text("元", color = Color(0xFF7C5CE7), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val availableWidth = maxWidth.value
            val estimatedFontSize = (availableWidth / (text.length * 0.58f))
                .coerceIn(10f, 20f)
            Text(
                text = text,
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF7C5CE7),
                fontSize = estimatedFontSize.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip
            )
        }
    }
}

@Composable
private fun InfoCard(title: String, body: String) {
    CardBlock {
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(body, color = TextTertiary)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, fontSize = 20.sp, fontWeight = FontWeight.Black, color = TextPrimary)
}

@Composable
private fun AppTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    suffix: String,
    keyboardType: KeyboardType,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    textAlign: TextAlign? = null,
    maxNumericValue: Double? = null
) {
    val backdrop = rememberLayerAwareBackdrop()
    val spec = GlassTokens.specFor(GlassLevel.S1_Control)
    val shape = GlassTokens.shapeControl
    // 洗色必须在组合期求值：onDrawSurface 是 DrawScope lambda，不是 @Composable 上下文。
    val fillColor = if (enabled) GlassTokens.controlFill else GlassTokens.l1Fill
    val accent = PrimaryBlue
    val selectionColors = remember(accent) {
        TextSelectionColors(
            handleColor = accent,
            backgroundColor = accent.copy(alpha = 0.30f)
        )
    }
    // focus highlight：聚焦时提高高光、加深内阴影并点亮 label。
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .drawBackdrop(
                backdrop = backdrop ?: emptyBackdrop(),
                shape = { shape },
                effects = {
                    if (backdrop != null) {
                        vibrancy()
                        // 磨砂与折射参数取自 S1_Control。
                        blur(spec.blurRadiusDp.dp.toPx())
                        applyGlassLens(spec, 0f)
                    }
                },
                highlight = {
                    Highlight.Default.copy(
                        alpha = when {
                            !enabled -> 0.16f
                            focused -> 0.72f
                            else -> 0.40f
                        }
                    )
                },
                shadow = { Shadow(radius = 8.dp, alpha = 0.14f) },
                innerShadow = {
                    when {
                        !enabled -> null
                        focused -> InnerShadow(radius = 2.dp, alpha = 0.22f)
                        else -> InnerShadow(radius = 1.dp, alpha = 0.12f)
                    }
                },
                onDrawSurface = { drawRect(fillColor) }
            )
            .padding(horizontal = 14.dp, vertical = 9.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                label,
                color = if (focused && enabled) accent else TextTertiary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                CompositionLocalProvider(LocalTextSelectionColors provides selectionColors) {
                    BasicTextField(
                        value = value,
                        onValueChange = {
                            onValueChange(sanitizeTextFieldInput(it, keyboardType, maxNumericValue))
                        },
                        singleLine = true,
                        enabled = enabled,
                        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                        textStyle = LocalTextStyle.current.copy(
                            fontSize = 15.sp,
                            color = if (enabled) TextPrimary else TextTertiary,
                            textAlign = textAlign ?: TextAlign.Start
                        ),
                        cursorBrush = SolidColor(accent),
                        interactionSource = interactionSource,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (suffix.isNotBlank()) {
                    Spacer(Modifier.width(6.dp))
                    Text(suffix, color = TextTertiary, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun AppTextArea(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    minLines: Int = 5,
    maxChars: Int = 1000,
    placeholder: String? = null
) {
    // 与 AppTextField 同源：输入框须"透出下方一层卡片"，用 layer-aware 采样。
    val backdrop = rememberLayerAwareBackdrop()
    val spec = GlassTokens.specFor(GlassLevel.S1_Control)
    val shape = GlassTokens.shapeControl
    val fillColor = if (enabled) GlassTokens.controlFill else GlassTokens.l1Fill
    val accent = PrimaryBlue
    val selectionColors = remember(accent) {
        TextSelectionColors(
            handleColor = accent,
            backgroundColor = accent.copy(alpha = 0.30f)
        )
    }
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .drawBackdrop(
                backdrop = backdrop ?: emptyBackdrop(),
                shape = { shape },
                effects = {
                    if (backdrop != null) {
                        vibrancy()
                        blur(spec.blurRadiusDp.dp.toPx())
                        applyGlassLens(spec, 0f)
                    }
                },
                highlight = {
                    Highlight.Default.copy(
                        alpha = when {
                            !enabled -> 0.16f
                            focused -> 0.72f
                            else -> 0.40f
                        }
                    )
                },
                shadow = { Shadow(radius = 8.dp, alpha = 0.14f) },
                innerShadow = {
                    when {
                        !enabled -> null
                        focused -> InnerShadow(radius = 2.dp, alpha = 0.22f)
                        else -> InnerShadow(radius = 1.dp, alpha = 0.12f)
                    }
                },
                onDrawSurface = { drawRect(fillColor) }
            )
            .padding(horizontal = 14.dp, vertical = 9.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    label,
                    color = if (focused && enabled) accent else TextTertiary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "${value.length}/$maxChars",
                    color = if (value.length >= maxChars) Error else TextTertiary,
                    fontSize = 11.sp
                )
            }
            CompositionLocalProvider(LocalTextSelectionColors provides selectionColors) {
                BasicTextField(
                    value = value,
                    onValueChange = { onValueChange(sanitizeMultilineInput(it, maxChars)) },
                    singleLine = false,
                    minLines = minLines,
                    maxLines = minLines + 4,
                    enabled = enabled,
                    // 多行文本：不做数字/单行清洗，交给 sanitizeMultilineInput。
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    textStyle = LocalTextStyle.current.copy(
                        fontSize = 15.sp,
                        color = if (enabled) TextPrimary else TextTertiary
                    ),
                    cursorBrush = SolidColor(accent),
                    interactionSource = interactionSource,
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { inner ->
                        if (value.isEmpty() && placeholder != null) {
                            Text(placeholder, color = TextTertiary, fontSize = 14.sp)
                        }
                        inner()
                    }
                )
            }
        }
    }
}

/** 多行文本的输入清洗 */
internal fun sanitizeMultilineInput(raw: String, maxChars: Int): String =
    raw.replace("\r\n", "\n")
        .replace("\r", "\n")
        .filterNot { it == '\t' || (it != '\n' && it.isISOControl()) }
        .take(maxChars)

internal fun sanitizeTextFieldInput(
    raw: String,
    keyboardType: KeyboardType,
    maxNumericValue: Double?
): String {
    val sanitized = when (keyboardType) {
        KeyboardType.Number -> raw.filter(Char::isDigit).take(9)
        KeyboardType.Decimal -> {
            val filtered = raw.filter { it.isDigit() || it == '.' }
            val hasDecimalPoint = filtered.contains('.')
            val integerPart = filtered.substringBefore('.').take(9)
            val decimalPart = filtered.substringAfter('.', "").filter(Char::isDigit).take(4)
            when {
                hasDecimalPoint && integerPart.isBlank() -> "0.$decimalPart"
                hasDecimalPoint -> "$integerPart.$decimalPart"
                else -> integerPart
            }
        }
        else -> raw.filterNot { it == '\n' || it == '\r' || it.isISOControl() }.take(120)
    }
    val numericValue = sanitized.toDoubleOrNull()
    if (maxNumericValue != null && numericValue != null && numericValue > maxNumericValue) {
        return if (maxNumericValue % 1.0 == 0.0) {
            maxNumericValue.toInt().toString()
        } else {
            maxNumericValue.toString()
        }
    }
    return sanitized
}

@Composable
private fun AppFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    accent: Color = PrimaryBlue
) {
    LiquidButton(
        onClick = onClick,
        backdrop = rememberLayerAwareBackdrop(),
        modifier = modifier,
        tint = if (selected) accent else Color.Unspecified
    ) {
        Text(
            label,
            color = if (selected) Color.White else accent.copy(alpha = 0.82f),
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold
        )
    }
}

/** L1/L2 液态玻璃卡片 */
/** 实心卡片底色 */
@Composable
private fun solidCardFill(): Color =
    androidx.compose.ui.graphics
        .lerp(Color.White, Color(0xFF202C3B), LocalAppDarkLevel.current)
        .copy(alpha = 0.96f)

@Composable
private fun CardBlock(
    modifier: Modifier = Modifier,
    level: GlassLevel = GlassLevel.L1_Card,
    accentWash: Color? = null,
    contentPadding: androidx.compose.ui.unit.Dp = 14.dp,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    /** 非 null 时在玻璃面之上、内容之下垫一层实心底板 */
    solidFill: Color? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    GlassPanel(
        level = level,
        backdrop = rememberLayerAwareBackdrop(),
        modifier = modifier.fillMaxWidth(),
        shape = GlassTokens.shapeCard,
        accentWash = accentWash,
        onClick = onClick,
        onLongClick = onLongClick
    ) {
        if (solidFill != null) {
            Box(
                Modifier
                    .matchParentSize()
                    .clip(GlassTokens.shapeCard)
                    .background(solidFill)
            )
        }
        Column(
            modifier = Modifier.padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(7.dp),
            content = content
        )
    }
}

private fun vehicleKindLabel(category: VehicleCategory, energy: EnergyType): String =
    vehicleKindFor(category, energy)?.label ?: "${category.label}/${energy.label}"

internal fun vehicleKindFor(category: VehicleCategory, energy: EnergyType): VehicleKind? =
    when {
        category == VehicleCategory.Car && energy == EnergyType.Fuel -> VehicleKind.FuelCar
        category == VehicleCategory.Car && energy != EnergyType.Human -> VehicleKind.NewEnergyCar
        category == VehicleCategory.Motorcycle &&
            energy in listOf(EnergyType.Fuel, EnergyType.Hybrid, EnergyType.PlugInHybrid) ->
            VehicleKind.FuelMotorcycle
        category == VehicleCategory.ElectricMotorcycle && energy == EnergyType.Electric -> VehicleKind.ElectricMotorcycle
        category == VehicleCategory.ElectricBike && energy == EnergyType.Electric -> VehicleKind.ElectricBike
        category == VehicleCategory.Bicycle && energy == EnergyType.Human -> VehicleKind.Bicycle
        else -> null
    }

internal fun energyOptionsFor(kind: VehicleKind?): List<EnergyType> =
    when (kind) {
        VehicleKind.FuelCar -> listOf(EnergyType.Fuel)
        VehicleKind.FuelMotorcycle -> listOf(
            EnergyType.Fuel,
            EnergyType.Hybrid,
            EnergyType.PlugInHybrid
        )
        VehicleKind.NewEnergyCar -> listOf(
            EnergyType.Electric,
            EnergyType.Hybrid,
            EnergyType.PlugInHybrid,
            EnergyType.RangeExtender
        )
        VehicleKind.ElectricMotorcycle, VehicleKind.ElectricBike -> listOf(EnergyType.Electric)
        VehicleKind.Bicycle -> listOf(EnergyType.Human)
        null -> EnergyType.entries
    }

internal fun resolvedEnergyFor(kind: VehicleKind, currentEnergy: EnergyType): EnergyType {
    val options = energyOptionsFor(kind)
    return currentEnergy.takeIf(options::contains) ?: kind.defaultEnergy
}

private fun vehicleCapacitySummary(vehicle: VehicleEntity): String {
    val parts = buildList {
        vehicle.fuelTankCapacityLiters?.takeIf { it > 0.0 }?.let { add("油箱 ${it.trimOne()}L") }
        vehicle.batteryCapacityKwh?.takeIf { it > 0.0 }?.let { add("电池 ${it.trimOne()}kWh") }
    }
    return parts.joinToString(" ? ").ifBlank { "未填写容量" }
}

private fun Double.trimOne(): String =
    if (this % 1.0 == 0.0) toInt().toString() else String.format(Locale.CHINA, "%.1f", this)

private fun deriveAmount(cost: String, unitPrice: String): String {
    val costValue = cost.toDoubleOrNull() ?: return ""
    val priceValue = unitPrice.toDoubleOrNull() ?: return ""
    if (priceValue <= 0.0) return ""
    return formatDecimal(costValue / priceValue)
}

private fun deriveCost(unitPrice: String, amount: String): String {
    val priceValue = unitPrice.toDoubleOrNull() ?: return ""
    val amountValue = amount.toDoubleOrNull() ?: return ""
    return formatDecimal(priceValue * amountValue)
}

private fun combinedChargePrice(electricityPrice: String, serviceFee: String): String {
    val electricity = electricityPrice.toDoubleOrNull() ?: 0.0
    val service = serviceFee.toDoubleOrNull() ?: 0.0
    return formatDecimal(electricity + service)
}

private fun formatDecimal(value: Double): String = String.format(Locale.CHINA, "%.2f", value)
