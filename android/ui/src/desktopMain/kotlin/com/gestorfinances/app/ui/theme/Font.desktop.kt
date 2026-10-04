package com.gestorfinances.app.ui.theme

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font

// Bundled variable fonts (resources/font, SIL Open Font License); Android downloads the same
// families from Google Fonts instead.
// One file per weight, cut from the variable fonts a step heavier than on the phone (450 for
// Normal, 600 and 580 for Medium): this Compose cannot pick a weight inside a variable file, and
// on a monitor the faces read thinner than on the phone's denser screen.
private fun bundledFamily(name: String): FontFamily =
    FontFamily(
        Font(resource = "font/$name-Normal.ttf", weight = FontWeight.Normal),
        Font(resource = "font/$name-Medium.ttf", weight = FontWeight.Medium),
    )

actual val InterfaceFontFamily: FontFamily = bundledFamily("Inter")

actual val DisplayFontFamily: FontFamily = bundledFamily("Newsreader")
