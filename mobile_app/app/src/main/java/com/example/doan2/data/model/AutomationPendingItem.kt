package com.example.doan2.data.model

data class AutomationPendingItem(
    val ruleId: String = "",
    val ruleName: String = "",
    val createdAt: Long = 0L,
    val expiresAt: Long = 0L,
    val timeoutAction: String = "auto",
    val answer: String = ""
)
