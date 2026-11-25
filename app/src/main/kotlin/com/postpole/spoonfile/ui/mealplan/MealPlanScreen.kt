package com.postpole.spoonfile.ui.mealplan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.postpole.spoonfile.R
import com.postpole.spoonfile.data.MealPlanRow
import com.postpole.spoonfile.data.RecipeEntity
import com.postpole.spoonfile.ui.recipe.RecipePhoto
import com.postpole.spoonfile.ui.showLatestSnackbar
import com.postpole.spoonfile.ui.theme.SpoonfilePolygons
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.flow.collectLatest

/**
 * The weekly meal plan (Pro). Free users are sent to the Pro screen via [onOpenPro]; nothing is shown or
 * saved for them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealPlanScreen(
    onBack: () -> Unit,
    onOpenRecipe: (Long) -> Unit,
    onCook: (Long) -> Unit,
    onOpenPro: () -> Unit,
    viewModel: MealPlanViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val emptyMessage = stringResource(R.string.meal_plan_nothing_to_shop)
    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest { event ->
            when (event) {
                MealPlanEvent.OpenPro -> onOpenPro()
                is MealPlanEvent.AddedToShopping -> snackbar.showLatestSnackbar(
                    if (event.count == 0) emptyMessage else context.resources.getQuantityString(R.plurals.meal_plan_shopping_added, event.count, event.count),
                )
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_meal_plan)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        if (!state.isPro) {
            // Unknown or free: nothing is shown; a free user is sent to Pro by the event above.
            Spacer(Modifier.padding(padding).fillMaxSize())
            return@Scaffold
        }
        val dateFormat = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }
        val weekdayFormat = remember { DateTimeFormatter.ofPattern("EEEE") }
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                WeekHeader(
                    start = state.weekStart,
                    format = dateFormat,
                    onPrevious = viewModel::previousWeek,
                    onNext = viewModel::nextWeek,
                    onThisWeek = viewModel::thisWeek,
                )
            }
            items(state.days, key = { it.date.toString() }) { day ->
                DayCard(
                    day = day,
                    weekday = day.date.format(weekdayFormat),
                    date = day.date.format(dateFormat),
                    viewModel = viewModel,
                    onOpenRecipe = onOpenRecipe,
                    onCook = onCook,
                )
            }
        }
        val pickerDate = state.pickerDate
        if (pickerDate != null) {
            RecipePicker(
                query = state.pickerQuery,
                recipes = state.pickerRecipes,
                onQuery = viewModel::setPickerQuery,
                onPick = viewModel::addRecipe,
                onDismiss = viewModel::closePicker,
                viewModel = viewModel,
            )
        }
    }
}

@Composable
private fun WeekHeader(
    start: LocalDate,
    format: DateTimeFormatter,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onThisWeek: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.meal_plan_previous_week))
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                stringResource(R.string.meal_plan_week_range, start.format(format), start.plusDays(6).format(format)),
                style = MaterialTheme.typography.titleMedium,
            )
            TextButton(onClick = onThisWeek) { Text(stringResource(R.string.meal_plan_this_week)) }
        }
        IconButton(onClick = onNext) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.meal_plan_next_week))
        }
    }
}

@Composable
private fun DayCard(
    day: MealPlanDay,
    weekday: String,
    date: String,
    viewModel: MealPlanViewModel,
    onOpenRecipe: (Long) -> Unit,
    onCook: (Long) -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = if (day.isToday) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(weekday, style = MaterialTheme.typography.titleMedium)
                    Text(date, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (day.isToday) {
                    Text(stringResource(R.string.meal_plan_today), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
            }
            if (day.entries.isEmpty()) {
                Text(
                    stringResource(R.string.meal_plan_day_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                day.entries.forEach { row ->
                    EntryRow(row, viewModel, onOpenRecipe, onCook)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { viewModel.openPicker(day.date) }) {
                    Text(stringResource(R.string.meal_plan_add_recipe))
                }
            }
            if (day.entries.isNotEmpty()) {
                TextButton(onClick = { viewModel.addDayToShopping(day) }) {
                    Text(stringResource(R.string.meal_plan_add_day_to_shopping))
                }
            }
        }
    }
}

@Composable
private fun EntryRow(row: MealPlanRow, viewModel: MealPlanViewModel, onOpenRecipe: (Long) -> Unit, onCook: (Long) -> Unit) {
    Surface(
        onClick = { onOpenRecipe(row.entry.recipeId) },
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RecipePhoto(
                    imageFile = row.imageFile,
                    load = viewModel::loadPhoto,
                    shape = SpoonfilePolygons.thumbnail,
                    modifier = Modifier.size(48.dp),
                    maxEdge = 160,
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    row.recipeTitle,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                TextButton(onClick = { onCook(row.entry.recipeId) }) { Text(stringResource(R.string.detail_cook_mode)) }
                TextButton(onClick = { viewModel.addEntryToShopping(row.entry.recipeId) }) {
                    Text(stringResource(R.string.meal_plan_add_to_shopping))
                }
                TextButton(onClick = { viewModel.remove(row.entry.id) }) { Text(stringResource(R.string.meal_plan_remove)) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecipePicker(
    query: String,
    recipes: List<RecipeEntity>,
    onQuery: (String) -> Unit,
    onPick: (Long) -> Unit,
    onDismiss: () -> Unit,
    viewModel: MealPlanViewModel,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.meal_plan_pick_title), style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                label = { Text(stringResource(R.string.recipes_search_hint)) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQuery("") }) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.close))
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            if (recipes.isEmpty()) {
                Text(stringResource(R.string.meal_plan_pick_empty), modifier = Modifier.padding(vertical = 16.dp))
            }
            LazyColumn(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(recipes, key = { it.id }) { recipe ->
                    Surface(
                        onClick = { onPick(recipe.id) },
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            RecipePhoto(
                                imageFile = recipe.imageFile,
                                load = viewModel::loadPhoto,
                                shape = SpoonfilePolygons.thumbnail,
                                modifier = Modifier.size(48.dp),
                                maxEdge = 160,
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(recipe.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            Spacer(Modifier.size(16.dp))
        }
    }
}
