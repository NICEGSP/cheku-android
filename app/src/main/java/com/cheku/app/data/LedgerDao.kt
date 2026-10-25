package com.cheku.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface LedgerDao {
    @Query("SELECT COUNT(*) FROM vehicles")
    suspend fun vehicleCount(): Int

    @Query("SELECT COUNT(*) FROM mileage_records")
    suspend fun mileageRecordCount(): Int

    @Query("SELECT COUNT(*) FROM energy_records")
    suspend fun energyRecordCount(): Int

    @Query("SELECT * FROM vehicles ORDER BY updatedAt DESC")
    fun observeVehicles(): Flow<List<VehicleEntity>>

    @Query("SELECT * FROM vehicles ORDER BY updatedAt DESC")
    suspend fun allVehicles(): List<VehicleEntity>

    @Query("SELECT * FROM vehicles ORDER BY updatedAt DESC LIMIT 1")
    suspend fun latestVehicle(): VehicleEntity?

    @Query("SELECT * FROM vehicles WHERE id = :id LIMIT 1")
    suspend fun vehicleById(id: String): VehicleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertVehicle(vehicle: VehicleEntity)

    @Update
    suspend fun updateVehicle(vehicle: VehicleEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertVehicles(vehicles: List<VehicleEntity>)

    @Query("DELETE FROM vehicles WHERE id = :id")
    suspend fun deleteVehicle(id: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMileageRecord(record: MileageRecordEntity)

    @Query("SELECT * FROM mileage_records ORDER BY createdAt DESC LIMIT :limit")
    fun observeMileageRecords(limit: Int): Flow<List<MileageRecordEntity>>

    @Query("SELECT * FROM mileage_records ORDER BY createdAt DESC")
    suspend fun allMileageRecords(): List<MileageRecordEntity>

    @Query("SELECT * FROM mileage_records WHERE id = :id LIMIT 1")
    suspend fun mileageRecordById(id: String): MileageRecordEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEnergyRecord(record: EnergyRecordEntity)

    @Query("SELECT * FROM energy_records ORDER BY createdAt DESC LIMIT :limit")
    fun observeEnergyRecords(limit: Int): Flow<List<EnergyRecordEntity>>

    @Query("SELECT * FROM energy_records ORDER BY createdAt DESC")
    suspend fun allEnergyRecords(): List<EnergyRecordEntity>

    @Query("SELECT * FROM energy_records WHERE id = :id LIMIT 1")
    suspend fun energyRecordById(id: String): EnergyRecordEntity?

    @Query("SELECT * FROM energy_records WHERE vehicleId = :vehicleId AND type = :type ORDER BY mileage DESC, createdAt DESC")
    suspend fun energyRecordsForVehicle(vehicleId: String, type: EnergyRecordType): List<EnergyRecordEntity>

    @Query("SELECT * FROM energy_settlements ORDER BY createdAt DESC")
    fun observeEnergySettlements(): Flow<List<EnergySettlementEntity>>

    @Query("SELECT * FROM energy_settlements WHERE vehicleId = :vehicleId AND type = :type ORDER BY createdAt DESC")
    suspend fun energySettlementsForVehicle(vehicleId: String, type: EnergyRecordType): List<EnergySettlementEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEnergySettlements(settlements: List<EnergySettlementEntity>)

    @Query("DELETE FROM energy_settlements WHERE vehicleId = :vehicleId AND type = :type")
    suspend fun deleteEnergySettlements(vehicleId: String, type: EnergyRecordType)

    @Query("SELECT * FROM mileage_records WHERE vehicleId = :vehicleId")
    suspend fun mileageRecordsForVehicle(vehicleId: String): List<MileageRecordEntity>

    @Query("SELECT * FROM energy_records WHERE vehicleId = :vehicleId")
    suspend fun allEnergyRecordsForVehicle(vehicleId: String): List<EnergyRecordEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMaintenanceItems(items: List<MaintenanceItemEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMaintenanceItem(item: MaintenanceItemEntity)

    @Query("SELECT * FROM maintenance_items ORDER BY updatedAt DESC")
    fun observeMaintenanceItems(): Flow<List<MaintenanceItemEntity>>

    @Query("SELECT * FROM maintenance_items WHERE id = :id LIMIT 1")
    suspend fun maintenanceItemById(id: String): MaintenanceItemEntity?

    @Query("SELECT * FROM maintenance_items WHERE vehicleId = :vehicleId")
    suspend fun maintenanceItemsForVehicle(vehicleId: String): List<MaintenanceItemEntity>

    @Query("SELECT COUNT(*) FROM maintenance_items WHERE vehicleId = :vehicleId")
    suspend fun maintenanceCountForVehicle(vehicleId: String): Int

    @Query("DELETE FROM maintenance_items WHERE id = :id")
    suspend fun deleteMaintenanceItem(id: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMaintenanceHistory(history: MaintenanceHistoryEntity)

    @Query("SELECT * FROM maintenance_history WHERE itemId = :itemId ORDER BY serviceAt DESC")
    fun observeHistoryForItem(itemId: String): Flow<List<MaintenanceHistoryEntity>>

    @Query("SELECT * FROM maintenance_history ORDER BY serviceAt DESC")
    fun observeAllHistory(): Flow<List<MaintenanceHistoryEntity>>

    @Query("SELECT * FROM maintenance_history WHERE itemId = :itemId ORDER BY serviceAt DESC")
    suspend fun historyForItem(itemId: String): List<MaintenanceHistoryEntity>

    @Query("SELECT * FROM maintenance_history WHERE id = :id LIMIT 1")
    suspend fun maintenanceHistoryById(id: String): MaintenanceHistoryEntity?

    @Query("DELETE FROM maintenance_history WHERE id = :id")
    suspend fun deleteMaintenanceHistory(id: String)

    @Query("DELETE FROM maintenance_history WHERE itemId = :itemId")
    suspend fun deleteMaintenanceHistoryForItem(itemId: String)

    @Query("DELETE FROM mileage_records WHERE id = :id")
    suspend fun deleteMileageRecord(id: String)

    @Query("DELETE FROM energy_records WHERE id = :id")
    suspend fun deleteEnergyRecord(id: String)

    @Query("DELETE FROM vehicles")
    suspend fun deleteAllVehicles()

    @Query("SELECT * FROM fit_activities ORDER BY startedAt DESC")
    fun observeFitActivities(): Flow<List<FitActivityEntity>>
    @Query("SELECT * FROM fit_activities ORDER BY startedAt") suspend fun allFitActivities(): List<FitActivityEntity>

    @Query("SELECT * FROM fit_activities WHERE id = :id LIMIT 1")
    suspend fun fitActivityById(id: String): FitActivityEntity?

    @Query("SELECT * FROM fit_activities WHERE status IN ('Recording','ManualPaused','AutoPaused','Interrupted') ORDER BY updatedAt DESC LIMIT 1")
    suspend fun activeFitActivity(): FitActivityEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFitActivity(activity: FitActivityEntity)

    @Query("DELETE FROM fit_activities WHERE id = :id")
    suspend fun deleteFitActivity(id: String)

    @Query("SELECT id FROM fit_activities WHERE vehicleId = :vehicleId")
    suspend fun fitActivityIdsForVehicle(vehicleId: String): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertFitSample(sample: FitSampleEntity): Long

    @Query("SELECT * FROM fit_samples WHERE activityId = :activityId ORDER BY timestamp")
    fun observeFitSamples(activityId: String): Flow<List<FitSampleEntity>>

    @Query("SELECT * FROM fit_samples WHERE activityId = :activityId ORDER BY timestamp")
    suspend fun fitSamples(activityId: String): List<FitSampleEntity>
    @Query("SELECT * FROM fit_samples ORDER BY activityId, timestamp") suspend fun allFitSamples(): List<FitSampleEntity>

    @Query("SELECT * FROM fit_samples WHERE activityId = :activityId ORDER BY timestamp DESC LIMIT 1")
    suspend fun latestFitSample(activityId: String): FitSampleEntity?

    @Query("DELETE FROM fit_samples WHERE activityId = :activityId")
    suspend fun deleteFitSamples(activityId: String)

    @Query("SELECT COUNT(*) FROM fit_samples WHERE activityId = :activityId")
    suspend fun fitSampleCount(activityId: String): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertFitTimerEvent(event: FitTimerEventEntity): Long

    @Query("SELECT * FROM fit_timer_events WHERE activityId = :activityId ORDER BY timestamp, id")
    suspend fun fitTimerEvents(activityId: String): List<FitTimerEventEntity>
    @Query("SELECT * FROM fit_timer_events ORDER BY activityId, timestamp") suspend fun allFitTimerEvents(): List<FitTimerEventEntity>

    @Query("DELETE FROM fit_timer_events WHERE activityId = :activityId")
    suspend fun deleteFitTimerEvents(activityId: String)
    @Query("DELETE FROM fit_samples") suspend fun deleteAllFitSamples()
    @Query("DELETE FROM fit_timer_events") suspend fun deleteAllFitTimerEvents()
    @Query("DELETE FROM fit_activities") suspend fun deleteAllFitActivities()
}
