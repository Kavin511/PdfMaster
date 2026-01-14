package com.pdfmaster.app.domain.repository

import kotlinx.coroutines.flow.Flow

interface SettingsRepository {

    // Theme
    fun getThemeMode(): Flow<ThemeMode>
    suspend fun setThemeMode(mode: ThemeMode)

    // Dynamic Color (Material You)
    fun getDynamicColorEnabled(): Flow<Boolean>
    suspend fun setDynamicColorEnabled(enabled: Boolean)

    // View Preferences
    fun getDefaultViewMode(): Flow<ViewMode>
    suspend fun setDefaultViewMode(mode: ViewMode)

    fun getDefaultSortOrder(): Flow<SortOrder>
    suspend fun setDefaultSortOrder(order: SortOrder)

    // Viewer Preferences
    fun getDefaultZoomLevel(): Flow<Float>
    suspend fun setDefaultZoomLevel(zoom: Float)

    fun getScrollDirection(): Flow<ScrollDirection>
    suspend fun setScrollDirection(direction: ScrollDirection)

    fun getPageTransition(): Flow<PageTransition>
    suspend fun setPageTransition(transition: PageTransition)

    fun getKeepScreenOn(): Flow<Boolean>
    suspend fun setKeepScreenOn(enabled: Boolean)

    // Editor Preferences
    fun getAutoSaveEnabled(): Flow<Boolean>
    suspend fun setAutoSaveEnabled(enabled: Boolean)

    fun getAutoSaveInterval(): Flow<Int>
    suspend fun setAutoSaveInterval(seconds: Int)

    // Compression Default
    fun getDefaultCompressionQuality(): Flow<String>
    suspend fun setDefaultCompressionQuality(quality: String)

    // App Lock
    fun getAppLockEnabled(): Flow<Boolean>
    suspend fun setAppLockEnabled(enabled: Boolean)

    fun getBiometricEnabled(): Flow<Boolean>
    suspend fun setBiometricEnabled(enabled: Boolean)

    // Premium Status
    fun isPremium(): Flow<Boolean>
    suspend fun setPremiumStatus(isPremium: Boolean)

    // Usage Stats (for free tier limits)
    fun getDailyCompressCount(): Flow<Int>
    suspend fun incrementCompressCount()
    suspend fun resetDailyLimits()

    fun getDailyMergeCount(): Flow<Int>
    suspend fun incrementMergeCount()

    // First Launch
    fun isFirstLaunch(): Flow<Boolean>
    suspend fun setFirstLaunchComplete()

    // Clear All
    suspend fun clearAllSettings()
}

enum class ThemeMode {
    LIGHT,
    DARK,
    SYSTEM
}

enum class ViewMode {
    LIST,
    GRID
}

enum class SortOrder {
    NAME_ASC,
    NAME_DESC,
    DATE_NEWEST,
    DATE_OLDEST,
    SIZE_LARGEST,
    SIZE_SMALLEST
}

enum class ScrollDirection {
    VERTICAL,
    HORIZONTAL
}

enum class PageTransition {
    SCROLL,
    SLIDE,
    FADE
}

enum class PdfReadingMode {
    NORMAL,     // Default white background
    DARK,       // Inverted colors for dark mode
    SEPIA,      // Warm sepia tone for reduced eye strain
    NIGHT       // Pure dark mode with reduced brightness
}
