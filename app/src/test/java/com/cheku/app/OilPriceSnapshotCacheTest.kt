package com.cheku.app

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OilPriceSnapshotCacheTest {
    private fun sample() = OilPriceSnapshot(
        province = "安徽马鞍山",
        updatedAt = "2026-10-18T03:39:50.854Z",
        grades = listOf(
            OilGradeQuote("92", 8.55, 0.31, "up"),
            OilGradeQuote("95", 9.14, 0.33, "up"),
            OilGradeQuote("98", 10.34, 0.33, "up"),
            OilGradeQuote("0", 8.34, 0.33, "up"),
        ),
        history = listOf(
            OilHistoryPoint("2026-09-12", mapOf("92" to 8.29, "95" to 8.83, "98" to 10.33, "0" to 8.02)),
            OilHistoryPoint("2026-09-25", mapOf("92" to 8.55, "95" to 9.14, "98" to 10.34, "0" to 8.34)),
        ),
        adjustment = OilAdjustment(
            lastDate = "2026-09-25",
            nextDate = "2026-10-23",
            daysLeft = 5,
            elapsedDays = 23,
            windowDays = 28,
            forecast = mapOf("92" to 8.69, "95" to 9.29, "98" to 10.49, "0" to 8.49),
            forecastDelta = 0.14,
            forecastDirection = "up",
        ),
        source = "autohome",
    )

    @Test
    fun cacheRoundTripKeepsEveryField() {
        val original = sample()
        val restored = OilPriceSnapshot.parse(original.toJson())
        assertNotNull("缓存内容必须能解析回来", restored)
        restored!!

        assertEquals("安徽马鞍山", restored.province)
        assertEquals(original.updatedAt, restored.updatedAt)
        assertEquals(original.source, restored.source)

        assertEquals(original.grades, restored.grades)

        assertEquals(original.history, restored.history)
        assertEquals(original.adjustment, restored.adjustment)
    }

    @Test
    fun legacyCacheWithTopLevelProvinceStillKeepsProvince() {
        val legacy = JSONObject(sample().toJson()).apply {
            remove("region")
            put("province", "北京")
        }
        val restored = OilPriceSnapshot.parse(legacy.toString())
        assertNotNull(restored)
        assertEquals("北京", restored!!.province)
    }

    @Test
    fun responsePayloadShapeStillParses() {
        val payload = JSONObject()
            .put("updatedAt", "2026-10-18T03:39:50.854Z")
            .put("region", JSONObject().put("province", "北京").put("city", ""))
            .put("grades", org.json.JSONArray().put(
                JSONObject().put("grade", "92").put("price", 8.61).put("delta", 0.32).put("trend", "up")
            ))
            .put("adjustment", JSONObject()
                .put("lastDate", "2026-09-25")
                .put("nextDate", "2026-10-23")
                .put("daysLeft", 5)
                .put("elapsedDays", 23)
                .put("windowDays", 28)
                .put("forecast", JSONObject().put("92", 8.75))
                .put("forecastDelta", 0.14)
                .put("forecastDirection", "up"))
            .put("history", org.json.JSONArray().put(
                JSONObject().put("date", "2026-09-25").put("92", 8.61)
            ))
            .put("source", "autohome")

        val snapshot = OilPriceSnapshot.parse(payload.toString())
        assertNotNull(snapshot)
        assertEquals("北京", snapshot!!.province)
        assertEquals(8.61, snapshot.priceOf("92")!!, 0.0001)
        assertEquals(8.75, snapshot.forecastOf("92")!!, 0.0001)
        assertTrue(snapshot.seriesFor("92").isNotEmpty())
    }
}
