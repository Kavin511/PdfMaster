package com.pdfmaster.app.billing

import com.pdfmaster.app.analytics.Analytics
import com.pdfmaster.app.analytics.UserProp
import com.pdfmaster.app.data.local.preferences.UserPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The single source of truth for "is the user premium?" that the rest of the app reads.
 *
 * It combines two signals:
 *  - [BillingManager.isPremium] — Play's live answer (authoritative, needs connectivity).
 *  - [UserPreferences.isPremium] — a local cache so entitlement survives offline / cold start
 *    before Billing finishes connecting.
 *
 * Premium = (live OR cached). The cache is written whenever the live signal resolves, so a
 * user who buys once stays unlocked offline, and a refund/expiry detected on the next
 * online reconciliation flips the cache back off.
 *
 * Gating code should inject THIS class and read [isPremium] (or call [isPremiumNow]).
 */
@Singleton
class PremiumManager @Inject constructor(
    private val billingManager: BillingManager,
    private val userPreferences: UserPreferences,
    private val analytics: Analytics,
    @BillingScope private val scope: CoroutineScope,
) {

    val isPremium: StateFlow<Boolean> =
        combine(
            billingManager.isPremium,
            userPreferences.isPremium(),
        ) { live, cached -> live || cached }
            .stateIn(scope, SharingStarted.Eagerly, false)

    init {
        // Kick off the Play connection as soon as anything touches entitlement.
        billingManager.startConnection()

        // Persist the live billing result into the offline cache whenever it changes.
        scope.launch {
            billingManager.isPremium.collect { live ->
                if (userPreferences.isPremium().first() != live) {
                    userPreferences.setPremiumStatus(live)
                }
            }
        }

        // Keep the analytics user property in sync for segmentation (free vs premium).
        scope.launch {
            isPremium.collect { premium ->
                analytics.setUserProperty(UserProp.IS_PREMIUM, premium.toString())
            }
        }
    }

    /** Synchronous read for non-Flow call sites (use the Flow where possible). */
    fun isPremiumNow(): Boolean = isPremium.value
}
