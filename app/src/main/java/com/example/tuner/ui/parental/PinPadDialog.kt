package com.example.tuner.ui.parental

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.tuner.ui.theme.TunerAmber
import com.example.tuner.ui.theme.TunerBackground
import com.example.tuner.ui.theme.TunerCyan
import com.example.tuner.ui.theme.TunerOutline
import com.example.tuner.ui.theme.TunerRed
import com.example.tuner.ui.theme.TunerSurface
import com.example.tuner.ui.theme.TunerTextPrimary
import com.example.tuner.ui.theme.TunerTextSecondary
import kotlinx.coroutines.delay

private const val PIN_LENGTH = 4

@Composable
internal fun ParentalDialogFrame(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(20.dp)
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .clip(shape)
                .background(TunerSurface)
                .border(1.dp, TunerOutline, shape)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = TunerTextPrimary,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(16.dp))
            content()
            TextButton(onClick = onDismiss) { Text("Cancel", color = TunerTextSecondary) }
        }
    }
}

/** 3×4 digit grid (1–9, blank, 0, backspace). Large keys so it works with touch and D-pad. */
@Composable
internal fun NumericKeypad(enabled: Boolean, onDigit: (Char) -> Unit, onBackspace: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("123", "456", "789").forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { digit -> KeypadKey(digit.toString(), digit.toString(), enabled) { onDigit(digit) } }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(width = 64.dp, height = 52.dp))
            KeypadKey("0", "0", enabled) { onDigit('0') }
            KeypadKey("⌫", "Delete", enabled, onBackspace)
        }
    }
}

@Composable
private fun KeypadKey(label: String, description: String, enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = Modifier
            .size(width = 64.dp, height = 52.dp)
            .clip(shape)
            .background(TunerBackground)
            .border(1.dp, TunerOutline, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleLarge,
            color = if (enabled) TunerTextPrimary else TunerTextSecondary
        )
    }
}

/**
 * 4-digit PIN entry. Calls [onPinEntered] as soon as the 4th digit is typed and clears the
 * dots for the next attempt. While [lockoutUntil] is in the future the keypad is disabled and
 * a countdown replaces [message].
 */
@Composable
fun PinPadDialog(
    title: String,
    message: String?,
    isError: Boolean,
    lockoutUntil: Long,
    onPinEntered: (String) -> Unit,
    onDismiss: () -> Unit,
    onForgotPin: (() -> Unit)? = null,
    inputEnabled: Boolean = true
) {
    var pin by remember { mutableStateOf("") }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(lockoutUntil) {
        while (System.currentTimeMillis() < lockoutUntil) {
            now = System.currentTimeMillis()
            delay(250)
        }
        now = System.currentTimeMillis()
    }
    val lockedSeconds = ((lockoutUntil - now + 999) / 1000).coerceAtLeast(0)
    val lockedOut = lockedSeconds > 0

    ParentalDialogFrame(title = title, onDismiss = onDismiss) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            repeat(PIN_LENGTH) { i ->
                Box(
                    Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(if (i < pin.length) TunerAmber else TunerOutline)
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        val status = if (lockedOut) "Too many wrong tries — wait ${lockedSeconds}s" else message
        if (status != null) {
            Text(
                text = status,
                style = MaterialTheme.typography.labelMedium,
                color = if (lockedOut || isError) TunerRed else TunerTextSecondary,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(12.dp))
        }
        NumericKeypad(
            enabled = !lockedOut && inputEnabled,
            onDigit = { digit ->
                if (pin.length < PIN_LENGTH) {
                    pin += digit
                    if (pin.length == PIN_LENGTH) {
                        val entered = pin
                        pin = ""
                        onPinEntered(entered)
                    }
                }
            },
            onBackspace = { pin = pin.dropLast(1) }
        )
        if (onForgotPin != null) {
            TextButton(onClick = onForgotPin) { Text("Forgot PIN?", color = TunerCyan) }
        }
    }
}
