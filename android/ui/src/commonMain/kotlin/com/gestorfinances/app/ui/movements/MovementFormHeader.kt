package com.gestorfinances.app.ui.movements

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.gestorfinances.app.ui.theme.LocalControlStyle
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.LastBaseline
import androidx.compose.ui.platform.LocalDensity
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gestorfinances.ui.resources.Res
import com.gestorfinances.ui.resources.movement_field_name
import com.gestorfinances.app.ui.common.IdentityIconTile
import com.gestorfinances.app.ui.common.doneKeyboardActions
import com.gestorfinances.app.ui.common.nextFieldKeyboardActions
import com.gestorfinances.app.ui.theme.FinanceTheme
import com.gestorfinances.app.ui.theme.asFigures
import kotlin.math.roundToInt

private val AmountFontSize = 36.sp

/** Height of a capital letter in the title's sans, as a fraction of the font size. */
private const val CapHeightEm = 0.73f

/**
 * The top of the movement form: the category's tile beside the title (the concept as it is
 * typed below, or what is being added) over the amount, typed in place as the hero figure.
 */
@Composable
fun MovementFormHeader(
    icon: ImageVector,
    iconColor: Color,
    title: String,
    titleIsPlaceholder: Boolean,
    amount: String,
    onAmountChange: (String) -> Unit,
    amountColor: Color,
    modifier: Modifier = Modifier,
    amountError: String? = null,
) {
    val titleStyle = MaterialTheme.typography.titleMedium
    Column(modifier = modifier.fillMaxWidth()) {
        // Centring the boxes would leave the text off-centre (each line keeps room for accents
        // and descenders), so the tile centres on what is seen: the title's capitals to the
        // amount's baseline.
        val capHeightPx = with(LocalDensity.current) { titleStyle.fontSize.toPx() } * CapHeightEm
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            IdentityIconTile(
                icon = icon,
                color = iconColor,
                size = 56.dp,
                modifier = Modifier.alignBy { it.measuredHeight / 2 },
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .alignBy { text ->
                        val capTop = text[FirstBaseline] - capHeightPx.roundToInt()
                        (capTop + text[LastBaseline]) / 2
                    },
            ) {
                Text(
                    text = title,
                    style = titleStyle,
                    color = if (titleIsPlaceholder) FinanceTheme.colors.mutedText else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                AmountInput(
                    amount = amount,
                    onAmountChange = onAmountChange,
                    color = if (amountError != null) MaterialTheme.colorScheme.error else amountColor,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        if (amountError != null) {
            Text(
                text = amountError,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 72.dp),
            )
        }
    }
}

@Composable
private fun AmountInput(
    amount: String,
    onAmountChange: (String) -> Unit,
    color: Color,
    modifier: Modifier = Modifier,
) {
    // No line height of its own: the placeholder and the typed text then lay out identically.
    val style = MaterialTheme.typography.displayMedium.asFigures()
        .copy(fontSize = AmountFontSize, lineHeight = TextUnit.Unspecified)
    // Grouping dots are shown, never stored: an edited amount arrives grouped ("1.234,56").
    val raw = amount.replace(".", "")
    // With a keyboard at hand the form opens ready for its amount; on a phone that would raise
    // the keyboard unasked.
    val focusRequester = remember { FocusRequester() }
    if (LocalControlStyle.current.pointer) LaunchedEffect(Unit) { focusRequester.requestFocus() }
    BasicTextField(
        value = raw,
        onValueChange = { typed -> normalizedAmountInput(typed)?.let(onAmountChange) },
        singleLine = true,
        textStyle = style.copy(color = color),
        cursorBrush = SolidColor(color),
        visualTransformation = GroupedEuroAmount,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
        keyboardActions = nextFieldKeyboardActions(),
        modifier = modifier.focusRequester(focusRequester),
        decorationBox = { innerTextField ->
            Box {
                if (raw.isEmpty()) {
                    Text(text = "0,00 €", style = style, color = FinanceTheme.colors.mutedText)
                }
                innerTextField()
            }
        },
    )
}

/**
 * What the amount field stores for [typed]: digits and one decimal comma with at most two
 * decimals, a typed point read as that comma (grouping is shown automatically, so a point is never
 * a thousands separator here). Null when [typed] cannot be an amount, so the keystroke is ignored.
 */
fun normalizedAmountInput(typed: String): String? {
    val value = typed.replace('.', ',')
    if (value.any { !it.isDigit() && it != ',' }) return null
    val comma = value.indexOf(',')
    if (comma >= 0 && (value.lastIndexOf(',') != comma || value.length - comma - 1 > 2)) return null
    return value
}

/** Shows a stored amount ("1234,5") as "1.234,5 €". */
object GroupedEuroAmount : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        if (text.isEmpty()) return TransformedText(text, OffsetMapping.Identity)
        val raw = text.text
        val integerLength = raw.indexOf(',').takeIf { it >= 0 } ?: raw.length
        // A dot goes before every integer digit k > 0 that has a multiple of three digits from it
        // to the end of the integer part.
        fun dotBefore(k: Int) = k in 1 until integerLength && (integerLength - k) % 3 == 0
        fun dotsBefore(offset: Int) = (0 until offset).count(::dotBefore)
        val shown = buildString {
            raw.forEachIndexed { k, c ->
                if (dotBefore(k)) append('.')
                append(c)
            }
            append(" €")
        }
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int) = offset + dotsBefore(offset)
            override fun transformedToOriginal(offset: Int): Int =
                (raw.length downTo 0).first { originalToTransformed(it) <= offset }
        }
        return TransformedText(AnnotatedString(shown), mapping)
    }
}

/** The concept: a field of its own, echoed as the header's title. */
@Composable
fun ConceptField(
    name: String,
    onNameChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    /** What Enter does once the concept is written, where a form is saved from the keyboard. */
    onDone: () -> Unit = {},
) {
    var focused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val style = MaterialTheme.typography.bodyLarge
    FieldFrame(
        label = stringResource(Res.string.movement_field_name),
        focused = focused,
        modifier = modifier.fillMaxWidth(),
        // The whole bordered box takes the tap, not just the line of text inside it.
        surfaceModifier = Modifier.clickable(
            indication = null,
            interactionSource = remember { MutableInteractionSource() },
        ) { focusRequester.requestFocus() },
    ) {
        BasicTextField(
            value = name,
            onValueChange = onNameChange,
            singleLine = true,
            textStyle = style.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = doneKeyboardActions(onDone),
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester)
                .onFocusChanged { focused = it.isFocused },
        )
    }
}
