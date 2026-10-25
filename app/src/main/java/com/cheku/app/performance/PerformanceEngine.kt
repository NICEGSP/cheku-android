package com.cheku.app.performance

import com.cheku.app.data.PerformanceTestType
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

data class ThresholdCrossing(val timestampNanos: Long, val distanceMeters: Double)

object PerformanceEngine {
    fun targetKmh(type: PerformanceTestType) = when (type) {
        PerformanceTestType.Acceleration50, PerformanceTestType.Braking50 -> 50.0
        PerformanceTestType.Acceleration100, PerformanceTestType.Braking100 -> 100.0
        PerformanceTestType.Comfort, PerformanceTestType.Combined -> 0.0
    }
    fun isBraking(type: PerformanceTestType) = type == PerformanceTestType.Braking50 || type == PerformanceTestType.Braking100
    fun crossing(t0: Long, speed0: Double, distance0: Double, t1: Long, speed1: Double, distance1: Double, target: Double): ThresholdCrossing? {
        if ((target - speed0) * (target - speed1) > 0.0 || speed0 == speed1) return null
        val f = ((target - speed0) / (speed1 - speed0)).coerceIn(0.0, 1.0)
        return ThresholdCrossing(t0 + ((t1 - t0) * f).toLong(), distance0 + (distance1 - distance0) * f)
    }
    fun jerkSeverity(jerk: Float) = when {
        abs(jerk) >= 8f -> com.cheku.app.data.JerkSeverity.Strong
        abs(jerk) >= 4f -> com.cheku.app.data.JerkSeverity.Noticeable
        else -> com.cheku.app.data.JerkSeverity.Mild
    }
}

data class VehicleAcceleration(
    val longitudinalMps2: Float,
    val lateralMps2: Float,
    val verticalMps2: Float
)

object VehicleAxisProjector {
    fun project(
        deviceAcceleration: FloatArray,
        deviceToWorldRotation: FloatArray,
        bearingDegrees: Float
    ): VehicleAcceleration {
        require(deviceAcceleration.size >= 3)
        require(deviceToWorldRotation.size >= 9)
        val east = deviceToWorldRotation[0] * deviceAcceleration[0] +
            deviceToWorldRotation[1] * deviceAcceleration[1] +
            deviceToWorldRotation[2] * deviceAcceleration[2]
        val north = deviceToWorldRotation[3] * deviceAcceleration[0] +
            deviceToWorldRotation[4] * deviceAcceleration[1] +
            deviceToWorldRotation[5] * deviceAcceleration[2]
        val up = deviceToWorldRotation[6] * deviceAcceleration[0] +
            deviceToWorldRotation[7] * deviceAcceleration[1] +
            deviceToWorldRotation[8] * deviceAcceleration[2]
        val radians = Math.toRadians(bearingDegrees.toDouble())
        val forwardEast = sin(radians)
        val forwardNorth = cos(radians)
        val rightEast = cos(radians)
        val rightNorth = -sin(radians)
        return VehicleAcceleration(
            longitudinalMps2 = (east * forwardEast + north * forwardNorth).toFloat(),
            lateralMps2 = (east * rightEast + north * rightNorth).toFloat(),
            verticalMps2 = up
        )
    }
}

data class PerformanceFrame(
    val timestampNanos: Long,
    val wallTimeMillis: Long,
    val speedMps: Double,
    val distanceMeters: Double,
    val accuracyMeters: Float,
    val longitudinalAccelerationMps2: Float
)

data class PerformanceResult(
    val elapsedSeconds: Double,
    val distanceMeters: Double,
    val start: ThresholdCrossing,
    val end: ThresholdCrossing
)

data class PerformanceStep(
    val state: ProcessorState,
    val result: PerformanceResult? = null
)

data class ProcessorState(
    val armed: Boolean = false,
    val running: Boolean = false,
    val stableSinceNanos: Long? = null,
    val start: ThresholdCrossing? = null,
    val previous: PerformanceFrame? = null
)

class PerformanceSessionProcessor(private val type: PerformanceTestType) {
    private var state = ProcessorState()

    fun accept(frame: PerformanceFrame): PerformanceStep {
        val previous = state.previous
        if (previous != null && frame.timestampNanos <= previous.timestampNanos) {
            return PerformanceStep(state)
        }
        if (type == PerformanceTestType.Comfort) {
            state = state.copy(armed = true, running = true, previous = frame)
            return PerformanceStep(state)
        }
        val targetMps = PerformanceEngine.targetKmh(type) / 3.6
        val braking = PerformanceEngine.isBraking(type)
        var next = state
        if (!next.armed) {
            next = if (braking) {
                if (frame.speedMps >= targetMps + 0.5) next.copy(armed = true) else next
            } else {
                val stableSince = if (frame.speedMps <= 0.3 && frame.accuracyMeters <= 5f) {
                    next.stableSinceNanos ?: frame.timestampNanos
                } else null
                next.copy(
                    armed = stableSince != null && frame.timestampNanos - stableSince >= 2_000_000_000L,
                    stableSinceNanos = stableSince
                )
            }
        }
        if (next.armed && !next.running && previous != null) {
            val start = if (braking) {
                if (previous.speedMps >= targetMps && frame.speedMps < targetMps) {
                    PerformanceEngine.crossing(
                        previous.timestampNanos, previous.speedMps, previous.distanceMeters,
                        frame.timestampNanos, frame.speedMps, frame.distanceMeters, targetMps
                    )
                } else null
            } else if (frame.longitudinalAccelerationMps2 > 0.5f && frame.speedMps > 0.3) {
                ThresholdCrossing(previous.timestampNanos, previous.distanceMeters)
            } else null
            if (start != null) next = next.copy(running = true, start = start)
        }
        if (next.running && previous != null && next.start != null) {
            val finishTarget = if (braking) 1.0 / 3.6 else targetMps
            val crossed = if (braking) {
                previous.speedMps > finishTarget && frame.speedMps <= finishTarget
            } else {
                previous.speedMps < finishTarget && frame.speedMps >= finishTarget
            }
            if (crossed) {
                val end = PerformanceEngine.crossing(
                    previous.timestampNanos, previous.speedMps, previous.distanceMeters,
                    frame.timestampNanos, frame.speedMps, frame.distanceMeters, finishTarget
                )
                if (end != null) {
                    val result = PerformanceResult(
                        elapsedSeconds = (end.timestampNanos - next.start.timestampNanos) / 1e9,
                        distanceMeters = abs(end.distanceMeters - next.start.distanceMeters),
                        start = next.start,
                        end = end
                    )
                    next = next.copy(previous = frame)
                    state = next
                    return PerformanceStep(next, result)
                }
            }
        }
        next = next.copy(previous = frame)
        state = next
        return PerformanceStep(next)
    }
}

data class CombinedPerformanceResult(
    val label: String,
    val result: PerformanceResult
)

enum class CombinedStage { Preparing, Accelerating, BrakingArmed, Braking, Complete }

class CombinedPerformanceProcessor {
    private var phase = CombinedStage.Preparing
    private var stableSince: Long? = null
    private var previous: PerformanceFrame? = null
    private var accelerationStart: ThresholdCrossing? = null
    private var acceleration50: CombinedPerformanceResult? = null
    private var acceleration100: CombinedPerformanceResult? = null
    private var braking100Start: ThresholdCrossing? = null
    private var braking50Start: ThresholdCrossing? = null
    private var braking100: CombinedPerformanceResult? = null
    private var braking50: CombinedPerformanceResult? = null

    fun accept(frame: PerformanceFrame): List<CombinedPerformanceResult> {
        val old = previous
        if (old != null && frame.timestampNanos <= old.timestampNanos) return emptyList()
        val emitted = mutableListOf<CombinedPerformanceResult>()
        when (phase) {
            CombinedStage.Preparing -> {
                if (frame.speedMps <= 0.3 && frame.accuracyMeters <= 5f) {
                    stableSince = stableSince ?: frame.timestampNanos
                    if (frame.timestampNanos - stableSince!! >= 2_000_000_000L) {
                        phase = CombinedStage.Accelerating
                    }
                } else {
                    stableSince = null
                }
            }
            CombinedStage.Accelerating -> {
                if (old != null && accelerationStart == null &&
                    frame.speedMps > 0.3 && frame.longitudinalAccelerationMps2 > 0.5f
                ) {
                    accelerationStart = ThresholdCrossing(old.timestampNanos, old.distanceMeters)
                }
                accelerationStart?.let { start ->
                    if (acceleration50 == null && old != null && old.speedMps < 50.0 / 3.6 &&
                        frame.speedMps >= 50.0 / 3.6
                    ) {
                        PerformanceEngine.crossing(old.timestampNanos, old.speedMps, old.distanceMeters, frame.timestampNanos, frame.speedMps, frame.distanceMeters, 50.0 / 3.6)
                            ?.let {
                                val result = PerformanceResult((it.timestampNanos - start.timestampNanos) / 1e9, it.distanceMeters - start.distanceMeters, start, it)
                                acceleration50 = CombinedPerformanceResult("0-50 km/h", result)
                                emitted += acceleration50!!
                            }
                    }
                    if (acceleration100 == null && old != null && old.speedMps < 100.0 / 3.6 &&
                        frame.speedMps >= 100.0 / 3.6
                    ) {
                        PerformanceEngine.crossing(old.timestampNanos, old.speedMps, old.distanceMeters, frame.timestampNanos, frame.speedMps, frame.distanceMeters, 100.0 / 3.6)
                            ?.let {
                                val result = PerformanceResult((it.timestampNanos - start.timestampNanos) / 1e9, it.distanceMeters - start.distanceMeters, start, it)
                                acceleration100 = CombinedPerformanceResult("0-100 km/h", result)
                                emitted += acceleration100!!
                            }
                    }
                }
                if (frame.speedMps >= 100.5 / 3.6) phase = CombinedStage.BrakingArmed
            }
            CombinedStage.BrakingArmed -> {
                if (old != null && frame.speedMps < old.speedMps && frame.longitudinalAccelerationMps2 < -0.8f) {
                    phase = CombinedStage.Braking
                    processBrakingFrame(old, frame, emitted)
                }
            }
            CombinedStage.Braking -> {
                if (old != null) processBrakingFrame(old, frame, emitted)
            }
            CombinedStage.Complete -> Unit
        }
        previous = frame
        return emitted
    }

    fun stage(): CombinedStage = phase

    fun isComplete() = phase == CombinedStage.Complete &&
        acceleration50 != null && acceleration100 != null && braking50 != null && braking100 != null

    fun summaryResult(): PerformanceResult? = acceleration100?.result

    private fun processBrakingFrame(
        old: PerformanceFrame,
        frame: PerformanceFrame,
        emitted: MutableList<CombinedPerformanceResult>
    ) {
        if (braking100Start == null && old.speedMps >= 100.0 / 3.6 && frame.speedMps < 100.0 / 3.6) {
            braking100Start = PerformanceEngine.crossing(
                old.timestampNanos, old.speedMps, old.distanceMeters,
                frame.timestampNanos, frame.speedMps, frame.distanceMeters,
                100.0 / 3.6
            )
        }
        if (braking50Start == null && old.speedMps >= 50.0 / 3.6 && frame.speedMps < 50.0 / 3.6) {
            braking50Start = PerformanceEngine.crossing(
                old.timestampNanos, old.speedMps, old.distanceMeters,
                frame.timestampNanos, frame.speedMps, frame.distanceMeters,
                50.0 / 3.6
            )
        }
        if (frame.speedMps > 1.0 / 3.6) return
        val end = PerformanceEngine.crossing(
            old.timestampNanos, old.speedMps, old.distanceMeters,
            frame.timestampNanos, frame.speedMps, frame.distanceMeters,
            1.0 / 3.6
        ) ?: return
        braking100Start?.let { start ->
            if (braking100 == null) {
                braking100 = CombinedPerformanceResult(
                    "100-0 km/h",
                    PerformanceResult(
                        (end.timestampNanos - start.timestampNanos) / 1e9,
                        abs(end.distanceMeters - start.distanceMeters),
                        start,
                        end
                    )
                )
                emitted += requireNotNull(braking100)
            }
        }
        braking50Start?.let { start ->
            if (braking50 == null) {
                braking50 = CombinedPerformanceResult(
                    "50-0 km/h",
                    PerformanceResult(
                        (end.timestampNanos - start.timestampNanos) / 1e9,
                        abs(end.distanceMeters - start.distanceMeters),
                        start,
                        end
                    )
                )
                emitted += requireNotNull(braking50)
            }
        }
        phase = CombinedStage.Complete
    }
}

data class PerformanceQualityReport(
    val valid: Boolean,
    val lowAccuracy: Boolean,
    val reason: String,
    val sampleRateHz: Double,
    val maxAccuracyMeters: Float
)

object PerformanceQualityEvaluator {
    fun evaluate(frames: List<PerformanceFrame>): PerformanceQualityReport {
        if (frames.size < 2) {
            return PerformanceQualityReport(false, false, "有效 GNSS 样本不足", 0.0, Float.POSITIVE_INFINITY)
        }
        val ordered = frames.sortedBy { it.timestampNanos }
        val durationSeconds = (ordered.last().timestampNanos - ordered.first().timestampNanos) / 1e9
        val sampleRate = if (durationSeconds > 0.0) (ordered.size - 1) / durationSeconds else 0.0
        val maxAccuracy = ordered.maxOf { it.accuracyMeters }
        val monotonic = ordered.zipWithNext().all { (a, b) -> b.timestampNanos > a.timestampNanos }
        val reason = when {
            !monotonic -> "GNSS 时间戳异常"
            maxAccuracy > 10f -> "GNSS 水平精度超过 10 米"
            sampleRate < 2.0 -> "GNSS 采样频率低于 2 Hz，仅可参考"
            maxAccuracy > 5f -> "GNSS 水平精度超过 5 米"
            sampleRate < 5.0 -> "GNSS 采样频率低于 5 Hz"
            else -> ""
        }
        return PerformanceQualityReport(
            valid = reason.isEmpty(),
            lowAccuracy = maxAccuracy <= 10f && sampleRate >= 2.0 && reason.isNotEmpty(),
            reason = reason,
            sampleRateHz = sampleRate,
            maxAccuracyMeters = maxAccuracy
        )
    }
}
