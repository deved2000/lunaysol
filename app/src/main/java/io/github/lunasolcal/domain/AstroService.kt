package io.github.lunasolcal.domain

import io.github.lunasolcal.astro.AstroInstant
import io.github.lunasolcal.astro.AstroTime
import io.github.lunasolcal.astro.Body
import io.github.lunasolcal.astro.Events
import io.github.lunasolcal.astro.MoonDay
import io.github.lunasolcal.astro.MoonPhase
import io.github.lunasolcal.astro.Observer
import io.github.lunasolcal.astro.SunDay
import io.github.lunasolcal.data.Place
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/** Bindeglied zwischen der Astronomie-Engine (JD/UT) und der App (java.time, Orte). */
object AstroService {

    fun jd(millis: Long): Double = AstroTime.jdFromMillis(millis)
    fun jd(z: ZonedDateTime): Double = jd(z.toInstant().toEpochMilli())
    fun toZoned(jd: Double, zone: ZoneId): ZonedDateTime = Instant.ofEpochMilli(AstroTime.millisFromJd(jd)).atZone(zone)

    fun dayBounds(date: LocalDate, zone: ZoneId): Pair<Double, Double> =
        jd(date.atStartOfDay(zone)) to jd(date.plusDays(1).atStartOfDay(zone))

    fun observer(place: Place) = Observer(place.latitude, place.longitude)

    fun sunDay(place: Place, date: LocalDate, full: Boolean = true): SunDay {
        val (s, e) = dayBounds(date, place.zone)
        return if (full) Events.sunDay(s, e, observer(place)) else Events.sunDayQuick(s, e, observer(place))
    }

    fun moonDay(place: Place, date: LocalDate): MoonDay {
        val (s, e) = dayBounds(date, place.zone)
        return Events.moonDay(s, e, observer(place))
    }

    /** Mondphase eines Tages: Hauptphase, falls sie an diesem Tag eintritt, sonst Zwischenphase. */
    class PhaseInfo(val name: String, val principal: MoonPhase?, val principalJd: Double?)

    fun phaseNames(p: MoonPhase) = when (p) {
        MoonPhase.NEW -> "Neumond"
        MoonPhase.FIRST_QUARTER -> "Erstes Viertel"
        MoonPhase.FULL -> "Vollmond"
        MoonPhase.LAST_QUARTER -> "Letztes Viertel"
    }

    fun phaseForDay(place: Place, date: LocalDate, refJd: Double): PhaseInfo {
        val (s, e) = dayBounds(date, place.zone)
        // Welche Hauptphase folgt als nächstes ab Tagesbeginn?
        val elong = AstroInstant(s).moonElongation()
        val next = MoonPhase.entries.minBy { ((it.target - elong) % 360 + 360) % 360 }
        val t = Events.nextPhase(s, next)
        if (t < e) return PhaseInfo(phaseNames(next), next, t)
        val el = AstroInstant(refJd).moonElongation()
        val name = when {
            el < 90 -> "Zunehmende Sichel"
            el < 180 -> "Zunehmender Mond"
            el < 270 -> "Abnehmender Mond"
            else -> "Abnehmende Sichel"
        }
        return PhaseInfo(name, null, null)
    }

    /** Kennwerte des Mondes zu einem Zeitpunkt. */
    class MoonStats(val ageDays: Double, val illumination: Double, val elongation: Double, val distanceKm: Double, val declination: Double)

    fun moonStats(jd: Double): MoonStats {
        val a = AstroInstant(jd)
        return MoonStats(Events.moonAgeDays(jd), a.moonIlluminatedFraction(), a.moonElongation(), a.moon.distanceKm, a.moon.declination)
    }

    /** Bezugszeitpunkt für Tageswerte des Mondes: Kulmination, falls am Tag vorhanden, sonst der gewählte Zeitpunkt. */
    fun moonReferenceJd(day: MoonDay, fallbackJd: Double): Double = day.transit?.jd ?: fallbackJd

    fun position(body: Body, place: Place, jd: Double) = AstroInstant(jd).horizontal(body, observer(place))
}
