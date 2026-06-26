package com.pdfmaster.app.analytics

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Default [Analytics] implementation: prints structured events to logcat under the `Analytics`
 * tag. Zero dependencies, so it works in every build today and gives a clean seam for
 * verifying the funnel before (or instead of) wiring a real backend.
 *
 * Swap to `FirebaseAnalyticsTracker` via the Hilt binding once Firebase is configured
 * (see `ANALYTICS.md`).
 */
@Singleton
class LogcatAnalytics @Inject constructor() : Analytics {

    override fun track(event: AnalyticsEvent) {
        val params = event.params.entries
            .filter { it.value != null }
            .joinToString(", ") { "${it.key}=${it.value}" }
        if (params.isEmpty()) {
            Log.d(TAG, "▸ ${event.name}")
        } else {
            Log.d(TAG, "▸ ${event.name} { $params }")
        }
    }

    override fun setUserProperty(key: String, value: String?) {
        Log.d(TAG, "● userProperty $key=$value")
    }

    override fun setUserId(id: String?) {
        Log.d(TAG, "● userId=$id")
    }

    private companion object {
        const val TAG = "Analytics"
    }
}
