package com.example

import android.app.Activity
import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

private const val TAG = "AdMobInterstitial"
private const val AD_INTERSTITIAL = "ca-app-pub-1835719222780575/9783628813"

@Composable
fun SubscriptionGate(
    content: @Composable (Boolean, Boolean, () -> Unit, () -> Unit) -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity

    var interstitialAd by remember { mutableStateOf<InterstitialAd?>(null) }

    fun loadInterstitial() {
        if (activity == null || interstitialAd != null) return

        InterstitialAd.load(
            activity,
            AD_INTERSTITIAL,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                    Log.d(TAG, "Interstitial loaded")
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    interstitialAd = null
                    Log.e(TAG, "Interstitial failed: code=" + error.code + ", message=" + error.message)
                }
            }
        )
    }

    fun showInterstitial() {
        if (activity == null) return

        val ad = interstitialAd
        if (ad == null) {
            loadInterstitial()
            return
        }

        interstitialAd = null
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                Log.d(TAG, "Interstitial shown")
            }

            override fun onAdDismissedFullScreenContent() {
                Log.d(TAG, "Interstitial dismissed")
                loadInterstitial()
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                Log.e(TAG, "Interstitial failed to show: " + adError.message)
                loadInterstitial()
            }
        }
        ad.show(activity)
    }

    LaunchedEffect(Unit) {
        loadInterstitial()
    }

    Box(Modifier.fillMaxSize()) {
        content(
            true,
            true,
            { showInterstitial() },
            { showInterstitial() }
        )
    }
}
