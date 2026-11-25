package com.postpole.spoonfile.ui.importer

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.postpole.spoonfile.R
import com.postpole.spoonfile.data.Limits
import com.postpole.spoonfile.importer.paprika.PaprikaFailure
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Bulk import of a recipe file: pick, preview with checkboxes, then progress and summary. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaprikaImportScreen(
    onBack: () -> Unit,
    onOpenPro: () -> Unit,
    viewModel: PaprikaImportViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Validated by content, not by name or type, so any file can be offered.
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.load(uri)
    }
    var asked by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!asked) {
            asked = true
            picker.launch(arrayOf("*/*"))
        }
    }
    val choose = { picker.launch(arrayOf("*/*")) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.add_import_file)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
        bottomBar = {
            val s = state
            if (s is PaprikaState.Preview) {
                Surface(tonalElevation = 3.dp) {
                    Button(
                        onClick = viewModel::import,
                        enabled = s.ticked.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                    ) {
                        Text(pluralStringResource(R.plurals.file_import_button, s.ticked.size, s.ticked.size))
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val s = state) {
                PaprikaState.Pick -> Message(stringResource(R.string.file_choose_prompt), stringResource(R.string.file_choose_action), choose)
                PaprikaState.Reading -> Message(stringResource(R.string.file_reading), null, {}, progress = true)
                is PaprikaState.Error -> Message(
                    stringResource(
                        when (s.kind) {
                            PaprikaError.NOT_ZIP -> R.string.file_error_not_zip
                            PaprikaError.UNREADABLE -> R.string.file_error_unreadable
                            PaprikaError.EMPTY -> R.string.file_error_empty
                        },
                    ),
                    stringResource(R.string.file_choose_another),
                    choose,
                )
                is PaprikaState.Preview -> PreviewList(s, viewModel, onOpenPro)
                is PaprikaState.Importing -> PreviewPlaceholder()
                is PaprikaState.Done -> PreviewPlaceholder()
            }
        }
    }

    when (val s = state) {
        is PaprikaState.Importing -> AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.file_importing, s.done, s.total)) },
            text = { LinearProgressIndicator(progress = { if (s.total == 0) 0f else s.done.toFloat() / s.total }, modifier = Modifier.fillMaxWidth()) },
            confirmButton = {},
        )
        is PaprikaState.Done -> SummaryDialog(s.summary, onDone = onBack)
        else -> Unit
    }
}

@Composable
private fun PreviewPlaceholder() = Box(Modifier.fillMaxSize())

@Composable
private fun Message(text: String, action: String?, onAction: () -> Unit, progress: Boolean = false) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge)
        if (progress) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (action != null) OutlinedButton(onClick = onAction) { Text(action) }
    }
}

@Composable
private fun PreviewList(state: PaprikaState.Preview, viewModel: PaprikaImportViewModel, onOpenPro: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        item(key = "header") {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    pluralStringResource(R.plurals.file_count, state.newCount, state.newCount),
                    style = MaterialTheme.typography.titleMedium,
                )
                if (state.unreadable > 0) {
                    Text(
                        pluralStringResource(R.plurals.file_unreadable_note, state.unreadable, state.unreadable),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (state.truncated) Text(stringResource(R.string.file_truncated_note), style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (state.overCap) {
            item(key = "banner") {
                Card(
                    Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            stringResource(R.string.file_free_room, Limits.FREE_RECIPE_LIMIT, state.remaining, state.newCount),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        TextButton(onClick = { viewModel.onUnlockPro(); onOpenPro() }) { Text(stringResource(R.string.file_unlock_pro)) }
                    }
                }
            }
        }
        item(key = "clear") {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = viewModel::clearAll, enabled = state.ticked.isNotEmpty()) {
                    Text(stringResource(R.string.file_clear_all))
                }
            }
        }
        items(state.items, key = { it.index }) { item ->
            val checked = item.index in state.ticked
            val canTick = checked || state.isPro || state.ticked.size < state.remaining
            PreviewRow(item, checked, canTick, viewModel) { viewModel.toggle(item.index) }
        }
    }
}

@Composable
private fun PreviewRow(item: PaprikaItem, checked: Boolean, canTick: Boolean, viewModel: PaprikaImportViewModel, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(enabled = canTick, onClick = onToggle).padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() }, enabled = canTick)
        val thumb by produceState<ImageBitmap?>(null, item.index) {
            value = if (item.hasPhoto) withContext(Dispatchers.Default) { viewModel.thumbnail(item.index) } else null
        }
        val shape = RoundedCornerShape(12.dp)
        val tile = Modifier.size(48.dp).clip(shape)
        val image = thumb
        if (image != null) {
            Image(bitmap = image, contentDescription = null, contentScale = ContentScale.Crop, modifier = tile)
        } else if (!item.hasPhoto) {
            Surface(shape = shape, color = MaterialTheme.colorScheme.tertiaryContainer, modifier = tile) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painterResource(R.drawable.ic_restaurant),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
            }
        } else {
            Surface(shape = shape, color = MaterialTheme.colorScheme.secondaryContainer, modifier = tile) {}
        }
        Column(Modifier.weight(1f)) {
            Text(item.title, style = MaterialTheme.typography.bodyLarge)
            if (item.duplicate) {
                Text(
                    stringResource(R.string.file_already_in),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        }
    }
}

@Composable
private fun SummaryDialog(summary: PaprikaSummary, onDone: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(stringResource(R.string.file_summary_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.file_summary_imported, summary.imported))
                Text(stringResource(R.string.file_summary_skipped, summary.skipped))
                if (summary.duplicatesSkipped > 0) {
                    Text(pluralStringResource(R.plurals.file_summary_duplicates, summary.duplicatesSkipped, summary.duplicatesSkipped))
                }
                if (summary.failed > 0) {
                    Text(stringResource(R.string.file_summary_failed, summary.failed))
                    summary.unreadable.forEach { (reason, count) ->
                        Text(stringResource(R.string.file_reason, stringResource(reason.label()), count), style = MaterialTheme.typography.bodyMedium)
                    }
                    if (summary.saveFailed > 0) {
                        Text(
                            stringResource(R.string.file_reason, stringResource(R.string.file_reason_save), summary.saveFailed),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDone) { Text(stringResource(R.string.file_done)) } },
    )
}

private fun PaprikaFailure.label(): Int = when (this) {
    PaprikaFailure.NOT_GZIP -> R.string.file_reason_not_gzip
    PaprikaFailure.TOO_LARGE -> R.string.file_reason_too_large
    PaprikaFailure.INVALID -> R.string.file_reason_invalid
    PaprikaFailure.NO_TITLE -> R.string.file_reason_no_title
}
