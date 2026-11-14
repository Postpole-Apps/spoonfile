package com.postpole.spoonfile.ui.recipe

import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.postpole.spoonfile.R
import com.postpole.spoonfile.data.StepEntity
import com.postpole.spoonfile.ui.findActivity
import com.postpole.spoonfile.ui.theme.CookBackgroundDark
import com.postpole.spoonfile.ui.theme.CookBackgroundLight
import com.postpole.spoonfile.ui.theme.CookTextDark
import com.postpole.spoonfile.ui.theme.CookTextLight
import com.postpole.spoonfile.ui.theme.LocalDarkTheme
import com.postpole.spoonfile.ui.theme.SpoonfileText
import kotlinx.coroutines.launch

/** One page of cook mode: the step text, its number (headers are not numbered) and its section, if any. */
internal data class CookStep(val number: Int, val text: String, val section: String?)

internal fun buildCookSteps(steps: List<StepEntity>): List<CookStep> {
    val result = mutableListOf<CookStep>()
    var section: String? = null
    var number = 0
    for (step in steps) {
        if (step.isHeader) {
            section = step.text
        } else {
            result += CookStep(++number, step.text, section)
        }
    }
    return result
}

/** Keeps the screen on only while this composable is in the composition (i.e. cook mode is visible). */
@Composable
private fun KeepScreenOn() {
    val activity = LocalContext.current.findActivity()
    DisposableEffect(activity) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CookModeScreen(
    onBack: () -> Unit,
    viewModel: RecipeDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val details = state.details
    val steps = buildCookSteps(details?.steps.orEmpty())
    // Follows the Theme setting (System / Light / Dark). Dark is pure black because the screen stays on (OLED).
    val dark = LocalDarkTheme.current
    val background = if (dark) CookBackgroundDark else CookBackgroundLight
    val foreground = if (dark) CookTextDark else CookTextLight

    KeepScreenOn()

    val pagerState = rememberPagerState(pageCount = { steps.size.coerceAtLeast(1) })
    val scope = rememberCoroutineScope()
    var showIngredients by rememberSaveable { mutableStateOf(false) }

    Surface(color = background, contentColor = foreground, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.cook_exit))
                }
                Spacer(Modifier.weight(1f))
                ScreenOnIndicator()
                TextButton(onClick = { showIngredients = true }, enabled = details != null) {
                    Text(stringResource(R.string.cook_ingredients))
                }
            }
            if (steps.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    if (state.loaded) {
                        Text(
                            stringResource(R.string.cook_no_steps),
                            style = MaterialTheme.typography.headlineMedium,
                            modifier = Modifier.padding(24.dp),
                        )
                    }
                }
            } else {
                Text(
                    stringResource(R.string.cook_step_of, pagerState.currentPage + 1, steps.size),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
                LinearWavyProgressIndicator(
                    progress = { (pagerState.currentPage + 1f) / steps.size },
                    color = foreground,
                    trackColor = foreground.copy(alpha = 0.25f),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
                )
                HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
                    val step = steps[page]
                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        StepBadge(
                            number = step.number,
                            size = 120.dp,
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            textStyle = SpoonfileText.cookStepNumber,
                        )
                        if (step.section != null) {
                            Text(step.section, style = SpoonfileText.sectionLabel)
                        }
                        Text(step.text, style = MaterialTheme.typography.headlineLarge)
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    val isLast = pagerState.currentPage >= steps.size - 1
                    OutlinedButton(
                        onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } },
                        enabled = pagerState.currentPage > 0,
                        modifier = Modifier.weight(1f).height(56.dp),
                    ) { Text(stringResource(R.string.cook_back), style = MaterialTheme.typography.labelLarge) }
                    Button(
                        onClick = {
                            if (isLast) onBack() else scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                        },
                        modifier = Modifier.weight(1f).height(56.dp),
                    ) {
                        Text(
                            stringResource(if (isLast) R.string.cook_finish else R.string.cook_next),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
        }
    }

    if (showIngredients && details != null) {
        ModalBottomSheet(
            onDismissRequest = { showIngredients = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Text(
                stringResource(R.string.cook_ingredients),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            LazyColumn(
                contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(details.ingredients, key = { it.id }) { ingredient ->
                    if (ingredient.isHeader) {
                        Text(
                            ingredient.text,
                            style = SpoonfileText.sectionLabel,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    } else {
                        Text(ingredient.text, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }
}

@Composable
private fun ScreenOnIndicator() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(8.dp).background(MaterialTheme.colorScheme.tertiary, CircleShape))
        Text(stringResource(R.string.cook_screen_on), style = MaterialTheme.typography.labelMedium)
    }
}
