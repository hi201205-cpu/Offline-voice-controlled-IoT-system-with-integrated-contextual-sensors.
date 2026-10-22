package com.example.doan2.data.repository



import com.example.doan2.data.model.UserPermission

import com.example.doan2.data.model.UserProfile

import com.google.firebase.auth.EmailAuthProvider

import com.google.firebase.auth.FirebaseAuth

import com.google.firebase.database.DataSnapshot

import com.google.firebase.database.DatabaseError

import com.google.firebase.database.FirebaseDatabase

import com.google.firebase.database.ValueEventListener

import kotlinx.coroutines.channels.awaitClose

import kotlinx.coroutines.flow.Flow

import kotlinx.coroutines.flow.callbackFlow



class AuthRepository(

    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),

    private val database: FirebaseDatabase = FirebaseDatabase.getInstance("https://do-an-2-f791a-default-rtdb.asia-southeast1.firebasedatabase.app")

) {



    companion object {
        const val masterEmail = "xhai2005@gmail.com"
        const val DEFAULT_HOME_CODE = "HOME-2026-ESP32"
    }

    val masterEmail: String
        get() = Companion.masterEmail



    val currentUid: String?

        get() = auth.currentUser?.uid



    val currentUserEmail: String?

        get() = auth.currentUser?.email



    val isLoggedIn: Boolean

        get() = auth.currentUser != null



    fun signIn(email: String, pass: String, onResult: (Boolean, String?) -> Unit) {

        if (email.isBlank() || pass.isBlank()) {

            onResult(false, "Email và mật khẩu không được để trống")

            return

        }

        auth.signInWithEmailAndPassword(email.trim(), pass)

            .addOnSuccessListener { result ->

                val uid = result.user?.uid ?: return@addOnSuccessListener onResult(true, null)

                val userEmail = result.user?.email ?: email.trim()

                ensureUserRecord(uid, userEmail)

                onResult(true, null)

            }

            .addOnFailureListener { exc ->

                onResult(false, exc.localizedMessage ?: "Đăng nhập thất bại")

            }

    }



    fun signUp(email: String, pass: String, fullName: String = "", onResult: (Boolean, String?) -> Unit) {

        if (email.isBlank() || pass.isBlank()) {

            onResult(false, "Email và mật khẩu không được để trống")

            return

        }

        if (pass.length < 6) {

            onResult(false, "Mật khẩu phải có ít nhất 6 ký tự")

            return

        }

        auth.createUserWithEmailAndPassword(email.trim(), pass)

            .addOnSuccessListener { result ->

                val uid = result.user?.uid ?: return@addOnSuccessListener onResult(true, null)

                val userEmail = result.user?.email ?: email.trim()

                val profileChangeReq = com.google.firebase.auth.UserProfileChangeRequest.Builder()

                    .setDisplayName(fullName.ifBlank { userEmail.substringBefore("@") })

                    .build()

                result.user?.updateProfile(profileChangeReq)

                ensureUserRecord(uid, userEmail, fullName.ifBlank { userEmail.substringBefore("@") })

                onResult(true, null)

            }

            .addOnFailureListener { exc ->

                onResult(false, exc.localizedMessage ?: "Đăng ký thất bại")

            }

    }







    fun signInWithGoogle(idToken: String, onResult: (Boolean, String?) -> Unit) {

        val credential = com.google.firebase.auth.GoogleAuthProvider.getCredential(idToken, null)

        auth.signInWithCredential(credential)

            .addOnSuccessListener { result ->

                val uid = result.user?.uid ?: return@addOnSuccessListener onResult(true, null)

                val userEmail = result.user?.email ?: ""

                ensureUserRecord(uid, userEmail)

                onResult(true, null)

            }

            .addOnFailureListener { exc ->

                onResult(false, exc.localizedMessage ?: "Đăng nhập Google thất bại")

            }

    }



    private fun ensureUserRecord(uid: String, email: String, customName: String? = null) {

        val ref = database.getReference("users/$uid")

        ref.addListenerForSingleValueEvent(object : ValueEventListener {

            override fun onDataChange(snapshot: DataSnapshot) {

                val isMaster = email.equals(masterEmail, ignoreCase = true)

                val existingName = snapshot.child("name").value?.toString()

                val currentName = customName?.ifEmpty { null }

                    ?: existingName?.ifEmpty { null }

                    ?: auth.currentUser?.displayName

                    ?: (if (isMaster) "Chủ nhà" else email.substringBefore("@"))



                if (!snapshot.exists()) {

                    val map = mutableMapOf<String, Any?>(

                        "email" to email,

                        "name" to currentName,

                        "role" to if (isMaster) "master" else "member",

                        "pairedHome" to if (isMaster) DEFAULT_HOME_CODE else null,

                        "permissions" to mapOf(

                            "light" to true,

                            "fan" to true,

                            "curtain" to true,

                            "door" to true,

                            "yard" to true,

                            "auto" to true,

                            "passcodes" to true

                        )

                    )

                    ref.setValue(map)

                } else {

                    ref.child("email").setValue(email)

                    if (existingName.isNullOrEmpty()) {

                        ref.child("name").setValue(currentName)

                    }

                    if (isMaster) {

                        ref.child("role").setValue("master")

                        ref.child("pairedHome").setValue(DEFAULT_HOME_CODE)

                    }

                }

            }



            override fun onCancelled(error: DatabaseError) {}

        })

    }



    fun observeUserProfile(): Flow<UserProfile?> = callbackFlow {

        val uid = currentUid

        if (uid == null) {

            trySend(null)

            close()

            return@callbackFlow

        }



        val ref = database.getReference("users/$uid")

        val listener = object : ValueEventListener {

            override fun onDataChange(snapshot: DataSnapshot) {

                val email = snapshot.child("email").value?.toString() ?: currentUserEmail ?: ""

                val isMaster = email.equals(masterEmail, ignoreCase = true)

                val defaultName = if (isMaster) "Chủ nhà" else email.substringBefore("@")

                val name = snapshot.child("name").value?.toString()

                    ?: auth.currentUser?.displayName

                    ?: defaultName



                val role = snapshot.child("role").value?.toString() ?: if (isMaster) "master" else "member"

                val pairedHome = snapshot.child("pairedHome").value?.toString()

                val isPaired = isMaster || (pairedHome == DEFAULT_HOME_CODE)



                val permSnap = snapshot.child("permissions")

                val perm = UserPermission(

                    light = permSnap.child("light").value as? Boolean ?: true,

                    fan = permSnap.child("fan").value as? Boolean ?: true,

                    curtain = permSnap.child("curtain").value as? Boolean ?: true,

                    door = permSnap.child("door").value as? Boolean ?: true,

                    yard = permSnap.child("yard").value as? Boolean ?: true,

                    auto = permSnap.child("auto").value as? Boolean ?: true,

                    passcodes = permSnap.child("passcodes").value as? Boolean ?: true,

                    passcodeAdd = permSnap.child("passcodeAdd").value as? Boolean ?: false,

                    passcodeDel = permSnap.child("passcodeDel").value as? Boolean ?: false,

                    voice = permSnap.child("voice").value as? Boolean ?: true

                )



                val phone = snapshot.child("phone").value?.toString() ?: snapshot.child("profile/phone").value?.toString() ?: ""

                val dob = snapshot.child("dob").value?.toString() ?: snapshot.child("profile/dob").value?.toString() ?: ""

                val gender = snapshot.child("gender").value?.toString() ?: snapshot.child("profile/gender").value?.toString() ?: ""

                val address = snapshot.child("address").value?.toString() ?: snapshot.child("profile/address").value?.toString() ?: ""



                trySend(UserProfile(uid, email, name, role, isPaired, pairedHome, perm, phone, dob, gender, address))

            }



            override fun onCancelled(error: DatabaseError) {

                trySend(null)

            }

        }



        ref.addValueEventListener(listener)

        awaitClose { ref.removeEventListener(listener) }

    }



    fun redeemInviteCode(codeStr: String, onResult: (Boolean, String?) -> Unit) {

        val formattedCode = codeStr.trim().uppercase()

        val uid = currentUid

        if (uid == null) {

            onResult(false, "Vui lòng đăng nhập lại")

            return

        }



        val inviteRef = database.getReference("invites/$formattedCode")

        inviteRef.addListenerForSingleValueEvent(object : ValueEventListener {

            override fun onDataChange(snapshot: DataSnapshot) {

                if (!snapshot.exists()) {

                    onResult(false, "Mã liên kết không tồn tại hoặc đã được sử dụng")

                    return

                }



                val expiresTs = snapshot.child("expiresTs").value as? Long ?: 0L

                val nowSec = System.currentTimeMillis() / 1000L

                if (expiresTs > 0 && nowSec > expiresTs) {

                    inviteRef.removeValue()

                    onResult(false, "Mã liên kết đã hết hạn")

                    return

                }



                val updateMap = mapOf<String, Any>(

                    "is_paired" to true,

                    "pairedHome" to DEFAULT_HOME_CODE

                )

                database.getReference("users/$uid").updateChildren(updateMap)

                    .addOnSuccessListener {

                        inviteRef.removeValue()

                        onResult(true, "Liên kết thiết bị thành công!")

                    }

                    .addOnFailureListener { exc ->

                        onResult(false, exc.localizedMessage ?: "Cập nhật liên kết thất bại")

                    }

            }



            override fun onCancelled(error: DatabaseError) {

                onResult(false, error.message)

            }

        })

    }



    fun sendPasswordResetEmail(email: String, onResult: (Boolean, String?) -> Unit) {

        val trimmedEmail = email.trim()

        if (trimmedEmail.isBlank()) {

            onResult(false, "Vui lòng nhập email của bạn")

            return

        }

        auth.sendPasswordResetEmail(trimmedEmail)

            .addOnSuccessListener {

                onResult(true, "Đã gửi email khôi phục mật khẩu. Vui lòng kiểm tra hộp thư (cả mục Spam) và nhấn vào liên kết để tạo mật khẩu mới.")

            }

            .addOnFailureListener { exc ->

                val msg = when {

                    exc.message?.contains("ERROR_USER_NOT_FOUND", ignoreCase = true) == true ||

                    exc.message?.contains("user-not-found", ignoreCase = true) == true -> "Email này chưa được đăng ký trong hệ thống"

                    exc.message?.contains("ERROR_INVALID_EMAIL", ignoreCase = true) == true ||

                    exc.message?.contains("invalid-email", ignoreCase = true) == true -> "Địa chỉ email không hợp lệ"

                    else -> exc.localizedMessage ?: "Lỗi gửi email khôi phục mật khẩu"

                }

                onResult(false, msg)

            }

    }



    fun changePassword(

        currentPass: String,

        newPass: String,

        confirmPass: String,

        onResult: (Boolean, String?) -> Unit

    ) {

        val user = auth.currentUser

        if (user == null) {

            onResult(false, "Vui lòng đăng nhập lại")

            return

        }



        // Check if Google Sign-In account (google.com provider, without password provider)

        val providers = user.providerData.map { it.providerId }

        val isGoogleOnly = providers.contains("google.com") && !providers.contains("password")

        if (isGoogleOnly) {

            onResult(false, "Tài khoản của bạn đăng nhập bằng Google, vui lòng quản lý mật khẩu qua tài khoản Google của bạn.")

            return

        }



        if (currentPass.isBlank()) {

            onResult(false, "Vui lòng nhập mật khẩu hiện tại")

            return

        }



        if (newPass.length < 6) {

            onResult(false, "Mật khẩu mới phải có ít nhất 6 ký tự")

            return

        }



        if (newPass != confirmPass) {

            onResult(false, "Mật khẩu mới và xác nhận mật khẩu không khớp nhau")

            return

        }



        if (newPass == currentPass) {

            onResult(false, "Mật khẩu mới phải khác mật khẩu hiện tại")

            return

        }



        val userEmail = user.email

        if (userEmail.isNullOrBlank()) {

            onResult(false, "Không tìm thấy thông tin email người dùng")

            return

        }



        val credential = EmailAuthProvider.getCredential(userEmail, currentPass)



        user.reauthenticate(credential)

            .addOnSuccessListener {

                user.updatePassword(newPass)

                    .addOnSuccessListener {

                        onResult(true, "Đổi mật khẩu thành công! Mật khẩu mới đã có hiệu lực.")

                    }

                    .addOnFailureListener { exc ->

                        onResult(false, exc.localizedMessage ?: "Cập nhật mật khẩu thất bại")

                    }

            }

            .addOnFailureListener { exc ->

                val msg = when {

                    exc.message?.contains("WRONG_PASSWORD", ignoreCase = true) == true ||

                    exc.message?.contains("invalid-credential", ignoreCase = true) == true ||

                    exc.message?.contains("invalid-password", ignoreCase = true) == true -> "Mật khẩu hiện tại không chính xác"

                    else -> exc.localizedMessage ?: "Xác thực lại thất bại, kiểm tra mật khẩu hiện tại"

                }

                onResult(false, msg)

            }

    }



    fun updateUserProfile(

        name: String,

        phone: String,

        dob: String,

        gender: String,

        address: String,

        onResult: (Boolean, String?) -> Unit

    ) {

        val uid = currentUid

        if (uid == null) {

            onResult(false, "Chưa đăng nhập")

            return

        }



        val updates = mapOf(

            "name" to name.trim(),

            "phone" to phone.trim(),

            "dob" to dob.trim(),

            "gender" to gender.trim(),

            "address" to address.trim(),

            "profile/phone" to phone.trim(),

            "profile/dob" to dob.trim(),

            "profile/gender" to gender.trim(),

            "profile/address" to address.trim(),

            "updatedAt" to System.currentTimeMillis()

        )



        database.getReference("users/$uid").updateChildren(updates)

            .addOnSuccessListener {

                if (name.isNotBlank()) {

                    val profileUpdates = com.google.firebase.auth.UserProfileChangeRequest.Builder()

                        .setDisplayName(name.trim())

                        .build()

                    auth.currentUser?.updateProfile(profileUpdates)

                }

                onResult(true, "Cập nhật thông tin hồ sơ thành công!")

            }

            .addOnFailureListener { exc ->

                onResult(false, exc.localizedMessage ?: "Cập nhật hồ sơ thất bại")

            }

    }



    fun signOut() {

        auth.signOut()

    }

}

