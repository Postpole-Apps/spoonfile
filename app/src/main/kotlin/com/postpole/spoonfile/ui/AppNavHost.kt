package com.postpole.spoonfile.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import android.net.Uri
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.postpole.spoonfile.R
import com.postpole.spoonfile.ui.importer.ImportLinkScreen
import com.postpole.spoonfile.ui.pro.ProScreen
import com.postpole.spoonfile.ui.recipe.CookModeScreen
import com.postpole.spoonfile.ui.recipe.RecipeDetailScreen
import com.postpole.spoonfile.ui.recipe.RecipeEditScreen
import com.postpole.spoonfile.ui.recipes.RecipeListScreen
import com.postpole.spoonfile.ui.settings.SettingsScreen

object Routes {
    const val RECIPES = "recipes"
    const val SETTINGS = "settings"
    const val PRO = "pro"

    /** Recipe detail; the `id` argument is the recipe's row id. */
    const val RECIPE = "recipe/{id}"
    const val COOK = "recipe/{id}/cook"

    /** Edit screen; `id` 0 (the default) creates a new recipe. */
    const val EDIT = "recipe-edit?id={id}"

    // Stub destinations, filled in by later issues.
    const val SHOPPING = "shopping"
    /** Import from a web link; the optional `url` argument (a shared link) starts the import straight away. */
    const val IMPORT_LINK = "import/link?url={url}"
    const val SCAN = "import/scan"
    const val IMPORT_FILE = "import/file"

    fun recipe(id: Long) = "recipe/$id"
    fun cook(id: Long) = "recipe/$id/cook"
    fun edit(id: Long = 0L) = "recipe-edit?id=$id"
    fun importLink(url: String = "") = "import/link?url=${Uri.encode(url)}"
}

private val idArg = listOf(navArgument("id") { type = NavType.LongType })
private val optionalIdArg = listOf(navArgument("id") { type = NavType.LongType; defaultValue = 0L })
private val urlArg = listOf(navArgument("url") { type = NavType.StringType; defaultValue = "" })

/**
 * [sharedText] is a link (or empty text) received from another app's Share sheet; it is consumed once via
 * [onSharedTextHandled] after routing to the import screen.
 */
@Composable
fun AppNavHost(
    onScreen: (String) -> Unit = {},
    sharedText: String? = null,
    onSharedTextHandled: () -> Unit = {},
) {
    val navController = rememberNavController()
    DisposableEffect(navController) {
        val listener = NavController.OnDestinationChangedListener { _, destination, _ ->
            destination.route?.let(onScreen)
        }
        navController.addOnDestinationChangedListener(listener)
        onDispose { navController.removeOnDestinationChangedListener(listener) }
    }
    val openPro = { navController.navigate(Routes.PRO) }
    NavHost(navController = navController, startDestination = Routes.RECIPES) {
        composable(Routes.RECIPES) {
            RecipeListScreen(
                onOpenRecipe = { navController.navigate(Routes.recipe(it)) },
                onNewRecipe = { navController.navigate(Routes.edit()) },
                onPasteLink = { navController.navigate(Routes.importLink()) },
                onScanPage = { navController.navigate(Routes.SCAN) },
                onImportFile = { navController.navigate(Routes.IMPORT_FILE) },
                onOpenShopping = { navController.navigate(Routes.SHOPPING) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenPro = { openPro() },
            )
        }
        composable(Routes.RECIPE, arguments = idArg) {
            RecipeDetailScreen(
                onBack = { navController.popBackStack() },
                onEdit = { navController.navigate(Routes.edit(it)) },
                onCook = { navController.navigate(Routes.cook(it)) },
            )
        }
        composable(Routes.COOK, arguments = idArg) {
            CookModeScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.EDIT, arguments = optionalIdArg) {
            RecipeEditScreen(
                onBack = { navController.popBackStack() },
                onSaved = { id ->
                    // Saving a new recipe lands on its detail page; editing returns to the existing one.
                    if (!navController.popBackStack(Routes.RECIPE, inclusive = false)) {
                        navController.navigate(Routes.recipe(id)) {
                            popUpTo(Routes.EDIT) { inclusive = true }
                        }
                    }
                },
                onOpenPro = { openPro() },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenPro = { openPro() },
            )
        }
        composable(Routes.PRO) {
            ProScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.SHOPPING) { StubScreen(R.string.nav_shopping_list, R.string.shopping_stub) { navController.popBackStack() } }
        composable(Routes.IMPORT_LINK, arguments = urlArg) {
            ImportLinkScreen(
                onBack = { navController.popBackStack() },
                onOpenRecipe = { id ->
                    navController.navigate(Routes.recipe(id)) { popUpTo(Routes.IMPORT_LINK) { inclusive = true } }
                },
                onTypeItIn = {
                    navController.navigate(Routes.edit()) { popUpTo(Routes.IMPORT_LINK) { inclusive = true } }
                },
                onOpenPro = { openPro() },
            )
        }
        composable(Routes.SCAN) { StubScreen(R.string.stub_scan, R.string.stub_coming_soon) { navController.popBackStack() } }
        composable(Routes.IMPORT_FILE) { StubScreen(R.string.stub_import_file, R.string.stub_coming_soon) { navController.popBackStack() } }
    }
    // After the NavHost so its graph is set before the first navigate (a link can arrive on a cold start).
    LaunchedEffect(sharedText) {
        if (sharedText != null) {
            // Replace any import already open so the new link starts fresh.
            navController.navigate(Routes.importLink(sharedText)) {
                popUpTo(Routes.IMPORT_LINK) { inclusive = true }
            }
            onSharedTextHandled()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StubScreen(title: Int, message: Int, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).padding(24.dp), contentAlignment = Alignment.Center) {
            Text(stringResource(message))
        }
    }
}
