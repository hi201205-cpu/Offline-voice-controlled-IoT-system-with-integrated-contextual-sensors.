package com.example.doan2.data.model

data class WebLog(
    val id: String = "",
    val device: String = "",
    val action: String = "",
    val user: String = "",
    val userName: String = "",
    val label: String = "",
    val detail: String = "",
    val source: String = "web",
    val ts: Long = 0L
)
