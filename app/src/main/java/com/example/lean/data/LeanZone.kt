package com.example.lean.data

import kotlin.math.abs
import kotlin.math.roundToInt

enum class LeanZone(val displayName: String) {
    STRAIGHT("Straight (0°–9°)"),
    MILD("Mild Corner (10°–19°)"),
    MODERATE("Moderate Corner (20°–29°)"),
    HARD("Hard Corner (30°–39°)"),
    AGGRESSIVE("Aggressive Corner (40°+)");

    companion object {
        fun classify(
            absAngle: Float
        ): LeanZone {
            val wholeAngle: Int = abs(absAngle.roundToInt())
            return when {
                wholeAngle < 10 -> STRAIGHT
                wholeAngle < 20 -> MILD
                wholeAngle < 30 -> MODERATE
                wholeAngle < 40 -> HARD
                else -> AGGRESSIVE
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
