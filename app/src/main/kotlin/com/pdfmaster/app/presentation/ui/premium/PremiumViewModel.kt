package com.pdfmaster.app.presentation.ui.premium

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pdfmaster.app.data.local.preferences.UserPreferences
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
    val error: String? = null
)

@HiltViewModel
class PremiumViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userPreferences: UserPreferences
) : ViewModel() {
    private val _uiState = MutableStateFlow(PremiumUiState())
    val uiState: StateFlow<PremiumUiState> = _uiState.asStateFlow()

    init {
        checkPremiumStatus()
    }

    private fun checkPremiumStatus() {
        viewModelScope.launch {
            userPreferences.isPremium().collect { premium ->
                _uiState.update { it.copy(isPremium = premium) }
            }
        }
    }

    fun purchase(planType: PlanType) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            // In a real implementation, you would:
            // 1. Connect to Google Play Billing
            // 2. Launch the purchase flow
            // 3. Handle the purchase result
            // 4. Verify the purchase on your backend
            // 5. Grant premium access

            // For now, we simulate a purchase
            try {
                // Simulated delay for purchase flow
                kotlinx.coroutines.delay(1500)

                // Grant premium access
                userPreferences.setPremium(true)
                _uiState.update { it.copy(isLoading = false, isPremium = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun restorePurchases() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            // In a real implementation, you would:
            // 1. Query Google Play for existing purchases
            // 2. Verify them on your backend
            // 3. Restore premium access if valid purchases exist

            try {
                kotlinx.coroutines.delay(1000)
                _uiState.update { it.copy(isLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun openTerms() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://pdfmaster.com/terms")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            // Handle error
        }
    }

    fun openPrivacy() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://pdfmaster.com/privacy")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            // Handle error
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
