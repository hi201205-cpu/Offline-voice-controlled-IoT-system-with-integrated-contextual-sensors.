package com.example.doan2.data.model

data class UserProfile(
    val uid: String = "",
    val email: String = "",
    val name: String = "",
    val role: String = "member", // "master" or "member"
    val isPaired: Boolean = false,
    val pairedHome: String? = null,
    val permissions: UserPermission = UserPermission(),
    val phone: String = "",
    val dob: String = "",
    val gender: String = "",
    val address: String = ""
)
