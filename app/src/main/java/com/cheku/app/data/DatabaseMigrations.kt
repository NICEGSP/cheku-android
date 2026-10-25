package com.cheku.app.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

private val TABLE_ORDER = listOf(
    "vehicles",
    "mileage_records",
    "energy_records",
    "energy_settlements",
    "maintenance_items",
    "maintenance_history"
)

private val LEGACY_TO_13: Array<Migration> = (1 until 13).map { startVersion ->
    object : Migration(startVersion, 13) {
        override fun migrate(db: SupportSQLiteDatabase) {
            migrateLegacyDatabaseTo13(db)
        }
    }
}.toTypedArray()

private val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `performance_sessions` (`id` TEXT NOT NULL, `vehicleId` TEXT, `name` TEXT NOT NULL, `testType` TEXT NOT NULL, `status` TEXT NOT NULL, `startedAt` INTEGER NOT NULL, `endedAt` INTEGER, `resultSeconds` REAL, `resultDistanceMeters` REAL, `maxAccelerationMps2` REAL NOT NULL, `averageAccelerationMps2` REAL NOT NULL, `averageNoiseDbA` REAL, `maxNoiseDbA` REAL, `quality` TEXT NOT NULL, `invalidReason` TEXT NOT NULL, `microphoneCalibrated` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_performance_sessions_vehicleId` ON `performance_sessions` (`vehicleId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_performance_sessions_status` ON `performance_sessions` (`status`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_performance_sessions_testType` ON `performance_sessions` (`testType`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `performance_samples` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `sessionId` TEXT NOT NULL, `timestampNanos` INTEGER NOT NULL, `wallTime` INTEGER NOT NULL, `gpsSpeedMps` REAL, `fusedSpeedMps` REAL NOT NULL, `distanceMeters` REAL NOT NULL, `latitude` REAL, `longitude` REAL, `gpsAccuracyMeters` REAL, `altitudeMeters` REAL, `longitudinalAccelerationMps2` REAL NOT NULL, `lateralAccelerationMps2` REAL NOT NULL, `verticalAccelerationMps2` REAL NOT NULL, `jerkMps3` REAL NOT NULL, `noiseDbA` REAL)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_performance_samples_sessionId` ON `performance_samples` (`sessionId`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_performance_samples_sessionId_timestampNanos` ON `performance_samples` (`sessionId`, `timestampNanos`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `performance_splits` (`id` TEXT NOT NULL, `sessionId` TEXT NOT NULL, `label` TEXT NOT NULL, `speedKmh` REAL NOT NULL, `elapsedSeconds` REAL NOT NULL, `distanceMeters` REAL NOT NULL, PRIMARY KEY(`id`))")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_performance_splits_sessionId` ON `performance_splits` (`sessionId`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `performance_noise` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `sessionId` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `dbA` REAL NOT NULL)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_performance_noise_sessionId` ON `performance_noise` (`sessionId`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_performance_noise_sessionId_timestamp` ON `performance_noise` (`sessionId`, `timestamp`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `performance_jerks` (`id` TEXT NOT NULL, `sessionId` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `speedKmh` REAL NOT NULL, `accelerationPeakMps2` REAL NOT NULL, `jerkPeakMps3` REAL NOT NULL, `durationMs` INTEGER NOT NULL, `severity` TEXT NOT NULL, `manual` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_performance_jerks_sessionId` ON `performance_jerks` (`sessionId`)")
    }
}

private val MIGRATION_14_15 = object : Migration(14, 15) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE `mileage_records` ADD COLUMN `roadFeeAmount` REAL NOT NULL DEFAULT 0.0"
        )
    }
}

val MIGRATIONS_TO_15: Array<Migration> = LEGACY_TO_13 + MIGRATION_13_14 + MIGRATION_14_15

private fun migrateLegacyDatabaseTo13(db: SupportSQLiteDatabase) {
    db.execSQL("PRAGMA defer_foreign_keys = ON")
    val legacyColumns = mutableMapOf<String, Set<String>>()

    TABLE_ORDER.forEach { table ->
        if (db.tableExists(table)) {
            legacyColumns[table] = db.columns(table)
            db.execSQL("DROP TABLE IF EXISTS `legacy_$table`")
            db.execSQL("CREATE TEMP TABLE `legacy_$table` AS SELECT * FROM `$table`")
        }
    }

    listOf(
        "energy_settlements",
        "maintenance_history",
        "mileage_records",
        "energy_records",
        "maintenance_items",
        "vehicles"
    ).forEach { table ->
        db.execSQL("DROP TABLE IF EXISTS `$table`")
    }

    createVersion13Schema(db)

    legacyColumns["vehicles"]?.let { columns ->
        db.copyLegacy(
            table = "vehicles",
            columns = columns,
            targets = listOf(
                "id" to text(columns, "id"),
                "name" to text(columns, "name", fallback = "'未命名车辆'"),
                "model" to text(columns, "model", fallback = "'未知型号'"),
                "plate" to text(columns, "plate", fallback = "'未填车牌'"),
                "category" to text(columns, "category", fallback = "'Car'"),
                "energyType" to text(columns, "energyType", "energy", fallback = "'Fuel'"),
                "transmissionType" to text(columns, "transmissionType", "transmission", fallback = "'Automatic'"),
                "fuelTankCapacityLiters" to nullable(columns, "fuelTankCapacityLiters", "fuelTankCapacity"),
                "batteryCapacityKwh" to nullable(columns, "batteryCapacityKwh", "batteryCapacity"),
                "defaultChargeLimitSoc" to nullable(columns, "defaultChargeLimitSoc", "chargeLimitSoc"),
                "currentMileage" to number(columns, "currentMileage", "mileage"),
                "updatedAt" to number(columns, "updatedAt", "createdAt"),
                "manufacturingDate" to nullable(columns, "manufacturingDate", "mfgDate"),
                "purchasePrice" to decimal(columns, "purchasePrice"),
                "purchaseTax" to decimal(columns, "purchaseTax")
            )
        )
    }
    legacyColumns["mileage_records"]?.let { columns ->
        db.copyLegacy(
            "mileage_records",
            columns,
            listOf(
                "id" to text(columns, "id"),
                "vehicleId" to text(columns, "vehicleId"),
                "mileage" to number(columns, "mileage"),
                "note" to text(columns, "note"),
                "createdAt" to number(columns, "createdAt")
            )
        )
    }
    legacyColumns["energy_records"]?.let { columns ->
        val orderAmount = decimal(columns, "orderAmount", "cost", "totalCost")
        db.copyLegacy(
            "energy_records",
            columns,
            listOf(
                "id" to text(columns, "id"),
                "vehicleId" to text(columns, "vehicleId"),
                "type" to text(columns, "type", fallback = "'Fuel'"),
                "mileage" to number(columns, "mileage"),
                "amount" to decimal(columns, "amount"),
                "orderAmount" to orderAmount,
                "paidAmount" to decimal(columns, "paidAmount", fallback = orderAmount),
                "isFull" to number(columns, "isFull", fallback = "1"),
                "missedPrevious" to number(columns, "missedPrevious"),
                "fuelGrade" to text(columns, "fuelGrade"),
                "unitPrice" to decimal(columns, "unitPrice"),
                "chargeMethod" to text(columns, "chargeMethod"),
                "electricityPrice" to decimal(columns, "electricityPrice"),
                "serviceFee" to decimal(columns, "serviceFee"),
                "chargeLimitSocSnapshot" to nullable(columns, "chargeLimitSocSnapshot", "chargeLimitSoc"),
                "note" to text(columns, "note"),
                "createdAt" to number(columns, "createdAt")
            )
        )
    }
    legacyColumns["energy_settlements"]?.let { columns ->
        db.copyLegacy(
            "energy_settlements",
            columns,
            listOf(
                "id" to text(columns, "id"),
                "vehicleId" to text(columns, "vehicleId"),
                "type" to text(columns, "type", fallback = "'Fuel'"),
                "status" to text(columns, "status", fallback = "'Waiting'"),
                "startRecordId" to nullable(columns, "startRecordId"),
                "endRecordId" to nullable(columns, "endRecordId"),
                "startMileage" to number(columns, "startMileage"),
                "endMileage" to nullable(columns, "endMileage"),
                "distanceKm" to nullable(columns, "distanceKm"),
                "totalAmount" to decimal(columns, "totalAmount"),
                "totalOrderAmount" to decimal(columns, "totalOrderAmount"),
                "totalPaidAmount" to decimal(columns, "totalPaidAmount"),
                "consumptionPer100Km" to nullable(columns, "consumptionPer100Km"),
                "costPerKm" to nullable(columns, "costPerKm"),
                "chargeLimitSoc" to nullable(columns, "chargeLimitSoc"),
                "invalidReason" to text(columns, "invalidReason"),
                "createdAt" to number(columns, "createdAt")
            )
        )
    }
    legacyColumns["maintenance_items"]?.let { columns ->
        db.copyLegacy(
            "maintenance_items",
            columns,
            listOf(
                "id" to text(columns, "id"),
                "vehicleId" to text(columns, "vehicleId"),
                "name" to text(columns, "name", fallback = "'保养项目'"),
                "intervalKm" to number(columns, "intervalKm", fallback = "1"),
                "intervalDays" to number(columns, "intervalDays", fallback = "1"),
                "lastServiceMileage" to number(columns, "lastServiceMileage"),
                "lastServiceAt" to number(columns, "lastServiceAt"),
                "updatedAt" to number(columns, "updatedAt"),
                "lastPartNumber" to text(columns, "lastPartNumber"),
                "itemType" to if ("itemType" in columns) text(columns, "itemType") else
                    "CASE WHEN `name` = '商业险' THEN 'CommercialInsurance' WHEN `name` = '交强险' THEN 'CompulsoryInsurance' WHEN `name` = '维修/检查' THEN 'RepairInspection' ELSE 'Periodic' END",
                "baselineAt" to number(columns, "baselineAt", "lastServiceAt", "updatedAt")
            )
        )
    }
    legacyColumns["maintenance_history"]?.let { columns ->
        db.copyLegacy(
            "maintenance_history",
            columns,
            listOf(
                "id" to text(columns, "id"),
                "vehicleId" to text(columns, "vehicleId"),
                "itemId" to text(columns, "itemId"),
                "itemName" to text(columns, "itemName", "name", fallback = "'保养项目'"),
                "serviceMileage" to number(columns, "serviceMileage", "mileage"),
                "serviceAt" to number(columns, "serviceAt", "createdAt"),
                "note" to text(columns, "note"),
                "laborCost" to decimal(columns, "laborCost"),
                "partsCost" to decimal(columns, "partsCost"),
                "partNumber" to text(columns, "partNumber"),
                "recordName" to text(columns, "recordName", "itemName", "name"),
                "insuranceAmount" to decimal(columns, "insuranceAmount"),
                "repairAmount" to decimal(columns, "repairAmount"),
                "expiresAt" to nullable(columns, "expiresAt")
            )
        )
    }

    TABLE_ORDER.forEach { db.execSQL("DROP TABLE IF EXISTS `legacy_$it`") }
}

private fun createVersion13Schema(db: SupportSQLiteDatabase) {
    db.execSQL("CREATE TABLE IF NOT EXISTS `vehicles` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `model` TEXT NOT NULL, `plate` TEXT NOT NULL, `category` TEXT NOT NULL, `energyType` TEXT NOT NULL, `transmissionType` TEXT NOT NULL, `fuelTankCapacityLiters` REAL, `batteryCapacityKwh` REAL, `defaultChargeLimitSoc` INTEGER, `currentMileage` REAL NOT NULL, `updatedAt` INTEGER NOT NULL, `manufacturingDate` INTEGER, `purchasePrice` REAL NOT NULL, `purchaseTax` REAL NOT NULL, PRIMARY KEY(`id`))")
    db.execSQL("CREATE TABLE IF NOT EXISTS `mileage_records` (`id` TEXT NOT NULL, `vehicleId` TEXT NOT NULL, `mileage` REAL NOT NULL, `note` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`vehicleId`) REFERENCES `vehicles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
    db.execSQL("CREATE TABLE IF NOT EXISTS `energy_records` (`id` TEXT NOT NULL, `vehicleId` TEXT NOT NULL, `type` TEXT NOT NULL, `mileage` REAL NOT NULL, `amount` REAL NOT NULL, `orderAmount` REAL NOT NULL, `paidAmount` REAL NOT NULL, `isFull` INTEGER NOT NULL, `missedPrevious` INTEGER NOT NULL, `fuelGrade` TEXT NOT NULL, `unitPrice` REAL NOT NULL, `chargeMethod` TEXT NOT NULL, `electricityPrice` REAL NOT NULL, `serviceFee` REAL NOT NULL, `chargeLimitSocSnapshot` INTEGER, `note` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`vehicleId`) REFERENCES `vehicles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
    db.execSQL("CREATE TABLE IF NOT EXISTS `energy_settlements` (`id` TEXT NOT NULL, `vehicleId` TEXT NOT NULL, `type` TEXT NOT NULL, `status` TEXT NOT NULL, `startRecordId` TEXT, `endRecordId` TEXT, `startMileage` REAL NOT NULL, `endMileage` REAL, `distanceKm` REAL, `totalAmount` REAL NOT NULL, `totalOrderAmount` REAL NOT NULL, `totalPaidAmount` REAL NOT NULL, `consumptionPer100Km` REAL, `costPerKm` REAL, `chargeLimitSoc` INTEGER, `invalidReason` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`vehicleId`) REFERENCES `vehicles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
    db.execSQL("CREATE TABLE IF NOT EXISTS `maintenance_items` (`id` TEXT NOT NULL, `vehicleId` TEXT NOT NULL, `name` TEXT NOT NULL, `intervalKm` INTEGER NOT NULL, `intervalDays` INTEGER NOT NULL, `lastServiceMileage` INTEGER NOT NULL, `lastServiceAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `lastPartNumber` TEXT NOT NULL, `itemType` TEXT NOT NULL, `baselineAt` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`vehicleId`) REFERENCES `vehicles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
    db.execSQL("CREATE TABLE IF NOT EXISTS `maintenance_history` (`id` TEXT NOT NULL, `vehicleId` TEXT NOT NULL, `itemId` TEXT NOT NULL, `itemName` TEXT NOT NULL, `serviceMileage` REAL NOT NULL, `serviceAt` INTEGER NOT NULL, `note` TEXT NOT NULL, `laborCost` REAL NOT NULL, `partsCost` REAL NOT NULL, `partNumber` TEXT NOT NULL, `recordName` TEXT NOT NULL, `insuranceAmount` REAL NOT NULL, `repairAmount` REAL NOT NULL, `expiresAt` INTEGER, PRIMARY KEY(`id`), FOREIGN KEY(`vehicleId`) REFERENCES `vehicles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
    db.execSQL("CREATE INDEX IF NOT EXISTS `index_mileage_records_vehicleId` ON `mileage_records` (`vehicleId`)")
    db.execSQL("CREATE INDEX IF NOT EXISTS `index_energy_records_vehicleId` ON `energy_records` (`vehicleId`)")
    db.execSQL("CREATE INDEX IF NOT EXISTS `index_energy_settlements_vehicleId` ON `energy_settlements` (`vehicleId`)")
    db.execSQL("CREATE INDEX IF NOT EXISTS `index_energy_settlements_vehicleId_type_status` ON `energy_settlements` (`vehicleId`, `type`, `status`)")
    db.execSQL("CREATE INDEX IF NOT EXISTS `index_maintenance_items_vehicleId` ON `maintenance_items` (`vehicleId`)")
    db.execSQL("CREATE INDEX IF NOT EXISTS `index_maintenance_history_vehicleId` ON `maintenance_history` (`vehicleId`)")
    db.execSQL("CREATE INDEX IF NOT EXISTS `index_maintenance_history_itemId` ON `maintenance_history` (`itemId`)")
    db.execSQL("CREATE TABLE IF NOT EXISTS `fit_activities` (`id` TEXT NOT NULL, `vehicleId` TEXT NOT NULL, `name` TEXT NOT NULL, `status` TEXT NOT NULL, `startedAt` INTEGER NOT NULL, `endedAt` INTEGER, `pausedDurationMs` INTEGER NOT NULL, `distanceMeters` REAL NOT NULL, `maxSpeedMps` REAL NOT NULL, `autoPauseEnabled` INTEGER NOT NULL, `lastSampleAt` INTEGER, `exportedAt` INTEGER, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
    db.execSQL("CREATE INDEX IF NOT EXISTS `index_fit_activities_vehicleId` ON `fit_activities` (`vehicleId`)")
    db.execSQL("CREATE INDEX IF NOT EXISTS `index_fit_activities_status` ON `fit_activities` (`status`)")
    db.execSQL("CREATE TABLE IF NOT EXISTS `fit_samples` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `activityId` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `latitude` REAL NOT NULL, `longitude` REAL NOT NULL, `speedMps` REAL NOT NULL, `distanceMeters` REAL NOT NULL, `altitudeMeters` REAL, `accuracyMeters` REAL NOT NULL, `bearingDegrees` REAL, `heartRateBpm` INTEGER, `cadenceRpm` INTEGER, `powerWatts` INTEGER, `motionMagnitude` REAL)")
    db.execSQL("CREATE INDEX IF NOT EXISTS `index_fit_samples_activityId` ON `fit_samples` (`activityId`)")
    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_fit_samples_activityId_timestamp` ON `fit_samples` (`activityId`, `timestamp`)")
    db.execSQL("CREATE TABLE IF NOT EXISTS `fit_timer_events` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `activityId` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `type` TEXT NOT NULL)")
    db.execSQL("CREATE INDEX IF NOT EXISTS `index_fit_timer_events_activityId` ON `fit_timer_events` (`activityId`)")
    db.execSQL("CREATE INDEX IF NOT EXISTS `index_fit_timer_events_activityId_timestamp` ON `fit_timer_events` (`activityId`, `timestamp`)")
}

private fun SupportSQLiteDatabase.copyLegacy(
    table: String,
    columns: Set<String>,
    targets: List<Pair<String, String>>
) {
    if (columns.isEmpty()) return
    val targetNames = targets.joinToString(", ") { "`${it.first}`" }
    val selectValues = targets.joinToString(", ") { it.second }
    execSQL("INSERT OR IGNORE INTO `$table` ($targetNames) SELECT $selectValues FROM `legacy_$table`")
}

private fun SupportSQLiteDatabase.tableExists(table: String): Boolean =
    query("SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?", arrayOf(table)).use { it.moveToFirst() }

private fun SupportSQLiteDatabase.columns(table: String): Set<String> =
    query("PRAGMA table_info(`$table`)").use { cursor ->
        val nameIndex = cursor.getColumnIndex("name")
        buildSet {
            while (cursor.moveToNext()) add(cursor.getString(nameIndex))
        }
    }

private fun firstColumn(columns: Set<String>, vararg candidates: String): String? =
    candidates.firstOrNull { it in columns }?.let { "`$it`" }

private fun text(columns: Set<String>, vararg candidates: String, fallback: String = "''"): String =
    firstColumn(columns, *candidates)?.let { "COALESCE($it, $fallback)" } ?: fallback

private fun number(columns: Set<String>, vararg candidates: String, fallback: String = "0"): String =
    firstColumn(columns, *candidates)?.let { "COALESCE($it, $fallback)" } ?: fallback

private fun decimal(columns: Set<String>, vararg candidates: String, fallback: String = "0.0"): String =
    firstColumn(columns, *candidates)?.let { "COALESCE($it, $fallback)" } ?: fallback

private fun nullable(columns: Set<String>, vararg candidates: String): String =
    firstColumn(columns, *candidates) ?: "NULL"
