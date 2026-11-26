package com.postpole.spoonfile.ui.recipe

import android.app.Activity
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.postpole.spoonfile.analytics.Analytics
import com.postpole.spoonfile.billing.BillingGateway
import com.postpole.spoonfile.billing.BillingProduct
import com.postpole.spoonfile.billing.BillingRepository
import com.postpole.spoonfile.billing.OwnedPurchase
import com.postpole.spoonfile.billing.ProductResult
import com.postpole.spoonfile.billing.PurchaseUpdate
import com.postpole.spoonfile.data.AppDatabase
import com.postpole.spoonfile.data.MealPlanRepository
import com.postpole.spoonfile.data.RecipeRepository
import com.postpole.spoonfile.data.ShoppingRepository
import com.postpole.spoonfile.pro.ProPromptSignals
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

internal class IdleGateway : BillingGateway {
    override val updates: Flow<PurchaseUpdate> = emptyFlow()
    override val disconnects: Flow<Unit> = emptyFlow()
    override suspend fun connect() = true
    override suspend fun queryProduct(product: BillingProduct): ProductResult = ProductResult.Unavailable
    override suspend fun queryPurchases(): List<OwnedPurchase>? = emptyList()
    override fun launchPurchase(activity: Activity, product: BillingProduct) = true
    override suspend fun acknowledge(token: String) = true
}

internal class RecordedAnalytics : Analytics {
    val events = CopyOnWriteArrayList<Pair<String, Map<String, Any>>>()
    override fun logEvent(name: String, params: Map<String, Any>) {
        events += name to params
    }
    override fun setUserProperty(name: String, value: String?) = Unit
    override fun logScreenView(screenName: String) = Unit
    override fun recordNonFatal(throwable: Throwable) = Unit
}

internal class CountingSignals : ProPromptSignals {
    val hardGates = AtomicInteger()
    override suspend fun recordEngaged() = Unit
    override suspend fun recordHardGate() {
        hardGates.incrementAndGet()
    }
}

internal fun detailViewModel(
    recipeId: Long,
    db: AppDatabase,
    recipes: RecipeRepository,
    dataStore: DataStore<Preferences>,
    scope: CoroutineScope,
    analytics: Analytics = RecordedAnalytics(),
    signals: ProPromptSignals = CountingSignals(),
): RecipeDetailViewModel {
    val shopping = ShoppingRepository(db.shoppingDao())
    val plan = MealPlanRepository(db.mealPlanDao(), db.recipeDao(), shopping)
    val billing = BillingRepository(IdleGateway(), dataStore, scope, log = {})
    return RecipeDetailViewModel(
        androidx.lifecycle.SavedStateHandle(mapOf("id" to recipeId)),
        recipes,
        shopping,
        plan,
        billing,
        analytics,
        signals,
    )
}
