package com.cheku.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.cheku.app.data.AppDatabase
import com.cheku.app.data.DB_SCHEMA_VERSION
import com.cheku.app.data.EnergyRecordEntity
import com.cheku.app.data.EnergyRecordInput
import com.cheku.app.data.EnergyRecordType
import com.cheku.app.data.EnergySettlementEntity
import com.cheku.app.data.EnergyType
import com.cheku.app.data.LedgerRepository
import com.cheku.app.data.MaintenanceHistoryEntity
import com.cheku.app.data.MaintenanceItemEntity
import com.cheku.app.data.MaintenanceStatus
import com.cheku.app.data.MileageRecordEntity
import com.cheku.app.data.TransmissionType
import com.cheku.app.data.VehicleCategory
import com.cheku.app.data.VehicleEntity
import com.cheku.app.data.SettlementStatus
import com.cheku.app.data.supportsCharging
import com.cheku.app.data.supportsFuel
import com.cheku.app.domain.EnergySaveResult
import com.cheku.app.domain.MaintenanceSnapshot
import com.cheku.app.domain.evaluateMaintenance
import android.content.Context
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import com.cheku.app.net.readBytesLimited
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipInputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

data class LedgerUiState(
    val vehicles: List<VehicleEntity> = emptyList(),
    val selectedVehicle: VehicleEntity? = null,
    val recentRecords: List<RecentRecordItem> = emptyList(),
    val maintenance: List<MaintenanceSnapshot> = emptyList(),
    val settings: AppSettings = AppSettings(),
    val isFetchingOilPrice: Boolean = false,
    /** 联网油价快照 */
    val oilPrice: OilPriceSnapshot? = null,
    val message: String? = null,
    val isSaving: Boolean = false,
    val vehicleConsumptions: Map<String, VehicleConsumptionSummary> = emptyMap(),
    val totalFuelLiters: String = "0",
    val totalChargeKwh: String = "0",
    val mileageRecords: List<MileageRecordEntity> = emptyList(),
    val energyRecords: List<EnergyRecordEntity> = emptyList(),
    val energySettlements: List<EnergySettlementEntity> = emptyList(),
    val maintenanceHistory: List<MaintenanceHistoryEntity> = emptyList()
)

data class VehicleConsumptionSummary(
    val fuel: String? = null,
    val charge: String? = null
)

data class UpdateUiState(
    val isChecking: Boolean = false,
    val hasChecked: Boolean = false,
    val checkFailed: Boolean = false,
    val latestVersion: String? = null,
    val availableUpdate: AppUpdateInfo? = null,
    val showUpToDateDialog: Boolean = false
)

data class EnergyRecordFormInput(
    val type: EnergyRecordType,
    val mileage: String,
    val amount: String,
    val orderAmount: String,
    val paidAmount: String,
    val isFull: Boolean,
    val missedPrevious: Boolean,
    val fuelGrade: String = "",
    val unitPrice: String = "",
    val chargeMethod: String = "",
    val electricityPrice: String = "",
    val serviceFee: String = "",
    val chargeLimitSoc: String = "",
    val note: String = ""
)

data class RecentRecordItem(
    val title: String,
    val subtitle: String,
    val timeText: String,
    val trailing: String,
    val detailLines: List<String>,
    val createdAt: Long,
    val type: RecentRecordType,
    val recordId: String = "",
    val vehicleId: String = "",
    val vehicleName: String = "",
    val mileageValue: Double = 0.0,
    val roadFeeAmount: Double = 0.0,
    val note: String = "",
    val amountValue: Double = 0.0,
    val orderAmountValue: Double = 0.0,
    val paidAmountValue: Double = 0.0,
    val isFull: Boolean = true,
    val missedPrevious: Boolean = false,
    val fuelGrade: String = "",
    val unitPriceValue: Double = 0.0,
    val chargeMethod: String = "",
    val electricityPriceValue: Double = 0.0,
    val serviceFeeValue: Double = 0.0,
    val chargeLimitSoc: Int? = null,
    val methodValue: String = "",
    val tripText: String = "",
    val amountLabel: String = "",
    val amountUnit: String = "",
    val statusText: String = ""
)

enum class RecentRecordType {
    Mileage,
    Fuel,
    Charge
}

private data class UiControlState(
    val message: String?,
    val isSaving: Boolean,
    val isFetchingOilPrice: Boolean,
    val oilPrice: OilPriceSnapshot?
)

private data class LedgerDataState(
    val vehicles: List<VehicleEntity>,
    val mileageRecords: List<MileageRecordEntity>,
    val energyRecords: List<EnergyRecordEntity>,
    val energySettlements: List<EnergySettlementEntity>,
    val maintenanceItems: List<MaintenanceItemEntity>
)

/** 可读导出的格式 */
enum class ReadableExportFormat(val extension: String, val mimeType: String) {
    /** 单文件分段 CSV，可用 Excel 直接打开 */
    Csv("csv", "text/csv"),

    /** 多工作表 + 总览汇总表 */
    Xls("xls", "application/vnd.ms-excel"),

    Zip("zip", "application/zip"),
}

data class BackupExportRange(
    val vehiclesAndMileage: Boolean = true,
    val energy: Boolean = true,
    val maintenance: Boolean = true,
    val performance: Boolean = true,
    val fit: Boolean = true,
) {
    val isEmpty: Boolean
        get() = !vehiclesAndMileage && !energy && !maintenance && !performance && !fit

    /** 给人看的范围摘要，写进导出历史 */
    val summary: String
        get() = buildList {
            if (vehiclesAndMileage) add("车辆与行驶")
            if (energy) add("能源")
            if (maintenance) add("保养与费用")
            if (performance) add("测试")
            if (fit) add("轨迹")
        }.joinToString("、").ifEmpty { "（未选）" }
}

sealed interface RegisterCodeState {
    data object Idle : RegisterCodeState
    data class Sent(val email: String) : RegisterCodeState
    data class RateLimited(val retryAfterSeconds: Long) : RegisterCodeState
    data class Failed(val message: String) : RegisterCodeState
}

/** 忘记密码流程的结果 */
sealed interface ForgotPasswordState {
    data object Idle : ForgotPasswordState
    data class CodeSent(val email: String) : ForgotPasswordState
    data class RateLimited(val retryAfterSeconds: Long) : ForgotPasswordState
    data class Failed(val message: String) : ForgotPasswordState
    data object PasswordReset : ForgotPasswordState
}

/** 云端用量 */
data class CloudUsage(
    val usedBytes: Long,
    val quotaBytes: Long,
    val backupAt: String?,
    val appVersion: String?,
)

/** 更新包下载状态 */
sealed interface UpdateDownloadState {
    data object Idle : UpdateDownloadState

    /** 下载中 */
    data class Downloading(val doneBytes: Long, val totalBytes: Long?) : UpdateDownloadState {
        val fraction: Float?
            get() = totalBytes?.takeIf { it > 0L }?.let { (doneBytes.toFloat() / it).coerceIn(0f, 1f) }
    }

    /** 已下好并已拉起安装器 */
    data class Ready(val bytes: Long) : UpdateDownloadState

    data class Failed(val message: String) : UpdateDownloadState
}

/** 上传 / 恢复进度 */
data class CloudProgress(
    val label: String,
    val doneBytes: Long,
    val totalBytes: Long,
) {
    val fraction: Float
        get() = if (totalBytes <= 0L) 0f else (doneBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
}

class LedgerViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.get(application)
    private val repository = LedgerRepository(database.ledgerDao(), database.performanceDao()) { block ->
        database.withTransaction { block() }
    }
    private val settingsStore = AppSettingsStore(application)
    private val selectedVehicleId = MutableStateFlow<String?>(null)
    private val message = MutableStateFlow<String?>(null)
    private val isSaving = MutableStateFlow(false)
    private val isFetchingOilPrice = MutableStateFlow(false)

    /** 油价快照的状态源 */
    private val oilPriceCache = OilPriceCache(
        application.getSharedPreferences(OilPriceCache.Key.PREFERENCES_NAME, android.content.Context.MODE_PRIVATE)
    )
    private val oilPrice = MutableStateFlow(oilPriceCache.read())
    private var pendingUndo: MaintenanceUndoSnapshot? = null
    private val _undoAvailable = MutableStateFlow(false)
    val undoAvailable: StateFlow<Boolean> = _undoAvailable
    private val _updateState = MutableStateFlow(UpdateUiState())
    val updateState: StateFlow<UpdateUiState> = _updateState
    private var updateCheckJob: Job? = null

    /** 更新包体积 */
    private val _updatePackageBytes = MutableStateFlow<Long?>(null)
    val updatePackageBytes: StateFlow<Long?> = _updatePackageBytes

    /** 体积探测是否进行中 */
    private val _updatePackageProbing = MutableStateFlow(false)
    val updatePackageProbing: StateFlow<Boolean> = _updatePackageProbing

    /** 更新包下载态 */
    private val _updateDownload = MutableStateFlow<UpdateDownloadState>(UpdateDownloadState.Idle)
    val updateDownload: StateFlow<UpdateDownloadState> = _updateDownload

    /** 已探测过体积的那个版本号 */
    private var probedUpdateVersion: String? = null

    /** 已下载好的安装包 */
    private var downloadedApk: java.io.File? = null

    private data class MaintenanceUndoSnapshot(
        val itemId: String,
        val previousMileage: Int,
        val previousAt: Long
    )

    private val controls = combine(message, isSaving, isFetchingOilPrice, oilPrice) { msg, saving, fetching, oil ->
        UiControlState(msg, saving, fetching, oil)
    }

    private val ledgerData = combine(
        repository.observeVehicles(),
        repository.observeMileageRecords(),
        repository.observeEnergyRecords(),
        repository.observeEnergySettlements(),
        repository.observeMaintenanceItems()
    ) { vehicles, mileageRecords, energyRecords, energySettlements, maintenanceItems ->
        LedgerDataState(vehicles, mileageRecords, energyRecords, energySettlements, maintenanceItems)
    }

    private val contentState = combine(
        ledgerData,
        repository.observeAllHistory(),
        settingsStore.observe(),
        selectedVehicleId
    ) { data, maintenanceHistory, settings, selectedId ->
        withContext(Dispatchers.Default) {
            val selected = data.vehicles.firstOrNull { it.id == selectedId } ?: data.vehicles.firstOrNull()
            val vehicleById = data.vehicles.associateBy { it.id }
            val settlementByEndRecord = data.energySettlements
                .filter { it.endRecordId != null }
                .associateBy { it.endRecordId }
            val recent = (data.mileageRecords.map { it.toRecent(vehicleById[it.vehicleId]) } +
                data.energyRecords.map {
                    it.toRecent(
                        vehicleById[it.vehicleId],
                        settings.distanceUnit,
                        settlementByEndRecord[it.id]
                    )
                })
                .sortedByDescending { it.createdAt }
                .take(12)

            // 小计里程：按时间顺序计算相邻记录里程差
            data class Rp(val vehicleId: String, val mileage: Double, val createdAt: Long)
            val points = buildList {
                data.mileageRecords.forEach { add(Rp(it.vehicleId, it.mileage, it.createdAt)) }
                data.energyRecords.forEach { add(Rp(it.vehicleId, it.mileage, it.createdAt)) }
            }.sortedBy { it.createdAt }
            val lastMileage = mutableMapOf<String, Double>()
            val tripByKey = mutableMapOf<Pair<String, Long>, Double>()
            for (p in points) {
                val prev = lastMileage[p.vehicleId]
                if (prev != null && p.mileage > prev) {
                    tripByKey[Pair(p.vehicleId, p.createdAt)] = p.mileage - prev
                }
                lastMileage[p.vehicleId] = p.mileage
            }
            val recentWithTrip = recent.map { r ->
                val trip = tripByKey[Pair(r.vehicleId, r.createdAt)]
                r.copy(tripText = if (trip != null) "+${formatNumber(trip)} km" else "")
            }

            val now = System.currentTimeMillis()
            val snapshots = data.maintenanceItems.mapNotNull { item ->
                val vehicle = vehicleById[item.vehicleId] ?: return@mapNotNull null
                evaluateMaintenance(item, vehicle.currentMileage.toInt(), now)
            }.sortedWith(
                compareBy<MaintenanceSnapshot> {
                    when (it.status) {
                        MaintenanceStatus.Overdue -> 0
                        MaintenanceStatus.DueSoon -> 1
                        MaintenanceStatus.Normal -> 2
                    }
                }.thenBy { it.remainingKm }
            )
            val settlementsByVehicle = data.energySettlements.groupBy { it.vehicleId }
            val recordsByVehicle = data.energyRecords.groupBy { it.vehicleId }
            val consumptions = data.vehicles.associate { vehicle ->
                val settlements = settlementsByVehicle[vehicle.id].orEmpty()
                val records = recordsByVehicle[vehicle.id].orEmpty()
                vehicle.id to VehicleConsumptionSummary(
                    fuel = energySummaryText(
                        EnergyRecordType.Fuel,
                        settlements,
                        records,
                        settings.distanceUnit
                    ).takeIf { vehicle.energyType.supportsFuel },
                    charge = energySummaryText(
                        EnergyRecordType.Charge,
                        settlements,
                        records,
                        settings.distanceUnit
                    ).takeIf { vehicle.energyType.supportsCharging }
                )
            }
            val totalFuel = data.energyRecords
                .filter { it.type == EnergyRecordType.Fuel }
                .sumOf { it.amount }
            val totalCharge = data.energyRecords
                .filter { it.type == EnergyRecordType.Charge }
                .sumOf { it.amount }
            LedgerUiState(
                vehicles = data.vehicles,
                selectedVehicle = selected,
                recentRecords = recentWithTrip,
                maintenance = snapshots,
                settings = settings,
                vehicleConsumptions = consumptions,
                totalFuelLiters = formatMoney(totalFuel),
                totalChargeKwh = formatMoney(totalCharge),
                mileageRecords = data.mileageRecords,
                energyRecords = data.energyRecords,
                energySettlements = data.energySettlements,
                maintenanceHistory = maintenanceHistory
            )
        }
    }

    val uiState: StateFlow<LedgerUiState> = combine(contentState, controls) { content, control ->
        content.copy(
            isFetchingOilPrice = control.isFetchingOilPrice,
            oilPrice = control.oilPrice,
            message = control.message,
            isSaving = control.isSaving
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LedgerUiState())

    init {
        viewModelScope.launch {
            val databaseIsEmpty = repository.getAllVehicles().isEmpty()
            val shouldSeedDemoV4 = !settingsStore.isDemoTemplatesSeeded()
            val shouldSeed = !settingsStore.isDataInitialized() ||
                (shouldSeedDemoV4 && databaseIsEmpty)
            if (shouldSeed || !databaseIsEmpty) {
                repository.seedIfEmpty()
            }
            if (shouldSeedDemoV4 && databaseIsEmpty) repository.seedQinPlusDemoIfMissing()
            if (shouldSeed) settingsStore.markDataInitialized()
            settingsStore.markDemoTemplatesSeeded()
        }
        checkForUpdates(manual = false)
        if (!oilPriceCache.fetchedToday()) refreshOilPrice(showFeedback = false)
        migrateExternalCertificates()
    }

    private fun migrateExternalCertificates() {
        viewModelScope.launch(Dispatchers.IO) {
            val failed = runCatching {
                CertificateRepository(getApplication()).migrateExternalCertificates()
            }.getOrDefault(0)
            if (failed > 0) {
                message.value = "有 $failed 份证书的原文件已无法读取，请到「人车证书」页重新上传"
            }
        }
    }

    fun selectVehicle(id: String) {
        selectedVehicleId.value = id
    }

    fun clearMessage() {
        message.value = null
    }

    fun checkForUpdates(manual: Boolean = true) {
        if (updateCheckJob?.isActive == true) return
        updateCheckJob = viewModelScope.launch {
            _updateState.update {
                it.copy(
                    isChecking = true,
                    checkFailed = false,
                    showUpToDateDialog = false
                )
            }
            runCatching { AppUpdateService.loadLatest() }
                .onSuccess { latest ->
                    val hasUpdate = isVersionNewer(latest.version, BuildConfig.VERSION_NAME)
                    val forceRequired = latest.forceUpdate ||
                        isForceUpdateRequired(latest.forceUpdateVersion, BuildConfig.VERSION_NAME)
                    val effectiveLatest = latest.copy(forceUpdate = forceRequired)
                    if (_updateState.value.availableUpdate?.version != effectiveLatest.version) {
                        _updateDownload.value = UpdateDownloadState.Idle
                        downloadedApk = null
                        _updatePackageBytes.value = null
                        probedUpdateVersion = null
                    }
                    _updateState.update {
                        it.copy(
                            isChecking = false,
                            hasChecked = true,
                            checkFailed = false,
                            latestVersion = latest.version,
                            availableUpdate = effectiveLatest.takeIf { hasUpdate },
                            showUpToDateDialog = manual && !hasUpdate
                        )
                    }
                }
                .onFailure {
                    _updateState.update { state ->
                        state.copy(
                            isChecking = false,
                            hasChecked = state.hasChecked || manual,
                            checkFailed = manual,
                            showUpToDateDialog = false
                        )
                    }
                    if (manual) message.value = "检查更新失败，请稍后重试"
                }
        }
    }

    fun dismissAvailableUpdate() {
        _updateState.update { state ->
            if (state.availableUpdate?.forceUpdate == true) state
            else state.copy(availableUpdate = null)
        }
    }

    fun dismissUpToDateDialog() {
        _updateState.update { it.copy(showUpToDateDialog = false) }
    }

    /** 探测更新包体积 */
    fun probeUpdatePackageSize(version: String?, url: String?) {
        if (version.isNullOrBlank() || url.isNullOrBlank()) return
        if (probedUpdateVersion == version && _updatePackageBytes.value != null) return
        probedUpdateVersion = version
        _updatePackageBytes.value = null
        _updatePackageProbing.value = true
        viewModelScope.launch {
            try {
                _updatePackageBytes.value = AppUpdateService.probeApkSize(url)
            } finally {
                _updatePackageProbing.value = false
            }
        }
    }

    /** 下载更新包 */
    fun downloadUpdatePackage() {
        val version = _updateState.value.availableUpdate?.version ?: return
        if (_updateDownload.value is UpdateDownloadState.Downloading) return
        viewModelScope.launch {
            _updateDownload.value = UpdateDownloadState.Downloading(0L, null)
            try {
                val apk = AppUpdateService.downloadLatestApk(getApplication(), version) { done, total ->
                    _updateDownload.value = UpdateDownloadState.Downloading(done, total)
                }
                downloadedApk = apk
                apk.length().takeIf { it > 0L }?.let { _updatePackageBytes.value = it }
                _updateDownload.value = UpdateDownloadState.Ready(apk.length())
                runCatching { AppUpdateService.launchInstaller(getApplication(), apk) }
            } catch (error: Throwable) {
                downloadedApk = null
                _updateDownload.value = UpdateDownloadState.Failed(
                    error.message ?: error.javaClass.simpleName
                )
            }
        }
    }

    /** 已经下好就只拉起安装器 */
    fun installDownloadedUpdate(): Boolean {
        val apk = downloadedApk ?: return false
        return runCatching { AppUpdateService.launchInstaller(getApplication(), apk) }.isSuccess
    }

    /** 联网刷新油价 */
    private fun refreshOilPrice(showFeedback: Boolean) {
        if (isFetchingOilPrice.value) return
        viewModelScope.launch {
            isFetchingOilPrice.value = true
            try {
                val province = settingsStore.observe().value.oilPriceProvince
                val snapshot = runCatching { OilPriceService.load(province) }.getOrNull()
                if (snapshot == null) {
                    if (showFeedback) message.value = "油价更新失败，已沿用上次数据"
                } else {
                    oilPrice.value = snapshot
                    oilPriceCache.write(snapshot)
                    settingsStore.updateOilPricesFromWeb(
                        province = snapshot.province,
                        prices = snapshot.grades.associate { quote ->
                            quote.grade to String.format(Locale.CHINA, "%.2f", quote.price)
                        },
                        source = snapshot.source
                    )
                    if (showFeedback) message.value = "已更新 ${snapshot.province} 油价"
                }
            } finally {
                isFetchingOilPrice.value = false
            }
        }
    }

    fun updateOilPreset(grade: String, value: String) {
        settingsStore.updateOilPrice(grade, value)
    }

    fun updateChargePreset(home: String, commercial: String, service: String) {
        settingsStore.updateCharge(home, commercial, service)
    }

    fun updateDistanceUnit(unit: DistanceUnit) {
        settingsStore.updateDistanceUnit(unit)
    }

    fun addVehicle(
        name: String,
        model: String,
        plate: String,
        category: VehicleCategory,
        energyType: EnergyType,
        transmissionType: TransmissionType,
        fuelTankCapacityText: String,
        batteryCapacityText: String,
        defaultChargeLimitSocText: String,
        mileageText: String,
        purchasePriceText: String = "0",
        purchaseTaxText: String = "0",
        mfgDateText: String = ""
    ) {
        val mileage = mileageText.toDoubleOrNull() ?: return show("请输入有效里程")
        val fuelTankCapacity = fuelTankCapacityText.toDoubleOrNull()
        val batteryCapacity = batteryCapacityText.toDoubleOrNull()
        val chargeLimit = parseChargeLimit(energyType, defaultChargeLimitSocText) ?: return
        val mfgDate = parseMfgDate(mfgDateText)
        val purchasePrice = purchasePriceText.toDoubleOrNull()?.takeIf { it >= 0.0 } ?: return show("请输入有效购车价")
        val purchaseTax = purchaseTaxText.toDoubleOrNull()?.takeIf { it >= 0.0 } ?: return show("请输入有效购置税")
        viewModelScope.launchSaving {
            val id = repository.addVehicle(name, model, plate, category, energyType, transmissionType, fuelTankCapacity, batteryCapacity, chargeLimit, mileage, purchasePrice, purchaseTax, mfgDate)
            selectedVehicleId.value = id
            message.value = "车辆已新增"
        }
    }

    fun updateVehicle(
        id: String,
        name: String,
        model: String,
        plate: String,
        category: VehicleCategory,
        energyType: EnergyType,
        transmissionType: TransmissionType,
        fuelTankCapacityText: String,
        batteryCapacityText: String,
        defaultChargeLimitSocText: String,
        mileageText: String,
        purchasePriceText: String = "0",
        purchaseTaxText: String = "0",
        mfgDateText: String = ""
    ) {
        val mileage = mileageText.toDoubleOrNull() ?: return show("请输入有效里程")
        val fuelTankCapacity = fuelTankCapacityText.toDoubleOrNull()
        val batteryCapacity = batteryCapacityText.toDoubleOrNull()
        val chargeLimit = parseChargeLimit(energyType, defaultChargeLimitSocText) ?: return
        val mfgDate = parseMfgDate(mfgDateText)
        val purchasePrice = purchasePriceText.toDoubleOrNull()?.takeIf { it >= 0.0 } ?: return show("请输入有效购车价")
        val purchaseTax = purchaseTaxText.toDoubleOrNull()?.takeIf { it >= 0.0 } ?: return show("请输入有效购置税")
        viewModelScope.launchSaving {
            repository.updateVehicle(id, name, model, plate, category, energyType, transmissionType, fuelTankCapacity, batteryCapacity, chargeLimit, mileage, purchasePrice, purchaseTax, mfgDate)
            selectedVehicleId.value = id
            message.value = "车辆已更新"
        }
    }

    private fun parseMfgDate(text: String): Long? {
        if (text.isBlank()) return null
        return runCatching { SimpleDateFormat("yyyy-MM-dd", java.util.Locale.CHINA).parse(text)?.time }.getOrNull()
    }

    fun deleteVehicle(id: String) {
        viewModelScope.launchSaving {
            repository.deleteVehicle(id)
            if (selectedVehicleId.value == id) selectedVehicleId.value = null
            message.value = "车辆已删除"
        }
    }

    fun saveMileage(mileageText: String, note: String, roadFeeText: String, timeText: String, allowOdometerDecrease: Boolean = false) {
        val vehicle = uiState.value.selectedVehicle ?: return show("请先选择车辆")
        val mileage = mileageText.toDoubleOrNull() ?: return show("请输入有效里程")
        val roadFee = roadFeeText.ifBlank { "0" }.toDoubleOrNull() ?: return show("请输入有效路费")
        val createdAt = parseRecordDateTime(timeText) ?: return show("请输入有效时间：yyyy-MM-dd HH:mm")
        viewModelScope.launchSaving {
            if (repository.addMileageRecord(vehicle.id, mileage, note, roadFee, allowOdometerDecrease, createdAt)) {
                message.value = "里程已保存"
            } else {
                message.value = "车辆不存在，保存失败"
            }
        }
    }

    fun addCarModeTrip(tripKm: Double, note: String) {
        if (!tripKm.isFinite() || tripKm <= 0.0) return
        val vehicle = uiState.value.selectedVehicle
            ?: return show("未选择车辆，本次行程未写入里程")
        val target = vehicle.currentMileage + tripKm
        viewModelScope.launchSaving {
            val saved = repository.addMileageRecord(
                vehicleId = vehicle.id,
                mileage = target,
                note = note,
                roadFeeAmount = 0.0,
                allowOdometerDecrease = false
            )
            message.value = if (saved) {
                "本次行程 ${String.format(Locale.CHINA, "%.2f", tripKm)} km 已写入「${vehicle.name}」"
            } else {
                "车辆不存在，本次行程未写入"
            }
        }
    }

    fun saveEnergy(form: EnergyRecordFormInput, timeText: String, allowOdometerDecrease: Boolean = false) {
        val vehicle = uiState.value.selectedVehicle ?: return show("请先选择车辆")
        val input = parseEnergyInput(form) ?: return
        val createdAt = parseRecordDateTime(timeText) ?: return show("请输入有效时间：yyyy-MM-dd HH:mm")
        viewModelScope.launchSaving {
            val result = repository.addEnergyRecord(vehicle.id, input, allowOdometerDecrease, createdAt)
            message.value = energyResultMessage(form.type, result, form.chargeLimitSoc)
        }
    }

    fun addCustomMaintenance(name: String, intervalKmText: String, intervalDaysText: String) {
        val vehicle = uiState.value.selectedVehicle ?: return show("请先选择车辆")
        val intervalKm = intervalKmText.toIntOrNull() ?: return show("请输入有效里程周期")
        val intervalDays = intervalDaysText.toIntOrNull() ?: return show("请输入有效天数周期")
        viewModelScope.launchSaving {
            repository.addCustomMaintenanceItem(vehicle.id, name, intervalKm, intervalDays)
            message.value = "保养项目已新增"
        }
    }

    fun completeMaintenance(
        itemId: String,
        laborCost: Double = 0.0,
        partsCost: Double = 0.0,
        note: String = "",
        partNumber: String = "",
        serviceAt: Long = System.currentTimeMillis()
    ) {
        val selected = uiState.value.maintenance.firstOrNull { it.item.id == itemId } ?: return
        pendingUndo = MaintenanceUndoSnapshot(itemId, selected.item.lastServiceMileage, selected.item.lastServiceAt)
        _undoAvailable.value = true
        viewModelScope.launchSaving {
            repository.completeMaintenance(
                itemId = itemId,
                serviceMileage = uiState.value.vehicles.firstOrNull { it.id == selected.item.vehicleId }?.currentMileage?.toInt()
                    ?: selected.item.lastServiceMileage,
                note = if (note.isNotBlank()) note else "手机端完成",
                laborCost = laborCost,
                partsCost = partsCost,
                partNumber = partNumber,
                now = serviceAt
            )
            message.value = "保养已完成"
        }
    }

    fun undoLastMaintenance() {
        val undo = pendingUndo ?: return
        pendingUndo = null
        _undoAvailable.value = false
        viewModelScope.launchSaving {
            repository.undoMaintenance(undo.itemId, undo.previousMileage, undo.previousAt)
            message.value = "已撤销维护"
        }
    }

    fun getHistoryFlow(itemId: String) = repository.observeHistoryForItem(itemId)

    suspend fun exportPackageTo(
        out: OutputStream,
        range: BackupExportRange = BackupExportRange(),
        atMillis: Long = System.currentTimeMillis(),
        scratchDir: File = getApplication<Application>().cacheDir
    ) {
        val data = ReadableExporter.LedgerData(
            vehicles = if (range.vehiclesAndMileage) repository.getAllVehicles() else emptyList(),
            mileageRecords = if (range.vehiclesAndMileage) repository.getAllMileageRecords() else emptyList(),
            energyRecords = if (range.energy) repository.getAllEnergyRecords() else emptyList(),
            energySettlements = if (range.energy) repository.getAllEnergySettlements() else emptyList(),
            maintenanceItems = if (range.maintenance) repository.getAllMaintenanceItems() else emptyList(),
            maintenanceHistory = if (range.maintenance) repository.getAllMaintenanceHistory() else emptyList(),
            performanceSessions = if (range.performance) repository.getAllPerformanceSessions() else emptyList(),
            fitActivities = if (range.fit) repository.getAllFitActivities() else emptyList(),
            fitSamples = if (range.fit) repository.getAllFitSamples() else emptyList(),
            fitEvents = if (range.fit) repository.getAllFitEvents() else emptyList(),
            performanceSamples = if (range.performance) repository.getAllPerformanceSamples() else emptyList(),
            performanceSplits = if (range.performance) repository.getAllPerformanceSplits() else emptyList(),
            performanceNoise = if (range.performance) repository.getAllPerformanceNoise() else emptyList(),
            performanceJerks = if (range.performance) repository.getAllPerformanceJerks() else emptyList()
        )
        val plan = ReadableExporter.planVolumesFor(data)
        val certFiles = certFiles()
        BackupPackage.writeExportPackageStreaming(
            out = out,
            atMillis = atMillis,
            appVersion = BuildConfig.VERSION_NAME,
            dbSchema = DB_SCHEMA_VERSION,
            counts = ReadableExporter.countsOf(data),
            scratchDir = scratchDir,
            plan = plan,
            renderVolume = { index, sink -> ReadableExporter.renderVolume(data, plan[index], sink) },
            writeCerts = if (certFiles.isNotEmpty()) {
                { zip ->
                    val entries = ArrayList<BackupPackage.CertEntry>()
                    BackupPackage.writeCertsEntries(zip, atMillis, BuildConfig.VERSION_NAME, certFiles, entries)
                }
            } else null
        )
    }

    fun exportCertsArchiveTo(out: OutputStream, atMillis: Long = System.currentTimeMillis()) {
        BackupPackage.writeCertsArchive(
            out = out,
            atMillis = atMillis,
            appVersion = BuildConfig.VERSION_NAME,
            files = certFiles()
        )
    }

    private val accountService by lazy {
        AccountService(
            EncryptedKeyValueStore(
                SharedPreferencesStore(
                    getApplication<Application>().getSharedPreferences(ACCOUNT_PREFS, Context.MODE_PRIVATE)
                ),
                KeystoreSecretCipher(),
                ACCOUNT_SECRET_KEYS
            )
        )
    }

    private val cloudService by lazy {
        CloudBackupService(tokenProvider = { accountService.getSession()?.token })
    }

    private val runtimeConfigStore by lazy {
        com.cheku.app.config.RuntimeConfigStore(
            SharedPreferencesStore(
                getApplication<Application>().getSharedPreferences(ACCOUNT_PREFS, Context.MODE_PRIVATE)
            )
        )
    }
    private val runtimeConfigService by lazy {
        com.cheku.app.config.RuntimeConfigService(runtimeConfigStore)
    }

    /** 远程功能开关 */
    private val _runtimeFeatures = MutableStateFlow(
        com.cheku.app.config.RuntimeConfig.Features(accounts = true, cloudBackup = true)
    )
    val runtimeFeatures: StateFlow<com.cheku.app.config.RuntimeConfig.Features> = _runtimeFeatures.asStateFlow()

    /** 待弹出的公告 */
    private val _notice = MutableStateFlow<Notice?>(null)
    val notice: StateFlow<Notice?> = _notice.asStateFlow()

    /** 一条待展示的公告 */
    data class Notice(val title: String, val body: String)

    init {
        viewModelScope.launch {
            runtimeConfigService.refresh(force = true)
            com.cheku.app.config.RuntimeConfigProvider.current()?.let { config ->
                _runtimeFeatures.value = config.features
                publishNoticeIfNew(config.noticeTitle, config.notice)
            }
        }
    }

    /** 仅当公告与上次弹过的不一致时才置为待弹 */
    private fun publishNoticeIfNew(noticeTitle: String, notice: String) {
        val title = noticeTitle.trim()
        val text = notice.trim()
        if (text.isEmpty()) {
            _notice.value = null
            return
        }
        val fingerprint = com.cheku.app.config.noticeFingerprint(title, text)
        if (fingerprint == runtimeConfigStore.lastSeenNoticeFingerprint()) return
        _notice.value = Notice(title, text)
    }

    fun dismissNotice() {
        _notice.value?.let {
            runtimeConfigStore.markNoticeSeen(
                com.cheku.app.config.noticeFingerprint(it.title, it.body)
            )
        }
        _notice.value = null
    }

    private val _accountEmail = MutableStateFlow(accountService.getEmail())
    val accountEmail: StateFlow<String?> = _accountEmail.asStateFlow()

    /** 非空 = 正在做某件事，UI 用它禁用按钮并显示进度文案 */
    private val _cloudBusy = MutableStateFlow<String?>(null)
    val cloudBusy: StateFlow<String?> = _cloudBusy.asStateFlow()

    /** 云端用量 */
    private val _cloudUsage = MutableStateFlow<CloudUsage?>(null)
    val cloudUsage: StateFlow<CloudUsage?> = _cloudUsage.asStateFlow()

    private var cloudUsageOwner: String? = null

    /** 上传 / 恢复的实时进度 */
    private val _cloudProgress = MutableStateFlow<CloudProgress?>(null)
    val cloudProgress: StateFlow<CloudProgress?> = _cloudProgress.asStateFlow()

    /** 注销宽限期到期时间 */
    private val _accountDeletionAt = MutableStateFlow<Long?>(null)
    val accountDeletionAt: StateFlow<Long?> = _accountDeletionAt.asStateFlow()

    private val _backupRateLimitKbps = MutableStateFlow<Int?>(null)
    val backupRateLimitKbps: StateFlow<Int?> = _backupRateLimitKbps.asStateFlow()

    /** 刷新传输策略 */
    fun refreshTransferPolicy() {
        viewModelScope.launch {
            _backupRateLimitKbps.value = runCatching { AppUpdateService.loadTransferPolicyKbps() }.getOrNull()
        }
    }

    /** 申请注销 */
    fun deleteAccount(password: String) = runCloudTask("注销账号") {
        val scheduledAt = accountService.deleteAccount(password)
        _accountDeletionAt.value = scheduledAt.takeIf { it > 0L } ?: (System.currentTimeMillis() + 24L * 60L * 60L * 1000L)
        "已提交注销：账号与云端数据保留 24 小时，期间可随时撤销"
    }

    /** 撤销注销 */
    fun cancelAccountDeletion() = runCloudTask("撤销注销") {
        accountService.cancelAccountDeletion()
        _accountDeletionAt.value = null
        refreshCloudUsage()
        "已撤销注销，账号恢复正常"
    }

    /** 刷新云端用量 */
    fun refreshCloudUsage(force: Boolean = false) {
        val session = accountService.getSession()
        if (session == null) {
            _cloudUsage.value = null
            cloudUsageOwner = null
            return
        }
        val owner = session.uid.ifBlank { session.email }
        if (!force && _cloudUsage.value != null && cloudUsageOwner == owner) return
        viewModelScope.launch { fetchCloudUsage(owner) }
    }

    /** 真正去拉一次用量并写进缓存 */
    private suspend fun fetchCloudUsage(owner: String) {
        try {
            val profile = accountService.loadMe()
            val meta = runCatching { cloudService.loadMeta() }.getOrNull()
            _cloudUsage.value = CloudUsage(
                usedBytes = meta?.sizeBytes ?: 0L,
                quotaBytes = profile.quotaBytes,
                backupAt = meta?.createdAt,
                appVersion = meta?.appVersion,
            )
            cloudUsageOwner = owner
            // 顺便同步"是否处于注销宽限期"：登录后与每次进页都能拿到最新状态
            _accountDeletionAt.value = profile.deletionScheduledAt
        } catch (error: Throwable) {
            android.util.Log.d("ChekuCloud", "刷新云端用量失败（不影响页面）：${error.message}")
        }
    }

    /** 当前会话的缓存归属键 */
    private fun currentUsageOwner(): String? =
        accountService.getSession()?.let { it.uid.ifBlank { it.email } }

    /** 上传成功后就地改写缓存的用量 */
    private fun applyUsageAfterUpload(bytes: Long) {
        val cached = _cloudUsage.value ?: return
        _cloudUsage.value = cached.copy(
            usedBytes = bytes,
            // 展示层只取日期（`backupAt.take(10)`），所以本地生成 ISO 串与服务端同形
            backupAt = java.time.Instant.now().toString(),
            appVersion = BuildConfig.VERSION_NAME,
        )
    }

    private val _registerCodeState = MutableStateFlow<RegisterCodeState>(RegisterCodeState.Idle)
    val registerCodeState: StateFlow<RegisterCodeState> = _registerCodeState.asStateFlow()

    private val _forgotState = MutableStateFlow<ForgotPasswordState>(ForgotPasswordState.Idle)
    val forgotState: StateFlow<ForgotPasswordState> = _forgotState.asStateFlow()

    fun clearRegisterCodeState() {
        _registerCodeState.value = RegisterCodeState.Idle
    }

    fun clearForgotState() {
        _forgotState.value = ForgotPasswordState.Idle
    }

    /** 发送注册验证码 */
    fun sendRegisterCode(email: String) {
        // 先回到 Idle，保证"成功"一定是一次可被界面观察到的状态变化（同邮箱重复发送时
        // data class 相等不会触发 StateFlow 发射，界面就会卡在等待）。
        _registerCodeState.value = RegisterCodeState.Idle
        runCodeTask(
            label = "发送验证码",
            target = _registerCodeState,
            action = { accountService.sendCode(email) },
            onSuccess = { RegisterCodeState.Sent(email) },
            toRateLimited = { RegisterCodeState.RateLimited(it) },
            toFailed = { RegisterCodeState.Failed(it) },
        )
    }

    /** 忘记密码 · 发送重置验证码 */
    fun sendResetCode(email: String) {
        _forgotState.value = ForgotPasswordState.Idle
        runCodeTask(
            label = "发送验证码",
            target = _forgotState,
            action = { accountService.sendResetCode(email) },
            onSuccess = { ForgotPasswordState.CodeSent(email) },
            toRateLimited = { ForgotPasswordState.RateLimited(it) },
            toFailed = { ForgotPasswordState.Failed(it) },
        )
    }

    /** 忘记密码 · 用验证码重置密码 */
    fun resetPassword(email: String, code: String, newPassword: String) {
        viewModelScope.launch {
            _cloudBusy.value = "重置密码"
            try {
                accountService.resetPassword(email, code, newPassword)
                _forgotState.value = ForgotPasswordState.PasswordReset
            } catch (error: CloudApiException) {
                _forgotState.value = ForgotPasswordState.Failed(error.error.userMessage)
            } catch (error: Throwable) {
                _forgotState.value = ForgotPasswordState.Failed(
                    error.message ?: "重置失败，请稍后重试"
                )
            } finally {
                _cloudBusy.value = null
            }
        }
    }

    /** 发码类任务的公共骨架 */
    private fun <T> runCodeTask(
        label: String,
        target: MutableStateFlow<T>,
        action: suspend () -> Unit,
        onSuccess: () -> T,
        toRateLimited: (Long) -> T,
        toFailed: (String) -> T,
    ) {
        viewModelScope.launch {
            _cloudBusy.value = label
            try {
                action()
                target.value = onSuccess()
            } catch (error: CloudApiException) {
                val apiError = error.error
                target.value = if (apiError.httpCode == 429) {
                    toRateLimited(retryAfterSecondsOf(apiError.rawBody))
                } else {
                    toFailed(apiError.userMessage)
                }
            } catch (error: Throwable) {
                target.value = toFailed(error.message ?: "操作失败，请稍后重试")
            } finally {
                _cloudBusy.value = null
            }
        }
    }

    /** 两步式注册第二步 */
    fun registerAccount(email: String, code: String, password: String) = runCloudTask("注册") {
        val session = accountService.register(email, code, password)
        _accountEmail.value = session.email
        // 强制拉一次：新账号的配额/注销状态必须立刻是它自己的（缓存归属随之换成新 uid）
        refreshCloudUsage(force = true)
        "已注册并登录：${session.email}"
    }

    fun loginAccount(email: String, password: String) = runCloudTask("登录") {
        val session = accountService.login(email, password)
        _accountEmail.value = session.email
        // 同上：换账号后配额与"待注销"状态必须立即是新账号的
        refreshCloudUsage(force = true)
        "已登录：${session.email}"
    }

    fun logoutAccount() = runCloudTask("退出登录") {
        accountService.logout()
        _accountEmail.value = null
        _cloudUsage.value = null
        cloudUsageOwner = null   // 缓存归属一并清掉：下次登录的是谁就重新拉谁的数字
        _cloudProgress.value = null
        _accountDeletionAt.value = null
        "已退出登录"
    }

    /** 上传到云端 */
    fun uploadToCloud(range: BackupExportRange) = runCloudTask("上传") {
        currentUsageOwner()?.let { fetchCloudUsage(it) }
        val cache = getApplication<Application>().cacheDir
        val uploadPackage = prepareUploadPackage(range, cache)
        val totalBytes = uploadPackage.bytes
        try {
            cloudService.uploadBackup(
                input = FileInputStream(uploadPackage.file),
                contentLength = totalBytes,
                sha256 = uploadPackage.sha256,
                onProgress = { done -> _cloudProgress.value = CloudProgress("上传", done, totalBytes) }
            )
            val stripped = if (uploadPackage.strippedEntries.isEmpty()) "（本机没有证书，无需剥离）"
            else "（已剥离 ${uploadPackage.strippedEntries.size} 项证书，证书不上云）"
            // 上传成功：缓存里的"已用"就地改成这次的体积（覆盖式备份），不再打网络
            applyUsageAfterUpload(totalBytes)
            "已上传到云端：${formatBackupSize(uploadPackage.bytes)} $stripped"
        } finally {
            uploadPackage.file.delete()
        }
    }

    /** 清空云端备份 */
    fun purgeCloudBackup() = runCloudTask("清空云端备份") {
        cloudService.purgeBackup()
        _cloudUsage.value = _cloudUsage.value?.copy(usedBytes = 0L, backupAt = null, appVersion = null)
        "已清空云端备份：账号保留，云端空间已释放"
    }

    /** 从云端恢复 */
    fun restoreFromCloud() = runCloudTask("从云端恢复") {
        val cache = getApplication<Application>().cacheDir
        val target = File(cache, "cloud-backup.zip")
        // 先取云端元信息拿到总长度，进度条才能给出百分比（取不到就退化为"已下载 N"，不阻断恢复）
        val totalBytes = runCatching { cloudService.loadMeta().sizeBytes }.getOrDefault(0L)
        try {
            FileOutputStream(target).use { out ->
                cloudService.downloadBackup(out) { done ->
                    _cloudProgress.value = CloudProgress("恢复", done, totalBytes)
                }
            }
            val preview = FileInputStream(target).use { parsePackageImport(it) }
                ?: return@runCloudTask "云端备份无法识别（不是新版导出包）"
            when (preview) {
                is PackageImportPreview.Failed -> "云端备份无法导入：${preview.reason}"
                is PackageImportPreview.Ready -> commitPackageImport(preview, cache)
            }
        } finally {
            target.delete()
        }
    }

    private fun runCloudTask(label: String, block: suspend () -> String) {
        viewModelScope.launch {
            _cloudBusy.value = label
            _cloudProgress.value = null
            try {
                showMessage(block())
                // 上传/恢复完成后刷新用量（备份大小、时间、配额占用都会变）
                refreshCloudUsage()
            } catch (error: CloudApiException) {
                showMessage("$label 失败：${error.error.userMessage}")
            } catch (error: Throwable) {
                showMessage("$label 失败：${error.message ?: error.javaClass.simpleName}")
            } finally {
                _cloudBusy.value = null
                _cloudProgress.value = null
            }
        }
    }

    /** 上传包 */
    data class UploadPackage(
        val file: File,
        val bytes: Long,
        val sha256: String,
        val strippedEntries: List<String>
    )

    suspend fun prepareUploadPackage(
        range: BackupExportRange,
        cacheDir: File,
        atMillis: Long = System.currentTimeMillis()
    ): UploadPackage {
        val full = File(cacheDir, "export-${BackupPackage.innerStampFor(atMillis)}.zip")
        val upload = File(cacheDir, BackupPackage.uploadFileName(atMillis))
        try {
            FileOutputStream(full).use { exportPackageTo(it, range, atMillis) }
            val report = FileInputStream(full).use { input ->
                FileOutputStream(upload).use { output -> BackupPackage.stripCertificates(input, output) }
            }
            val validation = FileInputStream(upload).use { BackupPackage.validateUploadPackage(it) }
            if (validation is BackupPackage.Validation.Failed) {
                throw IllegalStateException(
                    "上传包自检未通过：${validation.problem}（${validation.detail}）"
                )
            }
            val bytes = upload.length()
            val sha = FileInputStream(upload).use { BackupPackage.sha256Hex(it) }
            return UploadPackage(upload, bytes, sha, report.strippedEntries)
        } catch (error: Throwable) {
            // 中途失败不留半成品（上传包由调用方删，导出包由这里删）
            upload.delete()
            throw error
        } finally {
            full.delete()
        }
    }

    /** 证书原件的惰性源，新版导出包与单独证书包共用 */
    private fun certFiles(): List<BackupPackage.CertFile> =
        CertificateRepository(getApplication()).certificateSources().sources.map { source ->
            BackupPackage.CertFile(
                key = source.key,
                fileName = source.fileName.ifBlank { source.key },
                open = { source.open() }
            )
        }

    suspend fun exportReadableTo(out: OutputStream, format: ReadableExportFormat) {
        val data = ReadableExporter.LedgerData(
            vehicles = repository.getAllVehicles(),
            mileageRecords = repository.getAllMileageRecords(),
            energyRecords = repository.getAllEnergyRecords(),
            energySettlements = repository.getAllEnergySettlements(),
            maintenanceItems = repository.getAllMaintenanceItems(),
            maintenanceHistory = repository.getAllMaintenanceHistory(),
            performanceSessions = repository.getAllPerformanceSessions(),
            fitActivities = repository.getAllFitActivities()
        )
        when (format) {
            ReadableExportFormat.Csv -> ReadableExporter.exportCsv(data, out)
            ReadableExportFormat.Xls -> ReadableExporter.exportXls(data, out)
            ReadableExportFormat.Zip -> ReadableExporter.exportZip(data, readableCertificates(), out)
        }
    }

    private fun readableCertificates(): List<ReadableExporter.CertificateBinary> =
        CertificateRepository(getApplication()).certificateSources().sources.map { source ->
            ReadableExporter.CertificateBinary(source.fileName.ifBlank { source.key }) { source.open() }
        }

    fun parsePackageImport(input: InputStream): PackageImportPreview? {
        val manifestText = StringBuilder()
        val certsEntry = arrayOfNulls<String>(1)
        val allSheets = ArrayList<XlsSheetReader.SheetData>()
        var sawManifest = false
        /** 某个数据卷读/解析失败的说明 */
        var volumeFailure: String? = null

        ZipInputStream(BufferedInputStream(input)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name
                when {
                    name == BackupPackage.MANIFEST_ENTRY -> {
                        sawManifest = true
                        // manifest 很小，直接读文本
                        val buffer = ByteArray(8192)
                        while (true) {
                            val read = zip.read(buffer)
                            if (read < 0) break
                            manifestText.append(String(buffer, 0, read, Charsets.UTF_8))
                        }
                    }

                    name.startsWith(BackupPackage.CERTS_DIR) -> certsEntry[0] = name

                    name.startsWith(BackupPackage.DATA_DIR) && name.endsWith(".xls") -> {
                        try {
                            allSheets += XlsSheetReader.read(zip.readBytesLimited(XlsSheetReader.MAX_XLS_BYTES))
                        } catch (error: Exception) {
                            // 记下第一条失败，读到包尾后用 Failed 统一返回：保持"先校验后写入"的语义
                            // （这里一个字节都还没落库）。
                            if (volumeFailure == null) {
                                volumeFailure = "数据卷 $name 无法解析：${error.message ?: "格式错误"}"
                            }
                        }
                    }
                }
            }
        }
        if (!sawManifest) return null
        val manifest = BackupPackage.businessManifestFromJson(manifestText.toString()) ?: return null
        if (manifest.format != BackupPackage.FORMAT) return null
        volumeFailure?.let { return PackageImportPreview.Failed(it, manifest) }

        return when (val result = PackageImporter.parse(allSheets, manifest, DB_SCHEMA_VERSION)) {
            is PackageImporter.Result.Failed -> PackageImportPreview.Failed(result.reason, manifest)
            is PackageImporter.Result.Ok -> PackageImportPreview.Ready(
                data = result.data,
                warnings = result.warnings,
                ignoredSheets = result.ignoredSheets,
                manifest = manifest,
                certsEntryName = certsEntry[0]
            )
        }
    }

    private companion object {
        /** 账户会话的 SharedPreferences 名 */
        const val ACCOUNT_PREFS = "cheku_account"
    }

    /** 新版包的导入预览 */
    sealed interface PackageImportPreview {
        data class Ready(
            val data: ExportedData,
            val warnings: List<PackageImporter.Warning>,
            val ignoredSheets: List<String>,
            val manifest: BackupPackage.BusinessManifest,
            val certsEntryName: String?
        ) : PackageImportPreview

        data class Failed(val reason: String, val manifest: BackupPackage.BusinessManifest) : PackageImportPreview
    }

    suspend fun commitPackageImport(preview: PackageImportPreview.Ready, cacheDir: File): String {
        val safety = File(cacheDir, "preimport-${BackupPackage.innerStampFor(System.currentTimeMillis())}.zip")
        try {
            FileOutputStream(safety).use { exportPackageTo(it) }
        } catch (error: Throwable) {
            return "导入已中止：无法生成导入前的安全备份（${error.message ?: error.javaClass.simpleName}）"
        }

        return try {
            repository.importData(preview.data)
            safety.delete()
            buildString {
                append("已从新版包恢复：车辆 ${preview.data.vehicles.size}、里程 ${preview.data.mileageRecords.size}、")
                append("能源 ${preview.data.energyRecords.size}、保养 ${preview.data.maintenanceHistory.size}、")
                append("轨迹 ${preview.data.fitActivities.size}、性能 ${preview.data.performanceSessions.size}")
                if (preview.ignoredSheets.isNotEmpty()) {
                    append("；${preview.ignoredSheets.joinToString("、")} 是派生表，按设计不导入（会重算）")
                }
                if (preview.warnings.isNotEmpty()) {
                    append("；${preview.warnings.size} 处无法识别已回落默认值")
                }
            }
        } catch (error: Throwable) {
            "导入失败，本机数据可能已被部分修改；导入前的安全备份已保留：${safety.absolutePath}"
        }
    }

    /** 恢复新版包里的证书 */
    suspend fun restoreCertificatesFromPackage(openInput: () -> InputStream?): String? {
        val input = openInput() ?: return null
        ZipInputStream(BufferedInputStream(input)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: return null
                if (entry.name.startsWith(BackupPackage.CERTS_DIR) && !entry.isDirectory) {
                    return importArchivePackage { DataExporter.NonClosingInputStream(zip) }
                }
            }
        }
        @Suppress("UNREACHABLE_CODE")
        return null
    }

    /** 导入第一阶段 */
    suspend fun parseImport(input: InputStream): ImportPreview? {
        // 口令传 null：能走到这里的都是旧容器（v3/v4/裸 GZIP/ECB），它们的密钥是公开常量；
        // v5（口令派生）包目前没有任何产出路径，故不存在"有口令却没传"的真实场景。
        val parsed = DataExporter.parseBackup(null, input) ?: return null
        if (parsed.kind == CheBackupKind.Archive) return null
        return ImportPreview(parsed.business, parsed.manifest, parsed.archive)
    }

    /** 导入第二阶段 */
    suspend fun commitImport(preview: ImportPreview, archive: List<CertificateEntry>): String {
        repository.importData(preview.business)
        val failures = CertificateRepository(getApplication()).restoreCertificates(archive)
        selectedVehicleId.value = null
        val summary =
            "数据已导入（${preview.business.vehicles.size}辆车，${preview.business.mileageRecords.size}条里程记录）"
        if (failures.isEmpty()) return summary
        return "$summary；但有 ${failures.size} 份证书写入失败（${failures.joinToString("、") { it.key }}）"
    }

    private suspend fun verifyArchive(input: InputStream, preview: ImportPreview): Boolean {
        val (sha256, bytes) = DataExporter.sha256Of(input)
        return DataExporter.matchesArchive(sha256, bytes, preview.manifest)
    }

    /** 带配套证书包的导入 */
    suspend fun importWithArchive(preview: ImportPreview, openInput: () -> InputStream?): String {
        val verified = openInput()?.use { verifyArchive(it, preview) }
            ?: return "无法读取证书包"
        if (!verified) return "所选证书包与备份包不匹配，未导入任何数据"
        val businessMessage = commitImport(preview, emptyList())
        return "$businessMessage；${importArchivePackage(openInput)}"
    }

    suspend fun importAuto(openInput: () -> InputStream?): ImportStep {
        val packagePreview = openInput()?.use { parsePackageImport(it) }
        if (packagePreview != null) {
            return when (packagePreview) {
                is PackageImportPreview.Failed ->
                    ImportStep.Done("新版包无法导入：${packagePreview.reason}")

                is PackageImportPreview.Ready -> ImportStep.NeedPackageCommit(packagePreview)
            }
        }
        val kind = openInput()?.use { DataExporter.sniffBackupKind(null, it) }
            ?: return ImportStep.Done("无法识别该文件：可能已损坏，或不是本 App 的备份")
        if (kind != CheBackupKind.Archive) {
            val preview = openInput()?.use { parseImport(it) }
                ?: return ImportStep.Done("无法识别该文件：可能已损坏")
            return ImportStep.NeedBusinessCommit(preview)
        }
        return ImportStep.Done(importArchivePackage(openInput))
    }

    /** 证书包的完整导入 */
    private suspend fun importArchivePackage(openInput: () -> InputStream?): String {
        val staging = CertificateRepository(getApplication()).beginStaging()
        val parsed = try {
            openInput()?.use { DataExporter.parseArchiveStaged(null, it, staging.sink()) }
        } catch (error: Throwable) {
            staging.discard()
            throw error
        }
        if (parsed == null) {
            staging.discard()
            return "证书包内容无法解析（文件可能已损坏）"
        }
        val failures = staging.commit()
        val details = buildList {
            add("已恢复 ${parsed.certificates.size} 份证书")
            if (parsed.failures.isNotEmpty()) add("${parsed.failures.size} 份解不开（已跳过）")
            if (failures.isNotEmpty()) add("${failures.size} 份写入失败")
        }
        return details.joinToString("；")
    }

    /** 导入第一跳的结果 */
    sealed interface ImportStep {
        /** 业务包 */
        data class NeedBusinessCommit(val preview: ImportPreview) : ImportStep

        data class NeedPackageCommit(val preview: PackageImportPreview.Ready) : ImportStep

        data class Done(val message: String) : ImportStep
    }

    /** 导入第一阶段的产物 */
    data class ImportPreview(
        val business: ExportedData,
        val manifest: BackupManifest?,
        val inlineArchive: List<CertificateEntry>,
    ) {
        val needsArchiveFile: Boolean get() = manifest?.hasArchive == true
    }

    fun importXiongFuel(vehicleId: String, bytes: ByteArray) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val parsed = XiongFuelImporter.parse(bytes)
                var imported = 0
                parsed.rows.forEach { row ->
                    if (runCatching { repository.addEnergyRecord(vehicleId, row.input, false, row.createdAt) }.isSuccess) imported++
                }
                showMessage("小熊油耗导入完成：$imported 条（识别 ${parsed.recognized} 条；不兼容车辆类型的记录会跳过）")
            } catch (error: Throwable) {
                showMessage("小熊油耗导入失败：${error.message ?: error.javaClass.simpleName}")
            }
        }
    }

    fun updateMaintenanceHistory(id: String, mileage: String, timeText: String, partNumber: String, partsCost: String, laborCost: String, note: String) {
        val serviceMileage = mileage.toDoubleOrNull() ?: return show("请输入有效里程")
        val serviceAt = parseRecordDateTime(timeText) ?: return show("请输入有效时间：yyyy-MM-dd HH:mm")
        val parts = partsCost.toDoubleOrNull() ?: 0.0
        val labor = laborCost.toDoubleOrNull() ?: 0.0
        viewModelScope.launchSaving {
            repository.updateMaintenanceHistory(id, serviceMileage, serviceAt, note, partNumber, parts, labor)
            message.value = "维护记录已更新"
        }
    }

    fun updateMaintenanceItem(id: String, name: String, intervalKmText: String, intervalDaysText: String) {
        if (name.isBlank()) return show("请输入项目名称")
        val intervalKm = intervalKmText.toIntOrNull() ?: return show("请输入有效里程周期")
        val intervalDays = intervalDaysText.toIntOrNull() ?: return show("请输入有效天数周期")
        viewModelScope.launchSaving {
            repository.updateMaintenanceItem(id, name, intervalKm, intervalDays)
            message.value = "保养项目已更新"
        }
    }

    fun deleteMaintenanceItem(id: String) {
        viewModelScope.launchSaving {
            repository.deleteMaintenanceItem(id)
            message.value = "保养项目已删除"
        }
    }

    fun completeInsurance(itemId: String, timeText: String, expiresText: String, amountText: String, note: String) {
        val serviceAt = parseRecordDateTime(timeText) ?: return show("请输入有效时间：yyyy-MM-dd HH:mm")
        val expiresAt = parseRecordDateTime(expiresText) ?: return show("请输入有效到期时间：yyyy-MM-dd HH:mm")
        if (expiresAt <= serviceAt) return show("到期时间必须晚于缴费时间")
        val amount = amountText.toDoubleOrNull()?.takeIf { it >= 0.0 } ?: return show("请输入有效金额")
        viewModelScope.launchSaving {
            repository.completeInsurance(itemId, serviceAt, expiresAt, amount, note)
            message.value = "续保记录已保存"
        }
    }

    fun addRepairInspection(
        itemId: String, name: String, timeText: String, mileageText: String,
        amountText: String, laborText: String, note: String
    ) {
        if (name.isBlank()) return show("请输入检修名称")
        val serviceAt = parseRecordDateTime(timeText) ?: return show("请输入有效时间：yyyy-MM-dd HH:mm")
        val mileage = mileageText.toDoubleOrNull()?.takeIf { it >= 0.0 } ?: return show("请输入有效里程")
        val amount = amountText.toDoubleOrNull()?.takeIf { it >= 0.0 } ?: return show("请输入有效金额")
        val labor = laborText.toDoubleOrNull()?.takeIf { it >= 0.0 } ?: return show("请输入有效工时费")
        viewModelScope.launchSaving {
            repository.addRepairInspection(itemId, name, serviceAt, mileage, amount, labor, note)
            message.value = "检修记录已保存"
        }
    }

    fun updateSpecialMaintenanceHistory(
        id: String, name: String, timeText: String, expiresText: String, mileageText: String,
        amountText: String, laborText: String, note: String
    ) {
        val serviceAt = parseRecordDateTime(timeText) ?: return show("请输入有效时间：yyyy-MM-dd HH:mm")
        val expiresAt = expiresText.takeIf { it.isNotBlank() }?.let(::parseRecordDateTime)
        if (expiresText.isNotBlank() && expiresAt == null) return show("请输入有效到期时间：yyyy-MM-dd HH:mm")
        val mileage = mileageText.toDoubleOrNull()?.takeIf { it >= 0.0 } ?: return show("请输入有效里程")
        val amount = amountText.toDoubleOrNull()?.takeIf { it >= 0.0 } ?: return show("请输入有效金额")
        val labor = laborText.toDoubleOrNull()?.takeIf { it >= 0.0 } ?: return show("请输入有效工时费")
        viewModelScope.launchSaving {
            repository.updateSpecialMaintenanceHistory(id, name, serviceAt, mileage, amount, labor, note, expiresAt)
            message.value = "记录已更新"
        }
    }

    fun deleteMaintenanceHistory(id: String) {
        viewModelScope.launchSaving {
            repository.deleteMaintenanceHistory(id)
            message.value = "保养记录已删除"
        }
    }

    fun deleteRecord(recordId: String, type: RecentRecordType) {
        viewModelScope.launchSaving {
            when (type) {
                RecentRecordType.Mileage -> repository.deleteMileageRecord(recordId)
                RecentRecordType.Fuel, RecentRecordType.Charge -> repository.deleteEnergyRecord(recordId)
            }
            message.value = "记录已删除"
        }
    }

    fun clearAllData() {
        viewModelScope.launchSaving {
            repository.clearAllData()
            selectedVehicleId.value = null
            message.value = "所有数据已清除"
        }
    }

    fun updateMileageRecord(id: String, mileageText: String, timeText: String, note: String, roadFeeText: String) {
        val mileage = mileageText.toDoubleOrNull() ?: return show("请输入有效里程")
        val roadFee = roadFeeText.ifBlank { "0" }.toDoubleOrNull() ?: return show("请输入有效路费")
        val createdAt = parseRecordDateTime(timeText) ?: return show("请输入有效时间：yyyy-MM-dd HH:mm")
        viewModelScope.launchSaving {
            repository.updateMileageRecord(id, mileage, createdAt, note, roadFee)
            message.value = "里程已更新"
        }
    }

    fun updateEnergyRecord(id: String, timeText: String, form: EnergyRecordFormInput) {
        val createdAt = parseRecordDateTime(timeText) ?: return show("请输入有效时间：yyyy-MM-dd HH:mm")
        val input = parseEnergyInput(form) ?: return
        viewModelScope.launchSaving {
            val result = repository.updateEnergyRecord(id, input, createdAt)
            message.value = "记录已更新，${energyResultMessage(form.type, result, form.chargeLimitSoc)}"
        }
    }

    private fun parseChargeLimit(energyType: EnergyType, text: String): Int? {
        if (!energyType.supportsCharging) return 0
        val value = text.toIntOrNull()
        if (value == null || value !in 1..100) {
            show("请输入 1-100 的充电上限")
            return null
        }
        return value
    }

    private fun parseEnergyInput(form: EnergyRecordFormInput): EnergyRecordInput? {
        val mileage = form.mileage.toDoubleOrNull() ?: return showNull("请输入有效里程")
        val amount = form.amount.toDoubleOrNull()?.takeIf { it > 0.0 }
            ?: return showNull("请输入大于 0 的补能量")
        val orderAmount = form.orderAmount.toDoubleOrNull()?.takeIf { it >= 0.0 }
            ?: return showNull("请输入有效订单金额")
        val paidAmount = form.paidAmount.toDoubleOrNull()?.takeIf { it >= 0.0 }
            ?: return showNull("请输入有效实付金额")
        val chargeLimit = if (form.type == EnergyRecordType.Charge) {
            form.chargeLimitSoc.toIntOrNull()?.takeIf { it in 1..100 }
                ?: return showNull("请输入 1-100 的充电上限")
        } else null
        return EnergyRecordInput(
            type = form.type,
            mileage = mileage,
            amount = amount,
            orderAmount = orderAmount,
            paidAmount = paidAmount,
            isFull = form.isFull,
            missedPrevious = form.missedPrevious,
            fuelGrade = form.fuelGrade,
            unitPrice = form.unitPrice.toDoubleOrNull() ?: 0.0,
            chargeMethod = form.chargeMethod,
            electricityPrice = form.electricityPrice.toDoubleOrNull() ?: 0.0,
            serviceFee = form.serviceFee.toDoubleOrNull() ?: 0.0,
            chargeLimitSocSnapshot = chargeLimit,
            note = form.note
        )
    }

    private fun <T> showNull(text: String): T? {
        show(text)
        return null
    }

    private fun parseRecordDateTime(text: String): Long? {
        if (text.isBlank()) return null
        return runCatching {
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).apply {
                isLenient = false
            }.parse(text)?.time
        }.getOrNull()
    }

    private fun show(text: String) {
        message.value = text
    }

    fun showMessage(text: String) { message.value = text }

    private fun kotlinx.coroutines.CoroutineScope.launchSaving(block: suspend () -> Unit) {
        launch {
            isSaving.value = true
            runCatching { block() }
                .onFailure { message.value = it.message ?: "操作失败" }
            isSaving.value = false
        }
    }
}

private fun MileageRecordEntity.toRecent(vehicle: VehicleEntity?): RecentRecordItem =
    RecentRecordItem(
        title = "更新里程",
        subtitle = vehicle?.name ?: "未知车辆",
        timeText = formatDate(createdAt),
        trailing = "${formatNumber(mileage)} km",
        detailLines = listOf(
            "车辆：${vehicle?.name ?: "未知车辆"}",
            "时间：${formatDate(createdAt)}",
            "里程：$mileage km",
            note.takeIf { it.isNotBlank() }?.let { "备注：$it" } ?: "备注：无"
        ),
        createdAt = createdAt,
        type = RecentRecordType.Mileage,
        recordId = id,
        vehicleId = vehicleId,
        mileageValue = mileage,
        note = note,
        roadFeeAmount = roadFeeAmount,
        vehicleName = vehicle?.name ?: "未知车辆"
    )

private fun EnergyRecordEntity.toRecent(
    vehicle: VehicleEntity?,
    unit: DistanceUnit,
    settlement: EnergySettlementEntity?
): RecentRecordItem {
    val methodText = when (type) {
        EnergyRecordType.Fuel -> fuelGrade.takeIf { it.isNotBlank() }?.let { "#$it" }
        EnergyRecordType.Charge -> chargeMethod.takeIf { it.isNotBlank() }
    }
    val copy = type.uiCopy()
    val consumptionText = settlement
        ?.takeIf { it.status == SettlementStatus.Valid }
        ?.consumptionPer100Km
        ?.let { formatConsumption(it, unit, type) }
    val settlementText = energySettlementStatusText(
        type = type,
        status = settlement?.status,
        consumptionText = consumptionText.orEmpty(),
        invalidReason = settlement?.invalidReason.orEmpty()
    )
    val discount = orderAmount - paidAmount
    return RecentRecordItem(
        title = type.recordTitle(methodText.orEmpty()),
        subtitle = "${vehicle?.name ?: "未知车辆"} · ${formatNumber(mileage)} km",
        timeText = formatDate(createdAt),
        trailing = listOfNotNull(
            "${formatMoney(amount)}${type.unit}",
            "实付 ¥${formatMoney(paidAmount)}",
            consumptionText
        ).joinToString(" · "),
        detailLines = listOfNotNull(
            "车辆：${vehicle?.name ?: "未知车辆"}",
            "时间：${formatDate(createdAt)}",
            "里程：$mileage km",
            "${copy.amountLabel}：${formatMoney(amount)} ${copy.amountUnit}",
            "订单金额：${formatMoney(orderAmount)} 元",
            "实付金额：${formatMoney(paidAmount)} 元",
            discount.takeIf { it > 0.005 }?.let { "优惠金额：${formatMoney(it)} 元" },
            methodText?.let { "${copy.methodLabel}：$it" },
            "结算状态：$settlementText",
            note.takeIf { it.isNotBlank() }?.let { "备注：$it" }
        ),
        createdAt = createdAt,
        type = when (type) {
            EnergyRecordType.Fuel -> RecentRecordType.Fuel
            EnergyRecordType.Charge -> RecentRecordType.Charge
        },
        recordId = id,
        vehicleId = vehicleId,
        vehicleName = vehicle?.name ?: "未知车辆",
        mileageValue = mileage,
        note = note,
        amountValue = amount,
        orderAmountValue = orderAmount,
        paidAmountValue = paidAmount,
        isFull = isFull,
        missedPrevious = missedPrevious,
        fuelGrade = fuelGrade,
        unitPriceValue = unitPrice,
        chargeMethod = chargeMethod,
        electricityPriceValue = electricityPrice,
        serviceFeeValue = serviceFee,
        chargeLimitSoc = chargeLimitSocSnapshot,
        methodValue = methodText.orEmpty(),
        amountLabel = copy.amountLabel,
        amountUnit = copy.amountUnit,
        statusText = settlementText
    )
}

internal fun formatConsumption(valuePer100Km: Double, unit: DistanceUnit, type: EnergyRecordType): String =
    when (unit) {
        DistanceUnit.KmPer100 -> when (type) {
            EnergyRecordType.Fuel -> "${valuePer100Km.trimOne()} L/100km"
            EnergyRecordType.Charge -> "${valuePer100Km.trimOne()} kWh/100km"
        }
        DistanceUnit.KmPerL -> {
            val converted = if (valuePer100Km <= 0.0) 0.0 else 100.0 / valuePer100Km
            when (type) {
                EnergyRecordType.Fuel -> "${converted.trimOne()} km/L"
                EnergyRecordType.Charge -> "${converted.trimOne()} km/kWh"
            }
        }
    }

private fun energySummaryText(
    type: EnergyRecordType,
    settlements: List<EnergySettlementEntity>,
    records: List<EnergyRecordEntity>,
    unit: DistanceUnit
): String {
    val matching = settlements.filter { it.type == type }
    val latestValid = matching
        .filter { it.status == SettlementStatus.Valid && it.consumptionPer100Km != null }
        .maxByOrNull { it.createdAt }
    if (latestValid != null) {
        return formatConsumption(latestValid.consumptionPer100Km!!, unit, type)
    }
    val hasWaiting = matching.any { it.status == SettlementStatus.Waiting }
    if (hasWaiting) {
        return type.uiCopy().nextPending
    }
    val hasRecords = records.any { it.type == type }
    return if (hasRecords) {
        type.uiCopy().firstPending
    } else {
        if (type == EnergyRecordType.Fuel) "暂无加油记录" else "暂无充电记录"
    }
}

private fun energyResultMessage(
    type: EnergyRecordType,
    result: EnergySaveResult,
    chargeLimit: String
): String {
    val action = type.uiCopy().action
    return when (result) {
    EnergySaveResult.BaselineCreated -> if (type == EnergyRecordType.Fuel) {
        "${action}记录已保存，已建立油耗结算基准"
    } else {
        "${action}记录已保存，已按 ${chargeLimit}% 建立电耗结算基准"
    }
    is EnergySaveResult.BaselineReset -> {
        "${action}记录已保存，${result.reason}，已重新建立结算基准"
    }
    EnergySaveResult.WaitingForFull -> if (type == EnergyRecordType.Fuel) {
        "${action}记录已保存，待下次加满"
    } else {
        "${action}记录已保存，待下次充满"
    }
    is EnergySaveResult.Settled -> {
        "${action}记录已保存，已结算 · ${formatConsumption(result.consumptionPer100Km, DistanceUnit.KmPer100, type)}"
    }
    is EnergySaveResult.Invalid -> "${action}记录已保存，结算无效 · ${result.reason}"
    }
}

private fun formatDate(value: Long): String =
    SimpleDateFormat("MM月dd日 HH:mm", Locale.CHINA).format(Date(value))

private fun Double.trimOne(): String =
    if (this % 1.0 == 0.0) toInt().toString() else String.format(Locale.CHINA, "%.1f", this)

private fun formatNumber(value: Int): String = formatNumber(value.toDouble())

private fun formatNumber(value: Double): String =
    String.format(Locale.CHINA, "%,.2f", value)

private fun formatMoney(value: Double): String =
    String.format(Locale.CHINA, "%.2f", value)
