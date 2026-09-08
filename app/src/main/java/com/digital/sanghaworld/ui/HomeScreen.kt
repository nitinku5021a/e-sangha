package com.digital.sanghaworld.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class DurationOption(val minutes: Long, val value: String, val unit: String)

@Composable
fun HomeScreen(
    onDurationSelected: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val durations = listOf(
        DurationOption(15, "15", "minutes"),
        DurationOption(30, "30", "minutes"),
        DurationOption(45, "45", "minutes"),
        DurationOption(60, "1", "hour"),
        DurationOption(120, "2", "hours")
    )

    var showCustomDialog by remember { mutableStateOf(false) }

    if (showCustomDialog) {
        CustomDurationDialog(
            onDismiss = { showCustomDialog = false },
            onConfirm = { minutes ->
                showCustomDialog = false
                onDurationSelected(minutes * 60 * 1000)
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Vipassana",
            style = MaterialTheme.typography.displayLarge.copy(fontSize = 42.sp),
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )
        Text(
            text = "SILENCE  ·  INSIGHT",
            style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 3.sp),
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f),
            modifier = Modifier.padding(top = 8.dp, bottom = 36.dp)
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            items(durations) { option ->
                TimerOptionCard(value = option.value, unit = option.unit) {
                    onDurationSelected(option.minutes * 60 * 1000)
                }
            }
            item {
                TimerOptionCard(value = "+", unit = "custom") {
                    showCustomDialog = true
                }
            }
        }
    }
}

@Composable
fun CustomDurationDialog(
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit
) {
    var text by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Custom sit", style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column {
                Text(
                    text = "Minutes (1–240)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                TextField(
                    value = text,
                    onValueChange = { if (it.all { char -> char.isDigit() }) text = it },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedIndicatorColor = MaterialTheme.colorScheme.primary
                    )
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val minutes = text.toLongOrNull()
                    if (minutes != null && minutes in 1..240) {
                        onConfirm(minutes)
                    }
                }
            ) {
                Text("Begin", color = MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f))
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        textContentColor = MaterialTheme.colorScheme.onSurface,
        titleContentColor = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
fun TimerOptionCard(value: String, unit: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(28.dp)
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.22f), shape)
            .clickable(onClick = onClick)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = value,
                style = MaterialTheme.typography.displaySmall.copy(fontSize = 36.sp),
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Normal,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = unit.uppercase(),
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 11.sp, letterSpacing = 1.6.sp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                textAlign = TextAlign.Center
            )
        }
    }
}
