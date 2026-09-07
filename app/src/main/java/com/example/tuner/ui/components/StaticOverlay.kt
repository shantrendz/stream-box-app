package com.example.tuner.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.tuner.ui.theme.TunerRed
import com.example.tuner.ui.theme.TunerRedTint
import com.example.tuner.ui.theme.TunerTextPrimary
import com.example.tuner.ui.theme.TunerTextSecondary

/**
 * "Signal lost" state — a red-tinted static overlay rendered in-frame over the player, not
 * a generic Material snackbar/dialog. Keeps the error diegetic to the "TV" metaphor.
 */
@Composable
fun StaticOverlay(
    onRetry: () -> Unit,
    onRetryViaProxy: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(TunerRedTint)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "SIGNAL LOST",
            style = MaterialTheme.typography.headlineMedium,
            color = TunerRed,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "This stream couldn't be reached. It may be temporarily down, or blocked in your region by the broadcaster.",
            style = MaterialTheme.typography.bodyMedium,
            color = TunerTextPrimary,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(containerColor = TunerRed, contentColor = Color.Black),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("RETRY")
            }

            OutlinedButton(
                onClick = onRetryViaProxy,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TunerTextPrimary),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("RETRY VIA PROXY")
            }
        }

        Spacer(Modifier.height(10.dp))

        Text(
            text = "\"Retry via proxy\" is best-effort — it may help with network quirks, but it will not reliably defeat IP-based geo-restriction, since that's enforced by the broadcaster's servers.",
            style = MaterialTheme.typography.labelSmall,
            color = TunerTextSecondary,
            textAlign = TextAlign.Center
        )
    }
}
