package com.postpole.spoonfile.billing

import com.google.android.ump.ConsentInformation.PrivacyOptionsRequirementStatus
import com.postpole.spoonfile.R
import com.postpole.spoonfile.ads.isPrivacyOptionsRequired
import com.postpole.spoonfile.ui.pro.messageRes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RestoreResultTest {
    @Test fun proFound_isRestored() =
        assertEquals(RestoreResult.RESTORED, RestoreResult.from(refreshed = true, proOwned = true, removeAdsOwned = false))

    @Test fun removeAdsFound_isRestored() =
        assertEquals(RestoreResult.RESTORED, RestoreResult.from(refreshed = true, proOwned = false, removeAdsOwned = true))

    @Test fun nothingOwned_isNothingToRestore() =
        assertEquals(
            RestoreResult.NOTHING_TO_RESTORE,
            RestoreResult.from(refreshed = true, proOwned = false, removeAdsOwned = false),
        )

    @Test fun failedQuery_isFailedEvenIfOwnedFromBefore() =
        assertEquals(RestoreResult.FAILED, RestoreResult.from(refreshed = false, proOwned = true, removeAdsOwned = true))

    @Test fun eachResultHasItsOwnMessage() {
        assertEquals(R.string.pro_restore_done, RestoreResult.RESTORED.messageRes())
        assertEquals(R.string.pro_restore_nothing, RestoreResult.NOTHING_TO_RESTORE.messageRes())
        assertEquals(R.string.pro_restore_failed, RestoreResult.FAILED.messageRes())
    }

    @Test fun privacyRow_onlyWhenRequired() {
        assertTrue(isPrivacyOptionsRequired(PrivacyOptionsRequirementStatus.REQUIRED))
        assertFalse(isPrivacyOptionsRequired(PrivacyOptionsRequirementStatus.NOT_REQUIRED))
        assertFalse(isPrivacyOptionsRequired(PrivacyOptionsRequirementStatus.UNKNOWN))
    }
}
