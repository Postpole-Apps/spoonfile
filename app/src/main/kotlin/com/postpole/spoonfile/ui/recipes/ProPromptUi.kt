package com.postpole.spoonfile.ui.recipes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.postpole.spoonfile.R
import com.postpole.spoonfile.analytics.ProPromptMethod

private val MinTarget = 48.dp

/**
 * The automatic Pro sheet, shown at a rest point on the Recipes list. The three highlights are features that ship
 * today. [price] is the Play price, or null until it loads (the button then carries no number).
 * [onDismiss] receives a `ProPromptMethod`: `close`, `not_now`, or `swipe` (scrim tap, drag or system back).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProPromptSheet(
    price: String?,
    onCta: () -> Unit,
    onDismiss: (method: String) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = { onDismiss(ProPromptMethod.SWIPE) },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        ProPromptSheetContent(price, onCta, onDismiss)
    }
}

@Composable
internal fun ProPromptSheetContent(
    price: String?,
    onCta: () -> Unit,
    onDismiss: (method: String) -> Unit,
    insets: WindowInsets = WindowInsets.navigationBars,
) {
    val closeCd = stringResource(R.string.pro_prompt_close_cd)
    // Scrolls so a 200% font scale never clips the button.
    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .windowInsetsPadding(insets)
            .padding(horizontal = 24.dp)
            .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Text(
                stringResource(R.string.pro_prompt_sheet_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f).padding(top = 8.dp),
            )
            IconButton(
                onClick = { onDismiss(ProPromptMethod.CLOSE) },
                modifier = Modifier.heightIn(min = MinTarget),
            ) {
                Icon(Icons.Filled.Close, contentDescription = closeCd)
            }
        }
        Text(
            stringResource(R.string.pro_prompt_sheet_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Highlight(R.string.pro_feature_unlimited, R.string.pro_feature_unlimited_hint)
        Highlight(R.string.pro_feature_meal_plan, R.string.pro_feature_meal_plan_hint)
        Highlight(R.string.pro_feature_import, R.string.pro_feature_import_hint)
        Button(
            onClick = onCta,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        ) {
            Text(price?.let { stringResource(R.string.pro_buy, it) } ?: stringResource(R.string.pro_buy_no_price))
        }
        TextButton(
            onClick = { onDismiss(ProPromptMethod.NOT_NOW) },
            modifier = Modifier.fillMaxWidth().heightIn(min = MinTarget),
        ) {
            Text(stringResource(R.string.pro_prompt_not_now))
        }
    }
}

@Composable
private fun Highlight(title: Int, hint: Int) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** The automatic Pro card for the list header. No ads wording, for any buyer. */
@Composable
fun ProInlineCard(price: String?, onCta: () -> Unit, onDismiss: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.pro_prompt_card_title), style = MaterialTheme.typography.titleMedium)
            Text(
                price?.let { stringResource(R.string.pro_prompt_card_body, it) }
                    ?: stringResource(R.string.pro_prompt_card_body_no_price),
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FilledTonalButton(onClick = onCta, modifier = Modifier.heightIn(min = MinTarget)) {
                    Text(stringResource(R.string.pro_prompt_see_pro))
                }
                TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = MinTarget)) {
                    Text(stringResource(R.string.pro_prompt_not_now))
                }
            }
        }
    }
}
