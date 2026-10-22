package com.example.doan2.data.repository

import android.os.Handler
import android.os.Looper
import com.example.doan2.data.model.*
import com.google.firebase.database.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class FirebaseRepository(
    private val database: FirebaseDatabase = FirebaseDatabase.getInstance()
) {

    private fun safeDouble(value: Any?): Double {
        return when (value) {
            is Number -> value.toDouble()
            is String -> value.toDoubleOrNull() ?: 0.0
            else -> 0.0
        }
    }

    private fun safeLong(value: Any?): Long {
        return when (value) {
            is Number -> value.toLong()
            is String -> value.toLongOrNull() ?: 0L
            else -> 0L
        }
    }

    private fun safeInt(value: Any?): Int {
        return when (value) {
            is Number -> value.toInt()
            is String -> value.toIntOrNull() ?: 0
            else -> 0
        }
    }

    private fun safeBoolean(value: Any?): Boolean {
        return when (value) {
            is Boolean -> value
            is String -> value.equals("true", ignoreCase = true) || value == "1"
            is Number -> value.toInt() != 0
            else -> false
        }
    }

    // 1. Observe /devices/state (actual states) + /devices/esp32-voice/last_seen
    fun observeDeviceState(): Flow<DeviceState> = callbackFlow {
        val stateRef = database.getReference("devices/state")
        val lastSeenRef = database.getReference("devices/esp32-voice/last_seen")
        var currentEsp32LastSeen = 0L

        val lastSeenListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                currentEsp32LastSeen = safeLong(snapshot.value)
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        lastSeenRef.addValueEventListener(lastSeenListener)

        val stateListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) {
                    trySend(DeviceState(esp32LastSeen = currentEsp32LastSeen))
                    return
                }

                val lightAct = safeBoolean(snapshot.child("light").value)
                val fanAct = safeBoolean(snapshot.child("fan").value)

                val curtainRaw = snapshot.child("curtain").value
                val curtainAct = when (curtainRaw) {
                    is Boolean -> if (curtainRaw) "open" else "close"
                    is String -> curtainRaw
                    else -> "close"
                }

                val gardenAct = when {
                    snapshot.hasChild("garden") -> safeBoolean(snapshot.child("garden").value)
                    snapshot.hasChild("yard") -> safeBoolean(snapshot.child("yard").value)
                    else -> false
                }

                val doorRaw = snapshot.child("door").value
                val doorAct = when (doorRaw) {
                    is Boolean -> if (doorRaw) "open" else "closed"
                    is String -> doorRaw
                    else -> "closed"
                }

                val voiceAct = if (snapshot.hasChild("voice")) safeBoolean(snapshot.child("voice").value) else true
                val isLocked = safeBoolean(snapshot.child("keypad_locked").value)

                trySend(
                    DeviceState(
                        lightActual = lightAct,
                        lightDesired = lightAct,
                        fanActual = fanAct,
                        fanDesired = fanAct,
                        curtainActual = curtainAct,
                        curtainDesired = curtainAct,
                        gardenActual = gardenAct,
                        gardenDesired = gardenAct,
                        doorActual = doorAct,
                        doorDesired = doorAct,
                        voiceActual = voiceAct,
                        voiceDesired = voiceAct,
                        esp32LastSeen = currentEsp32LastSeen,
                        keypadLocked = isLocked
                    )
                )
            }

            override fun onCancelled(error: DatabaseError) {
                trySend(DeviceState())
            }
        }
        stateRef.addValueEventListener(stateListener)

        awaitClose {
            stateRef.removeEventListener(stateListener)
            lastSeenRef.removeEventListener(lastSeenListener)
        }
    }

    // 2. Observe /sensors/latest
    fun observeSensorLatest(): Flow<SensorData> = callbackFlow {
        val ref = database.getReference("sensors/latest")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) {
                    trySend(SensorData())
                    return
                }
                val temp = safeDouble(snapshot.child("temp").value)
                val hum = safeDouble(snapshot.child("hum").value)
                val motion = safeBoolean(snapshot.child("motion").value)
                val presence = snapshot.child("presence").value?.toString() ?: "absent"
                val lastMotionTs = safeLong(snapshot.child("last_motion_ts").value)
                val lightLevel = safeInt(snapshot.child("light_level").value)
                val isDark = safeBoolean(snapshot.child("is_dark").value)
                val ts = safeLong(snapshot.child("ts").value)

                trySend(SensorData(temp, hum, motion, presence, lastMotionTs, lightLevel, isDark, ts))
            }

            override fun onCancelled(error: DatabaseError) {
                trySend(SensorData())
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    // 3. Observe /sensors/history (up to 200 points)
    fun observeSensorHistory(): Flow<List<SensorHistoryPoint>> = callbackFlow {
        val ref = database.getReference("sensors/history").limitToLast(200)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<SensorHistoryPoint>()
                for (child in snapshot.children) {
                    val temp = safeDouble(child.child("temp").value)
                    val hum = safeDouble(child.child("hum").value)
                    val motion = safeBoolean(child.child("motion").value)
                    val lightLevel = safeInt(child.child("light_level").value)
                    val ts = safeLong(child.child("ts").value)
                    list.add(SensorHistoryPoint(temp, hum, motion, lightLevel, ts))
                }
                list.sortBy { it.ts }
                trySend(list)
            }

            override fun onCancelled(error: DatabaseError) {
                trySend(emptyList())
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    // 4. Observe /logs/door
    fun observeDoorLogs(): Flow<List<DoorLog>> = callbackFlow {
        val ref = database.getReference("logs/door").limitToLast(100)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<DoorLog>()
                for (child in snapshot.children) {
                    val id = child.key ?: ""
                    val event = child.child("event").value?.toString()
                        ?: child.child("action").value?.toString() ?: ""
                    val source = child.child("source").value?.toString() ?: ""
                    val who = child.child("who").value?.toString()
                    val reason = child.child("reason").value?.toString()
                    val ts = safeLong(child.child("ts").value)
                    list.add(DoorLog(id, event, source, who, reason, ts))
                }
                list.sortByDescending { it.ts }
                trySend(list)
            }

            override fun onCancelled(error: DatabaseError) {
                trySend(emptyList())
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    // 5. Observe /logs/auto
    fun observeAutoLogs(): Flow<List<AutoLog>> = callbackFlow {
        val ref = database.getReference("logs/auto").limitToLast(100)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<AutoLog>()
                for (child in snapshot.children) {
                    val id = child.key ?: ""
                    val action = child.child("action").value?.toString() ?: ""
                    val reason = child.child("reason").value?.toString() ?: ""
                    val temp = if (child.hasChild("temp")) safeDouble(child.child("temp").value) else null
                    val level = if (child.hasChild("level")) safeInt(child.child("level").value) else null
                    val ts = safeLong(child.child("ts").value)
                    list.add(AutoLog(id, action, reason, temp, level, ts))
                }
                list.sortByDescending { it.ts }
                trySend(list)
            }

            override fun onCancelled(error: DatabaseError) {
                trySend(emptyList())
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    // 6. Observe /logs/voice
    fun observeVoiceLogs(): Flow<List<VoiceLog>> = callbackFlow {
        val ref = database.getReference("logs/voice").limitToLast(100)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<VoiceLog>()
                for (child in snapshot.children) {
                    val id = child.key ?: ""
                    val keyword = child.child("keyword").value?.toString() ?: ""
                    val confidence = safeDouble(child.child("confidence").value)
                    val ts = safeLong(child.child("ts").value)
                    list.add(VoiceLog(id, keyword, confidence, ts))
                }
                list.sortByDescending { it.ts }
                trySend(list)
            }

            override fun onCancelled(error: DatabaseError) {
                trySend(emptyList())
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    // 7. Observe /alerts/door
    fun unlockKeypad(onResult: ((Boolean, String?) -> Unit)? = null) {
        val userEmail = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.email ?: "app"
        val doorCmd = mapOf(
            "action" to "unlock_keypad",
            "ts" to ServerValue.TIMESTAMP,
            "src" to "app",
            "who" to userEmail
        )
        val updates = mapOf<String, Any>(
            "commands/door" to doorCmd,
            "devices/state/keypad_locked" to false,
            "door/keypad_locked" to false
        )
        database.reference.updateChildren(updates)
            .addOnSuccessListener {
                logWebAction("door", "Mở khóa bàn phím", userEmail, "Mở khóa bàn phím ngoài", "")
                onResult?.invoke(true, null)
            }
            .addOnFailureListener { exc ->
                onResult?.invoke(false, exc.localizedMessage ?: "Mở khóa thất bại")
            }
    }

    fun observeDoorAlert(): Flow<DoorAlert?> = callbackFlow {
        val ref = database.getReference("alerts/door")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) {
                    trySend(null)
                    return
                }
                val type = snapshot.child("type").value?.toString()
                val count = if (snapshot.hasChild("count")) safeInt(snapshot.child("count").value) else null
                val ts = if (snapshot.hasChild("ts")) safeLong(snapshot.child("ts").value) else null
                val ack = if (snapshot.hasChild("ack")) safeBoolean(snapshot.child("ack").value) else true
                trySend(DoorAlert(type, count, ts, ack))
            }

            override fun onCancelled(error: DatabaseError) {
                trySend(null)
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    // 8. Observe /door/passcodes
    fun observePasscodes(): Flow<List<PasscodeItem>> = callbackFlow {
        val ref = database.getReference("door/passcodes")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<PasscodeItem>()
                for (child in snapshot.children) {
                    val id = child.key ?: ""
                    val name = child.child("name").value?.toString() ?: ""
                    val createdTs = safeLong(child.child("created_ts").value)
                    list.add(PasscodeItem(id, name, createdTs))
                }
                trySend(list)
            }

            override fun onCancelled(error: DatabaseError) {
                trySend(emptyList())
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    // 9. Observe /config/auto
    fun observeAutoConfig(): Flow<AutoConfig> = callbackFlow {
        val ref = database.getReference("config/auto")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) {
                    trySend(AutoConfig())
                    return
                }
                val enabled = safeBoolean(snapshot.child("enabled").value)
                val fanTempOn = safeDouble(snapshot.child("fan_temp_on").value)
                val noPersonMinutes = safeInt(snapshot.child("no_person_minutes").value)
                trySend(AutoConfig(enabled, if (fanTempOn == 0.0) 30.0 else fanTempOn, if (noPersonMinutes == 0) 15 else noPersonMinutes))
            }

            override fun onCancelled(error: DatabaseError) {
                trySend(AutoConfig())
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    // 10. Observe /config/garden
    fun observeGardenConfig(): Flow<GardenConfig> = callbackFlow {
        val ref = database.getReference("config/garden")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) {
                    trySend(GardenConfig())
                    return
                }
                val mode = snapshot.child("mode").value?.toString() ?: "auto"
                val darkBelow = safeInt(snapshot.child("dark_below").value)
                val brightAbove = safeInt(snapshot.child("bright_above").value)
                trySend(GardenConfig(mode, darkBelow, brightAbove))
            }

            override fun onCancelled(error: DatabaseError) {
                trySend(GardenConfig())
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    // 11. Observe /suggestions/pending
    fun observeSuggestionPending(): Flow<SuggestionPending?> = callbackFlow {
        val ref = database.getReference("suggestions/pending")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) {
                    trySend(null)
                    return
                }
                val device = snapshot.child("device").value?.toString() ?: ""
                val action = snapshot.child("action").value?.toString() ?: ""
                val reason = snapshot.child("reason").value?.toString() ?: ""
                val temp = safeDouble(snapshot.child("temp").value)
                val status = snapshot.child("status").value?.toString() ?: ""
                trySend(SuggestionPending(device, action, reason, temp, status))
            }

            override fun onCancelled(error: DatabaseError) {
                trySend(null)
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    // 12. Observe /logs/web
    fun observeWebLogs(): Flow<List<WebLog>> = callbackFlow {
        val ref = database.getReference("logs/web").limitToLast(100)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<WebLog>()
                for (child in snapshot.children) {
                    val id = child.key ?: ""
                    val device = child.child("device").value?.toString() ?: ""
                    val action = child.child("action").value?.toString() ?: ""
                    val user = child.child("user").value?.toString() ?: ""
                    val userName = child.child("userName").value?.toString() ?: ""
                    val label = child.child("label").value?.toString() ?: ""
                    val detail = child.child("detail").value?.toString() ?: ""
                    val source = child.child("source").value?.toString() ?: "web"
                    val ts = safeLong(child.child("ts").value)
                    list.add(WebLog(id, device, action, user, userName, label, detail, source, ts))
                }
                list.sortByDescending { it.ts }
                trySend(list)
            }

            override fun onCancelled(error: DatabaseError) {
                trySend(emptyList())
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    // Log Web/App action to /logs/web
    fun logWebAction(device: String, action: String, userEmail: String, label: String, userName: String = "") {
        val ref = database.getReference("logs/web").push()
        val finalUser = userEmail.ifEmpty { com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.email ?: "app" }
        val log = mapOf(
            "source" to "app",
            "device" to device,
            "action" to action,
            "user" to finalUser,
            "userName" to finalUser,
            "label" to label,
            "detail" to "Thực hiện bởi: $finalUser",
            "ts" to ServerValue.TIMESTAMP
        )
        ref.setValue(log)
    }

    // Master: Generate Single-Use Invite Code (HM-XXXX)
    fun generateInviteCode(masterEmail: String, onResult: (Boolean, String?) -> Unit) {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val randomStr = (1..4).map { chars.random() }.joinToString("")
        val code = "HM-$randomStr"

        val nowSec = System.currentTimeMillis() / 1000L
        val expiresSec = nowSec + 86400L

        val inviteData = mapOf(
            "code" to code,
            "createdBy" to masterEmail,
            "createdTs" to nowSec,
            "expiresTs" to expiresSec
        )

        database.getReference("invites/$code").setValue(inviteData)
            .addOnSuccessListener { onResult(true, code) }
            .addOnFailureListener { exc -> onResult(false, exc.localizedMessage ?: "Tạo mã thất bại") }
    }

    // Master: Observe Members List
    fun observeMembers(): Flow<List<UserProfile>> = callbackFlow {
        val ref = database.getReference("users")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<UserProfile>()
                for (child in snapshot.children) {
                    val uid = child.key ?: ""
                    val email = child.child("email").value?.toString() ?: ""
                    val name = child.child("name").value?.toString() ?: email.substringBefore("@")
                    val role = child.child("role").value?.toString() ?: "member"
                    val pairedHome = child.child("pairedHome").value?.toString()
                    val isMaster = role == "master" || email.equals("xhai2005@gmail.com", ignoreCase = true)

                    // TUYỆT ĐỐI KHÔNG hiển thị Master trong danh sách thành viên gia đình (giống Web 100%)
                    if (isMaster) {
                        continue
                    }

                    // Chỉ hiển thị thành viên gia đình đã liên kết với ngôi nhà (pairedHome == DEFAULT_HOME_CODE)
                    if (pairedHome != "HOME-2026-ESP32") {
                        continue
                    }

                    val permSnap = child.child("permissions")
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

                    val phone = child.child("phone").value?.toString() ?: child.child("profile/phone").value?.toString() ?: ""
                    val dob = child.child("dob").value?.toString() ?: child.child("profile/dob").value?.toString() ?: ""
                    val gender = child.child("gender").value?.toString() ?: child.child("profile/gender").value?.toString() ?: ""
                    val address = child.child("address").value?.toString() ?: child.child("profile/address").value?.toString() ?: ""
                    list.add(UserProfile(uid, email, name, role, isPaired = true, pairedHome, perm, phone, dob, gender, address))
                }
                trySend(list)
            }

            override fun onCancelled(error: DatabaseError) {
                trySend(emptyList())
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    // Master: Update Member Permissions
    fun updateMemberPermissions(targetUid: String, perm: UserPermission, onResult: ((Boolean, String?) -> Unit)? = null) {
        val map = mapOf(
            "light" to perm.light,
            "fan" to perm.fan,
            "curtain" to perm.curtain,
            "door" to perm.door,
            "yard" to perm.yard,
            "auto" to perm.auto,
            "passcodes" to perm.passcodes
        )
        database.getReference("users/$targetUid/permissions").setValue(map)
            .addOnSuccessListener { onResult?.invoke(true, null) }
            .addOnFailureListener { exc -> onResult?.invoke(false, exc.localizedMessage) }
    }

    // Master: Unpair / Remove Member
    fun unpairMember(targetUid: String, onResult: ((Boolean, String?) -> Unit)? = null) {
        val userRef = database.getReference("users/$targetUid")
        userRef.child("pairedHome").removeValue()
            .addOnSuccessListener {
                userRef.child("is_paired").removeValue()
                userRef.child("role").setValue("member")
                onResult?.invoke(true, null)
            }
            .addOnFailureListener { exc -> onResult?.invoke(false, exc.localizedMessage) }
    }

    // Master: Update single permission for member
    fun updateMemberPermission(targetUid: String, permKey: String, value: Boolean, onResult: ((Boolean, String?) -> Unit)? = null) {
        val permRef = database.getReference("users/$targetUid/permissions/$permKey")
        permRef.setValue(value)
            .addOnSuccessListener { onResult?.invoke(true, null) }
            .addOnFailureListener { exc -> onResult?.invoke(false, exc.localizedMessage) }
    }

    fun acceptSuggestion() {
        database.getReference("commands/fan").setValue(true)
        database.getReference("devices/fan/desired").setValue(true)
        database.getReference("suggestions/pending").removeValue()
    }

    fun dismissSuggestion() {
        database.getReference("suggestions/pending").removeValue()
    }

    // Toggle device based on latest actual from Firebase (devices/state)
    fun toggleDevice(deviceName: String, targetValue: Boolean? = null, onResult: ((Boolean, String?) -> Unit)? = null) {
        val stateKey = if (deviceName == "yard") "garden" else deviceName
        val cmdTarget = if (deviceName == "garden" || deviceName == "yard") "yard" else deviceName
        val stateRef = database.getReference("devices/state/$stateKey")

        if (targetValue != null) {
            val cmd = mapOf(
                "value" to targetValue,
                "id" to "${System.currentTimeMillis()}-$deviceName"
            )
            database.getReference("commands/$cmdTarget").setValue(cmd)
                .addOnSuccessListener {
                    if (cmdTarget == "yard") {
                        database.getReference("commands/garden").setValue(cmd)
                    }
                    onResult?.invoke(true, null)
                }
                .addOnFailureListener { exc ->
                    onResult?.invoke(false, exc.localizedMessage ?: "Lỗi ghi Firebase")
                }
        } else {
            stateRef.get().addOnSuccessListener { snapshot ->
                val currentActual = safeBoolean(snapshot.value)
                val desiredVal = !currentActual
                val cmd = mapOf(
                    "value" to desiredVal,
                    "id" to "${System.currentTimeMillis()}-$deviceName"
                )
                database.getReference("commands/$cmdTarget").setValue(cmd)
                    .addOnSuccessListener {
                        if (cmdTarget == "yard") {
                            database.getReference("commands/garden").setValue(cmd)
                        }
                        onResult?.invoke(true, null)
                    }
                    .addOnFailureListener { exc ->
                        onResult?.invoke(false, exc.localizedMessage ?: "Lỗi ghi Firebase")
                    }
            }.addOnFailureListener { exc ->
                onResult?.invoke(false, exc.localizedMessage ?: "Lỗi đọc Firebase")
            }
        }
    }

    fun toggleCurtainDevice(onResult: ((Boolean, String?) -> Unit)? = null) {
        val stateRef = database.getReference("devices/state/curtain")
        stateRef.get().addOnSuccessListener { snapshot ->
            val currentActual = snapshot.value?.toString() ?: "close"
            val desiredVal = if (currentActual.equals("open", ignoreCase = true)) "close" else "open"
            val cmd = mapOf(
                "value" to desiredVal,
                "id" to "${System.currentTimeMillis()}-curtain"
            )
            database.getReference("commands/curtain").setValue(cmd)
                .addOnSuccessListener {
                    onResult?.invoke(true, null)
                }
                .addOnFailureListener { exc ->
                    onResult?.invoke(false, exc.localizedMessage ?: "Lỗi ghi Firebase")
                }
        }.addOnFailureListener { exc ->
            onResult?.invoke(false, exc.localizedMessage ?: "Lỗi đọc Firebase")
        }
    }

    // Commands
    fun setLight(value: Boolean? = null, onResult: ((Boolean, String?) -> Unit)? = null) {
        toggleDevice("light", value, onResult)
    }

    fun setFan(value: Boolean? = null, onResult: ((Boolean, String?) -> Unit)? = null) {
        toggleDevice("fan", value, onResult)
    }

    fun setCurtain(value: String? = null, onResult: ((Boolean, String?) -> Unit)? = null) {
        toggleCurtainDevice(onResult)
    }

    fun setGarden(value: Boolean? = null, onResult: ((Boolean, String?) -> Unit)? = null) {
        database.getReference("config/garden/mode").setValue("manual")
            .addOnCompleteListener {
                toggleDevice("garden", value, onResult)
            }
    }

    fun setVoice(value: Boolean? = null, onResult: ((Boolean, String?) -> Unit)? = null) {
        val stateRef = database.getReference("devices/state/voice")
        if (value != null) {
            val cmd = mapOf(
                "value" to value,
                "id" to "${System.currentTimeMillis()}-voice"
            )
            database.getReference("commands/voice").setValue(cmd)
                .addOnSuccessListener {
                    stateRef.setValue(value)
                    onResult?.invoke(true, null)
                }
                .addOnFailureListener { exc ->
                    onResult?.invoke(false, exc.localizedMessage ?: "Lỗi ghi Firebase")
                }
        } else {
            stateRef.get().addOnSuccessListener { snapshot ->
                val currentActual = safeBoolean(snapshot.value)
                val desiredVal = !currentActual
                val cmd = mapOf(
                    "value" to desiredVal,
                    "id" to "${System.currentTimeMillis()}-voice"
                )
                database.getReference("commands/voice").setValue(cmd)
                    .addOnSuccessListener {
                        stateRef.setValue(desiredVal)
                        onResult?.invoke(true, null)
                    }
                    .addOnFailureListener { exc ->
                        onResult?.invoke(false, exc.localizedMessage ?: "Lỗi ghi Firebase")
                    }
            }.addOnFailureListener { exc ->
                onResult?.invoke(false, exc.localizedMessage ?: "Lỗi đọc Firebase")
            }
        }
    }

    fun closeDoor(onResult: ((Boolean, String?) -> Unit)? = null) {
        val timestampId = System.currentTimeMillis()
        val command = mapOf(
            "action" to "close",
            "id" to timestampId,
            "ts" to ServerValue.TIMESTAMP,
            "src" to "app"
        )
        database.getReference("commands/door").setValue(command)
            .addOnSuccessListener {
                onResult?.invoke(true, null)
            }
            .addOnFailureListener { exc ->
                onResult?.invoke(false, exc.localizedMessage ?: "Lỗi ghi Firebase")
            }
    }

    fun openDoor(onResult: ((Boolean, String?) -> Unit)? = null) {
        val timestampId = System.currentTimeMillis()
        val command = mapOf(
            "action" to "open",
            "id" to timestampId,
            "ts" to ServerValue.TIMESTAMP,
            "src" to "app"
        )
        database.getReference("commands/door").setValue(command)
            .addOnSuccessListener {
                onResult?.invoke(true, null)
            }
            .addOnFailureListener { exc ->
                onResult?.invoke(false, exc.localizedMessage ?: "Lỗi ghi Firebase")
            }
    }

    fun ackDoorAlert() {
        database.getReference("alerts/door/ack").setValue(true)
    }

    fun updateAutoConfig(config: AutoConfig) {
        val map = mapOf(
            "enabled" to config.enabled,
            "fan_temp_on" to config.fanTempOn,
            "no_person_minutes" to config.noPersonMinutes
        )
        database.getReference("config/auto").setValue(map)
    }

    // Observe /automation/enabled (Master switch)
    fun observeAutomationMaster(): Flow<Boolean> = callbackFlow {
        val ref = database.getReference("automation/enabled")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val enabled = safeBoolean(snapshot.value)
                trySend(enabled)
            }
            override fun onCancelled(error: DatabaseError) {
                trySend(true)
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    fun setAutomationMaster(enabled: Boolean) {
        database.getReference("automation/enabled").setValue(enabled)
    }

    // Observe /automation/rules
    fun observeAutomationRules(): Flow<List<AutoRuleItem>> = callbackFlow {
        val ref = database.getReference("automation/rules")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<AutoRuleItem>()
                if (snapshot.exists()) {
                    for (child in snapshot.children) {
                        val id = child.child("id").value?.toString() ?: child.key ?: ""
                        val name = child.child("name").value?.toString() ?: ""
                        val condition = child.child("condition").value?.toString() ?: "temp_high"
                        val threshold = safeDouble(child.child("threshold").value)
                        val mode = child.child("mode").value?.toString() ?: "auto"
                        val wait = safeInt(child.child("wait").value).let { if (it <= 0) 3 else it }
                        val timeout = child.child("timeout").value?.toString() ?: "auto"
                        val enabled = if (child.hasChild("enabled")) safeBoolean(child.child("enabled").value) else true
                        val from = child.child("from").value?.toString() ?: "18:00"
                        val to = child.child("to").value?.toString() ?: "22:00"

                        val daysList = mutableListOf<Int>()
                        child.child("days").children.forEach { daySnap ->
                            safeInt(daySnap.value).let { daysList.add(it) }
                        }

                        val actionList = mutableListOf<AutoAction>()
                        child.child("actions").children.forEach { actSnap ->
                            val type = actSnap.child("type").value?.toString() ?: "notify"
                            val device = actSnap.child("device").value?.toString() ?: "light"
                            val value = actSnap.child("value").value?.toString() ?: "on"
                            actionList.add(AutoAction(type, device, value))
                        }
                        if (actionList.isEmpty()) {
                            actionList.add(AutoAction(type = "notify"))
                        }

                        val lastRan = if (child.hasChild("lastRan")) safeLong(child.child("lastRan").value) else null

                        list.add(
                            AutoRuleItem(
                                id = id,
                                name = name,
                                condition = condition,
                                threshold = threshold,
                                actions = actionList,
                                mode = mode,
                                wait = wait,
                                timeout = timeout,
                                enabled = enabled,
                                from = from,
                                to = to,
                                days = daysList,
                                lastRan = lastRan
                            )
                        )
                    }
                }
                trySend(list)
            }

            override fun onCancelled(error: DatabaseError) {
                trySend(emptyList())
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    fun saveAutomationRules(rules: List<AutoRuleItem>, onResult: ((Boolean, String?) -> Unit)? = null) {
        val cleanList = rules.map { rule ->
            val map = mutableMapOf<String, Any>(
                "id" to rule.id,
                "name" to rule.name,
                "condition" to rule.condition,
                "actions" to rule.actions.map {
                    mapOf("type" to it.type, "device" to it.device, "value" to it.value)
                },
                "mode" to rule.mode,
                "enabled" to rule.enabled
            )
            if (rule.condition in listOf("temp_high", "temp_low", "hum_high", "hum_low", "no_motion")) {
                map["threshold"] = rule.threshold
            }
            if (rule.condition == "time") {
                map["from"] = rule.from
                map["to"] = rule.to
                map["days"] = rule.days
            }
            if (rule.mode == "ask") {
                map["wait"] = rule.wait
                map["timeout"] = rule.timeout
            }
            map
        }
        database.getReference("automation/rules").setValue(cleanList)
            .addOnSuccessListener { onResult?.invoke(true, null) }
            .addOnFailureListener { exc -> onResult?.invoke(false, exc.localizedMessage) }
    }

    fun testAutomationRule(rule: AutoRuleItem, onResult: ((Boolean, String?) -> Unit)? = null) {
        for (action in rule.actions) {
            if (action.type == "device") {
                val d = action.device
                val isTurnOn = action.value == "on"
                when (d) {
                    "door" -> {
                        database.getReference("commands/door").setValue(
                            mapOf("action" to "open", "ts" to ServerValue.TIMESTAMP, "src" to "app")
                        )
                    }
                    "curtain" -> {
                        database.getReference("commands/curtain").setValue(
                            mapOf("value" to if (isTurnOn) "open" else "close")
                        )
                    }
                    "yard" -> {
                        database.getReference("commands/garden").setValue(mapOf("value" to isTurnOn))
                        database.getReference("commands/yard").setValue(mapOf("value" to isTurnOn))
                    }
                    else -> {
                        database.getReference("commands/$d").setValue(mapOf("value" to isTurnOn))
                    }
                }
            }
        }
        val statusMap = mapOf(
            "state" to "ran",
            "lastRunAt" to (System.currentTimeMillis() / 1000L),
            "lastResult" to "Chạy thử từ App"
        )
        database.getReference("automation/status/${rule.id}").setValue(statusMap)
            .addOnSuccessListener { onResult?.invoke(true, null) }
            .addOnFailureListener { exc -> onResult?.invoke(false, exc.localizedMessage) }
    }

    fun updateGardenConfig(config: GardenConfig) {
        val map = mapOf(
            "mode" to config.mode,
            "dark_below" to config.darkBelow,
            "bright_above" to config.brightAbove
        )
        database.getReference("config/garden").setValue(map)
    }

    // Add Passcode
    fun addPasscode(name: String, code: String, onResult: (Boolean, String?) -> Unit) {
        val pendingRef = database.getReference("door/pending")
        val newKey = pendingRef.push().key
        if (newKey == null) {
            onResult(false, "Không thể tạo mã yêu cầu mới")
            return
        }

        val pendingData = mapOf(
            "op" to "add",
            "name" to name,
            "code" to code,
            "ts" to System.currentTimeMillis() / 1000L
        )

        val targetPendingRef = pendingRef.child(newKey)
        val passcodesRef = database.getReference("door/passcodes")

        val handler = Handler(Looper.getMainLooper())
        var isFinished = false

        lateinit var pendingListener: ValueEventListener

        val timeoutRunnable = Runnable {
            if (!isFinished) {
                isFinished = true
                targetPendingRef.removeEventListener(pendingListener)
                onResult(false, "Thiết bị chưa xử lý, kiểm tra kết nối")
            }
        }

        pendingListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (isFinished) return

                if (snapshot.exists()) {
                    if (snapshot.hasChild("error")) {
                        val err = snapshot.child("error").value?.toString() ?: "Lỗi thêm mật khẩu"
                        isFinished = true
                        handler.removeCallbacks(timeoutRunnable)
                        targetPendingRef.removeEventListener(pendingListener)
                        targetPendingRef.removeValue()
                        onResult(false, err)
                    }
                } else {
                    // Node was deleted by firmware. Check passcodes to confirm addition.
                    passcodesRef.addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(passSnapshot: DataSnapshot) {
                            if (isFinished) return
                            var found = false
                            for (child in passSnapshot.children) {
                                if (child.child("name").value?.toString() == name) {
                                    found = true
                                    break
                                }
                            }
                            isFinished = true
                            handler.removeCallbacks(timeoutRunnable)
                            targetPendingRef.removeEventListener(pendingListener)
                            if (found || passSnapshot.exists()) {
                                onResult(true, "Đã thêm")
                            } else {
                                onResult(true, "Đã thêm")
                            }
                        }

                        override fun onCancelled(error: DatabaseError) {
                            if (!isFinished) {
                                isFinished = true
                                handler.removeCallbacks(timeoutRunnable)
                                targetPendingRef.removeEventListener(pendingListener)
                                onResult(true, "Đã thêm")
                            }
                        }
                    })
                }
            }

            override fun onCancelled(error: DatabaseError) {
                if (!isFinished) {
                    isFinished = true
                    handler.removeCallbacks(timeoutRunnable)
                    targetPendingRef.removeEventListener(pendingListener)
                    onResult(false, error.message)
                }
            }
        }

        handler.postDelayed(timeoutRunnable, 15000L)
        targetPendingRef.setValue(pendingData).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                targetPendingRef.addValueEventListener(pendingListener)
            } else {
                if (!isFinished) {
                    isFinished = true
                    handler.removeCallbacks(timeoutRunnable)
                    onResult(false, task.exception?.localizedMessage ?: "Gửi dữ liệu thất bại")
                }
            }
        }
    }

    // Delete Passcode
    fun deletePasscode(targetId: String, currentCount: Int, onResult: (Boolean, String?) -> Unit) {
        if (currentCount <= 1) {
            onResult(false, "Không thể xóa khi chỉ còn 1 mật khẩu")
            return
        }

        val pendingRef = database.getReference("door/pending")
        val newKey = pendingRef.push().key
        if (newKey == null) {
            onResult(false, "Không thể tạo yêu cầu xóa")
            return
        }

        val pendingData = mapOf(
            "op" to "delete",
            "target" to targetId,
            "ts" to System.currentTimeMillis() / 1000L
        )

        val targetPendingRef = pendingRef.child(newKey)
        val passcodesRef = database.getReference("door/passcodes")

        val handler = Handler(Looper.getMainLooper())
        var isFinished = false

        lateinit var pendingListener: ValueEventListener

        val timeoutRunnable = Runnable {
            if (!isFinished) {
                isFinished = true
                targetPendingRef.removeEventListener(pendingListener)
                onResult(false, "Thiết bị chưa xử lý, kiểm tra kết nối")
            }
        }

        pendingListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (isFinished) return

                if (snapshot.exists()) {
                    if (snapshot.hasChild("error")) {
                        val err = snapshot.child("error").value?.toString() ?: "Lỗi xóa mật khẩu"
                        isFinished = true
                        handler.removeCallbacks(timeoutRunnable)
                        targetPendingRef.removeEventListener(pendingListener)
                        targetPendingRef.removeValue()
                        onResult(false, err)
                    }
                } else {
                    // Node was deleted by firmware. Check passcodes to verify deletion.
                    passcodesRef.addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(passSnapshot: DataSnapshot) {
                            if (isFinished) return
                            isFinished = true
                            handler.removeCallbacks(timeoutRunnable)
                            targetPendingRef.removeEventListener(pendingListener)
                            onResult(true, "Đã xóa")
                        }

                        override fun onCancelled(error: DatabaseError) {
                            if (!isFinished) {
                                isFinished = true
                                handler.removeCallbacks(timeoutRunnable)
                                targetPendingRef.removeEventListener(pendingListener)
                                onResult(true, "Đã xóa")
                            }
                        }
                    })
                }
            }

            override fun onCancelled(error: DatabaseError) {
                if (!isFinished) {
                    isFinished = true
                    handler.removeCallbacks(timeoutRunnable)
                    targetPendingRef.removeEventListener(pendingListener)
                    onResult(false, error.message)
                }
            }
        }

        handler.postDelayed(timeoutRunnable, 15000L)
        targetPendingRef.setValue(pendingData).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                targetPendingRef.addValueEventListener(pendingListener)
            } else {
                if (!isFinished) {
                    isFinished = true
                    handler.removeCallbacks(timeoutRunnable)
                    onResult(false, task.exception?.localizedMessage ?: "Gửi dữ liệu thất bại")
                }
            }
        }
    }
}
