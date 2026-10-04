package com.gestorfinances.app.ui.common

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable

/**
 * An entity's create/edit form over the page (a bottom sheet on the phone, a dialog on the
 * desktop): a title, the scrolling [content], and Cancel·la / save pinned
 * under it. [form] is the view model's live form, null once saved or discarded, so it is called whether
 * or not a form is open.
 * Every way out asks before losing [changed] edits.
 */
@Composable
expect fun <T : Any> EntityFormSheet(
    form: T?,
    key: (T) -> Any,
    changed: (initial: T, current: T) -> Boolean,
    onDiscard: () -> Unit,
    onSave: () -> Unit,
    title: @Composable (T) -> String,
    saveLabel: @Composable (T) -> String,
    saving: (T) -> Boolean = { false },
    /** Deletes what is being edited, for a record with no page (and so no menu) of its own. */
    onDelete: ((T) -> Unit)? = null,
    content: @Composable ColumnScope.(T) -> Unit,
)
