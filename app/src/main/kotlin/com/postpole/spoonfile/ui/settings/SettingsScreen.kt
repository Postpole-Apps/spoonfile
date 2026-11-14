package com.postpole.spoonfile.ui.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.postpole.spoonfile.BuildConfig
import com.postpole.spoonfile.R
import com.postpole.spoonfile.data.ThemeMode
import java.util.Currency

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenPro: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val isPro by viewModel.isPro.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val currency by viewModel.defaultCurrency.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resolver = context.contentResolver
    val snackbarHostState = remember { SnackbarHostState() }

    var showTheme by rememberSaveable { mutableStateOf(false) }
    var showCurrency by rememberSaveable { mutableStateOf(false) }
    var pendingImport by rememberSaveable { mutableStateOf<String?>(null) }

    // Storage Access Framework: the system picker grants access to the one chosen file, so no
    // storage permission is needed.
    val exportJson = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> uri?.let { viewModel.exportJson(resolver, it) } }
    val exportCsv = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri -> uri?.let { viewModel.exportCsv(resolver, it) } }
    val pickImport = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> pendingImport = uri?.toString() }

    val messages = rememberEventMessages()
    LaunchedEffect(viewModel) {
        viewModel.events.collect { snackbarHostState.showSnackbar(messages(it)) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_settings)) },
                navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.back)) } },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState())) {
            SectionHeader(R.string.settings_section_appearance)
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_theme)) },
                supportingContent = { Text(stringResource(themeMode.label())) },
                modifier = Modifier
                    .heightIn(min = 64.dp)
                    .clickable { showTheme = true },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_currency)) },
                supportingContent = { Text(currency?.let(::currencyLabel) ?: "") },
                modifier = Modifier.clickable { showCurrency = true },
            )
            HorizontalDivider()

            SectionHeader(R.string.settings_section_data)
            ListItem(
                headlineContent = { Text(stringResource(R.string.backup_export_json)) },
                supportingContent = { Text(stringResource(R.string.backup_export_json_hint)) },
                modifier = Modifier.clickable { exportJson.launch(backupFileName("json")) },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.backup_import_json)) },
                supportingContent = { Text(stringResource(R.string.backup_import_json_hint)) },
                modifier = Modifier.clickable { pickImport.launch(arrayOf("application/json", "text/plain", "*/*")) },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.backup_export_csv)) },
                modifier = Modifier.clickable { exportCsv.launch(backupFileName("csv")) },
            )
            HorizontalDivider()

            SectionHeader(R.string.settings_section_pro)
            if (isPro) {
                ListItem(headlineContent = { Text(stringResource(R.string.pro_active)) })
            } else {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.upgrade_to_pro)) },
                    modifier = Modifier.clickable(onClick = onOpenPro),
                )
            }
            ListItem(
                headlineContent = { Text(stringResource(R.string.pro_restore)) },
                modifier = Modifier.clickable(onClick = viewModel::restore),
            )
            HorizontalDivider()

            SectionHeader(R.string.settings_section_about)
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_version)) },
                supportingContent = { Text(BuildConfig.VERSION_NAME) },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_privacy_policy)) },
                modifier = Modifier.clickable {
                    context.openLink(Intent(Intent.ACTION_VIEW, Uri.parse(context.getString(R.string.privacy_policy_url))))
                },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_feedback)) },
                supportingContent = { Text(stringResource(R.string.support_email)) },
                modifier = Modifier.clickable {
                    val email = context.getString(R.string.support_email)
                    context.openLink(
                        Intent(Intent.ACTION_SENDTO).apply {
                            data = Uri.parse("mailto:")
                            putExtra(Intent.EXTRA_EMAIL, arrayOf(email))
                            putExtra(
                                Intent.EXTRA_SUBJECT,
                                context.getString(R.string.settings_feedback_subject, context.getString(R.string.app_name)),
                            )
                        },
                    )
                },
            )
        }
    }

    if (showTheme) {
        ThemeDialog(
            selected = themeMode,
            onSelect = {
                viewModel.setThemeMode(it)
                showTheme = false
            },
            onDismiss = { showTheme = false },
        )
    }

    if (showCurrency) {
        val codes = remember { Currency.getAvailableCurrencies().map { it.currencyCode }.sorted() }
        AlertDialog(
            onDismissRequest = { showCurrency = false },
            title = { Text(stringResource(R.string.settings_currency)) },
            text = {
                LazyColumn {
                    items(codes, key = { it }) { code ->
                        Row(
                            Modifier.fillMaxWidthClickable {
                                viewModel.setDefaultCurrency(code)
                                showCurrency = false
                            },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = code == currency, onClick = null)
                            Text(currencyLabel(code), Modifier.padding(start = 12.dp))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCurrency = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    pendingImport?.let { uriString ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text(stringResource(R.string.backup_import_confirm_title)) },
            text = { Text(stringResource(R.string.backup_import_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.importJson(resolver, Uri.parse(uriString))
                        pendingImport = null
                    },
                ) { Text(stringResource(R.string.backup_import_confirm_action)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingImport = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun SectionHeader(titleRes: Int) {
    Text(
        stringResource(titleRes),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 4.dp),
    )
}

/** Theme picker: choosing a row applies it and closes the dialog; the only button is Cancel. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ThemeDialog(selected: ThemeMode, onSelect: (ThemeMode) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.extraLarge,
        icon = {
            Box(
                Modifier
                    .size(56.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, MaterialShapes.Cookie9Sided.toShape()),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(R.drawable.ic_contrast),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        },
        title = {
            Text(
                stringResource(R.string.settings_theme),
                Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        },
        text = {
            Column(Modifier.selectableGroup()) {
                ThemeMode.entries.forEach { mode ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .selectable(selected = mode == selected, role = Role.RadioButton) { onSelect(mode) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = mode == selected, onClick = null)
                        Column(Modifier.padding(start = 16.dp)) {
                            Text(stringResource(mode.label()), style = MaterialTheme.typography.bodyLarge)
                            if (mode == ThemeMode.SYSTEM) {
                                Text(
                                    stringResource(R.string.theme_system_hint),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}


private fun Modifier.fillMaxWidthClickable(onClick: () -> Unit): Modifier =
    fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp)

private fun ThemeMode.label(): Int = when (this) {
    ThemeMode.SYSTEM -> R.string.theme_system
    ThemeMode.LIGHT -> R.string.theme_light
    ThemeMode.DARK -> R.string.theme_dark
}

private fun currencyLabel(code: String): String =
    runCatching { "$code — ${Currency.getInstance(code).displayName}" }.getOrDefault(code)

private fun backupFileName(extension: String): String {
    val date = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
    return "backup-$date.$extension"
}

private fun Context.openLink(intent: Intent) {
    try {
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        // No browser / mail app installed: nothing sensible to do.
    }
}

/** Resolves [SettingsEvent]s to localized text. */
@Composable
private fun rememberEventMessages(): (SettingsEvent) -> String {
    val exported = stringResource(R.string.backup_exported_generic)
    val exportFailed = stringResource(R.string.backup_export_failed)
    val imported = stringResource(R.string.backup_imported_generic)
    val importInvalid = stringResource(R.string.backup_import_invalid)
    val importFailed = stringResource(R.string.backup_import_failed)
    val restoreChecked = stringResource(R.string.restore_checked)
    val restoreFailed = stringResource(R.string.restore_failed)
    return { event ->
        when (event) {
            is SettingsEvent.Exported -> exported
            SettingsEvent.ExportFailed -> exportFailed
            is SettingsEvent.Imported -> imported
            SettingsEvent.ImportInvalid -> importInvalid
            SettingsEvent.ImportFailed -> importFailed
            SettingsEvent.RestoreChecked -> restoreChecked
            SettingsEvent.RestoreFailed -> restoreFailed
        }
    }
}
