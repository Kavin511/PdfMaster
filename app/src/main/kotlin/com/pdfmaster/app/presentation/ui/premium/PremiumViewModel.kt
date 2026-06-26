package com.pdfmaster.app.presentation.ui.premium

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pdfmaster.app.analytics.Analytics
import com.pdfmaster.app.analytics.AnalyticsEvent
import com.pdfmaster.app.billing.BillingEvent
import com.pdfmaster.app.billing.BillingManager
import com.pdfmaster.app.billing.PremiumManager
import com.pdfmaster.app.billing.PremiumPlan
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PremiumUiState(
    val isLoading: Boolean = false,
    val isPremium: Boolean = false,
    /** Localized prices from Play, keyed by [PlanType] (e.g. "$4.99"). */
    val prices: Map<PlanType, String> = emptyMap(),
    val purchaseSuccess: Boolean = false,
    val message: String? = null,
    val error: String? = null,
)

@HiltViewModel
class PremiumViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val billingManager: BillingManager,
    private val premiumManager: PremiumManager,
    private val analytics: Analytics,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PremiumUiState())
    val uiState: StateFlow<PremiumUiState> = _uiState.asStateFlow()

    init {
        // Make sure Play is connected when the paywall opens.
        billingManager.startConnection()
        observeEntitlement()
        observePrices()
        observeBillingEvents()
    }

    /** Report the paywall impression, tagged with the screen/source that opened it. */
    fun trackPaywallViewed(source: String) {
        analytics.track(AnalyticsEvent.PaywallViewed(source = source))
    }

    private fun observeEntitlement() {
        viewModelScope.launch {
            premiumManager.isPremium.collect { premium ->
                _uiState.update { it.copy(isPremium = premium) }
            }
        }
    }

    private fun observePrices() {
        viewModelScope.launch {
            billingManager.formattedPrices.collect { byPlan ->
                _uiState.update {
                    it.copy(prices = byPlan.mapKeys { (plan, _) -> plan.toPlanType() })
                }
            }
        }
    }

    private fun observeBillingEvents() {
        viewModelScope.launch {
            billingManager.events.collect { event ->
                when (event) {
                    is BillingEvent.PremiumGranted -> _uiState.update {
                        it.copy(isLoading = false, purchaseSuccess = true, message = "Premium unlocked!")
                    }
                    is BillingEvent.Cancelled -> _uiState.update {
                        it.copy(isLoading = false)
                    }
                    is BillingEvent.RestoreFinished -> _uiState.update {
                        it.copy(
                            isLoading = false,
                            message = if (event.restoredPremium) "Purchases restored."
                            else "No previous purchases found.",
                        )
                    }
                    is BillingEvent.Error -> _uiState.update {
                        it.copy(isLoading = false, error = event.message)
                    }
                }
            }
        }
    }

    /** Launches the real Play purchase sheet for the selected plan. */
    fun purchase(activity: Activity, planType: PlanType) {
        _uiState.update { it.copy(isLoading = true, error = null) }
        analytics.track(AnalyticsEvent.PlanSelected(planType.name))
        val started = billingManager.launchPurchaseFlow(activity, planType.toPremiumPlan())
        if (!started) {
            // launchPurchaseFlow already emits an error event; just clear the spinner.
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun restorePurchases() {
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch { billingManager.refreshPurchases(fromRestore = true) }
    }

    fun openTerms() = openUrl("https://pdfmaster.com/terms")
    fun openPrivacy() = openUrl("https://pdfmaster.com/privacy")

    private fun openUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }

    fun clearError() = _uiState.update { it.copy(error = null) }
    fun clearMessage() = _uiState.update { it.copy(message = null) }
}

private fun PlanType.toPremiumPlan(): PremiumPlan = when (this) {
    PlanType.WEEKLY -> PremiumPlan.WEEKLY
    PlanType.ANNUAL -> PremiumPlan.ANNUAL
    PlanType.LIFETIME -> PremiumPlan.LIFETIME
}

private fun PremiumPlan.toPlanType(): PlanType = when (this) {
    PremiumPlan.WEEKLY -> PlanType.WEEKLY
    PremiumPlan.ANNUAL -> PlanType.ANNUAL
    PremiumPlan.LIFETIME -> PlanType.LIFETIME
}
