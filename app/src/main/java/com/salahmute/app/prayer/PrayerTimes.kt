package com.salahmute.app.prayer

import com.salahmute.app.model.CalcMethod
import com.salahmute.app.model.Fiqh
import com.salahmute.app.model.Prayer
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.tan

/**
 * Offline prayer time calculation (same astronomical model as praytimes.org).
 * Supports Sunni (Hanafi & Shafi/Maliki/Hanbali) and Shia (Jafari / Ithna Ashari).
 * Results are minutes after local midnight; prayers that cannot be computed
 * (e.g. extreme latitudes) are omitted from the map.
 */
object PrayerTimes {

    fun compute(
        date: LocalDate,
        lat: Double,
        lon: Double,
        zone: ZoneId,
        method: CalcMethod,
        hanafi: Boolean,
    ): Map<Prayer, Double> = compute(
        date = date,
        lat = lat,
        lon = lon,
        zone = zone,
        method = method,
        fiqh = if (hanafi) Fiqh.HANAFI else Fiqh.SHAFI,
    )

    fun compute(
        date: LocalDate,
        lat: Double,
        lon: Double,
        zone: ZoneId,
        method: CalcMethod,
        fiqh: Fiqh,
    ): Map<Prayer, Double> {
        val jd = julian(date.year, date.monthValue, date.dayOfMonth) - lon / (15.0 * 24.0)
        val tz = zone.rules.getOffset(date.atTime(12, 0).atZone(zone).toInstant()).totalSeconds / 3600.0
        val effectiveMethod = if (fiqh == Fiqh.SHIA_JAFARI) CalcMethod.JAFARI else method
        val asrFactor = if (fiqh == Fiqh.HANAFI) 2.0 else 1.0

        var tFajr = 5.0
        var tDhuhr = 12.0
        var tAsr = 13.0
        var tSunset = 18.0
        var tIsha = 18.0

        var fajr = Double.NaN
        var dhuhr = Double.NaN
        var asr = Double.NaN
        var maghrib = Double.NaN
        var isha = Double.NaN

        repeat(2) {
            fajr = angleTime(effectiveMethod.fajrAngle, jd, tFajr, lat, ccw = true)
            dhuhr = midDay(jd, tDhuhr)
            asr = asrTime(asrFactor, jd, tAsr, lat)
            maghrib = angleTime(effectiveMethod.maghribAngle, jd, tSunset, lat, ccw = false)
            isha = if (effectiveMethod.ishaAfterMaghribMin > 0) {
                maghrib + effectiveMethod.ishaAfterMaghribMin / 60.0
            } else {
                angleTime(effectiveMethod.ishaAngle, jd, tIsha, lat, ccw = false)
            }
            if (!fajr.isNaN()) tFajr = fajr
            if (!dhuhr.isNaN()) tDhuhr = dhuhr
            if (!asr.isNaN()) tAsr = asr
            if (!maghrib.isNaN()) tSunset = maghrib
            if (!isha.isNaN()) tIsha = isha
        }

        val shift = tz - lon / 15.0
        val out = LinkedHashMap<Prayer, Double>()
        fun put(p: Prayer, h: Double) {
            if (!h.isNaN()) out[p] = (h + shift) * 60.0
        }
        put(Prayer.FAJR, fajr)
        put(Prayer.DHUHR, dhuhr)
        put(Prayer.ASR, asr)
        put(Prayer.MAGHRIB, maghrib)
        put(Prayer.ISHA, isha)
        return out
    }

    private class Sun(val decl: Double, val eqt: Double)

    private fun rad(d: Double) = Math.toRadians(d)
    private fun deg(r: Double) = Math.toDegrees(r)
    private fun fixAngle(a: Double) = ((a % 360.0) + 360.0) % 360.0
    private fun fixHour(h: Double) = ((h % 24.0) + 24.0) % 24.0

    private fun julian(year: Int, month: Int, day: Int): Double {
        var y = year
        var m = month
        if (m <= 2) {
            y -= 1
            m += 12
        }
        val a = floor(y / 100.0)
        val b = 2 - a + floor(a / 4.0)
        return floor(365.25 * (y + 4716)) + floor(30.6001 * (m + 1)) + day + b - 1524.5
    }

    private fun sunPosition(jd: Double): Sun {
        val d = jd - 2451545.0
        val g = fixAngle(357.529 + 0.98560028 * d)
        val q = fixAngle(280.459 + 0.98564736 * d)
        val l = fixAngle(q + 1.915 * sin(rad(g)) + 0.020 * sin(rad(2 * g)))
        val e = 23.439 - 0.00000036 * d
        val ra = fixHour(deg(atan2(cos(rad(e)) * sin(rad(l)), cos(rad(l)))) / 15.0)
        val eqt = q / 15.0 - ra
        val decl = deg(asin(sin(rad(e)) * sin(rad(l))))
        return Sun(decl, eqt)
    }

    private fun midDay(jd: Double, t: Double): Double = fixHour(12.0 - sunPosition(jd + t / 24.0).eqt)

    /** [angle] = degrees below the horizon (positive). */
    private fun angleTime(angle: Double, jd: Double, t: Double, lat: Double, ccw: Boolean): Double {
        val sun = sunPosition(jd + t / 24.0)
        val z = fixHour(12.0 - sun.eqt)
        val c = (-sin(rad(angle)) - sin(rad(sun.decl)) * sin(rad(lat))) /
            (cos(rad(sun.decl)) * cos(rad(lat)))
        if (c < -1.0 || c > 1.0) return Double.NaN
        val v = deg(acos(c)) / 15.0
        return z + if (ccw) -v else v
    }

    private fun asrTime(factor: Double, jd: Double, t: Double, lat: Double): Double {
        val sun = sunPosition(jd + t / 24.0)
        val altitude = deg(atan(1.0 / (factor + tan(rad(abs(lat - sun.decl))))))
        return angleTime(-altitude, jd, t, lat, ccw = false)
    }
}
