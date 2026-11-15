package com.postpole.spoonfile.ui.importer

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.postpole.spoonfile.R
import com.postpole.spoonfile.ui.recipe.Field
import com.postpole.spoonfile.ui.theme.RecipePhotoShape
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The shared editable recipe form, fed by an imported page. Nothing is saved until Save is tapped. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ImportPreviewScreen(
    form: ImportPreviewForm,
    photo: ByteArray?,
    onEdit: ((ImportPreviewForm) -> ImportPreviewForm) -> Unit,
    onToggleCategory: (String) -> Unit,
    onRemovePhoto: () -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.import_preview_title)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.cancel))
                    }
                },
                actions = {
                    TextButton(onClick = onSave, enabled = !form.saving) { Text(stringResource(R.string.save)) }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PhotoPreview(photo, Modifier.widthIn(max = 328.dp).fillMaxWidth().height(112.dp).align(Alignment.CenterHorizontally))
            if (photo != null) {
                OutlinedButton(onClick = onRemovePhoto, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text(stringResource(R.string.edit_photo_remove))
                }
            }
            if (form.sourceName.isNotBlank()) {
                Text(
                    stringResource(R.string.import_preview_source, form.sourceName),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            OutlinedTextField(
                value = form.title,
                onValueChange = { v -> onEdit { it.copy(title = v, titleError = false) } },
                label = { Text(stringResource(R.string.edit_field_title)) },
                isError = form.titleError,
                supportingText = if (form.titleError) {
                    { Text(stringResource(R.string.edit_title_required)) }
                } else {
                    null
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Field(form.description, R.string.edit_field_description, minLines = 2) { v -> onEdit { it.copy(description = v) } }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Field(form.servings, R.string.edit_field_servings, modifier = Modifier.weight(1f)) { v -> onEdit { it.copy(servings = v) } }
                Field(form.totalMinutes, R.string.edit_field_total, number = true, modifier = Modifier.weight(1f)) { v ->
                    onEdit { it.copy(totalMinutes = v.filter(Char::isDigit)) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Field(form.prepMinutes, R.string.edit_field_prep, number = true, modifier = Modifier.weight(1f)) { v ->
                    onEdit { it.copy(prepMinutes = v.filter(Char::isDigit)) }
                }
                Field(form.cookMinutes, R.string.edit_field_cook, number = true, modifier = Modifier.weight(1f)) { v ->
                    onEdit { it.copy(cookMinutes = v.filter(Char::isDigit)) }
                }
            }
            Field(form.ingredients, R.string.edit_field_ingredients, minLines = 6, hint = R.string.edit_ingredients_hint) { v ->
                onEdit { it.copy(ingredients = v) }
            }
            Field(form.steps, R.string.edit_field_steps, minLines = 6, hint = R.string.edit_steps_hint) { v ->
                onEdit { it.copy(steps = v) }
            }
            Field(form.nutrition, R.string.edit_field_nutrition, minLines = 2) { v -> onEdit { it.copy(nutrition = v) } }
            Field(form.sourceName, R.string.edit_field_source_name) { v -> onEdit { it.copy(sourceName = v) } }
            Field(form.sourceUrl, R.string.edit_field_source_url, url = true) { v -> onEdit { it.copy(sourceUrl = v) } }

            if (form.categories.isNotEmpty()) {
                Text(stringResource(R.string.import_preview_categories), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.import_preview_categories_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    form.categories.forEach { category ->
                        FilterChip(
                            selected = category.ticked,
                            onClick = { onToggleCategory(category.name) },
                            label = { Text(category.name) },
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** Decodes the in-memory photo downsampled for display; a tonal box shows when there is none. */
@Composable
private fun PhotoPreview(photo: ByteArray?, modifier: Modifier) {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, photo) {
        value = photo?.let { bytes -> withContext(Dispatchers.Default) { decodeForPreview(bytes) } }
    }
    Surface(shape = RecipePhotoShape, color = MaterialTheme.colorScheme.secondaryContainer, modifier = modifier.clip(RecipePhotoShape)) {
        val image = bitmap
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = stringResource(R.string.detail_photo_cd),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.import_preview_photo_missing), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

private fun decodeForPreview(bytes: ByteArray): ImageBitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    var sample = 1
    var edge = maxOf(bounds.outWidth, bounds.outHeight)
    while (edge / 2 >= 1000) {
        sample *= 2
        edge /= 2
    }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
}
