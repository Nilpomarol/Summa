package com.gestorfinances.app.ui.common

/** Shared "display order, then name" sort used by the accounts and categories lists. */
internal fun <T> List<T>.sortedByDisplayOrderThenName(
    displayOrder: (T) -> Long,
    name: (T) -> String,
): List<T> = sortedWith(compareBy<T> { displayOrder(it) }.thenBy { name(it).lowercase() })
