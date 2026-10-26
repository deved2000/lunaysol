package io.github.lunasolcal.data

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Ortssuche und Zeitzonen-Ermittlung über die freie Open-Meteo-API (kein API-Schlüssel nötig)
 * sowie Standortbestimmung über den Android-LocationManager (ohne Google Play Services).
 */
object GeoService {

    class SearchResult(val name: String, val subtitle: String, val latitude: Double, val longitude: Double, val timezone: String)

    private fun httpGet(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000
        conn.setRequestProperty("User-Agent", "LunaSolCal-Android")
        try {
            if (conn.responseCode !in 200..299) throw java.io.IOException("HTTP ${conn.responseCode}")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    /** Sucht Städte/Orte nach Namen. */
    suspend fun search(query: String): List<SearchResult> = withContext(Dispatchers.IO) {
        val q = URLEncoder.encode(query.trim(), "UTF-8")
        val json = JSONObject(httpGet("https://geocoding-api.open-meteo.com/v1/search?name=$q&count=20&language=de&format=json"))
        val arr = json.optJSONArray("results") ?: return@withContext emptyList()
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val parts = listOf(o.optString("admin1", ""), o.optString("country", ""))
                .filter { it.isNotBlank() && it != o.optString("name") }
            SearchResult(
                name = o.optString("name"),
                subtitle = parts.joinToString(", "),
                latitude = o.getDouble("latitude"),
                longitude = o.getDouble("longitude"),
                timezone = o.optString("timezone", "").ifBlank { fallbackZone(o.getDouble("longitude")).id }
            )
        }
    }

    /** Ermittelt die IANA-Zeitzone für Koordinaten (online). */
    suspend fun timezoneFor(lat: Double, lon: Double): String? = withContext(Dispatchers.IO) {
        try {
            val url = String.format(
                Locale.US,
                "https://api.open-meteo.com/v1/forecast?latitude=%.5f&longitude=%.5f&timezone=auto&daily=sunrise&forecast_days=1",
                lat, lon
            )
            JSONObject(httpGet(url)).optString("timezone").takeIf { it.isNotBlank() && it != "GMT" }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Offline-Ersatz: Gerätezeitzone, wenn sie zur geografischen Länge passt, sonst eine feste UTC-Verschiebung.
     */
    fun fallbackZone(lon: Double): ZoneId {
        val hours = (lon / 15.0).roundToInt().coerceIn(-12, 14)
        val device = ZoneId.systemDefault()
        val deviceStd = device.rules.getStandardOffset(Instant.now()).totalSeconds / 3600
        if (deviceStd == hours) return device
        // Achtung: Etc/GMT-Zonen haben umgekehrtes Vorzeichen (Etc/GMT-1 = UTC+1)
        return if (hours == 0) ZoneId.of("Etc/GMT") else ZoneId.of("Etc/GMT" + (if (hours > 0) "-" else "+") + kotlin.math.abs(hours))
    }

    /** Ortsname per Android-Geocoder (falls verfügbar). */
    @Suppress("DEPRECATION")
    suspend fun reverseName(context: Context, lat: Double, lon: Double): Pair<String, String>? = withContext(Dispatchers.IO) {
        try {
            if (!Geocoder.isPresent()) return@withContext null
            val list = Geocoder(context, Locale.GERMAN).getFromLocation(lat, lon, 1)
            val a = list?.firstOrNull() ?: return@withContext null
            val name = a.locality ?: a.subAdminArea ?: a.adminArea ?: return@withContext null
            name to listOfNotNull(a.adminArea, a.countryName).filter { it != name }.joinToString(", ")
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Fordert eine aktuelle Position an. Ruft [callback] genau einmal auf (null bei Fehler/Zeitüberschreitung).
     * Die Standortberechtigung muss vorher erteilt worden sein.
     */
    @SuppressLint("MissingPermission")
    fun requestLocation(context: Context, callback: (Location?) -> Unit) {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        if (lm == null) { callback(null); return }
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }

        val recent = providers.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .filter { System.currentTimeMillis() - it.time < 10 * 60 * 1000 }
            .minByOrNull { it.accuracy }
        if (recent != null) { callback(recent); return }
        if (providers.isEmpty()) {
            val any = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
                .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time }
            callback(any); return
        }

        val handler = Handler(Looper.getMainLooper())
        var done = false
        lateinit var listener: LocationListener
        val finish: (Location?) -> Unit = { loc ->
            if (!done) {
                done = true
                runCatching { lm.removeUpdates(listener) }
                handler.removeCallbacksAndMessages(null)
                callback(loc)
            }
        }
        listener = object : LocationListener {
            override fun onLocationChanged(location: Location) = finish(location)
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        }
        for (p in providers) {
            runCatching { lm.requestLocationUpdates(p, 0L, 0f, listener, Looper.getMainLooper()) }
        }
        handler.postDelayed({
            val last = providers.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time }
            finish(last)
        }, 30_000)
    }
}
