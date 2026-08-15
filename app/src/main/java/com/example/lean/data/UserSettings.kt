package com.example.lean.data

enum class AppThemeMode(val displayName: String) {
    DARK("Dark"),
    LIGHT("Light")
}

enum class SmoothingLevel(val displayName: String, val alpha: Float) {
    FAST("Fast (Responsive)", 0.25f),
    BALANCED("Balanced (Default)", 0.12f),
    SMOOTH("Smooth (Stable)", 0.05f)
}

data class UserSettings(
    val themeMode: AppThemeMode = AppThemeMode.DARK,
    val sensorMode: SensorMode = SensorMode.AUTOMATIC,
    val smoothingLevel: SmoothingLevel = SmoothingLevel.BALANCED,
    val keepScreenAwake: Boolean = true,
    val lockOrientation: Boolean = false,
    val straightThreshold: Float = 3f,
    val preferredMaxLeanThreshold: Float = 30f,
    val warningThreshold: Float = 30f,
    val criticalThreshold: Float = 40f,
    val isGpsEnabled: Boolean = true
)
