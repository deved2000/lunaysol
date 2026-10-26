package io.github.lunasolcal.data

import java.time.ZoneId

/**
 * Ein gespeicherter Ort.
 * @param longitude positiv = Ost
 * @param zoneId IANA-Zeitzone, z. B. "Europe/Vienna"
 */
data class Place(
    val name: String,
    val subtitle: String,
    val latitude: Double,
    val longitude: Double,
    val zoneId: String,
    val isGps: Boolean = false
) {
    val zone: ZoneId
        get() = try { ZoneId.of(zoneId) } catch (e: Exception) { ZoneId.systemDefault() }

    companion object {
        val DEFAULTS = listOf(
            Place("Wien", "Österreich", 48.228719, 16.371786, "Europe/Vienna"),
            Place("Bruneck - Brunico", "Italien", 46.795833, 11.935000, "Europe/Rome"),
            Place("Flachau", "Österreich", 47.343611, 13.391944, "Europe/Vienna"),
            Place("Madrid", "Spanien", 40.416389, -3.703611, "Europe/Madrid"),
            Place("Neubrandenburg", "Mecklenburg-Vorpommern, Deutschland", 53.557222, 13.260278, "Europe/Berlin"),
            Place("Hamburg", "Deutschland", 53.550000, 10.000556, "Europe/Berlin"),
            Place("New York", "USA", 40.712778, -74.006111, "America/New_York"),
            Place("Sydney", "Australien", -33.868820, 151.209290, "Australia/Sydney")
        )
    }
}
