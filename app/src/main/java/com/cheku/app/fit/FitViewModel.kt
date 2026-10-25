package com.cheku.app.fit

import android.Manifest
import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cheku.app.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.ExperimentalCoroutinesApi

data class FitSignalStatus(
    val level: Int = 0,
    val mode: String = "等待定位",
    val accuracy: Float? = null,
    val heartRate: Int? = null,
    val cadence: Int? = null,
    val power: Int? = null,
    val connectedSensors: Int = 0
)

sealed interface FitExportState {
    data object Idle : FitExportState
    data object Preparing : FitExportState
    data class AwaitingFile(val fileName: String) : FitExportState
    data class Success(val fileName: String) : FitExportState
    data class Failure(val message: String) : FitExportState
    data object Cancelled : FitExportState
}

@OptIn(ExperimentalCoroutinesApi::class)
class FitViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = FitRepository(AppDatabase.get(app).ledgerDao())
    val activities = repository.observeActivities().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val selectedId = MutableStateFlow<String?>(null)
    val selectedActivity = combine(activities, selectedId) { list, id -> list.firstOrNull { it.id == id } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val samples = selectedId.flatMapLatest { id -> if (id == null) flowOf(emptyList()) else repository.observeSamples(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val activeSamples = activities.map { list -> list.firstOrNull { it.status != FitActivityStatus.Completed }?.id }
        .distinctUntilChanged()
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else repository.observeSamples(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val message = MutableStateFlow<String?>(null)
    private val _exportState = MutableStateFlow<FitExportState>(FitExportState.Idle)
    val exportState: StateFlow<FitExportState> = _exportState.asStateFlow()
    private val bleManager = BleSensorManager(app)
    val bleDevices = MutableStateFlow<List<Pair<String, String>>>(emptyList())
    val selectedBleAddresses = MutableStateFlow(app.getSharedPreferences("fit_ble", 0).getStringSet("addresses", emptySet()).orEmpty())
    val signalStatus = flow {
        val prefs = app.getSharedPreferences("fit_runtime", 0)
        while (true) {
            emit(FitSignalStatus(
                level = prefs.getInt("gps_level", 0),
                mode = prefs.getString("mode", "等待定位") ?: "等待定位",
                accuracy = prefs.getFloat("accuracy", -1f).takeIf { it >= 0f },
                heartRate = prefs.getInt("heart_rate", -1).takeIf { it > 0 },
                cadence = prefs.getInt("cadence", -1).takeIf { it >= 0 },
                power = prefs.getInt("power", -1).takeIf { it >= 0 },
                connectedSensors = prefs.getInt("connected_sensors", 0)
            ))
            delay(1_000)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FitSignalStatus())

    init {
        // Room is the source of truth. Reattach the foreground recorder whenever the UI
        // process is recreated with an unfinished activity.
        viewModelScope.launch {
            if (repository.activeActivity() != null) startService(FitRecordingService.ACTION_START, foreground = true)
        }
    }

    fun select(id: String?) { selectedId.value = id }
    fun start(name: String, autoPause: Boolean) {
        val context = getApplication<Application>()
        ContextCompat.startForegroundService(context, Intent(context, FitRecordingService::class.java).apply {
            action = FitRecordingService.ACTION_START
            putExtra(FitRecordingService.EXTRA_NAME, name); putExtra(FitRecordingService.EXTRA_AUTO_PAUSE, autoPause)
        })
    }
    fun recover() = start("", true)
    fun pause() = viewModelScope.launch {
        repository.activeActivity()?.let { repository.setStatus(it.id, FitActivityStatus.ManualPaused, FitTimerEventType.ManualPause) }
        startService(FitRecordingService.ACTION_PAUSE)
    }
    fun resume() = viewModelScope.launch {
        repository.activeActivity()?.let { repository.setStatus(it.id, FitActivityStatus.Recording, FitTimerEventType.Resume) }
        startService(FitRecordingService.ACTION_START, foreground = true)
    }
    fun finish() = viewModelScope.launch {
        repository.activeActivity()?.let { repository.finish(it.id) }
        startService(FitRecordingService.ACTION_STOP)
    }
    private fun serviceAction(actionName: String) { getApplication<Application>().startService(Intent(getApplication(), FitRecordingService::class.java).setAction(actionName)) }
    private fun startService(actionName: String, foreground: Boolean = false) {
        val context = getApplication<Application>()
        val intent = Intent(context, FitRecordingService::class.java).setAction(actionName)
        if (foreground) ContextCompat.startForegroundService(context, intent) else context.startService(intent)
    }
    fun rename(id: String, name: String) = viewModelScope.launch { repository.rename(id, name) }
    fun delete(id: String) = viewModelScope.launch { repository.delete(id); if (selectedId.value == id) selectedId.value = null }
    suspend fun buildFit(id: String, startFraction: Float = 0f, endFraction: Float = 1f): ByteArray {
        val activity = repository.activity(id) ?: error("记录不存在")
        val all = repository.samples(id)
        require(all.size >= 2) { "有效采样不足两条" }
        val startIndex = ((all.lastIndex) * startFraction.coerceIn(0f, .95f)).toInt()
        val endIndex = ((all.lastIndex) * endFraction.coerceIn(.05f, 1f)).toInt().coerceAtLeast(startIndex + 1)
        val bytes = FitCodec.encode(activity, all, repository.events(id), all[startIndex].timestamp, all[endIndex].timestamp)
        require(FitCodec.validate(bytes).valid) { FitCodec.validate(bytes).message }
        return bytes
    }
    fun beginExport() {
        if (_exportState.value is FitExportState.Preparing || _exportState.value is FitExportState.AwaitingFile) return
        _exportState.value = FitExportState.Preparing
    }
    fun awaitingExportFile(fileName: String) { _exportState.value = FitExportState.AwaitingFile(fileName) }
    fun exportCancelled() { _exportState.value = FitExportState.Cancelled }
    fun exportFailed(error: Throwable) {
        _exportState.value = FitExportState.Failure(error.message?.ifBlank { null } ?: "FIT 导出失败")
    }
    fun markExported(id: String, fileName: String) = viewModelScope.launch {
        runCatching { repository.markExported(id) }
            .onSuccess { _exportState.value = FitExportState.Success(fileName) }
            .onFailure { exportFailed(it) }
    }
    fun resetExportState() { _exportState.value = FitExportState.Idle }
    fun clearMessage() { message.value = null }
    fun scanBle() {
        bleDevices.value = emptyList()
        val stop = bleManager.scan { device ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                ContextCompat.checkSelfPermission(getApplication(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
            ) return@scan
            val label = runCatching { device.name }.getOrNull().orEmpty().ifBlank { "BLE 传感器" }
            bleDevices.update { list -> (list + (device.address to label)).distinctBy { it.first } }
        } ?: return
        viewModelScope.launch { delay(10_000); stop() }
    }
    fun toggleBle(address: String) {
        val next = selectedBleAddresses.value.toMutableSet().apply { if (!add(address)) remove(address) }
        selectedBleAddresses.value = next
        getApplication<Application>().getSharedPreferences("fit_ble", 0).edit().putStringSet("addresses", next).apply()
    }
    override fun onCleared() { bleManager.close(); super.onCleared() }
}
