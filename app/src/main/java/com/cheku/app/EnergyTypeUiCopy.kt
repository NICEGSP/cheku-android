package com.cheku.app

import com.cheku.app.data.EnergyType

fun EnergyType.capabilityLabel(): String? = when (this) {
    EnergyType.Fuel -> "燃油驱动"
    EnergyType.Hybrid -> "油电协同，不外充"
    EnergyType.PlugInHybrid -> "油电驱动，可外充"
    EnergyType.RangeExtender -> "电驱为主，可油可电"
    EnergyType.Electric -> "纯电驱动"
    EnergyType.Human -> null
}
