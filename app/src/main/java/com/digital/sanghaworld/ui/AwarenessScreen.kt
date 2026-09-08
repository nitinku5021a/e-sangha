package com.digital.sanghaworld.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
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
    val fieldShape = RoundedCornerShape(16.dp)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Be Aware Always",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "A gentle gong through the day",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(36.dp))

        LabeledNumberField(
            label = "Total hours",
            value = hoursText,
            onValueChange = { if (it.all { ch -> ch.isDigit() }) hoursText = it },
            shape = fieldShape
        )
        Spacer(modifier = Modifier.height(16.dp))
        LabeledNumberField(
            label = "Gong every (minutes)",
            value = intervalText,
            onValueChange = { if (it.all { ch -> ch.isDigit() }) intervalText = it },
            shape = fieldShape
        )

        if (errorText.isNotBlank()) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = errorText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
        QuietButton(
            text = "Begin awareness",
            emphasized = true,
            onClick = {
                val hours = hoursText.toIntOrNull()
                val interval = intervalText.toIntOrNull()
                if (hours == null || hours <= 0) {
                    errorText = "Please enter hours greater than 0."
                    return@QuietButton
                }
                if (interval == null || interval <= 0) {
                    errorText = "Please enter interval minutes greater than 0."
                    return@QuietButton
                }
                errorText = ""
                onStart(hours, interval)
            }
        )
    }
}

@Composable
private fun LabeledNumberField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    shape: RoundedCornerShape
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 11.sp),
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f),
            modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
        )
        TextField(
            value = value,
            onValueChange = onValueChange,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            shape = shape,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent
            ),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), shape)
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
            style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 4.sp),
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Be Aware Always",
            style = MaterialTheme.typography.displaySmall.copy(fontSize = 28.sp),
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "${totalHours}h  ·  gong every ${intervalMinutes}m",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f)
        )
        Spacer(modifier = Modifier.height(40.dp))
        Text(
            text = timeFormatted,
            style = MaterialTheme.typography.displayLarge.copy(
                fontSize = 64.sp,
                fontWeight = FontWeight.Normal,
                letterSpacing = 2.sp
            ),
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(40.dp))
        QuietButton(text = "End awareness", onClick = onStop)
    }
}
