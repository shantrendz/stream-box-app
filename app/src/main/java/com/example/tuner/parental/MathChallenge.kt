package com.example.tuner.parental

import kotlin.random.Random

enum class ChallengeDifficulty { SETUP, RECOVERY }

/**
 * A mental-arithmetic problem used as a lightweight "is this a parent?" check before a PIN can
 * be created or reset. Not a security boundary — just harder than a young child will solve.
 */
data class MathChallenge(val question: String, val answer: Int) {

    fun isCorrect(input: String): Boolean = input.trim().toIntOrNull() == answer

    companion object {
        fun generate(difficulty: ChallengeDifficulty, random: Random = Random.Default): MathChallenge =
            when (difficulty) {
                ChallengeDifficulty.SETUP -> {
                    val a = random.nextInt(6, 10)
                    val b = random.nextInt(11, 20)
                    val c = random.nextInt(10, 100)
                    MathChallenge("$a × $b + $c", a * b + c)
                }
                ChallengeDifficulty.RECOVERY -> {
                    var a: Int
                    var b: Int
                    var c: Int
                    do {
                        a = random.nextInt(12, 50)
                        b = random.nextInt(6, 10)
                        c = random.nextInt(100, 200)
                    } while (a * b - c <= 0)
                    MathChallenge("$a × $b − $c", a * b - c)
                }
            }
    }
}
