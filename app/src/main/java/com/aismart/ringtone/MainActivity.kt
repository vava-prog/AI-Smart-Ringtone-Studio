package com.aismart.ringtone

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels

class MainActivity : ComponentActivity() {
    private val viewModel: RingtoneViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Chỉ xử lý intent ở lần mở đầu tiên, không xử lý lại khi xoay màn hình
        if (savedInstanceState == null) handleIncomingIntent(intent)
        setContent { RingtoneApp(viewModel) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    override fun onStop() {
        super.onStop()
        viewModel.stopPlayback()
    }

    /** Nhận file âm thanh hoặc link YouTube/TikTok được chia sẻ (SEND), hoặc mở file từ trình quản lý (VIEW). */
    private fun handleIncomingIntent(incoming: Intent?) {
        val uri: Uri? = when (incoming?.action) {
            Intent.ACTION_SEND -> {
                val stream = readStreamExtra(incoming)
                if (stream == null) {
                    val link = SourceLinks.parse(incoming.getStringExtra(Intent.EXTRA_TEXT))
                    if (link != null) viewModel.receiveLink(link.url)
                }
                stream
            }
            Intent.ACTION_VIEW -> incoming.data
            else -> null
        }
        if (uri != null) viewModel.loadSource(uri)
    }

    private fun readStreamExtra(incoming: Intent): Uri? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            incoming.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            incoming.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
        }
    }
}
