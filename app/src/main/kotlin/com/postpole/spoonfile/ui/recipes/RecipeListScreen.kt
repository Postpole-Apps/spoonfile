package com.postpole.spoonfile.ui.recipes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.res.pluralStringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.postpole.spoonfile.BuildConfig
import com.postpole.spoonfile.R
import com.postpole.spoonfile.ads.AdBanner
import com.postpole.spoonfile.analytics.ProPromptDismissMethod
import com.postpole.spoonfile.analytics.TodayPlanTarget
import com.postpole.spoonfile.pro.ProPromptTrigger
import com.postpole.spoonfile.pro.ProPromptViewModel
import com.postpole.spoonfile.data.CategoryWithCount
import com.postpole.spoonfile.data.Limits
import com.postpole.spoonfile.data.RecipeEntity
import com.postpole.spoonfile.data.RecipeSort
import com.postpole.spoonfile.ui.recipe.lastCookedText
import com.postpole.spoonfile.ui.recipe.BottomFade
import com.postpole.spoonfile.ui.recipe.RecipePhoto
import com.postpole.spoonfile.ui.recipe.connectedShape
import com.postpole.spoonfile.ui.theme.ConnectedGap
import com.postpole.spoonfile.ui.theme.FabShape
import com.postpole.spoonfile.ui.theme.SpoonfilePolygons

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RecipeListScreen(
    onOpenRecipe: (Long) -> Unit,
    onNewRecipe: () -> Unit,
    onPasteLink: () -> Unit,
    onScanPage: () -> Unit,
    onImportFile: () -> Unit,
    onOpenShopping: () -> Unit,
    onOpenMealPlan: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPro: () -> Unit,
    /** Opens the Pro screen from an automatic prompt; the argument is the `auto_<trigger>` value. */
    onOpenAutoPro: (String) -> Unit,
    onOpenCook: (Long) -> Unit = {},
    viewModel: RecipeListViewModel = hiltViewModel(),
    promptViewModel: ProPromptViewModel = hiltViewModel(),
) {
    // Rolls the Today's plan card over to the new day when the app returns to the front.
    LifecycleResumeEffect(Unit) {
        viewModel.onResume()
        onPauseOrDispose { }
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val activePrompt by promptViewModel.active.collectAsStateWithLifecycle()
    val proPrice by promptViewModel.price.collectAsStateWithLifecycle()
    val openFromPrompt = { promptViewModel.onCtaTap()?.let { onOpenAutoPro(it.proScreenTrigger) } }
    var showAddSheet by rememberSaveable { mutableStateOf(false) }
    var showCategories by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                RecipeListEvent.ShowAddSheet -> showAddSheet = true
                RecipeListEvent.OpenPro -> {
                    showAddSheet = false
                    onOpenPro()
                }
                is RecipeListEvent.Start -> {
                    showAddSheet = false
                    when (event.action) {
                        AddAction.PASTE_LINK -> onPasteLink()
                        AddAction.SCAN_PAGE -> onScanPage()
                        AddAction.TYPE_IT_IN -> onNewRecipe()
                        AddAction.IMPORT_FILE -> onImportFile()
                    }
                }
            }
        }
    }

    // True only while the banner actually occupies the bottom slot (false after no-fill / ads off / Pro).
    var showBanner by remember { mutableStateOf(false) }

    Scaffold(
        // Reserved slot under the list: the banner never overlays rows, the toolbar or the FAB, and sits
        // above the navigation bar.
        bottomBar = {
            AdBanner(
                BuildConfig.AD_UNIT_BANNER_RECIPES,
                contentModifier = Modifier.navigationBarsPadding(),
                onOccupiedChange = { showBanner = it },
            )
        },
        topBar = {
            TopAppBar(
                title = {
                    if (state.showCounter) {
                        val text = stringResource(R.string.recipes_counter, state.count, Limits.FREE_RECIPE_LIMIT)
                        val cd = stringResource(R.string.recipes_counter_cd, state.count, Limits.FREE_RECIPE_LIMIT)
                        val tone = if (state.atLimit) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                        Text(
                            text,
                            style = MaterialTheme.typography.titleMedium,
                            color = tone,
                            modifier = Modifier
                                .clickable(onClick = onOpenPro)
                                .padding(vertical = 8.dp, horizontal = 4.dp)
                                .semantics { contentDescription = cd },
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onOpenShopping) {
                        Icon(painterResource(R.drawable.ic_cart), contentDescription = stringResource(R.string.nav_shopping_list))
                    }
                    IconButton(onClick = onOpenMealPlan) {
                        Icon(Icons.Filled.DateRange, contentDescription = stringResource(R.string.nav_meal_plan))
                    }
                    TextButton(onClick = onOpenPro) { Text(stringResource(R.string.nav_pro)) }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.nav_settings))
                    }
                },
            )
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                // With a banner the bar below already owns the navigation-bar inset, so the toolbar and FAB
                // (which pad for it themselves) must not add it a second time.
                .then(if (showBanner) Modifier.consumeWindowInsets(WindowInsets.navigationBars) else Modifier),
        ) {
            if (state.isEmptyLibrary) {
                EmptyState(
                    onPasteLink = { viewModel.onAddAction(AddAction.PASTE_LINK) },
                    onScan = { viewModel.onAddAction(AddAction.SCAN_PAGE) },
                    onImport = { viewModel.onAddAction(AddAction.IMPORT_FILE) },
                )
            } else {
                RecipeGrid(
                    state,
                    viewModel,
                    onOpenRecipe,
                    onOpenPro,
                    onOpenSettings,
                    onOpenCook,
                    onOpenMealPlan,
                    onSeeAll = { showCategories = true },
                    // Never alongside the limit or backup banners.
                    proCard = if (activePrompt == ProPromptTrigger.RECIPES_MILESTONE && !state.atLimit && !state.showBackupNudge) {
                        ProCardUi(proPrice, onSeePro = { openFromPrompt() }, onNotNow = { promptViewModel.onDismissed(ProPromptDismissMethod.NOT_NOW) })
                    } else {
                        null
                    },
                )
            }

            BottomFade(height = 140.dp, modifier = Modifier.align(Alignment.BottomCenter))

            HorizontalFloatingToolbar(
                expanded = true,
                colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .navigationBarsPadding()
                    .padding(start = 16.dp, bottom = 30.dp)
                    .height(64.dp),
                content = { ListToolbar(state, viewModel) },
            )
            FloatingActionButton(
                onClick = viewModel::onAddRequested,
                shape = FabShape,
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = 16.dp, bottom = 30.dp)
                    .size(72.dp),
            ) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.fab_add_recipe), modifier = Modifier.size(32.dp))
            }
        }
    }

    if (activePrompt == ProPromptTrigger.COOK_OR_SAVE_MILESTONE) {
        ProPromptSheet(
            price = proPrice,
            onUnlock = { openFromPrompt() },
            onDismiss = promptViewModel::onDismissed,
        )
    }
    if (showAddSheet) {
        AddRecipeSheet(
            onDismiss = { showAddSheet = false },
            onAction = viewModel::onAddAction,
        )
    }
    if (showCategories) {
        CategoriesSheet(
            categories = state.categories,
            selectedId = state.selectedCategoryId,
            onDismiss = { showCategories = false },
            onSelect = {
                viewModel.selectCategory(it)
                showCategories = false
            },
        )
    }
}

@Composable
private fun ListToolbar(state: RecipeListUiState, viewModel: RecipeListViewModel) {
    var sortMenu by rememberSaveable { mutableStateOf(false) }
    IconButton(onClick = viewModel::toggleGrid) {
        if (state.grid) {
            Icon(Icons.Filled.List, contentDescription = stringResource(R.string.toolbar_list))
        } else {
            Icon(painterResource(R.drawable.ic_grid), contentDescription = stringResource(R.string.toolbar_grid))
        }
    }
    IconButton(onClick = viewModel::toggleFavoritesOnly) {
        Icon(
            if (state.favoritesOnly) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
            contentDescription = stringResource(R.string.toolbar_favorites),
        )
    }
    Box {
        IconButton(onClick = { sortMenu = true }) {
            Icon(painterResource(R.drawable.ic_sort), contentDescription = stringResource(R.string.toolbar_sort))
        }
        DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
            listOf(
                RecipeSort.RECENT to R.string.sort_recent,
                RecipeSort.AZ to R.string.sort_az,
                RecipeSort.RATING to R.string.sort_rating,
                RecipeSort.LAST_COOKED to R.string.sort_last_cooked,
            ).forEach { (sort, label) ->
                DropdownMenuItem(
                    text = { Text(stringResource(label)) },
                    onClick = {
                        viewModel.setSort(sort)
                        sortMenu = false
                    },
                    trailingIcon = if (state.sort == sort) {
                        { Icon(Icons.Filled.Star, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    } else {
                        null
                    },
                )
            }
        }
    }
}

@Composable
private fun RecipeGrid(
    state: RecipeListUiState,
    viewModel: RecipeListViewModel,
    onOpenRecipe: (Long) -> Unit,
    onOpenPro: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenCook: (Long) -> Unit,
    onOpenMealPlan: () -> Unit,
    onSeeAll: () -> Unit,
    proCard: ProCardUi?,
) {
    val full: LazyGridItemSpanScope.() -> GridItemSpan = { GridItemSpan(maxLineSpan) }
    LazyVerticalGrid(
        columns = GridCells.Fixed(if (state.grid) 2 else 1),
        modifier = Modifier.fillMaxSize(),
        // Room so the last rows can scroll clear of the floating toolbar and its fade.
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 170.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = full) {
            Text(
                stringResource(R.string.recipes_title),
                style = MaterialTheme.typography.displayLarge,
            )
        }
        item(span = full) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                placeholder = { Text(stringResource(R.string.recipes_search_hint)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (state.showTodayPlan) {
            item(span = full, key = "today-plan") {
                TodayPlanCard(
                    items = state.todayPlan,
                    onOpenRecipe = {
                        viewModel.onTodayPlanTap(TodayPlanTarget.RECIPE)
                        onOpenRecipe(it)
                    },
                    onCook = {
                        viewModel.onTodayPlanTap(TodayPlanTarget.COOK)
                        onOpenCook(it)
                    },
                    onSeeWeek = {
                        viewModel.onTodayPlanTap(TodayPlanTarget.WEEK)
                        onOpenMealPlan()
                    },
                )
            }
        }
        if (state.atLimit) {
            item(span = full) { LimitBanner(onOpenPro) }
        }
        if (state.showBackupNudge) {
            item(span = full) { BackupNudgeBanner(onBackUp = onOpenSettings, onDismiss = viewModel::dismissBackupNudge) }
        }
        if (proCard != null) {
            item(span = full, key = "pro-card") {
                ProInlineCard(price = proCard.price, onSeePro = proCard.onSeePro, onNotNow = proCard.onNotNow)
            }
        }
        if (state.categories.isNotEmpty()) {
            item(span = full) {
                CategoryTiles(state, onSelect = viewModel::selectCategory, onSeeAll = onSeeAll)
            }
        }
        item(span = full) {
            Text(stringResource(R.string.recipes_recent), style = MaterialTheme.typography.titleLarge)
        }
        if (state.recipes.isEmpty()) {
            item(span = full) {
                Text(
                    stringResource(R.string.recipes_no_results),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(state.recipes, key = { it.id }) { recipe ->
            if (state.grid) {
                RecipeGridCard(recipe, viewModel) { onOpenRecipe(recipe.id) }
            } else {
                RecipeRow(recipe, viewModel) { onOpenRecipe(recipe.id) }
            }
        }
    }
}

private const val TODAY_PLAN_MAX_ITEMS = 3

/** Pro owners' planned recipes for today: up to three rows, "+N more", a Cook button each and "See the week". */
@Composable
internal fun TodayPlanCard(
    items: List<TodayPlanItem>,
    onOpenRecipe: (Long) -> Unit,
    onCook: (Long) -> Unit,
    onSeeWeek: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(R.string.recipes_today_plan_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            val openLabel = stringResource(R.string.recipes_today_plan_open_label)
            items.take(TODAY_PLAN_MAX_ITEMS).forEach { item ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clickable(onClickLabel = openLabel) { onOpenRecipe(item.recipeId) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        item.title,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    IconButton(onClick = { onCook(item.recipeId) }, modifier = Modifier.size(48.dp)) {
                        Icon(
                            Icons.Filled.PlayArrow,
                            contentDescription = stringResource(R.string.recipes_today_plan_cook_cd, item.title),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            val more = items.size - TODAY_PLAN_MAX_ITEMS
            if (more > 0) {
                Text(
                    pluralStringResource(R.plurals.recipes_today_plan_more, more, more),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            TextButton(onClick = onSeeWeek, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.recipes_today_plan_see_week))
            }
        }
    }
}

/** Monthly reminder; no notification permission needed because it only shows inside the app. */
@Composable
private fun BackupNudgeBanner(onBackUp: () -> Unit, onDismiss: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.recipes_backup_nudge), style = MaterialTheme.typography.bodyLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onBackUp) { Text(stringResource(R.string.backup_now)) }
                androidx.compose.material3.TextButton(onClick = onDismiss) { Text(stringResource(R.string.recipes_backup_nudge_dismiss)) }
            }
        }
    }
}

@Composable
private fun LimitBanner(onOpenPro: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.recipes_limit_banner, Limits.FREE_RECIPE_LIMIT), style = MaterialTheme.typography.bodyLarge)
            Button(onClick = onOpenPro) { Text(stringResource(R.string.pro_buy_no_price)) }
        }
    }
}

@Composable
private fun CategoryTiles(state: RecipeListUiState, onSelect: (Long?) -> Unit, onSeeAll: () -> Unit) {
    val shapes = SpoonfilePolygons.categoryTiles
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 4.dp),
    ) {
        itemsIndexed(state.visibleCategories, key = { _, c -> c.category.id }) { index, item ->
            CategoryTile(
                label = item.category.name,
                shape = shapes[index % shapes.size],
                selected = state.selectedCategoryId == item.category.id,
                onClick = { onSelect(item.category.id) },
            )
        }
        if (state.showSeeAll) {
            item(key = "see_all") {
                Surface(
                    onClick = onSeeAll,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(96.dp),
                ) {
                    Column(
                        Modifier.fillMaxSize().padding(8.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(painterResource(R.drawable.ic_grid), contentDescription = null)
                        Text(
                            stringResource(R.string.categories_see_all),
                            style = MaterialTheme.typography.labelLarge,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun CategoryTile(label: String, shape: Shape, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = shape,
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier.size(96.dp),
    ) {
        // Narrow side padding keeps the text area wide; one line, never wrapped, so a word can't be split.
        Box(Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 16.dp), contentAlignment = Alignment.Center) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun RecipeRow(recipe: RecipeEntity, viewModel: RecipeListViewModel, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            RecipePhoto(
                imageFile = recipe.imageFile,
                load = viewModel::loadPhoto,
                shape = SpoonfilePolygons.thumbnail,
                modifier = Modifier.size(56.dp),
                maxEdge = 160,
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(recipe.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                RecipeMeta(recipe)
            }
            if (recipe.favorite) {
                Icon(Icons.Filled.Favorite, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
            }
        }
    }
}

@Composable
private fun RecipeGridCard(recipe: RecipeEntity, viewModel: RecipeListViewModel, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            RecipePhoto(
                imageFile = recipe.imageFile,
                load = viewModel::loadPhoto,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth().height(110.dp),
                maxEdge = 480,
            )
            Text(recipe.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            RecipeMeta(recipe)
        }
    }
}

@Composable
private fun RecipeMeta(recipe: RecipeEntity) {
    val minutes = recipe.totalMinutes ?: recipe.cookMinutes
    val parts = buildList {
        if (recipe.rating > 0) add("★".repeat(recipe.rating))
        if (minutes != null) add(stringResource(R.string.detail_minutes, minutes))
    }
    if (parts.isNotEmpty()) {
        Text(
            parts.joinToString("  ·  "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    // Wraps at large font scales instead of clipping.
    lastCookedText(recipe.lastCookedAt)?.let {
        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EmptyState(onPasteLink: () -> Unit, onScan: () -> Unit, onImport: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp).padding(bottom = 120.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Text(
            stringResource(R.string.empty_title),
            style = MaterialTheme.typography.displaySmall,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        BigButton(stringResource(R.string.empty_paste_link), onPasteLink)
        BigButton(stringResource(R.string.empty_scan), onScan)
        BigButton(stringResource(R.string.empty_import_file), onImport)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.chrome_share_tip),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun BigButton(label: String, onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick, modifier = Modifier.fillMaxWidth().height(64.dp)) {
        Text(label, style = MaterialTheme.typography.titleMedium)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddRecipeSheet(onDismiss: () -> Unit, onAction: (AddAction) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        AddRecipeSheetContent(onAction)
    }
}

/** Sheet body. [insets] (the navigation bar by default) keeps the footer clear of the gesture bar. */
@Composable
internal fun AddRecipeSheetContent(
    onAction: (AddAction) -> Unit,
    insets: WindowInsets = WindowInsets.navigationBars,
) {
    run {
        Column(
            Modifier.windowInsetsPadding(insets).padding(horizontal = 16.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(ConnectedGap),
        ) {
            Text(
                stringResource(R.string.add_sheet_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(start = 8.dp, bottom = 12.dp),
            )
            val rows = listOf(
                Triple(AddAction.PASTE_LINK, R.string.add_paste_link, R.string.add_paste_link_hint),
                Triple(AddAction.SCAN_PAGE, R.string.add_scan, R.string.add_scan_hint),
                Triple(AddAction.TYPE_IT_IN, R.string.add_type_it_in, R.string.add_type_it_in_hint),
                Triple(AddAction.IMPORT_FILE, R.string.add_import_file, R.string.add_import_file_hint),
            )
            rows.forEachIndexed { index, (action, title, hint) ->
                Surface(
                    onClick = { onAction(action) },
                    shape = connectedShape(index, rows.size),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth().height(72.dp),
                ) {
                    Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.Center) {
                        Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.chrome_share_tip),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            Text(
                stringResource(R.string.add_privacy),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoriesSheet(
    categories: List<CategoryWithCount>,
    selectedId: Long?,
    onDismiss: () -> Unit,
    onSelect: (Long?) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        androidx.compose.foundation.lazy.LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(ConnectedGap),
        ) {
            item {
                Text(
                    stringResource(R.string.categories_title),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(start = 8.dp, bottom = 12.dp),
                )
            }
            item {
                CategoryRow(stringResource(R.string.categories_all), null, selectedId == null, 0, categories.size + 1) { onSelect(null) }
            }
            items(categories.size) { index ->
                val c = categories[index]
                CategoryRow(c.category.name, c.recipeCount, selectedId == c.category.id, index + 1, categories.size + 1) {
                    onSelect(c.category.id)
                }
            }
        }
    }
}

@Composable
private fun CategoryRow(name: String, count: Int?, selected: Boolean, index: Int, total: Int, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = connectedShape(index, total),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth().height(56.dp),
    ) {
        Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            count?.let { Text(it.toString(), style = MaterialTheme.typography.bodyMedium) }
        }
    }
}
