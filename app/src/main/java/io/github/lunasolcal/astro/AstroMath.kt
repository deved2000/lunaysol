package io.github.lunasolcal.astro

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.tan

/** Kleine Mathe-Helfer (Grad/Radiant) und Zeitfunktionen. */
internal const val DEG = PI / 180.0
internal const val RAD = 180.0 / PI

internal fun norm360(x: Double): Double {
    val r = x - 360.0 * floor(x / 360.0)
    return if (r >= 360.0) r - 360.0 else r
}

/** Normalisiert auf (-180, 180]. */
internal fun norm180(x: Double): Double {
    val r = norm360(x)
    return if (r > 180.0) r - 360.0 else r
}

internal fun sind(x: Double) = sin(x * DEG)
internal fun cosd(x: Double) = cos(x * DEG)
internal fun tand(x: Double) = tan(x * DEG)
internal fun asind(x: Double) = asin(x.coerceIn(-1.0, 1.0)) * RAD
internal fun atan2d(y: Double, x: Double) = atan2(y, x) * RAD

/** Zeitumrechnungen. Alle Julianischen Daten (JD) in der App sind UT, sofern nicht anders angegeben. */
object AstroTime {
    const val J2000 = 2451545.0
    const val MS_PER_DAY = 86_400_000.0

    fun jdFromMillis(epochMillis: Long): Double = epochMillis / MS_PER_DAY + 2440587.5

    fun millisFromJd(jd: Double): Long = ((jd - 2440587.5) * MS_PER_DAY).roundToLong()

    fun decimalYear(jd: Double): Double = 2000.0 + (jd - J2000) / 365.25

    /**
     * ΔT = TT − UT in Sekunden. Für 1900–2025 aus Beobachtungen interpoliert,
     * danach Extrapolation nach Espenak & Meeus (genügend für Kalenderzwecke).
     */
    fun deltaT(jd: Double): Double {
        val y = decimalYear(jd)
        val table = doubleArrayOf(
            1900.0, -2.8, 1920.0, 21.2, 1940.0, 24.3, 1960.0, 33.2, 1980.0, 50.5,
            2000.0, 63.8, 2010.0, 66.1, 2020.0, 69.4, 2025.0, 69.2
        )
        if (y >= 1900.0 && y <= 2025.0) {
            var i = 0
            while (i < table.size - 2 && y > table[i + 2]) i += 2
            val y0 = table[i]; val v0 = table[i + 1]; val y1 = table[i + 2]; val v1 = table[i + 3]
            return v0 + (v1 - v0) * (y - y0) / (y1 - y0)
        }
        fun longTerm(yy: Double): Double { val u = (yy - 1820.0) / 100.0; return -20.0 + 32.0 * u * u }
        return when {
            y < 1900.0 -> longTerm(y)
            y < 2050.0 -> {
                val v2050 = longTerm(2050.0) - 0.5628 * (2150.0 - 2050.0)
                69.2 + (v2050 - 69.2) * (y - 2025.0) / 25.0
            }
            y < 2150.0 -> longTerm(y) - 0.5628 * (2150.0 - y)
            else -> longTerm(y)
        }
    }

    fun jdTT(jdUT: Double): Double = jdUT + deltaT(jdUT) / 86400.0
}
