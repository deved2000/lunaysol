package io.github.lunasolcal.astro

import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Beobachterstandort. Länge positiv nach Osten. */
data class Observer(val latitude: Double, val longitude: Double, val heightMeters: Double = 0.0)

enum class Body { SUN, MOON }

/** Scheinbare geozentrische Position eines Körpers. Winkel in Grad, Abstand in km. */
class GeoPosition(
    val rightAscension: Double,
    val declination: Double,
    val distanceKm: Double,
    val eclipticLongitude: Double,
    val eclipticLatitude: Double
)

/** Topozentrische Horizontkoordinaten (ohne Refraktion). Azimut von Nord über Ost. */
class Horizontal(
    val altitude: Double,
    val azimuth: Double,
    val rightAscension: Double,
    val declination: Double,
    val hourAngle: Double,
    val distanceKm: Double
)

const val AU_KM = 149_597_870.7
const val EARTH_RADIUS_KM = 6378.14
const val MOON_RADIUS_KM = 1737.4
const val SUN_RADIUS_KM = 696_000.0

/**
 * Alle Größen für einen Zeitpunkt (JD in UT). Wird bei Bedarf berechnet und zwischengespeichert.
 */
class AstroInstant(val jdUT: Double) {
    val jdTT: Double = AstroTime.jdTT(jdUT)
    private val t: Double = (jdTT - AstroTime.J2000) / 36525.0

    // Nutation (verkürzt, Genauigkeit ~0,5")
    private val omega = norm360(125.04452 - 1934.136261 * t)
    private val lSun = norm360(280.4665 + 36000.7698 * t)
    private val lMoon = norm360(218.3165 + 481267.8813 * t)
    val nutLon: Double = (-17.20 * sind(omega) - 1.32 * sind(2 * lSun) - 0.23 * sind(2 * lMoon) + 0.21 * sind(2 * omega)) / 3600.0
    private val nutObl: Double = (9.20 * cosd(omega) + 0.57 * cosd(2 * lSun) + 0.10 * cosd(2 * lMoon) - 0.09 * cosd(2 * omega)) / 3600.0
    val obliquity: Double = 23.0 + 26.0 / 60.0 + 21.448 / 3600.0 +
        (-46.8150 * t - 0.00059 * t * t + 0.001813 * t * t * t) / 3600.0 + nutObl

    /** Scheinbare Greenwich-Sternzeit in Grad. */
    val gast: Double by lazy {
        val d = jdUT - AstroTime.J2000
        val tu = d / 36525.0
        norm360(280.46061837 + 360.98564736629 * d + 0.000387933 * tu * tu - tu * tu * tu / 38710000.0 + nutLon * cosd(obliquity))
    }

    val sun: GeoPosition by lazy { computeSun() }
    val moon: GeoPosition by lazy { computeMoon() }

    /** Mittlere Länge der Sonne (für die Zeitgleichung). */
    val sunMeanLongitude: Double
        get() {
            val tau = t / 10.0
            return norm360(280.4664567 + 360007.6982779 * tau + 0.03032028 * tau * tau +
                tau * tau * tau / 49931.0 - tau * tau * tau * tau / 15300.0)
        }

    fun position(body: Body): GeoPosition = if (body == Body.SUN) sun else moon

    private fun computeSun(): GeoPosition {
        val lbr = Vsop87Earth.lbr(t / 10.0)
        val rAu = lbr[2]
        var lon = norm360(lbr[0] * RAD + 180.0)
        var lat = -lbr[1] * RAD
        // Umrechnung auf FK5
        val lp = lon - 1.397 * t - 0.00031 * t * t
        lon += -0.09033 / 3600.0
        lat += 0.03916 / 3600.0 * (cosd(lp) - sind(lp))
        // Nutation und Aberration
        lon = norm360(lon + nutLon - 20.4898 / 3600.0 / rAu)
        return toEquatorial(lon, lat, rAu * AU_KM)
    }

    private fun computeMoon(): GeoPosition {
        val p = MoonTheory.position(t)
        return toEquatorial(norm360(p[0] + nutLon), p[1], p[2])
    }

    private fun toEquatorial(lon: Double, lat: Double, dist: Double): GeoPosition {
        val e = obliquity
        val ra = norm360(atan2d(sind(lon) * cosd(e) - tand(lat) * sind(e), cosd(lon)))
        val dec = asind(sind(lat) * cosd(e) + cosd(lat) * sind(e) * sind(lon))
        return GeoPosition(ra, dec, dist, lon, lat)
    }

    /** Topozentrische Horizontkoordinaten (Parallaxe berücksichtigt, keine Refraktion). */
    fun horizontal(body: Body, obs: Observer): Horizontal {
        val p = position(body)
        val phi = obs.latitude
        val u = atan(0.99664719 * tand(phi))
        val rhoSin = 0.99664719 * sin(u) + obs.heightMeters / 6378140.0 * sind(phi)
        val rhoCos = cos(u) + obs.heightMeters / 6378140.0 * cosd(phi)
        val sinPi = EARTH_RADIUS_KM / p.distanceKm
        val h = norm360(gast + obs.longitude - p.rightAscension)
        val denom = cosd(p.declination) - rhoCos * sinPi * cosd(h)
        val dAlpha = atan2d(-rhoCos * sinPi * sind(h), denom)
        val decT = atan2d((sind(p.declination) - rhoSin * sinPi) * cosd(dAlpha), denom)
        val hT = h - dAlpha
        val alt = asind(sind(phi) * sind(decT) + cosd(phi) * cosd(decT) * cosd(hT))
        val az = norm360(atan2d(sind(hT), cosd(hT) * sind(phi) - tand(decT) * cosd(phi)) + 180.0)
        // topozentrischer Abstand (Näherung, für Halbmesser ausreichend)
        val topoDist = p.distanceKm - EARTH_RADIUS_KM * sind(alt)
        return Horizontal(alt, az, norm360(p.rightAscension + dAlpha), decT, norm180(hT), topoDist)
    }

    /** Beleuchteter Anteil des Mondes (0..1). */
    fun moonIlluminatedFraction(): Double {
        val psi = acosd(cosd(moon.eclipticLatitude) * cosd(moon.eclipticLongitude - sun.eclipticLongitude))
        val i = atan2d(sun.distanceKm * sind(psi), moon.distanceKm - sun.distanceKm * cosd(psi))
        return (1.0 + cosd(i)) / 2.0
    }

    /** Elongation Mond − Sonne in ekliptikaler Länge, 0..360 (0 = Neumond, 180 = Vollmond). */
    fun moonElongation(): Double = norm360(moon.eclipticLongitude - sun.eclipticLongitude)

    /** Zeitgleichung in Minuten (wahre minus mittlere Sonnenzeit). */
    fun equationOfTime(): Double {
        val e = sunMeanLongitude - 0.0057183 - sun.rightAscension + nutLon * cosd(obliquity)
        return norm180(e) * 4.0
    }
}

internal fun acosd(x: Double) = kotlin.math.acos(x.coerceIn(-1.0, 1.0)) * RAD

/** Winkelabstand zweier Punkte (Grad). */
internal fun angularSeparation(ra1: Double, dec1: Double, ra2: Double, dec2: Double): Double {
    // Haversine – numerisch stabil für kleine Winkel
    val dRa = (ra2 - ra1) * DEG
    val dDec = (dec2 - dec1) * DEG
    val a = sin(dDec / 2).let { it * it } + cosd(dec1) * cosd(dec2) * sin(dRa / 2).let { it * it }
    return 2.0 * kotlin.math.asin(sqrt(a.coerceIn(0.0, 1.0))) * RAD
}
