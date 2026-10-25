package com.cheku.app.domain

import com.cheku.app.data.EnergyRecordEntity
import com.cheku.app.data.EnergyRecordType
import com.cheku.app.data.EnergySettlementEntity
import com.cheku.app.data.SettlementStatus
import kotlin.math.roundToInt

data class EnergySettlementBuild(
    val settlements: List<EnergySettlementEntity>,
    val latestResult: EnergySaveResult
)

sealed interface EnergySaveResult {
    data object BaselineCreated : EnergySaveResult
    data class BaselineReset(val reason: String) : EnergySaveResult
    data object WaitingForFull : EnergySaveResult
    data class Settled(val consumptionPer100Km: Double, val costPerKm: Double) : EnergySaveResult
    data class Invalid(val reason: String) : EnergySaveResult
}

fun buildEnergySettlements(
    vehicleId: String,
    type: EnergyRecordType,
    sourceRecords: List<EnergyRecordEntity>
): EnergySettlementBuild {
    val records = sourceRecords
        .filter { it.vehicleId == vehicleId && it.type == type }
        .sortedWith(compareBy<EnergyRecordEntity> { it.createdAt }.thenBy { it.id })
    val settlements = mutableListOf<EnergySettlementEntity>()
    var anchor: EnergyRecordEntity? = null
    val pending = mutableListOf<EnergyRecordEntity>()
    var latestResult: EnergySaveResult = EnergySaveResult.WaitingForFull

    fun addInvalid(end: EnergyRecordEntity, reason: String) {
        val start = anchor
        settlements += settlement(
            vehicleId = vehicleId,
            type = type,
            status = SettlementStatus.Invalid,
            start = start,
            end = end,
            included = pending + end,
            reason = reason
        )
        latestResult = EnergySaveResult.Invalid(reason)
    }

    for (record in records) {
        if (record.amount <= 0.0) {
            if (anchor != null) addInvalid(record, "补能量必须大于 0")
            pending.clear()
            anchor = if (record.isFull && !record.missedPrevious) record else null
            continue
        }

        if (record.missedPrevious) {
            if (anchor != null) addInvalid(record, "此前存在漏记")
            pending.clear()
            anchor = if (record.isFull) record else null
            latestResult = if (anchor != null) {
                EnergySaveResult.BaselineReset("此前存在漏记")
            } else {
                EnergySaveResult.Invalid("此前存在漏记")
            }
            continue
        }

        val currentAnchor = anchor
        if (currentAnchor == null) {
            if (record.isFull) {
                anchor = record
                pending.clear()
                latestResult = EnergySaveResult.BaselineCreated
            } else {
                latestResult = EnergySaveResult.WaitingForFull
            }
            continue
        }

        if (!record.isFull) {
            pending += record
            latestResult = EnergySaveResult.WaitingForFull
            continue
        }

        if (type == EnergyRecordType.Charge &&
            currentAnchor.chargeLimitSocSnapshot != record.chargeLimitSocSnapshot
        ) {
            addInvalid(record, "充电上限已变更")
            anchor = record
            pending.clear()
            latestResult = EnergySaveResult.BaselineReset("充电上限已变更")
            continue
        }

        val distance = record.mileage - currentAnchor.mileage
        val included = pending + record
        if (distance <= 0) {
            addInvalid(record, "结算里程未增加")
            anchor = record
            pending.clear()
            continue
        }

        val totalAmount = included.sumOf { it.amount }
        if (totalAmount <= 0.0) {
            addInvalid(record, "区间补能量无效")
            anchor = record
            pending.clear()
            continue
        }

        val totalOrder = included.sumOf { it.orderAmount }
        val totalPaid = included.sumOf { it.paidAmount }
        val consumption = roundOne(totalAmount / distance * 100.0)
        val costPerKm = roundFour(totalPaid / distance)
        settlements += EnergySettlementEntity(
            id = settlementId(type, currentAnchor.id, record.id),
            vehicleId = vehicleId,
            type = type,
            status = SettlementStatus.Valid,
            startRecordId = currentAnchor.id,
            endRecordId = record.id,
            startMileage = currentAnchor.mileage,
            endMileage = record.mileage,
            distanceKm = distance,
            totalAmount = totalAmount,
            totalOrderAmount = totalOrder,
            totalPaidAmount = totalPaid,
            consumptionPer100Km = consumption,
            costPerKm = costPerKm,
            chargeLimitSoc = record.chargeLimitSocSnapshot,
            createdAt = record.createdAt
        )
        latestResult = EnergySaveResult.Settled(consumption, costPerKm)
        anchor = record
        pending.clear()
    }

    anchor?.let { start ->
        settlements += EnergySettlementEntity(
            id = settlementId(type, start.id, null),
            vehicleId = vehicleId,
            type = type,
            status = SettlementStatus.Waiting,
            startRecordId = start.id,
            endRecordId = null,
            startMileage = start.mileage,
            endMileage = null,
            distanceKm = null,
            totalAmount = pending.sumOf { it.amount },
            totalOrderAmount = pending.sumOf { it.orderAmount },
            totalPaidAmount = pending.sumOf { it.paidAmount },
            consumptionPer100Km = null,
            costPerKm = null,
            chargeLimitSoc = start.chargeLimitSocSnapshot,
            createdAt = records.lastOrNull()?.createdAt ?: start.createdAt
        )
    }

    return EnergySettlementBuild(settlements, latestResult)
}

private fun settlement(
    vehicleId: String,
    type: EnergyRecordType,
    status: SettlementStatus,
    start: EnergyRecordEntity?,
    end: EnergyRecordEntity,
    included: List<EnergyRecordEntity>,
    reason: String
) = EnergySettlementEntity(
    id = settlementId(type, start?.id, end.id),
    vehicleId = vehicleId,
    type = type,
    status = status,
    startRecordId = start?.id,
    endRecordId = end.id,
    startMileage = start?.mileage ?: end.mileage,
    endMileage = end.mileage,
    distanceKm = start?.let { end.mileage - it.mileage },
    totalAmount = included.sumOf { it.amount },
    totalOrderAmount = included.sumOf { it.orderAmount },
    totalPaidAmount = included.sumOf { it.paidAmount },
    consumptionPer100Km = null,
    costPerKm = null,
    chargeLimitSoc = end.chargeLimitSocSnapshot,
    invalidReason = reason,
    createdAt = end.createdAt
)

private fun settlementId(type: EnergyRecordType, startId: String?, endId: String?): String =
    "${type.name}:${startId ?: "none"}:${endId ?: "waiting"}"

private fun roundOne(value: Double): Double = (value * 10.0).roundToInt() / 10.0
private fun roundFour(value: Double): Double = (value * 10_000.0).roundToInt() / 10_000.0
