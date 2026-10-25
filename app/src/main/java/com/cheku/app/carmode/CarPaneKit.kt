package com.cheku.app.carmode

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.material.icons.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.outlined.KeyboardArrowRight
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.runtime.MutableFloatState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.cheku.app.glass.GlassLevel
import com.cheku.app.glass.GlassTokens
import com.cheku.app.glass.LocalAppDarkLevel
import com.cheku.app.glass.components.GlassPanel
import com.cheku.app.glass.components.LiquidGlassDialogButton
import com.cheku.app.glass.components.glassPressFeedback
import com.cheku.app.glass.rememberLayerAwareBackdrop
import com.cheku.glass.ui.components.LiquidButton
import com.cheku.glass.ui.components.LiquidSlider
import com.cheku.glass.ui.components.LiquidToggle
import kotlin.math.abs

internal val HubTextPrimary: Color
    @Composable get() = lerp(Color(0xFF333333), Color(0xFFF3F6FA), LocalAppDarkLevel.current)
internal val HubTextSecondary: Color
    @Composable get() = lerp(Color(0xFF666666), Color(0xFFC4CDD8), LocalAppDarkLevel.current)
internal val HubTextTertiary: Color
    @Composable get() = lerp(Color(0xFF999999), Color(0xFF98A6B7), LocalAppDarkLevel.current)
internal val HubTextHint: Color
    @Composable get() = lerp(Color(0xFFAAAAAA), Color(0xFF8391A2), LocalAppDarkLevel.current)
internal val HubPrimaryBlue: Color
    @Composable get() = lerp(Color(0xFF0058BC), Color(0xFF79AFFF), LocalAppDarkLevel.current)

internal val HubError = Color(0xFFFF3B30)
internal val HubSuccess = Color(0xFF1D9B58)

/** 面板骨架 */
@Composable
internal fun HubPaneFrame(
    title: String,
    count: Int?,
    compact: Boolean,
    onRefresh: (() -> Unit)?,
    titleAction: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxHeight()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = if (compact) 4.dp else 8.dp,
                        vertical = if (compact) 2.dp else 6.dp
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    title,
                    color = HubTextPrimary,
                    fontSize = if (compact) 17.sp else 22.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                if (count != null) {
                    Text(
                        " · $count",
                        color = HubTextTertiary,
                        fontSize = if (compact) 12.sp else 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
                Spacer(Modifier.weight(1f))
                if (titleAction != null) {
                    titleAction()
                }
                if (onRefresh != null) {
                    Box(
                        modifier = Modifier
                            .size(if (compact) 32.dp else 38.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onRefresh),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Outlined.Refresh,
                            contentDescription = "刷新",
                            tint = HubTextSecondary,
                            modifier = Modifier.size(if (compact) 18.dp else 21.dp)
                        )
                    }
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth(), content = content)
        }
    }
}

/** 权限卡 */
@Composable
internal fun HubPermissionCard(
    icon: ImageVector,
    title: String,
    caption: String,
    compact: Boolean,
    onRequest: () -> Unit
) {
    val context = LocalContext.current
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 16.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = HubTextTertiary,
                modifier = Modifier.size(if (compact) 40.dp else 56.dp)
            )
            Text(
                title,
                color = HubTextPrimary,
                fontSize = if (compact) 16.sp else 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                caption,
                color = HubTextSecondary,
                fontSize = if (compact) 12.sp else 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 420.dp)
            )
            GlassPanel(
                level = GlassLevel.S1_Control,
                backdrop = rememberLayerAwareBackdrop(),
                shape = GlassTokens.shapeControl,
                onClick = onRequest
            ) {
                Row(
                    modifier = Modifier.padding(
                        horizontal = if (compact) 18.dp else 26.dp,
                        vertical = if (compact) 10.dp else 14.dp
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Outlined.Lock,
                        contentDescription = null,
                        tint = HubPrimaryBlue,
                        modifier = Modifier.size(if (compact) 16.dp else 19.dp)
                    )
                    Text(
                        "去授权",
                        color = HubPrimaryBlue,
                        fontSize = if (compact) 14.sp else 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            Text(
                "授权后仍不可用？去系统设置里打开",
                color = HubTextTertiary,
                fontSize = if (compact) 11.sp else 12.sp,
                modifier = Modifier.clickable {
                    runCatching {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:${context.packageName}")
                            )
                        )
                    }
                }
            )
        }
    }
}

/** 空态 / 载入态的小字提示 */
@Composable
internal fun HubNotice(text: String, color: Color, compact: Boolean) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text,
            color = color,
            fontSize = if (compact) 13.sp else 15.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 420.dp)
        )
    }
}

/** 通用列表行 */
@Composable
internal fun HubListRow(
    title: String,
    subtitle: String,
    trailing: String?,
    avatarText: String,
    avatarTint: Color,
    compact: Boolean,
    leadingIcon: ImageVector? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(
                horizontal = if (compact) 8.dp else 12.dp,
                vertical = if (compact) 9.dp else 13.dp
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(if (compact) 32.dp else 40.dp)
                .clip(CircleShape)
                .background(avatarTint.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            if (leadingIcon != null) {
                Icon(
                    leadingIcon,
                    contentDescription = null,
                    tint = avatarTint,
                    modifier = Modifier.size(if (compact) 17.dp else 21.dp)
                )
            } else {
                Text(
                    avatarText.take(1).ifEmpty { "?" },
                    color = avatarTint,
                    fontSize = if (compact) 14.sp else 17.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = HubTextPrimary,
                fontSize = if (compact) 15.sp else 18.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                subtitle,
                color = HubTextSecondary,
                fontSize = if (compact) 11.sp else 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (trailing != null) {
            Text(
                trailing,
                color = HubTextTertiary,
                fontSize = if (compact) 10.sp else 12.sp,
                maxLines = 1
            )
        }
    }
}

/** 分区标题 */
@Composable
internal fun CarSectionTitle(text: String) {
    Text(text, fontSize = 20.sp, fontWeight = FontWeight.Black, color = HubTextPrimary)
}

/** 设置卡片 */
@Composable
internal fun CarCardBlock(
    modifier: Modifier = Modifier,
    contentPadding: Dp = 14.dp,
    scrollable: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    GlassPanel(
        level = GlassLevel.L1_Card,
        backdrop = rememberLayerAwareBackdrop(),
        shape = GlassTokens.shapeCard,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                .padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content
        )
    }
}

/** 卡片内的分隔线 */
@Composable
internal fun CarDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(HubTextTertiary.copy(alpha = 0.18f))
    )
}

/** 入口行 */
@Composable
internal fun CarEntryRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    trailingText: String? = null,
    chevron: Boolean = false,
    compact: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = if (compact) 6.dp else 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(if (compact) 30.dp else 36.dp)
                .clip(CircleShape)
                .background(HubPrimaryBlue.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = HubPrimaryBlue,
                modifier = Modifier.size(if (compact) 16.dp else 19.dp)
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = HubTextPrimary,
                fontSize = if (compact) 14.sp else 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    color = HubTextTertiary,
                    fontSize = if (compact) 11.sp else 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (trailingText != null) {
            Text(
                trailingText,
                color = HubTextSecondary,
                fontSize = if (compact) 12.sp else 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
        if (chevron) {
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = HubTextTertiary,
                modifier = Modifier.size(if (compact) 18.dp else 20.dp)
            )
        }
    }
}

/** 开关行 */
@Composable
internal fun CarSwitchRow(
    title: String,
    subtitle: String,
    value: Boolean,
    compact: Boolean = false,
    onChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = HubTextPrimary,
                fontSize = if (compact) 14.sp else 15.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                subtitle,
                color = HubTextTertiary,
                fontSize = if (compact) 11.sp else 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        LiquidToggle(
            selected = { value },
            onSelect = onChange,
            backdrop = rememberLayerAwareBackdrop(),
            modifier = Modifier.scale(if (compact) 0.72f else 0.85f)
        )
    }
}

/** 设置页行 */
@Composable
internal fun CarListRow(
    icon: ImageVector,
    /** 图标块的强调色 */
    tint: Color,
    title: String,
    subtitle: String? = null,
    footer: String? = null,
    trailingText: String? = null,
    chevron: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.glassPressFeedback(onClick = onClick) else Modifier)
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
            Text(title, color = HubTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
            if (subtitle != null) {
                Text(subtitle, color = HubTextTertiary, fontSize = 12.sp, lineHeight = 17.sp)
            }
            if (footer != null) {
                Text(footer, color = HubTextTertiary, fontSize = 11.sp, lineHeight = 16.sp)
            }
        }
        if (trailingText != null) {
            Text(trailingText, color = tint, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        if (chevron) {
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = HubTextTertiary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
internal fun CarListRowDivider() {
    Box(
        Modifier
            .padding(horizontal = 8.dp, vertical = 3.dp)
            .fillMaxWidth()
            .height(1.dp)
            .background(GlassTokens.strokeHairline)
    )
}

@Composable
internal fun CarOptionToggleRow(
    title: String,
    subtitle: String,
    value: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        LiquidToggle(
            selected = { value },
            onSelect = onChange,
            backdrop = rememberLayerAwareBackdrop(),
            modifier = Modifier.scale(0.85f)
        )
        Column(Modifier.weight(1f)) {
            Text(title, color = HubTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(
                subtitle,
                color = HubTextTertiary,
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
internal fun CarChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    compact: Boolean = false,
    modifier: Modifier = Modifier,
    accent: Color = HubPrimaryBlue
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
            fontSize = if (compact) 11.sp else 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1
        )
    }
}

val CarDrawerHandleWidth = 96.dp
val CarDrawerHandleHeight = 30.dp

/** 累计纵向位移超过它就算一次开合 */
const val CarDrawerSwipeThresholdPx = 48f

@Composable
internal fun CarDrawerHandle(
    open: Boolean,
    onToggle: (Boolean) -> Unit,
    swipe: MutableFloatState,
    axis: DrawerAxis = DrawerAxis.Horizontal,
    modifier: Modifier = Modifier
) {
    // 拖动手势跟着朝向走：横向把手用上下拖，竖向把手用左右拖。
    val gesture = if (axis == DrawerAxis.Horizontal) {
        Modifier.pointerInput(Unit) {
            detectVerticalDragGestures(
                onVerticalDrag = { _, dy -> swipe.floatValue += dy },
                onDragEnd = {
                    val delta = swipe.floatValue
                    swipe.floatValue = 0f
                    if (abs(delta) > CarDrawerSwipeThresholdPx) onToggle(delta < 0f)
                },
                onDragCancel = { swipe.floatValue = 0f }
            )
        }
    } else {
        Modifier.pointerInput(Unit) {
            detectHorizontalDragGestures(
                onHorizontalDrag = { _, dx -> swipe.floatValue += dx },
                onDragEnd = {
                    val delta = swipe.floatValue
                    swipe.floatValue = 0f
                    // 向左拖 = 展开（与"二级页向左划出"同向）
                    if (abs(delta) > CarDrawerSwipeThresholdPx) onToggle(delta < 0f)
                },
                onDragCancel = { swipe.floatValue = 0f }
            )
        }
    }
    val icon = when {
        axis == DrawerAxis.Horizontal && open -> Icons.Outlined.KeyboardArrowDown
        axis == DrawerAxis.Horizontal -> Icons.Outlined.KeyboardArrowUp
        open -> Icons.Outlined.KeyboardArrowRight
        else -> Icons.Outlined.KeyboardArrowLeft
    }

    Box(
        modifier = modifier
            .size(
                width = if (axis == DrawerAxis.Horizontal) CarDrawerHandleWidth else CarDrawerHandleHeight,
                height = if (axis == DrawerAxis.Horizontal) CarDrawerHandleHeight else CarDrawerHandleWidth
            )
            .clip(RoundedCornerShape(50))
            .background(
                if (open) HubPrimaryBlue.copy(alpha = 0.22f)
                else HubTextTertiary.copy(alpha = 0.18f)
            )
            .then(gesture)
            .clickable { onToggle(!open) },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = if (open) "收起" else "展开",
            tint = HubTextSecondary,
            modifier = Modifier.size(18.dp)
        )
    }
}

/** 抽屉把手的朝向 */
enum class DrawerAxis { Horizontal, Vertical }

/** 滑块行 */
@Composable
internal fun CarSliderRow(
    label: String,
    valueText: String,
    value: () -> Float,
    valueRange: ClosedFloatingPointRange<Float>,
    compact: Boolean = false,
    modifier: Modifier = Modifier,
    tint: Color? = null,
    /** 是否启用惯性形变 */
    deformWithVelocity: Boolean = true,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                color = tint ?: HubTextPrimary,
                fontSize = if (compact) 13.sp else 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Text(
                valueText,
                color = tint?.copy(alpha = 0.85f) ?: HubTextSecondary,
                fontSize = if (compact) 12.sp else 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        LiquidSlider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            visibilityThreshold = 0.001f,
            backdrop = rememberLayerAwareBackdrop(),
            deformWithVelocity = deformWithVelocity
        )
    }
}

@Composable
internal fun CarActionButton(
    text: String,
    icon: ImageVector?,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    LiquidGlassDialogButton(
        onClick = onClick,
        backdrop = rememberLayerAwareBackdrop(),
        modifier = modifier.fillMaxWidth()
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = color)
            Spacer(Modifier.width(9.dp))
        }
        Text(text, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
internal fun rememberHubPermissions(vararg permissions: String): Pair<Boolean, () -> Unit> {
    val context = LocalContext.current
    fun grantedNow(): Boolean = permissions.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
    var granted by remember { mutableStateOf(grantedNow()) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        granted = grantedNow()
    }
    return granted to { launcher.launch(arrayOf(*permissions)) }
}

/** 拨号 */
internal fun placeCarCall(context: Context, number: String) {
    val direct = ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) ==
        PackageManager.PERMISSION_GRANTED
    val action = if (direct) Intent.ACTION_CALL else Intent.ACTION_DIAL
    runCatching {
        context.startActivity(Intent(action, Uri.parse("tel:${Uri.encode(number)}")))
    }.onFailure {
        Toast.makeText(context, "无法拨号：${it.message ?: "未知原因"}", Toast.LENGTH_SHORT).show()
    }
}
