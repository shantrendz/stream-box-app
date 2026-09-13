package com.example.tuner.parental

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LockoutPolicyTest {

    @Test
    fun `first four wrong pins only count up`() {
        var failed = 0
        repeat(4) {
            val outcome = LockoutPolicy.onWrongPin(failed, now = 1_000L)
            assertEquals(0L, outcome.lockoutUntil)
            failed = outcome.failedAttempts
        }
        assertEquals(4, failed)
    }

    @Test
    fun `fifth wrong pin locks out for thirty seconds and resets the counter`() {
        val outcome = LockoutPolicy.onWrongPin(failedAttempts = 4, now = 1_000L)
        assertEquals(0, outcome.failedAttempts)
        assertEquals(31_000L, outcome.lockoutUntil)
    }

    @Test
    fun `locked out only before the deadline`() {
        assertTrue(LockoutPolicy.isLockedOut(lockoutUntil = 31_000L, now = 30_999L))
        assertFalse(LockoutPolicy.isLockedOut(lockoutUntil = 31_000L, now = 31_000L))
        assertFalse(LockoutPolicy.isLockedOut(lockoutUntil = 0L, now = 5L))
    }
}
