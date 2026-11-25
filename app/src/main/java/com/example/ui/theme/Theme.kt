package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = CloudEmerald,
    onPrimary = CloudOnEmerald,
    primaryContainer = CloudEmeraldContainer,
    onPrimaryContainer = CloudOnEmeraldContainer,
    secondary = CloudCyan,
    onSecondary = CloudOnCyan,
    secondaryContainer = CloudCyanContainer,
    onSecondaryContainer = CloudOnCyanContainer,
    tertiary = CloudAmber,
    onTertiary = CloudOnAmber,
    tertiaryContainer = CloudAmberContainer,
    onTertiaryContainer = CloudAmber,
    background = CloudBgDark,
    onBackground = CloudTextPrimaryDark,
    surface = CloudSurfaceDark,
    onSurface = CloudTextPrimaryDark,
    surfaceVariant = CloudSurfaceElevatedDark,
    onSurfaceVariant = CloudTextSecondaryDark,
    surfaceContainerLowest = CloudBgDark,
    surfaceContainerLow = CloudSurfaceDark,
    surfaceContainer = CloudSurfaceElevatedDark,
    surfaceContainerHigh = CloudSurfaceSelectedDark,
    surfaceContainerHighest = CloudSurfaceSelectedDark,
    outline = CloudOutlineDark,
    outlineVariant = CloudOutlineSubtleDark,
    error = CloudError,
    onError = CloudOnError,
    errorContainer = CloudErrorContainer,
    onErrorContainer = CloudOnErrorContainer
)

private val LightColorScheme = lightColorScheme(
    primary = CloudEmeraldLight,
    onPrimary = CloudOnEmeraldLight,
    primaryContainer = CloudEmeraldContainerLight,
    onPrimaryContainer = CloudOnEmeraldContainerLight,
    secondary = CloudCyanLight,
    onSecondary = CloudOnEmeraldLight,
    secondaryContainer = CloudCyanContainerLight,
    onSecondaryContainer = CloudOnCyanContainerLight,
    tertiary = CloudAmber,
    onTertiary = CloudOnAmber,
    background = CloudBgLight,
    onBackground = CloudTextPrimaryLight,
    surface = CloudSurfaceLight,
    onSurface = CloudTextPrimaryLight,
    surfaceVariant = CloudSurfaceElevatedLight,
    onSurfaceVariant = CloudTextSecondaryLight,
    outline = CloudOutlineLight,
    error = CloudError,
    onError = CloudOnError
)

val CloudDropShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun CloudDropTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = CloudDropShapes,
        content = content
    )
}
