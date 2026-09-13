package com.example.tuner.ui.parental

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.tuner.data.repository.PinResult
import com.example.tuner.parental.ChallengeDifficulty

enum class ParentalFlowStart { SETUP, UNLOCK, CHANGE_PIN }

private enum class FlowStep { SETUP_CHALLENGE, RECOVERY_CHALLENGE, UNLOCK, CHOOSE_PIN, CONFIRM_PIN }

/**
 * One dialog at a time, walking through:
 * - SETUP: math problem → choose PIN → confirm PIN
 * - UNLOCK: PIN pad ("Forgot PIN?" → harder math problem → choose PIN → confirm PIN)
 * - CHANGE_PIN: choose PIN → confirm PIN
 * [onFinished] gets true when the parent ends up unlocked, false on cancel.
 */
@Composable
fun ParentalAuthFlow(
    start: ParentalFlowStart,
    lockoutUntil: Long,
    onVerifyPin: (String, (PinResult) -> Unit) -> Unit,
    onSetPin: (String) -> Unit,
    onFinished: (success: Boolean) -> Unit
) {
    var step by remember {
        mutableStateOf(
            when (start) {
                ParentalFlowStart.SETUP -> FlowStep.SETUP_CHALLENGE
                ParentalFlowStart.UNLOCK -> FlowStep.UNLOCK
                ParentalFlowStart.CHANGE_PIN -> FlowStep.CHOOSE_PIN
            }
        )
    }
    var chosenPin by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }

    fun goTo(next: FlowStep, newMessage: String? = null, error: Boolean = false) {
        step = next
        message = newMessage
        isError = error
    }

    val cancel = { onFinished(false) }

    when (step) {
        FlowStep.SETUP_CHALLENGE -> MathChallengeDialog(
            difficulty = ChallengeDifficulty.SETUP,
            onSolved = { goTo(FlowStep.CHOOSE_PIN) },
            onDismiss = cancel
        )
        FlowStep.RECOVERY_CHALLENGE -> MathChallengeDialog(
            difficulty = ChallengeDifficulty.RECOVERY,
            onSolved = { goTo(FlowStep.CHOOSE_PIN) },
            onDismiss = cancel
        )
        FlowStep.UNLOCK -> PinPadDialog(
            title = "Enter parent PIN",
            message = message,
            isError = isError,
            lockoutUntil = lockoutUntil,
            onPinEntered = { pin ->
                onVerifyPin(pin) { result ->
                    when (result) {
                        PinResult.Ok -> onFinished(true)
                        is PinResult.Wrong -> {
                            message = "Wrong PIN — ${result.attemptsLeft} tries left"
                            isError = true
                        }
                        is PinResult.LockedOut -> {
                            message = null
                            isError = true
                        }
                    }
                }
            },
            onDismiss = cancel,
            onForgotPin = { goTo(FlowStep.RECOVERY_CHALLENGE) }
        )
        FlowStep.CHOOSE_PIN -> PinPadDialog(
            title = "Choose a 4-digit PIN",
            message = message,
            isError = isError,
            lockoutUntil = 0L,
            onPinEntered = { pin ->
                chosenPin = pin
                goTo(FlowStep.CONFIRM_PIN)
            },
            onDismiss = cancel
        )
        FlowStep.CONFIRM_PIN -> PinPadDialog(
            title = "Enter the PIN again",
            message = null,
            isError = false,
            lockoutUntil = 0L,
            onPinEntered = { pin ->
                if (pin == chosenPin) {
                    onSetPin(pin)
                    onFinished(true)
                } else {
                    chosenPin = ""
                    goTo(FlowStep.CHOOSE_PIN, "PINs didn't match — choose again", error = true)
                }
            },
            onDismiss = cancel
        )
    }
}
