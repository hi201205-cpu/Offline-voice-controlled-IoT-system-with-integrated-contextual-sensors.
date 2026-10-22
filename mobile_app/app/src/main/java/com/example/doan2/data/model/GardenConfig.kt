package com.example.doan2.data.model

data class GardenConfig(
    val mode: String = "auto", // "auto" or "manual"
    val darkBelow: Int = 30,
    val brightAbove: Int = 70
)
