package com.cheku.app.data

import androidx.room.TypeConverter

class LedgerConverters {
    @TypeConverter
    fun vehicleCategoryToString(value: VehicleCategory): String = value.name

    @TypeConverter
    fun stringToVehicleCategory(value: String): VehicleCategory = VehicleCategory.valueOf(value)

    @TypeConverter
    fun energyTypeToString(value: EnergyType): String = value.name

    @TypeConverter
    fun stringToEnergyType(value: String): EnergyType =
        runCatching { EnergyType.valueOf(value) }.getOrDefault(EnergyType.Fuel)

    @TypeConverter
    fun transmissionTypeToString(value: TransmissionType): String = value.name

    @TypeConverter
    fun stringToTransmissionType(value: String): TransmissionType = TransmissionType.valueOf(value)

    @TypeConverter
    fun energyRecordTypeToString(value: EnergyRecordType): String = value.name

    @TypeConverter
    fun stringToEnergyRecordType(value: String): EnergyRecordType = EnergyRecordType.valueOf(value)

    @TypeConverter
    fun settlementStatusToString(value: SettlementStatus): String = value.name

    @TypeConverter
    fun stringToSettlementStatus(value: String): SettlementStatus = SettlementStatus.valueOf(value)

    @TypeConverter
    fun maintenanceItemTypeToString(value: MaintenanceItemType): String = value.name

    @TypeConverter
    fun stringToMaintenanceItemType(value: String): MaintenanceItemType =
        runCatching { MaintenanceItemType.valueOf(value) }.getOrDefault(MaintenanceItemType.Periodic)

    @TypeConverter fun fitStatusToString(value: FitActivityStatus): String = value.name
    @TypeConverter fun stringToFitStatus(value: String): FitActivityStatus = FitActivityStatus.valueOf(value)
    @TypeConverter fun performanceTypeToString(value: PerformanceTestType) = value.name
    @TypeConverter fun stringToPerformanceType(value: String) = PerformanceTestType.valueOf(value)
    @TypeConverter fun performanceStatusToString(value: PerformanceStatus) = value.name
    @TypeConverter fun stringToPerformanceStatus(value: String) = PerformanceStatus.valueOf(value)
    @TypeConverter fun performanceQualityToString(value: PerformanceQuality) = value.name
    @TypeConverter fun stringToPerformanceQuality(value: String) = PerformanceQuality.valueOf(value)
    @TypeConverter fun jerkSeverityToString(value: JerkSeverity) = value.name
    @TypeConverter fun stringToJerkSeverity(value: String) = JerkSeverity.valueOf(value)
    @TypeConverter fun fitEventToString(value: FitTimerEventType): String = value.name
    @TypeConverter fun stringToFitEvent(value: String): FitTimerEventType = FitTimerEventType.valueOf(value)
}
