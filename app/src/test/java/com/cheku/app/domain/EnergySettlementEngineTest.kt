package com.cheku.app.domain

import com.cheku.app.data.EnergyRecordEntity
import com.cheku.app.data.EnergyRecordType
import com.cheku.app.data.SettlementStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EnergySettlementEngineTest {
    @Test
    fun fuelUsesPartialAndEndingFullRecordsButExcludesStartingFull() {
        val build = buildEnergySettlements(
            vehicleId = "v1",
            type = EnergyRecordType.Fuel,
            sourceRecords = listOf(
                record("a", EnergyRecordType.Fuel, 1_000, 20.0, 160.0, 160.0, true, 1L),
                record("b", EnergyRecordType.Fuel, 1_100, 10.0, 80.0, 70.0, false, 2L),
                record("c", EnergyRecordType.Fuel, 1_300, 8.0, 64.0, 50.0, true, 3L)
            )
        )

        val valid = build.settlements.single { it.status == SettlementStatus.Valid }
        assertEquals(300.0, valid.distanceKm!!, 0.001)
        assertEquals(18.0, valid.totalAmount, 0.001)
        assertEquals(6.0, valid.consumptionPer100Km!!, 0.001)
        assertEquals(0.4, valid.costPerKm!!, 0.001)
    }

    @Test
    fun chargeOnlySettlesMatchingFullLimits() {
        val build = buildEnergySettlements(
            vehicleId = "v1",
            type = EnergyRecordType.Charge,
            sourceRecords = listOf(
                charge("a", 1_000, 8.0, true, 80, 1L),
                charge("b", 1_100, 12.0, false, 80, 2L),
                charge("c", 1_250, 23.0, true, 80, 3L)
            )
        )

        val valid = build.settlements.single { it.status == SettlementStatus.Valid }
        assertEquals(35.0, valid.totalAmount, 0.001)
        assertEquals(14.0, valid.consumptionPer100Km!!, 0.001)
    }

    @Test
    fun changedChargeLimitInvalidatesOpenIntervalAndCreatesNewBaseline() {
        val build = buildEnergySettlements(
            vehicleId = "v1",
            type = EnergyRecordType.Charge,
            sourceRecords = listOf(
                charge("a", 1_000, 8.0, true, 80, 1L),
                charge("b", 1_200, 20.0, true, 90, 2L)
            )
        )

        val invalid = build.settlements.single { it.status == SettlementStatus.Invalid }
        val waiting = build.settlements.single { it.status == SettlementStatus.Waiting }
        assertEquals(EnergySaveResult.BaselineReset("充电上限已变更"), build.latestResult)
        assertEquals("充电上限已变更", invalid.invalidReason)
        assertEquals("b", waiting.startRecordId)
        assertEquals(90, waiting.chargeLimitSoc)
    }

    @Test
    fun partialChargeDoesNotProduceConsumption() {
        val build = buildEnergySettlements(
            vehicleId = "v1",
            type = EnergyRecordType.Charge,
            sourceRecords = listOf(
                charge("a", 1_000, 8.0, true, 80, 1L),
                charge("b", 1_100, 12.0, false, 80, 2L)
            )
        )

        assertNull(build.settlements.first { it.status == SettlementStatus.Waiting }.consumptionPer100Km)
    }

    private fun charge(
        id: String,
        mileage: Int,
        amount: Double,
        full: Boolean,
        limit: Int,
        createdAt: Long
    ) = record(id, EnergyRecordType.Charge, mileage, amount, amount, amount, full, createdAt, limit)

    private fun record(
        id: String,
        type: EnergyRecordType,
        mileage: Int,
        amount: Double,
        orderAmount: Double,
        paidAmount: Double,
        full: Boolean,
        createdAt: Long,
        chargeLimit: Int? = null
    ) = EnergyRecordEntity(
        id = id,
        vehicleId = "v1",
        type = type,
        mileage = mileage.toDouble(),
        amount = amount,
        orderAmount = orderAmount,
        paidAmount = paidAmount,
        isFull = full,
        missedPrevious = false,
        chargeLimitSocSnapshot = chargeLimit,
        createdAt = createdAt
    )
}
