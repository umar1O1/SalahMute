package com.salahmute.app.map

import com.salahmute.app.engine.Engine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

data class PlaceResult(
    val name: String,
    val subtitle: String,
    val lat: Double,
    val lon: Double,
)

/**
 * Open-source place search & reverse geocoding (Photon by Komoot with OpenStreetMap Nominatim fallback).
 * Requires no API keys. Also parses raw coordinates / Maps URLs offline.
 */
object PlaceSearch {
    private const val USER_AGENT = "SalahMute/1.0 (Android; OpenSource)"
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun search(
        query: String,
        biasLat: Double? = null,
        biasLon: Double? = null,
    ): List<PlaceResult> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.length < 2) return@withContext emptyList()

        // Instant offline coordinate / Maps URL match
        Engine.parseCoordinates(q)?.let { (lat, lon) ->
            return@withContext listOf(
                PlaceResult(
                    name = "%.5f, %.5f".format(Locale.US, lat, lon),
                    subtitle = "",
                    lat = lat,
                    lon = lon,
                ),
            )
        }

        // Reject raw URLs so unresolved web links never get fuzzy-matched to random foreign villages
        if (q.contains("http://", ignoreCase = true) || q.contains("https://", ignoreCase = true)) {
            return@withContext emptyList()
        }

        val enc = URLEncoder.encode(q, "UTF-8")
        val bias = if (biasLat != null && biasLon != null) {
            "&lat=${"%.4f".format(Locale.US, biasLat)}&lon=${"%.4f".format(Locale.US, biasLon)}"
        } else {
            ""
        }

        val photonBody = httpGet("https://photon.komoot.io/api/?q=$enc&limit=6&lang=en$bias")
        val photonResults = photonBody?.let { parsePhotonJson(it) }.orEmpty()
        if (photonResults.isNotEmpty()) return@withContext photonResults

        val nomBody = httpGet("https://nominatim.openstreetmap.org/search?format=jsonv2&limit=6&addressdetails=1&accept-language=en&q=$enc")
        nomBody?.let { parseNominatimJson(it) }.orEmpty()
    }

    suspend fun reverseGeocode(lat: Double, lon: Double): String? = withContext(Dispatchers.IO) {
        val la = "%.6f".format(Locale.US, lat)
        val lo = "%.6f".format(Locale.US, lon)

        httpGet("https://photon.komoot.io/reverse?lat=$la&lon=$lo&lang=en")?.let { body ->
            parsePhotonJson(body).firstOrNull()?.let { r ->
                return@withContext listOf(r.name, r.subtitle.substringBefore(","))
                    .filter { it.isNotBlank() }
                    .distinct()
                    .joinToString(", ")
                    .ifBlank { null }
            }
        }

        httpGet("https://nominatim.openstreetmap.org/reverse?format=jsonv2&zoom=17&accept-language=en&lat=$la&lon=$lo")?.let { body ->
            parseReverseNominatimJson(body)
        }
    }

    /** Reverse-geocodes street/area ONLY (never a nearby shop/business name) for unnamed map pins. */
    suspend fun reverseAreaOnly(lat: Double, lon: Double): String? = withContext(Dispatchers.IO) {
        val la = "%.6f".format(Locale.US, lat)
        val lo = "%.6f".format(Locale.US, lon)
        httpGet("https://nominatim.openstreetmap.org/reverse?format=jsonv2&zoom=16&accept-language=en&lat=$la&lon=$lo")?.let { body ->
            try {
                val obj = json.parseToJsonElement(body).jsonObject
                val addr = obj["address"]?.jsonObject ?: return@let null
                listOfNotNull(
                    addr.str("road"),
                    addr.str("neighbourhood") ?: addr.str("suburb") ?: addr.str("city") ?: addr.str("town"),
                ).distinct().joinToString(", ").ifBlank { null }
            } catch (_: Exception) {
                null
            }
        }
    }

    internal fun parsePhotonJson(body: String): List<PlaceResult> = try {
        val root = json.parseToJsonElement(body).jsonObject
        val features = root["features"]?.jsonArray ?: return emptyList()
        features.mapNotNull { el ->
            val obj = el.jsonObject
            val coords = obj["geometry"]?.jsonObject?.get("coordinates")?.jsonArray ?: return@mapNotNull null
            if (coords.size < 2) return@mapNotNull null
            val lon = coords[0].jsonPrimitive.doubleOrNull ?: return@mapNotNull null
            val lat = coords[1].jsonPrimitive.doubleOrNull ?: return@mapNotNull null
            val props = obj["properties"]?.jsonObject ?: return@mapNotNull null

            val name = props.str("name")
                ?: props.str("street")
                ?: props.str("district")
                ?: props.str("city")
                ?: return@mapNotNull null
            val parts = listOfNotNull(
                props.str("street")?.takeIf { it != name },
                props.str("district")?.takeIf { it != name },
                props.str("city")?.takeIf { it != name },
                props.str("state")?.takeIf { it != name },
                props.str("country")?.takeIf { it != name },
            ).distinct()
            PlaceResult(name = name, subtitle = parts.joinToString(", "), lat = lat, lon = lon)
        }
    } catch (_: Exception) {
        emptyList()
    }

    internal fun parseNominatimJson(body: String): List<PlaceResult> = try {
        val arr: JsonArray = json.parseToJsonElement(body).jsonArray
        arr.mapNotNull { el ->
            val obj = el.jsonObject
            val lat = obj.str("lat")?.toDoubleOrNull() ?: return@mapNotNull null
            val lon = obj.str("lon")?.toDoubleOrNull() ?: return@mapNotNull null
            val display = obj.str("display_name") ?: return@mapNotNull null
            val name = obj.str("name")?.ifBlank { null } ?: display.substringBefore(",").trim()
            val subtitle = display.substringAfter(",", "").trim()
            PlaceResult(name = name, subtitle = subtitle, lat = lat, lon = lon)
        }
    } catch (_: Exception) {
        emptyList()
    }

    internal fun parseReverseNominatimJson(body: String): String? = try {
        val obj = json.parseToJsonElement(body).jsonObject
        val name = obj.str("name")?.ifBlank { null }
        val addr = obj["address"]?.jsonObject
        val area = addr?.let {
            it.str("road") ?: it.str("neighbourhood") ?: it.str("suburb") ?: it.str("city") ?: it.str("town")
        }
        val primary = listOfNotNull(name, area).distinct().joinToString(", ")
        primary.ifBlank { obj.str("display_name")?.split(",")?.take(2)?.joinToString(",")?.trim() }
    } catch (_: Exception) {
        null
    }

    private fun JsonObject.str(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull?.trim()?.ifBlank { null }

    private fun httpGet(url: String): String? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 5000
                readTimeout = 5000
                requestMethod = "GET"
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Accept-Language", "en-US,en;q=0.9")
            }
            if (conn.responseCode in 200..299) {
                conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            } else {
                null
            }
        } catch (_: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }
}
