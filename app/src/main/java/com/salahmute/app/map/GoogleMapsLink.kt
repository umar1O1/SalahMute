package com.salahmute.app.map

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.util.Locale

data class ParsedMapLocation(
    val lat: Double,
    val lon: Double,
    val label: String? = null,
)

/**
 * Parses Google Maps links (full URLs, short `maps.app.goo.gl` links, `geo:` URIs,
 * or plain `lat, lon` coordinates) and launches Google Maps.
 */
object GoogleMapsLink {
    private val URL_RE = Regex("""https?://\S+""", RegexOption.IGNORE_CASE)
    private val PIN_3D4D_RE = Regex("""!3d(-?\d{1,2}(?:\.\d+)?)!4d(-?\d{1,3}(?:\.\d+)?)""")
    private val AT_COORD_RE = Regex("""@(-?\d{1,2}(?:\.\d+)?),(-?\d{1,3}(?:\.\d+)?)""")
    private val QUERY_COORD_RE = Regex("""(?:[?&](?:q|ll|query|center|daddr|sll)=|geo:)(-?\d{1,2}(?:\.\d+)?)(?:,|%2C)\s*(-?\d{1,3}(?:\.\d+)?)""", RegexOption.IGNORE_CASE)
    private val STATIC_MAP_RE = Regex("""staticmap\?[^"'\s]*?center=(-?\d{1,2}\.\d+)(?:,|%2C)(-?\d{1,3}\.\d+)""", RegexOption.IGNORE_CASE)
    private val PLAIN_COORD_RE = Regex("""(-?\d{1,2}\.\d+)\s*,\s*(-?\d{1,3}\.\d+)""")
    private val PLACE_PATH_RE = Regex("""/maps/place/([^/@?]+)""", RegexOption.IGNORE_CASE)
    private val QUERY_TEXT_RE = Regex("""[?&](?:q|query)=([^&]+)""", RegexOption.IGNORE_CASE)
    // Match both attribute orders: Google Maps frequently outputs <meta content="AH Pharmacy..." itemprop="name">
    private val META_TITLE_PATTERNS = listOf(
        Regex("""<meta\s+[^>]*?(?:property|name)=["']og:title["'][^>]*?content=["']([^"']+)["']""", RegexOption.IGNORE_CASE),
        Regex("""<meta\s+[^>]*?content=["']([^"']+)["'][^>]*?(?:property|name)=["']og:title["']""", RegexOption.IGNORE_CASE),
        Regex("""<meta\s+[^>]*?itemprop=["']name["'][^>]*?content=["']([^"']+)["']""", RegexOption.IGNORE_CASE),
        Regex("""<meta\s+[^>]*?content=["']([^"']+)["'][^>]*?itemprop=["']name["']""", RegexOption.IGNORE_CASE),
    )
    private val HTML_TITLE_RE = Regex("""<title[^>]*>([^<]+)</title>""", RegexOption.IGNORE_CASE)
    private val MARKUP_RE = Regex("""<|>|<!doctype|<html|\{|\}""", RegexOption.IGNORE_CASE)

    /** True if [raw] looks like a Google Maps link or coordinate pair worth parsing/resolving. */
    fun looksLikeMapLinkOrCoords(raw: String): Boolean {
        val s = raw.trim()
        if (s.isEmpty()) return false
        val lower = s.lowercase(Locale.US)
        if ("maps.app.goo.gl" in lower || "goo.gl/maps" in lower || "google.com/maps" in lower ||
            "maps.google." in lower || "geo:" in lower || "g.co/kgs" in lower || "maps.apple.com" in lower
        ) {
            return true
        }
        return PLAIN_COORD_RE.containsMatchIn(s)
    }

    /** Pure, JVM-testable parser for full Google Maps URLs (raw or URL-encoded), `geo:` URIs, or coordinates. */
    fun parseDirect(raw: String, fallbackLabel: String? = null, isHtml: Boolean = false): ParsedMapLocation? {
        val text = raw.trim()
        if (text.isEmpty()) return null
        val looksHtml = isHtml || MARKUP_RE.containsMatchIn(text.take(400))
        val decoded = if (looksHtml) text else decodeSafe(text)

        val extractedLabel = sanitizeLabel(
            if (looksHtml) {
                fallbackLabel
                    ?: extractOgTitle(text)
                    ?: extractPlaceFromUrl(text)
            } else {
                extractSharedTitle(text)
                    ?: extractPlaceFromUrl(text)
                    ?: extractPlaceFromUrl(decoded)
                    ?: fallbackLabel
            },
        )

        val candidates = if (looksHtml) listOf(text) else listOf(text, decoded)
        for (candidate in candidates) {
            // 1) Prefer exact pin (!3d<lat>!4d<lon>) over viewport center (@lat,lon)
            PIN_3D4D_RE.findAll(candidate).lastOrNull()?.let { m ->
                toLocation(m.groupValues[1], m.groupValues[2], extractedLabel)?.let { return it }
            }
            // 2) Query/geo parameter (?q=lat,lon, ?ll=lat,lon, ?center=lat,lon, geo:lat,lon)
            QUERY_COORD_RE.find(candidate)?.let { m ->
                toLocation(m.groupValues[1], m.groupValues[2], extractedLabel)?.let { return it }
            }
            // 3) Static map preview center inside Google Maps HTML
            STATIC_MAP_RE.find(candidate)?.let { m ->
                toLocation(m.groupValues[1], m.groupValues[2], extractedLabel)?.let { return it }
            }
            // 4) Viewport center (@lat,lon)
            AT_COORD_RE.find(candidate)?.let { m ->
                toLocation(m.groupValues[1], m.groupValues[2], extractedLabel)?.let { return it }
            }
        }
        // 5) Plain decimal coordinate pair ONLY for non-HTML short text (never random numbers inside HTML!)
        if (!looksHtml) {
            PLAIN_COORD_RE.find(text)?.let { m ->
                toLocation(m.groupValues[1], m.groupValues[2], extractedLabel)?.let { return it }
            }
        }
        return null
    }

    /**
     * Resolves any Google Maps shared text or pasted link (including `maps.app.goo.gl` short links,
     * URL-encoded consent redirects, HTML staticmap centers, or place-name queries) into exact coordinates.
     */
    suspend fun resolve(raw: String, biasLat: Double? = null, biasLon: Double? = null): ParsedMapLocation? =
        withContext(Dispatchers.IO) {
            val sharedTitle = extractSharedTitle(raw)
            parseDirect(raw, fallbackLabel = sharedTitle)?.let { direct ->
                return@withContext enrichLabelIfMissing(direct)
            }

            var bestPlaceName: String? = sharedTitle
            val url = URL_RE.find(raw)?.value?.trimEnd('.', ',', ')', ']')
            if (url != null) {
                var current: String = url.replaceFirst(Regex("^http://", RegexOption.IGNORE_CASE), "https://")
                for (hop in 0 until 8) {
                    if (!isAllowedMapsUrl(current)) break
                    val conn = try {
                        (URL(current).openConnection() as HttpURLConnection).apply {
                            instanceFollowRedirects = false
                            requestMethod = "GET"
                            connectTimeout = 8_000
                            readTimeout = 8_000
                            // Clean lightweight User-Agent gets an immediate 302 Location redirect from maps.app.goo.gl
                            // instead of Google's heavy JS-only mobile web shell.
                            setRequestProperty("User-Agent", "SalahMute/1.0 (Android)")
                            setRequestProperty("Accept-Language", "en-US,en;q=0.9")
                        }
                    } catch (_: Exception) {
                        break
                    }
                    try {
                        val code = conn.responseCode
                        val locHeader = conn.getHeaderField("Location")?.takeIf { it.isNotBlank() }
                        if (code in 300..399 && locHeader != null) {
                            val resolvedUrl: String = try {
                                URL(URL(current), locHeader).toString()
                            } catch (_: Exception) {
                                locHeader
                            }
                            bestPlaceName = bestPlaceName
                                ?: extractPlaceFromUrl(resolvedUrl)
                                ?: extractQueryText(resolvedUrl)
                            parseDirect(resolvedUrl, fallbackLabel = bestPlaceName)?.let {
                                return@withContext enrichLabelIfMissing(it)
                            }
                            val continueUrl = extractContinueUrl(resolvedUrl)
                            if (continueUrl != null) {
                                bestPlaceName = bestPlaceName
                                    ?: extractPlaceFromUrl(continueUrl)
                                    ?: extractQueryText(continueUrl)
                                parseDirect(continueUrl, fallbackLabel = bestPlaceName)?.let {
                                    return@withContext enrichLabelIfMissing(it)
                                }
                                current = continueUrl
                                continue
                            }
                            current = resolvedUrl
                            continue
                        }
                        if (code in 200..299) {
                            val body = readUpTo(conn, 262_144)
                            val htmlTitle = extractOgTitle(body)
                            bestPlaceName = bestPlaceName
                                ?: extractPlaceFromUrl(current)
                                ?: extractQueryText(current)
                                ?: htmlTitle
                            parseDirect(current, fallbackLabel = bestPlaceName)?.let {
                                return@withContext enrichLabelIfMissing(it)
                            }
                            parseDirect(body, fallbackLabel = bestPlaceName, isHtml = true)?.let {
                                return@withContext enrichLabelIfMissing(it)
                            }
                        }
                        break
                    } catch (_: Exception) {
                        break
                    } finally {
                        conn.disconnect()
                    }
                }
            }

            // Final fallback: if Google gave us a clean place name without inline coords, look it up via PlaceSearch
            // Keep the user's/Google's exact name (fallbackQuery) — never replace it with a mismatched neighbor shop!
            val fallbackQuery = sanitizeLabel(bestPlaceName)?.takeIf { it.length >= 2 }
            if (fallbackQuery != null) {
                val hit = PlaceSearch.search(fallbackQuery, biasLat, biasLon).firstOrNull()
                if (hit != null) {
                    return@withContext ParsedMapLocation(hit.lat, hit.lon, fallbackQuery.take(40))
                }
            }
            null
        }

    /** Creates an Intent to open Google Maps centered at [lat]/[lon] (or default view). */
    fun openGoogleMapsIntent(ctx: Context, lat: Double?, lon: Double?): Intent {
        val uri = if (lat != null && lon != null) {
            Uri.parse("geo:${"%.6f".format(Locale.US, lat)},${"%.6f".format(Locale.US, lon)}?z=16")
        } else {
            Uri.parse("geo:0,0?q=")
        }
        val mapsIntent = Intent(Intent.ACTION_VIEW, uri).setPackage("com.google.android.apps.maps")
        if (mapsIntent.resolveActivity(ctx.packageManager) != null) {
            return mapsIntent
        }
        val genericGeo = Intent(Intent.ACTION_VIEW, uri)
        if (genericGeo.resolveActivity(ctx.packageManager) != null) {
            return genericGeo
        }
        val webUri = if (lat != null && lon != null) {
            Uri.parse("https://maps.google.com/?ll=${"%.6f".format(Locale.US, lat)},${"%.6f".format(Locale.US, lon)}&z=16")
        } else {
            Uri.parse("https://maps.google.com/")
        }
        return Intent(Intent.ACTION_VIEW, webUri)
    }

    private suspend fun enrichLabelIfMissing(loc: ParsedMapLocation): ParsedMapLocation {
        if (!loc.label.isNullOrBlank()) return loc
        // Use street/area only — never substitute an unrelated neighboring business name from OSM
        val rev = PlaceSearch.reverseAreaOnly(loc.lat, loc.lon)
            ?.substringBefore(",")
            ?.trim()
            ?.take(40)
            ?.ifBlank { null }
        return loc.copy(label = sanitizeLabel(rev))
    }

    /** Strict HTTPS + Google Maps host allowlist to prevent SSRF / arbitrary redirects from untrusted shares. */
    internal fun isAllowedMapsUrl(rawUrl: String): Boolean {
        val u = try {
            URL(rawUrl)
        } catch (_: Exception) {
            return false
        }
        if (!u.protocol.equals("https", ignoreCase = true)) return false
        val host = u.host.lowercase(Locale.US)
        return host == "maps.app.goo.gl" ||
            host == "goo.gl" ||
            host == "g.co" ||
            host == "google.com" ||
            host.endsWith(".google.com") ||
            host.startsWith("maps.google.") ||
            host.startsWith("www.google.") ||
            host.startsWith("consent.google.")
    }

    private fun readUpTo(conn: HttpURLConnection, maxChars: Int): String {
        return try {
            conn.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                val sb = StringBuilder()
                val buf = CharArray(8192)
                while (sb.length < maxChars) {
                    val n = reader.read(buf, 0, minOf(buf.size, maxChars - sb.length))
                    if (n <= 0) break
                    sb.append(buf, 0, n)
                }
                sb.toString()
            }
        } catch (_: Exception) {
            ""
        }
    }

    private fun decodeSafe(s: String): String = try {
        URLDecoder.decode(s.replace("+", "%2B"), "UTF-8")
    } catch (_: Exception) {
        s
    }

    private fun unescapeHtml(s: String): String = s
        .replace("&amp;", "&")
        .replace("&#39;", "'")
        .replace("&apos;", "'")
        .replace("&quot;", "\"")
        .replace("&#x27;", "'")
        .replace("&nbsp;", " ")
        .trim()

    private fun sanitizeLabel(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val cleaned = unescapeHtml(raw)
            .substringBefore(" · ")
            .substringBefore(" - Google Maps")
            .trim()
        if (cleaned.isBlank()) return null
        if (MARKUP_RE.containsMatchIn(cleaned)) return null
        if (cleaned.startsWith("http://", true) || cleaned.startsWith("https://", true) || cleaned.startsWith("www.", true)) {
            return null
        }
        if (cleaned.equals("Google Maps", ignoreCase = true) ||
            cleaned.startsWith("Dropped pin", ignoreCase = true) ||
            cleaned.startsWith("Shared route", ignoreCase = true) ||
            cleaned.startsWith("Before you continue", ignoreCase = true)
        ) {
            return null
        }
        if (PLAIN_COORD_RE.matches(cleaned)) return null
        return cleaned.take(40)
    }

    private fun extractOgTitle(html: String): String? {
        for (re in META_TITLE_PATTERNS) {
            val raw = re.find(html)?.groupValues?.get(1)
            sanitizeLabel(raw)?.let { return it }
        }
        val title = HTML_TITLE_RE.find(html)?.groupValues?.get(1)
        return sanitizeLabel(title)
    }

    private fun extractContinueUrl(url: String): String? {
        if ("consent.google." !in url.lowercase(Locale.US)) return null
        val m = Regex("""[?&]continue=([^&]+)""").find(url) ?: return null
        return try {
            URLDecoder.decode(m.groupValues[1], "UTF-8")
        } catch (_: Exception) {
            null
        }
    }

    private fun extractQueryText(url: String): String? {
        val m = QUERY_TEXT_RE.find(url) ?: return null
        val decoded = try {
            URLDecoder.decode(m.groupValues[1].replace('+', ' '), "UTF-8")
        } catch (_: Exception) {
            m.groupValues[1].replace('+', ' ')
        }.trim()
        return sanitizeLabel(decoded.substringBefore(","))
    }

    private fun toLocation(latStr: String, lonStr: String, label: String?): ParsedMapLocation? {
        val lat = latStr.toDoubleOrNull() ?: return null
        val lon = lonStr.toDoubleOrNull() ?: return null
        if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
        if (lat == 0.0 && lon == 0.0) return null
        return ParsedMapLocation(lat, lon, sanitizeLabel(label))
    }

    private fun extractPlaceFromUrl(raw: String): String? {
        val m = PLACE_PATH_RE.find(raw) ?: return null
        val decoded = try {
            URLDecoder.decode(m.groupValues[1].replace('+', ' '), "UTF-8")
        } catch (_: Exception) {
            m.groupValues[1].replace('+', ' ')
        }.trim()
        return sanitizeLabel(decoded.substringBefore(","))
    }

    private fun extractSharedTitle(raw: String): String? {
        if (MARKUP_RE.containsMatchIn(raw.take(400))) return null
        val lines = raw.lines()
            .map { it.trim() }
            .filter {
                it.isNotBlank() &&
                    !it.startsWith("http://", true) &&
                    !it.startsWith("https://", true) &&
                    !MARKUP_RE.containsMatchIn(it)
            }
        val first = lines.firstOrNull() ?: return null
        return sanitizeLabel(first.substringBefore("https://").substringBefore("http://"))
    }
}
