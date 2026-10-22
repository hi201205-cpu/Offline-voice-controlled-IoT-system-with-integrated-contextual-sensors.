package com.example.doan2.data.model

data class UserPermission(
    val light: Boolean = true,
    val fan: Boolean = true,
    val curtain: Boolean = true,
    val door: Boolean = true,
    val yard: Boolean = true,
    val auto: Boolean = true,
    val passcodes: Boolean = true,
    val passcodeAdd: Boolean = false,
    val passcodeDel: Boolean = false,
    val voice: Boolean = true
)
