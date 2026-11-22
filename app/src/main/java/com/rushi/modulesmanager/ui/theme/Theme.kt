package com.rushi.modulesmanager.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

private val LightColors = lightColorScheme(
    primary = Color(0xFF6B2CC7),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFEAD7FF),
    onPrimaryContainer = Color(0xFF24005A),
    secondary = Color(0xFF64597B),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFEADDFC),
    onSecondaryContainer = Color(0xFF201634),
    tertiary = Color(0xFF814E70),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFD8EE),
    onTertiaryContainer = Color(0xFF340B29),
    background = Color(0xFFFCF8FF),
    onBackground = Color(0xFF1C1B20),
    surface = Color(0xFFFCF8FF),
    onSurface = Color(0xFF1C1B20),
    surfaceVariant = Color(0xFFE7E0EC),
    onSurfaceVariant = Color(0xFF49454F),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF6F2FA),
    surfaceContainer = Color(0xFFF0EBF5),
    surfaceContainerHigh = Color(0xFFEBE5F0),
    surfaceContainerHighest = Color(0xFFE5DFEB),
    outline = Color(0xFF7A757F),
    outlineVariant = Color(0xFFCAC4D0),
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFD2B4FF),
    onPrimary = Color(0xFF3C008D),
    primaryContainer = Color(0xFF5618B5),
    onPrimaryContainer = Color(0xFFEDDFFF),
    secondary = Color(0xFFCEBFE0),
    onSecondary = Color(0xFF352A47),
    secondaryContainer = Color(0xFF4C405E),
    onSecondaryContainer = Color(0xFFEADDFC),
    tertiary = Color(0xFFF1B3D8),
    onTertiary = Color(0xFF4B213E),
    tertiaryContainer = Color(0xFF643755),
    onTertiaryContainer = Color(0xFFFFD8EE),
    background = Color(0xFF141218),
    onBackground = Color(0xFFE6E0E9),
    surface = Color(0xFF141218),
    onSurface = Color(0xFFE6E0E9),
    surfaceVariant = Color(0xFF49454F),
    onSurfaceVariant = Color(0xFFCAC4D0),
    surfaceContainerLowest = Color(0xFF0F0D13),
    surfaceContainerLow = Color(0xFF1D1B22),
    surfaceContainer = Color(0xFF211E27),
    surfaceContainerHigh = Color(0xFF2B2832),
    surfaceContainerHighest = Color(0xFF36333D),
    outline = Color(0xFF948F99),
    outlineVariant = Color(0xFF49454F),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

val LocalAmoled = staticCompositionLocalOf { false }

/** Cards go fully black in AMOLED mode and gain a hairline outline. */
@Composable
fun amoledCardContainerColor(): Color =
    if (LocalAmoled.current) Color.Black else MaterialTheme.colorScheme.surfaceContainer

@Composable
fun amoledCardBorder(): BorderStroke? =
    if (LocalAmoled.current) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null

// Keep the active accents, including Material You ones, but turn the surfaces off.
private fun ColorScheme.withAmoledSurfaces(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF070707),
    surfaceContainer = Color(0xFF0D0D0D),
    surfaceContainerHigh = Color(0xFF151515),
    surfaceContainerHighest = Color(0xFF1E1E1E),
    surfaceDim = Color.Black,
    surfaceBright = Color(0xFF2C2C2C),
)

@Composable
fun ModulesManagerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    amoled: Boolean = false,
    content: @Composable () -> Unit,
) {
    val baseScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    val colorScheme = if (darkTheme && amoled) baseScheme.withAmoledSurfaces() else baseScheme
    val view = LocalView.current
    SideEffect {
        val activity = view.context.findActivity() ?: return@SideEffect
        WindowCompat.getInsetsController(activity.window, view).apply {
            isAppearanceLightStatusBars = !darkTheme
            isAppearanceLightNavigationBars = !darkTheme
        }
    }
    CompositionLocalProvider(LocalAmoled provides (darkTheme && amoled)) {
        MaterialTheme(colorScheme = colorScheme, content = content)
    }
}

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
