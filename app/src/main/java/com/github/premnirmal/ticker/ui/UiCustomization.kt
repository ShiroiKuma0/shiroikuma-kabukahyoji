package com.github.premnirmal.ticker.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.material3.Typography
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import com.github.premnirmal.ticker.AppPreferences
import com.github.premnirmal.tickerwidget.ui.theme.AppTypography
import java.io.File

/**
 * Font + typography helpers for the "白い熊 株価表示 UI" customization page.
 *
 * Fonts are referenced by a [token]: "" = system default, [TOKEN_SANS]/[TOKEN_SERIF]/[TOKEN_MONO]
 * for the platform generics, or a filename of an imported `.ttf`/`.otf` stored under
 * `filesDir/fonts/`. Imported fonts are loaded with Compose's [Font] (File) factory so they render
 * in their own glyphs both in the picker and across the app's [Typography].
 */
object FontManager {

  const val TOKEN_SYSTEM = ""
  const val TOKEN_SANS = "@sans"
  const val TOKEN_SERIF = "@serif"
  const val TOKEN_MONO = "@mono"
  const val TOKEN_INHERIT = "@inherit"

  private val FONT_EXTENSIONS = setOf("ttf", "otf")
  private val familyCache = HashMap<String, FontFamily?>()

  fun fontsDir(context: Context): File = File(context.filesDir, "fonts")

  fun importedFonts(context: Context): List<String> =
    fontsDir(context).listFiles()
      ?.filter { it.isFile && it.extension.lowercase() in FONT_EXTENSIONS }
      ?.map { it.name }
      ?.sortedBy { it.lowercase() }
      ?: emptyList()

  /** Returns the stored filename on success, or null if the file is not a usable font. */
  fun importFont(context: Context, uri: Uri): String? {
    val name = displayName(context, uri) ?: return null
    if (name.substringAfterLast('.', "").lowercase() !in FONT_EXTENSIONS) return null
    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
    val safeName = name.replace('/', '_')
    val dir = fontsDir(context).apply { mkdirs() }
    return runCatching {
      File(dir, safeName).writeBytes(bytes)
      familyCache.remove(safeName)
      safeName
    }.getOrNull()
  }

  /** The Compose [FontFamily] for a token, or null to keep the base typography's family. */
  fun fontFamilyFor(context: Context, token: String): FontFamily? = when (token) {
    TOKEN_SYSTEM -> null
    TOKEN_INHERIT -> null
    TOKEN_SANS -> FontFamily.SansSerif
    TOKEN_SERIF -> FontFamily.Serif
    TOKEN_MONO -> FontFamily.Monospace
    else -> familyCache.getOrPut(token) {
      runCatching { FontFamily(Font(File(fontsDir(context), token))) }.getOrNull()
    }
  }

  fun displayNameFor(token: String): String = when (token) {
    TOKEN_SYSTEM -> "System default"
    TOKEN_INHERIT -> "Inherit"
    TOKEN_SANS -> "Sans-serif"
    TOKEN_SERIF -> "Serif"
    TOKEN_MONO -> "Monospace"
    else -> token.substringBeforeLast('.')
  }

  /** All selectable font options: generics first, then imported fonts. */
  fun options(context: Context): List<FontOption> {
    val generics = listOf(
      FontOption(TOKEN_SYSTEM, displayNameFor(TOKEN_SYSTEM)),
      FontOption(TOKEN_SANS, displayNameFor(TOKEN_SANS)),
      FontOption(TOKEN_SERIF, displayNameFor(TOKEN_SERIF)),
      FontOption(TOKEN_MONO, displayNameFor(TOKEN_MONO)),
    )
    val imported = importedFonts(context).map { FontOption(it, displayNameFor(it)) }
    return generics + imported
  }

  private fun displayName(context: Context, uri: Uri): String? {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
      if (c.moveToFirst()) {
        val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (idx >= 0) return c.getString(idx)
      }
    }
    return uri.lastPathSegment?.substringAfterLast('/')
  }
}

data class FontOption(val token: String, val displayName: String)

data class FontWeightOption(val value: Int, val label: String)

val FONT_WEIGHT_OPTIONS = listOf(
  FontWeightOption(0, "Default"),
  FontWeightOption(100, "Thin"),
  FontWeightOption(300, "Light"),
  FontWeightOption(400, "Regular"),
  FontWeightOption(500, "Medium"),
  FontWeightOption(600, "Semibold"),
  FontWeightOption(700, "Bold"),
  FontWeightOption(900, "Black"),
)

fun fontWeightLabel(value: Int): String =
  FONT_WEIGHT_OPTIONS.firstOrNull { it.value == value }?.label ?: "Default"

private fun fontWeightOf(value: Int): FontWeight? = if (value <= 0) null else FontWeight(value)

/** Translate a stored colour pref (UNSET sentinel → [Color.Unspecified]). */
fun Int.toThemeColourOrUnspecified(): Color =
  if (this == AppPreferences.COLOUR_UNSET) Color.Unspecified else Color(this)

/**
 * Rebuilds the app [Typography] from the user's font choices. Display/headline/title styles use the
 * heading font + weight; body/label styles use the body font + weight. Every size and line-height is
 * multiplied by [scale].
 */
fun buildAppTypography(
  context: Context,
  headingToken: String,
  bodyToken: String,
  headingWeight: Int,
  bodyWeight: Int,
  headingColour: Int,
  bodyColour: Int,
  headingScale: Float,
  bodyScale: Float
): Typography {
  val headingFamily = FontManager.fontFamilyFor(context, headingToken)
  val bodyFamily = FontManager.fontFamilyFor(context, bodyToken)
  val hWeight = fontWeightOf(headingWeight)
  val bWeight = fontWeightOf(bodyWeight)
  val hColour = headingColour.toThemeColourOrUnspecified()
  val bColour = bodyColour.toThemeColourOrUnspecified()

  fun TextStyle.applied(family: FontFamily?, weight: FontWeight?, scale: Float, colour: Color): TextStyle = copy(
    fontFamily = family ?: fontFamily,
    fontWeight = weight ?: fontWeight,
    fontSize = if (fontSize != TextUnit.Unspecified) fontSize * scale else fontSize,
    lineHeight = if (lineHeight != TextUnit.Unspecified) lineHeight * scale else lineHeight,
    color = if (colour.isSpecified) colour else color,
  )

  val base = AppTypography
  return base.copy(
    displayLarge = base.displayLarge.applied(headingFamily, hWeight, headingScale, hColour),
    displayMedium = base.displayMedium.applied(headingFamily, hWeight, headingScale, hColour),
    displaySmall = base.displaySmall.applied(headingFamily, hWeight, headingScale, hColour),
    headlineLarge = base.headlineLarge.applied(headingFamily, hWeight, headingScale, hColour),
    headlineMedium = base.headlineMedium.applied(headingFamily, hWeight, headingScale, hColour),
    headlineSmall = base.headlineSmall.applied(headingFamily, hWeight, headingScale, hColour),
    titleLarge = base.titleLarge.applied(headingFamily, hWeight, headingScale, hColour),
    titleMedium = base.titleMedium.applied(headingFamily, hWeight, headingScale, hColour),
    titleSmall = base.titleSmall.applied(headingFamily, hWeight, headingScale, hColour),
    bodyLarge = base.bodyLarge.applied(bodyFamily, bWeight, bodyScale, bColour),
    bodyMedium = base.bodyMedium.applied(bodyFamily, bWeight, bodyScale, bColour),
    bodySmall = base.bodySmall.applied(bodyFamily, bWeight, bodyScale, bColour),
    labelLarge = base.labelLarge.applied(bodyFamily, bWeight, bodyScale, bColour),
    labelMedium = base.labelMedium.applied(bodyFamily, bWeight, bodyScale, bColour),
    labelSmall = base.labelSmall.applied(bodyFamily, bWeight, bodyScale, bColour),
  )
}
