package com.postpole.spoonfile.ui

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import android.net.Uri
import com.postpole.spoonfile.ads.rememberAdsController
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.postpole.spoonfile.ui.importer.ImportLinkScreen
import com.postpole.spoonfile.ui.importer.PaprikaImportScreen
import com.postpole.spoonfile.ui.importer.ScanScreen
import com.postpole.spoonfile.ui.mealplan.MealPlanScreen
import com.postpole.spoonfile.ui.pro.ProScreen
import com.postpole.spoonfile.ui.recipe.CookModeScreen
import com.postpole.spoonfile.ui.recipe.RecipeDetailScreen
import com.postpole.spoonfile.ui.recipe.RecipeEditScreen
import com.postpole.spoonfile.ui.recipes.RecipeListScreen
import com.postpole.spoonfile.ui.shopping.ShoppingListScreen
import com.postpole.spoonfile.ui.settings.CategoriesScreen
import com.postpole.spoonfile.ui.settings.SettingsScreen

object Routes {
    const val RECIPES = "recipes"
    const val SETTINGS = "settings"
    /** The Pro screen; the optional `trigger` argument (`auto_<trigger>`) is set when opened from an automatic prompt. */
    const val PRO = "pro?trigger={trigger}"
    const val CATEGORIES = "categories"

    /** Recipe detail; the `id` argument is the recipe's row id. */
    const val RECIPE = "recipe/{id}"
    const val COOK = "recipe/{id}/cook"

    /** Edit screen; `id` 0 (the default) creates a new recipe. */
    const val EDIT = "recipe-edit?id={id}"

    // Stub destinations, filled in by later issues.
    const val SHOPPING = "shopping"
    const val MEAL_PLAN = "meal-plan"
    /** Import from a web link; the optional `url` argument (a shared link) starts the import straight away. */
    const val IMPORT_LINK = "import/link?url={url}"
    const val SCAN = "import/scan"
    const val IMPORT_FILE = "import/file"

    fun pro(trigger: String = "") = "pro?trigger=$trigger"
    fun recipe(id: Long) = "recipe/$id"
    fun cook(id: Long) = "recipe/$id/cook"
    fun edit(id: Long = 0L) = "recipe-edit?id=$id"
    fun importLink(url: String = "") = "import/link?url=${Uri.encode(url)}"
}

private val idArg = listOf(navArgument("id") { type = NavType.LongType })
private val optionalIdArg = listOf(navArgument("id") { type = NavType.LongType; defaultValue = 0L })
private val urlArg = listOf(navArgument("url") { type = NavType.StringType; defaultValue = "" })
private val triggerArg = listOf(navArgument("trigger") { type = NavType.StringType; defaultValue = "" })

/**
 * [sharedText] is a link (or empty text) received from another app's Share sheet; it is consumed once via
 * [onSharedTextHandled] after routing to the import screen.
 */
@Composable
fun AppNavHost(
    onScreen: (route: String, proTrigger: String?) -> Unit = { _, _ -> },
    sharedText: String? = null,
    onSharedTextHandled: () -> Unit = {},
) {
    val navController = rememberNavController()
    val ads = rememberAdsController()
    val activity = LocalActivity.current
    val scope = rememberCoroutineScope()
    DisposableEffect(navController, ads, activity) {
        var previousRoute: String? = null
        var pendingInterstitial: Job? = null
        val listener = NavController.OnDestinationChangedListener { _, destination, args ->
            val route = destination.route
            route?.let { onScreen(it, args?.getString("trigger")) }
            // Returning to the list from another screen is a rest point for the automatic Pro sheet.
            if (route == Routes.RECIPES && previousRoute != null) ads.proPrompts.restPointPending = true
            // Any navigation abandons a pending interstitial.
            pendingInterstitial?.cancel()
            pendingInterstitial = null
            when {
                // Reading a recipe: warm one interstitial (only requested if it could be shown).
                route == Routes.RECIPE || route == Routes.COOK -> ads.preloadInterstitial()
                // The one interstitial placement: arriving at the list after leaving detail / cook mode.
                route == Routes.RECIPES && (previousRoute == Routes.RECIPE || previousRoute == Routes.COOK) &&
                    activity != null -> {
                    pendingInterstitial = scope.launch {
                        ads.showAfterRecipe(
                            activity,
                            stillOnList = {
                                // A Pro prompt chosen on arrival wins; the interstitial is skipped for this session.
                                navController.currentDestination?.route == Routes.RECIPES &&
                                    !ads.proPrompts.shownThisSession
                            },
                        )
                    }
                }
            }
            previousRoute = route
        }
        navController.addOnDestinationChangedListener(listener)
        onDispose {
            navController.removeOnDestinationChangedListener(listener)
            pendingInterstitial?.cancel()
        }
    }
    val openPro = { navController.navigate(Routes.pro()) }
    NavHost(navController = navController, startDestination = Routes.RECIPES) {
        composable(Routes.RECIPES) {
            RecipeListScreen(
                onOpenRecipe = { navController.navigate(Routes.recipe(it)) },
                onNewRecipe = { navController.navigate(Routes.edit()) },
                onPasteLink = { navController.navigate(Routes.importLink()) },
                onScanPage = { navController.navigate(Routes.SCAN) },
                onImportFile = { navController.navigate(Routes.IMPORT_FILE) },
                onOpenShopping = { navController.navigate(Routes.SHOPPING) },
                onOpenMealPlan = { navController.navigate(Routes.MEAL_PLAN) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenPro = { openPro() },
                onOpenProFromPrompt = { navController.navigate(Routes.pro(it)) },
            )
        }
        composable(Routes.RECIPE, arguments = idArg) {
            RecipeDetailScreen(
                onBack = { navController.popBackStack() },
                onEdit = { navController.navigate(Routes.edit(it)) },
                onCook = { navController.navigate(Routes.cook(it)) },
                onOpenShopping = { navController.navigate(Routes.SHOPPING) },
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
                onOpenCategories = { navController.navigate(Routes.CATEGORIES) },
            )
        }
        composable(Routes.CATEGORIES) {
            CategoriesScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.PRO, arguments = triggerArg) {
            ProScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.SHOPPING) {
            ShoppingListScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.MEAL_PLAN) {
            MealPlanScreen(
                onBack = { navController.popBackStack() },
                onOpenRecipe = { navController.navigate(Routes.recipe(it)) },
                onCook = { navController.navigate(Routes.cook(it)) },
                // A free user is sent to Pro in place of the plan, so Back returns to the recipes.
                onOpenPro = {
                    navController.navigate(Routes.pro()) { popUpTo(Routes.MEAL_PLAN) { inclusive = true } }
                },
            )
        }
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
        composable(Routes.SCAN) {
            ScanScreen(
                onBack = { navController.popBackStack() },
                onOpenRecipe = { id ->
                    navController.navigate(Routes.recipe(id)) { popUpTo(Routes.SCAN) { inclusive = true } }
                },
                onOpenPro = { openPro() },
            )
        }
        composable(Routes.IMPORT_FILE) {
            PaprikaImportScreen(onBack = { navController.popBackStack() }, onOpenPro = { openPro() })
        }
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
