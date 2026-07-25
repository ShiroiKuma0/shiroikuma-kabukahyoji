package com.github.premnirmal.ticker.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.github.premnirmal.ticker.ui.AppMessage.BottomSheetMessage

// 白い熊 fork: the message sheet (what's-new after an update, tutorial, quote data cards) is styled
// like the sister apps' info dialogs — pure black with a yellow border and yellow text.
private val SheetBlack = Color(0xFF000000)
private val SheetYellow = Color(0xFFFFFF00)
private val SheetShape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun BottomSheetWithMessage(
    message: BottomSheetMessage,
    onDismissRequest: () -> Unit = {},
) {
    ModalBottomSheet(
        modifier = Modifier.border(2.dp, SheetYellow, SheetShape),
        shape = SheetShape,
        containerColor = SheetBlack,
        dragHandle = {
            BottomSheetHandle()
        },
        onDismissRequest = onDismissRequest,
    ) {
        ModalBottomSheetWithMessage(message)
    }
}

@Composable
private fun ModalBottomSheetWithMessage(
    message: BottomSheetMessage
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
            text = message.title,
            style = MaterialTheme.typography.titleLarge,
            color = SheetYellow,
        )
        Text(
            modifier = Modifier
                .padding(bottom = 8.dp),
            text = message.message,
            style = MaterialTheme.typography.bodyMedium,
            color = SheetYellow,
        )
    }
}

@Composable
private fun BottomSheetHandle(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.padding(vertical = 8.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = SheetYellow
    ) {
        Box(
            Modifier.size(width = 32.dp, height = 4.dp)
        )
    }
}
