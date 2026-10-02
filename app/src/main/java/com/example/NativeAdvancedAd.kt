package com.example

import android.graphics.Color
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.google.android.gms.ads.nativead.NativeAdView

private const val NATIVE_AD_UNIT_ID = "ca-app-pub-2881312687834117/4378726065"

@Composable
fun NativeAdvancedAd(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var nativeAd by remember { mutableStateOf<NativeAd?>(null) }

    LaunchedEffect(Unit) {
        val loader = AdLoader.Builder(context, NATIVE_AD_UNIT_ID)
            .forNativeAd { ad ->
                nativeAd?.destroy()
                nativeAd = ad
            }
            .withNativeAdOptions(
                NativeAdOptions.Builder()
                    .setMediaAspectRatio(NativeAdOptions.NATIVE_MEDIA_ASPECT_RATIO_ANY)
                    .build()
            )
            .build()

        loader.loadAd(AdRequest.Builder().build())
    }

    DisposableEffect(Unit) {
        onDispose {
            nativeAd?.destroy()
            nativeAd = null
        }
    }

    nativeAd?.let { ad ->
        AndroidView(
            modifier = modifier
                .fillMaxWidth()
                .height(130.dp),
            factory = { ctx -> createNativeAdView(ctx, ad) },
            update = { view -> bindNativeAdView(view, ad) }
        )
    }
}

private fun createNativeAdView(
    context: android.content.Context,
    ad: NativeAd
): NativeAdView {
    val adView = NativeAdView(context)

    val container = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(12, 8, 12, 8)
        setBackgroundColor(Color.TRANSPARENT)
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
    }

    val mediaView = MediaView(context).apply {
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            70
        )
    }

    val headline = TextView(context).apply {
        textSize = 15f
        setTextColor(Color.WHITE)
        maxLines = 1
    }

    val body = TextView(context).apply {
        textSize = 12f
        setTextColor(Color.LTGRAY)
        maxLines = 1
    }

    container.addView(mediaView)
    container.addView(headline)
    container.addView(body)
    adView.addView(container)

    adView.headlineView = headline
    adView.bodyView = body
    adView.mediaView = mediaView
    bindNativeAdView(adView, ad)
    return adView
}

private fun bindNativeAdView(adView: NativeAdView, ad: NativeAd) {
    (adView.headlineView as? TextView)?.text = ad.headline ?: ""
    (adView.bodyView as? TextView)?.text = ad.body ?: ""
    adView.mediaView?.mediaContent = ad.mediaContent
    adView.setNativeAd(ad)
}
