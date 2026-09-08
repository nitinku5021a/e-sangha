package com.digital.sanghaworld.ui.hall

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.digital.sanghaworld.hall.AudioType
import com.digital.sanghaworld.hall.HallVisibility
import com.digital.sanghaworld.hall.ScheduleType
import com.digital.sanghaworld.ui.QuietButton
import java.time.DayOfWeek

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateHallScreen(
    onCreate: (
        name: String,
        description: String,
        durationMinutes: Int,
        hour: Int,
        minute: Int,
        scheduleType: ScheduleType,
        days: Set<DayOfWeek>,
        visibility: HallVisibility,
        audioType: AudioType
    ) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var duration by remember { mutableStateOf("60") }
    var hour by remember { mutableStateOf("6") }
    var minute by remember { mutableStateOf("0") }
    var scheduleType by remember { mutableStateOf(ScheduleType.DAILY) }
    var visibility by remember { mutableStateOf(HallVisibility.PUBLIC) }
    var audioType by remember { mutableStateOf(AudioType.BELL) }
    var sunday by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 8.dp)
    ) {
        Text("Create a hall", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(name, { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(description, { description = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            duration, { if (it.all { c -> c.isDigit() }) duration = it },
            label = { Text("Duration (minutes)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                hour, { if (it.all { c -> c.isDigit() }) hour = it },
                label = { Text("Hour") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            OutlinedTextField(
                minute, { if (it.all { c -> c.isDigit() }) minute = it },
                label = { Text("Minute") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
                singleLine = true
            )
        }
        Spacer(Modifier.height(12.dp))
        Text("Schedule", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(scheduleType == ScheduleType.DAILY, { scheduleType = ScheduleType.DAILY }, { Text("Daily") })
            FilterChip(scheduleType == ScheduleType.WEEKDAYS, { scheduleType = ScheduleType.WEEKDAYS }, { Text("Weekdays") })
            FilterChip(scheduleType == ScheduleType.WEEKLY, { scheduleType = ScheduleType.WEEKLY; sunday = true }, { Text("Weekly") })
            FilterChip(scheduleType == ScheduleType.ONCE, { scheduleType = ScheduleType.ONCE }, { Text("Once") })
        }
        Spacer(Modifier.height(12.dp))
        Text("Visibility", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(visibility == HallVisibility.PUBLIC, { visibility = HallVisibility.PUBLIC }, { Text("Public") })
            FilterChip(visibility == HallVisibility.PRIVATE, { visibility = HallVisibility.PRIVATE }, { Text("Private") })
        }
        Spacer(Modifier.height(12.dp))
        Text("Audio", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(audioType == AudioType.NONE, { audioType = AudioType.NONE }, { Text("Silence") })
            FilterChip(audioType == AudioType.BELL, { audioType = AudioType.BELL }, { Text("Bell") })
        }
        Spacer(Modifier.height(20.dp))
        QuietButton(
            text = "Create",
            emphasized = true,
            onClick = {
                onCreate(
                    name,
                    description,
                    duration.toIntOrNull() ?: 60,
                    hour.toIntOrNull() ?: 6,
                    minute.toIntOrNull() ?: 0,
                    scheduleType,
                    if (scheduleType == ScheduleType.WEEKLY) setOf(DayOfWeek.SUNDAY) else emptySet(),
                    visibility,
                    audioType
                )
            },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(10.dp))
        QuietButton(text = "Cancel", onClick = onCancel, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(24.dp))
    }
}
