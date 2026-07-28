package com.gestorfinances.app.ui.common

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.runtime.Composable
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager

/** Advances focus to the next field on IME "Next" (design baseline keyboard/focus basics). */
@Composable
fun nextFieldKeyboardActions(): KeyboardActions {
    val focusManager = LocalFocusManager.current
    return KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })
}

/** Clears focus (dismissing the keyboard) on IME "Done". */
@Composable
fun doneKeyboardActions(): KeyboardActions {
    val focusManager = LocalFocusManager.current
    return KeyboardActions(onDone = { focusManager.clearFocus() })
}
