package com.aismart.ringtone

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EntitlementTest {
    private val day = PaywallConfig.DAY_MS

    @Test
    fun disabled_isFreeMode() {
        assertEquals(AccessState.FREE_MODE, Entitlement.state(false, 10 * day, 1, 0, false))
        assertTrue(Entitlement.hasAccess(AccessState.FREE_MODE))
    }

    @Test
    fun paywallIsOffByDefault() {
        assertFalse(PaywallConfig.ENABLED)
    }

    @Test
    fun trial_thenExpired() {
        assertEquals(AccessState.TRIAL, Entitlement.state(true, 3 * day, day, 0, false, 7))
        assertEquals(AccessState.EXPIRED, Entitlement.state(true, 9 * day, day, 0, false, 7))
        assertFalse(Entitlement.hasAccess(AccessState.EXPIRED))
    }

    @Test
    fun paidAndLifetimeOverrideTrial() {
        assertEquals(AccessState.PAID, Entitlement.state(true, 20 * day, day, 30 * day, false, 7))
        assertEquals(AccessState.LIFETIME, Entitlement.state(true, 20 * day, day, 0, true, 7))
    }

    @Test
    fun daysLeftRoundsUp() {
        assertEquals(7, Entitlement.trialDaysLeft(0, 0, 7))
        assertEquals(7, Entitlement.trialDaysLeft(2 * day - 1, day, 7))
        assertEquals(6, Entitlement.trialDaysLeft(2 * day, day, 7))
        assertEquals(3, Entitlement.trialDaysLeft(5 * day, day, 7))
        assertEquals(0, Entitlement.trialDaysLeft(9 * day, day, 7))
    }

    @Test
    fun formatsVnd() {
        assertEquals("15.000đ", Entitlement.formatVnd(15_000))
    }
}
