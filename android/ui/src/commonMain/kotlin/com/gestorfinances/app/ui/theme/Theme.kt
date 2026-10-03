package com.gestorfinances.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

@Composable
fun GestorFinancesTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    val financeColors = if (darkTheme) DarkFinanceColors else LightFinanceColors
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

val LocalDarkTheme = staticCompositionLocalOf { false }

val FinanceTheme.isDark: Boolean
    @Composable
    @ReadOnlyComposable
    get() = LocalDarkTheme.current
