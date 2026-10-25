package com.cheku.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PerformanceDao {
    @Query("SELECT * FROM performance_sessions ORDER BY startedAt DESC") fun observeSessions(): Flow<List<PerformanceSessionEntity>>
    @Query("SELECT * FROM performance_sessions ORDER BY startedAt") suspend fun allSessions(): List<PerformanceSessionEntity>
    @Query("SELECT * FROM performance_sessions WHERE id=:id") suspend fun session(id: String): PerformanceSessionEntity?
    @Query("SELECT * FROM performance_sessions WHERE vehicleId=:vehicleId") suspend fun sessionsForVehicle(vehicleId: String): List<PerformanceSessionEntity>
    @Query("SELECT * FROM performance_sessions WHERE status NOT IN ('Completed','Interrupted') ORDER BY updatedAt DESC LIMIT 1") suspend fun active(): PerformanceSessionEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertSession(value: PerformanceSessionEntity)
    @Query("SELECT * FROM performance_samples WHERE sessionId=:id ORDER BY timestampNanos") fun observeSamples(id: String): Flow<List<PerformanceSampleEntity>>
    @Query("SELECT * FROM performance_samples WHERE sessionId=:id ORDER BY timestampNanos") suspend fun samples(id: String): List<PerformanceSampleEntity>
    @Query("SELECT * FROM performance_samples ORDER BY sessionId, timestampNanos") suspend fun allSamples(): List<PerformanceSampleEntity>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertSample(value: PerformanceSampleEntity): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertSplit(value: PerformanceSplitEntity)
    @Query("SELECT * FROM performance_splits WHERE sessionId=:id ORDER BY speedKmh") suspend fun splits(id: String): List<PerformanceSplitEntity>
    @Query("SELECT * FROM performance_splits WHERE sessionId=:id ORDER BY id") fun observeSplits(id: String): Flow<List<PerformanceSplitEntity>>
    @Query("SELECT * FROM performance_splits ORDER BY sessionId, speedKmh") suspend fun allSplits(): List<PerformanceSplitEntity>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertNoise(value: PerformanceNoiseEntity)
    @Query("SELECT * FROM performance_noise ORDER BY sessionId, timestamp") suspend fun allNoise(): List<PerformanceNoiseEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertJerk(value: PerformanceJerkEntity)
    @Query("SELECT * FROM performance_jerks WHERE sessionId=:id ORDER BY timestamp") fun observeJerks(id: String): Flow<List<PerformanceJerkEntity>>
    @Query("SELECT * FROM performance_jerks ORDER BY sessionId, timestamp") suspend fun allJerks(): List<PerformanceJerkEntity>
    @Query("DELETE FROM performance_samples WHERE sessionId=:id") suspend fun deleteSamples(id: String)
    @Query("DELETE FROM performance_splits WHERE sessionId=:id") suspend fun deleteSplits(id: String)
    @Query("DELETE FROM performance_noise WHERE sessionId=:id") suspend fun deleteNoise(id: String)
    @Query("DELETE FROM performance_jerks WHERE sessionId=:id") suspend fun deleteJerks(id: String)
    @Query("DELETE FROM performance_sessions WHERE id=:id") suspend fun deleteSession(id: String)
    @Query("DELETE FROM performance_samples") suspend fun deleteAllSamples()
    @Query("DELETE FROM performance_splits") suspend fun deleteAllSplits()
    @Query("DELETE FROM performance_noise") suspend fun deleteAllNoise()
    @Query("DELETE FROM performance_jerks") suspend fun deleteAllJerks()
    @Query("DELETE FROM performance_sessions") suspend fun deleteAllSessions()
}
