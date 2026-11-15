package com.postpole.spoonfile.ui.importer

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.postpole.spoonfile.R
import com.postpole.spoonfile.ui.recipe.RecipeFormFields
import com.postpole.spoonfile.ui.theme.RecipePhotoShape

/** The editable preview of an imported recipe. Nothing is saved until Save is tapped. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportPreviewScreen(
    suggestedCategories: List<String>,
    onClose: () -> Unit,
    viewModel: ImportLinkViewModel,
) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    val photo by viewModel.photo.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val selected by viewModel.selectedSuggestions.collectAsStateWithLifecycle()

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
                    TextButton(onClick = viewModel::save, enabled = !form.saving) { Text(stringResource(R.string.save)) }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            photo?.let { bitmap ->
                Surface(
                    shape = RecipePhotoShape,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.widthIn(max = 328.dp).fillMaxWidth().height(160.dp)
                        .align(Alignment.CenterHorizontally).clip(RecipePhotoShape),
                ) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = stringResource(R.string.detail_photo_cd),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                OutlinedButton(onClick = viewModel::removePhoto, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text(stringResource(R.string.edit_photo_remove))
                }
            }
            if (form.sourceName.isNotBlank()) {
                Text(
                    stringResource(R.string.detail_source, form.sourceName),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            RecipeFormFields(
                form = form,
                categories = categories,
                onEdit = viewModel::edit,
                onTitle = viewModel::setTitle,
                onRating = viewModel::setRating,
                onToggleCategory = viewModel::toggleCategory,
                onCreateCategory = viewModel::createCategory,
                suggestedCategories = suggestedCategories,
                selectedSuggestions = selected,
                onToggleSuggestion = viewModel::toggleSuggestion,
            )
        }
    }
}
