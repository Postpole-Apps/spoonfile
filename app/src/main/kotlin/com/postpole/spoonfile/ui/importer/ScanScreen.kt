package com.postpole.spoonfile.ui.importer

import android.content.ActivityNotFoundException
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.postpole.spoonfile.R
import com.postpole.spoonfile.data.RecipeOrigin
import java.io.File
import java.util.UUID

/**
 * Scan a cookbook page: take a photo (system camera, no CAMERA permission) or pick one (system photo picker,
 * no storage permission), tidy the recognised lines, then continue to the import preview.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(
    onBack: () -> Unit,
    onOpenRecipe: (Long) -> Unit,
    onOpenPro: () -> Unit,
    scanViewModel: ScanViewModel = hiltViewModel(),
    importViewModel: ImportLinkViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val scan by scanViewModel.state.collectAsStateWithLifecycle()
    val import by importViewModel.state.collectAsStateWithLifecycle()
    val saveFailed = stringResource(R.string.import_save_failed)
    val noCamera = stringResource(R.string.scan_no_camera)
    var showSources by rememberSaveable { mutableStateOf(false) }
    var pendingPhoto by rememberSaveable { mutableStateOf<String?>(null) }
    var backToCapture by rememberSaveable { mutableStateOf(false) }
    var showPaste by rememberSaveable { mutableStateOf(false) }
    var pasteText by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(scanViewModel) {
        scanViewModel.events.collect { event ->
            when (event) {
                is ScanEvent.Continue -> importViewModel.startFromCleanup(event.form, event.photo, RecipeOrigin.OCR)
            }
        }
    }
    LaunchedEffect(importViewModel) {
        importViewModel.events.collect { event ->
            when (event) {
                is ImportEvent.Saved -> {
                    scanViewModel.discardScan()
                    onOpenRecipe(event.id)
                }
                is ImportEvent.OpenRecipe -> onOpenRecipe(event.id)
                ImportEvent.OpenPro -> onOpenPro()
                ImportEvent.SaveFailed -> Toast.makeText(context, saveFailed, Toast.LENGTH_SHORT).show()
                ImportEvent.TypeItIn -> Unit
            }
        }
    }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = pendingPhoto?.let(Uri::parse)
        pendingPhoto = null
        if (ok && uri != null) {
            backToCapture = false
            scanViewModel.addPage(uri)
        }
    }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            backToCapture = false
            scanViewModel.addPage(uri)
        }
    }
    val launchCamera = {
        showSources = false
        try {
            val dir = File(context.cacheDir, "scan").apply { mkdirs() }
            val file = File(dir, "page-${UUID.randomUUID()}.jpg")
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            pendingPhoto = uri.toString()
            takePicture.launch(uri)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, noCamera, Toast.LENGTH_SHORT).show()
        }
    }
    val launchPicker = {
        showSources = false
        pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    val previewing = import is ImportState.Preview
    // Only the first capture step leaves the flow; it also drops the pages and the cached camera captures.
    val leave = {
        importViewModel.discard()
        scanViewModel.discardScan()
        onBack()
    }
    val captureStep = scan.lines.isEmpty() || backToCapture
    BackHandler {
        when {
            previewing -> importViewModel.closePreview()
            captureStep -> leave()
            else -> backToCapture = true
        }
    }

    if (previewing) {
        ImportPreviewScreen(emptyList(), onClose = importViewModel::closePreview, viewModel = importViewModel)
        return
    }

    if (captureStep) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.scan_title)) },
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
                Text(stringResource(R.string.scan_intro), style = MaterialTheme.typography.bodyLarge)
                Button(onClick = { launchCamera() }, enabled = !scan.busy, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.scan_take_photo))
                }
                OutlinedButton(onClick = { launchPicker() }, enabled = !scan.busy, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.scan_choose_photo))
                }
                if (scan.lines.isNotEmpty()) {
                    TextButton(onClick = { backToCapture = false }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.scan_back_to_text))
                    }
                }
                if (scan.busy) Text(stringResource(R.string.cleanup_reading), style = MaterialTheme.typography.bodyMedium)
                if (scan.readFailed) {
                    Text(
                        stringResource(R.string.cleanup_read_failed),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                    )
                    if (scan.lines.isEmpty()) {
                        TextButton(onClick = { showPaste = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.scan_paste_text))
                        }
                    }
                }
                Text(
                    stringResource(R.string.add_privacy),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    } else {
        CleanupScreen(
            viewModel = scanViewModel,
            onBack = { backToCapture = true },
            onContinue = { scanViewModel.continueImport(logScan = true) },
            onAddPage = { showSources = true },
        )
    }

    if (showPaste) {
        AlertDialog(
            onDismissRequest = { showPaste = false },
            title = { Text(stringResource(R.string.scan_paste_title)) },
            text = {
                OutlinedTextField(
                    value = pasteText,
                    onValueChange = { pasteText = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scanViewModel.startFromText(pasteText)
                        showPaste = false
                        pasteText = ""
                        backToCapture = false
                    },
                    enabled = pasteText.isNotBlank(),
                ) { Text(stringResource(R.string.scan_paste_use)) }
            },
            dismissButton = { TextButton(onClick = { showPaste = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    if (showSources) {
        AlertDialog(
            onDismissRequest = { showSources = false },
            title = { Text(stringResource(R.string.scan_add_source_title)) },
            text = {
                Column {
                    TextButton(onClick = { launchCamera() }) { Text(stringResource(R.string.scan_take_photo)) }
                    TextButton(onClick = { launchPicker() }) { Text(stringResource(R.string.scan_choose_photo)) }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showSources = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
