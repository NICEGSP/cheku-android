package com.cheku.app.data

import android.location.Location
import com.cheku.app.fit.FitLocationPolicy
import java.util.UUID
import kotlinx.coroutines.flow.Flow

class FitRepository(private val dao: LedgerDao) {
    fun observeActivities(): Flow<List<FitActivityEntity>> = dao.observeFitActivities()
    fun observeSamples(id: String): Flow<List<FitSampleEntity>> = dao.observeFitSamples(id)
    suspend fun activity(id: String) = dao.fitActivityById(id)
    suspend fun activeActivity() = dao.activeFitActivity()
    suspend fun samples(id: String) = dao.fitSamples(id)
    suspend fun events(id: String) = dao.fitTimerEvents(id)

    suspend fun start(vehicleId: String, name: String, autoPause: Boolean, now: Long = System.currentTimeMillis()): FitActivityEntity {
        dao.activeFitActivity()?.let { error("已有未结束的 FIT 记录") }
        val activity = FitActivityEntity(UUID.randomUUID().toString(), vehicleId, name.ifBlank { "运动记录" }, FitActivityStatus.Recording, now, autoPauseEnabled = autoPause)
        dao.upsertFitActivity(activity)
        dao.insertFitTimerEvent(FitTimerEventEntity(activityId = activity.id, timestamp = now, type = FitTimerEventType.Start))
        return activity
    }

    suspend fun setStatus(id: String, status: FitActivityStatus, event: FitTimerEventType, now: Long = System.currentTimeMillis()) {
        val activity = dao.fitActivityById(id) ?: return
        if (activity.status == status) return
        val wasPaused = activity.status == FitActivityStatus.ManualPaused || activity.status == FitActivityStatus.AutoPaused
        val pausedDuration = activity.pausedDurationMs + if (wasPaused && status == FitActivityStatus.Recording) {
            (now - activity.updatedAt).coerceAtLeast(0L)
        } else 0L
        dao.upsertFitActivity(activity.copy(status = status, pausedDurationMs = pausedDuration, updatedAt = now))
        dao.insertFitTimerEvent(FitTimerEventEntity(activityId = id, timestamp = now, type = event))
    }

    suspend fun finish(id: String, now: Long = System.currentTimeMillis()) {
        val activity = dao.fitActivityById(id) ?: return
        if (activity.status == FitActivityStatus.Completed) return
        if (activity.status == FitActivityStatus.Recording) {
            dao.insertFitTimerEvent(FitTimerEventEntity(activityId = id, timestamp = now, type = FitTimerEventType.Stop))
        }
        val wasPaused = activity.status == FitActivityStatus.ManualPaused || activity.status == FitActivityStatus.AutoPaused
        val pausedDuration = activity.pausedDurationMs + if (wasPaused) (now - activity.updatedAt).coerceAtLeast(0L) else 0L
        dao.upsertFitActivity(activity.copy(status = FitActivityStatus.Completed, endedAt = now, pausedDurationMs = pausedDuration, updatedAt = now))
    }

    suspend fun addLocation(
        id: String,
        location: Location,
        heartRate: Int?,
        cadence: Int?,
        power: Int?,
        motion: Float?,
        elapsedSinceLastSeconds: Double? = null,
        startNewSegment: Boolean = false
    ): Boolean {
        val activity = dao.fitActivityById(id) ?: return false
        if (activity.status != FitActivityStatus.Recording) return false
        if (location.accuracy > 50f || !location.latitude.isFinite() || !location.longitude.isFinite()) return false
        if (location.latitude !in -90.0..90.0 || location.longitude !in -180.0..180.0) return false
        val last = dao.latestFitSample(id)
        val timestamp = maxOf(location.time, (last?.timestamp ?: 0L) + 1L)
        val delta = if (last == null) 0.0 else haversineMeters(last.latitude, last.longitude, location.latitude, location.longitude)
        val dt = (elapsedSinceLastSeconds?.takeIf { it > 0.0 }
            ?: ((timestamp - (last?.timestamp ?: timestamp)) / 1000.0)).coerceAtLeast(0.001)
        if (last != null && !startNewSegment && FitLocationPolicy.isImplausibleJump(delta, dt, location.accuracy)) return false
        val speed = location.speed.toDouble().coerceAtLeast(0.0).takeIf { it.isFinite() } ?: 0.0
        val effectiveDelta = if (startNewSegment) 0.0 else FitLocationPolicy.effectiveDistanceMeters(
            delta,
            speed,
            location.accuracy,
            last?.accuracyMeters
        )
        val distance = (last?.distanceMeters ?: 0.0) + effectiveDelta
        if (dao.fitSampleCount(id) >= 100_000 || timestamp - activity.startedAt >= 24L * 60L * 60L * 1000L) return false
        val inserted = dao.insertFitSample(FitSampleEntity(
            activityId = id, timestamp = timestamp, latitude = location.latitude, longitude = location.longitude,
            speedMps = speed, distanceMeters = distance, altitudeMeters = location.altitude.takeIf { location.hasAltitude() },
            accuracyMeters = location.accuracy, bearingDegrees = location.bearing.takeIf { location.hasBearing() },
            heartRateBpm = heartRate, cadenceRpm = cadence, powerWatts = power, motionMagnitude = motion
        )) > 0
        if (inserted) dao.upsertFitActivity(activity.copy(distanceMeters = distance, maxSpeedMps = maxOf(activity.maxSpeedMps, speed), lastSampleAt = timestamp, updatedAt = timestamp))
        return inserted
    }

    suspend fun rename(id: String, name: String) {
        dao.fitActivityById(id)?.let { dao.upsertFitActivity(it.copy(name = name.trim().ifBlank { it.name }, updatedAt = System.currentTimeMillis())) }
    }

    suspend fun markExported(id: String) {
        dao.fitActivityById(id)?.let { dao.upsertFitActivity(it.copy(exportedAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis())) }
    }

    suspend fun delete(id: String) {
        dao.deleteFitSamples(id); dao.deleteFitTimerEvents(id); dao.deleteFitActivity(id)
    }

    private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6_371_000.0
        val p1 = Math.toRadians(lat1); val p2 = Math.toRadians(lat2)
        val dp = Math.toRadians(lat2 - lat1); val dl = Math.toRadians(lon2 - lon1)
        val a = kotlin.math.sin(dp / 2) * kotlin.math.sin(dp / 2) + kotlin.math.cos(p1) * kotlin.math.cos(p2) * kotlin.math.sin(dl / 2) * kotlin.math.sin(dl / 2)
        return 2 * r * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
    }
}
