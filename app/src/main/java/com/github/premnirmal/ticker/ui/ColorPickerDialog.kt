package com.github.premnirmal.ticker.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlin.math.min
import kotlin.math.roundToInt

private val PRESET_COLOURS = listOf(
    0xFF000000, 0xFFFFFFFF, 0xFF9E9E9E, 0xFFF44336, 0xFFE91E63, 0xFF9C27B0,
    0xFF673AB7, 0xFF3F51B5, 0xFF2196F3, 0xFF03A9F4, 0xFF00BCD4, 0xFF009688,
    0xFF4CAF50, 0xFF8BC34A, 0xFFCDDC39, 0xFFFFFF00, 0xFFFFC107, 0xFFFF9800,
).map { Color(it) }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorPickerDialog(
    title: String,
    initial: Color?,
    recentColours: List<Color> = emptyList(),
    onDismiss: () -> Unit,
    onUseDefault: () -> Unit,
    onPick: (Color) -> Unit,
) {
    val start = initial ?: MaterialTheme.colorScheme.primary
    var r by remember { mutableIntStateOf((start.red * 255).roundToInt()) }
    var g by remember { mutableIntStateOf((start.green * 255).roundToInt()) }
    var b by remember { mutableIntStateOf((start.blue * 255).roundToInt()) }
    var a by remember { mutableIntStateOf((start.alpha * 255).roundToInt()) }
    var hex by remember { mutableStateOf(toHex(start)) }

    fun setArgb(c: Color) {
        r = (c.red * 255).roundToInt()
        g = (c.green * 255).roundToInt()
        b = (c.blue * 255).roundToInt()
        a = (c.alpha * 255).roundToInt()
        hex = toHex(c)
    }

    val current = Color(r, g, b, a)
    // Recent colours first, then a default palette to fill out the row.
    val swatches = (recentColours + PRESET_COLOURS).distinct().take(18)

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp,
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(text = title, style = MaterialTheme.typography.titleLarge)
                Box(
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                ) {
                    Checkerboard(modifier = Modifier.fillMaxSize())
                    Box(modifier = Modifier.fillMaxSize().background(current))
                }
                ColourSlider("R", r, Color(0xFFD32F2F)) {
                    r = it
                    hex = toHex(Color(r, g, b, a))
                }
                ColourSlider("G", g, Color(0xFF388E3C)) {
                    g = it
                    hex = toHex(Color(r, g, b, a))
                }
                ColourSlider("B", b, Color(0xFF1976D2)) {
                    b = it
                    hex = toHex(Color(r, g, b, a))
                }
                ColourSlider("A", a, MaterialTheme.colorScheme.onSurfaceVariant) { a = it }
                OutlinedTextField(
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .fillMaxWidth(),
                    value = hex,
                    onValueChange = { value ->
                        hex = value
                        parseHex(value)?.let { c ->
                            r = (c.red * 255).roundToInt()
                            g = (c.green * 255).roundToInt()
                            b = (c.blue * 255).roundToInt()
                        }
                    },
                    singleLine = true,
                    label = { Text("Hex (RRGGBB)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                )
                FlowRow(
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    swatches.forEach { swatch ->
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
                                .clickable { setArgb(swatch) }
                        ) {
                            Checkerboard(modifier = Modifier.fillMaxSize())
                            Box(modifier = Modifier.fillMaxSize().background(swatch))
                        }
                    }
                }
                Row(
                    modifier = Modifier
                        .padding(top = 16.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onUseDefault) { Text("Use default") }
                    Box(modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    TextButton(onClick = { onPick(Color(r, g, b, a)) }) { Text("OK") }
                }
            }
        }
    }
}

@Composable
private fun Checkerboard(modifier: Modifier = Modifier) {
    val light = Color(0xFFCCCCCC)
    val dark = Color(0xFF777777)
    Canvas(modifier = modifier) {
        val cell = 8.dp.toPx()
        var y = 0f
        var row = 0
        while (y < size.height) {
            var x = 0f
            var col = 0
            while (x < size.width) {
                drawRect(
                    color = if ((row + col) % 2 == 0) light else dark,
                    topLeft = Offset(x, y),
                    size = Size(min(cell, size.width - x), min(cell, size.height - y)),
                )
                x += cell
                col++
            }
            y += cell
            row++
        }
    }
}

@Composable
private fun ColourSlider(label: String, value: Int, tint: Color, onChange: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .padding(top = 4.dp)
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = tint,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.size(width = 20.dp, height = 24.dp),
        )
        Slider(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp),
            value = value.toFloat(),
            onValueChange = { onChange(it.roundToInt().coerceIn(0, 255)) },
            valueRange = 0f..255f,
        )
        Text(
            text = value.toString().padStart(3, ' '),
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
        )
    }
}

private fun toHex(color: Color): String {
    val argb = color.toArgb()
    return String.format("%06X", 0xFFFFFF and argb)
}

private fun parseHex(text: String): Color? {
    val hex = text.trim().removePrefix("#")
    if (hex.length != 6) return null
    return runCatching { Color(("FF$hex").toLong(16).toInt()) }.getOrNull()
}
