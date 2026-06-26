package com.pdfmaster.app.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.pdfmaster.app.analytics.Analytics
import com.pdfmaster.app.analytics.AnalyticsEvent
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the connection to Google Play Billing and is the only class that talks to
 * [BillingClient]. It:
 *  - connects (with backoff retry) and queries product details,
 *  - launches the purchase flow for a [PremiumPlan],
 *  - receives purchase updates, acknowledges them, and reconciles entitlement,
 *  - restores prior purchases.
 *
 * Entitlement (the boolean "is this user premium?") is published on [isPremium].
 * [PremiumManager] layers an offline-cached view on top of this; gating code should
 * read [PremiumManager], not this class directly.
 *
 * NOTE on security: this performs only local acknowledgement. For revenue at scale you
 * should add server-side receipt verification (Play Developer API) before granting
 * entitlement. The [verifyAndAcknowledge] hook is where that call belongs.
 */
@Singleton
class BillingManager @Inject constructor(
    @ApplicationContext private val context: Context,
    @BillingScope private val scope: CoroutineScope,
    private val analytics: Analytics,
) : PurchasesUpdatedListener, BillingClientStateListener {

    private val billingClient: BillingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .build()
        )
        .build()

    /** True once Play reports the user owns any premium product. */
    private val _isPremium = MutableStateFlow(false)
    val isPremium: StateFlow<Boolean> = _isPremium.asStateFlow()

    /** Localized, formatted prices keyed by plan (e.g. WEEKLY -> "$4.99"). Empty until loaded. */
    private val _formattedPrices = MutableStateFlow<Map<PremiumPlan, String>>(emptyMap())
    val formattedPrices: StateFlow<Map<PremiumPlan, String>> = _formattedPrices.asStateFlow()

    /** One-shot billing events for the UI (errors, purchase success) to react to. */
    private val _events = MutableSharedFlow<BillingEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<BillingEvent> = _events.asSharedFlow()

    private val productDetailsCache = mutableMapOf<String, ProductDetails>()
    private val reconcileMutex = Mutex()

    @Volatile private var isConnecting = false
    @Volatile private var reconnectAttempts = 0

    /** Idempotent: safe to call repeatedly (e.g. on app start and when opening the paywall). */
    fun startConnection() {
        if (billingClient.isReady || isConnecting) return
        isConnecting = true
        runCatching { billingClient.startConnection(this) }
            .onFailure {
                isConnecting = false
                Log.e(TAG, "startConnection failed", it)
            }
    }

    // region BillingClientStateListener
    override fun onBillingSetupFinished(result: BillingResult) {
        isConnecting = false
        if (result.responseCode == BillingClient.BillingResponseCode.OK) {
            reconnectAttempts = 0
            Log.d(TAG, "Billing connected")
            scope.launch {
                queryProductDetails()
                refreshPurchases()
            }
        } else {
            Log.w(TAG, "Billing setup failed: ${result.debugMessage}")
            scheduleReconnect()
        }
    }

    override fun onBillingServiceDisconnected() {
        isConnecting = false
        Log.w(TAG, "Billing disconnected")
        scheduleReconnect()
    }
    // endregion

    private fun scheduleReconnect() {
        if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) return
        reconnectAttempts++
        // Exponential-ish backoff so a transient Play outage doesn't burn all attempts
        // in a tight synchronous loop. reconnectAttempts resets to 0 on a successful setup.
        scope.launch {
            delay(RECONNECT_BASE_DELAY_MS * reconnectAttempts)
            startConnection()
        }
    }

    /** Loads product details for subs + in-app and caches localized prices. */
    private suspend fun queryProductDetails() {
        val subProducts = BillingConstants.SUBSCRIPTION_PRODUCT_IDS.map {
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(it)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        }
        val inAppProducts = BillingConstants.INAPP_PRODUCT_IDS.map {
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(it)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        }

        suspend fun query(products: List<QueryProductDetailsParams.Product>) {
            if (products.isEmpty()) return
            val params = QueryProductDetailsParams.newBuilder()
                .setProductList(products)
                .build()
            val result = billingClient.queryProductDetails(params)
            if (result.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                result.productDetailsList?.forEach { productDetailsCache[it.productId] = it }
            } else {
                Log.w(TAG, "queryProductDetails: ${result.billingResult.debugMessage}")
            }
        }

        query(subProducts)
        query(inAppProducts)
        recomputePrices()
    }

    private fun recomputePrices() {
        val prices = mutableMapOf<PremiumPlan, String>()
        PremiumPlan.entries.forEach { plan ->
            val details = productDetailsCache[plan.productId] ?: return@forEach
            val price = if (plan.isSubscription) {
                offerFor(details, plan.basePlanId)?.let { offer ->
                    // First non-free pricing phase is the recurring charge.
                    offer.pricingPhases.pricingPhaseList
                        .firstOrNull { it.priceAmountMicros > 0L }
                        ?.formattedPrice
                }
            } else {
                details.oneTimePurchaseOfferDetails?.formattedPrice
            }
            if (price != null) prices[plan] = price
        }
        _formattedPrices.value = prices
    }

    /**
     * Picks the subscription offer for [basePlanId], preferring an offer that carries a
     * developer-defined offer id (the free-trial / promo offer) over the bare base plan.
     */
    private fun offerFor(
        details: ProductDetails,
        basePlanId: String?,
    ): ProductDetails.SubscriptionOfferDetails? {
        val candidates = details.subscriptionOfferDetails
            ?.filter { it.basePlanId == basePlanId }
            ?: return null
        return candidates.firstOrNull { !it.offerId.isNullOrEmpty() } ?: candidates.firstOrNull()
    }

    /**
     * Launches the Play purchase sheet for [plan]. Must be called with a real [Activity].
     * Returns false (and emits an error event) if details aren't loaded yet.
     */
    fun launchPurchaseFlow(activity: Activity, plan: PremiumPlan): Boolean {
        val details = productDetailsCache[plan.productId]
        if (details == null) {
            emit(BillingEvent.Error("Products still loading. Please try again."))
            startConnection()
            return false
        }

        val productParamsBuilder = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)

        if (plan.isSubscription) {
            val offerToken = offerFor(details, plan.basePlanId)?.offerToken
            if (offerToken == null) {
                emit(BillingEvent.Error("This plan is unavailable right now."))
                return false
            }
            productParamsBuilder.setOfferToken(offerToken)
        }

        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productParamsBuilder.build()))
            .build()

        val result = billingClient.launchBillingFlow(activity, flowParams)
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            analytics.track(AnalyticsEvent.PurchaseFailed(result.debugMessage))
            emit(BillingEvent.Error("Couldn't start purchase: ${result.debugMessage}"))
            return false
        }
        analytics.track(AnalyticsEvent.PurchaseStarted(plan.name))
        return true
    }

    // region PurchasesUpdatedListener
    override fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                if (purchases != null) scope.launch { handlePurchases(purchases) }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                analytics.track(AnalyticsEvent.PurchaseCancelled)
                emit(BillingEvent.Cancelled)
            }
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                // Out of sync — reconcile from Play's record of truth.
                scope.launch { refreshPurchases() }
            }
            else -> {
                analytics.track(AnalyticsEvent.PurchaseFailed(result.debugMessage))
                emit(BillingEvent.Error(result.debugMessage.ifBlank { "Purchase failed." }))
            }
        }
    }
    // endregion

    /**
     * Re-query the full set of owned purchases from Play and reconcile entitlement.
     * This is ALWAYS authoritative (it's the complete owned set), so it both grants and
     * revokes. Called on every successful Billing connect (cold start / reconnect) and by
     * the "Restore" button. [fromRestore] only controls whether restore UI events fire.
     */
    suspend fun refreshPurchases(fromRestore: Boolean = false) {
        if (!billingClient.isReady) {
            startConnection()
            // Tell the UI we couldn't act, so a restore spinner doesn't hang forever.
            if (fromRestore) emit(BillingEvent.Error("Not connected to Google Play yet. Try again in a moment."))
            return
        }
        val owned = mutableListOf<Purchase>()
        listOf(BillingClient.ProductType.SUBS, BillingClient.ProductType.INAPP).forEach { type ->
            val params = QueryPurchasesParams.newBuilder().setProductType(type).build()
            val result = billingClient.queryPurchasesAsync(params)
            if (result.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                owned += result.purchasesList
            }
        }
        handlePurchases(owned, authoritative = true, fromRestore = fromRestore)
    }

    /**
     * @param authoritative true when [purchases] is the COMPLETE owned set (from
     *   [refreshPurchases]); only then may entitlement be revoked. Live `onPurchasesUpdated`
     *   callbacks are partial, so they only ever flip entitlement on.
     */
    private suspend fun handlePurchases(
        purchases: List<Purchase>,
        authoritative: Boolean = false,
        fromRestore: Boolean = false,
    ) = reconcileMutex.withLock {
        var grantsPremium = false
        for (purchase in purchases) {
            if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) continue
            if (purchase.products.none { it in BillingConstants.PREMIUM_PRODUCT_IDS }) continue

            grantsPremium = true
            if (!purchase.isAcknowledged) verifyAndAcknowledge(purchase)
        }

        val wasPremium = _isPremium.value
        if (grantsPremium) {
            _isPremium.value = true
            if (!wasPremium) {
                emit(BillingEvent.PremiumGranted)
                if (!fromRestore) analytics.track(AnalyticsEvent.PurchaseSucceeded)
            }
        } else if (authoritative) {
            // Full owned set has no entitlement -> revoke (refund, expiry, chargeback).
            // PremiumManager mirrors this into the offline cache, so it also clears there.
            _isPremium.value = false
        }

        if (fromRestore) {
            analytics.track(AnalyticsEvent.PurchaseRestored(restoredPremium = grantsPremium))
            emit(BillingEvent.RestoreFinished(restoredPremium = grantsPremium))
        }
    }

    /**
     * Acknowledges a purchase, retrying with backoff. Play auto-refunds any purchase not
     * acknowledged within 3 days, so a silently-dropped failure directly costs revenue.
     *
     * Hook for server-side verification: replace the acknowledge call with a backend call
     * that validates [purchase.purchaseToken] against the Play Developer API once available.
     */
    private suspend fun verifyAndAcknowledge(purchase: Purchase) {
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        repeat(ACK_MAX_ATTEMPTS) { attempt ->
            val result = billingClient.acknowledgePurchase(params)
            if (result.responseCode == BillingClient.BillingResponseCode.OK) return
            Log.w(TAG, "Acknowledge failed (attempt ${attempt + 1}): ${result.debugMessage}")
            delay(ACK_RETRY_DELAY_MS * (attempt + 1))
        }
        Log.e(TAG, "Acknowledge ultimately failed for ${purchase.products}; will retry on next reconcile")
    }

    private fun emit(event: BillingEvent) {
        _events.tryEmit(event)
    }

    companion object {
        private const val TAG = "BillingManager"
        private const val MAX_RECONNECT_ATTEMPTS = 3
        private const val RECONNECT_BASE_DELAY_MS = 2_000L
        private const val ACK_MAX_ATTEMPTS = 3
        private const val ACK_RETRY_DELAY_MS = 2_000L
    }
}

/** One-shot signals from the billing layer for the UI to surface. */
sealed interface BillingEvent {
    data object PremiumGranted : BillingEvent
    data object Cancelled : BillingEvent
    data class RestoreFinished(val restoredPremium: Boolean) : BillingEvent
    data class Error(val message: String) : BillingEvent
}
