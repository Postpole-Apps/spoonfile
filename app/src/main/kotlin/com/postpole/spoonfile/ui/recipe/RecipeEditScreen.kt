package com.postpole.spoonfile.ui.recipe

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.postpole.spoonfile.R
import com.postpole.spoonfile.review.launchInAppReview
import com.postpole.spoonfile.ui.findActivity
import com.postpole.spoonfile.ui.theme.RecipePhotoShape

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RecipeEditScreen(
    onBack: () -> Unit,
    onSaved: (Long) -> Unit,
    onOpenPro: () -> Unit,
    viewModel: RecipeEditViewModel = hiltViewModel(),
) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context.findActivity()
    val photoFailed = stringResource(R.string.edit_photo_failed)
    var newCategory by rememberSaveable { mutableStateOf("") }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.onPhotoPicked { context.contentResolver.openInputStream(uri) }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is RecipeEditEvent.Saved -> {
                    if (event.showReview && activity != null) launchInAppReview(activity)
                    onSaved(event.id)
                }
                RecipeEditEvent.OpenPro -> onOpenPro()
                RecipeEditEvent.PhotoFailed -> Toast.makeText(context, photoFailed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    val leave = { viewModel.discard(onBack) }
    BackHandler(onBack = leave)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (viewModel.isNew) R.string.edit_title_new else R.string.edit_title_existing)) },
                navigationIcon = {
                    IconButton(onClick = leave) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.cancel))
                    }
                },
                actions = {
                    TextButton(onClick = viewModel::save, enabled = form.loaded && !form.saving) {
                        Text(stringResource(R.string.save))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RecipePhoto(
                imageFile = form.imageFile,
                load = viewModel::loadPhoto,
                shape = RecipePhotoShape,
                modifier = Modifier.widthIn(max = 328.dp).fillMaxWidth().height(112.dp).align(Alignment.CenterHorizontally),
                maxEdge = 1000,
                contentDescription = stringResource(R.string.detail_photo_cd),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.align(Alignment.CenterHorizontally)) {
                FilledTonalButton(onClick = {
                    // System Photo Picker: no storage permission needed.
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }) {
                    Text(stringResource(if (form.imageFile == null) R.string.edit_photo_add else R.string.edit_photo_change))
                }
                if (form.imageFile != null) {
                    OutlinedButton(onClick = viewModel::removePhoto) { Text(stringResource(R.string.edit_photo_remove)) }
                }
            }

            OutlinedTextField(
                value = form.title,
                onValueChange = viewModel::setTitle,
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
            Field(form.description, R.string.edit_field_description, minLines = 2) { v -> viewModel.edit { it.copy(description = v) } }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Field(form.servings, R.string.edit_field_servings, number = true, modifier = Modifier.weight(1f)) { v ->
                    viewModel.edit { it.copy(servings = v) }
                }
                Field(form.totalMinutes, R.string.edit_field_total, number = true, modifier = Modifier.weight(1f)) { v ->
                    viewModel.edit { it.copy(totalMinutes = v.filter(Char::isDigit)) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Field(form.prepMinutes, R.string.edit_field_prep, number = true, modifier = Modifier.weight(1f)) { v ->
                    viewModel.edit { it.copy(prepMinutes = v.filter(Char::isDigit)) }
                }
                Field(form.cookMinutes, R.string.edit_field_cook, number = true, modifier = Modifier.weight(1f)) { v ->
                    viewModel.edit { it.copy(cookMinutes = v.filter(Char::isDigit)) }
                }
            }
            Field(form.ingredients, R.string.edit_field_ingredients, minLines = 6, hint = R.string.edit_ingredients_hint) { v ->
                viewModel.edit { it.copy(ingredients = v) }
            }
            Field(form.steps, R.string.edit_field_steps, minLines = 6, hint = R.string.edit_steps_hint) { v ->
                viewModel.edit { it.copy(steps = v) }
            }
            Field(form.notes, R.string.edit_field_notes, minLines = 2) { v -> viewModel.edit { it.copy(notes = v) } }
            Field(form.nutrition, R.string.edit_field_nutrition, minLines = 2) { v -> viewModel.edit { it.copy(nutrition = v) } }
            Field(form.sourceName, R.string.edit_field_source_name) { v -> viewModel.edit { it.copy(sourceName = v) } }
            Field(form.sourceUrl, R.string.edit_field_source_url, url = true) { v -> viewModel.edit { it.copy(sourceUrl = v) } }

            Text(stringResource(R.string.edit_rating), style = MaterialTheme.typography.titleMedium)
            Row {
                (1..5).forEach { star ->
                    val cd = stringResource(R.string.edit_star_cd, star)
                    IconButton(
                        onClick = { viewModel.setRating(if (form.rating == star) 0 else star) },
                        modifier = Modifier.semantics { contentDescription = cd },
                    ) {
                        Icon(
                            Icons.Filled.Star,
                            contentDescription = null,
                            tint = if (star <= form.rating) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outlineVariant,
                        )
                    }
                }
            }

            Text(stringResource(R.string.edit_categories), style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                categories.forEach { category ->
                    FilterChip(
                        selected = category.id in form.categoryIds,
                        onClick = { viewModel.toggleCategory(category.id) },
                        label = { Text(category.name) },
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newCategory,
                    onValueChange = { newCategory = it },
                    label = { Text(stringResource(R.string.edit_new_category)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                FilledTonalButton(
                    onClick = {
                        viewModel.createCategory(newCategory)
                        newCategory = ""
                    },
                    enabled = newCategory.isNotBlank(),
                ) { Text(stringResource(R.string.edit_add_category)) }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun Field(
    value: String,
    label: Int,
    modifier: Modifier = Modifier,
    minLines: Int = 1,
    number: Boolean = false,
    url: Boolean = false,
    hint: Int? = null,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(label)) },
        supportingText = hint?.let { { Text(stringResource(it)) } },
        minLines = minLines,
        singleLine = minLines == 1,
        keyboardOptions = KeyboardOptions(
            keyboardType = when {
                number -> KeyboardType.Decimal
                url -> KeyboardType.Uri
                else -> KeyboardType.Text
            },
        ),
        modifier = modifier.fillMaxWidth(),
    )
}
