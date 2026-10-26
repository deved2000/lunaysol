@file:OptIn(ExperimentalFoundationApi::class)

package io.github.lunasolcal.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.lunasolcal.astro.AstroInstant
import io.github.lunasolcal.data.Place
import io.github.lunasolcal.domain.AstroService
import io.github.lunasolcal.domain.Fmt
import io.github.lunasolcal.ui.AppViewModel
import io.github.lunasolcal.ui.components.MoonIcon
import io.github.lunasolcal.ui.components.SunIcon
import io.github.lunasolcal.ui.theme.AppColors

private class PlaceTimes(val sunRise: String, val sunSet: String, val moonRise: String, val moonSet: String)

@Composable
fun PlacesTab(vm: AppViewModel, onAddPlace: () -> Unit, onPlaceChosen: () -> Unit) {
    val date = vm.date
    val snapshot = vm.places.toList()
    val times = rememberComputed(snapshot, date) {
        snapshot.map { p ->
            // Datum gilt jeweils in der Zeitzone des Ortes
            val sd = AstroService.sunDay(p, date, full = false)
            val md = AstroService.moonDay(p, date)
            PlaceTimes(Fmt.time(sd.rise?.jd, p.zone), Fmt.time(sd.set?.jd, p.zone), Fmt.time(md.rise?.jd, p.zone), Fmt.time(md.set?.jd, p.zone))
        }
    }
    val elongation = AstroInstant(AstroService.jd(vm.epochMillis)).moonElongation()
    var renameIndex by remember { mutableIntStateOf(-1) }

    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f).fillMaxWidth().background(Color.White)) {
            items(snapshot.size) { i ->
                PlaceRow(
                    place = snapshot[i],
                    times = times?.getOrNull(i),
                    selected = i == vm.selectedIndex,
                    decimal = vm.decimalCoordinates,
                    elongation = elongation,
                    canMoveUp = i > 0,
                    canMoveDown = i < snapshot.size - 1,
                    onSelect = { vm.selectPlace(i); onPlaceChosen() },
                    onRename = { renameIndex = i },
                    onMove = { vm.movePlace(i, it) },
                    onDelete = { vm.deletePlace(i) }
                )
            }
        }
        Column(Modifier.fillMaxWidth().background(AppColors.Navy).padding(horizontal = 8.dp, vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Langes Drücken auf einen Ort für weitere Optionen", color = AppColors.Yellow, fontSize = 16.sp, textAlign = TextAlign.Center)
            Button(
                onClick = onAddPlace,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp).height(50.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.ButtonBlue, contentColor = Color.White)
            ) { Text("Ort hinzufügen", fontSize = 18.sp, fontWeight = FontWeight.Bold) }
        }
        DateBar(vm)
    }

    if (renameIndex in snapshot.indices) {
        var text by remember(renameIndex) { mutableStateOf(snapshot[renameIndex].name) }
        AlertDialog(
            onDismissRequest = { renameIndex = -1 },
            title = { Text("Ort umbenennen") },
            text = { WhiteField(text, { text = it }, "Name") },
            confirmButton = { TextButton(onClick = { vm.renamePlace(renameIndex, text); renameIndex = -1 }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { renameIndex = -1 }) { Text("Abbrechen") } }
        )
    }
}

@Composable
private fun PlaceRow(
    place: Place,
    times: PlaceTimes?,
    selected: Boolean,
    decimal: Boolean,
    elongation: Double,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onSelect: () -> Unit,
    onRename: () -> Unit,
    onMove: (Int) -> Unit,
    onDelete: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    Box {
        Column(Modifier.fillMaxWidth().combinedClickable(onClick = onSelect, onLongClick = { menu = true })) {
            Row(Modifier.fillMaxWidth().height(80.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(5.dp).fillMaxHeight().background(if (selected) AppColors.Teal else Color.Transparent))
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(place.name, color = Color.Black, fontSize = 19.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(place.subtitle, color = Color.Black, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(Fmt.coordinates(place.latitude, place.longitude, decimal), color = Color.Black, fontSize = 16.sp, maxLines = 1)
                }
                Column(Modifier.width(76.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    SunIcon(Modifier.size(24.dp))
                    Text("${times?.sunRise ?: "…"}↑", color = Color.Black, fontSize = 16.sp)
                    Text("${times?.sunSet ?: "…"}↓", color = Color.Black, fontSize = 16.sp)
                }
                Column(Modifier.width(76.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    MoonIcon(elongation, Modifier.size(24.dp), place.latitude < 0)
                    Text("${times?.moonRise ?: "…"}↑", color = Color.Black, fontSize = 16.sp)
                    Text("${times?.moonSet ?: "…"}↓", color = Color.Black, fontSize = 16.sp)
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(AppColors.Divider))
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text("Auswählen") }, onClick = { menu = false; onSelect() })
            DropdownMenuItem(text = { Text("Umbenennen") }, onClick = { menu = false; onRename() })
            if (canMoveUp) DropdownMenuItem(text = { Text("Nach oben") }, onClick = { menu = false; onMove(-1) })
            if (canMoveDown) DropdownMenuItem(text = { Text("Nach unten") }, onClick = { menu = false; onMove(1) })
            DropdownMenuItem(text = { Text("Löschen") }, onClick = { menu = false; onDelete() })
        }
    }
}
