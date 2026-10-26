package io.github.lunasolcal.astro

import kotlin.math.abs
import kotlin.math.sqrt

/** Ein Ereignis (Aufgang, Untergang, Kulmination …) mit Zeitpunkt (JD UT) und Horizontposition. */
class EventPoint(val jd: Double, val azimuth: Double, val altitude: Double)

class Crossing(val jd: Double, val rising: Boolean)

/** Sonnendaten für einen lokalen Kalendertag. */
class SunDay(
    val rise: EventPoint?,
    val set: EventPoint?,
    val noon: EventPoint?,
    /** Zeit über dem Horizont innerhalb des Tages in Sekunden. */
    val dayLengthSeconds: Double,
    val civilMorning: Pair<Double?, Double?>,
    val nauticalMorning: Pair<Double?, Double?>,
    val astronomicalMorning: Pair<Double?, Double?>,
    val civilEvening: Pair<Double?, Double?>,
    val nauticalEvening: Pair<Double?, Double?>,
    val astronomicalEvening: Pair<Double?, Double?>,
    /** Untere Kulmination nach dem Mittag („echte Mitternacht“). */
    val midnight: EventPoint?
)

/** Monddaten für einen lokalen Kalendertag. */
class MoonDay(
    val rise: EventPoint?,
    val set: EventPoint?,
    val transit: EventPoint?,
    val lowerTransit: EventPoint?
)

enum class MoonPhase(val target: Double) { NEW(0.0), FIRST_QUARTER(90.0), FULL(180.0), LAST_QUARTER(270.0) }

/**
 * Ereignissuche: Auf-/Untergänge, Kulminationen, Dämmerung, Mondphasen, Apsiden, Jahreszeiten.
 * Alle Zeiten als Julianisches Datum (UT).
 */
object Events {

    const val SUN_STANDARD_ALTITUDE = -0.8333
    private const val MINUTE = 1.0 / 1440.0

    fun horizontal(body: Body, jd: Double, obs: Observer): Horizontal = AstroInstant(jd).horizontal(body, obs)

    /** Höhe über dem für Auf-/Untergang maßgeblichen Horizont (oberer Rand, Standardrefraktion). */
    private fun riseFunction(body: Body, obs: Observer): (Double) -> Double = { jd ->
        val h = horizontal(body, jd, obs)
        if (body == Body.SUN) h.altitude - SUN_STANDARD_ALTITUDE
        else h.altitude + 0.5667 + asind(MOON_RADIUS_KM / h.distanceKm)
    }

    private fun sunAltitudeFunction(obs: Observer, target: Double): (Double) -> Double = { jd ->
        horizontal(Body.SUN, jd, obs).altitude - target
    }

    /** Sucht alle Vorzeichenwechsel von [g] in [start, end). */
    fun findCrossings(start: Double, end: Double, stepDays: Double, g: (Double) -> Double, maxJump: Double = Double.MAX_VALUE): List<Crossing> {
        val result = ArrayList<Crossing>()
        var t0 = start
        var v0 = g(t0)
        while (t0 < end) {
            val t1 = minOf(t0 + stepDays, end)
            val v1 = g(t1)
            if ((v0 < 0.0) != (v1 < 0.0) && abs(v1 - v0) < maxJump) {
                var a = t0; var b = t1; var va = v0
                repeat(22) {
                    val m = (a + b) / 2
                    val vm = g(m)
                    if ((va < 0.0) == (vm < 0.0)) { a = m; va = vm } else b = m
                }
                val jd = (a + b) / 2
                if (jd >= start && jd < end) result.add(Crossing(jd, v0 < 0.0))
            }
            t0 = t1; v0 = v1
            if (t1 >= end) break
        }
        return result
    }

    private fun point(body: Body, jd: Double, obs: Observer): EventPoint {
        val h = horizontal(body, jd, obs)
        return EventPoint(jd, h.azimuth, h.altitude)
    }

    /** Obere (lower=false) bzw. untere Kulmination(en) im Intervall. */
    fun transits(body: Body, start: Double, end: Double, obs: Observer, lower: Boolean = false): List<EventPoint> {
        val g: (Double) -> Double = { jd ->
            val h = horizontal(body, jd, obs).hourAngle
            if (lower) norm180(h + 180.0) else h
        }
        return findCrossings(start, end, 20 * MINUTE, g, maxJump = 90.0)
            .filter { it.rising }
            .map { point(body, it.jd, obs) }
    }

    /** Zeitpunkte, zu denen der Körper ein bestimmtes Azimut erreicht (z. B. 90° = Ost). */
    fun azimuthCrossings(body: Body, start: Double, end: Double, obs: Observer, azimuth: Double): List<Double> {
        val g: (Double) -> Double = { jd -> norm180(horizontal(body, jd, obs).azimuth - azimuth) }
        return findCrossings(start, end, 10 * MINUTE, g, maxJump = 90.0).map { it.jd }
    }

    fun riseSet(body: Body, start: Double, end: Double, obs: Observer): List<Crossing> =
        findCrossings(start, end, 10 * MINUTE, riseFunction(body, obs))

    fun sunDay(dayStart: Double, dayEnd: Double, obs: Observer): SunDay {
        val f = riseFunction(Body.SUN, obs)
        val crossings = findCrossings(dayStart, dayEnd, 10 * MINUTE, f)
        val rise = crossings.firstOrNull { it.rising }?.let { point(Body.SUN, it.jd, obs) }
        val set = crossings.firstOrNull { !it.rising }?.let { point(Body.SUN, it.jd, obs) }

        // Tageslänge = Zeit über dem Horizont im Tagesintervall
        var above = 0.0
        var segStart: Double? = if (f(dayStart) > 0) dayStart else null
        for (c in crossings) {
            if (c.rising) segStart = c.jd
            else { if (segStart != null) above += c.jd - segStart; segStart = null }
        }
        if (segStart != null) above += dayEnd - segStart

        val noon = transits(Body.SUN, dayStart, dayEnd, obs).firstOrNull()
        val ref = noon?.jd ?: ((dayStart + dayEnd) / 2)

        fun morning(alt: Double): Double? =
            findCrossings(ref - 0.5, ref, 10 * MINUTE, sunAltitudeFunction(obs, alt)).lastOrNull { it.rising }?.jd
        fun evening(alt: Double): Double? =
            findCrossings(ref, ref + 0.5, 10 * MINUTE, sunAltitudeFunction(obs, alt)).firstOrNull { !it.rising }?.jd

        val m18 = morning(-18.0); val m12 = morning(-12.0); val m6 = morning(-6.0)
        val e18 = evening(-18.0); val e12 = evening(-12.0); val e6 = evening(-6.0)
        val mRise = findCrossings(ref - 0.5, ref, 10 * MINUTE, f).lastOrNull { it.rising }?.jd
        val eSet = findCrossings(ref, ref + 0.5, 10 * MINUTE, f).firstOrNull { !it.rising }?.jd

        val midnight = transits(Body.SUN, ref, ref + 1.1, obs, lower = true).firstOrNull()

        return SunDay(
            rise, set, noon, above * 86400.0,
            civilMorning = m6 to mRise,
            nauticalMorning = m12 to m6,
            astronomicalMorning = m18 to m12,
            civilEvening = eSet to e6,
            nauticalEvening = e6 to e12,
            astronomicalEvening = e12 to e18,
            midnight = midnight
        )
    }

    /** Nur Aufgang, Untergang, Mittag und Tageslänge – schneller für Monatstabellen. */
    fun sunDayQuick(dayStart: Double, dayEnd: Double, obs: Observer): SunDay {
        val f = riseFunction(Body.SUN, obs)
        val crossings = findCrossings(dayStart, dayEnd, 15 * MINUTE, f)
        val rise = crossings.firstOrNull { it.rising }?.let { point(Body.SUN, it.jd, obs) }
        val set = crossings.firstOrNull { !it.rising }?.let { point(Body.SUN, it.jd, obs) }
        var above = 0.0
        var segStart: Double? = if (f(dayStart) > 0) dayStart else null
        for (c in crossings) {
            if (c.rising) segStart = c.jd
            else { if (segStart != null) above += c.jd - segStart; segStart = null }
        }
        if (segStart != null) above += dayEnd - segStart
        val noon = transits(Body.SUN, dayStart, dayEnd, obs).firstOrNull()
        val none = null to null
        return SunDay(rise, set, noon, above * 86400.0, none, none, none, none, none, none, null)
    }

    fun moonDay(dayStart: Double, dayEnd: Double, obs: Observer): MoonDay {
        val crossings = riseSet(Body.MOON, dayStart, dayEnd, obs)
        val rise = crossings.firstOrNull { it.rising }?.let { point(Body.MOON, it.jd, obs) }
        val set = crossings.firstOrNull { !it.rising }?.let { point(Body.MOON, it.jd, obs) }
        val transit = transits(Body.MOON, dayStart, dayEnd, obs).firstOrNull()
        val lower = transits(Body.MOON, dayStart, dayEnd, obs, lower = true).firstOrNull()
        return MoonDay(rise, set, transit, lower)
    }

    // ---------------------------------------------------------------- Mondphasen

    private fun elongation(jd: Double) = AstroInstant(jd).moonElongation()

    private fun refinePhase(guess: Double, target: Double): Double {
        var t = guess
        repeat(10) {
            val e = norm180(elongation(t) - target)
            if (abs(e) < 1e-6) return t
            val rate = norm180(elongation(t + 0.02) - elongation(t)) / 0.02
            t -= e / (if (rate > 5.0) rate else 12.19)
        }
        return t
    }

    /** Erster Zeitpunkt ≥ jd, an dem die Phase eintritt. */
    fun nextPhase(jd: Double, phase: MoonPhase): Double {
        val delta = norm360(phase.target - elongation(jd))
        var t = refinePhase(jd + delta / 12.19, phase.target)
        if (t < jd) t = refinePhase(t + 29.53, phase.target)
        return t
    }

    /** Letzter Zeitpunkt ≤ jd, an dem die Phase eintrat. */
    fun previousPhase(jd: Double, phase: MoonPhase): Double {
        val delta = norm360(elongation(jd) - phase.target)
        var t = refinePhase(jd - delta / 12.19, phase.target)
        if (t > jd) t = refinePhase(t - 29.53, phase.target)
        return t
    }

    /** Mondalter in Tagen (aus der Elongation, bezogen auf einen mittleren synodischen Monat). */
    fun moonAgeDays(jd: Double): Double = elongation(jd) / 360.0 * 29.530588861

    // ---------------------------------------------------------------- Extremwerte

    private fun goldenExtremum(a0: Double, b0: Double, max: Boolean, f: (Double) -> Double, tol: Double = MINUTE / 2): Double {
        val gr = (sqrt(5.0) - 1) / 2
        var a = a0; var b = b0
        var c = b - gr * (b - a); var d = a + gr * (b - a)
        var fc = f(c); var fd = f(d)
        while (b - a > tol) {
            val better = if (max) fc > fd else fc < fd
            if (better) { b = d; d = c; fd = fc; c = b - gr * (b - a); fc = f(c) }
            else { a = c; c = d; fc = fd; d = a + gr * (b - a); fd = f(d) }
        }
        return (a + b) / 2
    }

    /** Nächstes lokales Extremum einer Funktion ab jd. */
    fun nextExtremum(jd: Double, step: Double, span: Double, max: Boolean, f: (Double) -> Double): Double? {
        var t0 = jd; var t1 = jd + step; var t2 = jd + 2 * step
        var v0 = f(t0); var v1 = f(t1)
        while (t2 <= jd + span) {
            val v2 = f(t2)
            val isExt = if (max) v1 >= v0 && v1 > v2 else v1 <= v0 && v1 < v2
            if (isExt) return goldenExtremum(t0, t2, max, f)
            t0 = t1; t1 = t2; t2 += step; v0 = v1; v1 = v2
        }
        return null
    }

    fun nextPerigee(jd: Double): Double? = nextExtremum(jd, 0.25, 35.0, false) { AstroInstant(it).moon.distanceKm }
    fun nextApogee(jd: Double): Double? = nextExtremum(jd, 0.25, 35.0, true) { AstroInstant(it).moon.distanceKm }
    fun nextPerihelion(jd: Double): Double? = nextExtremum(jd, 1.0, 400.0, false) { AstroInstant(it).sun.distanceKm }
    fun nextAphelion(jd: Double): Double? = nextExtremum(jd, 1.0, 400.0, true) { AstroInstant(it).sun.distanceKm }

    // ---------------------------------------------------------------- Jahreszeiten

    /**
     * Äquinoktien und Solstitien eines Jahres: [Frühling, Sommer, Herbst, Winter] (JD UT).
     */
    fun seasons(year: Int): DoubleArray {
        val guesses = intArrayOf(3, 20, 6, 21, 9, 23, 12, 21)
        return DoubleArray(4) { i ->
            val target = 90.0 * i
            var t = julianDay(year, guesses[2 * i], guesses[2 * i + 1]) + 0.5
            repeat(8) {
                val e = norm180(AstroInstant(t).sun.eclipticLongitude - target)
                t -= e / 0.9856
            }
            t
        }
    }

    /** JD (0h UT) eines gregorianischen Datums. */
    fun julianDay(year: Int, month: Int, day: Int): Double {
        var y = year; var m = month
        if (m <= 2) { y -= 1; m += 12 }
        val a = Math.floorDiv(y, 100)
        val b = 2 - a + Math.floorDiv(a, 4)
        return Math.floor(365.25 * (y + 4716)) + Math.floor(30.6001 * (m + 1)) + day + b - 1524.5
    }
}
