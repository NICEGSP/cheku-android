package com.cheku.app

import com.cheku.app.data.EnergyRecordEntity
import com.cheku.app.data.FitSampleEntity
import com.cheku.app.data.FitTimerEventEntity
import com.cheku.app.data.PerformanceJerkEntity
import com.cheku.app.data.PerformanceNoiseEntity
import com.cheku.app.data.PerformanceSampleEntity
import com.cheku.app.data.PerformanceSplitEntity
import com.cheku.app.data.EnergyRecordType
import com.cheku.app.data.EnergySettlementEntity
import com.cheku.app.data.FitActivityEntity
import com.cheku.app.data.MaintenanceHistoryEntity
import com.cheku.app.data.MaintenanceItemEntity
import com.cheku.app.data.MaintenanceItemType
import com.cheku.app.data.MileageRecordEntity
import com.cheku.app.data.PerformanceSessionEntity
import com.cheku.app.data.VehicleEntity
import jxl.Workbook
import jxl.write.Label as JxlLabel
import jxl.write.Number as JxlNumber
import jxl.write.WritableCell
import jxl.write.WritableCellFormat
import jxl.write.WritableFont
import jxl.write.WritableSheet
import jxl.write.WritableWorkbook
import java.io.BufferedInputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object ReadableExporter {
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA)
    private const val IO_BUFFER = 64 * 1024

    /** 一次性传入的全部实体快照 */
    data class LedgerData(
        val vehicles: List<VehicleEntity> = emptyList(),
        val mileageRecords: List<MileageRecordEntity> = emptyList(),
        val energyRecords: List<EnergyRecordEntity> = emptyList(),
        val energySettlements: List<EnergySettlementEntity> = emptyList(),
        val maintenanceItems: List<MaintenanceItemEntity> = emptyList(),
        val maintenanceHistory: List<MaintenanceHistoryEntity> = emptyList(),
        val performanceSessions: List<PerformanceSessionEntity> = emptyList(),
        val fitActivities: List<FitActivityEntity> = emptyList(),
        val fitSamples: List<FitSampleEntity> = emptyList(),
        val fitEvents: List<FitTimerEventEntity> = emptyList(),
        val performanceSamples: List<PerformanceSampleEntity> = emptyList(),
        val performanceSplits: List<PerformanceSplitEntity> = emptyList(),
        val performanceNoise: List<PerformanceNoiseEntity> = emptyList(),
        val performanceJerks: List<PerformanceJerkEntity> = emptyList(),
    )

    /** 一份证书原件 */
    data class CertificateBinary(val fileName: String, val open: () -> InputStream)

    // 表头加粗格式：只在写 XLS 时用，复用同一实例。
    private val headerFormat: WritableCellFormat by lazy {
        WritableCellFormat(
            WritableFont(WritableFont.ARIAL, WritableFont.DEFAULT_POINT_SIZE, WritableFont.BOLD)
        )
    }

    private fun fmt(value: Long?): String =
        if (value == null || value <= 0L) "" else dateFormat.format(Date(value))

    private fun yes(v: Boolean) = if (v) "是" else "否"

    private data class SheetSpec(
        val group: String,
        val name: String,
        val headers: List<String>,
        val rows: List<List<Any?>>,
    )

    private fun collectSheets(data: LedgerData): List<SheetSpec> = buildList {
        val vName = data.vehicles.associate { it.id to it.name }
        val itemTypeById = data.maintenanceItems.associate { it.id to it.itemType }

        add(vehicleSheet(data.vehicles))
        add(mileageSheet(data.mileageRecords, vName))

        add(energyRecordSheet(data.energyRecords, vName))
        add(energySettlementSheet(data.energySettlements, vName))

        add(maintenanceItemSheet(data.maintenanceItems, vName))
        val (repair, periodic) = data.maintenanceHistory.partition {
            itemTypeById[it.itemId] == MaintenanceItemType.RepairInspection
        }
        add(maintenanceHistorySheet("保养记录", "保养与费用", periodic, vName))
        add(maintenanceHistorySheet("检修记录", "保养与费用", repair, vName))
        add(costSummarySheet(data, vName))

        add(performanceSheet(data.performanceSessions, vName))
        add(fitActivitySheet(data.fitActivities, vName))

        add(fitSampleSheet(data.fitSamples))
        add(fitEventSheet(data.fitEvents))
        add(performanceSampleSheet(data.performanceSamples))
        add(performanceSplitSheet(data.performanceSplits))
        add(performanceNoiseSheet(data.performanceNoise))
        add(performanceJerkSheet(data.performanceJerks))
    }

    private fun fitSampleSheet(samples: List<FitSampleEntity>) = SheetSpec(
        group = "原始采样",
        name = "fit_samples",
        headers = listOf(
            "活动ID", "时间", "纬度", "经度", "速度(m/s)", "距离(m)", "海拔(m)", "精度(m)",
            "方位角", "心率", "踏频", "功率", "运动量"
        ),
        rows = samples.map {
            listOf(
                it.activityId, fmt(it.timestamp), it.latitude, it.longitude, it.speedMps,
                it.distanceMeters, it.altitudeMeters, it.accuracyMeters, it.bearingDegrees,
                it.heartRateBpm, it.cadenceRpm, it.powerWatts, it.motionMagnitude
            )
        }
    )

    private fun fitEventSheet(events: List<FitTimerEventEntity>) = SheetSpec(
        group = "原始采样",
        name = "fit_events",
        headers = listOf("活动ID", "时间", "类型"),
        rows = events.map { listOf(it.activityId, fmt(it.timestamp), it.type.name) }
    )

    private fun performanceSampleSheet(samples: List<PerformanceSampleEntity>) = SheetSpec(
        group = "原始采样",
        name = "perf_samples",
        headers = listOf(
            "会话ID", "纳秒时间", "墙钟时间", "GPS速度", "融合速度", "距离(m)", "纬度", "经度",
            "精度(m)", "海拔(m)", "纵向加速度", "横向加速度", "垂直加速度", "急动", "噪声(dBA)"
        ),
        rows = samples.map {
            listOf(
                it.sessionId, it.timestampNanos, fmt(it.wallTime), it.gpsSpeedMps, it.fusedSpeedMps,
                it.distanceMeters, it.latitude, it.longitude, it.gpsAccuracyMeters, it.altitudeMeters,
                it.longitudinalAccelerationMps2, it.lateralAccelerationMps2,
                it.verticalAccelerationMps2, it.jerkMps3, it.noiseDbA
            )
        }
    )

    private fun performanceSplitSheet(splits: List<PerformanceSplitEntity>) = SheetSpec(
        group = "原始采样",
        name = "perf_splits",
        headers = listOf("ID", "会话ID", "标签", "速度(km/h)", "用时(s)", "距离(m)"),
        rows = splits.map {
            listOf(it.id, it.sessionId, it.label, it.speedKmh, it.elapsedSeconds, it.distanceMeters)
        }
    )

    private fun performanceNoiseSheet(noises: List<PerformanceNoiseEntity>) = SheetSpec(
        group = "原始采样",
        name = "perf_noise",
        headers = listOf("会话ID", "时间", "数值(dBA)"),
        rows = noises.map { listOf(it.sessionId, fmt(it.timestamp), it.dbA) }
    )

    private fun performanceJerkSheet(jerks: List<PerformanceJerkEntity>) = SheetSpec(
        group = "原始采样",
        name = "perf_jerks",
        headers = listOf(
            "ID", "会话ID", "时间", "速度(km/h)", "峰值加速度", "峰值急动", "时长(ms)", "严重度", "手动标记"
        ),
        rows = jerks.map {
            listOf(
                it.id, it.sessionId, fmt(it.timestamp), it.speedKmh, it.accelerationPeakMps2,
                it.jerkPeakMps3, it.durationMs, it.severity.name, yes(it.manual)
            )
        }
    )

    /** 计算分卷计划 */
    fun planVolumesFor(data: LedgerData): List<BackupPackage.VolumePlan> =
        BackupPackage.planVolumes(tableRowCounts(data))

    fun renderVolume(data: LedgerData, volume: BackupPackage.VolumePlan, sink: OutputStream) {
        val vName = data.vehicles.associate { it.id to it.name }
        val (repair, periodic) = maintenanceHistorySplit(data)
        val picked = volume.slices.mapNotNull { slice ->
            val base = slice.sheet.substringBefore(".p")
            val part = (slice.sheet.substringAfterLast(".p", "1").toIntOrNull() ?: 1) - 1
            val full = specFor(base, data, vName, periodic, repair) ?: return@mapNotNull null
            val from = part * BackupPackage.MAX_ROWS_PER_SHEET
            val to = minOf(from + slice.rows, full.rows.size)
            full.copy(name = slice.sheet, rows = full.rows.subList(from, to))
        }
        writeSheetsXlsTo(sink, picked)
    }

    fun exportVolumes(data: LedgerData): List<BackupPackage.VolumeBytes> {
        val plan = planVolumesFor(data)
        return plan.mapIndexed { index, volume ->
            val bytes = java.io.ByteArrayOutputStream().use { baos ->
                renderVolume(data, volume, baos)
                baos.toByteArray()
            }
            BackupPackage.VolumeBytes(
                sheetNames = volume.sheetNames,
                rows = volume.rows,
                bytes = bytes
            )
        }
    }

    fun countsOf(data: LedgerData): Map<String, Int> = mapOf(
        "vehicles" to data.vehicles.size,
        "mileageRecords" to data.mileageRecords.size,
        "energyRecords" to data.energyRecords.size,
        "energySettlements" to data.energySettlements.size,
        "maintenanceItems" to data.maintenanceItems.size,
        "maintenanceHistory" to data.maintenanceHistory.size,
        "performanceSessions" to data.performanceSessions.size,
        "fitActivities" to data.fitActivities.size,
        "fitSamples" to data.fitSamples.size,
        "fitEvents" to data.fitEvents.size,
        "performanceSamples" to data.performanceSamples.size,
        "performanceSplits" to data.performanceSplits.size,
        "performanceNoise" to data.performanceNoise.size,
        "performanceJerks" to data.performanceJerks.size
    )

    /** 各表数据行数 */
    private fun tableRowCounts(data: LedgerData): List<BackupPackage.TableRows> {
        val (repair, periodic) = maintenanceHistorySplit(data)
        return listOf(
            BackupPackage.TableRows("车辆", data.vehicles.size),
            BackupPackage.TableRows("里程记录", data.mileageRecords.size),
            BackupPackage.TableRows("能源记录", data.energyRecords.size),
            BackupPackage.TableRows("能源结算", data.energySettlements.size),
            BackupPackage.TableRows("保养项目", data.maintenanceItems.size),
            BackupPackage.TableRows("保养记录", periodic.size),
            BackupPackage.TableRows("检修记录", repair.size),
            BackupPackage.TableRows("费用汇总", data.vehicles.size),
            BackupPackage.TableRows("性能记录", data.performanceSessions.size),
            BackupPackage.TableRows("轨迹记录", data.fitActivities.size),
            BackupPackage.TableRows("fit_samples", data.fitSamples.size),
            BackupPackage.TableRows("fit_events", data.fitEvents.size),
            BackupPackage.TableRows("perf_samples", data.performanceSamples.size),
            BackupPackage.TableRows("perf_splits", data.performanceSplits.size),
            BackupPackage.TableRows("perf_noise", data.performanceNoise.size),
            BackupPackage.TableRows("perf_jerks", data.performanceJerks.size)
        )
    }

    private fun maintenanceHistorySplit(data: LedgerData): Pair<List<MaintenanceHistoryEntity>, List<MaintenanceHistoryEntity>> {
        val itemTypeById = data.maintenanceItems.associate { it.id to it.itemType }
        return data.maintenanceHistory.partition { itemTypeById[it.itemId] == MaintenanceItemType.RepairInspection }
    }

    /** 取单张工作表的规格 */
    private fun specFor(
        name: String,
        data: LedgerData,
        vName: Map<String, String>,
        periodic: List<MaintenanceHistoryEntity>,
        repair: List<MaintenanceHistoryEntity>
    ): SheetSpec? = when (name) {
        "车辆" -> vehicleSheet(data.vehicles)
        "里程记录" -> mileageSheet(data.mileageRecords, vName)
        "能源记录" -> energyRecordSheet(data.energyRecords, vName)
        "能源结算" -> energySettlementSheet(data.energySettlements, vName)
        "保养项目" -> maintenanceItemSheet(data.maintenanceItems, vName)
        "保养记录" -> maintenanceHistorySheet("保养记录", "保养与费用", periodic, vName)
        "检修记录" -> maintenanceHistorySheet("检修记录", "保养与费用", repair, vName)
        "费用汇总" -> costSummarySheet(data, vName)
        "性能记录" -> performanceSheet(data.performanceSessions, vName)
        "轨迹记录" -> fitActivitySheet(data.fitActivities, vName)
        "fit_samples" -> fitSampleSheet(data.fitSamples)
        "fit_events" -> fitEventSheet(data.fitEvents)
        "perf_samples" -> performanceSampleSheet(data.performanceSamples)
        "perf_splits" -> performanceSplitSheet(data.performanceSplits)
        "perf_noise" -> performanceNoiseSheet(data.performanceNoise)
        "perf_jerks" -> performanceJerkSheet(data.performanceJerks)
        else -> null
    }

    private fun writeSheetsXlsTo(sink: OutputStream, sheets: List<SheetSpec>) {
        val workbook: WritableWorkbook = Workbook.createWorkbook(NonClosingOutputStream(sink))
        sheets.forEachIndexed { index, spec ->
            val sheet: WritableSheet = workbook.createSheet(spec.name, index)
            spec.headers.forEachIndexed { c, h -> sheet.addCell(JxlLabel(c, 0, h, headerFormat)) }
            spec.rows.forEachIndexed { rowIdx, row ->
                row.forEachIndexed { c, value -> sheet.addCell(cellFor(c, rowIdx + 1, value)) }
            }
        }
        workbook.write()
        workbook.close()
    }

    /** 便捷封装 */
    private fun writeSheetsXls(sheets: List<SheetSpec>): ByteArray =
        java.io.ByteArrayOutputStream().use { baos ->
            writeSheetsXlsTo(baos, sheets)
            baos.toByteArray()
        }

    private fun vehicleSheet(vehicles: List<VehicleEntity>) = SheetSpec(
        group = "车辆与行驶",
        name = "车辆",
        headers = listOf(
            "车辆ID", "名称", "型号", "车牌", "类别", "能源类型", "变速箱",
            "油箱容量(L)", "电池容量(kWh)", "充电上限SOC(%)", "当前里程(km)",
            "出厂日期", "购车价(元)", "购置税(元)", "更新时间"
        ),
        rows = vehicles.map { v ->
            listOf(
                v.id, v.name, v.model, v.plate, v.category.label, v.energyType.label, v.transmissionType.label,
                v.fuelTankCapacityLiters, v.batteryCapacityKwh, v.defaultChargeLimitSoc, v.currentMileage,
                fmt(v.manufacturingDate), v.purchasePrice, v.purchaseTax, fmt(v.updatedAt)
            )
        }
    )

    private fun mileageSheet(records: List<MileageRecordEntity>, vName: Map<String, String>) = SheetSpec(
        group = "车辆与行驶",
        name = "里程记录",
        headers = listOf("里程ID", "车辆ID", "车辆名称", "里程(km)", "备注", "过路费(元)", "记录时间"),
        rows = records.map { r ->
            listOf(r.id, r.vehicleId, vName[r.vehicleId].orEmpty(), r.mileage, r.note, r.roadFeeAmount, fmt(r.createdAt))
        }
    )

    private fun energyRecordSheet(records: List<EnergyRecordEntity>, vName: Map<String, String>) = SheetSpec(
        group = "能源",
        name = "能源记录",
        headers = listOf(
            "能源ID", "车辆ID", "车辆名称", "类型", "里程(km)", "金额(元)", "订单金额(元)", "实付金额(元)",
            "是否满充", "是否漏记上次", "燃油标号", "单价(元)", "充电方式", "电价(元/kWh)", "服务费(元)",
            "充电上限SOC(%)", "备注", "记录时间"
        ),
        rows = records.map { r ->
            listOf(
                r.id, r.vehicleId, vName[r.vehicleId].orEmpty(), r.type.label, r.mileage, r.amount,
                r.orderAmount, r.paidAmount, yes(r.isFull), yes(r.missedPrevious), r.fuelGrade,
                r.unitPrice, r.chargeMethod, r.electricityPrice, r.serviceFee, r.chargeLimitSocSnapshot,
                r.note, fmt(r.createdAt)
            )
        }
    )

    private fun energySettlementSheet(settlements: List<EnergySettlementEntity>, vName: Map<String, String>) = SheetSpec(
        group = "能源",
        name = "能源结算",
        headers = listOf(
            "结算ID", "车辆ID", "车辆名称", "能源类型", "状态", "起始里程(km)", "结束里程(km)", "距离(km)",
            "总金额(元)", "订单总额(元)", "实付总额(元)", "每百公里能耗", "每公里成本(元)", "充电上限SOC(%)",
            "无效原因", "结算时间"
        ),
        rows = settlements.map { s ->
            listOf(
                s.id, s.vehicleId, vName[s.vehicleId].orEmpty(), s.type.label, s.status.label,
                s.startMileage, s.endMileage, s.distanceKm,
                s.totalAmount, s.totalOrderAmount, s.totalPaidAmount,
                s.consumptionPer100Km, s.costPerKm, s.chargeLimitSoc, s.invalidReason, fmt(s.createdAt)
            )
        }
    )

    private fun maintenanceItemSheet(items: List<MaintenanceItemEntity>, vName: Map<String, String>) = SheetSpec(
        group = "保养与费用",
        name = "保养项目",
        headers = listOf(
            "项目ID", "车辆ID", "车辆名称", "名称", "周期里程(km)", "周期天数", "上次保养里程(km)",
            "上次保养时间", "上次零件号", "项目类型", "基准时间"
        ),
        rows = items.map { m ->
            listOf(
                m.id, m.vehicleId, vName[m.vehicleId].orEmpty(), m.name, m.intervalKm, m.intervalDays,
                m.lastServiceMileage, fmt(m.lastServiceAt), m.lastPartNumber, m.itemType.name, fmt(m.baselineAt)
            )
        }
    )

    private fun maintenanceHistorySheet(
        name: String,
        group: String,
        history: List<MaintenanceHistoryEntity>,
        vName: Map<String, String>,
    ) = SheetSpec(
        group = group,
        name = name,
        headers = listOf(
            "记录ID", "车辆ID", "车辆名称", "项目ID", "项目名称", "保养里程(km)", "保养时间", "备注",
            "工时费(元)", "零件费(元)", "零件号", "记录名称", "过期时间"
        ),
        rows = history.map { h ->
            listOf(
                h.id, h.vehicleId, vName[h.vehicleId].orEmpty(), h.itemId, h.itemName, h.serviceMileage,
                fmt(h.serviceAt), h.note, h.laborCost, h.partsCost, h.partNumber, h.recordName, fmt(h.expiresAt)
            )
        }
    )

    private fun costSummarySheet(data: LedgerData, vName: Map<String, String>): SheetSpec {
        val maintByVehicle = data.maintenanceHistory.groupBy { it.vehicleId }
        val energyByVehicle = data.energySettlements.groupBy { it.vehicleId }
        val rows = data.vehicles.map { v ->
            val m = maintByVehicle[v.id].orEmpty()
            val e = energyByVehicle[v.id].orEmpty()
            val avgCost = e.mapNotNull { it.costPerKm }.average().takeIf { !it.isNaN() }
            val avgCons = e.mapNotNull { it.consumptionPer100Km }.average().takeIf { !it.isNaN() }
            listOf(
                v.id, v.name,
                m.sumOf { it.laborCost }, m.sumOf { it.partsCost },
                m.sumOf { it.insuranceAmount }, m.sumOf { it.repairAmount },
                e.sumOf { it.totalAmount }, e.sumOf { it.totalOrderAmount }, e.sumOf { it.totalPaidAmount },
                avgCons, avgCost
            )
        }
        return SheetSpec(
            group = "保养与费用",
            name = "费用汇总",
            headers = listOf(
                "车辆ID", "车辆名称", "保养工时费合计(元)", "保养零件费合计(元)", "保险金额合计(元)",
                "维修金额合计(元)", "能源总金额(元)", "能源订单总额(元)", "能源实付总额(元)",
                "平均百公里能耗", "平均每公里成本(元)"
            ),
            rows = rows
        )
    }

    private fun performanceSheet(sessions: List<PerformanceSessionEntity>, vName: Map<String, String>) = SheetSpec(
        group = "测试与轨迹",
        name = "性能记录",
        headers = listOf(
            "会话ID", "车辆ID", "车辆名称", "名称", "测试类型", "状态", "开始时间", "结束时间", "结果(秒)",
            "结果距离(m)", "最大加速度(m/s²)", "平均加速度(m/s²)", "平均噪音(dBA)", "最大噪音(dBA)",
            "质量", "无效原因", "麦克风校准"
        ),
        rows = sessions.map { s ->
            val vid = s.vehicleId
            listOf(
                s.id, vid.orEmpty(), if (vid == null) "未关联车辆" else vName[vid].orEmpty(),
                s.name, s.testType.name, s.status.name, fmt(s.startedAt), fmt(s.endedAt),
                s.resultSeconds, s.resultDistanceMeters, s.maxAccelerationMps2, s.averageAccelerationMps2,
                s.averageNoiseDbA, s.maxNoiseDbA, s.quality.name, s.invalidReason, yes(s.microphoneCalibrated)
            )
        }
    )

    private fun fitActivitySheet(activities: List<FitActivityEntity>, vName: Map<String, String>) = SheetSpec(
        group = "测试与轨迹",
        name = "轨迹记录",
        headers = listOf(
            "轨迹ID", "车辆ID", "车辆名称", "名称", "状态", "开始时间", "结束时间", "暂停时长(ms)",
            "距离(m)", "最大速度(m/s)", "自动暂停", "最后采样时间", "导出时间"
        ),
        rows = activities.map { a ->
            listOf(
                a.id, a.vehicleId, vName[a.vehicleId].orEmpty(), a.name, a.status.name,
                fmt(a.startedAt), fmt(a.endedAt), a.pausedDurationMs, a.distanceMeters, a.maxSpeedMps,
                yes(a.autoPauseEnabled), fmt(a.lastSampleAt), fmt(a.exportedAt)
            )
        }
    )

    fun exportCsv(data: LedgerData, out: OutputStream) {
        val safe = NonClosingOutputStream(out)
        val writer = OutputStreamWriter(safe, Charsets.UTF_8)
        writer.appendLine("# 车辆台账·可读导出")
        writer.appendLine("# 生成时间: ${dateFormat.format(Date())}")
        writer.appendLine("# 时间口径: 里程/能耗=记录时间(createdAt), 保养=保养时间(serviceAt), 性能/轨迹=开始时间(startedAt)")
        collectSheets(data).forEach { spec ->
            writer.appendLine()
            writer.appendLine("# ── 分组: ${spec.group} ──")
            writer.appendLine("## ${spec.name}")
            writer.appendLine(spec.headers.joinToString(",") { csvField(it) })
            spec.rows.forEach { row -> writer.appendLine(row.joinToString(",") { csvField(it) }) }
        }
        writer.flush()
    }

    /** 便捷封装 */
    fun exportCsv(data: LedgerData): String =
        java.io.ByteArrayOutputStream().use { baos -> exportCsv(data, baos); baos.toString(Charsets.UTF_8.name()) }

    fun exportXls(data: LedgerData, out: OutputStream) {
        val workbook: WritableWorkbook = Workbook.createWorkbook(NonClosingOutputStream(out))
        val sheets = collectSheets(data)

        // 总览 / 汇总表
        val overview = workbook.createSheet("总览", 0)
        overview.addCell(JxlLabel(0, 0, "车辆台账·可读导出（总览）", headerFormat))
        overview.addCell(JxlLabel(0, 1, "生成时间: ${dateFormat.format(Date())}"))
        overview.addCell(JxlLabel(0, 2, "时间口径: 里程/能耗=createdAt, 保养=serviceAt, 性能/轨迹=startedAt"))
        overview.addCell(JxlLabel(0, 4, "分组"))
        overview.addCell(JxlLabel(1, 4, "工作表"))
        overview.addCell(JxlLabel(2, 4, "行数", headerFormat))
        sheets.forEachIndexed { i, spec ->
            val r = 5 + i
            overview.addCell(JxlLabel(0, r, spec.group))
            overview.addCell(JxlLabel(1, r, spec.name))
            overview.addCell(JxlNumber(2, r, spec.rows.size.toDouble()))
        }

        // 明细工作表
        sheets.forEachIndexed { index, spec ->
            val sheet: WritableSheet = workbook.createSheet(spec.name, index + 1)
            spec.headers.forEachIndexed { c, h -> sheet.addCell(JxlLabel(c, 0, h, headerFormat)) }
            spec.rows.forEachIndexed { rowIdx, row ->
                row.forEachIndexed { c, value -> sheet.addCell(cellFor(c, rowIdx + 1, value)) }
            }
        }
        workbook.write()
        workbook.close()
    }

    /** 便捷封装 */
    fun exportXls(data: LedgerData): ByteArray =
        java.io.ByteArrayOutputStream().use { baos -> exportXls(data, baos); baos.toByteArray() }

    fun exportZip(data: LedgerData, certificates: List<CertificateBinary>, out: OutputStream) {
        // 包一层 NonClosingOutputStream：[out] 归调用方所有（与 exportCsv / exportXls 同一契约）。
        // `ZipOutputStream(out).use { }` 的 use 会在结尾 close 掉 zip，而 zip.close() 会连带关掉
        // 底层 out；包一层后 zip.close() 写完整中央目录 + 调 safe.close()（只 flush），out 不会被关。
        val safe = NonClosingOutputStream(out)
        ZipOutputStream(safe).use { zip ->
            zip.putNextEntry(ZipEntry("可读台账.csv"))
            exportCsv(data, zip)
            zip.closeEntry()

            certificates.forEach { cert ->
                val name = sanitizeZipEntryName(cert.fileName)
                zip.putNextEntry(ZipEntry(name))
                cert.open().use { copyChunked(zip, it) }
                zip.closeEntry()
            }

            zip.putNextEntry(ZipEntry("说明.txt"))
            zip.write(buildReadme(data, certificates.size).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
    }

    /** 便捷封装 */
    fun exportZip(data: LedgerData, certificates: List<CertificateBinary>): ByteArray =
        java.io.ByteArrayOutputStream().use { baos -> exportZip(data, certificates, baos); baos.toByteArray() }

    private fun cellFor(col: Int, row: Int, value: Any?): WritableCell = when {
        value == null -> JxlLabel(col, row, "")
        value is Boolean -> JxlLabel(col, row, value.toString())
        value is Number -> JxlNumber(col, row, value.toDouble())
        else -> JxlLabel(col, row, value.toString())
    }

    private fun csvField(value: Any?): String {
        val raw = when (value) {
            null -> ""
            is Double -> if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
            else -> value.toString()
        }
        return if (raw.contains(',') || raw.contains('"') || raw.contains('\n')) {
            "\"${raw.replace("\"", "\"\"")}\""
        } else raw
    }

    private fun copyChunked(out: OutputStream, input: InputStream, bufferBytes: Int = IO_BUFFER) {
        BufferedInputStream(input, bufferBytes).use { bis ->
            val buffer = ByteArray(bufferBytes)
            while (true) {
                val read = bis.read(buffer)
                if (read < 0) break
                out.write(buffer, 0, read)
            }
        }
    }

    private class NonClosingOutputStream(private val delegate: OutputStream) : OutputStream() {
        override fun write(b: Int) = delegate.write(b)
        override fun write(b: ByteArray, off: Int, len: Int) = delegate.write(b, off, len)
        override fun flush() = delegate.flush()

        override fun close() = delegate.flush()
    }

    /** 净化 ZIP 条目名 */
    private fun sanitizeZipEntryName(raw: String): String {
        val base = raw.substringAfterLast('/').substringAfterLast('\\').ifBlank { "certificate" }
        val safe = base.replace(Regex("[^A-Za-z0-9._\\-\\u4e00-\\u9fa5]"), "_").trim('.')
        return if (safe.isBlank()) "certificate" else safe
    }

    private fun buildReadme(data: LedgerData, certificateCount: Int): String = buildString {
        appendLine("车辆台账·可读导出（ZIP 包说明）")
        appendLine("生成时间: ${dateFormat.format(Date())}")
        appendLine()
        appendLine("包内文件：")
        appendLine("  - 可读台账.csv : 全部台账的可读明细")
        appendLine("  - 证书原件($certificateCount 份) : 人车证书原文件，未加密")
        appendLine("  - 说明.txt : 本文件")
        appendLine()
        appendLine("9 类分四组：")
        appendLine("  车辆与行驶 : 车辆 / 里程记录")
        appendLine("  能源       : 能源记录 / 能源结算")
        appendLine("  保养与费用 : 保养项目 / 保养记录 / 检修记录(靠 MaintenanceItemType.RepairInspection 区分) / 费用汇总")
        appendLine("  测试与轨迹 : 性能记录 / 轨迹记录")
        appendLine()
        appendLine("时间口径：里程·能耗=createdAt，保养=serviceAt，性能·轨迹=startedAt")
        appendLine("费用汇总含此前从未导出的能源结算字段：总金额/订单总额/实付总额/每公里成本/每百公里能耗")
        appendLine("未关联车辆的性能记录同样保留（车辆名称记为「未关联车辆」）。")
        appendLine("实体规模：车辆 ${data.vehicles.size}，里程 ${data.mileageRecords.size}，能源记录 ${data.energyRecords.size}，")
        appendLine("能源结算 ${data.energySettlements.size}，保养项目 ${data.maintenanceItems.size}，保养/检修记录 ${data.maintenanceHistory.size}，")
        appendLine("性能会话 ${data.performanceSessions.size}，轨迹 ${data.fitActivities.size}。")
    }
}
