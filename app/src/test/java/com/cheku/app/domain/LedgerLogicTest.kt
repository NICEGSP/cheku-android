package com.cheku.app.domain

import com.cheku.app.data.EnergyType
import com.cheku.app.data.MaintenanceItemEntity
import com.cheku.app.data.MaintenanceStatus
import com.cheku.app.data.TransmissionType
import com.cheku.app.data.VehicleCategory
import com.cheku.app.data.VehicleEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class LedgerLogicTest {
    @Test
    fun maintenanceStatusCanBeOverdueDueSoonAndNormal() {
        val now = 100L * DAY
        val normal = item(lastMileage = 0, lastAt = now, intervalKm = 5_000, intervalDays = 180)
        val dueSoon = item(lastMileage = 0, lastAt = now - 170L * DAY, intervalKm = 5_000, intervalDays = 180)
        val overdue = item(lastMileage = 0, lastAt = now - 181L * DAY, intervalKm = 5_000, intervalDays = 180)

        assertEquals(MaintenanceStatus.Normal, evaluateMaintenance(normal, currentMileage = 1_000, now = now).status)
        assertEquals(MaintenanceStatus.DueSoon, evaluateMaintenance(dueSoon, currentMileage = 1_000, now = now).status)
        assertEquals(MaintenanceStatus.Overdue, evaluateMaintenance(overdue, currentMileage = 1_000, now = now).status)
    }

    @Test
    fun templatesIncludeEnergyAndTransmissionSpecificItems() {
        val vehicle = VehicleEntity(
            id = "v1",
            name = "家用车",
            model = "SUV",
            plate = "TEST",
            category = VehicleCategory.Car,
            energyType = EnergyType.Fuel,
            transmissionType = TransmissionType.Automatic,
            currentMileage = 10_000.0,
            updatedAt = 0L
        )

        val names = maintenanceTemplatesFor(vehicle).map { it.name }

        assertEquals(true, "发动机机油" in names)
        assertEquals(true, "机油滤清器" in names)
        assertEquals(true, "火花塞" in names)
        assertEquals(true, "自动变速箱油" in names)
    }

    private fun item(
        lastMileage: Int,
        lastAt: Long,
        intervalKm: Int,
        intervalDays: Int
    ) = MaintenanceItemEntity(
        id = "m$lastAt",
        vehicleId = "v1",
        name = "机油/机滤",
        intervalKm = intervalKm,
        intervalDays = intervalDays,
        lastServiceMileage = lastMileage,
        lastServiceAt = lastAt,
        updatedAt = lastAt
    )

    private companion object {
        const val DAY = 24L * 60L * 60L * 1000L
    }
}
