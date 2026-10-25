package com.cheku.app

import com.cheku.app.data.EnergyType
import com.cheku.app.data.supportsCharging
import com.cheku.app.data.supportsFuel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EnergyTypeUiCopyTest {
    @Test
    fun hybridCapabilitiesRemainDistinct() {
        assertTrue(EnergyType.Hybrid.supportsFuel)
        assertFalse(EnergyType.Hybrid.supportsCharging)
        assertTrue(EnergyType.PlugInHybrid.supportsFuel)
        assertTrue(EnergyType.PlugInHybrid.supportsCharging)
        assertTrue(EnergyType.RangeExtender.supportsFuel)
        assertTrue(EnergyType.RangeExtender.supportsCharging)
    }

    @Test
    fun energyPickerCapabilityLabelsUseSecondaryCopy() {
        assertEquals("燃油驱动", EnergyType.Fuel.capabilityLabel())
        assertEquals("油电协同，不外充", EnergyType.Hybrid.capabilityLabel())
        assertEquals("油电驱动，可外充", EnergyType.PlugInHybrid.capabilityLabel())
        assertEquals("电驱为主，可油可电", EnergyType.RangeExtender.capabilityLabel())
        assertEquals("纯电驱动", EnergyType.Electric.capabilityLabel())
    }
}
