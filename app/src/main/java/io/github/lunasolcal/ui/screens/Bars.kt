package io.github.lunasolcal.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import io.github.lunasolcal.domain.Fmt
import io.github.lunasolcal.ui.AppViewModel
import io.github.lunasolcal.ui.components.SliderBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** Berechnet einen Wert im Hintergrund; liefert null, bis das erste Ergebnis vorliegt (danach den letzten Wert). */
@Composable
fun <T> rememberComputed(vararg keys: Any?, compute: suspend () -> T): T? {
    val state = produceState<T?>(initialValue = null, *keys) {
        value = withContext(Dispatchers.Default) { compute() }
    }
    return state.value
}

@Composable
fun DateBar(vm: AppViewModel) {
    val dt = vm.dateTime
    SliderBar(
        label = Fmt.dateLong(dt.toLocalDate()),
        value = dt.dayOfYear.toFloat(),
        range = 1f..dt.toLocalDate().lengthOfYear().toFloat(),
        onValueChange = { vm.setDayOfYear(it.roundToInt()) },
        onPrev = { vm.shiftDays(-1) },
        onNext = { vm.shiftDays(1) }
    )
}

@Composable
fun TimeBar(vm: AppViewModel) {
    val dt = vm.dateTime
    SliderBar(
        label = Fmt.time(dt),
        value = (dt.hour * 60 + dt.minute).toFloat(),
        range = 0f..1439f,
        onValueChange = { vm.setMinuteOfDay(it.roundToInt()) },
        onPrev = { vm.shiftMinutes(-1) },
        onNext = { vm.shiftMinutes(1) }
    )
}

@Composable
fun YearBar(vm: AppViewModel) {
    val year = vm.dateTime.year
    val min = minOf(1900, year).toFloat()
    val max = maxOf(2100, year).toFloat()
    SliderBar(
        label = year.toString(),
        value = year.toFloat(),
        range = min..max,
        onValueChange = { vm.setYear(it.roundToInt()) },
        onPrev = { vm.shiftYears(-1) },
        onNext = { vm.shiftYears(1) }
    )
}
