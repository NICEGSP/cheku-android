package com.cheku.app.performance

import com.cheku.app.data.*
import java.util.UUID

class PerformanceRepository(private val dao: PerformanceDao) {
    fun observeSessions() = dao.observeSessions()
    fun observeSamples(id: String) = dao.observeSamples(id)
    fun observeJerks(id: String) = dao.observeJerks(id)
    fun observeSplits(id: String) = dao.observeSplits(id)
    suspend fun active() = dao.active()
    suspend fun interruptActive(now: Long = System.currentTimeMillis()) {
        dao.active()?.let {
            dao.upsertSession(
                it.copy(
                    status = PerformanceStatus.Interrupted,
                    endedAt = now,
                    quality = PerformanceQuality.Invalid,
                    invalidReason = "上次测试未正常结束，请重新校准后开始",
                    updatedAt = now
                )
            )
        }
    }
    suspend fun session(id: String) = dao.session(id)
    suspend fun samples(id: String) = dao.samples(id)
    suspend fun start(type: PerformanceTestType, vehicleId: String?, name: String, now: Long = System.currentTimeMillis()): PerformanceSessionEntity {
        dao.active()?.let { error("已有正在进行的性能测试") }
        return PerformanceSessionEntity(UUID.randomUUID().toString(), vehicleId?.takeIf { it.isNotBlank() }, name, type, startedAt = now).also { dao.upsertSession(it) }
    }
    suspend fun update(session: PerformanceSessionEntity) = dao.upsertSession(session)
    suspend fun addSample(sample: PerformanceSampleEntity) = dao.insertSample(sample)
    suspend fun addNoise(id: String, timestamp: Long, db: Float) = dao.insertNoise(PerformanceNoiseEntity(sessionId=id, timestamp=timestamp, dbA=db))
    suspend fun addJerk(value: PerformanceJerkEntity) = dao.upsertJerk(value)
    suspend fun split(id: String, label: String, speed: Double, seconds: Double, distance: Double) = dao.upsertSplit(PerformanceSplitEntity("$id-$label", id, label, speed, seconds, distance))
    suspend fun delete(id: String) { dao.deleteSamples(id); dao.deleteSplits(id); dao.deleteNoise(id); dao.deleteJerks(id); dao.deleteSession(id) }
}
