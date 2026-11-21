package com.postpole.spoonfile.ui.mealplan

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.postpole.spoonfile.R
import com.postpole.spoonfile.ui.recipe.RecipePhoto
import com.postpole.spoonfile.ui.theme.SpoonfilePolygons
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * The weekly meal plan. Pro only: a free user (or one whose entitlement is not Pro) is sent to the Pro screen
 * through [onLocked] and nothing here ever writes for them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealPlanScreen(
    onBack: () -> Unit,
    onOpenRecipe: (Long) -> Unit,
    onCook: (Long) -> Unit,
    onLocked: () -> Unit,
    viewModel: MealPlanViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    var pickerDate by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(state.access) {
        if (state.access == MealPlanAccess.Locked) onLocked()
    }
    val visibleCount = state.days.sumOf { it.entries.size }
    LaunchedEffect(state.access) {
        // Once, when a Pro user has the plan on screen; the count is the visible week's entries only.
        if (state.access == MealPlanAccess.Pro) viewModel.onViewed(visibleCount)
    }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is MealPlanEvent.AddedToShopping -> snackbar.showSnackbar(
                    if (event.count > 0) {
                        context.resources.getQuantityString(R.plurals.shopping_added, event.count, event.count)
                    } else {
                        context.getString(R.string.meal_plan_nothing_to_add)
                    },
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
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (state.access != MealPlanAccess.Pro) {
                // Loading, or about to leave for the Pro screen: show no plan content.
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            } else {
                Column(Modifier.fillMaxSize()) {
                    WeekHeader(
                        weekStart = state.weekStart,
                        onPrevious = viewModel::previousWeek,
                        onNext = viewModel::nextWeek,
                        onThisWeek = viewModel::thisWeek,
                    )
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(state.days, key = { it.date.toString() }) { day ->
                            DayCard(
                                day = day,
                                viewModel = viewModel,
                                onAdd = { pickerDate = day.date.toString() },
                                onOpenRecipe = onOpenRecipe,
                                onCook = onCook,
                            )
                        }
                        item { Spacer(Modifier.navigationBarsPadding().height(8.dp)) }
                    }
                }
            }
        }
    }

    pickerDate?.let { iso ->
        RecipePicker(
            viewModel = viewModel,
            onPick = { recipeId ->
                viewModel.addRecipe(LocalDate.parse(iso), recipeId)
                pickerDate = null
            },
            onDismiss = { pickerDate = null },
        )
    }
}

@Composable
private fun WeekHeader(weekStart: LocalDate, onPrevious: () -> Unit, onNext: () -> Unit, onThisWeek: () -> Unit) {
    val formatter = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.getDefault()) }
    val label = formatter.format(weekStart) + " – " + formatter.format(weekStart.plusDays(6))
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.meal_plan_previous_week))
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.titleMedium)
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
    viewModel: MealPlanViewModel,
    onAdd: () -> Unit,
    onOpenRecipe: (Long) -> Unit,
    onCook: (Long) -> Unit,
) {
    val formatter = remember { DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault()) }
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(formatter.format(day.date), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (day.isToday) {
                    Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.primaryContainer) {
                        Text(
                            stringResource(R.string.meal_plan_today),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }
            }
            if (day.entries.isEmpty()) {
                Text(
                    stringResource(R.string.meal_plan_empty_day),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                day.entries.forEach { row ->
                    EntryRow(
                        row = row,
                        viewModel = viewModel,
                        onOpenRecipe = onOpenRecipe,
                        onCook = onCook,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onAdd) { Text(stringResource(R.string.meal_plan_add_recipe)) }
                if (day.entries.isNotEmpty()) {
                    TextButton(onClick = { viewModel.addDayToShopping(day.date) }) {
                        Text(stringResource(R.string.meal_plan_add_day_to_shopping))
                    }
                }
            }
        }
    }
}

@Composable
private fun EntryRow(
    row: com.postpole.spoonfile.data.MealPlanRow,
    viewModel: MealPlanViewModel,
    onOpenRecipe: (Long) -> Unit,
    onCook: (Long) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clickable { onOpenRecipe(row.recipeId) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RecipePhoto(
            imageFile = row.imageFile,
            load = viewModel::loadPhoto,
            shape = SpoonfilePolygons.thumbnail,
            modifier = Modifier.size(48.dp),
            maxEdge = 160,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            row.title,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Box {
            IconButton(onClick = { menu = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.meal_plan_entry_actions))
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.meal_plan_open_recipe)) },
                    onClick = { menu = false; onOpenRecipe(row.recipeId) },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.detail_cook_mode)) },
                    onClick = { menu = false; onCook(row.recipeId) },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.detail_add_to_shopping)) },
                    onClick = { menu = false; viewModel.addRecipeToShopping(row.recipeId) },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.meal_plan_remove)) },
                    onClick = { menu = false; viewModel.remove(row.id) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecipePicker(viewModel: MealPlanViewModel, onPick: (Long) -> Unit, onDismiss: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val recipes by remember(query) { viewModel.searchRecipes(query) }.collectAsState(initial = emptyList())
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 16.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.meal_plan_pick_title), style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                label = { Text(stringResource(R.string.recipes_search_hint)) },
                modifier = Modifier.fillMaxWidth(),
            )
            if (recipes.isEmpty()) {
                Text(
                    stringResource(R.string.recipes_no_results),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            }
            LazyColumn(Modifier.fillMaxWidth().height(420.dp)) {
                items(recipes, key = { it.id }) { recipe ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onPick(recipe.id) }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RecipePhoto(
                            imageFile = recipe.imageFile,
                            load = viewModel::loadPhoto,
                            shape = SpoonfilePolygons.thumbnail,
                            modifier = Modifier.size(40.dp),
                            maxEdge = 120,
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(recipe.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}
