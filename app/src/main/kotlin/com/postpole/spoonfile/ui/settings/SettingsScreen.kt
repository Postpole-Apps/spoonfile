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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.activity.compose.LocalActivity
import com.postpole.spoonfile.R
import com.postpole.spoonfile.ads.rememberAdsController
import com.postpole.spoonfile.data.ThemeMode
import com.postpole.spoonfile.exporter.PaprikaArchiveWriter
import com.postpole.spoonfile.ui.showLatestSnackbar
import java.text.DateFormat
import kotlinx.coroutines.launch
import java.util.Date

/**
 * The Remove ads row. With a price a tap starts the purchase. Without one it retries loading the
 * product, and when Play already said it is unavailable it also gives feedback via [onUnavailableTap].
 */
@Composable
internal fun RemoveAdsRow(
    price: String?,
    unavailable: Boolean,
    pending: Boolean,
    onBuy: () -> Unit,
    onRetry: () -> Unit,
    onUnavailableTap: () -> Unit,
) {
    // The price only ever comes from Play; until it loads, no price is shown.
    ListItem(
        headlineContent = {
            Text(
                price?.let { stringResource(R.string.settings_remove_ads, it) }
                    ?: stringResource(R.string.settings_remove_ads_no_price),
            )
        },
        supportingContent = {
            Text(
                stringResource(
                    when {
                        pending -> R.string.settings_remove_ads_pending
                        price == null && unavailable -> R.string.settings_remove_ads_unavailable
                        else -> R.string.settings_remove_ads_hint
                    },
                ),
            )
        },
        modifier = Modifier.heightIn(min = 64.dp).clickable(enabled = !pending) {
            if (price != null) {
                onBuy()
            } else {
                if (unavailable) onUnavailableTap()
                onRetry()
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenPro: () -> Unit,
    onOpenCategories: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val isPro by viewModel.isPro.collectAsStateWithLifecycle()
    val removeAdsOwned by viewModel.removeAdsOwned.collectAsStateWithLifecycle()
    val showRemoveAdsRow by viewModel.showRemoveAdsRow.collectAsStateWithLifecycle()
    val removeAdsPrice by viewModel.removeAdsPrice.collectAsStateWithLifecycle()
    val removeAdsUnavailable by viewModel.removeAdsUnavailable.collectAsStateWithLifecycle()
    val removeAdsPending by viewModel.removeAdsPending.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val lastBackupAt by viewModel.lastBackupAt.collectAsStateWithLifecycle()
    val remind by viewModel.remindBackup.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resolver = context.contentResolver
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    // No ads are ever shown on this screen; it only reopens the UMP privacy options form.
    val ads = rememberAdsController()
    val activity = LocalActivity.current
    val privacyOptionsRequired by ads.privacyOptionsRequired.collectAsStateWithLifecycle()

    var showTheme by rememberSaveable { mutableStateOf(false) }
    var pendingRestore by rememberSaveable { mutableStateOf<String?>(null) }

    // Storage Access Framework: the system picker grants access to the one chosen file, so no
    // storage permission is needed.
    val backUp = rememberLauncherForActivityResult(
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
        viewModel.events.collect { snackbarHostState.showLatestSnackbar(messages(it)) }
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
            // Pro first: it is the better deal (no ads plus unlimited recipes and whole-file import).
            ProCard(isPro = isPro, adsAlreadyRemoved = removeAdsOwned, onClick = onOpenPro)
            if (showRemoveAdsRow) {
                LaunchedEffect(Unit) { viewModel.onRemoveAdsPaywallViewed() }
                // The price only ever comes from Play; until it loads, no price is shown.
                val unavailableMessage = stringResource(R.string.settings_remove_ads_unavailable_snackbar)
                RemoveAdsRow(
                    price = removeAdsPrice,
                    unavailable = removeAdsUnavailable,
                    pending = removeAdsPending,
                    onBuy = { activity?.let(viewModel::buyRemoveAds) },
                    onRetry = viewModel::retryProducts,
                    onUnavailableTap = { scope.launch { snackbarHostState.showLatestSnackbar(unavailableMessage) } },
                )
                HorizontalDivider()
            }

            SectionHeader(R.string.settings_section_recipes)
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_categories)) },
                supportingContent = { Text(stringResource(R.string.settings_categories_hint)) },
                modifier = Modifier.heightIn(min = 64.dp).clickable(onClick = onOpenCategories),
            )
            HorizontalDivider()

            SectionHeader(R.string.settings_section_appearance)
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_theme)) },
                supportingContent = { Text(stringResource(themeMode.label())) },
                modifier = Modifier
                    .heightIn(min = 64.dp)
                    .clickable { showTheme = true },
            )
            HorizontalDivider()

            SectionHeader(R.string.settings_section_data)
            Text(
                stringResource(R.string.settings_data_always_free),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.backup_now)) },
                supportingContent = {
                    Text(
                        lastBackupAt?.let { stringResource(R.string.backup_last, formatDate(it)) }
                            ?: stringResource(R.string.backup_never),
                    )
                },
                modifier = Modifier.heightIn(min = 64.dp).clickable { backUp.launch(backupFileName()) },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.export_recipe_file)) },
                supportingContent = { Text(stringResource(R.string.export_recipe_file_hint)) },
                modifier = Modifier.heightIn(min = 64.dp).clickable { exportRecipeFile.launch(recipeFileName()) },
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
                headlineContent = { Text(stringResource(R.string.settings_phone_transfer)) },
                modifier = Modifier.heightIn(min = 64.dp),
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
            // UMP-recommended: the entry point exists only where the region requires it.
            if (privacyOptionsRequired) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_privacy_options)) },
                    supportingContent = { Text(stringResource(R.string.settings_privacy_options_hint)) },
                    modifier = Modifier.heightIn(min = 64.dp).clickable(enabled = activity != null) {
                        activity?.let(ads::showPrivacyOptions)
                    },
                )
            }
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
            title = { Text(stringResource(R.string.backup_import_confirm_title)) },
            text = { Text(stringResource(R.string.backup_import_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.restoreBackup(resolver, Uri.parse(uriString))
                        pendingRestore = null
                    },
                ) { Text(stringResource(R.string.backup_import_confirm_action)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingRestore = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

/** Top of Settings: opens the Pro route; reads "Pro unlocked" once purchased. */
@Composable
private fun ProCard(isPro: Boolean, adsAlreadyRemoved: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier.fillMaxWidth().padding(16.dp).heightIn(min = 64.dp),
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(if (isPro) R.string.pro_unlocked else R.string.pro_features_title),
                style = MaterialTheme.typography.titleLarge,
            )
            if (!isPro) {
                // Remove ads owners are sold on features only; ad removal is not pitched again.
                val hint = if (adsAlreadyRemoved) R.string.settings_pro_card_hint else R.string.settings_pro_card_hint_with_no_ads
                Text(stringResource(hint), style = MaterialTheme.typography.bodyMedium)
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

private fun isoDate(): String =
    java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(Date())

private fun backupFileName(): String = "spoonfile-backup-${isoDate()}.zip"

private fun recipeFileName(): String = PaprikaArchiveWriter.fileName(isoDate())

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
    return { event ->
        when (event) {
            is SettingsEvent.Exported -> exported
            SettingsEvent.ExportFailed -> exportFailed
            is SettingsEvent.Imported -> imported
            SettingsEvent.ImportInvalid -> importInvalid
            SettingsEvent.ImportFailed -> importFailed
        }
    }
}
