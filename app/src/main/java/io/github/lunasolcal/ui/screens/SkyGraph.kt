package io.github.lunasolcal.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.lunasolcal.astro.AstroInstant
import io.github.lunasolcal.astro.Body
import io.github.lunasolcal.astro.Events
import io.github.lunasolcal.astro.Horizontal
import io.github.lunasolcal.data.Place
import io.github.lunasolcal.domain.AstroService
import io.github.lunasolcal.domain.Fmt
import io.github.lunasolcal.ui.components.LabelValue
import io.github.lunasolcal.ui.components.drawMoon
import io.github.lunasolcal.ui.components.drawSun
import io.github.lunasolcal.ui.theme.AppColors
import java.time.LocalDate
import kotlin.math.abs

/** Alle Daten für das Höhen-Diagramm eines Tages. */
class GraphData(
    val start: Double,
    val end: Double,
    val curve: FloatArray,
    val refCurves: List<FloatArray>,
    val yRange: Float,
    /** x-Anteil (0..1) und Zeitbeschriftung oben */
    val verticalLines: List<Pair<Float, String>>,
    /** x-Anteil, Buchstabe (O/S/W), Zeit (oder null) */
    val ticks: List<Triple<Float, String, String?>>,
    val riseLabel: Pair<Float, String>?,
    val setLabel: Pair<Float, String>?,
    val maxAlt: Float?,
    val minAlt: Float?
)

private const val SAMPLES = 160

private fun sampleCurve(body: Body, place: Place, start: Double, end: Double): FloatArray {
    val obs = AstroService.observer(place)
    return FloatArray(SAMPLES + 1) { i ->
        val jd = start + (end - start) * i / SAMPLES
        AstroInstant(jd).horizontal(body, obs).altitude.toFloat()
    }
}

private fun frac(jd: Double, start: Double, end: Double) = ((jd - start) / (end - start)).toFloat()

/** Diagrammdaten Sonne: 24 Stunden von „echter Mitternacht“ zu „echter Mitternacht“. */
fun buildSunGraph(place: Place, date: LocalDate): GraphData {
    val zone = place.zone
    val obs = AstroService.observer(place)
    val day = AstroService.sunDay(place, date, full = false)
    val (ds, de) = AstroService.dayBounds(date, zone)
    val noon = day.noon?.jd ?: ((ds + de) / 2)
    val start = noon - 0.5
    val end = noon + 0.5
    val curve = sampleCurve(Body.SUN, place, start, end)

    // Vergleichskurven: Sommer- und Wintersonnenwende
    val seasons = Events.seasons(date.year)
    val refs = listOf(seasons[1], seasons[3]).map { jd ->
        val d = AstroService.toZoned(jd, zone).toLocalDate()
        val (s, e) = AstroService.dayBounds(d, zone)
        val n = Events.transits(Body.SUN, s, e, obs).firstOrNull()?.jd ?: ((s + e) / 2)
        sampleCurve(Body.SUN, place, n - 0.5, n + 0.5)
    }

    val lines = ArrayList<Pair<Float, String>>()
    val rises = Events.riseSet(Body.SUN, start, end, obs)
    val rise = rises.firstOrNull { it.rising }
    val set = rises.firstOrNull { !it.rising }
    rise?.let { lines.add(frac(it.jd, start, end) to Fmt.time(it.jd, zone)) }
    lines.add(0.5f to Fmt.time(noon, zone))
    set?.let { lines.add(frac(it.jd, start, end) to Fmt.time(it.jd, zone)) }

    val ticks = ArrayList<Triple<Float, String, String?>>()
    for ((az, letter) in listOf(90.0 to "O", 180.0 to "S", 270.0 to "W")) {
        Events.azimuthCrossings(Body.SUN, start, end, obs, az).firstOrNull()?.let {
            ticks.add(Triple(frac(it, start, end), letter, if (letter == "S") null else Fmt.time(it, zone)))
        }
    }
    val riseLabel = rise?.let { frac(it.jd, start, end) to Fmt.deg(Events.horizontal(Body.SUN, it.jd, obs).azimuth) }
    val setLabel = set?.let { frac(it.jd, start, end) to Fmt.deg(Events.horizontal(Body.SUN, it.jd, obs).azimuth) }
    val yRange = ((90.0 - abs(place.latitude) + 23.44) * 1.11).coerceIn(25.0, 100.0).toFloat()
    return GraphData(start, end, curve, refs, yRange, lines, ticks, riseLabel, setLabel, curve.max(), curve.min())
}

/** Diagrammdaten Mond: lokaler Kalendertag 00:00–24:00. */
fun buildMoonGraph(place: Place, date: LocalDate): GraphData {
    val zone = place.zone
    val obs = AstroService.observer(place)
    val (start, end) = AstroService.dayBounds(date, zone)
    val curve = sampleCurve(Body.MOON, place, start, end)
    val md = Events.moonDay(start, end, obs)
    val lines = ArrayList<Pair<Float, String>>()
    md.rise?.let { lines.add(frac(it.jd, start, end) to Fmt.time(it.jd, zone)) }
    md.transit?.let { lines.add(frac(it.jd, start, end) to Fmt.time(it.jd, zone)) }
    md.set?.let { lines.add(frac(it.jd, start, end) to Fmt.time(it.jd, zone)) }
    val ticks = ArrayList<Triple<Float, String, String?>>()
    for ((az, letter) in listOf(90.0 to "O", 180.0 to "S", 270.0 to "W")) {
        Events.azimuthCrossings(Body.MOON, start, end, obs, az).firstOrNull()?.let {
            ticks.add(Triple(frac(it, start, end), letter, null))
        }
    }
    val yRange = ((90.0 - abs(place.latitude) + 28.6) * 1.13).coerceIn(25.0, 105.0).toFloat()
    return GraphData(
        start, end, curve, emptyList(), yRange, lines, ticks,
        md.rise?.let { frac(it.jd, start, end) to Fmt.deg(it.azimuth) },
        md.set?.let { frac(it.jd, start, end) to Fmt.deg(it.azimuth) },
        md.transit?.altitude?.toFloat() ?: curve.max(),
        null
    )
}

/** Blaues Höhen-Diagramm mit Horizont, Kurve(n) und Symbol an der aktuellen Position. */
@Composable
fun SkyGraph(
    data: GraphData?,
    body: Body,
    selectedJd: Double,
    current: Horizontal,
    elongation: Double,
    southern: Boolean,
    modifier: Modifier = Modifier
) {
    val measurer = rememberTextMeasurer()
    val labelColor = if (body == Body.SUN) Color.White else AppColors.Green
    Canvas(modifier.background(AppColors.GraphBlue)) {
        if (data == null) return@Canvas
        val w = size.width; val h = size.height
        val topPad = 46f
        val mid = h / 2 + topPad / 2
        val scale = (h - topPad) / 2 / data.yRange
        fun y(alt: Float) = mid - alt * scale
        fun x(f: Float) = f * w
        val small = TextStyle(color = labelColor, fontSize = 13.sp)
        val green = TextStyle(color = AppColors.Green, fontSize = 13.sp)
        val white = TextStyle(color = Color.White, fontSize = 13.sp)
        val thin = Color.White.copy(alpha = 0.55f)

        // Max-/Min-Linien mit Beschriftung
        data.maxAlt?.let { a ->
            drawLine(thin, Offset(0f, y(a)), Offset(w, y(a)), 1.5f)
            val t = measurer.measure(Fmt.deg(a.toDouble()), green)
            drawText(t, topLeft = Offset(w - t.size.width - 2, y(a) - t.size.height))
        }
        data.minAlt?.let { a ->
            drawLine(thin, Offset(0f, y(a)), Offset(w, y(a)), 1.5f)
            val t = measurer.measure(Fmt.deg(a.toDouble()), green)
            drawText(t, topLeft = Offset(w - t.size.width - 2, y(a) - t.size.height))
        }
        // Senkrechte Linien bei Auf-/Untergang/Kulmination
        for ((f, label) in data.verticalLines) {
            drawLine(thin, Offset(x(f), topPad + 10), Offset(x(f), mid + 12), 1.5f)
            val t = measurer.measure(label, small)
            val lx = (x(f) - t.size.width / 2).coerceIn(0f, w - t.size.width)
            drawText(t, topLeft = Offset(lx, 4f))
        }
        // Horizont
        drawLine(Color.White, Offset(0f, mid), Offset(w, mid), 3f)

        fun curvePath(c: FloatArray): Path {
            val p = Path()
            c.forEachIndexed { i, a ->
                val px = w * i / (c.size - 1)
                if (i == 0) p.moveTo(px, y(a)) else p.lineTo(px, y(a))
            }
            return p
        }
        for (rc in data.refCurves) drawPath(curvePath(rc), Color.Black, style = Stroke(width = 4f))
        drawPath(curvePath(data.curve), Color.White, style = Stroke(width = 3.5f))

        // Himmelsrichtungen
        for ((f, letter, time) in data.ticks) {
            drawLine(Color.White, Offset(x(f), mid - 6), Offset(x(f), mid + 6), 3f)
            val t = measurer.measure(letter, white)
            drawText(t, topLeft = Offset(x(f) - t.size.width / 2, mid + 14))
            if (time != null) {
                val tt = measurer.measure(time, white)
                drawText(tt, topLeft = Offset((x(f) - tt.size.width / 2).coerceIn(0f, w - tt.size.width), mid - tt.size.height - 6))
            }
        }
        // Azimut bei Auf-/Untergang
        data.riseLabel?.let { (f, s) ->
            val t = measurer.measure(s, green)
            drawText(t, topLeft = Offset((x(f) - t.size.width * 0.6f).coerceIn(0f, w - t.size.width), mid + 36))
        }
        data.setLabel?.let { (f, s) ->
            val t = measurer.measure(s, green)
            drawText(t, topLeft = Offset((x(f) - t.size.width * 0.4f).coerceIn(0f, w - t.size.width), mid + 36))
        }

        // Aktuelle Position
        var f = ((selectedJd - data.start) / (data.end - data.start)).toFloat()
        if (f < 0f) f += 1f
        if (f > 1f) f -= 1f
        val pos = Offset(x(f), y(current.altitude.toFloat()))
        if (body == Body.SUN) drawSun(pos, 30f) else drawMoon(pos, 26f, elongation, southern)
    }
}

/** Info-Block unter dem Diagramm: Höhe, Azimut, Rektaszension, Deklination. */
@Composable
fun PositionInfo(h: Horizontal) {
    Row(Modifier.fillMaxWidth().background(io.github.lunasolcal.ui.theme.AppColors.Navy)) {
        Column(Modifier.weight(1f).background(Color.Black).padding(horizontal = 4.dp)) {
            LabelValue("Höhe:", Fmt.deg(h.altitude), labelColor = AppColors.LightYellow)
            LabelValue("Azimut:", Fmt.deg(h.azimuth), labelColor = AppColors.LightYellow)
        }
        Column(Modifier.weight(1f).padding(start = 30.dp).background(Color.Black).padding(horizontal = 4.dp)) {
            LabelValue("Rektasz.:", Fmt.rightAscension(h.rightAscension), labelColor = AppColors.LightYellow)
            LabelValue("Deklination:", Fmt.deg(h.declination), labelColor = AppColors.LightYellow)
        }
    }
}
