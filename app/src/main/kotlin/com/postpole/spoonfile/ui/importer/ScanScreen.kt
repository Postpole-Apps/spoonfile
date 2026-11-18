package com.postpole.spoonfile.ui.importer

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.postpole.spoonfile.R
import java.io.File

/**
 * Scan a cookbook page: take or choose a photo (up to 4), sort the recognised lines on the cleanup screen,
 * then the import preview and save. The camera app and the system Photo Picker do the capturing, so the app
 * needs neither the CAMERA permission nor any storage permission.
 */
@Composable
fun ScanScreen(
    onBack: () -> Unit,
    onOpenRecipe: (Long) -> Unit,
    onOpenPro: () -> Unit,
    scanViewModel: ScanViewModel = hiltViewModel(),
    importViewModel: ImportLinkViewModel = hiltViewModel(),
) {
    val scan by scanViewModel.state.collectAsStateWithLifecycle()
    val importState by importViewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val saveFailed = stringResource(R.string.import_save_failed)

    LaunchedEffect(scanViewModel) {
        scanViewModel.events.collect { event ->
            when (event) {
                is ScanEvent.Continue -> importViewModel.startFromScan(event.form, event.photo)
            }
        }
    }
    LaunchedEffect(importViewModel) {
        importViewModel.events.collect { event ->
            when (event) {
                is ImportEvent.OpenRecipe -> onOpenRecipe(event.id)
                is ImportEvent.Saved -> onOpenRecipe(event.id)
                ImportEvent.OpenPro -> onOpenPro()
                ImportEvent.TypeItIn -> Unit
                ImportEvent.SaveFailed -> Toast.makeText(context, saveFailed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    var pendingShot by rememberSaveable { mutableStateOf<Uri?>(null) }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        val uri = pendingShot
        pendingShot = null
        if (taken && uri != null) scanViewModel.addPage(uri)
    }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scanViewModel.addPage(uri)
    }
    val onTakePhoto = {
        val uri = newScanUri(context)
        pendingShot = uri
        takePicture.launch(uri)
    }
    val onChoosePhoto = { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }

    val onAddPage: () -> Unit = { onChoosePhoto() }

    if (importState is ImportState.Preview) {
        // Back goes to the cleanup, which still holds the lines.
        BackHandler { importViewModel.cancel() }
        ImportPreviewScreen(
            (importState as ImportState.Preview).suggestedCategories,
            onClose = importViewModel::cancel,
            viewModel = importViewModel,
        )
        return
    }

    if (scan.pages.isEmpty()) {
        ScanStart(onBack = onBack, onTakePhoto = onTakePhoto, onChoosePhoto = onChoosePhoto)
        return
    }

    val message = when {
        scan.recognizing -> stringResource(R.string.scan_reading)
        scan.failed -> stringResource(R.string.scan_failed)
        scan.noText -> stringResource(R.string.scan_no_text)
        scan.pages.size >= ScanViewModel.MAX_PAGES -> stringResource(R.string.cleanup_pages_full)
        else -> null
    }
    CleanupScreen(
        title = stringResource(R.string.cleanup_title),
        controller = scanViewModel.cleanup,
        pages = CleanupPages(
            pages = scan.pages,
            usePhoto = scan.usePagePhoto,
            onUsePhoto = scanViewModel::setUsePagePhoto,
            onAddPage = if (scan.pages.size < ScanViewModel.MAX_PAGES) onAddPage else null,
            busy = scan.recognizing,
            message = message,
        ),
        onBack = onBack,
        onContinue = scanViewModel::continueToPreview,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScanStart(onBack: () -> Unit, onTakePhoto: () -> Unit, onChoosePhoto: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.scan_title)) },
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
            Text(stringResource(R.string.scan_intro), style = MaterialTheme.typography.bodyLarge)
            Button(onClick = onTakePhoto, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.scan_take_photo)) }
            OutlinedButton(onClick = onChoosePhoto, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.scan_choose_photo))
            }
            Text(
                stringResource(R.string.scan_privacy),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** A fresh cache file for the camera app to write into, shared through the FileProvider. */
private fun newScanUri(context: android.content.Context): Uri {
    val dir = File(context.cacheDir, "scans").apply { mkdirs() }
    val file = File.createTempFile("page_", ".jpg", dir)
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}
