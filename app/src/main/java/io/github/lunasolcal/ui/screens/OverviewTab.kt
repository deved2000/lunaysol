package io.github.lunasolcal.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.GpsOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.lunasolcal.astro.Body
import io.github.lunasolcal.domain.AstroService
import io.github.lunasolcal.domain.Fmt
import io.github.lunasolcal.ui.AppViewModel
import io.github.lunasolcal.ui.components.LabelValue
import io.github.lunasolcal.ui.components.MoonIcon
import io.github.lunasolcal.ui.components.PagerDots
import io.github.lunasolcal.ui.components.RightValue
import io.github.lunasolcal.ui.components.SunIcon
import io.github.lunasolcal.ui.components.drawMoon
import io.github.lunasolcal.ui.components.drawSun
import io.github.lunasolcal.ui.theme.AppColors
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun OverviewTab(vm: AppViewModel, pager: PagerState, onGps: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { page ->
            if (page == 0) OverviewPage(vm, onGps) else SkyViewPage(vm)
        }
        PagerDots(2, pager.currentPage)
        DateBar(vm)
    }
}

private class OverviewData(
    val sunRise: String, val sunSet: String, val dayLength: String,
    val moonEvents: List<Pair<String, String>>,
    val age: Double, val illumination: Double, val elongation: Double,
    val phase: String, val phaseTime: String?
)

@Composable
private fun OverviewPage(vm: AppViewModel, onGps: () -> Unit) {
    val place = vm.place
    val date = vm.date
    val jd = AstroService.jd(vm.epochMillis)
    val data = rememberComputed(place, date, vm.epochMillis) {
        val zone = place.zone
        val sd = AstroService.sunDay(place, date, full = false)
        val md = AstroService.moonDay(place, date)
        val ref = AstroService.moonReferenceJd(md, jd)
        val stats = AstroService.moonStats(ref)
        val ph = AstroService.phaseForDay(place, date, ref)
        val rise = "Mondaufgang:" to (md.rise?.jd)
        val set = "Monduntergang:" to (md.set?.jd)
        val ordered = if (md.rise != null && md.set != null && md.set.jd < md.rise.jd) listOf(set, rise) else listOf(rise, set)
        OverviewData(
            Fmt.time(sd.rise?.jd, zone), Fmt.time(sd.set?.jd, zone), Fmt.duration(sd.dayLengthSeconds),
            ordered.map { it.first to Fmt.time(it.second, zone) },
            stats.ageDays, stats.illumination, stats.elongation,
            ph.name, ph.principalJd?.let { Fmt.dateTimeLong(it, zone) }
        )
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 4.dp), verticalArrangement = Arrangement.SpaceEvenly) {
        // Sonne
        Card {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SunIcon(Modifier.size(64.dp))
                Column(Modifier.padding(start = 8.dp).weight(1f)) {
                    LabelValue("Sonnenaufgang:", data?.sunRise ?: "…", 26, 26)
                    LabelValue("Sonnenuntergang:", data?.sunSet ?: "…", 26, 26)
                    LabelValue("Tageslänge:", data?.dayLength ?: "…", 21, 21)
                }
            }
        }
        // Mond
        Card {
            Row(verticalAlignment = Alignment.Top) {
                MoonIcon(data?.elongation ?: 180.0, Modifier.padding(top = 4.dp).size(64.dp), southern = place.latitude < 0)
                Column(Modifier.padding(start = 8.dp).weight(1f)) {
                    data?.moonEvents?.forEach { (label, value) -> LabelValue(label, value, 26, 26) }
                        ?: LabelValue("Mondaufgang:", "…", 26, 26)
                    LabelValue("Alter/Sichtbar:", data?.let { "${Fmt.number(it.age)}d/${Fmt.percent(it.illumination)}" } ?: "…", 21, 21)
                    LabelValue("Phase:", data?.phase ?: "…", 21, 21)
                    data?.phaseTime?.let { RightValue(it, 16) }
                }
            }
        }
        // Ort
        val dt = vm.dateTime
        Row(
            Modifier.fillMaxWidth().background(Color.Black, RoundedCornerShape(10.dp)).padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(52.dp).clickable(onClick = onGps), contentAlignment = Alignment.Center) {
                if (vm.gpsBusy) CircularProgressIndicator(Modifier.size(28.dp), color = Color.White, strokeWidth = 2.dp)
                else Icon(if (place.isGps) Icons.Filled.GpsFixed else Icons.Filled.GpsOff, "Position aktualisieren", tint = Color.White, modifier = Modifier.size(32.dp))
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(place.name.ifBlank { "Unbenannt" }, color = AppColors.Yellow, fontSize = 24.sp, textAlign = TextAlign.Center)
                Text(Fmt.coordinates(place.latitude, place.longitude, vm.decimalCoordinates), color = AppColors.LightYellow, fontSize = 16.sp)
                Text("${Fmt.zoneName(place.zone, dt)} ${Fmt.utcOffset(dt)}", color = AppColors.LightYellow, fontSize = 16.sp, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.size(12.dp))
        }
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Box(
        Modifier.fillMaxWidth().background(AppColors.Card, RoundedCornerShape(10.dp)).padding(horizontal = 6.dp, vertical = 12.dp)
    ) { content() }
}

/** Zweite Überblick-Seite: Himmelskarte mit aktueller Position von Sonne und Mond. */
@Composable
private fun SkyViewPage(vm: AppViewModel) {
    val place = vm.place
    val jd = AstroService.jd(vm.epochMillis)
    val sun = AstroService.position(Body.SUN, place, jd)
    val moon = AstroService.position(Body.MOON, place, jd)
    val stats = AstroService.moonStats(jd)
    val measurer = rememberTextMeasurer()
    Column(Modifier.fillMaxSize().padding(4.dp)) {
        Text("Himmel um ${Fmt.time(vm.dateTime)}", color = AppColors.Yellow, fontSize = 22.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Canvas(Modifier.fillMaxWidth().weight(1f).padding(8.dp)) {
            val r = size.minDimension / 2 * 0.86f
            val c = center
            drawCircle(Color(0xFF00007A), r, c)
            for (alt in listOf(30f, 60f)) drawCircle(Color.White.copy(alpha = 0.3f), r * (90 - alt) / 90, c, style = Stroke(1.5f))
            drawCircle(Color.White, r, c, style = Stroke(3f))
            drawLine(Color.White.copy(alpha = 0.3f), Offset(c.x - r, c.y), Offset(c.x + r, c.y))
            drawLine(Color.White.copy(alpha = 0.3f), Offset(c.x, c.y - r), Offset(c.x, c.y + r))
            val st = TextStyle(color = Color.White, fontSize = 16.sp)
            for ((label, az) in listOf("N" to 0.0, "O" to 90.0, "S" to 180.0, "W" to 270.0)) {
                val t = measurer.measure(label, st)
                val px = c.x + (r + 18) * sin(Math.toRadians(az)).toFloat()
                val py = c.y - (r + 18) * cos(Math.toRadians(az)).toFloat()
                drawText(t, topLeft = Offset(px - t.size.width / 2, py - t.size.height / 2))
            }
            fun toXY(alt: Double, az: Double): Offset {
                val d = r * ((90 - alt) / 90).toFloat()
                return Offset(c.x + d * sin(Math.toRadians(az)).toFloat(), c.y - d * cos(Math.toRadians(az)).toFloat())
            }
            if (moon.altitude > -2) drawMoon(toXY(moon.altitude.coerceAtLeast(0.0), moon.azimuth), 24f, stats.elongation, place.latitude < 0)
            if (sun.altitude > -2) drawSun(toXY(sun.altitude.coerceAtLeast(0.0), sun.azimuth), 28f)
        }
        Column(Modifier.fillMaxWidth().background(Color.Black, RoundedCornerShape(10.dp)).padding(8.dp)) {
            LabelValue("Sonne Höhe / Azimut:", "${Fmt.deg(sun.altitude)} / ${Fmt.deg(sun.azimuth)}")
            LabelValue("Mond Höhe / Azimut:", "${Fmt.deg(moon.altitude)} / ${Fmt.deg(moon.azimuth)}")
            if (sun.altitude < -0.83) Text("Sonne unter dem Horizont", color = AppColors.LightYellow, fontSize = 15.sp)
            if (moon.altitude < -0.83) Text("Mond unter dem Horizont", color = AppColors.LightYellow, fontSize = 15.sp)
        }
    }
}
