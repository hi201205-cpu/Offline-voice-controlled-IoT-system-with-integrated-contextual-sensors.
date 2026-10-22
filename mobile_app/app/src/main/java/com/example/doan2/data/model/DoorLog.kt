package com.example.doan2.data.model

data class DoorLog(
    val id: String = "",
    val event: String = "", // "open", "close", "wrong_password", "lockout"
    val source: String = "", // "keypad", "button", "web", "app", "auto"
    val who: String? = null,
    val reason: String? = null,
    val ts: Long = 0L
)
