package io.github.lunasolcal.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Farbpalette nach den Design-Screenshots. */
object AppColors {
    val TopBar = Color(0xFF222222)
    val Navy = Color(0xFF02021E)
    val Card = Color(0xFF000000)
    val Yellow = Color(0xFFFFFF00)
    val LightYellow = Color(0xFFFFFF8D)
    val GraphBlue = Color(0xFF0000FF)
    val Green = Color(0xFF00FF00)
    val Highlight = Color(0xFF5CEAEA)
    val ButtonBlue = Color(0xFF0000E0)
    val Teal = Color(0xFF00A88F)
    val MenuBackground = Color(0xFF02021E)
    val TableHeader = Color(0xFF000000)
    val Divider = Color(0xFFCCCCCC)
}

private val scheme = darkColorScheme(
    primary = AppColors.Yellow,
    onPrimary = Color.Black,
    secondary = AppColors.Teal,
    background = AppColors.Navy,
    surface = AppColors.MenuBackground,
    onSurface = Color.White,
    onBackground = Color.White,
    surfaceContainer = AppColors.MenuBackground,
    surfaceContainerHigh = Color(0xFF14142E)
)

@Composable
fun LunaSolCalTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}
