package com.cheku.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

const val DB_SCHEMA_VERSION = 15

@Database(
    entities = [
        VehicleEntity::class,
        MileageRecordEntity::class,
        EnergyRecordEntity::class,
        EnergySettlementEntity::class,
        MaintenanceItemEntity::class,
        MaintenanceHistoryEntity::class,
        FitActivityEntity::class,
        FitSampleEntity::class,
        FitTimerEventEntity::class,
        PerformanceSessionEntity::class, PerformanceSampleEntity::class, PerformanceSplitEntity::class,
        PerformanceNoiseEntity::class, PerformanceJerkEntity::class
    ],
    version = DB_SCHEMA_VERSION,
    exportSchema = true
)
@TypeConverters(LedgerConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun ledgerDao(): LedgerDao
    abstract fun performanceDao(): PerformanceDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "CheKu.db"
                )
                    .addMigrations(*MIGRATIONS_TO_15)
                    .build()
                    .also { instance = it }
            }
        }
    }
}
