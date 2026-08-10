package com.gestorfinances.app.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

@Composable
fun GestorFinancesTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    val financeColors = if (darkTheme) DarkFinanceColors else LightFinanceColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = if (darkTheme) TokenColor.Dark0.toArgb() else TokenColor.Neutral50.toArgb()
            window.navigationBarColor = if (darkTheme) TokenColor.Dark0.toArgb() else TokenColor.Neutral50.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }
    CompositionLocalProvider(
        LocalFinanceColors provides financeColors,
        LocalDarkTheme provides darkTheme,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) GestorDarkColorScheme else GestorLightColorScheme,
            typography = GestorTypography,
            shapes = GestorShapes,
            content = content,
        )
    }
}

/** Convenience accessor for the semantic finance palette, mirroring MaterialTheme. */
object FinanceTheme {
    val colors: FinanceColors
        @Composable
        @ReadOnlyComposable
        get() = LocalFinanceColors.current
}

internal val LocalDarkTheme = staticCompositionLocalOf { false }

val FinanceTheme.isDark: Boolean
    @Composable
    @ReadOnlyComposable
    get() = LocalDarkTheme.current
