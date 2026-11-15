package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val AuraDarkColorScheme = darkColorScheme(
    primary = CyanAura,
    onPrimary = Color(0xFF041E2B),
    primaryContainer = CyanAuraContainer,
    onPrimaryContainer = Color(0xFFE0F2FE),
    secondary = AmberMath,
    onSecondary = Color(0xFF2B1800),
    secondaryContainer = AmberMathContainer,
    onSecondaryContainer = Color(0xFFFEF3C7),
    tertiary = VioletMatrix,
    onTertiary = Color(0xFF1E103C),
    background = ObsidianBg,
    onBackground = InkWhite,
    surface = ObsidianSurface,
    onSurface = InkWhite,
    surfaceVariant = ObsidianSurfaceVariant,
    onSurfaceVariant = InkMuted,
    surfaceContainerHigh = ObsidianSurfaceElevated,
    outline = GridLineDark,
    error = RoseCurve,
    onError = Color.White
)

private val AuraLightColorScheme = lightColorScheme(
    primary = AzurePrimary,
    onPrimary = Color.White,
    primaryContainer = AzureContainer,
    onPrimaryContainer = Color(0xFF082F49),
    secondary = AmberLight,
    onSecondary = Color.White,
    secondaryContainer = AmberLightContainer,
    onSecondaryContainer = Color(0xFF451A03),
    tertiary = Color(0xFF6D28D9),
    onTertiary = Color.White,
    background = DaylightBg,
    onBackground = InkDark,
    surface = DaylightSurface,
    onSurface = InkDark,
    surfaceVariant = DaylightSurfaceVariant,
    onSurfaceVariant = InkMutedLight,
    surfaceContainerHigh = DaylightSurfaceElevated,
    outline = GridLineLight,
    error = Color(0xFFE11D48),
    onError = Color.White
)

@Composable
fun AuraMathTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) AuraDarkColorScheme else AuraLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AuraTypography,
        content = content
    )
}
