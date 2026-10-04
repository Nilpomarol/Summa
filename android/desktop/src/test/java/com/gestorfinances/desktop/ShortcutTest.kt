package com.gestorfinances.desktop

import androidx.compose.ui.input.key.Key
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShortcutTest {
    private fun key(key: Key, down: Boolean = true, ctrl: Boolean = true, alt: Boolean = false) = shortcutOf(key, down, ctrl, alt)

    @Test
    fun ctrlWithAKeyOpensWhatItNames() {
        assertEquals(Shortcut.NewMovement, key(Key.N))
        assertEquals(Shortcut.Open(Page.HOME), key(Key.One))
        assertEquals(Shortcut.Open(Page.CATEGORIES), key(Key.Nine))
        assertEquals(Shortcut.Open(Page.TRIPS), key(Key.Zero))
        assertEquals(Shortcut.Open(Page.TAGS), key(Key.E))
        assertEquals(Shortcut.Open(Page.SETTINGS), key(Key.Comma))
    }

    @Test
    fun typingIsNotAShortcut() {
        // AltGr+2 is how @ is typed.
        assertNull(key(Key.Two, alt = true))
        assertNull(key(Key.N, ctrl = false))
        assertNull(key(Key.N, down = false))
        assertNull(key(Key.Q))
    }
}
