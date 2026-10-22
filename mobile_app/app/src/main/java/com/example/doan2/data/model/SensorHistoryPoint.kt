package com.example.doan2.data.model

data class SensorHistoryPoint(
    val temp: Double = 0.0,
    val hum: Double = 0.0,
    val motion: Boolean = false,
    val lightLevel: Int = 0,
    val ts: Long = 0L
)
