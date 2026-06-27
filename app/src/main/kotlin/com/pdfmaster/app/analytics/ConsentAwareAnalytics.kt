package com.pdfmaster.app.analytics

import com.pdfmaster.app.data.local.preferences.UserPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The [Analytics] the whole app injects. It wraps the real tracker (the [RawAnalytics]
 * delegate) and forwards events ONLY while the user has analytics enabled.
 *
 * Opt-out is honored centrally here, so no individual call site has to check consent.
 * Required for Google Play's User Data policy, GDPR/ePrivacy consent, and Firebase's
 * requirement to honor collection toggles.
 *
 * GDPR opt-in: consent defaults to OFF ([UserPreferences.analyticsEnabled] defaults to false)
 * until the user explicitly accepts the consent prompt; a Settings switch flips it. When off we
 * also tell the delegate to stop collecting and clear the id.
 */
@Singleton
class ConsentAwareAnalytics @Inject constructor(
    @RawAnalytics private val delegate: Analytics,
    userPreferences: UserPreferences,
) : Analytics {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Cheap in-memory mirror of the persisted consent flag, kept in sync below. Starts FALSE
     * (deny by default): the persisted value loads asynchronously, and the very first event
     * (`app_open`, fired synchronously at process start) must not be forwarded before consent
     * is known.
     */
    @Volatile
    private var enabled: Boolean = false

    init {
        scope.launch {
            userPreferences.analyticsEnabled().collect { consent ->
                enabled = consent
                delegate.setCollectionEnabled(consent)
                if (!consent) delegate.setUserId(null)
            }
        }
    }

    override fun track(event: AnalyticsEvent) {
        if (enabled) delegate.track(event)
    }

    override fun setUserProperty(key: String, value: String?) {
        if (enabled) delegate.setUserProperty(key, value)
    }

    override fun setUserId(id: String?) {
        if (enabled) delegate.setUserId(id)
    }

    override fun setCollectionEnabled(enabled: Boolean) {
        delegate.setCollectionEnabled(enabled)
    }
}
