package com.cheku.app.carmode

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import rikka.shizuku.Shizuku
import java.util.concurrent.CopyOnWriteArrayList

/** Shizuku 侧的连接与状态管理 */
internal object ShizukuCapture {
    const val TAG = "ChekuShizuku"

    /** 请求 Shizuku 授权的 requestCode */
    private const val REQUEST_CODE = 0x5EC5

    private const val USER_SERVICE_TAG = "cheku-spectrum"

    /** UserService 协议版本 */
    private const val USER_SERVICE_VERSION = 1

    /** 拉帧间隔 */
    private const val POLL_INTERVAL_MS = 20L

    enum class State {
        /** Shizuku 服务没在跑 */
        Unavailable,

        /** Shizuku 在跑，但本 App 还没拿到它的授权 */
        PermissionRequired,

        /** 已授权、可以连接 */
        Ready,

        /** 正在连接 */
        Connecting,

        /** 已连接、正在收帧 */
        Active,

        /** 连接失败 */
        Failed,
    }

    /** 当前状态 */
    var state by mutableStateOf(State.Unavailable)
        private set

    @Volatile
    var lastError: String? = null
        private set

    @Volatile
    var serviceInfo: String? = null
        private set

    @Volatile
    private var frameCount: Long = 0L

    @Volatile
    private var latest: FloatArray? = null

    /** 是否真的在收数据 */
    val isReady: Boolean get() = state == State.Active

    fun latestFrame(): FloatArray? = latest

    fun frames(): Long = frameCount

    private val listeners = CopyOnWriteArrayList<(FloatArray) -> Unit>()

    fun addListener(l: (FloatArray) -> Unit) {
        listeners.addIfAbsent(l)
    }

    fun removeListener(l: (FloatArray) -> Unit) {
        listeners.remove(l)
    }

    private var binderAlive = false

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        Log.i(TAG, "Shizuku binder 已就绪")
        binderAlive = true
        refresh()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        Log.w(TAG, "Shizuku binder 已断开（Shizuku 被杀 / 手机重启后未重启）")
        binderAlive = false
        // 通路随之失效：uid=2000 那边会自己收摊（binder 断了它就没人调用了），
        // 但 App 侧必须立刻把状态改成"不可用"，否则 UI 会一直显示"已连接"。
        stopPolling()
        state = State.Unavailable
        latest = null
        serviceInfo = null
    }

    private val requestPermissionResultListener =
        Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode != REQUEST_CODE) return@OnRequestPermissionResultListener
            if (grantResult == PackageManager.PERMISSION_GRANTED) {
                Log.i(TAG, "Shizuku 授权通过")
                state = State.Ready
                pendingContext?.let { connectInternal(it) }
            } else {
                lastError = "你拒绝了 Shizuku 授权"
                state = State.PermissionRequired
            }
        }

    private var listenersRegistered = false

    /** 注册 Shizuku 的全局回调 */
    fun ensureListeners() {
        if (listenersRegistered) return
        listenersRegistered = true
        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
        Shizuku.addRequestPermissionResultListener(requestPermissionResultListener)
    }

    /** 探测 Shizuku 可用性与授权状态 */
    fun refresh() {
        ensureListeners()
        state = try {
            // `isPreV11()`：v11 以下 Binder 协议不稳定，直接视为不可用。
            if (Shizuku.isPreV11() || !Shizuku.pingBinder()) {
                State.Unavailable
            } else if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                if (isReady) State.Active else State.Ready
            } else {
                State.PermissionRequired
            }
        } catch (t: Throwable) {
            Log.i(TAG, "refresh 失败（按不可用处理）：${t.message}")
            State.Unavailable
        }
    }

    fun requestPermission(context: Context) {
        pendingContext = context
        try {
            if (Shizuku.isPreV11() || !Shizuku.pingBinder()) {
                state = State.Unavailable
                return
            }
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                connectInternal(context)
                return
            }
            if (Shizuku.shouldShowRequestPermissionRationale()) {
                lastError = "你之前拒绝过 Shizuku 授权，请到 Shizuku 应用里手动允许「车库」"
                state = State.PermissionRequired
                return
            }
            Shizuku.requestPermission(REQUEST_CODE)
        } catch (t: Throwable) {
            lastError = "请求 Shizuku 授权失败：${t.message}"
            state = State.Failed
        }
    }

    /** 授权回调里要用的 Context */
    @Volatile
    private var pendingContext: Context? = null

    @Volatile
    private var service: ISpectrumService? = null

    @Volatile
    private var autoConnectSuppressed = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            Log.i(TAG, "UserService 已连接")
            val svc = ISpectrumService.asInterface(binder)
            if (svc == null) {
                lastError = "拿不到 UserService 接口（binder 为 null）"
                state = State.Failed
                return
            }
            service = svc
            try {
                val failure = svc.start()
                if (failure.isNullOrEmpty()) {
                    lastError = null
                    startPolling(svc)
                    Log.i(TAG, "Shizuku 捕获通路已启动；${svc.describe()}")
                } else {
                    lastError = failure
                    state = State.Failed
                    Log.w(TAG, "启动捕获失败：$failure")
                }
            } catch (t: Throwable) {
                lastError = "调用 UserService 失败：${t.message}"
                state = State.Failed
                Log.w(TAG, "调用 UserService 失败", t)
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.w(TAG, "UserService 已断开")
            stopPolling()
            service = null
            state = if (binderAlive) State.Ready else State.Unavailable
            latest = null
        }
    }

    private var poller: Thread? = null

    /** 启动拉帧线程 */
    private fun startPolling(svc: ISpectrumService) {
        stopPolling()
        state = State.Active
        poller = Thread {
            var lastInfoAt = 0L
            while (state == State.Active && !Thread.currentThread().isInterrupted) {
                val band = runCatching { svc.latestBands() }.getOrNull()
                if (band != null) {
                    latest = band
                    for (l in listeners) runCatching { l(band) }
                }
                val now = System.currentTimeMillis()
                if (now - lastInfoAt >= 1000L) {
                    lastInfoAt = now
                    runCatching { svc.frameCount() }.getOrNull()?.let { frameCount = it }
                    serviceInfo = runCatching { svc.describe() }.getOrNull()
                    // 服务端异常退出（读流失败等）要能反映到 UI 上，否则界面会一直显示"已连接"。
                    val alive = runCatching { svc.isCapturing() }.getOrNull() ?: false
                    if (!alive) {
                        lastError = "UserService 已停止捕获（详见日志 ChekuSpectrumSvc）"
                        state = State.Failed
                    }
                }
                try {
                    Thread.sleep(POLL_INTERVAL_MS)
                } catch (e: InterruptedException) {
                    break
                }
            }
        }.also { it.isDaemon = true; it.start() }
    }

    private fun stopPolling() {
        poller?.interrupt()
        poller = null
        frameCount = 0
    }

    private fun userServiceArgs(context: Context): Shizuku.UserServiceArgs =
        Shizuku.UserServiceArgs(
            ComponentName(context.packageName, SpectrumUserService::class.java.name)
        )
            .daemon(false)
            .version(USER_SERVICE_VERSION)
            .processNameSuffix("spectrum")
            .tag(USER_SERVICE_TAG)

    /** 连接并启动捕获 */
    fun connect(context: Context) {
        autoConnectSuppressed = false
        connectInternal(context)
    }

    fun maybeAutoConnect(context: Context) {
        if (autoConnectSuppressed) return
        if (state == State.Active || state == State.Connecting) return
        refresh()
        if (state == State.Ready) connectInternal(context)
    }

    private fun connectInternal(context: Context) {
        ensureListeners()
        if (state == State.Active || state == State.Connecting) return
        if (!binderAlive && !runCatching { Shizuku.pingBinder() }.getOrDefault(false)) {
            state = State.Unavailable
            return
        }
        binderAlive = true
        val granted = runCatching {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
        if (!granted) {
            requestPermission(context)
            return
        }
        state = State.Connecting
        lastError = null
        try {
            Shizuku.bindUserService(userServiceArgs(context), serviceConnection)
        } catch (t: Throwable) {
            lastError = "绑定 UserService 失败：${t.message}"
            state = State.Failed
        }
    }

    /** 断开并让 uid=2000 的进程退出 */
    fun disconnect(context: Context) {
        autoConnectSuppressed = true
        stopPolling()
        service?.let { runCatching { it.stop() } }
        runCatching {
            Shizuku.unbindUserService(userServiceArgs(context), serviceConnection, true)
        }
        service = null
        latest = null
        serviceInfo = null
        if (binderAlive) refresh() else state = State.Unavailable
    }

    fun refreshServiceInfo() {
        val s = service ?: return
        serviceInfo = runCatching { s.describe() }.getOrNull()
        runCatching { s.frameCount() }.getOrNull()?.let { frameCount = it }
    }
}
