package com.example.lean.settings

import android.content.Context
import android.content.SharedPreferences
import com.example.lean.data.AppThemeMode
import com.example.lean.data.SensorMode
import com.example.lean.data.SmoothingLevel
import com.example.lean.data.UserSettings

class SettingsRepository(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getSettings(): UserSettings {
        val themeName = prefs.getString(KEY_THEME_MODE, AppThemeMode.DARK.name) ?: AppThemeMode.DARK.name
        val modeName = prefs.getString(KEY_SENSOR_MODE, SensorMode.AUTOMATIC.name) ?: SensorMode.AUTOMATIC.name
        val smoothingName = prefs.getString(KEY_SMOOTHING_LEVEL, SmoothingLevel.MEDIUM.name) ?: SmoothingLevel.MEDIUM.name
        val keepAwake = prefs.getBoolean(KEY_KEEP_SCREEN_AWAKE, true)
        val lockOrientation = prefs.getBoolean(KEY_LOCK_ORIENTATION, false)
        val straightThreshold = prefs.getFloat(KEY_STRAIGHT_THRESHOLD, 3f)
        val preferredMaxLeanThreshold = prefs.getFloat(KEY_PREFERRED_MAX_LEAN_THRESHOLD, 30f)
        val warningThreshold = prefs.getFloat(KEY_WARNING_THRESHOLD, 30f)
        val criticalThreshold = prefs.getFloat(KEY_CRITICAL_THRESHOLD, 40f)
        val isGpsEnabled = prefs.getBoolean(KEY_GPS_ENABLED, true)
        val isFallDetectionEnabled = prefs.getBoolean(KEY_FALL_DETECTION_ENABLED, false)
        val fallDetectionCriticalAngle = prefs.getFloat(KEY_FALL_DETECTION_CRITICAL_ANGLE, 60f)
        val isEmergencySmsEnabled = prefs.getBoolean(KEY_EMERGENCY_SMS_ENABLED, false)
        val emergencyPhoneNumber = prefs.getString(KEY_EMERGENCY_PHONE_NUMBER, "") ?: ""

        val themeMode = runCatching { AppThemeMode.valueOf(themeName) }.getOrDefault(AppThemeMode.DARK)
        val sensorMode = runCatching { SensorMode.valueOf(modeName) }.getOrDefault(SensorMode.AUTOMATIC)
        val smoothingLevel = runCatching { SmoothingLevel.valueOf(smoothingName) }.getOrDefault(SmoothingLevel.MEDIUM)

        return UserSettings(
            themeMode = themeMode,
            sensorMode = sensorMode,
            smoothingLevel = smoothingLevel,
            keepScreenAwake = keepAwake,
            lockOrientation = lockOrientation,
            straightThreshold = straightThreshold,
            preferredMaxLeanThreshold = preferredMaxLeanThreshold,
            warningThreshold = warningThreshold,
            criticalThreshold = criticalThreshold,
            isGpsEnabled = isGpsEnabled,
            isFallDetectionEnabled = isFallDetectionEnabled,
            fallDetectionCriticalAngle = fallDetectionCriticalAngle,
            isEmergencySmsEnabled = isEmergencySmsEnabled,
            emergencyPhoneNumber = emergencyPhoneNumber
        )
    }

    fun saveThemeMode(themeMode: AppThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, themeMode.name).apply()
    }

    fun saveSensorMode(mode: SensorMode) {
        prefs.edit().putString(KEY_SENSOR_MODE, mode.name).apply()
    }

    fun saveSmoothingLevel(level: SmoothingLevel) {
        prefs.edit().putString(KEY_SMOOTHING_LEVEL, level.name).apply()
    }

    fun saveKeepScreenAwake(keepAwake: Boolean) {
        prefs.edit().putBoolean(KEY_KEEP_SCREEN_AWAKE, keepAwake).apply()
    }

    fun saveLockOrientation(lockOrientation: Boolean) {
        prefs.edit().putBoolean(KEY_LOCK_ORIENTATION, lockOrientation).apply()
    }

    fun saveStraightThreshold(value: Float) {
        prefs.edit().putFloat(KEY_STRAIGHT_THRESHOLD, value).apply()
    }

    fun savePreferredMaxLeanThreshold(value: Float) {
        prefs.edit().putFloat(KEY_PREFERRED_MAX_LEAN_THRESHOLD, value).apply()
    }

    fun saveWarningThreshold(value: Float) {
        prefs.edit().putFloat(KEY_WARNING_THRESHOLD, value).apply()
    }

    fun saveCriticalThreshold(value: Float) {
        prefs.edit().putFloat(KEY_CRITICAL_THRESHOLD, value).apply()
    }

    fun saveGpsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_GPS_ENABLED, enabled).apply()
    }

    fun saveFallDetectionEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_FALL_DETECTION_ENABLED, enabled).apply()
    }

    fun saveFallDetectionCriticalAngle(value: Float) {
        prefs.edit().putFloat(KEY_FALL_DETECTION_CRITICAL_ANGLE, value).apply()
    }

    fun saveEmergencySmsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_EMERGENCY_SMS_ENABLED, enabled).apply()
    }

    fun saveEmergencyPhoneNumber(number: String) {
        prefs.edit().putString(KEY_EMERGENCY_PHONE_NUMBER, number).apply()
    }

    fun resetToDefaults(): UserSettings {
        prefs.edit().clear().apply()
        return getSettings()
    }

    companion object {
        private const val PREFS_NAME = "lean_angle_prefs"
        private const val KEY_THEME_MODE = "app_theme_mode"
        private const val KEY_SENSOR_MODE = "sensor_mode"
        private const val KEY_SMOOTHING_LEVEL = "smoothing_level"
        private const val KEY_KEEP_SCREEN_AWAKE = "keep_screen_awake"
        private const val KEY_LOCK_ORIENTATION = "lock_orientation"
        private const val KEY_STRAIGHT_THRESHOLD = "straight_threshold"
        private const val KEY_PREFERRED_MAX_LEAN_THRESHOLD = "preferred_max_lean_threshold"
        private const val KEY_WARNING_THRESHOLD = "warning_threshold"
        private const val KEY_CRITICAL_THRESHOLD = "critical_threshold"
        private const val KEY_GPS_ENABLED = "gps_enabled"
        private const val KEY_FALL_DETECTION_ENABLED = "fall_detection_enabled"
        private const val KEY_FALL_DETECTION_CRITICAL_ANGLE = "fall_detection_critical_angle"
        private const val KEY_EMERGENCY_SMS_ENABLED = "emergency_sms_enabled"
        private const val KEY_EMERGENCY_PHONE_NUMBER = "emergency_phone_number"
    }
}
