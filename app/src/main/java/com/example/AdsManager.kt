package com.example

import android.app.Activity
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

object AdsManager {
    private const val INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-1835719222780575/4742220978"
    private const val APP_OPEN_AD_UNIT_ID = "ca-app-pub-1835719222780575/9119701259"
    private var appOpenShownThisLaunch = false

    fun showAppOpenAd(activity: Activity) {
        if (appOpenShownThisLaunch) return
        appOpenShownThisLaunch = true

        Log.d("AdsManager", "Loading real App Open ad")
        AppOpenAd.load(
            activity,
            APP_OPEN_AD_UNIT_ID,
            AdRequest.Builder().build(),
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.e("AdsManager", "Real App Open ad failed to load: $error")
                }

                override fun onAdLoaded(ad: AppOpenAd) {
                    Log.d("AdsManager", "Real App Open ad loaded; showing")
                    ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                        override fun onAdShowedFullScreenContent() {
                            Log.d("AdsManager", "Real App Open ad shown")
                        }

                        override fun onAdDismissedFullScreenContent() {
                            Log.d("AdsManager", "Real App Open ad dismissed")
                        }

                        override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                            Log.e("AdsManager", "Real App Open ad failed to show: $adError")
                        }
                    }
                    ad.show(activity)
                }
            }
        )
    }

    fun showStartTestAd(activity: Activity, onFinished: () -> Unit) {
        showInterstitial(activity, onFinished)
    }

    fun showInterstitial(activity: Activity, onFinished: () -> Unit) {
        Log.d("AdsManager", "Loading real interstitial ad")
        InterstitialAd.load(
            activity,
            INTERSTITIAL_AD_UNIT_ID,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.e("AdsManager", "Real interstitial failed to load: $error")
                    onFinished()
                }

                override fun onAdLoaded(ad: InterstitialAd) {
                    Log.d("AdsManager", "Real interstitial loaded; showing")
                    ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                        override fun onAdShowedFullScreenContent() {
                            Log.d("AdsManager", "Real interstitial shown")
                        }

                        override fun onAdDismissedFullScreenContent() {
                            Log.d("AdsManager", "Real interstitial dismissed")
                            onFinished()
                        }

                        override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                            Log.e("AdsManager", "Real interstitial failed to show: $adError")
                            onFinished()
                        }
                    }
                    ad.show(activity)
                }
            }
        )
    }
}
