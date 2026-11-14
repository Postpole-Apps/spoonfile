package com.postpole.spoonfile.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.postpole.spoonfile.R
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

    const val DETAIL = "recipe/{id}"
    const val EDIT = "recipe/{id}/edit"
    const val COOK = "recipe/{id}/cook"

    // Stub destinations, filled in by later issues.
    const val SHOPPING = "shopping"
    const val IMPORT_LINK = "import/link"
    const val IMPORT_SCAN = "import/scan"
    const val IMPORT_FILE = "import/file"

    fun detail(id: Long) = "recipe/$id"
    fun edit(id: Long) = "recipe/$id/edit"
    fun cook(id: Long) = "recipe/$id/cook"
}

private val idArgs = listOf(navArgument("id") { type = NavType.LongType })

@Composable
fun AppNavHost(onScreen: (String) -> Unit = {}) {
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
                onOpenRecipe = { navController.navigate(Routes.detail(it)) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenPro = { openPro() },
                onOpenShopping = { navController.navigate(Routes.SHOPPING) },
                onPasteLink = { navController.navigate(Routes.IMPORT_LINK) },
                onScan = { navController.navigate(Routes.IMPORT_SCAN) },
                onTypeIt = { navController.navigate(Routes.edit(0L)) },
                onImportFile = { navController.navigate(Routes.IMPORT_FILE) },
            )
        }
        composable(Routes.DETAIL, arguments = idArgs) {
            RecipeDetailScreen(
                onBack = { navController.popBackStack() },
                onEdit = { navController.navigate(Routes.edit(it)) },
                onCookMode = { navController.navigate(Routes.cook(it)) },
            )
        }
        composable(Routes.EDIT, arguments = idArgs) {
            RecipeEditScreen(
                onClose = { navController.popBackStack() },
                onSaved = { id, isNew ->
                    if (isNew) {
                        navController.navigate(Routes.detail(id)) {
                            popUpTo(Routes.EDIT) { inclusive = true }
                        }
                    } else {
                        navController.popBackStack()
                    }
                },
                onOpenPro = {
                    navController.navigate(Routes.PRO) { popUpTo(Routes.EDIT) { inclusive = true } }
                },
            )
        }
        composable(Routes.COOK, arguments = idArgs) {
            CookModeScreen(onBack = { navController.popBackStack() })
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
        composable(Routes.SHOPPING) {
            StubScreen(title = R.string.nav_shopping, onBack = { navController.popBackStack() })
        }
        composable(Routes.IMPORT_LINK) {
            StubScreen(title = R.string.add_paste_link, onBack = { navController.popBackStack() })
        }
        composable(Routes.IMPORT_SCAN) {
            StubScreen(title = R.string.add_scan, onBack = { navController.popBackStack() })
        }
        composable(Routes.IMPORT_FILE) {
            StubScreen(title = R.string.add_import, onBack = { navController.popBackStack() })
        }
    }
}
