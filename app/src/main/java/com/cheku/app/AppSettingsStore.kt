package com.cheku.app

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class DistanceUnit(val label: String) {
    KmPer100("L/100km"),
    KmPerL("km/L")
}

data class AppSettings(
    val oilPrices: Map<String, String> = mapOf(
        "92" to "7.18",
        "95" to "7.64",
        "98" to "9.14",
        "0" to "6.87"
    ),
    val homeChargePrice: String = "0.30",
    val commercialChargePrice: String = "1.00",
    val commercialServiceFee: String = "0.50",
    val distanceUnit: DistanceUnit = DistanceUnit.KmPer100,
    val oilPriceProvince: String = "北京",
    val oilPriceSource: String = "本地预设"
)

class AppSettingsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
    private val state = MutableStateFlow(read())

    fun observe(): StateFlow<AppSettings> = state

    fun isDataInitialized(): Boolean = prefs.getBoolean("data_initialized", false)

    fun markDataInitialized() {
        prefs.edit().putBoolean("data_initialized", true).apply()
    }

    fun isDemoTemplatesSeeded(): Boolean = prefs.getBoolean("demo_templates_v4", false)

    fun markDemoTemplatesSeeded() {
        prefs.edit().putBoolean("demo_templates_v4", true).apply()
    }

    fun updateOilPrice(grade: String, value: String) {
        prefs.edit().putString("oil_$grade", value).apply()
        state.value = read()
    }

    fun updateCharge(home: String, commercial: String, service: String) {
        prefs.edit()
            .putString("home_charge", home)
            .putString("commercial_charge", commercial)
            .putString("commercial_service", service)
            .apply()
        state.value = read()
    }

    fun updateDistanceUnit(unit: DistanceUnit) {
        prefs.edit().putString("distance_unit", unit.name).apply()
        state.value = read()
    }

    fun updateOilPricesFromWeb(province: String, prices: Map<String, String>, source: String) {
        val edit = prefs.edit()
            .putString("oil_province", province)
            .putString("oil_source", source)
        prices.forEach { (grade, price) -> edit.putString("oil_$grade", price) }
        edit.apply()
        state.value = read()
    }

    private fun read(): AppSettings {
        val defaults = AppSettings()
        return AppSettings(
            oilPrices = defaults.oilPrices.mapValues { (grade, price) -> prefs.getString("oil_$grade", price) ?: price },
            homeChargePrice = prefs.getString("home_charge", defaults.homeChargePrice) ?: defaults.homeChargePrice,
            commercialChargePrice = prefs.getString("commercial_charge", defaults.commercialChargePrice) ?: defaults.commercialChargePrice,
            commercialServiceFee = prefs.getString("commercial_service", defaults.commercialServiceFee) ?: defaults.commercialServiceFee,
            distanceUnit = runCatching {
                DistanceUnit.valueOf(prefs.getString("distance_unit", defaults.distanceUnit.name) ?: defaults.distanceUnit.name)
            }.getOrDefault(defaults.distanceUnit),
            oilPriceProvince = prefs.getString("oil_province", defaults.oilPriceProvince) ?: defaults.oilPriceProvince,
            oilPriceSource = prefs.getString("oil_source", defaults.oilPriceSource) ?: defaults.oilPriceSource
        )
    }
}
