package io.github.lunasolcal.astro

import kotlin.math.abs

enum class SolarEclipseType { PARTIAL, ANNULAR, TOTAL }
enum class LunarEclipseType { PENUMBRAL, PARTIAL, TOTAL }

/** Eine am Beobachtungsort sichtbare Sonnenfinsternis. jdMax = Zeitpunkt der größten Bedeckung (UT). */
class LocalSolarEclipse(val jdMax: Double, val type: SolarEclipseType, val magnitude: Double)

/** Eine Mondfinsternis (geozentrisch). */
class LunarEclipse(val jdMax: Double, val type: LunarEclipseType, val umbralMagnitude: Double, val penumbralMagnitude: Double)

/**
 * Finsternisberechnung durch direkte Geometrie aus den Sonnen- und Mondpositionen.
 * Sonnenfinsternisse werden lokal (topozentrisch, Sonne über dem Horizont) gesucht.
 */
object Eclipses {

    private const val SYNODIC = 29.530588861
    private const val MINUTE = 1.0 / 1440.0

    /** Mittlere Mondphase nach Meeus (k ganzzahlig = Neumond, k + 0.5 = Vollmond), JDE. */
    private fun meanPhase(k: Double): Double {
        val t = k / 1236.85
        return 2451550.09766 + SYNODIC * k + 0.00015437 * t * t - 0.000000150 * t * t * t
    }

    private fun kFor(jd: Double): Double = Math.floor((jd - 2451550.09766) / SYNODIC)

    private fun refine(jd: Double, target: Double): Double {
        var t = jd
        repeat(6) {
            val e = norm180(AstroInstant(t).moonElongation() - target)
            t -= e / 12.19
        }
        return t
    }

    // ---------------------------------------------------------------- Sonnenfinsternisse (lokal)

    private class SolarSample(val sep: Double, val rs: Double, val rm: Double, val sunAlt: Double) {
        val visible get() = sunAlt > Events.SUN_STANDARD_ALTITUDE
        val overlap get() = rs + rm - sep
    }

    private fun solarSample(jd: Double, obs: Observer): SolarSample {
        val a = AstroInstant(jd)
        val s = a.horizontal(Body.SUN, obs)
        val m = a.horizontal(Body.MOON, obs)
        val sep = angularSeparation(s.rightAscension, s.declination, m.rightAscension, m.declination)
        return SolarSample(sep, asind(SUN_RADIUS_KM / s.distanceKm), asind(MOON_RADIUS_KM / m.distanceKm), s.altitude)
    }

    /** Prüft eine Neumondzeit und liefert ggf. die am Ort sichtbare Finsternis. */
    private fun localSolarAt(newMoon: Double, obs: Observer): LocalSolarEclipse? {
        val start = newMoon - 5.0 / 24
        val step = 5 * MINUTE
        var best: Double? = null
        var bestSample: SolarSample? = null
        var t = start
        while (t <= newMoon + 5.0 / 24) {
            val s = solarSample(t, obs)
            if (s.visible && s.overlap > 0 && (bestSample == null || s.sep < bestSample.sep)) {
                best = t; bestSample = s
            }
            t += step
        }
        if (best == null || bestSample == null) return null
        // Verfeinern (Goldener Schnitt auf dem Abstand)
        var a = best - step; var b = best + step
        val gr = 0.6180339887
        while (b - a > 0.2 * MINUTE) {
            val c = b - gr * (b - a); val d = a + gr * (b - a)
            if (solarSample(c, obs).sep < solarSample(d, obs).sep) b = d else a = c
        }
        var tMax = (a + b) / 2
        var s = solarSample(tMax, obs)
        if (!s.visible || s.overlap <= 0) { tMax = best; s = bestSample }
        val type = when {
            s.sep <= s.rm - s.rs -> SolarEclipseType.TOTAL
            s.sep <= s.rs - s.rm -> SolarEclipseType.ANNULAR
            else -> SolarEclipseType.PARTIAL
        }
        return LocalSolarEclipse(tMax, type, (s.rs + s.rm - s.sep) / (2 * s.rs))
    }

    /**
     * Sucht ab jdFrom die jeweils nächste partielle, ringförmige und totale Sonnenfinsternis,
     * die am Ort sichtbar ist. Suche bis maxYears Jahre in die Zukunft.
     */
    fun nextLocalSolarEclipses(
        jdFrom: Double,
        obs: Observer,
        maxYears: Int = 1000,
        isCancelled: () -> Boolean = { false }
    ): Map<SolarEclipseType, LocalSolarEclipse?> {
        val found = HashMap<SolarEclipseType, LocalSolarEclipse?>()
        var k = kFor(jdFrom) - 1
        val limit = jdFrom + maxYears * 365.25
        while (found.size < 3) {
            if (isCancelled()) break
            val mean = meanPhase(k) - AstroTime.deltaT(meanPhase(k)) / 86400.0
            k += 1.0
            if (mean > limit) break
            if (mean < jdFrom - 1) continue
            // Schneller Vorfilter mit der mittleren Neumondzeit
            if (abs(AstroInstant(mean).moon.eclipticLatitude) > 2.2) continue
            val nm = refine(mean, 0.0)
            if (abs(AstroInstant(nm).moon.eclipticLatitude) > 1.6) continue
            val ecl = localSolarAt(nm, obs) ?: continue
            if (ecl.jdMax < jdFrom) continue
            if (!found.containsKey(ecl.type)) found[ecl.type] = ecl
        }
        for (t in SolarEclipseType.entries) if (!found.containsKey(t)) found[t] = null
        return found
    }

    // ---------------------------------------------------------------- Mondfinsternisse

    private fun lunarGeometry(jd: Double): DoubleArray {
        val a = AstroInstant(jd)
        val sun = a.sun; val moon = a.moon
        // Abstand Mond ↔ Schattenachse (Gegenpunkt der Sonne) in ekliptikalen Koordinaten
        val sep = angularSeparation(sun.eclipticLongitude + 180.0, -sun.eclipticLatitude, moon.eclipticLongitude, moon.eclipticLatitude)
        val piMoon = asind(EARTH_RADIUS_KM / moon.distanceKm)
        val rSun = asind(SUN_RADIUS_KM / sun.distanceKm)
        val piSun = asind(EARTH_RADIUS_KM / sun.distanceKm)
        val umbra = 1.02 * (0.99834 * piMoon - rSun + piSun)
        val penumbra = 1.02 * (0.99834 * piMoon + rSun + piSun)
        val sm = asind(MOON_RADIUS_KM / moon.distanceKm)
        return doubleArrayOf(sep, umbra, penumbra, sm)
    }

    private fun lunarAt(fullMoon: Double): LunarEclipse? {
        var a = fullMoon - 0.2; var b = fullMoon + 0.2
        val gr = 0.6180339887
        while (b - a > 0.2 * MINUTE) {
            val c = b - gr * (b - a); val d = a + gr * (b - a)
            if (lunarGeometry(c)[0] < lunarGeometry(d)[0]) b = d else a = c
        }
        val t = (a + b) / 2
        val g = lunarGeometry(t)
        val umbralMag = (g[1] + g[3] - g[0]) / (2 * g[3])
        val penMag = (g[2] + g[3] - g[0]) / (2 * g[3])
        if (penMag <= 0) return null
        val type = when {
            umbralMag >= 1.0 -> LunarEclipseType.TOTAL
            umbralMag > 0.0 -> LunarEclipseType.PARTIAL
            else -> LunarEclipseType.PENUMBRAL
        }
        return LunarEclipse(t, type, umbralMag, penMag)
    }

    fun nextLunarEclipses(jdFrom: Double, maxYears: Int = 200, isCancelled: () -> Boolean = { false }): Map<LunarEclipseType, LunarEclipse?> {
        val found = HashMap<LunarEclipseType, LunarEclipse?>()
        var k = kFor(jdFrom) - 1 + 0.5
        val limit = jdFrom + maxYears * 365.25
        while (found.size < 3) {
            if (isCancelled()) break
            val mean = meanPhase(k) - AstroTime.deltaT(meanPhase(k)) / 86400.0
            k += 1.0
            if (mean > limit) break
            if (mean < jdFrom - 1) continue
            if (abs(AstroInstant(mean).moon.eclipticLatitude) > 2.2) continue
            val fm = refine(mean, 180.0)
            if (abs(AstroInstant(fm).moon.eclipticLatitude) > 1.7) continue
            val ecl = lunarAt(fm) ?: continue
            if (ecl.jdMax < jdFrom) continue
            if (!found.containsKey(ecl.type)) found[ecl.type] = ecl
        }
        for (t in LunarEclipseType.entries) if (!found.containsKey(t)) found[t] = null
        return found
    }
}
