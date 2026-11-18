package com.postpole.spoonfile.ui.importer

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.postpole.spoonfile.R
import com.postpole.spoonfile.importer.ocr.CleanupController
import com.postpole.spoonfile.importer.ocr.CleanupLine
import com.postpole.spoonfile.importer.ocr.LineRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The page-photo part of the cleanup: thumbnails, Add page and the "use as recipe photo" switch. Absent for pasted text. */
class CleanupPages(
    val pages: List<Uri>,
    val usePhoto: Boolean,
    val onUsePhoto: (Boolean) -> Unit,
    val onAddPage: (() -> Unit)?,
    val busy: Boolean,
    val message: String?,
)

/** Screen 08: sort recognised lines into title, ingredients, steps and notes. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CleanupScreen(
    title: String,
    controller: CleanupController,
    pages: CleanupPages?,
    onBack: () -> Unit,
    onContinue: () -> Unit,
) {
    val state by controller.state.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    TextButton(onClick = onContinue, enabled = pages?.busy != true) {
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
            if (pages != null) {
                item { PagesHeader(pages) }
            }
            item {
                Text(
                    stringResource(R.string.cleanup_lines_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(state.lines, key = { it.id }) { line ->
                val index = state.lines.indexOf(line)
                LineRow(
                    line = line,
                    expanded = state.expandedId == line.id,
                    editing = state.editingId == line.id,
                    hasNext = index < state.lines.lastIndex,
                    controller = controller,
                )
            }
        }
    }
}

@Composable
private fun PagesHeader(pages: CleanupPages) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            items(pages.pages.size) { i ->
                PageThumb(pages.pages[i], stringResource(R.string.cleanup_page_cd, i + 1))
            }
            if (pages.onAddPage != null) {
                item {
                    OutlinedButton(onClick = pages.onAddPage, enabled = !pages.busy) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(stringResource(R.string.cleanup_add_page), modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
        if (pages.message != null) {
            Text(pages.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                stringResource(R.string.cleanup_use_photo),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Switch(checked = pages.usePhoto, onCheckedChange = pages.onUsePhoto)
        }
    }
}

@Composable
private fun PageThumb(uri: Uri, description: String) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(null, uri) {
        value = withContext(Dispatchers.IO) { decodeThumb(context, uri) }
    }
    Box(
        Modifier.size(72.dp, 96.dp).clip(RoundedCornerShape(12.dp)).border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)),
    ) {
        bitmap?.let {
            Image(it, contentDescription = description, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
        }
    }
}

private fun decodeThumb(context: android.content.Context, uri: Uri): ImageBitmap? = try {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    var edge = maxOf(bounds.outWidth, bounds.outHeight)
    while (edge / 2 >= THUMB_EDGE) {
        sample *= 2
        edge /= 2
    }
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }?.asImageBitmap()
} catch (_: java.io.IOException) {
    null
} catch (_: SecurityException) {
    null
}

private const val THUMB_EDGE = 300

@Composable
private fun LineRow(
    line: CleanupLine,
    expanded: Boolean,
    editing: Boolean,
    hasNext: Boolean,
    controller: CleanupController,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.clickable(role = Role.Button) { controller.toggleExpanded(line.id) }.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                RoleChip(line.role)
                if (editing) {
                    OutlinedTextField(
                        value = line.text,
                        onValueChange = { controller.edit(line.id, it) },
                        label = { Text(stringResource(R.string.cleanup_line_label)) },
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    Text(
                        line.text,
                        style = MaterialTheme.typography.bodyLarge,
                        textDecoration = if (line.role == LineRole.IGNORE) TextDecoration.LineThrough else null,
                        color = if (line.role == LineRole.IGNORE) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            if (expanded) {
                RoleGroup(line.role) { controller.setRole(line.id, it) }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { controller.mergeWithNext(line.id) }, enabled = hasNext) {
                        Text(stringResource(R.string.cleanup_merge))
                    }
                    TextButton(onClick = { controller.split(line.id) }) { Text(stringResource(R.string.cleanup_split)) }
                    Box(Modifier.weight(1f))
                    if (editing) {
                        TextButton(onClick = controller::finishEdit) { Text(stringResource(R.string.cleanup_edit_done)) }
                    } else {
                        IconButton(onClick = { controller.startEdit(line.id) }) {
                            Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.cleanup_edit))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun roleLabel(role: LineRole): String = stringResource(
    when (role) {
        LineRole.TITLE -> R.string.role_title
        LineRole.INGREDIENT -> R.string.role_ingredient
        LineRole.STEP -> R.string.role_step
        LineRole.NOTE -> R.string.role_note
        LineRole.IGNORE -> R.string.role_ignore
    },
)

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
        border = if (role == LineRole.IGNORE) androidx.compose.foundation.BorderStroke(1.dp, scheme.outline) else null,
    ) {
        Text(
            roleLabel(role),
            style = MaterialTheme.typography.labelMedium,
            textDecoration = if (role == LineRole.IGNORE) TextDecoration.LineThrough else null,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

/** The five roles as one connected button group. */
@Composable
private fun RoleGroup(selected: LineRole, onSelect: (LineRole) -> Unit) {
    val roles = LineRole.entries
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        roles.forEachIndexed { i, role ->
            SegmentedButton(
                selected = role == selected,
                onClick = { onSelect(role) },
                shape = SegmentedButtonDefaults.itemShape(i, roles.size),
                icon = {},
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 2.dp),
            ) {
                Text(roleLabel(role), style = MaterialTheme.typography.labelSmall, maxLines = 1, softWrap = false)
            }
        }
    }
}
