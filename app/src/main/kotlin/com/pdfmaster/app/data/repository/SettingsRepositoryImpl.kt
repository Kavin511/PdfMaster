package com.pdfmaster.app.data.repository

import com.pdfmaster.app.data.local.preferences.UserPreferences
import com.pdfmaster.app.domain.repository.*
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val userPreferences: UserPreferences
) : SettingsRepository {

    override fun getThemeMode(): Flow<ThemeMode> = userPreferences.getThemeMode()

    override suspend fun setThemeMode(mode: ThemeMode) = userPreferences.setThemeMode(mode)

    override fun getDynamicColorEnabled(): Flow<Boolean> = userPreferences.getDynamicColorEnabled()

    override suspend fun setDynamicColorEnabled(enabled: Boolean) = userPreferences.setDynamicColorEnabled(enabled)

    override fun getDefaultViewMode(): Flow<ViewMode> = userPreferences.getDefaultViewMode()

    override suspend fun setDefaultViewMode(mode: ViewMode) = userPreferences.setDefaultViewMode(mode)

    override fun getDefaultSortOrder(): Flow<SortOrder> = userPreferences.getDefaultSortOrder()

    override suspend fun setDefaultSortOrder(order: SortOrder) = userPreferences.setDefaultSortOrder(order)

    override fun getDefaultZoomLevel(): Flow<Float> = userPreferences.getDefaultZoomLevel()

    override suspend fun setDefaultZoomLevel(zoom: Float) = userPreferences.setDefaultZoomLevel(zoom)

    override fun getScrollDirection(): Flow<ScrollDirection> = userPreferences.getScrollDirection()

    override suspend fun setScrollDirection(direction: ScrollDirection) = userPreferences.setScrollDirection(direction)

    override fun getPageTransition(): Flow<PageTransition> = userPreferences.getPageTransition()

    override suspend fun setPageTransition(transition: PageTransition) = userPreferences.setPageTransition(transition)

    override fun getKeepScreenOn(): Flow<Boolean> = userPreferences.getKeepScreenOn()

    override suspend fun setKeepScreenOn(enabled: Boolean) = userPreferences.setKeepScreenOn(enabled)

    override fun getAutoSaveEnabled(): Flow<Boolean> = userPreferences.getAutoSaveEnabled()

    override suspend fun setAutoSaveEnabled(enabled: Boolean) = userPreferences.setAutoSaveEnabled(enabled)

    override fun getAutoSaveInterval(): Flow<Int> = userPreferences.getAutoSaveInterval()

    override suspend fun setAutoSaveInterval(seconds: Int) = userPreferences.setAutoSaveInterval(seconds)

    override fun getDefaultCompressionQuality(): Flow<String> = userPreferences.getDefaultCompressionQuality()

    override suspend fun setDefaultCompressionQuality(quality: String) = userPreferences.setDefaultCompressionQuality(quality)

    override fun getAppLockEnabled(): Flow<Boolean> = userPreferences.getAppLockEnabled()

    override suspend fun setAppLockEnabled(enabled: Boolean) = userPreferences.setAppLockEnabled(enabled)

    override fun getBiometricEnabled(): Flow<Boolean> = userPreferences.getBiometricEnabled()

    override suspend fun setBiometricEnabled(enabled: Boolean) = userPreferences.setBiometricEnabled(enabled)

    override fun isPremium(): Flow<Boolean> = userPreferences.isPremium()

    override suspend fun setPremiumStatus(isPremium: Boolean) = userPreferences.setPremiumStatus(isPremium)

    override fun getDailyCompressCount(): Flow<Int> = userPreferences.getDailyCompressCount()

    override suspend fun incrementCompressCount() = userPreferences.incrementCompressCount()

    override suspend fun resetDailyLimits() = userPreferences.resetDailyLimits()

    override fun getDailyMergeCount(): Flow<Int> = userPreferences.getDailyMergeCount()

    override suspend fun incrementMergeCount() = userPreferences.incrementMergeCount()

    override fun isFirstLaunch(): Flow<Boolean> = userPreferences.isFirstLaunch()

    override suspend fun setFirstLaunchComplete() = userPreferences.setFirstLaunchComplete()

    override suspend fun clearAllSettings() = userPreferences.clearAllSettings()
}
