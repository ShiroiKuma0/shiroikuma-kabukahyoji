package com.github.premnirmal.ticker.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

@Composable
fun FontPickerDialog(
  title: String,
  selectedToken: String,
  includeInherit: Boolean = false,
  onDismiss: () -> Unit,
  onSelect: (String) -> Unit,
) {
  val context = LocalContext.current
  val inheritOption = FontOption(FontManager.TOKEN_INHERIT, FontManager.displayNameFor(FontManager.TOKEN_INHERIT))
  var options by remember {
    mutableStateOf(if (includeInherit) listOf(inheritOption) + FontManager.options(context) else FontManager.options(context))
  }

  val importLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.OpenDocument()
  ) { uri ->
    if (uri != null) {
      val name = FontManager.importFont(context, uri)
      if (name != null) {
        options = if (includeInherit) listOf(inheritOption) + FontManager.options(context) else FontManager.options(context)
        onSelect(name)
      }
    }
  }

  Dialog(onDismissRequest = onDismiss) {
    Surface(
      shape = RoundedCornerShape(16.dp),
      color = MaterialTheme.colorScheme.surface,
      tonalElevation = 4.dp,
    ) {
      Column(modifier = Modifier.padding(vertical = 16.dp)) {
        Text(
          text = title,
          style = MaterialTheme.typography.titleLarge,
          modifier = Modifier.padding(horizontal = 20.dp),
        )
        LazyColumn(
          modifier = Modifier
            .padding(top = 8.dp)
            .heightIn(max = 360.dp)
        ) {
          items(options, key = { it.token }) { option ->
            val isSelected = option.token == selectedToken
            Text(
              text = option.displayName,
              style = MaterialTheme.typography.bodyLarge,
              fontFamily = FontManager.fontFamilyFor(context, option.token) ?: FontFamily.Default,
              color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
              modifier = Modifier
                .fillMaxWidth()
                .clickable {
                  onSelect(option.token)
                }
                .then(
                  if (isSelected) {
                    Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                  } else {
                    Modifier
                  }
                )
                .padding(horizontal = 20.dp, vertical = 12.dp),
            )
          }
        }
        TextButton(
          onClick = { importLauncher.launch(arrayOf("*/*")) },
          modifier = Modifier.padding(horizontal = 12.dp),
        ) {
          Text("Add font…")
        }
      }
    }
  }
}
