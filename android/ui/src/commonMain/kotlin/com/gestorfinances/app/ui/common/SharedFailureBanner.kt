package com.gestorfinances.app.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.gestorfinances.ui.resources.Res
import com.gestorfinances.ui.resources.common_retry
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** An error banner that shows the user-facing message and keeps the diagnostic detail out of it. */
@Composable
fun InlineFailureBanner(
    @Suppress("UNUSED_PARAMETER") diagnostic: String?,
    messageRes: StringResource,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    InlineBanner(
        kind = BannerKind.Error,
        text = stringResource(messageRes),
        actionLabel = if (onRetry != null) stringResource(Res.string.common_retry) else null,
        onAction = onRetry,
        modifier = modifier,
    )
}
