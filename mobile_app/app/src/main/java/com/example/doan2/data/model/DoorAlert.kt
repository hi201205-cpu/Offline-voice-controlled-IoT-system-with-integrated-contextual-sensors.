package com.example.doan2.data.model

data class DoorAlert(
    val type: String? = null,
    val count: Int? = null,
    val ts: Long? = null,
    val ack: Boolean? = true
)
