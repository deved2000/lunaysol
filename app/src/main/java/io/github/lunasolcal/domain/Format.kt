package io.github.lunasolcal.domain

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** Deutsche Formatierung von Zeiten, Winkeln und Entfernungen. */
object Fmt {
    val DE: Locale = Locale.GERMAN
    private val DE_NUM: Locale = Locale.GERMANY

    private val TIME = DateTimeFormatter.ofPattern("HH:mm", DE)
    private val DATE_LONG = DateTimeFormatter.ofPattern("EEEE, d. MMMM yyyy", DE)
    private val DATE_MEDIUM = DateTimeFormatter.ofPattern("d. MMMM yyyy", DE)
    private val DATE_NUM = DateTimeFormatter.ofPattern("dd.MM.yyyy", DE)

    /** Uhrzeit HH:mm (Sekunden werden abgeschnitten, wie in klassischen Almanachen). */
    fun time(jd: Double?, zone: ZoneId): String =
        if (jd == null) "-" else AstroService.toZoned(jd, zone).withSecond(0).withNano(0).format(TIME)

    fun time(z: ZonedDateTime): String = z.format(TIME)

    fun dateLong(d: LocalDate): String = d.format(DATE_LONG)
    fun dateMedium(d: LocalDate): String = d.format(DATE_MEDIUM)

    fun dateTimeLong(jd: Double?, zone: ZoneId): String =
        if (jd == null) "-" else AstroService.toZoned(jd, zone).let { "${it.format(DATE_LONG)} ${it.format(TIME)}" }

    fun dateTimeMedium(jd: Double?, zone: ZoneId): String =
        if (jd == null) "-" else AstroService.toZoned(jd, zone).let { "${it.format(DATE_MEDIUM)} ${it.format(TIME)}" }

    fun dateTimeNumeric(jd: Double?, zone: ZoneId): String =
        if (jd == null) "-" else AstroService.toZoned(jd, zone).let { "${it.format(DATE_NUM)} ${it.format(TIME)}" }

    fun deg(x: Double?, digits: Int = 1): String =
        if (x == null) "-" else String.format(DE_NUM, "%.${digits}f°", x)

    fun km(x: Double): String = String.format(DE_NUM, "%,d km", x.roundToLong())

    fun number(x: Double, digits: Int = 1): String = String.format(DE_NUM, "%.${digits}f", x)

    fun percent(fraction: Double): String = "${(fraction * 100).roundToInt()}%"

    /** Dauer als "15h 56m 52s". */
    fun duration(seconds: Double): String {
        val s = seconds.roundToLong().coerceAtLeast(0)
        return String.format(DE, "%dh %02dm %02ds", s / 3600, (s % 3600) / 60, s % 60)
    }

    /** Differenz als "+1m 12s", "+58s", "-8m 09s", "+7h 36m 26s". */
    fun delta(seconds: Double): String {
        val sign = if (seconds < 0) "-" else "+"
        val s = abs(seconds).roundToLong()
        val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
        return when {
            h > 0 -> String.format(DE, "%s%dh %02dm %02ds", sign, h, m, sec)
            m > 0 -> String.format(DE, "%s%dm %02ds", sign, m, sec)
            else -> "$sign${sec}s"
        }
    }

    /** Zeitgleichung in Minuten als "m:ss" (mit Vorzeichen bei negativen Werten). */
    fun equationOfTime(minutes: Double): String {
        val totalSec = (abs(minutes) * 60).toLong()
        return String.format(DE, "%s%d:%02d", if (minutes < 0) "-" else "", totalSec / 60, totalSec % 60)
    }

    /** Rektaszension in Stunden als "5h02m18s". */
    fun rightAscension(deg: Double): String {
        val totalSec = (deg / 15.0 * 3600.0).roundToLong() % 86400
        return String.format(DE, "%dh%02dm%02ds", totalSec / 3600, (totalSec % 3600) / 60, totalSec % 60)
    }

    private fun dms(value: Double): String {
        var a = abs(value)
        var d = floor(a).toInt()
        a = (a - d) * 60
        var m = floor(a).toInt()
        var s = ((a - m) * 60).roundToInt()
        if (s == 60) { s = 0; m += 1 }
        if (m == 60) { m = 0; d += 1 }
        return "$d°$m'$s\""
    }

    fun latitude(lat: Double, decimal: Boolean = false): String =
        if (decimal) String.format(DE_NUM, "%.5f°%s", abs(lat), if (lat >= 0) "N" else "S")
        else dms(lat) + if (lat >= 0) "N" else "S"

    fun longitude(lon: Double, decimal: Boolean = false): String =
        if (decimal) String.format(DE_NUM, "%.5f°%s", abs(lon), if (lon >= 0) "O" else "W")
        else dms(lon) + if (lon >= 0) "O" else "W"

    fun coordinates(lat: Double, lon: Double, decimal: Boolean = false) =
        "${latitude(lat, decimal)}   ${longitude(lon, decimal)}"

    /** Name der Zeitzone, z. B. "Mitteleuropäische Sommerzeit". */
    fun zoneName(zone: ZoneId, at: ZonedDateTime): String {
        val tz = TimeZone.getTimeZone(zone)
        val dst = zone.rules.isDaylightSavings(at.toInstant())
        val name = tz.getDisplayName(dst, TimeZone.LONG, DE)
        return if (name.startsWith("GMT") || name.startsWith("Etc")) zone.getDisplayName(TextStyle.FULL, DE) else name
    }

    /** "(UTC+2)" bzw. "(UTC+5:30)". */
    fun utcOffset(at: ZonedDateTime): String {
        val total = at.offset.totalSeconds
        val sign = if (total < 0) "-" else "+"
        val h = abs(total) / 3600; val m = (abs(total) % 3600) / 60
        return if (m == 0) "(UTC$sign$h)" else String.format(DE, "(UTC%s%d:%02d)", sign, h, m)
    }
}
