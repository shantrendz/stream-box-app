package com.example.tuner.parental

/** Wrong-PIN throttling: every [MAX_ATTEMPTS]th consecutive miss blocks entry for [LOCKOUT_MILLIS]. */
object LockoutPolicy {

    const val MAX_ATTEMPTS = 5
    const val LOCKOUT_MILLIS = 30_000L

    data class Outcome(val failedAttempts: Int, val lockoutUntil: Long)

    fun onWrongPin(failedAttempts: Int, now: Long): Outcome {
        val next = failedAttempts + 1
        return if (next >= MAX_ATTEMPTS) Outcome(0, now + LOCKOUT_MILLIS) else Outcome(next, 0L)
    }

    fun isLockedOut(lockoutUntil: Long, now: Long): Boolean = now < lockoutUntil
}
