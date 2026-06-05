package com.github.premnirmal.ticker.settings

import androidx.activity.compose.BackHandler
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.premnirmal.ticker.AppPreferences
import com.github.premnirmal.ticker.ThemePage
import com.github.premnirmal.ticker.ui.ColorPickerDialog
import com.github.premnirmal.ticker.ui.FONT_WEIGHT_OPTIONS
import com.github.premnirmal.ticker.ui.FontManager
import com.github.premnirmal.ticker.ui.FontPickerDialog
import com.github.premnirmal.ticker.ui.ListPreference
import com.github.premnirmal.ticker.ui.PageThemeProvider
import com.github.premnirmal.ticker.ui.SettingsText
import com.github.premnirmal.ticker.ui.TopBar
import com.github.premnirmal.tickerwidget.R
import com.github.premnirmal.tickerwidget.ui.theme.ColourPalette

private const val INDENT_STEP_DP = 48

@Composable
fun UiSettingsScreen(
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
  viewModel: UiSettingsViewModel = hiltViewModel(),
) {
  val version by viewModel.version.collectAsStateWithLifecycle(initialValue = 0)
  var editing by remember { mutableStateOf<ThemePage?>(null) }
  val page = editing
  if (page == null) {
    UiSettingsHub(version, viewModel, onBack, onSelectPage = { editing = it }, modifier)
  } else {
    PageThemeEditor(page, version, viewModel, onBack = { editing = null }, modifier)
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UiSettingsHub(
  version: Int,
  viewModel: UiSettingsViewModel,
  onBack: () -> Unit,
  onSelectPage: (ThemePage) -> Unit,
  modifier: Modifier,
) {
  Scaffold(
    modifier = modifier,
    topBar = {
      TopBar(
        text = stringResource(R.string.shiroikuma_ui_category),
        navigationIcon = { BackButton(onBack) },
      )
    },
  ) { padding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding)
        .verticalScroll(rememberScrollState()),
    ) {
      // Language (global, app-wide)
      SectionHeader(stringResource(R.string.ui_language))
      val systemLabel = stringResource(R.string.ui_language_system)
      val langLabels = LANGUAGE_OPTIONS
        .mapIndexed { index, option -> if (index == 0) systemLabel else option.label }
        .toTypedArray()
      val currentLangTag = AppCompatDelegate.getApplicationLocales().toLanguageTags().substringBefore(',')
      val selectedLang = LANGUAGE_OPTIONS
        .indexOfFirst { it.tag.equals(currentLangTag, ignoreCase = true) }
        .coerceAtLeast(0)
      ListPreference(
        modifier = indent(1),
        title = stringResource(R.string.ui_language),
        items = langLabels,
        selected = selectedLang,
        onSelected = { applyAppLanguage(LANGUAGE_OPTIONS[it].tag) },
      )

      // Theme (global)
      SectionHeader(stringResource(R.string.ui_section_theme))
      ListPreference(
        modifier = indent(1),
        title = stringResource(R.string.app_theme),
        items = stringArrayResource(R.array.app_themes),
        selected = viewModel.themePref(),
        onSelected = viewModel::setThemePref,
      )
      SwitchRow(
        label = stringResource(R.string.ui_dynamic_colour),
        subtitle = stringResource(R.string.ui_dynamic_colour_desc),
        level = 1,
        checked = viewModel.useDynamicColour(),
        onChange = viewModel::setUseDynamicColour,
      )

      // Pages
      SectionHeader(stringResource(R.string.ui_section_pages))
      PageRow(stringResource(R.string.ui_page_global)) { onSelectPage(ThemePage.GLOBAL) }
      PageRow(stringResource(R.string.ui_page_watchlist)) { onSelectPage(ThemePage.WATCHLIST) }
      PageRow(stringResource(R.string.ui_page_quote)) { onSelectPage(ThemePage.QUOTE_DETAIL) }
      PageRow(stringResource(R.string.ui_page_settings)) { onSelectPage(ThemePage.SETTINGS) }
      Spacer(modifier = Modifier.size(24.dp))
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PageThemeEditor(
  page: ThemePage,
  version: Int,
  viewModel: UiSettingsViewModel,
  onBack: () -> Unit,
  modifier: Modifier,
) {
  BackHandler { onBack() }
  val isGlobal = page == ThemePage.GLOBAL
  // (attr, titleRes) of the colour currently being edited.
  var editingColour by remember { mutableStateOf<Pair<String, Int>?>(null) }
  var fontEditAttr by remember { mutableStateOf<String?>(null) }

  Scaffold(
    modifier = modifier,
    topBar = {
      TopBar(
        text = stringResource(pageTitle(page)),
        navigationIcon = { BackButton(onBack) },
      )
    },
  ) { padding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding)
        .verticalScroll(rememberScrollState()),
    ) {
      // Colours
      SectionHeader(stringResource(R.string.ui_section_colours))
      SubgroupHeader(stringResource(R.string.ui_subgroup_foundation), 1)
      ColourRow(stringResource(R.string.ui_colour_accent), 2, viewModel.colour(page, AppPreferences.ATTR_ACCENT), isGlobal) {
        editingColour = AppPreferences.ATTR_ACCENT to R.string.ui_colour_accent
      }
      ColourRow(stringResource(R.string.ui_colour_background), 2, viewModel.colour(page, AppPreferences.ATTR_BACKGROUND), isGlobal) {
        editingColour = AppPreferences.ATTR_BACKGROUND to R.string.ui_colour_background
      }
      SubgroupHeader(stringResource(R.string.ui_subgroup_changes), 1)
      ColourRow(stringResource(R.string.ui_colour_gain), 2, viewModel.colour(page, AppPreferences.ATTR_GAIN), isGlobal) {
        editingColour = AppPreferences.ATTR_GAIN to R.string.ui_colour_gain
      }
      ColourRow(stringResource(R.string.ui_colour_loss), 2, viewModel.colour(page, AppPreferences.ATTR_LOSS), isGlobal) {
        editingColour = AppPreferences.ATTR_LOSS to R.string.ui_colour_loss
      }

      // Typography — each category sets its own font, weight, colour and size.
      SectionHeader(stringResource(R.string.ui_section_typography))
      SubgroupHeader(stringResource(R.string.ui_subgroup_headings), 1)
      TypographyCategory(
        page = page,
        isGlobal = isGlobal,
        fontAttr = AppPreferences.ATTR_HEADING_FONT,
        weightAttr = AppPreferences.ATTR_HEADING_WEIGHT,
        colourAttr = AppPreferences.ATTR_HEADING_COLOUR,
        sizeAttr = AppPreferences.ATTR_HEADING_SIZE,
        viewModel = viewModel,
        onPickColour = { editingColour = AppPreferences.ATTR_HEADING_COLOUR to R.string.ui_colour },
        onPickFont = { fontEditAttr = AppPreferences.ATTR_HEADING_FONT },
      )
      SubgroupHeader(stringResource(R.string.ui_subgroup_body), 1)
      TypographyCategory(
        page = page,
        isGlobal = isGlobal,
        fontAttr = AppPreferences.ATTR_BODY_FONT,
        weightAttr = AppPreferences.ATTR_BODY_WEIGHT,
        colourAttr = AppPreferences.ATTR_BODY_COLOUR,
        sizeAttr = AppPreferences.ATTR_BODY_SIZE,
        viewModel = viewModel,
        onPickColour = { editingColour = AppPreferences.ATTR_BODY_COLOUR to R.string.ui_colour },
        onPickFont = { fontEditAttr = AppPreferences.ATTR_BODY_FONT },
      )

      // Preview, rendered in this page's effective theme
      SubgroupHeader(stringResource(R.string.ui_preview), 1)
      PageThemeProvider(page) {
        val sample = stringResource(R.string.font_sample_text)
        Column(modifier = Modifier.padding(start = (2 * INDENT_STEP_DP).dp, end = 16.dp, top = 4.dp, bottom = 24.dp)) {
          Text(text = sample, style = MaterialTheme.typography.headlineSmall)
          Text(text = sample, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
          Row(modifier = Modifier.padding(top = 8.dp)) {
            Text(text = "+1.23%", style = MaterialTheme.typography.titleMedium, color = ColourPalette.ChangePositive)
            Spacer(modifier = Modifier.width(16.dp))
            Text(text = "-1.23%", style = MaterialTheme.typography.titleMedium, color = ColourPalette.ChangeNegative)
          }
        }
      }
    }
  }

  editingColour?.let { (attr, titleRes) ->
    val current = viewModel.colour(page, attr)
    ColorPickerDialog(
      title = stringResource(titleRes),
      initial = if (current == AppPreferences.COLOUR_UNSET) null else Color(current),
      recentColours = viewModel.recentColours().map { Color(it) },
      onDismiss = { editingColour = null },
      onUseDefault = {
        viewModel.clearColour(page, attr)
        editingColour = null
      },
      onPick = {
        viewModel.setColour(page, attr, it.toArgb())
        editingColour = null
      },
    )
  }

  fontEditAttr?.let { attr ->
    FontPickerDialog(
      title = stringResource(R.string.ui_font),
      selectedToken = viewModel.fontToken(page, attr),
      includeInherit = !isGlobal,
      onDismiss = { fontEditAttr = null },
      onSelect = {
        viewModel.setFont(page, attr, it)
        fontEditAttr = null
      },
    )
  }
}

@Composable
private fun TypographyCategory(
  page: ThemePage,
  isGlobal: Boolean,
  fontAttr: String,
  weightAttr: String,
  colourAttr: String,
  sizeAttr: String,
  viewModel: UiSettingsViewModel,
  onPickColour: () -> Unit,
  onPickFont: () -> Unit,
) {
  ValueRow(stringResource(R.string.ui_font), 2, FontManager.displayNameFor(viewModel.fontToken(page, fontAttr)), onPickFont)
  WeightRow(page, weightAttr, viewModel, isGlobal)
  ColourRow(stringResource(R.string.ui_colour), 2, viewModel.colour(page, colourAttr), isGlobal, onPickColour)
  SizeRow(page, sizeAttr, viewModel, isGlobal)
}

@Composable
private fun WeightRow(page: ThemePage, attr: String, viewModel: UiSettingsViewModel, isGlobal: Boolean) {
  val inheritLabel = stringResource(R.string.ui_inherit)
  val values = (if (!isGlobal) listOf(AppPreferences.INHERIT_WEIGHT) else emptyList()) + FONT_WEIGHT_OPTIONS.map { it.value }
  val labels = (if (!isGlobal) listOf(inheritLabel) else emptyList()) + FONT_WEIGHT_OPTIONS.map { it.label }
  val current = viewModel.weight(page, attr)
  ListPreference(
    modifier = indent(2),
    title = stringResource(R.string.ui_weight),
    items = labels.toTypedArray(),
    selected = values.indexOf(current).coerceAtLeast(0),
    onSelected = { viewModel.setWeight(page, attr, values[it]) },
  )
}

@Composable
private fun SizeRow(page: ThemePage, attr: String, viewModel: UiSettingsViewModel, isGlobal: Boolean) {
  val resolved = viewModel.resolvedSize(page, attr).coerceIn(0.5f, 2.0f)
  val raw = viewModel.size(page, attr)
  var sliderValue by remember(resolved) { mutableFloatStateOf(resolved) }
  val inherited = !isGlobal && raw <= 0f
  val percent = "${(sliderValue * 100).toInt()}%"
  val subtitle = if (inherited) "${stringResource(R.string.ui_inherit)} ($percent)" else percent
  Column(modifier = Modifier.fillMaxWidth().then(indent(2))) {
    SettingsText(modifier = Modifier.fillMaxWidth(), title = stringResource(R.string.ui_size), subtitle = subtitle)
    Slider(
      modifier = Modifier.padding(horizontal = 16.dp),
      value = sliderValue,
      onValueChange = { sliderValue = it },
      onValueChangeFinished = { viewModel.setSize(page, attr, sliderValue) },
      valueRange = 0.5f..2.0f,
      steps = 14,
    )
    Spacer(modifier = Modifier.size(4.dp))
  }
}

private fun pageTitle(page: ThemePage): Int = when (page) {
  ThemePage.GLOBAL -> R.string.ui_page_global
  ThemePage.WATCHLIST -> R.string.ui_page_watchlist
  ThemePage.QUOTE_DETAIL -> R.string.ui_page_quote
  ThemePage.SETTINGS -> R.string.ui_page_settings
}

private fun indent(level: Int): Modifier = Modifier.padding(start = (level * INDENT_STEP_DP).dp)

@Composable
private fun BackButton(onBack: () -> Unit) {
  IconButton(onClick = onBack) {
    Icon(painter = painterResource(R.drawable.ic_arrow_back), contentDescription = null)
  }
}

@Composable
private fun PageRow(title: String, onClick: () -> Unit) {
  SettingsText(
    modifier = Modifier
      .fillMaxWidth()
      .clickable { onClick() }
      .then(indent(1)),
    title = title,
  )
}

@Composable
private fun SectionHeader(text: String) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp),
  ) {
    Text(text = text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    HorizontalDivider(
      modifier = Modifier.padding(top = 4.dp),
      color = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
    )
  }
}

@Composable
private fun SubgroupHeader(text: String, level: Int) {
  Text(
    text = text,
    style = MaterialTheme.typography.titleSmall,
    color = MaterialTheme.colorScheme.primary,
    modifier = Modifier.padding(start = (16 + level * INDENT_STEP_DP).dp, end = 16.dp, top = 12.dp, bottom = 2.dp),
  )
}

@Composable
private fun ColourRow(label: String, level: Int, value: Int, isGlobal: Boolean, onClick: () -> Unit) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clickable { onClick() }
      .then(indent(level)),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    SettingsText(
      modifier = Modifier.weight(1f),
      title = label,
      subtitle = colourSubtitle(value, isGlobal),
    )
    Box(
      modifier = Modifier
        .padding(end = 20.dp)
        .size(28.dp)
        .background(
          color = if (value == AppPreferences.COLOUR_UNSET) Color.Transparent else Color(value),
          shape = CircleShape,
        )
        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
    )
  }
}

@Composable
private fun colourSubtitle(value: Int, isGlobal: Boolean): String =
  if (value == AppPreferences.COLOUR_UNSET) {
    if (isGlobal) stringResource(R.string.ui_colour_default) else stringResource(R.string.ui_inherit)
  } else {
    "#%08X".format(value)
  }

@Composable
private fun ValueRow(label: String, level: Int, value: String, onClick: () -> Unit) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clickable { onClick() }
      .then(indent(level)),
  ) {
    SettingsText(modifier = Modifier.fillMaxWidth(), title = label, subtitle = value)
  }
}

@Composable
private fun SwitchRow(label: String, subtitle: String, level: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clickable { onChange(!checked) }
      .then(indent(level)),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    SettingsText(modifier = Modifier.weight(1f), title = label, subtitle = subtitle)
    Switch(
      modifier = Modifier.padding(end = 16.dp),
      checked = checked,
      onCheckedChange = onChange,
    )
  }
}

private data class LanguageOption(val tag: String, val label: String)

// Tags match the app's locale resource qualifiers (values-ja, values-en-rGB, values-pt-rBR, …).
private val LANGUAGE_OPTIONS = listOf(
  LanguageOption("", "System default"),
  LanguageOption("en", "English"),
  LanguageOption("en-GB", "English (UK)"),
  LanguageOption("de", "Deutsch"),
  LanguageOption("es", "Español"),
  LanguageOption("fr", "Français"),
  LanguageOption("it", "Italiano"),
  LanguageOption("pt-BR", "Português (Brasil)"),
  LanguageOption("ru", "Русский"),
  LanguageOption("ja", "日本語"),
)

private fun applyAppLanguage(tag: String) {
  val locales = if (tag.isEmpty()) {
    LocaleListCompat.getEmptyLocaleList()
  } else {
    LocaleListCompat.forLanguageTags(tag)
  }
  AppCompatDelegate.setApplicationLocales(locales)
}
