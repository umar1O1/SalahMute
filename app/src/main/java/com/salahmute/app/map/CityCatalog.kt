package com.salahmute.app.map

import com.salahmute.app.model.CalcMethod

data class CityEntry(
    val city: String,
    val country: String,
    val lat: Double,
    val lon: Double,
    val suggestedMethod: CalcMethod,
) {
    val displayName: String get() = "$city, $country"
}

/**
 * Curated offline catalog of major cities so onboarding Country/City selection works
 * instantaneously even without internet, paired with live online search for any smaller town.
 */
object CityCatalog {
    val entries: List<CityEntry> = listOf(
        // Pakistan
        CityEntry("Karachi", "Pakistan", 24.8607, 67.0011, CalcMethod.KARACHI),
        CityEntry("Lahore", "Pakistan", 31.5204, 74.3587, CalcMethod.KARACHI),
        CityEntry("Islamabad", "Pakistan", 33.6844, 73.0479, CalcMethod.KARACHI),
        CityEntry("Rawalpindi", "Pakistan", 33.5651, 73.0169, CalcMethod.KARACHI),
        CityEntry("Faisalabad", "Pakistan", 31.4504, 73.1350, CalcMethod.KARACHI),
        CityEntry("Multan", "Pakistan", 30.1575, 71.5249, CalcMethod.KARACHI),
        CityEntry("Peshawar", "Pakistan", 34.0151, 71.5249, CalcMethod.KARACHI),
        CityEntry("Quetta", "Pakistan", 30.1798, 66.9750, CalcMethod.KARACHI),
        CityEntry("Hyderabad", "Pakistan", 25.3960, 68.3578, CalcMethod.KARACHI),
        CityEntry("Sialkot", "Pakistan", 32.4945, 74.5229, CalcMethod.KARACHI),
        // Saudi Arabia & Gulf
        CityEntry("Makkah", "Saudi Arabia", 21.4225, 39.8262, CalcMethod.MAKKAH),
        CityEntry("Madinah", "Saudi Arabia", 24.4672, 39.6112, CalcMethod.MAKKAH),
        CityEntry("Riyadh", "Saudi Arabia", 24.7136, 46.6753, CalcMethod.MAKKAH),
        CityEntry("Jeddah", "Saudi Arabia", 21.4858, 39.1925, CalcMethod.MAKKAH),
        CityEntry("Dammam", "Saudi Arabia", 26.4207, 50.0888, CalcMethod.MAKKAH),
        CityEntry("Dubai", "UAE", 25.2048, 55.2708, CalcMethod.MAKKAH),
        CityEntry("Abu Dhabi", "UAE", 24.4539, 54.3773, CalcMethod.MAKKAH),
        CityEntry("Sharjah", "UAE", 25.3463, 55.4209, CalcMethod.MAKKAH),
        CityEntry("Doha", "Qatar", 25.2854, 51.5310, CalcMethod.MAKKAH),
        CityEntry("Kuwait City", "Kuwait", 29.3759, 47.9774, CalcMethod.MAKKAH),
        CityEntry("Muscat", "Oman", 23.5880, 58.3829, CalcMethod.MAKKAH),
        CityEntry("Manama", "Bahrain", 26.2285, 50.5860, CalcMethod.MAKKAH),
        // India & Bangladesh
        CityEntry("New Delhi", "India", 28.6139, 77.2090, CalcMethod.KARACHI),
        CityEntry("Mumbai", "India", 19.0760, 72.8777, CalcMethod.KARACHI),
        CityEntry("Hyderabad", "India", 17.3850, 78.4867, CalcMethod.KARACHI),
        CityEntry("Lucknow", "India", 26.8467, 80.9462, CalcMethod.KARACHI),
        CityEntry("Bengaluru", "India", 12.9716, 77.5946, CalcMethod.KARACHI),
        CityEntry("Kolkata", "India", 22.5726, 88.3639, CalcMethod.KARACHI),
        CityEntry("Srinagar", "India", 34.0837, 74.7973, CalcMethod.KARACHI),
        CityEntry("Dhaka", "Bangladesh", 23.8103, 90.4125, CalcMethod.KARACHI),
        CityEntry("Chittagong", "Bangladesh", 22.3569, 91.7832, CalcMethod.KARACHI),
        // Turkey
        CityEntry("Istanbul", "Turkey", 41.0082, 28.9784, CalcMethod.TURKEY),
        CityEntry("Ankara", "Turkey", 39.9334, 32.8597, CalcMethod.TURKEY),
        CityEntry("Izmir", "Turkey", 38.4237, 27.1428, CalcMethod.TURKEY),
        // Iran & Iraq
        CityEntry("Tehran", "Iran", 35.6892, 51.3890, CalcMethod.JAFARI),
        CityEntry("Qom", "Iran", 34.6416, 50.8746, CalcMethod.JAFARI),
        CityEntry("Mashhad", "Iran", 36.2605, 59.6168, CalcMethod.JAFARI),
        CityEntry("Baghdad", "Iraq", 33.3152, 44.3661, CalcMethod.MWL),
        CityEntry("Najaf", "Iraq", 31.9996, 44.3267, CalcMethod.JAFARI),
        CityEntry("Karbala", "Iraq", 32.6160, 44.0249, CalcMethod.JAFARI),
        // Egypt, Levant & North Africa
        CityEntry("Cairo", "Egypt", 30.0444, 31.2357, CalcMethod.EGYPT),
        CityEntry("Alexandria", "Egypt", 31.2001, 29.9187, CalcMethod.EGYPT),
        CityEntry("Amman", "Jordan", 31.9454, 35.9284, CalcMethod.EGYPT),
        CityEntry("Beirut", "Lebanon", 33.8938, 35.5018, CalcMethod.EGYPT),
        CityEntry("Damascus", "Syria", 33.5138, 36.2765, CalcMethod.EGYPT),
        CityEntry("Jerusalem", "Palestine", 31.7683, 35.2137, CalcMethod.EGYPT),
        CityEntry("Casablanca", "Morocco", 33.5731, -7.5898, CalcMethod.MWL),
        CityEntry("Algiers", "Algeria", 36.7538, 3.0588, CalcMethod.EGYPT),
        CityEntry("Tunis", "Tunisia", 36.8065, 10.1815, CalcMethod.EGYPT),
        // Southeast Asia
        CityEntry("Jakarta", "Indonesia", -6.2088, 106.8456, CalcMethod.MWL),
        CityEntry("Surabaya", "Indonesia", -7.2575, 112.7521, CalcMethod.MWL),
        CityEntry("Bandung", "Indonesia", -6.9175, 107.6191, CalcMethod.MWL),
        CityEntry("Kuala Lumpur", "Malaysia", 3.1390, 101.6869, CalcMethod.MWL),
        CityEntry("Singapore", "Singapore", 1.3521, 103.8198, CalcMethod.MWL),
        // Europe, UK & Russia
        CityEntry("London", "United Kingdom", 51.5072, -0.1276, CalcMethod.MWL),
        CityEntry("Birmingham", "United Kingdom", 52.4862, -1.8904, CalcMethod.MWL),
        CityEntry("Manchester", "United Kingdom", 53.4808, -2.2426, CalcMethod.MWL),
        CityEntry("Paris", "France", 48.8566, 2.3522, CalcMethod.MWL),
        CityEntry("Berlin", "Germany", 52.5200, 13.4050, CalcMethod.MWL),
        CityEntry("Frankfurt", "Germany", 50.1109, 8.6821, CalcMethod.MWL),
        CityEntry("Madrid", "Spain", 40.4168, -3.7038, CalcMethod.MWL),
        CityEntry("Amsterdam", "Netherlands", 52.3676, 4.9041, CalcMethod.MWL),
        CityEntry("Moscow", "Russia", 55.7558, 37.6173, CalcMethod.MWL),
        CityEntry("Kazan", "Russia", 55.7961, 49.1064, CalcMethod.MWL),
        // North America & Australia
        CityEntry("New York", "United States", 40.7128, -74.0060, CalcMethod.ISNA),
        CityEntry("Chicago", "United States", 41.8781, -87.6298, CalcMethod.ISNA),
        CityEntry("Houston", "United States", 29.7604, -95.3698, CalcMethod.ISNA),
        CityEntry("Los Angeles", "United States", 34.0522, -118.2437, CalcMethod.ISNA),
        CityEntry("Toronto", "Canada", 43.6532, -79.3832, CalcMethod.ISNA),
        CityEntry("Sydney", "Australia", -33.8688, 151.2093, CalcMethod.MWL),
        CityEntry("Melbourne", "Australia", -37.8136, 144.9631, CalcMethod.MWL),
    )

    fun searchOffline(query: String): List<CityEntry> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return entries
        return entries.filter {
            it.city.lowercase().contains(q) || it.country.lowercase().contains(q)
        }
    }

    /** Picks the most standard regional calculation method for a country or subtitle string. */
    fun suggestMethodFor(text: String): CalcMethod {
        val s = text.lowercase()
        return when {
            "pakistan" in s || "india" in s || "bangladesh" in s || "afghanistan" in s -> CalcMethod.KARACHI
            "saudi" in s || "uae" in s || "emirates" in s || "qatar" in s || "kuwait" in s || "oman" in s || "bahrain" in s -> CalcMethod.MAKKAH
            "turkey" in s || "türkiye" in s -> CalcMethod.TURKEY
            "iran" in s -> CalcMethod.JAFARI
            "egypt" in s || "jordan" in s || "syria" in s || "lebanon" in s || "palestine" in s || "algeria" in s || "tunisia" in s || "libya" in s || "sudan" in s -> CalcMethod.EGYPT
            "united states" in s || "usa" in s || "canada" in s -> CalcMethod.ISNA
            else -> CalcMethod.MWL
        }
    }
}
