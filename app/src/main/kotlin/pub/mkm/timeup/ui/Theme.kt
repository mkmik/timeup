package pub.mkm.timeup.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext

@Composable
fun TimeUpTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val scheme = if (isSystemInDarkTheme()) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    MaterialTheme(colorScheme = scheme, content = content)
}

/** Black or white, whichever reads better on [bg]. */
fun contrastOn(bg: Color): Color = if (bg.luminance() > 0.45f) Color.Black else Color.White

/** The fixed palette priorities pick from. */
object Palette {
    val colors: List<Int> = listOf(
        0xFFE53935, // red
        0xFFFB8C00, // orange
        0xFFFDD835, // yellow
        0xFF43A047, // green
        0xFF00897B, // teal
        0xFF1E88E5, // blue
        0xFF3949AB, // indigo
        0xFF8E24AA, // purple
        0xFFD81B60, // pink
        0xFF6D4C41, // brown
    ).map { it.toInt() }
}
