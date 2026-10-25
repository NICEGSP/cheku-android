package com.cheku.app.carmode

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CallLog
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.CallMade
import androidx.compose.material.icons.outlined.CallMissed
import androidx.compose.material.icons.outlined.CallReceived
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material.icons.outlined.VolumeDown
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import kotlin.math.abs
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
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
import com.cheku.app.glass.rememberLayerAwareBackdrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Composable
internal fun CarContactsPane(compact: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val (granted, requestPermissions) = rememberHubPermissions(
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.CALL_PHONE
    )
    var contacts by remember { mutableStateOf<List<CarContact>?>(null) }
    var reload by remember { mutableIntStateOf(0) }

    LaunchedEffect(granted, reload) {
        if (granted) {
            contacts = withContext(Dispatchers.IO) {
                queryCarContacts(context.contentResolver)
            }
        }
    }

    HubPaneFrame(
        title = "联系人",
        count = contacts?.size,
        compact = compact,
        onRefresh = { reload++ },
        modifier = modifier
    ) {
        val list = contacts
        when {
            !granted -> HubPermissionCard(
                icon = Icons.Outlined.Contacts,
                title = "需要通讯录与电话权限",
                caption = "读通讯录用来显示名字与号码；电话权限用于点击后直接拨出。数据只在本机使用。",
                compact = compact,
                onRequest = requestPermissions
            )

            list == null -> HubNotice("正在读取通讯录…", HubTextTertiary, compact)

            list.isEmpty() -> HubNotice("通讯录里没有可用的号码", HubTextTertiary, compact)

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 10.dp)
            ) {
                items(list) { contact ->
                    HubListRow(
                        title = contact.name,
                        subtitle = contact.number,
                        trailing = null,
                        avatarText = contact.name,
                        avatarTint = HubPrimaryBlue,
                        compact = compact
                    ) {
                        placeCarCall(context, contact.number)
                    }
                }
            }
        }
    }
}

/** 通话类型的展示映射 */
private fun callTypeIcon(type: Int): ImageVector = when (type) {
    CallLog.Calls.INCOMING_TYPE -> Icons.Outlined.CallReceived
    CallLog.Calls.OUTGOING_TYPE -> Icons.Outlined.CallMade
    CallLog.Calls.MISSED_TYPE -> Icons.Outlined.CallMissed
    else -> Icons.Outlined.Call
}

private fun callTypeLabel(type: Int): String = when (type) {
    CallLog.Calls.INCOMING_TYPE -> "呼入"
    CallLog.Calls.OUTGOING_TYPE -> "呼出"
    CallLog.Calls.MISSED_TYPE -> "未接"
    CallLog.Calls.REJECTED_TYPE -> "已拒接"
    CallLog.Calls.BLOCKED_TYPE -> "已拦截"
    else -> "通话"
}

@Composable
internal fun CarCallLogPane(compact: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val (granted, requestPermissions) = rememberHubPermissions(
        Manifest.permission.READ_CALL_LOG,
        Manifest.permission.CALL_PHONE
    )
    var calls by remember { mutableStateOf<List<CarCall>?>(null) }
    var reload by remember { mutableIntStateOf(0) }

    LaunchedEffect(granted, reload) {
        if (granted) {
            calls = withContext(Dispatchers.IO) {
                queryCarCallLog(context.contentResolver)
            }
        }
    }

    HubPaneFrame(
        title = "电话",
        count = calls?.size,
        compact = compact,
        onRefresh = { reload++ },
        modifier = modifier
    ) {
        val list = calls
        when {
            !granted -> HubPermissionCard(
                icon = Icons.Outlined.Phone,
                title = "需要通话记录与电话权限",
                caption = "读通话记录用来显示最近通话；电话权限用于点击后直接回拨。数据只在本机使用。",
                compact = compact,
                onRequest = requestPermissions
            )

            list == null -> HubNotice("正在读取通话记录…", HubTextTertiary, compact)

            list.isEmpty() -> HubNotice("没有通话记录", HubTextTertiary, compact)

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 10.dp)
            ) {
                items(list) { call ->
                    val missed = call.type == CallLog.Calls.MISSED_TYPE
                    HubListRow(
                        title = call.name ?: call.number,
                        subtitle = if (call.name != null) {
                            "${callTypeLabel(call.type)} · ${call.number}"
                        } else {
                            callTypeLabel(call.type)
                        },
                        trailing = formatCarTimestamp(call.date, System.currentTimeMillis()),
                        avatarText = (call.name ?: call.number),
                        avatarTint = if (missed) HubError else HubSuccess,
                        compact = compact,
                        leadingIcon = callTypeIcon(call.type)
                    ) {
                        placeCarCall(context, call.number)
                    }
                }
            }
        }
    }
}

@Composable
internal fun CarSmsPane(compact: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val (granted, requestPermissions) = rememberHubPermissions(
        Manifest.permission.READ_SMS,
        Manifest.permission.SEND_SMS
    )
    var threads by remember { mutableStateOf<List<CarSmsThread>?>(null) }
    var openedThread by remember { mutableStateOf<CarSmsThread?>(null) }
    var reload by remember { mutableIntStateOf(0) }

    LaunchedEffect(granted, reload) {
        if (granted) {
            threads = withContext(Dispatchers.IO) {
                val contacts = queryCarContacts(context.contentResolver)
                queryCarSmsThreads(context.contentResolver, buildCarContactLookup(contacts))
            }
        }
    }

    val opened = openedThread
    if (opened != null) {
        CarSmsConversation(
            thread = opened,
            compact = compact,
            onBack = {
                openedThread = null
                reload++
            },
            modifier = modifier
        )
    } else {
        HubPaneFrame(
            title = "短信",
            count = threads?.size,
            compact = compact,
            onRefresh = { reload++ },
            modifier = modifier
        ) {
            val list = threads
            when {
                !granted -> HubPermissionCard(
                    icon = Icons.Outlined.Sms,
                    title = "需要短信权限",
                    caption = "读短信用来显示会话与内容；发送权限用于在面板里直接回复。数据只在本机使用。",
                    compact = compact,
                    onRequest = requestPermissions
                )

                list == null -> HubNotice("正在读取短信…", HubTextTertiary, compact)

                list.isEmpty() -> HubNotice("没有短信会话", HubTextTertiary, compact)

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 10.dp)
                ) {
                    items(list) { thread ->
                        HubListRow(
                            title = thread.name ?: thread.address,
                            subtitle = thread.snippet.ifEmpty { "（无内容）" },
                            trailing = formatCarTimestamp(thread.date, System.currentTimeMillis()),
                            avatarText = thread.name ?: thread.address,
                            avatarTint = HubPrimaryBlue,
                            compact = compact
                        ) {
                            openedThread = thread
                        }
                    }
                }
            }
        }
    }
}

/** 会话详情 */
@Composable
private fun CarSmsConversation(
    thread: CarSmsThread,
    compact: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var messages by remember(thread.threadId) { mutableStateOf<List<CarSmsMessage>?>(null) }
    var locallySent by remember(thread.threadId) { mutableStateOf<List<CarSmsMessage>>(emptyList()) }
    var draft by remember(thread.threadId) { mutableStateOf("") }
    var sendError by remember(thread.threadId) { mutableStateOf<String?>(null) }

    LaunchedEffect(thread.threadId) {
        messages = withContext(Dispatchers.IO) {
            queryCarSmsMessages(context.contentResolver, thread.threadId)
        }
    }

    Column(
        modifier = modifier.fillMaxSize().imePadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 顶栏：返回 + 对方名字/号码
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = if (compact) 2.dp else 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(if (compact) 34.dp else 40.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "返回会话列表",
                    tint = HubTextSecondary,
                    modifier = Modifier.size(if (compact) 20.dp else 24.dp)
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    thread.name ?: thread.address,
                    color = HubTextPrimary,
                    fontSize = if (compact) 16.sp else 20.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (thread.name != null) {
                    Text(
                        thread.address,
                        color = HubTextTertiary,
                        fontSize = if (compact) 11.sp else 13.sp,
                        maxLines = 1
                    )
                }
            }
        }

        // 消息区：最新在底（reverseLayout 直接消费"时间倒序"的数据）
        Box(Modifier.weight(1f).fillMaxWidth().widthIn(max = 720.dp)) {
            val merged = (locallySent + (messages ?: emptyList())).sortedByDescending { it.date }
            if (messages == null && locallySent.isEmpty()) {
                HubNotice("正在读取…", HubTextTertiary, compact)
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    reverseLayout = true,
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 10.dp)
                ) {
                    items(merged) { message -> CarSmsBubble(message, compact) }
                }
            }
        }

        // 输入行
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 720.dp)
                .padding(top = if (compact) 6.dp else 10.dp, bottom = if (compact) 4.dp else 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 10.dp)
        ) {
            BasicTextField(
                value = draft,
                onValueChange = {
                    draft = it
                    if (sendError != null) sendError = null
                },
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(18.dp))
                    .background(HubTextPrimary.copy(alpha = 0.08f))
                    .padding(horizontal = 14.dp, vertical = if (compact) 8.dp else 10.dp),
                textStyle = TextStyle(
                    color = HubTextPrimary,
                    fontSize = if (compact) 14.sp else 16.sp
                ),
                cursorBrush = SolidColor(HubPrimaryBlue),
                singleLine = true,
                decorationBox = { innerTextField ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (draft.isEmpty()) {
                            Text(
                                "输入短信…",
                                color = HubTextHint,
                                fontSize = if (compact) 14.sp else 16.sp
                            )
                        }
                        innerTextField()
                    }
                }
            )
            Box(
                modifier = Modifier
                    .size(if (compact) 40.dp else 46.dp)
                    .clip(CircleShape)
                    .background(HubPrimaryBlue.copy(alpha = if (draft.isBlank()) 0.25f else 1f))
                    .clickable(enabled = draft.isNotBlank()) {
                        val text = draft.trim()
                        if (text.isEmpty()) return@clickable
                        val ok = sendCarSms(context, thread.address, text)
                        if (ok) {
                            locallySent = locallySent + CarSmsMessage(
                                id = -System.nanoTime(),
                                body = text,
                                date = System.currentTimeMillis(),
                                outgoing = true
                            )
                            draft = ""
                            sendError = null
                        } else {
                            sendError = "发送失败：没有短信权限，或号码无效"
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.AutoMirrored.Outlined.Send,
                    contentDescription = "发送",
                    tint = Color.White,
                    modifier = Modifier.size(if (compact) 20.dp else 23.dp)
                )
            }
        }

        // 状态小字：发送错误 或 "本机未入库" 说明（只在发过之后出现，避免常驻噪声）
        val notice = sendError
            ?: if (locallySent.isNotEmpty()) "本应用不是默认短信应用，发出的短信不会写进系统短信库（对方能正常收到）" else null
        if (notice != null) {
            Text(
                notice,
                color = if (sendError != null) HubError else HubTextTertiary,
                fontSize = if (compact) 10.sp else 11.sp,
                maxLines = 2,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 720.dp)
                    .padding(bottom = 4.dp)
            )
        }
    }
}

@Composable
private fun CarSmsBubble(message: CarSmsMessage, compact: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.outgoing) Arrangement.End else Arrangement.Start
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = if (compact) 300.dp else 460.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(
                    if (message.outgoing) HubPrimaryBlue.copy(alpha = 0.18f)
                    else HubTextPrimary.copy(alpha = 0.07f)
                )
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(
                message.body,
                color = HubTextPrimary,
                fontSize = if (compact) 13.sp else 15.sp
            )
            Text(
                formatCarTimestamp(message.date, System.currentTimeMillis()),
                color = HubTextTertiary,
                fontSize = if (compact) 10.sp else 11.sp,
                modifier = Modifier.padding(top = 3.dp)
            )
        }
    }
}

private class MusicTone(
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
    val accent: Color
)

/** 取本面板当前该用的那套配色 */
@Composable
private fun musicTone(overArtwork: Boolean): MusicTone = if (overArtwork) {
    MusicTone(
        primary = Color.White,
        secondary = Color.White.copy(alpha = 0.80f),
        tertiary = Color.White.copy(alpha = 0.62f),
        accent = Color.White
    )
} else {
    MusicTone(
        primary = HubTextPrimary,
        secondary = HubTextSecondary,
        tertiary = HubTextTertiary,
        accent = HubPrimaryBlue
    )
}

@Composable
internal fun CarMusicPane(
    landscape: Boolean,
    compact: Boolean,
    /** 专辑封面模糊半径 */
    artworkBlur: Dp,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var artworkBlurDp by remember { mutableFloatStateOf(artworkBlur.value) }
    val controller = remember { CarMusicController(context.applicationContext) }
    var state by remember { mutableStateOf<CarMusicState?>(null) }
    var hasAccess by remember { mutableStateOf(controller.hasNotificationAccess()) }
    var volume by remember { mutableIntStateOf(controller.musicVolume()) }
    var volumeMax by remember { mutableIntStateOf(controller.musicVolumeMax().coerceAtLeast(1)) }
    val settings = remember { CarModeSettings(context.applicationContext) }
    var visualizerOn by remember { mutableStateOf(settings.musicVisualizer()) }

    val (micGranted, requestMic) = rememberHubPermissions(Manifest.permission.RECORD_AUDIO)
    LaunchedEffect(visualizerOn, micGranted) {
        if (visualizerOn && !micGranted) requestMic()
    }

    if (android.os.Build.VERSION.SDK_INT >= 24) {
        LaunchedEffect(visualizerOn) {
            if (visualizerOn) ShizukuCapture.maybeAutoConnect(context)
        }
    }
    var visFade by remember { mutableFloatStateOf(settings.visualizerFade()) }
    var visSmoothing by remember { mutableFloatStateOf(settings.visualizerSmoothing()) }
    var visGain by remember { mutableFloatStateOf(settings.visualizerGain()) }
    var visBlur by remember { mutableFloatStateOf(settings.visualizerBlur()) }
    var visAlpha by remember { mutableFloatStateOf(settings.visualizerAlpha()) }
    var visStyle by remember { mutableStateOf(VisualizerStyle.from(settings.visualizerStyle())) }

    LaunchedEffect(Unit) {
        while (true) {
            state = withContext(Dispatchers.IO) { controller.snapshot() }
            hasAccess = controller.hasNotificationAccess()
            volume = controller.musicVolume()
            volumeMax = controller.musicVolumeMax().coerceAtLeast(1)
            delay(1_500L)
        }
    }

    val current = state
    val artwork = current?.artwork
    val tone = musicTone(overArtwork = artwork != null)

    var paletteMode by remember {
        mutableStateOf(VisPaletteMode.fromKey(settings.visualizerPaletteMode()))
    }
    var customHues by remember { mutableStateOf(settings.visualizerCustomHues()) }
    val visPalette: VisPalette? = remember(paletteMode, customHues, artwork) {
        when (paletteMode) {
            VisPaletteMode.Theme -> null
            // 封面取色：拿不到封面时退到默认三色（不是黑）
            VisPaletteMode.Artwork -> artwork?.let { extractVisPalette(it) } ?: VisPalette.Fallback
            VisPaletteMode.Custom ->
                customPalette(customHues)
        }
    }
    val headlineSize = if (compact) 20.sp else 26.sp

    BoxWithConstraints(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val columnGap = when {
            maxHeight < 340.dp -> 6.dp
            compact || landscape -> 12.dp
            else -> 14.dp
        }
        if (artwork != null) {
            // `remember(artwork)`：`asImageBitmap()` 每次都会包一个新对象，
            // 而这里每 1.5s 就会被轮询重组一次，不该白扔。
            val image = remember(artwork) { artwork.asImageBitmap() }
            Image(
                bitmap = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(artworkBlurDp.dp, BlurredEdgeTreatment.Rectangle)
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.60f),
                            0.5f to Color.Black.copy(alpha = 0.40f),
                            1f to Color.Black.copy(alpha = 0.70f)
                        )
                    )
            )
        }
        if (visualizerOn) {
            MusicVisualizer(
                playing = current?.playing == true,
                tint = tone.primary,
                accent = tone.accent,
                fade = visFade,
                smoothing = visSmoothing,
                gain = visGain,
                alpha = visAlpha,
                style = visStyle,
                palette = visPalette,
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxSize()
                    .then(
                        if (visBlur > 0.01f) {
                            Modifier.blur(visBlur.dp, BlurredEdgeTreatment.Unbounded)
                        } else {
                            Modifier
                        }
                    )
            )
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.displayCutout),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(columnGap, Alignment.CenterVertically)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Box {
                    Text(
                        // 透明占位：两行的"空内容"，唯一作用是撑出与两行歌词完全相同的高度。
                        "\u00A0\n\u00A0",
                        color = Color.Transparent,
                        fontSize = headlineSize,
                        fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        modifier = Modifier.widthIn(max = if (compact) 280.dp else 520.dp)
                    )
                    Text(
                        when {
                            current?.title != null -> current.title
                            current?.playing == true -> "正在播放"
                            current == null -> "正在读取…"
                            else -> "没有正在播放的音乐"
                        },
                        color = tone.primary,
                        fontSize = headlineSize,
                        fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = if (compact) 280.dp else 520.dp)
                    )
                }
                // 副行：歌词模式且拿到真歌名时是"歌名 · 歌手"，否则就是歌手（与加歌词前一致）。
                val subtitle = listOfNotNull(current?.songTitle, current?.artist)
                    .joinToString(" · ")
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.widthIn(max = if (compact) 300.dp else 560.dp)
                ) {
                    Text(
                        subtitle.ifEmpty { " " },
                        color = tone.secondary,
                        fontSize = if (compact) 13.sp else 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Text(
                        current?.sourceLabel?.let { "来自 $it" } ?: " ",
                        color = tone.tertiary,
                        fontSize = if (compact) 11.sp else 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(min = if (compact) 56.dp else 68.dp)
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 18.dp)
            ) {
                MusicControlButton(
                    icon = Icons.Outlined.SkipPrevious,
                    size = if (compact) 42.dp else 54.dp,
                    compact = compact,
                    tint = tone.primary,
                    onClick = controller::skipPrevious
                )
                MusicControlButton(
                    icon = if (current?.playing == true) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                    size = if (compact) 52.dp else 68.dp,
                    compact = compact,
                    prominent = true,
                    tint = tone.accent,
                    onClick = controller::togglePlay
                )
                MusicControlButton(
                    icon = Icons.Outlined.SkipNext,
                    size = if (compact) 42.dp else 54.dp,
                    compact = compact,
                    tint = tone.primary,
                    onClick = controller::skipNext
                )
            }
            if (current != null && current.canSeek && current.durationMs > 0L) {
                var seekFraction by remember { mutableStateOf<Float?>(null) }
                LaunchedEffect(seekFraction) {
                    if (seekFraction != null) {
                        delay(800L)
                        seekFraction = null
                    }
                }
                val liveFraction = (current.positionMs.toFloat() / current.durationMs).coerceIn(0f, 1f)
                CarSliderRow(
                    label = "进度",
                    valueText = "${formatTripDuration(current.positionMs / 1000.0)} / " +
                        formatTripDuration(current.durationMs / 1000.0),
                    value = {
                        seekFraction ?: run {
                            val s = state
                            if (s != null && s.durationMs > 0L) {
                                (s.positionMs.toFloat() / s.durationMs).coerceIn(0f, 1f)
                            } else {
                                liveFraction
                            }
                        }
                    },
                    valueRange = 0f..1f,
                    compact = compact,
                    modifier = Modifier.widthIn(max = if (compact) 240.dp else 360.dp),
                    tint = tone.primary,
                    deformWithVelocity = false
                ) { fraction ->
                    seekFraction = fraction
                    controller.seekToFraction(fraction)
                }
            }

            CarSliderRow(
                label = "音量",
                valueText = "$volume/$volumeMax",
                value = { volume.toFloat() / volumeMax },
                valueRange = 0f..1f,
                compact = compact,
                modifier = Modifier.widthIn(max = if (compact) 240.dp else 360.dp),
                tint = tone.primary,
                deformWithVelocity = false
            ) { newFraction ->
                val target = (newFraction * volumeMax).roundToInt().coerceIn(0, volumeMax)
                volume = target
                controller.setMusicVolume(target)
            }
            if (!hasAccess) {
                Text(
                    "显示歌名需要「通知使用权」 · 点此开启",
                    color = tone.tertiary,
                    fontSize = if (compact) 11.sp else 12.sp,
                    modifier = Modifier.clickable {
                        runCatching { context.startActivity(controller.notificationAccessIntent()) }
                    }
                )
            } else if (current?.title == null) {
                Text(
                    "已开启通知使用权；播放器未提供曲目信息",
                    color = tone.tertiary,
                    fontSize = if (compact) 11.sp else 12.sp
                )
            }
        }

        MusicVisualizerDrawer(
            compact = compact,
            playing = current?.playing == true,
            on = visualizerOn,
            onToggle = { visualizerOn = it; settings.setMusicVisualizer(it) },
            fade = visFade, onFade = { visFade = it; settings.setVisualizerFade(it) },
            smoothing = visSmoothing, onSmoothing = { visSmoothing = it; settings.setVisualizerSmoothing(it) },
            gain = visGain, onGain = { visGain = it; settings.setVisualizerGain(it) },
            blur = visBlur, onBlur = { visBlur = it; settings.setVisualizerBlur(it) },
            alpha = visAlpha, onAlpha = { visAlpha = it; settings.setVisualizerAlpha(it) },
            style = visStyle, onStyle = { visStyle = it; settings.setVisualizerStyle(it.key) },
            palette = visPalette,
            paletteMode = paletteMode,
            onPaletteMode = { m -> paletteMode = m; settings.setVisualizerPaletteMode(m.key) },
            customHues = customHues,
            onCustomHue = { i, h ->
                val next = customHues.toMutableList().also { it[i] = h }
                customHues = next
                settings.setVisualizerCustomHues(next)
            },
            onCustomCount = { n ->
                val base = customHues.toMutableList()
                while (base.size < n) base.add(listOf(210f, 150f, 35f)[base.size % 3])
                val next = base.take(n)
                customHues = next
                settings.setVisualizerCustomHues(next)
            },
            artworkBlur = artworkBlurDp, onArtworkBlur = { artworkBlurDp = it; settings.setArtworkBlur(it) },
            modifier = Modifier.align(Alignment.CenterEnd)
        )
    }
}

private val MusicDrawerHandleWidth = 96.dp
private val MusicDrawerHandleHeight = 30.dp

/** 累计纵向位移超过它就算一次开合 */
private const val MUSIC_DRAWER_SWIPE_PX = 48f

@Composable
private fun StyleSelector(
    selected: VisualizerStyle,
    compact: Boolean,
    onSelect: (VisualizerStyle) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        VisualizerStyle.entries.forEach { st ->
            val active = st == selected
            CarChip(
                selected = active,
                onClick = { onSelect(st) },
                label = st.label,
                compact = compact
            )
        }
    }
}

@Composable
private fun MusicVisualizerDrawer(
    compact: Boolean,
    playing: Boolean,
    on: Boolean,
    onToggle: (Boolean) -> Unit,
    fade: Float, onFade: (Float) -> Unit,
    smoothing: Float, onSmoothing: (Float) -> Unit,
    gain: Float, onGain: (Float) -> Unit,
    blur: Float, onBlur: (Float) -> Unit,
    alpha: Float, onAlpha: (Float) -> Unit,
    style: VisualizerStyle, onStyle: (VisualizerStyle) -> Unit,
    /** 当前生效的三色调色板 */
    palette: VisPalette?,
    paletteMode: VisPaletteMode, onPaletteMode: (VisPaletteMode) -> Unit,
    customHues: List<Float>, onCustomHue: (Int, Float) -> Unit,
    onCustomCount: (Int) -> Unit,
    artworkBlur: Float, onArtworkBlur: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var open by rememberSaveable { mutableStateOf(false) }
    val swipe = remember { mutableFloatStateOf(0f) }

    AnimatedVisibility(
        visible = open,
        enter = slideInHorizontally(initialOffsetX = { -it }) + fadeIn(tween(200)),
        exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(tween(160)),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { _, dx -> swipe.floatValue += dx },
                        onDragEnd = {
                            if (swipe.floatValue < -MUSIC_DRAWER_SWIPE_PX) open = false
                            swipe.floatValue = 0f
                        },
                        onDragCancel = { swipe.floatValue = 0f }
                    )
                }
        ) {
            CarCardBlock(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 14.dp, vertical = 20.dp)
                    .fillMaxWidth(0.92f)
                    .heightIn(max = 560.dp),
                scrollable = true,
                contentPadding = 14.dp
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "音频频谱设置",
                        color = HubTextPrimary,
                        fontSize = if (compact) 15.sp else 17.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(HubTextTertiary.copy(alpha = 0.14f))
                            .combinedClickable(onClick = { open = false })
                            .pointerInput(Unit) {
                                detectHorizontalDragGestures(
                                    onHorizontalDrag = { _, dx ->
                                        if (dx < -6f) swipe.floatValue += dx
                                    },
                                    onDragEnd = {
                                        if (swipe.floatValue < -MUSIC_DRAWER_SWIPE_PX) open = false
                                        swipe.floatValue = 0f
                                    },
                                    onDragCancel = { swipe.floatValue = 0f }
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Outlined.KeyboardArrowLeft,
                            contentDescription = "收起设置（也可向左划）",
                            tint = HubTextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                CarSliderRow(
                    label = "专辑封面模糊",
                    valueText = "${artworkBlur.roundToInt()} dp",
                    value = { artworkBlur },
                    valueRange = 0f..24f,
                    compact = compact
                ) { onArtworkBlur(it) }

                CarListRowDivider()

                CarOptionToggleRow(
                    title = "启用频谱",
                    subtitle = "关闭后完全不绘制",
                    value = on
                ) { onToggle(it) }

                if (on) {
                    CarListRowDivider()

                    Text(
                        "配色",
                        color = HubTextPrimary,
                        fontSize = if (compact) 13.sp else 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        VisPaletteMode.entries.forEach { m ->
                            CarChip(
                                selected = m == paletteMode,
                                onClick = { onPaletteMode(m) },
                                label = m.label,
                                compact = compact
                            )
                        }
                    }
                    // 三色预览条：让"选了哪三色"一眼可见（默认模式下显示各款会用的色系说明）
                    if (palette != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(10.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(
                                    // 直接铺当前调色板本身（1~3 色都能正确显示）。
                                    Brush.horizontalGradient(
                                        listOf(
                                            palette.at(0f), palette.at(0.25f),
                                            palette.at(0.5f), palette.at(0.75f),
                                            palette.at(1f)
                                        )
                                    )
                                )
                        )
                    }
                    if (paletteMode == VisPaletteMode.Custom) {
                        // 数量切换（1=纯色 / 2=渐变 / 3=彩虹，见 [VisPalette.at]）
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(1, 2, 3).forEach { n ->
                                CarChip(
                                    selected = customHues.size == n,
                                    onClick = { onCustomCount(n) },
                                    label = when (n) { 1 -> "纯色"; 2 -> "渐变"; else -> "彩虹" },
                                    compact = compact
                                )
                            }
                        }
                        customHues.forEachIndexed { i, h ->
                            CarSliderRow(
                                label = "颜色 ${i + 1}",
                                valueText = "${h.roundToInt()}°",
                                value = { customHues.getOrElse(i) { 0f } },
                                valueRange = 0f..360f,
                                compact = compact
                            ) { v -> onCustomHue(i, v) }
                        }
                    }
                }

                if (on) {
                    CarListRowDivider()

                    Text(
                        "样式（${VisualizerStyle.entries.size} 款）",
                        color = HubTextPrimary,
                        fontSize = if (compact) 13.sp else 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    MusicVisualizer(
                        playing = playing,
                        tint = HubTextPrimary,
                        accent = HubPrimaryBlue,
                        fade = fade,
                        smoothing = smoothing,
                        gain = gain,
                        alpha = alpha,
                        style = style,
                        palette = palette,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(if (compact) 56.dp else 80.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(HubTextTertiary.copy(alpha = 0.08f))
                    )
                    StyleSelector(
                        selected = style,
                        compact = compact,
                        onSelect = onStyle
                    )

                    CarListRowDivider()

                    CarSliderRow(
                        label = "两端渐隐",
                        valueText = "${(fade * 100).roundToInt()}%",
                        value = { fade },
                        valueRange = 0f..1f,
                        compact = compact
                    ) { onFade(it) }
                    CarSliderRow(
                        label = "跟随平滑",
                        valueText = "%.2f".format(smoothing),
                        value = { smoothing },
                        valueRange = 0f..0.95f,
                        compact = compact
                    ) { onSmoothing(it) }
                    CarSliderRow(
                        label = "增益",
                        valueText = "×%.2f".format(gain),
                        value = { gain },
                        valueRange = 0.2f..3f,
                        compact = compact
                    ) { onGain(it) }
                    CarSliderRow(
                        label = "频谱模糊",
                        valueText = "${blur.roundToInt()} dp",
                        value = { blur },
                        valueRange = 0f..24f,
                        compact = compact
                    ) { onBlur(it) }
                    CarSliderRow(
                        label = "总透明度",
                        valueText = "${(alpha * 100).roundToInt()}%",
                        value = { alpha },
                        valueRange = 0f..1f,
                        compact = compact
                    ) { onAlpha(it) }
                }
            }
        }
    }

    CarDrawerHandle(
        open = open,
        onToggle = { open = it },
        swipe = swipe,
        axis = DrawerAxis.Vertical,
        modifier = modifier.padding(end = 8.dp)
    )
}

/** 音乐面板的三键 */
@Composable
private fun MusicControlButton(
    icon: ImageVector,
    size: Dp,
    compact: Boolean,
    /** 图标来源色 */
    tint: Color,
    prominent: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(size)
            .offset(y = -size * 0.04f)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(
                when {
                    prominent && compact -> 24.dp
                    prominent -> 33.dp
                    compact -> 20.dp
                    else -> 26.dp
                }
            )
        )
    }
}

@Composable
private fun MusicFooterRow(compact: Boolean, tint: Color, sourceLabel: String?) {
    if (sourceLabel == null) return
    Text(
        "来自 $sourceLabel",
        color = tint.copy(alpha = 0.75f),
        fontSize = if (compact) 11.sp else 12.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.widthIn(max = if (compact) 300.dp else 460.dp)
    )
}
