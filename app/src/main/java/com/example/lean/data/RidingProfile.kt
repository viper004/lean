package com.example.lean.data

/**
 * Operating profiles for sensor processing throughout LEAN.
 *
 * SPORTS:
 *  - Low-latency, high-bandwidth processing for track sessions and performance riding.
 *  - Responsive lean-angle tracking with minimal filter delay.
 *
 * CASUAL:
 *  - Noise-suppressing, smooth processing for commuting and highway travel.
 *  - Dampens motorcycle chassis and engine vibrations while retaining genuine lean angles.
 */
enum class RidingProfile(
    val id: Byte,
    val displayName: String,
    val description: String
) {
    SPORTS(
        id = 0x01,
        displayName = "SPORTS",
        description = "Track & performance — low latency, fast angle response"
    ),
    CASUAL(
        id = 0x02,
        displayName = "CASUAL",
        description = "Daily & highway — smooth filtering, vibration suppression"
    );

    companion object {
        fun fromId(id: Byte): RidingProfile = entries.firstOrNull { it.id == id } ?: SPORTS
        fun fromName(name: String): RidingProfile = entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: SPORTS
    }
}
