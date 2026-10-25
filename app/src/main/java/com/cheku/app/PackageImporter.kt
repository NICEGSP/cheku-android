package com.cheku.app

import com.cheku.app.data.EnergyRecordEntity
import com.cheku.app.data.EnergyType
import com.cheku.app.data.FitActivityEntity
import com.cheku.app.data.FitActivityStatus
import com.cheku.app.data.FitSampleEntity
import com.cheku.app.data.FitTimerEventEntity
import com.cheku.app.data.FitTimerEventType
import com.cheku.app.data.JerkSeverity
import com.cheku.app.data.MaintenanceHistoryEntity
import com.cheku.app.data.MaintenanceItemEntity
import com.cheku.app.data.MaintenanceItemType
import com.cheku.app.data.MileageRecordEntity
import com.cheku.app.data.PerformanceJerkEntity
import com.cheku.app.data.PerformanceNoiseEntity
import com.cheku.app.data.PerformanceQuality
import com.cheku.app.data.PerformanceSampleEntity
import com.cheku.app.data.PerformanceSessionEntity
import com.cheku.app.data.PerformanceSplitEntity
import com.cheku.app.data.PerformanceStatus
import com.cheku.app.data.PerformanceTestType
import com.cheku.app.data.TransmissionType
import com.cheku.app.data.VehicleCategory
import com.cheku.app.data.VehicleEntity
import java.text.SimpleDateFormat
import java.util.Locale

object PackageImporter {
    data class Warning(
        val sheet: String,
        val row: Int,
        val column: String,
        val raw: String,
        val note: String
    )

    sealed interface Result {
        data class Ok(
            val data: ExportedData,
            val warnings: List<Warning>,
            val ignoredSheets: List<String>
        ) : Result

        data class Failed(val reason: String) : Result
    }

    /** 派生表 */
    private val DERIVED_SHEETS = listOf("能源结算", "费用汇总")

    private val REQUIRED_COLUMNS: Map<String, List<String>> = mapOf(
        "车辆" to listOf("车辆ID", "名称", "类别", "能源类型", "变速箱", "更新时间"),
        "里程记录" to listOf("里程ID", "车辆ID", "里程(km)", "记录时间"),
        "能源记录" to listOf("能源ID", "车辆ID", "类型", "记录时间"),
        "保养项目" to listOf("项目ID", "车辆ID", "名称", "上次保养时间"),
        "保养记录" to listOf("记录ID", "车辆ID", "项目ID", "保养时间"),
        "检修记录" to listOf("记录ID", "车辆ID", "项目ID", "保养时间"),
        "性能记录" to listOf("会话ID", "名称", "测试类型", "开始时间"),
        "轨迹记录" to listOf("轨迹ID", "车辆ID", "名称", "开始时间"),
        "fit_samples" to listOf("活动ID", "时间"),
        "fit_events" to listOf("活动ID", "时间", "类型"),
        "perf_samples" to listOf("会话ID", "纳秒时间"),
        "perf_splits" to listOf("ID", "会话ID", "用时(s)"),
        "perf_noise" to listOf("会话ID", "时间"),
        "perf_jerks" to listOf("ID", "会话ID", "时间", "严重度")
    )

    fun parse(
        sheets: List<XlsSheetReader.SheetData>,
        manifest: BackupPackage.BusinessManifest,
        localDbSchema: Int
    ): Result {
        if (manifest.dbSchema > localDbSchema) {
            return Result.Failed(
                "该备份来自更新的 App（数据版本 ${manifest.dbSchema}，本机支持到 $localDbSchema），请先升级 App"
            )
        }

        val byName = mergeSlices(sheets).associateBy { it.name }
        REQUIRED_COLUMNS.forEach { (sheetName, required) ->
            val found = byName[sheetName] ?: return@forEach
            val missing = required.filter { found.columnOf(it) < 0 }
            if (missing.isNotEmpty()) {
                return Result.Failed("表「$sheetName」缺少必需列：${missing.joinToString("、")}")
            }
        }

        val warnings = ArrayList<Warning>()
        val time = TimeParser()

        fun warn(sheet: XlsSheetReader.SheetData, row: Int, column: String, raw: String, note: String) {
            // 只保留前 200 条，避免一个损坏的包产生几十万条告警把 UI 拖死
            if (warnings.size < 200) {
                warnings += Warning(sheet.name, row, column, raw.take(40), note)
            }
        }

        val vehicles = sheet(byName, "车辆").mapRows(time) { row, index, sheet, t ->
            VehicleEntity(
                id = sheet.cell(row, "车辆ID"),
                name = sheet.cell(row, "名称"),
                model = sheet.cell(row, "型号"),
                plate = sheet.cell(row, "车牌"),
                category = enumOf<VehicleCategory>(sheet.cell(row, "类别")) { warn(sheet, index, "类别", it, "未知类别，已回落") },
                energyType = enumOf<EnergyType>(sheet.cell(row, "能源类型")) { warn(sheet, index, "能源类型", it, "未知能源类型，已回落") },
                transmissionType = enumOf<TransmissionType>(sheet.cell(row, "变速箱")) { warn(sheet, index, "变速箱", it, "未知变速箱，已回落") },
                fuelTankCapacityLiters = sheet.cell(row, "油箱容量(L)").toDoubleOrNull(),
                batteryCapacityKwh = sheet.cell(row, "电池容量(kWh)").toDoubleOrNull(),
                defaultChargeLimitSoc = sheet.cell(row, "充电上限SOC(%)").toIntOrNull(),
                currentMileage = sheet.cell(row, "当前里程(km)").toDoubleOrNull() ?: 0.0,
                updatedAt = t.parse(sheet.cell(row, "更新时间")) ?: 0L,
                manufacturingDate = t.parse(sheet.cell(row, "出厂日期")),
                purchasePrice = sheet.cell(row, "购车价(元)").toDoubleOrNull() ?: 0.0,
                purchaseTax = sheet.cell(row, "购置税(元)").toDoubleOrNull() ?: 0.0
            )
        }

        val mileage = sheet(byName, "里程记录").mapRows(time) { row, _, sheet, t ->
            MileageRecordEntity(
                id = sheet.cell(row, "里程ID"),
                vehicleId = sheet.cell(row, "车辆ID"),
                mileage = sheet.cell(row, "里程(km)").toDoubleOrNull() ?: 0.0,
                note = sheet.cell(row, "备注"),
                createdAt = t.parse(sheet.cell(row, "记录时间")) ?: 0L,
                roadFeeAmount = sheet.cell(row, "过路费(元)").toDoubleOrNull() ?: 0.0
            )
        }

        val energy = sheet(byName, "能源记录").mapRows(time) { row, index, sheet, t ->
            EnergyRecordEntity(
                id = sheet.cell(row, "能源ID"),
                vehicleId = sheet.cell(row, "车辆ID"),
                type = enumOf<com.cheku.app.data.EnergyRecordType>(sheet.cell(row, "类型")) {
                    warn(sheet, index, "类型", it, "未知能源记录类型，已回落")
                },
                mileage = sheet.cell(row, "里程(km)").toDoubleOrNull() ?: 0.0,
                amount = sheet.cell(row, "金额(元)").toDoubleOrNull() ?: 0.0,
                orderAmount = sheet.cell(row, "订单金额(元)").toDoubleOrNull() ?: 0.0,
                paidAmount = sheet.cell(row, "实付金额(元)").toDoubleOrNull() ?: 0.0,
                isFull = sheet.cell(row, "是否满充") == "是",
                missedPrevious = sheet.cell(row, "是否漏记上次") == "是",
                fuelGrade = sheet.cell(row, "燃油标号"),
                unitPrice = sheet.cell(row, "单价(元)").toDoubleOrNull() ?: 0.0,
                chargeMethod = sheet.cell(row, "充电方式"),
                electricityPrice = sheet.cell(row, "电价(元/kWh)").toDoubleOrNull() ?: 0.0,
                serviceFee = sheet.cell(row, "服务费(元)").toDoubleOrNull() ?: 0.0,
                chargeLimitSocSnapshot = sheet.cell(row, "充电上限SOC(%)").toIntOrNull(),
                note = sheet.cell(row, "备注"),
                createdAt = t.parse(sheet.cell(row, "记录时间")) ?: 0L
            )
        }

        val items = sheet(byName, "保养项目").mapRows(time) { row, index, sheet, t ->
            val lastAt = t.parse(sheet.cell(row, "上次保养时间")) ?: 0L
            MaintenanceItemEntity(
                id = sheet.cell(row, "项目ID"),
                vehicleId = sheet.cell(row, "车辆ID"),
                name = sheet.cell(row, "名称"),
                intervalKm = sheet.cell(row, "周期里程(km)").toIntOrNull() ?: 0,
                intervalDays = sheet.cell(row, "周期天数").toIntOrNull() ?: 0,
                lastServiceMileage = sheet.cell(row, "上次保养里程(km)").toIntOrNull() ?: 0,
                lastServiceAt = lastAt,
                updatedAt = lastAt,
                lastPartNumber = sheet.cell(row, "上次零件号"),
                itemType = enumOf<MaintenanceItemType>(sheet.cell(row, "项目类型")) {
                    warn(sheet, index, "项目类型", it, "未知项目类型，已回落")
                },
                baselineAt = t.parse(sheet.cell(row, "基准时间")) ?: lastAt
            )
        }

        val history = listOf("保养记录", "检修记录").flatMap { name ->
            sheet(byName, name).mapRows(time) { row, _, sheet, t ->
            MaintenanceHistoryEntity(
                id = sheet.cell(row, "记录ID"),
                vehicleId = sheet.cell(row, "车辆ID"),
                itemId = sheet.cell(row, "项目ID"),
                itemName = sheet.cell(row, "项目名称"),
                serviceMileage = sheet.cell(row, "保养里程(km)").toDoubleOrNull() ?: 0.0,
                serviceAt = t.parse(sheet.cell(row, "保养时间")) ?: 0L,
                note = sheet.cell(row, "备注"),
                laborCost = sheet.cell(row, "工时费(元)").toDoubleOrNull() ?: 0.0,
                partsCost = sheet.cell(row, "零件费(元)").toDoubleOrNull() ?: 0.0,
                partNumber = sheet.cell(row, "零件号"),
                recordName = sheet.cell(row, "记录名称").ifBlank { sheet.cell(row, "项目名称") },
                insuranceAmount = 0.0,
                repairAmount = 0.0,
                expiresAt = t.parse(sheet.cell(row, "过期时间"))
            )
            }
        }

        val sessions = sheet(byName, "性能记录").mapRows(time) { row, index, sheet, t ->
            val startedAt = t.parse(sheet.cell(row, "开始时间")) ?: 0L
            PerformanceSessionEntity(
                id = sheet.cell(row, "会话ID"),
                vehicleId = sheet.cell(row, "车辆ID").takeIf { it.isNotBlank() },
                name = sheet.cell(row, "名称"),
                testType = enumOf<PerformanceTestType>(sheet.cell(row, "测试类型")) {
                    warn(sheet, index, "测试类型", it, "未知测试类型，已回落")
                },
                status = enumOf<PerformanceStatus>(sheet.cell(row, "状态")) {
                    warn(sheet, index, "状态", it, "未知状态，已回落")
                },
                startedAt = startedAt,
                endedAt = t.parse(sheet.cell(row, "结束时间")),
                resultSeconds = sheet.cell(row, "结果(秒)").toDoubleOrNull(),
                resultDistanceMeters = sheet.cell(row, "结果距离(m)").toDoubleOrNull(),
                maxAccelerationMps2 = sheet.cell(row, "最大加速度(m/s²)").toDoubleOrNull() ?: 0.0,
                averageAccelerationMps2 = sheet.cell(row, "平均加速度(m/s²)").toDoubleOrNull() ?: 0.0,
                averageNoiseDbA = sheet.cell(row, "平均噪音(dBA)").toDoubleOrNull(),
                maxNoiseDbA = sheet.cell(row, "最大噪音(dBA)").toDoubleOrNull(),
                quality = enumOf<PerformanceQuality>(sheet.cell(row, "质量")) {
                    warn(sheet, index, "质量", it, "未知质量标记，已回落")
                },
                invalidReason = sheet.cell(row, "无效原因"),
                microphoneCalibrated = sheet.cell(row, "麦克风校准") == "是",
                createdAt = startedAt,
                updatedAt = startedAt
            )
        }

        val activities = sheet(byName, "轨迹记录").mapRows(time) { row, index, sheet, t ->
            val startedAt = t.parse(sheet.cell(row, "开始时间")) ?: 0L
            FitActivityEntity(
                id = sheet.cell(row, "轨迹ID"),
                vehicleId = sheet.cell(row, "车辆ID"),
                name = sheet.cell(row, "名称"),
                status = enumOf<FitActivityStatus>(sheet.cell(row, "状态")) {
                    warn(sheet, index, "状态", it, "未知轨迹状态，已回落")
                },
                startedAt = startedAt,
                endedAt = t.parse(sheet.cell(row, "结束时间")),
                pausedDurationMs = sheet.cell(row, "暂停时长(ms)").toLongOrNull() ?: 0L,
                distanceMeters = sheet.cell(row, "距离(m)").toDoubleOrNull() ?: 0.0,
                maxSpeedMps = sheet.cell(row, "最大速度(m/s)").toDoubleOrNull() ?: 0.0,
                autoPauseEnabled = sheet.cell(row, "自动暂停") == "是",
                lastSampleAt = t.parse(sheet.cell(row, "最后采样时间")),
                exportedAt = t.parse(sheet.cell(row, "导出时间")),
                createdAt = startedAt,
                updatedAt = startedAt
            )
        }

        val fitSamples = sheet(byName, "fit_samples").mapRows(time) { row, _, sheet, t ->
            FitSampleEntity(
                activityId = sheet.cell(row, "活动ID"),
                timestamp = t.parse(sheet.cell(row, "时间")) ?: 0L,
                latitude = sheet.cell(row, "纬度").toDoubleOrNull() ?: 0.0,
                longitude = sheet.cell(row, "经度").toDoubleOrNull() ?: 0.0,
                speedMps = sheet.cell(row, "速度(m/s)").toDoubleOrNull() ?: 0.0,
                distanceMeters = sheet.cell(row, "距离(m)").toDoubleOrNull() ?: 0.0,
                altitudeMeters = sheet.cell(row, "海拔(m)").toDoubleOrNull(),
                accuracyMeters = sheet.cell(row, "精度(m)").toFloatOrNull() ?: 0f,
                bearingDegrees = sheet.cell(row, "方位角").toFloatOrNull(),
                heartRateBpm = sheet.cell(row, "心率").toIntOrNull(),
                cadenceRpm = sheet.cell(row, "踏频").toIntOrNull(),
                powerWatts = sheet.cell(row, "功率").toIntOrNull(),
                motionMagnitude = sheet.cell(row, "运动量").toFloatOrNull()
            )
        }

        val fitEvents = sheet(byName, "fit_events").mapRows(time) { row, index, sheet, t ->
            FitTimerEventEntity(
                activityId = sheet.cell(row, "活动ID"),
                timestamp = t.parse(sheet.cell(row, "时间")) ?: 0L,
                type = enumOf<FitTimerEventType>(sheet.cell(row, "类型")) {
                    warn(sheet, index, "类型", it, "未知事件类型，已回落")
                }
            )
        }

        val perfSamples = sheet(byName, "perf_samples").mapRows(time) { row, _, sheet, t ->
            PerformanceSampleEntity(
                sessionId = sheet.cell(row, "会话ID"),
                timestampNanos = sheet.cell(row, "纳秒时间").toLongOrNull() ?: 0L,
                wallTime = t.parse(sheet.cell(row, "墙钟时间")) ?: 0L,
                gpsSpeedMps = sheet.cell(row, "GPS速度").toDoubleOrNull(),
                fusedSpeedMps = sheet.cell(row, "融合速度").toDoubleOrNull() ?: 0.0,
                distanceMeters = sheet.cell(row, "距离(m)").toDoubleOrNull() ?: 0.0,
                latitude = sheet.cell(row, "纬度").toDoubleOrNull(),
                longitude = sheet.cell(row, "经度").toDoubleOrNull(),
                gpsAccuracyMeters = sheet.cell(row, "精度(m)").toFloatOrNull(),
                altitudeMeters = sheet.cell(row, "海拔(m)").toDoubleOrNull(),
                longitudinalAccelerationMps2 = sheet.cell(row, "纵向加速度").toFloatOrNull() ?: 0f,
                lateralAccelerationMps2 = sheet.cell(row, "横向加速度").toFloatOrNull() ?: 0f,
                verticalAccelerationMps2 = sheet.cell(row, "垂直加速度").toFloatOrNull() ?: 0f,
                jerkMps3 = sheet.cell(row, "急动").toFloatOrNull() ?: 0f,
                noiseDbA = sheet.cell(row, "噪声(dBA)").toFloatOrNull()
            )
        }

        val perfSplits = sheet(byName, "perf_splits").mapRows(time) { row, _, sheet, _ ->
            PerformanceSplitEntity(
                id = sheet.cell(row, "ID"),
                sessionId = sheet.cell(row, "会话ID"),
                label = sheet.cell(row, "标签"),
                speedKmh = sheet.cell(row, "速度(km/h)").toDoubleOrNull() ?: 0.0,
                elapsedSeconds = sheet.cell(row, "用时(s)").toDoubleOrNull() ?: 0.0,
                distanceMeters = sheet.cell(row, "距离(m)").toDoubleOrNull() ?: 0.0
            )
        }

        val perfNoise = sheet(byName, "perf_noise").mapRows(time) { row, _, sheet, t ->
            PerformanceNoiseEntity(
                sessionId = sheet.cell(row, "会话ID"),
                timestamp = t.parse(sheet.cell(row, "时间")) ?: 0L,
                dbA = sheet.cell(row, "数值(dBA)").toFloatOrNull() ?: 0f
            )
        }

        val perfJerks = sheet(byName, "perf_jerks").mapRows(time) { row, index, sheet, t ->
            PerformanceJerkEntity(
                id = sheet.cell(row, "ID"),
                sessionId = sheet.cell(row, "会话ID"),
                timestamp = t.parse(sheet.cell(row, "时间")) ?: 0L,
                speedKmh = sheet.cell(row, "速度(km/h)").toDoubleOrNull() ?: 0.0,
                accelerationPeakMps2 = sheet.cell(row, "峰值加速度").toFloatOrNull() ?: 0f,
                jerkPeakMps3 = sheet.cell(row, "峰值急动").toFloatOrNull() ?: 0f,
                durationMs = sheet.cell(row, "时长(ms)").toLongOrNull() ?: 0L,
                severity = enumOf<JerkSeverity>(sheet.cell(row, "严重度")) {
                    warn(sheet, index, "严重度", it, "未知严重度，已回落")
                },
                manual = sheet.cell(row, "手动标记") == "是"
            )
        }

        val parsedCounts = mapOf(
            "vehicles" to vehicles.size,
            "mileageRecords" to mileage.size,
            "energyRecords" to energy.size,
            "maintenanceItems" to items.size,
            "maintenanceHistory" to history.size,
            "performanceSessions" to sessions.size,
            "fitActivities" to activities.size,
            "fitSamples" to fitSamples.size,
            "fitEvents" to fitEvents.size,
            "performanceSamples" to perfSamples.size,
            "performanceSplits" to perfSplits.size,
            "performanceNoise" to perfNoise.size,
            "performanceJerks" to perfJerks.size
        )
        parsedCounts.forEach { (key, actual) ->
            val expected = manifest.counts[key] ?: return@forEach
            if (expected != actual) {
                return Result.Failed("数据不完整：$key 期望 $expected 行、实际解析出 $actual 行")
            }
        }

        val vehicleIds = vehicles.map { it.id }.toSet()
        val activityIds = activities.map { it.id }.toSet()
        val sessionIds = sessions.map { it.id }.toSet()
        val dangling = buildList {
            mileage.forEach { if (it.vehicleId !in vehicleIds) add("里程记录 ${it.id} → 车辆 ${it.vehicleId}") }
            energy.forEach { if (it.vehicleId !in vehicleIds) add("能源记录 ${it.id} → 车辆 ${it.vehicleId}") }
            items.forEach { if (it.vehicleId !in vehicleIds) add("保养项目 ${it.id} → 车辆 ${it.vehicleId}") }
            history.forEach { if (it.vehicleId !in vehicleIds) add("保养记录 ${it.id} → 车辆 ${it.vehicleId}") }
            activities.forEach { if (it.vehicleId !in vehicleIds) add("轨迹活动 ${it.id} → 车辆 ${it.vehicleId}") }
            sessions.forEach { session ->
                val vid = session.vehicleId
                if (vid != null && vid !in vehicleIds) add("性能会话 ${session.id} → 车辆 $vid")
            }
            fitSamples.forEach { if (it.activityId !in activityIds) add("fit_samples → 活动 ${it.activityId}") }
            fitEvents.forEach { if (it.activityId !in activityIds) add("fit_events → 活动 ${it.activityId}") }
            perfSamples.forEach { if (it.sessionId !in sessionIds) add("perf_samples → 会话 ${it.sessionId}") }
            perfSplits.forEach { if (it.sessionId !in sessionIds) add("perf_splits → 会话 ${it.sessionId}") }
            perfNoise.forEach { if (it.sessionId !in sessionIds) add("perf_noise → 会话 ${it.sessionId}") }
            perfJerks.forEach { if (it.sessionId !in sessionIds) add("perf_jerks → 会话 ${it.sessionId}") }
        }
        if (dangling.isNotEmpty()) {
            return Result.Failed(
                "包内引用不完整（${dangling.size} 处），已拒绝导入以免写进孤儿行。例：${dangling.first()}"
            )
        }

        return Result.Ok(
            data = ExportedData(
                vehicles = vehicles,
                mileageRecords = mileage,
                energyRecords = energy,
                maintenanceItems = items,
                maintenanceHistory = history,
                fitActivities = activities,
                fitSamples = fitSamples,
                fitEvents = fitEvents,
                performanceSessions = sessions,
                performanceSamples = perfSamples,
                performanceSplits = perfSplits,
                performanceNoise = perfNoise,
                performanceJerks = perfJerks
            ),
            warnings = warnings,
            ignoredSheets = DERIVED_SHEETS.filter { byName.containsKey(it) }
        )
    }

    fun mergeSlices(sheets: List<XlsSheetReader.SheetData>): List<XlsSheetReader.SheetData> {
        data class Piece(val part: Int, val sheet: XlsSheetReader.SheetData)

        val grouped = LinkedHashMap<String, MutableList<Piece>>()
        sheets.forEach { sheet ->
            val base = sheet.name.substringBefore(".p")
            val part = sheet.name.substringAfterLast(".p", "1").toIntOrNull() ?: 1
            grouped.getOrPut(base) { ArrayList() } += Piece(part, sheet)
        }
        return grouped.map { (base, pieces) ->
            val ordered = pieces.sortedBy { it.part }
            val first = ordered.first().sheet
            if (ordered.size == 1) {
                first.copy(name = base)
            } else {
                first.copy(name = base, rows = ordered.flatMap { it.sheet.rows })
            }
        }
    }

    private fun sheet(byName: Map<String, XlsSheetReader.SheetData>, name: String) =
        byName[name] ?: XlsSheetReader.SheetData(name, emptyList(), emptyList())

    private inline fun <T : Any> XlsSheetReader.SheetData.mapRows(
        time: TimeParser,
        mapper: (row: List<String>, rowIndex: Int, sheet: XlsSheetReader.SheetData, time: TimeParser) -> T
    ): List<T> = rows.mapIndexed { index, row -> mapper(row, index, this, time) }

    /** 枚举解析 */
    private inline fun <reified T : Enum<T>> enumOf(raw: String, warn: (String) -> Unit): T {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return enumValues<T>().first()
        enumValues<T>().firstOrNull { it.name == trimmed }?.let { return it }
        enumValues<T>().firstOrNull { labelOf(it) == trimmed }?.let { return it }
        warn(trimmed)
        return enumValues<T>().first()
    }

    private fun labelOf(value: Any): String? =
        runCatching { value.javaClass.getMethod("getLabel").invoke(value) as? String }.getOrNull()

    /** 时间口径与导出侧一致 */
    class TimeParser {
        private val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA)

        fun parse(raw: String): Long? {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) return null
            return runCatching { format.parse(trimmed)?.time }.getOrNull()
        }
    }
}
