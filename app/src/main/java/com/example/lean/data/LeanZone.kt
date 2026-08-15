package com.example.lean.data

enum class LeanZone(val displayName: String) {
    STRAIGHT("Straight (0°–3°)"),
    LIGHT("Light Lean (3°–10°)"),
    MODERATE("Moderate Lean (10°–20°)"),
    HIGH("High Lean (20°–30°)"),
    WARNING("Warning (30°–40°)"),
    CRITICAL("Critical (40°+)");

    companion object {
        fun classify(
            absAngle: Float,
            straightThreshold: Float = 3f,
            warningThreshold: Float = 30f,
            criticalThreshold: Float = 40f
        ): LeanZone {
            return when {
                absAngle <= straightThreshold -> STRAIGHT
                absAngle <= 10f -> LIGHT
                absAngle <= 20f -> MODERATE
                absAngle <= warningThreshold -> HIGH
                absAngle <= criticalThreshold -> WARNING
                else -> CRITICAL
            }
        }
    }
}

enum class LeanSafetyRating(val displayName: String) {
    GOOD("GOOD"),
    CAUTION("CAUTION"),
    HIGH("HIGH WARNING");

    companion object {
        fun evaluate(warningPercentage: Float, criticalPercentage: Float): LeanSafetyRating {
            return when {
                criticalPercentage > 3f || warningPercentage > 15f -> HIGH
                criticalPercentage > 0.5f || warningPercentage > 5f -> CAUTION
                else -> GOOD
            }
        }
    }
}
