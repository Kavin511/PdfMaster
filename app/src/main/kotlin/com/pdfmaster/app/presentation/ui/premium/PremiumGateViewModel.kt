package com.pdfmaster.app.presentation.ui.premium

import androidx.lifecycle.ViewModel
import com.pdfmaster.app.billing.FeatureGate
import com.pdfmaster.app.billing.GateResult
import com.pdfmaster.app.billing.PremiumFeature
import com.pdfmaster.app.billing.PremiumPrompt
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/**
 * Lightweight paywall gate for screens that are plain composables with no feature ViewModel
 * of their own (protect, unlock, sign) but still guard a hard-premium action.
 *
 * Call [ensure] synchronously right before the gated action: it returns true when the user is
 * entitled, otherwise raises [prompt] (which the screen renders via [PremiumGateDialog]) and
 * returns false so the caller can bail out.
 */
@HiltViewModel
class PremiumGateViewModel @Inject constructor(
    private val featureGate: FeatureGate,
) : ViewModel() {

    private val _prompt = MutableStateFlow<PremiumPrompt?>(null)
    val prompt: StateFlow<PremiumPrompt?> = _prompt.asStateFlow()

    /** True if [feature] is unlocked; otherwise raises the paywall prompt and returns false. */
    fun ensure(feature: PremiumFeature): Boolean =
        when (val gate = featureGate.require(feature)) {
            is GateResult.Allowed -> true
            is GateResult.Blocked -> {
                _prompt.update { gate.prompt }
                false
            }
        }

    fun clearPrompt() = _prompt.update { null }
}
