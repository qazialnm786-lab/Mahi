package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val FuturisticDarkColorScheme = darkColorScheme(
    primary = CyberCyan,
    onPrimary = ObsidianDark,
    secondary = NeonPink,
    onSecondary = ObsidianDark,
    tertiary = ElectricPurple,
    onTertiary = TextPrimary,
    background = ObsidianDark,
    onBackground = TextPrimary,
    surface = SurfaceDark,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = TextSecondary
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Default to futuristic dark
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = FuturisticDarkColorScheme,
        typography = Typography,
        content = content
    )
}
