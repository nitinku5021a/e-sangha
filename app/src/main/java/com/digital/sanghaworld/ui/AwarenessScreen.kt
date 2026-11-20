package com.digital.sanghaworld.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AwarenessSetupScreen(
    onStart: (totalHours: Int, intervalMinutes: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var hoursText by remember { mutableStateOf("8") }
    var intervalText by remember { mutableStateOf("10") }
    var errorText by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = SanghaScreenPadding),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ScreenIntro(
            subtitle = "A quiet gong through the day, at an interval you choose."
        )
        Spacer(modifier = Modifier.height(32.dp))

        SanghaField(
            value = hoursText,
            onValueChange = { if (it.all { ch -> ch.isDigit() }) hoursText = it },
            label = "Total hours",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        Spacer(modifier = Modifier.height(16.dp))
        SanghaField(
            value = intervalText,
            onValueChange = { if (it.all { ch -> ch.isDigit() }) intervalText = it },
            label = "Gong every (minutes)",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )

        if (errorText.isNotBlank()) {
            Spacer(modifier = Modifier.height(12.dp))
            StatusText(errorText, error = true)
        }

        Spacer(modifier = Modifier.height(28.dp))
        QuietButton(
            text = "Begin awareness",
            emphasized = true,
            onClick = {
                val hours = hoursText.toIntOrNull()
                val interval = intervalText.toIntOrNull()
                if (hours == null || hours <= 0) {
                    errorText = "Enter hours greater than 0."
                    return@QuietButton
                }
                if (interval == null || interval <= 0) {
                    errorText = "Enter interval minutes greater than 0."
                    return@QuietButton
                }
                errorText = ""
                onStart(hours, interval)
            },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun AwarenessRunningScreen(
    timeLeft: Long,
    totalDuration: Long,
    intervalMillis: Long,
    onStop: () -> Unit
) {
    val minutes = (timeLeft / 1000) / 60
    val seconds = (timeLeft / 1000) % 60
    val timeFormatted = String.format("%02d:%02d", minutes, seconds)
    val intervalMinutes = (intervalMillis / 1000) / 60
    val totalHours = totalDuration / (1000 * 60 * 60)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "AWARENESS",
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 3.sp),
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Be aware always",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "${totalHours}h  ·  gong every ${intervalMinutes}m",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f)
        )
        Spacer(modifier = Modifier.height(36.dp))
        Text(
            text = timeFormatted,
            style = MaterialTheme.typography.displayLarge.copy(
                fontSize = 56.sp,
                letterSpacing = 2.sp
            ),
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(36.dp))
        QuietButton(text = "End awareness", onClick = onStop)
    }
}
