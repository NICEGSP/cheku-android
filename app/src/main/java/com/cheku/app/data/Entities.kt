package com.cheku.app.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class VehicleCategory(val label: String) {
    Car("汽车"),
    Motorcycle("摩托"),
    ElectricMotorcycle("电摩"),
    ElectricBike("电自"),
    Bicycle("自行车")
}

enum class EnergyType(val label: String) {
    Fuel("燃油"),
    Electric("纯电"),
    Hybrid("油电混动"),
    PlugInHybrid("插电混动"),
    RangeExtender("增程"),
    Human("人力")
}

val EnergyType.supportsFuel: Boolean
    get() = this == EnergyType.Fuel || this == EnergyType.Hybrid ||
        this == EnergyType.PlugInHybrid || this == EnergyType.RangeExtender

val EnergyType.supportsCharging: Boolean
    get() = this == EnergyType.Electric || this == EnergyType.PlugInHybrid ||
        this == EnergyType.RangeExtender

val EnergyType.hasBattery: Boolean
    get() = this == EnergyType.Electric || this == EnergyType.Hybrid ||
        this == EnergyType.PlugInHybrid || this == EnergyType.RangeExtender

enum class TransmissionType(val label: String) {
    Manual("MT"),
    Automatic("AT"),
    Cvt("CVT"),
    ElectricDrive("电驱"),
    None("无")
}

enum class EnergyRecordType(val label: String, val unit: String) {
    Fuel("加油", "L"),
    Charge("充电", "kWh")
}

enum class SettlementStatus(val label: String) {
    Waiting("等待结算"),
    Valid("已结算"),
    Invalid("区间无效")
}

enum class MaintenanceStatus(val label: String) {
    Normal("正常"),
    DueSoon("即将到期"),
    Overdue("已逾期")
}

enum class MaintenanceItemType {
    Periodic,
    CommercialInsurance,
    CompulsoryInsurance,
    RepairInspection;

    val isInsurance: Boolean
        get() = this == CommercialInsurance || this == CompulsoryInsurance

    val isProtected: Boolean
        get() = this != Periodic
}

@Entity(tableName = "vehicles")
data class VehicleEntity(
    @PrimaryKey val id: String,
    val name: String,
    val model: String,
    val plate: String,
    val category: VehicleCategory,
    val energyType: EnergyType,
    val transmissionType: TransmissionType,
    val fuelTankCapacityLiters: Double? = null,
    val batteryCapacityKwh: Double? = null,
    val defaultChargeLimitSoc: Int? = null,
    val currentMileage: Double,
    val updatedAt: Long,
    val manufacturingDate: Long? = null,
    val purchasePrice: Double = 0.0,
    val purchaseTax: Double = 0.0
)

@Entity(
    tableName = "mileage_records",
    foreignKeys = [
        ForeignKey(
            entity = VehicleEntity::class,
            parentColumns = ["id"],
            childColumns = ["vehicleId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("vehicleId")]
)
data class MileageRecordEntity(
    @PrimaryKey val id: String,
    val vehicleId: String,
    val mileage: Double,
    val note: String,
    val createdAt: Long,
    val roadFeeAmount: Double = 0.0
)

@Entity(
    tableName = "energy_records",
    foreignKeys = [
        ForeignKey(
            entity = VehicleEntity::class,
            parentColumns = ["id"],
            childColumns = ["vehicleId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("vehicleId")]
)
data class EnergyRecordEntity(
    @PrimaryKey val id: String,
    val vehicleId: String,
    val type: EnergyRecordType,
    val mileage: Double,
    val amount: Double,
    val orderAmount: Double,
    val paidAmount: Double,
    val isFull: Boolean,
    val missedPrevious: Boolean,
    val fuelGrade: String = "",
    val unitPrice: Double = 0.0,
    val chargeMethod: String = "",
    val electricityPrice: Double = 0.0,
    val serviceFee: Double = 0.0,
    val chargeLimitSocSnapshot: Int? = null,
    val note: String = "",
    val createdAt: Long
)

data class EnergyRecordInput(
    val type: EnergyRecordType,
    val mileage: Double,
    val amount: Double,
    val orderAmount: Double,
    val paidAmount: Double,
    val isFull: Boolean,
    val missedPrevious: Boolean,
    val fuelGrade: String = "",
    val unitPrice: Double = 0.0,
    val chargeMethod: String = "",
    val electricityPrice: Double = 0.0,
    val serviceFee: Double = 0.0,
    val chargeLimitSocSnapshot: Int? = null,
    val note: String = ""
)

@Entity(
    tableName = "energy_settlements",
    foreignKeys = [
        ForeignKey(
            entity = VehicleEntity::class,
            parentColumns = ["id"],
            childColumns = ["vehicleId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("vehicleId"),
        Index(value = ["vehicleId", "type", "status"])
    ]
)
data class EnergySettlementEntity(
    @PrimaryKey val id: String,
    val vehicleId: String,
    val type: EnergyRecordType,
    val status: SettlementStatus,
    val startRecordId: String?,
    val endRecordId: String?,
    val startMileage: Double,
    val endMileage: Double?,
    val distanceKm: Double?,
    val totalAmount: Double,
    val totalOrderAmount: Double,
    val totalPaidAmount: Double,
    val consumptionPer100Km: Double?,
    val costPerKm: Double?,
    val chargeLimitSoc: Int? = null,
    val invalidReason: String = "",
    val createdAt: Long
)

@Entity(
    tableName = "maintenance_items",
    foreignKeys = [
        ForeignKey(
            entity = VehicleEntity::class,
            parentColumns = ["id"],
            childColumns = ["vehicleId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("vehicleId")]
)
data class MaintenanceItemEntity(
    @PrimaryKey val id: String,
    val vehicleId: String,
    val name: String,
    val intervalKm: Int,
    val intervalDays: Int,
    val lastServiceMileage: Int,
    val lastServiceAt: Long,
    val updatedAt: Long,
    val lastPartNumber: String = "",
    val itemType: MaintenanceItemType = MaintenanceItemType.Periodic,
    val baselineAt: Long = lastServiceAt
)

@Entity(
    tableName = "maintenance_history",
    foreignKeys = [
        ForeignKey(
            entity = VehicleEntity::class,
            parentColumns = ["id"],
            childColumns = ["vehicleId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("vehicleId"), Index("itemId")]
)
data class MaintenanceHistoryEntity(
    @PrimaryKey val id: String,
    val vehicleId: String,
    val itemId: String,
    val itemName: String,
    val serviceMileage: Double,
    val serviceAt: Long,
    val note: String,
    val laborCost: Double = 0.0,
    val partsCost: Double = 0.0,
    val partNumber: String = "",
    val recordName: String = itemName,
    val insuranceAmount: Double = 0.0,
    val repairAmount: Double = 0.0,
    val expiresAt: Long? = null
)

enum class FitActivityStatus { Recording, ManualPaused, AutoPaused, Completed, Interrupted }
enum class FitTimerEventType { Start, Stop, ManualPause, AutoPause, Resume }

@Entity(tableName = "fit_activities", indices = [Index("vehicleId"), Index("status")])
data class FitActivityEntity(
    @PrimaryKey val id: String,
    val vehicleId: String,
    val name: String,
    val status: FitActivityStatus,
    val startedAt: Long,
    val endedAt: Long? = null,
    val pausedDurationMs: Long = 0L,
    val distanceMeters: Double = 0.0,
    val maxSpeedMps: Double = 0.0,
    val autoPauseEnabled: Boolean = true,
    val lastSampleAt: Long? = null,
    val exportedAt: Long? = null,
    val createdAt: Long = startedAt,
    val updatedAt: Long = startedAt
)

@Entity(tableName = "fit_samples", indices = [Index("activityId"), Index(value = ["activityId", "timestamp"], unique = true)])
data class FitSampleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val activityId: String,
    val timestamp: Long,
    val latitude: Double,
    val longitude: Double,
    val speedMps: Double,
    val distanceMeters: Double,
    val altitudeMeters: Double?,
    val accuracyMeters: Float,
    val bearingDegrees: Float?,
    val heartRateBpm: Int? = null,
    val cadenceRpm: Int? = null,
    val powerWatts: Int? = null,
    val motionMagnitude: Float? = null
)

@Entity(tableName = "fit_timer_events", indices = [Index("activityId"), Index(value = ["activityId", "timestamp"])])
data class FitTimerEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val activityId: String,
    val timestamp: Long,
    val type: FitTimerEventType
)

enum class PerformanceTestType { Combined, Acceleration50, Acceleration100, Braking50, Braking100, Comfort }
enum class PerformanceStatus {
    Preparing,
    Armed,
    Running,
    Accelerating,
    BrakingArmed,
    Braking,
    Completed,
    Interrupted
}
enum class PerformanceQuality { Valid, LowAccuracy, Invalid }
enum class JerkSeverity { Mild, Noticeable, Strong }

@Entity(tableName = "performance_sessions", indices = [Index("vehicleId"), Index("status"), Index("testType")])
data class PerformanceSessionEntity(
    @PrimaryKey val id: String,
    val vehicleId: String? = null,
    val name: String,
    val testType: PerformanceTestType,
    val status: PerformanceStatus = PerformanceStatus.Preparing,
    val startedAt: Long,
    val endedAt: Long? = null,
    val resultSeconds: Double? = null,
    val resultDistanceMeters: Double? = null,
    val maxAccelerationMps2: Double = 0.0,
    val averageAccelerationMps2: Double = 0.0,
    val averageNoiseDbA: Double? = null,
    val maxNoiseDbA: Double? = null,
    val quality: PerformanceQuality = PerformanceQuality.Valid,
    val invalidReason: String = "",
    val microphoneCalibrated: Boolean = false,
    val createdAt: Long = startedAt,
    val updatedAt: Long = startedAt
)

@Entity(tableName = "performance_samples", indices = [Index("sessionId"), Index(value = ["sessionId", "timestampNanos"], unique = true)])
data class PerformanceSampleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String,
    val timestampNanos: Long,
    val wallTime: Long,
    val gpsSpeedMps: Double?,
    val fusedSpeedMps: Double,
    val distanceMeters: Double,
    val latitude: Double?,
    val longitude: Double?,
    val gpsAccuracyMeters: Float?,
    val altitudeMeters: Double?,
    val longitudinalAccelerationMps2: Float,
    val lateralAccelerationMps2: Float,
    val verticalAccelerationMps2: Float,
    val jerkMps3: Float,
    val noiseDbA: Float?
)

@Entity(tableName = "performance_splits", indices = [Index("sessionId")])
data class PerformanceSplitEntity(@PrimaryKey val id: String, val sessionId: String, val label: String, val speedKmh: Double, val elapsedSeconds: Double, val distanceMeters: Double)

@Entity(tableName = "performance_noise", indices = [Index("sessionId"), Index(value = ["sessionId", "timestamp"], unique = true)])
data class PerformanceNoiseEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val sessionId: String, val timestamp: Long, val dbA: Float)

@Entity(tableName = "performance_jerks", indices = [Index("sessionId")])
data class PerformanceJerkEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val timestamp: Long,
    val speedKmh: Double,
    val accelerationPeakMps2: Float,
    val jerkPeakMps3: Float,
    val durationMs: Long,
    val severity: JerkSeverity,
    val manual: Boolean = false
)
