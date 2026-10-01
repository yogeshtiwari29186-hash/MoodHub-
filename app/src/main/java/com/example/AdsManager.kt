package com.example

import android.app.Activity
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.appopen.AppOpenAd

object AdsManager {
    private const val START_TEST_AD_UNIT_ID = "ca-app-pub-1835719222780575/3065428792"
    private const val APP_OPEN_AD_UNIT_ID = "ca-app-pub-1835719222780575/9119701259"
    private var appOpenShownThisLaunch = false

    fun showAppOpenAd(activity: Activity) {
        if (appOpenShownThisLaunch) return
        appOpenShownThisLaunch = true

        AppOpenAd.load(
            activity,
            APP_OPEN_AD_UNIT_ID,
            AdRequest.Builder().build(),
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdFailedToLoad(error: LoadAdError) {
                    // Continue opening the app normally when no ad is available.
                }

                override fun onAdLoaded(ad: AppOpenAd) {
                    ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                        override fun onAdFailedToShowFullScreenContent(adError: AdError) = Unit
                    }
                    ad.show(activity)
                }
            }
        )
    }

    fun showTwoStartTestAds(activity: Activity, onFinished: () -> Unit) {
        showInterstitial(activity, 1, onFinished)
    }

    private fun showInterstitial(activity: Activity, number: Int, onFinished: () -> Unit) {
        InterstitialAd.load(
            activity,
            START_TEST_AD_UNIT_ID,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdFailedToLoad(error: LoadAdError) {
                    onFinished()
                }

                override fun onAdLoaded(ad: InterstitialAd) {
                    ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                        override fun onAdDismissedFullScreenContent() {
                            if (number < 2) showInterstitial(activity, number + 1, onFinished)
                            else onFinished()
                        }

                        override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                            if (number < 2) showInterstitial(activity, number + 1, onFinished)
                            else onFinished()
                        }
                    }
                    ad.show(activity)
                }
            }
        )
    }
}
