package com.salahmute.app

import com.salahmute.app.data.BackupManager
import com.salahmute.app.data.ScheduleOps
import com.salahmute.app.engine.Engine
import com.salahmute.app.map.CityCatalog
import com.salahmute.app.map.GoogleMapsLink
import com.salahmute.app.map.PlaceSearch
import com.salahmute.app.model.AppData
import com.salahmute.app.model.CalcMethod
import com.salahmute.app.model.Fiqh
import com.salahmute.app.model.GeoZone
import com.salahmute.app.model.Prayer
import com.salahmute.app.model.Profile
import com.salahmute.app.model.RingMode
import com.salahmute.app.model.Settings
import com.salahmute.app.model.Slot
import com.salahmute.app.prayer.OnlinePrayerSync
import com.salahmute.app.prayer.PrayerTimes
import com.salahmute.app.share.ShareCodec
import android.app.NotificationManager
import android.media.AudioManager
import com.salahmute.app.system.KeepAliveService
import com.salahmute.app.system.RingerController
import com.salahmute.app.ui.parseHexColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class EngineTest {
    private val zone = ZoneId.of("Asia/Karachi")

    private fun ms(y: Int, mo: Int, d: Int, h: Int, mi: Int) =
        ZonedDateTime.of(y, mo, d, h, mi, 0, 0, zone).toInstant().toEpochMilli()

    private fun data(vararg slots: Slot, settings: Settings = Settings(), geoZones: List<GeoZone> = emptyList()) =
        AppData(
            profiles = listOf(Profile("p", "P", slots.toList())),
            activeProfileId = "p",
            settings = settings,
            geoZones = geoZones,
        )

    // 2026-11-16 is a Monday
    @Test
    fun fixedSlotActiveOnlyInsideRange() {
        val d = data(Slot(startMin = 13 * 60, endMin = 13 * 60 + 30, days = listOf(1)))
        val now = ms(2026, 11, 16, 13, 10)
        val iv = Engine.intervals(d, now, zone)
        assertEquals(1, Engine.activeAt(iv, now).size)
        assertEquals(0, Engine.activeAt(iv, ms(2026, 11, 16, 13, 30)).size)
        assertEquals(0, Engine.activeAt(iv, ms(2026, 11, 16, 12, 59)).size)
    }

    @Test
    fun daySpecificSlotsDoNotRunOnOtherDays() {
        val d = data(Slot(startMin = 13 * 60, endMin = 14 * 60, days = listOf(1)))
        val tuesday = ms(2026, 11, 17, 13, 30)
        assertEquals(0, Engine.activeAt(Engine.intervals(d, tuesday, zone), tuesday).size)
    }

    @Test
    fun slotCrossingMidnight() {
        val d = data(Slot(startMin = 23 * 60 + 30, endMin = 15, days = listOf(1)))
        val tueEarly = ms(2026, 11, 17, 0, 10)
        assertEquals(1, Engine.activeAt(Engine.intervals(d, tueEarly, zone), tueEarly).size)
        val tueLater = ms(2026, 11, 17, 0, 20)
        assertEquals(0, Engine.activeAt(Engine.intervals(d, tueLater, zone), tueLater).size)
    }

    @Test
    fun overlapStrongestWinsAndChainEndExtends() {
        val d = data(
            Slot(startMin = 13 * 60, endMin = 13 * 60 + 30, mode = RingMode.VIBRATE, days = listOf(1)),
            Slot(startMin = 13 * 60 + 30, endMin = 14 * 60, mode = RingMode.SILENT, days = listOf(1)),
        )
        val now = ms(2026, 11, 16, 13, 10)
        val iv = Engine.intervals(d, now, zone)
        assertEquals(ms(2026, 11, 16, 14, 0), Engine.chainEnd(iv, now))
        assertEquals(ms(2026, 11, 16, 13, 0), Engine.chainStart(iv, now))
        assertEquals(ms(2026, 11, 16, 13, 30), Engine.nextBoundary(iv, now))
        val active = Engine.resolveActive(d, iv, now, 0L, emptySet(), emptySet())!!
        assertEquals(ms(2026, 11, 16, 13, 0), active.startMs)
        assertEquals(ms(2026, 11, 16, 14, 0), active.untilMs)
    }

    @Test
    fun masterSwitchOffDisablesEverything() {
        val d = data(Slot(days = listOf(1))).copy(enabled = false)
        assertTrue(Engine.intervals(d, ms(2026, 11, 16, 13, 10), zone).isEmpty())
    }

    @Test
    fun prayerSlotNeedsLocation() {
        val s = Slot(prayer = Prayer.DHUHR, before = 0, after = 20)
        assertNull(Engine.slotRange(s, LocalDate.of(2026, 11, 16), Settings(), zone))
        assertNotNull(Engine.slotRange(s, LocalDate.of(2026, 11, 16), Settings(lat = 24.86, lon = 67.01), zone))
    }

    @Test
    fun geoDistanceAndMatchingZones() {
        assertEquals(0.0, Engine.distanceMeters(24.8607, 67.0011, 24.8607, 67.0011), 0.01)
        // 1 degree latitude is ~111.19 km
        assertEquals(111_195.0, Engine.distanceMeters(24.0, 67.0, 25.0, 67.0), 300.0)

        val masjid = GeoZone(id = "m1", label = "Masjid", lat = 24.8607, lon = 67.0011, radiusMeters = 100, mode = RingMode.SILENT)
        val disabled = GeoZone(id = "m2", label = "Old", lat = 24.8607, lon = 67.0011, radiusMeters = 100, enabled = false)
        val d = data(geoZones = listOf(masjid, disabled))

        // ~55m north -> inside 100m radius
        assertEquals(listOf("m1"), Engine.matchingGeoZones(d, 24.8612, 67.0011).map { it.id })
        // ~220m north -> outside 100m radius
        assertTrue(Engine.matchingGeoZones(d, 24.8627, 67.0011).isEmpty())
        // master switch off -> none
        assertTrue(Engine.matchingGeoZones(d.copy(enabled = false), 24.8607, 67.0011).isEmpty())
    }

    @Test
    fun resolveActiveCombinesTimeAndGeoZones() {
        val slot = Slot(startMin = 13 * 60, endMin = 13 * 60 + 30, mode = RingMode.VIBRATE, days = listOf(1))
        val masjid = GeoZone(id = "m1", label = "Masjid", lat = 24.8607, lon = 67.0011, mode = RingMode.SILENT)
        val d = data(slot, geoZones = listOf(masjid))

        val duringSlot = ms(2026, 11, 16, 13, 10)
        val afterSlot = ms(2026, 11, 16, 14, 0)
        val iv = Engine.intervals(d, duringSlot, zone)

        // Both active -> strictest mode (SILENT) wins, keeps both untilMs and placeLabel
        val both = Engine.resolveActive(d, iv, duringSlot, 0L, setOf("m1"), emptySet())!!
        assertEquals(RingMode.SILENT, both.mode)
        assertEquals(ms(2026, 11, 16, 13, 30), both.untilMs)
        assertEquals("Masjid", both.placeLabel)

        // Slot ends while still inside Masjid -> stays SILENT with no fixed end time
        val geoOnly = Engine.resolveActive(d, iv, afterSlot, 0L, setOf("m1"), emptySet())!!
        assertEquals(RingMode.SILENT, geoOnly.mode)
        assertNull(geoOnly.untilMs)
        assertEquals("Masjid", geoOnly.placeLabel)

        // Leave Masjid -> restores original mode (null)
        assertNull(Engine.resolveActive(d, iv, afterSlot, 0L, emptySet(), emptySet()))

        // "End now" while inside Masjid skips it until exit
        assertNull(Engine.resolveActive(d, iv, afterSlot, 0L, setOf("m1"), setOf("m1")))

        // Master switch off disables geo mute too
        assertNull(Engine.resolveActive(d.copy(enabled = false), iv, afterSlot, 0L, setOf("m1"), emptySet()))

        // Separate geoEnabled toggle disables geo-zones while keeping time slots active!
        val geoOff = d.copy(geoEnabled = false)
        assertNull(Engine.resolveActive(geoOff, iv, afterSlot, 0L, setOf("m1"), emptySet()))
        val slotStillWorks = Engine.resolveActive(geoOff, iv, duringSlot, 0L, setOf("m1"), emptySet())!!
        assertEquals(RingMode.VIBRATE, slotStillWorks.mode)
        assertNull(slotStillWorks.placeLabel)
    }

    @Test
    fun nextUpcomingFindsSoonestFutureSlot() {
        val d = data(
            Slot(label = "Zuhr", startMin = 13 * 60, endMin = 13 * 60 + 30, days = listOf(1)),
            Slot(label = "Asr", startMin = 16 * 60, endMin = 16 * 60 + 30, days = listOf(1)),
        )
        val now = ms(2026, 11, 16, 12, 0)
        val iv = Engine.intervals(d, now, zone)
        val next = Engine.nextUpcoming(iv, now)!!
        assertEquals("Zuhr", next.label)
        assertEquals(ms(2026, 11, 16, 13, 0), next.startMs)
    }

    @Test
    fun parseCoordinatesHandlesTextAndMapsUrls() {
        assertEquals(24.8607 to 67.0011, Engine.parseCoordinates("24.8607, 67.0011"))
        assertEquals(-33.8688 to 151.2093, Engine.parseCoordinates("https://www.google.com/maps/@-33.8688,151.2093,17z"))
        assertEquals(21.4225 to 39.8262, Engine.parseCoordinates("geo:21.4225,39.8262?q=21.4225,39.8262"))
        assertNull(Engine.parseCoordinates("95.0, 20.0"))
        assertNull(Engine.parseCoordinates("not coordinates"))
    }

    @Test
    fun nestedBigAndSmallSlotsUpgradeAndStepDown() {
        val big = Slot(id = "big", label = "Office", startMin = 13 * 60, endMin = 15 * 60, mode = RingMode.VIBRATE, days = listOf(1))
        val small = Slot(id = "small", label = "Zuhr", startMin = 14 * 60, endMin = 14 * 60 + 45, mode = RingMode.SILENT, days = listOf(1))
        val d = data(big, small)
        val t130 = ms(2026, 11, 16, 13, 30)
        val t215 = ms(2026, 11, 16, 14, 15)
        val t250 = ms(2026, 11, 16, 14, 50)
        val t300 = ms(2026, 11, 16, 15, 0)
        val iv = Engine.intervals(d, t130, zone)

        // 1:30 PM -> Big slot (Vibrate), next boundary at 2:00 PM (Small starts), restores at 3:00 PM
        val at130 = Engine.resolveActive(d, iv, t130, 0L, emptySet(), emptySet())!!
        assertEquals(RingMode.VIBRATE, at130.mode)
        assertEquals("Office", at130.slotLabel)
        assertEquals(t300, at130.untilMs)
        assertEquals(ms(2026, 11, 16, 14, 0), Engine.nextBoundary(iv, t130))

        // 2:15 PM -> Small slot upgrades to Silent, next boundary at 2:45 PM (Small ends)
        val at215 = Engine.resolveActive(d, iv, t215, 0L, emptySet(), emptySet())!!
        assertEquals(RingMode.SILENT, at215.mode)
        assertEquals("Zuhr", at215.slotLabel)
        assertEquals(t300, at215.untilMs)
        assertEquals(ms(2026, 11, 16, 14, 45), Engine.nextBoundary(iv, t215))

        // 2:50 PM -> Small ended, Big still active -> steps cleanly back down to Vibrate (does NOT restore early!)
        val at250 = Engine.resolveActive(d, iv, t250, 0L, emptySet(), emptySet())!!
        assertEquals(RingMode.VIBRATE, at250.mode)
        assertEquals("Office", at250.slotLabel)
        assertEquals(t300, at250.untilMs)
        assertEquals(t300, Engine.nextBoundary(iv, t250))

        // 3:00 PM -> Both ended -> restores original mode
        assertNull(Engine.resolveActive(d, iv, t300, 0L, emptySet(), emptySet()))
    }

    @Test
    fun nestedEndNowSkipsOnlyActiveBigSlotSoInnerSlotStillFires() {
        val big = Slot(id = "big", label = "Office", startMin = 13 * 60, endMin = 15 * 60, mode = RingMode.VIBRATE, days = listOf(1))
        val small = Slot(id = "small", label = "Zuhr", startMin = 14 * 60, endMin = 14 * 60 + 45, mode = RingMode.SILENT, days = listOf(1))
        val d = data(big, small)
        val t130 = ms(2026, 11, 16, 13, 30)
        val t215 = ms(2026, 11, 16, 14, 15)
        val t250 = ms(2026, 11, 16, 14, 50)
        val iv = Engine.intervals(d, t130, zone)

        // End now at 1:30 PM -> skips ONLY the active Office instance
        val skippedAt130 = Engine.activeAt(iv, t130).map { it.instanceKey }.toSet()
        assertEquals(1, skippedAt130.size)
        assertNull(Engine.resolveActive(d, iv, t130, 0L, emptySet(), emptySet(), skippedAt130))

        // At 2:15 PM -> inner Zuhr STILL fires with countdown until 2:45 PM!
        val zuhr = Engine.resolveActive(d, iv, t215, 0L, emptySet(), emptySet(), skippedAt130)!!
        assertEquals(RingMode.SILENT, zuhr.mode)
        assertEquals("Zuhr", zuhr.slotLabel)
        assertEquals(ms(2026, 11, 16, 14, 45), zuhr.untilMs)

        // At 2:50 PM -> Zuhr finished and Office stays skipped -> restored!
        assertNull(Engine.resolveActive(d, iv, t250, 0L, emptySet(), emptySet(), skippedAt130))

        // End now at 2:15 PM (inside BOTH) -> skips both so phone stays unmuted at 2:15 and 2:50
        val skippedAt215 = Engine.activeAt(iv, t215).map { it.instanceKey }.toSet()
        assertEquals(2, skippedAt215.size)
        assertNull(Engine.resolveActive(d, iv, t215, 0L, emptySet(), emptySet(), skippedAt215))
        assertNull(Engine.resolveActive(d, iv, t250, 0L, emptySet(), emptySet(), skippedAt215))
    }

    @Test
    fun dstTransitionDayKeepsExactWallClockTime() {
        val ny = ZoneId.of("America/New_York")
        // Sunday 2026-03-08: DST spring-forward (02:00 -> 03:00)
        val springDay = ZonedDateTime.of(2026, 3, 8, 12, 0, 0, 0, ny).toInstant().toEpochMilli()
        val expectedSpringStart = ZonedDateTime.of(2026, 3, 8, 13, 0, 0, 0, ny).toInstant().toEpochMilli()
        val expectedSpringEnd = ZonedDateTime.of(2026, 3, 8, 13, 30, 0, 0, ny).toInstant().toEpochMilli()
        val d = data(Slot(id = "s", startMin = 13 * 60, endMin = 13 * 60 + 30, days = listOf(7)))
        val springMatch = Engine.intervals(d, springDay, ny).first { it.startMs == expectedSpringStart }
        assertEquals(expectedSpringEnd, springMatch.endMs)

        // Sunday 2026-11-01: DST fall-back (02:00 -> 01:00)
        val fallDay = ZonedDateTime.of(2026, 11, 1, 12, 0, 0, 0, ny).toInstant().toEpochMilli()
        val expectedFallStart = ZonedDateTime.of(2026, 11, 1, 13, 0, 0, 0, ny).toInstant().toEpochMilli()
        val expectedFallEnd = ZonedDateTime.of(2026, 11, 1, 13, 30, 0, 0, ny).toInstant().toEpochMilli()
        val fallMatch = Engine.intervals(d, fallDay, ny).first { it.startMs == expectedFallStart }
        assertEquals(expectedFallEnd, fallMatch.endMs)
    }

    @Test
    fun zeroLengthFixedSlotIsIgnoredNot24HourMute() {
        val bad = Slot(startMin = 13 * 60, endMin = 13 * 60, days = listOf(1))
        assertNull(Engine.slotRange(bad, LocalDate.of(2026, 11, 16), Settings(), zone))
        assertTrue(Engine.intervals(data(bad), ms(2026, 11, 16, 13, 10), zone).isEmpty())
    }

    @Test
    fun alreadyStricterModeIsNeverWeakened() {
        val all = NotificationManager.INTERRUPTION_FILTER_ALL
        val prio = NotificationManager.INTERRUPTION_FILTER_PRIORITY
        val none = NotificationManager.INTERRUPTION_FILTER_NONE
        val normal = AudioManager.RINGER_MODE_NORMAL
        val silent = AudioManager.RINGER_MODE_SILENT

        // Already in manual Priority DND -> Vibrate or Silent slots must NOT weaken DND
        assertTrue(RingerController.isAlreadyStricter(normal, prio, RingMode.VIBRATE))
        assertTrue(RingerController.isAlreadyStricter(normal, prio, RingMode.SILENT))
        assertFalse(RingerController.isAlreadyStricter(normal, prio, RingMode.DND))

        // Already in Total Silence (NONE) -> even DND (Alarms) must NOT weaken it
        assertTrue(RingerController.isAlreadyStricter(normal, none, RingMode.DND))

        // Already Silent -> Vibrate slot must NOT weaken Silent to Vibrate, but DND can upgrade
        assertTrue(RingerController.isAlreadyStricter(silent, all, RingMode.VIBRATE))
        assertFalse(RingerController.isAlreadyStricter(silent, all, RingMode.SILENT))
        assertFalse(RingerController.isAlreadyStricter(silent, all, RingMode.DND))

        // Normal phone -> every mute mode applies
        assertFalse(RingerController.isAlreadyStricter(normal, all, RingMode.VIBRATE))
        assertFalse(RingerController.isAlreadyStricter(normal, all, RingMode.SILENT))
        assertFalse(RingerController.isAlreadyStricter(normal, all, RingMode.DND))
    }
}

class PrayerTimesTest {
    @Test
    fun karachiTimesAreSane() {
        val t = PrayerTimes.compute(LocalDate.of(2026, 11, 16), 24.86, 67.01, ZoneId.of("Asia/Karachi"), CalcMethod.KARACHI, true)
        // Karachi mid-November: solar noon ~12:16 (67E vs 75E zone meridian, equation of time +15 min)
        assertEquals(12 * 60 + 16.0, t[Prayer.DHUHR]!!, 5.0)
        assertEquals(5 * 60 + 40.0, t[Prayer.FAJR]!!, 20.0)
        assertEquals(17 * 60 + 45.0, t[Prayer.MAGHRIB]!!, 15.0)
        assertEquals(19 * 60 + 5.0, t[Prayer.ISHA]!!, 20.0)
        assertTrue(t[Prayer.FAJR]!! < t[Prayer.DHUHR]!!)
        assertTrue(t[Prayer.DHUHR]!! < t[Prayer.ASR]!!)
        assertTrue(t[Prayer.ASR]!! < t[Prayer.MAGHRIB]!!)
        assertTrue(t[Prayer.MAGHRIB]!! < t[Prayer.ISHA]!!)
    }

    @Test
    fun hanafiAsrIsLater() {
        val z = ZoneId.of("Asia/Karachi")
        val d = LocalDate.of(2026, 11, 16)
        val h = PrayerTimes.compute(d, 24.86, 67.01, z, CalcMethod.KARACHI, true)[Prayer.ASR]!!
        val s = PrayerTimes.compute(d, 24.86, 67.01, z, CalcMethod.KARACHI, false)[Prayer.ASR]!!
        assertTrue(h > s)
    }

    @Test
    fun shiaJafariMaghribIsLaterThanSunniSunset() {
        val z = ZoneId.of("Asia/Karachi")
        val d = LocalDate.of(2026, 11, 16)
        val sunni = PrayerTimes.compute(d, 24.86, 67.01, z, CalcMethod.KARACHI, Fiqh.HANAFI)
        val shia = PrayerTimes.compute(d, 24.86, 67.01, z, CalcMethod.JAFARI, Fiqh.SHIA_JAFARI)
        // Shia Jafari Maghrib is at 4 degrees below horizon (~14-20 min after Sunni sunset)
        val diff = shia[Prayer.MAGHRIB]!! - sunni[Prayer.MAGHRIB]!!
        assertTrue("Expected Shia Maghrib 10-25 min later, got $diff", diff in 10.0..25.0)
        // Standard Asr for Shia (earlier than Hanafi Asr)
        assertTrue(shia[Prayer.ASR]!! < sunni[Prayer.ASR]!!)
    }

    @Test
    fun parsesAladhanMonthlyJsonAndCityCatalog() {
        val sample = """
            {
              "code": 200,
              "data": [
                {
                  "timings": {
                    "Fajr": "05:40 (PKT)",
                    "Dhuhr": "12:16 (PKT)",
                    "Asr": "16:15 (PKT)",
                    "Maghrib": "17:45 (PKT)",
                    "Isha": "19:05 (PKT)"
                  },
                  "date": { "gregorian": { "date": "16-11-2026" } }
                }
              ]
            }
        """.trimIndent()
        val parsed = OnlinePrayerSync.parseAladhanMonthJson(sample)
        val day = parsed["2026-11-16"]!!
        assertEquals(5 * 60 + 40.0, day[Prayer.FAJR]!!, 0.01)
        assertEquals(12 * 60 + 16.0, day[Prayer.DHUHR]!!, 0.01)
        assertEquals(17 * 60 + 45.0, day[Prayer.MAGHRIB]!!, 0.01)
        assertEquals(0, OnlinePrayerSync.aladhanMethodCode(CalcMethod.KARACHI, Fiqh.SHIA_JAFARI))
        assertEquals(1, OnlinePrayerSync.aladhanSchoolCode(Fiqh.HANAFI))

        assertTrue(CityCatalog.searchOffline("Karachi").any { it.country == "Pakistan" })
        assertEquals(CalcMethod.TURKEY, CityCatalog.suggestMethodFor("Istanbul, Turkey"))
        assertEquals(Fiqh.HANAFI, Settings(hanafi = true, fiqh = null).effectiveFiqh)
        assertEquals(Fiqh.SHAFI, Settings(hanafi = false, fiqh = null).effectiveFiqh)
        assertEquals(Fiqh.SHIA_JAFARI, Settings(hanafi = true, fiqh = Fiqh.SHIA_JAFARI).effectiveFiqh)
    }
}

class ShareCodecTest {
    private val sample = AppData(
        profiles = listOf(
            Profile("a", "Work", listOf(Slot(label = "Zuhr", days = listOf(1, 2, 3), mode = RingMode.SILENT))),
            Profile("b", "Ramadan", listOf(Slot(prayer = Prayer.ISHA, before = 5, after = 45))),
        ),
        activeProfileId = "b",
        settings = Settings(lat = 1.0, lon = 2.0, method = CalcMethod.MWL, hanafi = false),
        geoZones = listOf(
            GeoZone(id = "g1", label = "Masjid", lat = 24.8607, lon = 67.0011, radiusMeters = 120, mode = RingMode.DND),
        ),
    )

    @Test
    fun codeRoundTrip() {
        val code = ShareCodec.toCode(sample)
        assertTrue(code.startsWith(ShareCodec.PREFIX))
        val b = ShareCodec.parse("hey check this out\n$code\nthanks")!!
        assertEquals(2, b.profiles.size)
        assertEquals(2, b.slotCount)
        assertEquals(1, b.activeProfile)
        assertEquals(CalcMethod.MWL, b.method)
        assertEquals(listOf(1, 2, 3), b.profiles[0].slots[0].days)
        assertEquals(Prayer.ISHA, b.profiles[1].slots[0].prayer)
        assertTrue(b.geoZones.isEmpty())
    }

    @Test
    fun codeRoundTripWithLocationsOptIn() {
        val code = ShareCodec.toCode(sample, includeLocations = true)
        val wrapped = code.chunked(40).joinToString("\n")
        val b = ShareCodec.parse(wrapped)!!
        assertEquals(1, b.geoZones.size)
        assertEquals("Masjid", b.geoZones[0].label)
        assertEquals(120, b.geoZones[0].radiusMeters)
        assertEquals(RingMode.DND, b.geoZones[0].mode)
    }

    @Test
    fun jsonRoundTripAndNoLocationLeak() {
        val json = ShareCodec.toJson(sample)
        assertTrue(!json.contains("\"lat\""))
        assertEquals(2, ShareCodec.parse(json)!!.profiles.size)
    }

    @Test
    fun garbageIsRejected() {
        assertNull(ShareCodec.parse("hello"))
        assertNull(ShareCodec.parse("SALAHMUTE1:@@@"))
        assertNull(ShareCodec.parse("{\"format\":\"other\"}"))
    }
}

class PlaceSearchTest {
    @Test
    fun parsesPhotonGeoJson() {
        val sample = """
            {
              "features": [
                {
                  "geometry": { "coordinates": [73.0372, 33.7297] },
                  "properties": { "name": "Faisal Mosque", "city": "Islamabad", "country": "Pakistan" }
                }
              ]
            }
        """.trimIndent()
        val list = PlaceSearch.parsePhotonJson(sample)
        assertEquals(1, list.size)
        assertEquals("Faisal Mosque", list[0].name)
        assertEquals("Islamabad, Pakistan", list[0].subtitle)
        assertEquals(33.7297, list[0].lat, 0.0001)
        assertEquals(73.0372, list[0].lon, 0.0001)
    }

    @Test
    fun parsesNominatimSearchAndReverse() {
        val searchJson = """
            [
              { "lat": "21.4225", "lon": "39.8262", "name": "Masjid al-Haram", "display_name": "Masjid al-Haram, Makkah, Saudi Arabia" }
            ]
        """.trimIndent()
        val list = PlaceSearch.parseNominatimJson(searchJson)
        assertEquals(1, list.size)
        assertEquals("Masjid al-Haram", list[0].name)
        assertEquals("Makkah, Saudi Arabia", list[0].subtitle)

        val revJson = """
            { "name": "Jamia Masjid", "address": { "road": "Main Blvd", "city": "Karachi" }, "display_name": "Jamia Masjid, Main Blvd, Karachi" }
        """.trimIndent()
        assertEquals("Jamia Masjid, Main Blvd", PlaceSearch.parseReverseNominatimJson(revJson))
    }

    @Test
    fun parsesCustomAccentHex() {
        assertEquals(0xFF5865F2.toInt(), parseHexColor("#5865F2"))
        assertEquals(0xFF10B981.toInt(), parseHexColor("10b981"))
        assertNull(parseHexColor("#FFF"))
        assertNull(parseHexColor("not-a-color"))
    }
}

class ScheduleOpsTest {
    @Test
    fun editFridayOnlySplitsSharedSlotCleanly() {
        val dhuhr = Slot(id = "dhuhr", label = "Dhuhr", prayer = Prayer.DHUHR, days = Slot.ALL_DAYS)
        val jummah = Slot(label = "Jummah", startMin = 13 * 60, endMin = 14 * 60 + 15, prayer = null, days = listOf(5))

        val res = ScheduleOps.upsertForDay(listOf(dhuhr), originalId = "dhuhr", edited = jummah, day = 5, applyAllDays = false)
        assertEquals(2, res.size)
        val remainingDhuhr = res.first { it.id == "dhuhr" }
        assertEquals(listOf(1, 2, 3, 4, 6, 7), remainingDhuhr.days)
        val fridayOnly = res.first { it.label == "Jummah" }
        assertEquals(listOf(5), fridayOnly.days)
        assertNull(fridayOnly.prayer)
    }

    @Test
    fun toggleAndDeleteSingleDayKeepOtherDaysIntact() {
        val fajr = Slot(id = "fajr", label = "Fajr", prayer = Prayer.FAJR, days = Slot.ALL_DAYS, enabled = true)
        val toggled = ScheduleOps.setEnabledForDay(listOf(fajr), id = "fajr", day = 7, enabled = false)
        assertEquals(2, toggled.size)
        assertTrue(toggled.first { 1 in it.days }.enabled)
        assertTrue(!toggled.first { 7 in it.days }.enabled)

        val deletedMon = ScheduleOps.deleteForDay(listOf(fajr), id = "fajr", day = 1, allDays = false)
        assertEquals(1, deletedMon.size)
        assertEquals(listOf(2, 3, 4, 5, 6, 7), deletedMon[0].days)
    }

    @Test
    fun copyDayMirrorsScheduleWithoutBloatAndPreservesUnselectedDays() {
        val fajr = Slot(id = "fajr", label = "Fajr", prayer = Prayer.FAJR, days = Slot.ALL_DAYS)
        val monClass = Slot(id = "cls", label = "Class", startMin = 600, endMin = 660, days = listOf(1))
        val tueGym = Slot(id = "gym", label = "Gym", startMin = 1080, endMin = 1140, days = listOf(2, 4))

        val copied = ScheduleOps.copyDayTo(listOf(fajr, monClass, tueGym), fromDay = 1, targetDays = setOf(2, 3))
        // Still only 3 slot objects (zero bloat!)
        assertEquals(3, copied.size)
        // Tue & Wed now have Fajr + Class (not Gym)
        assertEquals(setOf("Fajr", "Class"), copied.filter { 2 in it.days }.map { it.label }.toSet())
        assertEquals(setOf("Fajr", "Class"), copied.filter { 3 in it.days }.map { it.label }.toSet())
        // Thu still has Fajr + Gym untouched
        assertEquals(setOf("Fajr", "Gym"), copied.filter { 4 in it.days }.map { it.label }.toSet())
    }

    @Test
    fun setPrayerSlotsReplacesPrayersWhileKeepingCustomSlots() {
        val oldFajr = Slot(id = "f", label = "Fajr", prayer = Prayer.FAJR, days = Slot.ALL_DAYS)
        val jummah = Slot(id = "j", label = "Jummah", startMin = 780, endMin = 855, days = listOf(5))
        val newAsr = Slot(id = "a", label = "Asr", prayer = Prayer.ASR, days = Slot.ALL_DAYS)

        val res = ScheduleOps.setPrayerSlots(listOf(oldFajr, jummah), listOf(newAsr))
        assertEquals(setOf("Jummah", "Asr"), res.map { it.label }.toSet())
    }

    @Test
    fun archiveCopySelectedAndDeleteManyWorkPerDay() {
        val fajr = Slot(id = "f", label = "Fajr", prayer = Prayer.FAJR, days = Slot.ALL_DAYS)
        val meet = Slot(id = "m", label = "Meeting", startMin = 600, endMin = 660, days = listOf(1))
        val jummah = Slot(id = "j", label = "Jummah", startMin = 780, endMin = 840, days = listOf(5))

        // Archive Fajr on Sunday only -> splits Sunday off as archived, Mon..Sat remain active
        val archived = ScheduleOps.setArchivedForDay(listOf(fajr, meet), id = "f", day = 7, archived = true)
        assertEquals(3, archived.size)
        assertTrue(archived.first { 7 in it.days }.archived)
        assertTrue(!archived.first { 1 in it.days && it.label == "Fajr" }.archived)

        // Copy only selected "Meeting" from Mon to Wed & Fri -> Friday's Jummah stays intact!
        val copiedSel = ScheduleOps.copySlotsTo(listOf(fajr, meet, jummah), slotIds = setOf("m"), targetDays = setOf(3, 5))
        assertEquals(listOf(1, 3, 5), copiedSel.first { it.id == "m" }.days)
        assertEquals(listOf(5), copiedSel.first { it.id == "j" }.days)

        // Delete multiple selected slots on Monday only -> Fajr remains on Tue..Sun, Meeting removed
        val deleted = ScheduleOps.deleteManyForDay(listOf(fajr, meet, jummah), ids = setOf("f", "m"), day = 1, allDays = false)
        assertEquals(setOf("Fajr", "Jummah"), deleted.map { it.label }.toSet())
        assertEquals(listOf(2, 3, 4, 5, 6, 7), deleted.first { it.id == "f" }.days)
    }

    @Test
    fun keepAliveServiceShouldRunOnlyWhenActiveWorkExists() {
        val empty = AppData()
        assertTrue(!KeepAliveService.shouldRun(empty))

        val archivedOnly = AppData(
            profiles = listOf(Profile(id = "default", slots = listOf(Slot(archived = true)))),
        )
        assertTrue(!KeepAliveService.shouldRun(archivedOnly))

        val withSlot = AppData(
            profiles = listOf(Profile(id = "default", slots = listOf(Slot(enabled = true)))),
        )
        assertTrue(KeepAliveService.shouldRun(withSlot))
        assertTrue(!KeepAliveService.shouldRun(withSlot.copy(enabled = false)))
        assertTrue(!KeepAliveService.shouldRun(withSlot.copy(settings = Settings(keepAliveService = false))))

        val withGeo = AppData(
            geoEnabled = true,
            geoZones = listOf(GeoZone(label = "Masjid", lat = 24.86, lon = 67.01, enabled = true)),
        )
        assertTrue(KeepAliveService.shouldRun(withGeo))
        assertTrue(!KeepAliveService.shouldRun(withGeo.copy(geoEnabled = false)))
    }

    @Test
    fun parsesGoogleMapsLinksAndSharedText() {
        // Exact !3d/!4d pin wins over @lat,lon viewport center, and place name is decoded cleanly
        val fullUrl = "https://www.google.com/maps/place/Faisal+Mosque/@33.7200,73.0300,15z/data=!3m1!4b1!4m6!3m5!1s0x0!8m2!3d33.7297!4d73.0372"
        val p1 = GoogleMapsLink.parseDirect(fullUrl)!!
        assertEquals(33.7297, p1.lat, 0.0001)
        assertEquals(73.0372, p1.lon, 0.0001)
        assertEquals("Faisal Mosque", p1.label)

        // Multi-line text shared from Google Maps app
        val shared = "Jamia Masjid\nMain Blvd, Karachi\nhttps://www.google.com/maps?q=24.8615,67.0099"
        val p2 = GoogleMapsLink.parseDirect(shared)!!
        assertEquals(24.8615, p2.lat, 0.0001)
        assertEquals(67.0099, p2.lon, 0.0001)
        assertEquals("Jamia Masjid", p2.label)

        // Short link detection & URL-encoded / staticmap HTML parsing
        assertTrue(GoogleMapsLink.looksLikeMapLinkOrCoords("Faisal Mosque\nhttps://maps.app.goo.gl/abc123"))
        assertTrue(GoogleMapsLink.looksLikeMapLinkOrCoords("geo:21.4225,39.8262"))
        val encodedConsent = "https://consent.google.com/m?continue=https%3A%2F%2Fwww.google.com%2Fmaps%2Fplace%2FBadshahi%2BMosque%2F%4031.5880%2C74.3109%2C17z"
        val p3 = GoogleMapsLink.parseDirect(encodedConsent)!!
        assertEquals(31.5880, p3.lat, 0.0001)
        assertEquals(74.3109, p3.lon, 0.0001)
        val staticMapHtml = """<!doctype html><html lang="en"><head><meta property="og:title" content="Faisal Mosque · Islamabad - Google Maps"><meta itemprop="image" content="https://maps.google.com/maps/api/staticmap?center=24.8732%2C67.0337&amp;zoom=17"></head><body></body></html>"""
        val p4 = GoogleMapsLink.parseDirect(staticMapHtml, isHtml = true)!!
        assertEquals(24.8732, p4.lat, 0.0001)
        assertEquals(67.0337, p4.lon, 0.0001)
        assertEquals("Faisal Mosque", p4.label)

        // HTML without og:title must NEVER leak <!doctype html> as a place label
        val bareHtml = """<!doctype html><html><meta itemprop="image" content="https://maps.google.com/maps/api/staticmap?center=24.8732%2C67.0337"></html>"""
        val p5 = GoogleMapsLink.parseDirect(bareHtml, isHtml = true)!!
        assertNull(p5.label)

        // Google Maps reversed-attribute <meta content="AH Pharmacy · ..." itemprop="name">
        val revMetaHtml = """<!doctype html><html><meta content="AH Pharmacy · Main Rd" itemprop="name"><meta content="https://maps.google.com/maps/api/staticmap?center=31.5204%2C74.3587&amp;zoom=17" itemprop="image"></html>"""
        val p6 = GoogleMapsLink.parseDirect(revMetaHtml, isHtml = true)!!
        assertEquals(31.5204, p6.lat, 0.0001)
        assertEquals(74.3587, p6.lon, 0.0001)
        assertEquals("AH Pharmacy", p6.label)
    }

    @Test
    fun localBackupRoundTripsFullAppData() {
        val original = AppData(
            profiles = listOf(
                Profile(
                    id = "p1",
                    name = "Work",
                    slots = listOf(Slot(label = "Dhuhr", prayer = Prayer.DHUHR, mode = RingMode.SILENT)),
                ),
            ),
            activeProfileId = "p1",
            settings = Settings(
                lat = 24.8607,
                lon = 67.0011,
                locationName = "Karachi, Pakistan",
                fiqh = Fiqh.HANAFI,
                onboarded = true,
            ),
            geoZones = listOf(GeoZone(label = "Masjid", lat = 24.8615, lon = 67.0099, radiusMeters = 100)),
        )
        val encoded = BackupManager.encode(original, nowMs = 1_700_000_000_000L)
        val decoded = BackupManager.decode(encoded)!!
        assertEquals(1_700_000_000_000L, decoded.second)
        assertEquals("Karachi, Pakistan", decoded.first.settings.locationName)
        assertEquals(1, decoded.first.profiles.size)
        assertEquals("Work", decoded.first.profiles[0].name)
        assertEquals(1, decoded.first.geoZones.size)
        assertEquals("Masjid", decoded.first.geoZones[0].label)

        // Security: HTTPS + Google host allowlist blocks SSRF/cleartext redirects
        assertTrue(GoogleMapsLink.isAllowedMapsUrl("https://maps.app.goo.gl/abc123"))
        assertTrue(GoogleMapsLink.isAllowedMapsUrl("https://www.google.com/maps/place/Test"))
        assertTrue(!GoogleMapsLink.isAllowedMapsUrl("http://maps.app.goo.gl/abc123"))
        assertTrue(!GoogleMapsLink.isAllowedMapsUrl("https://127.0.0.1/admin"))
        assertTrue(!GoogleMapsLink.isAllowedMapsUrl("https://evil.example.com/maps"))

        // Security: BackupManager sanitizes invalid coordinates & out-of-range values
        val dirty = AppData(
            profiles = listOf(Profile(name = "X".repeat(80), slots = listOf(Slot(startMin = -50, endMin = 9999)))),
            geoZones = listOf(GeoZone(label = "Bad", lat = Double.NaN, lon = 500.0)),
        )
        val clean = BackupManager.sanitizeAppData(dirty)
        assertEquals(40, clean.profiles[0].name.length)
        assertEquals(0, clean.profiles[0].slots[0].startMin)
        assertEquals(1439, clean.profiles[0].slots[0].endMin)
        assertTrue(clean.geoZones.isEmpty())
    }
}
