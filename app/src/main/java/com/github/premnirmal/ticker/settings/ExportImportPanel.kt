package com.github.premnirmal.ticker.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.github.premnirmal.tickerwidget.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// The black/yellow palette of the sister apps' export/import dialogs (kxkb / Kōjiki / Arcanechat) —
// deliberately hard-coded, not themed: these dialogs look identical in every 白い熊 fork.
private val EximBlack = Color(0xFF000000)
private val EximYellow = Color(0xFFFFFF00)
private val EximYellowDim = Color(0xFFC8C800)
private val EximWarnRed = Color(0xFFFF5252)

/** A finished/failed export or import, rendered as the yellow-bordered info dialog. */
private sealed interface EximResult {
    data class ExportOk(val fileName: String) : EximResult
    data class ImportOk(val summary: String) : EximResult
    data class Failure(val message: String) : EximResult
}

/**
 * The Export/Import panel of the "白い熊 株価表示 UI" page — Kōjiki's flow in Compose: settable SAF
 * export directory + latest-export line, category checkboxes with Select all, and an
 * Arcanechat-style pill button row (Cancel left; Import, Export right). Success dialogs close the
 * whole chain via [onCloseAll]; failures only dismiss themselves and leave the panel open.
 */
@Composable
@Suppress("LongMethod")
fun ExportImportPanel(
    onDismiss: () -> Unit,
    onCloseAll: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var statusTick by remember { mutableIntStateOf(0) }
    val checked = remember { mutableStateOf(SettingsExport.Cat.entries.toSet()) }
    var result by remember { mutableStateOf<EximResult?>(null) }

    val dirName = remember(statusTick) { SettingsExport.dirDisplayName(context) }
    val lastExport = remember(statusTick) { SettingsExport.lastExportStatus(context) }

    val noneSelectedMsg = stringResource(R.string.eim_none_selected)
    val dirPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            SettingsExport.setDirUri(context, uri)
            statusTick++
        }
    }
    val exportCreate = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) {
            runExport(
                scope = scope,
                context = context,
                write = { name ->
                    context.contentResolver.openOutputStream(uri)?.use { os ->
                        SettingsExport.export(context, checked.value, os)
                    } ?: error("no output stream")
                    name
                },
                onDone = {
                    result = it
                    statusTick++
                },
            )
        }
    }
    val importOpen = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runImport(context, scope, uri, checked.value) { result = it }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = EximBlack,
            border = BorderStroke(2.dp, EximYellow),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = stringResource(R.string.eim_title),
                    color = EximYellow,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Settable export directory (persisted tree URI; tap to pick via SAF).
                Column(
                    modifier = Modifier.padding(top = 14.dp).fillMaxWidth()
                        .border(1.5.dp, EximYellow, RoundedCornerShape(8.dp))
                        .clickable { dirPicker.launch(SettingsExport.dirUri(context)) }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Text(text = stringResource(R.string.eim_dir_label), color = EximYellowDim, fontSize = 12.sp)
                    Text(
                        text = dirName ?: stringResource(R.string.eim_dir_unset),
                        color = if (dirName == null) EximWarnRed else EximYellow,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text(
                    text = lastExport.first,
                    color = if (lastExport.second) EximWarnRed else EximYellowDim,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 8.dp),
                )

                EximDivider()

                val all = checked.value.size == SettingsExport.Cat.entries.size
                CheckRow(
                    label = stringResource(R.string.eim_select_all),
                    checkedState = all,
                    bold = true,
                ) { on ->
                    checked.value = if (on) SettingsExport.Cat.entries.toSet() else emptySet()
                }
                SettingsExport.Cat.entries.forEach { cat ->
                    CheckRow(label = stringResource(cat.labelRes), checkedState = cat in checked.value) { on ->
                        checked.value = if (on) checked.value + cat else checked.value - cat
                    }
                }

                EximDivider()

                // Arcanechat button bar: Cancel separated left; Import + Export grouped right.
                Row(
                    modifier = Modifier.padding(top = 4.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Pill(stringResource(R.string.eim_cancel), onClick = onDismiss)
                    Spacer(modifier = Modifier.weight(1f))
                    Pill(stringResource(R.string.eim_import)) {
                        if (checked.value.isEmpty()) {
                            result = EximResult.Failure(noneSelectedMsg)
                        } else {
                            importOpen.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Pill(stringResource(R.string.eim_export)) {
                        val dir = SettingsExport.exportDir(context)
                        when {
                            checked.value.isEmpty() -> result = EximResult.Failure(noneSelectedMsg)
                            dir == null -> exportCreate.launch(SettingsExport.exportFileName())
                            else -> runExport(
                                scope = scope,
                                context = context,
                                write = { name ->
                                    val file = dir.createFile("application/zip", name)
                                        ?: error("could not create file in folder")
                                    context.contentResolver.openOutputStream(file.uri)?.use { os ->
                                        SettingsExport.export(context, checked.value, os)
                                    } ?: error("no output stream")
                                    name
                                },
                                onDone = {
                                    result = it
                                    statusTick++
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    result?.let { r ->
        EximResultDialog(
            result = r,
            onAcknowledge = {
                result = null
                if (r !is EximResult.Failure) onCloseAll()
            },
            onRestart = { restartApp(context) },
        )
    }
}

/**
 * The yellow-bordered black info dialog. Success variants close the whole chain (info dialog →
 * panel → UI settings page) through [onAcknowledge]; failures only dismiss the info dialog.
 */
@Composable
private fun EximResultDialog(
    result: EximResult,
    onAcknowledge: () -> Unit,
    onRestart: () -> Unit,
) {
    Dialog(
        onDismissRequest = { if (result is EximResult.Failure) onAcknowledge() },
        properties = DialogProperties(
            dismissOnBackPress = result is EximResult.Failure,
            dismissOnClickOutside = result is EximResult.Failure,
        ),
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = EximBlack,
            border = BorderStroke(2.dp, EximYellow),
        ) {
            Column(modifier = Modifier.padding(horizontal = 22.dp, vertical = 20.dp)) {
                val title = when (result) {
                    is EximResult.ExportOk -> stringResource(R.string.eim_export_done_title)
                    is EximResult.ImportOk -> stringResource(R.string.eim_import_done_title)
                    is EximResult.Failure -> null
                }
                title?.let {
                    Text(text = it, color = EximYellow, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                }
                val body = when (result) {
                    is EximResult.ExportOk -> stringResource(R.string.eim_export_done_body, result.fileName)
                    is EximResult.ImportOk -> stringResource(R.string.eim_import_done_body, result.summary)
                    is EximResult.Failure -> result.message
                }
                Text(
                    text = body,
                    color = EximYellow,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = if (title != null) 10.dp else 0.dp),
                )
                Row(
                    modifier = Modifier.padding(top = 16.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Spacer(modifier = Modifier.weight(1f))
                    when (result) {
                        is EximResult.ImportOk -> {
                            Pill(stringResource(R.string.eim_restart_later), onClick = onAcknowledge)
                            Spacer(modifier = Modifier.width(10.dp))
                            Pill(stringResource(R.string.eim_restart_now), onClick = onRestart)
                        }
                        else -> Pill(stringResource(R.string.eim_ok), onClick = onAcknowledge)
                    }
                }
            }
        }
    }
}

/** An Arcanechat-style pill: fully rounded, black fill, thin yellow border, yellow text. */
@Composable
private fun Pill(text: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        border = BorderStroke(1.5.dp, EximYellow),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = EximBlack, contentColor = EximYellow),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp),
    ) {
        Text(text)
    }
}

@Composable
private fun EximDivider() {
    Spacer(
        modifier = Modifier.padding(vertical = 10.dp).fillMaxWidth().height(1.dp).background(EximYellow),
    )
}

@Composable
private fun CheckRow(
    label: String,
    checkedState: Boolean,
    bold: Boolean = false,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onChange(!checkedState) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checkedState,
            onCheckedChange = onChange,
            colors = CheckboxDefaults.colors(
                checkedColor = EximYellow,
                uncheckedColor = EximYellow,
                checkmarkColor = EximBlack,
            ),
        )
        Text(
            text = label,
            color = EximYellow,
            fontSize = 15.sp,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

private fun runExport(
    scope: CoroutineScope,
    context: Context,
    write: (fileName: String) -> String,
    onDone: (EximResult) -> Unit,
) {
    scope.launch {
        val outcome = withContext(Dispatchers.IO) {
            runCatching { write(SettingsExport.exportFileName()) }
        }
        onDone(
            outcome.fold(
                { EximResult.ExportOk(it) },
                { EximResult.Failure(context.getString(R.string.eim_export_fail, it.message ?: "")) },
            )
        )
    }
}

private fun runImport(
    context: Context,
    scope: CoroutineScope,
    uri: Uri,
    cats: Set<SettingsExport.Cat>,
    onDone: (EximResult) -> Unit,
) {
    scope.launch {
        val outcome = withContext(Dispatchers.IO) {
            runCatching {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: error("cannot read file")
                require(SettingsExport.categoriesIn(bytes).isNotEmpty()) {
                    context.getString(R.string.eim_import_none)
                }
                SettingsExport.import(context, bytes, cats)
            }
        }
        onDone(
            outcome.fold(
                { EximResult.ImportOk(it) },
                { EximResult.Failure(context.getString(R.string.eim_import_fail, it.message ?: "")) },
            )
        )
    }
}

/** Full app restart, as in the sister forks: relaunch the launcher task and exit the process. */
private fun restartApp(context: Context) {
    val app = context.applicationContext
    val launch = app.packageManager.getLaunchIntentForPackage(app.packageName) ?: return
    app.startActivity(Intent.makeRestartActivityTask(launch.component))
    Runtime.getRuntime().exit(0)
}
