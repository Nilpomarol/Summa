package com.gestorfinances.app.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class HslTest {
    @Test
    fun convertsKnownColours() {
        assertArrayEquals(floatArrayOf(0f, 1f, 0.5f), colorToHsl(Color(0xFFFF0000)), 0.001f)
        assertArrayEquals(floatArrayOf(210f, 0.5f, 0.4f), colorToHsl(Color(0xFF336699)), 0.001f)
        assertArrayEquals(floatArrayOf(0f, 0f, 0.502f), colorToHsl(Color(0xFF808080)), 0.001f)
        assertEquals(Color(0xFF336699), hslToColor(floatArrayOf(210f, 0.5f, 0.4f)))
    }

    @Test
    fun roundTripsEveryHueSegment() {
        listOf(0xFFE53935, 0xFFFDD835, 0xFF43A047, 0xFF00ACC1, 0xFF3949AB, 0xFF8E24AA, 0xFF000000, 0xFFFFFFFF)
            .map { Color(it) }
            .forEach { assertEquals(it, hslToColor(colorToHsl(it))) }
    }

    @Test
    fun parsesStoredHexColours() {
        assertEquals(Color(0xFF336699), categoryColor("#336699"))
        assertEquals(Color(0xFF336699), categoryColor(" #336699 "))
        assertEquals(Color(0x80336699), parseHexColorOrNull("#80336699"))
        assertEquals(TokenColor.CategoryUncategorized, categoryColor(null))
        assertEquals(TokenColor.CategoryUncategorized, categoryColor("336699"))
        assertEquals(TokenColor.CategoryUncategorized, categoryColor("#33669"))
        assertEquals(TokenColor.CategoryUncategorized, categoryColor("#GG6699"))
    }
}
