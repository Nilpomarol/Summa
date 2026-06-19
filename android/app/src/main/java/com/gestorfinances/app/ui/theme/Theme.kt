package com.gestorfinances.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

@Composable
fun GestorFinancesTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = GestorLightColorScheme,
        typography = GestorTypography,
        shapes = GestorShapes,
        content = content,
    )
}
