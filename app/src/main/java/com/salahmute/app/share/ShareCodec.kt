package com.salahmute.app.share

import com.salahmute.app.model.AppData
import com.salahmute.app.model.AppJson
import com.salahmute.app.model.CalcMethod
import com.salahmute.app.model.Fiqh
import com.salahmute.app.model.GeoZone
import com.salahmute.app.model.Profile
import com.salahmute.app.model.Slot
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.UUID
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/** Portable settings bundle. Prayer coordinates are never included; saved locations are optional. */
@Serializable
data class Bundle(
    val format: String = FORMAT,
    val version: Int = 1,
    val method: CalcMethod = CalcMethod.KARACHI,
    val hanafi: Boolean = true,
    val fiqh: Fiqh? = null,
    val activeProfile: Int = 0,
    val profiles: List<Profile> = emptyList(),
    val geoZones: List<GeoZone> = emptyList(),
) {
    val slotCount: Int get() = profiles.sumOf { it.slots.size }

    companion object {
        const val FORMAT = "salahmute"
    }
}

object ShareCodec {
    const val PREFIX = "SALAHMUTE1:"
    private val TOKEN_REGEX = Regex("SALAHMUTE1:[A-Za-z0-9_\\-]+")
    private val WRAPPED_REGEX = Regex("SALAHMUTE1:[A-Za-z0-9_\\-\\s]+")
    private val pretty = Json(from = AppJson) { prettyPrint = true }

    fun toBundle(data: AppData, includeLocations: Boolean = false): Bundle = Bundle(
        method = data.settings.method,
        hanafi = data.settings.effectiveFiqh == Fiqh.HANAFI,
        fiqh = data.settings.effectiveFiqh,
        activeProfile = data.profiles.indexOfFirst { it.id == data.active.id }.coerceAtLeast(0),
        profiles = data.profiles,
        geoZones = if (includeLocations) data.geoZones else emptyList(),
    )

    fun toJson(data: AppData, includeLocations: Boolean = false): String =
        pretty.encodeToString(Bundle.serializer(), toBundle(data, includeLocations))

    fun toCode(data: AppData, includeLocations: Boolean = false): String {
        val json = AppJson.encodeToString(Bundle.serializer(), toBundle(data, includeLocations))
        val bos = ByteArrayOutputStream()
        GZIPOutputStream(bos).use { it.write(json.toByteArray(Charsets.UTF_8)) }
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bos.toByteArray())
    }

    /** Accepts a share code (possibly surrounded by other text / line-wrapped) or raw JSON. */
    fun parse(text: String): Bundle? {
        val trimmed = text.trim()
        if (trimmed.startsWith("{")) return parseJson(trimmed)
        // Prefer an unbroken token so trailing chat text on the next line is never swallowed.
        TOKEN_REGEX.find(trimmed)?.let { m ->
            decodePayload(m.value.removePrefix(PREFIX))?.let { return it }
        }
        // Fallback for hard-wrapped codes split across lines.
        WRAPPED_REGEX.find(trimmed)?.let { m ->
            val payload = m.value.removePrefix(PREFIX).filterNot { it.isWhitespace() }
            decodePayload(payload)?.let { return it }
        }
        return null
    }

    private const val MAX_DECOMPRESSED_BYTES = 512 * 1024

    private fun decodePayload(payload: String): Bundle? {
        return try {
            if (payload.length > 256 * 1024) return null
            val bytes = Base64.getUrlDecoder().decode(payload)
            val out = ByteArrayOutputStream()
            val buf = ByteArray(4096)
            GZIPInputStream(ByteArrayInputStream(bytes)).use { gz ->
                var total = 0
                while (true) {
                    val n = gz.read(buf)
                    if (n <= 0) break
                    total += n
                    if (total > MAX_DECOMPRESSED_BYTES) return null // Defends against GZIP decompression bombs
                    out.write(buf, 0, n)
                }
            }
            parseJson(out.toByteArray().toString(Charsets.UTF_8))
        } catch (_: Exception) {
            null
        }
    }

    private fun parseJson(json: String): Bundle? = try {
        val b = AppJson.decodeFromString(Bundle.serializer(), json)
        if (b.format != Bundle.FORMAT || b.version > 1 || b.profiles.isEmpty()) null else sanitize(b)
    } catch (_: Exception) {
        null
    }

    /** Clamp values from untrusted input and give everything fresh ids. */
    fun sanitize(b: Bundle): Bundle {
        val profiles = b.profiles.take(20).map { p ->
            Profile(
                id = UUID.randomUUID().toString(),
                name = p.name.take(40).ifBlank { "Profile" },
                slots = p.slots.take(200).map { s ->
                    s.copy(
                        id = UUID.randomUUID().toString(),
                        label = s.label.take(40),
                        startMin = s.startMin.coerceIn(0, 1439),
                        endMin = s.endMin.coerceIn(0, 1439),
                        before = s.before.coerceIn(0, 180),
                        after = s.after.coerceIn(0, 180),
                        days = s.days.filter { it in 1..7 }.distinct().ifEmpty { Slot.ALL_DAYS },
                    )
                },
            )
        }
        val geoZones = b.geoZones.take(50).mapNotNull { z ->
            if (!z.lat.isFinite() || !z.lon.isFinite() || z.lat !in -90.0..90.0 || z.lon !in -180.0..180.0) {
                null
            } else {
                z.copy(
                    id = UUID.randomUUID().toString(),
                    label = z.label.take(40),
                    radiusMeters = z.radiusMeters.coerceIn(30, 5000),
                )
            }
        }
        return b.copy(
            profiles = profiles,
            activeProfile = b.activeProfile.coerceIn(0, profiles.lastIndex),
            geoZones = geoZones,
        )
    }
}
