package com.postpole.spoonfile.billing

import kotlinx.coroutines.flow.StateFlow

/** Read-only view of the Pro entitlement, so view models don't depend on the whole billing stack. */
interface ProStatus {
    val isPro: StateFlow<Boolean>
}
