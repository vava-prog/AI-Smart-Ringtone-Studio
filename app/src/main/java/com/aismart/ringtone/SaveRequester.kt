package com.aismart.ringtone

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import java.io.File

data class SaveRequest(
    val file: File,
    val title: String,
    val target: RingtoneTarget,
    val setDefault: Boolean
)

/**
 * Trả về hàm xử lý yêu cầu lưu/đặt nhạc chuông, tự xin các quyền cần thiết:
 * quyền sửa cài đặt hệ thống (khi đặt mặc định) và quyền ghi bộ nhớ (Android 9 trở xuống).
 */
@Composable
fun rememberSaveRequester(vm: RingtoneViewModel): (SaveRequest) -> Unit {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<SaveRequest?>(null) }

    val storagePermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val request = pending
        pending = null
        if (granted && request != null) {
            vm.saveFile(request.file, request.title, request.target, request.setDefault)
        } else {
            vm.showMessage(R.string.msg_perm_denied)
        }
    }

    return { request ->
        if (request.setDefault && !RingtoneSaver.canWriteSettings(context)) {
            vm.showMessage(R.string.msg_need_write_settings)
            try {
                context.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_WRITE_SETTINGS,
                        Uri.parse("package:${context.packageName}")
                    )
                )
            } catch (_: ActivityNotFoundException) {
            }
        } else {
            val needsStoragePermission = Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
                ContextCompat.checkSelfPermission(
                    context, Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            if (needsStoragePermission) {
                pending = request
                storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            } else {
                vm.saveFile(request.file, request.title, request.target, request.setDefault)
            }
        }
    }
}
