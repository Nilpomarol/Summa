package com.gestorfinances.app.ui.theme

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import com.gestorfinances.app.R

// Newsreader (editorial serif) for titles and hero amounts, Inter for the interface and list
// figures. Loaded as downloadable Google Fonts; if the provider/network is unavailable the
// platform default is used as a graceful fallback (tabular numerals on figures still apply via
// fontFeatureSettings = "tnum").
private val googleFontProvider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs,
)

private fun googleFamily(name: String): FontFamily {
    val font = GoogleFont(name)
    return FontFamily(
        Font(googleFont = font, fontProvider = googleFontProvider, weight = FontWeight.Normal),
        Font(googleFont = font, fontProvider = googleFontProvider, weight = FontWeight.Medium),
    )
}

/** Inter — interface typeface, and list/table figures. */
internal val InterfaceFontFamily: FontFamily = googleFamily("Inter")

/** Newsreader — screen titles, section titles, and hero amounts. */
internal val DisplayFontFamily: FontFamily = googleFamily("Newsreader")
