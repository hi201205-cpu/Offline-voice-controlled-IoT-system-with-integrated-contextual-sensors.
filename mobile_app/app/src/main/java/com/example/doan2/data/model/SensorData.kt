package com.example.doan2.data.model

data class SensorData(
    val temp: Double = 0.0,
    val hum: Double = 0.0,
    val motion: Boolean = false,
    val presence: String = "absent", // "present", "leaving", "absent"
    val lastMotionTs: Long = 0L,
    val lightLevel: Int = 0,
    val isDark: Boolean = false,
    val ts: Long = 0L
)
