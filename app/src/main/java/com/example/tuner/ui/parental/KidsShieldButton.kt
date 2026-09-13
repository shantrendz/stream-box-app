package com.example.tuner.ui.parental

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RemoveModerator
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.tuner.parental.KidsShieldState
import com.example.tuner.ui.theme.TunerAmber
import com.example.tuner.ui.theme.TunerRed
import com.example.tuner.ui.theme.TunerTextSecondary

/** Shown on channel rows/tiles only while a parent is unlocked. */
@Composable
fun KidsShieldButton(
    state: KidsShieldState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconSize: Dp = 22.dp
) {
    val icon = when (state) {
        KidsShieldState.VISIBLE_IN_KIDS, KidsShieldState.APPROVED -> Icons.Filled.Shield
        KidsShieldState.HIDDEN -> Icons.Filled.RemoveModerator
        KidsShieldState.NEUTRAL -> Icons.Outlined.Shield
    }
    val tint = when (state) {
        KidsShieldState.VISIBLE_IN_KIDS, KidsShieldState.APPROVED -> TunerAmber
        KidsShieldState.HIDDEN -> TunerRed
        KidsShieldState.NEUTRAL -> TunerTextSecondary
    }
    val description = when (state) {
        KidsShieldState.VISIBLE_IN_KIDS -> "Hide from kids"
        KidsShieldState.APPROVED -> "Remove from kids"
        KidsShieldState.HIDDEN, KidsShieldState.NEUTRAL -> "Allow for kids"
    }
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(iconSize))
    }
}
