package com.postpole.spoonfile.ui.recipe

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.LocalContext
import com.postpole.spoonfile.exporter.RecipeTextFormatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.postpole.spoonfile.R
import com.postpole.spoonfile.data.IngredientEntity
import com.postpole.spoonfile.data.RecipeEntity
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.SnackbarResult
import androidx.compose.ui.semantics.Role
import com.postpole.spoonfile.domain.QuantityScaler
import com.postpole.spoonfile.ui.showLatestSnackbar
import com.postpole.spoonfile.ui.theme.ExtendedFabShape
import com.postpole.spoonfile.ui.theme.RecipePhotoShape
import com.postpole.spoonfile.ui.theme.SpoonfilePolygons
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RecipeDetailScreen(
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onCook: (Long) -> Unit,
    onOpenShopping: () -> Unit,
    viewModel: RecipeDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val details = state.details
    var menu by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var showRate by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var shoppingSnackbarJob by remember { mutableStateOf<Job?>(null) }
    var showAddToShopping by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val viewList = stringResource(R.string.shopping_view_list)

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
                                contentDescription = stringResource(if (fav) R.string.detail_unfavorite else R.string.detail_favorite),
                            )
                        }
                        val labels = RecipeTextFormatter.Labels(
                            servings = stringResource(R.string.detail_servings),
                            ingredients = stringResource(R.string.detail_ingredients),
                            steps = stringResource(R.string.detail_steps),
                        )
                        val shareTitle = stringResource(R.string.detail_share)
                        IconButton(onClick = {
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, details.recipe.title)
                                putExtra(Intent.EXTRA_TEXT, RecipeTextFormatter.format(details, labels))
                            }
                            context.startActivity(Intent.createChooser(send, shareTitle))
                        }) {
                            Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.detail_share))
                        }
                        IconButton(onClick = { onEdit(details.recipe.id) }) {
                            Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.detail_edit))
                        }
                        Box {
                            IconButton(onClick = { menu = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.detail_more))
                            }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.detail_delete)) },
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
                state.loading -> Unit
                details == null -> Text(
                    stringResource(R.string.detail_not_found),
                    modifier = Modifier.padding(24.dp),
                    style = MaterialTheme.typography.bodyLarge,
                )
                else -> DetailContent(state, viewModel)
            }

            if (details != null) {
                // ~150 dp fade, solid from 42%: the toolbar never covers readable ingredient text.
                BottomFade(height = 150.dp, modifier = Modifier.align(Alignment.BottomCenter))
                Row(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 30.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HorizontalFloatingToolbar(
                        expanded = true,
                        colors = FloatingToolbarDefaults.standardFloatingToolbarColors(),
                        content = {
                            IconButton(onClick = { showAddToShopping = true }) {
                                Icon(painterResource(R.drawable.ic_cart), contentDescription = stringResource(R.string.detail_add_to_shopping))
                            }
                            IconButton(onClick = { showRate = true }) {
                                Icon(Icons.Filled.Star, contentDescription = stringResource(R.string.detail_rate))
                            }
                        },
                    )
                    Spacer(Modifier.weight(1f))
                    ExtendedFloatingActionButton(
                        onClick = { onCook(details.recipe.id) },
                        shape = ExtendedFabShape,
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.height(64.dp),
                        icon = { Icon(painterResource(R.drawable.ic_skillet), contentDescription = null) },
                        text = { Text(stringResource(R.string.detail_cook_mode), style = MaterialTheme.typography.titleMedium) },
                    )
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.detail_delete_title)) },
            text = { Text(stringResource(R.string.detail_delete_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.delete(onBack)
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (showAddToShopping && details != null) {
        AddToShoppingDialog(
            ingredients = details.ingredients.filter { !it.isHeader },
            scale = state.scale,
            onConfirm = { ids ->
                showAddToShopping = false
                viewModel.addToShopping(ids) { count ->
                    if (count > 0) {
                        shoppingSnackbarJob?.cancel()
                        shoppingSnackbarJob = scope.launch {
                            val result = snackbar.showLatestSnackbar(
                                context.resources.getQuantityString(R.plurals.shopping_added, count, count),
                                actionLabel = viewList,
                            )
                            if (result == SnackbarResult.ActionPerformed) onOpenShopping()
                        }
                    }
                }
            },
            onDismiss = { showAddToShopping = false },
        )
    }
    if (showRate && details != null) {
        RatingDialog(
            current = details.recipe.rating,
            onPick = {
                viewModel.setRating(it)
                showRate = false
            },
            onDismiss = { showRate = false },
        )
    }
}

@Composable
private fun DetailContent(state: RecipeDetailUiState, viewModel: RecipeDetailViewModel) {
    val details = requireNotNull(state.details)
    val recipe = details.recipe
    val uriHandler = LocalUriHandler.current
    val scale = state.scale
    LazyColumn(
        Modifier.fillMaxSize(),
        // Bottom room so every ingredient and step can scroll clear of the toolbar and fade.
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 190.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                RecipePhoto(
                    imageFile = recipe.imageFile,
                    load = viewModel::loadPhoto,
                    shape = RecipePhotoShape,
                    modifier = Modifier.widthIn(max = 328.dp).fillMaxWidth().height(112.dp),
                    maxEdge = 1000,
                    contentDescription = stringResource(R.string.detail_photo_cd),
                )
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(recipe.title, style = MaterialTheme.typography.displayMedium)
                if (recipe.rating > 0) {
                    val cd = stringResource(R.string.detail_rating_stars, recipe.rating)
                    Text(
                        "★".repeat(recipe.rating) + "☆".repeat(5 - recipe.rating),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.semantics { contentDescription = cd },
                    )
                }
                SourceLine(recipe) { url -> uriHandler.openUri(url) }
                recipe.description?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
            }
        }
        item { TimeGroup(recipe) }
        if (state.servings != null) {
            item { ServingsStepper(state.servings, onStep = viewModel::stepServings) }
        } else if (!recipe.servingsText.isNullOrBlank()) {
            item {
                Text(
                    stringResource(R.string.detail_servings) + ": " + recipe.servingsText,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
        if (details.ingredients.isNotEmpty()) {
            item { Text(stringResource(R.string.detail_ingredients), style = MaterialTheme.typography.titleLarge) }
            // Every ingredient in full: no maxLines, no ellipsis, no "show more". Long lines wrap.
            items(details.ingredients, key = { "i${it.id}" }) { ingredient ->
                if (ingredient.isHeader) {
                    Text(
                        ingredient.text,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                } else {
                    Text(QuantityScaler.scale(ingredient.text, scale), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        if (details.steps.isNotEmpty()) {
            item { Text(stringResource(R.string.detail_steps), style = MaterialTheme.typography.titleLarge) }
            val numbers = stepNumbers(details.steps.map { it.isHeader })
            items(details.steps.size, key = { "s${details.steps[it].id}" }) { index ->
                val step = details.steps[index]
                if (step.isHeader) {
                    Text(
                        step.text,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Surface(
                            shape = SpoonfilePolygons.stepNumber,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(36.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(numbers[index].toString(), style = MaterialTheme.typography.labelLarge)
                            }
                        }
                        Text(step.text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
        recipe.notes?.let { notes ->
            item { Text(stringResource(R.string.detail_notes), style = MaterialTheme.typography.titleLarge) }
            item { Text(notes, style = MaterialTheme.typography.bodyLarge) }
        }
        recipe.nutritionText?.let { nutrition ->
            item { Text(stringResource(R.string.detail_nutrition), style = MaterialTheme.typography.titleLarge) }
            item { Text(nutrition, style = MaterialTheme.typography.bodyLarge) }
        }
    }
}

/** Step numbers that skip section headers: `[header, step, step]` -> `[0, 1, 2]` (headers get 0). */
internal fun stepNumbers(isHeader: List<Boolean>): List<Int> {
    var n = 0
    return isHeader.map { header -> if (header) 0 else ++n }
}

@Composable
private fun SourceLine(recipe: RecipeEntity, openUrl: (String) -> Unit) {
    val name = recipe.sourceName?.takeIf { it.isNotBlank() } ?: recipe.sourceUrl?.takeIf { it.isNotBlank() } ?: return
    val url = recipe.sourceUrl?.trim()?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
    val text = stringResource(R.string.detail_source, name)
    if (url != null) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            textDecoration = TextDecoration.Underline,
            modifier = Modifier.clickable { openUrl(url) },
        )
    } else {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TimeGroup(recipe: RecipeEntity) {
    val items = listOf(
        R.string.detail_time_total to recipe.totalMinutes,
        R.string.detail_time_prep to recipe.prepMinutes,
        R.string.detail_time_cook to recipe.cookMinutes,
    ).filter { it.second != null }
    if (items.isEmpty()) return
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.fillMaxWidth()) {
        items.forEachIndexed { index, (label, minutes) ->
            Surface(
                // Horizontal connected group: only the outer left/right ends are fully rounded.
                shape = RoundedCornerShape(
                    topStart = if (index == 0) 24.dp else 8.dp,
                    bottomStart = if (index == 0) 24.dp else 8.dp,
                    topEnd = if (index == items.size - 1) 24.dp else 8.dp,
                    bottomEnd = if (index == items.size - 1) 24.dp else 8.dp,
                ),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.weight(1f),
            ) {
                Column(Modifier.padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(R.string.detail_minutes, minutes ?: 0), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
private fun ServingsStepper(servings: Double, onStep: (Int) -> Unit) {
    val shown = if (servings == servings.toLong().toDouble()) servings.toLong().toString() else servings.toString()
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.detail_servings), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        val fewer = stringResource(R.string.detail_servings_decrease)
        val more = stringResource(R.string.detail_servings_increase)
        FilledTonalIconButton(onClick = { onStep(-1) }, modifier = Modifier.semantics { contentDescription = fewer }) {
            Text("−", style = MaterialTheme.typography.titleLarge)
        }
        Text(shown, style = MaterialTheme.typography.titleLarge)
        FilledTonalIconButton(onClick = { onStep(1) }, modifier = Modifier.semantics { contentDescription = more }) {
            Text("+", style = MaterialTheme.typography.titleLarge)
        }
    }
}

/** Multi-select of ingredients (all ticked at first), shown with the quantities for the current servings. */
@Composable
private fun AddToShoppingDialog(
    ingredients: List<IngredientEntity>,
    scale: Double,
    onConfirm: (Set<Long>) -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by remember { mutableStateOf(ingredients.map { it.id }.toSet()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.shopping_add_title)) },
        text = {
            LazyColumn {
                items(ingredients, key = { it.id }) { ingredient ->
                    val checked = ingredient.id in selected
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .toggleable(checked, role = Role.Checkbox) {
                                selected = if (it) selected + ingredient.id else selected - ingredient.id
                            },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = checked, onCheckedChange = null)
                        Text(
                            QuantityScaler.scale(ingredient.text, scale),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selected) }, enabled = selected.isNotEmpty()) {
                Text(stringResource(R.string.shopping_add_confirm))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun RatingDialog(current: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.detail_rate_title)) },
        text = {
            Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                (1..RecipeDetailViewModel.MAX_RATING).forEach { star ->
                    val cd = stringResource(R.string.edit_star_cd, star)
                    IconButton(onClick = { onPick(if (star == current) 0 else star) }) {
                        Icon(
                            Icons.Filled.Star,
                            contentDescription = cd,
                            tint = if (star <= current) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outlineVariant,
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}
