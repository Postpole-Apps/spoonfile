package com.postpole.spoonfile.ui.recipe

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.postpole.spoonfile.R
import com.postpole.spoonfile.data.RecipeWithDetails
import com.postpole.spoonfile.domain.QuantityScaler
import com.postpole.spoonfile.ui.theme.ExtendedFabShape
import com.postpole.spoonfile.ui.theme.RecipePhotoShape
import com.postpole.spoonfile.ui.theme.SpoonfileText
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RecipeDetailScreen(
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onCookMode: (Long) -> Unit,
    viewModel: RecipeDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val details = state.details
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val comingSoon = stringResource(R.string.coming_soon)
    val stub: () -> Unit = { scope.launch { snackbar.showSnackbar(comingSoon) } }
    var menu by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var showRate by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    if (details != null) {
                        val fav = details.recipe.favorite
                        IconButton(onClick = viewModel::toggleFavorite) {
                            Icon(
                                if (fav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                contentDescription = stringResource(if (fav) R.string.recipe_unfavorite else R.string.recipe_favorite),
                            )
                        }
                        IconButton(onClick = stub) {
                            Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.recipe_share))
                        }
                        IconButton(onClick = { onEdit(details.recipe.id) }) {
                            Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.recipe_edit))
                        }
                        Box {
                            IconButton(onClick = { menu = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.recipe_more))
                            }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.recipe_delete)) },
                                    leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                                    onClick = {
                                        menu = false
                                        confirmDelete = true
                                    },
                                )
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                !state.loaded -> Unit
                details == null -> Text(
                    stringResource(R.string.recipe_not_found),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(24.dp),
                )
                else -> {
                    DetailContent(
                        details = details,
                        targetServings = state.targetServings,
                        photoFile = viewModel::photoFile,
                        onServings = viewModel::changeServings,
                    )
                    // ~150 dp fade, solid from 42%, so the toolbar never covers readable text.
                    Box(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(150.dp)
                            .background(
                                Brush.verticalGradient(
                                    0f to Color.Transparent,
                                    0.42f to MaterialTheme.colorScheme.background,
                                    1f to MaterialTheme.colorScheme.background,
                                ),
                            ),
                    )
                    Row(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, bottom = 30.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        HorizontalFloatingToolbar(
                            expanded = true,
                            colors = FloatingToolbarDefaults.standardFloatingToolbarColors(),
                            modifier = Modifier.height(64.dp),
                        ) {
                            IconButton(onClick = stub) {
                                Icon(Icons.Filled.ShoppingCart, contentDescription = stringResource(R.string.add_to_shopping))
                            }
                            IconButton(onClick = { showRate = true }) {
                                Icon(Icons.Filled.Star, contentDescription = stringResource(R.string.rate))
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        ExtendedFloatingActionButton(
                            onClick = { onCookMode(details.recipe.id) },
                            shape = ExtendedFabShape,
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.height(64.dp),
                            icon = { Icon(painterResource(R.drawable.ic_skillet), contentDescription = null) },
                            text = { Text(stringResource(R.string.cook_mode), style = MaterialTheme.typography.labelLarge) },
                        )
                    }
                }
            }
        }
    }

    if (confirmDelete && details != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_confirm_title)) },
            text = { Text(stringResource(R.string.delete_confirm_message, details.recipe.title)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.delete(onDone = onBack)
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
    if (showRate && details != null) {
        AlertDialog(
            onDismissRequest = { showRate = false },
            title = { Text(stringResource(R.string.rate_title)) },
            text = { RatingStars(details.recipe.rating, starSize = 32.dp, onChange = viewModel::setRating) },
            confirmButton = { TextButton(onClick = { showRate = false }) { Text(stringResource(R.string.done)) } },
        )
    }
}

@Composable
private fun DetailContent(
    details: RecipeWithDetails,
    targetServings: Double?,
    photoFile: (String) -> java.io.File,
    onServings: (Int) -> Unit,
) {
    val recipe = details.recipe
    val context = LocalContext.current
    val factor = QuantityScaler.factor(recipe.servings, targetServings ?: 1.0)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 190.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "photo") {
            RecipePhoto(
                file = recipe.imageFile?.let(photoFile),
                shape = RecipePhotoShape,
                modifier = Modifier.fillMaxWidth().widthIn(max = 328.dp).height(112.dp),
                maxEdgePx = 1024,
            )
        }
        item(key = "title") {
            Text(recipe.title, style = MaterialTheme.typography.displayMedium)
        }
        if (recipe.rating > 0) {
            item(key = "rating") { RatingStars(recipe.rating, starSize = 22.dp) }
        }
        val url = recipe.sourceUrl?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
        val sourceLabel = recipe.sourceName?.takeIf { it.isNotBlank() } ?: url
        if (sourceLabel != null) {
            item(key = "source") {
                val text = stringResource(R.string.source_prefix, sourceLabel)
                if (url != null) {
                    Text(
                        text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        textDecoration = TextDecoration.Underline,
                        modifier = Modifier.clickable {
                            try {
                                context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                            } catch (_: ActivityNotFoundException) {
                                // No browser installed: nothing to open.
                            }
                        },
                    )
                } else {
                    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (recipe.totalMinutes != null || recipe.prepMinutes != null || recipe.cookMinutes != null) {
            item(key = "times") {
                Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                        TimeCell(R.string.recipe_total, recipe.totalMinutes)
                        TimeCell(R.string.recipe_prep, recipe.prepMinutes)
                        TimeCell(R.string.recipe_cook, recipe.cookMinutes)
                    }
                }
            }
        }
        if (targetServings != null) {
            item(key = "servings") {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.servings_label), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    FilledTonalIconButton(onClick = { onServings(-1) }, enabled = targetServings > 1.0) {
                        Text("−", style = MaterialTheme.typography.titleLarge)
                    }
                    Text(
                        formatServings(targetServings),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    FilledTonalIconButton(onClick = { onServings(1) }) {
                        Text("+", style = MaterialTheme.typography.titleLarge)
                    }
                }
            }
        } else if (!recipe.servingsText.isNullOrBlank()) {
            item(key = "servings-text") {
                Text("${stringResource(R.string.servings_label)}: ${recipe.servingsText}", style = MaterialTheme.typography.bodyLarge)
            }
        }

        if (details.ingredients.isNotEmpty()) {
            item(key = "ingredients-title") { SectionTitle(R.string.section_ingredients) }
            // Every ingredient is shown in full: no collapse, truncation or "show more"; long lines wrap.
            items(details.ingredients, key = { "ing-${it.id}" }) { ingredient ->
                if (ingredient.isHeader) {
                    Text(
                        ingredient.text,
                        style = SpoonfileText.sectionLabel,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                } else {
                    Text(QuantityScaler.scale(ingredient.text, factor), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }

        if (details.steps.isNotEmpty()) {
            item(key = "steps-title") { SectionTitle(R.string.section_steps) }
            val numbers = numberSteps(details.steps.map { it.isHeader })
            items(details.steps.size, key = { "step-${details.steps[it].id}" }) { index ->
                val step = details.steps[index]
                if (step.isHeader) {
                    Text(
                        step.text,
                        style = SpoonfileText.sectionLabel,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        StepBadge(
                            number = numbers[index],
                            size = 36.dp,
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            textStyle = MaterialTheme.typography.labelLarge,
                        )
                        Text(step.text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        recipe.notes?.takeIf { it.isNotBlank() }?.let { notes ->
            item(key = "notes") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SectionTitle(R.string.section_notes)
                    Text(notes, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        recipe.nutritionText?.takeIf { it.isNotBlank() }?.let { nutrition ->
            item(key = "nutrition") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SectionTitle(R.string.section_nutrition)
                    Text(nutrition, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

/** Step numbers that skip section headers (headers get 0). */
internal fun numberSteps(isHeader: List<Boolean>): List<Int> {
    var n = 0
    return isHeader.map { if (it) 0 else ++n }
}

internal fun formatServings(value: Double): String =
    if (value == kotlin.math.floor(value)) value.toLong().toString() else value.toString()

@Composable
private fun SectionTitle(label: Int) {
    Text(
        stringResource(label),
        style = SpoonfileText.sectionTitle,
        modifier = Modifier.padding(top = 14.dp),
    )
}

@Composable
private fun TimeCell(label: Int, minutes: Int?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            if (minutes != null) stringResource(R.string.minutes_short, minutes) else "–",
            style = MaterialTheme.typography.titleMedium,
        )
    }
}
