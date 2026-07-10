package com.gestorfinances.app.ui.analysis.components

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.gestorfinances.app.R
import com.gestorfinances.app.ui.common.BannerKind
import com.gestorfinances.app.ui.common.InlineBanner
import com.gestorfinances.app.ui.common.SectionHeader
import com.gestorfinances.app.ui.theme.FinanceTheme

/** Muted "loading" line shown while a tab's data is being fetched. */
@Composable
internal fun TabLoading() {
    Text(
        text = stringResource(R.string.movement_loading),
        color = FinanceTheme.colors.mutedText,
        style = MaterialTheme.typography.bodyMedium,
    )
}

internal fun LazyListScope.tabErrorItem(message: String?) {
    message?.let { item { InlineBanner(kind = BannerKind.Error, text = it) } }
}

@Composable
internal fun TabSection(title: String) {
    SectionHeader(title = title)
}
