package com.gestorfinances.app.ui.analysis.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gestorfinances.app.R
import com.gestorfinances.app.ui.common.MoneyText
import com.gestorfinances.app.ui.common.formatBasisPoints
import com.gestorfinances.app.ui.theme.FinanceTheme

/** A dark hero KPI card showing one labelled money figure (design §2.6 hero surface). */
@Composable
internal fun MoneyMetricCard(
    label: String,
    cents: Long,
    color: Color,
    modifier: Modifier = Modifier,
    signed: Boolean = false,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = FinanceTheme.colors.heroSurface,
        contentColor = FinanceTheme.colors.heroOnSurface,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = label,
                color = FinanceTheme.colors.heroOnSurfaceMuted,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            MoneyText(
                cents = cents,
                color = color,
                style = MaterialTheme.typography.titleMedium,
                signed = signed,
            )
        }
    }
}

/** A hero KPI card showing the savings-rate percentage. */
@Composable
internal fun RateMetricCard(
    currentBasisPoints: Long,
    hasIncome: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = FinanceTheme.colors.heroSurface,
        contentColor = FinanceTheme.colors.heroOnSurface,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(R.string.analysis_savings_rate_title),
                color = FinanceTheme.colors.heroOnSurfaceMuted,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (hasIncome) {
                    formatBasisPoints(currentBasisPoints)
                } else {
                    stringResource(R.string.dashboard_savings_rate_unavailable)
                },
                color = if (currentBasisPoints >= 0) FinanceTheme.colors.income else FinanceTheme.colors.debt,
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}
