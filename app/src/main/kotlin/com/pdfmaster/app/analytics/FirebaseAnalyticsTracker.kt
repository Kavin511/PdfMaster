package com.pdfmaster.app.analytics

import android.content.Context
import android.os.Bundle
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Real [Analytics] backend: forwards typed events to Firebase Analytics (and toggles
 * Crashlytics collection alongside).
 *
 * Firebase only works once a default [FirebaseApp] is initialized, which requires
 * `app/google-services.json`. Until that file is added, [FirebaseApp.getApps] is empty; rather
 * than crash (the tracker is a Singleton touched early at startup) we fall back to [fallback]
 * (Logcat) so dev builds without the config still run and still show the funnel. Drop in
 * `google-services.json` and Firebase activates automatically — no code change.
 *
 * Wrapped by [ConsentAwareAnalytics], so opt-out is already honored upstream; we still relay
 * [setCollectionEnabled] to Firebase as a second line of defense.
 */
@Singleton
class FirebaseAnalyticsTracker @Inject constructor(
    @ApplicationContext context: Context,
    private val fallback: LogcatAnalytics,
) : Analytics {

    private val firebase: FirebaseAnalytics? =
        if (FirebaseApp.getApps(context).isNotEmpty()) FirebaseAnalytics.getInstance(context) else null

    init {
        if (firebase == null) {
            Log.w(
                TAG,
                "FirebaseApp not initialized (missing app/google-services.json) — " +
                    "analytics will log to Logcat only until it is added.",
            )
        }
    }

    override fun track(event: AnalyticsEvent) {
        val fa = firebase ?: return fallback.track(event)
        val bundle = Bundle().apply {
            event.params.forEach { (key, value) ->
                when (value) {
                    null -> {}
                    is Int -> putLong(key, value.toLong())
                    is Long -> putLong(key, value)
                    is Double -> putDouble(key, value)
                    is Float -> putDouble(key, value.toDouble())
                    is Boolean -> putString(key, value.toString())
                    else -> putString(key, value.toString())
                }
            }
        }
        fa.logEvent(event.name, bundle)
    }

    override fun setUserProperty(key: String, value: String?) {
        val fa = firebase ?: return fallback.setUserProperty(key, value)
        fa.setUserProperty(key, value)
    }

    override fun setUserId(id: String?) {
        val fa = firebase ?: return fallback.setUserId(id)
        fa.setUserId(id)
    }

    override fun setCollectionEnabled(enabled: Boolean) {
        firebase?.setAnalyticsCollectionEnabled(enabled)
    }

    private companion object {
        const val TAG = "FirebaseAnalytics"
    }
}
