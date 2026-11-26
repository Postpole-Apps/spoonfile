package com.postpole.spoonfile.ui.recipes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.postpole.spoonfile.R
import com.postpole.spoonfile.analytics.ProPromptDismissMethod
import com.postpole.spoonfile.pro.ProPromptSurface
import com.postpole.spoonfile.pro.ProPromptTrigger
import kotlinx.coroutines.launch

/** What [ProInlineCard] needs from the list screen. */
data class ProCardUi(
    val price: String?,
    val onSeePro: () -> Unit,
    val onNotNow: () -> Unit,
    val trigger: ProPromptTrigger = ProPromptTrigger.RECIPES_MILESTONE,
)

/** True for the triggers that render as a card on the Recipes list. */
fun ProPromptTrigger.isRecipesCard(): Boolean = surface == ProPromptSurface.CARD

/**
 * Dismissible tonal card in the Recipes list header for `recipes_milestone`, `recipe_cap_near` or `imports_3`
 * (the copy follows [trigger]). [price] is the localized Play price, or null until it loads (then the no-price
 * copy is used). Never mentions ads.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProInlineCard(
    price: String?,
    onSeePro: () -> Unit,
    onNotNow: () -> Unit,
    modifier: Modifier = Modifier,
    trigger: ProPromptTrigger = ProPromptTrigger.RECIPES_MILESTONE,
) {
    val title = when (trigger) {
        ProPromptTrigger.RECIPE_CAP_NEAR -> R.string.pro_prompt_cap_near_title
        ProPromptTrigger.IMPORTS_3 -> R.string.pro_prompt_imports_title
        else -> R.string.pro_prompt_card_title
    }
    val body = when (trigger) {
        ProPromptTrigger.RECIPE_CAP_NEAR -> R.string.pro_prompt_cap_near_body
        ProPromptTrigger.IMPORTS_3 -> R.string.pro_prompt_imports_body
        else -> R.string.pro_prompt_card_body
    }
    val bodyNoPrice = when (trigger) {
        ProPromptTrigger.RECIPE_CAP_NEAR -> R.string.pro_prompt_cap_near_body_no_price
        ProPromptTrigger.IMPORTS_3 -> R.string.pro_prompt_imports_body_no_price
        else -> R.string.pro_prompt_card_body_no_price
    }
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
            Text(
                price?.let { stringResource(body, it) } ?: stringResource(bodyNoPrice),
                style = MaterialTheme.typography.bodyMedium,
            )
            // Wraps onto a second line instead of clipping at large font scales.
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                FilledTonalButton(onClick = onSeePro, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.pro_prompt_see_pro))
                }
                TextButton(onClick = onNotNow, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.pro_prompt_not_now))
                }
            }
        }
    }
}

/**
 * Rest-point bottom sheet for `trigger=cook_or_save_milestone`. [onDismiss] gets a [ProPromptDismissMethod] value:
 * `close` (X), `not_now` (button) or `swipe` (scrim tap, drag or system back). [onUnlock] opens the Pro screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProPromptSheet(price: String?, onUnlock: () -> Unit, onDismiss: (String) -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    // Animate the sheet away first, then report; scrim tap / drag / back arrive via onDismissRequest.
    fun dismiss(method: String) {
        scope.launch {
            sheetState.hide()
            onDismiss(method)
        }
    }
    ModalBottomSheet(
        onDismissRequest = { onDismiss(ProPromptDismissMethod.SWIPE) },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        // Scrolls on short screens and at 200% font scale.
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    stringResource(R.string.pro_prompt_sheet_title),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f).padding(top = 12.dp),
                )
                IconButton(onClick = { dismiss(ProPromptDismissMethod.CLOSE) }) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.pro_prompt_close_cd))
                }
            }
            Text(
                stringResource(R.string.pro_prompt_sheet_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // Only features that exist today.
            Highlight(R.string.pro_feature_unlimited)
            Highlight(R.string.pro_feature_meal_plan)
            Highlight(R.string.pro_feature_import)
            Button(
                onClick = onUnlock,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                // The price only ever comes from Play; until it loads, no price is shown.
                Text(price?.let { stringResource(R.string.pro_buy, it) } ?: stringResource(R.string.pro_buy_no_price))
            }
            TextButton(
                onClick = { dismiss(ProPromptDismissMethod.NOT_NOW) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) { Text(stringResource(R.string.pro_prompt_not_now)) }
        }
    }
}

@Composable
private fun Highlight(textRes: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(stringResource(textRes), style = MaterialTheme.typography.bodyLarge)
    }
}
