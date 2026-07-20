package com.robcloud.bloodpressure.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = TealPrimaryLight,
    primaryContainer = TealPrimaryContainerLight,
    secondary = BlueSecondaryLight,
    secondaryContainer = BlueSecondaryContainerLight,
    background = BackgroundLight,
    surface = SurfaceLight
)

private val DarkColors = darkColorScheme(
    primary = TealPrimaryDark,
    primaryContainer = TealPrimaryContainerDark,
    secondary = BlueSecondaryDark,
    secondaryContainer = BlueSecondaryContainerDark,
    background = BackgroundDark,
    surface = SurfaceDark
)

private val ConsoleColors = darkColorScheme(
    primary = ConsoleGreen,
    onPrimary = Color.Black,
    primaryContainer = ConsoleGreenDark,
    onPrimaryContainer = ConsoleGreen,
    secondary = ConsoleGreenDim,
    onSecondary = Color.Black,
    secondaryContainer = ConsoleGreenDark,
    onSecondaryContainer = ConsoleGreen,
    background = ConsoleBackground,
    onBackground = ConsoleGreen,
    surface = ConsoleSurface,
    onSurface = ConsoleGreen,
    surfaceVariant = ConsoleSurfaceVariant,
    onSurfaceVariant = ConsoleGreenDim,
    outline = ConsoleGreenDim,
    error = ConsoleRed
)

data class ChartColors(
    val systolic: Color,
    val diastolic: Color,
    val heartRate: Color
)

val LocalChartColors = staticCompositionLocalOf {
    ChartColors(ChartSystolic, ChartDiastolic, ChartHeartRate)
}

@Composable
fun BloodPressureTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val context = LocalContext.current

    val colorScheme = when (themeMode) {
        ThemeMode.CONSOLE -> ConsoleColors
        ThemeMode.LIGHT -> LightColors
        ThemeMode.DARK -> DarkColors
        ThemeMode.SYSTEM ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (systemDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            } else {
                if (systemDark) DarkColors else LightColors
            }
    }

    val isDark = when (themeMode) {
        ThemeMode.CONSOLE, ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> systemDark
    }

    val chartColors = when {
        themeMode == ThemeMode.CONSOLE -> ChartColors(ConsoleGreen, ConsoleCyan, ConsoleRed)
        isDark -> ChartColors(ChartSystolicDark, ChartDiastolicDark, ChartHeartRate)
        else -> ChartColors(ChartSystolic, ChartDiastolic, ChartHeartRate)
    }

    val typography = if (themeMode == ThemeMode.CONSOLE) MonoTypography else Typography

    CompositionLocalProvider(LocalChartColors provides chartColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            content = content
        )
    }
}
