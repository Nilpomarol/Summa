package com.gestorfinances.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The few colours a miniature of the app needs, for either theme regardless of the one showing:
 * the theme picker draws light and dark side by side.
 */
data class ThemePreviewColors(
    val page: Color,
    val card: Color,
    val border: Color,
    val ink: Color,
    val muted: Color,
    val accent: Color,
)

fun themePreviewColors(dark: Boolean): ThemePreviewColors =
    if (dark) {
        ThemePreviewColors(
            page = TokenColor.Dark0,
            card = TokenColor.Dark50,
            border = TokenColor.Dark150,
            ink = TokenColor.Dark900,
            muted = TokenColor.Dark400,
            accent = TokenColor.SageLight,
        )
    } else {
        ThemePreviewColors(
            page = TokenColor.Page,
            card = TokenColor.Card,
            border = TokenColor.Border,
            ink = TokenColor.Ink,
            muted = TokenColor.BorderStrong,
            accent = TokenColor.Forest,
        )
    }
