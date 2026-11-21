package com.salahmute.app.prayer

import android.content.Context
import com.salahmute.app.model.AppJson
import com.salahmute.app.model.CalcMethod
import com.salahmute.app.model.Fiqh
import com.salahmute.app.model.Prayer
import com.salahmute.app.model.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

@Serializable
data class PrayerCache(
    val key: String = "",
    val syncedMonth: String = "",
    /** Map of ISO date ("YYYY-MM-DD") -> prayer name -> minutes after midnight. */
    val days: Map<String, Map<String, Int>> = emptyMap(),
)

/**
 * Fetches & locally caches monthly prayer timetables from the open Aladhan API
 * according to the user's city coordinates, calculation method, and Sunni/Shia school.
 * Once synced, schedules run 100% offline for the entire month and seamlessly fall back
 * to astronomical calculation if a date isn't cached.
 */
object OnlinePrayerSync {
    private val json = Json { ignoreUnknownKeys = true }
    private val TIME_RE = Regex("""(\d{1,2}):(\d{2})""")

    @Volatile
    private var file: File? = null

    @Volatile
    private var cache: PrayerCache = PrayerCache()

    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> get() = _version

    @Synchronized
    fun init(ctx: Context) {
        if (file != null) return
        val f = File(ctx.applicationContext.filesDir, "prayer_cache.json")
        file = f
        cache = try {
            if (f.exists()) AppJson.decodeFromString(PrayerCache.serializer(), f.readText()) else PrayerCache()
        } catch (_: Exception) {
            PrayerCache()
        }
    }

    fun cacheKey(lat: Double, lon: Double, method: CalcMethod, fiqh: Fiqh): String =
        "%.2f,%.2f|%s|%s".format(Locale.US, lat, lon, method.name, fiqh.name)

    /** Returns cached internet prayer times for [date] if they match [settings], else null. */
    fun getCached(date: LocalDate, settings: Settings): Map<Prayer, Double>? {
        if (!settings.useInternetPrayers) return null
        val lat = settings.lat ?: return null
        val lon = settings.lon ?: return null
        val c = cache
        if (c.key != cacheKey(lat, lon, settings.method, settings.effectiveFiqh)) return null
        val dayMap = c.days[date.toString()] ?: return null
        val out = LinkedHashMap<Prayer, Double>(5)
        for (p in Prayer.entries) {
            val m = dayMap[p.name] ?: continue
            out[p] = m.toDouble()
        }
        return out.takeIf { it.isNotEmpty() }
    }

    /** True if today's times are already cached for the user's current city + school + method. */
    fun isSyncedForToday(settings: Settings): Boolean =
        getCached(LocalDate.now(), settings) != null

    /** Fetches current month (and next month if within last 8 days of month) and caches locally. */
    suspend fun sync(ctx: Context, settings: Settings): Boolean = withContext(Dispatchers.IO) {
        init(ctx)
        val lat = settings.lat ?: return@withContext false
        val lon = settings.lon ?: return@withContext false
        val fiqh = settings.effectiveFiqh
        val today = LocalDate.now()
        val ym = YearMonth.from(today)
        val months = if (today.dayOfMonth >= ym.lengthOfMonth() - 8) listOf(ym, ym.plusMonths(1)) else listOf(ym)

        val merged = LinkedHashMap<String, Map<String, Int>>()
        for (m in months) {
            val raw = fetchMonthJson(m.year, m.monthValue, lat, lon, settings.method, fiqh) ?: continue
            val parsed = parseAladhanMonthJson(raw)
            for ((dateKey, prayers) in parsed) {
                merged[dateKey] = prayers.mapKeys { it.key.name }.mapValues { it.value.toInt() }
            }
        }
        if (merged.isEmpty()) return@withContext false

        val next = PrayerCache(
            key = cacheKey(lat, lon, settings.method, fiqh),
            syncedMonth = ym.toString(),
            days = merged,
        )
        synchronized(this@OnlinePrayerSync) {
            cache = next
            try {
                file?.writeText(AppJson.encodeToString(PrayerCache.serializer(), next))
            } catch (_: Exception) {
            }
            _version.value += 1
        }
        true
    }

    internal fun aladhanMethodCode(method: CalcMethod, fiqh: Fiqh): Int {
        if (fiqh == Fiqh.SHIA_JAFARI) return 0 // Shia Ithna-Ashari (Leva Research Institute, Qum)
        return when (method) {
            CalcMethod.KARACHI -> 1
            CalcMethod.ISNA -> 2
            CalcMethod.MWL -> 3
            CalcMethod.MAKKAH -> 4
            CalcMethod.EGYPT -> 5
            CalcMethod.TURKEY -> 13
            CalcMethod.JAFARI -> 0
        }
    }

    internal fun aladhanSchoolCode(fiqh: Fiqh): Int = if (fiqh == Fiqh.HANAFI) 1 else 0

    private fun fetchMonthJson(
        year: Int,
        month: Int,
        lat: Double,
        lon: Double,
        method: CalcMethod,
        fiqh: Fiqh,
    ): String? {
        val mCode = aladhanMethodCode(method, fiqh)
        val sCode = aladhanSchoolCode(fiqh)
        val url = "https://api.aladhan.com/v1/calendar/$year/$month" +
            "?latitude=${"%.5f".format(Locale.US, lat)}" +
            "&longitude=${"%.5f".format(Locale.US, lon)}" +
            "&method=$mCode&school=$sCode"
        return try {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8_000
                readTimeout = 8_000
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "SalahMute-Android/1.0")
            }
            try {
                if (conn.responseCode !in 200..299) null
                else conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            } finally {
                conn.disconnect()
            }
        } catch (_: Exception) {
            null
        }
    }

    /** Pure, JVM-testable parser for Aladhan `/v1/calendar/{year}/{month}` responses. */
    fun parseAladhanMonthJson(raw: String): Map<String, Map<Prayer, Double>> {
        val root = try {
            json.parseToJsonElement(raw).jsonObject
        } catch (_: Exception) {
            return emptyMap()
        }
        val arr = root["data"]?.let { runCatching { it.jsonArray }.getOrNull() } ?: return emptyMap()
        val out = LinkedHashMap<String, Map<Prayer, Double>>(arr.size)
        for (el in arr) {
            val obj = el as? JsonObject ?: continue
            val timings = obj["timings"] as? JsonObject ?: continue
            val gregDate = obj["date"]?.jsonObject?.get("gregorian")?.jsonObject?.get("date")?.jsonPrimitive?.content ?: continue
            val iso = ddmmyyyyToIso(gregDate) ?: continue
            val dayPrayers = LinkedHashMap<Prayer, Double>(5)
            parseMin(timings["Fajr"]?.jsonPrimitive?.content)?.let { dayPrayers[Prayer.FAJR] = it }
            parseMin(timings["Dhuhr"]?.jsonPrimitive?.content)?.let { dayPrayers[Prayer.DHUHR] = it }
            parseMin(timings["Asr"]?.jsonPrimitive?.content)?.let { dayPrayers[Prayer.ASR] = it }
            parseMin(timings["Maghrib"]?.jsonPrimitive?.content)?.let { dayPrayers[Prayer.MAGHRIB] = it }
            parseMin(timings["Isha"]?.jsonPrimitive?.content)?.let { dayPrayers[Prayer.ISHA] = it }
            if (dayPrayers.isNotEmpty()) out[iso] = dayPrayers
        }
        return out
    }

    private fun parseMin(raw: String?): Double? {
        if (raw.isNullOrBlank()) return null
        val m = TIME_RE.find(raw) ?: return null
        val h = m.groupValues[1].toIntOrNull() ?: return null
        val min = m.groupValues[2].toIntOrNull() ?: return null
        if (h !in 0..23 || min !in 0..59) return null
        return (h * 60 + min).toDouble()
    }

    private fun ddmmyyyyToIso(dmy: String): String? {
        val parts = dmy.trim().split("-")
        if (parts.size != 3) return null
        val d = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        val y = parts[2].toIntOrNull() ?: return null
        return "%04d-%02d-%02d".format(Locale.US, y, m, d)
    }
}
