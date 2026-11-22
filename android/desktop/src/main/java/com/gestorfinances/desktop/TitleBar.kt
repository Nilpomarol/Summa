package com.gestorfinances.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import com.gestorfinances.desktop.resources.Res
import com.gestorfinances.desktop.resources.window_close
import com.gestorfinances.desktop.resources.window_maximize
import com.gestorfinances.desktop.resources.window_minimize
import com.gestorfinances.desktop.resources.window_restore
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.win32.StdCallLibrary
import java.awt.event.WindowEvent
import org.jetbrains.compose.resources.stringResource

/** How much of the window's top the app's own title bar takes; nothing where Windows draws it. */
internal val LocalTitleBarHeight = compositionLocalOf { 0.dp }

internal val TitleBarHeight = 36.dp
private val CaptionButtonWidth = 46.dp

private interface WindowProcedure : StdCallLibrary.StdCallCallback {
    fun callback(window: Pointer, message: Int, wParam: Long, lParam: Long): Long
}

private interface ChildVisitor : StdCallLibrary.StdCallCallback {
    fun callback(window: Pointer, data: Pointer?): Boolean
}

private interface FrameUser32 : StdCallLibrary {
    fun EnumChildWindows(parent: Pointer, visitor: ChildVisitor, data: Pointer?): Boolean
    fun SetWindowLongPtrW(window: Pointer, index: Int, procedure: WindowProcedure): Pointer?
    fun CallWindowProcW(previous: Pointer, window: Pointer, message: Int, wParam: Long, lParam: Long): Long
    fun GetWindowRect(window: Pointer, rect: IntArray): Boolean
    fun IsZoomed(window: Pointer): Boolean
    fun GetDpiForWindow(window: Pointer): Int
    fun GetSystemMetricsForDpi(index: Int, dpi: Int): Int
    fun SetWindowPos(window: Pointer, after: Pointer?, x: Int, y: Int, width: Int, height: Int, flags: Int): Boolean
}

private const val GWLP_WNDPROC = -4
private const val WM_NCCALCSIZE = 0x0083
private const val WM_NCHITTEST = 0x0084
private const val HTTRANSPARENT = -1L
private const val HTCLIENT = 1L
private const val HTCAPTION = 2L
private const val HTTOP = 12L
private const val HTTOPLEFT = 13L
private const val HTTOPRIGHT = 14L
private const val SM_CYSIZEFRAME = 33
private const val SM_CXPADDEDBORDER = 92
private const val SWP_FRAME_ONLY = 0x0001 or 0x0002 or 0x0004 or 0x0010 or 0x0020 // no size, move, z-order or activation; frame changed

/**
 * The app's own title bar in place of Windows': the window keeps its frame, so its shadow, its
 * resizing edges, dragging to a screen edge and Win+arrows all stay Windows' own, but the page
 * reaches the window's top and the strip there is the app's to draw. Windows is told which part
 * of that strip moves the window and which is the caption buttons.
 *
 * ponytail: the buttons are the app's, so hovering maximize does not open Windows 11's snap
 * layouts (Win+Z still does); answer HTMAXBUTTON and handle its clicks here to have them.
 */
internal class OwnTitleBar private constructor(private val user32: FrameUser32, private val handle: Pointer) {
    @Volatile var heightPx = 0
    @Volatile var buttonsWidthPx = 0
    private var previous: Pointer? = null

    // Held here: Windows calls it for as long as the window lives.
    private val procedure = object : WindowProcedure {
        override fun callback(window: Pointer, message: Int, wParam: Long, lParam: Long): Long {
            val before = previous ?: return 0
            fun default() = user32.CallWindowProcW(before, window, message, wParam, lParam)
            when (message) {
                WM_NCCALCSIZE -> if (wParam != 0L) {
                    // Windows' own answer for the sides and the bottom, but nothing taken off the
                    // top; maximized, the frame hangs off the screen and the page must not.
                    val rect = Pointer(lParam)
                    val top = rect.getInt(4)
                    default()
                    rect.setInt(4, if (user32.IsZoomed(window)) top + frame(window) else top)
                    return 0
                }
                WM_NCHITTEST -> {
                    val hit = default()
                    return if (hit == HTCLIENT) zone(lParam) else hit
                }
            }
            return default()
        }
    }

    // The page is drawn on a window of its own inside the frame, and the mouse meets that one
    // first: over the strip it steps aside, so the frame is asked and can move or resize.
    private val children = mutableListOf<WindowProcedure>()

    private fun stepAside(child: Pointer) {
        var before: Pointer? = null
        val procedure = object : WindowProcedure {
            override fun callback(window: Pointer, message: Int, wParam: Long, lParam: Long): Long {
                val previous = before ?: return 0
                if (message == WM_NCHITTEST && zone(lParam) != HTCLIENT) return HTTRANSPARENT
                return user32.CallWindowProcW(previous, window, message, wParam, lParam)
            }
        }
        children += procedure
        before = user32.SetWindowLongPtrW(child, GWLP_WNDPROC, procedure)
    }

    /** What the screen point in [lParam] is to the frame: an edge to resize by, the strip to move by, or the page. */
    private fun zone(lParam: Long): Long {
        val x = lParam.toInt().toShort().toInt()
        val y = (lParam shr 16).toInt().toShort().toInt()
        val rect = IntArray(4)
        user32.GetWindowRect(handle, rect)
        val frame = frame(handle)
        val zoomed = user32.IsZoomed(handle)
        if (!zoomed && y < rect[1] + frame) {
            return when {
                x < rect[0] + 2 * frame -> HTTOPLEFT
                x >= rect[2] - 2 * frame -> HTTOPRIGHT
                else -> HTTOP
            }
        }
        val top = rect[1] + if (zoomed) frame else 0
        return if (y < top + heightPx && x < rect[2] - buttonsWidthPx) HTCAPTION else HTCLIENT
    }

    private fun frame(window: Pointer): Int {
        val dpi = user32.GetDpiForWindow(window)
        return user32.GetSystemMetricsForDpi(SM_CYSIZEFRAME, dpi) + user32.GetSystemMetricsForDpi(SM_CXPADDEDBORDER, dpi)
    }

    companion object {
        /** Takes the title bar over, or returns null where it cannot (not Windows): Windows' own stays. */
        fun install(window: ComposeWindow): OwnTitleBar? {
            if (!System.getProperty("os.name").orEmpty().startsWith("Windows")) return null
            return runCatching {
                val user32 = Native.load("user32", FrameUser32::class.java)
                val handle = Pointer(window.windowHandle)
                OwnTitleBar(user32, handle).also { bar ->
                    bar.previous = user32.SetWindowLongPtrW(handle, GWLP_WNDPROC, bar.procedure) ?: error("no window procedure")
                    user32.EnumChildWindows(
                        handle,
                        object : ChildVisitor {
                            override fun callback(window: Pointer, data: Pointer?): Boolean {
                                bar.stepAside(window)
                                return true
                            }
                        },
                        null,
                    )
                    check(bar.children.isNotEmpty()) { "no page window" }
                    user32.SetWindowPos(handle, null, 0, 0, 0, 0, SWP_FRAME_ONLY)
                }
            }.getOrNull()
        }
    }
}

/** Minimize, maximize or restore, and close, at the window's top right, as Windows draws them. */
@Composable
internal fun CaptionButtons(bar: OwnTitleBar, window: ComposeWindow, state: WindowState, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    SideEffect {
        with(density) {
            bar.heightPx = TitleBarHeight.roundToPx()
            bar.buttonsWidthPx = (CaptionButtonWidth * 3).roundToPx()
        }
    }
    val maximized = state.placement == WindowPlacement.Maximized
    Row(modifier) {
        CaptionButton(stringResource(Res.string.window_minimize), onClick = { state.isMinimized = true }) { color ->
            drawLine(color, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 1.dp.toPx())
        }
        CaptionButton(
            stringResource(if (maximized) Res.string.window_restore else Res.string.window_maximize),
            onClick = { state.placement = if (maximized) WindowPlacement.Floating else WindowPlacement.Maximized },
        ) { color ->
            val stroke = Stroke(1.dp.toPx())
            if (maximized) {
                val step = 2.dp.toPx()
                val side = Size(size.width - step, size.height - step)
                drawRect(color, Offset(0f, step), side, style = stroke)
                drawLine(color, Offset(step, 0f), Offset(size.width, 0f), stroke.width)
                drawLine(color, Offset(size.width, 0f), Offset(size.width, size.height - step), stroke.width)
            } else {
                drawRect(color, style = stroke)
            }
        }
        CaptionButton(
            stringResource(Res.string.window_close),
            onClick = { window.dispatchEvent(WindowEvent(window, WindowEvent.WINDOW_CLOSING)) },
            hover = Color(0xFFC42B1C),
            onHover = Color.White,
        ) { color ->
            drawLine(color, Offset.Zero, Offset(size.width, size.height), 1.dp.toPx())
            drawLine(color, Offset(0f, size.height), Offset(size.width, 0f), 1.dp.toPx())
        }
    }
}

@Composable
private fun CaptionButton(
    description: String,
    onClick: () -> Unit,
    hover: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
    onHover: Color = MaterialTheme.colorScheme.onSurface,
    glyph: DrawScope.(Color) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val color = if (hovered) onHover else MaterialTheme.colorScheme.onSurface
    Box(
        Modifier
            .size(CaptionButtonWidth, TitleBarHeight)
            .background(if (hovered) hover else Color.Transparent)
            .hoverable(interaction)
            .clickable(interaction, indication = null, role = Role.Button, onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(10.dp)) { glyph(color) }
    }
}
