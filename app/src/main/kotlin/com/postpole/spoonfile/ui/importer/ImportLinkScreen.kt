package com.postpole.spoonfile.ui.importer

import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.postpole.spoonfile.R
import com.postpole.spoonfile.data.RecipeEntity
import com.postpole.spoonfile.data.RecipeOrigin
import com.postpole.spoonfile.importer.web.SharedTextUrl
import com.postpole.spoonfile.ui.recipe.RecipePhoto
import com.postpole.spoonfile.ui.theme.RecipePhotoShape
import java.text.DateFormat
import java.util.Date

/** Import from a web link: the link box, loading, failure cards, duplicate dialog, then the preview. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportLinkScreen(
    onBack: () -> Unit,
    onOpenRecipe: (Long) -> Unit,
    onTypeItIn: () -> Unit,
    onOpenPro: () -> Unit,
    viewModel: ImportLinkViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val saveFailed = stringResource(R.string.import_save_failed)

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is ImportEvent.OpenRecipe -> onOpenRecipe(event.id)
                is ImportEvent.Saved -> onOpenRecipe(event.id)
                ImportEvent.OpenPro -> onOpenPro()
                ImportEvent.TypeItIn -> onTypeItIn()
                ImportEvent.SaveFailed -> Toast.makeText(context, saveFailed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    val leave = {
        viewModel.discard()
        onBack()
    }
    BackHandler {
        when (state) {
            ImportState.Loading, ImportState.Checking -> viewModel.cancel()
            else -> leave()
        }
    }

    val current = state
    if (current is ImportState.Cleanup) {
        // The pasted text goes through the same cleanup screen the page scan uses.
        val cleanup: ScanViewModel = hiltViewModel()
        val pasted by viewModel.pastedText.collectAsStateWithLifecycle()
        LaunchedEffect(cleanup) { cleanup.startFromText(pasted) }
        LaunchedEffect(cleanup) {
            cleanup.events.collect { event ->
                when (event) {
                    is ScanEvent.Continue -> viewModel.startFromCleanup(event.form, null, RecipeOrigin.PASTE)
                }
            }
        }
        val closeCleanup = {
            cleanup.reset()
            viewModel.closeCleanup()
        }
        BackHandler { closeCleanup() }
        CleanupScreen(
            viewModel = cleanup,
            onBack = closeCleanup,
            onContinue = { cleanup.continueImport(logScan = false) },
            onAddPage = null,
        )
        return
    }
    if (current is ImportState.Preview) {
        ImportPreviewScreen(current.suggestedCategories, onClose = leave, viewModel = viewModel)
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.stub_paste_link)) },
                navigationIcon = {
                    IconButton(onClick = leave) {
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
            when (current) {
                is ImportState.Input, is ImportState.Duplicate -> LinkInput(viewModel, invalid = (current as? ImportState.Input)?.invalid == true)
                ImportState.Checking, ImportState.Loading -> Importing(onCancel = viewModel::cancel)
                is ImportState.LoadFailed -> LoadFailedCard(canRetry = current.canRetry, onRetry = viewModel::retry)
                ImportState.NoRecipe -> NoRecipe(viewModel)
                is ImportState.Preview, ImportState.Cleanup -> Unit
            }
        }
    }

    if (current is ImportState.Duplicate) {
        DuplicateDialog(current.existing, viewModel)
    }
}

@Composable
private fun LinkInput(viewModel: ImportLinkViewModel, invalid: Boolean) {
    val text by viewModel.input.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // Read once when the box opens: offered only if it holds a web link.
    val clipboardUrl = remember { clipboardLink(context) }
    OutlinedTextField(
        value = text,
        onValueChange = viewModel::onInputChange,
        label = { Text(stringResource(R.string.import_link_label)) },
        isError = invalid,
        supportingText = if (invalid) {
            { Text(stringResource(R.string.import_link_invalid)) }
        } else {
            null
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
        keyboardActions = KeyboardActions(onGo = { viewModel.submit() }),
        modifier = Modifier.fillMaxWidth(),
    )
    if (clipboardUrl != null && text.isEmpty()) {
        FilledTonalButton(onClick = { viewModel.onInputChange(clipboardUrl) }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.import_use_copied_link), maxLines = 1)
        }
    }
    Button(onClick = viewModel::submit, enabled = text.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.import_action))
    }
    Text(
        stringResource(R.string.import_nothing_saved_hint),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun clipboardLink(context: Context): String? = try {
    val clip = context.getSystemService(ClipboardManager::class.java)?.primaryClip
    val text = if (clip != null && clip.itemCount > 0) clip.getItemAt(0).text?.toString() else null
    SharedTextUrl.extract(text)
} catch (_: RuntimeException) {
    null
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ColumnScope.Importing(onCancel: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        LoadingIndicator(modifier = Modifier.size(48.dp))
        Text(stringResource(R.string.import_loading_title), style = MaterialTheme.typography.titleMedium)
    }
    LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
    // Skeleton of the preview that will appear.
    SkeletonBar(160.dp)
    SkeletonBar(56.dp)
    SkeletonBar(96.dp)
    SkeletonBar(96.dp)
    OutlinedButton(onClick = onCancel, modifier = Modifier.align(Alignment.CenterHorizontally)) {
        Text(stringResource(R.string.cancel))
    }
}

@Composable
private fun SkeletonBar(height: androidx.compose.ui.unit.Dp) {
    Box(
        Modifier.fillMaxWidth().height(height)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(16.dp)),
    )
}

@Composable
private fun LoadFailedCard(canRetry: Boolean, onRetry: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.import_failed_title), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(if (canRetry) R.string.import_failed_message else R.string.import_too_big_message),
                style = MaterialTheme.typography.bodyLarge,
            )
            if (canRetry) {
                Button(onClick = onRetry, modifier = Modifier.padding(top = 8.dp)) { Text(stringResource(R.string.import_retry)) }
            }
        }
    }
}

@Composable
private fun NoRecipe(viewModel: ImportLinkViewModel) {
    val pasted by viewModel.pastedText.collectAsStateWithLifecycle()
    Text(stringResource(R.string.import_no_recipe_title), style = MaterialTheme.typography.titleLarge)
    Text(stringResource(R.string.import_no_recipe_message), style = MaterialTheme.typography.bodyLarge)
    OutlinedTextField(
        value = pasted,
        onValueChange = viewModel::onPastedTextChange,
        label = { Text(stringResource(R.string.import_page_text_label)) },
        minLines = 5,
        modifier = Modifier.fillMaxWidth(),
    )
    Button(onClick = viewModel::usePastedText, enabled = pasted.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.import_use_text))
    }
    OutlinedButton(onClick = viewModel::typeItIn, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.add_type_it_in))
    }
}

@Composable
private fun DuplicateDialog(existing: RecipeEntity, viewModel: ImportLinkViewModel) {
    val date = remember(existing.createdAt) { DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(existing.createdAt)) }
    AlertDialog(
        onDismissRequest = viewModel::dismissDuplicate,
        title = { Text(stringResource(R.string.dup_title)) },
        text = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                RecipePhoto(
                    imageFile = existing.imageFile,
                    load = viewModel::loadPhoto,
                    shape = RecipePhotoShape,
                    modifier = Modifier.size(72.dp),
                    maxEdge = 240,
                    contentDescription = stringResource(R.string.detail_photo_cd),
                )
                Column {
                    Text(existing.title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.dup_saved, date),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = { Button(onClick = viewModel::openExisting) { Text(stringResource(R.string.dup_open)) } },
        dismissButton = { TextButton(onClick = viewModel::saveCopy) { Text(stringResource(R.string.dup_save_copy)) } },
    )
}
