package com.gestorfinances.app.ui.common

/** Presents the short-lived Undo action for a successfully completed user-facing deletion. */
typealias DeleteUndoHandler = (undo: () -> Unit) -> Unit
