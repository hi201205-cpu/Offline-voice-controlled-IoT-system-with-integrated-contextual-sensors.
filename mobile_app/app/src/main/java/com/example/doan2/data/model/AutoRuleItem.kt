package com.example.doan2.data.model

data class AutoAction(
    val type: String = "notify", // "notify" or "device"
    val device: String = "light", // "light", "fan", "yard", "curtain", "door"
    val value: String = "on" // "on", "off"
)

data class AutoRuleItem(
    val id: String = "",
    val name: String = "",
    val condition: String = "temp_high",
    val threshold: Double = 30.0,
    val actions: List<AutoAction> = listOf(AutoAction(type = "notify")),
    val mode: String = "auto", // "auto" or "ask"
    val wait: Int = 3, // minutes
    val timeout: String = "auto", // "auto" or "none"
    val enabled: Boolean = true,
    val from: String = "18:00",
    val to: String = "22:00",
    val days: List<Int> = emptyList(),
    val lastRan: Long? = null
)

