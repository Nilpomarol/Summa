package com.gestorfinances.app.ui.theme

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import com.gestorfinances.app.R

// Schibsted Grotesk for the interface, JetBrains Mono for ledger figures — a clean, confident
// Nordic-newspaper grotesque with a precise coding monospace for figures. Note: JetBrains Mono's
// zero is dotted by default (its built-in disambiguation from "O"); if that reads as a stray mark
// on money figures, swap LedgerMonoFontFamily to a plain-zero mono (Red Hat Mono, Martian Mono).
// Loaded as downloadable Google Fonts; if the provider/network is unavailable the
// platform default is used as a graceful fallback (tabular numerals on figures still
// apply via fontFeatureSettings = "tnum" in the typography).
private val googleFontProvider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs,
)

private val schibstedGrotesk = GoogleFont("Schibsted Grotesk")
private val jetBrainsMono = GoogleFont("JetBrains Mono")

private fun googleFamily(font: GoogleFont): FontFamily =
    FontFamily(
        Font(googleFont = font, fontProvider = googleFontProvider, weight = FontWeight.Normal),
        Font(googleFont = font, fontProvider = googleFontProvider, weight = FontWeight.Medium),
        Font(googleFont = font, fontProvider = googleFontProvider, weight = FontWeight.SemiBold),
    )

/** Schibsted Grotesk — interface typeface. */
internal val InterfaceFontFamily: FontFamily = googleFamily(schibstedGrotesk)

/** JetBrains Mono — ledger figures, used with tabular numerals. */
internal val LedgerMonoFontFamily: FontFamily = googleFamily(jetBrainsMono)
