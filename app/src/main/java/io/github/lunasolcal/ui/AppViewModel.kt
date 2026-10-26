package io.github.lunasolcal.ui

import android.app.Application
import android.location.Location
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.lunasolcal.data.GeoService
import io.github.lunasolcal.data.Place
import io.github.lunasolcal.data.PlaceRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/** Zentraler Zustand der App: Orte, gewählter Ort, gewählter Zeitpunkt. */
class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = PlaceRepository(app)

    val places = mutableStateListOf<Place>().apply { addAll(repo.loadPlaces()) }
    var selectedIndex by mutableIntStateOf(repo.selectedIndex.coerceIn(0, places.size - 1))
        private set

    /** Gewählter Zeitpunkt (UTC-Millisekunden). */
    var epochMillis by mutableLongStateOf(System.currentTimeMillis())
        private set

    /** true = Zeit läuft mit der aktuellen Uhrzeit mit. */
    var followNow by mutableStateOf(true)
        private set

    var decimalCoordinates by mutableStateOf(repo.decimalCoordinates)
        private set
    var onlineLookups by mutableStateOf(repo.onlineLookups)
        private set

    var gpsBusy by mutableStateOf(false)
        private set

    /** Kurze Meldung für die UI (wird als Toast angezeigt und dann gelöscht). */
    var message by mutableStateOf<String?>(null)

    val place: Place get() = places.getOrNull(selectedIndex) ?: places.firstOrNull() ?: Place.DEFAULTS.first()
    val zone: ZoneId get() = place.zone
    val dateTime: ZonedDateTime get() = Instant.ofEpochMilli(epochMillis).atZone(zone)
    val date: LocalDate get() = dateTime.toLocalDate()

    init {
        viewModelScope.launch {
            while (true) {
                delay(15_000)
                if (followNow) epochMillis = System.currentTimeMillis()
            }
        }
    }

    // ------------------------------------------------------------ Zeit

    fun setDateTime(z: ZonedDateTime) {
        epochMillis = z.toInstant().toEpochMilli()
        followNow = false
    }

    fun resetToNow() {
        epochMillis = System.currentTimeMillis()
        followNow = true
    }

    fun shiftDays(days: Long) = setDateTime(dateTime.plusDays(days))
    fun shiftMinutes(minutes: Long) = setDateTime(dateTime.plusMinutes(minutes))
    fun shiftYears(years: Long) = setDateTime(dateTime.plusYears(years))

    fun setDayOfYear(day: Int) {
        val dt = dateTime
        setDateTime(dt.withDayOfYear(day.coerceIn(1, dt.toLocalDate().lengthOfYear())))
    }

    fun setMinuteOfDay(minute: Int) {
        val m = minute.coerceIn(0, 1439)
        setDateTime(dateTime.withHour(m / 60).withMinute(m % 60).withSecond(0))
    }

    fun setYear(year: Int) = setDateTime(dateTime.withYear(year.coerceIn(1, 9999)))

    fun setDate(date: LocalDate) {
        val dt = dateTime
        setDateTime(date.atTime(dt.toLocalTime()).atZone(zone))
    }

    // ------------------------------------------------------------ Orte

    private fun persist() {
        repo.savePlaces(places.toList())
        repo.selectedIndex = selectedIndex
    }

    fun selectPlace(index: Int) {
        if (index in places.indices) { selectedIndex = index; persist() }
    }

    fun addPlace(p: Place) {
        places.add(p)
        selectedIndex = places.size - 1
        persist()
    }

    fun deletePlace(index: Int) {
        if (places.size <= 1 || index !in places.indices) { message = "Der letzte Ort kann nicht gelöscht werden"; return }
        places.removeAt(index)
        if (selectedIndex >= index && selectedIndex > 0) selectedIndex--
        persist()
    }

    fun renamePlace(index: Int, name: String) {
        if (index !in places.indices) return
        places[index] = places[index].copy(name = name.trim())
        persist()
    }

    fun movePlace(index: Int, delta: Int) {
        val target = index + delta
        if (index !in places.indices || target !in places.indices) return
        val p = places.removeAt(index)
        places.add(target, p)
        selectedIndex = when (selectedIndex) {
            index -> target
            target -> index
            else -> selectedIndex
        }
        persist()
    }

    fun setZoneForSelected(zoneId: String) {
        places[selectedIndex] = place.copy(zoneId = zoneId)
        persist()
    }

    /** Zeitzone des gewählten Ortes online neu ermitteln. */
    fun detectZoneForSelected() {
        val p = place
        viewModelScope.launch {
            val tz = GeoService.timezoneFor(p.latitude, p.longitude)
            if (tz != null) {
                setZoneForSelected(tz); message = "Zeitzone: $tz"
            } else message = "Zeitzone konnte nicht ermittelt werden (offline?)"
        }
    }

    // ------------------------------------------------------------ Import / Export

    fun exportJson(): String = PlaceRepository.toJson(places.toList())

    fun importJson(json: String) {
        try {
            val imported = PlaceRepository.fromJson(json)
            var added = 0
            for (p in imported) {
                if (places.none { it.name == p.name && it.latitude == p.latitude && it.longitude == p.longitude }) {
                    places.add(p); added++
                }
            }
            persist()
            message = "$added Orte importiert"
        } catch (e: Exception) {
            message = "Import fehlgeschlagen: ${e.message}"
        }
    }

    // ------------------------------------------------------------ Einstellungen

    fun updateDecimalCoordinates(value: Boolean) { decimalCoordinates = value; repo.decimalCoordinates = value }
    fun updateOnlineLookups(value: Boolean) { onlineLookups = value; repo.onlineLookups = value }

    // ------------------------------------------------------------ GPS

    fun gpsStarted() { gpsBusy = true; message = "Position wird ermittelt …" }

    fun onGpsLocation(location: Location?) {
        if (location == null) {
            gpsBusy = false
            message = "Keine Position verfügbar"
            return
        }
        val lat = location.latitude; val lon = location.longitude
        viewModelScope.launch {
            val name = if (onlineLookups) GeoService.reverseName(getApplication<Application>(), lat, lon) else null
            val tz = (if (onlineLookups) GeoService.timezoneFor(lat, lon) else null) ?: GeoService.fallbackZone(lon).id
            val p = Place(name?.first ?: "GPS-Position", name?.second ?: "Aktuelle Position", lat, lon, tz, isGps = true)
            val existing = places.indexOfFirst { it.isGps }
            if (existing >= 0) {
                places[existing] = p
                selectedIndex = existing
                persist()
            } else addPlace(p)
            gpsBusy = false
            message = "Position aktualisiert"
        }
    }
}
