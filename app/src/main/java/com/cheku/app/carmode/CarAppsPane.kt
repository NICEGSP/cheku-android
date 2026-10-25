package com.cheku.app.carmode

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.cheku.app.glass.glassThemeColor

@Composable
internal fun CarAppsPane(
    compact: Boolean,
    catalog: CarAppCatalog?,
    desktopPackages: List<String>,
    statusText: String?,
    onOpenPane: (CarPane) -> Unit,
    onLaunchApp: (CarAppTile.App) -> Unit,
    /** 保存新的桌面清单 */
    onSaveDesktop: (List<String>) -> Unit,
    modifier: Modifier = Modifier
) {
    val installed = catalog?.let { carAppChoices(it) }.orEmpty()
    val byPackage = installed.associateBy { it.packageName }
    val desktopApps = desktopPackages.mapNotNull { byPackage[it] }
    val panes = catalog?.tiles?.filterIsInstance<CarAppTile.Pane>().orEmpty()

    var pickerOpen by remember { mutableStateOf(false) }
    var sorting by remember { mutableStateOf(false) }

    val appBounds = remember { mutableStateMapOf<String, Rect>() }
    val appCoords = remember { mutableMapOf<String, LayoutCoordinates>() }
    var draggingPackage by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var dropTargetPackage by remember { mutableStateOf<String?>(null) }

    /** 把拖到的目标位置落到清单上 */
    fun commitReorder(from: String, to: String) {
        if (from == to) return
        val order = desktopApps.map { it.packageName }.toMutableList()
        val fromIndex = order.indexOf(from)
        val toIndex = order.indexOf(to)
        if (fromIndex < 0 || toIndex < 0) return
        order.add(toIndex, order.removeAt(fromIndex))
        onSaveDesktop(order)
    }

    HubPaneFrame(
        title = "软件",
        count = desktopApps.size,
        compact = compact,
        onRefresh = null,
        titleAction = {
            SortModeButton(
                sorting = sorting,
                compact = compact,
                onToggle = { sorting = !sorting }
            )
        },
        modifier = modifier
    ) {
        val loaded = catalog
        if (loaded == null) {
            HubNotice("正在读取本机应用…", HubTextTertiary, compact)
            return@HubPaneFrame
        }

        val entries: List<AppsGridEntry> = buildList {
            panes.forEach { add(AppsGridEntry.PaneEntry(it)) }
            desktopApps.forEach { add(AppsGridEntry.AppEntry(it)) }
            add(AppsGridEntry.AddEntry)
        }

        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                when {
                    statusText != null -> HubHintLine(statusText, compact)
                    desktopApps.isEmpty() -> HubHintLine("点「＋」添加应用", compact)
                }

                LazyVerticalGrid(
                    // 自适应列宽而不是固定列数：分屏态主区约 310dp、全屏横屏 700dp+，
                    // 固定列数在窄窗口里会把标签挤成两行、在宽窗口里留出大片空白。
                    columns = GridCells.Adaptive(minSize = if (compact) 84.dp else 104.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        horizontal = if (compact) 2.dp else 6.dp,
                        vertical = if (compact) 4.dp else 8.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 8.dp),
                    verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 8.dp)
                ) {
                    items(entries, key = { it.key }) { entry ->
                        when (entry) {
                            is AppsGridEntry.PaneEntry -> PaneTile(entry.tile, compact) {
                                onOpenPane(entry.tile.pane)
                            }

                            is AppsGridEntry.AddEntry -> AddTile(compact) { pickerOpen = true }

                            is AppsGridEntry.AppEntry -> {
                                val tile = entry.tile
                                val pkg = tile.packageName
                                val isDragging = draggingPackage == pkg
                                val isDropTarget = dropTargetPackage == pkg && !isDragging
                                AppTile(
                                    tile = tile,
                                    icon = loaded.icons[pkg],
                                    compact = compact,
                                    highlighted = isDropTarget,
                                    sorting = sorting,
                                    onClick = {
                                        if (!sorting) onLaunchApp(tile)
                                    },
                                    onLongClick = { sorting = true },
                                    modifier = Modifier
                                        .zIndex(if (isDragging) 1f else 0f)
                                        .graphicsLayer {
                                            if (isDragging) {
                                                translationX = dragOffset.x
                                                translationY = dragOffset.y
                                                scaleX = 1.08f
                                                scaleY = 1.08f
                                            }
                                        }
                                        .onGloballyPositioned { coords ->
                                            appCoords[pkg] = coords
                                            appBounds[pkg] = coords.boundsInWindow()
                                        }
                                        .then(
                                            if (!sorting) {
                                                Modifier
                                            } else {
                                                Modifier.pointerInput(pkg, desktopApps) {
                                                    detectDragGestures(
                                                        onDragStart = {
                                                            draggingPackage = pkg
                                                            dragOffset = Offset.Zero
                                                            dropTargetPackage = null
                                                        },
                                                        onDrag = { change, delta ->
                                                            change.consume()
                                                            dragOffset += delta
                                                            val window = appCoords[pkg]
                                                                ?.localToWindow(change.position)
                                                            dropTargetPackage = window?.let { pos ->
                                                                appBounds.entries
                                                                    .firstOrNull {
                                                                        it.key != pkg && it.value.contains(pos)
                                                                    }
                                                                    ?.key
                                                            }
                                                        },
                                                        onDragEnd = {
                                                            val target = dropTargetPackage
                                                            if (target != null) commitReorder(pkg, target)
                                                            draggingPackage = null
                                                            dragOffset = Offset.Zero
                                                            dropTargetPackage = null
                                                        },
                                                        onDragCancel = {
                                                            draggingPackage = null
                                                            dragOffset = Offset.Zero
                                                            dropTargetPackage = null
                                                        }
                                                    )
                                                }
                                            }
                                        )
                                )
                            }
                        }
                    }
                }
            }

            if (pickerOpen) {
                AddAppsOverlay(
                    compact = compact,
                    apps = installed,
                    icons = loaded.icons,
                    selected = desktopPackages.toSet(),
                    onToggle = { pkg ->
                        onSaveDesktop(
                            if (pkg in desktopPackages) desktopPackages - pkg
                            else desktopPackages + pkg
                        )
                    },
                    onClose = { pickerOpen = false }
                )
            }
        }
    }
}

/** 网格里的一格 */
private sealed interface AppsGridEntry {
    val key: String

    class PaneEntry(val tile: CarAppTile.Pane) : AppsGridEntry {
        override val key: String = "entry:${tile.id}"
    }

    class AppEntry(val tile: CarAppTile.App) : AppsGridEntry {
        override val key: String = "entry:${tile.id}"
    }

    object AddEntry : AppsGridEntry {
        override val key: String = "entry:add"
    }
}

/** 面板顶部的一行小字 */
@Composable
private fun HubHintLine(text: String, compact: Boolean) {
    Text(
        text,
        color = HubTextTertiary,
        fontSize = if (compact) 11.sp else 12.sp,
        modifier = Modifier.padding(horizontal = if (compact) 4.dp else 8.dp, vertical = 2.dp)
    )
}

@Composable
private fun SortModeButton(sorting: Boolean, compact: Boolean, onToggle: () -> Unit) {
    Box(
        modifier = Modifier
            .size(if (compact) 32.dp else 38.dp)
            .clip(CircleShape)
            .background(
                if (sorting) HubPrimaryBlue.copy(alpha = 0.16f) else Color.Transparent
            )
            .clickable(onClick = onToggle),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Outlined.SwapHoriz,
            contentDescription = if (sorting) "完成排序" else "排序",
            tint = if (sorting) HubPrimaryBlue else HubTextSecondary,
            modifier = Modifier.size(if (compact) 18.dp else 21.dp)
        )
    }
}

/** 自绘面板的瓦片 */
@Composable
private fun PaneTile(tile: CarAppTile.Pane, compact: Boolean, onClick: () -> Unit) {
    TileShell(label = tile.label, compact = compact, onClick = onClick) {
        Box(
            modifier = Modifier
                .size(if (compact) 38.dp else 48.dp)
                .clip(CircleShape)
                .background(HubPrimaryBlue.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                tile.pane.icon,
                contentDescription = tile.label,
                tint = HubPrimaryBlue,
                modifier = Modifier.size(if (compact) 20.dp else 25.dp)
            )
        }
    }
}

@Composable
private fun AddTile(compact: Boolean, onClick: () -> Unit) {
    TileShell(label = "添加", compact = compact, onClick = onClick) {
        Box(
            modifier = Modifier
                .size(if (compact) 38.dp else 48.dp)
                .clip(RoundedCornerShape((if (compact) 38.dp else 48.dp) / 4))
                .background(HubPrimaryBlue.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Outlined.Add,
                contentDescription = "添加应用",
                tint = HubPrimaryBlue,
                modifier = Modifier.size(if (compact) 22.dp else 27.dp)
            )
        }
    }
}

/** 已安装应用的瓦片 */
@Composable
private fun AppTile(
    tile: CarAppTile.App,
    icon: ImageBitmap?,
    compact: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    sorting: Boolean = false
) {
    TileShell(
        label = tile.label,
        compact = compact,
        onClick = onClick,
        onLongClick = onLongClick,
        caption = tile.caption,
        highlighted = highlighted,
        modifier = modifier
    ) {
        val size = if (compact) 38.dp else 48.dp
        val scale = if (sorting) 1.06f else 1f
        Box(Modifier.size(size).graphicsLayer { scaleX = scale; scaleY = scale }) {
            if (icon != null) {
                Image(
                    bitmap = icon,
                    contentDescription = tile.label,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(size).clip(RoundedCornerShape(size / 4))
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(size)
                        .clip(RoundedCornerShape(size / 4))
                        .background(HubTextTertiary.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.Apps,
                        contentDescription = null,
                        tint = HubTextTertiary,
                        modifier = Modifier.size(if (compact) 18.dp else 22.dp)
                    )
                }
            }
        }
    }
}

/** 瓦片外壳 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TileShell(
    label: String,
    compact: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    caption: String? = null,
    highlighted: Boolean = false,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .then(
                if (highlighted) {
                    Modifier.background(HubPrimaryBlue.copy(alpha = 0.14f))
                } else {
                    Modifier
                }
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(vertical = if (compact) 6.dp else 9.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 6.dp)
    ) {
        icon()
        Text(
            label,
            color = HubTextPrimary,
            fontSize = if (compact) 11.sp else 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        if (caption != null) {
            Text(
                caption,
                color = HubTextTertiary,
                fontSize = if (compact) 9.sp else 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun AddAppsOverlay(
    compact: Boolean,
    apps: List<CarAppTile.App>,
    icons: Map<String, ImageBitmap>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    onClose: () -> Unit
) {
    val panelColor = glassThemeColor(Color(0xFFF6F7FA), Color(0xFF171A1F))
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures { } }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (compact) 6.dp else 12.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(panelColor)
                .padding(if (compact) 8.dp else 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "添加应用",
                    color = HubTextPrimary,
                    fontSize = if (compact) 15.sp else 17.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "完成",
                    color = HubPrimaryBlue,
                    fontSize = if (compact) 12.sp else 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(onClick = onClose)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
            Text(
                "勾选要放到桌面上的应用，再次点击即移除",
                color = HubTextTertiary,
                fontSize = if (compact) 10.sp else 11.sp
            )
            Spacer(Modifier.height(if (compact) 6.dp else 10.dp))
            if (apps.isEmpty()) {
                Text("没有可添加的应用", color = HubTextTertiary, fontSize = 12.sp)
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = if (compact) 84.dp else 104.dp),
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 8.dp),
                    verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 8.dp)
                ) {
                    items(apps, key = { it.id }) { app ->
                        AddableAppTile(
                            tile = app,
                            icon = icons[app.packageName],
                            compact = compact,
                            checked = app.packageName in selected
                        ) { onToggle(app.packageName) }
                    }
                }
            }
        }
    }
}

/** 选择器里的一格 */
@Composable
private fun AddableAppTile(
    tile: CarAppTile.App,
    icon: ImageBitmap?,
    compact: Boolean,
    checked: Boolean,
    onClick: () -> Unit
) {
    val size = if (compact) 38.dp else 48.dp
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .then(
                if (checked) Modifier.background(HubPrimaryBlue.copy(alpha = 0.14f)) else Modifier
            )
            .clickable(onClick = onClick)
            .padding(vertical = if (compact) 6.dp else 9.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 6.dp)
    ) {
        Box(Modifier.size(size)) {
            if (icon != null) {
                Image(
                    bitmap = icon,
                    contentDescription = tile.label,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(size).clip(RoundedCornerShape(size / 4))
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(size)
                        .clip(RoundedCornerShape(size / 4))
                        .background(HubTextTertiary.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.Apps,
                        contentDescription = null,
                        tint = HubTextTertiary,
                        modifier = Modifier.size(if (compact) 18.dp else 22.dp)
                    )
                }
            }
            if (checked) {
                Icon(
                    Icons.Outlined.CheckCircle,
                    contentDescription = "已添加",
                    tint = HubPrimaryBlue,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(if (compact) 16.dp else 19.dp)
                        .background(Color.White, CircleShape)
                )
            }
        }
        Text(
            tile.label,
            color = if (checked) HubPrimaryBlue else HubTextPrimary,
            fontSize = if (compact) 11.sp else 12.sp,
            fontWeight = if (checked) FontWeight.Bold else FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        if (tile.caption != null) {
            Text(
                tile.caption,
                color = HubTextTertiary,
                fontSize = if (compact) 9.sp else 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
