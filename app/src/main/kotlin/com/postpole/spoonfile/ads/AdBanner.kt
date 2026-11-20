package com.postpole.spoonfile.ads

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class AdsViewModel @Inject constructor(val controller: AdsController) : ViewModel() {
    val bannersAllowed: StateFlow<Boolean> = controller.bannersAllowed
}

@Composable
fun rememberAdsController(viewModel: AdsViewModel = hiltViewModel()): AdsController = viewModel.controller

/** True while a banner may be on screen (kill switch, UMP, format flag and entitlement all allow it). */
@Composable
fun rememberBannersAllowed(viewModel: AdsViewModel = hiltViewModel()): Boolean {
    val allowed by viewModel.bannersAllowed.collectAsStateWithLifecycle()
    return allowed
}

/**
 * A bottom anchored adaptive banner for [unitId]. Draws nothing unless [rememberBannersAllowed]; leaves composition
 * (and destroys its [AdView]) the moment that turns false or the load fails. The caller places it in a bottom bar
 * slot (never over content) and supplies any navigation-bar padding via [contentModifier].
 *
 * The [AdView] is paused / resumed with the host lifecycle and destroyed on dispose, so nothing leaks on rotation,
 * backgrounding or leaving the screen.
 */
@Composable
fun AdBanner(unitId: String, modifier: Modifier = Modifier, contentModifier: Modifier = Modifier) {
    if (!rememberBannersAllowed()) return
    val context = LocalContext.current
    val widthDp = LocalConfiguration.current.screenWidthDp
    val adSize = remember(widthDp) { AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, widthDp) }
    var failed by remember(unitId) { mutableStateOf(false) }
    if (failed) return

    val adView = remember(unitId, adSize) {
        AdView(context).apply {
            setAdSize(adSize)
            adUnitId = unitId
            adListener = object : AdListener() {
                override fun onAdFailedToLoad(error: LoadAdError) {
                    failed = true
                }
            }
            loadAd(AdRequest.Builder().build())
        }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(adView, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> adView.pause()
                Lifecycle.Event.ON_RESUME -> adView.resume()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            adView.destroy()
        }
    }

    Surface(color = MaterialTheme.colorScheme.surface, modifier = modifier.fillMaxWidth()) {
        Box(contentModifier.fillMaxWidth()) {
            AndroidView(factory = { adView }, modifier = Modifier.fillMaxWidth().height(adSize.height.dp))
        }
    }
}
