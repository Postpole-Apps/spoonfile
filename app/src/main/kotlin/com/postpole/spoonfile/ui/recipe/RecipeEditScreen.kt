package com.postpole.spoonfile.ui.recipe

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.postpole.spoonfile.R
import com.postpole.spoonfile.ui.theme.RecipePhotoShape

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RecipeEditScreen(
    onClose: () -> Unit,
    onSaved: (id: Long, isNew: Boolean) -> Unit,
    onOpenPro: () -> Unit,
    viewModel: RecipeEditViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val categories by viewModel.allCategories.collectAsStateWithLifecycle()
    val form = state.form
    var newCategory by rememberSaveable { mutableStateOf("") }

    // System Photo Picker: no storage permission needed.
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.onPhotoPicked(uri)
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is RecipeEditEvent.Saved -> onSaved(event.id, event.isNew)
                RecipeEditEvent.OpenPro -> onOpenPro()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (viewModel.isNew) R.string.edit_new_title else R.string.edit_title)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.close))
                    }
                },
                actions = {
                    TextButton(onClick = viewModel::save, enabled = !state.loading && !state.saving) {
                        Text(stringResource(R.string.save))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Photo
            RecipePhoto(
                file = form.imageFile?.let(viewModel::photoFile),
                shape = RecipePhotoShape,
                modifier = Modifier.fillMaxWidth().widthIn(max = 328.dp).height(112.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                ) {
                    Text(stringResource(if (form.imageFile == null) R.string.photo_choose else R.string.photo_change))
                }
                if (form.imageFile != null) {
                    TextButton(onClick = viewModel::removePhoto) { Text(stringResource(R.string.photo_remove)) }
                }
            }
            if (state.photoError) {
                Text(
                    stringResource(R.string.photo_error),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Field(
                value = form.title,
                label = R.string.field_title,
                onChange = { v -> viewModel.update { it.copy(title = v) } },
                error = if (state.titleError) R.string.field_title_required else null,
                singleLine = true,
            )
            Field(form.description, R.string.field_description, { v -> viewModel.update { it.copy(description = v) } }, minLines = 2)
            Field(
                form.servings, R.string.field_servings, { v -> viewModel.update { it.copy(servings = v) } },
                singleLine = true,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Field(
                    form.prep, R.string.field_prep, { v -> viewModel.update { it.copy(prep = v.digits()) } },
                    modifier = Modifier.weight(1f), singleLine = true, keyboardType = KeyboardType.Number,
                )
                Field(
                    form.cook, R.string.field_cook, { v -> viewModel.update { it.copy(cook = v.digits()) } },
                    modifier = Modifier.weight(1f), singleLine = true, keyboardType = KeyboardType.Number,
                )
                Field(
                    form.total, R.string.field_total, { v -> viewModel.update { it.copy(total = v.digits()) } },
                    modifier = Modifier.weight(1f), singleLine = true, keyboardType = KeyboardType.Number,
                )
            }
            Field(
                form.ingredients, R.string.field_ingredients, { v -> viewModel.update { it.copy(ingredients = v) } },
                hint = R.string.field_ingredients_hint, minLines = 6,
            )
            Field(
                form.steps, R.string.field_steps, { v -> viewModel.update { it.copy(steps = v) } },
                hint = R.string.field_steps_hint, minLines = 6,
            )

            Text(stringResource(R.string.categories_title), style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                categories.forEach { c ->
                    FilterChip(
                        selected = c.category.id in form.categoryIds,
                        onClick = { viewModel.toggleCategory(c.category.id) },
                        label = { Text(c.category.name) },
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = newCategory,
                    onValueChange = { newCategory = it },
                    label = { Text(stringResource(R.string.category_new)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = {
                        viewModel.createCategory(newCategory)
                        newCategory = ""
                    },
                    enabled = newCategory.isNotBlank(),
                ) { Text(stringResource(R.string.category_add)) }
            }

            Text(stringResource(R.string.rate), style = MaterialTheme.typography.titleMedium)
            RatingStars(form.rating, starSize = 28.dp, onChange = viewModel::setRating)

            Field(form.notes, R.string.field_notes, { v -> viewModel.update { it.copy(notes = v) } }, minLines = 3)
            Field(form.nutrition, R.string.field_nutrition, { v -> viewModel.update { it.copy(nutrition = v) } }, minLines = 2)
            Field(
                form.sourceName, R.string.field_source_name, { v -> viewModel.update { it.copy(sourceName = v) } },
                singleLine = true,
            )
            Field(
                form.sourceUrl, R.string.field_source_url, { v -> viewModel.update { it.copy(sourceUrl = v) } },
                singleLine = true, keyboardType = KeyboardType.Uri,
            )
        }
    }
}

private fun String.digits() = filter { it.isDigit() }.take(5)

@Composable
private fun Field(
    value: String,
    label: Int,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    error: Int? = null,
    hint: Int? = null,
    singleLine: Boolean = false,
    minLines: Int = 1,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(label)) },
        isError = error != null,
        supportingText = {
            val message = error ?: hint
            if (message != null) Text(stringResource(message))
        },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else minLines,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = modifier.fillMaxWidth(),
    )
}
