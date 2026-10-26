package io.github.lunasolcal.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Speichert Orte und Einstellungen in den SharedPreferences. */
class PlaceRepository(context: Context) {

    private val prefs = context.getSharedPreferences("lunasolcal", Context.MODE_PRIVATE)

    fun loadPlaces(): List<Place> {
        val json = prefs.getString(KEY_PLACES, null) ?: return Place.DEFAULTS
        return try {
            fromJson(json).ifEmpty { Place.DEFAULTS }
        } catch (e: Exception) {
            Place.DEFAULTS
        }
    }

    fun savePlaces(places: List<Place>) {
        prefs.edit().putString(KEY_PLACES, toJson(places)).apply()
    }

    var selectedIndex: Int
        get() = prefs.getInt(KEY_SELECTED, 0)
        set(value) = prefs.edit().putInt(KEY_SELECTED, value).apply()

    var decimalCoordinates: Boolean
        get() = prefs.getBoolean(KEY_DECIMAL, false)
        set(value) = prefs.edit().putBoolean(KEY_DECIMAL, value).apply()

    var onlineLookups: Boolean
        get() = prefs.getBoolean(KEY_ONLINE, true)
        set(value) = prefs.edit().putBoolean(KEY_ONLINE, value).apply()

    companion object {
        private const val KEY_PLACES = "places"
        private const val KEY_SELECTED = "selected"
        private const val KEY_DECIMAL = "decimal_coordinates"
        private const val KEY_ONLINE = "online_lookups"

        fun toJson(places: List<Place>): String {
            val arr = JSONArray()
            for (p in places) {
                arr.put(JSONObject().apply {
                    put("name", p.name)
                    put("subtitle", p.subtitle)
                    put("latitude", p.latitude)
                    put("longitude", p.longitude)
                    put("timezone", p.zoneId)
                    put("gps", p.isGps)
                })
            }
            return arr.toString(2)
        }

        fun fromJson(json: String): List<Place> {
            val arr = JSONArray(json)
            val result = ArrayList<Place>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val lat = o.getDouble("latitude")
                val lon = o.getDouble("longitude")
                if (lat !in -90.0..90.0 || lon !in -180.0..180.0) continue
                result.add(
                    Place(
                        name = o.optString("name", ""),
                        subtitle = o.optString("subtitle", ""),
                        latitude = lat,
                        longitude = lon,
                        zoneId = o.optString("timezone", java.util.TimeZone.getDefault().id),
                        isGps = o.optBoolean("gps", false)
                    )
                )
            }
            return result
        }
    }
}
