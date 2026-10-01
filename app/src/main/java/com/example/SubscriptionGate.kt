package com.example

import android.app.Activity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.FullScreenContentCallback

// Existing AdMob interstitial unit.
// The other supplied units are Rewarded / Rewarded Interstitial and are not
// interchangeable with InterstitialAd.
private const val AD_INTERSTITIAL = "ca-app-pub-1835719222780575/4072312143"

@Composable
fun SubscriptionGate(
    content: @Composable (Boolean, Boolean, () -> Unit, () -> Unit) -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity

    var interstitialAd by remember { mutableStateOf<InterstitialAd?>(null) }
    var showAfterLoad by remember { mutableStateOf(false) }

    fun loadInterstitial(showWhenLoaded: Boolean = false) {
        if (activity == null) return

        showAfterLoad = showWhenLoaded
        InterstitialAd.load(
            activity,
            AD_INTERSTITIAL,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad

                    if (showAfterLoad) {
                        showAfterLoad = false
                        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                            override fun onAdDismissedFullScreenContent() {
                                interstitialAd = null
                                loadInterstitial(false)
                            }

                            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                                interstitialAd = null
                                loadInterstitial(false)
                            }
                        }
                        interstitialAd = null
                        ad.show(activity)
                    }
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    interstitialAd = null
                    showAfterLoad = false
                }
            }
        )
    }

    fun showInterstitial() {
        if (activity == null) return

        val ad = interstitialAd
        if (ad == null) {
            loadInterstitial(false)
            return
        }

        interstitialAd = null
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                loadInterstitial(false)
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                loadInterstitial(false)
            }
        }
        ad.show(activity)
    }

    // Load immediately and show the interstitial as soon as it is available.
    LaunchedEffect(Unit) {
        loadInterstitial(showWhenLoaded = true)
    }

    Box(Modifier.fillMaxSize()) {
        // Subscription/Firebase gating is removed; ads only.
        content(
            true,
            true,
            { showInterstitial() },
            { showInterstitial() }
        )
    }
}
