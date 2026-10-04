package com.gestorfinances.app.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.roundToInt

// The same arithmetic as androidx.core's ColorUtils, on 8-bit channels, so a saved identity
// colour is adjusted to the same result on every platform.

/** Hue in degrees (0..360), saturation and lightness (0..1) of an opaque [color]. */
fun colorToHsl(color: Color): FloatArray {
    val r = (color.red * 255f).roundToInt() / 255f
    val g = (color.green * 255f).roundToInt() / 255f
    val b = (color.blue * 255f).roundToInt() / 255f
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val delta = max - min
    val lightness = (max + min) / 2f
    var hue = 0f
    var saturation = 0f
    if (max != min) {
        hue = when (max) {
            r -> ((g - b) / delta) % 6f
            g -> ((b - r) / delta) + 2f
            else -> ((r - g) / delta) + 4f
        }
        saturation = delta / (1f - abs(2f * lightness - 1f))
    }
    hue = (hue * 60f) % 360f
    if (hue < 0f) hue += 360f
    return floatArrayOf(hue.coerceIn(0f, 360f), saturation.coerceIn(0f, 1f), lightness.coerceIn(0f, 1f))
}

/** The opaque colour of [hsl] as [colorToHsl] returns it. */
fun hslToColor(hsl: FloatArray): Color {
    val (hue, saturation, lightness) = hsl
    val c = (1f - abs(2f * lightness - 1f)) * saturation
    val m = lightness - 0.5f * c
    val x = c * (1f - abs((hue / 60f % 2f) - 1f))
    val (r, g, b) = when (hue.toInt() / 60) {
        0 -> Triple(c + m, x + m, m)
        1 -> Triple(x + m, c + m, m)
        2 -> Triple(m, c + m, x + m)
        3 -> Triple(m, x + m, c + m)
        4 -> Triple(x + m, m, c + m)
        else -> Triple(c + m, m, x + m)
    }
    fun channel(value: Float) = (255f * value).roundToInt().coerceIn(0, 255)
    return Color(channel(r), channel(g), channel(b))
}
