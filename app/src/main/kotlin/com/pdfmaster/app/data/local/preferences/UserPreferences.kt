package com.pdfmaster.app.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.pdfmaster.app.domain.repository.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

@Singleton
class UserPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR_ENABLED = booleanPreferencesKey("dynamic_color_enabled")
        val DEFAULT_VIEW_MODE = stringPreferencesKey("default_view_mode")
        val DEFAULT_SORT_ORDER = stringPreferencesKey("default_sort_order")
        val DEFAULT_ZOOM_LEVEL = floatPreferencesKey("default_zoom_level")
        val SCROLL_DIRECTION = stringPreferencesKey("scroll_direction")
        val PAGE_TRANSITION = stringPreferencesKey("page_transition")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val PDF_READING_MODE = stringPreferencesKey("pdf_reading_mode")
        val AUTO_SAVE_ENABLED = booleanPreferencesKey("auto_save_enabled")
        val AUTO_SAVE_INTERVAL = intPreferencesKey("auto_save_interval")
        val DEFAULT_COMPRESSION = stringPreferencesKey("default_compression")
        val APP_LOCK_ENABLED = booleanPreferencesKey("app_lock_enabled")
        val BIOMETRIC_ENABLED = booleanPreferencesKey("biometric_enabled")
        val IS_PREMIUM = booleanPreferencesKey("is_premium")
        val DAILY_COMPRESS_COUNT = intPreferencesKey("daily_compress_count")
        val DAILY_MERGE_COUNT = intPreferencesKey("daily_merge_count")
        val LAST_RESET_DATE = stringPreferencesKey("last_reset_date")
        val IS_FIRST_LAUNCH = booleanPreferencesKey("is_first_launch")
    }

    private val dataStore = context.dataStore

    private fun <T> getPreference(key: Preferences.Key<T>, defaultValue: T): Flow<T> {
        return dataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }
            .map { preferences ->
                preferences[key] ?: defaultValue
            }
    }

    private suspend fun <T> setPreference(key: Preferences.Key<T>, value: T) {
        dataStore.edit { preferences ->
            preferences[key] = value
        }
    }

    // Theme
    fun getThemeMode(): Flow<ThemeMode> = getPreference(Keys.THEME_MODE, ThemeMode.SYSTEM.name)
        .map { ThemeMode.valueOf(it) }

    suspend fun setThemeMode(mode: ThemeMode) = setPreference(Keys.THEME_MODE, mode.name)

    // Dynamic Color
    fun getDynamicColorEnabled(): Flow<Boolean> = getPreference(Keys.DYNAMIC_COLOR_ENABLED, true)
    suspend fun setDynamicColorEnabled(enabled: Boolean) = setPreference(Keys.DYNAMIC_COLOR_ENABLED, enabled)

    // View Mode
    fun getDefaultViewMode(): Flow<ViewMode> = getPreference(Keys.DEFAULT_VIEW_MODE, ViewMode.LIST.name)
        .map { ViewMode.valueOf(it) }

    suspend fun setDefaultViewMode(mode: ViewMode) = setPreference(Keys.DEFAULT_VIEW_MODE, mode.name)

    // Sort Order
    fun getDefaultSortOrder(): Flow<SortOrder> = getPreference(Keys.DEFAULT_SORT_ORDER, SortOrder.DATE_NEWEST.name)
        .map { SortOrder.valueOf(it) }

    suspend fun setDefaultSortOrder(order: SortOrder) = setPreference(Keys.DEFAULT_SORT_ORDER, order.name)

    // Zoom Level
    fun getDefaultZoomLevel(): Flow<Float> = getPreference(Keys.DEFAULT_ZOOM_LEVEL, 1f)
    suspend fun setDefaultZoomLevel(zoom: Float) = setPreference(Keys.DEFAULT_ZOOM_LEVEL, zoom)

    // Scroll Direction
    fun getScrollDirection(): Flow<ScrollDirection> = getPreference(Keys.SCROLL_DIRECTION, ScrollDirection.VERTICAL.name)
        .map { ScrollDirection.valueOf(it) }

    suspend fun setScrollDirection(direction: ScrollDirection) = setPreference(Keys.SCROLL_DIRECTION, direction.name)

    // Page Transition
    fun getPageTransition(): Flow<PageTransition> = getPreference(Keys.PAGE_TRANSITION, PageTransition.SCROLL.name)
        .map { PageTransition.valueOf(it) }

    suspend fun setPageTransition(transition: PageTransition) = setPreference(Keys.PAGE_TRANSITION, transition.name)

    // Keep Screen On
    fun getKeepScreenOn(): Flow<Boolean> = getPreference(Keys.KEEP_SCREEN_ON, false)
    suspend fun setKeepScreenOn(enabled: Boolean) = setPreference(Keys.KEEP_SCREEN_ON, enabled)

    // PDF Reading Mode
    fun getPdfReadingMode(): Flow<PdfReadingMode> = getPreference(Keys.PDF_READING_MODE, PdfReadingMode.NORMAL.name)
        .map { PdfReadingMode.valueOf(it) }

    suspend fun setPdfReadingMode(mode: PdfReadingMode) = setPreference(Keys.PDF_READING_MODE, mode.name)

    // Auto Save
    fun getAutoSaveEnabled(): Flow<Boolean> = getPreference(Keys.AUTO_SAVE_ENABLED, true)
    suspend fun setAutoSaveEnabled(enabled: Boolean) = setPreference(Keys.AUTO_SAVE_ENABLED, enabled)

    fun getAutoSaveInterval(): Flow<Int> = getPreference(Keys.AUTO_SAVE_INTERVAL, 30)
    suspend fun setAutoSaveInterval(seconds: Int) = setPreference(Keys.AUTO_SAVE_INTERVAL, seconds)

    // Compression
    fun getDefaultCompressionQuality(): Flow<String> = getPreference(Keys.DEFAULT_COMPRESSION, "MEDIUM")
    suspend fun setDefaultCompressionQuality(quality: String) = setPreference(Keys.DEFAULT_COMPRESSION, quality)

    // App Lock
    fun getAppLockEnabled(): Flow<Boolean> = getPreference(Keys.APP_LOCK_ENABLED, false)
    suspend fun setAppLockEnabled(enabled: Boolean) = setPreference(Keys.APP_LOCK_ENABLED, enabled)

    fun getBiometricEnabled(): Flow<Boolean> = getPreference(Keys.BIOMETRIC_ENABLED, false)
    suspend fun setBiometricEnabled(enabled: Boolean) = setPreference(Keys.BIOMETRIC_ENABLED, enabled)

    // Premium
    fun isPremium(): Flow<Boolean> = getPreference(Keys.IS_PREMIUM, false)
    suspend fun setPremiumStatus(isPremium: Boolean) = setPreference(Keys.IS_PREMIUM, isPremium)
    suspend fun setPremium(isPremium: Boolean) = setPremiumStatus(isPremium)

    // Daily Limits
    fun getDailyCompressCount(): Flow<Int> {
        checkAndResetDailyLimits()
        return getPreference(Keys.DAILY_COMPRESS_COUNT, 0)
    }

    suspend fun incrementCompressCount() {
        dataStore.edit { preferences ->
            val current = preferences[Keys.DAILY_COMPRESS_COUNT] ?: 0
            preferences[Keys.DAILY_COMPRESS_COUNT] = current + 1
        }
    }

    fun getDailyMergeCount(): Flow<Int> {
        checkAndResetDailyLimits()
        return getPreference(Keys.DAILY_MERGE_COUNT, 0)
    }

    suspend fun incrementMergeCount() {
        dataStore.edit { preferences ->
            val current = preferences[Keys.DAILY_MERGE_COUNT] ?: 0
            preferences[Keys.DAILY_MERGE_COUNT] = current + 1
        }
    }

    private fun checkAndResetDailyLimits() {
        val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        // This would be handled in a coroutine scope in real implementation
    }

    suspend fun resetDailyLimits() {
        val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        dataStore.edit { preferences ->
            preferences[Keys.DAILY_COMPRESS_COUNT] = 0
            preferences[Keys.DAILY_MERGE_COUNT] = 0
            preferences[Keys.LAST_RESET_DATE] = today
        }
    }

    // First Launch
    fun isFirstLaunch(): Flow<Boolean> = getPreference(Keys.IS_FIRST_LAUNCH, true)
    suspend fun setFirstLaunchComplete() = setPreference(Keys.IS_FIRST_LAUNCH, false)

    // Clear All
    suspend fun clearAllSettings() {
        dataStore.edit { preferences ->
            preferences.clear()
        }
    }
}
