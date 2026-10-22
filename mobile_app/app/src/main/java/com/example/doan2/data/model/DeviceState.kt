package com.example.doan2.data.model

data class DeviceState(
    val lightActual: Boolean = false,
    val lightDesired: Boolean = false,

    val fanActual: Boolean = false,
    val fanDesired: Boolean = false,

    val curtainActual: String = "close", // "open" or "close"
    val curtainDesired: String = "close",

    val gardenActual: Boolean = false,
    val gardenDesired: Boolean = false,

    val doorActual: String = "closed", // "open" or "closed"
    val doorDesired: String = "closed",

    val voiceActual: Boolean = true,
    val voiceDesired: Boolean = true,

    val esp32LastSeen: Long = 0L,
    val keypadLocked: Boolean = false
) {
    // Legacy / Convenience Getters
    val light: Boolean get() = lightActual
    val fan: Boolean get() = fanActual
    val curtain: String get() = curtainActual
    val garden: Boolean get() = gardenActual
    val door: String get() = doorActual
    val voice: Boolean get() = voiceActual

    val isLightPending: Boolean get() = lightActual != lightDesired
    val isFanPending: Boolean get() = fanActual != fanDesired
    val isCurtainPending: Boolean get() = curtainActual != curtainDesired
    val isGardenPending: Boolean get() = gardenActual != gardenDesired
    val isDoorPending: Boolean get() = doorActual != doorDesired
    val isVoicePending: Boolean get() = voiceActual != voiceDesired
}
