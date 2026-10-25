package com.cheku.app

import com.cheku.app.data.EnergyType
import com.cheku.app.data.VehicleCategory
import com.cheku.app.data.supportsCharging
import com.cheku.app.data.supportsFuel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleTypeLogicTest {
    @Test
    fun fuelCarOnlyAllowsFuelAndCorrectsElectricSelection() {
        assertEquals(listOf(EnergyType.Fuel), energyOptionsFor(VehicleKind.FuelCar))
        assertEquals(
            EnergyType.Fuel,
            resolvedEnergyFor(VehicleKind.FuelCar, EnergyType.Electric)
        )
        assertEquals(
            VehicleKind.FuelCar,
            vehicleKindFor(VehicleCategory.Car, EnergyType.Fuel)
        )
    }

    @Test
    fun newEnergyCarExcludesFuelAndCorrectsFuelSelection() {
        val options = energyOptionsFor(VehicleKind.NewEnergyCar)

        assertFalse(EnergyType.Fuel in options)
        assertTrue(EnergyType.Electric in options)
        assertTrue(EnergyType.Hybrid in options)
        assertTrue(EnergyType.PlugInHybrid in options)
        assertTrue(EnergyType.RangeExtender in options)
        assertEquals(
            EnergyType.Electric,
            resolvedEnergyFor(VehicleKind.NewEnergyCar, EnergyType.Fuel)
        )
        assertEquals(
            VehicleKind.NewEnergyCar,
            vehicleKindFor(VehicleCategory.Car, EnergyType.Electric)
        )
    }

    @Test
    fun changingWithinNewEnergyTypesPreservesCurrentEnergy() {
        assertEquals(
            EnergyType.PlugInHybrid,
            resolvedEnergyFor(VehicleKind.NewEnergyCar, EnergyType.PlugInHybrid)
        )
    }

    @Test
    fun fuelMotorcycleAllowsFuelHybridAndPlugInHybrid() {
        assertEquals(
            listOf(EnergyType.Fuel, EnergyType.Hybrid, EnergyType.PlugInHybrid),
            energyOptionsFor(VehicleKind.FuelMotorcycle)
        )
        assertEquals(
            VehicleKind.FuelMotorcycle,
            vehicleKindFor(VehicleCategory.Motorcycle, EnergyType.Hybrid)
        )
        assertEquals(
            VehicleKind.FuelMotorcycle,
            vehicleKindFor(VehicleCategory.Motorcycle, EnergyType.PlugInHybrid)
        )
        assertTrue(EnergyType.Hybrid.supportsFuel)
        assertFalse(EnergyType.Hybrid.supportsCharging)
        assertTrue(EnergyType.PlugInHybrid.supportsFuel)
        assertTrue(EnergyType.PlugInHybrid.supportsCharging)
    }
}
