package com.example.tuner.parental

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class MathChallengeTest {

    private fun operands(question: String): List<String> = question.split(" ")

    @Test
    fun `setup problems stay in range and answer matches the question`() {
        val random = Random(42)
        repeat(1000) {
            val c = MathChallenge.generate(ChallengeDifficulty.SETUP, random)
            val (a, times, b, plus, addend) = operands(c.question)
            assertEquals("×", times)
            assertEquals("+", plus)
            assertTrue(a.toInt() in 6..9)
            assertTrue(b.toInt() in 11..19)
            assertTrue(addend.toInt() in 10..99)
            assertEquals(a.toInt() * b.toInt() + addend.toInt(), c.answer)
        }
    }

    @Test
    fun `recovery problems stay in range and are always positive`() {
        val random = Random(7)
        repeat(1000) {
            val c = MathChallenge.generate(ChallengeDifficulty.RECOVERY, random)
            val (a, times, b, minus, subtrahend) = operands(c.question)
            assertEquals("×", times)
            assertEquals("−", minus)
            assertTrue(a.toInt() in 12..49)
            assertTrue(b.toInt() in 6..9)
            assertTrue(subtrahend.toInt() in 100..199)
            assertEquals(a.toInt() * b.toInt() - subtrahend.toInt(), c.answer)
            assertTrue(c.answer > 0)
        }
    }

    @Test
    fun `isCorrect trims input and rejects non-numbers`() {
        val c = MathChallenge("7 × 13 + 18", 109)
        assertTrue(c.isCorrect("109"))
        assertTrue(c.isCorrect(" 109 "))
        assertFalse(c.isCorrect("108"))
        assertFalse(c.isCorrect(""))
        assertFalse(c.isCorrect("abc"))
    }
}
