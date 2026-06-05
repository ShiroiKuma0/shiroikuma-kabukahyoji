package com.github.premnirmal.ticker.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.premnirmal.ticker.AppPreferences
import com.github.premnirmal.ticker.ui.ColorPickerDialog
import com.github.premnirmal.ticker.ui.FONT_WEIGHT_OPTIONS
import com.github.premnirmal.ticker.ui.FontManager
import com.github.premnirmal.ticker.ui.FontPickerDialog
import com.github.premnirmal.ticker.ui.ListPreference
import com.github.premnirmal.ticker.ui.SettingsText
import com.github.premnirmal.ticker.ui.TopBar
import com.github.premnirmal.ticker.ui.fontWeightLabel
import com.github.premnirmal.tickerwidget.R
import com.github.premnirmal.tickerwidget.ui.theme.ColourPalette

private const val INDENT_STEP_DP = 48

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UiSettingsScreen(
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
  viewModel: UiSettingsViewModel = hiltViewModel(),
) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  var colourTarget by remember { mutableStateOf<ColourTarget?>(null) }
  var fontTarget by remember { mutableStateOf<FontTarget?>(null) }
  val weightLabels = remember { FONT_WEIGHT_OPTIONS.map { it.label }.toTypedArray() }

  Scaffold(
    modifier = modifier,
    topBar = {
      TopBar(
        text = stringResource(R.string.shiroikuma_ui_category),
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(painter = painterResource(R.drawable.ic_arrow_back), contentDescription = null)
          }
        },
      )
    },
  ) { padding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding)
        .verticalScroll(rememberScrollState()),
    ) {
      // ---- Language ----
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

      // ---- Theme ----
      SectionHeader(stringResource(R.string.ui_section_theme))
      ListPreference(
        modifier = indent(1),
        title = stringResource(R.string.app_theme),
        items = stringArrayResource(R.array.app_themes),
        selected = state.themePref,
        onSelected = viewModel::setThemePref,
      )
      SwitchRow(
        label = stringResource(R.string.ui_dynamic_colour),
        subtitle = stringResource(R.string.ui_dynamic_colour_desc),
        level = 1,
        checked = state.useDynamicColour,
        onChange = viewModel::setUseDynamicColour,
      )

      // ---- Colours ----
      SectionHeader(stringResource(R.string.ui_section_colours))
      SubgroupHeader(stringResource(R.string.ui_subgroup_foundation), 1)
      ColourRow(stringResource(R.string.ui_colour_accent), 2, state.accent) { colourTarget = ColourTarget.ACCENT }
      ColourRow(stringResource(R.string.ui_colour_background), 2, state.background) { colourTarget = ColourTarget.BACKGROUND }
      ColourRow(stringResource(R.string.ui_colour_text), 2, state.text) { colourTarget = ColourTarget.TEXT }
      SubgroupHeader(stringResource(R.string.ui_subgroup_changes), 1)
      ColourRow(stringResource(R.string.ui_colour_gain), 2, state.gain) { colourTarget = ColourTarget.GAIN }
      ColourRow(stringResource(R.string.ui_colour_loss), 2, state.loss) { colourTarget = ColourTarget.LOSS }

      // ---- Typography ----
      SectionHeader(stringResource(R.string.ui_section_typography))
      SubgroupHeader(stringResource(R.string.ui_subgroup_headings), 1)
      ValueRow(stringResource(R.string.ui_font), 2, FontManager.displayNameFor(state.headingFont)) {
        fontTarget = FontTarget.HEADING
      }
      ListPreference(
        modifier = indent(2),
        title = stringResource(R.string.ui_weight),
        items = weightLabels,
        selected = weightIndex(state.headingWeight),
        onSelected = { viewModel.setWeight(FontTarget.HEADING, FONT_WEIGHT_OPTIONS[it].value) },
      )
      SubgroupHeader(stringResource(R.string.ui_subgroup_body), 1)
      ValueRow(stringResource(R.string.ui_font), 2, FontManager.displayNameFor(state.bodyFont)) {
        fontTarget = FontTarget.BODY
      }
      ListPreference(
        modifier = indent(2),
        title = stringResource(R.string.ui_weight),
        items = weightLabels,
        selected = weightIndex(state.bodyWeight),
        onSelected = { viewModel.setWeight(FontTarget.BODY, FONT_WEIGHT_OPTIONS[it].value) },
      )
      SliderRow(
        label = stringResource(R.string.ui_text_size),
        level = 1,
        value = state.textScale,
        onChange = viewModel::setTextScale,
      )

      // ---- Live preview ----
      SubgroupHeader(stringResource(R.string.ui_preview), 1)
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

  colourTarget?.let { target ->
    val current = state.colourFor(target)
    ColorPickerDialog(
      title = stringResource(colourTitle(target)),
      initial = if (current == AppPreferences.COLOUR_UNSET) null else Color(current),
      recentColours = state.recentColours.map { Color(it) },
      onDismiss = { colourTarget = null },
      onUseDefault = {
        viewModel.clearColour(target)
        colourTarget = null
      },
      onPick = {
        viewModel.setColour(target, it.toArgb())
        viewModel.addRecentColour(it.toArgb())
        colourTarget = null
      },
    )
  }

  fontTarget?.let { target ->
    val selected = if (target == FontTarget.HEADING) state.headingFont else state.bodyFont
    FontPickerDialog(
      title = stringResource(R.string.ui_font),
      selectedToken = selected,
      onDismiss = { fontTarget = null },
      onSelect = {
        viewModel.setFont(target, it)
        fontTarget = null
      },
    )
  }
}

private fun UiSettingsState.colourFor(target: ColourTarget): Int = when (target) {
  ColourTarget.ACCENT -> accent
  ColourTarget.BACKGROUND -> background
  ColourTarget.TEXT -> text
  ColourTarget.GAIN -> gain
  ColourTarget.LOSS -> loss
}

private fun colourTitle(target: ColourTarget): Int = when (target) {
  ColourTarget.ACCENT -> R.string.ui_colour_accent
  ColourTarget.BACKGROUND -> R.string.ui_colour_background
  ColourTarget.TEXT -> R.string.ui_colour_text
  ColourTarget.GAIN -> R.string.ui_colour_gain
  ColourTarget.LOSS -> R.string.ui_colour_loss
}

private fun weightIndex(value: Int): Int =
  FONT_WEIGHT_OPTIONS.indexOfFirst { it.value == value }.coerceAtLeast(0)

private data class LanguageOption(val tag: String, val label: String)

// Tags match the app's locale resource qualifiers (values-ja, values-en-rGB, values-pt-rBR, …).
// Language names are shown in their own language by convention; only "System default" is localized.
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

private fun indent(level: Int): Modifier = Modifier.padding(start = (level * INDENT_STEP_DP).dp)

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
private fun ColourRow(label: String, level: Int, value: Int, onClick: () -> Unit) {
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
      subtitle = if (value == AppPreferences.COLOUR_UNSET) {
        stringResource(R.string.ui_colour_default)
      } else {
        "#%06X".format(0xFFFFFF and value)
      },
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

@Composable
private fun SliderRow(label: String, level: Int, value: Float, onChange: (Float) -> Unit) {
  Column(modifier = Modifier.fillMaxWidth().then(indent(level))) {
    SettingsText(modifier = Modifier.fillMaxWidth(), title = label, subtitle = "${(value * 100).toInt()}%")
    Slider(
      modifier = Modifier.padding(horizontal = 16.dp),
      value = value,
      onValueChange = onChange,
      valueRange = 0.8f..1.4f,
      steps = 5,
    )
    Spacer(modifier = Modifier.height(4.dp))
  }
}
