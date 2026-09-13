package com.example.tuner.ui.parental

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.tuner.parental.ChallengeDifficulty
import com.example.tuner.parental.MathChallenge
import com.example.tuner.ui.theme.TunerAmber
import com.example.tuner.ui.theme.TunerBackground
import com.example.tuner.ui.theme.TunerRed
import com.example.tuner.ui.theme.TunerTextPrimary

private const val MAX_WRONG_ANSWERS = 3
private const val MAX_ANSWER_DIGITS = 4

@Composable
fun MathChallengeDialog(
    difficulty: ChallengeDifficulty,
    onSolved: () -> Unit,
    onDismiss: () -> Unit
) {
    var challenge by remember { mutableStateOf(MathChallenge.generate(difficulty)) }
    var answer by remember { mutableStateOf("") }
    var wrongCount by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }

    val title = if (difficulty == ChallengeDifficulty.SETUP) "Parents only: solve this" else "Reset PIN: solve this"

    ParentalDialogFrame(title = title, onDismiss = onDismiss) {
        Text(
            text = "${challenge.question} = ?",
            style = MaterialTheme.typography.headlineSmall,
            color = TunerAmber,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = answer.ifEmpty { "–" },
            style = MaterialTheme.typography.headlineMedium,
            color = TunerTextPrimary
        )
        message?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, style = MaterialTheme.typography.labelMedium, color = TunerRed, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(12.dp))
        NumericKeypad(
            enabled = true,
            onDigit = { digit -> if (answer.length < MAX_ANSWER_DIGITS) answer += digit },
            onBackspace = { answer = answer.dropLast(1) }
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = {
                if (challenge.isCorrect(answer)) {
                    onSolved()
                } else {
                    wrongCount++
                    answer = ""
                    if (wrongCount >= MAX_WRONG_ANSWERS) {
                        challenge = MathChallenge.generate(difficulty)
                        wrongCount = 0
                        message = "Not quite — here's a new problem."
                    } else {
                        message = "Not quite — try again."
                    }
                }
            },
            enabled = answer.isNotEmpty(),
            colors = ButtonDefaults.buttonColors(containerColor = TunerAmber, contentColor = TunerBackground)
        ) {
            Text("CHECK")
        }
    }
}
