package io.github.lunasolcal.ui.screens

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.lunasolcal.AppInfo
import io.github.lunasolcal.domain.AstroService
import io.github.lunasolcal.domain.Fmt
import io.github.lunasolcal.ui.AppViewModel
import io.github.lunasolcal.ui.theme.AppColors
import java.time.ZoneId

private enum class Dialog { NONE, TIMEZONE, SETTINGS, PRIVACY, ABOUT }

@Composable
fun MainScreen(vm: AppViewModel) {
    val context = LocalContext.current
    var tab by remember { mutableIntStateOf(0) }
    var showAddPlace by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf(Dialog.NONE) }

    val overviewPager = rememberPagerState { 2 }
    val sunPager = rememberPagerState { 4 }
    val moonPager = rememberPagerState { 3 }

    // Meldungen als Toast
    LaunchedEffect(vm.message) {
        vm.message?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show(); vm.message = null }
    }

    // GPS: Berechtigung anfragen, dann Position holen
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted.values.any { it }) {
            vm.gpsStarted()
            io.github.lunasolcal.data.GeoService.requestLocation(context) { vm.onGpsLocation(it) }
        } else vm.message = "Standortberechtigung verweigert"
    }
    val startGps = {
        permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }

    // Import/Export über das Android-Dateisystem (Storage Access Framework)
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { it.write(vm.exportJson().toByteArray()) }
                vm.message = "Orte exportiert"
            } catch (e: Exception) { vm.message = "Export fehlgeschlagen" }
        }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } ?: ""
                vm.importJson(text)
            } catch (e: Exception) { vm.message = "Import fehlgeschlagen" }
        }
    }

    if (showAddPlace) {
        AddPlaceScreen(vm) { showAddPlace = false; tab = 3 }
        return
    }

    Column(Modifier.fillMaxSize().background(AppColors.Navy)) {
        // ---- App-Leiste
        Column(Modifier.fillMaxWidth().background(AppColors.TopBar).statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Luna y Sol", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 20.dp).weight(1f))
                BarIcon(Icons.Filled.Share, "Teilen", Color.White) { share(context, vm) }
                BarIcon(Icons.Filled.Update, "Jetzt", if (vm.followNow) Color.White else AppColors.Yellow) { vm.resetToNow() }
                Box {
                    BarIcon(Icons.Filled.MoreVert, "Menü", Color.White) { menuOpen = true }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }, modifier = Modifier.background(AppColors.MenuBackground)) {
                        fun item(text: String, action: () -> Unit) = Pair(text, action)
                        val items = listOf(
                            item("Position aktual. (GPS)") { startGps() },
                            item("Orte importieren") { importer.launch(arrayOf("application/json", "text/plain", "*/*")) },
                            item("Orte exportieren") { exporter.launch("lunaysol-orte.json") },
                            item("Datum") { pickDateTime(context, vm) },
                            item("Zeitzone") { dialog = Dialog.TIMEZONE },
                            item("Einstellungen") { dialog = Dialog.SETTINGS },
                            item("Glossar (Online)") { openUrl(context, AppInfo.GLOSSARY_URL) },
                            item("FAQ (Online)") { openUrl(context, AppInfo.FAQ_URL) },
                            item("Datenschutz") { dialog = Dialog.PRIVACY },
                            item("Über") { dialog = Dialog.ABOUT }
                        )
                        items.forEach { (text, action) ->
                            DropdownMenuItem(
                                text = { Text(text, color = Color.White, fontSize = 20.sp, modifier = Modifier.padding(vertical = 8.dp)) },
                                onClick = { menuOpen = false; action() }
                            )
                        }
                    }
                }
            }
            TabStrip(listOf("ÜBERBLICK", "SONNE", "MOND", "ORTE"), tab) { tab = it }
        }

        // ---- Inhalt
        Box(Modifier.weight(1f).fillMaxWidth().navigationBarsPadding()) {
            when (tab) {
                0 -> OverviewTab(vm, overviewPager, onGps = startGps)
                1 -> SunTab(vm, sunPager)
                2 -> MoonTab(vm, moonPager)
                else -> PlacesTab(vm, onAddPlace = { showAddPlace = true }, onPlaceChosen = { tab = 0 })
            }
        }
    }

    when (dialog) {
        Dialog.TIMEZONE -> TimezoneDialog(vm) { dialog = Dialog.NONE }
        Dialog.SETTINGS -> SettingsDialog(vm) { dialog = Dialog.NONE }
        Dialog.PRIVACY -> InfoDialog("Datenschutz", AppInfo.PRIVACY_TEXT) { dialog = Dialog.NONE }
        Dialog.ABOUT -> InfoDialog("Über Luna y Sol", AppInfo.aboutText()) { dialog = Dialog.NONE }
        Dialog.NONE -> {}
    }
}

@Composable
private fun BarIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, tint: Color, onClick: () -> Unit) {
    Box(Modifier.size(56.dp).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, description, tint = tint, modifier = Modifier.size(30.dp))
    }
}

private fun pickDateTime(context: Context, vm: AppViewModel) {
    val dt = vm.dateTime
    DatePickerDialog(context, { _, y, m, d ->
        val date = java.time.LocalDate.of(y, m + 1, d)
        TimePickerDialog(context, { _, h, min ->
            vm.setDateTime(date.atTime(h, min).atZone(vm.zone))
        }, dt.hour, dt.minute, true).show()
    }, dt.year, dt.monthValue - 1, dt.dayOfMonth).show()
}

private fun openUrl(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (e: Exception) {
        Toast.makeText(context, url, Toast.LENGTH_LONG).show()
    }
}

private fun share(context: Context, vm: AppViewModel) {
    val p = vm.place
    val zone = p.zone
    val date = vm.date
    val sd = AstroService.sunDay(p, date, full = false)
    val md = AstroService.moonDay(p, date)
    val ref = AstroService.moonReferenceJd(md, AstroService.jd(vm.epochMillis))
    val st = AstroService.moonStats(ref)
    val ph = AstroService.phaseForDay(p, date, ref)
    val text = buildString {
        appendLine("${p.name} (${Fmt.coordinates(p.latitude, p.longitude)})")
        appendLine(Fmt.dateLong(date))
        appendLine("Sonnenaufgang: ${Fmt.time(sd.rise?.jd, zone)}")
        appendLine("Sonnenuntergang: ${Fmt.time(sd.set?.jd, zone)}")
        appendLine("Tageslänge: ${Fmt.duration(sd.dayLengthSeconds)}")
        appendLine("Mondaufgang: ${Fmt.time(md.rise?.jd, zone)}")
        appendLine("Monduntergang: ${Fmt.time(md.set?.jd, zone)}")
        appendLine("Mondphase: ${ph.name} (${Fmt.percent(st.illumination)})")
        append("— Luna y Sol")
    }
    val intent = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }
    context.startActivity(Intent.createChooser(intent, "Teilen"))
}

@Composable
private fun InfoDialog(title: String, text: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { LazyColumn(Modifier.heightIn(max = 420.dp)) { item { Text(text) } } },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } }
    )
}

@Composable
private fun SettingsDialog(vm: AppViewModel, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Einstellungen") },
        text = {
            Column {
                SettingSwitch("Koordinaten in Dezimalgrad", vm.decimalCoordinates) { vm.updateDecimalCoordinates(it) }
                SettingSwitch("Online-Dienste (Ortssuche, Zeitzone)", vm.onlineLookups) { vm.updateOnlineLookups(it) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } }
    )
}

@Composable
private fun SettingSwitch(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = value, onCheckedChange = onChange)
    }
}

@Composable
private fun TimezoneDialog(vm: AppViewModel, onDismiss: () -> Unit) {
    var filter by remember { mutableStateOf("") }
    val all = remember { ZoneId.getAvailableZoneIds().filter { it.contains('/') && !it.startsWith("SystemV") }.sorted() }
    val shown = all.filter { filter.isBlank() || it.contains(filter.trim(), ignoreCase = true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Zeitzone für „${vm.place.name}“") },
        text = {
            Column {
                Text("Aktuell: ${vm.place.zoneId}", fontSize = 14.sp)
                Spacer(Modifier.height(6.dp))
                WhiteField(filter, { filter = it }, "Suchen, z. B. Vienna", Modifier.fillMaxWidth(), fontSize = 16)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Automatisch ermitteln (online)",
                    color = AppColors.Yellow,
                    modifier = Modifier.fillMaxWidth().clickable { vm.detectZoneForSelected(); onDismiss() }.padding(vertical = 8.dp)
                )
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(shown.size) { i ->
                        val id = shown[i]
                        Text(
                            id,
                            color = if (id == vm.place.zoneId) AppColors.Yellow else Color.White,
                            modifier = Modifier.fillMaxWidth().clickable { vm.setZoneForSelected(id); onDismiss() }.padding(vertical = 8.dp)
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Schließen") } }
    )
}
