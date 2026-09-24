package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = FloraDarkPrimary,
    onPrimary = FloraDarkOnPrimary,
    primaryContainer = FloraDarkPrimaryContainer,
    onPrimaryContainer = FloraDarkOnPrimaryContainer,
    secondary = FloraDarkSecondary,
    onSecondary = FloraDarkOnSecondary,
    secondaryContainer = FloraDarkSecondaryContainer,
    onSecondaryContainer = FloraDarkOnSecondaryContainer,
    tertiary = FloraDarkTertiary,
    onTertiary = FloraDarkOnTertiary,
    tertiaryContainer = FloraDarkTertiaryContainer,
    onTertiaryContainer = FloraDarkOnTertiaryContainer,
    background = FloraBackgroundDark,
    surface = FloraSurfaceDark,
    surfaceVariant = FloraSurfaceVariantDark,
    onBackground = Color(0xFFE1E8E2),
    onSurface = Color(0xFFE1E8E2),
    onSurfaceVariant = Color(0xFFC0CAC2)
)

private val LightColorScheme = lightColorScheme(
    primary = FloraPrimary,
    onPrimary = FloraOnPrimary,
    primaryContainer = FloraPrimaryContainer,
    onPrimaryContainer = FloraOnPrimaryContainer,
    secondary = FloraSecondary,
    onSecondary = FloraOnSecondary,
    secondaryContainer = FloraSecondaryContainer,
    onSecondaryContainer = FloraOnSecondaryContainer,
    tertiary = FloraTertiary,
    onTertiary = FloraOnTertiary,
    tertiaryContainer = FloraTertiaryContainer,
    onTertiaryContainer = FloraOnTertiaryContainer,
    background = FloraBackgroundLight,
    surface = FloraSurfaceLight,
    surfaceVariant = FloraSurfaceVariantLight,
    onBackground = Color(0xFF181D1A),
    onSurface = Color(0xFF181D1A),
    onSurfaceVariant = Color(0xFF404943)
)

@Composable
fun FloraPulseTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Use our handcrafted botanical theme for consistent branding
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
