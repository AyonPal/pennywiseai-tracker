package com.pennywiseai.tracker.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

@Singleton
class UserPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object PreferencesKeys {
        val DARK_THEME_ENABLED = booleanPreferencesKey("dark_theme_enabled")
        val DYNAMIC_COLOR_ENABLED = booleanPreferencesKey("dynamic_color_enabled")
        val HAS_SKIPPED_SMS_PERMISSION = booleanPreferencesKey("has_skipped_sms_permission")
        val DEVELOPER_MODE_ENABLED = booleanPreferencesKey("developer_mode_enabled")
        val SYSTEM_PROMPT = stringPreferencesKey("system_prompt")
        val HAS_SHOWN_SCAN_TUTORIAL = booleanPreferencesKey("has_shown_scan_tutorial")
        val ACTIVE_DOWNLOAD_ID = longPreferencesKey("active_download_id")
        val SMS_SCAN_MONTHS = intPreferencesKey("sms_scan_months")
        val SMS_SCAN_ALL_TIME = booleanPreferencesKey("sms_scan_all_time")
        val LAST_SCAN_TIMESTAMP = longPreferencesKey("last_scan_timestamp")
        val LAST_SCAN_PERIOD = intPreferencesKey("last_scan_period")
        val BASE_CURRENCY = stringPreferencesKey("base_currency")
        
        // In-App Review preferences
        val FIRST_LAUNCH_TIME = longPreferencesKey("first_launch_time")
        val HAS_SHOWN_REVIEW_PROMPT = booleanPreferencesKey("has_shown_review_prompt")
        val LAST_REVIEW_PROMPT_TIME = longPreferencesKey("last_review_prompt_time")
    }

    val userPreferences: Flow<UserPreferences> = context.dataStore.data
        .map { preferences ->
            UserPreferences(
                isDarkThemeEnabled = preferences[PreferencesKeys.DARK_THEME_ENABLED],
                isDynamicColorEnabled = preferences[PreferencesKeys.DYNAMIC_COLOR_ENABLED] ?: false,
                hasSkippedSmsPermission = preferences[PreferencesKeys.HAS_SKIPPED_SMS_PERMISSION] ?: false,
                isDeveloperModeEnabled = preferences[PreferencesKeys.DEVELOPER_MODE_ENABLED] ?: false,
                hasShownScanTutorial = preferences[PreferencesKeys.HAS_SHOWN_SCAN_TUTORIAL] ?: false,
                smsScanMonths = preferences[PreferencesKeys.SMS_SCAN_MONTHS] ?: 3, // Default to 3 months
                smsScanAllTime = preferences[PreferencesKeys.SMS_SCAN_ALL_TIME] ?: false, // Default to false
                baseCurrency = preferences[PreferencesKeys.BASE_CURRENCY] ?: "INR" // Default to INR
            )
        }

    val baseCurrency: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.BASE_CURRENCY] ?: "INR"
        }

    val isDeveloperModeEnabled: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.DEVELOPER_MODE_ENABLED] ?: false
        }

    suspend fun updateDarkThemeEnabled(enabled: Boolean?) {
        context.dataStore.edit { preferences ->
            if (enabled == null) {
                preferences.remove(PreferencesKeys.DARK_THEME_ENABLED)
            } else {
                preferences[PreferencesKeys.DARK_THEME_ENABLED] = enabled
            }
        }
    }

    suspend fun updateDynamicColorEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.DYNAMIC_COLOR_ENABLED] = enabled
        }
    }
    
    suspend fun updateSkippedSmsPermission(skipped: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.HAS_SKIPPED_SMS_PERMISSION] = skipped
        }
    }
    
    suspend fun setDeveloperModeEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.DEVELOPER_MODE_ENABLED] = enabled
        }
    }
    
    suspend fun updateSystemPrompt(prompt: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SYSTEM_PROMPT] = prompt
        }
    }
    
    fun getSystemPrompt(): Flow<String?> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.SYSTEM_PROMPT]
        }
    
    suspend fun markScanTutorialShown() {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.HAS_SHOWN_SCAN_TUTORIAL] = true
        }
    }
    
    suspend fun saveActiveDownloadId(id: Long) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.ACTIVE_DOWNLOAD_ID] = id
        }
    }
    
    suspend fun getActiveDownloadId(): Long? {
        return context.dataStore.data
            .map { preferences -> preferences[PreferencesKeys.ACTIVE_DOWNLOAD_ID] }
            .first()
    }
    
    suspend fun clearActiveDownloadId() {
        context.dataStore.edit { preferences ->
            preferences.remove(PreferencesKeys.ACTIVE_DOWNLOAD_ID)
        }
    }
    
    val smsScanMonths: Flow<Int> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.SMS_SCAN_MONTHS] ?: 3 // Default to 3 months
        }
    
    suspend fun updateSmsScanMonths(months: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SMS_SCAN_MONTHS] = months
        }
    }

    suspend fun getSmsScanMonths(): Int {
        return context.dataStore.data
            .map { preferences -> preferences[PreferencesKeys.SMS_SCAN_MONTHS] ?: 3 }
            .first()
    }

    val smsScanAllTime: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.SMS_SCAN_ALL_TIME] ?: false
        }

    suspend fun updateSmsScanAllTime(allTime: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SMS_SCAN_ALL_TIME] = allTime
        }
    }

    suspend fun getSmsScanAllTime(): Boolean {
        return context.dataStore.data
            .map { preferences -> preferences[PreferencesKeys.SMS_SCAN_ALL_TIME] ?: false }
            .first()
    }
    
    suspend fun setLastScanTimestamp(timestamp: Long) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.LAST_SCAN_TIMESTAMP] = timestamp
        }
    }
    
    suspend fun setLastScanPeriod(period: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.LAST_SCAN_PERIOD] = period
        }
    }
    
    suspend fun setFirstLaunchTime(timestamp: Long) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.FIRST_LAUNCH_TIME] = timestamp
        }
    }
    
    suspend fun hasShownReviewPrompt(): Boolean {
        return context.dataStore.data
            .map { preferences -> preferences[PreferencesKeys.HAS_SHOWN_REVIEW_PROMPT] ?: false }
            .first()
    }
    
    suspend fun markReviewPromptShown() {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.HAS_SHOWN_REVIEW_PROMPT] = true
            preferences[PreferencesKeys.LAST_REVIEW_PROMPT_TIME] = System.currentTimeMillis()
        }
    }
    
    // Flow methods for backup/restore
    fun getLastScanTimestamp(): Flow<Long?> = context.dataStore.data
        .map { preferences -> preferences[PreferencesKeys.LAST_SCAN_TIMESTAMP] }
    
    fun getLastScanPeriod(): Flow<Int?> = context.dataStore.data
        .map { preferences -> preferences[PreferencesKeys.LAST_SCAN_PERIOD] }
    
    fun getFirstLaunchTime(): Flow<Long?> = context.dataStore.data
        .map { preferences -> preferences[PreferencesKeys.FIRST_LAUNCH_TIME] }
    
    fun getHasShownReviewPrompt(): Flow<Boolean> = context.dataStore.data
        .map { preferences -> preferences[PreferencesKeys.HAS_SHOWN_REVIEW_PROMPT] ?: false }
    
    fun getLastReviewPromptTime(): Flow<Long?> = context.dataStore.data
        .map { preferences -> preferences[PreferencesKeys.LAST_REVIEW_PROMPT_TIME] }
    
    // Update methods for import
    suspend fun updateDarkTheme(enabled: Boolean?) {
        updateDarkThemeEnabled(enabled)
    }
    
    suspend fun updateDynamicColor(enabled: Boolean) {
        updateDynamicColorEnabled(enabled)
    }
    
    suspend fun updateHasSkippedSmsPermission(skipped: Boolean) {
        updateSkippedSmsPermission(skipped)
    }
    
    suspend fun updateDeveloperMode(enabled: Boolean) {
        setDeveloperModeEnabled(enabled)
    }
    
    suspend fun updateLastScanTimestamp(timestamp: Long) {
        setLastScanTimestamp(timestamp)
    }
    
    suspend fun updateLastScanPeriod(period: Int) {
        setLastScanPeriod(period)
    }
    
    suspend fun updateFirstLaunchTime(timestamp: Long) {
        setFirstLaunchTime(timestamp)
    }
    
    suspend fun updateHasShownScanTutorial(shown: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.HAS_SHOWN_SCAN_TUTORIAL] = shown
        }
    }
    
    suspend fun updateHasShownReviewPrompt(shown: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.HAS_SHOWN_REVIEW_PROMPT] = shown
        }
    }
    
    suspend fun updateLastReviewPromptTime(timestamp: Long) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.LAST_REVIEW_PROMPT_TIME] = timestamp
        }
    }
}

data class UserPreferences(
    val isDarkThemeEnabled: Boolean? = null, // null means follow system
    val isDynamicColorEnabled: Boolean = false, // Default to custom brand colors
    val hasSkippedSmsPermission: Boolean = false,
    val isDeveloperModeEnabled: Boolean = false,
    val hasShownScanTutorial: Boolean = false,
    val smsScanMonths: Int = 3, // Default to 3 months
    val smsScanAllTime: Boolean = false, // Default to false
    val baseCurrency: String = "INR" // Default to INR
)