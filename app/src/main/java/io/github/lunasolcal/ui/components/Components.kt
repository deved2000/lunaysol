package io.github.lunasolcal.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.lunasolcal.ui.theme.AppColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// ------------------------------------------------------------------ Sonne & Mond zeichnen

/** Zeichnet eine Sonne mit Strahlenkranz. */
fun DrawScope.drawSun(center: Offset, radius: Float) {
    val rays = 28
    val inner = radius * 0.72f
    val path = Path()
    for (i in 0 until rays) {
        val a = 2 * PI * i / rays
        val a1 = a - PI / rays * 0.55
        val a2 = a + PI / rays * 0.55
        val tip = if (i % 2 == 0) radius else radius * 0.88f
        path.moveTo(center.x + inner * cos(a1).toFloat(), center.y + inner * sin(a1).toFloat())
        path.lineTo(center.x + tip * cos(a).toFloat(), center.y + tip * sin(a).toFloat())
        path.lineTo(center.x + inner * cos(a2).toFloat(), center.y + inner * sin(a2).toFloat())
        path.close()
    }
    drawPath(path, Brush.radialGradient(listOf(Color(0xFFFFC107), Color(0xFFFF6D00)), center, radius))
    drawCircle(
        Brush.radialGradient(
            listOf(Color(0xFFFFF59D), Color(0xFFFFD54F), Color(0xFFFF9800)),
            center = Offset(center.x - inner * 0.25f, center.y - inner * 0.3f),
            radius = inner * 1.3f
        ),
        radius = inner * 0.86f,
        center = center
    )
}

/**
 * Zeichnet den Mond mit beleuchtetem Anteil.
 * @param elongation 0 = Neumond, 90 = erstes Viertel, 180 = Vollmond, 270 = letztes Viertel
 * @param southern auf der Südhalbkugel erscheint die Mondsichel gespiegelt
 */
fun DrawScope.drawMoon(center: Offset, radius: Float, elongation: Double, southern: Boolean = false) {
    // Dunkle Seite (leichtes Erdlicht)
    drawCircle(
        Brush.radialGradient(listOf(Color(0xFF3C3C3C), Color(0xFF141414)), Offset(center.x - radius * 0.3f, center.y - radius * 0.3f), radius * 1.4f),
        radius, center
    )
    val waxing = elongation < 180.0
    val side = if (waxing xor southern) 1f else -1f
    val t = cos(elongation * PI / 180.0).toFloat()
    val n = 48
    val lit = Path()
    for (i in 0..n) {
        val th = -PI / 2 + PI * i / n
        val x = center.x + side * radius * cos(th).toFloat()
        val y = center.y + radius * sin(th).toFloat()
        if (i == 0) lit.moveTo(x, y) else lit.lineTo(x, y)
    }
    for (i in n downTo 0) {
        val th = -PI / 2 + PI * i / n
        lit.lineTo(center.x + side * t * radius * cos(th).toFloat(), center.y + radius * sin(th).toFloat())
    }
    lit.close()
    clipPath(lit) {
        drawCircle(
            Brush.radialGradient(listOf(Color(0xFFF2F2F2), Color(0xFFB8B8B8), Color(0xFF7A7A7A)), Offset(center.x - radius * 0.2f, center.y - radius * 0.25f), radius * 1.3f),
            radius, center
        )
        // „Mare“-Flecken
        val maria = floatArrayOf(-0.25f, -0.32f, 0.24f, 0.18f, -0.38f, 0.17f, 0.28f, 0.02f, 0.2f, -0.12f, 0.18f, 0.15f, -0.36f, 0.28f, 0.12f, 0.05f, 0.55f, 0.1f)
        var i = 0
        while (i < maria.size) {
            drawCircle(Color(0x55505050), maria[i + 2] * radius, Offset(center.x + maria[i] * radius, center.y + maria[i + 1] * radius))
            i += 3
        }
    }
}

@Composable
fun SunIcon(modifier: Modifier = Modifier) {
    Canvas(modifier) { drawSun(center, size.minDimension / 2) }
}

@Composable
fun MoonIcon(elongation: Double, modifier: Modifier = Modifier, southern: Boolean = false) {
    Canvas(modifier) { drawMoon(center, size.minDimension / 2 * 0.95f, elongation, southern) }
}

// ------------------------------------------------------------------ Bedienleisten

/** Weißes Dreieck, das beim Gedrückthalten wiederholt auslöst. */
@Composable
fun RepeatTriangle(pointsLeft: Boolean, onStep: () -> Unit, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val step by rememberUpdatedState(onStep)
    Canvas(
        modifier
            .size(64.dp, 52.dp)
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    val job = scope.launch {
                        step()
                        delay(450)
                        while (isActive) { step(); delay(70) }
                    }
                    tryAwaitRelease()
                    job.cancel()
                })
            }
    ) {
        val w = size.height * 0.62f
        val h = size.height * 0.62f
        val cx = size.width / 2; val cy = size.height / 2
        val p = Path()
        if (pointsLeft) {
            p.moveTo(cx - w / 2, cy); p.lineTo(cx + w / 2, cy - h / 2); p.lineTo(cx + w / 2, cy + h / 2)
        } else {
            p.moveTo(cx + w / 2, cy); p.lineTo(cx - w / 2, cy - h / 2); p.lineTo(cx - w / 2, cy + h / 2)
        }
        p.close()
        drawPath(p, Color.White)
    }
}

/** Schwarze Leiste mit Beschriftung, Schieberegler und Pfeilen (Datum, Uhrzeit, Jahr). */
@Composable
fun SliderBar(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    labelColor: Color = AppColors.Yellow,
    modifier: Modifier = Modifier
) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .background(Color.Black, RoundedCornerShape(10.dp))
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, color = labelColor, fontSize = 22.sp, textAlign = TextAlign.Center)
        Row(verticalAlignment = Alignment.CenterVertically) {
            RepeatTriangle(true, onPrev)
            Slider(
                value = value.coerceIn(range.start, range.endInclusive),
                onValueChange = onValueChange,
                valueRange = range,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.White,
                    inactiveTrackColor = Color(0xFF3A3A3A)
                )
            )
            RepeatTriangle(false, onNext)
        }
    }
}

@Composable
fun PagerDots(count: Int, current: Int, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.Center) {
        repeat(count) { i ->
            Box(
                Modifier
                    .padding(horizontal = 5.dp)
                    .size(if (i == current) 7.dp else 6.dp)
                    .background(if (i == current) Color.White else Color(0xFF6A6A6A), CircleShape)
            )
        }
    }
}

/** Kopfzeile mit Ortsname und Zeitzone (Sonne-/Mond-Reiter). */
@Composable
fun LocationHeader(name: String, zoneName: String) {
    Column(
        Modifier.fillMaxWidth().background(AppColors.Navy).padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(name, color = AppColors.LightYellow, fontSize = 20.sp)
        Text(zoneName, color = AppColors.LightYellow, fontSize = 15.sp)
    }
}

/** Beschriftung links, Wert rechts. */
@Composable
fun LabelValue(label: String, value: String, labelSize: Int = 18, valueSize: Int = 18, labelColor: Color = AppColors.LightYellow, valueColor: Color = AppColors.Yellow, indent: Int = 0) {
    Row(Modifier.fillMaxWidth().padding(start = indent.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = labelColor, fontSize = labelSize.sp, modifier = Modifier.weight(1f))
        Text(value, color = valueColor, fontSize = valueSize.sp, textAlign = TextAlign.End)
    }
}

/** Wert rechtsbündig in eigener Zeile. */
@Composable
fun RightValue(value: String, size: Int = 18, color: Color = AppColors.Yellow) {
    Text(value, color = color, fontSize = size.sp, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth())
}

@Composable
fun VSpace(h: Int) = Spacer(Modifier.height(h.dp))

@Composable
fun HSpace(w: Int) = Spacer(Modifier.width(w.dp))

@Composable
fun SectionTitle(text: String) {
    Text(text, color = AppColors.LightYellow, fontSize = 18.sp, fontWeight = FontWeight.Normal)
}
