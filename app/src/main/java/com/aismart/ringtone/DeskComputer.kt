package com.aismart.ringtone

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/*
 * Cảnh máy tính để bàn cổ (isometric) do dự án tự vẽ bằng Canvas, lấy ý tưởng chung "máy tính thập niên 90".
 * Không sao chép mã hay hình từ nguồn bên ngoài.
 */

private class IsoBox(val x: Float, val y: Float, val z: Float, val w: Float, val d: Float, val h: Float)

private class IsoView(private val origin: Offset, val unit: Float) {
    fun p(x: Float, y: Float, z: Float) = Offset(
        origin.x + (x - y) * unit * 0.866f,
        origin.y + (x + y) * unit * 0.5f - z * unit
    )

    fun bounds(b: IsoBox): Rect {
        val xs = floatArrayOf(b.x, b.x + b.w)
        val ys = floatArrayOf(b.y, b.y + b.d)
        val zs = floatArrayOf(b.z, b.z + b.h)
        var left = Float.MAX_VALUE
        var top = Float.MAX_VALUE
        var right = -Float.MAX_VALUE
        var bottom = -Float.MAX_VALUE
        for (x in xs) for (y in ys) for (z in zs) {
            val o = p(x, y, z)
            left = min(left, o.x)
            top = min(top, o.y)
            right = kotlin.math.max(right, o.x)
            bottom = kotlin.math.max(bottom, o.y)
        }
        return Rect(left, top, right, bottom)
    }
}

private const val DESK_TOP = 0.5f

private val caseBox = IsoBox(0.5f, 1.0f, DESK_TOP, 2.0f, 3.2f, 3.8f)
private val standBox = IsoBox(4.4f, 1.9f, DESK_TOP, 1.4f, 1.2f, 0.6f)
private val monitorBox = IsoBox(3.4f, 0.9f, DESK_TOP + 0.6f, 3.4f, 3.0f, 2.8f)
private val keyboardBox = IsoBox(3.6f, 4.3f, DESK_TOP, 3.2f, 1.3f, 0.25f)
private val mouseBox = IsoBox(7.5f, 4.5f, DESK_TOP, 0.6f, 0.9f, 0.25f)

private fun DrawScope.polygon(points: List<Offset>, color: Color) {
    val path = Path()
    path.moveTo(points[0].x, points[0].y)
    for (i in 1 until points.size) path.lineTo(points[i].x, points[i].y)
    path.close()
    drawPath(path, color)
}

private fun DrawScope.drawBox(v: IsoView, b: IsoBox, top: Color, left: Color, right: Color) {
    val x1 = b.x + b.w
    val y1 = b.y + b.d
    val z1 = b.z + b.h
    polygon(listOf(v.p(b.x, b.y, z1), v.p(x1, b.y, z1), v.p(x1, y1, z1), v.p(b.x, y1, z1)), top)
    polygon(listOf(v.p(b.x, y1, z1), v.p(x1, y1, z1), v.p(x1, y1, b.z), v.p(b.x, y1, b.z)), left)
    polygon(listOf(v.p(x1, b.y, z1), v.p(x1, y1, z1), v.p(x1, y1, b.z), v.p(x1, b.y, b.z)), right)
}

private fun DrawScope.drawDisc(v: IsoView, cx: Float, cy: Float, z: Float, r: Float, color: Color) {
    val pts = (0 until 28).map { i ->
        val t = 2.0 * PI * i / 28
        v.p(cx + r * cos(t).toFloat(), cy + r * sin(t).toFloat(), z)
    }
    polygon(pts, color)
}

private val beigeTop = Color(0xFFE6DFC9)
private val beigeLeft = Color(0xFFC9C1A8)
private val beigeRight = Color(0xFFAFA68C)

/** Cảnh máy tính cổ: chạm màn hình để tạo mới, bàn phím để khám phá, thùng máy để mở lịch sử. */
@Composable
fun DeskComputer(
    onCreate: () -> Unit,
    onDiscover: () -> Unit,
    onHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    val phase by rememberInfiniteTransition(label = "desk").animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart),
        label = "wave"
    )
    val description = stringResource(R.string.desk_cd)

    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1.15f)
                .semantics { contentDescription = description }
                .pointerInput(Unit) {
                    detectTapGestures { tap ->
                        val view = isoView(size.width.toFloat(), size.height.toFloat())
                        when {
                            view.bounds(monitorBox).contains(tap) -> onCreate()
                            view.bounds(keyboardBox).contains(tap) -> onDiscover()
                            view.bounds(caseBox).contains(tap) -> onHistory()
                        }
                    }
                }
        ) {
            val v = isoView(size.width, size.height)

            // Mặt bàn
            drawBox(v, IsoBox(0f, 0f, 0f, 10f, 6f, DESK_TOP), Color(0xFFC49A6C), Color(0xFF9B7449), Color(0xFF7D5C39))

            // Thùng máy: khe ổ đĩa và đèn nguồn
            drawBox(v, caseBox, beigeTop, beigeLeft, beigeRight)
            val cy = caseBox.y + caseBox.d
            for (i in 0 until 2) {
                val zz = caseBox.z + 2.6f - i * 0.6f
                polygon(
                    listOf(
                        v.p(caseBox.x + 0.2f, cy, zz + 0.3f), v.p(caseBox.x + 1.8f, cy, zz + 0.3f),
                        v.p(caseBox.x + 1.8f, cy, zz), v.p(caseBox.x + 0.2f, cy, zz)
                    ),
                    Color(0xFF8E866E)
                )
            }
            drawDisc(v, caseBox.x + 0.4f, cy + 0.01f, caseBox.z + 0.5f, 0.12f, Color(0xFF4CD27A))

            // Màn hình CRT
            drawBox(v, standBox, beigeTop, beigeLeft, beigeRight)
            drawBox(v, monitorBox, beigeTop, beigeLeft, beigeRight)
            val my = monitorBox.y + monitorBox.d
            fun sp(u: Float, w: Float) = v.p(monitorBox.x + u * monitorBox.w, my, monitorBox.z + w * monitorBox.h)
            polygon(listOf(sp(0.1f, 0.9f), sp(0.9f, 0.9f), sp(0.9f, 0.18f), sp(0.1f, 0.18f)), Color(0xFF5A5546))
            polygon(listOf(sp(0.14f, 0.85f), sp(0.86f, 0.85f), sp(0.86f, 0.22f), sp(0.14f, 0.22f)), Color(0xFF0F2A24))
            val bars = 14
            for (i in 0 until bars) {
                val u = 0.2f + 0.6f * i / (bars - 1)
                val amp = (sin(phase + i * 0.7f) * 0.5f + 0.5f) * 0.28f + 0.05f
                drawLine(
                    Color(0xFF7CFFB2),
                    sp(u, 0.535f - amp),
                    sp(u, 0.535f + amp),
                    strokeWidth = v.unit * 0.07f
                )
            }

            // Bàn phím và các hàng phím
            drawBox(v, keyboardBox, Color(0xFFD8D1BB), beigeLeft, beigeRight)
            val kz = keyboardBox.z + keyboardBox.h
            for (r in 1..3) {
                val yy = keyboardBox.y + keyboardBox.d * r / 4f
                drawLine(
                    Color(0xFF8E866E),
                    v.p(keyboardBox.x + 0.15f, yy, kz),
                    v.p(keyboardBox.x + keyboardBox.w - 0.15f, yy, kz),
                    strokeWidth = v.unit * 0.06f
                )
            }

            // Chuột và dây
            drawLine(
                Color(0xFF6B655A),
                v.p(mouseBox.x + 0.3f, mouseBox.y, kz),
                v.p(keyboardBox.x + keyboardBox.w, keyboardBox.y + 0.6f, kz),
                strokeWidth = v.unit * 0.04f
            )
            drawBox(v, mouseBox, beigeTop, beigeLeft, beigeRight)

            // Đĩa CD-R
            drawDisc(v, 8.3f, 1.9f, DESK_TOP + 0.01f, 0.95f, Color(0xFFD5DCE4))
            drawDisc(v, 8.3f, 1.9f, DESK_TOP + 0.02f, 0.3f, Color(0xFFC49A6C))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onCreate) { Text(stringResource(R.string.nav_create)) }
            TextButton(onClick = onDiscover) { Text(stringResource(R.string.nav_discover)) }
            TextButton(onClick = onHistory) { Text(stringResource(R.string.nav_history)) }
        }
        Text(
            stringResource(R.string.desk_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private fun isoView(width: Float, height: Float): IsoView {
    val unit = min(width / 14.5f, height / 13.8f)
    return IsoView(Offset(width / 2f - 1.73f * unit, height / 2f - 1.3f * unit), unit)
}
