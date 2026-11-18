package com.postpole.spoonfile.ui.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.toShape
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.postpole.spoonfile.BuildConfig
import com.postpole.spoonfile.R
import com.postpole.spoonfile.data.ThemeMode
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenPro: () -> Unit,
    onOpenCategories: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val isPro by viewModel.isPro.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val lastBackupAt by viewModel.lastBackupAt.collectAsStateWithLifecycle()
    val remind by viewModel.remindBackup.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resolver = context.contentResolver
    val snackbarHostState = remember { SnackbarHostState() }

    var showTheme by rememberSaveable { mutableStateOf(false) }
    var pendingRestore by rememberSaveable { mutableStateOf<String?>(null) }

    // Storage Access Framework: the system picker grants access to the one chosen file, so no storage
    // permission is needed.
    val backupNow = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri -> uri?.let { viewModel.backUpNow(resolver, it) } }
    val exportRecipeFile = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri -> uri?.let { viewModel.exportRecipeFile(resolver, it) } }
    val pickRestore = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> pendingRestore = uri?.toString() }

    val messages = rememberEventMessages()
    LaunchedEffect(viewModel) {
        viewModel.events.collect { snackbarHostState.showSnackbar(messages(it)) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState())) {
            ProCard(isPro = isPro, onClick = onOpenPro)

            SectionHeader(R.string.settings_section_recipes)
            ListItem(
                headlineContent = { Text(stringResource(R.string.categories_title)) },
                supportingContent = { Text(stringResource(R.string.settings_categories_hint)) },
                modifier = Modifier.heightIn(min = 64.dp).clickable(onClick = onOpenCategories),
            )
            HorizontalDivider()

            SectionHeader(R.string.settings_section_appearance)
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_theme)) },
                supportingContent = { Text(stringResource(themeMode.label())) },
                modifier = Modifier.heightIn(min = 64.dp).clickable { showTheme = true },
            )
            HorizontalDivider()

            SectionHeader(R.string.settings_section_data)
            ListItem(
                headlineContent = { Text(stringResource(R.string.backup_now)) },
                supportingContent = {
                    Text(
                        lastBackupAt?.let { stringResource(R.string.backup_last, formatDate(it)) }
                            ?: stringResource(R.string.backup_none_yet),
                    )
                },
                modifier = Modifier.heightIn(min = 64.dp).clickable { backupNow.launch(fileName("spoonfile-backup", "zip")) },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.recipe_file_export)) },
                supportingContent = { Text(stringResource(R.string.recipe_file_export_hint)) },
                // The recipe-file extension belongs in the saved file's name only, never in on-screen text.
                modifier = Modifier.heightIn(min = 64.dp).clickable {
                    exportRecipeFile.launch(fileName("spoonfile-recipes", "paprikarecipes"))
                },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.backup_restore)) },
                supportingContent = { Text(stringResource(R.string.backup_restore_hint)) },
                modifier = Modifier.heightIn(min = 64.dp).clickable {
                    pickRestore.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream"))
                },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.backup_remind)) },
                trailingContent = { Switch(checked = remind, onCheckedChange = null) },
                modifier = Modifier
                    .heightIn(min = 64.dp)
                    .selectable(selected = remind, role = Role.Switch) { viewModel.setRemindBackup(!remind) },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_transfer_note)) },
            )
            HorizontalDivider()

            SectionHeader(R.string.settings_section_about)
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_feedback)) },
                supportingContent = { Text(stringResource(R.string.support_email)) },
                modifier = Modifier.heightIn(min = 64.dp).clickable {
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
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_privacy_policy)) },
                modifier = Modifier.heightIn(min = 64.dp).clickable {
                    context.openLink(Intent(Intent.ACTION_VIEW, Uri.parse(context.getString(R.string.privacy_policy_url))))
                },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_version)) },
                supportingContent = { Text(BuildConfig.VERSION_NAME) },
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

    pendingRestore?.let { uriString ->
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text(stringResource(R.string.backup_restore_confirm_title)) },
            text = { Text(stringResource(R.string.backup_restore_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.restoreBackup(resolver, Uri.parse(uriString))
                        pendingRestore = null
                    },
                ) { Text(stringResource(R.string.backup_restore_confirm_action)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingRestore = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun ProCard(isPro: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier.fillMaxWidth().padding(16.dp),
    ) {
        Column(Modifier.padding(20.dp).heightIn(min = 48.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(if (isPro) R.string.pro_unlocked else R.string.pro_features_title),
                style = MaterialTheme.typography.titleLarge,
            )
            if (!isPro) {
                Text(stringResource(R.string.pro_feature_unlimited), style = MaterialTheme.typography.bodyMedium)
            }
        }
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

private fun ThemeMode.label(): Int = when (this) {
    ThemeMode.SYSTEM -> R.string.theme_system
    ThemeMode.LIGHT -> R.string.theme_light
    ThemeMode.DARK -> R.string.theme_dark
}

private fun formatDate(time: Long): String = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(time))

private fun fileName(prefix: String, extension: String): String {
    val date = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(Date())
    return "$prefix-$date.$extension"
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
    val backupFailed = stringResource(R.string.backup_failed)
    val recipeFileFailed = stringResource(R.string.recipe_file_failed)
    val restoreInvalid = stringResource(R.string.backup_restore_invalid)
    val restoreFailed = stringResource(R.string.backup_restore_failed)
    val resources = LocalContext.current.resources
    return { event ->
        when (event) {
            is SettingsEvent.BackedUp -> resources.getQuantityString(R.plurals.backup_saved, event.count, event.count)
            SettingsEvent.BackupFailed -> backupFailed
            is SettingsEvent.RecipeFileSaved -> resources.getQuantityString(R.plurals.recipe_file_saved, event.count, event.count)
            SettingsEvent.RecipeFileFailed -> recipeFileFailed
            is SettingsEvent.Restored -> resources.getQuantityString(R.plurals.backup_restored, event.count, event.count)
            SettingsEvent.RestoreInvalid -> restoreInvalid
            SettingsEvent.RestoreFailed -> restoreFailed
        }
    }
}
