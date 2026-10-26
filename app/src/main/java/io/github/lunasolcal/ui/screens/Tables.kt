@file:OptIn(ExperimentalFoundationApi::class)

package io.github.lunasolcal.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.lunasolcal.data.Place
import io.github.lunasolcal.domain.AstroService
import io.github.lunasolcal.domain.Fmt
import io.github.lunasolcal.ui.AppViewModel
import io.github.lunasolcal.ui.components.MoonIcon
import io.github.lunasolcal.ui.theme.AppColors
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/** Eine Tabellenzeile: Datum + vier Spalten mit je Haupt- und Nebenwert. */
class TableRow(
    val date: LocalDate,
    val cells: List<Pair<String, String>>,
    val elongation: Double? = null
)

class MonthBlock(val month: YearMonth, val rows: List<TableRow>)

private fun weekdayShort(d: LocalDate) =
    d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.GERMAN).take(2) + "."

fun buildSunTable(place: Place, around: LocalDate): List<MonthBlock> {
    val zone = place.zone
    val first = YearMonth.from(around).minusMonths(1)
    var prevLength = AstroService.sunDay(place, first.atDay(1).minusDays(1), full = false).dayLengthSeconds
    return (0L..2L).map { m ->
        val ym = first.plusMonths(m)
        val rows = (1..ym.lengthOfMonth()).map { dayNo ->
            val d = ym.atDay(dayNo)
            val sd = AstroService.sunDay(place, d, full = false)
            val row = TableRow(
                d,
                listOf(
                    Fmt.time(sd.rise?.jd, zone) to Fmt.deg(sd.rise?.azimuth),
                    Fmt.time(sd.set?.jd, zone) to Fmt.deg(sd.set?.azimuth),
                    Fmt.time(sd.noon?.jd, zone) to Fmt.deg(sd.noon?.altitude),
                    Fmt.duration(sd.dayLengthSeconds) to Fmt.delta(sd.dayLengthSeconds - prevLength)
                )
            )
            prevLength = sd.dayLengthSeconds
            row
        }
        MonthBlock(ym, rows)
    }
}

fun buildMoonTable(place: Place, around: LocalDate, timeOfDayJdOffset: Double): List<MonthBlock> {
    val zone = place.zone
    val first = YearMonth.from(around).minusMonths(1)
    return (0L..2L).map { m ->
        val ym = first.plusMonths(m)
        val rows = (1..ym.lengthOfMonth()).map { dayNo ->
            val d = ym.atDay(dayNo)
            val md = AstroService.moonDay(place, d)
            val (s, _) = AstroService.dayBounds(d, zone)
            val ref = AstroService.moonReferenceJd(md, s + timeOfDayJdOffset)
            val st = AstroService.moonStats(ref)
            val ph = AstroService.phaseForDay(place, d, ref)
            TableRow(
                d,
                listOf(
                    Fmt.time(md.rise?.jd, zone) to Fmt.deg(md.rise?.azimuth),
                    Fmt.time(md.set?.jd, zone) to Fmt.deg(md.set?.azimuth),
                    ph.name to "${Fmt.km(st.distanceKm)}/${Fmt.number(st.ageDays)}d/${Fmt.percent(st.illumination)}"
                ),
                st.elongation
            )
        }
        MonthBlock(ym, rows)
    }
}

@Composable
private fun MonthHeader(ym: YearMonth, titles: List<Pair<String, String>>, weights: List<Float>) {
    Row(Modifier.fillMaxWidth().background(AppColors.TableHeader).padding(horizontal = 6.dp, vertical = 2.dp)) {
        Column(Modifier.weight(0.6f)) {
            Text(ym.month.getDisplayName(TextStyle.FULL_STANDALONE, Locale.GERMAN), color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Clip)
            Text(ym.year.toString(), color = Color.White, fontSize = 15.sp)
        }
        titles.forEachIndexed { i, (a, b) ->
            Column(Modifier.weight(weights[i]), horizontalAlignment = Alignment.End) {
                Text(a, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(b, color = Color.White, fontSize = 15.sp, maxLines = 1)
            }
        }
    }
}

@Composable
private fun DataRow(row: TableRow, weights: List<Float>, selected: Boolean, southern: Boolean, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(if (selected) AppColors.Highlight else Color.White).clickable(onClick = onClick)) {
        Row(Modifier.fillMaxWidth().padding(start = 6.dp, end = 4.dp, top = 3.dp, bottom = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(0.6f)) {
                Text("${row.date.dayOfMonth}.", color = Color.Black, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text(weekdayShort(row.date), color = Color.Black, fontSize = 15.sp)
            }
            row.cells.forEachIndexed { i, (a, b) ->
                Column(Modifier.weight(weights[i]), horizontalAlignment = Alignment.End) {
                    Text(a, color = Color.Black, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, textAlign = TextAlign.End)
                    Text(b, color = Color.Black, fontSize = 15.sp, maxLines = 1, textAlign = TextAlign.End)
                }
            }
            row.elongation?.let {
                Box(Modifier.padding(start = 6.dp)) { MoonIcon(it, Modifier.size(42.dp), southern) }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(AppColors.Divider))
    }
}

@Composable
private fun MonthTable(
    blocks: List<MonthBlock>?,
    selected: LocalDate,
    titles: List<Pair<String, String>>,
    weights: List<Float>,
    southern: Boolean,
    onSelect: (LocalDate) -> Unit
) {
    if (blocks == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Berechne Tabelle …", color = AppColors.Yellow) }
        return
    }
    val state = rememberLazyListState()
    LaunchedEffect(blocks) {
        var index = 0
        for (b in blocks) {
            index++ // Kopfzeile
            val i = b.rows.indexOfFirst { it.date == selected }
            if (i >= 0) { state.scrollToItem((index + i - 3).coerceAtLeast(0)); break }
            index += b.rows.size
        }
    }
    LazyColumn(state = state, modifier = Modifier.fillMaxSize().background(Color.White)) {
        for (b in blocks) {
            stickyHeader(key = "h${b.month}") { MonthHeader(b.month, titles, weights) }
            items(b.rows.size, key = { "r${b.month}-$it" }) { i ->
                val r = b.rows[i]
                DataRow(r, weights, r.date == selected, southern) { onSelect(r.date) }
            }
        }
    }
}

@Composable
fun SunTablePage(vm: AppViewModel) {
    val place = vm.place
    val date = vm.date
    val ym = YearMonth.from(date)
    val blocks = rememberComputed(place, ym) { buildSunTable(place, date) }
    MonthTable(
        blocks, date,
        listOf("Aufgang" to "Azimut", "Untergang" to "Azimut", "Mittag" to "Höhe", "Tageslänge" to ""),
        listOf(0.95f, 1.05f, 0.9f, 1.35f),
        place.latitude < 0
    ) { vm.setDate(it) }
}

@Composable
fun MoonTablePage(vm: AppViewModel) {
    val place = vm.place
    val date = vm.date
    val ym = YearMonth.from(date)
    val dt = vm.dateTime
    val offset = (dt.toLocalTime().toSecondOfDay()) / 86400.0
    val blocks = rememberComputed(place, ym) { buildMoonTable(place, date, offset) }
    MonthTable(
        blocks, date,
        listOf("Aufgang" to "Azimut", "Untergang" to "Azimut", "Phase" to "Alter/Sichtbar"),
        listOf(0.8f, 0.9f, 2.1f),
        place.latitude < 0
    ) { vm.setDate(it) }
}
