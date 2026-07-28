package com.gestorfinances.app.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.conflate

/**
 * Compact HSV color picker: a 2D saturation/value rectangle + a rainbow hue slider + a hex
 * input. Seeded from [initialHex] once; emits "#RRGGBB" via [onSelect].
 *
 * Touch handling ([colorPickerDrag]) follows the canonical Compose pattern: a single gesture
 * loop that consumes the initial down and every subsequent move at the Main pass. Because the
 * picker (a descendant) processes the Main pass before its scrollable ancestors, consuming the
 * changes here means the surrounding ModalBottomSheet drag-to-dismiss and verticalScroll never
 * see an unconsumed drag, so they don't hijack the gesture. Tap-to-pick and drag both work from
 * the very first touch — no slop threshold, no second detector to conflict with.
 *
 * Output is throttled to one [onSelect] per frame via snapshotFlow + conflate: the Canvas
 * redraws from local state at full rate while parent recomposition is decoupled from the
 * pointer event rate, which keeps dragging smooth.
 */
@Composable
fun HsvColorPicker(
    initialHex: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val seed = remember { hexToHsv(initialHex) ?: floatArrayOf(0f, 0.8f, 0.9f) }
    var hue by remember { mutableStateOf(seed[0]) }
    var saturation by remember { mutableStateOf(seed[1]) }
    var value by remember { mutableStateOf(seed[2]) }

    // Hex field: unfocused → mirrors the picker; focused → user owns the text.
    var hexInput by remember {
        mutableStateOf(initialHex ?: colorToHex(hsvToColor(seed[0], seed[1], seed[2])))
    }
    var hexFocused by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        snapshotFlow { colorToHex(hsvToColor(hue, saturation, value)) }
            .conflate()
            .collect { hex ->
                if (!hexFocused) hexInput = hex
                onSelect(hex)
            }
    }

    val hueColor = hsvToColor(hue, 1f, 1f)
    val currentColor = hsvToColor(hue, saturation, value)

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {

        // 2D saturation (x) / value (y) field
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(MaterialTheme.shapes.medium)
                .colorPickerDrag { x, y ->
                    saturation = x
                    value = 1f - y
                },
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawRect(brush = Brush.horizontalGradient(listOf(Color.White, hueColor)))
                drawRect(brush = Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
                val cx = saturation * size.width
                val cy = (1f - value) * size.height
                drawCircle(Color.White, 10.dp.toPx(), Offset(cx, cy), style = Stroke(2.5.dp.toPx()))
                drawCircle(Color.Black.copy(alpha = 0.25f), 10.dp.toPx(), Offset(cx, cy), style = Stroke(1.dp.toPx()))
                drawCircle(currentColor, 7.5.dp.toPx(), Offset(cx, cy))
            }
        }

        // Hue slider (x → 0..360°)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .clip(MaterialTheme.shapes.extraSmall)
                .colorPickerDrag { x, _ -> hue = x * 360f },
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawRect(
                    brush = Brush.horizontalGradient(
                        listOf(
                            Color(0xFFFF0000), Color(0xFFFFFF00), Color(0xFF00FF00),
                            Color(0xFF00FFFF), Color(0xFF0000FF), Color(0xFFFF00FF),
                            Color(0xFFFF0000),
                        ),
                    ),
                )
                val tx = (hue / 360f) * size.width
                drawRect(Color.White, Offset(tx - 2.5.dp.toPx(), 0f), Size(2.5.dp.toPx(), size.height))
                drawRect(Color.Black.copy(0.4f), Offset(tx - 2.5.dp.toPx(), 0f), Size(1.dp.toPx(), size.height))
            }
        }

        // Color preview + editable hex
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(currentColor, MaterialTheme.shapes.small)
                    .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small),
            )
            OutlinedTextField(
                value = hexInput,
                onValueChange = { raw ->
                    hexInput = raw
                    val normalized = raw.trim().let { if (it.startsWith("#")) it else "#$it" }
                    hexToHsv(normalized)?.let { hsv ->
                        hue = hsv[0]
                        saturation = hsv[1]
                        value = hsv[2]
                    }
                },
                label = { Text(text = "Hex") },
                placeholder = { Text(text = "#RRGGBB") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Ascii,
                    capitalization = KeyboardCapitalization.Characters,
                ),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged { hexFocused = it.isFocused },
            )
        }
    }
}

/**
 * Canonical single-gesture drag tracker for a picker surface. Consumes the down and every move
 * at the Main pass so scrollable/dismissable ancestors can't steal the gesture. Reports the
 * touch position as fractions in [0, 1] of the element's own size, on down and on every move.
 */
private fun Modifier.colorPickerDrag(
    onPositionFraction: (x: Float, y: Float) -> Unit,
): Modifier = this.pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        down.consume()
        onPositionFraction(
            (down.position.x / size.width).coerceIn(0f, 1f),
            (down.position.y / size.height).coerceIn(0f, 1f),
        )
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            change.consume()
            if (!change.pressed) break
            onPositionFraction(
                (change.position.x / size.width).coerceIn(0f, 1f),
                (change.position.y / size.height).coerceIn(0f, 1f),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Shared color helpers
// ---------------------------------------------------------------------------

internal fun hsvToColor(hue: Float, saturation: Float, value: Float): Color =
    Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, value)))

internal fun colorToHex(color: Color): String {
    val r = (color.red * 255).toInt()
    val g = (color.green * 255).toInt()
    val b = (color.blue * 255).toInt()
    return "#%02X%02X%02X".format(r, g, b)
}

internal fun hexToHsv(hex: String?): FloatArray? {
    if (hex.isNullOrBlank()) return null
    return try {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(android.graphics.Color.parseColor(hex.trim()), hsv)
        hsv
    } catch (_: IllegalArgumentException) {
        null
    }
}
