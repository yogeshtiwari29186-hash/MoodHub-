package com.example

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.appopen.AppOpenAd

class AppOpenAdManager {
    companion object {
        private const val TAG = "AdMobAppOpen"
        private const val AD_UNIT_ID = "ca-app-pub-1835719222780575/5437275416"
    }

    private var appOpenAd: AppOpenAd? = null
    private var isLoading = false
    private var isShowing = false
    private var showWhenLoaded = false

    fun loadAd(context: Context, showWhenLoaded: Boolean = false) {
        if (isLoading || appOpenAd != null) return

        this.showWhenLoaded = showWhenLoaded
        isLoading = true

        AppOpenAd.load(
            context,
            AD_UNIT_ID,
            AdRequest.Builder().build(),
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: AppOpenAd) {
                    isLoading = false
                    appOpenAd = ad
                    Log.d(TAG, "App open ad loaded")

                    if (this@AppOpenAdManager.showWhenLoaded) {
                        this@AppOpenAdManager.showWhenLoaded = false
                        (context as? Activity)?.let { showAdIfAvailable(it) }
                    }
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    isLoading = false
                    this@AppOpenAdManager.showWhenLoaded = false
                    Log.e(TAG, "App open ad failed: code=" + error.code + ", message=" + error.message)
                }
            }
        )
    }

    fun showAdIfAvailable(activity: Activity) {
        val ad = appOpenAd
        if (ad == null || isShowing) {
            loadAd(activity, showWhenLoaded = false)
            return
        }

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                isShowing = true
                Log.d(TAG, "App open ad shown")
            }

            override fun onAdDismissedFullScreenContent() {
                isShowing = false
                appOpenAd = null
                Log.d(TAG, "App open ad dismissed")
                loadAd(activity)
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                isShowing = false
                appOpenAd = null
                Log.e(TAG, "App open ad failed to show: " + adError.message)
                loadAd(activity)
            }
        }

        appOpenAd = null
        ad.show(activity)
    }
}
