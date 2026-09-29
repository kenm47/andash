package co.lunt.andash.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import co.lunt.andash.domain.Urgency

object Palette {
    val Background = Color(0xFF000000)
    val Primary = Color(0xFFFFFFFF)
    val Secondary = Color(0xFFB8B8B8)
    val Dim = Color(0xFF808080)
    val Divider = Color(0xFF262626)
    val Soon = Color(0xFFFFB300)
    val Now = Color(0xFF34D399)
    val Warning = Color(0xFFFF6B6B)

    fun accent(u: Urgency): Color = when (u) {
        Urgency.NORMAL -> Primary
        Urgency.SOON -> Soon
        Urgency.NOW -> Now
    }
}

@Composable
fun AndashTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Palette.Background,
            surface = Palette.Background,
            onBackground = Palette.Primary,
            onSurface = Palette.Primary,
            primary = Palette.Now,
        ),
        content = content,
    )
}
