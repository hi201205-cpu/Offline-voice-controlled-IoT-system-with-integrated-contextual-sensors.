package com.example.doan2.data.model

data class AutoLog(
    val id: String = "",
    val action: String = "", // "fan_on", "fan_off", "light_off", "garden_on", "garden_off"
    val reason: String = "",
    val temp: Double? = null,
    val level: Int? = null,
    val ts: Long = 0L
)
