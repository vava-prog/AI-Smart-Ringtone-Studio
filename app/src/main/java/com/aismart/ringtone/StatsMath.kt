package com.aismart.ringtone

import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Tính thống kê cho màn hình Tổng quan (không phụ thuộc Android để dễ kiểm thử). */
object StatsMath {
    /**
     * Số mục được tạo trong mỗi ngày của [days] ngày gần nhất, xếp từ cũ đến mới
     * (phần tử cuối là hôm nay theo múi giờ [zone]).
     */
    fun dailyCounts(timestampsMs: List<Long>, nowMs: Long, zone: ZoneId, days: Int): List<Int> {
        val counts = IntArray(days)
        val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        for (ts in timestampsMs) {
            val date = Instant.ofEpochMilli(ts).atZone(zone).toLocalDate()
            val ago = ChronoUnit.DAYS.between(date, today).toInt()
            if (ago in 0 until days) counts[days - 1 - ago]++
        }
        return counts.toList()
    }
}
