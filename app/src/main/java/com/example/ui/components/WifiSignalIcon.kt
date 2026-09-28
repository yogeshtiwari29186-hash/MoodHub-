package com.example.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NetworkWifi
import androidx.compose.material.icons.filled.NetworkWifi1Bar
import androidx.compose.material.icons.filled.NetworkWifi2Bar
import androidx.compose.material.icons.filled.NetworkWifi3Bar
import androidx.compose.material.icons.filled.SignalWifi0Bar
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.SignalGreen
import com.example.ui.theme.SignalOrange
import com.example.ui.theme.SignalRed
import com.example.ui.theme.SignalYellow

@Composable
fun WifiSignalIcon(
    signalLevel: Int, // 0 to 4
    isSecure: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    tintOverride: Color? = null
) {
    val (icon, color) = when (signalLevel) {
        4 -> Icons.Filled.Wifi to SignalGreen
        3 -> Icons.Filled.NetworkWifi to SignalGreen
        2 -> Icons.Filled.NetworkWifi3Bar to SignalYellow
        1 -> Icons.Filled.NetworkWifi2Bar to SignalOrange
        else -> Icons.Filled.SignalWifi0Bar to SignalRed
    }

    val finalTint = tintOverride ?: color

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = "Wi-Fi Signal Strength $signalLevel of 4",
            tint = finalTint,
            modifier = Modifier.size(size)
        )

        if (isSecure) {
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = "Secured Network",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(size * 0.45f)
                    .align(Alignment.BottomEnd)
                    .offset(x = 2.dp, y = 2.dp)
            )
        }
    }
}
