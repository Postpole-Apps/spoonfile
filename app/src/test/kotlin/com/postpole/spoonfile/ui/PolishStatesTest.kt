package com.postpole.spoonfile.ui

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.postpole.spoonfile.R
import com.postpole.spoonfile.ui.pro.ProHeadline
import com.postpole.spoonfile.ui.settings.RemoveAdsRow
import com.postpole.spoonfile.ui.theme.SpoonfileTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** UI states for the Pro headline when owned and the Remove ads row when products are unavailable. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PolishStatesTest {
    @get:Rule val rule = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private fun str(id: Int) = context.getString(id)

    @Test fun proHeadline_notOwned_showsUnlockWording() {
        rule.setContent { SpoonfileTheme { ProHeadline(isPro = false) } }
        rule.onNodeWithText(str(R.string.pro_features_title)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.pro_pay_once)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.pro_owned_headline)).assertDoesNotExist()
    }

    @Test fun proHeadline_owned_showsOwnedHeadlineAndHidesUnlockWording() {
        rule.setContent { SpoonfileTheme { ProHeadline(isPro = true) } }
        rule.onNodeWithText(str(R.string.pro_owned_headline)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.pro_features_title)).assertDoesNotExist()
        rule.onNodeWithText(str(R.string.pro_pay_once)).assertDoesNotExist()
    }

    private class Taps {
        var buy = 0
        var retry = 0
        var unavailable = 0
    }

    private fun setRow(price: String?, unavailable: Boolean, pending: Boolean = false): Taps {
        val taps = Taps()
        rule.setContent {
            SpoonfileTheme {
                RemoveAdsRow(
                    price = price,
                    unavailable = unavailable,
                    pending = pending,
                    onBuy = { taps.buy++ },
                    onRetry = { taps.retry++ },
                    onUnavailableTap = { taps.unavailable++ },
                )
            }
        }
        return taps
    }

    @Test fun removeAdsRow_unavailable_showsHintAndGivesFeedbackWhileRetrying() {
        val taps = setRow(price = null, unavailable = true)
        rule.onNodeWithText(str(R.string.settings_remove_ads_unavailable)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.settings_remove_ads_no_price)).performClick()
        assertEquals(1, taps.unavailable)
        assertEquals(1, taps.retry)
        assertEquals(0, taps.buy)
    }

    @Test fun removeAdsRow_stillLoading_retriesWithoutUnavailableFeedback() {
        val taps = setRow(price = null, unavailable = false)
        rule.onNodeWithText(str(R.string.settings_remove_ads_hint)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.settings_remove_ads_no_price)).performClick()
        assertEquals(0, taps.unavailable)
        assertEquals(1, taps.retry)
    }

    @Test fun removeAdsRow_withPrice_buys() {
        val taps = setRow(price = "₹99", unavailable = false)
        rule.onNodeWithText(context.getString(R.string.settings_remove_ads, "₹99")).performClick()
        assertEquals(1, taps.buy)
        assertEquals(0, taps.retry)
        assertEquals(0, taps.unavailable)
    }

    @Test fun newStrings_haveNoBannedWording() {
        listOf(R.string.pro_owned_headline, R.string.settings_remove_ads_unavailable_snackbar).forEach {
            val s = str(it).lowercase()
            assert("offline" !in s && "ad-free" !in s)
        }
    }
}
