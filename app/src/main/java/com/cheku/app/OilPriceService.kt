package com.cheku.app

import android.content.SharedPreferences
import com.cheku.app.config.RuntimeConfigProvider
import com.cheku.app.net.HttpPinning
import com.cheku.app.net.MAX_API_BODY_BYTES
import com.cheku.app.net.readBodyLimited
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** 联网油价 */
object OilPriceService {
    /** 展示顺序 */
    val GRADE_ORDER = listOf("92", "95", "98", "0")

    private val websiteBaseUrl get() = RuntimeConfigProvider.websiteBaseUrl()

    /** 拉一次油价快照 */
    suspend fun load(province: String?): OilPriceSnapshot? = withContext(Dispatchers.IO) {
        val base = websiteBaseUrl
        if (base.isBlank()) return@withContext null
        val query = province?.takeIf { it.isNotBlank() }
            ?.let { "?province=" + URLEncoder.encode(it, "UTF-8") }
            .orEmpty()
        val connection = try {
            URL("$base/api/app/oil-prices$query").openConnection() as HttpURLConnection
        } catch (error: Throwable) {
            return@withContext null
        }
        try {
            HttpPinning.configure(connection)
            connection.requestMethod = "GET"
            connection.connectTimeout = 10_000
            connection.readTimeout = 12_000
            connection.useCaches = false
            connection.setRequestProperty("User-Agent", "CheKu/${BuildConfig.VERSION_NAME} (Android)")
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Cache-Control", "no-store")
            val code = connection.responseCode
            if (code !in 200..299) return@withContext null
            val raw = connection.inputStream.use { it.readBodyLimited(MAX_API_BODY_BYTES) }
            OilPriceSnapshot.parse(raw)
        } catch (error: Throwable) {
            null
        } finally {
            connection.disconnect()
        }
    }
}

/** 单档油价 */
data class OilGradeQuote(
    val grade: String,
    val price: Double,
    val delta: Double,
    val trend: String,
)

/** 一个调价日的历史价格 */
data class OilHistoryPoint(val date: String, val prices: Map<String, Double>)

/** 调价窗口 */
data class OilAdjustment(
    val lastDate: String,
    val nextDate: String,
    val daysLeft: Int,
    val elapsedDays: Int,
    val windowDays: Int,
    val forecast: Map<String, Double>,
    val forecastDelta: Double,
    val forecastDirection: String,
)

/** 一次请求拿到的全部数据 */
data class OilPriceSnapshot(
    val province: String,
    val updatedAt: String,
    val grades: List<OilGradeQuote>,
    val history: List<OilHistoryPoint>,
    val adjustment: OilAdjustment?,
    val source: String,
) {
    fun quoteOf(grade: String): OilGradeQuote? = grades.firstOrNull { it.grade == grade }

    fun priceOf(grade: String): Double? = quoteOf(grade)?.price

    /** 某个油号的历史序列 */
    fun seriesFor(grade: String): List<Double> =
        history.mapNotNull { it.prices[grade]?.takeIf { value -> value > 0.0 } }

    fun forecastOf(grade: String): Double? =
        adjustment?.forecast?.get(grade)?.takeIf { it > 0.0 }

    fun updatedClock(): String = runCatching {
        Instant.parse(updatedAt).atZone(ZoneId.systemDefault()).format(CLOCK)
    }.getOrDefault("")

    fun toJson(): String = JSONObject().apply {
        put("region", JSONObject().apply { put("province", province) })
        put("updatedAt", updatedAt)
        put("source", source)
        put("grades", JSONArray().apply {
            grades.forEach { quote ->
                put(JSONObject().apply {
                    put("grade", quote.grade)
                    put("price", quote.price)
                    put("delta", quote.delta)
                    put("trend", quote.trend)
                })
            }
        })
        put("history", JSONArray().apply {
            history.forEach { point ->
                put(JSONObject().apply {
                    put("date", point.date)
                    point.prices.forEach { (grade, price) -> put(grade, price) }
                })
            }
        })
        adjustment?.let { window ->
            put("adjustment", JSONObject().apply {
                put("lastDate", window.lastDate)
                put("nextDate", window.nextDate)
                put("daysLeft", window.daysLeft)
                put("elapsedDays", window.elapsedDays)
                put("windowDays", window.windowDays)
                put("forecastDelta", window.forecastDelta)
                put("forecastDirection", window.forecastDirection)
                put("forecast", JSONObject().apply {
                    window.forecast.forEach { (grade, price) -> put(grade, price) }
                })
            })
        }
    }.toString()

    companion object {
        private val CLOCK = DateTimeFormatter.ofPattern("HH:mm")
        private val GRADE_KEYS = listOf("92", "95", "98", "0")

        /** 解析接口响应 / 本地缓存 */
        fun parse(raw: String): OilPriceSnapshot? {
            val json = runCatching { JSONObject(raw) }.getOrNull() ?: return null
            val gradesJson = json.optJSONArray("grades") ?: return null
            val grades = buildList {
                for (index in 0 until gradesJson.length()) {
                    val item = gradesJson.optJSONObject(index) ?: continue
                    val grade = item.optString("grade")
                    val price = item.optDouble("price", 0.0)
                    if (grade.isBlank() || price <= 0.0) continue
                    add(
                        OilGradeQuote(
                            grade = grade,
                            price = price,
                            delta = item.optDouble("delta", 0.0),
                            trend = item.optString("trend", "flat"),
                        )
                    )
                }
            }
            if (grades.isEmpty()) return null

            val historyJson = json.optJSONArray("history") ?: JSONArray()
            val history = buildList {
                for (index in 0 until historyJson.length()) {
                    val item = historyJson.optJSONObject(index) ?: continue
                    val date = item.optString("date")
                    if (date.isBlank()) continue
                    val prices = buildMap {
                        GRADE_KEYS.forEach { grade ->
                            val value = item.optDouble(grade, 0.0)
                            if (value > 0.0) put(grade, value)
                        }
                    }
                    if (prices.isNotEmpty()) add(OilHistoryPoint(date, prices))
                }
            }

            val region = json.optJSONObject("region")
            val windowJson = json.optJSONObject("adjustment")
            val adjustment = windowJson?.let {
                val forecastJson = it.optJSONObject("forecast")
                OilAdjustment(
                    lastDate = it.optString("lastDate"),
                    nextDate = it.optString("nextDate"),
                    daysLeft = it.optInt("daysLeft", 0),
                    elapsedDays = it.optInt("elapsedDays", 0),
                    windowDays = it.optInt("windowDays", 10),
                    forecast = buildMap {
                        GRADE_KEYS.forEach { grade ->
                            val value = forecastJson?.optDouble(grade, 0.0) ?: 0.0
                            if (value > 0.0) put(grade, value)
                        }
                    },
                    forecastDelta = it.optDouble("forecastDelta", 0.0),
                    forecastDirection = it.optString("forecastDirection", "flat"),
                )
            }

            return OilPriceSnapshot(
                province = region?.optString("province").orEmpty()
                    .ifBlank { json.optString("province") }
                    .ifBlank { "全国" },
                updatedAt = json.optString("updatedAt"),
                grades = grades,
                history = history,
                adjustment = adjustment,
                source = json.optString("source", "autohome"),
            )
        }
    }
}

/** 快照缓存 */
class OilPriceCache(private val preferences: SharedPreferences) {
    object Key {
        const val PREFERENCES_NAME = "oil_price_cache"
        const val SNAPSHOT = "snapshot"
        const val FETCHED_DAY = "fetched_day"
    }

    fun read(): OilPriceSnapshot? =
        preferences.getString(Key.SNAPSHOT, null)?.let { OilPriceSnapshot.parse(it) }

    /** 今天是否已经成功拉过 */
    fun fetchedToday(now: Long = System.currentTimeMillis()): Boolean =
        preferences.getString(Key.FETCHED_DAY, null) == feedbackDayKey(now)

    fun write(snapshot: OilPriceSnapshot, now: Long = System.currentTimeMillis()) {
        preferences.edit()
            .putString(Key.SNAPSHOT, snapshot.toJson())
            .putString(Key.FETCHED_DAY, feedbackDayKey(now))
            .apply()
    }
}
