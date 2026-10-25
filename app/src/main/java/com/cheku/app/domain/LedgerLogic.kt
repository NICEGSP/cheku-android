package com.cheku.app.domain

import com.cheku.app.data.EnergyType
import com.cheku.app.data.MaintenanceItemEntity
import com.cheku.app.data.MaintenanceStatus
import com.cheku.app.data.MaintenanceItemType
import com.cheku.app.data.TransmissionType
import com.cheku.app.data.VehicleCategory
import com.cheku.app.data.VehicleEntity
import com.cheku.app.data.supportsFuel
import kotlin.math.roundToInt

private const val DAY_MS = 24L * 60L * 60L * 1000L

data class MaintenanceTemplate(
    val name: String,
    val intervalKm: Int,
    val intervalDays: Int,
    val itemType: MaintenanceItemType = MaintenanceItemType.Periodic
)

data class MaintenanceSnapshot(
    val item: MaintenanceItemEntity,
    val status: MaintenanceStatus,
    val remainingKm: Int,
    val remainingDays: Int,
    val dueMileage: Int,
    val dueAt: Long
)

fun maintenanceTemplatesFor(vehicle: VehicleEntity): List<MaintenanceTemplate> {
    val isFuel = vehicle.energyType == EnergyType.Fuel
    val isElectric = vehicle.energyType == EnergyType.Electric
    val isHybrid = vehicle.energyType == EnergyType.Hybrid || vehicle.energyType == EnergyType.PlugInHybrid
    val isRangeExtender = vehicle.energyType == EnergyType.RangeExtender
    val hasEngine = vehicle.energyType.supportsFuel

    val items = mutableListOf<MaintenanceTemplate>()

    when (vehicle.category) {
        VehicleCategory.Car -> {
            if (hasEngine) {
                items.add(MaintenanceTemplate("发动机机油", 10_000, 365))
                items.add(MaintenanceTemplate("机油滤清器", 10_000, 365))
                items.add(MaintenanceTemplate("发动机空气滤芯", 20_000, 365))
                items.add(MaintenanceTemplate("火花塞", 60_000, 1_095))
                items.add(MaintenanceTemplate("燃油滤清器", 40_000, 730))
            }
            items.add(MaintenanceTemplate("空调滤芯", 20_000, 730))
            items.add(MaintenanceTemplate("制动液", 40_000, 730))
            items.add(MaintenanceTemplate("制动片/制动盘检查", 10_000, 365))
            items.add(MaintenanceTemplate("轮胎检查/换位", 10_000, 180))
            if (isElectric) {
                items.add(MaintenanceTemplate("电池热管理冷却液检查", 40_000, 730))
                items.add(MaintenanceTemplate("高压电池健康检查", 20_000, 365))
                items.add(MaintenanceTemplate("制动卡钳清洁润滑", 20_000, 365))
            }
            if (isHybrid || isRangeExtender) {
                items.add(MaintenanceTemplate("防冻液/冷却液", 60_000, 1_095))
                items.add(MaintenanceTemplate("动力电池健康检查", 20_000, 365))
            }
            if (hasEngine) {
                items.add(MaintenanceTemplate("防冻液/冷却液", 60_000, 1_095))
            }
            when (vehicle.transmissionType) {
                TransmissionType.Automatic -> items.add(MaintenanceTemplate("自动变速箱油", 60_000, 1_095))
                TransmissionType.Manual -> items.add(MaintenanceTemplate("手动变速箱油/离合器检查", 60_000, 1_095))
                TransmissionType.Cvt -> items.add(MaintenanceTemplate("CVT变速箱油", 40_000, 730))
                TransmissionType.ElectricDrive -> items.add(MaintenanceTemplate("电驱减速器油检查", 60_000, 1_095))
                TransmissionType.None -> {}
            }
        }

        VehicleCategory.Motorcycle -> {
            if (isFuel || isHybrid || isRangeExtender) {
                items.add(MaintenanceTemplate("发动机机油", 5_000, 180))
                items.add(MaintenanceTemplate("机油滤清器", 10_000, 365))
                items.add(MaintenanceTemplate("空气滤芯", 10_000, 365))
                items.add(MaintenanceTemplate("火花塞", 20_000, 730))
                items.add(MaintenanceTemplate("冷却液", 24_000, 730))
                items.add(MaintenanceTemplate("12V电瓶检查", 10_000, 365))
                items.add(MaintenanceTemplate("链条清洁/润滑", 800, 30))
                items.add(MaintenanceTemplate("链条张紧检查", 3_000, 60))
                if (vehicle.transmissionType == TransmissionType.Cvt) {
                    items.add(MaintenanceTemplate("传动皮带检查", 10_000, 365))
                    items.add(MaintenanceTemplate("普利珠检查", 15_000, 730))
                }
            }
            items.add(MaintenanceTemplate("制动液", 20_000, 730))
            items.add(MaintenanceTemplate("制动片检查", 5_000, 180))
            items.add(MaintenanceTemplate("轮胎检查", 5_000, 180))
        }

        VehicleCategory.ElectricMotorcycle -> {
            items.add(MaintenanceTemplate("动力电池健康检查", 5_000, 180))
            items.add(MaintenanceTemplate("制动片检查", 3_000, 180))
            items.add(MaintenanceTemplate("制动液", 20_000, 730))
            items.add(MaintenanceTemplate("轮胎检查", 3_000, 180))
            items.add(MaintenanceTemplate("充电口检查", 5_000, 365))
            items.add(MaintenanceTemplate("电机/轮毂轴承检查", 10_000, 365))
            items.add(MaintenanceTemplate("前叉/减震检查", 10_000, 365))
        }

        VehicleCategory.ElectricBike -> {
            items.add(MaintenanceTemplate("轮胎检查", 1_000, 90))
            items.add(MaintenanceTemplate("制动片/刹车线检查", 1_000, 90))
            items.add(MaintenanceTemplate("电池健康检查", 2_000, 180))
            items.add(MaintenanceTemplate("充电口检查", 2_000, 180))
            items.add(MaintenanceTemplate("电机/控制器检查", 5_000, 365))
            items.add(MaintenanceTemplate("车架螺丝检查", 1_000, 90))
        }

        VehicleCategory.Bicycle -> {
            items.add(MaintenanceTemplate("链条清洁/润滑", 300, 30))
            items.add(MaintenanceTemplate("制动片/刹车线检查", 500, 90))
            items.add(MaintenanceTemplate("轮胎检查", 500, 60))
            items.add(MaintenanceTemplate("变速系统调试", 1_000, 180))
            items.add(MaintenanceTemplate("中轴/花鼓检查", 3_000, 365))
        }
    }

    items += MaintenanceTemplate("商业险", 0, 365, MaintenanceItemType.CommercialInsurance)
    items += MaintenanceTemplate("交强险", 0, 365, MaintenanceItemType.CompulsoryInsurance)
    items += MaintenanceTemplate("维修/检查", 0, 0, MaintenanceItemType.RepairInspection)
    return items.distinctBy { it.itemType to it.name }
}

fun evaluateMaintenance(
    item: MaintenanceItemEntity,
    currentMileage: Int,
    now: Long,
    warnKm: Int = 500,
    warnDays: Int = 14
): MaintenanceSnapshot {
    val dueMileage = item.lastServiceMileage + item.intervalKm
    val dueAt = if (item.itemType.isInsurance && item.baselineAt > item.lastServiceAt) {
        item.baselineAt
    } else item.lastServiceAt + item.intervalDays * DAY_MS
    val remainingKm = dueMileage - currentMileage
    val remainingDays = ((dueAt - now) / DAY_MS.toDouble()).roundToInt()
    val mileageOverdue = item.intervalKm > 0 && remainingKm < 0
    val daysOverdue = item.intervalDays > 0 && remainingDays < 0
    val mileageDueSoon = item.intervalKm > 0 && remainingKm <= warnKm
    val daysDueSoon = item.intervalDays > 0 && remainingDays <= warnDays
    val status = when {
        item.itemType == MaintenanceItemType.RepairInspection -> MaintenanceStatus.Normal
        mileageOverdue || daysOverdue -> MaintenanceStatus.Overdue
        mileageDueSoon || daysDueSoon -> MaintenanceStatus.DueSoon
        else -> MaintenanceStatus.Normal
    }
    return MaintenanceSnapshot(item, status, remainingKm, remainingDays, dueMileage, dueAt)
}
