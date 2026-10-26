package io.github.lunasolcal.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.lunasolcal.astro.AstroInstant
import io.github.lunasolcal.astro.Body
import io.github.lunasolcal.astro.Eclipses
import io.github.lunasolcal.astro.Events
import io.github.lunasolcal.astro.LunarEclipse
import io.github.lunasolcal.astro.LunarEclipseType
import io.github.lunasolcal.astro.MoonDay
import io.github.lunasolcal.astro.MoonPhase
import io.github.lunasolcal.domain.AstroService
import io.github.lunasolcal.domain.Fmt
import io.github.lunasolcal.ui.AppViewModel
import io.github.lunasolcal.ui.components.LabelValue
import io.github.lunasolcal.ui.components.LocationHeader
import io.github.lunasolcal.ui.components.MoonIcon
import io.github.lunasolcal.ui.components.PagerDots
import io.github.lunasolcal.ui.components.RightValue
import io.github.lunasolcal.ui.components.VSpace
import io.github.lunasolcal.ui.theme.AppColors
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import java.time.ZoneId

@Composable
fun MoonTab(vm: AppViewModel, pager: PagerState) {
    Column(Modifier.fillMaxSize()) {
        LocationHeader(vm.place.name, Fmt.zoneName(vm.zone, vm.dateTime))
        HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { page ->
            when (page) {
                0 -> MoonDetailsPage(vm)
                1 -> MoonGraphPage(vm)
                else -> MoonTablePage(vm)
            }
        }
        if (pager.currentPage == 1) TimeBar(vm)
        PagerDots(3, pager.currentPage)
        DateBar(vm)
    }
}

private class MoonDetails(
    val day: MoonDay,
    val stats: AstroService.MoonStats,
    val phase: String,
    val nextNew: Double,
    val nextFull: Double,
    val perigee: Double?,
    val apogee: Double?
)

@Composable
private fun MoonDetailsPage(vm: AppViewModel) {
    val place = vm.place
    val date = vm.date
    val zone = place.zone
    val jd = AstroService.jd(vm.epochMillis)
    val d = rememberComputed(place, date, vm.epochMillis) {
        val day = AstroService.moonDay(place, date)
        val ref = AstroService.moonReferenceJd(day, jd)
        val ph = AstroService.phaseForDay(place, date, ref)
        MoonDetails(
            day, AstroService.moonStats(ref), ph.name,
            Events.nextPhase(jd, MoonPhase.NEW), Events.nextPhase(jd, MoonPhase.FULL),
            Events.nextPerigee(jd), Events.nextApogee(jd)
        )
    }
    val eclipses = rememberComputed(date) {
        val ctx = currentCoroutineContext()
        Eclipses.nextLunarEclipses(AstroService.dayBounds(date, zone).first) { !ctx.isActive }
    }
    BlackPanel {
        if (d == null) Text("Berechne …", color = AppColors.Yellow, fontSize = 18.sp)
        else MoonDetailsContent(d, zone, eclipses, place.latitude < 0)
    }
}

private fun lunarName(e: LunarEclipse) = when (e.type) {
    LunarEclipseType.TOTAL -> "Totale Kernschattenfinsternis:"
    LunarEclipseType.PARTIAL -> "Partielle Kernschattenfinsternis:"
    LunarEclipseType.PENUMBRAL -> if (e.penumbralMagnitude >= 1.0) "Totale Halbschattenfinsternis:" else "Partielle Halbschattenfinsternis:"
}

@Composable
private fun MoonDetailsContent(d: MoonDetails, zone: ZoneId, eclipses: Map<LunarEclipseType, LunarEclipse?>?, southern: Boolean) {
    val day = d.day
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(0.9f)) { MoonIcon(d.stats.elongation, Modifier.size(46.dp), southern) }
        Text("Aufgang", color = AppColors.LightYellow, fontSize = 18.sp, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
        Text("Untergang", color = AppColors.LightYellow, fontSize = 18.sp, textAlign = TextAlign.End, modifier = Modifier.weight(1.1f))
        Text("Kulminat.", color = AppColors.LightYellow, fontSize = 18.sp, textAlign = TextAlign.End, modifier = Modifier.weight(0.85f))
    }
    Grid4("Zeit:", Fmt.time(day.rise?.jd, zone), Fmt.time(day.set?.jd, zone), Fmt.time(day.transit?.jd, zone), 18, 30)
    Grid4("Azimut:", Fmt.deg(day.rise?.azimuth), Fmt.deg(day.set?.azimuth), Fmt.deg(day.transit?.azimuth))
    Grid4("Höhe:", Fmt.deg(day.rise?.altitude), Fmt.deg(day.set?.altitude), Fmt.deg(day.transit?.altitude))
    VSpace(18)
    LabelValue("Phase:", d.phase)
    VSpace(14)
    Text("Nächster Neumond:", color = AppColors.LightYellow, fontSize = 18.sp)
    RightValue(Fmt.dateTimeLong(d.nextNew, zone))
    Text("Nächster Vollmond:", color = AppColors.LightYellow, fontSize = 18.sp)
    RightValue(Fmt.dateTimeLong(d.nextFull, zone))
    VSpace(18)
    LabelValue("Alter/Sichtbar:", "${Fmt.number(d.stats.ageDays)}d / ${Fmt.percent(d.stats.illumination)}")
    LabelValue("Untere Kulmination:", Fmt.dateTimeLong(day.lowerTransit?.jd, zone))
    day.lowerTransit?.let { RightValue(Fmt.deg(it.altitude)) }
    LabelValue("Deklination:", Fmt.deg(d.stats.declination))
    LabelValue("Distanz:", Fmt.km(d.stats.distanceKm))
    LabelValue("Nächstes Perigäum:", Fmt.dateTimeMedium(d.perigee, zone))
    d.perigee?.let { RightValue(Fmt.km(AstroInstant(it).moon.distanceKm)) }
    LabelValue("Nächstes Apogäum:", Fmt.dateTimeMedium(d.apogee, zone))
    d.apogee?.let { RightValue(Fmt.km(AstroInstant(it).moon.distanceKm)) }
    VSpace(18)
    Text("Nächste Mondfinsternisse:", color = AppColors.LightYellow, fontSize = 18.sp)
    if (eclipses == null) {
        LabelValue("", "berechne …", indent = 12)
    } else {
        eclipses.values.filterNotNull().sortedBy { it.jdMax }.forEach { e ->
            LabelValue(lunarName(e), Fmt.dateTimeMedium(e.jdMax, zone), labelSize = 16, valueSize = 16, indent = 12)
        }
    }
    VSpace(8)
    Text("Mondfinsternisse: Zeitpunkt der Finsternismitte, unabhängig von der Sichtbarkeit am Ort.", color = Color.Gray, fontSize = 12.sp)
}

@Composable
private fun MoonGraphPage(vm: AppViewModel) {
    val place = vm.place
    val date = vm.date
    val jd = AstroService.jd(vm.epochMillis)
    val graph = rememberComputed(place, date) { buildMoonGraph(place, date) }
    val pos = AstroService.position(Body.MOON, place, jd)
    val elong = AstroInstant(jd).moonElongation()
    Column(Modifier.fillMaxSize()) {
        SkyGraph(graph, Body.MOON, jd, pos, elong, place.latitude < 0, Modifier.fillMaxWidth().weight(1f))
        PositionInfo(pos)
    }
}
