package com.aismart.ringtone

/**
 * Khung dùng thử và trả phí, hiện TẮT: [PaywallConfig.ENABLED] = false nên mọi tính năng miễn phí và không hiện giá.
 * Chỉ bật khi đã nối thanh toán thật (ví dụ Google Play Billing); khung này chưa xử lý giao dịch nào.
 */
object PaywallConfig {
    const val ENABLED = false
    const val TRIAL_DAYS = 7
    const val MONTHLY_PRICE_VND = 15_000
    const val DAY_MS = 86_400_000L
}

enum class AccessState { FREE_MODE, TRIAL, PAID, LIFETIME, EXPIRED }

object Entitlement {
    /** [trialStartMs] <= 0 nghĩa là chưa bắt đầu dùng thử, coi như còn đủ ngày dùng thử. */
    fun state(
        enabled: Boolean,
        nowMs: Long,
        trialStartMs: Long,
        paidUntilMs: Long,
        lifetime: Boolean,
        trialDays: Int = PaywallConfig.TRIAL_DAYS
    ): AccessState = when {
        !enabled -> AccessState.FREE_MODE
        lifetime -> AccessState.LIFETIME
        paidUntilMs > nowMs -> AccessState.PAID
        trialStartMs <= 0L -> AccessState.TRIAL
        nowMs < trialStartMs + trialDays * PaywallConfig.DAY_MS -> AccessState.TRIAL
        else -> AccessState.EXPIRED
    }

    fun hasAccess(state: AccessState): Boolean = state != AccessState.EXPIRED

    /** Số ngày dùng thử còn lại (làm tròn lên), 0 nếu đã hết. */
    fun trialDaysLeft(nowMs: Long, trialStartMs: Long, trialDays: Int = PaywallConfig.TRIAL_DAYS): Int {
        if (trialStartMs <= 0L) return trialDays
        val left = trialStartMs + trialDays * PaywallConfig.DAY_MS - nowMs
        if (left <= 0L) return 0
        return ((left + PaywallConfig.DAY_MS - 1) / PaywallConfig.DAY_MS).toInt()
    }

    fun formatVnd(amount: Int): String = "%,d".format(java.util.Locale.US, amount).replace(',', '.') + "đ"
}
