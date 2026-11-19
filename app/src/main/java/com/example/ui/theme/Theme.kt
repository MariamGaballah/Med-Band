package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val MedBandDarkColorScheme = darkColorScheme(
    primary = CyanPulse,
    onPrimary = NavyDeep,
    primaryContainer = NavyElevated,
    onPrimaryContainer = ClinicalWhite,
    secondary = ElectricBlueBright,
    onSecondary = Color.White,
    secondaryContainer = NavyCard,
    onSecondaryContainer = CyanSoft,
    tertiary = StatusGreen,
    onTertiary = NavyDeep,
    background = NavyDeep,
    onBackground = TextPrimaryDark,
    surface = NavySurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = NavyCard,
    onSurfaceVariant = TextSecondaryDark,
    outline = Color(0xFF28467E),
    error = EmergencyRed,
    onError = Color.White,
    errorContainer = EmergencyRedDeep,
    onErrorContainer = Color(0xFFFFDADE)
)

private val MedBandLightColorScheme = lightColorScheme(
    primary = ElectricBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD8E6FF),
    onPrimaryContainer = NavyDeep,
    secondary = Color(0xFF0097B2),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0F7FA),
    onSecondaryContainer = Color(0xFF003640),
    tertiary = StatusGreenDark,
    onTertiary = Color.White,
    background = IceBackground,
    onBackground = TextPrimaryLight,
    surface = IceSurface,
    onSurface = TextPrimaryLight,
    surfaceVariant = Color(0xFFE3EEFF),
    onSurfaceVariant = TextSecondaryLight,
    outline = IceCardBorder,
    error = EmergencyRed,
    onError = Color.White,
    errorContainer = Color(0xFFFFE5E9),
    onErrorContainer = Color(0xFF7A0C1E)
)

val MedBandShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp)
)

@Composable
fun MedBandTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) MedBandDarkColorScheme else MedBandLightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = MedBandTypography,
        shapes = MedBandShapes,
        content = content
    )
}
