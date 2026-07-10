package com.gestorfinances.app.ui.common

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier

/**
 * Scrolls the field this modifier is attached to into the viewport whenever [condition] flips to
 * `true` (e.g. a form field's validation error just appeared) — so a validation failure surfaces
 * where the user is looking instead of leaving them to scroll back up to find it (audit U8,
 * `docs/17` WP2). Requires an ancestor scrollable container (`Modifier.verticalScroll`/
 * `LazyColumn` both support `BringIntoViewRequester` out of the box).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.scrollToWhen(condition: Boolean): Modifier {
    val requester = remember { BringIntoViewRequester() }
    LaunchedEffect(condition) {
        if (condition) {
            requester.bringIntoView()
        }
    }
    return this.bringIntoViewRequester(requester)
}
