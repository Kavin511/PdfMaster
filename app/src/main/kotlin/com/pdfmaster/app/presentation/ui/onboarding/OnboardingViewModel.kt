package com.pdfmaster.app.presentation.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pdfmaster.app.analytics.Analytics
import com.pdfmaster.app.analytics.AnalyticsEvent
import com.pdfmaster.app.data.local.preferences.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val userPreferences: UserPreferences,
    private val analytics: Analytics,
) : ViewModel() {

    private val _isFirstLaunch = MutableStateFlow(true)
    val isFirstLaunch: StateFlow<Boolean> = _isFirstLaunch.asStateFlow()

    /** Show the one-time GDPR analytics consent prompt until the user has answered it. */
    private val _showAnalyticsConsent = MutableStateFlow(false)
    val showAnalyticsConsent: StateFlow<Boolean> = _showAnalyticsConsent.asStateFlow()

    init {
        checkFirstLaunch()
        checkAnalyticsConsent()
        analytics.track(AnalyticsEvent.OnboardingStarted)
    }

    private fun checkFirstLaunch() {
        viewModelScope.launch {
            _isFirstLaunch.value = userPreferences.isFirstLaunch().first()
        }
    }

    private fun checkAnalyticsConsent() {
        viewModelScope.launch {
            _showAnalyticsConsent.value = !userPreferences.analyticsConsentAsked().first()
        }
    }

    /** Records the user's choice; analytics stays OFF unless [granted] is true. */
    fun respondToAnalyticsConsent(granted: Boolean) {
        viewModelScope.launch {
            userPreferences.setAnalyticsEnabled(granted)
            userPreferences.setAnalyticsConsentAsked(true)
            _showAnalyticsConsent.value = false
        }
    }

    fun completeOnboarding() {
        viewModelScope.launch {
            userPreferences.setFirstLaunchComplete()
            _isFirstLaunch.value = false
            analytics.track(AnalyticsEvent.OnboardingCompleted)
        }
    }
}
