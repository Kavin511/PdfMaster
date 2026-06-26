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

    init {
        checkFirstLaunch()
        analytics.track(AnalyticsEvent.OnboardingStarted)
    }

    private fun checkFirstLaunch() {
        viewModelScope.launch {
            _isFirstLaunch.value = userPreferences.isFirstLaunch().first()
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
