package com.cheku.app.carmode

import android.content.ComponentName
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.net.Uri
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.SystemClock
import android.provider.CallLog
import android.provider.ContactsContract
import android.provider.Settings
import android.provider.Telephony
import android.telephony.SmsManager
import android.view.KeyEvent
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** 一条联系人号码 */
internal data class CarContact(val name: String, val number: String)

/** 读通讯录 */
internal fun queryCarContacts(resolver: ContentResolver, limit: Int = 400): List<CarContact> {
    val out = ArrayList<CarContact>(minOf(limit, 64))
    val projection = arrayOf(
        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,
        ContactsContract.CommonDataKinds.Phone.NUMBER
    )
    try {
        resolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            null,
            null,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY + " ASC"
        )?.use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow(projection[0])
            val numberIndex = cursor.getColumnIndexOrThrow(projection[1])
            while (out.size < limit && cursor.moveToNext()) {
                val number = cursor.getString(numberIndex)?.trim().orEmpty()
                if (number.isEmpty()) continue
                val name = cursor.getString(nameIndex)?.trim().orEmpty()
                out += CarContact(name.ifEmpty { number }, number)
            }
        }
    } catch (_: Exception) {
        // SecurityException（权限被撤）/ provider 不可用：按"空通讯录"处理。
    }
    return out
}

/** 一条通话记录 */
internal data class CarCall(val name: String?, val number: String, val type: Int, val date: Long)

/** 读最近通话 */
internal fun queryCarCallLog(resolver: ContentResolver, limit: Int = 100): List<CarCall> {
    val out = ArrayList<CarCall>(minOf(limit, 64))
    val projection = arrayOf(
        CallLog.Calls.CACHED_NAME,
        CallLog.Calls.NUMBER,
        CallLog.Calls.TYPE,
        CallLog.Calls.DATE
    )
    try {
        resolver.query(
            CallLog.Calls.CONTENT_URI,
            projection,
            null,
            null,
            CallLog.Calls.DATE + " DESC"
        )?.use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow(CallLog.Calls.CACHED_NAME)
            val numberIndex = cursor.getColumnIndexOrThrow(CallLog.Calls.NUMBER)
            val typeIndex = cursor.getColumnIndexOrThrow(CallLog.Calls.TYPE)
            val dateIndex = cursor.getColumnIndexOrThrow(CallLog.Calls.DATE)
            while (out.size < limit && cursor.moveToNext()) {
                val number = cursor.getString(numberIndex)?.trim().orEmpty()
                if (number.isEmpty()) continue
                out += CarCall(
                    name = cursor.getString(nameIndex)?.trim()?.takeIf { it.isNotEmpty() },
                    number = number,
                    type = cursor.getInt(typeIndex),
                    date = cursor.getLong(dateIndex)
                )
            }
        }
    } catch (_: Exception) {
        // 同 queryCarContacts：权限缺失 = 空列表，解释交给 UI。
    }
    return out
}

/** 一个短信会话 */
internal data class CarSmsThread(
    val threadId: Long,
    val address: String,
    /** 号码命中的联系人名 */
    val name: String?,
    val snippet: String,
    val date: Long
)

/** 读短信会话列表 */
internal fun queryCarSmsThreads(
    resolver: ContentResolver,
    lookup: Map<String, String>,
    limit: Int = 80,
    scanLimit: Int = 800
): List<CarSmsThread> {
    val out = ArrayList<CarSmsThread>(minOf(limit, 64))
    val seen = HashSet<Long>()
    val projection = arrayOf(
        Telephony.Sms.THREAD_ID,
        Telephony.Sms.ADDRESS,
        Telephony.Sms.BODY,
        Telephony.Sms.DATE
    )
    try {
        resolver.query(
            Telephony.Sms.CONTENT_URI,
            projection,
            null,
            null,
            Telephony.Sms.DATE + " DESC"
        )?.use { cursor ->
            val threadIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.THREAD_ID)
            val addressIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val bodyIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val dateIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)
            var scanned = 0
            while (scanned < scanLimit && out.size < limit && cursor.moveToNext()) {
                scanned++
                val threadId = cursor.getLong(threadIndex)
                if (!seen.add(threadId)) continue
                val address = cursor.getString(addressIndex)?.trim().orEmpty()
                out += CarSmsThread(
                    threadId = threadId,
                    address = address,
                    name = lookup[carPhoneMatchKey(address)],
                    // 列表只放得下一行：正文里的换行会把行高打乱，折成空格。
                    snippet = cursor.getString(bodyIndex)?.replace('\n', ' ')?.trim().orEmpty(),
                    date = cursor.getLong(dateIndex)
                )
            }
        }
    } catch (_: Exception) {
        // 同上。
    }
    return out
}

/** 一条短信 */
internal data class CarSmsMessage(val id: Long, val body: String, val date: Long, val outgoing: Boolean)

/** 读某个会话的消息 */
internal fun queryCarSmsMessages(
    resolver: ContentResolver,
    threadId: Long,
    limit: Int = 200
): List<CarSmsMessage> {
    val out = ArrayList<CarSmsMessage>(minOf(limit, 64))
    val projection = arrayOf(
        Telephony.Sms._ID,
        Telephony.Sms.BODY,
        Telephony.Sms.DATE,
        Telephony.Sms.TYPE
    )
    try {
        resolver.query(
            Telephony.Sms.CONTENT_URI,
            projection,
            Telephony.Sms.THREAD_ID + " = ?",
            arrayOf(threadId.toString()),
            Telephony.Sms.DATE + " DESC"
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(Telephony.Sms._ID)
            val bodyIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val dateIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)
            val typeIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.TYPE)
            while (out.size < limit && cursor.moveToNext()) {
                out += CarSmsMessage(
                    id = cursor.getLong(idIndex),
                    body = cursor.getString(bodyIndex).orEmpty(),
                    date = cursor.getLong(dateIndex),
                    outgoing = cursor.getInt(typeIndex) == Telephony.Sms.MESSAGE_TYPE_SENT
                )
            }
        }
    } catch (_: Exception) {
        // 同上。
    }
    return out
}

/** 直接发一条短信 */
internal fun sendCarSms(context: Context, address: String, body: String): Boolean {
    val text = body.trim()
    if (text.isEmpty() || address.isBlank()) return false
    return try {
        val manager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        } ?: return false
        val parts = manager.divideMessage(text)
        if (parts.size <= 1) {
            manager.sendTextMessage(address, null, text, null, null)
        } else {
            manager.sendMultipartTextMessage(address, null, parts, null, null)
        }
        true
    } catch (_: Exception) {
        false
    }
}

/** 号码归一化 */
internal fun carPhoneMatchKey(raw: String): String {
    val digits = raw.filter { it.isDigit() }
    return if (digits.length > 11) digits.takeLast(11) else digits
}

internal fun buildCarContactLookup(contacts: List<CarContact>): Map<String, String> {
    val map = HashMap<String, String>(contacts.size * 2)
    for (contact in contacts) {
        val key = carPhoneMatchKey(contact.number)
        if (key.isNotEmpty() && key !in map) map[key] = contact.name
    }
    return map
}

/** 列表里的时间戳文案 */
internal fun formatCarTimestamp(epochMs: Long, nowMs: Long): String {
    val calendar = Calendar.getInstance()
    calendar.timeInMillis = nowMs
    val nowYear = calendar.get(Calendar.YEAR)
    val nowDay = calendar.get(Calendar.DAY_OF_YEAR)
    calendar.timeInMillis = epochMs
    val year = calendar.get(Calendar.YEAR)
    val day = calendar.get(Calendar.DAY_OF_YEAR)
    val time = SimpleDateFormat("HH:mm", Locale.CHINA).format(Date(epochMs))
    return when {
        year == nowYear && day == nowDay -> time
        year == nowYear && nowDay - day == 1 -> "昨天 $time"
        year == nowYear -> SimpleDateFormat("M月d日", Locale.CHINA).format(Date(epochMs))
        else -> SimpleDateFormat("yyyy年M月d日", Locale.CHINA).format(Date(epochMs))
    }
}

internal const val LYRIC_FLIP_THRESHOLD = 2

internal data class LyricWatch(
    /** 这些计数属于哪首歌 */
    val trackKey: String? = null,
    /** 上一次看到的 TITLE */
    val lastTitle: String? = null,
    /** 同一首歌内 TITLE 变过的次数 */
    val flips: Int = 0,
    val titleDiffersFromDisplay: Boolean = false
) {
    val isLyricMode: Boolean
        get() = titleDiffersFromDisplay || flips >= LYRIC_FLIP_THRESHOLD
}

/** 推进一次观测 */
internal fun advanceLyricWatch(
    prev: LyricWatch,
    trackKey: String,
    title: String?,
    titleDiffersFromDisplay: Boolean = false
): LyricWatch = when {
    trackKey != prev.trackKey -> LyricWatch(
        trackKey = trackKey,
        lastTitle = title,
        flips = 0,
        titleDiffersFromDisplay = titleDiffersFromDisplay
    )

    // 强证据第一次出现即判定（不必再攒变化次数）。
    titleDiffersFromDisplay && !prev.titleDiffersFromDisplay ->
        prev.copy(titleDiffersFromDisplay = true, lastTitle = title ?: prev.lastTitle)

    title != null && prev.lastTitle != null && title != prev.lastTitle -> LyricWatch(
        trackKey = trackKey,
        lastTitle = title,
        flips = prev.flips + 1,
        titleDiffersFromDisplay = prev.titleDiffersFromDisplay
    )

    else -> prev
}

internal data class CarMusicState(
    val playing: Boolean,
    val title: String?,
    val artist: String?,
    val sourceLabel: String?,
    /** 专辑封面 */
    val artwork: Bitmap? = null,
    val lyricMode: Boolean = false,
    val songTitle: String? = null,

    /** 当前播放进度 */
    val positionMs: Long = 0L,

    /** 本曲总时长 */
    val durationMs: Long = 0L,

    val canSeek: Boolean = false
)

/** 音乐读取与控制 */
internal class CarMusicController(private val context: Context) {
    private val audio: AudioManager? = context.getSystemService(AudioManager::class.java)

    private var artworkKey: String? = null
    private var artworkCache: Bitmap? = null

    // 歌词模式的观测状态（判据的纯逻辑在 [advanceLyricWatch]，这里只持有它）。
    private var lyricWatch = LyricWatch()

    private var cachedController: MediaController? = null

    fun hasNotificationAccess(): Boolean {
        val enabled = try {
            Settings.Secure.getString(context.contentResolver, ENABLED_NOTIFICATION_LISTENERS)
        } catch (_: Exception) {
            null
        }.orEmpty()
        return enabled.contains(context.packageName)
    }

    fun notificationAccessIntent(): Intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)

    /** 读一次音乐状态 */
    fun snapshot(): CarMusicState {
        val musicActive = audio?.isMusicActive == true
        if (!hasNotificationAccess()) {
            return CarMusicState(playing = musicActive, title = null, artist = null, sourceLabel = null)
        }
        val controller = activeController()
            ?: return CarMusicState(playing = musicActive, title = null, artist = null, sourceLabel = null)
        val metadata = controller.metadata
        val playing = controller.playbackState?.state == PlaybackState.STATE_PLAYING || musicActive
        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)?.trim()?.takeIf { it.isNotEmpty() }
        val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)?.trim()?.takeIf { it.isNotEmpty() }

        val album = metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM)?.trim()?.takeIf { it.isNotEmpty() }
        val duration = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
        val trackKey = if (album == null && duration <= 0L && artist == null) {
            "title:${title.orEmpty()}"
        } else {
            "meta:${album.orEmpty()}|$duration|${artist.orEmpty()}"
        }
        if (trackKey != artworkKey) {
            artworkKey = trackKey
            artworkCache = loadArtwork(metadata)
        }

        val displayTitle = metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
        lyricWatch = advanceLyricWatch(
            prev = lyricWatch,
            trackKey = trackKey,
            title = title,
            titleDiffersFromDisplay = displayTitle != null && displayTitle != title
        )
        val lyricMode = lyricWatch.isLyricMode

        return CarMusicState(
            playing = playing,
            title = title,
            artist = artist,
            sourceLabel = try {
                val info = context.packageManager.getApplicationInfo(controller.packageName, 0)
                context.packageManager.getApplicationLabel(info).toString()
            } catch (_: Exception) {
                null
            },
            artwork = artworkCache,
            lyricMode = lyricMode,
            // 与当前 title 相同的 DISPLAY_TITLE 是"没区分"而不是"歌名"，丢掉。
            songTitle = if (lyricMode) displayTitle?.takeIf { it != title } else null,
            positionMs = positionOf(controller.playbackState, duration),
            durationMs = duration.coerceAtLeast(0L),
            // 有控制器就能拖（`TransportControls.seekTo` 走的就是它）。
            canSeek = duration > 0L
        )
    }

    private fun positionOf(state: PlaybackState?, duration: Long): Long {
        state ?: return 0L
        val base = state.position.coerceAtLeast(0L)
        val extrapolated = if (state.state == PlaybackState.STATE_PLAYING) {
            val elapsed = SystemClock.elapsedRealtime() - state.lastPositionUpdateTime
            if (elapsed in 1..POSITION_EXTRAPOLATION_LIMIT_MS) base + elapsed else base
        } else {
            base
        }
        return if (duration > 0L) extrapolated.coerceIn(0L, duration) else extrapolated
    }

    /** 进度拖动 */
    fun seekToFraction(fraction: Float): Boolean {
        val controller = cachedController ?: return false
        val duration = controller.metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
        if (duration <= 0L) return false
        val target = (duration * fraction.coerceIn(0f, 1f)).toLong()
        return runCatching { controller.transportControls.seekTo(target) }.isSuccess
    }

    private fun loadArtwork(metadata: MediaMetadata?): Bitmap? {
        metadata ?: return null
        BITMAP_KEYS.forEach { key ->
            runCatching { metadata.getBitmap(key) }.getOrNull()?.let { return it }
        }
        BITMAP_URI_KEYS.forEach { key ->
            val text = metadata.getString(key)?.takeIf { it.isNotBlank() } ?: return@forEach
            val uri = runCatching { Uri.parse(text) }.getOrNull() ?: return@forEach
            if (uri.scheme != ContentResolver.SCHEME_CONTENT) return@forEach
            runCatching { decodeBounded(context.contentResolver.openInputStream(uri)) }.getOrNull()
                ?.let { return it }
        }
        return null
    }

    private fun decodeBounded(stream: InputStream?): Bitmap? {
        if (stream == null) return null
        val bytes = stream.use { it.readBytes() }
        if (bytes.isEmpty()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val longest = maxOf(bounds.outWidth, bounds.outHeight)
        if (longest <= 0) return null
        var sample = 1
        while (longest / sample > MAX_ARTWORK_PX) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }

    private fun activeController(): MediaController? {
        val manager = context.getSystemService(MediaSessionManager::class.java) ?: return null
        val sessions = try {
            manager.getActiveSessions(ComponentName(context, CarNotificationListener::class.java))
        } catch (_: Exception) {
            return null
        }
        // "正在播放"的那个优先；都在暂停就取最近活跃的（列表第一个）。
        val chosen = sessions.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: sessions.firstOrNull()
        cachedController = chosen
        return chosen
    }

    fun togglePlay() = dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)

    fun skipNext() = dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)

    fun skipPrevious() = dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)

    private fun dispatchMediaKey(keyCode: Int) {
        val manager = audio ?: return
        manager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        manager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
    }

    fun musicVolume(): Int = audio?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0

    fun musicVolumeMax(): Int = audio?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 1

    fun setMusicVolume(value: Int) {
        audio?.setStreamVolume(AudioManager.STREAM_MUSIC, value.coerceIn(0, musicVolumeMax()), 0)
    }

    private companion object {
        const val ENABLED_NOTIFICATION_LISTENERS = "enabled_notification_listeners"

        /** 直接给位图的两个键 */
        val BITMAP_KEYS = listOf(
            MediaMetadata.METADATA_KEY_ALBUM_ART,
            MediaMetadata.METADATA_KEY_ART
        )

        val BITMAP_URI_KEYS = listOf(
            MediaMetadata.METADATA_KEY_ALBUM_ART_URI,
            MediaMetadata.METADATA_KEY_ART_URI
        )

        const val MAX_ARTWORK_PX = 1024

        const val POSITION_EXTRAPOLATION_LIMIT_MS = 20_000L
    }
}
