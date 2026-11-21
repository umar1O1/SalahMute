package com.salahmute.app.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID

/** Ordered from weakest to strongest: when slots overlap, the strongest wins. */
@Serializable
enum class RingMode { VIBRATE, SILENT, DND }

@Serializable
enum class Prayer { FAJR, DHUHR, ASR, MAGHRIB, ISHA }

@Serializable
enum class Fiqh {
    HANAFI,
    SHAFI,
    SHIA_JAFARI,
}

@Serializable
enum class CalcMethod(
    val fajrAngle: Double,
    val ishaAngle: Double,
    val ishaAfterMaghribMin: Int = 0,
    val maghribAngle: Double = 0.833,
) {
    MWL(18.0, 17.0),
    ISNA(15.0, 15.0),
    EGYPT(19.5, 17.5),
    MAKKAH(18.5, 0.0, 90),
    KARACHI(18.0, 18.0),
    TURKEY(18.0, 17.0),
    JAFARI(16.0, 14.0, 0, 4.0),
}

/**
 * A time range during which the phone is muted.
 * Fixed slots use [startMin]/[endMin] (minutes after midnight; end <= start means it crosses midnight).
 * Prayer slots (when [prayer] != null) run from (prayer time - [before]) to (prayer time + [after]).
 * [days] uses ISO numbering: 1 = Monday ... 7 = Sunday.
 */
@Serializable
data class Slot(
    val id: String = UUID.randomUUID().toString(),
    val label: String = "",
    val startMin: Int = 13 * 60,
    val endMin: Int = 13 * 60 + 30,
    val mode: RingMode = RingMode.VIBRATE,
    val days: List<Int> = ALL_DAYS,
    val enabled: Boolean = true,
    val archived: Boolean = false,
    val prayer: Prayer? = null,
    val before: Int = 0,
    val after: Int = 20,
) {
    companion object {
        val ALL_DAYS = listOf(1, 2, 3, 4, 5, 6, 7)
        val WEEKDAYS = listOf(1, 2, 3, 4, 5)
    }
}

@Serializable
data class Profile(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Default",
    val slots: List<Slot> = emptyList(),
)

@Serializable
enum class ThemeMode { SYSTEM, DARK, LIGHT, BLACK, NAVY, FROST, SEPIA, FOREST }

@Serializable
data class Settings(
    val lat: Double? = null,
    val lon: Double? = null,
    val locationName: String = "",
    val countryName: String = "",
    val method: CalcMethod = CalcMethod.KARACHI,
    val hanafi: Boolean = true,
    val fiqh: Fiqh? = null,
    val useInternetPrayers: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val accentHex: String = "",
    val onboarded: Boolean = false,
    val keepAliveService: Boolean = true,
    val compactCardActions: Boolean = true,
    val tapCardToEdit: Boolean = false,
) {
    val effectiveFiqh: Fiqh
        get() = fiqh ?: if (hanafi) Fiqh.HANAFI else Fiqh.SHAFI
}

@Serializable
data class GeoZone(
    val id: String = UUID.randomUUID().toString(),
    val label: String = "",
    val lat: Double = 0.0,
    val lon: Double = 0.0,
    val radiusMeters: Int = 100,
    val mode: RingMode = RingMode.VIBRATE,
    val enabled: Boolean = true,
)

@Serializable
data class AppData(
    val profiles: List<Profile> = listOf(Profile(id = "default", name = "Default")),
    val activeProfileId: String = "default",
    val settings: Settings = Settings(),
    val enabled: Boolean = true,
    val geoEnabled: Boolean = true,
    val geoZones: List<GeoZone> = emptyList(),
) {
    val active: Profile
        get() = profiles.firstOrNull { it.id == activeProfileId } ?: profiles.first()
}

val AppJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}
