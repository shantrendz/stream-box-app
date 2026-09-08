package com.example.tuner.cast

import android.content.Context
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.framework.CastOptions
import com.google.android.gms.cast.framework.OptionsProvider
import com.google.android.gms.cast.framework.SessionProvider

/**
 * Registered via the `OPTIONS_PROVIDER_CLASS` meta-data in AndroidManifest.xml — required by
 * the Cast SDK before `CastContext.getSharedInstance()` can be used anywhere in the app.
 * Uses Google's default media receiver so any Chromecast-type device can play the plain HLS
 * URLs this app already streams, with no custom receiver app to host.
 */
class TunerCastOptionsProvider : OptionsProvider {
    override fun getCastOptions(context: Context): CastOptions =
        CastOptions.Builder()
            .setReceiverApplicationId(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID)
            .build()

    override fun getAdditionalSessionProviders(context: Context): List<SessionProvider>? = null
}
