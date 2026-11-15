package com.postpole.spoonfile.ui.importer

import android.text.format.DateUtils
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.postpole.spoonfile.R
import com.postpole.spoonfile.data.RecipeEntity
import com.postpole.spoonfile.importer.web.SharedTextUrl
import com.postpole.spoonfile.ui.recipe.RecipePhoto
import com.postpole.spoonfile.ui.theme.RecipePhotoShape

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ImportLinkScreen(
    onBack: () -> Unit,
    onOpenRecipe: (Long) -> Unit,
    onSaved: (Long) -> Unit,
    onOpenPro: () -> Unit,
    onTypeItIn: () -> Unit,
    viewModel: ImportLinkViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val link by viewModel.link.collectAsStateWithLifecycle()
    val form by viewModel.form.collectAsStateWithLifecycle()
    val photo by viewModel.photo.collectAsStateWithLifecycle()
    val clipboardUrl by viewModel.clipboardUrl.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val saveFailed = stringResource(R.string.import_save_failed)

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is ImportEvent.Saved -> onSaved(event.id)
                ImportEvent.OpenPro -> onOpenPro()
                ImportEvent.SaveFailed -> Toast.makeText(context, saveFailed, Toast.LENGTH_SHORT).show()
                ImportEvent.TypeItIn -> onTypeItIn()
            }
        }
    }

    // Offer a copied link as a suggestion only; reading it never starts a fetch.
    LaunchedEffect(Unit) {
        val text = clipboard.getClipEntry()?.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
        viewModel.onClipboardText(text)
    }

    when (state) {
        ImportUiState.Preview -> {
            BackHandler(onBack = viewModel::backToInput)
            ImportPreviewScreen(
                form = form,
                photo = photo,
                onEdit = viewModel::edit,
                onToggleCategory = viewModel::toggleCategory,
                onRemovePhoto = viewModel::removePhoto,
                onSave = viewModel::save,
                onClose = viewModel::backToInput,
            )
        }
        else -> Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.import_link_title)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                        }
                    },
                )
            },
        ) { padding ->
            Column(
                Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                when (val s = state) {
                    is ImportUiState.Input, is ImportUiState.Duplicate -> InputContent(
                        link = link,
                        invalid = (s as? ImportUiState.Input)?.invalid == true,
                        clipboardHost = clipboardUrl?.let { SharedTextUrl.host(it) },
                        onLinkChange = viewModel::setLink,
                        onUseClipboard = viewModel::useClipboardLink,
                        onSubmit = viewModel::submit,
                    )
                    ImportUiState.Loading -> {
                        BackHandler(onBack = viewModel::cancel)
                        LoadingContent(onCancel = viewModel::cancel)
                    }
                    is ImportUiState.Failed -> FailedCard(s, onRetry = viewModel::retry)
                    ImportUiState.NoRecipe -> NoRecipeContent(onPasteText = viewModel::usePastedText, onTypeItIn = viewModel::typeItIn)
                    ImportUiState.Preview -> Unit
                }
            }
        }
    }

    (state as? ImportUiState.Duplicate)?.let { dup ->
        DuplicateDialog(
            recipe = dup.recipe,
            loadPhoto = viewModel::loadPhoto,
            onOpen = { onOpenRecipe(dup.recipe.id) },
            onSaveCopy = viewModel::importAnyway,
            onDismiss = viewModel::dismissDuplicate,
        )
    }
}

@Composable
private fun InputContent(
    link: String,
    invalid: Boolean,
    clipboardHost: String?,
    onLinkChange: (String) -> Unit,
    onUseClipboard: () -> Unit,
    onSubmit: () -> Unit,
) {
    Text(
        stringResource(R.string.import_link_hint),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    OutlinedTextField(
        value = link,
        onValueChange = onLinkChange,
        label = { Text(stringResource(R.string.import_link_field)) },
        isError = invalid,
        supportingText = if (invalid) {
            { Text(stringResource(R.string.import_link_invalid)) }
        } else {
            null
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
        keyboardActions = KeyboardActions(onGo = { onSubmit() }),
        modifier = Modifier.fillMaxWidth(),
    )
    if (clipboardHost != null && link.isBlank()) {
        AssistChip(
            onClick = onUseClipboard,
            label = { Text(stringResource(R.string.import_link_clipboard, clipboardHost)) },
        )
    }
    Button(onClick = onSubmit, enabled = link.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.import_link_go))
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LoadingContent(onCancel: () -> Unit) {
    val cd = stringResource(R.string.import_loading_cd)
    Column(
        Modifier.fillMaxWidth().semantics { contentDescription = cd },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        LoadingIndicator()
        Text(stringResource(R.string.import_loading), style = MaterialTheme.typography.titleMedium)
        LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
        OutlinedButton(onClick = onCancel) { Text(stringResource(R.string.import_cancel)) }
    }
    PreviewSkeleton()
}

/** Placeholder blocks that hint at the preview that is on its way. */
@Composable
private fun PreviewSkeleton() {
    val tone = MaterialTheme.colorScheme.surfaceContainerHigh
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Spacer(Modifier.fillMaxWidth().height(112.dp).clip(RecipePhotoShape).background(tone))
        Spacer(Modifier.fillMaxWidth(0.7f).height(24.dp).clip(MaterialTheme.shapes.small).background(tone))
        repeat(4) { Spacer(Modifier.fillMaxWidth().height(16.dp).clip(MaterialTheme.shapes.small).background(tone)) }
    }
}

/** Screen 05. The too-big case reuses the card with its own message and no Retry. */
@Composable
private fun FailedCard(state: ImportUiState.Failed, onRetry: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.import_failed_title), style = MaterialTheme.typography.titleLarge)
            Text(
                when {
                    state.tooBig -> stringResource(R.string.import_too_big_body)
                    state.site != null -> stringResource(R.string.import_failed_body_site, state.site)
                    else -> stringResource(R.string.import_failed_body)
                },
                style = MaterialTheme.typography.bodyLarge,
            )
            if (!state.tooBig) {
                Button(onClick = onRetry) { Text(stringResource(R.string.import_retry)) }
            }
        }
    }
}

@Composable
private fun NoRecipeContent(onPasteText: (String) -> Unit, onTypeItIn: () -> Unit) {
    var showPaste by rememberSaveable { mutableStateOf(false) }
    var text by rememberSaveable { mutableStateOf("") }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.import_no_recipe_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.import_no_recipe_body), style = MaterialTheme.typography.bodyLarge)
            if (showPaste) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(SharedTextUrl.MAX_INPUT_CHARS * 16) },
                    label = { Text(stringResource(R.string.import_paste_field)) },
                    minLines = 6,
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(onClick = { onPasteText(text) }, enabled = text.isNotBlank()) {
                    Text(stringResource(R.string.import_paste_use))
                }
            } else {
                Button(onClick = { showPaste = true }) { Text(stringResource(R.string.import_no_recipe_paste)) }
            }
            OutlinedButton(onClick = onTypeItIn) { Text(stringResource(R.string.import_no_recipe_type)) }
        }
    }
}

/** Screen 06: the same link is already saved. Open is the primary action. */
@Composable
private fun DuplicateDialog(
    recipe: RecipeEntity,
    loadPhoto: suspend (String, Int) -> androidx.compose.ui.graphics.ImageBitmap?,
    onOpen: () -> Unit,
    onSaveCopy: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.import_duplicate_title)) },
        text = {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                RecipePhoto(
                    imageFile = recipe.imageFile,
                    load = loadPhoto,
                    shape = RecipePhotoShape,
                    modifier = Modifier.size(72.dp),
                    maxEdge = 240,
                    contentDescription = stringResource(R.string.detail_photo_cd),
                )
                Column {
                    Text(recipe.title, style = MaterialTheme.typography.titleMedium)
                    val date = DateUtils.formatDateTime(LocalContext.current, recipe.createdAt, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_YEAR)
                    Text(
                        stringResource(R.string.import_duplicate_saved, date),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = { Button(onClick = onOpen) { Text(stringResource(R.string.import_duplicate_open)) } },
        dismissButton = { TextButton(onClick = onSaveCopy) { Text(stringResource(R.string.import_duplicate_copy)) } },
    )
}
