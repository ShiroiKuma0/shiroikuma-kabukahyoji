package com.github.premnirmal.ticker.detail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.takeOrElse
import com.github.premnirmal.ticker.news.QuoteDetailViewModel.QuoteDetail
import com.github.premnirmal.ticker.ui.LocalAppMessaging
import com.github.premnirmal.ticker.ui.QuoteElement
import com.github.premnirmal.ticker.ui.quoteElementColour
import com.github.premnirmal.ticker.ui.quoteElementTextStyle
import com.github.premnirmal.tickerwidget.ui.AppCard

@Composable
fun QuoteDetailCard(
    modifier: Modifier = Modifier,
    item: QuoteDetail
) {
    val appMessaging = LocalAppMessaging.current
    AppCard(
        modifier = modifier.fillMaxSize(),
        onClick = {
            appMessaging.sendBottomSheet(item.title, item.data)
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(all = 16.dp)
        ) {
            Text(
                text = stringResource(item.title),
                style = quoteElementTextStyle(QuoteElement.STAT_LABEL, MaterialTheme.typography.labelMedium)
            )
            Text(
                modifier = Modifier.padding(top = 8.dp),
                text = item.data,
                style = quoteElementTextStyle(QuoteElement.STAT_VALUE, MaterialTheme.typography.bodyLarge),
                color = quoteElementColour(QuoteElement.STAT_VALUE).takeOrElse { MaterialTheme.colorScheme.onSurfaceVariant }
            )
        }
    }
}
