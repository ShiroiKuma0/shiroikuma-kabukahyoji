package com.github.premnirmal.ticker.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.premnirmal.ticker.AppPreferences
import com.github.premnirmal.ticker.ThemePage
import com.github.premnirmal.ticker.detail.QuoteElement
import com.github.premnirmal.ticker.ui.ColorPickerDialog
import com.github.premnirmal.ticker.ui.FONT_WEIGHT_OPTIONS
import com.github.premnirmal.ticker.ui.FontManager
import com.github.premnirmal.ticker.ui.FontPickerDialog
import com.github.premnirmal.ticker.ui.FontWeightOption
import com.github.premnirmal.ticker.ui.TopBar
import com.github.premnirmal.tickerwidget.R
import com.github.premnirmal.tickerwidget.ui.theme.COLOUR_UNSET
import org.koin.androidx.compose.koinViewModel
import kotlin.math.roundToInt

private data class ColourEdit(val page: ThemePage, val attr: String, val title: String)
private data class FontEdit(val page: ThemePage, val attr: String, val includeInherit: Boolean)
private data class LanguageOption(val tag: String, val label: String)

private val LANGUAGE_OPTIONS = listOf(
    LanguageOption("", ""),
    LanguageOption("en", "English"),
    LanguageOption("ja", "日本語"),
)

@Composable
fun UiSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: UiSettingsViewModel = koinViewModel(),
) {
    val version by viewModel.themeVersion.collectAsStateWithLifecycle(initialValue = 0)
    var editingPage by remember { mutableStateOf<ThemePage?>(null) }
    val page = editingPage
    if (page == null) {
        Hub(version, viewModel, onBack, onSelectPage = { editingPage = it }, modifier)
    } else {
        BackHandler { editingPage = null }
        PageEditor(page, version, viewModel, onBack = { editingPage = null }, modifier)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Hub(
    @Suppress("UnusedParameter") version: Int, // forces recomposition on theme edits
    viewModel: UiSettingsViewModel,
    onBack: () -> Unit,
    onSelectPage: (ThemePage) -> Unit,
    modifier: Modifier,
) {
    val context = LocalContext.current
    var showExim by remember { mutableStateOf(false) }
    var eximTick by remember { mutableIntStateOf(0) }
    // The settable export directory is queried for the latest export whenever the page (re)opens.
    val lastExport = remember(eximTick) { SettingsExport.lastExportStatus(context) }

    Scaffold(modifier = modifier, topBar = {
        TopBar(text = stringResource(R.string.shiroikuma_ui_title), navigationIcon = { BackButton(onBack) })
    }) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()),
        ) {
            SectionHeader(stringResource(R.string.eim_title), first = true)
            ExportImportRow(status = lastExport) { showExim = true }
            SectionHeader(stringResource(R.string.ui_section_colours))
            SwitchRow(
                label = stringResource(R.string.ui_dynamic_colour),
                subtitle = stringResource(R.string.ui_dynamic_colour_desc),
                checked = viewModel.useDynamicColour(),
                onChange = viewModel::setUseDynamicColour,
            )
            SectionHeader(stringResource(R.string.ui_language))
            LanguageChips(viewModel)
            SectionHeader(stringResource(R.string.ui_section_pages))
            PageRow(stringResource(R.string.ui_page_global)) { onSelectPage(ThemePage.GLOBAL) }
            PageRow(stringResource(R.string.ui_page_watchlist)) { onSelectPage(ThemePage.WATCHLIST) }
            PageRow(stringResource(R.string.ui_page_quote)) { onSelectPage(ThemePage.QUOTE_DETAIL) }
            PageRow(stringResource(R.string.ui_page_settings)) { onSelectPage(ThemePage.SETTINGS) }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showExim) {
        ExportImportPanel(
            onDismiss = {
                showExim = false
                eximTick++
            },
            onCloseAll = {
                showExim = false
                onBack()
            },
        )
    }
}

@Composable
private fun ExportImportRow(status: Pair<String, Boolean>, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(start = ITEM_INDENT, end = 16.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = stringResource(R.string.eim_desc), style = MaterialTheme.typography.bodyLarge)
            Text(
                text = status.first,
                style = MaterialTheme.typography.bodySmall,
                color = if (status.second) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        Text(
            text = "›",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private val COLOUR_ATTRS = listOf(
    AppPreferences.ATTR_ACCENT to R.string.ui_accent,
    AppPreferences.ATTR_BACKGROUND to R.string.ui_background,
    AppPreferences.ATTR_TEXT to R.string.ui_text,
    AppPreferences.ATTR_GAIN to R.string.ui_gain,
    AppPreferences.ATTR_LOSS to R.string.ui_loss,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PageEditor(
    page: ThemePage,
    @Suppress("UnusedParameter") version: Int, // forces recomposition on theme edits
    viewModel: UiSettingsViewModel,
    onBack: () -> Unit,
    modifier: Modifier,
) {
    var editingColour by remember { mutableStateOf<ColourEdit?>(null) }
    var editingFont by remember { mutableStateOf<FontEdit?>(null) }
    val nonGlobal = page != ThemePage.GLOBAL

    Scaffold(modifier = modifier, topBar = {
        TopBar(text = stringResource(pageTitle(page)), navigationIcon = { BackButton(onBack) })
    }) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()),
        ) {
            SectionHeader(stringResource(R.string.ui_section_colours), first = true)
            COLOUR_ATTRS.forEach { (attr, labelRes) ->
                val label = stringResource(labelRes)
                ColourRow(label, viewModel.colour(page, attr)) { editingColour = ColourEdit(page, attr, label) }
            }

            SectionHeader(stringResource(R.string.ui_heading))
            StyleGroup(
                page, AppPreferences.ATTR_HEADING_FONT, AppPreferences.ATTR_HEADING_WEIGHT,
                AppPreferences.ATTR_HEADING_COLOUR, AppPreferences.ATTR_HEADING_SIZE, nonGlobal, viewModel,
                onEditColour = { editingColour = it }, onEditFont = { editingFont = it },
            )

            SectionHeader(stringResource(R.string.ui_body))
            StyleGroup(
                page, AppPreferences.ATTR_BODY_FONT, AppPreferences.ATTR_BODY_WEIGHT,
                AppPreferences.ATTR_BODY_COLOUR, AppPreferences.ATTR_BODY_SIZE, nonGlobal, viewModel,
                onEditColour = { editingColour = it }, onEditFont = { editingFont = it },
            )

            if (page == ThemePage.QUOTE_DETAIL) {
                SectionHeader(stringResource(R.string.ui_section_elements))
                QuoteElement.entries.forEach { element ->
                    SubHeader(stringResource(elementLabel(element)))
                    StyleGroup(
                        page, "${element.name}_FONT", "${element.name}_WEIGHT",
                        "${element.name}_COLOUR", "${element.name}_SIZE", includeInherit = true, viewModel,
                        onEditColour = { editingColour = it }, onEditFont = { editingFont = it },
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    editingColour?.let { edit ->
        val current = viewModel.colour(edit.page, edit.attr)
        ColorPickerDialog(
            title = edit.title,
            initial = if (current == COLOUR_UNSET) null else Color(current),
            recentColours = viewModel.recentColours().map { Color(it) },
            onDismiss = { editingColour = null },
            onUseDefault = {
                viewModel.setColour(edit.page, edit.attr, COLOUR_UNSET)
                editingColour = null
            },
            onPick = {
                viewModel.setColour(edit.page, edit.attr, it.toArgb())
                editingColour = null
            },
        )
    }
    editingFont?.let { edit ->
        FontPickerDialog(
            title = stringResource(R.string.ui_font),
            selectedToken = viewModel.font(edit.page, edit.attr),
            includeInherit = edit.includeInherit,
            onDismiss = { editingFont = null },
            onSelect = {
                viewModel.setFont(edit.page, edit.attr, it)
                editingFont = null
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StyleGroup(
    page: ThemePage,
    fontAttr: String,
    weightAttr: String,
    colourAttr: String,
    sizeAttr: String,
    includeInherit: Boolean,
    viewModel: UiSettingsViewModel,
    onEditColour: (ColourEdit) -> Unit,
    onEditFont: (FontEdit) -> Unit,
) {
    val fontLabel = stringResource(R.string.ui_font)
    FontRow(fontLabel, FontManager.displayNameFor(viewModel.font(page, fontAttr))) {
        onEditFont(FontEdit(page, fontAttr, includeInherit))
    }

    val weightOptions = if (includeInherit) {
        listOf(FontWeightOption(AppPreferences.INHERIT_WEIGHT, stringResource(R.string.ui_inherit))) + FONT_WEIGHT_OPTIONS
    } else {
        FONT_WEIGHT_OPTIONS
    }
    val selectedWeight = viewModel.weight(page, weightAttr)
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(start = ITEM_INDENT, end = 16.dp, top = 4.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        weightOptions.forEach { option ->
            FilterChip(
                selected = selectedWeight == option.value,
                onClick = { viewModel.setWeight(page, weightAttr, option.value) },
                label = { Text(option.label) },
            )
        }
    }

    val colourLabel = stringResource(R.string.ui_colour)
    ColourRow(
        colourLabel,
        viewModel.colour(page, colourAttr)
    ) { onEditColour(ColourEdit(page, colourAttr, colourLabel)) }

    val size = viewModel.resolvedSize(page, sizeAttr)
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = ITEM_INDENT, end = 16.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = stringResource(R.string.ui_size), style = MaterialTheme.typography.bodyMedium)
        Slider(
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
            value = size,
            valueRange = 0.5f..2.0f,
            onValueChange = { viewModel.setSize(page, sizeAttr, (it * 100).roundToInt() / 100f) },
        )
        Text(text = "${(size * 100).roundToInt() / 100f}×", style = MaterialTheme.typography.bodyMedium)
    }
}

private fun pageTitle(page: ThemePage): Int = when (page) {
    ThemePage.GLOBAL -> R.string.ui_page_global
    ThemePage.WATCHLIST -> R.string.ui_page_watchlist
    ThemePage.QUOTE_DETAIL -> R.string.ui_page_quote
    ThemePage.SETTINGS -> R.string.ui_page_settings
}

private fun elementLabel(element: QuoteElement): Int = when (element) {
    QuoteElement.NAME -> R.string.ui_element_name
    QuoteElement.PRICE -> R.string.ui_element_price
    QuoteElement.CHANGE -> R.string.ui_element_change
    QuoteElement.STAT_LABEL -> R.string.ui_element_stat_label
    QuoteElement.STAT_VALUE -> R.string.ui_element_stat_value
    QuoteElement.NEWS -> R.string.ui_element_news
}

@Composable
private fun BackButton(onBack: () -> Unit) {
    IconButton(onClick = onBack) {
        Icon(painter = painterResource(R.drawable.ic_arrow_back), contentDescription = null)
    }
}

// kxkb page geometry: heading indent, sub-heading indent, item indent (see the kxkb UI page).
private val HEADING_INDENT = 36.dp
private val SUB_INDENT = 54.dp
private val ITEM_INDENT = 72.dp

/**
 * kxkb-style section heading: a full-width hairline separator above the group (skipped for the
 * first section), then a big bold heading underlined only as wide as its own text.
 */
@Composable
private fun SectionHeader(text: String, first: Boolean = false) {
    val accent = MaterialTheme.colorScheme.primary
    Column(modifier = Modifier.fillMaxWidth().padding(top = if (first) 12.dp else 10.dp, bottom = 2.dp)) {
        if (!first) HorizontalDivider(thickness = Dp.Hairline, color = accent)
        Column(modifier = Modifier.padding(start = HEADING_INDENT, top = 8.dp).width(IntrinsicSize.Max)) {
            Text(text = text, color = accent, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Box(
                modifier = Modifier.padding(top = 2.dp).fillMaxWidth().height(2.5.dp).background(accent),
            )
        }
    }
}

/** kxkb-style sub-heading: smaller, deeper-indented, with a thinner text-wide underline. */
@Composable
private fun SubHeader(text: String) {
    val accent = MaterialTheme.colorScheme.primary
    Column(
        modifier = Modifier.padding(start = SUB_INDENT, top = 10.dp, bottom = 2.dp).width(IntrinsicSize.Max),
    ) {
        Text(text = text, color = accent, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Box(modifier = Modifier.padding(top = 2.dp).fillMaxWidth().height(1.5.dp).background(accent))
    }
}

@Composable
private fun PageRow(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(start = ITEM_INDENT, end = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(
            text = "›",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SwitchRow(label: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onChange(!checked) }
            .padding(start = ITEM_INDENT, end = 16.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun ColourRow(label: String, value: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(start = ITEM_INDENT, end = 16.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        val isSet = value != COLOUR_UNSET
        Text(
            text = if (isSet) stringResource(R.string.ui_custom) else stringResource(R.string.ui_default),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 12.dp),
        )
        Box(
            modifier = Modifier.size(28.dp).clip(RoundedCornerShape(6.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
                .then(if (isSet) Modifier.background(Color(value)) else Modifier),
        )
    }
}

@Composable
private fun FontRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(start = ITEM_INDENT, end = 16.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun LanguageChips(viewModel: UiSettingsViewModel) {
    val current = viewModel.language()
    val systemLabel = stringResource(R.string.ui_language_system)
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(start = ITEM_INDENT, end = 16.dp, top = 4.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LANGUAGE_OPTIONS.forEach { option ->
            FilterChip(
                selected = current.equals(option.tag, ignoreCase = true),
                onClick = { viewModel.setLanguage(option.tag) },
                label = { Text(if (option.tag.isEmpty()) systemLabel else option.label) },
            )
        }
    }
}
