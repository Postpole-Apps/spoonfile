package com.postpole.spoonfile.pro

import androidx.lifecycle.ViewModel
import com.postpole.spoonfile.billing.BillingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/** Thin UI handle on [ProPromptController] and the Play price, for the Recipes list and `AppNavHost`. */
@HiltViewModel
class ProPromptViewModel @Inject constructor(
    val controller: ProPromptController,
    billing: BillingRepository,
) : ViewModel() {
    val active: StateFlow<ProPromptTrigger?> = controller.active

    /** Localized Play price, null until loaded (the prompt then shows its no-price copy). */
    val price: StateFlow<String?> = billing.price

    fun onDismissed(method: String) = controller.onDismissed(method)

    fun onCtaTap(): ProPromptTrigger? = controller.onCtaTap()
}
