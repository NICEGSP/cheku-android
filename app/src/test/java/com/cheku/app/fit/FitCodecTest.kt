package com.cheku.app.fit

import com.cheku.app.data.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class FitCodecTest {
    @Test fun activityFitHasValidHeaderCrcAndStandardMessages() {
        val start = 1_750_000_000_000L
        val activity = FitActivityEntity("fit-test", "v1", "测试轨迹", FitActivityStatus.Completed, start, start + 2_000)
        val samples = (0..2).map { i -> FitSampleEntity(
            activityId = activity.id, timestamp = start + i * 1_000L,
            latitude = 31.2304 + i * .00001, longitude = 121.4737 + i * .00001,
            speedMps = 5.0, distanceMeters = i * 5.0, altitudeMeters = 12.0 + i,
            accuracyMeters = 3f, bearingDegrees = 30f, heartRateBpm = 120 + i,
            cadenceRpm = 80, powerWatts = 180
        ) }
        val events = listOf(
            FitTimerEventEntity(activityId=activity.id, timestamp=start, type=FitTimerEventType.Start),
            FitTimerEventEntity(activityId=activity.id, timestamp=start+2_000, type=FitTimerEventType.Stop)
        )
        val bytes = FitCodec.encode(activity, samples, events)
        File("build/outputs/fit-codec-golden.fit").apply { parentFile.mkdirs(); writeBytes(bytes) }
        assertEquals(".FIT", String(bytes.copyOfRange(8, 12)))
        assertTrue(FitCodec.validate(bytes).valid)
        val broken = bytes.copyOf().also { it[it.lastIndex - 3] = (it[it.lastIndex - 3].toInt() xor 1).toByte() }
        assertFalse(FitCodec.validate(broken).valid)
    }
}
