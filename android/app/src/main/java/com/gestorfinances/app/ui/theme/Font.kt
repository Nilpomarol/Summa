package com.gestorfinances.app.ui.theme

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import com.gestorfinances.app.R

// Geist for the interface, IBM Plex Mono for ledger figures.
// Loaded as downloadable Google Fonts; if the provider/network is unavailable the
// platform default is used as a graceful fallback (tabular numerals on figures still
// apply via fontFeatureSettings = "tnum" in the typography).
private val googleFontProvider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs,
)

private val geist = GoogleFont("Geist")
private val ibmPlexMono = GoogleFont("IBM Plex Mono")

private fun googleFamily(font: GoogleFont): FontFamily =
    FontFamily(
        Font(googleFont = font, fontProvider = googleFontProvider, weight = FontWeight.Normal),
        Font(googleFont = font, fontProvider = googleFontProvider, weight = FontWeight.Medium),
        Font(googleFont = font, fontProvider = googleFontProvider, weight = FontWeight.SemiBold),
    )

/** Geist — interface typeface. */
internal val GeistFontFamily: FontFamily = googleFamily(geist)

/** IBM Plex Mono — ledger figures, used with tabular numerals. */
internal val LedgerMonoFontFamily: FontFamily = googleFamily(ibmPlexMono)
