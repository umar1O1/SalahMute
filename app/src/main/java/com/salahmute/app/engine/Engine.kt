package com.salahmute.app.engine

import com.salahmute.app.model.AppData
import com.salahmute.app.model.GeoZone
import com.salahmute.app.model.RingMode
import com.salahmute.app.model.Settings
import com.salahmute.app.model.Slot
import com.salahmute.app.prayer.OnlinePrayerSync
import com.salahmute.app.prayer.PrayerTimes
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt

/** Pure scheduling and geofence logic (no Android dependencies, unit-testable). */
object Engine {

    data class Interval(
        val startMs: Long,
        val endMs: Long,
        val mode: RingMode,
        val label: String,
        val slotId: String = "",
    ) {
        val instanceKey: String get() = "$slotId@$startMs"
    }

    data class ActiveMute(
        val mode: RingMode,
        val startMs: Long?,
        val untilMs: Long?,
        val slotLabel: String?,
        val placeLabel: String?,
    )

    data class NextSlot(
        val startMs: Long,
        val endMs: Long,
        val mode: RingMode,
        val label: String,
    )

    private val COORD_PAIR = Regex("""(-?\d{1,2}(?:\.\d+)?)\s*,\s*(-?\d{1,3}(?:\.\d+)?)""")

    /** Start/end in minutes after midnight of [date] (end may exceed 1440). Null if it can't be resolved. */
    fun slotRange(slot: Slot, date: LocalDate, settings: Settings, zone: ZoneId): Pair<Double, Double>? {
        val prayer = slot.prayer
        if (prayer == null) {
            val start = slot.startMin.toDouble()
            var end = slot.endMin.toDouble()
            if (end == start) return null
            if (end < start) end += 1440.0
            return start to end
        }
        val lat = settings.lat ?: return null
        val lon = settings.lon ?: return null
        val cached = OnlinePrayerSync.getCached(date, settings)?.get(prayer)
        val t = cached
            ?: PrayerTimes.compute(date, lat, lon, zone, settings.method, settings.effectiveFiqh)[prayer]
            ?: return null
        val center = t.roundToLong().toDouble()
        val start = center - slot.before
        val end = center + slot.after
        if (end <= start) return null
        return start to end
    }

    /** Converts local wall-clock [minutes] relative to [date] into epoch millis, staying exact across DST shifts. */
    private fun wallClockMs(date: LocalDate, minutes: Double, zone: ZoneId): Long {
        val totalSec = (minutes * 60.0).roundToLong()
        val dayShift = Math.floorDiv(totalSec, 86_400L)
        val secOfDay = Math.floorMod(totalSec, 86_400L)
        val targetDate = date.plusDays(dayShift)
        val localTime = LocalTime.ofSecondOfDay(secOfDay)
        return targetDate.atTime(localTime).atZone(zone).toInstant().toEpochMilli()
    }

    /** All mute intervals from yesterday up to 8 days ahead of [nowMs]. */
    fun intervals(data: AppData, nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): List<Interval> {
        if (!data.enabled) return emptyList()
        val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        val out = ArrayList<Interval>()
        for (offset in -1L..8L) {
            val date = today.plusDays(offset)
            val dow = date.dayOfWeek.value
            for (slot in data.active.slots) {
                if (!slot.enabled || slot.archived || dow !in slot.days) continue
                val (a, b) = slotRange(slot, date, data.settings, zone) ?: continue
                val startMs = wallClockMs(date, a, zone)
                val endMs = wallClockMs(date, b, zone)
                if (endMs <= startMs) continue
                out += Interval(startMs, endMs, slot.mode, slot.label, slot.id)
            }
        }
        return out
    }

    fun activeAt(intervals: List<Interval>, nowMs: Long): List<Interval> =
        intervals.filter { it.startMs <= nowMs && nowMs < it.endMs }

    fun nextBoundary(intervals: List<Interval>, nowMs: Long): Long? {
        var best: Long? = null
        for (i in intervals) {
            for (t in longArrayOf(i.startMs, i.endMs)) {
                if (t > nowMs && (best == null || t < best)) best = t
            }
        }
        return best
    }

    /** Start of the continuous muted period that contains [nowMs]. */
    fun chainStart(intervals: List<Interval>, nowMs: Long): Long? {
        var start = activeAt(intervals, nowMs).minOfOrNull { it.startMs } ?: return null
        var extended = true
        while (extended) {
            extended = false
            for (i in intervals) {
                if (i.endMs >= start && i.startMs < start) {
                    start = i.startMs
                    extended = true
                }
            }
        }
        return start
    }

    /** End of the continuous muted period that contains [nowMs] (back-to-back / overlapping slots are chained). */
    fun chainEnd(intervals: List<Interval>, nowMs: Long): Long? {
        var end = activeAt(intervals, nowMs).maxOfOrNull { it.endMs } ?: return null
        var extended = true
        while (extended) {
            extended = false
            for (i in intervals) {
                if (i.startMs <= end && i.endMs > end) {
                    end = i.endMs
                    extended = true
                }
            }
        }
        return end
    }

    /** Next upcoming time slot after [nowMs], if any. */
    fun nextUpcoming(intervals: List<Interval>, nowMs: Long): NextSlot? =
        intervals
            .filter { it.startMs > nowMs }
            .minByOrNull { it.startMs }
            ?.let { NextSlot(it.startMs, it.endMs, it.mode, it.label) }

    /** Great-circle distance in meters between two WGS84 coordinates (Haversine). */
    fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return 2.0 * r * atan2(sqrt(a), sqrt(1.0 - a))
    }

    /** Enabled geo-zones that currently contain the point ([lat], [lon]). */
    fun matchingGeoZones(data: AppData, lat: Double, lon: Double): List<GeoZone> {
        if (!data.enabled || !data.geoEnabled) return emptyList()
        return data.geoZones.filter { z ->
            z.enabled && distanceMeters(lat, lon, z.lat, z.lon) <= z.radiusMeters
        }
    }

    /**
     * Combines active time slots and active geo-zones into the single effective mute state.
     * When both apply, the strictest RingMode wins (DND > SILENT > VIBRATE).
     * [skippedIntervals] skips only specific dismissed slot instances (`slotId@startMs`),
     * so upcoming inner/new/other-profile slots still fire on time.
     */
    fun resolveActive(
        data: AppData,
        intervals: List<Interval>,
        nowMs: Long,
        skipUntil: Long,
        insideGeoIds: Set<String>,
        skippedGeoIds: Set<String>,
        skippedIntervals: Set<String> = emptySet(),
    ): ActiveMute? {
        if (!data.enabled) return null
        val usable = if (skippedIntervals.isEmpty()) intervals else intervals.filter { it.instanceKey !in skippedIntervals }
        val activeSlots = if (nowMs < skipUntil) emptyList() else activeAt(usable, nowMs)
        val activeGeos = if (data.geoEnabled) {
            data.geoZones.filter { it.enabled && it.id in insideGeoIds && it.id !in skippedGeoIds }
        } else {
            emptyList()
        }
        if (activeSlots.isEmpty() && activeGeos.isEmpty()) return null

        val mode = (activeSlots.map { it.mode } + activeGeos.map { it.mode }).maxBy { it.ordinal }
        val start = if (activeSlots.isNotEmpty()) chainStart(usable, nowMs) else null
        val until = if (activeSlots.isNotEmpty()) chainEnd(usable, nowMs) else null
        val slotLabel = if (activeSlots.isNotEmpty()) {
            activeSlots.maxBy { it.mode.ordinal }.label.trim().ifBlank { null }
        } else {
            null
        }
        val place = if (activeGeos.isNotEmpty()) {
            activeGeos.maxBy { it.mode.ordinal }.label.trim()
        } else {
            null
        }
        return ActiveMute(mode = mode, startMs = start, untilMs = until, slotLabel = slotLabel, placeLabel = place)
    }

    /** Extracts a valid (lat, lon) pair from coordinates text or a Maps / geo: URL. */
    fun parseCoordinates(text: String): Pair<Double, Double>? {
        val m = COORD_PAIR.find(text) ?: return null
        val lat = m.groupValues[1].toDoubleOrNull() ?: return null
        val lon = m.groupValues[2].toDoubleOrNull() ?: return null
        if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
        return lat to lon
    }
}
