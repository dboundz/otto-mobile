package to.ottomot.driftd.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val OttoDarkScheme =
    darkColorScheme(
        primary = OttoPrimary,
        onPrimary = OttoOnPrimary,
        primaryContainer = OttoPrimaryContainer,
        onPrimaryContainer = OttoOnPrimaryContainer,
        secondary = OttoSecondary,
        onSecondary = OttoOnSecondary,
        secondaryContainer = OttoSecondaryContainer,
        onSecondaryContainer = OttoOnSecondaryContainer,
        background = OttoBackground,
        onBackground = OttoOnBackground,
        surface = OttoSurface,
        onSurface = OttoOnSurface,
        surfaceVariant = OttoSurfaceContainerHigh,
        onSurfaceVariant = OttoOnSurfaceVariant,
        surfaceDim = OttoSurfaceDim,
        surfaceBright = OttoSurfaceBright,
        surfaceContainerLowest = OttoSurfaceContainerLowest,
        surfaceContainerLow = OttoSurfaceContainerLow,
        surfaceContainer = OttoSurfaceContainer,
        surfaceContainerHigh = OttoSurfaceContainerHigh,
        surfaceContainerHighest = OttoSurfaceContainerHighest,
        outline = OttoOutline,
        outlineVariant = OttoOutlineVariant,
        error = OttoError,
        onError = OttoOnError,
        errorContainer = OttoErrorContainer,
        onErrorContainer = OttoOnErrorContainer,
    )

@Composable
fun OttoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = OttoDarkScheme,
        typography = ottoTypography(),
        shapes = OttoShapes,
        content = content,
    )
}
