package com.example

import android.app.Activity
import android.util.Log
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

object AdsManager {
    private const val START_TEST_AD_UNIT_ID = "ca-app-pub-3940256099942544/1033173712"
    private const val APP_OPEN_AD_UNIT_ID = "ca-app-pub-3940256099942544/9257395921"
    private const val BANNER_AD_UNIT_ID = "ca-app-pub-1835719222780575/4072312143"
    private var appOpenShownThisLaunch = false

    @Composable
    fun BannerAd(modifier: Modifier = Modifier) {
        AndroidView(
            modifier = modifier,
            factory = { context ->
                AdView(context).apply {
                    setAdSize(AdSize.BANNER)
                    adUnitId = BANNER_AD_UNIT_ID
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                    loadAd(AdRequest.Builder().build())
                }
            }
        )
    }

    fun showAppOpenAd(activity: Activity) {
        if (appOpenShownThisLaunch) return
        appOpenShownThisLaunch = true
        Log.d("AdsManager", "Loading App Open test ad")
        AppOpenAd.load(activity, APP_OPEN_AD_UNIT_ID, AdRequest.Builder().build(),
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.e("AdsManager", "App Open ad failed to load: $error")
                }
                override fun onAdLoaded(ad: AppOpenAd) {
                    Log.d("AdsManager", "App Open ad loaded; showing")
                    ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                        override fun onAdShowedFullScreenContent() {
                            Log.d("AdsManager", "App Open ad shown")
                        }
                        override fun onAdDismissedFullScreenContent() {
                            Log.d("AdsManager", "App Open ad dismissed")
                        }
                        override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                            Log.e("AdsManager", "App Open ad failed to show: $adError")
                        }
                    }
                    ad.show(activity)
                }
            })
    }

    fun showTwoStartTestAds(activity: Activity, onFinished: () -> Unit) {
        Log.d("AdsManager", "Start Test: loading interstitial #1")
        showInterstitial(activity, 1, onFinished)
    }

    private fun showInterstitial(activity: Activity, number: Int, onFinished: () -> Unit) {
        InterstitialAd.load(activity, START_TEST_AD_UNIT_ID, AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.e("AdsManager", "Interstitial #\$number failed to load: \$error")
                    if (number < 2) showInterstitial(activity, number + 1, onFinished) else onFinished()
                }
                override fun onAdLoaded(ad: InterstitialAd) {
                    Log.d("AdsManager", "Interstitial #\$number loaded; showing")
                    ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                        override fun onAdShowedFullScreenContent() {
                            Log.d("AdsManager", "Interstitial #\$number shown")
                        }
                        override fun onAdDismissedFullScreenContent() {
                            Log.d("AdsManager", "Interstitial #\$number dismissed")
                            if (number < 2) showInterstitial(activity, number + 1, onFinished) else onFinished()
                        }
                        override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                            Log.e("AdsManager", "Interstitial #\$number failed to show: \$adError")
                            if (number < 2) showInterstitial(activity, number + 1, onFinished) else onFinished()
                        }
                    }
                    ad.show(activity)
                }
            })
    }
}
