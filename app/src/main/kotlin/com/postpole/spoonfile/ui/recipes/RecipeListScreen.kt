package com.postpole.spoonfile.ui.recipes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
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
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.postpole.spoonfile.R
import com.postpole.spoonfile.data.CategoryWithCount
import com.postpole.spoonfile.data.RecipeEntity
import com.postpole.spoonfile.data.RecipeSort
import com.postpole.spoonfile.ui.recipe.RatingStars
import com.postpole.spoonfile.ui.recipe.RecipePhoto
import com.postpole.spoonfile.ui.theme.ConnectedGap
import com.postpole.spoonfile.ui.theme.ConnectedInnerCorner
import com.postpole.spoonfile.ui.theme.ConnectedOuterCorner
import com.postpole.spoonfile.ui.theme.FabShape
import com.postpole.spoonfile.ui.theme.SpoonfilePolygons
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RecipeListScreen(
    onOpenRecipe: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPro: () -> Unit,
    onOpenShopping: () -> Unit,
    onPasteLink: () -> Unit,
    onScan: () -> Unit,
    onTypeIt: () -> Unit,
    onImportFile: () -> Unit,
    viewModel: RecipeListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showAddSheet by rememberSaveable { mutableStateOf(false) }
    var showAllCategories by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                RecipeListEvent.OpenPro -> {
                    showAddSheet = false
                    onOpenPro()
                }
                RecipeListEvent.ShowAddSheet -> showAddSheet = true
                is RecipeListEvent.Open -> {
                    showAddSheet = false
                    when (event.action) {
                        AddAction.PASTE_LINK -> onPasteLink()
                        AddAction.SCAN -> onScan()
                        AddAction.TYPE_IT -> onTypeIt()
                        AddAction.IMPORT_FILE -> onImportFile()
                    }
                }
            }
        }
    }

    Scaffold { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            Column(Modifier.fillMaxSize()) {
                TopRow(
                    state = state,
                    onOpenPro = onOpenPro,
                    onOpenShopping = onOpenShopping,
                    onOpenSettings = onOpenSettings,
                )
                RecipeContent(
                    state = state,
                    photoFile = viewModel::photoFile,
                    onQuery = viewModel::setQuery,
                    onToggleCategory = viewModel::toggleCategory,
                    onSeeAll = { showAllCategories = true },
                    onOpenRecipe = onOpenRecipe,
                    onOpenPro = onOpenPro,
                    onAddAction = viewModel::onAddAction,
                    modifier = Modifier.weight(1f),
                )
            }

            // Fade so list text is never half-covered by the toolbar / FAB.
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(140.dp)
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.45f to MaterialTheme.colorScheme.background.copy(alpha = 0.92f),
                            1f to MaterialTheme.colorScheme.background,
                        ),
                    ),
            )

            BottomToolbar(
                state = state,
                onToggleGrid = viewModel::toggleGrid,
                onToggleFavorites = viewModel::toggleFavoritesOnly,
                onSort = viewModel::setSort,
                modifier = Modifier.align(Alignment.BottomStart).padding(start = 16.dp, bottom = 30.dp),
            )
            FloatingActionButton(
                onClick = viewModel::onAddClicked,
                shape = FabShape,
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 30.dp)
                    .size(72.dp),
            ) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.add_recipe_cd), modifier = Modifier.size(32.dp))
            }
        }
    }

    if (showAddSheet) {
        AddRecipeSheet(onDismiss = { showAddSheet = false }, onAction = viewModel::onAddAction)
    }
    if (showAllCategories) {
        AllCategoriesSheet(
            categories = state.categories,
            selectedId = state.categoryId,
            onSelect = {
                viewModel.toggleCategory(it)
                showAllCategories = false
            },
            onDismiss = { showAllCategories = false },
        )
    }
}

@Composable
private fun TopRow(
    state: RecipeListState,
    onOpenPro: () -> Unit,
    onOpenShopping: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp).heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (state.showCounter) {
            val atCap = state.atCap
            Surface(
                onClick = onOpenPro,
                shape = CircleShape,
                color = if (atCap) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
                contentColor = if (atCap) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
            ) {
                Text(
                    stringResource(R.string.recipes_counter, state.total, state.limit),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onOpenShopping) {
            Icon(Icons.Filled.ShoppingCart, contentDescription = stringResource(R.string.nav_shopping))
        }
        TextButton(onClick = onOpenPro) { Text(stringResource(R.string.nav_pro)) }
        IconButton(onClick = onOpenSettings) {
            Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.nav_settings))
        }
    }
}

@Composable
private fun RecipeContent(
    state: RecipeListState,
    photoFile: (String) -> File,
    onQuery: (String) -> Unit,
    onToggleCategory: (Long) -> Unit,
    onSeeAll: () -> Unit,
    onOpenRecipe: (Long) -> Unit,
    onOpenPro: () -> Unit,
    onAddAction: (AddAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val columns = if (state.grid) 2 else 1
    val emptyLibrary = state.total == 0 && !state.isFiltered
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 168.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(if (state.grid) 12.dp else 4.dp),
    ) {
        item(key = "title", span = { GridItemSpan(maxLineSpan) }) {
            Text(
                stringResource(R.string.recipes_title),
                style = MaterialTheme.typography.displayLarge,
                modifier = Modifier.padding(top = 8.dp, bottom = 12.dp),
            )
        }
        item(key = "search", span = { GridItemSpan(maxLineSpan) }) {
            SearchField(state.query, onQuery)
        }
        if (state.categories.isNotEmpty()) {
            item(key = "categories", span = { GridItemSpan(maxLineSpan) }) {
                CategoryTilesRow(state, onToggleCategory, onSeeAll)
            }
        }
        if (state.atCap) {
            item(key = "limit", span = { GridItemSpan(maxLineSpan) }) {
                LimitBanner(state, onOpenPro)
            }
        }
        if (emptyLibrary) {
            item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                EmptyState(onAddAction)
            }
        } else {
            item(key = "header", span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    stringResource(
                        when {
                            state.isFiltered -> R.string.search_results
                            state.sort == RecipeSort.RECENT -> R.string.recently_added
                            else -> R.string.all_recipes
                        },
                    ),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                )
            }
            if (state.recipes.isEmpty()) {
                item(key = "none", span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        stringResource(R.string.empty_filtered),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                }
            }
            items(state.recipes, key = { it.id }) { recipe ->
                if (state.grid) {
                    RecipeCard(recipe, photoFile, onOpenRecipe)
                } else {
                    RecipeRow(recipe, photoFile, onOpenRecipe)
                }
            }
        }
    }
}

@Composable
private fun SearchField(query: String, onQuery: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQuery,
        singleLine = true,
        placeholder = { Text(stringResource(R.string.search_hint)) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQuery("") }) {
                    Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.search_clear))
                }
            }
        },
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun CategoryTilesRow(state: RecipeListState, onToggle: (Long) -> Unit, onSeeAll: () -> Unit) {
    val tiles = state.tiles
    val shapes = SpoonfilePolygons.categoryTiles
    LazyRow(
        contentPadding = PaddingValues(vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        items(tiles.visible, key = { it.category.id }) { item ->
            val index = tiles.visible.indexOf(item)
            val selected = state.categoryId == item.category.id
            CategoryTile(
                label = item.category.name,
                glyph = item.category.name.take(1).uppercase(),
                shape = shapes[index % shapes.size],
                selected = selected,
                onClick = { onToggle(item.category.id) },
            )
        }
        if (tiles.showSeeAll) {
            item(key = "see-all") {
                CategoryTile(
                    label = stringResource(R.string.categories_see_all),
                    glyph = null,
                    shape = shapes[tiles.visible.size % shapes.size],
                    selected = false,
                    onClick = onSeeAll,
                )
            }
        }
    }
}

@Composable
private fun CategoryTile(label: String, glyph: String?, shape: Shape, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.width(96.dp).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(96.dp)
                .clip(shape)
                .background(
                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer,
                ),
            contentAlignment = Alignment.Center,
        ) {
            val tint = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer
            if (glyph != null) {
                Text(glyph, style = MaterialTheme.typography.headlineMedium, color = tint)
            } else {
                Icon(Icons.Filled.ArrowForward, contentDescription = null, tint = tint)
            }
        }
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun LimitBanner(state: RecipeListState, onOpenPro: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                stringResource(R.string.limit_banner, state.total, state.limit),
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(onClick = onOpenPro) { Text(stringResource(R.string.unlock_pro)) }
        }
    }
}

@Composable
private fun EmptyState(onAddAction: (AddAction) -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(top = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            stringResource(R.string.empty_title),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        BigButton(R.drawable.ic_link, R.string.empty_paste_link) { onAddAction(AddAction.PASTE_LINK) }
        BigButton(R.drawable.ic_scan, R.string.empty_scan) { onAddAction(AddAction.SCAN) }
        BigButton(R.drawable.ic_file_import, R.string.empty_import) { onAddAction(AddAction.IMPORT_FILE) }
        Text(
            stringResource(R.string.chrome_tip),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun BigButton(icon: Int, label: Int, onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick, modifier = Modifier.fillMaxWidth().height(64.dp)) {
        Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(12.dp))
        Text(stringResource(label), style = MaterialTheme.typography.labelLarge)
    }
}

private fun subtitleMinutes(recipe: RecipeEntity): Int? =
    recipe.totalMinutes ?: ((recipe.prepMinutes ?: 0) + (recipe.cookMinutes ?: 0)).takeIf { it > 0 }

@Composable
private fun RecipeRow(recipe: RecipeEntity, photoFile: (String) -> File, onOpen: (Long) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onOpen(recipe.id) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        RecipePhoto(
            file = recipe.imageFile?.let(photoFile),
            shape = SpoonfilePolygons.thumbnail,
            modifier = Modifier.size(56.dp),
            maxEdgePx = 256,
        )
        Column(Modifier.weight(1f)) {
            Text(recipe.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            RecipeMeta(recipe)
        }
        if (recipe.favorite) {
            Icon(Icons.Filled.Favorite, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
        }
    }
}

@Composable
private fun RecipeCard(recipe: RecipeEntity, photoFile: (String) -> File, onOpen: (Long) -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable { onOpen(recipe.id) },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        RecipePhoto(
            file = recipe.imageFile?.let(photoFile),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f),
            maxEdgePx = 640,
        )
        Text(recipe.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        RecipeMeta(recipe)
    }
}

@Composable
private fun RecipeMeta(recipe: RecipeEntity) {
    val minutes = subtitleMinutes(recipe)
    if (minutes != null) {
        Text(
            stringResource(R.string.minutes_short, minutes),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (recipe.rating > 0) RatingStars(recipe.rating, starSize = 14.dp)
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BottomToolbar(
    state: RecipeListState,
    onToggleGrid: () -> Unit,
    onToggleFavorites: () -> Unit,
    onSort: (RecipeSort) -> Unit,
    modifier: Modifier = Modifier,
) {
    var sortMenu by rememberSaveable { mutableStateOf(false) }
    HorizontalFloatingToolbar(
        expanded = true,
        colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(),
        modifier = modifier.height(64.dp),
    ) {
        IconButton(onClick = onToggleGrid) {
            Icon(
                painterResource(if (state.grid) R.drawable.ic_view_list else R.drawable.ic_view_grid),
                contentDescription = stringResource(if (state.grid) R.string.view_list else R.string.view_grid),
            )
        }
        IconButton(onClick = onToggleFavorites) {
            Icon(
                if (state.favoritesOnly) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                contentDescription = stringResource(R.string.filter_favorites),
            )
        }
        Box {
            IconButton(onClick = { sortMenu = true }) {
                Icon(painterResource(R.drawable.ic_sort), contentDescription = stringResource(R.string.sort))
            }
            DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                listOf(
                    RecipeSort.RECENT to R.string.sort_recent,
                    RecipeSort.AZ to R.string.sort_az,
                    RecipeSort.RATING to R.string.sort_rating,
                ).forEach { (sort, label) ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(label),
                                color = if (state.sort == sort) MaterialTheme.colorScheme.primary else Color.Unspecified,
                            )
                        },
                        onClick = {
                            onSort(sort)
                            sortMenu = false
                        },
                    )
                }
            }
        }
    }
}

/** Rows are connected: outer corners large, inner corners small, small gaps. */
fun connectedShape(index: Int, count: Int): Shape {
    val outer = ConnectedOuterCorner
    val inner = ConnectedInnerCorner
    return RoundedCornerShape(
        topStart = if (index == 0) outer else inner,
        topEnd = if (index == 0) outer else inner,
        bottomStart = if (index == count - 1) outer else inner,
        bottomEnd = if (index == count - 1) outer else inner,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddRecipeSheet(onDismiss: () -> Unit, onAction: (AddAction) -> Unit) {
    val rows = listOf(
        Triple(AddAction.PASTE_LINK, R.drawable.ic_link, R.string.add_paste_link to R.string.add_paste_link_sub),
        Triple(AddAction.SCAN, R.drawable.ic_scan, R.string.add_scan to R.string.add_scan_sub),
        Triple(AddAction.TYPE_IT, null, R.string.add_type to R.string.add_type_sub),
        Triple(AddAction.IMPORT_FILE, R.drawable.ic_file_import, R.string.add_import to R.string.add_import_sub),
    )
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Text(
                stringResource(R.string.add_sheet_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(start = 8.dp, bottom = 16.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(ConnectedGap)) {
                rows.forEachIndexed { index, (action, icon, labels) ->
                    Surface(
                        onClick = { onAction(action) },
                        shape = connectedShape(index, rows.size),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth().height(72.dp),
                    ) {
                        Row(
                            Modifier.padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            Box(
                                Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    if (icon != null) painterResource(icon) else androidx.compose.ui.graphics.vector.rememberVectorPainter(
                                        Icons.Filled.Add,
                                    ),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                            Column {
                                Text(stringResource(labels.first), style = MaterialTheme.typography.titleMedium)
                                Text(
                                    stringResource(labels.second),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
            Text(
                stringResource(R.string.chrome_tip),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp, top = 16.dp),
            )
            Text(
                stringResource(R.string.privacy_line),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp, top = 4.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AllCategoriesSheet(
    categories: List<CategoryWithCount>,
    selectedId: Long?,
    onSelect: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        androidx.compose.foundation.lazy.LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(ConnectedGap),
        ) {
            item {
                Text(
                    stringResource(R.string.categories_title),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(start = 8.dp, bottom = 16.dp),
                )
            }
            items(categories.size) { index ->
                val c = categories[index]
                val selected = c.category.id == selectedId
                Surface(
                    onClick = { onSelect(c.category.id) },
                    shape = connectedShape(index, categories.size),
                    color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                ) {
                    Row(
                        Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(c.category.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Text(
                            c.recipeCount.toString(),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
