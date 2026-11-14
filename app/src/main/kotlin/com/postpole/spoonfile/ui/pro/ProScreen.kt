package com.postpole.spoonfile.ui.pro

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.postpole.spoonfile.R
import com.postpole.spoonfile.billing.BillingMessage
import com.postpole.spoonfile.data.Limits
import com.postpole.spoonfile.ui.findActivity
import com.postpole.spoonfile.ui.theme.SpoonfilePolygons

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProScreen(onBack: () -> Unit, viewModel: ProViewModel = hiltViewModel()) {
    val isPro by viewModel.isPro.collectAsStateWithLifecycle()
    val price by viewModel.price.collectAsStateWithLifecycle()
    val pending by viewModel.pending.collectAsStateWithLifecycle()
    val unavailable by viewModel.productUnavailable.collectAsStateWithLifecycle()
    val activity = LocalContext.current.findActivity()
    val snackbarHostState = remember { SnackbarHostState() }
    val purchaseError = stringResource(R.string.pro_error_purchase)
    val launchError = stringResource(R.string.pro_error_launch)
    LaunchedEffect(viewModel) {
        viewModel.messages.collect {
            snackbarHostState.showSnackbar(
                when (it) {
                    BillingMessage.PURCHASE_ERROR -> purchaseError
                    BillingMessage.LAUNCH_FAILED -> launchError
                },
            )
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_pro)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Content scrolls above the pinned purchase area on short screens.
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Box(
                    Modifier
                        .padding(top = 8.dp)
                        .size(112.dp)
                        .clip(SpoonfilePolygons.categoryTiles.first())
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Star,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(48.dp),
                    )
                }
                Text(
                    stringResource(R.string.pro_title),
                    style = MaterialTheme.typography.displaySmall,
                    textAlign = TextAlign.Center,
                )
                Text(
                    stringResource(R.string.pro_tagline),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Only features that ship in this release.
                    FeatureRow(stringResource(R.string.pro_feature_unlimited))
                    FeatureRow(stringResource(R.string.pro_feature_import))
                }
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            stringResource(R.string.pro_always_free),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        FeatureRow(stringResource(R.string.pro_free_cap, Limits.FREE_RECIPE_LIMIT))
                        FeatureRow(stringResource(R.string.pro_free_search))
                        FeatureRow(stringResource(R.string.pro_free_cook))
                    }
                }
            }

            // Pinned in the thumb zone.
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                when {
                    isPro -> Text(
                        stringResource(R.string.pro_active),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                    pending -> Text(stringResource(R.string.pro_pending), textAlign = TextAlign.Center)
                    unavailable && price == null -> Text(stringResource(R.string.pro_unavailable), textAlign = TextAlign.Center)
                    else -> Button(
                        onClick = { activity?.let(viewModel::buy) },
                        // The price comes only from billing; while it is null the label carries no price.
                        enabled = price != null && activity != null,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                    ) {
                        Text(
                            price?.let { stringResource(R.string.pro_buy, it) } ?: stringResource(R.string.unlock_pro),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
                TextButton(onClick = viewModel::restore) { Text(stringResource(R.string.pro_restore)) }
            }
        }
    }
}

@Composable
private fun FeatureRow(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}
