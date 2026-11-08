package com.aismart.ringtone

import android.content.ContentValues
import android.content.Context
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import androidx.annotation.StringRes
import java.io.File
import java.io.IOException

enum class RingtoneTarget(
    @StringRes val labelRes: Int,
    val mediaDir: String,
    val managerType: Int
) {
    RINGTONE(R.string.target_ringtone, Environment.DIRECTORY_RINGTONES, RingtoneManager.TYPE_RINGTONE),
    NOTIFICATION(R.string.target_notification, Environment.DIRECTORY_NOTIFICATIONS, RingtoneManager.TYPE_NOTIFICATION),
    ALARM(R.string.target_alarm, Environment.DIRECTORY_ALARMS, RingtoneManager.TYPE_ALARM)
}

/** Lưu file vào thư viện âm thanh của hệ thống và đặt làm nhạc chuông mặc định. */
object RingtoneSaver {
    fun canWriteSettings(context: Context): Boolean = Settings.System.canWrite(context)

    /** Lưu vào Ringtones/Notifications/Alarms để hệ thống nhìn thấy; trả về Uri trong MediaStore. */
    fun saveToLibrary(context: Context, source: File, title: String, target: RingtoneTarget): Uri {
        val format = OutputFormat.fromExt(source.extension)
        val fileName = "$title.${format.ext}"
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveScoped(context, source, title, fileName, format.mime, target)
        } else {
            saveLegacy(context, source, title, fileName, format.mime, target)
        }
    }

    fun setAsDefault(context: Context, uri: Uri, target: RingtoneTarget) {
        RingtoneManager.setActualDefaultRingtoneUri(context, target.managerType, uri)
    }

    private fun typeFlags(values: ContentValues, target: RingtoneTarget) {
        values.put(MediaStore.Audio.Media.IS_RINGTONE, target == RingtoneTarget.RINGTONE)
        values.put(MediaStore.Audio.Media.IS_NOTIFICATION, target == RingtoneTarget.NOTIFICATION)
        values.put(MediaStore.Audio.Media.IS_ALARM, target == RingtoneTarget.ALARM)
        values.put(MediaStore.Audio.Media.IS_MUSIC, false)
    }

    private fun saveScoped(
        context: Context,
        source: File,
        title: String,
        fileName: String,
        mime: String,
        target: RingtoneTarget
    ): Uri {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, target.mediaDir)
            put(MediaStore.Audio.Media.TITLE, title)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        typeFlags(values, target)

        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = resolver.insert(collection, values)
            ?: throw IOException("MediaStore insert failed")
        try {
            val stream = resolver.openOutputStream(uri)
                ?: throw IOException("Cannot open output stream")
            stream.use { out -> source.inputStream().use { it.copyTo(out) } }
            val done = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            resolver.update(uri, done, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
        return uri
    }

    @Suppress("DEPRECATION")
    private fun saveLegacy(
        context: Context,
        source: File,
        title: String,
        fileName: String,
        mime: String,
        target: RingtoneTarget
    ): Uri {
        val dir = Environment.getExternalStoragePublicDirectory(target.mediaDir)
        if (!dir.exists() && !dir.mkdirs()) throw IOException("Cannot create ${dir.path}")

        var dest = File(dir, fileName)
        if (dest.exists()) dest = File(dir, "${title}_${System.currentTimeMillis()}.${source.extension}")
        source.copyTo(dest, overwrite = false)

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DATA, dest.absolutePath)
            put(MediaStore.MediaColumns.DISPLAY_NAME, dest.name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.SIZE, dest.length())
            put(MediaStore.Audio.Media.TITLE, title)
        }
        typeFlags(values, target)

        val collection = MediaStore.Audio.Media.getContentUriForPath(dest.absolutePath)
            ?: MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        return context.contentResolver.insert(collection, values)
            ?: throw IOException("MediaStore insert failed")
    }
}
