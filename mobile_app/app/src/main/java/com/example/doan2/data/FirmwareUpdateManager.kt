package com.example.doan2.data

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class FirmwareInfo(
    val version: String = "1.0.0",
    val url: String = "",
    val releaseNotes: String = ""
)

class FirmwareUpdateManager {
    private val database = FirebaseDatabase.getInstance(
        "https://do-an-2-f791a-default-rtdb.asia-southeast1.firebasedatabase.app"
    )

    private val _currentFirmwareVersion = MutableStateFlow("1.0.0")
    val currentFirmwareVersion: StateFlow<String> = _currentFirmwareVersion.asStateFlow()

    private val _otaProgress = MutableStateFlow(0)
    val otaProgress: StateFlow<Int> = _otaProgress.asStateFlow()

    private val _otaStatus = MutableStateFlow("idle")
    val otaStatus: StateFlow<String> = _otaStatus.asStateFlow()

    private val _otaError = MutableStateFlow<String?>(null)
    val otaError: StateFlow<String?> = _otaError.asStateFlow()

    init {
        // Lắng nghe phiên bản firmware hiện tại của thiết bị
        database.getReference("devices/esp32-voice/firmware_version")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val ver = snapshot.value?.toString() ?: "1.0.0"
                    _currentFirmwareVersion.value = ver
                }
                override fun onCancelled(error: DatabaseError) {}
            })

        // Lắng nghe tiến trình nạp Flash OTA (0..100)
        database.getReference("devices/esp32-voice/ota_progress")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val prog = (snapshot.value as? Long)?.toInt()
                        ?: (snapshot.value as? Number)?.toInt()
                        ?: 0
                    _otaProgress.value = prog
                }
                override fun onCancelled(error: DatabaseError) {}
            })

        // Lắng nghe trạng thái OTA: idle, downloading, flashing, success, failed
        database.getReference("devices/esp32-voice/ota_status")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    _otaStatus.value = snapshot.value?.toString() ?: "idle"
                }
                override fun onCancelled(error: DatabaseError) {}
            })

        database.getReference("devices/esp32-voice/ota_error")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    _otaError.value = snapshot.value?.toString()
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    fun checkForFirmwareUpdate(onResult: (FirmwareInfo?, Boolean, String?) -> Unit) {
        database.getReference("firmware_info").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) {
                    onResult(null, false, "Chưa có thông tin Firmware mới trên máy chủ")
                    return
                }

                val remoteVer = snapshot.child("version").value?.toString() ?: ""
                val url = snapshot.child("url").value?.toString() ?: ""
                val notes = snapshot.child("releaseNotes").value?.toString() ?: ""

                val info = FirmwareInfo(
                    version = remoteVer,
                    url = url,
                    releaseNotes = notes
                )

                val curVer = _currentFirmwareVersion.value
                val hasUpdate = remoteVer.isNotBlank() && remoteVer != curVer && url.isNotBlank()

                onResult(info, hasUpdate, null)
            }

            override fun onCancelled(error: DatabaseError) {
                onResult(null, false, error.message)
            }
        })
    }

    fun triggerOtaUpdate(url: String, version: String, onResult: (Boolean, String?) -> Unit) {
        val payload = mapOf(
            "url" to url,
            "version" to version,
            "ts" to System.currentTimeMillis()
        )
        database.getReference("commands/ota").setValue(payload)
            .addOnSuccessListener {
                onResult(true, "Đã gửi lệnh cập nhật Firmware tới thiết bị!")
            }
            .addOnFailureListener { exc ->
                onResult(false, exc.localizedMessage ?: "Gửi lệnh OTA thất bại")
            }
    }
}
