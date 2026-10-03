package com.gestorfinances.app.ui.goals

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.ui.graphics.vector.ImageVector
import com.gestorfinances.app.ui.common.categoryIcon

/** A goal's chosen icon, or a piggy bank when it has none. */
fun goalIcon(key: String?): ImageVector = key?.let(::categoryIcon) ?: Icons.Outlined.Savings
