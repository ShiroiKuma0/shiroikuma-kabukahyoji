package com.github.premnirmal.ticker.detail

import android.R.color
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.view.MotionEvent
import android.view.ViewGroup.LayoutParams
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells.Adaptive
import androidx.compose.foundation.lazy.grid.GridCells.Fixed
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.layout.DisplayFeature
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.XAxis.XAxisPosition.BOTTOM
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.components.YAxis.AxisDependency.RIGHT
import com.github.mikephil.charting.components.YAxis.YAxisLabelPosition.OUTSIDE_CHART
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.data.LineDataSet.Mode.CUBIC_BEZIER
import com.github.mikephil.charting.listener.ChartTouchListener.ChartGesture
import com.github.mikephil.charting.listener.OnChartGestureListener
import com.github.premnirmal.ticker.model.FetchResult
import com.github.premnirmal.ticker.model.HistoryProvider
import com.github.premnirmal.ticker.model.HistoryProvider.Range
import com.github.premnirmal.ticker.navigation.calculateContentAndNavigationType
import com.github.premnirmal.ticker.network.data.DataPoint
import com.github.premnirmal.ticker.network.data.Quote
import com.github.premnirmal.ticker.news.NewsCard
import com.github.premnirmal.ticker.news.NewsFeedItem.ArticleNewsFeed
import com.github.premnirmal.ticker.news.QuoteDetailViewModel
import com.github.premnirmal.ticker.news.QuoteDetailViewModel.QuoteDetail
import com.github.premnirmal.ticker.news.QuoteDetailViewModel.QuoteWithSummary
import com.github.premnirmal.ticker.portfolio.AlertsActivity
import com.github.premnirmal.ticker.portfolio.DisplaynameActivity
import com.github.premnirmal.ticker.portfolio.HoldingsActivity
import com.github.premnirmal.ticker.portfolio.NotesActivity
import com.github.premnirmal.ticker.portfolio.search.AddSymbolDialog
import com.github.premnirmal.ticker.AppPreferences
import com.github.premnirmal.ticker.ui.ContentType
import com.github.premnirmal.ticker.ui.DateAxisFormatter
import com.github.premnirmal.ticker.ui.FontManager
import com.github.premnirmal.ticker.ui.LocalQuoteElementStyles
import com.github.premnirmal.ticker.ui.QuoteElement
import com.github.premnirmal.ticker.ui.quoteElementColour
import com.github.premnirmal.ticker.ui.quoteElementTextStyle
import com.github.premnirmal.ticker.ui.ErrorState
import com.github.premnirmal.ticker.ui.HourAxisFormatter
import com.github.premnirmal.ticker.ui.LinkText
import com.github.premnirmal.ticker.ui.LinkTextData
import com.github.premnirmal.ticker.ui.LocalAppMessaging
import com.github.premnirmal.ticker.ui.MultilineXAxisRenderer
import com.github.premnirmal.ticker.ui.TextMarkerView
import com.github.premnirmal.ticker.ui.TopBar
import com.github.premnirmal.ticker.ui.ValueAxisFormatter
import com.github.premnirmal.ticker.ui.fadingEdges
import com.github.premnirmal.tickerwidget.R
import com.google.accompanist.adaptive.FoldAwareConfiguration
import com.google.accompanist.adaptive.HorizontalTwoPaneStrategy
import com.google.accompanist.adaptive.TwoPane

@Composable
fun QuoteDetailScreen(
    modifier: Modifier = Modifier,
    widthSizeClass: WindowWidthSizeClass,
    contentType: ContentType?,
    displayFeatures: List<DisplayFeature>,
    quote: Quote,
    viewModel: QuoteDetailViewModel = hiltViewModel()
) {
    val details by viewModel.details.collectAsState(initial = emptyList())
    val articles by viewModel.newsData.collectAsStateWithLifecycle()
    val quoteDetail by viewModel.quote.collectAsStateWithLifecycle(null)
    val quote = quoteDetail?.dataSafe?.quote ?: quote
    QuoteDetailContent(
        modifier, widthSizeClass, contentType, displayFeatures, quote, viewModel, details, articles, quoteDetail
    )

    DisposableEffect(quote.symbol) {
        viewModel.loadQuote(quote.symbol)
        viewModel.fetchAll(quote)
        viewModel.fetchQuoteInRealTime(quote.symbol)
        onDispose {
            viewModel.reset()
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
private fun QuoteDetailContent(
    modifier: Modifier = Modifier,
    widthSizeClass: WindowWidthSizeClass,
    contentType: ContentType?,
    displayFeatures: List<DisplayFeature>,
    quote: Quote,
    viewModel: QuoteDetailViewModel,
    details: List<QuoteDetail>,
    articles: List<ArticleNewsFeed>?,
    quoteDetail: FetchResult<QuoteWithSummary>?,
) {
    val contentType: ContentType = contentType
        ?: calculateContentAndNavigationType(
            widthSizeClass = widthSizeClass, displayFeatures = displayFeatures
        ).second
    var showAddRemoveDialog by remember { mutableStateOf(false) }
    var isInPortfolio by remember(quote, quote.position) { mutableStateOf(viewModel.isInPortfolio(quote.symbol)) }
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val showAddRemoveTooltip by viewModel.showAddRemoveTooltip.collectAsStateWithLifecycle(true)
    val chartData by viewModel.data.collectAsStateWithLifecycle()
    val quoteLayout by viewModel.quoteLayout.collectAsStateWithLifecycle(initialValue = AppPreferences.QUOTE_LAYOUT_DEFAULT)
    val state = rememberLazyGridState()
    var fullscreenChart by remember { mutableStateOf(false) }
    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface),
        topBar = {
            TopBar(
                text = quote.symbol,
                textStyle = quoteElementTextStyle(QuoteElement.TICKER, MaterialTheme.typography.headlineMedium),
                actions = {
                    IconButton(
                        onClick = {
                            if (!isRefreshing) {
                                viewModel.fetchAll(quote)
                            }
                        }
                    ) {
                        if (isRefreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp)
                            )
                        } else {
                            Icon(
                                painter = painterResource(R.drawable.ic_refresh),
                                contentDescription = null,
                            )
                        }
                    }
                }
            )
        },
        snackbarHost = {
            SnackbarHost(hostState = LocalAppMessaging.current.snackbarHostState)
        },
        floatingActionButton = {
            val tooltipPosition = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above)
            val tooltipState = rememberTooltipState(
                initialIsVisible = false,
                isPersistent = true,
            )
            LaunchedEffect(quote.symbol) {
                if (showAddRemoveTooltip) {
                    tooltipState.show()
                    viewModel.addRemoveTooltipShown()
                }
            }
            FloatingActionButton(
                modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars),
                onClick = {
                    showAddRemoveDialog = true
                },
            ) {
                TooltipBox(
                    positionProvider = tooltipPosition,
                    state = tooltipState,
                    tooltip = {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceTint,
                            shape = MaterialTheme.shapes.medium,
                        ) {
                            Text(
                                modifier = Modifier.padding(12.dp),
                                text = stringResource(R.string.add_to_portfolio),
                            )
                        }
                    }
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_add_to_list),
                        contentDescription = null,
                    )
                }
            }
        },
        floatingActionButtonPosition = FabPosition.End,
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            if (quoteLayout != AppPreferences.QUOTE_LAYOUT_GRAPH_TOP && contentType == ContentType.SINGLE_PANE) {
                LazyVerticalGrid(
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .fadingEdges(state = state),
                    columns = Adaptive(150.dp),
                    state = state,
                    contentPadding = padding,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    quoteInfo(quote, chartData, viewModel, onChartDoubleTap = { fullscreenChart = true })
                    quoteDetailsGrid(details)
                    quotePositionsNotesAlerts(quote, isInPortfolio)
                    quoteBackground(quoteDetail)
                    newsItems(articles)
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            } else if (quoteLayout == AppPreferences.QUOTE_LAYOUT_GRAPH_TOP) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 8.dp)
                ) {
                    QuoteHeader(quote, chartData)
                    GraphItem(quote, chartData, viewModel, onChartDoubleTap = { fullscreenChart = true })
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LazyVerticalGrid(
                            modifier = Modifier
                                .weight(1f)
                                .fadingEdges(state = state),
                            columns = Fixed(1),
                            state = state,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            quoteDetailsGrid(details)
                            quotePositionsNotesAlerts(quote, isInPortfolio)
                            quoteBackground(quoteDetail)
                            item { Spacer(modifier = Modifier.height(16.dp)) }
                        }
                        LazyVerticalGrid(
                            modifier = Modifier.weight(1f),
                            columns = Fixed(1),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            newsItems(articles)
                            item { Spacer(modifier = Modifier.height(16.dp)) }
                        }
                    }
                }
            } else {
                TwoPane(
                    strategy = HorizontalTwoPaneStrategy(
                        splitFraction = 1f / 2f,
                    ),
                    displayFeatures = displayFeatures,
                    foldAwareConfiguration = FoldAwareConfiguration.VerticalFoldsOnly,
                    first = {
                        LazyVerticalGrid(
                            modifier = Modifier
                                .padding(horizontal = 8.dp)
                                .fadingEdges(state = state),
                            columns = Adaptive(150.dp),
                            state = state,
                            contentPadding = padding,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            quoteInfo(quote, chartData, viewModel, onChartDoubleTap = { fullscreenChart = true })
                            quoteBackground(quoteDetail)
                            item {
                                Spacer(modifier = Modifier.height(16.dp))
                            }
                        }
                    },
                    second = {
                        LazyVerticalGrid(
                            modifier = Modifier
                                .padding(horizontal = 8.dp)
                                .fadingEdges(state = state),
                            columns = Fixed(1),
                            state = state,
                            contentPadding = padding,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            quoteDetailsGrid(details)
                            quotePositionsNotesAlerts(quote, isInPortfolio)
                            newsItems(articles)
                            item {
                                Spacer(modifier = Modifier.height(16.dp))
                            }
                        }
                    }
                )
            }

            if (showAddRemoveDialog) {
                AddSymbolDialog(
                    symbol = quote.symbol,
                    onDismissRequest = {
                        showAddRemoveDialog = false
                    },
                )
            }

            if (fullscreenChart) {
                FullscreenChartOverlay(
                    quote = quote,
                    chartData = chartData,
                    viewModel = viewModel,
                    onClose = { fullscreenChart = false },
                )
            }
        }
    }
}

@Composable
private fun FullscreenChartOverlay(
    quote: Quote,
    chartData: HistoryProvider.ChartData?,
    viewModel: QuoteDetailViewModel,
    onClose: () -> Unit,
) {
    val range by viewModel.range.collectAsStateWithLifecycle()
    val color = chartData?.changeColour ?: quote.changeColour
    val axesSpec = LocalQuoteElementStyles.current?.specs?.get(QuoteElement.AXES)
    val axesContext = LocalContext.current
    val axisColour = axesSpec?.colour ?: AppPreferences.COLOUR_UNSET
    val axisSizeScale = axesSpec?.size ?: 0f
    val axisTypeface = axesSpec?.fontToken?.takeIf { it != AppPreferences.INHERIT_FONT }
        ?.let { FontManager.androidTypeface(axesContext, it) }
    // A full-window Dialog so fullscreen covers the whole screen even when launched from the
    // inline (list-detail) chart. Double-tap, the close button, or Back all dismiss.
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                AndroidView(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    factory = { context ->
                        createGraphView(context).apply {
                            onChartGestureListener = chartGestureListener(onDoubleTap = onClose, onLongPress = onClose)
                        }
                    },
                    update = { graphView ->
                        updateGraphView(chartData?.dataPoints, graphView, quote, range, color.toArgb(), axisColour, axisSizeScale, axisTypeface)
                    },
                )
                IconButton(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .windowInsetsPadding(WindowInsets.statusBars),
                    onClick = onClose,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_close),
                        contentDescription = null,
                    )
                }
            }
        }
    }
}

private fun LazyGridScope.quoteBackground(quoteDetail: FetchResult<QuoteWithSummary>?) {
    val longBusinessSummary = quoteDetail?.dataSafe?.quoteSummary?.assetProfile?.longBusinessSummary
    val website = quoteDetail?.dataSafe?.quoteSummary?.assetProfile?.website
    if (!longBusinessSummary.isNullOrEmpty() || !website.isNullOrEmpty()) {
        item(span = {
            GridItemSpan(maxLineSpan)
        }) {
            Column {
                if (!website.isNullOrEmpty()) {
                    LinkText(
                        modifier = Modifier.padding(top = 8.dp),
                        linkTextData = listOf(
                            LinkTextData(
                                text = website,
                                tag = website,
                                annotation = website
                            )
                        )
                    )
                }
                if (!longBusinessSummary.isNullOrEmpty()) {
                    Text(
                        modifier = Modifier.padding(top = 8.dp),
                        text = longBusinessSummary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

private fun LazyGridScope.quoteInfo(
    quote: Quote,
    chartData: HistoryProvider.ChartData?,
    viewModel: QuoteDetailViewModel,
    onChartDoubleTap: () -> Unit
) {
    item(span = { GridItemSpan(maxLineSpan) }) {
        QuoteHeader(quote, chartData)
    }
    item(span = { GridItemSpan(maxLineSpan) }) {
        GraphItem(quote, chartData, viewModel, onChartDoubleTap)
    }
}

@Composable
private fun QuoteHeader(quote: Quote, chartData: HistoryProvider.ChartData?) {
    val lastTradePrice = quote.priceFormat.format(chartData?.regularMarketPrice ?: quote.lastTradePrice)
    val change = chartData?.changeStringWithSign() ?: quote.changeStringWithSign()
    val changePercent = chartData?.changePercentStringWithSign() ?: quote.changePercentStringWithSign()
    val changeColour = chartData?.changeColour ?: quote.changeColour
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = quote.name,
            style = quoteElementTextStyle(QuoteElement.NAME, MaterialTheme.typography.bodyLarge),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = lastTradePrice,
            style = quoteElementTextStyle(QuoteElement.PRICE, MaterialTheme.typography.titleLarge),
            color = quoteElementColour(QuoteElement.PRICE).takeOrElse { changeColour },
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            Text(
                modifier = Modifier.padding(end = 4.dp),
                text = change,
                color = quoteElementColour(QuoteElement.CHANGE).takeOrElse { changeColour },
                style = quoteElementTextStyle(QuoteElement.CHANGE, MaterialTheme.typography.bodyMedium),
                textAlign = TextAlign.End
            )
            Text(
                modifier = Modifier.padding(start = 4.dp),
                text = changePercent,
                color = quoteElementColour(QuoteElement.CHANGE).takeOrElse { changeColour },
                style = quoteElementTextStyle(QuoteElement.CHANGE, MaterialTheme.typography.bodyMedium),
                textAlign = TextAlign.Start
            )
        }
    }
}

@Composable
private fun GraphItem(
    quote: Quote,
    graphData: HistoryProvider.ChartData?,
    viewModel: QuoteDetailViewModel,
    onChartDoubleTap: () -> Unit
) {
    Column {
        val range by viewModel.range.collectAsStateWithLifecycle()
        val color = graphData?.changeColour ?: quote.changeColour
        val axesSpec = LocalQuoteElementStyles.current?.specs?.get(QuoteElement.AXES)
        val axesContext = LocalContext.current
        val axisColour = axesSpec?.colour ?: AppPreferences.COLOUR_UNSET
        val axisSizeScale = axesSpec?.size ?: 0f
        val axisTypeface = axesSpec?.fontToken?.takeIf { it != AppPreferences.INHERIT_FONT }
            ?.let { FontManager.androidTypeface(axesContext, it) }
        LaunchedEffect(quote.symbol, range) {
            viewModel.fetchChartData(quote.symbol, range)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp),
            contentAlignment = Alignment.Center
        ) {
            val graphError by viewModel.dataFetchError.collectAsStateWithLifecycle()
            if (graphError == null && graphData?.dataPoints.isNullOrEmpty()) {
                CircularProgressIndicator()
            } else if (graphError != null && graphData?.dataPoints.isNullOrEmpty()) {
                ErrorState(text = stringResource(id = R.string.graph_fetch_failed))
            } else {
                AndroidView(
                    factory = { context ->
                        createGraphView(context).apply {
                            onChartGestureListener = chartGestureListener(
                                onDoubleTap = onChartDoubleTap,
                                onLongPress = { viewModel.toggleQuoteLayout() },
                            )
                        }
                    },
                    update = { graphView ->
                        updateGraphView(graphData?.dataPoints, graphView, quote, range, color.toArgb(), axisColour, axisSizeScale, axisTypeface)
                    },
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            FilterChip(onClick = {
                viewModel.range.value = Range.ONE_DAY
            }, selected = range != Range.ONE_DAY, label = {
                Text(text = stringResource(id = R.string.one_day_short))
            })
            FilterChip(onClick = {
                viewModel.range.value = Range.TWO_WEEKS
            }, selected = range != Range.TWO_WEEKS, label = {
                Text(text = stringResource(id = R.string.two_weeks_short))
            })
            FilterChip(onClick = {
                viewModel.range.value = Range.ONE_MONTH
            }, selected = range != Range.ONE_MONTH, label = {
                Text(text = stringResource(id = R.string.one_month_short))
            })
            FilterChip(onClick = {
                viewModel.range.value = Range.THREE_MONTH
            }, selected = range != Range.THREE_MONTH, label = {
                Text(text = stringResource(id = R.string.three_month_short))
            })
            FilterChip(onClick = {
                viewModel.range.value = Range.ONE_YEAR
            }, selected = range != Range.ONE_YEAR, label = {
                Text(text = stringResource(id = R.string.one_year_short))
            })
            FilterChip(onClick = {
                viewModel.range.value = Range.FIVE_YEARS
            }, selected = range != Range.FIVE_YEARS, label = {
                Text(text = stringResource(id = R.string.five_years_short))
            })
            FilterChip(onClick = {
                viewModel.range.value = Range.MAX
            }, selected = range != Range.MAX, label = {
                Text(text = stringResource(id = R.string.max))
            })
        }
    }
}

private fun LazyGridScope.quoteDetailsGrid(details: List<QuoteDetail>) {
    item(span = {
        GridItemSpan(maxLineSpan)
    }) {
        val list = details
        if (list.isNotEmpty()) {
            val first = list.filterIndexed { index, _ ->
                index % 2 == 0
            }
            val second = list.filterIndexed { index, _ ->
                index % 2 != 0
            }
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(0.5f)) {
                    first.forEach {
                        Box(Modifier.padding(top = 4.dp, bottom = 4.dp, end = 4.dp)) {
                            QuoteDetailCard(item = it)
                        }
                    }
                }
                Column(modifier = Modifier.weight(0.5f)) {
                    second.forEach {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .padding(top = 4.dp, bottom = 4.dp, start = 4.dp)
                        ) {
                            QuoteDetailCard(item = it)
                        }
                    }
                }
            }
        }
    }
}

@Suppress("LongMethod")
private fun LazyGridScope.quotePositionsNotesAlerts(
    quote: Quote,
    isInPortfolio: Boolean
) {
    if (isInPortfolio) {
        item(span = {
            GridItemSpan(maxLineSpan)
        }) {
            val context = LocalContext.current
            val intent = Intent(context, HoldingsActivity::class.java)
            intent.putExtra(HoldingsActivity.TICKER, quote.symbol)
            var holdings by remember(quote.position) {
                mutableStateOf(quote.position)
            }
            val launcher =
                rememberLauncherForActivityResult(
                    ActivityResultContracts.StartActivityForResult()
                ) { result: ActivityResult ->
                    if (result.resultCode == Activity.RESULT_OK) {
                        val intent = result.data
                        holdings = intent?.getParcelableExtra(HoldingsActivity.POSITIONS) ?: holdings
                    }
                }
            Column(
                modifier = Modifier.clickable {
                    launcher.launch(intent)
                }
            ) {
                EditSectionHeader(title = R.string.positions)
                PositionDetailCard(
                    modifier = Modifier.padding(top = 8.dp),
                    quote = quote,
                    position = holdings,
                    onClick = {
                        launcher.launch(intent)
                    },
                )
            }
        }
        item(span = {
            GridItemSpan(maxLineSpan)
        }) {
        }
        item(span = {
            GridItemSpan(maxLineSpan)
        }) {
            val context = LocalContext.current
            val intent = Intent(context, AlertsActivity::class.java)
            intent.putExtra(AlertsActivity.TICKER, quote.symbol)
            var alertAbove by remember {
                mutableFloatStateOf(quote.getAlertAbove())
            }
            var alertBelow by remember {
                mutableFloatStateOf(quote.getAlertBelow())
            }
            val launcher =
                rememberLauncherForActivityResult(
                    ActivityResultContracts.StartActivityForResult()
                ) { result: ActivityResult ->
                    if (result.resultCode == Activity.RESULT_OK) {
                        val intent = result.data
                        alertAbove = intent?.getFloatExtra(AlertsActivity.ALERT_ABOVE, alertAbove) ?: alertAbove
                        alertBelow = intent?.getFloatExtra(AlertsActivity.ALERT_BELOW, alertBelow) ?: alertBelow
                    }
                }
            Column(
                modifier = Modifier.clickable {
                    launcher.launch(intent)
                }
            ) {
                EditSectionHeader(title = R.string.alerts)
                AlertsCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    alertAbove = alertAbove,
                    alertBelow = alertBelow,
                    onClick = {
                        launcher.launch(intent)
                    }
                )
            }
        }
        item(span = {
            GridItemSpan(maxLineSpan)
        }) {
            val context = LocalContext.current
            var notes by remember(quote.properties) { mutableStateOf(quote.properties?.notes ?: "") }
            val intent = Intent(context, NotesActivity::class.java)
            intent.putExtra(NotesActivity.TICKER, quote.symbol)
            val launcher =
                rememberLauncherForActivityResult(
                    ActivityResultContracts.StartActivityForResult()
                ) { result: ActivityResult ->
                    if (result.resultCode == Activity.RESULT_OK) {
                        val intent = result.data
                        notes = intent?.getStringExtra(NotesActivity.NOTES) ?: notes
                    }
                }
            Column(
                modifier = Modifier.clickable {
                    launcher.launch(intent)
                }
            ) {
                EditSectionHeader(title = R.string.notes)
                Text(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 16.dp)
                        .clickable {
                            launcher.launch(intent)
                        },
                    text = notes.ifEmpty { "--" },
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        item(span = {
            GridItemSpan(maxLineSpan)
        }) {
            val context = LocalContext.current
            var displayname by remember(quote.properties) { mutableStateOf(quote.properties?.displayname ?: "") }
            val intent = Intent(context, DisplaynameActivity::class.java)
            intent.putExtra(DisplaynameActivity.TICKER, quote.symbol)
            val launcher =
                rememberLauncherForActivityResult(
                    ActivityResultContracts.StartActivityForResult()
                ) { result: ActivityResult ->
                    if (result.resultCode == Activity.RESULT_OK) {
                        val intent = result.data
                        displayname = intent?.getStringExtra(DisplaynameActivity.DISPLAYNAME) ?: displayname
                    }
                }
            Column(
                modifier = Modifier.clickable {
                    launcher.launch(intent)
                }
            ) {
                EditSectionHeader(title = R.string.displayname)
                Text(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 16.dp)
                        .clickable {
                            launcher.launch(intent)
                        },
                    text = displayname.ifEmpty { "--" },
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun LazyGridScope.newsItems(articles: List<ArticleNewsFeed>?) {
    items(
        count = articles?.size ?: 0,
        span = {
            GridItemSpan(maxLineSpan)
        }
    ) { i ->
        val item = articles!![i].article
        NewsCard(item)
    }
}

private fun updateGraphView(
    dataPoints: List<DataPoint>?,
    graphView: LineChart,
    quote: Quote,
    range: Range,
    color: Int,
    axisColour: Int = AppPreferences.COLOUR_UNSET,
    axisSizeScale: Float = 0f,
    axisTypeface: android.graphics.Typeface? = null
) {
    if (dataPoints.isNullOrEmpty()) {
        graphView.setNoDataText(graphView.context.getString(R.string.no_data))
        graphView.invalidate()
        return
    }
    graphView.setNoDataText("")
    graphView.lineData?.clearValues()
    val series = LineDataSet(dataPoints, quote.symbol)
    series.setDrawHorizontalHighlightIndicator(false)
    series.setDrawValues(false)
    series.setDrawFilled(true)
    series.color = color
    series.fillColor = color
    series.fillAlpha = 150
    series.setDrawCircles(true)
    series.mode = CUBIC_BEZIER
    series.cubicIntensity = 0.07f
    series.lineWidth = 2f
    series.setDrawCircles(false)
    series.highLightColor = Color.GRAY
    val lineData = LineData(series)
    graphView.data = lineData
    val xAxis: XAxis = graphView.xAxis
    val yAxis: YAxis = graphView.axisRight
    if (range == Range.ONE_DAY) {
        xAxis.valueFormatter = HourAxisFormatter()
    } else {
        xAxis.valueFormatter = DateAxisFormatter()
    }
    yAxis.valueFormatter = ValueAxisFormatter()
    xAxis.position = BOTTOM
    val axisTextColour = if (axisColour != AppPreferences.COLOUR_UNSET) axisColour else Color.GRAY
    val axisTextSize = 10f * (if (axisSizeScale > 0f) axisSizeScale else 1f)
    xAxis.textSize = axisTextSize
    yAxis.textSize = axisTextSize
    xAxis.textColor = axisTextColour
    yAxis.textColor = axisTextColour
    if (axisTypeface != null) {
        xAxis.typeface = axisTypeface
        yAxis.typeface = axisTypeface
    }
    xAxis.setLabelCount(5, true)
    yAxis.setLabelCount(5, true)
    yAxis.setPosition(OUTSIDE_CHART)
    xAxis.setDrawAxisLine(true)
    yAxis.setDrawAxisLine(true)
    xAxis.setDrawGridLines(false)
    yAxis.setDrawGridLines(false)
    graphView.invalidate()
}

// Routes double-tap and long-press through MPAndroidChart's own gesture detection so single-tap
// (marker) and drag keep working. Double-tap-to-zoom is already disabled in createGraphView.
private fun chartGestureListener(onDoubleTap: () -> Unit, onLongPress: () -> Unit) = object : OnChartGestureListener {
    override fun onChartGestureStart(me: MotionEvent?, lastPerformedGesture: ChartGesture?) {}
    override fun onChartGestureEnd(me: MotionEvent?, lastPerformedGesture: ChartGesture?) {}
    override fun onChartLongPressed(me: MotionEvent?) { onLongPress() }
    override fun onChartDoubleTapped(me: MotionEvent?) { onDoubleTap() }
    override fun onChartSingleTapped(me: MotionEvent?) {}
    override fun onChartFling(me1: MotionEvent?, me2: MotionEvent?, velocityX: Float, velocityY: Float) {}
    override fun onChartScale(me: MotionEvent?, scaleX: Float, scaleY: Float) {}
    override fun onChartTranslate(me: MotionEvent?, dX: Float, dY: Float) {}
}

private fun createGraphView(context: Context): LineChart {
    val graphView = LineChart(context)
    graphView.layoutParams = LayoutParams(MATCH_PARENT, MATCH_PARENT)
    graphView.isDoubleTapToZoomEnabled = false
    graphView.axisLeft.setDrawGridLines(false)
    graphView.axisLeft.setDrawAxisLine(false)
    graphView.axisLeft.isEnabled = false
    graphView.axisRight.setDrawGridLines(false)
    graphView.axisRight.setDrawAxisLine(true)
    graphView.axisRight.isEnabled = true
    graphView.xAxis.setDrawGridLines(false)
    graphView.setXAxisRenderer(
        MultilineXAxisRenderer(
            graphView.viewPortHandler,
            graphView.xAxis,
            graphView.getTransformer(RIGHT)
        )
    )
    graphView.extraBottomOffset =
        context.resources.getDimension(R.dimen.graph_bottom_offset)
    graphView.legend.isEnabled = false
    graphView.description = null
    val colorAccent = if (VERSION.SDK_INT >= VERSION_CODES.S) {
        ContextCompat.getColor(context, color.system_accent1_600)
    } else {
        ContextCompat.getColor(context, R.color.accent_fallback)
    }
    graphView.setNoDataText("")
    graphView.setNoDataTextColor(colorAccent)
    graphView.marker = TextMarkerView(context)
    return graphView
}
