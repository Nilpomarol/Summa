package com.gestorfinances.desktop

import com.gestorfinances.app.ui.theme.DisplayFontFamily
import com.gestorfinances.app.ui.theme.InterfaceFontFamily
import com.gestorfinances.app.ui.theme.TokenColor
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.LocalScrollbarStyle
import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gestorfinances.app.ui.theme.ControlStyle
import com.gestorfinances.app.ui.theme.FinanceTheme
import com.gestorfinances.app.ui.theme.GestorFinancesTheme
import com.gestorfinances.app.ui.theme.LocalControlStyle
import com.gestorfinances.app.ui.theme.LocalFinanceColors
import com.gestorfinances.app.ui.theme.isDark
import kotlinx.coroutines.launch

/*
 * The app as a Windows app: the brand's greens and money colours on Windows' own neutral layers,
 * the phone's typefaces, small corners, compact controls that answer the pointer. The shared theme
 * stays the phone's; this only re-dresses it.
 */

/** The window's own ground, behind the navigation pane and the title bar. */
internal fun windowBackground(dark: Boolean): Color = if (dark) TokenColor.Dark0 else Color(0xFFF3F3F3)

/** The panel pages sit on, one step above the window's ground. */
internal val layerColor: Color
    @Composable @ReadOnlyComposable
    get() = if (FinanceTheme.isDark) Color(0xFF151515) else Color(0xFFF9F9F9)

internal val layerBorder: Color
    @Composable @ReadOnlyComposable
    get() = if (FinanceTheme.isDark) TokenColor.Dark100 else Color(0xFFE5E5E5)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DesktopTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    GestorFinancesTheme(darkTheme = darkTheme) {
        val base = MaterialTheme.colorScheme
        val scheme = if (darkTheme) {
            // Dark is the phone's own: near-black ground, cards a step above it.
            base
        } else {
            base.copy(
                background = windowBackground(false),
                surface = Color(0xFFFFFFFF),
                surfaceVariant = Color(0xFFEDEDED),
                surfaceContainerHighest = Color(0xFFEDEDED),
                onBackground = Color(0xFF1B1B1B),
                onSurface = Color(0xFF1B1B1B),
                onSurfaceVariant = Color(0xFF5F5F5F),
                outline = Color(0xFFCFCFCF),
                outlineVariant = Color(0xFFE5E5E5),
            )
        }
        val finance = if (darkTheme) {
            FinanceTheme.colors
        } else {
            FinanceTheme.colors.copy(
                cardBorder = Color(0xFFE5E5E5),
                mutedText = Color(0xFF5F5F5F),
                subtleText = Color(0xFF767676),
                progressTrack = Color(0xFFE2E2E2),
            )
        }
        CompositionLocalProvider(
            LocalFinanceColors provides finance,
            LocalControlStyle provides PointerControlStyle,
            // No touch ripples and no finger-sized minimums: see PointerIndication.
            LocalRippleConfiguration provides null,
            LocalMinimumInteractiveComponentSize provides Dp.Unspecified,
            LocalScrollbarStyle provides ScrollbarStyle(
                minimalHeight = 32.dp,
                thickness = 6.dp,
                shape = RoundedCornerShape(3.dp),
                hoverDurationMillis = 150,
                unhoverColor = scheme.onSurface.copy(alpha = 0.28f),
                hoverColor = scheme.onSurface.copy(alpha = 0.5f),
            ),
        ) {
            MaterialTheme(colorScheme = scheme, typography = DesktopTypography, shapes = DesktopShapes) {
                // MaterialTheme installs its ripple; anything merely clickable answers the pointer instead.
                CompositionLocalProvider(LocalIndication provides PointerIndication, content = content)
            }
        }
    }
}

private val PointerControlStyle = ControlStyle(
    pointer = true,
    // The phone's soft corners and pills at a pointer's size: the two apps are one family.
    buttonShape = RoundedCornerShape(10.dp),
    buttonHeight = 36.dp,
    buttonPadding = PaddingValues(horizontal = 16.dp),
    pillShape = RoundedCornerShape(percent = 50),
    chipPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
    searchHeight = 36.dp,
    segmentTrackRadius = 10.dp,
    segmentRadius = 8.dp,
    segmentHeight = 30.dp,
    linkShape = RoundedCornerShape(8.dp),
    linkHeight = 30.dp,
    tileCorner = 0.3f,
)

private val DesktopShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(12.dp),
)

private fun text(size: Int, line: Int, weight: FontWeight = FontWeight.Normal, family: FontFamily = InterfaceFontFamily) =
    TextStyle(fontFamily = family, fontSize = size.sp, lineHeight = line.sp, fontWeight = weight)

private fun display(size: Int, line: Int) = text(size, line, FontWeight.Medium, DisplayFontFamily)

// The phone's faces (Newsreader for titles and hero amounts, Inter for the rest, nothing past
// Medium) on Windows' type ramp: caption 12, body 14, title 28.
private val DesktopTypography = Typography(
    displayLarge = display(28, 36),
    displayMedium = display(28, 36),
    displaySmall = display(28, 36),
    headlineLarge = display(28, 36),
    headlineMedium = display(28, 36),
    headlineSmall = display(24, 32),
    titleLarge = display(18, 24),
    titleMedium = text(14, 20, FontWeight.Medium),
    titleSmall = text(14, 20, FontWeight.Medium),
    bodyLarge = text(14, 20),
    bodyMedium = text(14, 20),
    bodySmall = text(12, 16),
    labelLarge = text(14, 20),
    labelMedium = text(12, 16),
    labelSmall = text(12, 16),
)

/** What a clickable thing shows under the pointer: a faint wash on hover, a stronger one while pressed. */
private object PointerIndication : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = Node(interactionSource)

    override fun equals(other: Any?): Boolean = other === this

    override fun hashCode(): Int = 1

    private class Node(private val source: InteractionSource) :
        Modifier.Node(), DrawModifierNode, CompositionLocalConsumerModifierNode {
        private var hovered = false
        private var pressed = 0
        private var focused = false

        override fun onAttach() {
            coroutineScope.launch {
                source.interactions.collect { interaction ->
                    when (interaction) {
                        is HoverInteraction.Enter -> hovered = true
                        is HoverInteraction.Exit -> hovered = false
                        is PressInteraction.Press -> pressed++
                        is PressInteraction.Release, is PressInteraction.Cancel -> pressed = (pressed - 1).coerceAtLeast(0)
                        is FocusInteraction.Focus -> focused = true
                        is FocusInteraction.Unfocus -> focused = false
                    }
                    invalidateDraw()
                }
            }
        }

        override fun ContentDrawScope.draw() {
            drawContent()
            val alpha = if (pressed > 0) 0.10f else if (hovered || focused) 0.06f else 0f
            if (alpha > 0f) drawRect(currentValueOf(LocalContentColor).copy(alpha = alpha))
        }
    }
}
