package io.github.lunasolcal.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.lunasolcal.astro.AstroInstant
import io.github.lunasolcal.astro.Body
import io.github.lunasolcal.astro.Eclipses
import io.github.lunasolcal.astro.Events
import io.github.lunasolcal.astro.SolarEclipseType
import io.github.lunasolcal.astro.SunDay
import io.github.lunasolcal.domain.AstroService
import io.github.lunasolcal.domain.Fmt
import io.github.lunasolcal.ui.AppViewModel
import io.github.lunasolcal.ui.components.LabelValue
import io.github.lunasolcal.ui.components.LocationHeader
import io.github.lunasolcal.ui.components.PagerDots
import io.github.lunasolcal.ui.components.RightValue
import io.github.lunasolcal.ui.components.SunIcon
import io.github.lunasolcal.ui.components.VSpace
import io.github.lunasolcal.ui.theme.AppColors
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import java.time.ZoneId

@Composable
fun SunTab(vm: AppViewModel, pager: PagerState) {
    val dt = vm.dateTime
    Column(Modifier.fillMaxSize()) {
        LocationHeader(vm.place.name, Fmt.zoneName(vm.zone, dt))
        HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { page ->
            when (page) {
                0 -> SunDetailsPage(vm)
                1 -> SunGraphPage(vm)
                2 -> SunTablePage(vm)
                else -> SeasonsPage(vm)
            }
        }
        if (pager.currentPage == 1) TimeBar(vm)
        PagerDots(4, pager.currentPage)
        if (pager.currentPage == 3) YearBar(vm) else DateBar(vm)
    }
}

/** Gemeinsames Raster: Beschriftung + drei rechtsbündige Spalten. */
@Composable
fun Grid4(label: String, a: String, b: String, c: String, size: Int = 18, bigSize: Int = size, labelColor: Color = AppColors.LightYellow) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Text(label, color = labelColor, fontSize = 18.sp, modifier = Modifier.weight(0.9f))
        Text(a, color = AppColors.Yellow, fontSize = bigSize.sp, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
        Text(b, color = AppColors.Yellow, fontSize = bigSize.sp, textAlign = TextAlign.End, modifier = Modifier.weight(1.1f))
        Text(c, color = AppColors.Yellow, fontSize = size.sp, textAlign = TextAlign.End, modifier = Modifier.weight(0.85f))
    }
}

@Composable
fun BlackPanel(content: @Composable () -> Unit) {
    Box(
        Modifier.fillMaxSize().padding(horizontal = 2.dp).background(Color.Black, RoundedCornerShape(10.dp))
            .verticalScroll(rememberScrollState()).padding(4.dp)
    ) { Column { content() } }
}

private class SunDetails(
    val day: SunDay,
    val yesterday: Double,
    val winter: Double,
    val summer: Double,
    val eqTime: Double,
    val declination: Double,
    val distance: Double,
    val aphelion: Double?,
    val perihelion: Double?
)

private fun twilight(p: Pair<Double?, Double?>, zone: ZoneId) = "${Fmt.time(p.first, zone)}  -  ${Fmt.time(p.second, zone)}"

@Composable
private fun SunDetailsPage(vm: AppViewModel) {
    val place = vm.place
    val date = vm.date
    val zone = place.zone
    val jd = AstroService.jd(vm.epochMillis)
    val d = rememberComputed(place, date) {
        val day = AstroService.sunDay(place, date)
        val yest = AstroService.sunDay(place, date.minusDays(1), full = false).dayLengthSeconds
        val seasons = Events.seasons(date.year)
        val summerDate = AstroService.toZoned(seasons[1], zone).toLocalDate()
        val winterDate = AstroService.toZoned(seasons[3], zone).toLocalDate()
        val ref = day.noon?.jd ?: jd
        val a = AstroInstant(ref)
        SunDetails(
            day, yest,
            AstroService.sunDay(place, winterDate, full = false).dayLengthSeconds,
            AstroService.sunDay(place, summerDate, full = false).dayLengthSeconds,
            a.equationOfTime(), a.sun.declination, a.sun.distanceKm,
            Events.nextAphelion(ref), Events.nextPerihelion(ref)
        )
    }
    val eclipses = rememberComputed(place.latitude, place.longitude, date) {
        val ctx = currentCoroutineContext()
        Eclipses.nextLocalSolarEclipses(AstroService.dayBounds(date, zone).first, AstroService.observer(place)) { !ctx.isActive }
    }

    BlackPanel {
        if (d == null) Text("Berechne …", color = AppColors.Yellow, fontSize = 18.sp)
        else SunDetailsContent(d, zone, eclipses)
    }
}

@Composable
private fun SunDetailsContent(d: SunDetails, zone: ZoneId, eclipses: Map<SolarEclipseType, io.github.lunasolcal.astro.LocalSolarEclipse?>?) {
    run {
        val day = d.day
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SunIcon(Modifier.size(48.dp).weight(0.9f, fill = false))
            Box(Modifier.weight(0.1f))
            Text("Aufgang", color = AppColors.LightYellow, fontSize = 18.sp, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
            Text("Untergang", color = AppColors.LightYellow, fontSize = 18.sp, textAlign = TextAlign.End, modifier = Modifier.weight(1.1f))
            Text("Mittag", color = AppColors.LightYellow, fontSize = 18.sp, textAlign = TextAlign.End, modifier = Modifier.weight(0.85f))
        }
        Grid4("Zeit:", Fmt.time(day.rise?.jd, zone), Fmt.time(day.set?.jd, zone), Fmt.time(day.noon?.jd, zone), 18, 30)
        Grid4("Azimut:", Fmt.deg(day.rise?.azimuth), Fmt.deg(day.set?.azimuth), Fmt.deg(day.noon?.azimuth))
        Grid4("Höhe:", Fmt.deg(day.rise?.altitude), Fmt.deg(day.set?.altitude), Fmt.deg(day.noon?.altitude))
        VSpace(14)
        LabelValue("Tageslänge:", Fmt.duration(day.dayLengthSeconds))
        SubDelta("Gestern:", Fmt.delta(day.dayLengthSeconds - d.yesterday))
        SubDelta("Wintersonnenwende:", Fmt.delta(day.dayLengthSeconds - d.winter))
        SubDelta("Sommersonnenwende:", Fmt.delta(day.dayLengthSeconds - d.summer))
        VSpace(14)
        Text("Dämmerungsbeginn/-ende:", color = AppColors.LightYellow, fontSize = 18.sp)
        LabelValue("Astronomisch:", twilight(day.astronomicalMorning, zone), indent = 12)
        RightValue(twilight(day.astronomicalEvening, zone))
        LabelValue("Nautisch:", twilight(day.nauticalMorning, zone), indent = 12)
        RightValue(twilight(day.nauticalEvening, zone))
        LabelValue("Bürgerlich:", twilight(day.civilMorning, zone), indent = 12)
        RightValue(twilight(day.civilEvening, zone))
        VSpace(14)
        LabelValue("Echte Mitternacht:", Fmt.dateTimeNumeric(day.midnight?.jd, zone))
        RightValue(Fmt.deg(day.midnight?.altitude))
        VSpace(14)
        LabelValue("Zeitgleichung:", Fmt.equationOfTime(d.eqTime))
        LabelValue("Deklination:", Fmt.deg(d.declination))
        LabelValue("Distanz:", Fmt.km(d.distance))
        VSpace(10)
        LabelValue("Nächstes Aphel:", Fmt.dateTimeMedium(d.aphelion, zone))
        d.aphelion?.let { RightValue(Fmt.km(AstroInstant(it).sun.distanceKm)) }
        LabelValue("Nächstes Perihel:", Fmt.dateTimeMedium(d.perihelion, zone))
        d.perihelion?.let { RightValue(Fmt.km(AstroInstant(it).sun.distanceKm)) }
        VSpace(14)
        Text("Nächste Sonnenfinsternisse:", color = AppColors.LightYellow, fontSize = 18.sp)
        val labels = listOf(SolarEclipseType.PARTIAL to "Partiell:", SolarEclipseType.ANNULAR to "Ringförmig:", SolarEclipseType.TOTAL to "Total:")
        for ((type, label) in labels) {
            val value = when {
                eclipses == null -> "berechne …"
                eclipses[type] == null -> "> 1000 Jahre"
                else -> Fmt.dateTimeMedium(eclipses[type]!!.jdMax, zone)
            }
            LabelValue(label, value, indent = 12)
        }
        VSpace(8)
        Text("Sonnenfinsternisse: lokal sichtbar (Sonne über dem Horizont), Zeit der größten Bedeckung.", color = Color.Gray, fontSize = 12.sp)
    }
}

@Composable
private fun SubDelta(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, color = AppColors.LightYellow, fontSize = 15.sp, textAlign = TextAlign.End, modifier = Modifier.weight(2.4f))
        Text(value, color = AppColors.Yellow, fontSize = 15.sp, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun SunGraphPage(vm: AppViewModel) {
    val place = vm.place
    val date = vm.date
    val jd = AstroService.jd(vm.epochMillis)
    val graph = rememberComputed(place, date) { buildSunGraph(place, date) }
    val pos = AstroService.position(Body.SUN, place, jd)
    Column(Modifier.fillMaxSize()) {
        SkyGraph(graph, Body.SUN, jd, pos, 0.0, place.latitude < 0, Modifier.fillMaxWidth().weight(1f))
        PositionInfo(pos)
    }
}

@Composable
private fun SeasonsPage(vm: AppViewModel) {
    val year = vm.dateTime.year
    val zone = vm.zone
    val seasons = rememberComputed(year, zone) { Events.seasons(year) }
    val south = vm.place.latitude < 0
    val names = if (!south) listOf("Frühling (Tag- u. Nachtgleiche)", "Sommer (Sonnenwende)", "Herbst (Tag- u. Nachtgleiche)", "Winter (Sonnenwende)")
    else listOf("Herbst (Tag- u. Nachtgleiche)", "Winter (Sonnenwende)", "Frühling (Tag- u. Nachtgleiche)", "Sommer (Sonnenwende)")
    Box(Modifier.fillMaxSize().padding(horizontal = 2.dp).background(Color.Black, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Jahreszeiten", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            VSpace(14)
            names.forEachIndexed { i, n ->
                Text(n, color = AppColors.LightYellow, fontSize = 22.sp, textAlign = TextAlign.Center)
                val z = seasons?.let { AstroService.toZoned(it[i], zone) }
                Text(
                    z?.let { "${Fmt.dateMedium(it.toLocalDate())} - ${Fmt.time(it)}" } ?: "…",
                    color = AppColors.Yellow, fontSize = 22.sp, textAlign = TextAlign.Center
                )
                VSpace(10)
            }
        }
    }
}
