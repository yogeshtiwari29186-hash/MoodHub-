package com.example

import android.app.Activity
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

object AdsManager {
    private const val START_TEST_AD_UNIT_ID = "ca-app-pub-1835719222780575/3065428792"

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
