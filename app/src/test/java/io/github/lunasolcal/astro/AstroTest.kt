package io.github.lunasolcal.astro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Prüft die Engine gegen Werte aus J. Meeus und gegen Referenzwerte für Wien (7. Juni 2026).
 */
class AstroTest {

    private val vienna = Observer(48.228719, 16.371786)
    private val zone = ZoneId.of("Europe/Vienna")

    private fun jd(z: ZonedDateTime) = AstroTime.jdFromMillis(z.toInstant().toEpochMilli())
    private fun minutesOfDay(jd: Double): Double {
        val z = java.time.Instant.ofEpochMilli(AstroTime.millisFromJd(jd)).atZone(zone)
        return z.hour * 60 + z.minute + z.second / 60.0
    }

    @Test
    fun moonPositionMeeusExample47a() {
        // 1992-04-12 0h TD
        val a = AstroInstant(2448724.5 - AstroTime.deltaT(2448724.5) / 86400.0)
        assertEquals(133.167, a.moon.eclipticLongitude, 0.002)
        assertEquals(-3.229, a.moon.eclipticLatitude, 0.001)
        assertEquals(368409.7, a.moon.distanceKm, 1.0)
    }

    @Test
    fun sunPositionMeeusExample25b() {
        // 1992-10-13 0h TD
        val a = AstroInstant(2448908.5 - AstroTime.deltaT(2448908.5) / 86400.0)
        assertEquals(198.37817, a.sun.rightAscension, 0.001)
        assertEquals(-7.78397, a.sun.declination, 0.001)
    }

    @Test
    fun sunriseSunsetVienna() {
        val d = LocalDate.of(2026, 6, 7)
        val day = Events.sunDay(jd(d.atStartOfDay(zone)), jd(d.plusDays(1).atStartOfDay(zone)), vienna)
        assertEquals(4 * 60 + 54.5, minutesOfDay(day.rise!!.jd), 1.5)
        assertEquals(20 * 60 + 51.5, minutesOfDay(day.set!!.jd), 1.5)
        assertEquals(12 * 60 + 53.5, minutesOfDay(day.noon!!.jd), 1.0)
        assertEquals(64.5, day.noon!!.altitude, 0.1)
    }

    @Test
    fun moonriseVienna() {
        val d = LocalDate.of(2026, 6, 7)
        val day = Events.moonDay(jd(d.atStartOfDay(zone)), jd(d.plusDays(1).atStartOfDay(zone)), vienna)
        assertEquals(0 * 60 + 54.5, minutesOfDay(day.rise!!.jd), 2.0)
        assertEquals(11 * 60 + 32.5, minutesOfDay(day.set!!.jd), 2.0)
        assertEquals(30.0, day.transit!!.altitude, 0.2)
    }

    @Test
    fun seasons2026() {
        val s = Events.seasons(2026)
        // Frühlingsanfang 20.03.2026 14:46 UTC
        assertEquals(jd(ZonedDateTime.of(2026, 3, 20, 14, 46, 0, 0, ZoneId.of("UTC"))), s[0], 3.0 / 1440)
        // Sommeranfang 21.06.2026 08:24 UTC
        assertEquals(jd(ZonedDateTime.of(2026, 6, 21, 8, 24, 0, 0, ZoneId.of("UTC"))), s[1], 3.0 / 1440)
    }

    @Test
    fun lunarEclipses() {
        val start = jd(ZonedDateTime.of(2026, 6, 7, 12, 0, 0, 0, zone))
        val e = Eclipses.nextLunarEclipses(start)
        val partial = e[LunarEclipseType.PARTIAL]
        assertNotNull(partial)
        // Partielle Mondfinsternis 28.08.2026, Maximum ca. 04:13 UTC
        assertEquals(jd(ZonedDateTime.of(2026, 8, 28, 4, 13, 0, 0, ZoneId.of("UTC"))), partial!!.jdMax, 10.0 / 1440)
    }

    @Test
    fun newMoonJune2026() {
        val start = jd(ZonedDateTime.of(2026, 6, 7, 12, 0, 0, 0, zone))
        val nm = Events.nextPhase(start, MoonPhase.NEW)
        // Neumond 15.06.2026 ca. 02:54 UTC
        assertEquals(jd(ZonedDateTime.of(2026, 6, 15, 2, 54, 0, 0, ZoneId.of("UTC"))), nm, 5.0 / 1440)
    }
}
