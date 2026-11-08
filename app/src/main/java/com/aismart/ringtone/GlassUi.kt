package com.aismart.ringtone

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider

val LocalIsDark = staticCompositionLocalOf { false }

/** Nền gradient mềm với các quầng sáng mờ, làm "nền" để các thẻ kính nổi lên. */
@Composable
fun GlassBackground(dark: Boolean, content: @Composable () -> Unit) {
    val base = if (dark) {
        listOf(Color(0xFF0E0A2B), Color(0xFF2B0F46))
    } else {
        listOf(Color(0xFFE9ECFF), Color(0xFFFBE7F4))
    }
    val cyan = Color(0xFF22E1FF).copy(alpha = if (dark) 0.32f else 0.45f)
    val orange = Color(0xFFFFB36B).copy(alpha = if (dark) 0.22f else 0.45f)
    val pink = Color(0xFFFF6BD6).copy(alpha = if (dark) 0.25f else 0.30f)

    CompositionLocalProvider(LocalIsDark provides dark) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.linearGradient(base))
                .drawBehind {
                    val r = size.width * 0.8f
                    drawRect(
                        Brush.radialGradient(
                            listOf(cyan, Color.Transparent),
                            center = Offset(size.width * 0.95f, size.height * 0.05f),
                            radius = r
                        )
                    )
                    drawRect(
                        Brush.radialGradient(
                            listOf(orange, Color.Transparent),
                            center = Offset(size.width * 0.05f, size.height * 0.95f),
                            radius = r
                        )
                    )
                    drawRect(
                        Brush.radialGradient(
                            listOf(pink, Color.Transparent),
                            center = Offset(size.width * 0.1f, size.height * 0.15f),
                            radius = r * 0.7f
                        )
                    )
                }
        ) {
            content()
        }
    }
}

/** Thẻ kính mờ: nền trong suốt dạng gradient và viền sáng ở góc trên trái. */
@Composable
fun GlassCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val dark = LocalIsDark.current
    val shape = RoundedCornerShape(24.dp)
    val fill = Brush.linearGradient(
        if (dark) {
            listOf(Color.White.copy(alpha = 0.16f), Color.White.copy(alpha = 0.05f))
        } else {
            listOf(Color.White.copy(alpha = 0.78f), Color.White.copy(alpha = 0.36f))
        }
    )
    val edge = Brush.linearGradient(
        listOf(
            Color.White.copy(alpha = if (dark) 0.55f else 0.95f),
            Color.White.copy(alpha = 0.08f),
            Color.White.copy(alpha = if (dark) 0.30f else 0.60f)
        )
    )
    Column(
        modifier = modifier
            .clip(shape)
            .background(fill, shape)
            .border(1.dp, edge, shape),
        content = content
    )
}
