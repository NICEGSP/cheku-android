package com.cheku.app

import com.cheku.app.data.EnergyRecordType
import com.cheku.app.data.SettlementStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class EnergyRecordUiCopyTest {
    @Test
    fun fuelAndChargeUseConsistentSemanticLabels() {
        val fuel = EnergyRecordType.Fuel.uiCopy()
        val charge = EnergyRecordType.Charge.uiCopy()

        assertEquals("加油量", fuel.amountLabel)
        assertEquals("L", fuel.amountUnit)
        assertEquals("本次加满", fuel.fullLabel)
        assertEquals("充电量", charge.amountLabel)
        assertEquals("kWh", charge.amountUnit)
        assertEquals("本次充满", charge.fullLabel)
    }

    @Test
    fun recordTitlesUseTheSameSeparator() {
        assertEquals("加油 · #95", EnergyRecordType.Fuel.recordTitle("#95"))
        assertEquals("充电 · 家充", EnergyRecordType.Charge.recordTitle("家充"))
    }

    @Test
    fun settlementStatusesUseUnifiedCopy() {
        assertEquals(
            "已结算 · 6.0 L/100km",
            energySettlementStatusText(EnergyRecordType.Fuel, SettlementStatus.Valid, "6.0 L/100km")
        )
        assertEquals(
            "结算无效 · 此前存在漏记",
            energySettlementStatusText(
                EnergyRecordType.Fuel,
                SettlementStatus.Invalid,
                invalidReason = "此前存在漏记"
            )
        )
        assertEquals(
            "待下次充满",
            energySettlementStatusText(EnergyRecordType.Charge, null)
        )
    }

    @Test
    fun distanceUnitAppliesToFuelAndChargeConsumption() {
        assertEquals(
            "6 L/100km",
            formatConsumption(6.0, DistanceUnit.KmPer100, EnergyRecordType.Fuel)
        )
        assertEquals(
            "16.7 km/L",
            formatConsumption(6.0, DistanceUnit.KmPerL, EnergyRecordType.Fuel)
        )
        assertEquals(
            "14 kWh/100km",
            formatConsumption(14.0, DistanceUnit.KmPer100, EnergyRecordType.Charge)
        )
        assertEquals(
            "7.1 km/kWh",
            formatConsumption(14.0, DistanceUnit.KmPerL, EnergyRecordType.Charge)
        )
    }
}
