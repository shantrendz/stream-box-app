package com.example.tuner.ui.parental

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.tuner.ui.channels.ChannelListUiState
import com.example.tuner.ui.channels.ChannelListViewModel
import com.example.tuner.ui.theme.TunerAmber
import com.example.tuner.ui.theme.TunerBackground
import com.example.tuner.ui.theme.TunerRed
import com.example.tuner.ui.theme.TunerTextPrimary
import com.example.tuner.ui.theme.TunerTextSecondary

@Composable
fun ParentalControlSection(uiState: ChannelListUiState, viewModel: ChannelListViewModel) {
    var flowStart by remember { mutableStateOf<ParentalFlowStart?>(null) }
    val amberButton = ButtonDefaults.buttonColors(containerColor = TunerAmber, contentColor = TunerBackground)

    when {
        !uiState.hasParentPin -> SettingRow(
            title = "Set up parental control",
            subtitle = "Kids Mode shows only kids channels. Settings and leaving Kids Mode need a PIN."
        ) {
            Button(onClick = { flowStart = ParentalFlowStart.SETUP }, colors = amberButton) { Text("SET UP") }
        }

        !uiState.parentUnlocked -> SettingRow(
            title = "Parental control is on",
            subtitle = if (uiState.kidsMode) "Kids Mode is active." else "Unlock to turn on Kids Mode or manage channels."
        ) {
            Button(onClick = { flowStart = ParentalFlowStart.UNLOCK }, colors = amberButton) { Text("UNLOCK") }
        }

        else -> {
            SettingRow(
                title = "Kids Mode",
                subtitle = "Only kids channels, search and favorites. Turning it off needs the PIN."
            ) {
                Switch(
                    checked = uiState.kidsMode,
                    onCheckedChange = viewModel::setKidsMode,
                    colors = SwitchDefaults.colors(checkedThumbColor = TunerAmber, checkedTrackColor = TunerAmber.copy(alpha = 0.4f))
                )
            }
            Text(
                text = "While unlocked, tap the shield on any channel to hide it from Kids Mode or allow it.",
                style = MaterialTheme.typography.labelSmall,
                color = TunerTextSecondary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = { flowStart = ParentalFlowStart.CHANGE_PIN }, modifier = Modifier.weight(1f)) {
                    Text("CHANGE PIN", color = TunerTextPrimary, maxLines = 1)
                }
                OutlinedButton(onClick = viewModel::lockParent, modifier = Modifier.weight(1f)) {
                    Text("LOCK", color = TunerTextPrimary, maxLines = 1)
                }
                Button(
                    onClick = viewModel::disableParentalControl,
                    colors = ButtonDefaults.buttonColors(containerColor = TunerRed, contentColor = Color.Black),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("TURN OFF", maxLines = 1)
                }
            }
        }
    }

    flowStart?.let { start ->
        ParentalAuthFlow(
            start = start,
            lockoutUntil = uiState.pinLockoutUntil,
            onVerifyPin = viewModel::verifyParentPin,
            onSetPin = viewModel::setParentPin,
            onFinished = { flowStart = null }
        )
    }
}

@Composable
private fun SettingRow(title: String, subtitle: String, action: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = TunerTextPrimary)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = TunerTextSecondary)
        }
        Spacer(Modifier.width(12.dp))
        action()
    }
}
