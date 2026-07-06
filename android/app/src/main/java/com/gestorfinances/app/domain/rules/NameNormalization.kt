package com.gestorfinances.app.domain.rules

import java.util.Locale

/** Case/whitespace-insensitive comparison key for movement names/payees, shared by [DuplicateDetector]
 * and [RecurringPatternDetector]. */
internal fun normalizeMovementName(value: String): String =
    value.trim()
        .lowercase(Locale.ROOT)
        .replace(Regex("\\s+"), " ")
