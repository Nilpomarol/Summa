package com.gestorfinances.app.ui.movements

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.gestorfinances.ui.resources.Res
import com.gestorfinances.ui.resources.category_field_name
import com.gestorfinances.ui.resources.category_form_new_title
import com.gestorfinances.ui.resources.category_save_new
import com.gestorfinances.ui.resources.common_cancel
import com.gestorfinances.app.ui.common.CategoryIconPalette
import com.gestorfinances.app.ui.common.EntityAppearancePickers
import com.gestorfinances.app.ui.common.EntityColorPalette
import com.gestorfinances.app.ui.common.EntityFormHeader
import com.gestorfinances.app.ui.common.PrimaryButton
import com.gestorfinances.app.ui.common.SecondaryButton
import com.gestorfinances.app.ui.common.categoryIcon
import com.gestorfinances.app.ui.common.doneKeyboardActions
import com.gestorfinances.app.ui.theme.FinanceTheme
import com.gestorfinances.app.ui.theme.categoryColor

/**
 * A category made without leaving the movement form, laid out like the category form: its tile
 * and name, with colour and icon behind the tile. Its use follows the movement type; parent and
 * nature are edited later in Categories. The host decides what it sits in: a sheet, a dialog.
 */
@Composable
fun ColumnScope.CreateCategoryContent(
    onConfirm: (name: String, iconKey: String?, colorHex: String) -> Unit,
    onCancel: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var colorHex by remember { mutableStateOf(EntityColorPalette.first().hex) }
    var iconKey by remember { mutableStateOf<String?>(null) }
    var appearanceOpen by remember { mutableStateOf(false) }
    var created by remember { mutableStateOf(false) }
    val create = {
        if (name.isNotBlank() && !created) {
            onConfirm(name.trim(), iconKey, colorHex)
            created = true
        }
    }
    Column(
        modifier = Modifier
            .weight(1f, fill = false)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(Res.string.category_form_new_title),
            style = MaterialTheme.typography.titleLarge,
        )
        EntityFormHeader(
            icon = categoryIcon(iconKey),
            color = categoryColor(colorHex),
            name = name,
            onNameChange = { name = it },
            nameLabel = stringResource(Res.string.category_field_name),
            onTileClick = { appearanceOpen = !appearanceOpen },
            imeAction = ImeAction.Done,
            keyboardActions = doneKeyboardActions(create),
        )
        EntityAppearancePickers(
            open = appearanceOpen,
            colorHex = colorHex,
            onColor = { colorHex = it },
            iconOptions = CategoryIconPalette,
            iconKey = iconKey,
            onIcon = { iconKey = it },
        )
    }
    HorizontalDivider(color = FinanceTheme.colors.cardBorder)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SecondaryButton(
            text = stringResource(Res.string.common_cancel),
            onClick = onCancel,
            modifier = Modifier.weight(1f),
        )
        PrimaryButton(
            text = stringResource(Res.string.category_save_new),
            onClick = create,
            enabled = name.isNotBlank() && !created,
            modifier = Modifier.weight(1f),
        )
    }
}
