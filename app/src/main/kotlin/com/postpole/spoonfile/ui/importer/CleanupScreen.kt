package com.postpole.spoonfile.ui.importer

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.postpole.spoonfile.R
import com.postpole.spoonfile.importer.ocr.LineRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private fun LineRole.labelRes() = when (this) {
    LineRole.TITLE -> R.string.role_title
    LineRole.INGREDIENT -> R.string.role_ingredient
    LineRole.STEP -> R.string.role_step
    LineRole.NOTE -> R.string.role_note
    LineRole.IGNORE -> R.string.role_ignore
}

/**
 * Screen 08: tidy up recognised (or pasted) text. [onAddPage] is null for pasted text, which has no pages.
 * Continue hands the lines on to the import preview.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CleanupScreen(
    viewModel: ScanViewModel,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    onAddPage: (() -> Unit)?,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.cleanup_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    TextButton(onClick = onContinue, enabled = state.lines.isNotEmpty() && !state.busy) {
                        Text(stringResource(R.string.cleanup_continue))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (onAddPage != null) {
                item(key = "pages") {
                    PagesRow(state.pages, canAdd = state.canAddPage, busy = state.busy, onAddPage = onAddPage)
                }
                item(key = "usePhoto") {
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            stringResource(R.string.cleanup_use_photo),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        Switch(checked = state.usePagePhoto, onCheckedChange = viewModel::setUsePagePhoto)
                    }
                }
            }
            if (state.readFailed) {
                item(key = "failed") {
                    Text(
                        stringResource(R.string.cleanup_read_failed),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            if (state.lines.isNotEmpty()) {
                item(key = "hint") {
                    Text(
                        stringResource(R.string.cleanup_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(state.lines, key = { it.id }) { line ->
                LineRow(
                    line = line,
                    expanded = state.expandedId == line.id,
                    editing = state.editingId == line.id,
                    isLast = line.id == state.lines.last().id,
                    viewModel = viewModel,
                )
            }
        }
    }
}

@Composable
private fun PagesRow(pages: List<Uri>, canAdd: Boolean, busy: Boolean, onAddPage: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            items(pages) { uri -> PageThumb(uri) }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onAddPage, enabled = canAdd) { Text(stringResource(R.string.cleanup_add_page)) }
            Text(
                if (busy) stringResource(R.string.cleanup_reading) else stringResource(R.string.cleanup_page_count, pages.size, ScanViewModel.MAX_PAGES),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PageThumb(uri: Uri) {
    val context = LocalContext.current
    val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, uri) {
        value = withContext(Dispatchers.IO) {
            try {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                var sample = 1
                var edge = maxOf(bounds.outWidth, bounds.outHeight)
                while (edge / 2 >= THUMB_EDGE) {
                    sample *= 2
                    edge /= 2
                }
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
                }?.asImageBitmap()
            } catch (_: Exception) {
                null
            }
        }
    }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.size(width = 72.dp, height = 96.dp).clip(RoundedCornerShape(12.dp)),
    ) {
        bitmap?.let {
            Image(
                bitmap = it,
                contentDescription = stringResource(R.string.cleanup_page_cd),
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(width = 72.dp, height = 96.dp),
            )
        }
    }
}

private const val THUMB_EDGE = 300

@Composable
private fun LineRow(line: CleanupLine, expanded: Boolean, editing: Boolean, isLast: Boolean, viewModel: ScanViewModel) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.fillMaxWidth().clickable { viewModel.toggleExpanded(line.id) },
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                RoleChip(line.role)
                Text(
                    line.text,
                    style = MaterialTheme.typography.bodyLarge,
                    textDecoration = if (line.role == LineRole.IGNORE) TextDecoration.LineThrough else null,
                    modifier = Modifier.weight(1f),
                )
            }
            if (expanded) {
                RoleGroup(line.role) { viewModel.setRole(line.id, it) }
                if (editing) EditField(line, viewModel, isLast) else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { viewModel.mergeWithNext(line.id) }, enabled = !isLast) {
                            Text(stringResource(R.string.cleanup_merge))
                        }
                        TextButton(onClick = { viewModel.split(line.id, splitPoint(line.text)) }) {
                            Text(stringResource(R.string.cleanup_split))
                        }
                        IconButton(onClick = { viewModel.startEditing(line.id) }) {
                            Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.cleanup_edit))
                        }
                    }
                }
            }
        }
    }
}

/** Without a cursor to go by, split at the space nearest the middle of the line. */
private fun splitPoint(text: String): Int {
    val mid = text.length / 2
    val before = text.lastIndexOf(' ', mid)
    val after = text.indexOf(' ', mid)
    return when {
        before < 0 && after < 0 -> mid
        before < 0 -> after
        after < 0 -> before
        mid - before <= after - mid -> before
        else -> after
    }
}

@Composable
private fun EditField(line: CleanupLine, viewModel: ScanViewModel, isLast: Boolean) {
    var value by remember(line.id) { mutableStateOf(TextFieldValue(line.text, TextRange(line.text.length))) }
    OutlinedTextField(
        value = value,
        onValueChange = {
            value = it
            viewModel.editText(line.id, it.text)
        },
        label = { Text(stringResource(R.string.cleanup_edit)) },
        modifier = Modifier.fillMaxWidth(),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = { viewModel.mergeWithNext(line.id) }, enabled = !isLast) {
            Text(stringResource(R.string.cleanup_merge))
        }
        // While editing, the split goes where the cursor is.
        TextButton(onClick = { viewModel.split(line.id, value.selection.start) }) {
            Text(stringResource(R.string.cleanup_split))
        }
        TextButton(onClick = viewModel::stopEditing) { Text(stringResource(R.string.cleanup_done)) }
    }
}

@Composable
private fun RoleChip(role: LineRole) {
    val scheme = MaterialTheme.colorScheme
    val (container, content) = when (role) {
        LineRole.TITLE -> scheme.primaryContainer to scheme.onPrimaryContainer
        LineRole.INGREDIENT -> scheme.tertiaryContainer to scheme.onTertiaryContainer
        LineRole.STEP -> scheme.secondaryContainer to scheme.onSecondaryContainer
        LineRole.NOTE -> scheme.surfaceContainerHighest to scheme.onSurface
        LineRole.IGNORE -> Color.Transparent to scheme.onSurfaceVariant
    }
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = container,
        contentColor = content,
        border = if (role == LineRole.IGNORE) BorderStroke(1.dp, scheme.outline) else null,
    ) {
        Text(
            stringResource(role.labelRes()),
            style = MaterialTheme.typography.labelMedium,
            textDecoration = if (role == LineRole.IGNORE) TextDecoration.LineThrough else null,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun RoleGroup(selected: LineRole, onSelect: (LineRole) -> Unit) {
    val roles = LineRole.entries
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        roles.forEachIndexed { index, role ->
            SegmentedButton(
                selected = role == selected,
                onClick = { onSelect(role) },
                shape = SegmentedButtonDefaults.itemShape(index, roles.size),
                icon = {},
                label = {
                    Text(
                        stringResource(role.labelRes()),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        softWrap = false,
                    )
                },
            )
        }
    }
}
