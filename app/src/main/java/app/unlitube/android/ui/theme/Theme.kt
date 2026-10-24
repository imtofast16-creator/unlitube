package app.unlitube.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// UnliTube premium palette. Violet is the single brand accent, used only for
// active states, progress and the logo. Coral is reserved for LIVE badges.
// Everything else is quiet charcoal with one soft surface step, so video
// thumbnails carry the color instead of the chrome around them.
val UnliCharcoal = Color(0xFF0E0E13)
val UnliSurface = Color(0xFF17171F)
val UnliSurfaceVariant = Color(0xFF1F1F2A)
val UnliOutline = Color(0xFF2B2B3A)
val UnliViolet = Color(0xFF8B5CF6)
val UnliCoral = Color(0xFFFF5A5F)
val UnliTextPrimary = Color(0xFFF6F6F8)
val UnliTextSecondary = Color(0xFFA2A2B0)

private val DarkColors = darkColorScheme(
    primary = UnliViolet,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF2A2140),
    secondary = UnliCoral,
    background = UnliCharcoal,
    surface = UnliSurface,
    surfaceVariant = UnliSurfaceVariant,
    surfaceContainer = UnliSurfaceVariant,
    outline = UnliOutline,
    outlineVariant = UnliOutline,
    onBackground = UnliTextPrimary,
    onSurface = UnliTextPrimary,
    onSurfaceVariant = UnliTextSecondary
)

private val AmoledColors = darkColorScheme(
    primary = UnliViolet,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF1A1430),
    secondary = UnliCoral,
    background = Color.Black,
    surface = Color(0xFF050507),
    surfaceVariant = Color(0xFF0E0E13),
    surfaceContainer = Color(0xFF0E0E13),
    outline = Color(0xFF23232E),
    outlineVariant = Color(0xFF23232E),
    onBackground = UnliTextPrimary,
    onSurface = UnliTextPrimary,
    onSurfaceVariant = UnliTextSecondary
)

private val LightColors = lightColorScheme(
    primary = UnliViolet,
    onPrimary = Color.White,
    secondary = UnliCoral,
    background = Color(0xFFFAFAFC),
    surface = Color.White,
    surfaceVariant = Color(0xFFECECF1),
    surfaceContainer = Color(0xFFECECF1),
    outline = Color(0xFFE0E0E8),
    onBackground = Color(0xFF131318),
    onSurface = Color(0xFF131318),
    onSurfaceVariant = Color(0xFF6B6B78)
)

@Composable
fun UnliTubeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    amoled: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = when {
            darkTheme && amoled -> AmoledColors
            darkTheme -> DarkColors
            else -> LightColors
        },
        content = content
    )
}
