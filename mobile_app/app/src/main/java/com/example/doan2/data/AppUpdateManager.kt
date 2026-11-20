package com.example.doan2.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

data class AppUpdateInfo(
    val versionCode: Long = 0,
    val versionName: String = "",
    val downloadUrl: String = "",
    val releaseNotes: String = "",
    val force: Boolean = false
)

class AppUpdateManager(private val context: Context) {

    private val database = FirebaseDatabase.getInstance(
        "https://do-an-2-f791a-default-rtdb.asia-southeast1.firebasedatabase.app"
    )

    fun getCurrentVersionCode(): Long {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                pInfo.versionCode.toLong()
            }
        } catch (_: Exception) {
            1L
        }
    }

    fun getCurrentVersionName(): String {
        return try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0"
        } catch (_: Exception) {
            "1.0"
        }
    }

    fun checkForUpdate(onResult: (AppUpdateInfo?, Boolean, String?) -> Unit) {
        val updateRef = database.getReference("app_update")
        updateRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) {
                    onResult(null, false, "Chưa có thông tin cập nhật trên máy chủ")
                    return
                }

                val remoteVersionCode = (snapshot.child("versionCode").value as? Long)
                    ?: (snapshot.child("versionCode").value as? Number)?.toLong()
                    ?: 0L
                val remoteVersionName = snapshot.child("versionName").value?.toString() ?: ""
                val downloadUrl = snapshot.child("downloadUrl").value?.toString() ?: ""
                val releaseNotes = snapshot.child("releaseNotes").value?.toString() ?: ""
                val force = snapshot.child("force").value as? Boolean ?: false

                val info = AppUpdateInfo(
                    versionCode = remoteVersionCode,
                    versionName = remoteVersionName,
                    downloadUrl = downloadUrl,
                    releaseNotes = releaseNotes,
                    force = force
                )

                val currentCode = getCurrentVersionCode()
                val hasUpdate = (remoteVersionCode > currentCode) && downloadUrl.isNotBlank()

                onResult(info, hasUpdate, null)
            }

            override fun onCancelled(error: DatabaseError) {
                onResult(null, false, error.message)
            }
        })
    }

    suspend fun downloadAndInstall(
        downloadUrl: String,
        onProgress: (Int) -> Unit,
        onError: (String) -> Unit
    ) {
        withContext(Dispatchers.IO) {
            try {
                // Kiểm tra quyền cài đặt từ nguồn không xác định trên Android 8.0+
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    if (!context.packageManager.canRequestPackageInstalls()) {
                        withContext(Dispatchers.Main) {
                            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                                data = Uri.parse("package:${context.packageName}")
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        }
                    }
                }

                val downloadsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                    ?: context.cacheDir
                val apkFile = File(downloadsDir, "SmartHome-Update.apk")
                if (apkFile.exists()) {
                    apkFile.delete()
                }

                var connection = openConnectionWithRedirects(downloadUrl)
                val responseCode = connection.responseCode
                if (responseCode !in 200..299) {
                    withContext(Dispatchers.Main) {
                        onError("Không thể tải file cập nhật (HTTP $responseCode)")
                    }
                    return@withContext
                }

                val fileLength = connection.contentLength
                val inputStream: InputStream = connection.inputStream
                val outputStream = FileOutputStream(apkFile)

                val buffer = ByteArray(8192)
                var total: Long = 0
                var count: Int
                var lastPercent = -1

                while (inputStream.read(buffer).also { count = it } != -1) {
                    total += count.toLong()
                    outputStream.write(buffer, 0, count)

                    if (fileLength > 0) {
                        val percent = ((total * 100) / fileLength).toInt().coerceIn(0, 100)
                        if (percent != lastPercent) {
                            lastPercent = percent
                            withContext(Dispatchers.Main) {
                                onProgress(percent)
                            }
                        }
                    }
                }

                outputStream.flush()
                outputStream.close()
                inputStream.close()

                withContext(Dispatchers.Main) {
                    onProgress(100)
                    installApk(apkFile)
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onError("Lỗi khi tải file: ${e.localizedMessage ?: e.message}")
                }
            }
        }
    }

    private fun openConnectionWithRedirects(urlStr: String): HttpURLConnection {
        var currentUrl = urlStr
        var redirects = 0
        while (redirects < 5) {
            val url = URL(currentUrl)
            val conn = url.openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = false
            conn.connectTimeout = 15000
            conn.readTimeout = 30000
            conn.connect()

            val status = conn.responseCode
            if (status == HttpURLConnection.HTTP_MOVED_TEMP ||
                status == HttpURLConnection.HTTP_MOVED_PERM ||
                status == HttpURLConnection.HTTP_SEE_OTHER ||
                status == 307 || status == 308) {
                val newUrl = conn.getHeaderField("Location")
                if (newUrl != null) {
                    currentUrl = newUrl
                    redirects++
                    continue
                }
            }
            return conn
        }
        throw Exception("Quá nhiều lần chuyển hướng mạng (Redirect loop)")
    }

    fun installApk(file: File) {
        try {
            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(installIntent)
        } catch (e: Exception) {
            android.widget.Toast.makeText(
                context,
                "Không thể mở gói cài đặt: ${e.localizedMessage}",
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }
}
