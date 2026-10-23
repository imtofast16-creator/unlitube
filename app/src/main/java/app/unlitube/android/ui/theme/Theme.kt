package app.unlitube.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// UnliTube brand palette (PLAN.md Section 5)
// Base charcoal, violet to coral accent used only on play actions, progress and the logo.
val UnliCharcoal = Color(0xFF0B0B10)
val UnliSurface = Color(0xFF15151D)
val UnliSurfaceVariant = Color(0xFF1E1E28)
val UnliViolet = Color(0xFF7C4DFF)
val UnliCoral = Color(0xFFFF5A5F)
val UnliTextPrimary = Color(0xFFF5F5F7)
val UnliTextSecondary = Color(0xFF9A9AA5)

private val DarkColors = darkColorScheme(
    primary = UnliViolet,
    secondary = UnliCoral,
    background = UnliCharcoal,
    surface = UnliSurface,
    surfaceVariant = UnliSurfaceVariant,
    onBackground = UnliTextPrimary,
    onSurface = UnliTextPrimary,
    onSurfaceVariant = UnliTextSecondary
)

private val LightColors = lightColorScheme(
    primary = UnliViolet,
    secondary = UnliCoral,
    background = Color(0xFFFAFAFC),
    surface = Color.White,
    onBackground = Color(0xFF131318),
    onSurface = Color(0xFF131318)
)

@Composable
fun UnliTubeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
