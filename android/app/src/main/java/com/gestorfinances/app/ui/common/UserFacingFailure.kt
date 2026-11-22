package com.gestorfinances.app.ui.common

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource

/** Keeps diagnostic failure detail out of user-facing Compose surfaces. */
@Composable
fun userFacingFailureText(
    @Suppress("UNUSED_PARAMETER") diagnostic: String?,
    @StringRes messageRes: Int,
): String = stringResource(messageRes)

@Composable
fun InlineFailureBanner(
    diagnostic: String?,
    @StringRes messageRes: Int,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    InlineBanner(
        kind = BannerKind.Error,
        text = userFacingFailureText(diagnostic, messageRes),
        actionLabel = if (onRetry != null) stringResource(com.gestorfinances.app.R.string.common_retry) else null,
        onAction = onRetry,
        modifier = modifier,
    )
}
