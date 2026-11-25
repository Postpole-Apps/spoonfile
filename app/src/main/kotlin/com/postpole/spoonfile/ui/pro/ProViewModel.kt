package com.postpole.spoonfile.ui.pro

import android.app.Activity
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.postpole.spoonfile.R
import com.postpole.spoonfile.billing.BillingRepository
import com.postpole.spoonfile.billing.BillingMessage
import com.postpole.spoonfile.billing.RestoreResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** String shown in the snackbar after Restore purchases finishes. */
fun RestoreResult.messageRes(): Int = when (this) {
    RestoreResult.RESTORED -> R.string.pro_restore_done
    RestoreResult.NOTHING_TO_RESTORE -> R.string.pro_restore_nothing
    RestoreResult.FAILED -> R.string.pro_restore_failed
}

@HiltViewModel
class ProViewModel @Inject constructor(
    private val billing: BillingRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    /** Why this screen opened (`auto_<trigger>` from an automatic prompt); null for manual / hard-gate entries. */
    private val trigger: String? = savedStateHandle.get<String>("trigger")?.takeIf { it.isNotEmpty() }

    val isPro: StateFlow<Boolean> = billing.isPro
    val removeAdsOwned: StateFlow<Boolean> = billing.removeAdsOwned
    val price: StateFlow<String?> = billing.price
    val pending: StateFlow<Boolean> = billing.pending
    val productUnavailable: StateFlow<Boolean> = billing.productUnavailable
    val messages: Flow<BillingMessage> = billing.messages

    private val _restoreResults = Channel<RestoreResult>(Channel.BUFFERED)
    val restoreResults: Flow<RestoreResult> = _restoreResults.receiveAsFlow()

    fun buy(activity: Activity) {
        billing.launchPurchase(activity, trigger = trigger)
    }

    fun restore() {
        viewModelScope.launch { _restoreResults.send(billing.restore()) }
    }
}
