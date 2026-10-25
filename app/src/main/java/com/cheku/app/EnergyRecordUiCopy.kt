package com.cheku.app

import com.cheku.app.data.EnergyRecordType
import com.cheku.app.data.SettlementStatus

data class EnergyRecordUiCopy(
    val action: String,
    val sectionTitle: String,
    val methodLabel: String,
    val amountLabel: String,
    val amountUnit: String,
    val unitPriceLabel: String,
    val fullLabel: String,
    val firstPending: String,
    val nextPending: String
)

fun EnergyRecordType.uiCopy(): EnergyRecordUiCopy = when (this) {
    EnergyRecordType.Fuel -> EnergyRecordUiCopy(
        action = "加油",
        sectionTitle = "本次加油",
        methodLabel = "油品",
        amountLabel = "加油量",
        amountUnit = "L",
        unitPriceLabel = "油价",
        fullLabel = "本次加满",
        firstPending = "待首次加满",
        nextPending = "待下次加满"
    )
    EnergyRecordType.Charge -> EnergyRecordUiCopy(
        action = "充电",
        sectionTitle = "本次充电",
        methodLabel = "充电方式",
        amountLabel = "充电量",
        amountUnit = "kWh",
        unitPriceLabel = "电价",
        fullLabel = "本次充满",
        firstPending = "待首次充满",
        nextPending = "待下次充满"
    )
}

fun EnergyRecordType.recordTitle(method: String): String =
    if (method.isBlank()) uiCopy().action else "${uiCopy().action} · $method"

fun energySettlementStatusText(
    type: EnergyRecordType,
    status: SettlementStatus?,
    consumptionText: String = "",
    invalidReason: String = ""
): String = when (status) {
    SettlementStatus.Valid -> "已结算 · $consumptionText"
    SettlementStatus.Invalid -> listOf("结算无效", invalidReason.takeIf { it.isNotBlank() })
        .filterNotNull()
        .joinToString(" · ")
    SettlementStatus.Waiting, null -> type.uiCopy().nextPending
}
