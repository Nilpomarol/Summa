package com.gestorfinances.desktop

import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.ptr.IntByReference
import com.sun.jna.win32.StdCallLibrary
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.Timer
import kotlin.math.abs
import kotlin.math.roundToInt

private interface Dwm : StdCallLibrary {
    fun DwmSetWindowAttribute(window: Pointer, attribute: Int, value: IntByReference, size: Int): Int
}

private interface User32 : StdCallLibrary {
    fun GetClientRect(window: Pointer, rect: IntArray): Boolean
    fun GetWindowRect(window: Pointer, rect: IntArray): Boolean
    fun IsZoomed(window: Pointer): Boolean
    fun SetWindowPos(window: Pointer, after: Pointer?, x: Int, y: Int, width: Int, height: Int, flags: Int): Boolean
}

private const val SWP_NOMOVE = 0x0002
private const val SWP_NOZORDER = 0x0004
private const val SWP_NOACTIVATE = 0x0010

private const val DWMWA_USE_IMMERSIVE_DARK_MODE = 20
private const val DWMWA_BORDER_COLOR = 34
private const val DWMWA_CAPTION_COLOR = 35
private const val DWMWA_TEXT_COLOR = 36

/**
 * Makes Windows' own title bar part of the app: the window's ground colour behind the title and
 * the caption buttons, in the light or dark form, where it is otherwise white whatever the theme.
 * The same colour sits behind the content, so nothing white shows at an edge or while resizing.
 * Windows 10 only takes the dark form; anywhere else this does nothing.
 */
internal fun applyWindowChrome(window: ComposeWindow, dark: Boolean, ground: Color, text: Color) {
    val awt = java.awt.Color(ground.toArgb())
    window.background = awt
    window.contentPane.background = awt
    if (!System.getProperty("os.name").orEmpty().startsWith("Windows")) return
    runCatching {
        val dwm = Native.load("dwmapi", Dwm::class.java)
        val handle = Pointer(window.windowHandle)
        fun set(attribute: Int, value: Int) = dwm.DwmSetWindowAttribute(handle, attribute, IntByReference(value), 4)
        set(DWMWA_USE_IMMERSIVE_DARK_MODE, if (dark) 1 else 0)
        set(DWMWA_CAPTION_COLOR, ground.colorRef())
        set(DWMWA_BORDER_COLOR, ground.colorRef())
        set(DWMWA_TEXT_COLOR, text.colorRef())
    }
}

/**
 * Keeps the text sharp at Windows' fractional display scales (125 %, 150 %...). The content is
 * drawn at a whole size in scaled units; when the window's real pixels are not a whole number of
 * those, the picture is stretched the missing pixel over the window and everything goes soft. So
 * once a resize settles, the window gives up the few pixels that do not fit. A maximized window
 * cannot.
 */
internal fun keepPixelAligned(window: ComposeWindow) {
    if (!System.getProperty("os.name").orEmpty().startsWith("Windows")) return
    val user32 = runCatching { Native.load("user32", User32::class.java) }.getOrNull() ?: return
    val settle = Timer(150) {
        runCatching {
            val scale = window.graphicsConfiguration.defaultTransform.scaleX
            // The fewest pixels that make a whole number of scaled units: 5 at 125 %, 3 at 150 %.
            val step = (1..20).first { abs(it / scale - (it / scale).roundToInt()) < 1e-6 }
            val handle = Pointer(window.windowHandle)
            val client = IntArray(4)
            val frame = IntArray(4)
            if (step == 1 || user32.IsZoomed(handle) || !user32.GetClientRect(handle, client) || !user32.GetWindowRect(handle, frame)) return@Timer
            val dx = client[2] % step
            val dy = client[3] % step
            if (dx != 0 || dy != 0) {
                user32.SetWindowPos(handle, null, 0, 0, frame[2] - frame[0] - dx, frame[3] - frame[1] - dy, SWP_NOMOVE or SWP_NOZORDER or SWP_NOACTIVATE)
            }
        }
    }.apply { isRepeats = false }
    window.addComponentListener(object : ComponentAdapter() {
        override fun componentResized(event: ComponentEvent) = settle.restart()
        override fun componentShown(event: ComponentEvent) = settle.restart()
    })
    settle.restart()
}

/** Windows' COLORREF: 0x00BBGGRR. */
private fun Color.colorRef(): Int {
    val argb = toArgb()
    return ((argb and 0xFF) shl 16) or (argb and 0xFF00) or ((argb shr 16) and 0xFF)
}
