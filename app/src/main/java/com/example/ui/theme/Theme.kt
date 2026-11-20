package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

private val FfDarkColorScheme = darkColorScheme(
    primary = NeonCrimson,
    onPrimary = TextPrimary,
    primaryContainer = DeepCrimson,
    onPrimaryContainer = TextPrimary,
    secondary = ElectricPurple,
    onSecondary = TextPrimary,
    secondaryContainer = DeepViolet,
    onSecondaryContainer = NeonViolet,
    tertiary = VerifiedBlue,
    onTertiary = PitchBlack,
    background = PitchBlack,
    onBackground = TextPrimary,
    surface = ObsidianSurface,
    onSurface = TextPrimary,
    surfaceVariant = CyberCard,
    onSurfaceVariant = TextSecondary,
    surfaceContainerLowest = PitchBlack,
    surfaceContainerLow = ObsidianSurface,
    surfaceContainer = CyberCard,
    surfaceContainerHigh = CyberCardElevated,
    outline = DividerDark,
    outlineVariant = DividerDark,
    error = NeonCrimsonGlow,
    onError = PitchBlack
)

val FfShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun FfCreatorTheme(
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            window?.let {
                WindowCompat.getInsetsController(it, view).apply {
                    isAppearanceLightStatusBars = false
                    isAppearanceLightNavigationBars = false
                }
            }
        }
    }

    // Enforce Arabic RTL layout direction cleanly across all screens
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(
            colorScheme = FfDarkColorScheme,
            typography = FfTypography,
            shapes = FfShapes,
            content = content
        )
    }
}
