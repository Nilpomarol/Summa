package com.gestorfinances.app.ui.theme

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * How the shared controls are sized and shaped. The phone's are made for a thumb: tall, soft,
 * pill-ended. The desktop app provides its own, made for a pointer: compact and squared, as a
 * Windows app's are.
 */
data class ControlStyle(
    /** Pointer-sized controls with hover and press states instead of touch ripples. */
    val pointer: Boolean,
    val buttonShape: Shape,
    val buttonHeight: Dp,
    val buttonPadding: PaddingValues,
    /** Chips, the search field and small badges. */
    val pillShape: Shape,
    val chipPadding: PaddingValues,
    val searchHeight: Dp,
    val segmentTrackRadius: Dp,
    val segmentRadius: Dp,
    val segmentHeight: Dp,
    val linkShape: Shape,
    val linkHeight: Dp,
    /** An identity tile's corner as a fraction of its size. */
    val tileCorner: Float,
)

val TouchControlStyle = ControlStyle(
    pointer = false,
    buttonShape = RoundedCornerShape(16.dp),
    buttonHeight = 52.dp,
    buttonPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
    pillShape = RoundedCornerShape(percent = 50),
    chipPadding = PaddingValues(horizontal = 14.dp, vertical = 9.dp),
    searchHeight = 48.dp,
    segmentTrackRadius = 12.dp,
    segmentRadius = 9.dp,
    segmentHeight = 38.dp,
    linkShape = RoundedCornerShape(10.dp),
    linkHeight = 36.dp,
    tileCorner = 0.35f,
)

val LocalControlStyle = staticCompositionLocalOf { TouchControlStyle }

/** A button's height: at least the touch target on the phone, exactly the control height with a pointer. */
fun Modifier.buttonHeight(style: ControlStyle): Modifier =
    if (style.pointer) height(style.buttonHeight) else heightIn(min = style.buttonHeight)

/** A form's leading action: the sheet's full width on the phone; with a pointer, its own width at the trailing edge. */
@Composable
fun Modifier.formAction(): Modifier =
    if (LocalControlStyle.current.pointer) fillMaxWidth().wrapContentWidth(Alignment.End) else fillMaxWidth()
