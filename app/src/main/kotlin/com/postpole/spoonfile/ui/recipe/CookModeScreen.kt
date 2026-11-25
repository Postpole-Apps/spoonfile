package com.postpole.spoonfile.ui.recipe

import android.view.WindowManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.postpole.spoonfile.R
import com.postpole.spoonfile.data.RecipeWithDetails
import com.postpole.spoonfile.domain.QuantityScaler
import com.postpole.spoonfile.domain.StepTimerParser
import com.postpole.spoonfile.ui.findActivity
import com.postpole.spoonfile.ui.theme.CookBackgroundDark
import com.postpole.spoonfile.ui.theme.CookBackgroundLight
import com.postpole.spoonfile.ui.theme.CookTextDark
import com.postpole.spoonfile.ui.theme.CookTextLight
import com.postpole.spoonfile.ui.theme.SpoonfilePolygons
import com.postpole.spoonfile.ui.theme.SpoonfileText
import kotlinx.coroutines.launch

/** One cook-mode page: a step plus the section header (if any) it falls under. */
internal data class CookPage(val number: Int, val text: String, val section: String?)

internal fun cookPages(details: RecipeWithDetails): List<CookPage> {
    var section: String? = null
    var number = 0
    return buildList {
        for (step in details.steps) {
            if (step.isHeader) {
                section = step.text
            } else {
                add(CookPage(++number, step.text, section))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CookModeScreen(onBack: () -> Unit, viewModel: RecipeDetailViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val details = state.details

    // The screen stays on only while cook mode is visible; the flag is cleared as soon as it leaves.
    val activity = LocalContext.current.findActivity()
    DisposableEffect(activity) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    // Follows the Theme setting: pure black when dark (the screen stays on, OLED), primaryContainer when light.
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val background = if (dark) CookBackgroundDark else CookBackgroundLight
    val content = if (dark) CookTextDark else CookTextLight

    Surface(color = background, contentColor = content, modifier = Modifier.fillMaxSize()) {
        val pages = remember(details) { details?.let(::cookPages).orEmpty() }
        if (details == null || pages.isEmpty()) {
            Column(
                Modifier.fillMaxSize().statusBarsPadding().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                if (details != null) Text(stringResource(R.string.cook_no_steps), style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = onBack) { Text(stringResource(R.string.cook_exit), color = content) }
            }
        } else {
            CookPager(
                details,
                pages,
                state.scale,
                content,
                background,
                onBack,
                // Finishing the last step counts as a cook-mode completion; leaving early does not.
                onDone = {
                    viewModel.onCookCompleted()
                    onBack()
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CookPager(
    details: RecipeWithDetails,
    pages: List<CookPage>,
    scale: Double,
    content: Color,
    background: Color,
    onBack: () -> Unit,
    onDone: () -> Unit,
) {
    val pagerState = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    var showIngredients by rememberSaveable { mutableStateOf(false) }
    val page = pagerState.currentPage
    val last = page == pages.lastIndex
    // One timer per cook-mode session; it lives here so it survives step swipes and rotation.
    var timer by rememberSaveable(stateSaver = CookTimerState.Saver) { mutableStateOf(CookTimerState()) }
    val suggested = remember(pages, page) { StepTimerParser.parse(pages[page].text) }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.cook_exit))
            }
            Text(
                stringResource(R.string.cook_step_of, page + 1, pages.size),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            Text("●  " + stringResource(R.string.cook_screen_on), style = MaterialTheme.typography.labelLarge)
        }
        val progressCd = stringResource(R.string.cook_progress_cd)
        LinearWavyProgressIndicator(
            progress = { (page + 1f) / pages.size },
            color = content,
            trackColor = content.copy(alpha = 0.24f),
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).semantics { contentDescription = progressCd },
        )

        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { index ->
            val item = pages[index]
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Surface(
                    shape = SpoonfilePolygons.stepNumber,
                    color = content,
                    contentColor = background,
                    modifier = Modifier.size(120.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(item.number.toString(), style = SpoonfileText.cookStepNumber)
                    }
                }
                item.section?.let {
                    Text(it, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                }
                // The whole step is always shown; long steps scroll.
                Text(item.text, fontSize = 31.sp, lineHeight = 40.sp, textAlign = TextAlign.Center)
            }
        }

        CookTimerSection(
            timer = timer,
            onTimerChange = { timer = it },
            suggestedSeconds = suggested,
            content = content,
        )

        Row(
            Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(
                onClick = { scope.launch { pagerState.animateScrollToPage(page - 1) } },
                enabled = page > 0,
                modifier = Modifier.height(56.dp).weight(1f),
            ) { Text(stringResource(R.string.cook_back), color = content.copy(alpha = if (page > 0) 1f else 0.4f)) }
            TextButton(onClick = { showIngredients = true }, modifier = Modifier.height(56.dp)) {
                Text(stringResource(R.string.cook_ingredients), color = content)
            }
            Button(
                onClick = { if (last) onDone() else scope.launch { pagerState.animateScrollToPage(page + 1) } },
                modifier = Modifier.height(56.dp).weight(1f),
            ) { Text(stringResource(if (last) R.string.cook_done else R.string.cook_next)) }
        }
    }

    if (showIngredients) {
        ModalBottomSheet(
            onDismissRequest = { showIngredients = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Text(
                stringResource(R.string.cook_ingredients),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            // Every ingredient, in full.
            LazyColumn(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(details.ingredients, key = { it.id }) { ingredient ->
                    if (ingredient.isHeader) {
                        Text(
                            ingredient.text,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    } else {
                        Text(QuantityScaler.scale(ingredient.text, scale), style = MaterialTheme.typography.bodyLarge)
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}
