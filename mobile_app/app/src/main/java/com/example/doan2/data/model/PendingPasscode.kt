package com.example.doan2.data.model

data class PendingPasscode(
    val op: String = "", // "add" or "delete"
    val name: String? = null,
    val code: String? = null,
    val target: String? = null,
    val ts: Long = 0L,
    val error: String? = null
)
