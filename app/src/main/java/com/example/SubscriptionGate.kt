package com.example

import android.app.Activity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

private const val AD_APP_START = "ca-app-pub-1835719222780575/8269654017"
private const val AD_START_TEST = "ca-app-pub-1835719222780575/4072312143"
private const val AD_NETWORK = "ca-app-pub-1835719222780575/3065428792"
private const val AD_DEVICE_SETTINGS = "ca-app-pub-1835719222780575/4742220978"

@Composable
fun SubscriptionGate(content: @Composable (Boolean, Boolean, () -> Unit) -> Unit) {
    val context = LocalContext.current
    val activity = context as? Activity
    var currentAd by remember { mutableStateOf<InterstitialAd?>(null) }
    var loadedAdUnit by remember { mutableStateOf<String?>(null) }
    
    fun loadAd(unitId: String) {
        if (activity == null) return
        InterstitialAd.load(
            activity,
            unitId,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    currentAd = ad
                    loadedAdUnit = unitId
                }
                override fun onAdFailedToLoad(error: LoadAdError) {
                    currentAd = null
                }
            }
        )
    }

    fun showAd(unitId: String) {
        if (activity == null) return
        val ad = currentAd
        if (ad != null && loadedAdUnit == unitId) {
            currentAd = null
            loadedAdUnit = null
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    loadAd(unitId)
                }
                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    loadAd(unitId)
                }
            }
            ad.show(activity)
        } else {
            currentAd = null
            loadedAdUnit = null
            loadAd(unitId)
        }
    }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(1000)
        loadAd(AD_APP_START)
        kotlinx.coroutines.delay(1800)
        showAd(AD_APP_START)
    }

    Box(Modifier.fillMaxSize()) {
        content(true, true, { showAd(AD_NETWORK) }, { showAd(AD_START_TEST) })
        BannerAd(Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun BannerAd(modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier.fillMaxWidth().wrapContentHeight(),
        factory = { context ->
            AdView(context).apply {
                setAdSize(AdSize.BANNER)
                adUnitId = AD_DEVICE_SETTINGS
                loadAd(AdRequest.Builder().build())
            }
        }
    )
}
