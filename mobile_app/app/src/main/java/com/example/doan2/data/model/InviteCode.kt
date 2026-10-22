package com.example.doan2.data.model

data class InviteCode(
    val code: String = "",
    val createdBy: String = "",
    val createdTs: Long = 0L,
    val expiresTs: Long = 0L
)
