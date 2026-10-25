package com.cheku.app.data

import com.cheku.app.ExportedData
import com.cheku.app.domain.EnergySaveResult
import com.cheku.app.domain.buildEnergySettlements
import com.cheku.app.domain.maintenanceTemplatesFor
import com.cheku.app.data.MaintenanceItemType
import java.util.UUID
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class LedgerRepository(
    private val dao: LedgerDao,
    private val performanceDao: PerformanceDao? = null,
    private val inTransaction: suspend (suspend () -> Unit) -> Unit = { it() }
) {
    private val builtInMaintenanceNames = setOf(
        "商业险", "交强险", "维修/检查",
        "机油/机滤", "机油", "机滤", "发动机机油", "机油滤清器",
        "空气滤芯", "空滤", "发动机空气滤芯", "空调滤芯",
        "火花塞", "燃油滤清器", "刹车油", "制动液",
        "防冻液", "防冻液/冷却液", "冷却液", "电池热管理冷却液检查",
        "刹车片检查", "制动片检查", "制动片/制动盘检查", "制动片/刹车线检查", "刹车片/刹车线检查",
        "轮胎检查", "轮胎检查/换位",
        "减速器油", "电驱减速器油检查", "电池健康检查", "高压电池健康检查", "动力电池健康检查",
        "制动卡钳清洁润滑", "变速箱油", "自动变速箱油", "变速箱油/离合器检查", "手动变速箱油/离合器检查", "CVT变速箱油",
        "电瓶检查", "12V电瓶检查", "链条清洁/润滑", "链条张紧检查", "传动皮带检查", "普利珠检查",
        "充电口检查", "电机/轮毂检查", "电机/轮毂轴承检查", "前叉/减震检查", "电机/控制器检查", "车架螺丝检查",
        "变速系统调试", "中轴/花鼓检查"
    )

    private val maintenanceNameAliases = mapOf(
        "机油/机滤" to "发动机机油",
        "机油" to "发动机机油",
        "机滤" to "机油滤清器",
        "空滤" to "空气滤芯",
        "刹车油" to "制动液",
        "防冻液" to "防冻液/冷却液",
        "刹车片检查" to "制动片检查",
        "刹车片/刹车线检查" to "制动片/刹车线检查",
        "减速器油" to "电驱减速器油检查",
        "电池健康检查" to "动力电池健康检查",
        "变速箱油" to "自动变速箱油",
        "变速箱油/离合器检查" to "手动变速箱油/离合器检查",
        "电瓶检查" to "12V电瓶检查",
        "电机/轮毂检查" to "电机/轮毂轴承检查"
    )

    fun observeVehicles(): Flow<List<VehicleEntity>> = dao.observeVehicles()

    fun observeMileageRecords(limit: Int = Int.MAX_VALUE): Flow<List<MileageRecordEntity>> =
        dao.observeMileageRecords(limit)

    fun observeEnergyRecords(limit: Int = Int.MAX_VALUE): Flow<List<EnergyRecordEntity>> =
        dao.observeEnergyRecords(limit)

    fun observeEnergySettlements(): Flow<List<EnergySettlementEntity>> =
        dao.observeEnergySettlements()

    fun observeMaintenanceItems(): Flow<List<MaintenanceItemEntity>> =
        dao.observeMaintenanceItems()

    fun observeAllHistory(): Flow<List<MaintenanceHistoryEntity>> = dao.observeAllHistory()

    suspend fun seedIfEmpty(now: Long = System.currentTimeMillis()) {
        if (dao.vehicleCount() > 0) {
            dao.vehicleById("seed-road-bike")
                ?.takeIf { it.name == "晨光公路车" || it.model == "Road Bike" }
                ?.let {
                    dao.upsertVehicle(
                        it.copy(
                            name = "捷安特公路车",
                            model = "GIANT TCR"
                        )
                    )
                }
            syncDefaultMaintenanceTemplates(now)
            seedRecordsIfEmpty(now)
            return
        }
        val dayMs = 24L * 60L * 60L * 1000L
        val vehicles = listOf(
            VehicleEntity(
                id = "seed-su7",
                name = "小米SU7",
                model = "SU7 Max",
                plate = "京A00001",
                category = VehicleCategory.Car,
                energyType = EnergyType.Electric,
                transmissionType = TransmissionType.ElectricDrive,
                batteryCapacityKwh = 101.0,
                defaultChargeLimitSoc = 90,
                currentMileage = 34_180.0,
                updatedAt = now,
                manufacturingDate = now - 26L * 30L * dayMs   // 约 2 年
            ),
            VehicleEntity(
                id = "seed-500rr",
                name = "张雪500RR",
                model = "500RR",
                plate = "京B00002",
                category = VehicleCategory.Motorcycle,
                energyType = EnergyType.Fuel,
                transmissionType = TransmissionType.Manual,
                fuelTankCapacityLiters = 15.0,
                currentMileage = 21_460.0,
                updatedAt = now - 2 * dayMs,
                manufacturingDate = now - 47L * 30L * dayMs   // 约 4 年
            ),
            VehicleEntity(
                id = "seed-mzmix",
                name = "九号Mz MIX",
                model = "Mz MIX",
                plate = "京C00003",
                category = VehicleCategory.ElectricBike,
                energyType = EnergyType.Electric,
                transmissionType = TransmissionType.ElectricDrive,
                batteryCapacityKwh = 2.5,
                defaultChargeLimitSoc = 100,
                currentMileage = 5_420.0,
                updatedAt = now - dayMs,
                manufacturingDate = now - 33L * 30L * dayMs   // 约 3 年
            ),
            VehicleEntity(
                id = "seed-road-bike",
                name = "捷安特公路车",
                model = "GIANT TCR",
                plate = "无牌",
                category = VehicleCategory.Bicycle,
                energyType = EnergyType.Human,
                transmissionType = TransmissionType.None,
                currentMileage = 6_240.0,
                updatedAt = now - 3 * dayMs,
                manufacturingDate = now - 58L * 30L * dayMs   // 约 5 年
            )
        )
        dao.upsertVehicles(vehicles)
        vehicles.forEach { applyMaintenanceTemplate(it.id, now) }
        syncDefaultMaintenanceTemplates(now)

        // 近五年密集历史：月度里程 + 按各自节奏的加油/充电 + 保养记录
        seedDemoHistory(vehicles, now)
    }

    /** 演示车队 */
    private suspend fun seedDemoHistory(vehicles: List<VehicleEntity>, now: Long) {
        val rnd = java.util.Random(20260922L)
        val dayMs = 24L * 60L * 60L * 1000L
        vehicles.forEach { vehicle ->
            val months = ((now - (vehicle.manufacturingDate ?: now)) / (30L * dayMs)).toInt().coerceIn(12, 60)
            // 每月里程 与 "一次补能能跑多远"（按车型/能源类型）
            val monthlyKm: Double
            val perFillKm: Double
            when (vehicle.category) {
                VehicleCategory.Car -> if (vehicle.energyType == EnergyType.Electric) {
                    monthlyKm = 1_180.0; perFillKm = 330.0
                } else {
                    monthlyKm = 1_320.0; perFillKm = 620.0
                }
                VehicleCategory.Motorcycle -> { monthlyKm = 450.0; perFillKm = 330.0 }
                VehicleCategory.ElectricBike -> { monthlyKm = 165.0; perFillKm = 42.0 }
                VehicleCategory.Bicycle -> { monthlyKm = 105.0; perFillKm = 0.0 }
                else -> { monthlyKm = 800.0; perFillKm = 400.0 }
            }
            val isElectric = vehicle.energyType == EnergyType.Electric
            val isHuman = vehicle.energyType == EnergyType.Human
            val tankLiters = vehicle.fuelTankCapacityLiters?.takeIf { it > 0.0 } ?: 8.0
            val batteryKwh = vehicle.batteryCapacityKwh?.takeIf { it > 0.0 } ?: 2.0

            var mileage = (vehicle.currentMileage - monthlyKm * months * 0.94).coerceAtLeast(60.0)
            var nextFillAt = mileage + perFillKm * (0.9 + rnd.nextDouble() * 0.2)

            for (month in months - 1 downTo 0) {
                val monthEnd = now - month * 30L * dayMs - (rnd.nextDouble() * 3 * dayMs).toLong()
                val delta = monthlyKm * (0.78 + rnd.nextDouble() * 0.44)
                mileage += delta
                val roadFee = if (isHuman || vehicle.category == VehicleCategory.Bicycle) {
                    0.0
                } else if (rnd.nextDouble() < 0.35) {
                    (30 + rnd.nextInt(260)).toDouble()
                } else 0.0
                dao.insertMileageRecord(
                    MileageRecordEntity(
                        id = "seed-mile-${vehicle.id}-$month",
                        vehicleId = vehicle.id,
                        mileage = mileage,
                        note = when (month) {
                            months - 1 -> "示例数据 · 起始里程"
                            0 -> "当前里程"
                            else -> "月度里程"
                        },
                        createdAt = monthEnd,
                        roadFeeAmount = roadFee,
                    )
                )

                if (!isHuman) {
                    while (nextFillAt <= mileage) {
                        val travelled = mileage - nextFillAt
                        val at = (monthEnd - (travelled / delta.coerceAtLeast(1.0) * 30L * dayMs).toLong())
                            .coerceAtMost(now)
                        if (isElectric) {
                            val kwh = batteryKwh * (0.52 + rnd.nextDouble() * 0.42)
                            val home = rnd.nextDouble() < 0.72
                            val price = if (home) 0.32 else 1.18
                            addEnergyRecord(
                                vehicle.id,
                                EnergyRecordInput(
                                    type = EnergyRecordType.Charge,
                                    mileage = nextFillAt,
                                    amount = Math.round(kwh * 10) / 10.0,
                                    orderAmount = Math.round(kwh * price * 100) / 100.0,
                                    paidAmount = Math.round(kwh * price * 100) / 100.0,
                                    isFull = true,
                                    missedPrevious = false,
                                    chargeMethod = if (home) "家充" else "公共快充",
                                    electricityPrice = price,
                                    chargeLimitSocSnapshot = (vehicle.defaultChargeLimitSoc ?: 100).coerceIn(50, 100)
                                ),
                                now = at
                            )
                        } else {
                            val liters = tankLiters * (0.5 + rnd.nextDouble() * 0.44)
                            val price = if (vehicle.category == VehicleCategory.Motorcycle) 8.16 else 7.88
                            addEnergyRecord(
                                vehicle.id,
                                EnergyRecordInput(
                                    type = EnergyRecordType.Fuel,
                                    mileage = nextFillAt,
                                    amount = Math.round(liters * 10) / 10.0,
                                    orderAmount = Math.round(liters * price * 100) / 100.0,
                                    paidAmount = Math.round((liters * price - 3.8) * 100) / 100.0,
                                    isFull = true,
                                    missedPrevious = false,
                                    fuelGrade = if (vehicle.category == VehicleCategory.Motorcycle) "95" else "92",
                                    unitPrice = price
                                ),
                                now = at
                            )
                        }
                        nextFillAt += perFillKm * (0.86 + rnd.nextDouble() * 0.3)
                    }
                }
            }
            // 结算档位必须重建：能耗/费用统计依赖它
            if (!isHuman) {
                rebuildEnergySettlements(vehicle.id, EnergyRecordType.Fuel)
                rebuildEnergySettlements(vehicle.id, EnergyRecordType.Charge)
            }
            seedDemoMaintenance(vehicle, months, mileage, now, rnd)
        }
    }

    /** 演示车队的保养历史 */
    private suspend fun seedDemoMaintenance(
        vehicle: VehicleEntity,
        months: Int,
        finalMileage: Double,
        now: Long,
        rnd: java.util.Random
    ) {
        val items = dao.maintenanceItemsForVehicle(vehicle.id).ifEmpty { return }
        val dayMs = 24L * 60L * 60L * 1000L
        val intervalKm = if (vehicle.category == VehicleCategory.Motorcycle) 5_000.0 else 10_000.0
        val isCar = vehicle.category == VehicleCategory.Car
        var at = now - months * 30L * dayMs
        var mileage = finalMileage * 0.08
        var index = 0
        var monthsSinceInsurance = 12 // 让第一条不久就出现保险，避免首年空白
        while (mileage < finalMileage * 0.95) {
            mileage += intervalKm * (0.92 + rnd.nextDouble() * 0.22)
            val stepMonths = 7 + rnd.nextInt(4)
            at += 30L * dayMs * stepMonths
            monthsSinceInsurance += stepMonths
            if (at > now) break
            val item = items[rnd.nextInt(items.size)]
            val isInsurance = monthsSinceInsurance >= 12
            val isRepair = !isInsurance && rnd.nextDouble() < 0.18
            if (isInsurance) monthsSinceInsurance = 0
            val title = when {
                isInsurance -> "车辆保险"
                isRepair -> "故障检修"
                else -> item.name
            }
            dao.insertMaintenanceHistory(
                MaintenanceHistoryEntity(
                    id = "seed-hist-${vehicle.id}-$index",
                    vehicleId = vehicle.id,
                    itemId = item.id,
                    itemName = title,
                    recordName = title,
                    serviceMileage = mileage.coerceAtMost(finalMileage),
                    serviceAt = at,
                    note = when {
                        isInsurance -> "示例数据 · 年度保险"
                        isRepair -> "示例数据 · 故障检修"
                        else -> "示例数据 · 定期保养"
                    },
                    laborCost = if (isInsurance || isRepair) 0.0 else (120 + rnd.nextInt(280)).toDouble(),
                    partsCost = if (isInsurance || isRepair) 0.0 else (60 + rnd.nextInt(420)).toDouble(),
                    insuranceAmount = if (isInsurance) {
                        (if (isCar) 3200 + rnd.nextInt(2600) else 380 + rnd.nextInt(420)).toDouble()
                    } else 0.0,
                    repairAmount = if (isRepair) {
                        (if (isCar) 260 + rnd.nextInt(2600) else 120 + rnd.nextInt(600)).toDouble()
                    } else 0.0,
                )
            )
            index++
        }
    }

    suspend fun seedQinPlusDemoIfMissing(now: Long = System.currentTimeMillis()) {
        val vehicleId = "seed-qin-plus"
        inTransaction {
            if (dao.vehicleById(vehicleId) == null) {
                dao.upsertVehicle(
                    VehicleEntity(
                        id = vehicleId,
                        name = "比亚迪秦 PLUS",
                        model = "秦 PLUS DM-i",
                        plate = "京D00004",
                        category = VehicleCategory.Car,
                        energyType = EnergyType.PlugInHybrid,
                        transmissionType = TransmissionType.ElectricDrive,
                        fuelTankCapacityLiters = 48.0,
                        batteryCapacityKwh = 18.3,
                        defaultChargeLimitSoc = 100,
                        currentMileage = 8_600.0,
                        updatedAt = now,
                        manufacturingDate = now - 240L * 24L * 60L * 60L * 1000L
                    )
                )
            }
            if (dao.mileageRecordById("seed-qin-plus-mileage") == null) {
                dao.insertMileageRecord(
                    MileageRecordEntity(
                        id = "seed-qin-plus-mileage",
                        vehicleId = vehicleId,
                        mileage = 8_600.0,
                        note = "当前里程",
                        createdAt = now
                    )
                )
            }
            listOf(
                EnergyRecordInput(
                    type = EnergyRecordType.Fuel,
                    mileage = 8_000.0,
                    amount = 24.0,
                    orderAmount = 172.32,
                    paidAmount = 168.0,
                    isFull = true,
                    missedPrevious = false,
                    fuelGrade = "92",
                    unitPrice = 7.18,
                    note = "满油基准"
                ).toEntity("seed-qin-plus-fuel-1", vehicleId, 8_000.0, now - 20L * 24L * 60L * 60L * 1000L),
                EnergyRecordInput(
                    type = EnergyRecordType.Fuel,
                    mileage = 8_500.0,
                    amount = 22.0,
                    orderAmount = 157.96,
                    paidAmount = 153.0,
                    isFull = true,
                    missedPrevious = false,
                    fuelGrade = "92",
                    unitPrice = 7.18,
                    note = "混动通勤"
                ).toEntity("seed-qin-plus-fuel-2", vehicleId, 8_500.0, now - 4L * 24L * 60L * 60L * 1000L),
                EnergyRecordInput(
                    type = EnergyRecordType.Charge,
                    mileage = 8_400.0,
                    amount = 14.0,
                    orderAmount = 4.20,
                    paidAmount = 4.20,
                    isFull = true,
                    missedPrevious = false,
                    chargeMethod = "家充",
                    electricityPrice = 0.30,
                    chargeLimitSocSnapshot = 100,
                    note = "满电基准"
                ).toEntity("seed-qin-plus-charge-1", vehicleId, 8_400.0, now - 8L * 24L * 60L * 60L * 1000L),
                EnergyRecordInput(
                    type = EnergyRecordType.Charge,
                    mileage = 8_500.0,
                    amount = 13.2,
                    orderAmount = 3.96,
                    paidAmount = 3.96,
                    isFull = true,
                    missedPrevious = false,
                    chargeMethod = "家充",
                    electricityPrice = 0.30,
                    chargeLimitSocSnapshot = 100,
                    note = "夜间家充"
                ).toEntity("seed-qin-plus-charge-2", vehicleId, 8_500.0, now - 3L * 24L * 60L * 60L * 1000L)
            ).forEach { record ->
                if (dao.energyRecordById(record.id) == null) dao.insertEnergyRecord(record)
            }
            rebuildEnergySettlements(vehicleId, EnergyRecordType.Fuel)
            rebuildEnergySettlements(vehicleId, EnergyRecordType.Charge)
        }
        applyMaintenanceTemplate(vehicleId, now)
    }

    private suspend fun seedRecordsIfEmpty(now: Long) {
        if (dao.mileageRecordCount() > 0 || dao.energyRecordCount() > 0) return
        val vehicle = dao.latestVehicle() ?: return
        addMileageRecord(vehicle.id, vehicle.currentMileage, "首条里程", now = now)
    }

    suspend fun addVehicle(
        name: String,
        model: String,
        plate: String,
        category: VehicleCategory,
        energyType: EnergyType,
        transmissionType: TransmissionType,
        fuelTankCapacityLiters: Double?,
        batteryCapacityKwh: Double?,
        defaultChargeLimitSoc: Int?,
        currentMileage: Double,
        purchasePrice: Double = 0.0,
        purchaseTax: Double = 0.0,
        manufacturingDate: Long? = null,
        now: Long = System.currentTimeMillis()
    ): String {
        val id = UUID.randomUUID().toString()
        dao.upsertVehicle(
            VehicleEntity(
                id = id,
                name = name.ifBlank { "未命名车辆" },
                model = model.ifBlank { "未知型号" },
                plate = plate.ifBlank { "未填车牌" },
                category = category,
                energyType = energyType,
                transmissionType = transmissionType,
                fuelTankCapacityLiters = fuelTankCapacityLiters?.coerceAtLeast(0.0),
                batteryCapacityKwh = batteryCapacityKwh?.coerceAtLeast(0.0),
                defaultChargeLimitSoc = defaultChargeLimitSoc
                    ?.takeIf { energyType.supportsCharging }
                    ?.coerceIn(1, 100),
                currentMileage = currentMileage.coerceAtLeast(0.0),
                updatedAt = now,
                manufacturingDate = manufacturingDate,
                purchasePrice = purchasePrice.coerceAtLeast(0.0),
                purchaseTax = purchaseTax.coerceAtLeast(0.0)
            )
        )
        applyMaintenanceTemplate(id, now)
        return id
    }

    suspend fun updateVehicle(
        id: String,
        name: String,
        model: String,
        plate: String,
        category: VehicleCategory,
        energyType: EnergyType,
        transmissionType: TransmissionType,
        fuelTankCapacityLiters: Double?,
        batteryCapacityKwh: Double?,
        defaultChargeLimitSoc: Int?,
        currentMileage: Double,
        purchasePrice: Double = 0.0,
        purchaseTax: Double = 0.0,
        manufacturingDate: Long? = null,
        now: Long = System.currentTimeMillis()
    ) {
        val existing = dao.vehicleById(id) ?: return
        dao.updateVehicle(
            existing.copy(
                name = name.ifBlank { "未命名车辆" },
                model = model.ifBlank { "未知型号" },
                plate = plate.ifBlank { "未填车牌" },
                category = category,
                energyType = energyType,
                transmissionType = transmissionType,
                fuelTankCapacityLiters = fuelTankCapacityLiters?.coerceAtLeast(0.0),
                batteryCapacityKwh = batteryCapacityKwh?.coerceAtLeast(0.0),
                defaultChargeLimitSoc = defaultChargeLimitSoc
                    ?.takeIf { energyType.supportsCharging }
                    ?.coerceIn(1, 100),
                currentMileage = currentMileage.coerceAtLeast(0.0),
                updatedAt = now,
                manufacturingDate = manufacturingDate,
                purchasePrice = purchasePrice.coerceAtLeast(0.0),
                purchaseTax = purchaseTax.coerceAtLeast(0.0)
            )
        )
        syncMaintenanceTemplatesForVehicle(id, now)
    }

    suspend fun deleteVehicle(id: String) {
        inTransaction {
            dao.fitActivityIdsForVehicle(id).forEach { activityId ->
                dao.deleteFitSamples(activityId)
                dao.deleteFitTimerEvents(activityId)
                dao.deleteFitActivity(activityId)
            }
            performanceDao?.sessionsForVehicle(id)?.forEach { session ->
                performanceDao.deleteSamples(session.id)
                performanceDao.deleteSplits(session.id)
                performanceDao.deleteNoise(session.id)
                performanceDao.deleteJerks(session.id)
                performanceDao.deleteSession(session.id)
            }
            dao.deleteVehicle(id)
        }
    }

    suspend fun addMileageRecord(
        vehicleId: String,
        mileage: Double,
        note: String,
        roadFeeAmount: Double = 0.0,
        allowOdometerDecrease: Boolean = false,
        now: Long = System.currentTimeMillis()
    ): Boolean {
        val vehicle = dao.vehicleById(vehicleId) ?: return false
        val normalizedMileage = mileage.coerceAtLeast(0.0)
        dao.insertMileageRecord(
            MileageRecordEntity(
                id = UUID.randomUUID().toString(),
                vehicleId = vehicleId,
                mileage = normalizedMileage,
                note = note,
                createdAt = now,
                roadFeeAmount = roadFeeAmount.coerceAtLeast(0.0)
            )
        )
        val nextMileage = if (allowOdometerDecrease) normalizedMileage else maxOf(vehicle.currentMileage, normalizedMileage)
        dao.updateVehicle(vehicle.copy(currentMileage = nextMileage, updatedAt = now))
        return true
    }

    suspend fun addEnergyRecord(
        vehicleId: String,
        input: EnergyRecordInput,
        allowOdometerDecrease: Boolean = false,
        now: Long = System.currentTimeMillis()
    ): EnergySaveResult {
        val vehicle = dao.vehicleById(vehicleId) ?: error("车辆不存在，无法保存记录")
        validateEnergyInput(vehicle, input)
        val normalizedMileage = input.mileage.coerceAtLeast(0.0)
        val record = input.toEntity(UUID.randomUUID().toString(), vehicleId, normalizedMileage, now)
        var result: EnergySaveResult = EnergySaveResult.WaitingForFull
        inTransaction {
            dao.insertEnergyRecord(record)
            val nextMileage = if (allowOdometerDecrease) normalizedMileage else maxOf(vehicle.currentMileage, normalizedMileage)
            val nextChargeLimit = if (input.type == EnergyRecordType.Charge) {
                input.chargeLimitSocSnapshot
            } else {
                vehicle.defaultChargeLimitSoc
            }
            dao.updateVehicle(
                vehicle.copy(
                    currentMileage = nextMileage,
                    defaultChargeLimitSoc = nextChargeLimit,
                    updatedAt = now
                )
            )
            result = rebuildEnergySettlements(vehicleId, input.type)
        }
        return result
    }

    suspend fun applyMaintenanceTemplate(vehicleId: String, now: Long = System.currentTimeMillis()) {
        val vehicle = dao.vehicleById(vehicleId) ?: return
        if (dao.maintenanceCountForVehicle(vehicleId) > 0) return
        val items = maintenanceTemplatesFor(vehicle).map { template ->
            val itemId = UUID.randomUUID().toString()
            if (template.itemType == MaintenanceItemType.Periodic) {
                dao.insertMaintenanceHistory(
                    MaintenanceHistoryEntity(
                        id = UUID.randomUUID().toString(),
                        vehicleId = vehicleId,
                        itemId = itemId,
                        itemName = template.name,
                        serviceMileage = vehicle.currentMileage,
                        serviceAt = now,
                        note = "初始记录"
                    )
                )
            }
            val baseline = vehicle.manufacturingDate ?: vehicle.updatedAt.takeIf { it > 0L } ?: now
            MaintenanceItemEntity(
                id = itemId,
                vehicleId = vehicleId,
                name = template.name,
                intervalKm = template.intervalKm,
                intervalDays = template.intervalDays,
                lastServiceMileage = 0,
                lastServiceAt = baseline,
                updatedAt = now,
                itemType = template.itemType,
                baselineAt = baseline
            )
        }
        dao.upsertMaintenanceItems(items)
    }

    private suspend fun syncDefaultMaintenanceTemplates(now: Long = System.currentTimeMillis()) {
        dao.allVehicles().forEach { syncMaintenanceTemplatesForVehicle(it.id, now) }
    }

    private suspend fun syncMaintenanceTemplatesForVehicle(
        vehicleId: String,
        now: Long = System.currentTimeMillis()
    ) {
        val vehicle = dao.vehicleById(vehicleId) ?: return
        val templates = maintenanceTemplatesFor(vehicle)
        val templateByName = templates.associateBy { it.name }
        val existing = dao.maintenanceItemsForVehicle(vehicle.id)
        val existingByName = existing.associateBy { it.name }
        val usedIds = mutableSetOf<String>()
        val changes = templates.mapNotNull { template ->
            val aliasEntry = existing.firstOrNull { maintenanceNameAliases[it.name] == template.name }
            val current = existingByName[template.name] ?: aliasEntry
            if (current != null) {
                usedIds += current.id
                current.copy(
                    name = template.name,
                    intervalKm = if (template.itemType == MaintenanceItemType.Periodic) current.intervalKm else 0,
                    intervalDays = when {
                        template.itemType == MaintenanceItemType.RepairInspection -> 0
                        template.itemType.isInsurance -> current.intervalDays.takeIf { it > 0 } ?: template.intervalDays
                        else -> current.intervalDays
                    },
                    itemType = template.itemType,
                    baselineAt = current.baselineAt.takeIf { it > 0L }
                        ?: vehicle.manufacturingDate ?: vehicle.updatedAt.takeIf { it > 0L } ?: now,
                    updatedAt = now
                )
            } else {
                val baseline = vehicle.manufacturingDate ?: vehicle.updatedAt.takeIf { it > 0L } ?: now
                MaintenanceItemEntity(
                    id = UUID.randomUUID().toString(),
                    vehicleId = vehicle.id,
                    name = template.name,
                    intervalKm = template.intervalKm,
                    intervalDays = template.intervalDays,
                    lastServiceMileage = vehicle.currentMileage.roundToInt(),
                    lastServiceAt = baseline,
                    updatedAt = now,
                    itemType = template.itemType,
                    baselineAt = baseline
                )
            }
        }
        if (changes.isNotEmpty()) dao.upsertMaintenanceItems(changes)
        existing
            .filter { it.name in builtInMaintenanceNames }
            .filter { it.id !in usedIds }
            .filter { it.name !in templateByName }
            .forEach { dao.deleteMaintenanceItem(it.id) }
    }

    suspend fun addCustomMaintenanceItem(
        vehicleId: String,
        name: String,
        intervalKm: Int,
        intervalDays: Int,
        now: Long = System.currentTimeMillis()
    ) {
        val vehicle = dao.vehicleById(vehicleId) ?: return
        dao.upsertMaintenanceItem(
            MaintenanceItemEntity(
                id = UUID.randomUUID().toString(),
                vehicleId = vehicleId,
                name = name.ifBlank { "自定义保养" },
                intervalKm = intervalKm.coerceAtLeast(1),
                intervalDays = intervalDays.coerceAtLeast(1),
                lastServiceMileage = vehicle.currentMileage.roundToInt(),
                lastServiceAt = vehicle.manufacturingDate ?: now,
                updatedAt = now
            )
        )
    }

    suspend fun updateMaintenanceItem(id: String, name: String, intervalKm: Int, intervalDays: Int) {
        val existing = dao.maintenanceItemById(id) ?: return
        dao.upsertMaintenanceItem(existing.copy(
            name = if (existing.itemType.isProtected) existing.name else name.ifBlank { "自定义保养" },
            intervalKm = when (existing.itemType) {
                MaintenanceItemType.Periodic -> intervalKm.coerceAtLeast(1)
                else -> 0
            },
            intervalDays = when {
                existing.itemType.isInsurance -> intervalDays.coerceAtLeast(1)
                existing.itemType == MaintenanceItemType.RepairInspection -> 0
                else -> intervalDays.coerceAtLeast(1)
            },
            updatedAt = System.currentTimeMillis()
        ))
    }

    suspend fun deleteMaintenanceItem(id: String) {
        val item = dao.maintenanceItemById(id) ?: return
        require(!item.itemType.isProtected) { "内置项目不可删除" }
        inTransaction {
            dao.deleteMaintenanceHistoryForItem(id)
            dao.deleteMaintenanceItem(id)
        }
    }

    suspend fun undoMaintenance(itemId: String, previousMileage: Int, previousAt: Long) {
        val item = dao.maintenanceItemById(itemId) ?: return
        inTransaction {
            dao.historyForItem(itemId).maxByOrNull { it.serviceAt }?.let {
                dao.deleteMaintenanceHistory(it.id)
            }
            dao.upsertMaintenanceItem(item.copy(
                lastServiceMileage = previousMileage,
                lastServiceAt = previousAt,
                lastPartNumber = latestRemainingHistory(itemId)?.partNumber.orEmpty(),
                updatedAt = System.currentTimeMillis()
            ))
        }
    }

    private suspend fun latestRemainingHistory(itemId: String): MaintenanceHistoryEntity? =
        dao.historyForItem(itemId).maxWithOrNull(
            compareBy<MaintenanceHistoryEntity> { it.serviceAt }.thenBy { it.id }
        )

    fun observeHistoryForItem(itemId: String): Flow<List<MaintenanceHistoryEntity>> =
        dao.observeHistoryForItem(itemId)

    suspend fun updateMaintenanceHistory(id: String, serviceMileage: Double, serviceAt: Long, note: String,
                                         partNumber: String = "", partsCost: Double = 0.0, laborCost: Double = 0.0) {
        val existing = dao.maintenanceHistoryById(id) ?: return
        inTransaction {
            dao.insertMaintenanceHistory(existing.copy(
                serviceMileage = serviceMileage,
                serviceAt = serviceAt,
                note = note,
                partNumber = partNumber,
                partsCost = partsCost.coerceAtLeast(0.0),
                laborCost = laborCost.coerceAtLeast(0.0)
            ))
            val latest = latestRemainingHistory(existing.itemId) ?: return@inTransaction
            val item = dao.maintenanceItemById(existing.itemId) ?: return@inTransaction
            dao.upsertMaintenanceItem(item.copy(
                lastServiceMileage = latest.serviceMileage.roundToInt(),
                lastServiceAt = latest.serviceAt,
                updatedAt = System.currentTimeMillis(),
                lastPartNumber = latest.partNumber
            ))
        }
    }

    suspend fun completeMaintenance(
        itemId: String,
        serviceMileage: Int,
        note: String,
        laborCost: Double = 0.0,
        partsCost: Double = 0.0,
        partNumber: String = "",
        now: Long = System.currentTimeMillis()
    ) {
        val item = dao.maintenanceItemById(itemId) ?: return
        val mileage = serviceMileage.coerceAtLeast(0).toDouble()
        inTransaction {
            dao.insertMaintenanceHistory(
                MaintenanceHistoryEntity(
                    id = UUID.randomUUID().toString(),
                    vehicleId = item.vehicleId,
                    itemId = item.id,
                    itemName = item.name,
                    serviceMileage = mileage,
                    serviceAt = now,
                    note = note,
                    laborCost = laborCost.coerceAtLeast(0.0),
                    partsCost = partsCost.coerceAtLeast(0.0),
                    partNumber = partNumber
                )
            )
            dao.upsertMaintenanceItem(
                item.copy(
                    lastServiceMileage = mileage.roundToInt(),
                    lastServiceAt = now,
                    updatedAt = now,
                    lastPartNumber = partNumber
                )
            )
            val vehicle = dao.vehicleById(item.vehicleId) ?: return@inTransaction
            dao.updateVehicle(vehicle.copy(currentMileage = maxOf(vehicle.currentMileage, mileage), updatedAt = now))
        }
    }

    suspend fun completeInsurance(itemId: String, serviceAt: Long, expiresAt: Long, amount: Double, note: String) {
        val item = dao.maintenanceItemById(itemId) ?: return
        require(item.itemType.isInsurance) { "该项目不是保险" }
        val vehicle = dao.vehicleById(item.vehicleId) ?: return
        inTransaction {
            dao.insertMaintenanceHistory(
                MaintenanceHistoryEntity(
                    id = UUID.randomUUID().toString(), vehicleId = item.vehicleId, itemId = item.id,
                    itemName = item.name, recordName = item.name, serviceMileage = vehicle.currentMileage,
                    serviceAt = serviceAt, note = note, insuranceAmount = amount.coerceAtLeast(0.0), expiresAt = expiresAt
                )
            )
            dao.upsertMaintenanceItem(item.copy(lastServiceAt = serviceAt, baselineAt = expiresAt, updatedAt = System.currentTimeMillis()))
        }
    }

    suspend fun addRepairInspection(
        itemId: String, recordName: String, serviceAt: Long, mileage: Double,
        amount: Double, laborCost: Double, note: String
    ) {
        val item = dao.maintenanceItemById(itemId) ?: return
        require(item.itemType == MaintenanceItemType.RepairInspection) { "该项目不是维修/检查" }
        require(recordName.isNotBlank()) { "请输入检修名称" }
        inTransaction {
            dao.insertMaintenanceHistory(
                MaintenanceHistoryEntity(
                    id = UUID.randomUUID().toString(), vehicleId = item.vehicleId, itemId = item.id,
                    itemName = item.name, recordName = recordName.trim(), serviceMileage = mileage.coerceAtLeast(0.0),
                    serviceAt = serviceAt, note = note, repairAmount = amount.coerceAtLeast(0.0),
                    laborCost = laborCost.coerceAtLeast(0.0)
                )
            )
            val vehicle = dao.vehicleById(item.vehicleId) ?: return@inTransaction
            dao.updateVehicle(vehicle.copy(currentMileage = maxOf(vehicle.currentMileage, mileage), updatedAt = serviceAt))
        }
    }

    suspend fun updateSpecialMaintenanceHistory(
        id: String, recordName: String, serviceAt: Long, mileage: Double,
        amount: Double, laborCost: Double, note: String, expiresAt: Long? = null
    ) {
        val existing = dao.maintenanceHistoryById(id) ?: return
        val item = dao.maintenanceItemById(existing.itemId) ?: return
        require(item.itemType != MaintenanceItemType.Periodic)
        if (item.itemType.isInsurance) require(expiresAt != null && expiresAt > serviceAt) {
            "保险到期时间必须晚于缴费时间"
        }
        inTransaction {
            dao.insertMaintenanceHistory(existing.copy(
                recordName = if (item.itemType == MaintenanceItemType.RepairInspection) recordName.trim().ifBlank { error("请输入检修名称") } else item.name,
                serviceAt = serviceAt,
                serviceMileage = mileage.coerceAtLeast(0.0),
                insuranceAmount = if (item.itemType.isInsurance) amount.coerceAtLeast(0.0) else 0.0,
                repairAmount = if (item.itemType == MaintenanceItemType.RepairInspection) amount.coerceAtLeast(0.0) else 0.0,
                laborCost = if (item.itemType == MaintenanceItemType.RepairInspection) laborCost.coerceAtLeast(0.0) else 0.0,
                note = note,
                expiresAt = if (item.itemType.isInsurance) expiresAt else null
            ))
            if (item.itemType.isInsurance) {
                val latest = dao.historyForItem(item.id).maxByOrNull { it.serviceAt }
                dao.upsertMaintenanceItem(item.copy(
                    lastServiceAt = latest?.serviceAt ?: item.lastServiceAt,
                    baselineAt = latest?.expiresAt ?: item.baselineAt,
                    updatedAt = System.currentTimeMillis()
                ))
            }
        }
    }

    suspend fun deleteMaintenanceHistory(id: String) {
        val history = dao.maintenanceHistoryById(id) ?: return
        val item = dao.maintenanceItemById(history.itemId) ?: return
        inTransaction {
            dao.deleteMaintenanceHistory(id)
            val latest = latestRemainingHistory(item.id)
            val vehicle = dao.vehicleById(item.vehicleId)
            val initialAt = vehicle?.manufacturingDate ?: vehicle?.updatedAt ?: item.lastServiceAt
            val resetMileage = vehicle?.currentMileage?.roundToInt() ?: item.lastServiceMileage
            dao.upsertMaintenanceItem(item.copy(
                lastServiceMileage = latest?.serviceMileage?.roundToInt() ?: resetMileage,
                lastServiceAt = latest?.serviceAt ?: initialAt,
                baselineAt = if (item.itemType.isInsurance) latest?.expiresAt ?: initialAt else item.baselineAt,
                lastPartNumber = latest?.partNumber.orEmpty(),
                updatedAt = System.currentTimeMillis()
            ))
        }
    }

    suspend fun deleteMileageRecord(id: String) {
        val record = dao.mileageRecordById(id) ?: return
        dao.deleteMileageRecord(id)
        refreshVehicleMileage(record.vehicleId, record.mileage)
    }

    suspend fun deleteEnergyRecord(id: String) {
        val record = dao.energyRecordById(id) ?: return
        inTransaction {
            dao.deleteEnergyRecord(id)
            rebuildEnergySettlements(record.vehicleId, record.type)
            if (record.type == EnergyRecordType.Charge) refreshDefaultChargeLimit(record.vehicleId)
            refreshVehicleMileage(record.vehicleId, record.mileage)
        }
    }

    suspend fun updateMileageRecord(id: String, mileage: Double, createdAt: Long, note: String, roadFeeAmount: Double = 0.0) {
        val existing = dao.mileageRecordById(id) ?: return
        dao.insertMileageRecord(existing.copy(
            mileage = mileage.coerceAtLeast(0.0),
            note = note,
            createdAt = createdAt,
            roadFeeAmount = roadFeeAmount.coerceAtLeast(0.0)
        ))
        refreshVehicleMileage(existing.vehicleId, existing.mileage)
    }

    suspend fun updateEnergyRecord(id: String, input: EnergyRecordInput, createdAt: Long): EnergySaveResult {
        val existing = dao.energyRecordById(id) ?: error("记录不存在，无法更新")
        val vehicle = dao.vehicleById(existing.vehicleId) ?: error("车辆不存在，无法更新记录")
        validateEnergyInput(vehicle, input)
        var result: EnergySaveResult = EnergySaveResult.WaitingForFull
        inTransaction {
            dao.insertEnergyRecord(input.toEntity(existing.id, existing.vehicleId, input.mileage.coerceAtLeast(0.0), createdAt))
            if (input.type == existing.type) {
                result = rebuildEnergySettlements(existing.vehicleId, input.type)
            } else {
                rebuildEnergySettlements(existing.vehicleId, existing.type)
                result = rebuildEnergySettlements(existing.vehicleId, input.type)
            }
            if (existing.type == EnergyRecordType.Charge || input.type == EnergyRecordType.Charge) {
                refreshDefaultChargeLimit(existing.vehicleId)
            }
            refreshVehicleMileage(existing.vehicleId, existing.mileage)
        }
        return result
    }

    suspend fun clearAllData() {
        performanceDao?.let {
            it.deleteAllSamples(); it.deleteAllSplits(); it.deleteAllNoise(); it.deleteAllJerks(); it.deleteAllSessions()
        }
        dao.deleteAllFitSamples()
        dao.deleteAllFitTimerEvents()
        dao.deleteAllFitActivities()
        dao.deleteAllVehicles()
    }

    private suspend fun refreshVehicleMileage(vehicleId: String, previousRecordMileage: Double? = null) {
        val vehicle = dao.vehicleById(vehicleId) ?: return
        val mileage = (
            dao.mileageRecordsForVehicle(vehicleId).map { it.mileage } +
                dao.allEnergyRecordsForVehicle(vehicleId).map { it.mileage }
            ).maxOrNull()
        val nextMileage = when {
            mileage == null -> vehicle.currentMileage
            previousRecordMileage != null && previousRecordMileage >= vehicle.currentMileage -> mileage
            else -> maxOf(vehicle.currentMileage, mileage)
        }
        dao.updateVehicle(vehicle.copy(currentMileage = nextMileage, updatedAt = System.currentTimeMillis()))
    }

    private suspend fun refreshDefaultChargeLimit(vehicleId: String) {
        val vehicle = dao.vehicleById(vehicleId) ?: return
        if (!vehicle.energyType.supportsCharging) return
        val latestLimit = dao.energyRecordsForVehicle(vehicleId, EnergyRecordType.Charge)
            .maxWithOrNull(compareBy<EnergyRecordEntity> { it.createdAt }.thenBy { it.id })
            ?.chargeLimitSocSnapshot
            ?: 100
        dao.updateVehicle(
            vehicle.copy(
                defaultChargeLimitSoc = latestLimit.coerceIn(1, 100),
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    private suspend fun rebuildEnergySettlements(vehicleId: String, type: EnergyRecordType): EnergySaveResult {
        val build = buildEnergySettlements(vehicleId, type, dao.energyRecordsForVehicle(vehicleId, type))
        dao.deleteEnergySettlements(vehicleId, type)
        if (build.settlements.isNotEmpty()) dao.upsertEnergySettlements(build.settlements)
        return build.latestResult
    }

    suspend fun getAllVehicles(): List<VehicleEntity> = dao.observeVehicles().first()
    suspend fun getAllMileageRecords(): List<MileageRecordEntity> = dao.allMileageRecords()
    suspend fun getAllEnergyRecords(): List<EnergyRecordEntity> = dao.allEnergyRecords()

    /** 能源结算 */
    suspend fun getAllEnergySettlements(): List<EnergySettlementEntity> = dao.observeEnergySettlements().first()
    suspend fun getAllMaintenanceItems(): List<MaintenanceItemEntity> = dao.observeMaintenanceItems().first()
    suspend fun getAllMaintenanceHistory(): List<MaintenanceHistoryEntity> = dao.observeAllHistory().first()
    suspend fun getAllFitActivities() = dao.allFitActivities()
    suspend fun getAllFitSamples() = dao.allFitSamples()
    suspend fun getAllFitEvents() = dao.allFitTimerEvents()
    suspend fun getAllPerformanceSessions() = performanceDao?.allSessions().orEmpty()
    suspend fun getAllPerformanceSamples() = performanceDao?.allSamples().orEmpty()
    suspend fun getAllPerformanceSplits() = performanceDao?.allSplits().orEmpty()
    suspend fun getAllPerformanceNoise() = performanceDao?.allNoise().orEmpty()
    suspend fun getAllPerformanceJerks() = performanceDao?.allJerks().orEmpty()

    suspend fun importData(data: ExportedData) {
        inTransaction {
            performanceDao?.let {
                it.deleteAllSamples(); it.deleteAllSplits(); it.deleteAllNoise(); it.deleteAllJerks(); it.deleteAllSessions()
            }
            dao.deleteAllFitSamples(); dao.deleteAllFitTimerEvents(); dao.deleteAllFitActivities()
            dao.deleteAllVehicles()
            data.vehicles.forEach { dao.upsertVehicle(it) }
            data.mileageRecords.forEach { dao.insertMileageRecord(it) }
            data.energyRecords.forEach { dao.insertEnergyRecord(it) }
            data.maintenanceItems.forEach { dao.upsertMaintenanceItem(it) }
            data.maintenanceHistory.forEach { dao.insertMaintenanceHistory(it) }
            data.fitActivities.forEach { dao.upsertFitActivity(it) }
            data.fitSamples.forEach { dao.insertFitSample(it) }
            data.fitEvents.forEach { dao.insertFitTimerEvent(it) }
            performanceDao?.let { performance ->
                data.performanceSessions.forEach { performance.upsertSession(it) }
                data.performanceSamples.forEach { performance.insertSample(it) }
                data.performanceSplits.forEach { performance.upsertSplit(it) }
                data.performanceNoise.forEach { performance.insertNoise(it) }
                data.performanceJerks.forEach { performance.upsertJerk(it) }
            }
            data.energyRecords
                .groupBy { it.vehicleId to it.type }
                .keys
                .forEach { (vehicleId, type) -> rebuildEnergySettlements(vehicleId, type) }
        }
        data.vehicles.forEach { syncMaintenanceTemplatesForVehicle(it.id) }
    }

    private fun validateEnergyInput(vehicle: VehicleEntity, input: EnergyRecordInput) {
        require(input.amount > 0.0) { "补能量必须大于 0" }
        require(input.orderAmount >= 0.0) { "订单金额不能为负数" }
        require(input.paidAmount >= 0.0) { "实付金额不能为负数" }
        if (input.type == EnergyRecordType.Fuel) {
            require(vehicle.energyType.supportsFuel) { "当前动力类型不支持加油" }
            require((vehicle.fuelTankCapacityLiters ?: 0.0) > 0.0) { "请先填写油箱容量" }
            require(input.unitPrice >= 0.0) { "油价不能为负数" }
        } else {
            require(vehicle.energyType.supportsCharging) { "当前动力类型不支持外接充电" }
            require((vehicle.batteryCapacityKwh ?: 0.0) > 0.0) { "请先填写电池容量" }
            require(input.electricityPrice >= 0.0) { "电价不能为负数" }
            require(input.serviceFee >= 0.0) { "服务费不能为负数" }
            require(input.chargeLimitSocSnapshot in 1..100) { "充电上限必须为 1-100" }
        }
    }

    private fun EnergyRecordInput.toEntity(
        id: String,
        vehicleId: String,
        normalizedMileage: Double,
        createdAt: Long
    ) = EnergyRecordEntity(
        id = id,
        vehicleId = vehicleId,
        type = type,
        mileage = normalizedMileage,
        amount = amount,
        orderAmount = orderAmount,
        paidAmount = paidAmount,
        isFull = isFull,
        missedPrevious = missedPrevious,
        fuelGrade = fuelGrade,
        unitPrice = unitPrice,
        chargeMethod = chargeMethod,
        electricityPrice = electricityPrice,
        serviceFee = serviceFee,
        chargeLimitSocSnapshot = chargeLimitSocSnapshot,
        note = note,
        createdAt = createdAt
    )
}
