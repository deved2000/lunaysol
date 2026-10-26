package io.github.lunasolcal.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.lunasolcal.data.GeoService
import io.github.lunasolcal.data.Place
import io.github.lunasolcal.ui.AppViewModel
import io.github.lunasolcal.ui.components.HSpace
import io.github.lunasolcal.ui.theme.AppColors
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.floor

/** Weißes Eingabefeld im Stil der Screenshots. */
@Composable
fun WhiteField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Done,
    onAction: () -> Unit = {},
    fontSize: Int = 20,
    trailing: (@Composable () -> Unit)? = null
) {
    var focused by remember { mutableStateOf(false) }
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(color = Color.Black, fontSize = fontSize.sp),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        keyboardActions = KeyboardActions(onDone = { onAction() }, onSearch = { onAction() }, onNext = { onAction() }),
        modifier = modifier.onFocusChanged { focused = it.isFocused },
        decorationBox = { inner ->
            Row(
                Modifier
                    .background(Color.White, RoundedCornerShape(6.dp))
                    .border(2.dp, if (focused) Color(0xFFE0A030) else Color(0xFFBBBBBB), RoundedCornerShape(6.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) Text(placeholder, color = Color.Gray, fontSize = fontSize.sp, maxLines = 1)
                    inner()
                }
                trailing?.invoke()
            }
        }
    )
}

@Composable
fun AddPlaceScreen(vm: AppViewModel, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    var tab by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().background(AppColors.Navy).imePadding()) {
        Column(Modifier.fillMaxWidth().background(AppColors.TopBar).statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(56.dp).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück", tint = Color.White)
                }
                HSpace(16)
                Text("Ort hinzufügen", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Medium)
            }
            TabStrip(listOf("STADT SUCHEN", "GEO KOORDINATEN"), tab) { tab = it }
        }
        if (tab == 0) SearchTab(vm, onClose) else CoordinatesTab(vm, onClose)
    }
}

@Composable
private fun SearchTab(vm: AppViewModel, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    var query by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var results by remember { mutableStateOf<List<GeoService.SearchResult>>(emptyList()) }
    val doSearch = {
        if (query.isNotBlank()) {
            focus.clearFocus()
            busy = true; error = null
            scope.launch {
                try {
                    results = GeoService.search(query)
                    if (results.isEmpty()) error = "Keine Treffer"
                } catch (e: Exception) {
                    error = "Suche nicht möglich (Internetverbindung?)"
                }
                busy = false
            }
        }
    }
    Column(Modifier.fillMaxSize().padding(6.dp)) {
        WhiteField(
            query, { query = it }, "Stadt oder Ort eingeben",
            Modifier.fillMaxWidth(), imeAction = ImeAction.Search, onAction = doSearch,
            trailing = { Icon(Icons.Filled.Search, "Suchen", tint = Color.Black, modifier = Modifier.size(30.dp).clickable { doSearch() }) }
        )
        if (!vm.onlineLookups) Text("Online-Suche ist in den Einstellungen deaktiviert – nutze GEO KOORDINATEN.", color = AppColors.LightYellow, modifier = Modifier.padding(8.dp))
        if (busy) Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = AppColors.Yellow) }
        error?.let { Text(it, color = AppColors.LightYellow, fontSize = 16.sp, modifier = Modifier.padding(10.dp)) }
        LazyColumn(Modifier.fillMaxSize()) {
            items(results.size) { i ->
                val r = results[i]
                Column(
                    Modifier.fillMaxWidth().clickable {
                        vm.addPlace(Place(r.name, r.subtitle, r.latitude, r.longitude, r.timezone))
                        onClose()
                    }.padding(horizontal = 8.dp, vertical = 10.dp)
                ) {
                    Text(r.name, color = AppColors.Yellow, fontSize = 20.sp)
                    Text(r.subtitle, color = AppColors.LightYellow, fontSize = 15.sp)
                    Text("${io.github.lunasolcal.domain.Fmt.coordinates(r.latitude, r.longitude)}  ·  ${r.timezone}", color = Color(0xFFBBBBBB), fontSize = 14.sp)
                }
            }
        }
    }
}

private fun splitDms(v: Double): Triple<String, String, String> {
    var a = abs(v)
    val d = floor(a).toInt(); a = (a - d) * 60
    val m = floor(a).toInt(); val s = (a - m) * 60
    return Triple(d.toString(), m.toString(), String.format(java.util.Locale.GERMANY, "%.2f", s))
}

private fun parseNum(s: String): Double? = s.trim().replace(',', '.').ifEmpty { "0" }.toDoubleOrNull()

@Composable
private fun CoordinatesTab(vm: AppViewModel, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    val current = vm.place
    val lat0 = splitDms(current.latitude)
    val lon0 = splitDms(current.longitude)
    var name by remember { mutableStateOf("") }
    var latD by remember { mutableStateOf(lat0.first) }
    var latM by remember { mutableStateOf(lat0.second) }
    var latS by remember { mutableStateOf(lat0.third) }
    var north by remember { mutableStateOf(current.latitude >= 0) }
    var lonD by remember { mutableStateOf(lon0.first) }
    var lonM by remember { mutableStateOf(lon0.second) }
    var lonS by remember { mutableStateOf(lon0.third) }
    var east by remember { mutableStateOf(current.longitude >= 0) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun submit() {
        val ld = parseNum(latD); val lm = parseNum(latM); val ls = parseNum(latS)
        val od = parseNum(lonD); val om = parseNum(lonM); val os = parseNum(lonS)
        if (ld == null || lm == null || ls == null || od == null || om == null || os == null) {
            error = "Bitte gültige Zahlen eingeben"; return
        }
        val lat = (abs(ld) + lm / 60 + ls / 3600) * if (north) 1 else -1
        val lon = (abs(od) + om / 60 + os / 3600) * if (east) 1 else -1
        if (abs(lat) > 90 || abs(lon) > 180) { error = "Koordinaten außerhalb des gültigen Bereichs"; return }
        busy = true
        scope.launch {
            val tz = (if (vm.onlineLookups) GeoService.timezoneFor(lat, lon) else null) ?: GeoService.fallbackZone(lon).id
            vm.addPlace(Place(name.trim(), "", lat, lon, tz))
            busy = false
            onClose()
        }
    }

    Column(Modifier.fillMaxSize().padding(6.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Name:", color = AppColors.LightYellow, fontSize = 22.sp, modifier = Modifier.width(130.dp))
            WhiteField(name, { name = it }, "", Modifier.weight(1f))
        }
        DmsRow("Breitengrad:", latD, { latD = it }, latM, { latM = it }, latS, { latS = it }, if (north) "N" else "S") { north = !north }
        DmsRow("Längengrad:", lonD, { lonD = it }, lonM, { lonM = it }, lonS, { lonS = it }, if (east) "O" else "W") { east = !east }
        Text("Tipp: N/S bzw. O/W antippen zum Umschalten. Dezimalgrad: alles in das Grad-Feld eintragen, Minuten/Sekunden auf 0.", color = Color(0xFFAAAAAA), fontSize = 13.sp)
        error?.let { Text(it, color = Color(0xFFFF8080), fontSize = 16.sp) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Button(
                enabled = !busy,
                onClick = { submit() },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.ButtonBlue, contentColor = Color.White)
            ) { Text(if (busy) "…" else "Hinzufügen", fontSize = 18.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun DmsRow(label: String, d: String, onD: (String) -> Unit, m: String, onM: (String) -> Unit, s: String, onS: (String) -> Unit, hemi: String, onHemi: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = AppColors.LightYellow, fontSize = 22.sp, modifier = Modifier.width(130.dp))
        WhiteField(d, onD, "", Modifier.weight(1.2f), KeyboardType.Number, ImeAction.Next, fontSize = 18)
        Text("°", color = AppColors.Yellow, fontSize = 18.sp, modifier = Modifier.padding(horizontal = 3.dp))
        WhiteField(m, onM, "", Modifier.weight(1f), KeyboardType.Number, ImeAction.Next, fontSize = 18)
        Text("'", color = AppColors.Yellow, fontSize = 18.sp, modifier = Modifier.padding(horizontal = 3.dp))
        WhiteField(s, onS, "", Modifier.weight(1.3f), KeyboardType.Decimal, ImeAction.Next, fontSize = 18)
        Text("\"", color = AppColors.Yellow, fontSize = 18.sp, modifier = Modifier.padding(horizontal = 3.dp))
        Box(
            Modifier.width(44.dp).height(42.dp).background(Color.White, RoundedCornerShape(6.dp)).clickable(onClick = onHemi),
            contentAlignment = Alignment.Center
        ) { Text(hemi, color = Color.Black, fontSize = 18.sp) }
    }
}

/** Einfache Reiterleiste (Großbuchstaben, türkise Unterstreichung). */
@Composable
fun TabStrip(titles: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().height(56.dp)) {
        titles.forEachIndexed { i, t ->
            if (i > 0) Box(Modifier.width(1.dp).height(40.dp).align(Alignment.CenterVertically).background(Color(0xFF555555)))
            Column(Modifier.weight(1f).height(56.dp).clickable { onSelect(i) }, horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(t, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                }
                Box(Modifier.fillMaxWidth().height(3.dp).background(if (i == selected) AppColors.Teal else Color.Transparent))
            }
        }
    }
}
